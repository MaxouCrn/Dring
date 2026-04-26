package fr.dring.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import android.widget.Toast
import fr.dring.app.R
import fr.dring.app.poke.PokeWorker

class DringLargeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, id))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) WidgetConfigStore.clearLarge(context, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_POKE_LARGE) return

        val state = WidgetStateStore.get(context)
        if (state == WidgetState.SENDING) {
            toast(context, "Envoi en cours…")
            return
        }
        if (state == WidgetState.COOLDOWN) {
            val secs = (WidgetStateStore.remainingCooldownMs(context) / 1000) + 1
            toast(context, "Encore ${secs}s avant le prochain Dring")
            return
        }

        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        WidgetStateStore.set(context, WidgetState.SENDING)
        DringWidgetProvider.refreshAll(context)
        refreshAll(context)
        PokeWorker.enqueue(context, message)
    }

    companion object {
        const val ACTION_POKE_LARGE = "fr.dring.app.WIDGET_POKE_LARGE"
        const val EXTRA_MESSAGE = "message"

        // (viewId emoji, viewId label, viewId clickArea)
        private val SLOT_VIEW_IDS = listOf(
            Triple(R.id.widget_large_emoji_1, R.id.widget_large_label_1, R.id.widget_large_btn_1),
            Triple(R.id.widget_large_emoji_2, R.id.widget_large_label_2, R.id.widget_large_btn_2),
            Triple(R.id.widget_large_emoji_3, R.id.widget_large_label_3, R.id.widget_large_btn_3),
            Triple(R.id.widget_large_emoji_4, R.id.widget_large_label_4, R.id.widget_large_btn_4),
            Triple(R.id.widget_large_emoji_5, R.id.widget_large_label_5, R.id.widget_large_btn_5),
            Triple(R.id.widget_large_emoji_6, R.id.widget_large_label_6, R.id.widget_large_btn_6),
            Triple(R.id.widget_large_emoji_7, R.id.widget_large_label_7, R.id.widget_large_btn_7),
            Triple(R.id.widget_large_emoji_8, R.id.widget_large_label_8, R.id.widget_large_btn_8),
        )

        private fun buildViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dring_large)

            val state = WidgetStateStore.get(context)
            val bgRes = when (state) {
                WidgetState.IDLE -> R.drawable.widget_background
                WidgetState.SENDING -> R.drawable.widget_background_sending
                WidgetState.COOLDOWN -> R.drawable.widget_background_cooldown
            }
            views.setInt(R.id.widget_root_large, "setBackgroundResource", bgRes)

            val statusText = when (state) {
                WidgetState.IDLE -> ""
                WidgetState.SENDING -> "📡 envoi…"
                WidgetState.COOLDOWN -> {
                    val secs = (WidgetStateStore.remainingCooldownMs(context) / 1000) + 1
                    "⏳ ${secs}s"
                }
            }
            views.setTextViewText(R.id.widget_large_status, statusText)
            views.setInt(
                R.id.widget_large_status,
                "setVisibility",
                if (state == WidgetState.IDLE) android.view.View.GONE else android.view.View.VISIBLE,
            )

            val slots = WidgetConfigStore.getLargeSlots(context, widgetId)
            slots.forEachIndexed { i, body ->
                val (emojiId, labelId, btnId) = SLOT_VIEW_IDS[i]
                val (emoji, label) = WidgetLabels.emojiAndLabel(body)
                views.setTextViewText(emojiId, emoji)
                views.setTextViewText(labelId, label)

                val intent = Intent(context, DringLargeWidgetProvider::class.java).apply {
                    action = ACTION_POKE_LARGE
                    component = ComponentName(context, DringLargeWidgetProvider::class.java)
                    putExtra(EXTRA_MESSAGE, body)
                }
                val pi = PendingIntent.getBroadcast(
                    context, widgetId * 100 + i, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                views.setOnClickPendingIntent(btnId, pi)
            }
            return views
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DringLargeWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context, id))
            }
        }

        private fun toast(ctx: Context, text: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
