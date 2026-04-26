package fr.dring.app

object Config {
    const val SUPABASE_URL = "https://czhscmdhmroezawkasmr.supabase.co"
    const val SUPABASE_KEY = "sb_publishable_xFzcJkuMmoyqx5Gwqt3gAw_8w02hJjv"

    data class User(val id: String, val displayName: String)

    val USER_A = User("11111111-1111-1111-1111-111111111111", "Max")
    val USER_B = User("22222222-2222-2222-2222-222222222222", "Léonie")
    val ALL_USERS = listOf(USER_A, USER_B)

    /** Pool de messages aléatoires utilisés par le widget 1x1. */
    val POKE_MESSAGES = listOf(
        "❤️ Je t'aime",
        "🤗 Je veux un câlin",
        "😘 Je veux un bisou",
        "🔥 J'ai envie de toi",
        "🥺 Tu me manques",
        "💭 Je pense à toi",
    )

    data class QuickMessage(val emoji: String, val text: String) {
        /** Le body envoyé : "${emoji} ${text}" */
        fun body(): String = "$emoji $text"
    }

    /** Messages affichés dans la grille de l'app et le widget 4x2. */
    val QUICK_MESSAGES = listOf(
        QuickMessage("❤️", "Je t'aime"),
        QuickMessage("🤗", "Je veux un câlin"),
        QuickMessage("😘", "Je veux un bisou"),
        QuickMessage("🔥", "J'ai envie de toi"),
        QuickMessage("🥺", "Tu me manques"),
        QuickMessage("💭", "Je pense à toi"),
        QuickMessage("🍝", "On mange quoi ?"),
        QuickMessage("🚗", "Je rentre"),
    )
}
