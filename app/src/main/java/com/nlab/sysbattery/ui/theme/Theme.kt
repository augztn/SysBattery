package com.nlab.sysbattery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nlab.sysbattery.R

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val SpaceGroteskFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

private val CenteredLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private val NoFontPaddingPlatformStyle = PlatformTextStyle(
    includeFontPadding = false
)

val SysBatteryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

fun buildSysBatteryTypography(fontPreset: String = "Modern (Plus Jakarta + Space)"): Typography {
    val (headingFamily, bodyFamily) = when (fontPreset) {
        "Plus Jakarta Sans" -> PlusJakartaSansFamily to PlusJakartaSansFamily
        "Space Grotesk" -> SpaceGroteskFamily to SpaceGroteskFamily
        "Technical Mono" -> JetBrainsMonoFamily to PlusJakartaSansFamily
        else -> SpaceGroteskFamily to PlusJakartaSansFamily
    }

    return Typography(
        displayLarge = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            lineHeight = 60.sp,
            letterSpacing = (-1.5).sp,
            fontFeatureSettings = "tnum",
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        displayMedium = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 42.sp,
            lineHeight = 46.sp,
            letterSpacing = (-0.8).sp,
            fontFeatureSettings = "tnum",
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        headlineLarge = TextStyle(
            fontFamily = headingFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.4).sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        headlineMedium = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.3).sp,
            fontFeatureSettings = "tnum",
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        headlineSmall = TextStyle(
            fontFamily = headingFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        titleLarge = TextStyle(
            fontFamily = headingFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = (-0.2).sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        titleMedium = TextStyle(
            fontFamily = headingFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        titleSmall = TextStyle(
            fontFamily = headingFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.1.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            letterSpacing = 0.15.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.15.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.25.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.35.sp,
            platformStyle = NoFontPaddingPlatformStyle,
            lineHeightStyle = CenteredLineHeightStyle
        )
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF0B1118),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF111924),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1D2939),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF070C12),
    surfaceContainerLow = Color(0xFF15202E),
    surfaceContainer = Color(0xFF1B2838),
    surfaceContainerHigh = Color(0xFF223246),
    surfaceContainerHighest = Color(0xFF2B3E56),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF263548)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1)
)

@Composable
fun SysBatteryTheme(
    themeMode: String = "System default",
    dynamicColor: Boolean = true,
    fontPreset: String = "Modern (Plus Jakarta + Space)",
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        else -> systemDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildSysBatteryTypography(fontPreset),
        shapes = SysBatteryShapes,
        content = content
    )
}
