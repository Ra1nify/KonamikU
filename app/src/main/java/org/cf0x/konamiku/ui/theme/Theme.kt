package org.cf0x.konamiku.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import org.cf0x.konamiku.data.ColorSource
import org.cf0x.konamiku.data.ThemeMode

// CompositionLocal to allow components to check for expressive mode
val LocalExpressiveMode = staticCompositionLocalOf { true }

// Standard M3 Shapes - Strictly following M3 guidelines
val StandardShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Expressive M3 Shapes with bolder, larger radii
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun KonamikuTheme(
    themeMode: ThemeMode     = ThemeMode.SYSTEM,
    colorSource: ColorSource = ColorSource.PRESET,
    seedColor: Color         = Color(0xFF6750A4),
    isExpressive: Boolean    = true,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark  = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
    }

    val effectiveSeed = remember(colorSource, isDark) {
        if (colorSource == ColorSource.MONET && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = if (isDark) dynamicDarkColorScheme(context)
            else        dynamicLightColorScheme(context)
            scheme.primary
        } else {
            null
        }
    } ?: seedColor

    val dynamicTypography = getTypography(isExpressive)

    CompositionLocalProvider(LocalExpressiveMode provides isExpressive) {
        DynamicMaterialTheme(
            seedColor   = effectiveSeed,
            isDark      = isDark,
            animate     = true,
            style       = paletteStyle,
            typography  = dynamicTypography,
            shapes      = if (isExpressive) ExpressiveShapes else StandardShapes
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                content = content
            )
        }
    }
}
