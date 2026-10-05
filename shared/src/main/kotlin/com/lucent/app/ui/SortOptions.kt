package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.lucent.app.data.Note
import com.lucent.app.data.SearchQuery

enum class NoteSort(val key: String) {
    RECENT("recent"),
    OLDEST("oldest"),
    TITLE_AZ("title_az"),
    CUSTOM("custom");

    val label: String
        get() = when (this) {
            RECENT -> com.lucent.app.i18n.S.sortLastEdited
            OLDEST -> com.lucent.app.i18n.S.sortOldestFirst
            TITLE_AZ -> com.lucent.app.i18n.S.sortTitleAz
            CUSTOM -> com.lucent.app.i18n.S.sortCustom
        }

    companion object {
        fun fromKey(key: String?): NoteSort = entries.firstOrNull { it.key == key } ?: RECENT
    }
}

fun List<Note>.sortedForDisplay(sort: NoteSort, query: SearchQuery = SearchQuery()): List<Note> {
    val chosen: Comparator<Note> = when (sort) {
        NoteSort.RECENT -> compareByDescending { it.updatedAt }
        NoteSort.OLDEST -> compareBy { it.updatedAt }
        NoteSort.TITLE_AZ -> compareBy { it.title.lowercase() }
        NoteSort.CUSTOM -> compareBy<Note> { it.manualOrder }.thenByDescending { it.updatedAt }
    }
    val ranked = query.terms.isNotEmpty() || query.phrases.isNotEmpty()
    if (!ranked) {
        return sortedWith(compareByDescending<Note> { it.pinned }.then(chosen))
    }
    val comparator = compareByDescending<Pair<Note, Int>> { it.first.pinned }
        .thenByDescending { it.second }
        .then(compareBy(chosen) { it.first })
    return map { it to query.rank(it) }.sortedWith(comparator).map { it.first }
}

@Composable
fun <T> SortMenuButton(
    current: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    tint: Color,
    activeTint: Color,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isDefault = options.isNotEmpty() && current == options.first()
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = com.lucent.app.i18n.S.sortByA11y(label(current)),
                tint = if (isDefault) tint else activeTint
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            fontWeight = if (option == current) FontWeight.Bold else null
                        )
                    },
                    leadingIcon = if (option == current) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
