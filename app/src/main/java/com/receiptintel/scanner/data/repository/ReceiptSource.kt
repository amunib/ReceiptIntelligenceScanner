package com.receiptintel.scanner.data.repository

import android.graphics.Bitmap

/** One unit of input to the processing pipeline, regardless of where it came from. */
sealed class ReceiptSource {
    abstract val sourceType: String
    abstract val sourceFilePath: String?

    data class FromImage(
        val bitmap: Bitmap,
        override val sourceFilePath: String?,
        val fromCamera: Boolean
    ) : ReceiptSource() {
        override val sourceType get() = if (fromCamera) "CAMERA" else "IMAGE"
    }

    data class FromText(
        val text: String,
        override val sourceFilePath: String?
    ) : ReceiptSource() {
        override val sourceType get() = "TXT"
    }
}

/** Result of processing a single [ReceiptSource]. */
sealed class ProcessResult {
    data class Success(val receiptId: Long, val isDuplicate: Boolean) : ProcessResult()
    data class Failure(val reason: String) : ProcessResult()
}
