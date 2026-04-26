package fr.dring.app.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fr.dring.app.DringApp
import fr.dring.app.Identity
import fr.dring.app.LastReceivedStore
import fr.dring.app.MainActivity
import fr.dring.app.R
import fr.dring.app.ReplyPresets
import fr.dring.app.Supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class DringMessagingService : FirebaseMessagingService() {

    @Serializable
    private data class TokenUpdate(val fcm_token: String)

    override fun onNewToken(token: String) {
        val userId = Identity.get(applicationContext)?.id ?: return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                Supabase.client.postgrest.from("profiles")
                    .update(TokenUpdate(token)) { filter { eq("id", userId) } }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // On lit prioritairement le payload `data` (livré dans tous les états de l'app),
        // avec fallback sur `notification` pour compat.
        val title = message.data["title"]
            ?: message.notification?.title
            ?: "Dring 🔔"
        val body = message.data["body"]
            ?: message.notification?.body
            ?: ""
        if (body.isNotBlank()) LastReceivedStore.set(applicationContext, body)
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val notifId = System.currentTimeMillis().toInt()

        val openAppIntent = PendingIntent.getActivity(
            this, notifId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(this, DringApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(0xFFE91E63.toInt())
            .setAutoCancel(true)
            .setContentIntent(openAppIntent)

        // Logo en grande icône
        val logoId = resources.getIdentifier("ic_dring_logo", "drawable", packageName)
        if (logoId != 0) {
            runCatching { BitmapFactory.decodeResource(resources, logoId) }
                .getOrNull()?.let { builder.setLargeIcon(it) }
        }

        // Une seule action "Répondre" avec RemoteInput + choix prédéfinis
        // (Samsung One UI affiche les choices comme des chips de smart-reply)
        val choices = ReplyPresets.forMessage(body).take(5).toTypedArray()
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Répondre à $title")
            .setChoices(choices)
            .build()

        val replyIntent = Intent(this, ReplyReceiver::class.java).apply {
            action = ReplyReceiver.ACTION
            putExtra(ReplyReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val replyPi = PendingIntent.getBroadcast(
            this,
            notifId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_widget_bell, "Répondre", replyPi,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        builder.addAction(replyAction)

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(notifId, builder.build())
    }

    companion object {
        const val KEY_REPLY_TEXT = "key_text_reply"
    }
}
