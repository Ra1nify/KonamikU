package org.cf0x.konamiku.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Dynamic Typography based on Expressive Mode
@Composable
fun getTypography(isExpressive: Boolean): Typography {
    val weight = if (isExpressive) FontWeight.SemiBold else FontWeight.Medium
    val tracking = if (isExpressive) (-0.02).sp else 0.sp
    val bodyWeight = FontWeight.Normal

    return Typography(
        headlineLarge = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 32.sp,
            lineHeight    = 40.sp,
            letterSpacing = tracking
        ),
        headlineSmall = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 24.sp,
            lineHeight    = 32.sp,
            letterSpacing = tracking
        ),
        titleLarge = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 22.sp,
            lineHeight    = 28.sp,
            letterSpacing = tracking
        ),
        titleMedium = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 16.sp,
            lineHeight    = 24.sp,
            letterSpacing = tracking
        ),
        bodyLarge = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = bodyWeight,
            fontSize      = 16.sp,
            lineHeight    = 24.sp,
            letterSpacing = 0.5.sp
        ),
        labelMedium = TextStyle(
            fontFamily    = FontFamily.Default,
            fontWeight    = weight,
            fontSize      = 12.sp,
            lineHeight    = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}

// Fallback static typography (defaulting to standard weights)
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily    = FontFamily.Default,
        fontWeight    = FontWeight.Normal,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = 0.5.sp
    )
)
