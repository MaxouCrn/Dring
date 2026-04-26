package fr.dring.app

object ReplyPresets {

    /** Réponses préfaites — affichées à la fois dans la notif et dans l'app. */
    private val MAP: Map<String, List<String>> = mapOf(
        "❤️ Je t'aime" to listOf(
            "💖 Moi aussi je t'aime",
        ),
        "🤗 Je veux un câlin" to listOf(
            "🤗 J'arrive",
            "😍 Avec plaisir",
            "⏳ Bientôt",
        ),
        "😘 Je veux un bisou" to listOf(
            "🤗 J'arrive",
            "😍 Avec plaisir",
            "⏳ Bientôt",
        ),
        "🔥 J'ai envie de toi" to listOf(
            "🔥 Moi aussi",
            "💋 Sucette ?",
            "❌ Pas ce soir",
        ),
        "🥺 Tu me manques" to listOf(
            "🥹 Toi aussi",
            "💞 Je rentre bientôt !",
        ),
        "💭 Je pense à toi" to listOf(
            "💭 Moi aussi je pense à toi",
        ),
        "🍝 On mange quoi ?" to listOf(
            "👀 On commande ?",
            "🤔 Je ne sais pas",
        ),
        "🚗 Je rentre" to listOf(
            "❤️ À tout de suite mon amour",
        ),
    )

    private val DEFAULT = listOf(
        "❤️ Reçu mon amour",
        "👍 OK pas de souci bébé",
    )

    fun forMessage(body: String): List<String> {
        MAP[body]?.let { return it }
        for ((key, replies) in MAP) if (body.contains(key, ignoreCase = true)) return replies
        return DEFAULT
    }
}
