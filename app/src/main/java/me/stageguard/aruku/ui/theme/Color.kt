package me.stageguard.aruku.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val md_theme_light_primary = Color(0xFF006971)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFF84F3FF)
val md_theme_light_onPrimaryContainer = Color(0xFF002023)
val md_theme_light_secondary = Color(0xFF4A6365)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFCCE7EB)
val md_theme_light_onSecondaryContainer = Color(0xFF051F22)
val md_theme_light_tertiary = Color(0xFF505E7D)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFD8E2FF)
val md_theme_light_onTertiaryContainer = Color(0xFF0B1B36)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFFAFDFD)
val md_theme_light_onBackground = Color(0xFF191C1D)
val md_theme_light_surface = Color(0xFFFAFDFD)
val md_theme_light_onSurface = Color(0xFF191C1D)
val md_theme_light_surfaceVariant = Color(0xFFDAE4E5)
val md_theme_light_onSurfaceVariant = Color(0xFF3F484A)
val md_theme_light_outline = Color(0xFF6F797A)
val md_theme_light_inverseOnSurface = Color(0xFFEFF1F1)
val md_theme_light_inverseSurface = Color(0xFF2D3131)
val md_theme_light_inversePrimary = Color(0xFF4DD9E6)
val md_theme_light_shadow = Color(0xFF000000)
val md_theme_light_surfaceTint = Color(0xFF006971)
val md_theme_light_outlineVariant = Color(0xFFBEC8C9)
val md_theme_light_scrim = Color(0xFF000000)

val md_theme_dark_primary = Color(0xFF4DD9E6)
val md_theme_dark_onPrimary = Color(0xFF00363B)
val md_theme_dark_primaryContainer = Color(0xFF004F55)
val md_theme_dark_onPrimaryContainer = Color(0xFF84F3FF)
val md_theme_dark_secondary = Color(0xFFB1CBCE)
val md_theme_dark_onSecondary = Color(0xFF1C3437)
val md_theme_dark_secondaryContainer = Color(0xFF324B4E)
val md_theme_dark_onSecondaryContainer = Color(0xFFCCE7EB)
val md_theme_dark_tertiary = Color(0xFFB8C6EA)
val md_theme_dark_onTertiary = Color(0xFF22304C)
val md_theme_dark_tertiaryContainer = Color(0xFF384764)
val md_theme_dark_onTertiaryContainer = Color(0xFFD8E2FF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF191C1D)
val md_theme_dark_onBackground = Color(0xFFE0E3E3)
val md_theme_dark_surface = Color(0xFF191C1D)
val md_theme_dark_onSurface = Color(0xFFE0E3E3)
val md_theme_dark_surfaceVariant = Color(0xFF3F484A)
val md_theme_dark_onSurfaceVariant = Color(0xFFBEC8C9)
val md_theme_dark_outline = Color(0xFF899294)
val md_theme_dark_inverseOnSurface = Color(0xFF191C1D)
val md_theme_dark_inverseSurface = Color(0xFFE0E3E3)
val md_theme_dark_inversePrimary = Color(0xFF006971)
val md_theme_dark_shadow = Color(0xFF000000)
val md_theme_dark_surfaceTint = Color(0xFF4DD9E6)
val md_theme_dark_outlineVariant = Color(0xFF3F484A)
val md_theme_dark_scrim = Color(0xFF000000)

val ColorAccountOnline = Color(40, 232, 139)
val ColorAccountOffline = Color.Gray

val seed = Color(0xFF006971)


val ColorScheme.surface0: Color @Composable get() = surfaceColorAtElevation(ElevationTokens.Level0)
val ColorScheme.surface1: Color @Composable get() = surfaceColorAtElevation(ElevationTokens.Level1)
val ColorScheme.surface2: Color @Composable get() = surfaceColorAtElevation(ElevationTokens.Level2)
val ColorScheme.surface3: Color @Composable get() = surfaceColorAtElevation(ElevationTokens.Level3)
val ColorScheme.surface4: Color @Composable get() = surfaceColorAtElevation(ElevationTokens.Level4)
val ColorScheme.surface5: Color @Composable get() = surfaceColorAtElevation(ElevationTokens.Level5)

object ElevationTokens {
    val Level0 = 0.0.dp
    val Level1 = 1.0.dp
    val Level2 = 3.0.dp
    val Level3 = 6.0.dp
    val Level4 = 8.0.dp
    val Level5 = 12.0.dp
}