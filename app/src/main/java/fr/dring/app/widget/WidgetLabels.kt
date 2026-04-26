package fr.dring.app.widget

import fr.dring.app.Config

/**
 * Map body -> emoji + label court pour l'affichage dans le widget 4x2 (sous le bouton).
 */
object WidgetLabels {
    private val SHORT_LABELS = mapOf(
        "❤️ Je t'aime" to ("❤️" to "J'aime"),
        "🤗 Je veux un câlin" to ("🤗" to "Câlin"),
        "😘 Je veux un bisou" to ("😘" to "Bisou"),
        "🔥 J'ai envie de toi" to ("🔥" to "Envie"),
        "🥺 Tu me manques" to ("🥺" to "Manque"),
        "💭 Je pense à toi" to ("💭" to "Pense"),
        "🍝 On mange quoi ?" to ("🍝" to "Mange ?"),
        "🚗 Je rentre" to ("🚗" to "Rentre"),
    )

    fun emojiAndLabel(body: String): Pair<String, String> {
        SHORT_LABELS[body]?.let { return it }
        // fallback : split sur l'espace
        val parts = body.split(" ", limit = 2)
        return (parts.firstOrNull() ?: "🔔") to (parts.getOrNull(1) ?: "Dring")
    }

    /** Label "Emoji + texte" affiché dans les pickers. */
    fun pretty(body: String): String {
        val msg = Config.QUICK_MESSAGES.firstOrNull { it.body() == body }
        return if (msg != null) "${msg.emoji} ${msg.text}" else body
    }
}
