package com.lucent.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lucent.app.AppNavigation
import com.lucent.app.AppScope
import com.lucent.app.Screen
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Note
import com.lucent.app.data.StartupLog
import com.lucent.app.data.TrashCleanup
import kotlinx.coroutines.launch

@Composable
fun HomePanelPage(panel: HomePanel, from: Screen = Screen.Notebooks) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var noteToTrash by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(panel) {
        StartupLog.event(context, "panel: showing ${panel.logKey}")
    }

    val back: () -> Unit = { AppNavigation.requestScreen(Screen.Notebooks) }
    val openNote: (Note) -> Unit = { AppNavigation.openNote(it.id, from = from) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when (panel) {
                HomePanel.Archive -> ArchivedNotesScreen(onBack = back, onOpen = openNote, onDeleteRequest = { noteToTrash = it })
                HomePanel.Trash -> TrashNotesScreen(onBack = back)
                HomePanel.Hidden -> HiddenNotesScreen(onBack = back, onOpen = openNote)
            }
        }
    }

    noteToTrash?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToTrash = null },
            title = { Text(com.lucent.app.i18n.S.moveToTrashTitle) },
            text = {
                Text(com.lucent.app.i18n.S.moveNoteTrashBody(note.title.ifBlank { com.lucent.app.i18n.S.untitledNote }, TrashCleanup.RETENTION_DAYS))
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = note
                    noteToTrash = null
                    AppScope.io.launch {
                        db.noteDao().update(target.copy(trashedAt = System.currentTimeMillis()))
                    }
                }) {
                    Text(com.lucent.app.i18n.S.actionDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToTrash = null }) {
                    Text(com.lucent.app.i18n.S.actionCancel)
                }
            }
        )
    }
}
