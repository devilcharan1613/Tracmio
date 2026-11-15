package com.tracmio.app.ui.nativeapp

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tracmio.app.data.*
import com.tracmio.app.data.local.*
import com.tracmio.app.data.repository.FinanceRepository
import com.tracmio.app.data.security.AppLockManager
import com.tracmio.app.data.security.TracmioSecurityEngine
import com.tracmio.app.data.services.NativeBackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class NativeTracmioViewModel(application: Application) : AndroidViewModel(application) {
    data class PendingBackupPreview(
        val plainJson: String,
        val report: FinanceRepository.BackupPreviewReport
    )

    private val appContext = application.applicationContext
    private val prefsStore = PreferencesAndThemeStore(appContext)
    private val appLockManager = AppLockManager(appContext)
    private val initializingVault = AtomicBoolean(false)

    private val vaultStateFlow = MutableStateFlow<TracmioSecurityEngine.VaultInitState>(
        TracmioSecurityEngine.VaultInitState.Loading
    )
    private val repositoryFlow = MutableStateFlow<FinanceRepository?>(null)
    private val categoriesFlow = MutableStateFlow<List<CategoryEntity>>(emptyList())

    val settingsFlow: StateFlow<LiquidGlassSettings> = prefsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = prefsStore.getInitialSettings()
    )

    private val quickChipsFlow = MutableStateFlow(prefsStore.getQuickSpendChips())
    private val pendingRefundsFlow = MutableStateFlow(prefsStore.getPendingRefunds())
    private val pinnedToolIdsFlow = MutableStateFlow(prefsStore.getPinnedToolIds())
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val deletedTxFlow = MutableStateFlow<TransactionEntity?>(null)
    private val deletedPairTxFlow = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private val deletedChipFlow = MutableStateFlow<QuickSpendChip?>(null)
    private val isAppLockedFlow = MutableStateFlow(appLockManager.getState().isEnabled)
    private val appLockStateFlow = MutableStateFlow(appLockManager.getState())
    private val pendingNotificationActionFlow = MutableStateFlow<String?>(null)
    private val dailyReminderEnabledFlow = MutableStateFlow(prefsStore.isDailyReminderEnabled())
    private val monthlyBackupEnabledFlow = MutableStateFlow(prefsStore.isMonthlyBackupEnabled())

    private val pendingBackupPreviewFlow = MutableStateFlow<PendingBackupPreview?>(null)
    val pendingBackupPreview: StateFlow<PendingBackupPreview?> = pendingBackupPreviewFlow.asStateFlow()

    private val lastRestoreReportFlow = MutableStateFlow<FinanceRepository.RestoreReport?>(null)
    val lastRestoreReport: StateFlow<FinanceRepository.RestoreReport?> = lastRestoreReportFlow.asStateFlow()

    private val coreLedgerFlow = repositoryFlow.flatMapLatest { repo ->
        if (repo == null) {
            flowOf(CoreLedgerSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        } else {
            combine(
                repo.accounts,
                repo.transactions,
                repo.budgets,
                repo.goals,
                repo.loans
            ) { accounts, transactions, budgets, goals, loans ->
                CoreLedgerSnapshot(accounts, transactions, budgets, goals, loans)
            }
        }
    }

    private val planningExtrasFlow = repositoryFlow.flatMapLatest { repo ->
        if (repo == null) {
            flowOf(PlanningExtrasSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        } else {
            combine(
                repo.goalContributions,
                repo.loanPayments,
                repo.persons,
                repo.customTrackers,
                repo.trackerFolders
            ) { contribs, payments, persons, trackers, folders ->
                PlanningExtrasSnapshot(contribs, payments, persons, trackers, folders)
            }
        }
    }

    private val toolsEntitiesFlow = repositoryFlow.flatMapLatest { repo ->
        if (repo == null) {
            flowOf(ToolsEntitiesSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        } else {
            combine(
                repo.subscriptions,
                repo.academicEvents,
                repo.reminders,
                repo.khataEntries,
                repo.wishlistCooldowns
            ) { subs, academic, reminders, khata, wishlist ->
                ToolsEntitiesSnapshot(subs, academic, reminders, khata, wishlist)
            }
        }
    }

    private val cashbackFlow = repositoryFlow.flatMapLatest { it?.cashbackRewards ?: flowOf(emptyList()) }
    private val ppuFlow = repositoryFlow.flatMapLatest { it?.pricePerUseItems ?: flowOf(emptyList()) }
    private val trackerEntriesFlow = repositoryFlow.flatMapLatest { it?.trackerEntries ?: flowOf(emptyList()) }

    private val rewardAndTrackFlow = combine(
        cashbackFlow,
        ppuFlow,
        trackerEntriesFlow,
        quickChipsFlow,
        pendingRefundsFlow
    ) { cashback, ppu, trackerEntries, chips, refunds ->
        RewardAndTrackSnapshot(cashback, ppu, trackerEntries, chips, refunds)
    }

    private val uiFlagsFlow = combine(
        statusMessageFlow,
        deletedTxFlow,
        deletedChipFlow,
        isAppLockedFlow,
        appLockStateFlow
    ) { msg, delTx, delChip, locked, lockState ->
        UiFlagsSnapshot(msg, delTx, delChip, locked, lockState)
    }

    private val auxFlagsFlow = combine(
        vaultStateFlow,
        pinnedToolIdsFlow,
        pendingNotificationActionFlow,
        dailyReminderEnabledFlow,
        monthlyBackupEnabledFlow
    ) { vaultState, pinned, action, dailyRem, monthlyBackup ->
        AuxFlagsSnapshot(vaultState, pinned, action, dailyRem, monthlyBackup)
    }

    val uiState: StateFlow<NativeTracmioUiState> = combine(
        coreLedgerFlow,
        planningExtrasFlow,
        toolsEntitiesFlow,
        rewardAndTrackFlow,
        uiFlagsFlow,
        auxFlagsFlow
    ) { array ->
        val core = array[0] as CoreLedgerSnapshot
        val plan = array[1] as PlanningExtrasSnapshot
        val tools = array[2] as ToolsEntitiesSnapshot
        val rew = array[3] as RewardAndTrackSnapshot
        val flags = array[4] as UiFlagsSnapshot
        val aux = array[5] as AuxFlagsSnapshot

        val totalBal = MoneyPaise.roundRupees(core.accounts.sumOf { FinancialCalculator.balance(it, core.transactions) })
        val totalInc = MoneyPaise.toRupees(
            core.transactions.filter { it.type.equals("INCOME", true) }
                .sumOf { if (it.amountPaise > 0L) it.amountPaise else MoneyPaise.toPaise(it.amount) }
        )
        val totalExp = MoneyPaise.toRupees(
            core.transactions.filter { it.type.equals("EXPENSE", true) }
                .sumOf { if (it.amountPaise > 0L) it.amountPaise else MoneyPaise.toPaise(it.amount) }
        )

        NativeTracmioUiState(
            vaultInitState = aux.vaultInitState,
            accounts = core.accounts,
            transactions = core.transactions,
            budgets = core.budgets,
            goals = core.goals,
            goalContributions = plan.goalContributions,
            loans = core.loans,
            loanPayments = plan.loanPayments,
            persons = plan.persons,
            customTrackers = plan.customTrackers,
            trackerEntries = rew.trackerEntries,
            trackerFolders = plan.trackerFolders,
            subscriptions = tools.subscriptions,
            academicEvents = tools.academicEvents,
            reminders = tools.reminders,
            khataEntries = tools.khataEntries,
            wishlistCooldowns = tools.wishlistCooldowns,
            cashbackRewards = rew.cashbackRewards,
            pricePerUseItems = rew.pricePerUseItems,
            pendingRefunds = rew.pendingRefunds,
            quickSpendChips = rew.quickSpendChips,
            totalBalance = totalBal,
            totalIncome = totalInc,
            totalExpense = totalExp,
            statusMessage = flags.statusMessage,
            recentlyDeletedTransaction = flags.deletedTx,
            recentlyDeletedChip = flags.deletedChip,
            hasPendingUndo = flags.deletedTx != null || flags.deletedChip != null,
            isAppLocked = flags.isAppLocked,
            appLockState = flags.appLockState,
            pendingNotificationAction = aux.pendingNotificationAction,
            pinnedToolIds = aux.pinnedToolIds,
            dailyReminderEnabled = aux.dailyReminderEnabled,
            monthlyBackupEnabled = aux.monthlyBackupEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NativeTracmioUiState(
            vaultInitState = vaultStateFlow.value,
            quickSpendChips = quickChipsFlow.value,
            pendingRefunds = pendingRefundsFlow.value,
            isAppLocked = isAppLockedFlow.value,
            appLockState = appLockStateFlow.value,
            pinnedToolIds = pinnedToolIdsFlow.value,
            dailyReminderEnabled = dailyReminderEnabledFlow.value,
            monthlyBackupEnabled = monthlyBackupEnabledFlow.value
        )
    )

    init {
        repositoryFlow
            .flatMapLatest { it?.categories ?: flowOf(emptyList()) }
            .onEach { categoriesFlow.value = it }
            .launchIn(viewModelScope)
        initializeVault()
    }

    fun retryVaultInitialization() {
        if (repositoryFlow.value != null) return
        initializeVault()
    }

    private fun initializeVault() {
        if (!initializingVault.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                vaultStateFlow.value = TracmioSecurityEngine.VaultInitState.Loading
                when (val result = TracmioDatabase.openVaultOrFail(appContext)) {
                    is VaultOpenResult.Success -> {
                        val repo = FinanceRepository(result.database)
                        repositoryFlow.value = repo
                        vaultStateFlow.value = TracmioSecurityEngine.VaultInitState.Ready

                        LegacyMigrationGuard.runOneTimeLegacyMigrationIfNeeded(appContext, repo)
                        val existingAccounts = repo.accounts.first()
                        if (existingAccounts.isEmpty() && !prefsStore.hasSeededDefaults()) {
                            repo.insertAccount(
                                AccountEntity(
                                    name = "Cash Wallet",
                                    accountType = "Wallet",
                                    balance = 0.0,
                                    balancePaise = 0L,
                                    colorHex = "#00F5D4"
                                )
                            )
                            repo.insertAccount(
                                AccountEntity(
                                    name = "Primary Bank",
                                    accountType = "Bank",
                                    balance = 0.0,
                                    balancePaise = 0L,
                                    colorHex = "#38BDF8"
                                )
                            )
                            prefsStore.markSeededDefaults()
                        }
                    }
                    is VaultOpenResult.Failure -> {
                        repositoryFlow.value = null
                        vaultStateFlow.value = TracmioSecurityEngine.VaultInitState.SecurityError(
                            title = result.title,
                            detail = result.detail,
                            canRetry = true
                        )
                    }
                }
            } finally {
                initializingVault.set(false)
            }
        }
    }

    private fun requireRepo(): FinanceRepository? {
        val repo = repositoryFlow.value
        if (repo == null) {
            statusMessageFlow.value = "Encrypted vault is not unlocked yet"
        }
        return repo
    }

    private fun isValidPositiveAmount(v: Double): Boolean = v.isFinite() && v > 0.0
    private fun isValidNonNegativeAmount(v: Double): Boolean = v.isFinite() && v >= 0.0

    fun updateSettings(transform: (LiquidGlassSettings) -> LiquidGlassSettings) = prefsStore.updateSettings(transform)
    fun togglePrivacyMask() = prefsStore.updatePrivacyMask(!settingsFlow.value.privacyMaskActive)
    fun toggleStealthMode() = prefsStore.updateStealthMode(!settingsFlow.value.stealthModeActive)
    fun toggleRoundUp(enabled: Boolean) = prefsStore.updateSettings { it.copy(roundUpEnabled = enabled) }
    fun completeOnboarding(name: String, currency: String) = prefsStore.completeOnboarding(name, currency)
    fun triggerNotificationAction(action: String) { pendingNotificationActionFlow.value = action }
    fun consumePendingNotificationAction() { pendingNotificationActionFlow.value = null }
    fun clearStatusMessage() { statusMessageFlow.value = null }
    fun postStatus(message: String) { statusMessageFlow.value = message }

    fun toggleToolPin(toolId: String) {
        val current = pinnedToolIdsFlow.value.toMutableList()
        if (current.contains(toolId)) current.remove(toolId) else current.add(toolId)
        pinnedToolIdsFlow.value = current
        prefsStore.savePinnedToolIds(current)
    }

    fun executeQuickSpendChip(chip: QuickSpendChip) {
        if (!isValidPositiveAmount(chip.amount)) return
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = repo.accounts.first()
            val targetAcc = accounts.firstOrNull { it.accountType.equals("Wallet", true) || it.accountType.equals("Cash", true) }
                ?: accounts.firstOrNull()
                ?: return@launch
            val cleanAmt = MoneyPaise.roundRupees(chip.amount)
            val tx = TransactionEntity(
                accountId = targetAcc.id,
                type = "EXPENSE",
                amount = cleanAmt,
                amountPaise = MoneyPaise.toPaise(cleanAmt),
                category = chip.category,
                paymentMethod = if (targetAcc.accountType.equals("Cash", true)) "Cash" else "UPI",
                note = "${chip.emoji} ${chip.title} (Quick Spend)"
            )
            val id = repo.insertTransaction(tx)
            applyRoundUpIfEnabled(repo, cleanAmt)
            deletedTxFlow.value = tx.copy(id = id)
            deletedChipFlow.value = null
            statusMessageFlow.value = "Logged ${chip.title} (${settingsFlow.value.currencySymbol}${cleanAmt.toInt()})"
        }
    }

    fun addQuickSpendChip(emoji: String, title: String, amount: Double, category: String = "Food & Chai") {
        val cleanTitle = TracmioSecurityEngine.sanitizeInput(title, 30)
        if (cleanTitle.isBlank() || !isValidPositiveAmount(amount)) {
            statusMessageFlow.value = "Enter a valid chip title and positive amount"
            return
        }
        val newChip = QuickSpendChip(
            id = "chip_${System.currentTimeMillis()}",
            emoji = emoji,
            title = cleanTitle,
            amount = MoneyPaise.roundRupees(amount),
            category = TracmioSecurityEngine.sanitizeInput(category, 40).ifBlank { "Food & Chai" }
        )
        val updated = quickChipsFlow.value + newChip
        quickChipsFlow.value = updated
        prefsStore.saveQuickSpendChips(updated)
        statusMessageFlow.value = "Added Quick Spend chip: $cleanTitle"
    }

    fun deleteQuickSpendChip(chip: QuickSpendChip) {
        deletedChipFlow.value = chip
        deletedTxFlow.value = null
        val updated = quickChipsFlow.value.filterNot { it.id == chip.id }
        quickChipsFlow.value = updated
        prefsStore.saveQuickSpendChips(updated)
        statusMessageFlow.value = "Removed chip ${chip.title}"
    }

    fun performPendingUndo() {
        val delChip = deletedChipFlow.value
        if (delChip != null) {
            val restored = quickChipsFlow.value + delChip
            quickChipsFlow.value = restored
            prefsStore.saveQuickSpendChips(restored)
            deletedChipFlow.value = null
            statusMessageFlow.value = "Restored chip ${delChip.title}"
            return
        }
        val repo = requireRepo() ?: return
        val delTx = deletedTxFlow.value
        val delPair = deletedPairTxFlow.value
        if (delPair.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                repo.runInTransaction {
                    delPair.forEach { repo.insertTransaction(it.copy(id = 0)) }
                }
                deletedPairTxFlow.value = emptyList()
                deletedTxFlow.value = null
                statusMessageFlow.value = "Restored transfer pair"
            }
            return
        }
        if (delTx != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val existing = repo.transactions.first().any { it.id == delTx.id }
                if (existing) {
                    repo.deleteTransaction(delTx)
                    statusMessageFlow.value = "Undid quick spend"
                } else {
                    repo.insertTransaction(delTx.copy(id = 0))
                    statusMessageFlow.value = "Restored deleted transaction"
                }
                deletedTxFlow.value = null
            }
        }
    }

    fun saveAccount(
        editingId: Long?,
        name: String,
        accountType: String,
        balance: Double,
        creditLimit: Double = 0.0,
        statementDay: Int = 1,
        dueDay: Int = 15
    ) {
        val cleanName = TracmioSecurityEngine.sanitizeInput(name, 60)
        if (cleanName.isBlank() || !balance.isFinite() || !isValidNonNegativeAmount(creditLimit)) {
            statusMessageFlow.value = "Enter a valid account name and finite balance"
            return
        }
        val repo = requireRepo() ?: return
        val cleanBal = MoneyPaise.roundRupees(balance)
        val cleanLimit = MoneyPaise.roundRupees(creditLimit)
        val safeStatementDay = statementDay.coerceIn(1, 28)
        val safeDueDay = dueDay.coerceIn(1, 28)
        viewModelScope.launch(Dispatchers.IO) {
            if (editingId != null) {
                val old = repo.getAccountById(editingId)
                if (old != null) {
                    repo.updateAccount(
                        old.copy(
                            name = cleanName,
                            accountType = accountType,
                            type = accountType,
                            balance = cleanBal,
                            balancePaise = MoneyPaise.toPaise(cleanBal),
                            creditLimit = cleanLimit,
                            creditLimitPaise = MoneyPaise.toPaise(cleanLimit),
                            statementDay = safeStatementDay,
                            dueDay = safeDueDay,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    statusMessageFlow.value = "Updated account $cleanName"
                    return@launch
                }
            }
            repo.insertAccount(
                AccountEntity(
                    name = cleanName,
                    accountType = accountType,
                    type = accountType,
                    balance = cleanBal,
                    balancePaise = MoneyPaise.toPaise(cleanBal),
                    creditLimit = cleanLimit,
                    creditLimitPaise = MoneyPaise.toPaise(cleanLimit),
                    statementDay = safeStatementDay,
                    dueDay = safeDueDay
                )
            )
            statusMessageFlow.value = "Created account $cleanName"
        }
    }

    fun updateAccountAllocations(account: AccountEntity, allocations: List<AccountAllocation>) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val json = NativeLedgerCalculator.serializeAllocations(allocations)
            repo.updateAccount(account.copy(allocationsJson = json, updatedAt = System.currentTimeMillis()))
            statusMessageFlow.value = "Updated allocations for ${account.name}"
        }
    }

    fun reconcileAccountBalance(account: AccountEntity, targetActualBalance: Double, customNote: String? = null) {
        if (!targetActualBalance.isFinite()) {
            statusMessageFlow.value = "Enter a valid target balance"
            return
        }
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val applied = reconcileAccountBalanceInternal(repo, account, targetActualBalance, customNote)
            if (applied) {
                statusMessageFlow.value = "Reconciled ${account.name}"
            } else {
                statusMessageFlow.value = "${account.name} already matches target balance"
            }
        }
    }

    private suspend fun reconcileAccountBalanceInternal(
        repo: FinanceRepository,
        account: AccountEntity,
        targetActualBalance: Double,
        customNote: String? = null
    ): Boolean {
        val txns = repo.transactions.first()
        val currentBal = FinancialCalculator.balance(account, txns)
        val delta = targetActualBalance - currentBal
        if (kotlin.math.abs(delta) >= 0.01) {
            val cleanAbs = MoneyPaise.roundRupees(kotlin.math.abs(delta))
            val notePrefix = customNote?.takeIf { it.isNotBlank() }?.let { TracmioSecurityEngine.sanitizeInput(it, 120) }
            val finalNote = if (notePrefix != null) {
                "$notePrefix · Reconciled ${account.name} to ${settingsFlow.value.currencySymbol}${"%,.2f".format(targetActualBalance)}"
            } else {
                "Reconciled ${account.name} to ${settingsFlow.value.currencySymbol}${"%,.2f".format(targetActualBalance)}"
            }
            repo.insertTransaction(
                TransactionEntity(
                    accountId = account.id,
                    type = if (delta >= 0) "INCOME" else "EXPENSE",
                    amount = cleanAbs,
                    amountPaise = MoneyPaise.toPaise(cleanAbs),
                    category = "Balance Adjustment",
                    paymentMethod = account.accountType,
                    note = finalNote
                )
            )
            return true
        }
        return false
    }

    fun applyCashDenominationCount(lines: List<CashCountLine>, note: String, accountId: Long?) {
        val total = NativeLedgerCalculator.computeCashDenominationTotal(lines)
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = repo.accounts.first()
            val target = accounts.firstOrNull { it.id == accountId }
                ?: accounts.firstOrNull { it.accountType.equals("Cash", true) || it.accountType.equals("Wallet", true) }
                ?: return@launch
            reconcileAccountBalanceInternal(repo, target, total, note.ifBlank { "Cash Denomination Count" })
            statusMessageFlow.value = "Cash count applied to ${target.name} (${settingsFlow.value.currencySymbol}${"%,.0f".format(total)})"
        }
    }

    fun performAtmWithdrawal(amount: Double) {
        if (!isValidPositiveAmount(amount)) {
            statusMessageFlow.value = "Enter a positive withdrawal amount"
            return
        }
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = repo.accounts.first()
            val bank = accounts.firstOrNull { it.accountType.equals("Bank", true) } ?: accounts.firstOrNull() ?: return@launch
            val cash = accounts.firstOrNull { it.accountType.equals("Cash", true) || it.accountType.equals("Wallet", true) }
                ?: accounts.lastOrNull() ?: return@launch
            if (bank.id == cash.id) {
                statusMessageFlow.value = "Add a separate Bank and Cash/Wallet account to withdraw"
                return@launch
            }
            transferFunds(bank.id, cash.id, amount, "🏧 1-Tap ATM Withdrawal")
        }
    }

    fun transferFunds(fromId: Long, toId: Long, amount: Double, note: String) {
        if (!isValidPositiveAmount(amount) || fromId == toId) {
            statusMessageFlow.value = "Select distinct accounts and a positive transfer amount"
            return
        }
        val repo = requireRepo() ?: return
        val cleanAmt = MoneyPaise.roundRupees(amount)
        viewModelScope.launch(Dispatchers.IO) {
            val fromAcc = repo.getAccountById(fromId)
            val toAcc = repo.getAccountById(toId)
            if (fromAcc == null || toAcc == null) {
                statusMessageFlow.value = "Transfer accounts no longer exist"
                return@launch
            }
            val xferId = "xfer_${System.currentTimeMillis()}"
            val cleanNote = TracmioSecurityEngine.sanitizeInput(note, 150)
            repo.runInTransaction {
                repo.insertTransaction(
                    TransactionEntity(
                        accountId = fromId,
                        type = "TRANSFER_OUT",
                        amount = cleanAmt,
                        amountPaise = MoneyPaise.toPaise(cleanAmt),
                        category = "Transfer",
                        transferAccountId = toId,
                        transferId = xferId,
                        paymentMethod = "Bank Transfer",
                        note = cleanNote
                    )
                )
                repo.insertTransaction(
                    TransactionEntity(
                        accountId = toId,
                        type = "TRANSFER_IN",
                        amount = cleanAmt,
                        amountPaise = MoneyPaise.toPaise(cleanAmt),
                        category = "Transfer",
                        transferAccountId = fromId,
                        transferId = xferId,
                        paymentMethod = "Bank Transfer",
                        note = cleanNote
                    )
                )
            }
            statusMessageFlow.value = "Transferred ${settingsFlow.value.currencySymbol}${"%,.0f".format(cleanAmt)}"
        }
    }

    fun saveTransaction(
        editingId: Long?,
        accountId: Long,
        type: String,
        amount: Double,
        category: String,
        subcategory: String,
        paymentMethod: String,
        upiApp: String,
        note: String,
        warrantyDate: String,
        allocationId: String,
        cashPortion: Double,
        upiPortion: Double
    ) {
        if (!isValidPositiveAmount(amount) || !cashPortion.isFinite() || !upiPortion.isFinite() || cashPortion < 0.0 || upiPortion < 0.0) {
            statusMessageFlow.value = "Enter a valid positive transaction amount"
            return
        }
        val repo = requireRepo() ?: return
        val cleanAmt = MoneyPaise.roundRupees(amount)
        viewModelScope.launch(Dispatchers.IO) {
            val cleanNote = TracmioSecurityEngine.sanitizeInput(note, 300)
            val hashtags = Regex("#[A-Za-z0-9_]+").findAll(cleanNote).joinToString(" ") { it.value }
            val cleanCategory = TracmioSecurityEngine.sanitizeInput(category, 60).ifBlank { "Other" }
            val cleanSub = TracmioSecurityEngine.sanitizeInput(subcategory, 60)
            val cleanWarranty = TracmioSecurityEngine.sanitizeInput(warrantyDate, 30)

            if (paymentMethod == "Half-Cash + Half-UPI Split" && editingId == null && (cashPortion > 0.0 || upiPortion > 0.0)) {
                val cashAmt = MoneyPaise.roundRupees(if (cashPortion > 0) cashPortion else cleanAmt / 2.0)
                val upiAmt = MoneyPaise.roundRupees(if (upiPortion > 0) upiPortion else (cleanAmt - cashAmt).coerceAtLeast(0.0))
                val accounts = repo.accounts.first()
                val cashAcc = accounts.firstOrNull { it.accountType.equals("Cash", true) || it.accountType.equals("Wallet", true) }?.id ?: accountId
                repo.runInTransaction {
                    if (cashAmt > 0.0) {
                        repo.insertTransaction(
                            TransactionEntity(
                                accountId = cashAcc, type = type.uppercase(), amount = cashAmt, amountPaise = MoneyPaise.toPaise(cashAmt),
                                category = cleanCategory, subcategory = cleanSub, paymentMethod = "Cash",
                                note = "$cleanNote [Cash Split]".trim(), hashtags = hashtags, warrantyDate = cleanWarranty
                            )
                        )
                    }
                    if (upiAmt > 0.0) {
                        repo.insertTransaction(
                            TransactionEntity(
                                accountId = accountId, type = type.uppercase(), amount = upiAmt, amountPaise = MoneyPaise.toPaise(upiAmt),
                                category = cleanCategory, subcategory = cleanSub, paymentMethod = "UPI",
                                upiApp = upiApp.ifBlank { "UPI" }, note = "$cleanNote [UPI Split]".trim(),
                                hashtags = hashtags, warrantyDate = cleanWarranty
                            )
                        )
                    }
                    if (type.equals("EXPENSE", true)) {
                        applyRoundUpIfEnabled(repo, cashAmt + upiAmt)
                    }
                }
                statusMessageFlow.value = "Saved Half-Cash + Half-UPI split"
                return@launch
            }

            if (editingId != null) {
                repo.runInTransaction {
                    val existing = repo.getTransactionById(editingId)
                    if (existing != null && existing.allocationId.isNotBlank() && !existing.type.equals("TRANSFER", true)) {
                        repo.getAccountById(existing.accountId)?.let { prevAcc ->
                            val reverseType = if (existing.type.equals("INCOME", true)) "EXPENSE" else "INCOME"
                            val revertedAcc = NativeLedgerCalculator.adjustAllocationsForTransaction(
                                prevAcc, existing.allocationId, reverseType, existing.amount
                            )
                            if (revertedAcc != prevAcc) repo.updateAccount(revertedAcc)
                        }
                    }
                    val updated = if (existing != null) {
                        existing.copy(
                            accountId = accountId,
                            type = type.uppercase(),
                            amount = cleanAmt,
                            amountPaise = MoneyPaise.toPaise(cleanAmt),
                            category = cleanCategory,
                            subcategory = cleanSub,
                            paymentMethod = paymentMethod,
                            upiApp = upiApp,
                            note = cleanNote,
                            hashtags = hashtags,
                            warrantyDate = cleanWarranty,
                            allocationId = allocationId
                        )
                    } else {
                        TransactionEntity(
                            id = editingId,
                            accountId = accountId,
                            type = type.uppercase(),
                            amount = cleanAmt,
                            amountPaise = MoneyPaise.toPaise(cleanAmt),
                            category = cleanCategory,
                            subcategory = cleanSub,
                            paymentMethod = paymentMethod,
                            upiApp = upiApp,
                            note = cleanNote,
                            hashtags = hashtags,
                            warrantyDate = cleanWarranty,
                            allocationId = allocationId
                        )
                    }
                    repo.updateTransaction(updated)
                    if (allocationId.isNotBlank() && !type.equals("TRANSFER", true)) {
                        repo.getAccountById(accountId)?.let { newAcc ->
                            val adjustedAcc = NativeLedgerCalculator.adjustAllocationsForTransaction(
                                newAcc, allocationId, type, cleanAmt
                            )
                            if (adjustedAcc != newAcc) repo.updateAccount(adjustedAcc)
                        }
                    }
                }
                statusMessageFlow.value = "Updated transaction"
            } else {
                val tx = TransactionEntity(
                    accountId = accountId,
                    type = type.uppercase(),
                    amount = cleanAmt,
                    amountPaise = MoneyPaise.toPaise(cleanAmt),
                    category = cleanCategory,
                    subcategory = cleanSub,
                    paymentMethod = paymentMethod,
                    upiApp = upiApp,
                    note = cleanNote,
                    hashtags = hashtags,
                    warrantyDate = cleanWarranty,
                    allocationId = allocationId
                )
                repo.runInTransaction {
                    repo.insertTransaction(tx)
                    val acc = repo.getAccountById(accountId)
                    if (acc != null) {
                        if (type.equals("INCOME", true)) {
                            val splitAcc = NativeLedgerCalculator.applyAutoSplitToAllocations(acc, cleanAmt)
                            if (splitAcc != acc) repo.updateAccount(splitAcc)
                        } else if (allocationId.isNotBlank()) {
                            val adjAcc = NativeLedgerCalculator.adjustAllocationsForTransaction(acc, allocationId, type, cleanAmt)
                            if (adjAcc != acc) repo.updateAccount(adjAcc)
                        }
                    }
                    if (type.equals("EXPENSE", true)) applyRoundUpIfEnabled(repo, cleanAmt)
                }
                statusMessageFlow.value = "Saved ${type.lowercase()} (${settingsFlow.value.currencySymbol}${"%,.0f".format(cleanAmt)})"
            }
        }
    }

    private suspend fun applyRoundUpIfEnabled(repo: FinanceRepository, expenseAmount: Double) {
        if (!settingsFlow.value.roundUpEnabled) return
        val spare = NativeLedgerCalculator.computeRoundUpSpareChange(expenseAmount, 10)
        if (spare > 0.0) {
            val firstGoal = repo.goals.first().firstOrNull()
            if (firstGoal != null) {
                val cur = if (firstGoal.currentAmount > 0) firstGoal.currentAmount else MoneyPaise.toRupees(firstGoal.currentPaise)
                val next = MoneyPaise.roundRupees(cur + spare)
                repo.updateGoal(firstGoal.copy(currentAmount = next, currentPaise = MoneyPaise.toPaise(next)))
                repo.insertGoalContribution(GoalContributionEntity(goalId = firstGoal.id, amount = spare, amountPaise = MoneyPaise.toPaise(spare), note = "Round-Up Spare Change"))
            }
        }
    }

    fun repeatTransaction(tx: TransactionEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val repeated = tx.copy(
                id = 0,
                stableUid = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis()
            )
            repo.runInTransaction {
                repo.insertTransaction(repeated)
                val acc = repo.getAccountById(repeated.accountId)
                if (acc != null) {
                    if (repeated.type.equals("INCOME", true)) {
                        val splitAcc = NativeLedgerCalculator.applyAutoSplitToAllocations(acc, repeated.amount)
                        if (splitAcc != acc) repo.updateAccount(splitAcc)
                    } else if (repeated.allocationId.isNotBlank()) {
                        val adjAcc = NativeLedgerCalculator.adjustAllocationsForTransaction(acc, repeated.allocationId, repeated.type, repeated.amount)
                        if (adjAcc != acc) repo.updateAccount(adjAcc)
                    }
                }
                if (repeated.type.equals("EXPENSE", true)) {
                    applyRoundUpIfEnabled(repo, repeated.amount)
                }
            }
            statusMessageFlow.value = "Repeated transaction (${tx.category})"
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deletedChipFlow.value = null
            val tid = tx.transferId
            if (!tid.isNullOrBlank()) {
                val linked = repo.getTransactionsByTransferId(tid)
                if (linked.size > 1) {
                    deletedPairTxFlow.value = linked
                    deletedTxFlow.value = tx
                    repo.runInTransaction {
                        linked.forEach { repo.deleteTransaction(it) }
                    }
                    statusMessageFlow.value = "Deleted linked transfer pair"
                    return@launch
                }
            }
            deletedPairTxFlow.value = emptyList()
            deletedTxFlow.value = tx
            repo.runInTransaction {
                if (tx.allocationId.isNotBlank() && !tx.type.equals("TRANSFER", true)) {
                    repo.getAccountById(tx.accountId)?.let { acc ->
                        val reverseType = if (tx.type.equals("INCOME", true)) "EXPENSE" else "INCOME"
                        val reverted = NativeLedgerCalculator.adjustAllocationsForTransaction(
                            acc, tx.allocationId, reverseType, tx.amount
                        )
                        if (reverted != acc) repo.updateAccount(reverted)
                    }
                }
                repo.deleteTransaction(tx)
            }
            statusMessageFlow.value = "Deleted ${tx.category}"
        }
    }

    // Pending Refunds
    fun addPendingRefund(title: String, amount: Double, source: String = "Online Order") {
        val cleanTitle = TracmioSecurityEngine.sanitizeInput(title, 80)
        if (cleanTitle.isBlank() || !isValidPositiveAmount(amount)) {
            statusMessageFlow.value = "Enter a valid refund title and positive amount"
            return
        }
        val next = pendingRefundsFlow.value + PendingRefundItem(
            id = "ref_${System.currentTimeMillis()}",
            title = cleanTitle,
            source = TracmioSecurityEngine.sanitizeInput(source, 50).ifBlank { "Online Order" },
            amount = MoneyPaise.roundRupees(amount)
        )
        pendingRefundsFlow.value = next
        prefsStore.savePendingRefunds(next)
        statusMessageFlow.value = "Added pending refund: $cleanTitle"
    }

    fun markRefundReceived(ref: PendingRefundItem) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val acc = repo.accounts.first().firstOrNull()
            if (acc != null && isValidPositiveAmount(ref.amount)) {
                val amt = MoneyPaise.roundRupees(ref.amount)
                repo.runInTransaction {
                    repo.insertTransaction(
                        TransactionEntity(
                            accountId = acc.id,
                            type = "INCOME",
                            amount = amt,
                            amountPaise = MoneyPaise.toPaise(amt),
                            category = "Other",
                            paymentMethod = "Bank Transfer",
                            note = "📦 Refund received: ${ref.title}"
                        )
                    )
                }
            }
            deletePendingRefund(ref.id)
            statusMessageFlow.value = "Credited refund: ${ref.title}"
        }
    }

    fun deletePendingRefund(id: String) {
        val next = pendingRefundsFlow.value.filterNot { it.id == id }
        pendingRefundsFlow.value = next
        prefsStore.savePendingRefunds(next)
    }

    // People & Khata
    fun addPerson(name: String, upiId: String, openingBalance: Double) {
        val cleanName = TracmioSecurityEngine.sanitizeInput(name, 60)
        if (cleanName.isBlank() || !openingBalance.isFinite()) {
            statusMessageFlow.value = "Enter a valid person name and balance"
            return
        }
        val repo = requireRepo() ?: return
        val cleanBal = MoneyPaise.roundRupees(openingBalance)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertPerson(
                PersonEntity(
                    name = cleanName,
                    upiId = TracmioSecurityEngine.sanitizeInput(upiId, 80),
                    netBalance = cleanBal,
                    netBalancePaise = MoneyPaise.toPaise(cleanBal)
                )
            )
            statusMessageFlow.value = "Added $cleanName"
        }
    }

    fun settlePerson(person: PersonEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.updatePerson(person.copy(netBalance = 0.0, netBalancePaise = 0L))
            statusMessageFlow.value = "Settled balance with ${person.name}"
        }
    }

    fun addKhataEntry(shopOrFriend: String, itemDescription: String, amount: Double) {
        val cleanShop = TracmioSecurityEngine.sanitizeInput(shopOrFriend, 60)
        if (cleanShop.isBlank() || !isValidPositiveAmount(amount)) {
            statusMessageFlow.value = "Enter a valid shop/friend name and positive amount"
            return
        }
        val repo = requireRepo() ?: return
        val cleanAmt = MoneyPaise.roundRupees(amount)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertKhataEntry(
                KhataEntryEntity(
                    shopOrFriend = cleanShop,
                    itemDescription = TracmioSecurityEngine.sanitizeInput(itemDescription, 100),
                    amount = cleanAmt
                )
            )
            statusMessageFlow.value = "Added Khata entry for $cleanShop"
        }
    }

    fun settleKhataEntry(entry: KhataEntryEntity) {
        if (entry.isSettled) return
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val acc = repo.accounts.first().firstOrNull()
            repo.runInTransaction {
                repo.updateKhataEntry(entry.copy(isSettled = true))
                if (acc != null && isValidPositiveAmount(entry.amount)) {
                    val cleanAmt = MoneyPaise.roundRupees(entry.amount)
                    repo.insertTransaction(
                        TransactionEntity(
                            accountId = acc.id,
                            type = "EXPENSE",
                            amount = cleanAmt,
                            amountPaise = MoneyPaise.toPaise(cleanAmt),
                            category = "Khata & Kirana",
                            paymentMethod = "UPI",
                            note = "Cleared Khata: ${entry.shopOrFriend} (${entry.itemDescription})"
                        )
                    )
                }
            }
            statusMessageFlow.value = "Cleared Khata for ${entry.shopOrFriend}"
        }
    }

    fun deleteKhataEntry(entry: KhataEntryEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) { repo.deleteKhataEntry(entry) }
    }

    // Budgets, Goals, Loans, Wishlist, Cashback, PPU
    fun addBudget(category: String, limit: Double) {
        val cleanCategory = TracmioSecurityEngine.sanitizeInput(category, 60)
        if (cleanCategory.isBlank() || !isValidPositiveAmount(limit)) {
            statusMessageFlow.value = "Enter a valid category and positive monthly limit"
            return
        }
        val repo = requireRepo() ?: return
        val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        viewModelScope.launch(Dispatchers.IO) {
            repo.upsertBudget(cleanCategory, limit, currentMonthKey)
            statusMessageFlow.value = "Saved budget for $cleanCategory ($currentMonthKey)"
        }
    }

    fun addGoal(title: String, target: Double) {
        val cleanTitle = TracmioSecurityEngine.sanitizeInput(title, 60)
        if (cleanTitle.isBlank() || !isValidPositiveAmount(target)) {
            statusMessageFlow.value = "Enter a valid goal name and positive target"
            return
        }
        val repo = requireRepo() ?: return
        val cleanTarget = MoneyPaise.roundRupees(target)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertGoal(
                GoalEntity(
                    name = cleanTitle,
                    title = cleanTitle,
                    targetAmount = cleanTarget,
                    targetPaise = MoneyPaise.toPaise(cleanTarget)
                )
            )
            statusMessageFlow.value = "Created goal: $cleanTitle"
        }
    }

    fun contributeToGoal(goal: GoalEntity, amount: Double) {
        if (!isValidPositiveAmount(amount)) return
        val repo = requireRepo() ?: return
        val cleanAmt = MoneyPaise.roundRupees(amount)
        viewModelScope.launch(Dispatchers.IO) {
            repo.runInTransaction {
                val cur = if (goal.currentAmount > 0) goal.currentAmount else MoneyPaise.toRupees(goal.currentPaise)
                val next = MoneyPaise.roundRupees(cur + cleanAmt)
                repo.updateGoal(goal.copy(currentAmount = next, currentPaise = MoneyPaise.toPaise(next)))
                repo.insertGoalContribution(
                    GoalContributionEntity(
                        goalId = goal.id,
                        amount = cleanAmt,
                        amountPaise = MoneyPaise.toPaise(cleanAmt),
                        note = "Manual deposit"
                    )
                )
            }
            statusMessageFlow.value = "Added ${settingsFlow.value.currencySymbol}${cleanAmt.toInt()} to ${goal.title.ifBlank { goal.name }}"
        }
    }

    fun addLoan(name: String, principal: Double, apr: Double, monthlyEmi: Double) {
        val cleanName = TracmioSecurityEngine.sanitizeInput(name, 60)
        if (cleanName.isBlank() || !isValidPositiveAmount(principal) || !isValidNonNegativeAmount(apr) || !isValidPositiveAmount(monthlyEmi)) {
            statusMessageFlow.value = "Enter valid loan name, principal, APR, and EMI"
            return
        }
        val repo = requireRepo() ?: return
        val cleanPrincipal = MoneyPaise.roundRupees(principal)
        val cleanEmi = MoneyPaise.roundRupees(monthlyEmi)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertLoan(
                LoanEntity(
                    name = cleanName,
                    lender = cleanName,
                    principal = cleanPrincipal,
                    principalPaise = MoneyPaise.toPaise(cleanPrincipal),
                    interestRate = apr,
                    monthlyPayment = cleanEmi,
                    emiPaise = MoneyPaise.toPaise(cleanEmi)
                )
            )
            statusMessageFlow.value = "Added loan: $cleanName"
        }
    }

    fun recordLoanRepayment(loan: LoanEntity, amount: Double) {
        if (!isValidPositiveAmount(amount)) return
        val repo = requireRepo() ?: return
        val cleanAmt = MoneyPaise.roundRupees(amount)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertLoanPayment(
                LoanPaymentEntity(
                    loanId = loan.id,
                    amount = cleanAmt,
                    amountPaise = MoneyPaise.toPaise(cleanAmt)
                )
            )
            statusMessageFlow.value = "Recorded loan payment for ${loan.name.ifBlank { loan.lender }}"
        }
    }

    fun addWishlistCooldown(item: String, price: Double) {
        val cleanItem = TracmioSecurityEngine.sanitizeInput(item, 80)
        if (cleanItem.isBlank() || !isValidPositiveAmount(price)) {
            statusMessageFlow.value = "Enter a valid wishlist item and price"
            return
        }
        val repo = requireRepo() ?: return
        val cleanPrice = MoneyPaise.roundRupees(price)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertWishlistCooldown(WishlistCooldownEntity(itemName = cleanItem, price = cleanPrice))
            statusMessageFlow.value = "Started 24h cooldown for $cleanItem"
        }
    }

    fun skipWishlistAndSave(item: WishlistCooldownEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateWishlistCooldown(item.copy(status = "SKIPPED_SAVED"))
            statusMessageFlow.value = "Skipped ${item.itemName} and saved ${settingsFlow.value.currencySymbol}${item.price.toInt()}!"
        }
    }

    fun addCashbackReward(app: String, amount: Double) {
        val cleanApp = TracmioSecurityEngine.sanitizeInput(app, 60)
        if (cleanApp.isBlank() || !isValidPositiveAmount(amount)) {
            statusMessageFlow.value = "Enter a valid source app and cashback amount"
            return
        }
        val repo = requireRepo() ?: return
        val cleanAmt = MoneyPaise.roundRupees(amount)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertCashbackReward(CashbackRewardEntity(sourceApp = cleanApp, amount = cleanAmt))
            statusMessageFlow.value = "Logged ${settingsFlow.value.currencySymbol}${cleanAmt.toInt()} cashback from $cleanApp"
        }
    }

    fun addPricePerUseItem(name: String, price: Double) {
        val cleanName = TracmioSecurityEngine.sanitizeInput(name, 80)
        if (cleanName.isBlank() || !isValidPositiveAmount(price)) {
            statusMessageFlow.value = "Enter a valid item name and purchase price"
            return
        }
        val repo = requireRepo() ?: return
        val cleanPrice = MoneyPaise.roundRupees(price)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertPricePerUseItem(PricePerUseEntity(itemName = cleanName, purchasePrice = cleanPrice))
            statusMessageFlow.value = "Tracking value for $cleanName"
        }
    }

    fun incrementItemUse(item: PricePerUseEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.updatePricePerUseItem(item.copy(usageCount = item.usageCount + 1))
        }
    }

    // Subscriptions, Academic, Reminders, Trackers
    fun addSubscription(name: String, amount: Double, cycle: String, isSim: Boolean = false, validityDays: Int = 28) {
        val cleanName = TracmioSecurityEngine.sanitizeInput(name, 80)
        if (cleanName.isBlank() || !isValidPositiveAmount(amount) || validityDays <= 0) {
            statusMessageFlow.value = "Enter a valid subscription name, amount, and validity days"
            return
        }
        val repo = requireRepo() ?: return
        val cleanAmt = MoneyPaise.roundRupees(amount)
        viewModelScope.launch(Dispatchers.IO) {
            val due = System.currentTimeMillis() + (validityDays.toLong() * 86_400_000L)
            repo.insertSubscription(
                SubscriptionEntity(
                    name = cleanName,
                    amount = cleanAmt,
                    amountPaise = MoneyPaise.toPaise(cleanAmt),
                    billingCycle = cycle,
                    nextDueDateMillis = due,
                    isSimOrWifi = isSim,
                    validityDays = validityDays
                )
            )
            statusMessageFlow.value = "Added $cleanName"
        }
    }

    fun deleteSubscription(sub: SubscriptionEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) { repo.deleteSubscription(sub) }
    }

    fun addAcademicEvent(title: String, fee: Double, dueDate: String, semester: String) {
        val cleanTitle = TracmioSecurityEngine.sanitizeInput(title, 80)
        if (cleanTitle.isBlank() || !isValidNonNegativeAmount(fee)) {
            statusMessageFlow.value = "Enter a valid academic event title and fee"
            return
        }
        val repo = requireRepo() ?: return
        val cleanFee = MoneyPaise.roundRupees(fee)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertAcademicEvent(
                AcademicEventEntity(
                    title = cleanTitle,
                    feeAmount = cleanFee,
                    dueDate = TracmioSecurityEngine.sanitizeInput(dueDate, 30),
                    semester = TracmioSecurityEngine.sanitizeInput(semester, 30)
                )
            )
            statusMessageFlow.value = "Added academic event: $cleanTitle"
        }
    }

    fun markAcademicEventPaid(event: AcademicEventEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateAcademicEvent(event.copy(isPaid = true, paidAmount = event.feeAmount))
            statusMessageFlow.value = "Marked ${event.title} paid"
        }
    }

    fun addReminder(title: String, amount: Double, dueDate: String) {
        val cleanTitle = TracmioSecurityEngine.sanitizeInput(title, 80)
        if (cleanTitle.isBlank() || !isValidNonNegativeAmount(amount)) {
            statusMessageFlow.value = "Enter a valid reminder title and amount"
            return
        }
        val repo = requireRepo() ?: return
        val cleanAmt = MoneyPaise.roundRupees(amount)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertReminder(
                ReminderItemEntity(
                    title = cleanTitle,
                    amount = cleanAmt,
                    dueDate = TracmioSecurityEngine.sanitizeInput(dueDate, 30)
                )
            )
            statusMessageFlow.value = "Added reminder: $cleanTitle"
        }
    }

    fun toggleReminderCompleted(rem: ReminderItemEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateReminder(rem.copy(isCompleted = !rem.isCompleted))
        }
    }

    fun deleteReminder(rem: ReminderItemEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) { repo.deleteReminder(rem) }
    }

    fun setDailyReminderEnabled(enabled: Boolean) {
        dailyReminderEnabledFlow.value = enabled
        prefsStore.saveDailyReminderConfig(enabled, 21, 0)
    }

    fun setMonthlyBackupEnabled(enabled: Boolean) {
        monthlyBackupEnabledFlow.value = enabled
        prefsStore.saveMonthlyBackupEnabled(enabled)
    }

    fun addCustomTracker(title: String, type: String, target: Double) {
        val cleanTitle = TracmioSecurityEngine.sanitizeInput(title, 60)
        if (cleanTitle.isBlank() || !isValidPositiveAmount(target)) {
            statusMessageFlow.value = "Enter a valid tracker title and positive target"
            return
        }
        val repo = requireRepo() ?: return
        val cleanTarget = MoneyPaise.roundRupees(target)
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertCustomTracker(
                CustomTrackerEntity(
                    title = cleanTitle,
                    trackerType = type,
                    targetValue = cleanTarget,
                    unit = if (type == "SAVINGS") settingsFlow.value.currencySymbol else "pts"
                )
            )
            statusMessageFlow.value = "Started tracker: $cleanTitle"
        }
    }

    fun incrementCustomTracker(tracker: CustomTrackerEntity, delta: Double) {
        if (!delta.isFinite() || delta == 0.0) return
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val entry = CustomTrackerEntryEntity(trackerId = tracker.id, deltaValue = delta)
            repo.runInTransaction {
                repo.insertTrackerEntry(entry)
                repo.updateCustomTracker(CustomTrackersCalculator.applyEntryDelta(tracker, entry))
            }
        }
    }

    fun deleteCustomTracker(tracker: CustomTrackerEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) { repo.deleteCustomTracker(tracker) }
    }

    fun addTrackerFolder(folderName: String, icon: String) {
        val cleanName = TracmioSecurityEngine.sanitizeInput(folderName, 40)
        if (cleanName.isBlank()) return
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertTrackerFolder(TrackerFolderEntity(folderName = cleanName, icon = icon))
        }
    }

    fun deleteTrackerFolder(folder: TrackerFolderEntity) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) { repo.deleteTrackerFolder(folder) }
    }

    // App Lock & Security Audit
    fun refreshAppLockState() {
        appLockStateFlow.value = appLockManager.getState()
    }

    fun enableAppLock(pin: String, biometric: Boolean, question: String, answer: String): String {
        return runCatching {
            val recoveryKey = appLockManager.enableLock(pin, biometric, question, answer)
            appLockStateFlow.value = appLockManager.getState()
            statusMessageFlow.value = "App Lock enabled. Store your recovery key safely."
            recoveryKey
        }.getOrElse { err ->
            appLockStateFlow.value = appLockManager.getState()
            statusMessageFlow.value = err.localizedMessage ?: "Unable to enable App Lock"
            ""
        }
    }

    fun verifyCurrentPinForSensitiveAction(pin: String): Boolean {
        val ok = appLockManager.verifyPin(pin)
        appLockStateFlow.value = appLockManager.getState()
        return ok
    }

    fun disableAppLock() {
        runCatching {
            appLockManager.disableLock()
            appLockStateFlow.value = appLockManager.getState()
            isAppLockedFlow.value = false
            statusMessageFlow.value = "App Lock disabled"
        }.onFailure { err ->
            appLockStateFlow.value = appLockManager.getState()
            statusMessageFlow.value = err.localizedMessage ?: "Failed to disable App Lock"
        }
    }

    fun unlockWithPin(pin: String): Boolean {
        val ok = appLockManager.verifyPin(pin)
        appLockStateFlow.value = appLockManager.getState()
        if (ok) isAppLockedFlow.value = false
        return ok
    }

    fun unlockWithBiometricSuccess() {
        appLockManager.resetLockoutOnBiometricSuccess()
        appLockStateFlow.value = appLockManager.getState()
        isAppLockedFlow.value = false
    }

    fun recoverPinWithSecret(secret: String, newPin: String?): Boolean {
        return runCatching {
            val ok = appLockManager.verifyRecoveryAndReset(secret, newPin)
            appLockStateFlow.value = appLockManager.getState()
            if (ok) {
                isAppLockedFlow.value = false
                statusMessageFlow.value = if (newPin.isNullOrBlank()) "App Lock reset & unlocked" else "PIN updated & unlocked"
            }
            ok
        }.getOrElse { err ->
            statusMessageFlow.value = err.localizedMessage ?: "Recovery failed"
            false
        }
    }

    fun runDatabaseHealthReport(): TracmioSecurityEngine.DatabaseHealthReport {
        val repo = repositoryFlow.value
        if (repo == null) {
            return TracmioSecurityEngine.DatabaseHealthReport(
                isHealthy = false,
                pragmaIntegrity = "vault_locked",
                foreignKeyErrors = 0,
                accountCount = 0,
                transactionCount = 0,
                trackerCount = 0,
                orphanedTransactions = 0,
                diagnosticSummary = "Encrypted vault is unavailable or locked."
            )
        }
        val st = uiState.value
        return repo.runDatabaseHealthCheck(st.accounts.size, st.transactions.size, st.customTrackers.size)
    }

    fun repairDatabaseOrphans(onComplete: (TracmioSecurityEngine.DatabaseHealthReport) -> Unit) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val repaired = runCatching { repo.repairOrphanRecords() }.getOrDefault(0)
            val st = uiState.value
            val refreshed = repo.runDatabaseHealthCheck(st.accounts.size, st.transactions.size, st.customTrackers.size)
            statusMessageFlow.value = if (repaired > 0) {
                "Repaired $repaired orphan/UID records"
            } else {
                "No orphan records needed repair"
            }
            withContext(Dispatchers.Main) {
                onComplete(refreshed)
            }
        }
    }

    fun buildBackupJson(): String {
        return NativeBackupService.buildFullBackupJson(
            state = uiState.value,
            settings = settingsFlow.value,
            appFilesDir = appContext.filesDir,
            categories = categoriesFlow.value
        )
    }

    fun prepareBackupPreview(rawText: String, password: CharArray? = null) {
        val passCopy = password?.copyOf()
        val repo = requireRepo()
        if (repo == null) {
            passCopy?.fill('\u0000')
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isEncrypted = rawText.contains("\"cipherText\"")
                if (isEncrypted && (passCopy == null || passCopy.isEmpty())) {
                    statusMessageFlow.value = "This backup is encrypted. Use 'Import Encrypted' and enter your password."
                    return@launch
                }
                val plain = runCatching {
                    if (isEncrypted && passCopy != null) {
                        TracmioSecurityEngine.decryptBackup(rawText, passCopy)
                    } else {
                        rawText
                    }
                }.getOrElse {
                    statusMessageFlow.value = "Backup decrypt failed: wrong password or corrupted envelope"
                    return@launch
                }

                val preview = runCatching { repo.inspectBackupPayload(plain) }.getOrElse { err ->
                    statusMessageFlow.value = err.localizedMessage ?: "Invalid backup JSON format"
                    return@launch
                }
                pendingBackupPreviewFlow.value = PendingBackupPreview(plainJson = plain, report = preview)
            } finally {
                passCopy?.fill('\u0000')
            }
        }
    }

    fun dismissBackupPreview() {
        pendingBackupPreviewFlow.value = null
    }

    fun confirmBackupRestore(mode: FinanceRepository.BackupRestoreMode) {
        val pending = pendingBackupPreviewFlow.value ?: return
        val repo = requireRepo() ?: return
        pendingBackupPreviewFlow.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val report = runCatching {
                repo.restoreBackupJson(
                    jsonText = pending.plainJson,
                    mode = mode,
                    appFilesDir = appContext.filesDir
                )
            }.getOrElse { err ->
                statusMessageFlow.value = "Restore failed: ${err.localizedMessage ?: "invalid payload"}"
                return@launch
            }
            applyPreferencesFromBackupJson(pending.plainJson, report.restoredAttachmentMap)
            lastRestoreReportFlow.value = report
            statusMessageFlow.value = report.details
        }
    }

    fun importBackupJsonText(rawText: String, password: CharArray? = null) {
        val passCopy = password?.copyOf()
        val repo = requireRepo()
        if (repo == null) {
            passCopy?.fill('\u0000')
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val plain = runCatching {
                    if (passCopy != null && rawText.contains("\"cipherText\"")) {
                        TracmioSecurityEngine.decryptBackup(rawText, passCopy)
                    } else {
                        rawText
                    }
                }.getOrElse {
                    statusMessageFlow.value = "Import failed: wrong password or corrupted backup"
                    return@launch
                }
                val report = runCatching {
                    repo.restoreBackupJson(
                        jsonText = plain,
                        mode = FinanceRepository.BackupRestoreMode.SAFE_MERGE,
                        appFilesDir = appContext.filesDir
                    )
                }.getOrElse { err ->
                    statusMessageFlow.value = "Import failed: ${err.localizedMessage ?: "invalid backup"}"
                    return@launch
                }
                applyPreferencesFromBackupJson(plain, report.restoredAttachmentMap)
                lastRestoreReportFlow.value = report
                statusMessageFlow.value = report.details
            } finally {
                passCopy?.fill('\u0000')
            }
        }
    }

    fun importLegacyJsonText(rawText: String) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val report = runCatching {
                LegacyMigrationGuard.importLegacyJsonSnapshotToRepo(rawText, repo)
            }.getOrElse { err ->
                statusMessageFlow.value = "Legacy JSON import failed: ${err.localizedMessage}"
                return@launch
            }
            lastRestoreReportFlow.value = report
            statusMessageFlow.value = "Legacy JSON imported: ${report.details}"
        }
    }

    fun importLegacyDatabaseUri(uri: Uri) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val report = runCatching {
                LegacyMigrationGuard.importLegacySqliteDbUri(appContext, uri, repo)
            }.getOrElse { err ->
                statusMessageFlow.value = "Legacy DB import failed: ${err.localizedMessage}"
                return@launch
            }
            lastRestoreReportFlow.value = report
            statusMessageFlow.value = "Legacy DB imported: ${report.details}"
        }
    }

    private fun applyPreferencesFromBackupJson(plain: String, attachmentMap: Map<String, String>) {
        runCatching {
            val obj = JSONObject(plain)
            obj.optJSONObject("appPreferences")?.let { p ->
                val rawWallpaperPath = if (p.has("customWallpaperPath") && !p.isNull("customWallpaperPath")) {
                    p.optString("customWallpaperPath", "").takeIf { it.isNotBlank() }
                } else null
                val mappedWallpaper = rawWallpaperPath?.let { attachmentMap[it] ?: it }
                val rootCanon = appContext.filesDir.canonicalPath
                val validatedWallpaper = mappedWallpaper?.let { candidate ->
                    runCatching {
                        val f = File(candidate)
                        val canon = f.canonicalPath
                        if (f.exists() && f.isFile && canon.startsWith(rootCanon + File.separator)) canon else null
                    }.getOrNull()
                }

                prefsStore.updateSettings { cur ->
                    cur.copy(
                        userName = TracmioSecurityEngine.sanitizeInput(p.optString("userName", cur.userName), 40).ifBlank { cur.userName },
                        currencySymbol = TracmioSecurityEngine.sanitizeInput(p.optString("currencySymbol", cur.currencySymbol), 6).ifBlank { cur.currencySymbol },
                        themePreset = p.optString("themePreset", cur.themePreset),
                        glassUiStyle = p.optString("glassUiStyle", cur.glassUiStyle),
                        wallpaperPreset = p.optString("wallpaperPreset", cur.wallpaperPreset),
                        backgroundVisibility = p.optDouble("backgroundVisibility", cur.backgroundVisibility.toDouble()).toFloat().coerceIn(0.05f, 1.0f),
                        wallpaperBlurDp = p.optDouble("wallpaperBlurDp", cur.wallpaperBlurDp.toDouble()).toFloat().coerceIn(0f, 30f),
                        glowIntensity = p.optDouble("glowIntensity", cur.glowIntensity.toDouble()).toFloat().coerceIn(0.1f, 1.2f),
                        uiScale = p.optDouble("uiScale", cur.uiScale.toDouble()).toFloat().coerceIn(0.8f, 1.3f),
                        deviceMotionEnabled = p.optBoolean("deviceMotionEnabled", cur.deviceMotionEnabled),
                        animationStyle = p.optInt("animationStyle", cur.animationStyle),
                        customPrimaryHex = TracmioSecurityEngine.sanitizeInput(p.optString("customPrimaryHex", cur.customPrimaryHex), 16).ifBlank { cur.customPrimaryHex },
                        customSecondaryHex = TracmioSecurityEngine.sanitizeInput(p.optString("customSecondaryHex", cur.customSecondaryHex), 16).ifBlank { cur.customSecondaryHex },
                        customWallpaperPath = validatedWallpaper ?: cur.customWallpaperPath,
                        roundUpEnabled = p.optBoolean("roundUpEnabled", cur.roundUpEnabled)
                    )
                }
                if (p.has("dailyReminderEnabled")) {
                    setDailyReminderEnabled(p.optBoolean("dailyReminderEnabled", false))
                }
                if (p.has("monthlyBackupEnabled")) {
                    setMonthlyBackupEnabled(p.optBoolean("monthlyBackupEnabled", false))
                }
            }
            obj.optJSONArray("quickSpendChips")?.let { arr ->
                val chips = (0 until arr.length()).map { idx ->
                    val c = arr.getJSONObject(idx)
                    QuickSpendChip(
                        id = c.optString("id", "q_$idx"),
                        emoji = c.optString("emoji", "⚡"),
                        title = TracmioSecurityEngine.sanitizeInput(c.optString("title", "Spend"), 30),
                        amount = c.optDouble("amount", 0.0),
                        category = TracmioSecurityEngine.sanitizeInput(c.optString("category", "Other"), 40)
                    )
                }.filter { it.amount.isFinite() && it.amount > 0.0 }
                if (chips.isNotEmpty()) {
                    quickChipsFlow.value = chips
                    prefsStore.saveQuickSpendChips(chips)
                }
            }
            obj.optJSONArray("pendingRefunds")?.let { arr ->
                val refs = (0 until arr.length()).map { idx ->
                    val r = arr.getJSONObject(idx)
                    PendingRefundItem(
                        id = r.optString("id", "ref_$idx"),
                        title = TracmioSecurityEngine.sanitizeInput(r.optString("title", "Refund"), 80),
                        source = TracmioSecurityEngine.sanitizeInput(r.optString("source", "Store / App"), 50),
                        amount = r.optDouble("amount", 0.0),
                        expectedDate = TracmioSecurityEngine.sanitizeInput(r.optString("expectedDate", "Soon"), 30),
                        isReceived = r.optBoolean("isReceived", false)
                    )
                }.filter { it.amount.isFinite() && it.amount > 0.0 }
                pendingRefundsFlow.value = refs
                prefsStore.savePendingRefunds(refs)
            }
            obj.optJSONArray("pinnedToolIds")?.let { arr ->
                val pins = (0 until arr.length()).map { arr.getString(it) }
                pinnedToolIdsFlow.value = pins
                prefsStore.savePinnedToolIds(pins)
            }
        }
    }

    fun importCsvText(csvText: String) {
        val repo = requireRepo() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val defaultAccId = repo.accounts.first().firstOrNull()?.id ?: 1L
            val parsed = CsvParser.parseCsvTransactions(csvText, defaultAccId)
            repo.runInTransaction {
                parsed.forEach { repo.insertTransaction(it) }
            }
            statusMessageFlow.value = "Imported ${parsed.size} CSV transactions"
        }
    }

    private data class CoreLedgerSnapshot(
        val accounts: List<AccountEntity>,
        val transactions: List<TransactionEntity>,
        val budgets: List<BudgetEntity>,
        val goals: List<GoalEntity>,
        val loans: List<LoanEntity>
    )

    private data class PlanningExtrasSnapshot(
        val goalContributions: List<GoalContributionEntity>,
        val loanPayments: List<LoanPaymentEntity>,
        val persons: List<PersonEntity>,
        val customTrackers: List<CustomTrackerEntity>,
        val trackerFolders: List<TrackerFolderEntity>
    )

    private data class ToolsEntitiesSnapshot(
        val subscriptions: List<SubscriptionEntity>,
        val academicEvents: List<AcademicEventEntity>,
        val reminders: List<ReminderItemEntity>,
        val khataEntries: List<KhataEntryEntity>,
        val wishlistCooldowns: List<WishlistCooldownEntity>
    )

    private data class RewardAndTrackSnapshot(
        val cashbackRewards: List<CashbackRewardEntity>,
        val pricePerUseItems: List<PricePerUseEntity>,
        val trackerEntries: List<CustomTrackerEntryEntity>,
        val quickSpendChips: List<QuickSpendChip>,
        val pendingRefunds: List<PendingRefundItem>
    )

    private data class UiFlagsSnapshot(
        val statusMessage: String?,
        val deletedTx: TransactionEntity?,
        val deletedChip: QuickSpendChip?,
        val isAppLocked: Boolean,
        val appLockState: AppLockState
    )

    private data class AuxFlagsSnapshot(
        val vaultInitState: TracmioSecurityEngine.VaultInitState,
        val pinnedToolIds: List<String>,
        val pendingNotificationAction: String?,
        val dailyReminderEnabled: Boolean,
        val monthlyBackupEnabled: Boolean
    )
}
