package fr.dring.app.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import fr.dring.app.LastReceivedStore
import fr.dring.app.poke.PokeWorker

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 1. Réponse depuis RemoteInput (chip de smart-reply ou texte tapé)
        // 2. Fallback : extra direct (anciennes notifs)
        val message = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(DringMessagingService.KEY_REPLY_TEXT)
            ?.toString()
            ?: intent.getStringExtra(EXTRA_MESSAGE)
            ?: return

        if (message.isBlank()) return

        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        if (notifId != 0) NotificationManagerCompat.from(context).cancel(notifId)
        LastReceivedStore.markHandled(context)

        PokeWorker.enqueue(context, message, isReply = true)
    }

    companion object {
        const val ACTION = "fr.dring.app.REPLY"
        const val EXTRA_MESSAGE = "reply_message"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
