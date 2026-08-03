package com.receiptintel.scanner.parser

/**
 * Normalized output of the parsing pipeline, independent of whether the
 * source was a camera image, an imported image, or a TXT export.
 *
 * All numeric fields are nullable because real-world receipts are frequently
 * missing fields or contain OCR noise; the app should never crash or silently
 * drop a receipt just because one field failed to parse. Instead the receipt
 * is stored with [hasErrors]=true and [errorNotes] describing what's missing,
 * and it still shows up in History for manual review.
 */
data class ParsedReceiptItem(
    val name: String,
    val quantity: Double?,
    val unitPrice: Double?,
    val subtotal: Double?
)

data class ParsedReceipt(
    val receiptNumber: String?,
    val date: String?,           // normalized yyyy-MM-dd if possible
    val time: String?,           // HH:mm
    val sellerTin: String?,
    val sellerName: String?,
    val sellerAddress: String?,
    val sellerPhone: String?,
    val buyerTin: String?,
    val items: List<ParsedReceiptItem>,
    val taxableAmount: Double?,
    val vatPercentage: Double?,
    val vatAmount: Double?,
    val total: Double?,
    val paymentMethod: String?,
    val rawText: String,
    val confidence: Float,
    val missingFields: List<String>
) {
    val hasErrors: Boolean get() = missingFields.isNotEmpty()
}
