package fr.dring.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

/**
 * Tick toutes les secondes pendant le cooldown pour rafraîchir le countdown
 * affiché sur les widgets, puis remet l'état IDLE quand c'est fini.
 */
class CooldownResetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        while (WidgetStateStore.remainingCooldownMs(applicationContext) > 0) {
            DringWidgetProvider.refreshAll(applicationContext)
            DringLargeWidgetProvider.refreshAll(applicationContext)
            delay(1000L)
        }
        WidgetStateStore.set(applicationContext, WidgetState.IDLE)
        DringWidgetProvider.refreshAll(applicationContext)
        DringLargeWidgetProvider.refreshAll(applicationContext)
        return Result.success()
    }
}
