package com.nlab.sysbattery.ui.theme

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.cos
import kotlin.math.sin

val LocalLiquidTheme = staticCompositionLocalOf { false }

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

val LiquidBatteryShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val SysBatteryTypography = Typography(
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
        fontFamily = SpaceGroteskFamily,
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
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    ),
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.35.sp,
        platformStyle = NoFontPaddingPlatformStyle,
        lineHeightStyle = CenteredLineHeightStyle
    )
)

private val LiquidColorScheme = darkColorScheme(
    primary = Color(0xFF2CE5B5),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0x4D064E3B),
    onPrimaryContainer = Color(0xFFB8FFEA),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0x4D0C4A6E),
    onSecondaryContainer = Color(0xFFD6F2FF),
    tertiary = Color(0xFFC084FC),
    onTertiary = Color(0xFF3B0764),
    tertiaryContainer = Color(0x4D581C87),
    onTertiaryContainer = Color(0xFFF3E8FF),
    background = Color(0xFF040A12),
    onBackground = Color(0xFFF4FAFF),
    surface = Color(0x990A1626),
    onSurface = Color(0xFFF4FAFF),
    surfaceVariant = Color(0x7316283E),
    onSurfaceVariant = Color(0xFFA8C5DA),
    surfaceContainerLowest = Color(0x7A06101C),
    surfaceContainerLow = Color(0x850B1B2E),
    surfaceContainer = Color(0x8F11243B),
    surfaceContainerHigh = Color(0x9918304C),
    surfaceContainerHighest = Color(0xAD213D5E),
    outline = Color(0xFF5CE1E6),
    outlineVariant = Color(0x4D7DD3FC)
)

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
fun liquidBorderBrush(alphaScale: Float = 1f): Brush {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    return Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = (0.48f * alphaScale).coerceIn(0f, 1f)),
            primary.copy(alpha = (0.42f * alphaScale).coerceIn(0f, 1f)),
            Color.White.copy(alpha = (0.08f * alphaScale).coerceIn(0f, 1f)),
            secondary.copy(alpha = (0.36f * alphaScale).coerceIn(0f, 1f))
        )
    )
}

@Composable
fun liquidSurfaceBrush(): Brush {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    return Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.13f),
            primary.copy(alpha = 0.06f),
            Color(0xFF0A192D).copy(alpha = 0.32f),
            secondary.copy(alpha = 0.08f)
        )
    )
}

@Composable
fun LiquidMeshBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_mesh_transition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid_mesh_phase"
    )

    val emerald = Color(0xFF10B981)
    val cyan = Color(0xFF0EA5E9)
    val violet = Color(0xFF8B5CF6)
    val deepBase = Color(0xFF040A12)
    val midBase = Color(0xFF071426)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(deepBase, midBase, deepBase)
            )
        )

        val w = size.width
        val h = size.height
        val maxDim = maxOf(w, h).coerceAtLeast(1f)

        // Orb 1: Bioluminescent Aqua-Emerald (top-left drifting)
        val orb1Center = Offset(
            x = w * (0.22f + 0.14f * cos(phase)),
            y = h * (0.18f + 0.08f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    emerald.copy(alpha = 0.28f),
                    emerald.copy(alpha = 0.09f),
                    Color.Transparent
                ),
                center = orb1Center,
                radius = maxDim * 0.55f
            ),
            radius = maxDim * 0.55f,
            center = orb1Center
        )

        // Orb 2: Electric Cyan (mid-right counter-drifting)
        val orb2Center = Offset(
            x = w * (0.82f - 0.15f * sin(phase * 0.85f)),
            y = h * (0.48f + 0.10f * cos(phase * 0.85f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    cyan.copy(alpha = 0.25f),
                    cyan.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = orb2Center,
                radius = maxDim * 0.58f
            ),
            radius = maxDim * 0.58f,
            center = orb2Center
        )

        // Orb 3: Iridescent Violet-Blue (bottom-left slow wave)
        val orb3Center = Offset(
            x = w * (0.30f + 0.12f * sin(phase + 1.4f)),
            y = h * (0.84f - 0.07f * cos(phase + 1.4f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    violet.copy(alpha = 0.20f),
                    cyan.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = orb3Center,
                radius = maxDim * 0.52f
            ),
            radius = maxDim * 0.52f,
            center = orb3Center
        )

        // Subtle refractive caustic wave ribbon
        val wavePath = Path().apply {
            val baseY = h * 0.34f
            moveTo(0f, baseY)
            val steps = 24
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val x = w * t
                val y = baseY + sin(t * 4.2f + phase) * 26.dp.toPx() + cos(t * 2.1f - phase) * 14.dp.toPx()
                lineTo(x, y)
            }
        }
        drawPath(
            path = wavePath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    emerald.copy(alpha = 0.16f),
                    cyan.copy(alpha = 0.20f),
                    Color.Transparent
                )
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun SysBatteryTheme(
    themeMode: String = "Liquid",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isLiquid = themeMode.equals("Liquid", ignoreCase = true)
    val dark = when {
        isLiquid -> true
        themeMode == "Light" -> false
        themeMode == "Dark" -> true
        else -> systemDark
    }

    val colorScheme = when {
        isLiquid -> LiquidColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalLiquidTheme provides isLiquid) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SysBatteryTypography,
            shapes = if (isLiquid) LiquidBatteryShapes else SysBatteryShapes,
            content = content
        )
    }
}

