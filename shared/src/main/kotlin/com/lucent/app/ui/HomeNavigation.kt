package com.lucent.app.ui

import androidx.compose.runtime.mutableStateOf
import com.lucent.app.Screen

enum class HomePanel {
    Archive, Trash, Hidden;

    val title: String
        get() = when (this) {
            Archive -> com.lucent.app.i18n.S.navArchive
            Trash -> com.lucent.app.i18n.S.screenTrash
            Hidden -> com.lucent.app.i18n.S.screenHidden
        }

    fun label(): String = when (this) {
        Archive -> com.lucent.app.i18n.S.screenArchivedNotes
        else -> title
    }

    val logKey: String
        get() = name.lowercase()

    companion object {
        fun visible(hiddenVisible: Boolean): List<HomePanel> =
            entries.filter { it != Hidden || hiddenVisible }
    }
}

object HomeSearch {
    val query = mutableStateOf("")

    fun submit(q: String) {
        query.value = q.trim()
    }

    fun clear() {
        query.value = ""
    }
}
