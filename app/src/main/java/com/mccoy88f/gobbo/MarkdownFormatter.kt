package com.mccoy88f.gobbo

import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
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
    
    fun formatMarkdownSimple(text: String): SpannableString {
        val spannable = SpannableString(text)
        
        // Formatta i titoli (# ## ###)
        val lines = text.split("\n")
        var currentPos = 0
        
        lines.forEach { line ->
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val content = headingMatch.groupValues[2]
                val start = currentPos
                val end = currentPos + line.length
                
                // Applica stile grassetto
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                // Dimensione relativa
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
            
            // Formatta grassetto **testo**
            val boldPattern = Regex("\\*\\*([^*]+?)\\*\\*")
            boldPattern.findAll(line).forEach { matchResult ->
                val matchStart = currentPos + matchResult.range.first
                val matchEnd = currentPos + matchResult.range.last + 1
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    matchStart,
                    matchEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            // Formatta corsivo *testo* (non **)
            val italicPattern = Regex("(?<!\\*|_)\\*([^*]+?)\\*(?!\\*)|(?<!_|\\*)_([^_]+?)_(?!_)")
            italicPattern.findAll(line).forEach { matchResult ->
                val matchStart = currentPos + matchResult.range.first
                val matchEnd = currentPos + matchResult.range.last + 1
                spannable.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    matchStart,
                    matchEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            currentPos += line.length + 1 // +1 per il newline
        }
        
        return spannable
    }
}
