package fr.dring.app.widget

import android.content.Context
import androidx.core.content.edit

enum class WidgetState { IDLE, SENDING, COOLDOWN }

object WidgetStateStore {
    private const val PREFS = "dring_widget"
    private const val KEY_STATE = "state"
    private const val KEY_LAST_POKE_AT = "last_poke_at"
    private const val KEY_COOLDOWN_MS = "cooldown_ms"

    /** Cooldowns par défaut. */
    const val COOLDOWN_REGULAR_MS = 5_000L
    const val COOLDOWN_REPLY_MS = 2_000L

    fun get(ctx: Context): WidgetState {
        val raw = ctx.prefs().getString(KEY_STATE, WidgetState.IDLE.name) ?: WidgetState.IDLE.name
        val state = runCatching { WidgetState.valueOf(raw) }.getOrDefault(WidgetState.IDLE)
        if (state == WidgetState.COOLDOWN && remainingCooldownMs(ctx) <= 0) {
            set(ctx, WidgetState.IDLE)
            return WidgetState.IDLE
        }
        return state
    }

    fun set(ctx: Context, state: WidgetState) {
        ctx.prefs().edit { putString(KEY_STATE, state.name) }
    }

    /** Marque qu'un poke vient d'être envoyé avec une durée de cooldown spécifique. */
    fun markPokeSent(ctx: Context, cooldownMs: Long = COOLDOWN_REGULAR_MS) {
        ctx.prefs().edit {
            putLong(KEY_LAST_POKE_AT, System.currentTimeMillis())
            putLong(KEY_COOLDOWN_MS, cooldownMs)
            putString(KEY_STATE, WidgetState.COOLDOWN.name)
        }
    }

    fun remainingCooldownMs(ctx: Context): Long {
        val last = ctx.prefs().getLong(KEY_LAST_POKE_AT, 0L)
        if (last == 0L) return 0L
        val cooldown = ctx.prefs().getLong(KEY_COOLDOWN_MS, COOLDOWN_REGULAR_MS)
        val elapsed = System.currentTimeMillis() - last
        return (cooldown - elapsed).coerceAtLeast(0L)
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
