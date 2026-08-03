package com.receiptintel.scanner.ui.screens.scan

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.receiptintel.scanner.data.repository.PendingBatchHolder
import com.receiptintel.scanner.data.repository.ReceiptRepository
import com.receiptintel.scanner.data.repository.ReceiptSource

class ScanViewModel(@Suppress("unused") private val repository: ReceiptRepository) : ViewModel() {

    var batchMode by mutableStateOf(false)
        private set

    var captures by mutableStateOf<List<Bitmap>>(emptyList())
        private set

    fun toggleBatchMode() {
        batchMode = !batchMode
    }

    fun addCapture(bitmap: Bitmap) {
        captures = captures + bitmap
    }

    fun removeCapture(index: Int) {
        captures = captures.toMutableList().apply { removeAt(index) }
    }

    fun clearCaptures() {
        captures = emptyList()
    }

    /** Hands the captured bitmaps off to the processing pipeline. */
    fun commitToProcessingQueue() {
        PendingBatchHolder.sources = captures.map { bmp ->
            ReceiptSource.FromImage(bitmap = bmp, sourceFilePath = null, fromCamera = true)
        }
        clearCaptures()
    }
}
