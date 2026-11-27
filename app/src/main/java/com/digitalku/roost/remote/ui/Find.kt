package com.digitalku.roost.remote.ui

import com.digitalku.roost.remote.net.SidebarRow
import java.text.Normalizer

/*
 * Finding a folder or a tag by name in the lists the app already holds, the
 * navigation pane's and the pickers'. Nothing here reaches the server: a
 * library's folders and tags are a few hundred rows in memory.
 */

private val marks = Regex("\\p{M}+")
private val between = Regex("[^\\p{L}\\p{N}]+")

/** Lowercased words, marks removed, split at anything not a letter or digit. */
internal fun wordsOf(text: String): List<String> =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(marks, "")
        .lowercase()
        .split(between)
        .filter { it.isNotEmpty() }

/** Every typed word starts a word of [name], as the window's searches match. */
internal fun matches(name: String, typed: List<String>): Boolean {
    if (typed.isEmpty()) return true
    val words = wordsOf(name)
    return typed.all { wanted -> words.any { it.startsWith(wanted) } }
}

/**
 * The rows of a depth-first tree whose names match, each with its ancestors
 * along as context and its own subtree left out unless it matches too: the
 * tree keeps its shape, as the window's folder search does (ADR-0033 §6).
 */
internal fun treeMatching(rows: List<SidebarRow>, typed: String): List<SidebarRow> {
    val words = wordsOf(typed)
    if (words.isEmpty()) return rows
    val keep = BooleanArray(rows.size)
    val above = ArrayList<Int>()
    rows.forEachIndexed { index, row ->
        while (above.isNotEmpty() && rows[above.last()].depth >= row.depth) above.removeAt(above.lastIndex)
        if (matches(row.name, words)) {
            keep[index] = true
            above.forEach { keep[it] = true }
        }
        above.add(index)
    }
    return rows.filterIndexed { index, _ -> keep[index] }
}

/** The folders directly inside [folder], from the pane's depth-first rows. */
internal fun childrenOf(rows: List<SidebarRow>, folder: Long): List<SidebarRow> {
    val at = rows.indexOfFirst { it.folder == folder }
    if (at < 0) return emptyList()
    val depth = rows[at].depth
    return rows.drop(at + 1).takeWhile { it.depth > depth }.filter { it.depth == depth + 1 }
}
