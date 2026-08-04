package com.receiptintel.scanner.parser

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Parses raw text (from OCR or a TXT/PDF import) into a [ParsedReceipt].
 *
 * Employs a multi-strategy format engine:
 *  1. [EthiopianFiscalFormat] for ERCA/EFDA fiscal cash register receipts.
 *  2. [GenericReceiptFormat] for general retail, supermarket, restaurant, and invoice receipts worldwide.
 */
object ReceiptParser {

    private val FORMATS: List<ReceiptFormat> = listOf(
        EthiopianFiscalFormat,
        GenericReceiptFormat
    )

    fun parse(rawText: String): ParsedReceipt {
        val cleaned = normalizeOcrNoise(rawText)
        val bestResult = FORMATS
            .map { it.tryParse(cleaned) }
            .maxByOrNull { it.confidence }
            ?: GenericReceiptFormat.tryParse(cleaned)

        val warnings = validateCrossFields(bestResult)
        val allMissing = (bestResult.missingFields + warnings).distinct()

        return bestResult.copy(missingFields = allMissing)
    }

    /**
     * Fixes common OCR misreads before parsing:
     *  - 'O' -> '0' and 'l'/'I' -> '1' inside numeric-looking tokens
     *  - stray spaces inside numbers ("94, 944.00" -> "94,944.00")
     *  - noise symbols like '|', ';', '$' inside numeric patterns
     */
    private fun normalizeOcrNoise(text: String): String {
        var t = text.replace("\r\n", "\n").replace('\r', '\n')
        t = t.replace(Regex(""",\s+(?=\d)"""), ",")          // "94, 944" -> "94,944"
        t = t.replace(Regex("""(?<=\d)\s*[Oo](?=\d)"""), "0") // digit O digit -> 0
        t = t.replace(Regex("""(?<=\d)\s*\|\s*(?=\d)"""), "1") // | -> 1
        t = t.replace(Regex("""(?<=\d)\s*;\s*(?=\d)"""), ",") // ; -> ,
        t = t.replace(Regex("""(?<=\d)\s*[S]\s*(?=\d)"""), "5") // S -> 5
        t = t.replace(Regex("""(?<=\d)\s*\$\s*(?=\d)"""), "8") // $ -> 8
        return t
    }

    private fun validateCrossFields(receipt: ParsedReceipt): List<String> {
        val warnings = mutableListOf<String>()

        if (receipt.taxableAmount != null && receipt.vatAmount != null && receipt.total != null) {
            val expected = receipt.taxableAmount + receipt.vatAmount
            if (abs(expected - receipt.total) > Math.max(1.0, receipt.total * 0.02)) {
                warnings.add("Taxable + VAT does not match grand total")
            }
        }

        if (receipt.items.isNotEmpty()) {
            val itemsSum = receipt.items.mapNotNull { it.subtotal }.sum()
            val target = receipt.taxableAmount ?: receipt.total
            if (itemsSum > 0 && target != null && abs(itemsSum - target) > Math.max(1.0, target * 0.05)) {
                warnings.add("Item subtotals sum ($itemsSum) differs from receipt total ($target)")
            }
        }

        return warnings
    }
}

/** Strategy interface so additional receipt layouts can be plugged in easily. */
interface ReceiptFormat {
    val confidence: Float
    fun tryParse(text: String): ParsedReceipt
}

object EthiopianFiscalFormat : ReceiptFormat {

    override val confidence: Float get() = 1.0f

    override fun tryParse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val sellerTin = extractSellerTin(text)
        val buyerTin = extractBuyerTin(text)
        val receiptNumber = extractReceiptNumber(text)
        val (date, time) = extractDateTime(text)
        val (taxable, vatPct, vatAmount) = extractTax(text)
        val total = extractTotal(text)
        val paymentMethod = extractPaymentMethod(text)
        val items = extractItems(lines)
        val (sellerName, sellerAddress, sellerPhone) = extractSellerIdentity(lines)

        val missing = mutableListOf<String>()
        if (receiptNumber == null) missing += "receiptNumber"
        if (date == null) missing += "date"
        if (total == null) missing += "total"
        if (sellerTin == null) missing += "sellerTin"
        if (items.isEmpty()) missing += "items"

        val importantFieldCount = 7f
        val foundCount = importantFieldCount - missing.size.coerceAtMost(importantFieldCount.toInt())
        val calculatedConfidence = (foundCount / importantFieldCount).coerceIn(0f, 1f)

        return ParsedReceipt(
            receiptNumber = receiptNumber,
            date = date,
            time = time,
            sellerTin = sellerTin,
            sellerName = sellerName,
            sellerAddress = sellerAddress,
            sellerPhone = sellerPhone,
            buyerTin = buyerTin,
            items = items,
            taxableAmount = taxable,
            vatPercentage = vatPct,
            vatAmount = vatAmount,
            total = total,
            paymentMethod = paymentMethod,
            rawText = text,
            confidence = calculatedConfidence,
            missingFields = missing
        )
    }

    private fun extractSellerTin(text: String): String? {
        val match = Regex("""(?<!BUYER'S\s)(?<!BUYER\s)TIN\s*[:\-]?\s*(\d{6,12})""", RegexOption.IGNORE_CASE)
            .find(text)
        return match?.groupValues?.get(1)
    }

    private fun extractBuyerTin(text: String): String? =
        Regex("""BUYER'?S?\s*TIN\s*[:\-]?\s*(\d{6,12})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)

    private fun extractReceiptNumber(text: String): String? =
        Regex("""(?:FS\s*No\.?|F\.S\s*No\.?|FSNO|FS#|Receipt\s*No\.?|Rec\.\s*No\.?)\s*(\d{4,12})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)

    private fun extractDateTime(text: String): Pair<String?, String?> {
        val dateTimeMatch = Regex("""(\d{1,2}/\d{1,2}/\d{4})\s+(\d{1,2}:\d{2})""").find(text)
        if (dateTimeMatch != null) {
            return normalizeDate(dateTimeMatch.groupValues[1]) to dateTimeMatch.groupValues[2]
        }
        val dateOnly = Regex("""\b(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4})\b""").find(text)
        val timeOnly = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?)\b""").find(text)
        return dateOnly?.let { normalizeDate(it.groupValues[1]) } to timeOnly?.groupValues?.get(1)
    }

    private fun normalizeDate(raw: String): String {
        val parts = raw.split('/', '-', '.').map { it.trim() }
        if (parts.size != 3) return raw
        return try {
            val (d, m, y) = parts
            val fullY = if (y.length == 2) "20$y" else y
            val day = d.padStart(2, '0')
            val month = m.padStart(2, '0')
            "%s-%s-%s".format(fullY, month, day)
        } catch (e: Exception) {
            raw
        }
    }

    private data class TaxInfo(val taxable: Double?, val vatPercentage: Double?, val vatAmount: Double?)

    private fun extractTax(text: String): TaxInfo {
        val taxable = Regex("""TXBL\s*\d*\s*\*?\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.let { parseAmount(it) }

        val taxMatch = Regex(
            """(?:TAX|VAT)\s*\d*\s*\(\s*([\d.]+)\s*%\s*\)\s*\*?\s*([\d,]+\.\d{2})""",
            RegexOption.IGNORE_CASE
        ).find(text)

        val vatPct = taxMatch?.groupValues?.get(1)?.toDoubleOrNull()
        val vatAmount = taxMatch?.groupValues?.get(2)?.let { parseAmount(it) }

        return TaxInfo(taxable, vatPct, vatAmount)
    }

    private fun extractTotal(text: String): Double? =
        Regex("""\b(?:GRAND\s*TOTAL|NET\s*TOTAL|AMOUNT\s*DUE|Sub\s*Total|TOTAL)\s*\*?\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.let { parseAmount(it) }

    private fun extractPaymentMethod(text: String): String? {
        val known = listOf("TELEBIRR", "MPESA", "CASH", "CARD", "CREDIT", "MOBILE", "TRANSFER", "CHEQUE", "CHECK")
        for (word in known) {
            if (Regex("""\b$word\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return word.uppercase()
        }
        return null
    }

    private fun extractSellerIdentity(lines: List<String>): Triple<String?, String?, String?> {
        var sellerName: String? = null
        var address: String? = null
        var phone: String? = null

        for (line in lines) {
            if (phone == null) {
                val match = Regex("""(?:TEL|PHONE|MOB|CELL)[.:\s]*(\+?[\d][\d\s\-()]{6,})""", RegexOption.IGNORE_CASE).find(line)
                if (match?.groupValues?.get(1) != null) phone = match.groupValues[1].replace(Regex("""[\s()]"""), "")
            }
            if (address == null && Regex("""(ADDRESS|ADDIS|STREET|ROAD|CITY|SUB\s*CITY|WOREDA|KEBELE|P\.?O\.?\s*BOX)""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                address = line.replace(Regex("""^ADDRESS[.:\s]*""", RegexOption.IGNORE_CASE), "").trim()
            }
            if (sellerName == null && line.any { it.isLetter() } &&
                !Regex("""(TIN|FS\s*No|TEL|PHONE|ADDRESS|VAT|RECEIPT|INVOICE)""", RegexOption.IGNORE_CASE).containsMatchIn(line) &&
                !line.all { it.isDigit() || it.isWhitespace() || it == '-' }) {
                sellerName = line.replace(Regex("""[*]+"""), "").trim()
            }
        }
        return Triple(sellerName, address, phone)
    }

    private fun extractItems(lines: List<String>): List<ParsedReceiptItem> {
        val qtyLineRegex = Regex("""^([\d,]+\.?\d*)\s*[xX]\s*([\d,]+\.?\d*)$""")
        val nameLineRegex = Regex("""^(.+?)\s*[*\-]?\s*([\d,]+\.\d{2})$""")

        val items = mutableListOf<ParsedReceiptItem>()
        var i = 0
        while (i < lines.size) {
            val qtyMatch = qtyLineRegex.find(lines[i])
            if (qtyMatch != null && i + 1 < lines.size) {
                val nameMatch = nameLineRegex.find(lines[i + 1])
                if (nameMatch != null) {
                    val unitPrice = parseAmount(qtyMatch.groupValues[1])
                    val quantity = parseAmount(qtyMatch.groupValues[2])
                    val name = nameMatch.groupValues[1].trim()
                    val subtotal = parseAmount(nameMatch.groupValues[2])
                    items += ParsedReceiptItem(name, quantity, unitPrice, subtotal)
                    i += 2
                    continue
                }
            }
            i++
        }
        return items
    }

    private fun parseAmount(raw: String): Double? =
        raw.replace(Regex("""(?i)ETB|USD|EUR|GBP|BIRR|Br|\$|€|£"""), "")
           .replace(",", "")
           .replace("*", "")
           .trim()
           .toDoubleOrNull()
}

/** Generic receipt format for any store/restaurant/retail receipt worldwide. */
object GenericReceiptFormat : ReceiptFormat {

    override val confidence: Float get() = 0.8f

    override fun tryParse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val sellerName = extractMerchantName(lines)
        val total = extractGrandTotal(text)
        val (date, time) = extractGenericDateTime(text)
        val receiptNumber = extractGenericReceiptNo(text)
        val taxAmount = extractGenericTax(text)
        val items = extractGenericItems(lines)
        val paymentMethod = extractGenericPayment(text)

        val missing = mutableListOf<String>()
        if (total == null) missing += "total"
        if (date == null) missing += "date"
        if (sellerName == null) missing += "sellerName"
        if (items.isEmpty()) missing += "items"

        val foundCount = 4f - missing.size
        val conf = (foundCount / 4f).coerceIn(0.1f, 0.9f)

        return ParsedReceipt(
            receiptNumber = receiptNumber,
            date = date,
            time = time,
            sellerTin = null,
            sellerName = sellerName,
            sellerAddress = null,
            sellerPhone = null,
            buyerTin = null,
            items = items,
            taxableAmount = if (total != null && taxAmount != null) total - taxAmount else null,
            vatPercentage = null,
            vatAmount = taxAmount,
            total = total,
            paymentMethod = paymentMethod,
            rawText = text,
            confidence = conf,
            missingFields = missing
        )
    }

    private fun extractMerchantName(lines: List<String>): String? {
        for (i in 0 until minOf(4, lines.size)) {
            val line = lines[i]
            if (line.any { it.isLetter() } &&
                !Regex("""(TOTAL|RECEIPT|INVOICE|DATE|WELCOME|THANK|ORDER|CHECK|TABLE)""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                return line
            }
        }
        return null
    }

    private fun extractGrandTotal(text: String): Double? {
        val patterns = listOf(
            Regex("""\b(?:TOTAL|BALANCE DUE|AMOUNT DUE|GRAND TOTAL|NET TOTAL|PAYMENT)\s*[:=]?\s*[\$€£]?\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE),
            Regex("""\b[\$€£]\s*([\d,]+\.\d{2})\b""")
        )
        for (p in patterns) {
            val match = p.find(text)
            if (match != null) {
                return match.groupValues[1].replace(",", "").toDoubleOrNull()
            }
        }
        return null
    }

    private fun extractGenericDateTime(text: String): Pair<String?, String?> {
        val dateMatch = Regex("""\b(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}|\d{4}[/\-.]\d{1,2}[/\-.]\d{1,2})\b""").find(text)
        val timeMatch = Regex("""\b(\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?)\b""", RegexOption.IGNORE_CASE).find(text)
        return dateMatch?.groupValues?.get(1) to timeMatch?.groupValues?.get(1)
    }

    private fun extractGenericReceiptNo(text: String): String? {
        val match = Regex("""(?:RECEIPT|INV|INVOICE|ORDER|TICKET)\s*#?\s*[:.-]?\s*([A-Za-z0-9-]{3,15})""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)
    }

    private fun extractGenericTax(text: String): Double? {
        val match = Regex("""(?:TAX|SALES TAX|VAT|GST|HST)\s*[:=]?\s*[\$€£]?\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }

    private fun extractGenericPayment(text: String): String? {
        val methods = listOf("VISA", "MASTERCARD", "AMEX", "CREDIT CARD", "DEBIT CARD", "CASH", "APPLE PAY", "PAYPAL")
        for (m in methods) {
            if (text.contains(m, ignoreCase = true)) return m
        }
        return null
    }

    private fun extractGenericItems(lines: List<String>): List<ParsedReceiptItem> {
        val items = mutableListOf<ParsedReceiptItem>()
        val itemPattern = Regex("""^(.+?)\s+([\d,]+\.\d{2})$""")
        val nonItem = Regex("""(TOTAL|SUBTOTAL|TAX|BALANCE|CHANGE|CASH|CARD|VISA|DISCOUNT|DUE|DATE|THANK)""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            if (nonItem.containsMatchIn(line)) continue
            val match = itemPattern.find(line)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val price = match.groupValues[2].replace(",", "").toDoubleOrNull()
                if (name.length > 2 && price != null && price > 0) {
                    items.add(ParsedReceiptItem(name = name, quantity = 1.0, unitPrice = price, subtotal = price))
                }
            }
        }
        return items
    }
}

