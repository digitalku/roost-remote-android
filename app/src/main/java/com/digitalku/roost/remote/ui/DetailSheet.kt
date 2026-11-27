package com.digitalku.roost.remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.digitalku.roost.remote.net.AddTags
import com.digitalku.roost.remote.net.AddToFolder
import com.digitalku.roost.remote.net.AssetDetail
import com.digitalku.roost.remote.net.RemoveFromFolder
import com.digitalku.roost.remote.net.RemoveTags
import com.digitalku.roost.remote.net.SidebarRow
import com.digitalku.roost.remote.net.WriteNote

/**
 * The inspector, for one asset: what it is, its tags, its folders and its note.
 *
 * Every edit is sent and then read back (`asset:detail` again), never drawn
 * from what was sent: the engine decides what a tag becomes.
 *
 * A tag or folder chip opens a menu: go to it, which [onGo] does in the grid's
 * place, or take it off this file.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailSheet(model: LibraryModel, detail: AssetDetail, onGo: (SidebarRow) -> Unit, onDismiss: () -> Unit) {
    val ids = listOf(detail.id)
    var note by remember(detail.id, detail.note) { mutableStateOf(detail.note ?: "") }
    var choosingFolder by remember { mutableStateOf(false) }
    var choosingTag by remember { mutableStateOf(false) }
    val sidebar = model.sidebar

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
                    val row = sidebar?.tags?.let { tags ->
                        tags.firstOrNull { it.name == name } ?: tags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    }
                    ChipMenu(
                        label = name,
                        icon = Icons.AutoMirrored.Filled.Label,
                        go = "Show files tagged $name",
                        remove = "Remove this tag",
                        onGo = row?.let { { onGo(it) } },
                        onRemove = { model.edit(RemoveTags(ids, listOf(name))) },
                    )
                }
                SuggestionChip(
                    onClick = { choosingTag = true },
                    label = { Text("Add tag…") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }

            Heading("Folders")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (folder in detail.folders) {
                    val row = sidebar?.folders?.firstOrNull { it.folder == folder.id }
                    ChipMenu(
                        label = folder.path,
                        icon = Icons.Filled.Folder,
                        go = "Open this folder",
                        remove = "Take out of this folder",
                        onGo = row?.let { { onGo(it) } },
                        onRemove = { model.edit(RemoveFromFolder(ids, folder.id)) },
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
        FolderPicker(
            folders = sidebar?.folders.orEmpty(),
            chosen = detail.folders.map { it.id }.toSet(),
            onPick = { folder ->
                choosingFolder = false
                model.edit(AddToFolder(ids, folder))
            },
            onDismiss = { choosingFolder = false },
        )
    }

    if (choosingTag) {
        TagPicker(
            tags = sidebar?.tags.orEmpty(),
            has = detail.tags,
            onAdd = { name -> model.edit(AddTags(ids, listOf(name))) },
            onDismiss = { choosingTag = false },
        )
    }
}

/** A chip that opens a menu: go to what it names, or take it off the file. */
@Composable
private fun ChipMenu(
    label: String,
    icon: ImageVector,
    go: String,
    remove: String,
    onGo: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        InputChip(
            selected = false,
            onClick = { open = true },
            label = { Text(label) },
            leadingIcon = {
                Icon(icon, contentDescription = null, modifier = Modifier.size(InputChipDefaults.IconSize))
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onGo != null) {
                DropdownMenuItem(
                    text = { Text(go) },
                    onClick = {
                        open = false
                        onGo()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(remove) },
                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/** The field a picker is searched with. */
@Composable
private fun FindField(value: String, onChange: (String) -> Unit, placeholder: String, onDone: () -> Unit = {}) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Every tag in the library, found by typing, and a new one made from what was
 * typed when no tag is called that. It stays open, so several can be added;
 * one added leaves the list once the file is read back.
 */
@Composable
private fun TagPicker(tags: List<SidebarRow>, has: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val owned = has.map { it.lowercase() }.toSet()
    val offered = pickable(tags, has, typed)
    val name = typed.trim()
    val fresh = name.isNotEmpty() && name.lowercase() !in owned && tags.none { it.name.equals(name, ignoreCase = true) }
    val add = { tag: String ->
        onAdd(tag)
        typed = ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FindField(typed, { typed = it }, "Find or make a tag") {
                    when {
                        fresh -> add(name)
                        offered.size == 1 -> add(offered.first().name)
                    }
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (fresh) {
                        item(key = "fresh") {
                            PickRow(text = "Make the tag \u201c$name\u201d", icon = Icons.Filled.Add) { add(name) }
                        }
                    }
                    items(offered, key = { it.id }) { row ->
                        PickRow(text = row.name, count = row.count) { add(row.name) }
                    }
                    if (offered.isEmpty() && !fresh) {
                        item(key = "none") {
                            Text(
                                if (name.isEmpty()) "There is no other tag yet. Type one to make it." else "This file has that tag.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun PickRow(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Long? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text("%,d".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

/**
 * The folder tree as the pane draws it, found by typing with the tree's shape
 * kept, and the folders this file is already in ticked and not offered.
 */
@Composable
private fun FolderPicker(folders: List<SidebarRow>, chosen: Set<Long>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val shown = treeMatching(folders, typed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (folders.isNotEmpty()) FindField(typed, { typed = it }, "Find a folder")
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.id }) { row ->
                        val already = row.folder in chosen
                        PickRow(
                            text = row.name,
                            icon = if (already) Icons.Filled.Check else Icons.Filled.Folder,
                            enabled = !already,
                            modifier = Modifier.padding(start = (row.depth * 16).dp),
                        ) { row.folder?.let(onPick) }
                    }
                    if (shown.isEmpty()) {
                        item(key = "none") {
                            Text(
                                if (folders.isEmpty()) "This library has no folders." else "No folder is called that.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
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
 * The library's tags a picker offers: none the file has, and those matching
 * what is typed, every tag before a letter is typed, in the library's order.
 */
internal fun pickable(tags: List<SidebarRow>, has: List<String>, typed: String): List<SidebarRow> {
    val owned = has.map { it.lowercase() }.toSet()
    val words = wordsOf(typed)
    return tags.filter { it.name.lowercase() !in owned && matches(it.name, words) }
}
