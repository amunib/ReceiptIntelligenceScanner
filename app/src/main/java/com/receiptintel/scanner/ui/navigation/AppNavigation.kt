package com.receiptintel.scanner.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.receiptintel.scanner.R
import com.receiptintel.scanner.ui.AppViewModelFactory
import com.receiptintel.scanner.ui.screens.dashboard.DashboardScreen
import com.receiptintel.scanner.ui.screens.dashboard.DashboardViewModel
import com.receiptintel.scanner.ui.screens.export.ExportScreen
import com.receiptintel.scanner.ui.screens.export.ExportViewModel
import com.receiptintel.scanner.ui.screens.history.HistoryScreen
import com.receiptintel.scanner.ui.screens.history.HistoryViewModel
import com.receiptintel.scanner.ui.screens.importfiles.ImportScreen
import com.receiptintel.scanner.ui.screens.importfiles.ImportViewModel
import com.receiptintel.scanner.ui.screens.processing.ProcessingScreen
import com.receiptintel.scanner.ui.screens.processing.ProcessingViewModel
import com.receiptintel.scanner.ui.screens.scan.ScanScreen
import com.receiptintel.scanner.ui.screens.scan.ScanViewModel

private sealed class Dest(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Dashboard : Dest("dashboard", R.string.nav_dashboard, Icons.Default.Dashboard)
    data object Scan : Dest("scan", R.string.nav_scan, Icons.Default.CameraAlt)
    data object Import : Dest("import", R.string.nav_import, Icons.Default.FileUpload)
    data object History : Dest("history", R.string.nav_history, Icons.Default.History)
    data object Export : Dest("export", R.string.nav_export, Icons.Default.IosShare)
}

private const val PROCESSING_ROUTE = "processing"

private val bottomDestinations = listOf(Dest.Dashboard, Dest.Scan, Dest.Import, Dest.History, Dest.Export)

@Composable
fun AppNavigation(viewModelFactory: AppViewModelFactory) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            // Hide the bottom bar on the full-screen Processing step.
            if (currentDestination?.route != PROCESSING_ROUTE) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomDestinations.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                            label = {
                                Text(
                                    stringResource(dest.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Dashboard.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Dest.Dashboard.route) {
                val vm: DashboardViewModel = viewModel(factory = viewModelFactory)
                DashboardScreen(vm)
            }
            composable(Dest.Scan.route) {
                val vm: ScanViewModel = viewModel(factory = viewModelFactory)
                ScanScreen(vm) {
                    navController.navigate(PROCESSING_ROUTE) { launchSingleTop = true }
                }
            }
            composable(Dest.Import.route) {
                val vm: ImportViewModel = viewModel(factory = viewModelFactory)
                ImportScreen(vm) {
                    navController.navigate(PROCESSING_ROUTE) { launchSingleTop = true }
                }
            }
            composable(Dest.History.route) {
                val vm: HistoryViewModel = viewModel(factory = viewModelFactory)
                HistoryScreen(vm)
            }
            composable(Dest.Export.route) {
                val vm: ExportViewModel = viewModel(factory = viewModelFactory)
                ExportScreen(vm)
            }
            composable(PROCESSING_ROUTE) {
                val vm: ProcessingViewModel = viewModel(factory = viewModelFactory)
                ProcessingScreen(vm) {
                    navController.navigate(Dest.History.route) {
                        popUpTo(PROCESSING_ROUTE) { inclusive = true }
                    }
                }
            }
        }
    }
}
