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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.dring.app.Config
import fr.dring.app.R
import fr.dring.app.poke.PokeWorker
import java.util.concurrent.TimeUnit

class DringWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val state = WidgetStateStore.get(context)
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, state)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) WidgetConfigStore.clearSmall(context, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_POKE) return

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

        val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        val configured = if (widgetId >= 0) WidgetConfigStore.getSmallMessage(context, widgetId) else null
        val message = when {
            configured == null || configured == WidgetConfigStore.RANDOM_VALUE -> Config.POKE_MESSAGES.random()
            else -> configured
        }

        WidgetStateStore.set(context, WidgetState.SENDING)
        refreshAll(context)
        PokeWorker.enqueue(context, message)
    }

    private fun updateWidget(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        state: WidgetState,
    ) {
        manager.updateAppWidget(widgetId, buildViews(context, widgetId, state))
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_POKE = "fr.dring.app.WIDGET_POKE"
        const val EXTRA_WIDGET_ID = "widget_id"

        private fun pokePendingIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, DringWidgetProvider::class.java).apply {
                action = ACTION_POKE
                component = ComponentName(context, DringWidgetProvider::class.java)
                putExtra(EXTRA_WIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(
                context, widgetId, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, DringWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            val state = WidgetStateStore.get(context)
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context, id, state))
            }
        }

        private fun buildViews(
            context: Context,
            widgetId: Int,
            state: WidgetState,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dring)
            val bgRes = when (state) {
                WidgetState.IDLE -> R.drawable.widget_bg_selector
                WidgetState.SENDING -> R.drawable.widget_background_sending
                WidgetState.COOLDOWN -> R.drawable.widget_background_cooldown
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgRes)

            val (emoji, label) = when (state) {
                WidgetState.COOLDOWN -> {
                    val secs = (WidgetStateStore.remainingCooldownMs(context) / 1000) + 1
                    "⏳" to "${secs}s"
                }
                WidgetState.SENDING -> "📡" to "envoi…"
                WidgetState.IDLE -> {
                    val configured = WidgetConfigStore.getSmallMessage(context, widgetId)
                    when {
                        configured == null || configured == WidgetConfigStore.RANDOM_VALUE ->
                            "🎲" to "Aléatoire"
                        else -> WidgetLabels.emojiAndLabel(configured)
                    }
                }
            }
            views.setTextViewText(R.id.widget_emoji, emoji)
            views.setTextViewText(R.id.widget_label, label)

            views.setOnClickPendingIntent(R.id.widget_root, pokePendingIntent(context, widgetId))
            return views
        }

        fun scheduleCooldownReset(
            @Suppress("UNUSED_PARAMETER") context: Context,
            @Suppress("UNUSED_PARAMETER") cooldownMs: Long = WidgetStateStore.COOLDOWN_REGULAR_MS,
        ) {
            val req = OneTimeWorkRequestBuilder<CooldownResetWorker>().build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
