package com.forge.imperiale.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.forge.imperiale.R

val ForgeDisplayFont = FontFamily(
    Font(R.font.bebas_neue_regular, FontWeight.Normal),
    Font(R.font.bebas_neue_regular, FontWeight.Bold)
)

val ForgeSansFont = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal),
    Font(R.font.manrope_variable, FontWeight.Medium),
    Font(R.font.manrope_variable, FontWeight.SemiBold),
    Font(R.font.manrope_variable, FontWeight.Bold),
    Font(R.font.manrope_variable, FontWeight.ExtraBold)
)

val ForgeMonoFont = FontFamily(
    Font(R.font.dm_mono_medium, FontWeight.Medium),
    Font(R.font.dm_mono_medium, FontWeight.Normal)
)

val ForgeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ForgeDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 52.sp,
        lineHeight = 50.sp,
        letterSpacing = 1.2.sp
    ),
    displayMedium = TextStyle(
        fontFamily = ForgeDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = 1.sp
    ),
    displaySmall = TextStyle(
        fontFamily = ForgeDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 33.sp,
        letterSpacing = 0.8.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = ForgeDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.7.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ForgeSansFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = ForgeSansFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ForgeSansFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ForgeSansFont,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ForgeMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ForgeSansFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ForgeSansFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ForgeSansFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = ForgeSansFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ForgeMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.9.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ForgeMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.8.sp
    )
)
