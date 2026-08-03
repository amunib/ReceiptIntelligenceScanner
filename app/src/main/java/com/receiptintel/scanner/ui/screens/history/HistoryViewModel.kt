package com.receiptintel.scanner.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptintel.scanner.data.local.entity.ReceiptEntity
import com.receiptintel.scanner.data.local.entity.ReceiptWithItems
import com.receiptintel.scanner.data.repository.ReceiptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: ReceiptRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<ReceiptWithItems>> = _query
        .debounce(250)
        .flatMapLatest { q -> repository.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun delete(receipt: ReceiptEntity) {
        viewModelScope.launch { repository.delete(receipt) }
    }
}
