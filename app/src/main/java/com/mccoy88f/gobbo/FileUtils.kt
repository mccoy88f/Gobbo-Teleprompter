package com.mccoy88f.gobbo

import android.content.Context
import android.net.Uri
import org.apache.poi.xwpf.usermodel.XWPFDocument
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStreamReader

object FileUtils {
    
    fun readTextFile(context: Context, uri: Uri, extension: String): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                when (extension.lowercase()) {
                    "docx" -> readDocx(inputStream)
                    "pdf" -> try { readPdf(inputStream) } catch (e: Throwable) { e.printStackTrace(); null }
                    "rtf" -> readRtf(inputStream)
                    else -> readPlainText(inputStream)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
    
    private fun readPlainText(inputStream: java.io.InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val content = StringBuilder()
        var line: String?
        
        while (reader.readLine().also { line = it } != null) {
            content.append(line).append("\n")
        }
        
        return content.toString()
    }
    
    private fun readDocx(inputStream: java.io.InputStream): String {
        val document = XWPFDocument(inputStream)
        val content = StringBuilder()
        
        document.paragraphs.forEach { paragraph ->
            content.append(paragraph.text).append("\n")
        }
        
        document.close()
        return content.toString()
    }
    
    private fun readPdf(inputStream: java.io.InputStream): String? {
        var document: PDDocument? = null
        return try {
            document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            stripper.getText(document)
        } finally {
            try { document?.close() } catch (_: Exception) { }
        }
    }
    
    private fun readRtf(inputStream: java.io.InputStream): String {
        // Legge il file RTF e rimuove i codici di formattazione
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        val content = StringBuilder()
        var line: String?
        
        while (reader.readLine().also { line = it } != null) {
            content.append(line).append("\n")
        }
        
        val rtfContent = content.toString()
        
        // Parser RTF migliorato - estrae solo il testo leggibile
        var cleanText = rtfContent
            // Prima decodifica i caratteri speciali RTF (es. \'f2 -> ò, \'e9 -> é, \'e0 -> à)
            .replace(Regex("\\\\'([0-9a-fA-F]{2})")) { matchResult ->
                val hex = matchResult.groupValues[1]
                try {
                    val charCode = hex.toInt(16)
                    charCode.toChar().toString()
                } catch (e: Exception) {
                    ""
                }
            }
            // Rimuove i gruppi RTF di formattazione (fonttbl, colortbl, etc.)
            .replace(Regex("\\{[^}]*\\\\fonttbl[^}]*\\}"), "") // Font table
            .replace(Regex("\\{[^}]*\\\\colortbl[^}]*\\}"), "") // Color table
            .replace(Regex("\\{[^}]*\\\\expandedcolortbl[^}]*\\}"), "") // Expanded color table
            .replace(Regex("\\\\paperw\\d+\\\\paperh\\d+\\\\margl\\d+\\\\margr\\d+\\\\vieww\\d+\\\\viewh\\d+\\\\viewkind\\d+"), "") // Page settings
            // Rimuove comandi RTF ma preserva il testo
            .replace(Regex("\\\\rtf1"), "")
            .replace(Regex("\\\\ansi"), "")
            .replace(Regex("\\\\ansicpg\\d+"), "")
            .replace(Regex("\\\\cocoartf\\d+"), "")
            .replace(Regex("\\\\cocoatextscaling\\d+"), "")
            .replace(Regex("\\\\cocoaplatform\\d+"), "")
            .replace(Regex("\\\\pard[^\\\\]*"), "") // Paragraph settings
            .replace(Regex("\\\\pardirnatural"), "")
            .replace(Regex("\\\\partightenfactor\\d+"), "")
            .replace(Regex("\\\\tx\\d+"), "") // Tab stops
            .replace(Regex("\\\\cf\\d+"), "") // Color foreground
            .replace(Regex("\\\\f\\d+"), "") // Font
            .replace(Regex("\\\\fs\\d+"), "") // Font size
            .replace(Regex("\\\\[a-z]+\\d*"), "") // Altri comandi RTF
            .replace(Regex("\\\\[{}]"), "") // Parentesi graffe escape
            .replace(Regex("\\\\[^a-z{}'\\s]+"), "") // Altri comandi RTF
            // Rimuove le parentesi graffe rimanenti
            .replace("{", "")
            .replace("}", "")
            // Rimuove backslash rimanenti
            .replace("\\", "")
            // Normalizza spazi multipli mantenendo i newline
            .replace(Regex("[ \\t]+"), " ") // Spazi e tab multipli -> spazio singolo
            .replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n") // Più di 2 newline -> 2 newline
            .trim()
        
        // Ripristina i newline dove erano presenti nel testo originale
        // Pattern comuni per testi musicali o formattati
        cleanText = cleanText
            .replace(Regex("\\s+([A-Z][A-Z]+\\s)"), "\n$1") // Pattern come "SOL", "MI-", "DO"
            .replace(Regex("\\s+([A-Z][A-Z\\.]+\\s)"), "\n$1") // Pattern come "RE.", "DO."
            .replace(Regex("\\s+([A-Z][A-Z-]+\\s)"), "\n$1") // Pattern come "MI-", "DO-"
            .replace(Regex("\\s+X\\s+"), "\nX\n") // Separatore "X"
        
        return cleanText
    }
    
    fun getFileExtension(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        
        return when {
            mimeType.contains("text/plain") -> "txt"
            mimeType.contains("text/markdown") -> "md"
            mimeType.contains("application/rtf") || mimeType.contains("text/rtf") -> "rtf"
            mimeType.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document") -> "docx"
            mimeType.contains("application/msword") -> "doc"
            mimeType.contains("application/pdf") -> "pdf"
            else -> {
                // Prova a estrarre dall'URI
                val path = uri.toString()
                val lastDot = path.lastIndexOf('.')
                if (lastDot != -1 && lastDot < path.length - 1) {
                    path.substring(lastDot + 1).lowercase()
                } else {
                    "txt" // Default
                }
            }
        }
    }
}
