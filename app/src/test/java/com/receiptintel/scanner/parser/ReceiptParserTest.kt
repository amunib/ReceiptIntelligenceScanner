package com.receiptintel.scanner.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReceiptParserTest {

    // Exact sample format supplied in the project spec.
    private val sampleReceipt = """
        TIN: 0063936942
        FS No.00000259
        21/07/2025 11:46

        BUYER'S TIN 0003603905

        34400 x 2.400
        BIRTHDAY-DICORAN *82,560.00

        TXBL 1 *82,560.00
        TAX 1(15.00%) *12,384.00

        TOTAL *94,944.00
        CASH *94,944.00
    """.trimIndent()

    @Test
    fun `parses seller TIN correctly and not the buyer TIN`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.sellerTin).isEqualTo("0063936942")
    }

    @Test
    fun `parses buyer TIN correctly`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.buyerTin).isEqualTo("0003603905")
    }

    @Test
    fun `parses receipt number from FS No`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.receiptNumber).isEqualTo("00000259")
    }

    @Test
    fun `parses and normalizes date to ISO format`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.date).isEqualTo("2025-07-21")
    }

    @Test
    fun `parses time`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.time).isEqualTo("11:46")
    }

    @Test
    fun `parses taxable amount, vat percentage, and vat amount`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.taxableAmount).isEqualTo(82560.00)
        assertThat(result.vatPercentage).isEqualTo(15.00)
        assertThat(result.vatAmount).isEqualTo(12384.00)
    }

    @Test
    fun `parses grand total`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.total).isEqualTo(94944.00)
    }

    @Test
    fun `parses payment method`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.paymentMethod).isEqualTo("CASH")
    }

    @Test
    fun `parses the single line item with quantity and unit price`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.items).hasSize(1)
        val item = result.items.first()
        assertThat(item.name).isEqualTo("BIRTHDAY-DICORAN")
        assertThat(item.unitPrice).isEqualTo(34400.0)
        assertThat(item.quantity).isEqualTo(2.400)
        assertThat(item.subtotal).isEqualTo(82560.00)
    }

    @Test
    fun `reports no missing fields on a complete receipt`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.missingFields).isEmpty()
        assertThat(result.hasErrors).isFalse()
    }

    @Test
    fun `confidence is high for a fully-parsed receipt`() {
        val result = ReceiptParser.parse(sampleReceipt)
        assertThat(result.confidence).isAtLeast(0.9f)
    }

    @Test
    fun `handles a receipt missing the total gracefully instead of crashing`() {
        val broken = sampleReceipt.replace(Regex("""TOTAL \*94,944\.00"""), "")
        val result = ReceiptParser.parse(broken)
        assertThat(result.total).isNull()
        assertThat(result.missingFields).contains("total")
        assertThat(result.hasErrors).isTrue()
    }

    @Test
    fun `handles multiple line items`() {
        val multiItem = """
            TIN: 0011223344
            FS No.00000777
            01/08/2026 09:15

            100 x 1.000
            BREAD-LOAF *100.00

            250 x 2.000
            MILK-1L *500.00

            TXBL 1 *600.00
            TAX 1(15.00%) *90.00

            TOTAL *690.00
            CASH *690.00
        """.trimIndent()

        val result = ReceiptParser.parse(multiItem)
        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].name).isEqualTo("BREAD-LOAF")
        assertThat(result.items[1].name).isEqualTo("MILK-1L")
    }

    @Test
    fun `normalizes common OCR digit noise before parsing`() {
        // Simulates OCR mis-reading a comma-separated number with stray whitespace.
        val noisy = sampleReceipt.replace("94,944.00", "94, 944.00")
        val result = ReceiptParser.parse(noisy)
        assertThat(result.total).isEqualTo(94944.00)
    }

    @Test
    fun `tolerates card and other payment methods`() {
        val cardReceipt = sampleReceipt.replace("CASH *94,944.00", "CARD *94,944.00")
        val result = ReceiptParser.parse(cardReceipt)
        assertThat(result.paymentMethod).isEqualTo("CARD")
    }
}
