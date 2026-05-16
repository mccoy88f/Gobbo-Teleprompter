package com.mccoy88f.gobbo

/**
 * Estrae la durata prevista dal markdown con un commento HTML:
 * `<!-- gobbo-duration: 300 -->` (secondi)
 * oppure `5m`, `90s`, `1h` (case insensitive).
 */
object GobboDurationParser {

    private val tagRegex = Regex(
        """<!--\s*gobbo-duration:\s*([^>]+?)\s*-->""",
        RegexOption.IGNORE_CASE
    )

    fun parseSeconds(rawText: String): Int? {
        val match = tagRegex.find(rawText) ?: return null
        return parseToken(match.groupValues[1].trim())
    }

    fun parseToken(token: String): Int? {
        val t = token.lowercase().trim()
        if (t.isEmpty()) return null
        return when {
            t.endsWith("ms") ->
                (t.removeSuffix("ms").trim().toDoubleOrNull()?.div(1000.0))?.toInt()
            t.endsWith("m") || t.endsWith("min") -> {
                val n = t.removeSuffix("min").removeSuffix("m").trim().toDoubleOrNull() ?: return null
                (n * 60).toInt()
            }
            t.endsWith("h") -> {
                val n = t.removeSuffix("h").trim().toDoubleOrNull() ?: return null
                (n * 3600).toInt()
            }
            t.endsWith("s") -> t.removeSuffix("s").trim().toDoubleOrNull()?.toInt()
            else -> t.toIntOrNull() ?: t.toDoubleOrNull()?.toInt()
        }?.coerceAtLeast(0)
    }
}
