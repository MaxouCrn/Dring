package fr.dring.app

import android.content.Context
import androidx.core.content.edit

object Identity {
    private const val PREFS = "dring_prefs"
    private const val KEY_USER_ID = "user_id"

    fun get(ctx: Context): Config.User? {
        val id = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USER_ID, null) ?: return null
        return Config.ALL_USERS.firstOrNull { it.id == id }
    }

    fun set(ctx: Context, user: Config.User) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_USER_ID, user.id)
        }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(KEY_USER_ID)
        }
    }
}
