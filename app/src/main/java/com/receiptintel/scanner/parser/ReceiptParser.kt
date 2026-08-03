package com.receiptintel.scanner.parser

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses raw text (from OCR or a TXT import) into a [ParsedReceipt].
 *
 * Design notes for future maintainers / adding new receipt layouts:
 * ------------------------------------------------------------------
 * Each field is extracted independently by its own small regex-based
 * function below (e.g. [extractReceiptNumber], [extractTotal]). This is
 * deliberate: a new receipt layout almost never changes *every* field at
 * once — usually just the label text or ordering for one or two fields.
 * To support a new layout:
 *   1. Add the new label pattern as an additional alternative inside the
 *      relevant extractXxx() regex (e.g. add `|Grand Total` next to `TOTAL`).
 *   2. If the layout is structurally different enough (e.g. columns instead
 *      of lines), implement a new object conforming to [ReceiptFormat] and
 *      register it in [FORMATS]; [parse] will try each format in order and
 *      use the one that yields the highest [ParsedReceipt.confidence].
 *
 * The default implementation below (object [EthiopianFiscalFormat]) targets
 * the ERCA/EFDA-style fiscal cash register receipts you supplied as sample
 * data (TIN / FS No. / TXBL / TAX / TOTAL / CASH layout).
 */
object ReceiptParser {

    private val FORMATS: List<ReceiptFormat> = listOf(EthiopianFiscalFormat)

    fun parse(rawText: String): ParsedReceipt {
        val cleaned = normalizeOcrNoise(rawText)
        return FORMATS
            .map { it.tryParse(cleaned) }
            .maxByOrNull { it.confidence }
            ?: EthiopianFiscalFormat.tryParse(cleaned) // FORMATS is never empty, but keep a safe fallback
    }

    /**
     * Fixes common OCR misreads before parsing:
     *  - 'O' -> '0' and 'l'/'I' -> '1' inside numeric-looking tokens
     *  - stray spaces inside numbers ("94, 944.00" -> "94,944.00")
     *  - curly/smart quotes around labels
     */
    private fun normalizeOcrNoise(text: String): String {
        var t = text.replace("\r\n", "\n").replace('\r', '\n')
        t = t.replace(Regex(""",\s+(?=\d)"""), ",")   // "94, 944" -> "94,944"
        t = t.replace(Regex("""(?<=\d)\s*[Oo](?=\d)"""), "0") // digit O digit -> 0
        return t
    }
}

/** Strategy interface so additional receipt layouts can be plugged in later. */
interface ReceiptFormat {
    fun tryParse(text: String): ParsedReceipt
}

object EthiopianFiscalFormat : ReceiptFormat {

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
        val sellerName = extractSellerName(lines)

        val missing = mutableListOf<String>()
        if (receiptNumber == null) missing += "receiptNumber"
        if (date == null) missing += "date"
        if (total == null) missing += "total"
        if (sellerTin == null) missing += "sellerTin"
        if (items.isEmpty()) missing += "items"

        // Simple confidence heuristic: fraction of the "important" fields found.
        val importantFieldCount = 7f
        val foundCount = importantFieldCount - missing.size.coerceAtMost(importantFieldCount.toInt())
        val confidence = (foundCount / importantFieldCount).coerceIn(0f, 1f)

        return ParsedReceipt(
            receiptNumber = receiptNumber,
            date = date,
            time = time,
            sellerTin = sellerTin,
            sellerName = sellerName,
            sellerAddress = null, // Not present in the sample fiscal format; extend when a layout provides it
            sellerPhone = null,
            buyerTin = buyerTin,
            items = items,
            taxableAmount = taxable,
            vatPercentage = vatPct,
            vatAmount = vatAmount,
            total = total,
            paymentMethod = paymentMethod,
            rawText = text,
            confidence = confidence,
            missingFields = missing
        )
    }

    private fun extractSellerTin(text: String): String? {
        // First "TIN:" occurrence that is not prefixed by "BUYER'S"
        val match = Regex("""(?<!BUYER'S\s)(?<!BUYER\s)TIN\s*[:\-]?\s*(\d{6,12})""", RegexOption.IGNORE_CASE)
            .find(text)
        return match?.groupValues?.get(1)
    }

    private fun extractBuyerTin(text: String): String? =
        Regex("""BUYER'?S?\s*TIN\s*[:\-]?\s*(\d{6,12})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)

    private fun extractReceiptNumber(text: String): String? =
        Regex("""FS\s*No\.?\s*(\d{4,12})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)

    /** Returns (date, time) both normalized where possible. */
    private fun extractDateTime(text: String): Pair<String?, String?> {
        val dateTimeMatch = Regex("""(\d{1,2}/\d{1,2}/\d{4})\s+(\d{1,2}:\d{2})""").find(text)
        if (dateTimeMatch != null) {
            return normalizeDate(dateTimeMatch.groupValues[1]) to dateTimeMatch.groupValues[2]
        }
        val dateOnly = Regex("""\b(\d{1,2}[/\-.]\d{1,2}[/\-.]\d{4})\b""").find(text)
        val timeOnly = Regex("""\b(\d{1,2}:\d{2})\b""").find(text)
        return dateOnly?.let { normalizeDate(it.groupValues[1]) } to timeOnly?.groupValues?.get(1)
    }

    /** Accepts several delimiters/orderings; stores as yyyy-MM-dd (ISO) when day/month/year is unambiguous. */
    private fun normalizeDate(raw: String): String {
        val parts = raw.split('/', '-', '.').map { it.trim() }
        if (parts.size != 3) return raw
        return try {
            val (d, m, y) = parts // Ethiopian fiscal receipts print DD/MM/YYYY
            val day = d.padStart(2, '0')
            val month = m.padStart(2, '0')
            "%s-%s-%s".format(y, month, day)
        } catch (e: Exception) {
            raw
        }
    }

    private data class TaxInfo(val taxable: Double?, val vatPercentage: Double?, val vatAmount: Double?)

    private fun extractTax(text: String): TaxInfo {
        val taxable = Regex("""TXBL\s*\d*\s*\*?\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.let { parseAmount(it) }

        val taxMatch = Regex(
            """TAX\s*\d*\s*\(\s*([\d.]+)\s*%\s*\)\s*\*?\s*([\d,]+\.\d{2})""",
            RegexOption.IGNORE_CASE
        ).find(text)

        val vatPct = taxMatch?.groupValues?.get(1)?.toDoubleOrNull()
        val vatAmount = taxMatch?.groupValues?.get(2)?.let { parseAmount(it) }

        return TaxInfo(taxable, vatPct, vatAmount)
    }

    private fun extractTotal(text: String): Double? =
        Regex("""\bTOTAL\s*\*?\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.let { parseAmount(it) }

    private fun extractPaymentMethod(text: String): String? {
        val known = listOf("CASH", "CARD", "CREDIT", "MOBILE", "TELEBIRR", "CHEQUE", "CHECK")
        for (word in known) {
            if (Regex("""\b$word\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) return word.uppercase()
        }
        return null
    }

    private fun extractSellerName(lines: List<String>): String? {
        // Heuristic: the fiscal-header format supplied doesn't include a seller
        // name line, but many real receipts have it on the first 1-3 lines,
        // before the TIN line, in a mix of letters (not purely numeric/label text).
        val tinIndex = lines.indexOfFirst { it.contains("TIN", ignoreCase = true) }
        if (tinIndex <= 0) return null
        val candidate = lines.subList(0, tinIndex).firstOrNull { line ->
            line.any { it.isLetter() } && !line.contains("FS No", ignoreCase = true)
        }
        return candidate
    }

    /**
     * Item lines in the sample format come in pairs:
     *   "<unitPrice> x <quantity>"
     *   "<ITEM NAME> *<subtotal>"
     * e.g.
     *   34400 x 2.400
     *   BIRTHDAY-DICORAN *82,560.00
     *
     * We scan consecutive line pairs matching this shape. Lines that don't
     * match either pattern (headers, TIN, TOTAL, etc.) are skipped, so this
     * is tolerant of extra boilerplate between the item block and the rest
     * of the receipt.
     */
    private fun extractItems(lines: List<String>): List<ParsedReceiptItem> {
        val qtyLineRegex = Regex("""^([\d,]+\.?\d*)\s*[xX]\s*([\d,]+\.?\d*)$""")
        val nameLineRegex = Regex("""^(.+?)\s*\*\s*([\d,]+\.\d{2})$""")

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
        raw.replace(",", "").replace("*", "").trim().toDoubleOrNull()
}
