package com.digitalku.roost.remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.digitalku.roost.remote.net.Facets
import com.digitalku.roost.remote.net.SidebarRow
import com.digitalku.roost.remote.net.TypeRow

private enum class Facet { Tags, Rating, Type }

/**
 * The window's filter bar (ADR-0035), as a row of chips over the grid: Tags,
 * Rating and Type, each opening a sheet of what to tick. It is a view of the
 * query, not a second state: a tick is sent as values (`query:join`) and the
 * chips are read back out of the query (`query:split`), so the search field
 * shows what the bar did and a query typed there ticks the bar.
 */
@Composable
fun FilterBar(model: LibraryModel) {
    val facets = model.facets
    var open by remember { mutableStateOf<Facet?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarChip(tagsLabel(facets), facets.tags.isNotEmpty()) { open = Facet.Tags }
        BarChip(ratingLabel(facets), facets.stars.isNotEmpty() || facets.unrated) { open = Facet.Rating }
        BarChip(typeLabel(facets), facets.exts.isNotEmpty()) { open = Facet.Type }
        if (facets.active) {
            AssistChip(
                onClick = { model.filter(Facets.NONE.copy(tagRule = facets.tagRule)) },
                label = { Text("Clear") },
                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }
    open?.let { facet ->
        FacetSheet(model, facet, onDismiss = { open = null })
    }
}

@Composable
private fun BarChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

private fun tagsLabel(facets: Facets): String = when (facets.tags.size) {
    0 -> "Tags"
    1 -> facets.tags.first()
    else -> "${facets.tags.size} tags · " + if (facets.tagRule == "all") "all" else "any"
}

private fun ratingLabel(facets: Facets): String {
    val parts = facets.stars.sortedDescending().map { "$it★" } + if (facets.unrated) listOf("unrated") else emptyList()
    return if (parts.isEmpty()) "Rating" else parts.joinToString(", ")
}

private fun typeLabel(facets: Facets): String = when (facets.exts.size) {
    0 -> "Type"
    in 1..2 -> facets.exts.joinToString(", ") { it.uppercase().ifEmpty { "none" } }
    else -> "${facets.exts.size} types"
}

/**
 * One facet's sheet. What is ticked is drawn from a copy held here and sent at
 * every tick, so a tick shows at once rather than after the round trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FacetSheet(model: LibraryModel, facet: Facet, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(model.facets) }
    val change = { next: Facets ->
        draft = next
        model.filter(next)
    }
    // The menus offer what the place holds, read as a menu opens (ADR-0035 §8).
    var offeredTags by remember { mutableStateOf(model.sidebar?.tags.orEmpty()) }
    var offeredTypes by remember { mutableStateOf(model.sidebar?.types.orEmpty()) }
    LaunchedEffect(facet, draft.tagRule, draft.tags, draft.stars, draft.unrated, draft.exts) {
        if (facet == Facet.Rating) return@LaunchedEffect
        val read = model.vocabulary(draft)
        offeredTags = read?.tags ?: model.sidebar?.tags.orEmpty()
        offeredTypes = read?.types ?: model.sidebar?.types.orEmpty()
    }

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (facet) {
                Facet.Tags -> TagsFacet(draft, offeredTags, change)
                Facet.Rating -> RatingFacet(draft, model.sidebar?.ratings.orEmpty(), change)
                Facet.Type -> TypeFacet(draft, offeredTypes, change)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagsFacet(draft: Facets, offered: List<SidebarRow>, change: (Facets) -> Unit) {
    var typed by remember { mutableStateOf("") }
    Text("Tags", style = MaterialTheme.typography.titleLarge)
    // ADR-0035 §7: Any is `(tag:a OR tag:b)`, All is `tag:a tag:b`.
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        listOf("any" to "Any of them", "all" to "All of them").forEachIndexed { index, (rule, label) ->
            SegmentedButton(
                selected = draft.tagRule == rule,
                onClick = { if (draft.tagRule != rule) change(draft.copy(tagRule = rule)) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
            ) { Text(label) }
        }
    }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        placeholder = { Text("Find a tag") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (typed.isNotEmpty()) {
                IconButton(onClick = { typed = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val rows = facetTags(offered, draft.tags, typed)
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(rows, key = { it.first }) { (name, count) ->
            val ticked = draft.tags.any { it.equals(name, ignoreCase = true) }
            TickRow(name, count, ticked) {
                change(
                    draft.copy(
                        tags = if (ticked) draft.tags.filterNot { it.equals(name, ignoreCase = true) } else draft.tags + name,
                    ),
                )
            }
        }
        if (rows.isEmpty()) {
            item(key = "none") {
                Text(
                    if (typed.isBlank()) "Nothing here has a tag." else "No tag is called that.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * The rows the Tags sheet draws: what is ticked first, whether or not the place
 * still offers it, so it can be unticked; then what is offered, found by typing.
 */
internal fun facetTags(offered: List<SidebarRow>, ticked: List<String>, typed: String): List<Pair<String, Long?>> {
    val words = wordsOf(typed)
    val counts = offered.associate { it.name.lowercase() to it.count }
    val first = ticked.map { it to counts[it.lowercase()] }
    val chosen = ticked.map { it.lowercase() }.toSet()
    val rest = offered.filter { it.name.lowercase() !in chosen && matches(it.name, words) }.map { it.name to it.count }
    return first + rest
}

@Composable
private fun RatingFacet(draft: Facets, ratings: List<SidebarRow>, change: (Facets) -> Unit) {
    Text("Rating", style = MaterialTheme.typography.titleLarge)
    val counts = ratings.associate { (it.stars ?: -1) to it.count }
    for (stars in 5 downTo 1) {
        val ticked = stars in draft.stars
        TickRow(null, counts[stars], ticked, label = { StarsLabel(stars) }) {
            change(draft.copy(stars = if (ticked) draft.stars - stars else (draft.stars + stars).sorted()))
        }
    }
    TickRow("Unrated", counts[0], draft.unrated) { change(draft.copy(unrated = !draft.unrated)) }
}

@Composable
private fun StarsLabel(stars: Int) {
    Row {
        repeat(5) { index ->
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = if (index < stars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TypeFacet(draft: Facets, offered: List<TypeRow>, change: (Facets) -> Unit) {
    Text("Type", style = MaterialTheme.typography.titleLarge)
    val counts = offered.associate { it.ext to it.count }
    val rows = draft.exts.map { it to counts[it] } + offered.filter { it.ext !in draft.exts }.map { it.ext to it.count }
    LazyColumn(Modifier.heightIn(max = 480.dp)) {
        items(rows, key = { it.first }) { (ext, count) ->
            val ticked = ext in draft.exts
            TickRow(ext.uppercase().ifEmpty { "No extension" }, count, ticked) {
                change(draft.copy(exts = if (ticked) draft.exts - ext else draft.exts + ext))
            }
        }
    }
}

@Composable
private fun TickRow(
    text: String?,
    count: Long?,
    ticked: Boolean,
    label: (@Composable () -> Unit)? = null,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = ticked, onCheckedChange = { onToggle() })
        Row(Modifier.weight(1f)) {
            if (label != null) label() else Text(text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
        }
        count?.let {
            Text(
                "%,d".format(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
