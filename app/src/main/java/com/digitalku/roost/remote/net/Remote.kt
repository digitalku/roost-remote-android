package com.digitalku.roost.remote.net

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Request as HttpRequest

private val JSON = "application/json".toMediaType()

/**
 * A problem the server answered with: an HTTP refusal (RR-5, RR-7, RR-9), or an
 * engine's `ok: false` (RR-8). [code] is what to branch on, never [message].
 */
class RemoteException(val status: Int, val problem: Problem) : IOException(problem.message) {
    val code: String get() = problem.code
}

/** A Roost window's server before pairing: the two routes that need no token. */
class Server(val base: HttpUrl, private val http: OkHttpClient) {
    suspend fun hello(): Hello {
        val request = HttpRequest.Builder().url("${base}api/1/hello").get().build()
        return wire.decodeFromString(Hello.serializer(), execute(http, request))
    }

    /** RR-5. The password is sent once and kept nowhere here (RR-6). */
    suspend fun pair(password: String, device: String): Paired {
        val body = buildJsonObject {
            put("password", password)
            put("device", device)
        }
        val request = HttpRequest.Builder()
            .url("${base}api/1/pair")
            .post(body.toString().toRequestBody(JSON))
            .build()
        return wire.decodeFromString(Paired.serializer(), execute(http, request))
    }
}

/** What the event stream says (RR-12), and what happened to the stream itself. */
sealed interface ServerEvent {
    /** Connected, the first time or again. What is on screen is re-read (RR-12). */
    data object Connected : ServerEvent

    data object Disconnected : ServerEvent

    data object Changed : ServerEvent

    data class Derived(val assets: List<Redrawn>) : ServerEvent

    data class Renamed(val name: String) : ServerEvent

    /** The window closed the library, and sharing stopped with it. */
    data object Lost : ServerEvent

    /** RR-7: the token no longer opens anything. The stream ends here. */
    data object Unpaired : ServerEvent
}

/** A paired library: every route, with the device's token. */
class Session(val base: HttpUrl, val token: String, private val http: OkHttpClient) {
    private val streaming = http.newBuilder()
        // A ping arrives every 25 seconds (RR-12), so a minute of silence is a
        // stream that has gone, not a quiet library.
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Whether [url] is this server's, which is what decides who gets the token. */
    fun owns(url: HttpUrl): Boolean = url.host == base.host && url.port == base.port

    /** One contract request, and its value (RR-8). */
    suspend fun <T> ask(request: Request, answer: KSerializer<T>): T {
        val http = HttpRequest.Builder()
            .url("${base}api/1/request")
            .header("Authorization", "Bearer $token")
            .post(wire.encodeToString(Request.serializer(), request).toRequestBody(JSON))
            .build()
        val outcome = wire.parseToJsonElement(execute(this.http, http)).jsonObject
        if (outcome["ok"]?.jsonPrimitive?.booleanOrNull == true) {
            return wire.decodeFromJsonElement(answer, outcome["value"] ?: JsonNull)
        }
        val problem = outcome["problem"] ?: throw IOException("an answer with neither value nor problem")
        throw RemoteException(200, wire.decodeFromJsonElement(Problem.serializer(), problem))
    }

    /** A request whose answer is `null`, which is what every edit answers. */
    suspend fun tell(request: Request) {
        ask(request, JsonElement.serializer())
    }

    /**
     * RR-14: everything that changes a thumbnail's pixels is in its address,
     * `at` then `v`, each only when it is not `null`.
     */
    fun thumbnail(id: String, posterMs: Long?, derivedAt: Long?, size: Int = 256): String {
        val parts = buildList {
            if (posterMs != null) add("at=$posterMs")
            if (derivedAt != null) add("v=$derivedAt")
        }
        val query = if (parts.isEmpty()) "" else parts.joinToString("&", prefix = "?")
        return "${base}bytes/thumb/$id@$size$query"
    }

    fun thumbnail(card: AssetCard, size: Int = 256): String =
        thumbnail(card.id, card.posterMs, card.derivedAt, size)

    /** The file itself; the hash keeps a cached copy from outliving an edit (RR-14). */
    fun file(id: String, contentHash: String?): String =
        "${base}bytes/file/$id" + (contentHash?.let { "?h=$it" } ?: "")

    /** A picture Android can draw of a file it cannot: RAW, TIFF (RR-13). */
    fun render(id: String): String = "${base}bytes/render/$id"

    /**
     * The event stream, reconnected for as long as it is collected, waiting a
     * second and then longer each time it drops. It ends only on [ServerEvent.Unpaired].
     */
    fun events(): Flow<ServerEvent> = flow {
        var wait = 1_000L
        while (true) {
            var unpaired = false
            connection()
                .catch { if (it !is IOException) throw it }
                .collect { event ->
                    if (event == ServerEvent.Connected) wait = 1_000L
                    if (event == ServerEvent.Unpaired) unpaired = true
                    emit(event)
                }
            if (unpaired) return@flow
            emit(ServerEvent.Disconnected)
            delay(wait)
            wait = (wait * 2).coerceAtMost(30_000L)
        }
    }

    private fun connection(): Flow<ServerEvent> = callbackFlow {
        val call = streaming.newCall(
            HttpRequest.Builder()
                .url("${base}api/1/events")
                .header("Authorization", "Bearer $token")
                .header("Accept", "text/event-stream")
                .build(),
        )
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { read(it) }
                    close(IOException("the event stream ended"))
                } catch (e: IOException) {
                    close(e)
                }
            }

            private fun read(response: Response) {
                if (response.code == 401) {
                    trySendBlocking(ServerEvent.Unpaired)
                    close()
                    return
                }
                if (!response.isSuccessful) throw IOException("events answered ${response.code}")
                trySendBlocking(ServerEvent.Connected)
                val source = response.body.source()
                var name: String? = null
                val data = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: return
                    when {
                        line.isEmpty() -> {
                            parseEvent(name, data.toString())?.let { trySendBlocking(it) }
                            name = null
                            data.clear()
                        }
                        line.startsWith(":") -> Unit
                        line.startsWith("event:") -> name = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trimStart())
                        }
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }
}

/** An event this app does not know is ignored, as a newer server may send one. */
internal fun parseEvent(name: String?, data: String): ServerEvent? {
    if (name == null) return null
    return when (name) {
        "library:changed" -> ServerEvent.Changed
        "library:lost" -> ServerEvent.Lost
        "library:renamed" -> {
            val event = runCatching { wire.parseToJsonElement(data).jsonObject }.getOrNull()
            event?.get("name")?.jsonPrimitive?.content?.let { ServerEvent.Renamed(it) }
        }
        "assets:derived" -> {
            val assets = runCatching {
                val event = wire.parseToJsonElement(data).jsonObject
                wire.decodeFromJsonElement(ListSerializer(Redrawn.serializer()), event.getValue("assets"))
            }.getOrNull()
            assets?.let { ServerEvent.Derived(it) }
        }
        else -> null
    }
}

/**
 * Adds the token to a request for the paired server that does not carry one:
 * Coil's pictures and Media3's video, which are built from a URL alone.
 */
class Bearer(private val active: () -> Session?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val session = active()
        if (session == null || !session.owns(request.url) || request.header("Authorization") != null) {
            return chain.proceed(request)
        }
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer ${session.token}").build(),
        )
    }
}

private suspend fun execute(http: OkHttpClient, request: HttpRequest): String =
    withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (response.isSuccessful) return@use text
            val problem = runCatching {
                val body = wire.parseToJsonElement(text).jsonObject.getValue("problem")
                wire.decodeFromJsonElement(Problem.serializer(), body)
            }.getOrElse { Problem("http_${response.code}", "The server answered ${response.code}") }
            throw RemoteException(response.code, problem)
        }
    }
