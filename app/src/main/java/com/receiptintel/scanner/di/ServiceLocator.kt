package com.receiptintel.scanner.di

import android.content.Context
import com.receiptintel.scanner.ReceiptScannerApp
import com.receiptintel.scanner.data.repository.ReceiptRepository
import com.receiptintel.scanner.ocr.TextRecognitionEngine

/**
 * Minimal manual DI. Deliberately not a singleton-per-process object so unit
 * tests can construct a [ReceiptRepository] directly with fakes instead of
 * going through this locator at all — this class exists purely to wire real
 * dependencies for the running app.
 */
object ServiceLocator {

    @Volatile private var repository: ReceiptRepository? = null

    fun receiptRepository(context: Context): ReceiptRepository =
        repository ?: synchronized(this) {
            repository ?: build(context).also { repository = it }
        }

    private fun build(context: Context): ReceiptRepository {
        val app = context.applicationContext as ReceiptScannerApp
        return ReceiptRepository(
            receiptDao = app.database.receiptDao(),
            itemDao = app.database.itemDao(),
            ocrEngine = TextRecognitionEngine()
        )
    }
}
