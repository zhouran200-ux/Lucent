package com.lucent.app.data

import java.time.Instant
import java.time.ZoneId

object MarkdownExport {

    private fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            .format(com.lucent.app.i18n.LDates.of("yyyy-MM-dd HH:mm"))

    private val S get() = com.lucent.app.i18n.S

    fun render(notes: List<Note>): String {
        val live = notes.filter { it.trashedAt == null }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

        val sb = StringBuilder()
        sb.appendLine("# ${S.exportDocNotesTitle}")
        sb.appendLine()
        sb.appendLine("_${S.exportDocNoteCount(live.size)}, ${S.exportDocExportedAt(formatTime(System.currentTimeMillis()))}_")
        sb.appendLine()
        sb.appendLine(S.exportDocAttachmentsNote)
        sb.appendLine()

        if (live.isEmpty()) {
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("_${S.exportDocNoNotes}_")
            return sb.toString()
        }

        live.forEach { note ->
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine("## ${note.title.ifBlank { S.untitled }}")
            sb.appendLine()

            val meta = buildList {
                add(S.exportDocUpdated(formatTime(note.updatedAt)))
                if (note.pinned) add(S.exportDocPinned)
                if (note.archived) add(S.exportDocArchived)
                val tags = NoteTags.parse(note.tags)
                if (tags.isNotEmpty()) add(tags.joinToString(" ") { "#" + NoteTags.label(it) })
            }
            sb.appendLine("_${meta.joinToString(" · ")}_")
            sb.appendLine()

            if (note.isChecklist) {
                val items = Checklist.parse(note.checklist)
                if (items.isEmpty()) {
                    sb.appendLine("_${S.exportDocEmptyChecklist}_")
                } else {
                    sb.appendLine(Checklist.toMarkdown(note.checklist))
                }
                sb.appendLine()
            }
            if (note.body.isNotBlank()) {
                sb.appendLine(note.body.trimEnd())
                sb.appendLine()
            }

            val canvases = DoodleExport.canvasesOf(note)
            if (canvases.isNotEmpty()) {
                sb.appendLine("**${S.exportDocDoodleCanvases(canvases.size)}**")
                sb.appendLine()
                sb.appendLine(S.exportDocDoodleLine(canvases.joinToString(", ") { it.fileName }))
                sb.appendLine()
            }

            val attachments = Attachments.parse(note.attachments)
            if (attachments.isNotEmpty()) {
                sb.appendLine("**${S.exportDocAttachmentsLine(attachments.joinToString(", ") { it.name })}**")
                sb.appendLine()
            }
        }

        return sb.toString()
    }
}
