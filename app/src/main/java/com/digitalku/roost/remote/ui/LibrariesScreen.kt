package com.digitalku.roost.remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digitalku.roost.remote.store.Libraries
import com.digitalku.roost.remote.store.SavedLibrary
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The libraries this phone is paired with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(
    libraries: Libraries,
    onOpen: (SavedLibrary) -> Unit,
    onAdd: () -> Unit,
    onChangeAddress: (SavedLibrary) -> Unit,
) {
    val all by libraries.all.collectAsState()
    var forgetting by remember { mutableStateOf<SavedLibrary?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Roost Remote") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add a library") },
            )
        },
    ) { padding ->
        if (all.isEmpty()) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text("No library yet", style = MaterialTheme.typography.titleLarge)
                Text(
                    "On the computer, open the library in Roost and choose Share on this " +
                        "network… from the ☰ menu. Then add the address it shows here.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(all, key = { it.libraryId }) { library ->
                    var menu by remember { mutableStateOf(false) }
                    ListItem(
                        modifier = Modifier.clickable { onOpen(library) },
                        supportingContent = { Text(hostOf(library.address)) },
                        trailingContent = {
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Change address…") },
                                        onClick = {
                                            menu = false
                                            onChangeAddress(library)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Forget…") },
                                        onClick = {
                                            menu = false
                                            forgetting = library
                                        },
                                    )
                                }
                            }
                        },
                    ) { Text(library.name) }
                }
            }
        }
    }

    forgetting?.let { library ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("Forget ${library.name}?") },
            text = {
                Text(
                    "This phone forgets its key to the library. The computer still lists the " +
                        "phone until you choose Unpair there, in Share on this network….",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    libraries.forget(library.libraryId)
                    forgetting = null
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Cancel") } },
        )
    }
}

/** `192.168.1.20:7387`, without the scheme and the slash. */
fun hostOf(address: String): String {
    val url = address.toHttpUrlOrNull() ?: return address
    val host = if (':' in url.host) "[${url.host}]" else url.host
    return "$host:${url.port}"
}
