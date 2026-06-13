package org.cf0x.konamiku.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.materialkolor.DynamicMaterialTheme
import com.materialkolor.PaletteStyle
import org.cf0x.konamiku.data.ColorSource
import org.cf0x.konamiku.data.ThemeMode

// Expressive M3 Shapes with larger radii
val ExpressiveShapes = Shapes(
    extraSmall = ShapeDefaults.ExtraSmall,
    small      = ShapeDefaults.Small,
    medium     = RoundedCornerShape(24.dp), // Increased from 12dp
    large      = RoundedCornerShape(28.dp), // Increased from 16dp
    extraLarge = RoundedCornerShape(32.dp)  // Increased from 28dp
)

@Composable
fun KonamikuTheme(
    themeMode: ThemeMode     = ThemeMode.SYSTEM,
    colorSource: ColorSource = ColorSource.PRESET,
    seedColor: Color         = Color(0xFF6750A4),
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

    DynamicMaterialTheme(
        seedColor  = effectiveSeed,
        isDark     = isDark,
        animate    = true,
        style      = PaletteStyle.Expressive, // Use Expressive palette style
        typography = Typography,
        shapes     = ExpressiveShapes,
        content    = content
    )
}
