package fr.dring.app

import android.content.Context
import androidx.core.content.edit

data class LastReceived(
    val message: String,
    val receivedAt: Long,
    val handled: Boolean,
)

object LastReceivedStore {
    private const val PREFS = "dring_received"
    private const val KEY_MESSAGE = "message"
    private const val KEY_RECEIVED_AT = "received_at"
    private const val KEY_HANDLED = "handled"

    /** Délai au-delà duquel un message reçu n'est plus considéré comme "à répondre". */
    const val FRESH_WINDOW_MS = 30 * 60 * 1000L // 30 min

    fun set(ctx: Context, message: String) {
        ctx.prefs().edit {
            putString(KEY_MESSAGE, message)
            putLong(KEY_RECEIVED_AT, System.currentTimeMillis())
            putBoolean(KEY_HANDLED, false)
        }
    }

    fun get(ctx: Context): LastReceived? {
        val msg = ctx.prefs().getString(KEY_MESSAGE, null) ?: return null
        return LastReceived(
            message = msg,
            receivedAt = ctx.prefs().getLong(KEY_RECEIVED_AT, 0L),
            handled = ctx.prefs().getBoolean(KEY_HANDLED, true),
        )
    }

    fun markHandled(ctx: Context) {
        ctx.prefs().edit { putBoolean(KEY_HANDLED, true) }
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
