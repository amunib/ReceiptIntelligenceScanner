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
    primaryContainer = TealContainerLight,
    onPrimaryContainer = OnTealContainerLight,
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private val DarkColors = darkColorScheme(
    primary = TealPrimaryDark,
    onPrimary = Color(0xFF00382E),
    primaryContainer = TealContainerDark,
    onPrimaryContainer = OnTealContainerDark,
    secondary = AmberDark,
    onSecondary = Color(0xFF452B00),
    secondaryContainer = Color(0xFF653E00),
    error = ErrorRedDark,
    onError = Color(0xFF600004),
    errorContainer = Color(0xFF8C1D18),
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
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
