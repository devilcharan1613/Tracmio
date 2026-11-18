package com.tracmio.app.ui.nativeapp

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracmio.app.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val NunitoFontFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.SemiBold),
    Font(R.font.nunito, FontWeight.Bold),
    Font(R.font.nunito, FontWeight.ExtraBold)
)

val PlusJakartaFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val TracmioTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineSmall = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    ),
    titleSmall = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum"
    ),
    labelMedium = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = NunitoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

data class LiquidGlassSettings(
    val themePreset: String = "dark",
    val glassUiStyle: String = "ui1",
    val wallpaperPreset: String = "wp1",
    val backgroundVisibility: Float = 0.45f,
    val wallpaperBlurDp: Float = 8f,
    val glowIntensity: Float = 0.75f,
    val uiScale: Float = 1.0f,
    val deviceMotionEnabled: Boolean = false,
    val animationStyle: Int = 1,
    val customPrimaryHex: String = "#00F5D4",
    val customSecondaryHex: String = "#A855F7",
    val privacyMaskEnabled: Boolean = false,
    val privacyMaskActive: Boolean = false,
    val stealthModeActive: Boolean = false,
    val customWallpaperPath: String? = null,
    val userName: String = "Charan",
    val currencySymbol: String = "₹",
    val hasCompletedOnboarding: Boolean = false,
    val roundUpEnabled: Boolean = false
) {
    companion object {
        val ALLOWED_CURRENCIES = listOf("₹", "$", "€", "£")
    }
}

data class ThemePalette(
    val id: String,
    val displayName: String,
    val isLight: Boolean,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val backgroundMid: Color,
    val backgroundBottom: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val meshGlow1: Color,
    val meshGlow2: Color
)

object ThemePresets {
    val ALL_THEMES: List<ThemePalette> = listOf(
        ThemePalette(
            id = "dark",
            displayName = "Aurora Teal",
            isLight = false,
            primary = Color(0xFF00F5D4),
            secondary = Color(0xFFA855F7),
            tertiary = Color(0xFFFBBF24),
            background = Color(0xFF030B10),
            backgroundMid = Color(0xFF06161F),
            backgroundBottom = Color(0xFF030910),
            surface = Color(0xCC0A2534),
            surfaceElevated = Color(0xE010364A),
            surfaceBorder = Color(0x477DF5FF),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFC2D6E2),
            meshGlow1 = Color(0xFF00D6A3),
            meshGlow2 = Color(0xFF7E22CE)
        ),
        ThemePalette(
            id = "tracmio",
            displayName = "Tracmio Neon",
            isLight = false,
            primary = Color(0xFF18F2D0),
            secondary = Color(0xFF10B981),
            tertiary = Color(0xFF38BDF8),
            background = Color(0xFF021A16),
            backgroundMid = Color(0xFF052922),
            backgroundBottom = Color(0xFF031411),
            surface = Color(0xC708332B),
            surfaceElevated = Color(0xE00E463B),
            surfaceBorder = Color(0x4718F2D0),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFBBE7DF),
            meshGlow1 = Color(0xFF10B981),
            meshGlow2 = Color(0xFF00D6A3)
        ),
        ThemePalette(
            id = "obsidian",
            displayName = "Obsidian Gold",
            isLight = false,
            primary = Color(0xFFF5A623),
            secondary = Color(0xFFFBBF24),
            tertiary = Color(0xFF38BDF8),
            background = Color(0xFF100E09),
            backgroundMid = Color(0xFF1A160D),
            backgroundBottom = Color(0xFF0B0A07),
            surface = Color(0xCC211B10),
            surfaceElevated = Color(0xE62E2616),
            surfaceBorder = Color(0x4DF5A623),
            textPrimary = Color(0xFFFFFBF0),
            textSecondary = Color(0xFFD6CBB5),
            meshGlow1 = Color(0xFFF5A623),
            meshGlow2 = Color(0xFF92400E)
        ),
        ThemePalette(
            id = "sapphire",
            displayName = "Sapphire Prism",
            isLight = false,
            primary = Color(0xFF38BDF8),
            secondary = Color(0xFF818CF8),
            tertiary = Color(0xFF00F5D4),
            background = Color(0xFF051126),
            backgroundMid = Color(0xFF0B1C3D),
            backgroundBottom = Color(0xFF040D1E),
            surface = Color(0xC70E234A),
            surfaceElevated = Color(0xE0163161),
            surfaceBorder = Color(0x4738BDF8),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFC4D6F2),
            meshGlow1 = Color(0xFF38BDF8),
            meshGlow2 = Color(0xFF6366F1)
        ),
        ThemePalette(
            id = "light",
            displayName = "Arctic Pearl",
            isLight = true,
            primary = Color(0xFF00897B),
            secondary = Color(0xFF0284C7),
            tertiary = Color(0xFFD97706),
            background = Color(0xFFEAF4F7),
            backgroundMid = Color(0xFFF2F9FB),
            backgroundBottom = Color(0xFFE0EFF4),
            surface = Color(0xDEFFFFFF),
            surfaceElevated = Color(0xF5F7FCFE),
            surfaceBorder = Color(0x5500897B),
            textPrimary = Color(0xFF0F2936),
            textSecondary = Color(0xFF436475),
            meshGlow1 = Color(0xFF2DD4BF),
            meshGlow2 = Color(0xFF38BDF8)
        ),
        ThemePalette(
            id = "ruby",
            displayName = "Cyber Ruby",
            isLight = false,
            primary = Color(0xFFFB7185),
            secondary = Color(0xFFA855F7),
            tertiary = Color(0xFF00F5D4),
            background = Color(0xFF1B0710),
            backgroundMid = Color(0xFF2B0B19),
            backgroundBottom = Color(0xFF14050B),
            surface = Color(0xC7360F20),
            surfaceElevated = Color(0xE04A142C),
            surfaceBorder = Color(0x47FB7185),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFEAC2CD),
            meshGlow1 = Color(0xFFFB7185),
            meshGlow2 = Color(0xFF9333EA)
        ),
        ThemePalette(
            id = "emerald",
            displayName = "Matrix Emerald",
            isLight = false,
            primary = Color(0xFF34D399),
            secondary = Color(0xFF22D3EE),
            tertiary = Color(0xFFFBBF24),
            background = Color(0xFF031910),
            backgroundMid = Color(0xFF06291A),
            backgroundBottom = Color(0xFF02120B),
            surface = Color(0xC7093322),
            surfaceElevated = Color(0xE00E462F),
            surfaceBorder = Color(0x4734D399),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFB7E4CE),
            meshGlow1 = Color(0xFF34D399),
            meshGlow2 = Color(0xFF059669)
        ),
        ThemePalette(
            id = "amethyst",
            displayName = "Amethyst Nebula",
            isLight = false,
            primary = Color(0xFFC084FC),
            secondary = Color(0xFF38BDF8),
            tertiary = Color(0xFFFB7185),
            background = Color(0xFF120822),
            backgroundMid = Color(0xFF1D0E36),
            backgroundBottom = Color(0xFF0D051A),
            surface = Color(0xC728144A),
            surfaceElevated = Color(0xE0361B63),
            surfaceBorder = Color(0x47C084FC),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFD9C7EE),
            meshGlow1 = Color(0xFFA855F7),
            meshGlow2 = Color(0xFF06B6D4)
        ),
        ThemePalette(
            id = "titanium",
            displayName = "Titanium Amber",
            isLight = false,
            primary = Color(0xFFFBBF24),
            secondary = Color(0xFF94A3B8),
            tertiary = Color(0xFF38BDF8),
            background = Color(0xFF111418),
            backgroundMid = Color(0xFF1A1F26),
            backgroundBottom = Color(0xFF0B0E11),
            surface = Color(0xCC222831),
            surfaceElevated = Color(0xE62E3642),
            surfaceBorder = Color(0x47FBBF24),
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFFCBD5E1),
            meshGlow1 = Color(0xFFFBBF24),
            meshGlow2 = Color(0xFF475569)
        ),
        ThemePalette(
            id = "ocean",
            displayName = "Deep Ocean Aqua",
            isLight = false,
            primary = Color(0xFF22D3EE),
            secondary = Color(0xFF0EA5E9),
            tertiary = Color(0xFF00F5D4),
            background = Color(0xFF021420),
            backgroundMid = Color(0xFF042134),
            backgroundBottom = Color(0xFF010E17),
            surface = Color(0xC7072B42),
            surfaceElevated = Color(0xE00B3B59),
            surfaceBorder = Color(0x4722D3EE),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFBAE0F5),
            meshGlow1 = Color(0xFF22D3EE),
            meshGlow2 = Color(0xFF0284C7)
        ),
        ThemePalette(
            id = "custom",
            displayName = "Custom Neon",
            isLight = false,
            primary = Color(0xFF00F5D4),
            secondary = Color(0xFFA855F7),
            tertiary = Color(0xFF38BDF8),
            background = Color(0xFF061621),
            backgroundMid = Color(0xFF0A2434),
            backgroundBottom = Color(0xFF040F17),
            surface = Color(0xC70E2E42),
            surfaceElevated = Color(0xE0143D56),
            surfaceBorder = Color(0x5500F5D4),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFC2E2EE),
            meshGlow1 = Color(0xFF00F5D4),
            meshGlow2 = Color(0xFFA855F7)
        )
    )

    val WALLPAPER_PRESETS: List<Pair<String, String>> = listOf(
        "wp1" to "Aurora Cyan Mesh",
        "wp2" to "Cosmic Violet Wave",
        "wp3" to "Emerald Horizon",
        "wp4" to "Solar Gold Halo",
        "wp5" to "Midnight Sapphire",
        "wp6" to "Coral Rose Mist",
        "wp7" to "Arctic Frost",
        "wp8" to "Deep Trench",
        "wp9" to "Neon Matrix",
        "wp10" to "Prism Borealis"
    )

    val GLASS_UI_STYLES: List<Pair<String, String>> = listOf(
        "ui1" to "Classic Liquid Glass",
        "ui2" to "Frosted Diamond",
        "ui3" to "Neon Cyber Edge",
        "ui4" to "3D Floating Prism",
        "ui5" to "Velvet Matte Smoke",
        "ui6" to "Liquid Mercury Chrome",
        "ui7" to "Emerald Bio-Glass",
        "ui8" to "Royal Gold Luxe",
        "ui9" to "Tactical HUD Grid",
        "ui10" to "Ultra-Round Bubble"
    )

    val ANIMATION_STYLES: List<Pair<Int, String>> = listOf(
        1 to "Silk Liquid Glide",
        2 to "Spring Elastic Pop",
        3 to "Hyper-Snap Instant",
        4 to "3D Depth Flip",
        5 to "Vortex Zoom Rise",
        6 to "Crystal Blur Resolve",
        7 to "Kinetic Lift Up",
        8 to "Liquid Ripple Morph",
        9 to "Glass Refraction Slide",
        10 to "Aurora Horizon Glide"
    )

    private fun parseHexColorOrDefault(hex: String, fallback: Color): Color {
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6 && cleaned.length != 8) return fallback
        return runCatching {
            val value = cleaned.toLong(16)
            if (cleaned.length == 6) Color(0xFF000000L or value) else Color(value)
        }.getOrDefault(fallback)
    }

    fun getPalette(preset: String, primaryHex: String = "#00F5D4", secondaryHex: String = "#A855F7"): ThemePalette {
        val base = ALL_THEMES.firstOrNull { it.id == preset } ?: ALL_THEMES.first()
        if (preset == "custom") {
            val p = parseHexColorOrDefault(primaryHex, base.primary)
            val s = parseHexColorOrDefault(secondaryHex, base.secondary)
            return base.copy(
                primary = p,
                secondary = s,
                surfaceBorder = p.copy(alpha = 0.42f),
                meshGlow1 = p,
                meshGlow2 = s
            )
        }
        return base
    }
}

@Composable
fun TracmioNativeTheme(
    settings: LiquidGlassSettings = LiquidGlassSettings(),
    content: @Composable () -> Unit
) {
    val palette = ThemePresets.getPalette(settings.themePreset, settings.customPrimaryHex, settings.customSecondaryHex)
    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity, settings.uiScale) {
        val factor = settings.uiScale.coerceIn(0.8f, 1.3f)
        Density(density = baseDensity.density * factor, fontScale = baseDensity.fontScale)
    }

    val colorScheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceElevated,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceElevated,
            surfaceContainerHighest = palette.surfaceElevated,
            outline = palette.surfaceBorder,
            onPrimary = Color.White,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary
        )
    } else {
        darkColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceElevated,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceElevated,
            surfaceContainerHighest = palette.surfaceElevated,
            outline = palette.surfaceBorder,
            onPrimary = palette.background,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary
        )
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TracmioTypography,
            content = content
        )
    }
}

fun tracmioTransitionSpec(animationStyle: Int): ContentTransform {
    val silkEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val elasticEase = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val kineticEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    return when (animationStyle) {
        2 -> (fadeIn(tween(340, easing = elasticEase)) +
            scaleIn(initialScale = 0.92f, animationSpec = tween(400, easing = elasticEase)) +
            slideInVertically(animationSpec = tween(380, easing = elasticEase)) { it / 16 }) togetherWith
            fadeOut(tween(180))
        3 -> (fadeIn(tween(140, easing = LinearEasing)) +
            slideInVertically(animationSpec = tween(140, easing = LinearEasing)) { it / 48 }) togetherWith
            fadeOut(tween(110))
        4 -> (fadeIn(tween(340, easing = silkEase)) +
            scaleIn(initialScale = 0.95f, animationSpec = tween(360, easing = silkEase)) +
            slideInHorizontally(animationSpec = tween(360, easing = silkEase)) { -it / 16 }) togetherWith
            (fadeOut(tween(220)) + scaleOut(targetScale = 0.96f, animationSpec = tween(220)))
        5 -> (fadeIn(tween(380, easing = silkEase)) +
            scaleIn(initialScale = 0.90f, animationSpec = tween(380, easing = silkEase))) togetherWith
            (fadeOut(tween(200)) + scaleOut(targetScale = 1.04f, animationSpec = tween(200)))
        6 -> (fadeIn(tween(360, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.97f, animationSpec = tween(360, easing = FastOutSlowInEasing))) togetherWith
            fadeOut(tween(220))
        7 -> (fadeIn(tween(300, easing = kineticEase)) +
            slideInVertically(animationSpec = tween(320, easing = kineticEase)) { it / 10 }) togetherWith
            (fadeOut(tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 24 })
        8 -> (fadeIn(tween(380, easing = elasticEase)) +
            scaleIn(initialScale = 0.94f, animationSpec = tween(400, easing = elasticEase))) togetherWith
            fadeOut(tween(220))
        9 -> (fadeIn(tween(300, easing = FastOutSlowInEasing)) +
            slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { it / 10 }) togetherWith
            (fadeOut(tween(220)) + slideOutHorizontally(animationSpec = tween(220)) { -it / 14 })
        10 -> (fadeIn(tween(380, easing = silkEase)) +
            slideInVertically(animationSpec = tween(380, easing = silkEase)) { -it / 18 } +
            scaleIn(initialScale = 0.98f, animationSpec = tween(380, easing = silkEase))) togetherWith
            fadeOut(tween(220))
        else -> (fadeIn(tween(320, easing = silkEase)) +
            slideInVertically(animationSpec = tween(340, easing = silkEase)) { it / 20 } +
            scaleIn(initialScale = 0.985f, animationSpec = tween(340, easing = silkEase))) togetherWith
            fadeOut(tween(200))
    }
}

@Composable
fun GlassCard(
    settings: LiquidGlassSettings,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val palette = ThemePresets.getPalette(settings.themePreset, settings.customPrimaryHex, settings.customSecondaryHex)
    val styleId = settings.glassUiStyle
    val resolvedShape: Shape = shape ?: when (styleId) {
        "ui6" -> RoundedCornerShape(24.dp)
        "ui7" -> RoundedCornerShape(18.dp)
        "ui9" -> RoundedCornerShape(14.dp)
        "ui10" -> RoundedCornerShape(28.dp)
        else -> RoundedCornerShape(22.dp)
    }
    val resolvedBorderWidth: Dp = borderWidth ?: when (styleId) {
        "ui3" -> 2.0.dp
        "ui7" -> 1.0.dp
        "ui8" -> 1.8.dp
        "ui9" -> 1.2.dp
        "ui10" -> 2.1.dp
        else -> 1.4.dp
    }
    val alphaFactor = when (styleId) {
        "ui2" -> 0.86f
        "ui4" -> 0.95f
        "ui5" -> 0.96f
        "ui7" -> 0.68f
        "ui10" -> 0.98f
        else -> 0.88f
    }

    val bgBrush = if (backgroundColor != null) {
        Brush.verticalGradient(listOf(backgroundColor, backgroundColor.copy(alpha = (backgroundColor.alpha * 0.92f))))
    } else if (palette.isLight) {
        Brush.verticalGradient(listOf(palette.surfaceElevated, palette.surface))
    } else if (styleId == "ui5") {
        Brush.verticalGradient(listOf(Color(0xF00B131C), Color(0xF2050A10)))
    } else if (styleId == "ui6") {
        Brush.linearGradient(listOf(Color(0xD41B3544), Color(0xD00B1C28), Color(0xD4172D3D)))
    } else if (palette.id == "dark" && styleId == "ui1") {
        Brush.verticalGradient(listOf(Color(0xC70F3A4E), Color(0xB8092B3C)))
    } else {
        Brush.verticalGradient(
            listOf(
                palette.surfaceElevated.copy(alpha = alphaFactor),
                palette.surface.copy(alpha = (alphaFactor * 0.90f).coerceIn(0.4f, 0.98f))
            )
        )
    }

    val strokeBrush = if (borderColor != null) {
        Brush.verticalGradient(listOf(borderColor.copy(alpha = 0.85f), borderColor))
    } else {
        when (styleId) {
            "ui3" -> Brush.linearGradient(listOf(palette.primary, palette.secondary, palette.primary.copy(alpha = 0.65f)))
            "ui6" -> Brush.verticalGradient(listOf(Color(0xBBFFFFFF), Color(0x5594A3B8), palette.primary.copy(alpha = 0.45f)))
            "ui7" -> Brush.verticalGradient(listOf(Color(0x5510B981), Color(0x3300F5D4)))
            "ui8" -> Brush.verticalGradient(listOf(Color(0xAAFDE68A), Color(0x88F59E0B), Color(0x44FDE68A)))
            "ui10" -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), palette.primary))
            else -> Brush.verticalGradient(listOf(Color(0x3DFFFFFF), palette.surfaceBorder))
        }
    }

    val shadowModifier = when (styleId) {
        "ui4" -> Modifier.shadow(14.dp, resolvedShape, ambientColor = palette.primary.copy(alpha = 0.35f), spotColor = palette.secondary.copy(alpha = 0.35f))
        "ui3" -> Modifier.shadow(10.dp, resolvedShape, ambientColor = palette.primary.copy(alpha = 0.45f), spotColor = palette.primary.copy(alpha = 0.45f))
        else -> Modifier
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .clip(resolvedShape)
            .background(bgBrush)
            .border(width = resolvedBorderWidth, brush = strokeBrush, shape = resolvedShape)
            .then(
                if (styleId == "ui8") {
                    Modifier.padding(2.dp).border(0.8.dp, Color(0x66FBBF24), resolvedShape)
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        // Version 2.5 subtle top specular glass rim shine
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (palette.isLight) 0.55f else 0.24f),
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun GlassBadge3D(
    emoji: String,
    accent: Color,
    sizeDp: Dp
) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.12f))
                )
            )
            .border(1.2.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(text = emoji)
    }
}

@Composable
fun LiquidMeshBackground(
    settings: LiquidGlassSettings,
    tiltXProvider: () -> Float,
    tiltYProvider: () -> Float
) {
    val palette = ThemePresets.getPalette(settings.themePreset, settings.customPrimaryHex, settings.customSecondaryHex)
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_mesh")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_phase"
    )

    val customBitmap = remember(settings.customWallpaperPath) {
        settings.customWallpaperPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }
    }
    val blurDp = settings.wallpaperBlurDp.coerceIn(0f, 32f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(palette.background, palette.backgroundMid, palette.backgroundBottom)
                )
            )
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = "Custom Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurDp > 0.5f) {
                            Modifier.blur(blurDp.dp)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer { alpha = settings.backgroundVisibility.coerceIn(0.05f, 1f) }
            )
            if (blurDp > 0.5f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background.copy(alpha = (blurDp / 32f) * 0.34f))
                )
            }
        }

        // GPU-composited radial aurora orbs (zero per-frame .blur() modifier)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = tiltXProvider() * 14f
                    translationY = tiltYProvider() * 14f
                }
        ) {
            val glowAlpha = (0.26f * settings.glowIntensity).coerceIn(0.08f, 0.52f)
            val (orb1Color, orb2Color, orb3Color) = when (settings.wallpaperPreset) {
                "wp2" -> Triple(Color(0xFFA855F7), Color(0xFF6366F1), palette.primary)
                "wp3" -> Triple(Color(0xFF10B981), Color(0xFF00D6A3), Color(0xFF38BDF8))
                "wp4" -> Triple(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFA855F7))
                "wp5" -> Triple(Color(0xFF38BDF8), Color(0xFF3B82F6), Color(0xFF00F5D4))
                "wp6" -> Triple(Color(0xFFFB7185), Color(0xFFF43F5E), Color(0xFFA855F7))
                "wp7" -> Triple(Color(0xFF67E8F9), Color(0xFFBAE6FD), palette.primary)
                "wp8" -> Triple(Color(0xFF0284C7), Color(0xFF0E7490), Color(0xFF1E3A8A))
                "wp9" -> Triple(Color(0xFF34D399), Color(0xFF00F5D4), Color(0xFF8B5CF6))
                "wp10" -> Triple(Color(0xFFC084FC), Color(0xFF00F5D4), Color(0xFFFBBF24))
                else -> Triple(
                    palette.meshGlow1,
                    if (palette.id == "dark") Color(0xFF6B1035) else palette.meshGlow2,
                    palette.primary
                )
            }
            val orb1Center = Offset(
                x = size.width * (0.20f + 0.06f * cos(phase)),
                y = size.height * (0.14f + 0.05f * sin(phase))
            )
            val orb2Center = Offset(
                x = size.width * (0.68f - 0.06f * sin(phase)),
                y = size.height * (0.50f + 0.06f * cos(phase))
            )
            val orb3Center = Offset(
                x = size.width * (0.35f + 0.05f * sin(phase * 0.7f)),
                y = size.height * (0.86f - 0.04f * cos(phase * 0.7f))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb1Color.copy(alpha = glowAlpha), Color.Transparent),
                    center = orb1Center,
                    radius = size.width * 0.78f
                ),
                center = orb1Center,
                radius = size.width * 0.78f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb2Color.copy(alpha = glowAlpha * 0.95f), Color.Transparent),
                    center = orb2Center,
                    radius = size.width * 0.72f
                ),
                center = orb2Center,
                radius = size.width * 0.72f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(orb3Color.copy(alpha = glowAlpha * 0.55f), Color.Transparent),
                    center = orb3Center,
                    radius = size.width * 0.65f
                ),
                center = orb3Center,
                radius = size.width * 0.65f
            )
        }
    }
}
