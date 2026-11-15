package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AccentPreset(
    val id: String,
    val labelEn: String,
    val labelFa: String,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondaryDark: Color,
    val secondaryLight: Color
) {
    CYAN_PRISM(
        id = "SAMSUNG_BLUE",
        labelEn = "Samsung One UI Blue",
        labelFa = "آبی سامسونگ (One UI)",
        primaryDark = Color(0xFF3E82F7),
        primaryLight = Color(0xFF0381FE),
        secondaryDark = Color(0xFF68A0FF),
        secondaryLight = Color(0xFF0062CC)
    ),
    ROYAL_VIOLET(
        id = "GALAXY_INDIGO",
        labelEn = "Galaxy Indigo",
        labelFa = "نیلی کهکشانی (Galaxy)",
        primaryDark = Color(0xFF7C74FF),
        primaryLight = Color(0xFF5850EC),
        secondaryDark = Color(0xFFA5A0FF),
        secondaryLight = Color(0xFF4338CA)
    ),
    PERSIAN_TURQUOISE(
        id = "ONEUI_TEAL",
        labelEn = "One UI Teal",
        labelFa = "فیروزه‌ای ملایم",
        primaryDark = Color(0xFF22C5D6),
        primaryLight = Color(0xFF00838F),
        secondaryDark = Color(0xFF67E8F9),
        secondaryLight = Color(0xFF006064)
    ),
    GREEN_MATRIX(
        id = "ONEUI_EMERALD",
        labelEn = "One UI Emerald",
        labelFa = "سبز زمردی",
        primaryDark = Color(0xFF34D399),
        primaryLight = Color(0xFF059669),
        secondaryDark = Color(0xFF6EE7B7),
        secondaryLight = Color(0xFF047857)
    ),
    AMBER_GOLD(
        id = "ONEUI_GOLD",
        labelEn = "Warm Gold",
        labelFa = "طلایی ملایم",
        primaryDark = Color(0xFFFBBF24),
        primaryLight = Color(0xFFD97706),
        secondaryDark = Color(0xFFFCD34D),
        secondaryLight = Color(0xFFB45309)
    ),
    MAGENTA_NEON(
        id = "ONEUI_LAVENDER",
        labelEn = "Soft Lavender",
        labelFa = "یاسی ملایم",
        primaryDark = Color(0xFFA78BFA),
        primaryLight = Color(0xFF7C3AED),
        secondaryDark = Color(0xFFC4B5FD),
        secondaryLight = Color(0xFF6D28D9)
    ),
    CUSTOM_HUE(
        id = "CUSTOM",
        labelEn = "Custom Color",
        labelFa = "رنگ دلخواه",
        primaryDark = Color(0xFF3E82F7),
        primaryLight = Color(0xFF0381FE),
        secondaryDark = Color(0xFF68A0FF),
        secondaryLight = Color(0xFF0062CC)
    )
}

fun neonColorFromHue(hue: Float, saturation: Float = 0.72f, value: Float = 0.96f): Color {
    val hsv = floatArrayOf(hue.coerceIn(0f, 360f), saturation, value)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// Samsung One UI 6 / 7 Dark Surfaces (Pure AMOLED Black + Soft Elevated Grey Cards)
val OneUiDarkBackground = Color(0xFF000000)
val OneUiDarkSurface = Color(0xFF17171A)
val OneUiDarkSurfaceVariant = Color(0xFF222227)
val OneUiDarkOnSurface = Color(0xFFF2F2F7)
val OneUiDarkOnSurfaceVariant = Color(0xFF9898A2)
val OneUiDarkOutline = Color(0xFF2C2C34)

// Samsung One UI 6 / 7 Light Surfaces
val OneUiLightBackground = Color(0xFFF6F6F8)
val OneUiLightSurface = Color(0xFFFFFFFF)
val OneUiLightSurfaceVariant = Color(0xFFEBEBF0)
val OneUiLightOnSurface = Color(0xFF121216)
val OneUiLightOnSurfaceVariant = Color(0xFF63636E)
val OneUiLightOutline = Color(0xFFDCDCE4)
