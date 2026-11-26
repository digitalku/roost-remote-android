package com.digitalku.roost.remote.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.digitalku.roost.remote.RoostRemote
import com.digitalku.roost.remote.net.AssetCard
import com.digitalku.roost.remote.net.AssetDetail
import com.digitalku.roost.remote.net.AssetPage
import com.digitalku.roost.remote.net.CountAssets
import com.digitalku.roost.remote.net.Cursor
import com.digitalku.roost.remote.net.DrawOnScreen
import com.digitalku.roost.remote.net.ReadDetail
import com.digitalku.roost.remote.net.ReadPage
import com.digitalku.roost.remote.net.ReadSidebar
import com.digitalku.roost.remote.net.RemoteException
import com.digitalku.roost.remote.net.Request
import com.digitalku.roost.remote.net.Restore
import com.digitalku.roost.remote.net.ServerEvent
import com.digitalku.roost.remote.net.Session
import com.digitalku.roost.remote.net.Sidebar
import com.digitalku.roost.remote.net.Trash
import com.digitalku.roost.remote.store.SavedLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl

/** A screen of grid with room to scroll into, as the window asks for. */
private const val PAGE = 120

/** The contract's `MAX_PAGE_LIMIT`. */
private const val MAX_PAGE = 10_000

/** How the connection to the library stands, for the banner over the grid. */
enum class Link { Connecting, Live, Offline, Lost, Unpaired }

/** Something to say in the snackbar, with one action at most. */
data class Notice(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/**
 * One open library: what the grid holds, the navigation pane, and the event
 * stream that keeps both current (RR-12).
 *
 * Everything here is re-read rather than patched after an edit, as the window
 * does: an edit answers `null` (`asset:tagAdd` and the rest), and the engine is
 * what normalises a tag or drops an empty note. The one exception is
 * `assets:derived`, which says exactly which cards have new pictures.
 */
class LibraryModel(
    private val app: RoostRemote,
    private val saved: SavedLibrary,
    token: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val session = Session(saved.address.toHttpUrl(), token, app.http)
    val address: String = hostOf(saved.address)

    /** The one client, for Media3: its token comes from the interceptor. */
    val http get() = app.http

    var name by mutableStateOf(saved.name)
        private set
    var link by mutableStateOf(Link.Connecting)
        private set

    /** The query on screen, RQL as the window's search field holds it. */
    var query by mutableStateOf("")
        private set

    /** What the title says: the row that was chosen, or the query typed. */
    var title by mutableStateOf("All assets")
        private set
    var cards by mutableStateOf<List<AssetCard>>(emptyList())
        private set
    var count by mutableStateOf<Long?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var problem by mutableStateOf<String?>(null)
        private set
    var sidebar by mutableStateOf<Sidebar?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)

    /**
     * Read details, by id. A stale one stays until its fresh copy arrives, so a
     * sheet drawn from it does not vanish for the length of a round trip.
     */
    val details = mutableStateMapOf<String, AssetDetail>()

    /** The [detailVersion] each detail was read at. */
    private val readAt = mutableMapOf<String, Int>()

    /** Moves whenever a detail may be stale, so a viewer reads it again. */
    var detailVersion by mutableIntStateOf(0)
        private set

    private var next: Cursor? = null
    private var generation = 0
    private var paging: Job? = null
    private var changed: Job? = null

    init {
        app.session = session
        scope.launch { session.events().collect(::on) }
    }

    fun close() {
        if (app.session === session) app.session = null
        scope.cancel()
    }

    /** A row of the navigation pane, or a query typed into the search field. */
    fun show(query: String, title: String) {
        this.query = query
        this.title = title
        cards = emptyList()
        count = null
        reload(keep = 0)
    }

    /** The next page, when the grid nears the end of what it holds. */
    fun more() {
        val after = next ?: return
        if (loading) return
        val generation = generation
        loading = true
        paging = scope.launch {
            try {
                val page = session.ask(ReadPage(query, PAGE, after, null), AssetPage.serializer())
                if (generation != this@LibraryModel.generation) return@launch
                cards = cards + page.assets
                next = page.next
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            } finally {
                if (generation == this@LibraryModel.generation) loading = false
            }
        }
    }

    /** RR-11: the thumbnails on screen are drawn, and no more than those. */
    fun drawOnScreen(ids: List<String>) {
        if (ids.isEmpty() || link != Link.Live) return
        scope.launch { runCatching { session.tell(DrawOnScreen(ids)) } }
    }

    /** Reads a detail unless it has been read since the library last changed. */
    fun want(id: String) {
        val version = detailVersion
        if (readAt[id] == version) return
        readAt[id] = version
        scope.launch {
            try {
                val detail = session.ask(ReadDetail(id), AssetDetail.serializer().nullable)
                if (version != detailVersion) return@launch
                if (detail == null) details.remove(id) else details[id] = detail
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                readAt.remove(id)
                failed(e)
            }
        }
    }

    /** An edit, and the detail read back after it rather than trusted. */
    fun edit(request: Request, done: String? = null) {
        scope.launch {
            try {
                session.tell(request)
                stale()
                if (done != null) notice = Notice(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }

    /** The reversible delete (RR-10), with Undo beside it. */
    fun trash(ids: List<String>) {
        scope.launch {
            try {
                session.tell(Trash(ids))
                stale()
                val what = if (ids.size == 1) "Moved to the trash" else "${ids.size} moved to the trash"
                notice = Notice(what, "Undo") { edit(Restore(ids)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }

    private fun on(event: ServerEvent) {
        when (event) {
            // RR-12: what was sent while the stream was down is not repeated.
            ServerEvent.Connected -> {
                link = Link.Live
                refresh()
            }
            ServerEvent.Disconnected ->
                if (link == Link.Live || link == Link.Connecting) link = Link.Offline
            ServerEvent.Changed -> {
                // A burst of edits is one re-read, not one each.
                changed?.cancel()
                changed = scope.launch {
                    delay(250)
                    refresh()
                }
            }
            is ServerEvent.Derived -> {
                // RR-14: the address moves with `derivedAt`, so a patched card
                // loads its new picture and an unpatched one keeps its cache.
                val redrawn = event.assets.associateBy { it.id }
                cards = cards.map { card ->
                    val now = redrawn[card.id] ?: return@map card
                    card.copy(
                        derivedAt = now.derivedAt ?: card.derivedAt,
                        undrawn = now.undrawn,
                        chosenThumbnail = now.chosen,
                        width = now.width ?: card.width,
                        height = now.height ?: card.height,
                    )
                }
                event.assets.forEach { readAt.remove(it.id) }
            }
            is ServerEvent.Renamed -> {
                name = event.name
                app.libraries.rename(saved.libraryId, event.name)
            }
            ServerEvent.Lost -> link = Link.Lost
            ServerEvent.Unpaired -> unpaired()
        }
    }

    private fun refresh() {
        stale()
        reload(keep = cards.size)
        scope.launch {
            try {
                sidebar = session.ask(ReadSidebar, Sidebar.serializer())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            }
        }
    }

    private fun stale() {
        detailVersion++
    }

    /**
     * The listing from the start, as many rows as are on hand, so that a re-read
     * after a change does not throw the owner back to the top of the grid.
     */
    private fun reload(keep: Int) {
        val generation = ++generation
        paging?.cancel()
        loading = true
        scope.launch {
            try {
                val limit = maxOf(PAGE, keep).coerceAtMost(MAX_PAGE)
                val page = session.ask(ReadPage(query, limit, null, null), AssetPage.serializer())
                if (generation != this@LibraryModel.generation) return@launch
                cards = page.assets
                next = page.next
                problem = null
                count = session.ask(CountAssets(query), Long.serializer())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == this@LibraryModel.generation) failed(e)
            } finally {
                if (generation == this@LibraryModel.generation) loading = false
            }
        }
    }

    private fun failed(error: Exception) {
        if (error is RemoteException && error.status == 401) {
            unpaired()
            return
        }
        if (error is RemoteException && error.status == 200) {
            // The engine refused: a query that does not parse, an asset gone.
            problem = error.problem.message
            return
        }
        if (link == Link.Live) link = Link.Offline
        problem = explain(error, address)
    }

    /** RR-7: the token is forgotten, and the screen offers to pair again. */
    private fun unpaired() {
        app.libraries.forget(saved.libraryId)
        link = Link.Unpaired
    }
}
