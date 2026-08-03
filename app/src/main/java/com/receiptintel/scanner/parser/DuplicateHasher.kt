package com.receiptintel.scanner.parser

import java.security.MessageDigest

/**
 * Produces a stable hash from the fields most likely to uniquely identify a
 * receipt (receipt number + seller TIN + total + date). Two receipts with
 * the same hash are flagged as likely duplicates — e.g. the same physical
 * receipt scanned twice, or the same TXT file imported twice.
 *
 * Deliberately excludes OCR-noisy free text (item names, addresses) since
 * those vary between two scans of the *same* receipt due to OCR jitter.
 */
object DuplicateHasher {

    fun hash(receipt: ParsedReceipt): String {
        val key = listOf(
            receipt.receiptNumber?.trim()?.uppercase().orEmpty(),
            receipt.sellerTin?.trim().orEmpty(),
            receipt.date?.trim().orEmpty(),
            receipt.total?.let { "%.2f".format(it) }.orEmpty()
        ).joinToString("|")

        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
