package com.digitalku.roost.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.digitalku.roost.remote.net.AssetCard
import com.digitalku.roost.remote.net.Session
import com.digitalku.roost.remote.net.Sidebar
import com.digitalku.roost.remote.net.SidebarRow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * One library: the grid, the navigation pane in a drawer, and the search field
 * in the bar, which holds the query as the window's does (RQL, the whole of
 * it). A row of the pane replaces the query with the row's own; the app writes
 * no query of its own.
 */
@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun LibraryScreen(model: LibraryModel, onBack: () -> Unit, onPairAgain: () -> Unit) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }
    var viewing by remember { mutableStateOf<Int?>(null) }
    var searching by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = searching && viewing == null) { searching = false }

    val show = { query: String, title: String ->
        model.show(query, title)
        scope.launch { grid.scrollToItem(0) }
        Unit
    }

    model.notice?.let { notice ->
        LaunchedEffect(notice) {
            val result = snackbar.showSnackbar(
                notice.text,
                notice.action,
                duration = if (notice.action == null) SnackbarDuration.Short else SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) notice.onAction?.invoke()
            if (model.notice === notice) model.notice = null
        }
    }

    LaunchedEffect(grid) {
        snapshotFlow {
            val info = grid.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (last, total) -> if (total > 0 && last >= total - 40) model.more() }
    }

    LaunchedEffect(grid) {
        snapshotFlow { model.link to grid.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .distinctUntilChanged()
            .debounce(400)
            .collect { (_, ids) -> model.drawOnScreen(ids) }
    }

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = viewing == null,
            drawerContent = {
                Pane(model.name, model.address, model.sidebar, model.query) { row ->
                    searching = false
                    show(row.query, row.name)
                    scope.launch { drawer.close() }
                }
            },
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawer.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Folders and tags")
                            }
                        },
                        title = {
                            if (searching) {
                                SearchField(typed, { typed = it }) {
                                    val query = typed.trim()
                                    show(query, query.ifEmpty { "All assets" })
                                }
                            } else {
                                Column {
                                    Text(model.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        subtitle(model.name, model.count),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        actions = {
                            if (searching) {
                                IconButton(onClick = { searching = false }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                                }
                            } else {
                                IconButton(onClick = {
                                    typed = model.query
                                    searching = true
                                }) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                            }
                        },
                    )
                },
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Banner(model, onBack)
                    model.problem?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(104.dp),
                            state = grid,
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(model.cards, key = { _, card -> card.id }) { index, card ->
                                Cell(card, model.session) { viewing = index }
                            }
                        }
                        if (model.cards.isEmpty()) {
                            if (model.loading) {
                                CircularProgressIndicator(Modifier.align(Alignment.Center))
                            } else if (model.problem == null && model.link == Link.Live) {
                                Text(
                                    "Nothing here.",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        viewing?.let { start ->
            AssetViewer(model, start) { last ->
                viewing = null
                val visible = grid.layoutInfo.visibleItemsInfo.map { it.index }
                if (last !in visible) scope.launch { grid.scrollToItem(last) }
            }
        }

        // Over the viewer as well as the grid: Undo for a trash sent from the
        // viewer has to be where the owner is looking, clear of its stars.
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (viewing != null) 64.dp else 0.dp),
        )
    }

    if (model.link == Link.Unpaired) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("This phone was unpaired") },
            text = {
                Text(
                    "${model.name} no longer accepts this phone's key: it was unpaired on the " +
                        "computer, or the library's password changed. Pair again with the password.",
                )
            },
            confirmButton = { TextButton(onClick = onPairAgain) { Text("Pair again") } },
            dismissButton = { TextButton(onClick = onBack) { Text("Back") } },
        )
    }
}

private fun subtitle(name: String, count: Long?): String = when (count) {
    null -> name
    1L -> "$name · 1 item"
    else -> "$name · ${"%,d".format(count)} items"
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Search, or tag:name") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            onSearch()
        }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** How the connection stands, said once above the grid. */
@Composable
private fun Banner(model: LibraryModel, onBack: () -> Unit) {
    when (model.link) {
        Link.Connecting -> LinearProgressIndicator(Modifier.fillMaxWidth())
        Link.Offline -> BannerLine("Not connected to ${model.address}. Trying again…")
        Link.Lost -> BannerLine("Roost on the computer closed ${model.name}.") {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Link.Live, Link.Unpaired -> Unit
    }
}

@Composable
private fun BannerLine(text: String, action: (@Composable () -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

@Composable
private fun Cell(card: AssetCard, session: Session, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Under the picture, so that a file with no thumbnail yet still says
        // what it is (a 404 is "not drawn yet", RR-14).
        Text(
            card.ext.uppercase().ifEmpty { "FILE" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AsyncImage(
            model = session.thumbnail(card),
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        if (card.kind == "video") {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp),
            )
        }
    }
}

/** The window's navigation pane: places, folders, smart folders, tags. */
@Composable
private fun Pane(
    name: String,
    address: String,
    sidebar: Sidebar?,
    query: String,
    onPick: (SidebarRow) -> Unit,
) {
    ModalDrawerSheet {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
                    Text(name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (sidebar == null) return@LazyColumn
            items(sidebar.places, key = { "place:${it.id}" }) { PaneRow(it, query, onPick) }
            section("Folders", sidebar.folders, query, onPick)
            section("Smart folders", sidebar.smartFolders, query, onPick)
            section("Tags", sidebar.tags, query, onPick)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    rows: List<SidebarRow>,
    query: String,
    onPick: (SidebarRow) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "title:$title") {
        HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
    }
    items(rows, key = { "$title:${it.id}" }) { PaneRow(it, query, onPick) }
}

@Composable
private fun PaneRow(row: SidebarRow, query: String, onPick: (SidebarRow) -> Unit) {
    NavigationDrawerItem(
        label = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        selected = row.query == query,
        onClick = { onPick(row) },
        badge = { row.count?.let { Text("%,d".format(it)) } },
        modifier = Modifier.padding(start = 12.dp + (row.depth * 16).dp, end = 12.dp),
    )
}
