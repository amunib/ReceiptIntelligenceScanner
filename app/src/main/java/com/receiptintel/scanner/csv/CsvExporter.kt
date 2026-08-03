package com.receiptintel.scanner.csv

import com.opencsv.CSVWriter
import com.receiptintel.scanner.data.local.entity.ReceiptWithItems
import java.io.File
import java.io.FileWriter

/**
 * Flattens receipts + their line items into one CSV row per item (matching
 * the format requested in the spec: one line per item with the receipt's
 * header fields repeated). Receipts with zero parsed items still get one row
 * so they're not silently dropped from the export.
 */
object CsvExporter {

    val HEADER = arrayOf(
        "Receipt_Number", "Date", "Time", "Seller_TIN", "Buyer_TIN",
        "Item", "Quantity", "Unit_Price", "Item_Subtotal",
        "Taxable", "VAT_Percent", "VAT", "Total", "Payment", "Has_Errors"
    )

    fun export(receipts: List<ReceiptWithItems>, outFile: File): File {
        FileWriter(outFile).use { fw ->
            CSVWriter(fw).use { writer ->
                writer.writeNext(HEADER)
                for (rwi in receipts) {
                    val r = rwi.receipt
                    if (rwi.items.isEmpty()) {
                        writer.writeNext(
                            rowFor(r, itemName = "", quantity = "", unitPrice = "", subtotal = "")
                        )
                    } else {
                        for (item in rwi.items) {
                            writer.writeNext(
                                rowFor(
                                    r,
                                    itemName = item.itemName,
                                    quantity = item.quantity?.toString().orEmpty(),
                                    unitPrice = item.unitPrice?.toString().orEmpty(),
                                    subtotal = item.subtotal?.toString().orEmpty()
                                )
                            )
                        }
                    }
                }
            }
        }
        return outFile
    }

    private fun rowFor(
        r: com.receiptintel.scanner.data.local.entity.ReceiptEntity,
        itemName: String,
        quantity: String,
        unitPrice: String,
        subtotal: String
    ): Array<String> = arrayOf(
        r.receiptNumber.orEmpty(),
        r.date.orEmpty(),
        r.time.orEmpty(),
        r.sellerTin.orEmpty(),
        r.buyerTin.orEmpty(),
        itemName,
        quantity,
        unitPrice,
        subtotal,
        r.taxableAmount?.toString().orEmpty(),
        r.vatPercentage?.toString().orEmpty(),
        r.vatAmount?.toString().orEmpty(),
        r.total?.toString().orEmpty(),
        r.paymentMethod.orEmpty(),
        if (r.hasErrors) "YES" else "NO"
    )
}
