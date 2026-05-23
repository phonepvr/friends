package com.phonepvr.friends.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Slight warmth on top of Material 3's defaults — bumps display + headline
 * weight a touch (medium / semi-bold) and gives line heights a friendlier
 * breathing room. No custom font file: keeps the APK lean and lets the system
 * provide the regional default (Roboto, Noto Sans, etc.).
 */
private val baseline = Typography()

val Typography = baseline.copy(
    displayLarge = baseline.displayLarge.warmHeading(),
    displayMedium = baseline.displayMedium.warmHeading(),
    displaySmall = baseline.displaySmall.warmHeading(),
    headlineLarge = baseline.headlineLarge.warmHeading(),
    headlineMedium = baseline.headlineMedium.warmHeading(),
    headlineSmall = baseline.headlineSmall.warmHeading(),
    titleLarge = baseline.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
    ),
    bodyLarge = baseline.bodyLarge.copy(lineHeight = 24.sp),
    bodyMedium = baseline.bodyMedium.copy(lineHeight = 22.sp),
)

private fun TextStyle.warmHeading(): TextStyle = copy(
    fontWeight = FontWeight.SemiBold,
    lineHeight = (lineHeight.value + 2f).sp,
)
