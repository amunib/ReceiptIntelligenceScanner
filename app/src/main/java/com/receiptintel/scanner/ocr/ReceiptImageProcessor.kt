package com.receiptintel.scanner.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight, dependency-free image preprocessing to improve OCR accuracy on
 * phone-camera receipt photos: grayscale + contrast stretch (reduces shadow
 * impact) and a simple luminance-gradient based auto-crop that finds the
 * receipt's bounding box against a background.
 *
 * This intentionally avoids pulling in OpenCV (a large native dependency) for
 * what is fundamentally a contrast + bounding-box problem. If perspective-
 * warped (non-rectangular) receipts become a common complaint, swap
 * [autoCrop] for OpenCV's `findContours` + `warpPerspective`, keeping the
 * same function signature.
 */
object ReceiptImageProcessor {

    /** Converts to grayscale, applies adaptive contrast, sharpens, and binarizes via Summed-Area Table adaptive thresholding. */
    fun enhanceForOcr(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        val gray = toLuminance(source)
        var sum = 0L
        for (v in gray) sum += v
        val avgLuminance = if (gray.isNotEmpty()) (sum / gray.size).toFloat() else 128f
        val contrast = 1.0f + (255f - avgLuminance) / 255f * 1.5f // Adaptive contrast stretch

        val grayscaleMatrix = ColorMatrix().apply { setSaturation(0f) }
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayscaleMatrix.postConcat(contrastMatrix)

        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(grayscaleMatrix) }
        canvas.drawBitmap(source, 0f, 0f, paint)

        val sharpened = unsharpMask(result)
        return integralAdaptiveThreshold(sharpened, windowRadius = 18, bias = 8)
    }

    private fun unsharpMask(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val scaled = Bitmap.createScaledBitmap(src, max(1, w / 2), max(1, h / 2), true)
        val blurred = Bitmap.createScaledBitmap(scaled, w, h, true)
        scaled.recycle()

        val srcPixels = IntArray(w * h)
        val blurPixels = IntArray(w * h)
        src.getPixels(srcPixels, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPixels, 0, w, 0, 0, w, h)
        blurred.recycle()

        val amount = 1.5f
        for (i in srcPixels.indices) {
            val sR = Color.red(srcPixels[i])
            val bR = Color.red(blurPixels[i])
            val r = (sR + (sR - bR) * amount).toInt().coerceIn(0, 255)
            srcPixels[i] = Color.rgb(r, r, r)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(srcPixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Summed-area table (Integral Image) local mean thresholding.
     * Computes O(1) per-pixel local average luminance to isolate ink strokes
     * even under heavy shadow gradients on thermal receipts.
     */
    private fun integralAdaptiveThreshold(src: Bitmap, windowRadius: Int = 18, bias: Int = 8): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        }

        // Integral image calculation (DoubleArray to prevent overflow)
        val integral = DoubleArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0.0
            for (x in 0 until w) {
                rowSum += gray[y * w + x]
                integral[(y + 1) * (w + 1) + (x + 1)] = integral[y * (w + 1) + (x + 1)] + rowSum
            }
        }

        fun areaSum(x0: Int, y0: Int, x1: Int, y1: Int): Double {
            return integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] - integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
        }

        for (y in 0 until h) {
            val y0 = max(0, y - windowRadius)
            val y1 = min(h, y + windowRadius + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - windowRadius)
                val x1 = min(w, x + windowRadius + 1)
                val mean = areaSum(x0, y0, x1, y1) / ((x1 - x0) * (y1 - y0))
                val value = gray[y * w + x]
                val ink = if (value < mean - bias) Color.BLACK else Color.WHITE
                pixels[y * w + x] = ink
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }


    /**
     * Finds a bounding rectangle around the receipt by scanning rows/columns
     * for where content (non-background) begins/ends, based on luminance
     * variance versus the image border average. Falls back to the full image
     * if no confident boundary is found (e.g. receipt already fills the frame).
     */
    fun autoCrop(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 20 || h < 20) return source

        val gray = toLuminance(source)
        val backgroundLuminance = estimateBorderLuminance(gray, w, h)
        val threshold = 20 // luminance delta from background to count as "content"

        var top = 0
        var bottom = h - 1
        var left = 0
        var right = w - 1

        top = firstRowExceedingThreshold(gray, w, h, backgroundLuminance, threshold, fromTop = true)
        bottom = firstRowExceedingThreshold(gray, w, h, backgroundLuminance, threshold, fromTop = false)
        left = firstColExceedingThreshold(gray, w, h, backgroundLuminance, threshold, fromLeft = true)
        right = firstColExceedingThreshold(gray, w, h, backgroundLuminance, threshold, fromLeft = false)

        // Add small padding so we don't clip characters right at the edge.
        val pad = (min(w, h) * 0.01f).toInt().coerceAtLeast(2)
        val rect = Rect(
            max(0, left - pad),
            max(0, top - pad),
            min(w, right + pad),
            min(h, bottom + pad)
        )

        if (rect.width() < w * 0.3 || rect.height() < h * 0.3) {
            // Boundary detection likely failed (too aggressive a crop) — keep original.
            return source
        }

        return Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
    }

    private fun toLuminance(bitmap: Bitmap): IntArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            out[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        return out
    }

    private fun estimateBorderLuminance(gray: IntArray, w: Int, h: Int): Int {
        var sum = 0L
        var count = 0L
        // Sample the outer 5% border ring.
        val marginX = (w * 0.05f).toInt().coerceAtLeast(1)
        val marginY = (h * 0.05f).toInt().coerceAtLeast(1)
        for (y in 0 until h step max(1, h / 50)) {
            for (x in intArrayOf(0.coerceAtMost(w - 1), marginX, w - 1 - marginX, w - 1)) {
                if (x in 0 until w) {
                    sum += gray[y * w + x]; count++
                }
            }
        }
        for (x in 0 until w step max(1, w / 50)) {
            for (y in intArrayOf(0, marginY, h - 1 - marginY, h - 1)) {
                if (y in 0 until h) {
                    sum += gray[y * w + x]; count++
                }
            }
        }
        return if (count > 0) (sum / count).toInt() else 200
    }

    private fun firstRowExceedingThreshold(
        gray: IntArray, w: Int, h: Int, background: Int, threshold: Int, fromTop: Boolean
    ): Int {
        val range = if (fromTop) 0 until h else (h - 1) downTo 0
        for (y in range) {
            var deltaSum = 0L
            for (x in 0 until w step max(1, w / 100)) {
                deltaSum += kotlin.math.abs(gray[y * w + x] - background)
            }
            val avgDelta = deltaSum / (w / max(1, w / 100)).coerceAtLeast(1)
            if (avgDelta > threshold) return y
        }
        return if (fromTop) 0 else h - 1
    }

    private fun firstColExceedingThreshold(
        gray: IntArray, w: Int, h: Int, background: Int, threshold: Int, fromLeft: Boolean
    ): Int {
        val range = if (fromLeft) 0 until w else (w - 1) downTo 0
        for (x in range) {
            var deltaSum = 0L
            for (y in 0 until h step max(1, h / 100)) {
                deltaSum += kotlin.math.abs(gray[y * w + x] - background)
            }
            val avgDelta = deltaSum / (h / max(1, h / 100)).coerceAtLeast(1)
            if (avgDelta > threshold) return x
        }
        return if (fromLeft) 0 else w - 1
    }
}
