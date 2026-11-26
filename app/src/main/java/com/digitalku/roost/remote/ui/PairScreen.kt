package com.digitalku.roost.remote.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.digitalku.roost.remote.RoostRemote
import com.digitalku.roost.remote.net.CurrentLibrary
import com.digitalku.roost.remote.net.Hello
import com.digitalku.roost.remote.net.LibrarySummary
import com.digitalku.roost.remote.net.Server
import com.digitalku.roost.remote.net.Session
import com.digitalku.roost.remote.net.parseServerAddress
import com.digitalku.roost.remote.store.SavedLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.nullable
import okhttp3.HttpUrl

/** The one version of the API this build speaks (RR-2). */
private const val API = 1

/**
 * Adding a library: the address the window prints, then the library's
 * password, once (RR-4, RR-5).
 *
 * An address that answers with a library this phone is already paired with
 * moves that library there and asks for nothing: the token is the computer's,
 * not the address's, so a laptop the router renumbered still knows the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(
    app: RoostRemote,
    address: String?,
    onBack: () -> Unit,
    onPaired: (SavedLibrary) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var typed by remember { mutableStateOf(address?.let(::hostOf) ?: "") }
    var server by remember { mutableStateOf<Server?>(null) }
    var hello by remember { mutableStateOf<Hello?>(null) }
    val password = rememberTextFieldState()
    var device by remember { mutableStateOf(deviceName(context)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val connect: () -> Unit = connect@{
        val base: HttpUrl = parseServerAddress(typed) ?: run {
            error = "That is not an address. Type it as the computer shows it, like 192.168.1.20:7387."
            return@connect
        }
        busy = true
        error = null
        scope.launch {
            try {
                val candidate = Server(base, app.http)
                val answer = candidate.hello()
                val library = answer.library
                when {
                    answer.api != API -> error =
                        "That computer speaks version ${answer.api} of Roost Remote's protocol, " +
                        "and this app speaks version $API. Update the app."
                    library == null -> error =
                        "Roost on that computer is sharing, but has no library open."
                    else -> {
                        val known = app.libraries.all.value.firstOrNull { it.libraryId == library.id }
                        if (known != null && stillPaired(app, base, known)) {
                            app.libraries.move(known.libraryId, base.toString())
                            onPaired(known.copy(address = base.toString()))
                        } else {
                            server = candidate
                            hello = answer
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = explain(e, hostOf(base.toString()))
            } finally {
                busy = false
            }
        }
    }

    val pair: () -> Unit = pair@{
        val at = server ?: return@pair
        val library = hello?.library ?: return@pair
        val typedPassword = password.text.toString()
        if (typedPassword.isEmpty()) return@pair
        busy = true
        error = null
        scope.launch {
            try {
                val paired = at.pair(typedPassword, device.trim().take(64).ifEmpty { "Android" })
                // RR-6: the password is not kept a moment longer than pairing.
                password.clearText()
                app.libraries.remember(
                    libraryId = library.id,
                    name = library.name,
                    address = at.base.toString(),
                    deviceId = paired.device.id,
                    token = paired.token,
                )
                app.libraries.all.value.firstOrNull { it.libraryId == library.id }?.let(onPaired)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = explain(e, hostOf(at.base.toString()))
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(hello?.library?.name?.let { "Pair with $it" } ?: "Add a library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val library = hello?.library
            if (library == null) {
                Text(
                    "Type the address Roost shows under Share on this network… on the computer.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Address") },
                    placeholder = { Text("192.168.1.20:7387") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (!busy) connect() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = connect, enabled = !busy && typed.isNotBlank()) { Text("Connect") }
            } else {
                Text(
                    "${hostOf(server?.base.toString())} is sharing ${library.name}. Type the library's password to pair this phone. It is " +
                        "asked once, and this phone does not keep it.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedSecureTextField(
                    state = password,
                    label = { Text("Library password") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    onKeyboardAction = { if (!busy) pair() },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = device,
                    onValueChange = { device = it.take(64) },
                    label = { Text("This phone's name, as the computer lists it") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = pair, enabled = !busy && password.text.isNotEmpty()) { Text("Pair") }
                Text(
                    "Plain HTTP: on your own Wi-Fi the password crosses the network unencrypted. " +
                        "Through Tailscale it is encrypted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Whether the token this phone holds still opens the library at [base]. */
private suspend fun stillPaired(app: RoostRemote, base: HttpUrl, library: SavedLibrary): Boolean {
    val token = app.libraries.token(library) ?: return false
    return runCatching {
        Session(base, token, app.http).ask(CurrentLibrary, LibrarySummary.serializer().nullable)
    }.isSuccess
}

/** What the owner called the phone in its settings, or its model. */
private fun deviceName(context: android.content.Context): String =
    Settings.Global.getString(context.contentResolver, "device_name")?.takeIf { it.isNotBlank() }
        ?: Build.MODEL
