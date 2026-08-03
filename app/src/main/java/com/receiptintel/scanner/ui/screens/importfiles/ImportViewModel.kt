package com.receiptintel.scanner.ui.screens.importfiles

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptintel.scanner.data.repository.PendingBatchHolder
import com.receiptintel.scanner.data.repository.ReceiptRepository
import com.receiptintel.scanner.data.repository.ReceiptSource
import com.receiptintel.scanner.util.PdfPageExtractor
import com.receiptintel.scanner.util.TxtReceiptReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val selectedUris: List<Uri> = emptyList(),
    val isPreparing: Boolean = false
)

class ImportViewModel(
    private val appContext: Context,
    @Suppress("unused") private val repository: ReceiptRepository
) : ViewModel() {

    var uiState by mutableStateOf(ImportUiState())
        private set

    fun onFilesSelected(uris: List<Uri>) {
        uiState = uiState.copy(selectedUris = uiState.selectedUris + uris)
    }

    fun clearSelection() {
        uiState = ImportUiState()
    }

    /**
     * Reads every selected file off the main thread, builds [ReceiptSource]
     * objects (decoding images/PDF pages to bitmaps, reading TXT as text),
     * and hands the batch to [PendingBatchHolder] for the Processing screen.
     */
    fun prepareAndQueue(onReady: () -> Unit) {
        viewModelScope.launch {
            uiState = uiState.copy(isPreparing = true)
            val sources = withContext(Dispatchers.IO) {
                uiState.selectedUris.flatMap { uri -> uriToSources(uri) }
            }
            PendingBatchHolder.sources = sources
            uiState = ImportUiState()
            onReady()
        }
    }

    private fun uriToSources(uri: Uri): List<ReceiptSource> {
        val mime = appContext.contentResolver.getType(uri).orEmpty()
        val path = uri.toString()
        return when {
            mime.startsWith("text/") || path.endsWith(".txt", ignoreCase = true) -> {
                val text = TxtReceiptReader.read(appContext, uri)
                TxtReceiptReader.splitMultiReceipt(text).map { chunk ->
                    ReceiptSource.FromText(text = chunk, sourceFilePath = path)
                }
            }
            mime == "application/pdf" || path.endsWith(".pdf", ignoreCase = true) -> {
                PdfPageExtractor.extractPages(appContext, uri).map { bitmap ->
                    ReceiptSource.FromImage(bitmap = bitmap, sourceFilePath = path, fromCamera = false)
                }
            }
            mime.startsWith("image/") -> {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }?.let { bmp ->
                    listOf(ReceiptSource.FromImage(bitmap = bmp, sourceFilePath = path, fromCamera = false))
                } ?: emptyList()
            }
            else -> emptyList()
        }
    }
}
