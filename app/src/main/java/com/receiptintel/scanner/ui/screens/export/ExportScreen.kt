package com.receiptintel.scanner.ui.screens.export

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.receiptintel.scanner.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(viewModel: ExportViewModel) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.lastEvent) {
        when (val event = state.lastEvent) {
            is ExportEvent.Success -> snackbarHostState.showSnackbar(
                context.getString(R.string.export_success, event.count, "app files/exports")
            )
            is ExportEvent.Error -> snackbarHostState.showSnackbar(event.message)
            null -> {}
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.export_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(onClick = { viewModel.exportCsv() }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.export_csv), style = MaterialTheme.typography.titleMedium)
                }
            }
            ElevatedCard(onClick = { viewModel.exportExcel() }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.export_excel), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.export_excel_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isExporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            (state.lastEvent as? ExportEvent.Success)?.let { success ->
                Button(
                    onClick = {
                        context.startActivity(
                            android.content.Intent.createChooser(
                                viewModel.shareIntent(success.fileUri),
                                context.getString(R.string.export_share)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_share))
                }
            }
        }
    }
}
