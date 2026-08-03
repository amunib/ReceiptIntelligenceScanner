package com.receiptintel.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    secondary = Amber,
    error = ErrorRed,
    background = SurfaceLight,
    surface = SurfaceLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color.Black,
    primaryContainer = TealPrimary,
    secondary = AmberDark,
    error = ErrorRedDark,
    background = SurfaceDark,
    surface = SurfaceDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark
)

/**
 * @param darkTheme when null, follows the system setting; otherwise this is
 * the user's explicit in-app preference from Settings (persisted via
 * DataStore — see [com.receiptintel.scanner.util.UserPreferences]).
 */
@Composable
fun ReceiptScannerTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val useDark = darkTheme ?: isSystemInDarkTheme()
    val colors = if (useDark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
