package com.digitalku.roost.remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.digitalku.roost.remote.net.AddTags
import com.digitalku.roost.remote.net.AddToFolder
import com.digitalku.roost.remote.net.AssetDetail
import com.digitalku.roost.remote.net.RemoveFromFolder
import com.digitalku.roost.remote.net.RemoveTags
import com.digitalku.roost.remote.net.SidebarRow
import com.digitalku.roost.remote.net.WriteNote

/** At most this many tags offered under the field as it is typed in. */
private const val SUGGESTIONS = 8

/**
 * The inspector, for one asset: what it is, its tags, its folders and its note.
 *
 * Every edit is sent and then read back (`asset:detail` again), never drawn
 * from what was sent: the engine decides what a tag becomes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailSheet(model: LibraryModel, detail: AssetDetail, onDismiss: () -> Unit) {
    val ids = listOf(detail.id)
    var tag by remember { mutableStateOf("") }
    var note by remember(detail.id, detail.note) { mutableStateOf(detail.note ?: "") }
    var choosingFolder by remember { mutableStateOf(false) }

    // Opened all the way, never half: half a sheet leaves the tag field under
    // the keyboard, with nothing to scroll it into view.
    val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(detail.name, style = MaterialTheme.typography.titleLarge)
            Text(
                facts(detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Stars(detail.rating, tint = MaterialTheme.colorScheme.onSurfaceVariant) { stars ->
                model.edit(com.digitalku.roost.remote.net.Rate(ids, stars))
            }

            Heading("Tags")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (name in detail.tags) {
                    InputChip(
                        selected = false,
                        onClick = { model.edit(RemoveTags(ids, listOf(name))) },
                        label = { Text(name) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove $name",
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
            val add = {
                val name = tag.trim()
                if (name.isNotEmpty()) model.edit(AddTags(ids, listOf(name)))
                tag = ""
            }
            OutlinedTextField(
                value = tag,
                onValueChange = { tag = it },
                label = { Text("Add a tag") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.fillMaxWidth(),
            )
            val offered = suggestions(model.sidebar?.tags.orEmpty(), detail.tags, tag)
            if (offered.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (name in offered) {
                        SuggestionChip(
                            onClick = {
                                model.edit(AddTags(ids, listOf(name)))
                                tag = ""
                            },
                            label = { Text(name) },
                        )
                    }
                }
            }

            Heading("Folders")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (folder in detail.folders) {
                    InputChip(
                        selected = false,
                        onClick = { model.edit(RemoveFromFolder(ids, folder.id)) },
                        label = { Text(folder.path) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Take out of ${folder.path}",
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
                SuggestionChip(
                    onClick = { choosingFolder = true },
                    label = { Text("Add to folder…") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }

            Heading("Note")
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (note != (detail.note ?: "")) {
                TextButton(onClick = { model.edit(WriteNote(ids, note.ifBlank { null }), "Note saved") }) {
                    Text("Save note")
                }
            }

            detail.sourceUrl?.let {
                Heading("Source")
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (choosingFolder) {
        val chosen = detail.folders.map { it.id }.toSet()
        FolderPicker(
            folders = model.sidebar?.folders.orEmpty().filter { it.folder != null && it.folder !in chosen },
            onPick = { folder ->
                choosingFolder = false
                model.edit(AddToFolder(ids, folder))
            },
            onDismiss = { choosingFolder = false },
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** The folder tree as the pane draws it, indented, with those it is in left out. */
@Composable
private fun FolderPicker(folders: List<SidebarRow>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to folder") },
        text = {
            if (folders.isEmpty()) {
                Text("There is no other folder.")
            } else {
                LazyColumn {
                    items(folders, key = { it.id }) { row ->
                        Text(
                            row.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { row.folder?.let(onPick) }
                                .padding(start = (row.depth * 16).dp, top = 12.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** JPG · 4.2 MB · 4032 × 3024 · 1:05. */
private fun facts(detail: AssetDetail): String = buildList {
    add(detail.ext.uppercase().ifEmpty { detail.kind })
    detail.size?.let { add(bytes(it)) }
    if (detail.width != null && detail.height != null) add("${detail.width} × ${detail.height}")
    detail.durationMs?.let { add(duration(it)) }
    detail.pageCount?.let { add(if (it == 1) "1 page" else "$it pages") }
    if (detail.trashedAt != null) add("in the trash")
}.joinToString(" · ")

/**
 * The library's tags that start a word with what is typed, commonest first, and
 * none the asset has. Nothing is offered before a letter is typed: a library can
 * hold thousands.
 */
internal fun suggestions(tags: List<SidebarRow>, has: List<String>, typed: String): List<String> {
    val needle = typed.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    val owned = has.map { it.lowercase() }.toSet()
    return tags
        .asSequence()
        .filter { it.name.lowercase() !in owned }
        .filter { row -> row.name.lowercase().split(' ', '-', '_', '/').any { it.startsWith(needle) } }
        .sortedByDescending { it.count ?: 0 }
        .map { it.name }
        .take(SUGGESTIONS)
        .toList()
}
