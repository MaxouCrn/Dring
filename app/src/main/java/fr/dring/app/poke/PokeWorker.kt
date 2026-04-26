package fr.dring.app.poke

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.dring.app.Config
import fr.dring.app.Identity
import fr.dring.app.widget.DringWidgetProvider
import fr.dring.app.widget.WidgetState
import fr.dring.app.widget.WidgetStateStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PokeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val userId = Identity.get(applicationContext)?.id
        if (userId == null) {
            toast("Ouvre Dring et choisis qui tu es")
            resetWidget()
            return Result.failure()
        }

        val message = inputData.getString(KEY_MESSAGE) ?: Config.POKE_MESSAGES.random()
        val isReply = inputData.getBoolean(KEY_IS_REPLY, false)
        val cooldownMs = if (isReply) WidgetStateStore.COOLDOWN_REPLY_MS
                        else WidgetStateStore.COOLDOWN_REGULAR_MS

        val payload: JsonObject = buildJsonObject {
            put("message", message)
            put("is_reply", JsonPrimitive(isReply))
        }
        val client = HttpClient(Android)

        return try {
            val res = client.post("${Config.SUPABASE_URL}/functions/v1/send-poke") {
                headers {
                    append("apikey", Config.SUPABASE_KEY)
                    append("Authorization", "Bearer ${Config.SUPABASE_KEY}")
                    append("x-user-id", userId)
                }
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(JsonObject.serializer(), payload))
            }

            when (res.status) {
                HttpStatusCode.OK -> {
                    toast("Dring envoyé 🔔")
                    WidgetStateStore.markPokeSent(applicationContext, cooldownMs)
                    DringWidgetProvider.refreshAll(applicationContext)
                    fr.dring.app.widget.DringLargeWidgetProvider.refreshAll(applicationContext)
                    DringWidgetProvider.scheduleCooldownReset(applicationContext, cooldownMs)
                    Result.success()
                }
                HttpStatusCode.TooManyRequests -> {
                    toast("Attends un peu avant de relancer 😉")
                    WidgetStateStore.markPokeSent(applicationContext, cooldownMs)
                    DringWidgetProvider.refreshAll(applicationContext)
                    fr.dring.app.widget.DringLargeWidgetProvider.refreshAll(applicationContext)
                    DringWidgetProvider.scheduleCooldownReset(applicationContext, cooldownMs)
                    Result.success()
                }
                else -> {
                    val txt = runCatching { res.bodyAsText() }.getOrDefault("")
                    toast("Erreur ${res.status.value}: $txt")
                    resetWidget()
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            toast("Erreur réseau: ${e.message}")
            resetWidget()
            Result.retry()
        } finally {
            client.close()
        }
    }

    private fun resetWidget() {
        WidgetStateStore.set(applicationContext, WidgetState.IDLE)
        DringWidgetProvider.refreshAll(applicationContext)
        fr.dring.app.widget.DringLargeWidgetProvider.refreshAll(applicationContext)
    }

    private fun toast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val KEY_MESSAGE = "message"
        const val KEY_IS_REPLY = "is_reply"

        fun enqueue(context: Context, message: String? = null, isReply: Boolean = false) {
            val data = Data.Builder().apply {
                if (message != null) putString(KEY_MESSAGE, message)
                putBoolean(KEY_IS_REPLY, isReply)
            }.build()
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<PokeWorker>().setInputData(data).build()
            )
        }
    }
}
