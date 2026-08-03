package com.receiptintel.scanner.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptintel.scanner.data.local.entity.DashboardSummary
import com.receiptintel.scanner.data.local.entity.ReceiptEntity
import com.receiptintel.scanner.data.repository.ReceiptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val totalReceipts: Int = 0,
    val totalSales: Double = 0.0,
    val totalVat: Double = 0.0,
    val recent: List<ReceiptEntity> = emptyList(),
    val isLoading: Boolean = true
)

class DashboardViewModel(private val repository: ReceiptRepository) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeDashboardSummary(),
        repository.observeRecent(limit = 10)
    ) { summary: DashboardSummary, recent: List<ReceiptEntity> ->
        DashboardUiState(
            totalReceipts = summary.totalReceipts,
            totalSales = summary.totalSales ?: 0.0,
            totalVat = summary.totalVat ?: 0.0,
            recent = recent,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )
}
