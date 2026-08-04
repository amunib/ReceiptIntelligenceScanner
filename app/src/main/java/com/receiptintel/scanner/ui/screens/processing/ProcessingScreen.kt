package com.receiptintel.scanner.ui.screens.processing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.isDone) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isDone) Icons.Default.CheckCircle else Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = if (state.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    if (state.isDone) stringResource(R.string.processing_scan_complete)
                    else stringResource(R.string.processing_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(24.dp))

                val progress = if (state.total > 0) state.currentIndex / state.total.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.processing_current, state.currentIndex, state.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatChip(label = stringResource(R.string.processing_success, state.succeeded), positive = true)
                    StatChip(label = stringResource(R.string.processing_failed, state.failed), positive = false)
                }

                if (state.duplicates > 0) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.processing_duplicates_found, state.duplicates),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                if (state.isDone) {
                    Button(
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.processing_view_results),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.cancel(); onDone() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.processing_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, positive: Boolean) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                label,
                fontWeight = FontWeight.Medium
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (positive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer,
            labelColor = if (positive)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onErrorContainer
        )
    )
}

