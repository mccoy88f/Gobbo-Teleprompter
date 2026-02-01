package com.mccoy88f.gobbo

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.TypefaceSpan
import android.graphics.Typeface

object MarkdownFormatter {
    
    fun formatMarkdown(text: String): SpannableString {
        val spannable = SpannableString(text)
        var index = 0
        
        // Formatta i titoli (# ## ###)
        val headingPattern = Regex("^(#{1,6})\\s+(.+)$", RegexOption.MULTILINE)
        headingPattern.findAll(text).forEach { matchResult ->
            val level = matchResult.groupValues[1].length
            val content = matchResult.groupValues[2]
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            
            // Applica stile grassetto e dimensione maggiore
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            
            // Dimensione relativa in base al livello
            val sizeMultiplier = when (level) {
                1 -> 1.8f
                2 -> 1.5f
                3 -> 1.3f
                4 -> 1.2f
                5 -> 1.1f
                else -> 1.05f
            }
            spannable.setSpan(
                RelativeSizeSpan(sizeMultiplier),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Formatta il testo in grassetto (**testo**)
        val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
        boldPattern.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Formatta il testo in corsivo (*testo* o _testo_)
        val italicPattern = Regex("(?<!\\*)\\*([^*]+?)\\*(?!\\*)|_(.+?)_")
        italicPattern.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            spannable.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Rimuove i simboli Markdown dal testo visualizzato
        var result = text
            .replace(Regex("^#{1,6}\\s+"), "") // Rimuove # dai titoli
            .replace("**", "") // Rimuove **
            .replace(Regex("(?<!\\*)\\*(?!\\*)"), "") // Rimuove * singoli (non **)
            .replace("_", "") // Rimuove _
        
        // Crea un nuovo SpannableString con il testo pulito
        val cleanSpannable = SpannableString(result)
        
        // Applica gli span al testo pulito (con offset corretti)
        // Per semplicità, riapplica gli stili al testo pulito
        val cleanBoldPattern = Regex("\\*\\*(.+?)\\*\\*")
        cleanBoldPattern.findAll(result).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            cleanSpannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        return cleanSpannable
    }
    
    /**
     * Formatta Markdown per la visualizzazione: rimuove simboli (# ** * _ __ ~~ ` [](url) )
     * e applica titoli, grassetto, corsivo, barrato, monospace, link (solo testo).
     */
    fun formatMarkdownSimple(text: String): SpannableString {
        val out = SpannableStringBuilder()
        val lines = text.split("\n")
        val headingRegex = Regex("^(#{1,6})\\s+(.+)$")

        for ((i, line) in lines.withIndex()) {
            val headingMatch = headingRegex.find(line)
            if (headingMatch != null) {
                // Mostra solo il testo del titolo, senza i #
                val level = headingMatch.groupValues[1].length
                val content = headingMatch.groupValues[2].trim()
                val sizeMultiplier = when (level) {
                    1 -> 1.8f
                    2 -> 1.5f
                    3 -> 1.3f
                    4 -> 1.2f
                    5 -> 1.1f
                    else -> 1.05f
                }
                val start = out.length
                out.append(content)
                val end = out.length
                if (start < end) {
                    out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(sizeMultiplier), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                // Righe normali: rimuovi ** e * / _ e applica stili
                appendLineWithInlineFormatting(out, line)
            }
            if (i < lines.size - 1) out.append("\n")
        }

        return SpannableString(out)
    }

    /** Aggiunge una riga processando tutte le sintassi inline Markdown, senza mostrare i simboli. */
    private fun appendLineWithInlineFormatting(out: SpannableStringBuilder, line: String) {
        // Ordine: match più specifici per primi (***, __, **, ~~, `, [], *, _)
        val boldItalicRegex = Regex("\\*\\*\\*([^*]+?)\\*\\*\\*")
        val boldDoubleUnderscoreRegex = Regex("__(?![_\\s])([^_]+?)__(?!_)")
        val boldRegex = Regex("\\*\\*([^*]+?)\\*\\*")
        val strikethroughRegex = Regex("~~([^~]+?)~~")
        val codeRegex = Regex("`([^`]+?)`")
        val linkRegex = Regex("\\[([^\\]]+?)\\]\\([^)]*\\)")
        val italicStarRegex = Regex("(?<!\\*)\\*([^*]+?)\\*(?!\\*)")
        val italicUnderscoreRegex = Regex("(?<!_)_([^_]+?)_(?!_)")
        var remaining = line

        while (remaining.isNotEmpty()) {
            val mBoldItalic = boldItalicRegex.find(remaining)
            val mBoldUnd = boldDoubleUnderscoreRegex.find(remaining)
            val mBold = boldRegex.find(remaining)
            val mStrike = strikethroughRegex.find(remaining)
            val mCode = codeRegex.find(remaining)
            val mLink = linkRegex.find(remaining)
            val mItalicStar = italicStarRegex.find(remaining)
            val mItalicUnd = italicUnderscoreRegex.find(remaining)
            val first = listOf(
                mBoldItalic?.range?.first,
                mBoldUnd?.range?.first,
                mBold?.range?.first,
                mStrike?.range?.first,
                mCode?.range?.first,
                mLink?.range?.first,
                mItalicStar?.range?.first,
                mItalicUnd?.range?.first
            ).filterNotNull().minOrNull() ?: Int.MAX_VALUE

            if (first == Int.MAX_VALUE) {
                out.append(remaining)
                break
            }
            if (first > 0) {
                out.append(remaining.substring(0, first))
                remaining = remaining.substring(first)
            }
            when {
                mBoldItalic != null && remaining.startsWith("***") -> {
                    val content = mBoldItalic.groupValues[1]
                    val start = out.length
                    out.append(content)
                    val end = out.length
                    out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    remaining = remaining.removePrefix("***$content***")
                }
                mBoldUnd != null && remaining.startsWith("__") -> {
                    val content = mBoldUnd.groupValues[1]
                    val start = out.length
                    out.append(content)
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    remaining = remaining.removePrefix("__${content}__")
                }
                mBold != null && remaining.startsWith("**") -> {
                    val content = mBold.groupValues[1]
                    val start = out.length
                    out.append(content)
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    remaining = remaining.removePrefix("**$content**")
                }
                mStrike != null && remaining.startsWith("~~") -> {
                    val content = mStrike.groupValues[1]
                    val start = out.length
                    out.append(content)
                    out.setSpan(StrikethroughSpan(), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    remaining = remaining.removePrefix("~~$content~~")
                }
                mCode != null && remaining.startsWith("`") -> {
                    val content = mCode.groupValues[1]
                    val start = out.length
                    out.append(content)
                    out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    remaining = remaining.removePrefix("`$content`")
                }
                mLink != null && remaining.startsWith("[") -> {
                    val content = mLink.groupValues[1]
                    val fullMatch = mLink.value
                    out.append(content)
                    remaining = remaining.removePrefix(fullMatch)
                }
                mItalicStar != null && remaining.startsWith("*") && !remaining.startsWith("**") -> {
                    val content = mItalicStar.groupValues[1]
                    val start = out.length
                    out.append(content)
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    remaining = remaining.removePrefix("*$content*")
                }
                mItalicUnd != null && remaining.startsWith("_") && !remaining.startsWith("__") -> {
                    val content = mItalicUnd.groupValues[1]
                    val start = out.length
                    out.append(content)
                    out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    remaining = remaining.removePrefix("_${content}_")
                }
                else -> {
                    out.append(remaining.take(1))
                    remaining = remaining.drop(1)
                }
            }
        }
    }
}
