package com.receiptintel.scanner.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DuplicateHasherTest {

    private fun receipt(receiptNumber: String, tin: String, date: String, total: Double) = ParsedReceipt(
        receiptNumber = receiptNumber,
        date = date,
        time = "10:00",
        sellerTin = tin,
        sellerName = null,
        sellerAddress = null,
        sellerPhone = null,
        buyerTin = null,
        items = emptyList(),
        taxableAmount = null,
        vatPercentage = null,
        vatAmount = null,
        total = total,
        paymentMethod = "CASH",
        rawText = "",
        confidence = 1f,
        missingFields = emptyList()
    )

    @Test
    fun `identical key fields produce identical hash`() {
        val a = receipt("00000259", "0063936942", "2025-07-21", 94944.00)
        val b = receipt("00000259", "0063936942", "2025-07-21", 94944.00)
        assertThat(DuplicateHasher.hash(a)).isEqualTo(DuplicateHasher.hash(b))
    }

    @Test
    fun `different receipt number produces different hash`() {
        val a = receipt("00000259", "0063936942", "2025-07-21", 94944.00)
        val b = receipt("00000260", "0063936942", "2025-07-21", 94944.00)
        assertThat(DuplicateHasher.hash(a)).isNotEqualTo(DuplicateHasher.hash(b))
    }

    @Test
    fun `hash is stable across item-list differences (OCR noise tolerant)`() {
        val a = receipt("00000259", "0063936942", "2025-07-21", 94944.00)
            .copy(items = listOf(ParsedReceiptItem("ITEM-A", 1.0, 1.0, 1.0)))
        val b = receipt("00000259", "0063936942", "2025-07-21", 94944.00)
            .copy(items = emptyList())
        assertThat(DuplicateHasher.hash(a)).isEqualTo(DuplicateHasher.hash(b))
    }
}
