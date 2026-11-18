package com.tracmio.app.ui.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LiquidGlassComponents(
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        settings = settings,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundColor = palette.surface.copy(alpha = 0.84f),
        borderColor = palette.primary.copy(alpha = 0.36f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        if (palette.isLight) {
                            listOf(palette.surfaceElevated.copy(alpha = 0.90f), palette.surface.copy(alpha = 0.82f))
                        } else {
                            listOf(Color(0xD0103444), Color(0xD9081B2A))
                        }
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun TracmioBrandBadge(
    sizeDp: Dp = 42.dp,
    accent: Color = Color(0xFF00F5D4)
) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = accent.copy(alpha = 0.55f),
                spotColor = accent.copy(alpha = 0.65f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A3A3A), Color(0xFF041C24), Color(0xFF072926))
                )
            )
            .border(
                width = 1.4.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFFA7F3D0), accent, Color(0xFF10B981))
                ),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val w = size.width
            val h = size.height
            // 3 rising green-cyan chart bars
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF059669))),
                topLeft = Offset(w * 0.08f, h * 0.60f),
                size = Size(w * 0.20f, h * 0.32f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF34D399), Color(0xFF047857))),
                topLeft = Offset(w * 0.38f, h * 0.42f),
                size = Size(w * 0.20f, h * 0.50f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF2DD4BF), Color(0xFF0D9488))),
                topLeft = Offset(w * 0.68f, h * 0.24f),
                size = Size(w * 0.20f, h * 0.68f),
                cornerRadius = CornerRadius(3f, 3f)
            )
            // Sweeping glowing upward growth arrow
            val arrowPath = Path().apply {
                moveTo(w * 0.06f, h * 0.66f)
                cubicTo(w * 0.34f, h * 0.56f, w * 0.60f, h * 0.34f, w * 0.88f, h * 0.12f)
            }
            drawPath(
                path = arrowPath,
                brush = Brush.horizontalGradient(listOf(Color(0xFF86EFAC), Color(0xFF5EEAD4))),
                style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            val tipPath = Path().apply {
                moveTo(w * 0.64f, h * 0.12f)
                lineTo(w * 0.90f, h * 0.10f)
                lineTo(w * 0.86f, h * 0.34f)
            }
            drawPath(
                path = tipPath,
                color = Color(0xFFA7F3D0),
                style = Stroke(width = 4.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

@Composable
fun HeroAuroraWaveBackdrop(
    isLight: Boolean,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (isLight) {
            drawRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFF2FCFA), Color(0xFFE0F7F4), Color(0xFFEDE9FE))
                )
            )
            return@Canvas
        }

        // Base dark cyan-to-violet glass depth
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF084B55),
                    Color(0xFF0A273B),
                    Color(0xFF28194B)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        // Sweeping top-left diagonal cyan aurora wave
        val cyanWave = Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.78f, 0f)
            cubicTo(
                w * 0.64f, h * 0.28f,
                w * 0.42f, h * 0.44f,
                0f, h * 0.62f
            )
            close()
        }
        drawPath(
            path = cyanWave,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF42FFE0).copy(alpha = 0.76f),
                    primary.copy(alpha = 0.56f),
                    Color(0xFF0E7490).copy(alpha = 0.22f)
                ),
                start = Offset(w * 0.55f, 0f),
                end = Offset(0f, h * 0.62f)
            )
        )

        // Overlapping curved right purple/violet wave
        val purpleWave = Path().apply {
            moveTo(w * 0.78f, 0f)
            lineTo(w, 0f)
            lineTo(w, h * 0.54f)
            cubicTo(
                w * 0.80f, h * 0.60f,
                w * 0.56f, h * 0.46f,
                w * 0.42f, h * 0.38f
            )
            cubicTo(
                w * 0.58f, h * 0.24f,
                w * 0.70f, h * 0.12f,
                w * 0.78f, 0f
            )
            close()
        }
        drawPath(
            path = purpleWave,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFA78BFA).copy(alpha = 0.62f),
                    secondary.copy(alpha = 0.48f),
                    Color(0xFF311B5C).copy(alpha = 0.18f)
                ),
                start = Offset(w * 0.85f, 0f),
                end = Offset(w * 0.48f, h * 0.55f)
            )
        )

        // Crisp luminous specular wave ridge line
        val ridgePath = Path().apply {
            moveTo(w * 0.78f, 0f)
            cubicTo(
                w * 0.63f, h * 0.27f,
                w * 0.44f, h * 0.42f,
                w * 0.10f, h * 0.56f
            )
        }
        drawPath(
            path = ridgePath,
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    Color(0xFFE0F2FE).copy(alpha = 0.85f),
                    Color(0xFFA5F3FC).copy(alpha = 0.90f)
                )
            ),
            style = Stroke(width = 3.2f, cap = StrokeCap.Round)
        )

        // Soft bottom-right magenta/violet glass glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(w * 0.90f, h * 0.92f),
                radius = w * 0.55f
            ),
            center = Offset(w * 0.90f, h * 0.92f),
            radius = w * 0.55f
        )
    }
}

@Composable
fun MonthlyPaceNeedleTrack(
    spentFraction: Float,
    expectedFraction: Float,
    isOnTrack: Boolean,
    primary: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        val trackHeight = 6.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

        // Track background
        drawRoundRect(
            color = Color(0xFF283542),
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = corner
        )

        // Spent progress fill
        val clampedSpend = spentFraction.coerceIn(0f, 1f)
        if (clampedSpend > 0.005f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    if (isOnTrack) listOf(primary.copy(alpha = 0.75f), primary)
                    else listOf(Color(0xFFF59E0B), Color(0xFFFB7185))
                ),
                topLeft = Offset(0f, trackTop),
                size = Size((size.width * clampedSpend).coerceAtLeast(trackHeight), trackHeight),
                cornerRadius = corner
            )
        }

        // Expected day-of-month glowing white vertical needle marker
        val needleW = 3.5.dp.toPx()
        val needleH = 15.dp.toPx()
        val needleX = (size.width * expectedFraction.coerceIn(0.03f, 0.97f)) - (needleW / 2f)
        val needleTop = (size.height - needleH) / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.30f),
            topLeft = Offset(needleX - 2.dp.toPx(), needleTop - 1.dp.toPx()),
            size = Size(needleW + 4.dp.toPx(), needleH + 2.dp.toPx()),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(needleX, needleTop),
            size = Size(needleW, needleH),
            cornerRadius = CornerRadius(needleW / 2f, needleW / 2f)
        )
    }
}

enum class QuickStatIconType {
    BORROWED,
    LENT,
    NET_WORTH,
    TRANSACTIONS
}

@Composable
fun QuickStatGlassBadge(
    type: QuickStatIconType,
    sizeDp: Dp = 46.dp
) {
    val (bgColors, borderColors) = when (type) {
        QuickStatIconType.BORROWED ->
            listOf(Color(0xFF5B21B6).copy(alpha = 0.72f), Color(0xFF3B0764).copy(alpha = 0.85f)) to
                listOf(Color(0xFFD8B4FE), Color(0xFF9333EA))
        QuickStatIconType.LENT ->
            listOf(Color(0xFFD97706).copy(alpha = 0.72f), Color(0xFF78350F).copy(alpha = 0.88f)) to
                listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))
        QuickStatIconType.NET_WORTH ->
            listOf(Color(0xFF0E7490).copy(alpha = 0.75f), Color(0xFF083344).copy(alpha = 0.88f)) to
                listOf(Color(0xFFA5F3FC), Color(0xFF06B6D4))
        QuickStatIconType.TRANSACTIONS ->
            listOf(Color(0xFF1D4ED8).copy(alpha = 0.72f), Color(0xFF1E1B4B).copy(alpha = 0.88f)) to
                listOf(Color(0xFF93C5FD), Color(0xFF6366F1))
    }

    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(bgColors))
            .border(1.3.dp, Brush.linearGradient(borderColors), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp * 0.56f)) {
            val w = size.width
            val h = size.height
            when (type) {
                QuickStatIconType.BORROWED -> {
                    val strokeColor = Color(0xFFE9D5FF)
                    // Down arrow
                    drawLine(strokeColor, Offset(w * 0.50f, h * 0.12f), Offset(w * 0.50f, h * 0.58f), strokeWidth = 4.2f, cap = StrokeCap.Round)
                    val arrow = Path().apply {
                        moveTo(w * 0.34f, h * 0.42f)
                        lineTo(w * 0.50f, h * 0.58f)
                        lineTo(w * 0.66f, h * 0.42f)
                    }
                    drawPath(arrow, color = strokeColor, style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // Tray
                    val tray = Path().apply {
                        moveTo(w * 0.12f, h * 0.62f)
                        lineTo(w * 0.32f, h * 0.62f)
                        lineTo(w * 0.39f, h * 0.74f)
                        lineTo(w * 0.61f, h * 0.74f)
                        lineTo(w * 0.68f, h * 0.62f)
                        lineTo(w * 0.88f, h * 0.62f)
                        lineTo(w * 0.88f, h * 0.88f)
                        lineTo(w * 0.12f, h * 0.88f)
                        close()
                    }
                    drawPath(tray, color = Color(0xFFC084FC).copy(alpha = 0.40f))
                    drawPath(tray, color = Color(0xFFD8B4FE), style = Stroke(width = 3.4f, join = StrokeJoin.Round))
                }
                QuickStatIconType.LENT -> {
                    val strokeColor = Color(0xFFFEF08A)
                    // Up arrow
                    drawLine(strokeColor, Offset(w * 0.50f, h * 0.58f), Offset(w * 0.50f, h * 0.12f), strokeWidth = 4.2f, cap = StrokeCap.Round)
                    val arrow = Path().apply {
                        moveTo(w * 0.34f, h * 0.28f)
                        lineTo(w * 0.50f, h * 0.12f)
                        lineTo(w * 0.66f, h * 0.28f)
                    }
                    drawPath(arrow, color = strokeColor, style = Stroke(width = 4.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    // Tray
                    val tray = Path().apply {
                        moveTo(w * 0.12f, h * 0.62f)
                        lineTo(w * 0.32f, h * 0.62f)
                        lineTo(w * 0.39f, h * 0.74f)
                        lineTo(w * 0.61f, h * 0.74f)
                        lineTo(w * 0.68f, h * 0.62f)
                        lineTo(w * 0.88f, h * 0.62f)
                        lineTo(w * 0.88f, h * 0.88f)
                        lineTo(w * 0.12f, h * 0.88f)
                        close()
                    }
                    drawPath(tray, color = Color(0xFFFBBF24).copy(alpha = 0.42f))
                    drawPath(tray, color = Color(0xFFFDE68A), style = Stroke(width = 3.4f, join = StrokeJoin.Round))
                }
                QuickStatIconType.NET_WORTH -> {
                    val diamond = Path().apply {
                        moveTo(w * 0.24f, h * 0.16f)
                        lineTo(w * 0.76f, h * 0.16f)
                        lineTo(w * 0.94f, h * 0.38f)
                        lineTo(w * 0.50f, h * 0.88f)
                        lineTo(w * 0.06f, h * 0.38f)
                        close()
                    }
                    drawPath(diamond, color = Color(0xFF00F5D4).copy(alpha = 0.28f))
                    drawPath(diamond, color = Color(0xFFA5F3FC), style = Stroke(width = 3.4f, join = StrokeJoin.Round))
                    drawLine(Color(0xFFA5F3FC), Offset(w * 0.07f, h * 0.38f), Offset(w * 0.93f, h * 0.38f), strokeWidth = 2.6f)
                    val facets = Path().apply {
                        moveTo(w * 0.36f, h * 0.16f)
                        lineTo(w * 0.31f, h * 0.38f)
                        lineTo(w * 0.50f, h * 0.86f)
                        lineTo(w * 0.69f, h * 0.38f)
                        lineTo(w * 0.64f, h * 0.16f)
                    }
                    drawPath(facets, color = Color(0xFF67E8F9), style = Stroke(width = 2.4f, join = StrokeJoin.Round))
                }
                QuickStatIconType.TRANSACTIONS -> {
                    val r = CornerRadius(4f, 4f)
                    drawRoundRect(
                        color = Color(0xFF60A5FA),
                        topLeft = Offset(w * 0.12f, h * 0.54f),
                        size = Size(w * 0.19f, h * 0.34f),
                        cornerRadius = r
                    )
                    drawRoundRect(
                        color = Color(0xFF818CF8),
                        topLeft = Offset(w * 0.405f, h * 0.36f),
                        size = Size(w * 0.19f, h * 0.52f),
                        cornerRadius = r
                    )
                    drawRoundRect(
                        color = Color(0xFFC084FC),
                        topLeft = Offset(w * 0.69f, h * 0.16f),
                        size = Size(w * 0.19f, h * 0.72f),
                        cornerRadius = r
                    )
                }
            }
        }
    }
}

@Composable
fun QuickStatGlassCard(
    iconType: QuickStatIconType,
    valueText: String,
    label: String,
    valueColor: Color,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
    animatedAmount: Double? = null,
    currencySymbol: String = "₹",
    stealth: Boolean = false,
    mask: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val bgBrush = if (palette.isLight) {
        Brush.linearGradient(listOf(palette.surfaceElevated, palette.surface))
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xD91A5865),
                Color(0xD90E273B),
                Color(0xD9331B4E)
            ),
            start = Offset(0f, 0f),
            end = Offset(400f, 360f)
        )
    }
    val borderBrush = if (palette.isLight) {
        Brush.linearGradient(listOf(palette.primary.copy(alpha = 0.45f), palette.secondary.copy(alpha = 0.45f)))
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0x997DF5FF),
                Color(0x5538BDF8),
                Color(0xAA9333EA)
            )
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(1.4.dp, borderBrush, shape)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickStatGlassBadge(type = iconType, sizeDp = 46.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (animatedAmount != null) {
                    AnimatedCurrencyText(
                        targetValue = animatedAmount,
                        currencySymbol = currencySymbol,
                        stealth = stealth,
                        mask = mask,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = valueColor,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.ExtraBold,
                        color = valueColor,
                        maxLines = 1
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun LiquidCapsuleSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayText: String,
    palette: ThemePalette,
    onValueChange: (Float) -> Unit,
    onStepMinus: () -> Unit,
    onStepPlus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.surfaceElevated,
                    modifier = Modifier.clickable(onClick = onStepMinus)
                ) {
                    Text("-1", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Text(displayText, style = MaterialTheme.typography.labelLarge, color = palette.primary, fontWeight = FontWeight.ExtraBold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.surfaceElevated,
                    modifier = Modifier.clickable(onClick = onStepPlus)
                ) {
                    Text("+1", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = palette.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = palette.primary,
                activeTrackColor = palette.primary,
                inactiveTrackColor = palette.surfaceElevated
            )
        )
    }
}

@Composable
fun AuroraGlassSectionHeader(
    emoji: String,
    title: String,
    subtitle: String,
    palette: ThemePalette,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            GlassBadge3D(emoji = emoji, accent = palette.primary, sizeDp = 40.dp)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                }
            }
        }
        trailing?.invoke()
    }
}

@androidx.compose.runtime.Composable
fun AnimatedCurrencyText(
    targetValue: Double,
    currencySymbol: String,
    stealth: Boolean,
    mask: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val animatedVal by androidx.compose.animation.core.animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 380,
            easing = androidx.compose.animation.core.CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        ),
        label = "currency_count_up"
    )
    val displayValue = if (kotlin.math.abs(animatedVal - targetValue.toFloat()) < 1f) targetValue else animatedVal.toDouble()
    Text(
        text = com.tracmio.app.data.MoneyPaise.formatCurrency(displayValue, currencySymbol, stealth, mask),
        style = style.copy(fontFeatureSettings = "tnum"),
        fontWeight = fontWeight,
        fontSize = fontSize,
        color = color,
        maxLines = maxLines,
        modifier = modifier
    )
}
