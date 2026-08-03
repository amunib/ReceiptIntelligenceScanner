package com.receiptintel.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.receiptintel.scanner.ui.AppViewModelFactory
import com.receiptintel.scanner.ui.navigation.AppNavigation
import com.receiptintel.scanner.ui.theme.ReceiptScannerTheme
import com.receiptintel.scanner.util.UserPreferences

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModelFactory = AppViewModelFactory(applicationContext)
        val prefs = UserPreferences(applicationContext)

        setContent {
            val darkModeOverride by prefs.darkModeOverride.collectAsState(initial = null)

            ReceiptScannerTheme(darkTheme = darkModeOverride) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(viewModelFactory = viewModelFactory)
                }
            }
        }
    }
}
