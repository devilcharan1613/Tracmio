package com.tracmio.app.ui.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tracmio.app.data.*
import com.tracmio.app.data.local.AccountEntity
import com.tracmio.app.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    onQuickAdd: () -> Unit,
    onOpenTransactionReceipt: (TransactionEntity) -> Unit,
    onNavigate: (String) -> Unit
) {
    val curr = settings.currencySymbol
    val mask = settings.privacyMaskActive
    val stealth = settings.stealthModeActive

    val pace = remember(state.accounts, state.transactions, state.budgets) {
        NativeIntelligenceCalculators.computeMonthlyPace(state.accounts, state.transactions, state.budgets)
    }
    val safeToSpend = remember(state.accounts, state.transactions) {
        NativeLedgerCalculator.computeSafeToSpend(state.accounts, state.transactions)
    }
    val monthDateFraction = remember {
        val cal = Calendar.getInstance()
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH).coerceIn(1, daysInMonth)
        currentDay.toFloat() / daysInMonth.toFloat()
    }
    val borrowedTotal = remember(state.transactions) {
        MoneyPaise.toRupees(
            state.transactions.filter { it.type.equals("BORROW", true) }
                .sumOf { if (it.amountPaise > 0L) it.amountPaise else MoneyPaise.toPaise(it.amount) }
        )
    }
    val lentTotal = remember(state.transactions) {
        MoneyPaise.toRupees(
            state.transactions.filter { it.type.equals("LEND", true) }
                .sumOf { if (it.amountPaise > 0L) it.amountPaise else MoneyPaise.toPaise(it.amount) }
        )
    }
    val netWorth = remember(state.totalBalance, lentTotal, borrowedTotal) {
        MoneyPaise.toRupees(
            MoneyPaise.toPaise(state.totalBalance) + MoneyPaise.toPaise(lentTotal) - MoneyPaise.toPaise(borrowedTotal)
        )
    }
    var showAddChipDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("dashboard_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Total Balance Card (Version 2.5 Aurora Wave Reference Design)
        item {
            val heroShape = RoundedCornerShape(28.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 16.dp,
                        shape = heroShape,
                        ambientColor = palette.primary.copy(alpha = 0.25f),
                        spotColor = palette.secondary.copy(alpha = 0.30f)
                    )
                    .clip(heroShape)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            if (palette.isLight) {
                                listOf(palette.primary.copy(alpha = 0.55f), palette.secondary.copy(alpha = 0.45f))
                            } else {
                                listOf(
                                    Color(0xCC7DF5FF),
                                    Color(0x8838BDF8),
                                    Color(0xCCA855F7)
                                )
                            }
                        ),
                        shape = heroShape
                    )
                    .testTag("dashboard_hero_balance_card")
            ) {
                HeroAuroraWaveBackdrop(
                    isLight = palette.isLight,
                    primary = palette.primary,
                    secondary = palette.secondary,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TOTAL BALANCE",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (palette.isLight) palette.textSecondary else Color.White.copy(alpha = 0.88f),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp
                        )
                        AnimatedCurrencyText(
                            targetValue = state.totalBalance,
                            currencySymbol = curr,
                            stealth = stealth,
                            mask = mask,
                            style = MaterialTheme.typography.displaySmall,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            modifier = Modifier.testTag("dashboard_total_balance_value")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Income Glass Sub-Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        if (palette.isLight) {
                                            listOf(Color(0xFFD1FAE5).copy(alpha = 0.92f), Color(0xFFA7F3D0).copy(alpha = 0.72f))
                                        } else {
                                            listOf(Color(0xB80B433E), Color(0xD006262C))
                                        }
                                    )
                                )
                                .border(
                                    width = 1.4.dp,
                                    brush = Brush.linearGradient(
                                        if (palette.isLight) {
                                            listOf(Color(0xFF10B981), Color(0xFF059669))
                                        } else {
                                            listOf(Color(0xCC7DF5FF), Color(0x8810B981))
                                        }
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(vertical = 14.dp, horizontal = 12.dp)
                                .testTag("dashboard_income_subcard"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF34D399), Color(0xFF059669))
                                            )
                                        )
                                        .border(1.6.dp, Color(0xFFA7F3D0), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Income",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                AnimatedCurrencyText(
                                    targetValue = state.totalIncome,
                                    currencySymbol = curr,
                                    stealth = stealth,
                                    mask = mask,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.textPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Expense Glass Sub-Card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        if (palette.isLight) {
                                            listOf(Color(0xFFF3E8FF).copy(alpha = 0.92f), Color(0xFFE9D5FF).copy(alpha = 0.72f))
                                        } else {
                                            listOf(Color(0xB8251C48), Color(0xD015112E))
                                        }
                                    )
                                )
                                .border(
                                    width = 1.4.dp,
                                    brush = Brush.linearGradient(
                                        if (palette.isLight) {
                                            listOf(Color(0xFFA855F7), Color(0xFF7E22CE))
                                        } else {
                                            listOf(Color(0xCCDDD6FE), Color(0x88A855F7))
                                        }
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(vertical = 14.dp, horizontal = 12.dp)
                                .testTag("dashboard_expense_subcard"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFFF87171), Color(0xFFDC2626))
                                            )
                                        )
                                        .border(1.6.dp, Color(0xFFFECDD3), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Expense",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                AnimatedCurrencyText(
                                    targetValue = state.totalExpense,
                                    currencySymbol = curr,
                                    stealth = stealth,
                                    mask = mask,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.textPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Expense",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Monthly Pace Card with Expected-Day Needle Marker
        item {
            val paceShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(paceShape)
                    .background(
                        Brush.linearGradient(
                            if (palette.isLight) {
                                listOf(palette.surfaceElevated, palette.surface)
                            } else {
                                listOf(Color(0xCC122936), Color(0xD90A1824))
                            }
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            if (palette.isLight) {
                                listOf(palette.primary.copy(alpha = 0.4f), palette.surfaceBorder)
                            } else {
                                listOf(Color(0x557DF5FF), Color(0x28FFFFFF), Color(0x44A855F7))
                            }
                        ),
                        shape = paceShape
                    )
                    .clickable { onNavigate("budgets") }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("dashboard_monthly_pace_card")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Schedule,
                                contentDescription = "Monthly Pace",
                                tint = palette.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "MONTHLY PACE",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.textPrimary.copy(alpha = 0.85f),
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.1.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (pace.isOnTrack) Color(0xCC064E3B) else Color(0xCC78350F),
                            modifier = Modifier.border(
                                1.1.dp,
                                if (pace.isOnTrack) Color(0xFF10B981) else Color(0xFFF59E0B),
                                RoundedCornerShape(999.dp)
                            )
                        ) {
                            Text(
                                text = if (pace.isOnTrack) "● On Track" else "● Overpace",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pace.isOnTrack) Color(0xFF34D399) else Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val spentFraction = (pace.spentSoFar / pace.totalBudget.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                    MonthlyPaceNeedleTrack(
                        spentFraction = spentFraction,
                        expectedFraction = monthDateFraction,
                        isOnTrack = pace.isOnTrack,
                        primary = palette.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pace.remainingDays} days left in month",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                        Text(
                            text = "${MoneyPaise.formatCurrency(pace.dailySafeLimit, curr, stealth, mask)}/day safe limit",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. QUICK STATS (2x2 Glass Grid Matching Version 2.5 Screenshot)
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("dashboard_quick_stats_section")
            ) {
                Text(
                    text = "QUICK STATS",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickStatGlassCard(
                        iconType = QuickStatIconType.BORROWED,
                        valueText = MoneyPaise.formatCurrency(borrowedTotal, curr, stealth, mask),
                        label = "Borrowed",
                        valueColor = palette.textPrimary,
                        palette = palette,
                        animatedAmount = borrowedTotal,
                        currencySymbol = curr,
                        stealth = stealth,
                        mask = mask,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("quick_stat_borrowed"),
                        onClick = { onNavigate("people") }
                    )
                    QuickStatGlassCard(
                        iconType = QuickStatIconType.LENT,
                        valueText = MoneyPaise.formatCurrency(lentTotal, curr, stealth, mask),
                        label = "Lent",
                        valueColor = palette.textPrimary,
                        palette = palette,
                        animatedAmount = lentTotal,
                        currencySymbol = curr,
                        stealth = stealth,
                        mask = mask,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("quick_stat_lent"),
                        onClick = { onNavigate("people") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickStatGlassCard(
                        iconType = QuickStatIconType.NET_WORTH,
                        valueText = MoneyPaise.formatCurrency(netWorth, curr, stealth, mask),
                        label = "Net Worth",
                        valueColor = if (netWorth >= 0.0) palette.primary else Color(0xFFFB7185),
                        palette = palette,
                        animatedAmount = netWorth,
                        currencySymbol = curr,
                        stealth = stealth,
                        mask = mask,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("quick_stat_net_worth"),
                        onClick = { onNavigate("financial_xray") }
                    )
                    QuickStatGlassCard(
                        iconType = QuickStatIconType.TRANSACTIONS,
                        valueText = state.transactions.size.toString(),
                        label = "Transactions",
                        valueColor = palette.textPrimary,
                        palette = palette,
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("quick_stat_transactions"),
                        onClick = { onNavigate("transactions") }
                    )
                }
            }
        }

        // 3b. Horizontal Accounts & Wallets Strip (Version 2.5 .hscroll parity)
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("dashboard_accounts_strip")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACCOUNTS & WALLETS",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Manage →",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable { onNavigate("accounts") }
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.accounts, key = { it.id }) { acc ->
                        val accBal = remember(acc, state.transactions) {
                            FinancialCalculator.balance(acc, state.transactions)
                        }
                        val accIcon = when {
                            acc.accountType.equals("Cash", true) -> Icons.Default.Payments
                            acc.accountType.equals("Credit Card", true) -> Icons.Default.CreditCard
                            acc.accountType.equals("Wallet", true) -> Icons.Default.AccountBalanceWallet
                            else -> Icons.Default.AccountBalance
                        }
                        val accAccent = when {
                            acc.accountType.equals("Cash", true) -> Color(0xFF34D399)
                            acc.accountType.equals("Credit Card", true) -> Color(0xFFFB7185)
                            acc.accountType.equals("Wallet", true) -> Color(0xFFA855F7)
                            else -> palette.primary
                        }
                        GlassCard(
                            settings = settings,
                            shape = RoundedCornerShape(20.dp),
                            onClick = { onNavigate("accounts") },
                            modifier = Modifier.width(160.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    GlassBadge3D(
                                        icon = accIcon,
                                        accent = accAccent,
                                        sizeDp = 28.dp,
                                        contentDescription = acc.accountType
                                    )
                                    Text(
                                        text = acc.accountType.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.primary,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    text = MoneyPaise.formatCurrency(accBal, curr, stealth, mask),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (accBal >= 0) palette.textPrimary else Color(0xFFFB7185),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    item {
                        GlassCard(
                            settings = settings,
                            shape = RoundedCornerShape(20.dp),
                            borderColor = palette.primary.copy(alpha = 0.55f),
                            onClick = { onNavigate("accounts") },
                            modifier = Modifier.width(110.dp).height(94.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(10.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("+", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = palette.primary)
                                Text("Accounts", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. Safe-to-Spend Today Card
        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛡️", fontSize = 16.sp)
                        Text(
                            "SAFE-TO-SPEND TODAY",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        "${MoneyPaise.formatCurrency(safeToSpend.dailyAllowance, curr, stealth, mask)} / day",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Spent today: ${MoneyPaise.formatCurrency(safeToSpend.spentToday, curr, stealth, mask)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textPrimary
                    )
                    Text(
                        "${safeToSpend.remainingDaysInMonth}d left · Unallocated ${MoneyPaise.formatCurrency(safeToSpend.unallocatedBalance, curr, stealth, mask)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        }

        // 4. 1-Tap Quick Spend Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("⚡", fontSize = 18.sp)
                        Text(
                            "1-TAP QUICK SPEND",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = palette.primary.copy(alpha = 0.2f),
                        modifier = Modifier
                            .border(1.dp, palette.primary, RoundedCornerShape(16.dp))
                            .clickable { showAddChipDialog = true }
                            .testTag("add_quick_chip_button")
                    ) {
                        Text(
                            "+ Chip",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.quickSpendChips, key = { it.id }) { chip ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(palette.surface.copy(alpha = 0.9f))
                                .border(1.2.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                                .clickable { viewModel.executeQuickSpendChip(chip) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("quick_chip_${chip.id}")
                        ) {
                            Text(chip.emoji)
                            Text(chip.title, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text("$curr${chip.amount.toInt()}", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFB7185).copy(alpha = 0.25f))
                                    .clickable { viewModel.deleteQuickSpendChip(chip) }
                            ) {
                                Text("×", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // 5. 2x2 Quick Intelligence Grid (Runway, Goals, Subscriptions, No-Spend Streak)
        item {
            val runwaySummary = remember(state.accounts, state.transactions) {
                NativeLedgerCalculator.computeEmergencyRunway(state.accounts, state.transactions, 3)
            }
            val streakDays = remember(state.transactions) {
                NativeIntelligenceCalculators.computeNoSpendStreakDays(state.transactions)
            }
            val activeGoalsCount = state.goals.size
            val activeSubsCount = state.subscriptions.size

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCard(
                        settings = settings,
                        onClick = { onNavigate("runway") },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("dashboard_quick_runway")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🛟 Emergency Runway", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontWeight = FontWeight.Bold)
                            Text(
                                "${"%.1f".format(runwaySummary.months)} mos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.primary
                            )
                            Text(
                                if (runwaySummary.months >= 6.0) "Fortress Ready" else "3-mo rolling avg",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                    GlassCard(
                        settings = settings,
                        onClick = { onNavigate("goals") },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("dashboard_quick_goals")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🎯 Savings Goals", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontWeight = FontWeight.Bold)
                            Text(
                                "$activeGoalsCount Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF34D399)
                            )
                            Text(
                                if (settings.roundUpEnabled) "🪙 Round-Up Jar ON" else "Tap to manage goals",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassCard(
                        settings = settings,
                        onClick = { onNavigate("subscriptions") },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("dashboard_quick_subscriptions")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🔁 Subscriptions & SIM", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontWeight = FontWeight.Bold)
                            Text(
                                "$activeSubsCount Tracked",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFA855F7)
                            )
                            Text("Recurring & 28/84d SIM", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                    GlassCard(
                        settings = settings,
                        onClick = { onNavigate("calendar") },
                        modifier = Modifier.weight(1f).fillMaxHeight().testTag("dashboard_quick_streak")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🔥 No-Spend Streak", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, fontWeight = FontWeight.Bold)
                            Text(
                                "$streakDays Days",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBBF24)
                            )
                            Text(
                                if (streakDays >= 7) "🔥 7d+ Streak" else "Zero-spend days",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // 6. Opt-In Paste SMS / UPI Quick-Log Card (Zero SMS Permission Required)
        item {
            var smsPasteText by remember { mutableStateOf("") }
            LiquidGlassComponents(settings = settings, palette = palette, modifier = Modifier.testTag("dashboard_sms_quick_log")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📩", fontSize = 16.sp)
                        Column {
                            Text("PASTE UPI / BANK ALERT QUICK-LOG", style = MaterialTheme.typography.labelMedium, color = palette.primary, fontWeight = FontWeight.ExtraBold)
                            Text("100% offline parser · Zero SMS permission required", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = smsPasteText,
                        onValueChange = { smsPasteText = it },
                        label = { Text("Paste e.g. 'Rs. 240 debited via UPI to Swiggy'") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("sms_paste_input")
                    )
                    Button(
                        onClick = {
                            if (viewModel.parseAndLogSmsAlert(smsPasteText)) {
                                smsPasteText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("sms_parse_button")
                    ) {
                        Text("⚡ Log", color = palette.background, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // 7. Recent Transactions Preview Card
        item {
            val recentTxns = remember(state.transactions) {
                state.transactions.sortedByDescending { it.timestamp }.take(5)
            }
            LiquidGlassComponents(settings = settings, palette = palette, modifier = Modifier.testTag("dashboard_recent_transactions")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧾 RECENT TRANSACTIONS", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                    Text(
                        "See All →",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.clickable { onNavigate("transactions") }.testTag("dashboard_see_all_transactions")
                    )
                }
                if (recentTxns.isEmpty()) {
                    Text("No transactions yet. Tap + Add or a 1-Tap Chip above.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                } else {
                    recentTxns.forEachIndexed { idx, tx ->
                        val isInc = tx.type.equals("INCOME", true) || tx.type.equals("TRANSFER_IN", true) || tx.type.equals("BORROW", true)
                        StaggeredRowEntrance(index = idx) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.surfaceElevated.copy(alpha = 0.55f))
                                    .clickable { onOpenTransactionReceipt(tx) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tx.category, fontWeight = FontWeight.Bold, color = palette.textPrimary, style = MaterialTheme.typography.bodyMedium)
                                    if (tx.note.isNotBlank()) {
                                        Text(tx.note, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary, maxLines = 1)
                                    }
                                }
                                Text(
                                    (if (isInc) "+" else "-") + MoneyPaise.formatCurrency(tx.amount, curr, stealth, mask),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isInc) Color(0xFF34D399) else Color(0xFFFB7185)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddChipDialog) {
        var emoji by remember { mutableStateOf("🍔") }
        var title by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddChipDialog = false },
            containerColor = palette.surfaceElevated,
            title = { Text("⚡ Add 1-Tap Quick Spend Chip", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("Emoji") }, singleLine = true)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Label (e.g. Metro, Snacks)") }, singleLine = true)
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount ($curr)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank() && amt > 0.0) {
                            viewModel.addQuickSpendChip(emoji.ifBlank { "⚡" }, title.trim(), amt)
                            showAddChipDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) { Text("Save Chip", color = palette.background, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddChipDialog = false }) { Text("Cancel", color = palette.textSecondary) }
            }
        )
    }
}

@Composable
fun AccountsScreen(
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel
) {
    val curr = settings.currencySymbol
    val mask = settings.privacyMaskActive
    val stealth = settings.stealthModeActive

    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showCreateAccount by remember { mutableStateOf(false) }
    var allocatingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var cashCountingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var reconcilingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var quickActionAccount by remember { mutableStateOf<Pair<AccountEntity, Boolean>?>(null) }
    var transferringFromAccount by remember { mutableStateOf<AccountEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("accounts_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Accounts & Wallets", style = MaterialTheme.typography.headlineSmall, color = palette.textPrimary, fontWeight = FontWeight.ExtraBold)
                Button(
                    onClick = { showCreateAccount = true },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("+ Add Account", color = palette.background, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // 1-Tap ATM Withdrawal (Bank -> Cash) Card
        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("🏧 1-Tap ATM Withdrawal (Bank → Cash)", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                Text("Instant atomic transfer from primary Bank to Cash Wallet", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(500.0, 1000.0, 2000.0).forEach { amt ->
                        Button(
                            onClick = { viewModel.performAtmWithdrawal(amt) },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("$curr${amt.toInt()}", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        items(state.accounts, key = { it.id }) { acc ->
            val computedBalance = FinancialCalculator.balance(acc, state.transactions)
            val allocations = remember(acc.allocationsJson) {
                NativeLedgerCalculator.parseAllocations(acc.allocationsJson)
            }
            val allocatedSum = allocations.sumOf { it.allocatedAmount }
            val unallocated = (computedBalance - allocatedSum).coerceAtLeast(0.0)
            val isCreditCard = acc.accountType.equals("Credit Card", true)
            val ccUtilization = if (isCreditCard && acc.creditLimit > 0.0) (computedBalance / acc.creditLimit) * 100.0 else 0.0

            LiquidGlassComponents(settings = settings, palette = palette) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(acc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                            Surface(shape = RoundedCornerShape(8.dp), color = palette.surfaceElevated) {
                                Text(acc.accountType, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = palette.primary)
                            }
                        }
                        if (isCreditCard) {
                            Text(
                                "Stmt Day ${acc.statementDay} · Due Day ${acc.dueDay} · Limit $curr${"%,.0f".format(acc.creditLimit)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary
                            )
                        }
                    }
                    Text(
                        MoneyPaise.formatCurrency(computedBalance, curr, stealth, mask),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.primary
                    )
                }

                if (isCreditCard && ccUtilization > 30.0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF78350F).copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFBBF24), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            "⚠️ >30% Credit Utilization (${"%.1f".format(ccUtilization)}%) — Pay early to protect credit score!",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (allocations.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allocations.forEach { a ->
                            Surface(shape = RoundedCornerShape(10.dp), color = palette.surfaceElevated) {
                                Text(
                                    "${a.category} · $curr${"%,.0f".format(a.allocatedAmount)}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textPrimary
                                )
                            }
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = palette.surface) {
                            Text(
                                "Unallocated · $curr${"%,.0f".format(unallocated)}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.primary
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { quickActionAccount = acc to true }, shape = RoundedCornerShape(12.dp)) {
                        Text("＋ Deposit", color = Color(0xFF34D399), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { quickActionAccount = acc to false }, shape = RoundedCornerShape(12.dp)) {
                        Text("－ Withdraw", color = Color(0xFFFB7185), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    if (state.accounts.size > 1) {
                        OutlinedButton(onClick = { transferringFromAccount = acc }, shape = RoundedCornerShape(12.dp)) {
                            Text("⇄ Transfer", color = palette.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(onClick = { allocatingAccount = acc }, shape = RoundedCornerShape(12.dp)) {
                        Text("📂 Allocate", color = palette.primary, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { reconcilingAccount = acc }, shape = RoundedCornerShape(12.dp)) {
                        Text("⚖️ Reconcile", color = palette.textPrimary, style = MaterialTheme.typography.labelSmall)
                    }
                    if (acc.accountType.equals("Cash", true) || acc.accountType.equals("Wallet", true)) {
                        OutlinedButton(onClick = { cashCountingAccount = acc }, shape = RoundedCornerShape(12.dp)) {
                            Text("💵 Cash Count", color = palette.primary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(onClick = { editingAccount = acc }, shape = RoundedCornerShape(12.dp)) {
                        Text("✏️ Edit", color = palette.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    if (showCreateAccount || editingAccount != null) {
        val currentEditing = editingAccount
        AccountEditDialog(
            initialAccount = currentEditing,
            palette = palette,
            onDismiss = {
                showCreateAccount = false
                editingAccount = null
            },
            onDelete = if (currentEditing != null && state.accounts.size > 1) {
                {
                    viewModel.deleteAccount(currentEditing)
                    showCreateAccount = false
                    editingAccount = null
                }
            } else null,
            onSave = { name, type, bal, limit, stmt, due ->
                viewModel.saveAccount(currentEditing?.id, name, type, bal, limit, stmt, due)
                showCreateAccount = false
                editingAccount = null
            }
        )
    }

    quickActionAccount?.let { (acc, isDeposit) ->
        var amtText by remember { mutableStateOf("") }
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { quickActionAccount = null },
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    if (isDeposit) "＋ Quick Deposit to ${acc.name}" else "－ Quick Withdraw from ${acc.name}",
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = amtText, onValueChange = { amtText = it }, label = { Text("Amount ($curr)") }, singleLine = true)
                    OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Note (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amtText.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0) {
                            viewModel.quickAccountDepositOrWithdraw(acc.id, isDeposit, amt, noteText)
                            quickActionAccount = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) { Text(if (isDeposit) "Deposit" else "Withdraw", color = palette.background, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { quickActionAccount = null }) { Text("Cancel", color = palette.textSecondary) }
            }
        )
    }

    transferringFromAccount?.let { fromAcc ->
        val destinations = state.accounts.filter { it.id != fromAcc.id }
        var targetId by remember(destinations) { mutableLongStateOf(destinations.firstOrNull()?.id ?: 0L) }
        var amtText by remember { mutableStateOf("") }
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { transferringFromAccount = null },
            containerColor = palette.surfaceElevated,
            title = { Text("⇄ Transfer from ${fromAcc.name}", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select Destination Account:", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        destinations.forEach { dest ->
                            val sel = dest.id == targetId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) palette.primary.copy(alpha = 0.22f) else palette.surface)
                                    .border(1.dp, if (sel) palette.primary else palette.surfaceBorder, RoundedCornerShape(10.dp))
                                    .clickable { targetId = dest.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(dest.name, style = MaterialTheme.typography.labelSmall, color = if (sel) palette.primary else palette.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    OutlinedTextField(value = amtText, onValueChange = { amtText = it }, label = { Text("Amount ($curr)") }, singleLine = true)
                    OutlinedTextField(value = noteText, onValueChange = { noteText = it }, label = { Text("Note (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amtText.toDoubleOrNull() ?: 0.0
                        if (amt > 0.0 && targetId != 0L) {
                            viewModel.transferFunds(fromAcc.id, targetId, amt, noteText.ifBlank { "Account Transfer" })
                            transferringFromAccount = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) { Text("Transfer", color = palette.background, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { transferringFromAccount = null }) { Text("Cancel", color = palette.textSecondary) }
            }
        )
    }

    allocatingAccount?.let { acc ->
        AccountAllocationDialog(
            account = acc,
            palette = palette,
            currencySymbol = curr,
            liveBalance = FinancialCalculator.balance(acc, state.transactions),
            onDismiss = { allocatingAccount = null },
            onSaveAllocations = { list ->
                viewModel.updateAccountAllocations(acc, list)
                allocatingAccount = null
            }
        )
    }

    cashCountingAccount?.let { acc ->
        CashCountDenominationDialog(
            account = acc,
            palette = palette,
            currencySymbol = curr,
            onDismiss = { cashCountingAccount = null },
            onApplyReconcile = { countedTotal, note, lines ->
                viewModel.applyCashDenominationCount(lines, note, acc.id)
                cashCountingAccount = null
            }
        )
    }

    reconcilingAccount?.let { acc ->
        var actualBalText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reconcilingAccount = null },
            containerColor = palette.surfaceElevated,
            title = { Text("⚖️ Reconcile ${acc.name}", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter actual current balance to auto-log a Balance Adjustment:", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    OutlinedTextField(
                        value = actualBalText,
                        onValueChange = { actualBalText = it },
                        label = { Text("Actual Balance ($curr)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = actualBalText.toDoubleOrNull()
                        if (target != null) {
                            viewModel.reconcileAccountBalance(acc, target)
                            reconcilingAccount = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) { Text("Reconcile", color = palette.background, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { reconcilingAccount = null }) { Text("Cancel", color = palette.textSecondary) }
            }
        )
    }
}
