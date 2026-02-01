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
                    "pdf" -> readPdf(inputStream)
                    "rtf" -> readRtf(inputStream)
                    else -> readPlainText(inputStream)
                }
            }
        } catch (e: Exception) {
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
    
    private fun readPdf(inputStream: java.io.InputStream): String {
        val document = PDDocument.load(inputStream)
        val stripper = PDFTextStripper()
        val text = stripper.getText(document)
        document.close()
        return text
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
        
        // Rimuove i codici RTF comuni
        var cleanText = rtfContent
            // Rimuove i comandi RTF principali
            .replace(Regex("\\\\[a-z]+\\d*"), "") // \par, \b, \i, etc.
            .replace(Regex("\\\\'[0-9a-fA-F]{2}"), "") // Caratteri esadecimali
            .replace(Regex("\\\\[{}]"), "") // Parentesi graffe escape
            .replace(Regex("\\{[^}]*\\}"), "") // Gruppi RTF
            .replace(Regex("\\\\[^a-z{}'\\s]+"), "") // Altri comandi RTF
            // Rimuove le parentesi graffe rimanenti
            .replace("{", "")
            .replace("}", "")
            // Rimuove spazi multipli
            .replace(Regex("\\s+"), " ")
            .trim()
        
        return cleanText
    }
    
    fun getFileExtension(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        
        return when {
            mimeType.contains("text/plain") -> "txt"
            mimeType.contains("text/markdown") -> "md"
            mimeType.contains("application/rtf") -> "rtf"
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
