package com.receiptintel.scanner.ui.screens.processing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.receiptintel.scanner.data.repository.ProcessResult
import com.receiptintel.scanner.data.repository.ReceiptRepository
import com.receiptintel.scanner.data.repository.ReceiptSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProcessingUiState(
    val total: Int = 0,
    val currentIndex: Int = 0,
    val succeeded: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val isDone: Boolean = false,
    val failedNotes: List<String> = emptyList()
)

class ProcessingViewModel(private val repository: ReceiptRepository) : ViewModel() {

    var uiState by mutableStateOf(ProcessingUiState())
        private set

    private var job: Job? = null

    /**
     * Processes sources one at a time, off the main thread, updating UI
     * state as it goes so the screen never freezes even across 1000+
     * receipts (per the performance requirement). Each receipt is committed
     * to Room individually inside [ReceiptRepository.processOne] rather than
     * batched into one giant transaction, so progress already made survives
     * if the process is killed mid-batch — the user just resumes by
     * re-importing whatever's left instead of losing everything.
     */
    fun start(sources: List<ReceiptSource>) {
        if (job?.isActive == true) return
        uiState = ProcessingUiState(total = sources.size)

        job = viewModelScope.launch {
            var succeeded = 0
            var duplicates = 0
            var failed = 0
            val notes = mutableListOf<String>()

            for ((index, source) in sources.withIndex()) {
                val result = withContext(Dispatchers.Default) { repository.processOne(source) }
                when (result) {
                    is ProcessResult.Success -> {
                        succeeded++
                        if (result.isDuplicate) duplicates++
                    }
                    is ProcessResult.Failure -> {
                        failed++
                        notes += result.reason
                    }
                }
                uiState = uiState.copy(
                    currentIndex = index + 1,
                    succeeded = succeeded,
                    duplicates = duplicates,
                    failed = failed,
                    failedNotes = notes
                )
            }
            uiState = uiState.copy(isDone = true)
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
