package com.receiptintel.scanner.ui.screens.processing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.receiptintel.scanner.R
import com.receiptintel.scanner.data.repository.PendingBatchHolder

@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    onDone: () -> Unit
) {
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        val sources = PendingBatchHolder.sources
        PendingBatchHolder.sources = emptyList()
        if (sources.isNotEmpty()) viewModel.start(sources)
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.processing_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        val progress = if (state.total > 0) state.currentIndex / state.total.toFloat() else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.processing_current, state.currentIndex, state.total))

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            StatChip(label = stringResource(R.string.processing_success, state.succeeded), positive = true)
            StatChip(label = stringResource(R.string.processing_failed, state.failed), positive = false)
        }

        if (state.duplicates > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Possible duplicates: ${state.duplicates}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(Modifier.height(32.dp))
        if (state.isDone) {
            Button(onClick = onDone) { Text(stringResource(R.string.processing_view_results)) }
        } else {
            OutlinedButton(onClick = { viewModel.cancel(); onDone() }) {
                Text(stringResource(R.string.processing_cancel))
            }
        }
    }
}

@Composable
private fun StatChip(label: String, positive: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (positive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    )
}
