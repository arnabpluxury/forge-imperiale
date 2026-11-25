package com.forge.imperiale.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ForgeThemeMode {
    AMOLED_DARK,
    LINKTREE_LIGHT,
    SYSTEM
}

@Immutable
data class ForgeExtendedColors(
    val isDark: Boolean,
    val background: Color,
    val cardSurface: Color,
    val cardElevated: Color,
    val cardBorder: Color,
    val heroBannerBg: Color,
    val heroBannerInk: Color,
    val navPillBg: Color,
    val navPillBorder: Color,
    val navActiveBg: Color,
    val navActiveInk: Color,
    val primaryPillBg: Color,
    val primaryPillInk: Color,
    val tuitionBg: Color,
    val tuitionInk: Color,
    val workoutBg: Color,
    val workoutInk: Color,
    val mealBg: Color,
    val mealInk: Color,
    val sleepBg: Color,
    val sleepInk: Color,
    val dangerBg: Color,
    val dangerInk: Color,
    val mutedText: Color
)

private val AmoledExtendedColors = ForgeExtendedColors(
    isDark = true,
    background = ForgePalette.TrueBlack,
    cardSurface = ForgePalette.Charcoal,
    cardElevated = ForgePalette.CharcoalElevated,
    cardBorder = ForgePalette.HairlineDark,
    heroBannerBg = ForgePalette.Chartreuse,
    heroBannerInk = ForgePalette.Forest,
    navPillBg = Color(0xF2121512),
    navPillBorder = Color(0x2EFFFFFF),
    navActiveBg = ForgePalette.Chartreuse,
    navActiveInk = ForgePalette.TrueBlack,
    primaryPillBg = ForgePalette.Chartreuse,
    primaryPillInk = ForgePalette.TrueBlack,
    tuitionBg = ForgePalette.Dahlia,
    tuitionInk = ForgePalette.Lavender,
    workoutBg = ForgePalette.Forest,
    workoutInk = ForgePalette.Chartreuse,
    mealBg = Color(0xFF2B2106),
    mealInk = ForgePalette.Marigold,
    sleepBg = Color(0xFF0A2430),
    sleepInk = ForgePalette.Sky,
    dangerBg = ForgePalette.Currant,
    dangerInk = Color.White,
    mutedText = ForgePalette.MutedOnDark
)

private val LinktreeLightExtendedColors = ForgeExtendedColors(
    isDark = false,
    background = ForgePalette.Alabaster,
    cardSurface = ForgePalette.PureWhite,
    cardElevated = ForgePalette.Chalk,
    cardBorder = ForgePalette.HairlineLight,
    heroBannerBg = ForgePalette.Chartreuse,
    heroBannerInk = ForgePalette.Forest,
    navPillBg = ForgePalette.PureWhite,
    navPillBorder = ForgePalette.HairlineLight,
    navActiveBg = ForgePalette.Forest,
    navActiveInk = ForgePalette.Chartreuse,
    primaryPillBg = ForgePalette.Forest,
    primaryPillInk = ForgePalette.Chartreuse,
    tuitionBg = ForgePalette.Lavender,
    tuitionInk = ForgePalette.Dahlia,
    workoutBg = ForgePalette.Celadon,
    workoutInk = ForgePalette.CeladonInk,
    mealBg = ForgePalette.CreamTint,
    mealInk = ForgePalette.Espresso,
    sleepBg = ForgePalette.IceBlue,
    sleepInk = ForgePalette.Iris,
    dangerBg = ForgePalette.Currant,
    dangerInk = Color.White,
    mutedText = ForgePalette.MutedOnLight
)

val LocalForgeColors = staticCompositionLocalOf { AmoledExtendedColors }

private val AmoledMaterialScheme = darkColorScheme(
    primary = ForgePalette.Chartreuse,
    onPrimary = ForgePalette.TrueBlack,
    primaryContainer = ForgePalette.Forest,
    onPrimaryContainer = ForgePalette.Chartreuse,
    secondary = ForgePalette.Canopy,
    onSecondary = ForgePalette.TrueBlack,
    secondaryContainer = ForgePalette.Dahlia,
    onSecondaryContainer = ForgePalette.Lavender,
    tertiary = ForgePalette.Marigold,
    onTertiary = ForgePalette.TrueBlack,
    background = ForgePalette.TrueBlack,
    onBackground = Color.White,
    surface = ForgePalette.Charcoal,
    onSurface = Color.White,
    surfaceVariant = ForgePalette.CharcoalElevated,
    onSurfaceVariant = ForgePalette.MutedOnDark,
    error = ForgePalette.Rose,
    onError = Color.White,
    outline = ForgePalette.Mortar
)

private val LinktreeLightMaterialScheme = lightColorScheme(
    primary = ForgePalette.Forest,
    onPrimary = ForgePalette.Chartreuse,
    primaryContainer = ForgePalette.Chartreuse,
    onPrimaryContainer = ForgePalette.Forest,
    secondary = ForgePalette.DeepEmerald,
    onSecondary = Color.White,
    secondaryContainer = ForgePalette.Celadon,
    onSecondaryContainer = ForgePalette.CeladonInk,
    tertiary = ForgePalette.Marigold,
    onTertiary = ForgePalette.Espresso,
    background = ForgePalette.Alabaster,
    onBackground = ForgePalette.InkLight,
    surface = ForgePalette.PureWhite,
    onSurface = ForgePalette.InkLight,
    surfaceVariant = ForgePalette.Chalk,
    onSurfaceVariant = ForgePalette.MutedOnLight,
    error = ForgePalette.Currant,
    onError = Color.White,
    outline = Color(0x3314171F)
)

@Composable
fun ForgeTheme(
    themeMode: ForgeThemeMode = ForgeThemeMode.AMOLED_DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ForgeThemeMode.AMOLED_DARK -> true
        ForgeThemeMode.LINKTREE_LIGHT -> false
        ForgeThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val materialScheme = if (useDark) AmoledMaterialScheme else LinktreeLightMaterialScheme
    val extendedColors = if (useDark) AmoledExtendedColors else LinktreeLightExtendedColors

    CompositionLocalProvider(LocalForgeColors provides extendedColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = ForgeTypography,
            content = content
        )
    }
}
