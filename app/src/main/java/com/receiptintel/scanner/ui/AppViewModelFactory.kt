package com.receiptintel.scanner.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.receiptintel.scanner.data.repository.ReceiptRepository
import com.receiptintel.scanner.di.ServiceLocator
import com.receiptintel.scanner.ui.screens.dashboard.DashboardViewModel
import com.receiptintel.scanner.ui.screens.export.ExportViewModel
import com.receiptintel.scanner.ui.screens.history.HistoryViewModel
import com.receiptintel.scanner.ui.screens.importfiles.ImportViewModel
import com.receiptintel.scanner.ui.screens.processing.ProcessingViewModel
import com.receiptintel.scanner.ui.screens.scan.ScanViewModel
import com.receiptintel.scanner.util.UserPreferences

/**
 * One small factory for every screen's ViewModel, all sharing the same
 * [ReceiptRepository] singleton from [ServiceLocator]. Avoids boilerplate of
 * a per-screen factory class without pulling in Hilt.
 */
class AppViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val repository: ReceiptRepository = ServiceLocator.receiptRepository(appContext)
        val prefs = UserPreferences(appContext)

        @Suppress("UNCHECKED_CAST")
        return when (modelClass) {
            DashboardViewModel::class.java -> DashboardViewModel(repository) as T
            ScanViewModel::class.java -> ScanViewModel(repository) as T
            ImportViewModel::class.java -> ImportViewModel(appContext, repository) as T
            ProcessingViewModel::class.java -> ProcessingViewModel(repository) as T
            HistoryViewModel::class.java -> HistoryViewModel(repository) as T
            ExportViewModel::class.java -> ExportViewModel(appContext, repository, prefs) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
