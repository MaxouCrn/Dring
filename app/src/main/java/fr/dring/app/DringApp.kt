package fr.dring.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class DringApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Supabase.client // initialise le client + restore session
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        getSystemService(NOTIFICATION_SERVICE).let { it as NotificationManager }
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "dring_pokes"
        lateinit var instance: DringApp
            private set

        fun ctx(): Context = instance.applicationContext
    }
}
