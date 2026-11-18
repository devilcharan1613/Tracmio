package com.tracmio.app.ui.nativeapp

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingDialog(
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    isReplayMode: Boolean = false,
    onDismiss: () -> Unit = {},
    onComplete: (userName: String, currencySymbol: String) -> Unit
) {
    var name by remember(settings.userName) { mutableStateOf(settings.userName.ifBlank { "Charan" }) }
    var selectedCurrency by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol.ifBlank { "₹" }) }
    var customCurrency by remember { mutableStateOf("") }
    var showCustomCurrency by remember {
        mutableStateOf(settings.currencySymbol !in LiquidGlassSettings.ALLOWED_CURRENCIES)
    }

    val infinite = rememberInfiniteTransition(label = "welcome_splash_anim")
    val pulse1 by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring_1"
    )
    val pulse1Alpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha_1"
    )
    val pulse2 by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 900, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring_2"
    )
    val pulse2Alpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha_2"
    )
    val badgeFloatY by infinite.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_float"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val entranceScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = tween(420, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "welcome_entrance_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.background.copy(alpha = 0.97f),
                        palette.backgroundMid.copy(alpha = 0.98f),
                        palette.backgroundBottom.copy(alpha = 0.98f)
                    )
                )
            )
            .clickable(enabled = false) {}
            .systemBarsPadding()
            .imePadding()
            .padding(20.dp)
            .testTag("welcome_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            settings = settings,
            shape = RoundedCornerShape(30.dp),
            borderColor = palette.primary.copy(alpha = 0.55f),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .scale(entranceScale)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated Sonar Pulse Rings + Floating Brand Badge
                Box(
                    modifier = Modifier.size(128.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .scale(pulse1)
                            .alpha(pulse1Alpha)
                            .border(1.8.dp, palette.primary, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .scale(pulse2)
                            .alpha(pulse2Alpha)
                            .border(1.5.dp, palette.secondary, CircleShape)
                    )
                    Box(modifier = Modifier.offset(y = badgeFloatY.dp)) {
                        TracmioBrandBadge(sizeDp = 76.dp, accent = palette.primary)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontStyle = FontStyle.Italic
                                )
                            ) {
                                append("Trac")
                            }
                            withStyle(
                                SpanStyle(
                                    color = if (palette.isLight) palette.primary else Color(0xFF10E89A),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontStyle = FontStyle.Italic
                                )
                            ) {
                                append("mio")
                            }
                        },
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 34.sp,
                        letterSpacing = (-0.6).sp
                    )
                    Text(
                        text = "Track smart. Spend wise.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.primary,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.primary.copy(alpha = 0.14f),
                        modifier = Modifier.border(1.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    ) {
                        Text(
                            text = if (isReplayMode) "WELCOME SPLASH PREVIEW · v16.13" else "100% OFFLINE ENCRYPTED VAULT · v16.13",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.primary,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Text(
                    text = "Aurora Teal Liquid Glass interface powered by local SQLCipher AES storage. Zero cloud tracking or internet permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("welcome_name_input")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Primary Currency Symbol",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiquidGlassSettings.ALLOWED_CURRENCIES.forEach { curr ->
                            val isSelected = !showCustomCurrency && selectedCurrency == curr
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) palette.primary.copy(alpha = 0.26f) else palette.surface)
                                    .border(1.3.dp, if (isSelected) palette.primary else palette.surfaceBorder, RoundedCornerShape(14.dp))
                                    .clickable {
                                        showCustomCurrency = false
                                        selectedCurrency = curr
                                    }
                                    .padding(vertical = 11.dp)
                            ) {
                                Text(
                                    curr,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isSelected) palette.primary else palette.textPrimary
                                )
                            }
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.2f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (showCustomCurrency) palette.primary.copy(alpha = 0.26f) else palette.surface)
                                .border(1.3.dp, if (showCustomCurrency) palette.primary else palette.surfaceBorder, RoundedCornerShape(14.dp))
                                .clickable { showCustomCurrency = !showCustomCurrency }
                                .padding(vertical = 11.dp)
                        ) {
                            Text(
                                "Other",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (showCustomCurrency) palette.primary else palette.textPrimary
                            )
                        }
                    }
                    if (showCustomCurrency) {
                        OutlinedTextField(
                            value = customCurrency,
                            onValueChange = { if (it.length <= 4) customCurrency = it },
                            label = { Text("Custom Symbol (e.g. ¥, AED, RM)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                val resolvedCurrency = if (showCustomCurrency && customCurrency.isNotBlank()) {
                    customCurrency.trim()
                } else {
                    selectedCurrency.ifBlank { "₹" }
                }

                Button(
                    onClick = {
                        onComplete(name.trim().ifBlank { "Charan" }, resolvedCurrency)
                        if (isReplayMode) onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("welcome_get_started_button")
                ) {
                    Text(
                        if (isReplayMode) "Apply & Return to Studio" else "Get Started →",
                        color = palette.background,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                if (isReplayMode) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("welcome_close_preview_button")
                    ) {
                        Text("Close Splash Preview", color = palette.textSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
