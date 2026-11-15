package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

enum class ThemeMode(val id: String, val labelEn: String, val labelFa: String) {
    DARK("DARK", "One UI Dark", "تیره سامسونگ (One UI Dark)"),
    LIGHT("LIGHT", "One UI Light", "روشن سامسونگ (One UI Light)"),
    SYSTEM("SYSTEM", "System Adaptive", "هماهنگ با سیستم")
}

@Composable
fun TakzGalleryTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    accentPreset: AccentPreset = AccentPreset.CYAN_PRISM,
    customNeonHue: Float = 215f,
    dynamicColor: Boolean = false,
    isRtl: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val activePrimaryDark = if (accentPreset == AccentPreset.CUSTOM_HUE) {
        neonColorFromHue(customNeonHue, saturation = 0.72f, value = 0.96f)
    } else {
        accentPreset.primaryDark
    }
    val activeSecondaryDark = if (accentPreset == AccentPreset.CUSTOM_HUE) {
        neonColorFromHue((customNeonHue + 25f) % 360f, saturation = 0.58f, value = 0.96f)
    } else {
        accentPreset.secondaryDark
    }

    val activePrimaryLight = if (accentPreset == AccentPreset.CUSTOM_HUE) {
        neonColorFromHue(customNeonHue, saturation = 0.82f, value = 0.85f)
    } else {
        accentPreset.primaryLight
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = activePrimaryDark,
            onPrimary = Color.White,
            primaryContainer = activePrimaryDark.copy(alpha = 0.20f),
            onPrimaryContainer = activePrimaryDark,
            secondary = activeSecondaryDark,
            onSecondary = Color(0xFF0A0A0E),
            secondaryContainer = OneUiDarkSurfaceVariant,
            onSecondaryContainer = OneUiDarkOnSurface,
            tertiary = activePrimaryDark,
            background = OneUiDarkBackground,
            onBackground = OneUiDarkOnSurface,
            surface = OneUiDarkSurface,
            onSurface = OneUiDarkOnSurface,
            surfaceVariant = OneUiDarkSurfaceVariant,
            onSurfaceVariant = OneUiDarkOnSurfaceVariant,
            outline = OneUiDarkOutline,
            error = Color(0xFFF87171),
            onError = Color.White
        )
        else -> lightColorScheme(
            primary = activePrimaryLight,
            onPrimary = Color.White,
            primaryContainer = activePrimaryLight.copy(alpha = 0.14f),
            onPrimaryContainer = activePrimaryLight,
            secondary = accentPreset.secondaryLight,
            onSecondary = Color.White,
            secondaryContainer = OneUiLightSurfaceVariant,
            onSecondaryContainer = OneUiLightOnSurface,
            tertiary = activePrimaryLight,
            background = OneUiLightBackground,
            onBackground = OneUiLightOnSurface,
            surface = OneUiLightSurface,
            onSurface = OneUiLightOnSurface,
            surfaceVariant = OneUiLightSurfaceVariant,
            onSurfaceVariant = OneUiLightOnSurfaceVariant,
            outline = OneUiLightOutline,
            error = Color(0xFFDC2626),
            onError = Color.White
        )
    }

    val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = buildTakzTypography(usePersianFont = true)

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography
        ) {
            androidx.compose.material3.ProvideTextStyle(
                value = androidx.compose.ui.text.TextStyle(fontFamily = VazirmatnFontFamily),
                content = content
            )
        }
    }
}
