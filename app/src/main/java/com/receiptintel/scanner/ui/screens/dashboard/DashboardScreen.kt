package com.receiptintel.scanner.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.receiptintel.scanner.R
import com.receiptintel.scanner.data.local.entity.ReceiptEntity

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.app_name)) })

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                title = stringResource(R.string.dashboard_total_receipts),
                value = state.totalReceipts.toString(),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = stringResource(R.string.dashboard_total_sales),
                value = "%.2f".format(state.totalSales),
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SummaryCard(
                title = stringResource(R.string.dashboard_total_vat),
                value = "%.2f".format(state.totalVat),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            stringResource(R.string.dashboard_recent),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (state.recent.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.dashboard_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.recent, key = { it.id }) { receipt -> RecentRow(receipt) }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun RecentRow(receipt: ReceiptEntity) {
    ListItem(
        headlineContent = { Text("#${receipt.receiptNumber ?: "—"}  •  ${receipt.date ?: "—"}") },
        supportingContent = {
            Text("${receipt.sellerName ?: receipt.sellerTin ?: "Unknown seller"}  •  ${receipt.total?.let { "%.2f".format(it) } ?: "—"}")
        }
    )
    HorizontalDivider()
}
