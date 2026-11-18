package com.tracmio.app.ui.nativeapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracmio.app.data.local.TransactionEntity
import com.tracmio.app.data.security.TracmioSecurityEngine
import java.util.Calendar

@Composable
fun NativeTracmioApp(
    viewModel: NativeTracmioViewModel,
    hostCallbacks: NativeHostCallbacks
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()
    val palette = ThemePresets.getPalette(settings.themePreset, settings.customPrimaryHex, settings.customSecondaryHex)

    val backStack = remember { mutableStateListOf("dashboard") }
    val currentRoute = backStack.lastOrNull() ?: "dashboard"
    val topTabs = remember { setOf("dashboard", "accounts", "transactions", "more") }

    fun navigateTo(route: String) {
        if (route in topTabs) {
            backStack.clear()
            backStack.add(route)
        } else if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }

    fun replaceSubRoute(route: String) {
        if (route in topTabs) {
            navigateTo(route)
        } else if (backStack.isNotEmpty() && backStack.last() != route) {
            backStack[backStack.lastIndex] = route
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else if (currentRoute != "dashboard") {
            backStack.clear()
            backStack.add("dashboard")
        }
    }

    BackHandler(enabled = backStack.size > 1 || currentRoute != "dashboard") {
        navigateBack()
    }

    // Subtle SensorManager tilt parallax (updates floats without triggering full-tree recomposition)
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    DisposableEffect(settings.deviceMotionEnabled) {
        if (!settings.deviceMotionEnabled) {
            tiltX = 0f
            tiltY = 0f
            onDispose { }
        } else {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val nx = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val ny = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                    tiltX = tiltX * 0.85f + nx * 0.15f
                    tiltY = tiltY * 0.85f + ny * 0.15f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            if (accel != null) {
                sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose { sm?.unregisterListener(listener) }
        }
    }

    val replayWelcome by viewModel.replayWelcome.collectAsStateWithLifecycle()

    var showAddTxSheet by remember { mutableStateOf(false) }
    var addTxInitialType by remember { mutableStateOf("EXPENSE") }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var receiptTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var initialLedgerSearch by remember { mutableStateOf("") }
    var dockVisible by remember { mutableStateOf(true) }

    LaunchedEffect(currentRoute) {
        dockVisible = true
    }

    val scrollDockConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -14f) {
                    dockVisible = false
                } else if (available.y > 10f) {
                    dockVisible = true
                }
                return Offset.Zero
            }
        }
    }

    var appEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appEntered = true }
    val appOpenAlpha by animateFloatAsState(
        targetValue = if (appEntered) 1f else 0f,
        animationSpec = tween(420, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "app_open_alpha"
    )
    val appOpenScale by animateFloatAsState(
        targetValue = if (appEntered) 1f else 0.992f,
        animationSpec = tween(420, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "app_open_scale"
    )
    val appOpenOffsetY by animateFloatAsState(
        targetValue = if (appEntered) 0f else 18f,
        animationSpec = tween(420, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "app_open_offset"
    )

    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    val snackbarHostState = remember { SnackbarHostState() }

    // Trigger Add Sheet when launched from notification quick action (+ Add income / + Add expense)
    LaunchedEffect(state.pendingNotificationAction) {
        when (state.pendingNotificationAction?.lowercase()) {
            "income" -> {
                editingTransaction = null
                addTxInitialType = "INCOME"
                showAddTxSheet = true
                viewModel.consumePendingNotificationAction()
            }
            "expense" -> {
                editingTransaction = null
                addTxInitialType = "EXPENSE"
                showAddTxSheet = true
                viewModel.consumePendingNotificationAction()
            }
        }
    }

    // Status message + 2-second UNDO Snackbar
    LaunchedEffect(state.statusMessage, state.hasPendingUndo) {
        val msg = state.statusMessage
        if (!msg.isNullOrBlank()) {
            val res = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (state.hasPendingUndo) "UNDO" else null,
                duration = SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed && state.hasPendingUndo) {
                viewModel.performPendingUndo()
            }
            viewModel.clearStatusMessage()
        }
    }

    TracmioNativeTheme(settings = settings) {
        Box(modifier = Modifier.fillMaxSize()) {
            LiquidMeshBackground(
                settings = settings,
                tiltXProvider = { tiltX },
                tiltYProvider = { tiltY }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .graphicsLayer {
                        alpha = appOpenAlpha
                        scaleX = appOpenScale
                        scaleY = appOpenScale
                        translationY = appOpenOffsetY
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        width = 1.4.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                palette.primary.copy(alpha = 0.85f),
                                Color(0xFF38BDF8).copy(alpha = 0.45f),
                                palette.primary.copy(alpha = 0.80f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .testTag("app_safe_border_frame")
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    },
                    topBar = {
                        TracmioTopBar(
                            settings = settings,
                            palette = palette,
                            onSingleTapPrivacy = { viewModel.togglePrivacyMask() },
                            onTripleTapStealth = {
                                viewModel.toggleStealthMode()
                                viewModel.postStatus(
                                    if (!settings.stealthModeActive) "Stealth Mode Activated" else "Stealth Mode Off"
                                )
                            },
                            onQuickAdd = {
                                editingTransaction = null
                                addTxInitialType = "EXPENSE"
                                showAddTxSheet = true
                            }
                        )
                    },
                    bottomBar = {
                        AnimatedVisibility(
                            visible = dockVisible && !isImeVisible,
                            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(220)),
                            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                FloatingGlassBottomNav(
                                    currentRoute = currentRoute,
                                    palette = palette,
                                    onSelectTab = { navigateTo(it) },
                                    onQuickAdd = {
                                        editingTransaction = null
                                        addTxInitialType = "EXPENSE"
                                        showAddTxSheet = true
                                    }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .nestedScroll(scrollDockConnection)
                    ) {
                        AnimatedContent(
                            targetState = currentRoute,
                            transitionSpec = { tracmioTransitionSpec(settings.animationStyle) },
                            label = "tracmio_screen"
                        ) { route ->
                        when (route) {
                            "dashboard" -> DashboardScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onQuickAdd = {
                                    editingTransaction = null
                                    addTxInitialType = "EXPENSE"
                                    showAddTxSheet = true
                                },
                                onOpenTransactionReceipt = { receiptTransaction = it },
                                onNavigate = { navigateTo(it) }
                            )
                            "accounts" -> AccountsScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel
                            )
                            "transactions" -> TransactionsListScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                initialSearchQuery = initialLedgerSearch,
                                onConsumeInitialSearchQuery = { initialLedgerSearch = "" },
                                onOpenReceipt = { receiptTransaction = it }
                            )
                            "more" -> MoreScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onNavigate = { navigateTo(it) }
                            )
                            "settings" -> SettingsScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                hostCallbacks = hostCallbacks,
                                onBack = { navigateBack() },
                                onNavigate = { navigateTo(it) }
                            )
                            "tools_hub" -> ToolsHubFolderScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onBack = { navigateBack() },
                                onNavigate = { navigateTo(it) }
                            )
                            "guide" -> InAppGuideScreen(
                                settings = settings,
                                palette = palette,
                                onBack = { navigateBack() },
                                onNavigate = { navigateTo(it) }
                            )
                            "cash_counter" -> StandaloneCashCounterScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onBack = { navigateBack() }
                            )
                            // Planning, People, Khata, Wishlist, Cashback, PPU, Round-Up, Runway, Debt Planner
                            "people", "khata",
                            "planning", "budgets", "runway",
                            "goals", "wishlist", "cashback", "price_per_use", "round_up",
                            "loans", "debt_planner" -> PlanningAndPeopleScreens(
                                subRoute = route,
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                hostCallbacks = hostCallbacks,
                                onBack = { navigateBack() },
                                onSelectSubRoute = { replaceSubRoute(it) }
                            )
                            // Reports & Intelligence
                            "reports", "financial_xray", "money_story", "spending_dna", "calendar" -> ReportsAndIntelligenceScreens(
                                subRoute = route,
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onBack = { navigateBack() },
                                onSelectSubRoute = { replaceSubRoute(it) },
                                onFilterCategoryInLedger = { cat ->
                                    initialLedgerSearch = cat
                                    navigateTo("transactions")
                                }
                            )
                            // Trackers & Time Machine
                            "custom_trackers", "time_machine" -> TrackersAndTimeMachineScreens(
                                subRoute = route,
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onBack = { navigateBack() },
                                onSelectSubRoute = { replaceSubRoute(it) }
                            )
                            // Tools Hub utility screens
                            "refunds" -> PendingRefundsScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onBack = { navigateBack() }
                            )
                            "subscriptions", "recharge", "academic" -> SubscriptionsAndAcademicScreen(
                                subRoute = route,
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onBack = { navigateBack() },
                                onSelectSubRoute = { replaceSubRoute(it) }
                            )
                            "global_search" -> GlobalSearchScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                onBack = { navigateBack() },
                                onNavigate = { navigateTo(it) }
                            )
                            "reminders", "warranty_vault" -> RemindersAndWarrantyScreen(
                                subRoute = route,
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                hostCallbacks = hostCallbacks,
                                onBack = { navigateBack() },
                                onSelectSubRoute = { replaceSubRoute(it) }
                            )
                            "data_export" -> DataAndExportScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                hostCallbacks = hostCallbacks,
                                onBack = { navigateBack() }
                            )
                            "permissions" -> PrivacyAndSecurityAuditScreen(
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                hostCallbacks = hostCallbacks,
                                onBack = { navigateBack() }
                            )
                            "app_lock" -> AppLockScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                hostCallbacks = hostCallbacks,
                                onBack = { navigateBack() }
                            )
                            "themes" -> ThemesStudioScreen(
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                hostCallbacks = hostCallbacks,
                                onBack = { navigateBack() }
                            )
                            else -> DashboardScreen(
                                state = state,
                                settings = settings,
                                palette = palette,
                                viewModel = viewModel,
                                onQuickAdd = {
                                    editingTransaction = null
                                    addTxInitialType = "EXPENSE"
                                    showAddTxSheet = true
                                },
                                onOpenTransactionReceipt = { receiptTransaction = it },
                                onNavigate = { navigateTo(it) }
                            )
                        }
                    }
                }
            }
            }

            if (showAddTxSheet) {
                AddEditTransactionSheet(
                    state = state,
                    settings = settings,
                    palette = palette,
                    viewModel = viewModel,
                    editingTransaction = editingTransaction,
                    initialType = addTxInitialType,
                    hostCallbacks = hostCallbacks,
                    onDismiss = {
                        showAddTxSheet = false
                        editingTransaction = null
                    }
                )
            }

            receiptTransaction?.let { tx ->
                ExpenseReceiptModal(
                    transaction = tx,
                    palette = palette,
                    currencySymbol = settings.currencySymbol,
                    onDismiss = { receiptTransaction = null },
                    onDelete = {
                        viewModel.deleteTransaction(it)
                        receiptTransaction = null
                    },
                    onEdit = {
                        receiptTransaction = null
                        editingTransaction = it
                        addTxInitialType = it.type
                        showAddTxSheet = true
                    },
                    onRepeat = {
                        viewModel.repeatTransaction(it)
                        receiptTransaction = null
                    },
                    onPrint = {
                        val accName = state.accounts.firstOrNull { a -> a.id == it.accountId }?.name ?: "Account"
                        hostCallbacks.printReceipt(it, accName)
                    }
                )
            }

            if (!settings.hasCompletedOnboarding || replayWelcome) {
                OnboardingDialog(
                    settings = settings,
                    palette = palette,
                    isReplayMode = replayWelcome,
                    onDismiss = { viewModel.dismissReplayWelcome() },
                    onComplete = { name, curr ->
                        viewModel.completeOnboarding(name, curr)
                    }
                )
            }

            if (state.isAppLocked) {
                AppLockOverlayScreen(
                    state = state,
                    settings = settings,
                    palette = palette,
                    viewModel = viewModel,
                    hostCallbacks = hostCallbacks
                )
            }

            val vaultErr = state.vaultInitState as? TracmioSecurityEngine.VaultInitState.SecurityError
            if (vaultErr != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background.copy(alpha = 0.96f))
                        .systemBarsPadding()
                        .padding(24.dp)
                        .testTag("vault_security_error_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidGlassComponents(settings = settings, palette = palette) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GlassBadge3D("🛡️", Color(0xFFFB7185), 52.dp)
                            Text(
                                vaultErr.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.textPrimary
                            )
                            Text(
                                vaultErr.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary
                            )
                            if (vaultErr.canRetry) {
                                Button(
                                    onClick = { viewModel.retryVaultInitialization() },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                    modifier = Modifier.fillMaxWidth().testTag("vault_retry_button")
                                ) {
                                    Text("Retry Encrypted Vault Unlock", color = palette.background, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TracmioTopBar(
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    onSingleTapPrivacy: () -> Unit,
    onTripleTapStealth: () -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapMs by remember { mutableLongStateOf(0L) }
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }
    val userName = settings.userName.ifBlank { "Charan" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("tracmio_topbar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TracmioBrandBadge(sizeDp = 42.dp, accent = palette.primary)
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
                    fontSize = 24.sp,
                    letterSpacing = (-0.5).sp
                )
            }
            Text(
                text = if (settings.stealthModeActive) "Stealth Mode Active 🕶️" else "$greeting, $userName 👋",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary.copy(alpha = 0.92f),
                maxLines = 1
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (palette.isLight) palette.surfaceElevated else Color(0xCC111D28))
                    .border(1.2.dp, if (palette.isLight) palette.surfaceBorder else Color(0x4DFFFFFF), CircleShape)
                    .clickable {
                        val now = System.currentTimeMillis()
                        if (now - lastTapMs <= 750L) {
                            tapCount += 1
                        } else {
                            tapCount = 1
                        }
                        lastTapMs = now
                        if (tapCount >= 3) {
                            tapCount = 0
                            onTripleTapStealth()
                        } else {
                            onSingleTapPrivacy()
                        }
                    }
                    .testTag("privacy_eye_button")
            ) {
                Icon(
                    imageVector = if (settings.privacyMaskActive || settings.stealthModeActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle Amount Privacy",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(999.dp),
                        ambientColor = palette.primary.copy(alpha = 0.55f),
                        spotColor = palette.primary.copy(alpha = 0.65f)
                    )
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            if (palette.isLight) {
                                listOf(palette.primary.copy(alpha = 0.22f), palette.secondary.copy(alpha = 0.14f))
                            } else {
                                listOf(Color(0xE0092A2D), Color(0xE00C3C3D))
                            }
                        )
                    )
                    .border(1.6.dp, palette.primary, RoundedCornerShape(999.dp))
                    .clickable(onClick = onQuickAdd)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("topbar_quick_add_button")
            ) {
                Text(
                    text = "+ Add",
                    color = if (palette.isLight) palette.primary else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun FloatingGlassBottomNav(
    currentRoute: String,
    palette: ThemePalette,
    onSelectTab: (String) -> Unit,
    onQuickAdd: () -> Unit
) {
    val leftTabs = listOf(
        Triple("dashboard", "Home", Icons.Default.Home),
        Triple("accounts", "Accounts", Icons.Default.CreditCard)
    )
    val rightTabs = listOf(
        Triple("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
        Triple("more", "More", Icons.Default.Menu)
    )
    val coreTabRoutes = remember { setOf("dashboard", "accounts", "transactions") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        if (palette.isLight) {
                            listOf(palette.surfaceElevated.copy(alpha = 0.94f), palette.surface.copy(alpha = 0.92f))
                        } else {
                            listOf(Color(0xEB152D3B), Color(0xF2081622))
                        }
                    )
                )
                .border(
                    width = 1.3.dp,
                    brush = Brush.verticalGradient(
                        if (palette.isLight) {
                            listOf(palette.primary.copy(alpha = 0.45f), palette.surfaceBorder)
                        } else {
                            listOf(Color(0x66A5F3FC), Color(0x287DF5FF), Color(0x33A855F7))
                        }
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leftTabs.forEach { (route, label, icon) ->
                BottomNavTabItem(
                    route = route,
                    label = label,
                    icon = icon,
                    selected = currentRoute == route,
                    palette = palette,
                    onClick = { onSelectTab(route) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(0.9f))

            rightTabs.forEach { (route, label, icon) ->
                val isSelected = currentRoute == route || (route == "more" && currentRoute !in coreTabRoutes)
                BottomNavTabItem(
                    route = route,
                    label = label,
                    icon = icon,
                    selected = isSelected,
                    palette = palette,
                    onClick = { onSelectTab(route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Raised glowing central + button protruding above the rounded glass dock
        val fabInteraction = remember { MutableInteractionSource() }
        val fabPressed by fabInteraction.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (fabPressed) 0.88f else 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
            label = "nav_fab_press"
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 2.dp)
                .size(58.dp)
                .graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    ambientColor = palette.primary.copy(alpha = 0.8f),
                    spotColor = palette.primary.copy(alpha = 0.85f)
                )
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF67FBE0), palette.primary, Color(0xFF00C2B2))
                    )
                )
                .border(1.8.dp, Color(0xCCF0FDFA), CircleShape)
                .clickable(interactionSource = fabInteraction, indication = null, onClick = onQuickAdd)
                .testTag("nav_quick_add_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Transaction",
                tint = Color(0xFF031923),
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun BottomNavTabItem(
    route: String,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    palette: ThemePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabInteraction = remember { MutableInteractionSource() }
    val tabPressed by tabInteraction.collectIsPressedAsState()
    val tabScale by animateFloatAsState(
        targetValue = when {
            tabPressed -> 0.92f
            selected -> 1.05f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "nav_tab_bounce"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = tabScale
                scaleY = tabScale
            }
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (selected) {
                    Modifier
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    palette.primary.copy(alpha = 0.24f),
                                    palette.primary.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(1.4.dp, palette.primary.copy(alpha = 0.85f), RoundedCornerShape(22.dp))
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = tabInteraction, indication = null, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .testTag("bottom_nav_$route")
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.primary else palette.textSecondary,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) palette.primary else palette.textSecondary,
            maxLines = 1
        )
    }
}
