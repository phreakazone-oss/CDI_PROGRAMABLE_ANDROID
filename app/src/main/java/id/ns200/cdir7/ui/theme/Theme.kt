package id.ns200.cdir7.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = MotecOrange,
    onPrimary = CarbonDark,
    primaryContainer = CardBackground,
    onPrimaryContainer = MotecOrange,
    secondary = ElectricCyan,
    onSecondary = CarbonDark,
    secondaryContainer = SurfacePanel,
    onSecondaryContainer = ElectricCyan,
    tertiary = RacingLime,
    onTertiary = CarbonDark,
    background = CarbonDark,
    onBackground = TextPrimary,
    surface = SurfacePanel,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderAccent,
    error = RaceRedline,
    onError = TextPrimary
)

@Composable
fun CdiR7Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
