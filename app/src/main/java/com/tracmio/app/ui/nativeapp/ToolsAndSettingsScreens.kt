package com.tracmio.app.ui.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tracmio.app.data.MoneyPaise
import com.tracmio.app.data.NativeTracmioUiState
import com.tracmio.app.data.repository.FinanceRepository
import com.tracmio.app.data.security.DeviceSecurityCapability
import com.tracmio.app.data.security.TracmioSecurityEngine
import kotlinx.coroutines.delay

@Composable
fun PendingRefundsScreen(
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    onBack: () -> Unit
) {
    val curr = settings.currencySymbol
    var title by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("Amazon / Flipkart") }
    var amount by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("pending_refunds_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text("Pending Refunds & Returns", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
            }
        }

        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("📦 Track New Pending Refund", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Order / Item Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Merchant / App") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount ($curr)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank() && amt > 0.0) {
                            viewModel.addPendingRefund(title.trim(), amt, source.trim().ifBlank { "Store / App" })
                            title = ""
                            amount = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Track Refund", color = palette.background, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (state.pendingRefunds.isEmpty()) {
            item {
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Text("No pending refunds right now. Add returns or cancelled UPI orders to credit them when received.", color = palette.textSecondary)
                }
            }
        } else {
            items(state.pendingRefunds, key = { it.id }) { ref ->
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ref.title, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                            Text("$curr${"%,.0f".format(ref.amount)} · ${ref.source}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { viewModel.markRefundReceived(ref) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("Received ✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text("✕", color = Color(0xFFFB7185), modifier = Modifier.clickable { viewModel.deletePendingRefund(ref.id) }.padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionsAndAcademicScreen(
    subRoute: String,
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    onBack: () -> Unit,
    onSelectSubRoute: (String) -> Unit
) {
    val curr = settings.currencySymbol
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("28") }
    var examTitle by remember { mutableStateOf("") }
    var examFee by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("subscriptions_academic_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    if (subRoute == "recharge") "SIM & WiFi Recharge" else "Subscriptions & Bills",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("recharge" to "📶 SIM / WiFi", "subscriptions" to "🔁 Subscriptions", "academic" to "🎓 College Fees").forEach { (key, label) ->
                    val sel = subRoute == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) palette.primary else palette.surface)
                            .clickable { onSelectSubRoute(key) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (sel) palette.background else palette.textPrimary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (subRoute == "academic") {
            item {
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Text("🎓 College & Exam Fee Tracker", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                    OutlinedTextField(value = examTitle, onValueChange = { examTitle = it }, label = { Text("Exam / Tuition Fee Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = examFee, onValueChange = { examFee = it }, label = { Text("Fee ($curr)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = examDate, onValueChange = { examDate = it }, label = { Text("Due Date") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            val fee = examFee.toDoubleOrNull() ?: 0.0
                            if (examTitle.isNotBlank() && fee > 0.0) {
                                viewModel.addAcademicEvent(examTitle.trim(), fee, examDate.trim().ifBlank { "Upcoming" }, "Current")
                                examTitle = ""
                                examFee = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Add Fee Deadline", color = palette.background, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            items(state.academicEvents, key = { it.id }) { ev ->
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(ev.title, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                            Text("$curr${"%,.0f".format(ev.feeAmount)} · Due: ${ev.dueDate}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                        if (!ev.isPaid) {
                            Button(onClick = { viewModel.markAcademicEventPaid(ev) }, colors = ButtonDefaults.buttonColors(containerColor = palette.primary)) {
                                Text("Mark Paid", color = palette.background, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("✓ Paid", color = Color(0xFF34D399), fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        } else {
            item {
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Text(
                        if (subRoute == "recharge") "📶 Track SIM / WiFi Pack Validity" else "🔁 Add Recurring Subscription",
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.primary
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(if (subRoute == "recharge") "Operator (e.g. Jio 5G / ACT Fibernet)" else "Service Name (e.g. Spotify, Netflix)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount ($curr)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Validity (Days)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("28", "56", "84", "30").forEach { d ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (days == d) palette.primary else palette.surface,
                                modifier = Modifier.clickable { days = d }
                            ) {
                                Text("${d}d", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = if (days == d) palette.background else palette.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val amt = amount.toDoubleOrNull() ?: 0.0
                            val d = days.toIntOrNull() ?: 28
                            if (name.isNotBlank() && amt > 0.0) {
                                viewModel.addSubscription(name.trim(), amt, "${d}d", isSim = subRoute == "recharge", validityDays = d)
                                name = ""
                                amount = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Save Pack / Subscription", color = palette.background, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            items(state.subscriptions, key = { it.id }) { sub ->
                val daysLeft = ((sub.nextDueDateMillis - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0L)
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sub.name, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                            Text("$curr${"%,.0f".format(sub.amount)} · ${sub.billingCycle} · ${daysLeft}d remaining", style = MaterialTheme.typography.labelSmall, color = palette.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { viewModel.paySubscriptionNow(sub) },
                                colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("⚡ Pay & Log", color = palette.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            }
                            Text("✕", color = Color(0xFFFB7185), modifier = Modifier.clickable { viewModel.deleteSubscription(sub) }.padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSearchScreen(
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val curr = settings.currencySymbol
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val matchedTools = remember(q) {
        if (q.isBlank()) ALL_TOOLS_CATALOG.take(6)
        else ALL_TOOLS_CATALOG.filter { it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q) }
    }
    val matchedTx = remember(q, state.transactions) {
        if (q.isBlank()) state.transactions.take(5)
        else state.transactions.filter {
            it.category.lowercase().contains(q) ||
                it.note.lowercase().contains(q) ||
                it.hashtags.lowercase().contains(q) ||
                it.paymentMethod.lowercase().contains(q) ||
                it.upiApp.lowercase().contains(q)
        }.take(20)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("global_search_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text("Global Search", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search transactions, #hashtags, UPI, or any tool...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("global_search_input")
            )
        }

        if (matchedTools.isNotEmpty()) {
            item { Text("MATCHING TOOLS", style = MaterialTheme.typography.labelMedium, color = palette.primary, fontWeight = FontWeight.ExtraBold) }
            items(matchedTools, key = { it.id }) { tool ->
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(tool.route) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${tool.emoji} ${tool.title}", fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text("Open →", color = palette.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (matchedTx.isNotEmpty()) {
            item { Text("TRANSACTIONS (${matchedTx.size})", style = MaterialTheme.typography.labelMedium, color = palette.primary, fontWeight = FontWeight.ExtraBold) }
            items(matchedTx, key = { it.id }) { tx ->
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tx.category, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text("${tx.paymentMethod}${if (tx.note.isNotBlank()) " · ${tx.note}" else ""}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                        }
                        Text(
                            "$curr${"%,.0f".format(tx.amount)}",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (tx.type.equals("INCOME", true)) palette.primary else Color(0xFFFB7185)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersAndWarrantyScreen(
    subRoute: String,
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    hostCallbacks: NativeHostCallbacks,
    onBack: () -> Unit,
    onSelectSubRoute: (String) -> Unit
) {
    val curr = settings.currencySymbol
    var remTitle by remember { mutableStateOf("") }
    var remAmt by remember { mutableStateOf("") }
    var remDate by remember { mutableStateOf("") }

    val warrantyTransactions = remember(state.transactions) {
        state.transactions.filter { it.warrantyDate.isNotBlank() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("reminders_warranty_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    if (subRoute == "warranty_vault") "Receipt Warranty Vault" else "9 PM Summary & Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("reminders" to "⏰ Smart Reminders", "warranty_vault" to "🛡️ Warranty Vault").forEach { (key, label) ->
                    val sel = subRoute == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) palette.primary else palette.surface)
                            .clickable { onSelectSubRoute(key) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (sel) palette.background else palette.textPrimary, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (subRoute == "warranty_vault") {
            if (warrantyTransactions.isEmpty()) {
                item {
                    LiquidGlassComponents(settings = settings, palette = palette) {
                        Text("🛡️ Receipt Warranty & Return Vault", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                        Text("When logging purchases, fill the optional Warranty / Return Deadline date to track expiry countdowns here offline.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    }
                }
            } else {
                items(warrantyTransactions, key = { it.id }) { tx ->
                    LiquidGlassComponents(settings = settings, palette = palette) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(tx.note.ifBlank { tx.category }, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                                Text("Deadline: ${tx.warrantyDate} · $curr${"%,.0f".format(tx.amount)}", style = MaterialTheme.typography.labelSmall, color = palette.primary)
                            }
                            Text("🛡️", fontSize = 20.sp)
                        }
                    }
                }
            }
        } else {
            item {
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🌙 9:00 PM Daily Expense Check-In", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                            Text("Rotates 100 local messages with + Income / + Expense quick actions", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                        }
                        Switch(
                            checked = state.dailyReminderEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setDailyReminderEnabled(enabled)
                                if (enabled && !hostCallbacks.hasNotificationPermission()) {
                                    hostCallbacks.requestNotificationPermission()
                                }
                                hostCallbacks.scheduleDailyReminder(enabled, 21, 0, settings.userName)
                            }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (!hostCallbacks.hasNotificationPermission()) {
                                    hostCallbacks.requestNotificationPermission()
                                }
                                hostCallbacks.dispatchTestReminder()
                                viewModel.postStatus("Dispatched offline reminder preview")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🔔 Preview Alert", color = palette.primary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val nextEnabled = !state.monthlyBackupEnabled
                                viewModel.setMonthlyBackupEnabled(nextEnabled)
                                if (nextEnabled && !hostCallbacks.hasNotificationPermission()) {
                                    hostCallbacks.requestNotificationPermission()
                                }
                                hostCallbacks.scheduleMonthlyBackupReminder(nextEnabled, 1, 20, 0)
                                viewModel.postStatus(if (nextEnabled) "Monthly backup alert active" else "Monthly backup alert paused")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (state.monthlyBackupEnabled) "💾 Monthly Backup ON" else "💾 Monthly OFF", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            item {
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Text("➕ Add Custom Offline Reminder", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
                    OutlinedTextField(value = remTitle, onValueChange = { remTitle = it }, label = { Text("Reminder (e.g. Pay WiFi Bill)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = remAmt, onValueChange = { remAmt = it }, label = { Text("Amount ($curr)") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = remDate, onValueChange = { remDate = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            if (remTitle.isNotBlank()) {
                                val amt = remAmt.toDoubleOrNull() ?: 0.0
                                viewModel.addReminder(remTitle.trim(), amt, remDate.trim().ifBlank { "Tomorrow" })
                                remTitle = ""
                                remAmt = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Save Reminder", color = palette.background, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            items(state.reminders, key = { it.id }) { rem ->
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(checked = rem.isCompleted, onCheckedChange = { viewModel.toggleReminderCompleted(rem) })
                            Column {
                                Text(rem.title, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                                Text("${rem.dueDate}${if (rem.amount > 0) " · $curr${"%,.0f".format(rem.amount)}" else ""}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                            }
                        }
                        Text("✕", color = Color(0xFFFB7185), modifier = Modifier.clickable { viewModel.deleteReminder(rem) }.padding(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DataAndExportScreen(
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    hostCallbacks: NativeHostCallbacks,
    onBack: () -> Unit
) {
    var showEncryptDialog by remember { mutableStateOf(false) }
    var showDecryptDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val pendingPreview by viewModel.pendingBackupPreview.collectAsStateWithLifecycle()
    val lastRestoreReport by viewModel.lastRestoreReport.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("data_export_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text("Backup, Restore & Exports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
            }
        }

        lastRestoreReport?.let { rep ->
            item {
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Text("✅ Last Restore Summary (${rep.mode.name})", fontWeight = FontWeight.ExtraBold, color = Color(0xFF34D399))
                    Text(
                        "Inserted: ${rep.insertedCount} · Skipped Duplicates: ${rep.skippedDuplicatesCount} · Rejected: ${rep.rejectedCount} · Attachments: ${rep.attachmentsRestoredCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textPrimary
                    )
                }
            }
        }

        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("💾 Full JSON Backup & Restore", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                Text("Includes accounts, allocations, transactions, goals, loans, trackers, attachments, and Liquid Glass preferences.", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                Button(
                    onClick = { hostCallbacks.exportPlainJsonBackup("tracmio_backup_16.13.json") },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export Full JSON Backup", color = palette.background, fontWeight = FontWeight.ExtraBold)
                }
                Button(
                    onClick = {
                        password = ""
                        showEncryptDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔐 Export AES-256-GCM Encrypted JSON", color = palette.primary, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { hostCallbacks.importJsonBackup(null) }, modifier = Modifier.weight(1f)) {
                        Text("Import Plain JSON", color = palette.textPrimary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            password = ""
                            showDecryptDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Import Encrypted", color = palette.primary, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("🧳 Legacy Migration Wizard (Tracmio / FinTrack)", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                Text(
                    "Safely import older JSON snapshots or unencrypted legacy SQLite (.db) backups using Safe Merge deduplication.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { hostCallbacks.importLegacyJsonBackup() },
                        modifier = Modifier.weight(1f).testTag("legacy_json_import_button")
                    ) {
                        Text("Legacy JSON", color = palette.textPrimary, fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { hostCallbacks.importLegacyDatabaseFile() },
                        modifier = Modifier.weight(1f).testTag("legacy_db_import_button")
                    ) {
                        Text("Legacy .DB File", color = palette.primary, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("📊 Excel (.xlsx), CSV & PDF Statement", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { hostCallbacks.exportWorkbookXlsx("tracmio_ledger_16.13.xlsx") },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export .XLSX", color = palette.primary, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { hostCallbacks.exportCsv("tracmio_transactions_16.13.csv") },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.surfaceElevated),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Export .CSV", color = palette.textPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { hostCallbacks.importCsv() }, modifier = Modifier.weight(1f)) {
                        Text("Import .CSV", color = palette.textPrimary)
                    }
                    Button(
                        onClick = { hostCallbacks.printStatement(state.accounts, state.transactions) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖨️ Print PDF", color = palette.background, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }

    if (showEncryptDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptDialog = false },
            containerColor = palette.surfaceElevated,
            title = { Text("🔐 Encrypt JSON Backup", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Enter Strong Backup Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.isNotBlank()) {
                            hostCallbacks.exportEncryptedJsonBackup("tracmio_encrypted_16.13.tracmio.json", password.toCharArray())
                            password = ""
                            showEncryptDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) { Text("Export Encrypted", color = palette.background, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { password = ""; showEncryptDialog = false }) { Text("Cancel", color = palette.textSecondary) } }
        )
    }

    if (showDecryptDialog) {
        AlertDialog(
            onDismissRequest = { showDecryptDialog = false },
            containerColor = palette.surfaceElevated,
            title = { Text("🔓 Decrypt JSON Backup", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary) },
            text = {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Enter Backup Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.isNotBlank()) {
                            hostCallbacks.importJsonBackup(password.toCharArray())
                            password = ""
                            showDecryptDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) { Text("Choose File", color = palette.background, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { password = ""; showDecryptDialog = false }) { Text("Cancel", color = palette.textSecondary) } }
        )
    }

    pendingPreview?.let { preview ->
        var restoreMode by remember(preview) { mutableStateOf(FinanceRepository.BackupRestoreMode.SAFE_MERGE) }
        val rep = preview.report
        AlertDialog(
            onDismissRequest = { viewModel.dismissBackupPreview() },
            containerColor = palette.surfaceElevated,
            title = {
                Text(
                    "📦 Confirm Backup Restore (v${rep.formatVersion})",
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Accounts: ${rep.accountCount} · Transactions: ${rep.transactionCount} (${rep.duplicateTransactionCount} already in vault)",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textPrimary
                    )
                    Text(
                        "Budgets: ${rep.budgetCount} · Goals: ${rep.goalCount} · Loans: ${rep.loanCount} · Trackers: ${rep.trackerCount} · Attachments: ${rep.attachmentCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .clickable { restoreMode = FinanceRepository.BackupRestoreMode.SAFE_MERGE }
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = restoreMode == FinanceRepository.BackupRestoreMode.SAFE_MERGE,
                            onClick = { restoreMode = FinanceRepository.BackupRestoreMode.SAFE_MERGE }
                        )
                        Column {
                            Text("Safe Merge (Recommended)", fontWeight = FontWeight.Bold, color = palette.textPrimary)
                            Text("Keeps existing records and deduplicates by UID", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .clickable { restoreMode = FinanceRepository.BackupRestoreMode.CLEAN_REPLACE }
                            .padding(8.dp)
                    ) {
                        RadioButton(
                            selected = restoreMode == FinanceRepository.BackupRestoreMode.CLEAN_REPLACE,
                            onClick = { restoreMode = FinanceRepository.BackupRestoreMode.CLEAN_REPLACE }
                        )
                        Column {
                            Text("Clean Replace", fontWeight = FontWeight.Bold, color = Color(0xFFFB7185))
                            Text("Wipes existing local database before restoring", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                        }
                    }
                    if (restoreMode == FinanceRepository.BackupRestoreMode.CLEAN_REPLACE) {
                        Text(
                            "⚠️ Warning: Clean Replace will permanently erase all current local vault tables before importing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFBBF24),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmBackupRestore(restoreMode) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (restoreMode == FinanceRepository.BackupRestoreMode.CLEAN_REPLACE) Color(0xFFFB7185) else palette.primary
                    ),
                    modifier = Modifier.testTag("confirm_backup_restore_button")
                ) {
                    Text(
                        if (restoreMode == FinanceRepository.BackupRestoreMode.CLEAN_REPLACE) "Replace Vault" else "Merge Safely",
                        color = palette.background,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBackupPreview() }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }
}

@Composable
fun PrivacyAndSecurityAuditScreen(
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    hostCallbacks: NativeHostCallbacks,
    onBack: () -> Unit
) {
    var report by remember { mutableStateOf<TracmioSecurityEngine.DatabaseHealthReport?>(null) }
    val hasNotif = hostCallbacks.hasNotificationPermission()
    val hasCam = hostCallbacks.hasCameraPermission()
    val bioCap = hostCallbacks.getDeviceSecurityCapability()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("security_audit_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text("Offline Security Audit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
            }
        }

        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("🛡️ Zero-Network Offline Guarantees", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                Text("• Network Permissions: 0 (INTERNET & ACCESS_NETWORK_STATE omitted)", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                Text("• Local Database: Room + SQLCipher AES encryption", color = palette.textPrimary)
                Text("• Backup Cipher: PBKDF2-HMAC-SHA256 (150k iters) + AES-256-GCM", color = palette.textPrimary)
                Text("• Biometric Hardware: ${bioCap.name}", color = palette.textSecondary)
                Text("• Notifications Permission: ${if (hasNotif) "Granted" else "Not Requested / Denied"}", color = palette.textSecondary)
                Text("• Camera Permission: ${if (hasCam) "Granted" else "Optional (On-Demand)"}", color = palette.textSecondary)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (!hasNotif) {
                        OutlinedButton(onClick = { hostCallbacks.requestNotificationPermission() }, modifier = Modifier.weight(1f)) {
                            Text("Enable Alerts", color = palette.primary)
                        }
                    }
                    Button(
                        onClick = { report = viewModel.runDatabaseHealthReport() },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Run DB Integrity Check", color = palette.background, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        report?.let { rep ->
            item {
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Text(
                        if (rep.isHealthy) "✅ Database Integrity: HEALTHY" else "⚠️ Database Integrity Warning",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (rep.isHealthy) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                    Text(rep.diagnosticSummary, style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                    Text("PRAGMA integrity_check: ${rep.pragmaIntegrity}", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    Text(
                        "FK errors: ${rep.foreignKeyErrors} · Orphan Tx: ${rep.orphanedTransactions} · Orphan Dependents: ${rep.orphanDependentRecords} · Dup UIDs: ${rep.duplicateUidCount} · Invalid Amts: ${rep.invalidAmountCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                    if (!rep.isHealthy) {
                        Button(
                            onClick = { viewModel.repairDatabaseOrphans { updated -> report = updated } },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Repair Orphans & Duplicate UIDs", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppLockScreen(
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    hostCallbacks: NativeHostCallbacks,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var useBiometrics by remember { mutableStateOf(state.appLockState.biometricEnabled) }
    var question by remember { mutableStateOf(state.appLockState.securityQuestion.ifBlank { "What was your first school's name?" }) }
    var answer by remember { mutableStateOf("") }
    var pendingRecoveryKeyDialog by remember { mutableStateOf<String?>(null) }
    var savedRecoveryAck by remember { mutableStateOf(false) }

    var pendingSensitiveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var reauthPin by remember { mutableStateOf("") }
    var reauthError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("app_lock_settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text("App Lock & Biometrics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
            }
        }

        if (!state.appLockState.secureStorageAvailable) {
            item {
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Text(
                        "⚠️ Android Keystore secure storage unavailable on this device/environment.",
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text(
                    if (state.appLockState.isEnabled) "🔐 App Lock is ACTIVE" else "🔓 App Lock is Optional (Off)",
                    fontWeight = FontWeight.ExtraBold,
                    color = if (state.appLockState.isEnabled) palette.primary else palette.textPrimary
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("4 or 6-Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable Biometric / Device Unlock", color = palette.textPrimary)
                    Switch(checked = useBiometrics, onCheckedChange = { useBiometrics = it })
                }
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Mandatory Recovery Security Question") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("Security Answer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (pin.length >= 4 && question.isNotBlank() && answer.isNotBlank()) {
                            val applyAction: () -> Unit = {
                                val recKey = viewModel.enableAppLock(pin, useBiometrics, question, answer)
                                if (recKey.isNotBlank()) {
                                    pendingRecoveryKeyDialog = recKey
                                    savedRecoveryAck = false
                                    pin = ""
                                    answer = ""
                                }
                            }
                            if (state.appLockState.isEnabled) {
                                reauthPin = ""
                                reauthError = null
                                pendingSensitiveAction = applyAction
                            } else {
                                applyAction()
                            }
                        } else {
                            viewModel.postStatus("Enter a 4-6 digit PIN and security question/answer")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.appLockState.isEnabled) "Update App Lock & Recovery Key" else "Enable Liquid Glass App Lock", color = palette.background, fontWeight = FontWeight.ExtraBold)
                }

                if (state.appLockState.isEnabled) {
                    OutlinedButton(
                        onClick = {
                            reauthPin = ""
                            reauthError = null
                            pendingSensitiveAction = {
                                viewModel.disableAppLock()
                                pendingRecoveryKeyDialog = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable App Lock", color = Color(0xFFFB7185), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    pendingRecoveryKeyDialog?.let { recKey ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = palette.surfaceElevated,
            title = { Text("🔑 Save Your Recovery Key (Shown Once)", fontWeight = FontWeight.ExtraBold, color = palette.primary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Store this recovery key offline. For your security, Tracmio only stores an irreversible cryptographic hash and cannot display it again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(palette.surface).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(recKey, fontWeight = FontWeight.ExtraBold, color = palette.primary)
                        TextButton(onClick = { hostCallbacks.copyToClipboard(recKey) }) {
                            Text("Copy", color = palette.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { savedRecoveryAck = !savedRecoveryAck }
                    ) {
                        Checkbox(checked = savedRecoveryAck, onCheckedChange = { savedRecoveryAck = it })
                        Text("I have saved my recovery key safely", style = MaterialTheme.typography.bodySmall, color = palette.textPrimary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { pendingRecoveryKeyDialog = null },
                    enabled = savedRecoveryAck,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Text("Done", color = palette.background, fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }

    pendingSensitiveAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingSensitiveAction = null },
            containerColor = palette.surfaceElevated,
            title = { Text("🛡️ Confirm Current PIN", fontWeight = FontWeight.ExtraBold, color = palette.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Verify your current App Lock PIN before modifying or disabling security.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                    OutlinedTextField(
                        value = reauthPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) reauthPin = it },
                        label = { Text("Current PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    reauthError?.let { Text(it, color = Color(0xFFFB7185), style = MaterialTheme.typography.labelSmall) }
                    if (state.appLockState.biometricEnabled) {
                        OutlinedButton(
                            onClick = {
                                hostCallbacks.authenticateBiometric(
                                    onSuccess = {
                                        viewModel.unlockWithBiometricSuccess()
                                        pendingSensitiveAction = null
                                        action()
                                    },
                                    onFailure = { err -> reauthError = err }
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Verify with Biometrics", color = palette.primary)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.verifyCurrentPinForSensitiveAction(reauthPin)) {
                            pendingSensitiveAction = null
                            action()
                        } else {
                            val lockSec = viewModel.uiState.value.appLockState.lockoutRemainingSeconds
                            reauthError = if (lockSec > 0) "Too many attempts. Locked for ${lockSec}s" else "Incorrect current PIN"
                            reauthPin = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                ) {
                    Text("Verify", color = palette.background, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSensitiveAction = null }) {
                    Text("Cancel", color = palette.textSecondary)
                }
            }
        )
    }
}

@Composable
fun AppLockOverlayScreen(
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    hostCallbacks: NativeHostCallbacks
) {
    var enteredPin by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var secretInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    var remainingLockoutSec by remember(state.appLockState.lockoutRemainingSeconds) {
        mutableIntStateOf(state.appLockState.lockoutRemainingSeconds)
    }
    LaunchedEffect(state.appLockState.lockoutRemainingSeconds) {
        var cur = state.appLockState.lockoutRemainingSeconds
        remainingLockoutSec = cur
        while (cur > 0) {
            delay(1000L)
            cur--
            remainingLockoutSec = cur
        }
        viewModel.refreshAppLockState()
    }
    val isLockedOut = remainingLockoutSec > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.96f))
            .systemBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassComponents(settings = settings, palette = palette) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassBadge3D("🔐", palette.primary, 52.dp)
                Text("Tracmio Vault Locked", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)

                if (!showRecovery) {
                    Text(
                        if (isLockedOut) "Locked for ${remainingLockoutSec}s (${state.appLockState.failedAttempts} failed attempts)"
                        else "Enter ${state.appLockState.pinLength}-digit PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLockedOut) Color(0xFFFBBF24) else palette.textSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(state.appLockState.pinLength.coerceIn(4, 6)) { idx ->
                            val filled = idx < enteredPin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) palette.primary else palette.surface)
                                    .border(1.4.dp, palette.primary, CircleShape)
                            )
                        }
                    }
                    errorText?.let { Text(it, color = Color(0xFFFB7185), style = MaterialTheme.typography.labelSmall) }

                    // Apple-style numeric keypad
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("⌫", "0", "✓")
                    ).forEach { rowDigits ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            rowDigits.forEach { d ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(if (isLockedOut) palette.surface.copy(alpha = 0.5f) else palette.surfaceElevated)
                                        .border(1.dp, palette.surfaceBorder, CircleShape)
                                        .clickable(enabled = !isLockedOut) {
                                            errorText = null
                                            when (d) {
                                                "⌫" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                                "✓" -> {
                                                    if (!viewModel.unlockWithPin(enteredPin)) {
                                                        val sec = viewModel.uiState.value.appLockState.lockoutRemainingSeconds
                                                        errorText = if (sec > 0) "Too many failed attempts. Wait ${sec}s" else "Incorrect PIN"
                                                        enteredPin = ""
                                                    }
                                                }
                                                else -> if (enteredPin.length < 6) {
                                                    enteredPin += d
                                                    if (enteredPin.length == state.appLockState.pinLength) {
                                                        if (viewModel.unlockWithPin(enteredPin)) {
                                                            enteredPin = ""
                                                        } else {
                                                            val sec = viewModel.uiState.value.appLockState.lockoutRemainingSeconds
                                                            errorText = if (sec > 0) "Too many failed attempts. Wait ${sec}s" else "Incorrect PIN"
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Text(
                                        d,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isLockedOut) palette.textSecondary else palette.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.appLockState.biometricEnabled) {
                            OutlinedButton(
                                onClick = {
                                    hostCallbacks.authenticateBiometric(
                                        onSuccess = { viewModel.unlockWithBiometricSuccess() },
                                        onFailure = { errorText = it }
                                    )
                                }
                            ) { Text("Biometric Unlock", color = palette.primary) }
                        }
                        TextButton(onClick = { showRecovery = true }) {
                            Text("Forgot PIN?", color = palette.textSecondary)
                        }
                    }
                } else {
                    Text(
                        "Question: ${state.appLockState.securityQuestion.ifBlank { "Enter Recovery Key (TRAC-XXXX-XXXX)" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textPrimary
                    )
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it },
                        label = { Text("Security Answer or TRAC-XXXX-XXXX Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorText?.let { Text(it, color = Color(0xFFFB7185), style = MaterialTheme.typography.labelSmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showRecovery = false }) { Text("Back", color = palette.textSecondary) }
                        Button(
                            onClick = {
                                if (!viewModel.recoverPinWithSecret(secretInput, null)) {
                                    errorText = "Invalid answer or recovery key"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.primary)
                        ) { Text("Verify & Unlock", color = palette.background, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemesStudioScreen(
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    hostCallbacks: NativeHostCallbacks,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("themes_studio_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                    Text("← Back", color = palette.primary, fontWeight = FontWeight.ExtraBold)
                }
                Text("Liquid Glass UI Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
            }
        }

        // 10 Themes
        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("🎨 Color Themes (${ThemePresets.ALL_THEMES.size} Presets)", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePresets.ALL_THEMES.forEach { t ->
                        val sel = settings.themePreset == t.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(t.backgroundMid)
                                .border(if (sel) 2.dp else 1.dp, if (sel) t.primary else t.surfaceBorder, RoundedCornerShape(14.dp))
                                .clickable { viewModel.updateSettings { it.copy(themePreset = t.id) } }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(t.primary))
                                Text(t.displayName, style = MaterialTheme.typography.labelSmall, color = t.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 10 Glass UI Styles
        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("✨ Liquid Glass UI Styles (${ThemePresets.GLASS_UI_STYLES.size})", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePresets.GLASS_UI_STYLES.forEach { (id, label) ->
                        val sel = settings.glassUiStyle == id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) palette.primary else palette.surface)
                                .clickable { viewModel.updateSettings { it.copy(glassUiStyle = id) } }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (sel) palette.background else palette.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 10 3D Mesh Wallpapers & Custom Image Wallpaper
        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("🌌 3D Mesh Wallpapers (${ThemePresets.WALLPAPER_PRESETS.size})", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePresets.WALLPAPER_PRESETS.forEach { (id, label) ->
                        val sel = settings.wallpaperPreset == id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) palette.primary.copy(alpha = 0.25f) else palette.surface)
                                .border(1.dp, if (sel) palette.primary else palette.surfaceBorder, RoundedCornerShape(12.dp))
                                .clickable { viewModel.updateSettings { it.copy(wallpaperPreset = id, customWallpaperPath = null) } }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = if (sel) palette.primary else palette.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { hostCallbacks.chooseWallpaperImage() }, modifier = Modifier.weight(1f)) {
                        Text("🖼️ Pick Photo", color = palette.primary)
                    }
                    OutlinedButton(onClick = { hostCallbacks.captureWallpaperImage() }, modifier = Modifier.weight(1f)) {
                        Text("📷 Camera", color = palette.textPrimary)
                    }
                }
            }
        }

        // Apple-style Capsule Sliders +1/-1 + Sensor Parallax
        item {
            LiquidGlassComponents(settings = settings, palette = palette) {
                Text("🎛️ Glass Optics & UI Scale", fontWeight = FontWeight.ExtraBold, color = palette.primary)
                LiquidCapsuleSlider(
                    label = "Wallpaper Visibility",
                    value = settings.backgroundVisibility,
                    valueRange = 0.05f..1.0f,
                    displayText = "${(settings.backgroundVisibility * 100).toInt()}%",
                    palette = palette,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(backgroundVisibility = v) } },
                    onStepMinus = { viewModel.updateSettings { it.copy(backgroundVisibility = (it.backgroundVisibility - 0.01f).coerceAtLeast(0.05f)) } },
                    onStepPlus = { viewModel.updateSettings { it.copy(backgroundVisibility = (it.backgroundVisibility + 0.01f).coerceAtMost(1.0f)) } }
                )
                LiquidCapsuleSlider(
                    label = "Aurora Glow Intensity",
                    value = settings.glowIntensity,
                    valueRange = 0.1f..1.2f,
                    displayText = "${(settings.glowIntensity * 100).toInt()}%",
                    palette = palette,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(glowIntensity = v) } },
                    onStepMinus = { viewModel.updateSettings { it.copy(glowIntensity = (it.glowIntensity - 0.01f).coerceAtLeast(0.1f)) } },
                    onStepPlus = { viewModel.updateSettings { it.copy(glowIntensity = (it.glowIntensity + 0.01f).coerceAtMost(1.2f)) } }
                )
                LiquidCapsuleSlider(
                    label = "UI Scale",
                    value = settings.uiScale,
                    valueRange = 0.8f..1.3f,
                    displayText = "${(settings.uiScale * 100).toInt()}%",
                    palette = palette,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(uiScale = v) } },
                    onStepMinus = { viewModel.updateSettings { it.copy(uiScale = (it.uiScale - 0.01f).coerceAtLeast(0.8f)) } },
                    onStepPlus = { viewModel.updateSettings { it.copy(uiScale = (it.uiScale + 0.01f).coerceAtMost(1.3f)) } }
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Phone-Tilt Liquid Parallax", fontWeight = FontWeight.Bold, color = palette.textPrimary)
                        Text("Subtle SensorManager tilt flow for aurora mesh", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    }
                    Switch(
                        checked = settings.deviceMotionEnabled,
                        onCheckedChange = { enabled -> viewModel.updateSettings { it.copy(deviceMotionEnabled = enabled) } }
                    )
                }
            }
        }
    }
}

@Composable
fun StandaloneCashCounterScreen(
    state: NativeTracmioUiState,
    settings: LiquidGlassSettings,
    palette: ThemePalette,
    viewModel: NativeTracmioViewModel,
    onBack: () -> Unit
) {
    val preferredAccounts = remember(state.accounts) {
        val wallets = state.accounts.filter { it.accountType.equals("Cash", true) || it.accountType.equals("Wallet", true) }
        if (wallets.isNotEmpty()) wallets else state.accounts
    }
    var selectedAccountId by remember(preferredAccounts) {
        mutableLongStateOf(preferredAccounts.firstOrNull()?.id ?: 0L)
    }
    val cashAccount = state.accounts.firstOrNull { it.id == selectedAccountId } ?: preferredAccounts.firstOrNull()
    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
        if (cashAccount == null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text("← Back", color = palette.primary) }
                LiquidGlassComponents(settings = settings, palette = palette) {
                    Text("Create a Cash or Wallet account first to reconcile cash denominations.", color = palette.textSecondary)
                }
            }
        } else {
            CashCountDenominationDialog(
                account = cashAccount,
                palette = palette,
                currencySymbol = settings.currencySymbol,
                availableAccounts = state.accounts,
                onSelectAccount = { selectedAccountId = it.id },
                onDismiss = onBack,
                onApplyReconcile = { _, note, lines ->
                    viewModel.applyCashDenominationCount(lines, note, cashAccount.id)
                    onBack()
                }
            )
        }
    }
}
