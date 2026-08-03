package com.receiptintel.scanner.ui.screens.importfiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.receiptintel.scanner.R

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onBatchReadyForProcessing: () -> Unit
) {
    val state = viewModel.uiState

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        viewModel.onFilesSelected(uris)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onFilesSelected(uris)
    }
    val txtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.onFilesSelected(uris)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.import_title)) })

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ImportOptionCard(
                icon = Icons.Default.Image,
                label = stringResource(R.string.import_images),
                onClick = { imagePicker.launch("image/*") }
            )
            ImportOptionCard(
                icon = Icons.Default.PictureAsPdf,
                label = stringResource(R.string.import_pdf),
                onClick = { pdfPicker.launch(arrayOf("application/pdf")) }
            )
            ImportOptionCard(
                icon = Icons.Default.Description,
                label = stringResource(R.string.import_txt),
                onClick = { txtPicker.launch(arrayOf("text/plain")) }
            )
        }

        if (state.selectedUris.isNotEmpty()) {
            Text(
                stringResource(R.string.import_selected_count, state.selectedUris.size),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
            LazyColumn(Modifier.weight(1f).padding(16.dp)) {
                items(state.selectedUris) { uri ->
                    Text(
                        uri.lastPathSegment ?: uri.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            Button(
                onClick = { viewModel.prepareAndQueue(onBatchReadyForProcessing) },
                enabled = !state.isPreparing,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                if (state.isPreparing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.import_start))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ImportOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}
