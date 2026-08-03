package com.receiptintel.scanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * Renders each page of an imported PDF receipt to a [Bitmap] using the
 * platform's built-in `android.graphics.pdf.PdfRenderer` (API 21+), so we
 * avoid bundling a heavy third-party PDF library. The bitmaps then flow
 * through the same [com.receiptintel.scanner.ocr.TextRecognitionEngine]
 * pipeline used for camera/imported images — most "PDF receipts" are scanned
 * images embedded in a PDF wrapper anyway, so OCR (rather than text
 * extraction) is the reliable path for this domain.
 *
 * Note: if a PDF has a real embedded text layer (rare for retail receipts)
 * and higher fidelity is needed later, swap in PDFBox-Android and read the
 * text layer directly, falling back to this renderer for image-only pages.
 */
object PdfPageExtractor {

    fun extractPages(context: Context, uri: Uri, targetDpi: Int = 200): List<Bitmap> {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return emptyList()

        val bitmaps = mutableListOf<Bitmap>()
        PdfRenderer(pfd).use { renderer ->
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val scale = targetDpi / 72f
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps += bitmap
                }
            }
        }
        return bitmaps
    }
}
