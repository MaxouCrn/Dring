package fr.dring.app

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
private data class PokeRow(val id: Long)

data class TodayCounts(val sent: Int, val received: Int)

object Stats {
    suspend fun loadTodayCounts(userId: String): TodayCounts = runCatching {
        val startIso = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toString()

        val sent = Supabase.client.postgrest.from("pokes")
            .select(Columns.list("id")) {
                filter {
                    eq("sender_id", userId)
                    gte("sent_at", startIso)
                }
            }
            .decodeList<PokeRow>()
            .size

        val received = Supabase.client.postgrest.from("pokes")
            .select(Columns.list("id")) {
                filter {
                    eq("receiver_id", userId)
                    gte("sent_at", startIso)
                }
            }
            .decodeList<PokeRow>()
            .size

        TodayCounts(sent, received)
    }.getOrDefault(TodayCounts(0, 0))
}
