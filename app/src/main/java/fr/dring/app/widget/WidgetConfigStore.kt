package fr.dring.app.widget

import android.content.Context
import androidx.core.content.edit
import fr.dring.app.Config

/**
 * Stockage de la config par widgetId.
 * - Petit widget (1x1) : 1 message (ou RANDOM).
 * - Grand widget (4x2) : 8 messages (slots 0..7).
 */
object WidgetConfigStore {
    private const val PREFS = "dring_widget_config"
    const val RANDOM_VALUE = "__RANDOM__"

    // ---- 1x1 ----

    private fun smallKey(widgetId: Int) = "small_${widgetId}_msg"

    /** Renvoie le message configuré, ou null si pas configuré (= défaut RANDOM). */
    fun getSmallMessage(ctx: Context, widgetId: Int): String? =
        ctx.prefs().getString(smallKey(widgetId), null)

    /** value = body complet ("❤️ Je t'aime") ou RANDOM_VALUE. */
    fun setSmallMessage(ctx: Context, widgetId: Int, value: String) {
        ctx.prefs().edit { putString(smallKey(widgetId), value) }
    }

    fun clearSmall(ctx: Context, widgetId: Int) {
        ctx.prefs().edit { remove(smallKey(widgetId)) }
    }

    // ---- 4x2 ----

    private fun largeKey(widgetId: Int, slot: Int) = "large_${widgetId}_slot_${slot}"

    fun getLargeSlot(ctx: Context, widgetId: Int, slot: Int): String? =
        ctx.prefs().getString(largeKey(widgetId, slot), null)

    fun setLargeSlot(ctx: Context, widgetId: Int, slot: Int, body: String) {
        ctx.prefs().edit { putString(largeKey(widgetId, slot), body) }
    }

    /** Renvoie les 8 messages, en utilisant les défauts si non configuré. */
    fun getLargeSlots(ctx: Context, widgetId: Int): List<String> {
        return DEFAULT_LARGE_SLOTS.mapIndexed { i, default ->
            getLargeSlot(ctx, widgetId, i) ?: default
        }
    }

    fun clearLarge(ctx: Context, widgetId: Int) {
        ctx.prefs().edit {
            for (i in 0..7) remove(largeKey(widgetId, i))
        }
    }

    /** Layout par défaut du grand widget (avant config). */
    val DEFAULT_LARGE_SLOTS = listOf(
        "❤️ Je t'aime",
        "🤗 Je veux un câlin",
        "😘 Je veux un bisou",
        "🔥 J'ai envie de toi",
        "🥺 Tu me manques",
        "💭 Je pense à toi",
        "🍝 On mange quoi ?",
        "🚗 Je rentre",
    )

    /** Toutes les options sélectionnables (= QUICK_MESSAGES bodies). */
    fun allOptions(): List<String> = Config.QUICK_MESSAGES.map { it.body() }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
