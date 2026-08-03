package com.receiptintel.scanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single processed receipt header. Line items live separately in
 * [ItemEntity], joined by [id] == [ItemEntity.receiptId].
 *
 * [contentHash] is a hash of (receiptNumber + sellerTin + total + date) used
 * for fast duplicate detection across large batches (1000+ receipts) without
 * comparing full text every time.
 */
@Entity(
    tableName = "receipts",
    indices = [
        Index(value = ["receiptNumber"]),
        Index(value = ["contentHash"], unique = false),
        Index(value = ["date"])
    ]
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val receiptNumber: String?,
    val date: String?,          // Stored normalized as yyyy-MM-dd when parseable, raw string otherwise
    val time: String?,          // HH:mm
    val sellerTin: String?,
    val sellerName: String?,
    val sellerAddress: String?,
    val sellerPhone: String?,
    val buyerTin: String?,

    @ColumnInfo(name = "taxable_amount")
    val taxableAmount: Double?,
    val vatPercentage: Double?,
    val vatAmount: Double?,
    val total: Double?,
    val paymentMethod: String?,

    /** Path to the original source (image / txt / pdf page) for audit + re-OCR. */
    val sourceFilePath: String?,
    val sourceType: String,     // "CAMERA" | "IMAGE" | "TXT" | "PDF"

    /** Raw OCR/parsed text kept for troubleshooting mis-parses. */
    val rawText: String?,

    /** 0.0–1.0 heuristic confidence the parser assigns to this record. */
    val parseConfidence: Float,

    /** True when the parser could not find enough required fields. */
    val hasErrors: Boolean,
    val errorNotes: String? = null,

    val contentHash: String,
    val isDuplicateOfId: Long? = null,

    val createdAtEpochMs: Long = System.currentTimeMillis()
)
