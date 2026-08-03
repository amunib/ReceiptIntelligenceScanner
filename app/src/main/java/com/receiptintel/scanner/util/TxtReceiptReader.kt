package com.receiptintel.scanner.util

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * Reads a fiscal-register TXT export as plain text for the parser. Some
 * point-of-sale/fiscal devices export in Windows-1252 rather than UTF-8, so
 * we try UTF-8 first and fall back if decoding produces replacement
 * characters.
 */
object TxtReceiptReader {

    fun read(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ""

        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('\uFFFD')) return utf8

        return try {
            String(bytes, Charset.forName("windows-1252"))
        } catch (e: Exception) {
            utf8
        }
    }

    /** For splitting a single TXT file that contains multiple receipts back-to-back. */
    fun splitMultiReceipt(text: String): List<String> {
        // Heuristic: fiscal exports typically separate receipts with a line of
        // dashes/equals or a form-feed character between "TOTAL...CASH" blocks
        // and the next "TIN:" header.
        val chunks = text.split(Regex("""\n\s*[-=]{4,}\s*\n|\u000C"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return if (chunks.size > 1) chunks else listOf(text)
    }
}
