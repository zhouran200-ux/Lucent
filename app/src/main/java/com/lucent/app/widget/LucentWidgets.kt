@file:Suppress("DEPRECATION")

package com.lucent.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lucent.app.AppScope
import com.lucent.app.R
import com.lucent.app.data.AppDatabase
import com.lucent.app.data.Checklist
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking



private const val WIDE_MIN_DP = 176
private const val MEDIUM_MIN_DP = 100

private fun sizeBucketLayout(options: Bundle?, small: Int, medium: Int, wide: Int): Int {
    val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
    val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
    return when {
        w >= WIDE_MIN_DP -> wide
        w >= MEDIUM_MIN_DP || h >= MEDIUM_MIN_DP -> medium
        else -> small
    }
}

abstract class ResponsiveActionWidget(
    private val small: Int,
    private val medium: Int,
    private val wide: Int,
    private val action: String
) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val layout = sizeBucketLayout(manager.getAppWidgetOptions(id), small, medium, wide)
        val views = RemoteViews(context.packageName, layout)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetActions.pendingIntent(context, action))
        manager.updateAppWidget(id, views)
    }
}

class NewNoteWidget : ResponsiveActionWidget(
    R.layout.widget_new_note_small, R.layout.widget_new_note, R.layout.widget_new_note_wide,
    WidgetActions.NEW_NOTE
)

class QuickActionsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            views.setOnClickPendingIntent(R.id.cell_note, WidgetActions.pendingIntent(context, WidgetActions.NEW_NOTE))
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

class PinnedNoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val note = try {
            runBlocking {
                AppDatabase.getInstance(context).noteDao().getAllOnce()
                    .filter { it.pinned && !it.archived && it.trashedAt == null }
                    .maxByOrNull { it.updatedAt }
            }
        } catch (t: Throwable) {
            null
        }

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_pinned_note).apply {
                if (note == null) {
                    setTextViewText(R.id.widget_pinned_title, context.getString(R.string.widget_no_pinned_note))
                    setViewVisibility(R.id.widget_pinned_body, android.view.View.GONE)
                    setOnClickPendingIntent(R.id.widget_pinned_root, WidgetActions.pendingIntent(context, WidgetActions.NEW_NOTE))
                } else {
                    val preview = if (note.isChecklist) {
                        Checklist.parse(note.checklist).take(3).joinToString("\n") { "• " + it.text }
                    } else {
                        note.body
                    }
                    setTextViewText(R.id.widget_pinned_title, note.title.ifBlank { context.getString(R.string.widget_untitled) })
                    if (preview.isBlank()) {
                        setViewVisibility(R.id.widget_pinned_body, android.view.View.GONE)
                    } else {
                        setViewVisibility(R.id.widget_pinned_body, android.view.View.VISIBLE)
                        setTextViewText(R.id.widget_pinned_body, preview)
                    }
                    setOnClickPendingIntent(
                        R.id.widget_pinned_root,
                        WidgetActions.itemPendingIntent(context, WidgetActions.OPEN_NOTE_ITEM, note.id)
                    )
                }
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

object WidgetUpdater {
    fun refreshContent(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val appContext = context.applicationContext

        val pinnedIds = manager.getAppWidgetIds(ComponentName(appContext, PinnedNoteWidget::class.java))
        if (pinnedIds.isNotEmpty()) {
            appContext.sendBroadcast(
                Intent(appContext, PinnedNoteWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, pinnedIds)
                }
            )
        }
    }
}
