package com.lucent.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Checklist
import com.lucent.app.data.Note
import com.lucent.app.data.SavedSearches
import com.lucent.app.data.SearchQuery
import com.lucent.app.data.SettingsRepository
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CANDIDATE_LIMIT = 300
private const val RESULT_LIMIT = 100

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onOpenNote: (Note) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current
    val hazeState = LocalHazeState.current

    var raw by remember { mutableStateOf("") }
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val savedJson by repo.savedSearches.collectAsState(initial = "")
    val savedList = remember(savedJson) { SavedSearches.parse(savedJson) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var noteResults by remember { mutableStateOf<List<Note>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { onBack() }

    val query = remember(raw) { SearchQuery.parse(raw) }

    LaunchedEffect(query) {
        if (query.isEmpty) {
            noteResults = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(180)

        noteResults = db.noteDao()
            .searchNotes(
                text = query.sqlText,
                tag = query.sqlTag,
                archived = query.sqlArchived,
                trashed = query.sqlTrashed,
                limit = CANDIDATE_LIMIT
            )
            .filter { query.matches(it) }
            .map { it to query.rank(it) }
            .sortedWith(compareByDescending<Pair<Note, Int>> { it.second }.thenByDescending { it.first.updatedAt })
            .take(RESULT_LIMIT)
            .map { it.first }

        searching = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.lucent.app.i18n.S.actionBack, tint = onGradient)
            }
            Text(com.lucent.app.i18n.S.searchEverything, color = onGradient, fontSize = 20.sp, modifier = Modifier.weight(1f))
            if (raw.isNotBlank()) {
                IconButton(onClick = {
                    saveName = raw.trim().take(24)
                    showSaveDialog = true
                }) {
                    Icon(
                        Icons.Default.BookmarkAdd,
                        contentDescription = com.lucent.app.i18n.S.saveSearchAction,
                        tint = onGradientMuted
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            placeholder = { Text(com.lucent.app.i18n.S.searchPlaceholder, color = onGradientMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = onGradientMuted) },
            trailingIcon = {
                if (raw.isNotEmpty()) {
                    IconButton(onClick = { raw = "" }) {
                        Icon(Icons.Default.Close, contentDescription = com.lucent.app.i18n.S.actionClear, tint = onGradientMuted)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().frostedGlass()
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (savedList.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (item in savedList) {
                    FilterChip(
                        selected = raw == item.query,
                        onClick = { raw = if (raw == item.query) "" else item.query },
                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(item.name, fontSize = 12.sp) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = com.lucent.app.i18n.S.actionRemove,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable {
                                        scope.launch {
                                            repo.setSavedSearches(SavedSearches.remove(savedJson, item.name))
                                        }
                                    }
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val activeTokens = remember(raw) {
            raw.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SearchQuery.HINTS.forEach { token ->
                FilterChip(
                    selected = token in activeTokens,
                    onClick = { raw = toggleSearchToken(raw, token) },
                    label = { Text(searchChipLabel(token), fontSize = 12.sp) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val total = noteResults.size
        when {
            query.isEmpty -> EmptyState(
                isFiltered = false,
                emptyMessage = com.lucent.app.i18n.S.searchEmptyHint,
                noMatchMessage = ""
            )

            searching -> EmptyState(
                isFiltered = false,
                emptyMessage = com.lucent.app.i18n.S.searchingEllipsis,
                noMatchMessage = ""
            )

            total == 0 -> EmptyState(
                isFiltered = true,
                emptyMessage = "",
                noMatchMessage = com.lucent.app.i18n.S.searchNoMatch(raw)
            )

            else -> {
                Text(
                    if (total == 1) com.lucent.app.i18n.S.oneResult else com.lucent.app.i18n.S.nResults(total),
                    color = onGradientMuted,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.hazeSource(state = hazeState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = LocalBottomBarInset.current)
                ) {
                    if (noteResults.isNotEmpty()) {
                        item(key = "notes_header") {
                            SectionHeader(com.lucent.app.i18n.S.tabNotes, noteResults.size, Icons.AutoMirrored.Filled.Notes)
                        }
                        items(noteResults, key = { "n${it.id}" }) { note ->
                            NoteResultRow(note = note, onOpen = { onOpenNote(note) })
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(com.lucent.app.i18n.S.saveSearchAction) },
            text = {
                Column {
                    Text(raw.trim(), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        placeholder = { Text(com.lucent.app.i18n.S.saveSearchNamePlaceholder) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = saveName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            repo.setSavedSearches(SavedSearches.add(savedJson, saveName, raw))
                        }
                        showSaveDialog = false
                    }
                ) { Text(com.lucent.app.i18n.S.actionSave) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(com.lucent.app.i18n.S.actionCancel) }
            }
        )
    }
}

private val WHITESPACE = Regex("\\s+")

internal fun toggleSearchToken(raw: String, token: String): String {
    val parts = raw.split(WHITESPACE).filter { it.isNotBlank() }.toMutableList()
    val idx = parts.indexOf(token)
    if (idx >= 0) {
        parts.removeAt(idx)
    } else {
        parts.add(token)
    }
    return if (parts.isEmpty()) "" else parts.joinToString(" ") + " "
}

private fun searchChipLabel(token: String): String = when (token) {
    "tag:" -> com.lucent.app.i18n.S.searchChipTag
    "is:pinned" -> com.lucent.app.i18n.S.searchChipPinned
    "is:archived" -> com.lucent.app.i18n.S.searchChipArchived
    "is:checklist" -> com.lucent.app.i18n.S.searchChipChecklist
    "has:attachment" -> com.lucent.app.i18n.S.searchChipAttachment
    "link:" -> com.lucent.app.i18n.S.searchChipLink
    else -> token
}

@Composable
internal fun SectionHeader(label: String, count: Int, icon: ImageVector) {
    val onGradientMuted = LocalOnGradientMuted.current
    Row(
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = onGradientMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.padding(start = 6.dp))
        Text(
            "$label ($count)",
            color = onGradientMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun NoteResultRow(note: Note, onOpen: () -> Unit) {
    val onGradient = LocalOnGradient.current
    val onGradientMuted = LocalOnGradientMuted.current

    val state = when {
        note.trashedAt != null -> com.lucent.app.i18n.S.statusInTrash
        note.archived -> com.lucent.app.i18n.S.statusArchived
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .frostedGlass()
            .clickable { onOpen() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) {
                PinnedMarker(modifier = Modifier.padding(end = 4.dp))
            }
            Text(
                note.title.ifBlank { com.lucent.app.i18n.S.untitled },
                color = onGradient,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (state != null) {
                Text(state, color = onGradientMuted, fontSize = 11.sp)
            }
        }
        val preview = if (note.isChecklist) {
            Checklist.parse(note.checklist).take(2).joinToString(" · ") { it.text }
        } else {
            note.body.trim()
        }
        if (preview.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(preview, color = onGradientMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
