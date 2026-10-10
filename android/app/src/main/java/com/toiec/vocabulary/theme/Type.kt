package com.toiec.vocabulary.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default, fontWeight = weight, fontSize = size.sp,
    lineHeight = line.sp, letterSpacing = 0.sp,
)
val Typography = Typography(
    headlineLarge = style(40, 48, FontWeight.SemiBold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 30, FontWeight.SemiBold),
    titleMedium = style(18, 26, FontWeight.Medium),
    bodyLarge = style(18, 28), bodyMedium = style(16, 24), bodySmall = style(14, 22),
    labelLarge = style(16, 24, FontWeight.SemiBold),
    labelMedium = style(14, 20, FontWeight.Medium), labelSmall = style(12, 18, FontWeight.Medium),
)
