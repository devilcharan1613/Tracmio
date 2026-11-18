package com.tracmio.app.ui.nativeapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tracmio.app.data.NativeIntelligenceCalculators
import com.tracmio.app.data.NativeLedgerCalculator
import com.tracmio.app.data.NativeTracmioUiState
import com.tracmio.app.data.SpendingChartWindow
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportsAndIntelligenceScreens(
    subRoute: String,
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    onBack: (() -> Unit)? = null,
    onSelectSubRoute: (String) -> Unit,
    onFilterCategoryInLedger: (String) -> Unit = {}
) {
    val curr = settings.currencySymbol
    val tabs = listOf(
        "reports" to "📈 Reports & Charts",
        "financial_xray" to "🩻 Financial X-Ray",
        "money_story" to "📖 Money Story",
        "spending_dna" to "🧬 Spending DNA",
        "calendar" to "📅 Calendar & SIM"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                    }
                }
                tabs.forEach { (routeKey, label) ->
                    val sel = subRoute == routeKey
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) palette.primary else palette.surface)
                            .border(1.dp, if (sel) palette.primary else palette.surfaceBorder, RoundedCornerShape(16.dp))
                            .clickable { onSelectSubRoute(routeKey) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(label, color = if (sel) palette.background else palette.textPrimary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        when (subRoute) {
            "financial_xray" -> {
                item {
                    val xray = remember(state.accounts, state.transactions, state.budgets, state.goals, state.loans, state.subscriptions, state.academicEvents) {
                        NativeIntelligenceCalculators.computeFinancialXRay(
                            accounts = state.accounts,
                            transactions = state.transactions,
                            budgets = state.budgets,
                            goals = state.goals,
                            loans = state.loans,
                            loanPayments = state.loanPayments,
                            subscriptions = state.subscriptions,
                            academicEvents = state.academicEvents
                        )
                    }
                    LiquidGlassComponents(settings = settings, palette = palette) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("🩻 10-Point Financial X-Ray", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.primary)
                                Text("Diagnostic Health Grade: ${xray.grade}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                            }
                            Text("${xray.overallScore}/100", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.primary)
                        }
                        xray.metrics.forEach { m ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.surface).padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(m.name, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                    Text(m.summary, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                }
                                Text("${m.score}% · ${m.status}", fontWeight = FontWeight.ExtraBold, color = palette.primary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            "money_story" -> {
                item {
                    val story = remember(state.transactions) {
                        NativeIntelligenceCalculators.computeMoneyStory(state.transactions)
                    }
                    LiquidGlassComponents(settings = settings, palette = palette) {
                        Text("📖 Monthly Money Story Narrative", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.primary)
                        Text(
                            "You logged ${story.transactionCount} transactions this period with a ${story.savingsRatePercent}% savings rate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textPrimary
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.surface).padding(12.dp)) {
                                Column {
                                    Text("Top Category", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                    Text(story.topCategory, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary, maxLines = 1)
                                    Text("$curr${"%,.0f".format(story.topCategoryAmount)}", style = MaterialTheme.typography.labelMedium, color = palette.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.surface).padding(12.dp)) {
                                Column {
                                    Text("Most Used Rail", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                    Text(story.topPaymentMethod, fontWeight = FontWeight.ExtraBold, color = palette.primary, maxLines = 1)
                                    Text("Primary spend channel", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surface).padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Biggest Single Expense", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                    Text(story.biggestExpenseLabel, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                }
                                Text("$curr${"%,.0f".format(story.biggestExpenseAmount)}", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFB7185))
                            }
                        }
                    }
                }
            }
            "spending_dna" -> {
                item {
                    val split = remember(state.transactions) {
                        NativeIntelligenceCalculators.compute503020Breakdown(state.transactions)
                    }
                    val weekdayAmounts = remember(state.transactions) {
                        val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        val map = mutableMapOf<Int, Double>()
                        val cal = Calendar.getInstance()
                        state.transactions.filter { it.type.equals("EXPENSE", true) }.forEach { tx ->
                            cal.timeInMillis = tx.timestamp
                            val dow = when (cal.get(Calendar.DAY_OF_WEEK)) {
                                Calendar.MONDAY -> 0
                                Calendar.TUESDAY -> 1
                                Calendar.WEDNESDAY -> 2
                                Calendar.THURSDAY -> 3
                                Calendar.FRIDAY -> 4
                                Calendar.SATURDAY -> 5
                                else -> 6
                            }
                            map[dow] = (map[dow] ?: 0.0) + tx.amount
                        }
                        labels.mapIndexed { idx, lbl -> lbl to (map[idx] ?: 0.0) }
                    }
                    val maxWeekday = weekdayAmounts.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        LiquidGlassComponents(settings = settings, palette = palette) {
                            Text(
                                "🧬 50/30/20 Spending DNA Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.primary
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.surface).padding(12.dp)) {
                                    Column {
                                        Text("50% Needs", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                        Text("$curr${"%,.0f".format(split.needs)}", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.surface).padding(12.dp)) {
                                    Column {
                                        Text("30% Wants", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                        Text("$curr${"%,.0f".format(split.wants)}", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFB7185))
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(palette.surface).padding(12.dp)) {
                                    Column {
                                        Text("20% Savings", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                        Text("$curr${"%,.0f".format(split.savings)}", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                                    }
                                }
                            }
                        }

                        LiquidGlassComponents(settings = settings, palette = palette) {
                            Text("📊 Mon–Sun Day-of-Week Spend Rhythm", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            weekdayAmounts.forEach { (dayLabel, amt) ->
                                val ratio = (amt / maxWeekday).toFloat().coerceIn(0.04f, 1f)
                                val isWeekend = dayLabel == "Sat" || dayLabel == "Sun"
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(dayLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isWeekend) Color(0xFFFBBF24) else palette.textPrimary)
                                        Text("$curr${"%,.0f".format(amt)}", style = MaterialTheme.typography.labelSmall, color = palette.primary, fontWeight = FontWeight.Bold)
                                    }
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                        color = if (isWeekend) Color(0xFFFBBF24) else palette.primary,
                                        trackColor = palette.surface
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "calendar" -> {
                item {
                    var simName by remember { mutableStateOf("Jio / Airtel Prepaid") }
                    var simAmt by remember { mutableStateOf("299") }
                    var simDays by remember { mutableStateOf("28") }
                    val streakDays = remember(state.transactions) {
                        NativeIntelligenceCalculators.computeNoSpendStreakDays(state.transactions)
                    }
                    val (daysInMonth, dailySpendMap) = remember(state.transactions) {
                        val nowCal = Calendar.getInstance()
                        val curY = nowCal.get(Calendar.YEAR)
                        val curM = nowCal.get(Calendar.MONTH)
                        val maxDays = nowCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val map = mutableMapOf<Int, Double>()
                        val txCal = Calendar.getInstance()
                        state.transactions.filter { it.type.equals("EXPENSE", true) }.forEach { tx ->
                            txCal.timeInMillis = tx.timestamp
                            if (txCal.get(Calendar.YEAR) == curY && txCal.get(Calendar.MONTH) == curM) {
                                val d = txCal.get(Calendar.DAY_OF_MONTH)
                                map[d] = (map[d] ?: 0.0) + tx.amount
                            }
                        }
                        maxDays to map
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        LiquidGlassComponents(settings = settings, palette = palette) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("🔥 Calendar No-Spend Streak & Milestones", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = palette.primary)
                                Text("${streakDays}d Streak", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                            }
                            val milestones = listOf(
                                Triple("🏅 3d Saver", streakDays >= 3, 3),
                                Triple("🔥 7d Streak", streakDays >= 7, 7),
                                Triple("💎 14d Pro", streakDays >= 14, 14),
                                Triple("🏆 30d Vault", streakDays >= 30, 30)
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                milestones.forEach { (badge, unlocked, _) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (unlocked) palette.primary.copy(alpha = 0.22f) else palette.surface)
                                            .border(1.dp, if (unlocked) palette.primary else palette.surfaceBorder, RoundedCornerShape(10.dp))
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (unlocked) palette.primary else palette.textSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Monthly 7-Column Calendar Day Grid
                        LiquidGlassComponents(settings = settings, palette = palette) {
                            Text("📅 Monthly Spend & No-Spend Day Grid", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            Text("🟢 Zero-Spend Day   🔴 Expense Logged", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            val daysList = (1..daysInMonth).toList()
                            daysList.chunked(7).forEach { week ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    week.forEach { dayNum ->
                                        val spent = dailySpendMap[dayNum] ?: 0.0
                                        val isNoSpend = spent <= 0.0
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isNoSpend) Color(0xFF064E3B).copy(alpha = 0.45f) else Color(0xFF7F1D1D).copy(alpha = 0.45f))
                                                .border(
                                                    1.dp,
                                                    if (isNoSpend) Color(0xFF10B981).copy(alpha = 0.55f) else Color(0xFFFB7185).copy(alpha = 0.55f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("$dayNum", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isNoSpend) Color(0xFF34D399) else Color(0xFFFB7185))
                                                )
                                            }
                                        }
                                    }
                                    repeat(7 - week.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        LiquidGlassComponents(settings = settings, palette = palette) {
                            Text("📶 SIM / WiFi Recharge Countdown (28 / 56 / 84d)", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = simName, onValueChange = { simName = it }, label = { Text("Operator / WiFi") }, singleLine = true, modifier = Modifier.weight(1f))
                                OutlinedTextField(value = simAmt, onValueChange = { simAmt = it }, label = { Text("Plan ($curr)") }, singleLine = true, modifier = Modifier.width(95.dp))
                                OutlinedTextField(value = simDays, onValueChange = { simDays = it }, label = { Text("Days") }, singleLine = true, modifier = Modifier.width(80.dp))
                            }
                            Button(
                                onClick = {
                                    val amt = simAmt.toDoubleOrNull() ?: 0.0
                                    val days = (simDays.toIntOrNull() ?: 0).coerceAtLeast(0)
                                    if (simName.isNotBlank() && amt > 0.0 && days > 0) {
                                        viewModel.addSubscription(simName.trim(), amt, "${days}d", isSim = true, validityDays = days)
                                        simName = ""
                                        simAmt = ""
                                    } else {
                                        viewModel.postStatus("Enter valid operator, plan amount, and days")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Track SIM / WiFi Pack", color = palette.background, fontWeight = FontWeight.ExtraBold)
                            }
                            state.subscriptions.forEach { sub ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.surface).padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(sub.name, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                        Text("$curr${"%,.0f".format(sub.amount)} · ${sub.billingCycle}", style = MaterialTheme.typography.labelSmall, color = palette.primary)
                                    }
                                    Text("✕", color = Color(0xFFFB7185), modifier = Modifier.clickable { viewModel.deleteSubscription(sub) })
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                item {
                    var chartStyle by remember { mutableStateOf("📊 3D Neon Bars") }
                    var chartWindow by remember { mutableStateOf(SpendingChartWindow.MONTHLY) }
                    val distribution = remember(state.transactions, state.categories, chartWindow) {
                        NativeLedgerCalculator.computeSpendingDistribution(state.transactions, state.categories, chartWindow)
                    }
                    val monthCmp = remember(state.transactions) {
                        NativeIntelligenceCalculators.computeMonthComparison(state.transactions)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        LiquidGlassComponents(settings = settings, palette = palette) {
                            Text("📈 4-Style Visual Chart Switcher", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("📊 3D Neon Bars", "🌊 Area Wave", "💊 Capsules & Polar", "⚡ Executive").forEach { st ->
                                    val sel = chartStyle == st
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (sel) palette.primary else palette.surface)
                                            .clickable { chartStyle = st }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(st, style = MaterialTheme.typography.labelSmall, color = if (sel) palette.background else palette.textPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    SpendingChartWindow.DAILY to "Daily",
                                    SpendingChartWindow.WEEKLY to "Weekly",
                                    SpendingChartWindow.MONTHLY to "Monthly",
                                    SpendingChartWindow.YEARLY to "Yearly"
                                ).forEach { (win, label) ->
                                    val sel = chartWindow == win
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) palette.primary.copy(alpha = 0.22f) else palette.surface)
                                            .border(1.dp, if (sel) palette.primary else palette.surfaceBorder, RoundedCornerShape(10.dp))
                                            .clickable { chartWindow = win }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall, color = if (sel) palette.primary else palette.textSecondary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (distribution.isEmpty()) {
                                Text("Add expenses to see category distribution charts.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                            } else {
                                val entries = distribution.entries.toList().sortedByDescending { it.value }
                                val maxVal = (entries.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0)
                                val totalVal = entries.sumOf { it.value }.coerceAtLeast(1.0)
                                val sliceColors = listOf(palette.primary, Color(0xFFA855F7), Color(0xFF38BDF8), Color(0xFF34D399), Color(0xFFFB7185), Color(0xFFFBBF24))

                                when (chartStyle) {
                                    "🌊 Area Wave" -> {
                                        val pointsList = entries.map { (it.value / maxVal).toFloat().coerceIn(0.08f, 1f) }
                                        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                            val w = size.width
                                            val h = size.height
                                            val step = if (pointsList.size <= 1) w else w / (pointsList.size - 1).toFloat()
                                            val linePath = Path()
                                            val fillPath = Path()
                                            pointsList.forEachIndexed { idx, ratio ->
                                                val x = idx * step
                                                val y = h - (ratio * (h * 0.78f)) - 12f
                                                if (idx == 0) {
                                                    linePath.moveTo(x, y)
                                                    fillPath.moveTo(x, h)
                                                    fillPath.lineTo(x, y)
                                                } else {
                                                    val prevX = (idx - 1) * step
                                                    val prevY = h - (pointsList[idx - 1] * (h * 0.78f)) - 12f
                                                    val midX = (prevX + x) / 2f
                                                    linePath.cubicTo(midX, prevY, midX, y, x, y)
                                                    fillPath.cubicTo(midX, prevY, midX, y, x, y)
                                                }
                                                drawCircle(color = palette.primary, radius = 6f, center = Offset(x, y))
                                            }
                                            fillPath.lineTo((pointsList.size - 1) * step, h)
                                            fillPath.close()
                                            drawPath(
                                                path = fillPath,
                                                brush = Brush.verticalGradient(listOf(palette.primary.copy(alpha = 0.35f), Color.Transparent))
                                            )
                                            drawPath(path = linePath, color = palette.primary, style = Stroke(width = 4f, cap = StrokeCap.Round))
                                        }
                                        entries.take(4).forEach { (cat, amt) ->
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(cat, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                                Text("$curr${"%,.0f".format(amt)}", style = MaterialTheme.typography.labelSmall, color = palette.primary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    "💊 Capsules & Polar" -> {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Canvas(modifier = Modifier.size(110.dp)) {
                                                var startAngle = -90f
                                                val stroke = Stroke(width = 22f, cap = StrokeCap.Round)
                                                entries.forEachIndexed { idx, (_, amt) ->
                                                    val sweep = ((amt / totalVal) * 360.0).toFloat().coerceAtLeast(8f)
                                                    drawArc(
                                                        color = sliceColors[idx % sliceColors.size],
                                                        startAngle = startAngle,
                                                        sweepAngle = sweep - 4f,
                                                        useCenter = false,
                                                        style = stroke,
                                                        size = Size(size.width, size.height)
                                                    )
                                                    startAngle += sweep
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                entries.take(5).forEachIndexed { idx, (cat, amt) ->
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(sliceColors[idx % sliceColors.size]))
                                                        Text("$cat (${((amt / totalVal) * 100).toInt()}%)", style = MaterialTheme.typography.labelSmall, color = palette.textPrimary, modifier = Modifier.weight(1f))
                                                        Text("$curr${"%,.0f".format(amt)}", style = MaterialTheme.typography.labelSmall, color = palette.primary, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "⚡ Executive" -> {
                                        entries.forEach { (cat, amt) ->
                                            val pct = ((amt / totalVal) * 100).toInt()
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(palette.surface)
                                                    .clickable { onFilterCategoryInLedger(cat) }
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(cat, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                                Text("$pct% · $curr${"%,.0f".format(amt)} →", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                                            }
                                        }
                                    }
                                    else -> {
                                        entries.forEachIndexed { idx, (cat, amt) ->
                                            val barColor = sliceColors[idx % sliceColors.size]
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { onFilterCategoryInLedger(cat) }
                                                    .padding(vertical = 3.dp)
                                            ) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("$cat →", style = MaterialTheme.typography.labelMedium, color = palette.textPrimary, fontWeight = FontWeight.Bold)
                                                    Text("$curr${"%,.0f".format(amt)}", style = MaterialTheme.typography.labelMedium, color = barColor, fontWeight = FontWeight.ExtraBold)
                                                }
                                                LinearProgressIndicator(
                                                    progress = { (amt / maxVal).toFloat().coerceIn(0.05f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)),
                                                    color = barColor,
                                                    trackColor = palette.surfaceElevated
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 6-Month Cashflow Pulse (Grouped Income/Expense Bars + Net Trend Curve)
                        LiquidGlassComponents(settings = settings, palette = palette, modifier = Modifier.testTag("reports_6m_cashflow_pulse")) {
                            val sixMonths = remember(state.transactions) {
                                val fmt = SimpleDateFormat("MMM", Locale.US)
                                val cal = Calendar.getInstance()
                                (5 downTo 0).map { offset ->
                                    val targetCal = Calendar.getInstance().apply {
                                        timeInMillis = cal.timeInMillis
                                        add(Calendar.MONTH, -offset)
                                    }
                                    val y = targetCal.get(Calendar.YEAR)
                                    val m = targetCal.get(Calendar.MONTH)
                                    val txCal = Calendar.getInstance()
                                    var inc = 0.0
                                    var exp = 0.0
                                    state.transactions.forEach { tx ->
                                        txCal.timeInMillis = tx.timestamp
                                        if (txCal.get(Calendar.YEAR) == y && txCal.get(Calendar.MONTH) == m) {
                                            if (tx.type.equals("INCOME", true)) inc += tx.amount
                                            else if (tx.type.equals("EXPENSE", true)) exp += tx.amount
                                        }
                                    }
                                    Triple(fmt.format(targetCal.time), inc, exp)
                                }
                            }
                            var selectedMonthIdx by remember { mutableIntStateOf(5) }
                            val activeMonth = sixMonths[selectedMonthIdx.coerceIn(0, 5)]
                            val maxBar = sixMonths.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1.0) ?: 1.0

                            Text("🌊 6-Month Cashflow Pulse", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                val slotW = size.width / sixMonths.size.toFloat()
                                val barW = (slotW * 0.28f).coerceAtLeast(8f)
                                val baseBottom = size.height - 10f
                                val usableH = size.height * 0.78f
                                val netPath = Path()

                                sixMonths.forEachIndexed { idx, (_, inc, exp) ->
                                    val centerX = slotW * idx + slotW / 2f
                                    val incH = ((inc / maxBar) * usableH).toFloat().coerceAtLeast(4f)
                                    val expH = ((exp / maxBar) * usableH).toFloat().coerceAtLeast(4f)
                                    drawRoundRect(
                                        color = Color(0xFF34D399),
                                        topLeft = Offset(centerX - barW - 2f, baseBottom - incH),
                                        size = Size(barW, incH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                    )
                                    drawRoundRect(
                                        color = Color(0xFFFB7185),
                                        topLeft = Offset(centerX + 2f, baseBottom - expH),
                                        size = Size(barW, expH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                                    )
                                    val netRatio = (((inc - exp) / maxBar).toFloat() * 0.5f + 0.5f).coerceIn(0.08f, 0.92f)
                                    val netY = baseBottom - netRatio * usableH
                                    if (idx == 0) netPath.moveTo(centerX, netY) else netPath.lineTo(centerX, netY)
                                    drawCircle(
                                        color = if (idx == selectedMonthIdx) palette.primary else Color.White,
                                        radius = if (idx == selectedMonthIdx) 7f else 4.5f,
                                        center = Offset(centerX, netY)
                                    )
                                }
                                drawPath(path = netPath, color = palette.primary, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                sixMonths.forEachIndexed { idx, (lbl, _, _) ->
                                    val sel = idx == selectedMonthIdx
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) palette.primary else palette.surface)
                                            .clickable { selectedMonthIdx = idx }
                                            .padding(vertical = 5.dp)
                                    ) {
                                        Text(lbl, style = MaterialTheme.typography.labelSmall, color = if (sel) palette.background else palette.textSecondary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                "${activeMonth.first}: +$curr${"%,.0f".format(activeMonth.second)} In · -$curr${"%,.0f".format(activeMonth.third)} Out · Net $curr${"%,.0f".format(activeMonth.second - activeMonth.third)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Merchant & UPI App Breakdown + Weekend vs Weekday Radar + Side-by-Side Month Comparison
                        LiquidGlassComponents(settings = settings, palette = palette) {
                            Text("💳 Merchant & UPI App Breakdown", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            val upiMap = state.transactions.filter { it.type.equals("EXPENSE", true) }
                                .groupBy { it.upiApp.ifBlank { it.paymentMethod } }
                            if (upiMap.isEmpty()) {
                                Text("Log transactions to see UPI & payment app breakdown.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                            } else {
                                upiMap.forEach { (method, list) ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("$method (${list.size} txns)", color = palette.textPrimary)
                                        Text("$curr${"%,.0f".format(list.sumOf { it.amount })}", color = palette.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        LiquidGlassComponents(settings = settings, palette = palette) {
                            Text("📅 Weekend vs. Weekday Spend Radar & Month Comparison", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            var weekendSpend = 0.0
                            var weekdaySpend = 0.0
                            state.transactions.filter { it.type.equals("EXPENSE", true) }.forEach { tx ->
                                val dow = Calendar.getInstance().apply { timeInMillis = tx.timestamp }.get(Calendar.DAY_OF_WEEK)
                                if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) weekendSpend += tx.amount else weekdaySpend += tx.amount
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Weekday: $curr${"%,.0f".format(weekdaySpend)}", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                                Text("Weekend: $curr${"%,.0f".format(weekendSpend)}", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = palette.surfaceBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(palette.surface).padding(10.dp)) {
                                    Column {
                                        Text("This Month", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                        Text("-$curr${"%,.0f".format(monthCmp.thisMonthExpense)}", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFB7185))
                                        Text("+$curr${"%,.0f".format(monthCmp.thisMonthIncome)}", style = MaterialTheme.typography.labelSmall, color = palette.primary)
                                    }
                                }
                                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(palette.surface).padding(10.dp)) {
                                    Column {
                                        Text("Last Month", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                        Text("-$curr${"%,.0f".format(monthCmp.lastMonthExpense)}", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                                        Text("+$curr${"%,.0f".format(monthCmp.lastMonthIncome)}", style = MaterialTheme.typography.labelSmall, color = palette.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
