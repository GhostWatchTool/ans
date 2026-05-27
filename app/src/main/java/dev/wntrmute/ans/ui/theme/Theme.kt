package dev.wntrmute.ans.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Monochrome Material 3 theme, ported from the GWTBC app (../kbc). A single
 * foreground gray on a dark surface emulates an old single-colour display;
 * emphasis comes from size and weight, not hue. The light scheme is the
 * channel-wise inverse of the dark one. Unlike GWTBC (which defaults to dark),
 * this app follows the system setting via isSystemInDarkTheme().
 */

private val ForegroundGray = Color(0xFFC4C4C4)
private val SurfaceGray = Color(0xFF1D1D1D)
private val BrightGray = Color(0xFFE4E4E4) // emphasis: headline, selected, errors
private val DimGray = Color(0xFF7E7E7E) // captions / secondary text
private val LiftGray = Color(0xFF262626) // cards / fields lifted off the surface

private val DarkColors = darkColorScheme(
    primary = ForegroundGray,
    onPrimary = SurfaceGray,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = BrightGray,
    secondary = ForegroundGray,
    onSecondary = SurfaceGray,
    secondaryContainer = Color(0xFF2E2E2E),
    onSecondaryContainer = BrightGray,
    background = SurfaceGray,
    onBackground = ForegroundGray,
    surface = SurfaceGray,
    onSurface = ForegroundGray,
    surfaceVariant = LiftGray,
    onSurfaceVariant = DimGray,
    outline = Color(0xFF545454),
    outlineVariant = Color(0xFF333333),
    error = BrightGray,
    onError = SurfaceGray,
)

// Channel-wise inverse of the dark scheme (each grayscale value v -> 0xFF - v).
private val ForegroundDark = Color(0xFF3B3B3B) // body text
private val SurfaceLight = Color(0xFFE2E2E2) // screen / cards
private val EmphasisDark = Color(0xFF1B1B1B) // headline, selected, errors
private val DimDark = Color(0xFF818181) // captions
private val LiftLight = Color(0xFFD9D9D9) // cards / fields lifted

private val LightColors = lightColorScheme(
    primary = ForegroundDark,
    onPrimary = SurfaceLight,
    primaryContainer = Color(0xFFCCCCCC),
    onPrimaryContainer = EmphasisDark,
    secondary = ForegroundDark,
    onSecondary = SurfaceLight,
    secondaryContainer = Color(0xFFD1D1D1),
    onSecondaryContainer = EmphasisDark,
    background = SurfaceLight,
    onBackground = ForegroundDark,
    surface = SurfaceLight,
    onSurface = ForegroundDark,
    surfaceVariant = LiftLight,
    onSurfaceVariant = DimDark,
    outline = Color(0xFFABABAB),
    outlineVariant = Color(0xFFCCCCCC),
    error = EmphasisDark,
    onError = SurfaceLight,
)

private val NumberStationTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 72.sp,
        lineHeight = 80.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
    ),
)

@Composable
fun NumberStationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NumberStationTypography,
        content = content,
    )
}
