package com.receiptintel.scanner.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin coroutine-friendly wrapper around ML Kit's on-device Latin text
 * recognizer. ML Kit's model ships/downloads once and then runs fully
 * on-device, satisfying the offline requirement (see the
 * `com.google.mlkit.vision.DEPENDENCIES` meta-data in AndroidManifest.xml,
 * which forces the model to install at app-install time rather than lazily
 * over Wi-Fi on first use).
 *
 * Swap point: if Amharic-script receipts become common, ML Kit's Latin
 * recognizer will not read Ge'ez script. At that point replace this class's
 * internals with Tesseract (tess-two / Tesseract4Android) using the `amh`
 * trained data file, keeping the same [recognize] signature so nothing else
 * in the app needs to change.
 */
class TextRecognitionEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (cont.isActive) cont.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                if (cont.isActive) cont.resumeWithException(e)
            }
    }

    fun close() {
        recognizer.close()
    }
}
