package com.receiptintel.scanner.ui.screens.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptintel.scanner.csv.CsvExporter
import com.receiptintel.scanner.csv.ExcelExporter
import com.receiptintel.scanner.data.repository.ReceiptRepository
import com.receiptintel.scanner.util.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ExportEvent {
    data class Success(val fileUri: Uri, val count: Int) : ExportEvent()
    data class Error(val message: String) : ExportEvent()
}

data class ExportUiState(
    val isExporting: Boolean = false,
    val lastEvent: ExportEvent? = null
)

class ExportViewModel(
    private val appContext: Context,
    private val repository: ReceiptRepository,
    @Suppress("unused") private val prefs: UserPreferences
) : ViewModel() {

    var uiState by mutableStateOf(ExportUiState())
        private set

    fun exportCsv() = export(extension = "csv") { receipts, outFile -> CsvExporter.export(receipts, outFile) }

    fun exportExcel() = export(extension = "xlsx") { receipts, outFile -> ExcelExporter.export(receipts, outFile) }

    private fun export(
        extension: String,
        write: (List<com.receiptintel.scanner.data.local.entity.ReceiptWithItems>, File) -> File
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(isExporting = true)
            try {
                val receipts = repository.observeAll().first()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val exportsDir = File(appContext.getExternalFilesDir(null), "exports").apply { mkdirs() }
                val outFile = File(exportsDir, "receipts_$timestamp.$extension")

                val result = withContext(Dispatchers.IO) { write(receipts, outFile) }
                val uri = FileProvider.getUriForFile(
                    appContext, "${appContext.packageName}.fileprovider", result
                )
                uiState = ExportUiState(isExporting = false, lastEvent = ExportEvent.Success(uri, receipts.size))
            } catch (e: ExcelExporter.NotConfiguredException) {
                uiState = ExportUiState(isExporting = false, lastEvent = ExportEvent.Error(e.message ?: "Excel export unavailable"))
            } catch (e: Exception) {
                uiState = ExportUiState(isExporting = false, lastEvent = ExportEvent.Error(e.message ?: "Export failed"))
            }
        }
    }

    fun shareIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
}
