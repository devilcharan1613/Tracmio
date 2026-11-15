package com.tracmio.app.data.repository

import android.util.Base64
import androidx.room.withTransaction
import com.tracmio.app.data.MoneyPaise
import com.tracmio.app.data.local.*
import com.tracmio.app.data.security.TracmioSecurityEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class FinanceRepository(private val db: TracmioDatabase) {
    private val accountDao = db.accountDao()
    private val financeDao = db.financeDao()
    private val customTrackerDao = db.customTrackerDao()
    private val featureDao = db.featureDao()

    val accounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val allAccountsIncludingArchived: Flow<List<AccountEntity>> = accountDao.getAllAccountsIncludingArchived()
    val transactions: Flow<List<TransactionEntity>> = financeDao.getAllTransactions()
    val budgets: Flow<List<BudgetEntity>> = financeDao.getAllBudgets()
    val goals: Flow<List<GoalEntity>> = financeDao.getAllGoals()
    val goalContributions: Flow<List<GoalContributionEntity>> = financeDao.getAllGoalContributions()
    val loans: Flow<List<LoanEntity>> = financeDao.getAllLoans()
    val loanPayments: Flow<List<LoanPaymentEntity>> = financeDao.getAllLoanPayments()
    val persons: Flow<List<PersonEntity>> = financeDao.getAllPersons()
    val categories: Flow<List<CategoryEntity>> = financeDao.getAllCategories()
    val customTrackers: Flow<List<CustomTrackerEntity>> = customTrackerDao.getAllTrackers()
    val trackerEntries: Flow<List<CustomTrackerEntryEntity>> = customTrackerDao.getAllEntries()
    val trackerFolders: Flow<List<TrackerFolderEntity>> = customTrackerDao.getAllFolders()
    val subscriptions: Flow<List<SubscriptionEntity>> = featureDao.getAllSubscriptions()
    val academicEvents: Flow<List<AcademicEventEntity>> = featureDao.getAllAcademicEvents()
    val reminders: Flow<List<ReminderItemEntity>> = featureDao.getAllReminders()
    val khataEntries: Flow<List<KhataEntryEntity>> = featureDao.getAllKhataEntries()
    val wishlistCooldowns: Flow<List<WishlistCooldownEntity>> = featureDao.getAllWishlistCooldowns()
    val cashbackRewards: Flow<List<CashbackRewardEntity>> = featureDao.getAllCashbackRewards()
    val pricePerUseItems: Flow<List<PricePerUseEntity>> = featureDao.getAllPricePerUseItems()

    enum class BackupRestoreMode {
        SAFE_MERGE,
        CLEAN_REPLACE
    }

    data class BackupPreviewReport(
        val formatVersion: Int,
        val appName: String,
        val accountCount: Int,
        val transactionCount: Int,
        val duplicateTransactionCount: Int,
        val budgetCount: Int,
        val goalCount: Int,
        val loanCount: Int,
        val personCount: Int,
        val trackerCount: Int,
        val attachmentCount: Int,
        val otherEntityCount: Int,
        val hasPreferences: Boolean
    )

    data class RestoreReport(
        val mode: BackupRestoreMode,
        val insertedCount: Int,
        val skippedDuplicatesCount: Int,
        val rejectedCount: Int,
        val attachmentsRestoredCount: Int,
        val details: String,
        val restoredAttachmentMap: Map<String, String> = emptyMap()
    )

    suspend fun <T> runInTransaction(block: suspend () -> T): T = db.withTransaction(block)

    suspend fun getAccountById(id: Long): AccountEntity? = accountDao.getAccountById(id)
    suspend fun insertAccount(account: AccountEntity): Long = accountDao.insertAccount(account)
    suspend fun updateAccount(account: AccountEntity) = accountDao.updateAccount(account)
    suspend fun deleteAccount(account: AccountEntity) {
        db.withTransaction {
            financeDao.clearTransferAccountReferences(account.id)
            financeDao.deleteTransactionsForAccount(account.id)
            accountDao.deleteAccount(account)
        }
    }

    suspend fun getTransactionById(id: Long): TransactionEntity? = financeDao.getTransactionById(id)
    suspend fun getTransactionsByTransferId(transferId: String): List<TransactionEntity> = financeDao.getTransactionsByTransferId(transferId)
    suspend fun insertTransaction(tx: TransactionEntity): Long = financeDao.insertTransaction(tx)
    suspend fun updateTransaction(tx: TransactionEntity) = financeDao.updateTransaction(tx)
    suspend fun deleteTransaction(tx: TransactionEntity) = financeDao.deleteTransaction(tx)

    suspend fun insertBudget(budget: BudgetEntity): Long = financeDao.insertBudget(budget)
    suspend fun upsertBudget(category: String, monthlyLimit: Double, month: String): Long {
        val cleanCategory = TracmioSecurityEngine.sanitizeInput(category, 60)
        val cleanMonth = TracmioSecurityEngine.sanitizeInput(month, 20)
        require(cleanCategory.isNotBlank()) { "Budget category cannot be blank" }
        require(monthlyLimit.isFinite() && monthlyLimit > 0.0) { "Budget limit must be positive" }
        val limitPaise = MoneyPaise.toPaise(monthlyLimit)
        val roundedLimit = MoneyPaise.toRupees(limitPaise)
        return db.withTransaction {
            val existing = financeDao.findBudgetByCategoryAndMonth(cleanCategory, cleanMonth)
            if (existing != null) {
                financeDao.updateBudget(
                    existing.copy(
                        category = cleanCategory,
                        monthlyLimit = roundedLimit,
                        limitPaise = limitPaise,
                        month = cleanMonth
                    )
                )
                existing.id
            } else {
                financeDao.insertBudget(
                    BudgetEntity(
                        category = cleanCategory,
                        monthlyLimit = roundedLimit,
                        limitPaise = limitPaise,
                        month = cleanMonth
                    )
                )
            }
        }
    }
    suspend fun deleteBudget(budget: BudgetEntity) = financeDao.deleteBudget(budget)

    suspend fun insertGoal(goal: GoalEntity): Long = financeDao.insertGoal(goal)
    suspend fun updateGoal(goal: GoalEntity) = financeDao.updateGoal(goal)
    suspend fun deleteGoal(goal: GoalEntity) {
        db.withTransaction {
            financeDao.deleteContributionsForGoal(goal.id)
            financeDao.deleteGoal(goal)
        }
    }
    suspend fun insertGoalContribution(contrib: GoalContributionEntity): Long = financeDao.insertGoalContribution(contrib)

    suspend fun insertLoan(loan: LoanEntity): Long = financeDao.insertLoan(loan)
    suspend fun updateLoan(loan: LoanEntity) = financeDao.updateLoan(loan)
    suspend fun deleteLoan(loan: LoanEntity) {
        db.withTransaction {
            financeDao.deletePaymentsForLoan(loan.id)
            financeDao.deleteLoan(loan)
        }
    }
    suspend fun insertLoanPayment(payment: LoanPaymentEntity): Long = financeDao.insertLoanPayment(payment)

    suspend fun insertPerson(person: PersonEntity): Long = financeDao.insertPerson(person)
    suspend fun updatePerson(person: PersonEntity) = financeDao.updatePerson(person)
    suspend fun deletePerson(person: PersonEntity) {
        db.withTransaction {
            financeDao.clearPersonReferences(person.id)
            financeDao.deletePerson(person)
        }
    }

    suspend fun insertCategory(category: CategoryEntity): Long = financeDao.insertCategory(category)

    suspend fun insertCustomTracker(tracker: CustomTrackerEntity): Long = customTrackerDao.insertTracker(tracker)
    suspend fun updateCustomTracker(tracker: CustomTrackerEntity) = customTrackerDao.updateTracker(tracker)
    suspend fun deleteCustomTracker(tracker: CustomTrackerEntity) {
        db.withTransaction {
            customTrackerDao.deleteEntriesForTracker(tracker.id)
            customTrackerDao.deleteTracker(tracker)
        }
    }
    suspend fun insertTrackerEntry(entry: CustomTrackerEntryEntity): Long = customTrackerDao.insertEntry(entry)
    suspend fun insertTrackerFolder(folder: TrackerFolderEntity): Long = customTrackerDao.insertFolder(folder)
    suspend fun deleteTrackerFolder(folder: TrackerFolderEntity) {
        db.withTransaction {
            customTrackerDao.clearFolderReferences(folder.id)
            customTrackerDao.deleteFolder(folder)
        }
    }

    suspend fun insertSubscription(item: SubscriptionEntity): Long = featureDao.insertSubscription(item)
    suspend fun updateSubscription(item: SubscriptionEntity) = featureDao.updateSubscription(item)
    suspend fun deleteSubscription(item: SubscriptionEntity) = featureDao.deleteSubscription(item)

    suspend fun insertAcademicEvent(item: AcademicEventEntity): Long = featureDao.insertAcademicEvent(item)
    suspend fun updateAcademicEvent(item: AcademicEventEntity) = featureDao.updateAcademicEvent(item)
    suspend fun deleteAcademicEvent(item: AcademicEventEntity) = featureDao.deleteAcademicEvent(item)

    suspend fun insertReminder(item: ReminderItemEntity): Long = featureDao.insertReminder(item)
    suspend fun updateReminder(item: ReminderItemEntity) = featureDao.updateReminder(item)
    suspend fun deleteReminder(item: ReminderItemEntity) = featureDao.deleteReminder(item)

    suspend fun insertKhataEntry(item: KhataEntryEntity): Long = featureDao.insertKhataEntry(item)
    suspend fun updateKhataEntry(item: KhataEntryEntity) = featureDao.updateKhataEntry(item)
    suspend fun deleteKhataEntry(item: KhataEntryEntity) = featureDao.deleteKhataEntry(item)

    suspend fun insertWishlistCooldown(item: WishlistCooldownEntity): Long = featureDao.insertWishlistCooldown(item)
    suspend fun updateWishlistCooldown(item: WishlistCooldownEntity) = featureDao.updateWishlistCooldown(item)
    suspend fun deleteWishlistCooldown(item: WishlistCooldownEntity) = featureDao.deleteWishlistCooldown(item)

    suspend fun insertCashbackReward(item: CashbackRewardEntity): Long = featureDao.insertCashbackReward(item)
    suspend fun deleteCashbackReward(item: CashbackRewardEntity) = featureDao.deleteCashbackReward(item)

    suspend fun insertPricePerUseItem(item: PricePerUseEntity): Long = featureDao.insertPricePerUseItem(item)
    suspend fun updatePricePerUseItem(item: PricePerUseEntity) = featureDao.updatePricePerUseItem(item)
    suspend fun deletePricePerUseItem(item: PricePerUseEntity) = featureDao.deletePricePerUseItem(item)

    fun runDatabaseHealthCheck(
        accountCount: Int,
        transactionCount: Int,
        trackerCount: Int
    ): TracmioSecurityEngine.DatabaseHealthReport {
        val sqlite = db.openHelper.readableDatabase
        val integrity = sqlite.query("PRAGMA integrity_check").use { c ->
            if (c.moveToFirst()) c.getString(0) else "unknown"
        }

        var fkErrors = 0
        sqlite.query("PRAGMA foreign_key_check").use { c ->
            while (c.moveToNext()) fkErrors++
        }

        fun scalarInt(sql: String): Int = sqlite.query(sql).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

        val liveAccountCount = scalarInt("SELECT COUNT(*) FROM accounts")
        val liveTxCount = scalarInt("SELECT COUNT(*) FROM transactions")
        val liveTrackerCount = scalarInt("SELECT COUNT(*) FROM custom_trackers")

        val orphanedTransactions = scalarInt(
            "SELECT COUNT(*) FROM transactions WHERE accountId NOT IN (SELECT id FROM accounts) OR (transferAccountId IS NOT NULL AND transferAccountId NOT IN (SELECT id FROM accounts))"
        )
        val orphanGoalContribs = scalarInt(
            "SELECT COUNT(*) FROM goal_contributions WHERE goalId NOT IN (SELECT id FROM goals)"
        )
        val orphanLoanPayments = scalarInt(
            "SELECT COUNT(*) FROM loan_payments WHERE loanId NOT IN (SELECT id FROM loans)"
        )
        val orphanTrackerEntries = scalarInt(
            "SELECT COUNT(*) FROM tracker_entries WHERE trackerId NOT IN (SELECT id FROM custom_trackers)"
        )
        val orphanDependents = orphanGoalContribs + orphanLoanPayments + orphanTrackerEntries

        val duplicateUidCount = scalarInt(
            "SELECT COALESCE(SUM(cnt - 1), 0) FROM (SELECT COUNT(*) AS cnt FROM transactions WHERE stableUid != '' GROUP BY stableUid HAVING cnt > 1)"
        )
        val invalidTxAmounts = scalarInt(
            "SELECT COUNT(*) FROM transactions WHERE amount <= 0 OR amountPaise <= 0"
        )
        val invalidBudgets = scalarInt(
            "SELECT COUNT(*) FROM budgets WHERE monthlyLimit <= 0"
        )
        val invalidGoals = scalarInt(
            "SELECT COUNT(*) FROM goals WHERE targetAmount <= 0"
        )
        val invalidLoans = scalarInt(
            "SELECT COUNT(*) FROM loans WHERE principal < 0 OR monthlyPayment < 0"
        )
        val invalidAmountCount = invalidTxAmounts + invalidBudgets + invalidGoals + invalidLoans

        val isHealthy = integrity.equals("ok", ignoreCase = true) &&
            fkErrors == 0 &&
            orphanedTransactions == 0 &&
            orphanDependents == 0 &&
            duplicateUidCount == 0 &&
            invalidAmountCount == 0

        val summary = if (isHealthy) {
            "SQLCipher integrity verified (ok). $liveAccountCount accounts, $liveTxCount transactions, $liveTrackerCount trackers, 0 orphans, 0 duplicate UIDs."
        } else {
            buildString {
                append("Audit detected issues: PRAGMA=$integrity")
                if (fkErrors > 0) append(", FK errors=$fkErrors")
                if (orphanedTransactions > 0) append(", orphan tx=$orphanedTransactions")
                if (orphanDependents > 0) append(", orphan dependents=$orphanDependents")
                if (duplicateUidCount > 0) append(", duplicate UIDs=$duplicateUidCount")
                if (invalidAmountCount > 0) append(", invalid amounts=$invalidAmountCount")
            }
        }

        return TracmioSecurityEngine.DatabaseHealthReport(
            isHealthy = isHealthy,
            pragmaIntegrity = integrity,
            foreignKeyErrors = fkErrors,
            accountCount = liveAccountCount.coerceAtLeast(accountCount),
            transactionCount = liveTxCount.coerceAtLeast(transactionCount),
            trackerCount = liveTrackerCount.coerceAtLeast(trackerCount),
            orphanedTransactions = orphanedTransactions,
            orphanDependentRecords = orphanDependents,
            duplicateUidCount = duplicateUidCount,
            invalidAmountCount = invalidAmountCount,
            diagnosticSummary = summary
        )
    }

    suspend fun repairOrphanRecords(): Int {
        return db.withTransaction {
            val sqlite = db.openHelper.writableDatabase
            var repaired = 0
            sqlite.query("SELECT id FROM transactions WHERE accountId NOT IN (SELECT id FROM accounts)").use { c ->
                while (c.moveToNext()) repaired++
            }
            sqlite.execSQL("DELETE FROM transactions WHERE accountId NOT IN (SELECT id FROM accounts)")
            sqlite.execSQL("UPDATE transactions SET transferAccountId = NULL WHERE transferAccountId IS NOT NULL AND transferAccountId NOT IN (SELECT id FROM accounts)")
            sqlite.execSQL("DELETE FROM goal_contributions WHERE goalId NOT IN (SELECT id FROM goals)")
            sqlite.execSQL("DELETE FROM loan_payments WHERE loanId NOT IN (SELECT id FROM loans)")
            sqlite.execSQL("DELETE FROM tracker_entries WHERE trackerId NOT IN (SELECT id FROM custom_trackers)")

            // Ensure every transaction has a distinct stableUid
            val seen = mutableSetOf<String>()
            val allTx = financeDao.getAllTransactions().first()
            for (tx in allTx) {
                if (tx.stableUid.isBlank() || !seen.add(tx.stableUid)) {
                    val fresh = UUID.randomUUID().toString()
                    seen.add(fresh)
                    financeDao.updateTransaction(tx.copy(stableUid = fresh))
                    repaired++
                }
            }
            repaired
        }
    }

    suspend fun inspectBackupPayload(jsonText: String): BackupPreviewReport {
        val obj = JSONObject(jsonText)
        val formatVersion = obj.optInt("backupFormatVersion", 1)
        val appName = obj.optString("app", "")
        val accountsArr = obj.optJSONArray("accounts")
        val txArr = obj.optJSONArray("transactions")
        val budgetsArr = obj.optJSONArray("budgets")
        val goalsArr = obj.optJSONArray("goals")
        val loansArr = obj.optJSONArray("loans")
        val personsArr = obj.optJSONArray("persons")
        val trackersArr = obj.optJSONArray("customTrackers")
        val attachmentsArr = obj.optJSONArray("attachments")

        val hasAnyKnownSection = appName.isNotBlank() ||
            accountsArr != null ||
            txArr != null ||
            budgetsArr != null ||
            goalsArr != null ||
            loansArr != null ||
            trackersArr != null
        require(hasAnyKnownSection) { "File is not a valid Tracmio JSON backup." }

        val existingUids = financeDao.getAllTransactionStableUids().toSet()
        var duplicateTx = 0
        if (txArr != null) {
            for (i in 0 until txArr.length()) {
                val uid = txArr.optJSONObject(i)?.optString("stableUid", "")?.trim().orEmpty()
                if (uid.isNotEmpty() && uid in existingUids) {
                    duplicateTx++
                }
            }
        }

        val otherCount = listOf(
            "categories", "goalContributions", "loanPayments", "trackerFolders",
            "trackerEntries", "subscriptions", "academicEvents", "reminders",
            "khataEntries", "wishlistCooldowns", "cashbackRewards", "pricePerUseItems"
        ).sumOf { key -> obj.optJSONArray(key)?.length() ?: 0 }

        return BackupPreviewReport(
            formatVersion = formatVersion,
            appName = appName.ifBlank { "Legacy / Tracmio" },
            accountCount = accountsArr?.length() ?: 0,
            transactionCount = txArr?.length() ?: 0,
            duplicateTransactionCount = duplicateTx,
            budgetCount = budgetsArr?.length() ?: 0,
            goalCount = goalsArr?.length() ?: 0,
            loanCount = loansArr?.length() ?: 0,
            personCount = personsArr?.length() ?: 0,
            trackerCount = trackersArr?.length() ?: 0,
            attachmentCount = attachmentsArr?.length() ?: 0,
            otherEntityCount = otherCount,
            hasPreferences = obj.optJSONObject("appPreferences") != null
        )
    }

    suspend fun restoreBackupJson(
        jsonText: String,
        mode: BackupRestoreMode = BackupRestoreMode.SAFE_MERGE,
        appFilesDir: File? = null
    ): RestoreReport {
        val obj = JSONObject(jsonText)
        val hasAnySection = obj.optString("app", "").isNotBlank() ||
            obj.optJSONArray("accounts") != null ||
            obj.optJSONArray("transactions") != null ||
            obj.optJSONArray("budgets") != null ||
            obj.optJSONArray("goals") != null ||
            obj.optJSONArray("loans") != null ||
            obj.optJSONArray("customTrackers") != null
        require(hasAnySection) { "Invalid backup structure: missing Tracmio ledger arrays." }

        return db.withTransaction {
            if (mode == BackupRestoreMode.CLEAN_REPLACE) {
                db.clearAllTables()
            }

            var insertedCount = 0
            var skippedCount = 0
            var rejectedCount = 0
            var attachmentsRestored = 0

            val restoredAttachmentPaths = mutableMapOf<String, String>()
            val attachmentsArr = obj.optJSONArray("attachments")
            if (attachmentsArr != null && appFilesDir != null) {
                val targetRoot = File(appFilesDir, "restored_attachments").apply { mkdirs() }
                val rootCanon = targetRoot.canonicalPath
                var totalBytesRestored = 0L
                for (i in 0 until attachmentsArr.length()) {
                    val item = attachmentsArr.optJSONObject(i) ?: continue
                    val origPath = item.optString("originalPath", "")
                    val relName = item.optString("relativeName", "att_$i.jpg")
                    val mime = item.optString("mimeType", "image/jpeg").lowercase()
                    val dataB64 = item.optString("dataB64", "")
                    if (relName.contains("..") || relName.contains("/") || relName.contains("\\") || dataB64.isBlank()) {
                        rejectedCount++
                        continue
                    }
                    val ext = relName.substringAfterLast('.', "").lowercase()
                    if (ext !in ALLOWED_IMAGE_EXTS || mime !in ALLOWED_IMAGE_MIMES) {
                        rejectedCount++
                        continue
                    }
                    val bytes = runCatching { Base64.decode(dataB64, Base64.DEFAULT) }.getOrNull()
                    if (bytes == null || bytes.isEmpty() || bytes.size > MAX_SINGLE_ATTACHMENT_BYTES) {
                        rejectedCount++
                        continue
                    }
                    if (totalBytesRestored + bytes.size > MAX_TOTAL_RESTORED_ATTACHMENT_BYTES) {
                        rejectedCount++
                        continue
                    }
                    val safeName = "${UUID.randomUUID().toString().take(8)}_${relName.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
                    val destFile = File(targetRoot, safeName)
                    if (!destFile.canonicalPath.startsWith(rootCanon + File.separator)) {
                        rejectedCount++
                        continue
                    }
                    destFile.writeBytes(bytes)
                    totalBytesRestored += bytes.size
                    if (origPath.isNotBlank()) {
                        restoredAttachmentPaths[origPath] = destFile.absolutePath
                    }
                    attachmentsRestored++
                }
            }

            val accountIdMap = mutableMapOf<Long, Long>()
            val personIdMap = mutableMapOf<Long, Long>()
            val goalIdMap = mutableMapOf<Long, Long>()
            val loanIdMap = mutableMapOf<Long, Long>()
            val folderIdMap = mutableMapOf<Long, Long>()
            val trackerIdMap = mutableMapOf<Long, Long>()

            // 1. Accounts
            val existingAccountsByKey = if (mode == BackupRestoreMode.SAFE_MERGE) {
                accountDao.getAllAccountsIncludingArchived().first()
                    .associateBy { "${it.name.trim().lowercase()}|${it.accountType.trim().lowercase()}" }
                    .toMutableMap()
            } else {
                mutableMapOf()
            }
            val accArr = obj.optJSONArray("accounts")
            if (accArr != null) {
                for (i in 0 until accArr.length()) {
                    val a = accArr.optJSONObject(i)
                    if (a == null) {
                        rejectedCount++
                        continue
                    }
                    val oldId = a.optLong("id", (i + 1).toLong())
                    val name = TracmioSecurityEngine.sanitizeInput(a.optString("name", ""), 80).ifBlank { "Imported Account" }
                    val accType = TracmioSecurityEngine.sanitizeInput(a.optString("accountType", a.optString("type", "Bank")), 40).ifBlank { "Bank" }
                    val bal = a.optDouble("balance", 0.0)
                    val limit = a.optDouble("creditLimit", 0.0)
                    if (!bal.isFinite() || !limit.isFinite()) {
                        rejectedCount++
                        continue
                    }
                    val mergeKey = "${name.lowercase()}|${accType.lowercase()}"
                    val existingAcc = if (mode == BackupRestoreMode.SAFE_MERGE) existingAccountsByKey[mergeKey] else null
                    if (existingAcc != null) {
                        accountIdMap[oldId] = existingAcc.id
                        skippedCount++
                        continue
                    }
                    val balPaise = a.optLong("balancePaise", MoneyPaise.toPaise(bal))
                    val limitPaise = a.optLong("creditLimitPaise", MoneyPaise.toPaise(limit))
                    val newEntity = AccountEntity(
                        name = name,
                        accountType = accType,
                        type = accType,
                        balance = MoneyPaise.toRupees(balPaise),
                        balancePaise = balPaise,
                        creditLimit = MoneyPaise.toRupees(limitPaise),
                        creditLimitPaise = limitPaise,
                        statementDay = a.optInt("statementDay", 1).coerceIn(1, 28),
                        dueDay = a.optInt("dueDay", 15).coerceIn(1, 28),
                        allocationsJson = a.optString("allocationsJson", "[]"),
                        accountNumber = TracmioSecurityEngine.sanitizeInput(a.optString("accountNumber", ""), 32),
                        isArchived = a.optBoolean("isArchived", false),
                        colorHex = TracmioSecurityEngine.sanitizeInput(a.optString("colorHex", "#00F5D4"), 16).ifBlank { "#00F5D4" },
                        createdAt = a.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = a.optLong("updatedAt", System.currentTimeMillis())
                    )
                    val newId = accountDao.insertAccount(newEntity)
                    accountIdMap[oldId] = newId
                    existingAccountsByKey[mergeKey] = newEntity.copy(id = newId)
                    insertedCount++
                }
            }

            // 2. Persons (before transactions so personId resolves)
            val existingPersonsByName = if (mode == BackupRestoreMode.SAFE_MERGE) {
                financeDao.getAllPersons().first().associateBy { it.name.trim().lowercase() }.toMutableMap()
            } else {
                mutableMapOf()
            }
            val personsArr = obj.optJSONArray("persons")
            if (personsArr != null) {
                for (i in 0 until personsArr.length()) {
                    val p = personsArr.optJSONObject(i) ?: continue
                    val oldId = p.optLong("id", (i + 1).toLong())
                    val name = TracmioSecurityEngine.sanitizeInput(p.optString("name", "Contact"), 80)
                    if (name.isBlank()) {
                        rejectedCount++
                        continue
                    }
                    val existing = if (mode == BackupRestoreMode.SAFE_MERGE) existingPersonsByName[name.lowercase()] else null
                    if (existing != null) {
                        personIdMap[oldId] = existing.id
                        skippedCount++
                        continue
                    }
                    val bal = p.optDouble("netBalance", 0.0)
                    if (!bal.isFinite()) {
                        rejectedCount++
                        continue
                    }
                    val balPaise = p.optLong("netBalancePaise", MoneyPaise.toPaise(bal))
                    val person = PersonEntity(
                        name = name,
                        phone = TracmioSecurityEngine.sanitizeInput(p.optString("phone", ""), 20),
                        upiId = TracmioSecurityEngine.sanitizeInput(p.optString("upiId", ""), 80),
                        netBalance = MoneyPaise.toRupees(balPaise),
                        netBalancePaise = balPaise,
                        notes = TracmioSecurityEngine.sanitizeInput(p.optString("notes", ""), 160),
                        createdAt = p.optLong("createdAt", System.currentTimeMillis())
                    )
                    val newId = financeDao.insertPerson(person)
                    personIdMap[oldId] = newId
                    existingPersonsByName[name.lowercase()] = person.copy(id = newId)
                    insertedCount++
                }
            }

            // 3. Categories
            val existingCategoryKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                financeDao.getAllCategories().first().map { "${it.name.lowercase()}|${it.type.uppercase()}" }.toMutableSet()
            } else {
                mutableSetOf()
            }
            val catArr = obj.optJSONArray("categories")
            if (catArr != null) {
                for (i in 0 until catArr.length()) {
                    val c = catArr.optJSONObject(i) ?: continue
                    val name = TracmioSecurityEngine.sanitizeInput(c.optString("name", ""), 60)
                    val type = TracmioSecurityEngine.sanitizeInput(c.optString("type", "EXPENSE"), 20).uppercase()
                    if (name.isBlank()) {
                        rejectedCount++
                        continue
                    }
                    val key = "${name.lowercase()}|$type"
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingCategoryKeys.add(key)) {
                        skippedCount++
                        continue
                    }
                    financeDao.insertCategory(
                        CategoryEntity(
                            name = name,
                            type = type,
                            icon = TracmioSecurityEngine.sanitizeInput(c.optString("icon", "📁"), 12),
                            colorHex = TracmioSecurityEngine.sanitizeInput(c.optString("colorHex", "#00F5D4"), 16)
                        )
                    )
                    insertedCount++
                }
            }

            // 4. Transactions (deduplicated by stableUid in SAFE_MERGE)
            val fallbackAccId = accountDao.getAllAccountsIncludingArchived().first().firstOrNull()?.id
            val existingUids = if (mode == BackupRestoreMode.SAFE_MERGE) {
                financeDao.getAllTransactionStableUids().filter { it.isNotBlank() }.toMutableSet()
            } else {
                mutableSetOf()
            }
            val txArr = obj.optJSONArray("transactions")
            if (txArr != null) {
                for (i in 0 until txArr.length()) {
                    val t = txArr.optJSONObject(i)
                    if (t == null) {
                        rejectedCount++
                        continue
                    }
                    val amt = t.optDouble("amount", 0.0)
                    if (!amt.isFinite() || amt <= 0.0) {
                        rejectedCount++
                        continue
                    }
                    val uid = TracmioSecurityEngine.sanitizeInput(t.optString("stableUid", ""), 80).ifBlank {
                        UUID.randomUUID().toString()
                    }
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingUids.add(uid)) {
                        skippedCount++
                        continue
                    }
                    val oldAccId = t.optLong("accountId", 1L)
                    val mappedAccId = accountIdMap[oldAccId] ?: fallbackAccId ?: oldAccId
                    val oldTransferAccId = if (t.has("transferAccountId") && !t.isNull("transferAccountId")) {
                        t.optLong("transferAccountId", -1L).takeIf { it > 0L }
                    } else null
                    val mappedTransferAccId = oldTransferAccId?.let { accountIdMap[it] }
                    val transferId = if (t.has("transferId") && !t.isNull("transferId")) {
                        TracmioSecurityEngine.sanitizeInput(t.optString("transferId", ""), 64).ifBlank { null }
                    } else null
                    val oldPersonId = if (t.has("personId") && !t.isNull("personId")) {
                        t.optLong("personId", -1L).takeIf { it > 0L }
                    } else null
                    val mappedPersonId = oldPersonId?.let { personIdMap[it] }
                    val rawAtt = t.optString("attachmentUri", "")
                    val mappedAtt = restoredAttachmentPaths[rawAtt] ?: rawAtt
                    val amtPaise = t.optLong("amountPaise", MoneyPaise.toPaise(amt)).takeIf { it > 0L } ?: MoneyPaise.toPaise(amt)
                    val txType = t.optString("type", "EXPENSE").uppercase()

                    financeDao.insertTransaction(
                        TransactionEntity(
                            stableUid = uid,
                            accountId = mappedAccId,
                            type = if (txType in VALID_TX_TYPES) txType else "EXPENSE",
                            amount = MoneyPaise.toRupees(amtPaise),
                            amountPaise = amtPaise,
                            category = TracmioSecurityEngine.sanitizeInput(t.optString("category", "Other"), 60).ifBlank { "Other" },
                            subcategory = TracmioSecurityEngine.sanitizeInput(t.optString("subcategory", ""), 60),
                            paymentMethod = TracmioSecurityEngine.sanitizeInput(t.optString("paymentMethod", "Cash"), 40).ifBlank { "Cash" },
                            upiApp = TracmioSecurityEngine.sanitizeInput(t.optString("upiApp", ""), 40),
                            note = TracmioSecurityEngine.sanitizeInput(t.optString("note", ""), 300),
                            hashtags = TracmioSecurityEngine.sanitizeInput(t.optString("hashtags", ""), 120),
                            warrantyDate = TracmioSecurityEngine.sanitizeInput(t.optString("warrantyDate", ""), 30),
                            attachmentUri = TracmioSecurityEngine.sanitizeInput(mappedAtt, 300),
                            allocationId = TracmioSecurityEngine.sanitizeInput(t.optString("allocationId", ""), 40),
                            transferAccountId = mappedTransferAccId,
                            transferId = transferId,
                            personId = mappedPersonId,
                            timestamp = t.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                    insertedCount++
                }
            }

            // 5. Budgets (upsert/deduplicate by category+month)
            val budgetsArr = obj.optJSONArray("budgets")
            if (budgetsArr != null) {
                for (i in 0 until budgetsArr.length()) {
                    val b = budgetsArr.optJSONObject(i) ?: continue
                    val limit = b.optDouble("monthlyLimit", 0.0)
                    val cat = TracmioSecurityEngine.sanitizeInput(b.optString("category", "Other"), 60)
                    val month = TracmioSecurityEngine.sanitizeInput(b.optString("month", b.optString("monthKey", "")), 20)
                    if (!limit.isFinite() || limit <= 0.0 || cat.isBlank()) {
                        rejectedCount++
                        continue
                    }
                    val existing = if (mode == BackupRestoreMode.SAFE_MERGE) {
                        financeDao.findBudgetByCategoryAndMonth(cat, month)
                    } else null
                    if (existing != null) {
                        skippedCount++
                    } else {
                        val limitPaise = b.optLong("limitPaise", MoneyPaise.toPaise(limit)).takeIf { it > 0L } ?: MoneyPaise.toPaise(limit)
                        financeDao.insertBudget(
                            BudgetEntity(
                                category = cat,
                                monthlyLimit = MoneyPaise.toRupees(limitPaise),
                                limitPaise = limitPaise,
                                month = month,
                                createdAt = b.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                        insertedCount++
                    }
                }
            }

            // 6. Goals & Contributions
            val existingGoalsByTitle = if (mode == BackupRestoreMode.SAFE_MERGE) {
                financeDao.getAllGoals().first().associateBy { it.title.ifBlank { it.name }.trim().lowercase() }.toMutableMap()
            } else mutableMapOf()
            val goalsArr = obj.optJSONArray("goals")
            if (goalsArr != null) {
                for (i in 0 until goalsArr.length()) {
                    val g = goalsArr.optJSONObject(i) ?: continue
                    val oldId = g.optLong("id", (i + 1).toLong())
                    val target = g.optDouble("targetAmount", 0.0)
                    val current = g.optDouble("currentAmount", 0.0)
                    val title = TracmioSecurityEngine.sanitizeInput(g.optString("title", g.optString("name", "Goal")), 80)
                    if (!target.isFinite() || target <= 0.0 || !current.isFinite() || title.isBlank()) {
                        rejectedCount++
                        continue
                    }
                    val existing = if (mode == BackupRestoreMode.SAFE_MERGE) existingGoalsByTitle[title.lowercase()] else null
                    if (existing != null) {
                        goalIdMap[oldId] = existing.id
                        skippedCount++
                        continue
                    }
                    val targetPaise = g.optLong("targetPaise", MoneyPaise.toPaise(target))
                    val currentPaise = g.optLong("currentPaise", MoneyPaise.toPaise(current))
                    val entity = GoalEntity(
                        name = title,
                        title = title,
                        targetAmount = MoneyPaise.toRupees(targetPaise),
                        targetPaise = targetPaise,
                        currentAmount = MoneyPaise.toRupees(currentPaise),
                        currentPaise = currentPaise,
                        deadline = TracmioSecurityEngine.sanitizeInput(g.optString("deadline", ""), 40),
                        icon = TracmioSecurityEngine.sanitizeInput(g.optString("icon", "🎯"), 12),
                        colorHex = TracmioSecurityEngine.sanitizeInput(g.optString("colorHex", "#00F5D4"), 16),
                        createdAt = g.optLong("createdAt", System.currentTimeMillis())
                    )
                    val newId = financeDao.insertGoal(entity)
                    goalIdMap[oldId] = newId
                    existingGoalsByTitle[title.lowercase()] = entity.copy(id = newId)
                    insertedCount++
                }
            }

            val existingGoalContribKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                financeDao.getAllGoalContributions().first().map { "${it.goalId}|${it.amountPaise}|${it.timestamp}" }.toMutableSet()
            } else mutableSetOf()
            val goalContribArr = obj.optJSONArray("goalContributions")
            if (goalContribArr != null) {
                for (i in 0 until goalContribArr.length()) {
                    val c = goalContribArr.optJSONObject(i) ?: continue
                    val oldGoalId = c.optLong("goalId", 1L)
                    val amt = c.optDouble("amount", 0.0)
                    if (!amt.isFinite() || amt <= 0.0) {
                        rejectedCount++
                        continue
                    }
                    val mappedGoalId = goalIdMap[oldGoalId] ?: continue
                    val amtPaise = c.optLong("amountPaise", MoneyPaise.toPaise(amt))
                    val ts = c.optLong("timestamp", System.currentTimeMillis())
                    val key = "$mappedGoalId|$amtPaise|$ts"
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingGoalContribKeys.add(key)) {
                        skippedCount++
                        continue
                    }
                    financeDao.insertGoalContribution(
                        GoalContributionEntity(
                            goalId = mappedGoalId,
                            amount = MoneyPaise.toRupees(amtPaise),
                            amountPaise = amtPaise,
                            note = TracmioSecurityEngine.sanitizeInput(c.optString("note", ""), 120),
                            timestamp = ts
                        )
                    )
                    insertedCount++
                }
            }

            // 7. Loans & Loan Payments
            val existingLoansByName = if (mode == BackupRestoreMode.SAFE_MERGE) {
                financeDao.getAllLoans().first().associateBy { it.name.ifBlank { it.lender }.trim().lowercase() }.toMutableMap()
            } else mutableMapOf()
            val loansArr = obj.optJSONArray("loans")
            if (loansArr != null) {
                for (i in 0 until loansArr.length()) {
                    val l = loansArr.optJSONObject(i) ?: continue
                    val oldId = l.optLong("id", (i + 1).toLong())
                    val principal = l.optDouble("principal", 0.0)
                    val emi = l.optDouble("monthlyPayment", 0.0)
                    val apr = l.optDouble("interestRate", 0.0)
                    val name = TracmioSecurityEngine.sanitizeInput(l.optString("name", l.optString("lender", "Loan")), 80)
                    if (!principal.isFinite() || principal < 0.0 || !emi.isFinite() || !apr.isFinite() || name.isBlank()) {
                        rejectedCount++
                        continue
                    }
                    val existing = if (mode == BackupRestoreMode.SAFE_MERGE) existingLoansByName[name.lowercase()] else null
                    if (existing != null) {
                        loanIdMap[oldId] = existing.id
                        skippedCount++
                        continue
                    }
                    val principalPaise = l.optLong("principalPaise", MoneyPaise.toPaise(principal))
                    val emiPaise = l.optLong("emiPaise", MoneyPaise.toPaise(emi))
                    val entity = LoanEntity(
                        name = name,
                        lender = name,
                        principal = MoneyPaise.toRupees(principalPaise),
                        principalPaise = principalPaise,
                        interestRate = apr,
                        tenureMonths = l.optInt("tenureMonths", 12).coerceAtLeast(1),
                        monthlyPayment = MoneyPaise.toRupees(emiPaise),
                        emiPaise = emiPaise,
                        startDateMillis = l.optLong("startDateMillis", System.currentTimeMillis()),
                        isClosed = l.optBoolean("isClosed", false)
                    )
                    val newId = financeDao.insertLoan(entity)
                    loanIdMap[oldId] = newId
                    existingLoansByName[name.lowercase()] = entity.copy(id = newId)
                    insertedCount++
                }
            }

            val existingLoanPayKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                financeDao.getAllLoanPayments().first().map { "${it.loanId}|${it.amountPaise}|${it.timestamp}" }.toMutableSet()
            } else mutableSetOf()
            val loanPayArr = obj.optJSONArray("loanPayments")
            if (loanPayArr != null) {
                for (i in 0 until loanPayArr.length()) {
                    val lp = loanPayArr.optJSONObject(i) ?: continue
                    val oldLoanId = lp.optLong("loanId", 1L)
                    val amt = lp.optDouble("amount", 0.0)
                    if (!amt.isFinite() || amt <= 0.0) {
                        rejectedCount++
                        continue
                    }
                    val mappedLoanId = loanIdMap[oldLoanId] ?: continue
                    val amtPaise = lp.optLong("amountPaise", MoneyPaise.toPaise(amt))
                    val ts = lp.optLong("timestamp", System.currentTimeMillis())
                    val key = "$mappedLoanId|$amtPaise|$ts"
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingLoanPayKeys.add(key)) {
                        skippedCount++
                        continue
                    }
                    financeDao.insertLoanPayment(
                        LoanPaymentEntity(
                            loanId = mappedLoanId,
                            amount = MoneyPaise.toRupees(amtPaise),
                            amountPaise = amtPaise,
                            note = TracmioSecurityEngine.sanitizeInput(lp.optString("note", ""), 120),
                            timestamp = ts
                        )
                    )
                    insertedCount++
                }
            }

            // 8. Tracker Folders, Custom Trackers, Tracker Entries
            val existingFoldersByName = if (mode == BackupRestoreMode.SAFE_MERGE) {
                customTrackerDao.getAllFolders().first().associateBy { it.folderName.trim().lowercase() }.toMutableMap()
            } else mutableMapOf()
            val foldersArr = obj.optJSONArray("trackerFolders")
            if (foldersArr != null) {
                for (i in 0 until foldersArr.length()) {
                    val f = foldersArr.optJSONObject(i) ?: continue
                    val oldId = f.optLong("id", (i + 1).toLong())
                    val name = TracmioSecurityEngine.sanitizeInput(f.optString("folderName", "Folder"), 60)
                    if (name.isBlank()) {
                        rejectedCount++
                        continue
                    }
                    val existing = if (mode == BackupRestoreMode.SAFE_MERGE) existingFoldersByName[name.lowercase()] else null
                    if (existing != null) {
                        folderIdMap[oldId] = existing.id
                        skippedCount++
                        continue
                    }
                    val entity = TrackerFolderEntity(
                        folderName = name,
                        icon = TracmioSecurityEngine.sanitizeInput(f.optString("icon", "📂"), 12),
                        colorHex = TracmioSecurityEngine.sanitizeInput(f.optString("colorHex", "#00F5D4"), 16),
                        sortOrder = f.optInt("sortOrder", i)
                    )
                    val newId = customTrackerDao.insertFolder(entity)
                    folderIdMap[oldId] = newId
                    existingFoldersByName[name.lowercase()] = entity.copy(id = newId)
                    insertedCount++
                }
            }

            val existingTrackersByTitle = if (mode == BackupRestoreMode.SAFE_MERGE) {
                customTrackerDao.getAllTrackers().first().associateBy { "${it.title.trim().lowercase()}|${it.trackerType.uppercase()}" }.toMutableMap()
            } else mutableMapOf()
            val trackersArr = obj.optJSONArray("customTrackers")
            if (trackersArr != null) {
                for (i in 0 until trackersArr.length()) {
                    val t = trackersArr.optJSONObject(i) ?: continue
                    val oldId = t.optLong("id", (i + 1).toLong())
                    val oldFolder = t.optLong("folderId", 0L)
                    val title = TracmioSecurityEngine.sanitizeInput(t.optString("title", "Tracker"), 80)
                    val tType = TracmioSecurityEngine.sanitizeInput(t.optString("trackerType", "SAVINGS"), 30).uppercase()
                    val target = t.optDouble("targetValue", 0.0)
                    val current = t.optDouble("currentValue", 0.0)
                    if (title.isBlank() || !target.isFinite() || !current.isFinite()) {
                        rejectedCount++
                        continue
                    }
                    val key = "${title.lowercase()}|$tType"
                    val existing = if (mode == BackupRestoreMode.SAFE_MERGE) existingTrackersByTitle[key] else null
                    if (existing != null) {
                        trackerIdMap[oldId] = existing.id
                        skippedCount++
                        continue
                    }
                    val entity = CustomTrackerEntity(
                        title = title,
                        trackerType = tType,
                        targetValue = target,
                        currentValue = current,
                        unit = TracmioSecurityEngine.sanitizeInput(t.optString("unit", "₹"), 12),
                        icon = TracmioSecurityEngine.sanitizeInput(t.optString("icon", "🎯"), 12),
                        colorHex = TracmioSecurityEngine.sanitizeInput(t.optString("colorHex", "#00F5D4"), 16),
                        folderId = if (oldFolder > 0L) (folderIdMap[oldFolder] ?: 0L) else 0L,
                        isPinned = t.optBoolean("isPinned", false),
                        updatedAt = t.optLong("updatedAt", System.currentTimeMillis())
                    )
                    val newId = customTrackerDao.insertTracker(entity)
                    trackerIdMap[oldId] = newId
                    existingTrackersByTitle[key] = entity.copy(id = newId)
                    insertedCount++
                }
            }

            val existingEntryKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                customTrackerDao.getAllEntries().first().map { "${it.trackerId}|${it.deltaValue}|${it.timestamp}" }.toMutableSet()
            } else mutableSetOf()
            val entriesArr = obj.optJSONArray("trackerEntries")
            if (entriesArr != null) {
                for (i in 0 until entriesArr.length()) {
                    val e = entriesArr.optJSONObject(i) ?: continue
                    val oldT = e.optLong("trackerId", 1L)
                    val mappedTrackerId = trackerIdMap[oldT] ?: continue
                    val delta = e.optDouble("deltaValue", 0.0)
                    if (!delta.isFinite()) {
                        rejectedCount++
                        continue
                    }
                    val ts = e.optLong("timestamp", System.currentTimeMillis())
                    val key = "$mappedTrackerId|$delta|$ts"
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingEntryKeys.add(key)) {
                        skippedCount++
                        continue
                    }
                    val rawPath = e.optString("attachmentPath", "")
                    val mappedPath = restoredAttachmentPaths[rawPath] ?: rawPath
                    customTrackerDao.insertEntry(
                        CustomTrackerEntryEntity(
                            trackerId = mappedTrackerId,
                            deltaValue = delta,
                            note = TracmioSecurityEngine.sanitizeInput(e.optString("note", ""), 120),
                            attachmentPath = TracmioSecurityEngine.sanitizeInput(mappedPath, 300),
                            timestamp = ts
                        )
                    )
                    insertedCount++
                }
            }

            // 9. Subscriptions, Academic Events, Reminders, Khata, Wishlist, Cashback, PricePerUse
            val existingSubKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                featureDao.getAllSubscriptions().first().map { "${it.name.lowercase()}|${it.amountPaise}|${it.billingCycle}" }.toMutableSet()
            } else mutableSetOf()
            val subsArr = obj.optJSONArray("subscriptions")
            if (subsArr != null) {
                for (i in 0 until subsArr.length()) {
                    val s = subsArr.optJSONObject(i) ?: continue
                    val amt = s.optDouble("amount", 0.0)
                    val name = TracmioSecurityEngine.sanitizeInput(s.optString("name", "Subscription"), 80)
                    val cycle = TracmioSecurityEngine.sanitizeInput(s.optString("billingCycle", "Monthly"), 20)
                    if (!amt.isFinite() || amt <= 0.0 || name.isBlank()) {
                        rejectedCount++
                        continue
                    }
                    val amtPaise = s.optLong("amountPaise", MoneyPaise.toPaise(amt))
                    val key = "${name.lowercase()}|$amtPaise|$cycle"
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingSubKeys.add(key)) {
                        skippedCount++
                        continue
                    }
                    featureDao.insertSubscription(
                        SubscriptionEntity(
                            name = name,
                            amount = MoneyPaise.toRupees(amtPaise),
                            amountPaise = amtPaise,
                            billingCycle = cycle,
                            nextDueDateMillis = s.optLong("nextDueDateMillis", System.currentTimeMillis()),
                            category = TracmioSecurityEngine.sanitizeInput(s.optString("category", "SIM, WiFi & Bills"), 40),
                            isSimOrWifi = s.optBoolean("isSimOrWifi", false),
                            validityDays = s.optInt("validityDays", 28).coerceAtLeast(1),
                            isActive = s.optBoolean("isActive", true)
                        )
                    )
                    insertedCount++
                }
            }

            val existingAcadKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                featureDao.getAllAcademicEvents().first().map { "${it.title.lowercase()}|${it.dueDate}" }.toMutableSet()
            } else mutableSetOf()
            val acadArr = obj.optJSONArray("academicEvents")
            if (acadArr != null) {
                for (i in 0 until acadArr.length()) {
                    val ev = acadArr.optJSONObject(i) ?: continue
                    val title = TracmioSecurityEngine.sanitizeInput(ev.optString("title", "Academic Fee"), 80)
                    val due = TracmioSecurityEngine.sanitizeInput(ev.optString("dueDate", ""), 30)
                    val fee = ev.optDouble("feeAmount", 0.0)
                    val paid = ev.optDouble("paidAmount", 0.0)
                    if (title.isBlank() || !fee.isFinite() || !paid.isFinite()) {
                        rejectedCount++
                        continue
                    }
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingAcadKeys.add("${title.lowercase()}|$due")) {
                        skippedCount++
                        continue
                    }
                    featureDao.insertAcademicEvent(
                        AcademicEventEntity(
                            title = title,
                            feeAmount = fee,
                            paidAmount = paid,
                            dueDate = due,
                            semester = TracmioSecurityEngine.sanitizeInput(ev.optString("semester", "Current"), 30),
                            isPaid = ev.optBoolean("isPaid", false)
                        )
                    )
                    insertedCount++
                }
            }

            val existingRemKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                featureDao.getAllReminders().first().map { "${it.title.lowercase()}|${it.dueDate}" }.toMutableSet()
            } else mutableSetOf()
            val remArr = obj.optJSONArray("reminders")
            if (remArr != null) {
                for (i in 0 until remArr.length()) {
                    val r = remArr.optJSONObject(i) ?: continue
                    val title = TracmioSecurityEngine.sanitizeInput(r.optString("title", "Reminder"), 100)
                    val due = TracmioSecurityEngine.sanitizeInput(r.optString("dueDate", ""), 30)
                    val amt = r.optDouble("amount", 0.0)
                    if (title.isBlank() || !amt.isFinite()) {
                        rejectedCount++
                        continue
                    }
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingRemKeys.add("${title.lowercase()}|$due")) {
                        skippedCount++
                        continue
                    }
                    featureDao.insertReminder(
                        ReminderItemEntity(
                            title = title,
                            amount = amt,
                            dueDate = due,
                            timeHour = r.optInt("timeHour", 9).coerceIn(0, 23),
                            timeMinute = r.optInt("timeMinute", 0).coerceIn(0, 59),
                            isCompleted = r.optBoolean("isCompleted", false),
                            isRecurring = r.optBoolean("isRecurring", false)
                        )
                    )
                    insertedCount++
                }
            }

            val existingKhataKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                featureDao.getAllKhataEntries().first().map { "${it.shopOrFriend.lowercase()}|${it.timestamp}|${it.amount}" }.toMutableSet()
            } else mutableSetOf()
            val khataArr = obj.optJSONArray("khataEntries")
            if (khataArr != null) {
                for (i in 0 until khataArr.length()) {
                    val k = khataArr.optJSONObject(i) ?: continue
                    val shop = TracmioSecurityEngine.sanitizeInput(k.optString("shopOrFriend", "Shop"), 80)
                    val amt = k.optDouble("amount", 0.0)
                    val ts = k.optLong("timestamp", System.currentTimeMillis())
                    if (shop.isBlank() || !amt.isFinite() || amt <= 0.0) {
                        rejectedCount++
                        continue
                    }
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingKhataKeys.add("${shop.lowercase()}|$ts|$amt")) {
                        skippedCount++
                        continue
                    }
                    featureDao.insertKhataEntry(
                        KhataEntryEntity(
                            shopOrFriend = shop,
                            itemDescription = TracmioSecurityEngine.sanitizeInput(k.optString("itemDescription", ""), 120),
                            amount = amt,
                            isSettled = k.optBoolean("isSettled", false),
                            timestamp = ts
                        )
                    )
                    insertedCount++
                }
            }

            val existingWishlistKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                featureDao.getAllWishlistCooldowns().first().map { "${it.itemName.lowercase()}|${it.createdAtMillis}" }.toMutableSet()
            } else mutableSetOf()
            val wishlistArr = obj.optJSONArray("wishlistCooldowns")
            if (wishlistArr != null) {
                for (i in 0 until wishlistArr.length()) {
                    val w = wishlistArr.optJSONObject(i) ?: continue
                    val item = TracmioSecurityEngine.sanitizeInput(w.optString("itemName", "Wishlist Item"), 80)
                    val price = w.optDouble("price", 0.0)
                    val ts = w.optLong("createdAtMillis", System.currentTimeMillis())
                    if (item.isBlank() || !price.isFinite() || price <= 0.0) {
                        rejectedCount++
                        continue
                    }
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingWishlistKeys.add("${item.lowercase()}|$ts")) {
                        skippedCount++
                        continue
                    }
                    featureDao.insertWishlistCooldown(
                        WishlistCooldownEntity(
                            itemName = item,
                            price = price,
                            createdAtMillis = ts,
                            status = TracmioSecurityEngine.sanitizeInput(w.optString("status", "COOLING"), 24)
                        )
                    )
                    insertedCount++
                }
            }

            val existingCashbackKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                featureDao.getAllCashbackRewards().first().map { "${it.sourceApp.lowercase()}|${it.amount}|${it.timestamp}" }.toMutableSet()
            } else mutableSetOf()
            val cashbackArr = obj.optJSONArray("cashbackRewards")
            if (cashbackArr != null) {
                for (i in 0 until cashbackArr.length()) {
                    val c = cashbackArr.optJSONObject(i) ?: continue
                    val app = TracmioSecurityEngine.sanitizeInput(c.optString("sourceApp", "UPI App"), 50)
                    val amt = c.optDouble("amount", 0.0)
                    val ts = c.optLong("timestamp", System.currentTimeMillis())
                    if (app.isBlank() || !amt.isFinite() || amt <= 0.0) {
                        rejectedCount++
                        continue
                    }
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingCashbackKeys.add("${app.lowercase()}|$amt|$ts")) {
                        skippedCount++
                        continue
                    }
                    featureDao.insertCashbackReward(
                        CashbackRewardEntity(
                            sourceApp = app,
                            amount = amt,
                            note = TracmioSecurityEngine.sanitizeInput(c.optString("note", ""), 120),
                            timestamp = ts
                        )
                    )
                    insertedCount++
                }
            }

            val existingPpuKeys = if (mode == BackupRestoreMode.SAFE_MERGE) {
                featureDao.getAllPricePerUseItems().first().map { "${it.itemName.lowercase()}|${it.createdAtMillis}" }.toMutableSet()
            } else mutableSetOf()
            val ppuArr = obj.optJSONArray("pricePerUseItems")
            if (ppuArr != null) {
                for (i in 0 until ppuArr.length()) {
                    val p = ppuArr.optJSONObject(i) ?: continue
                    val item = TracmioSecurityEngine.sanitizeInput(p.optString("itemName", "Item"), 80)
                    val price = p.optDouble("purchasePrice", 0.0)
                    val ts = p.optLong("createdAtMillis", System.currentTimeMillis())
                    if (item.isBlank() || !price.isFinite() || price <= 0.0) {
                        rejectedCount++
                        continue
                    }
                    if (mode == BackupRestoreMode.SAFE_MERGE && !existingPpuKeys.add("${item.lowercase()}|$ts")) {
                        skippedCount++
                        continue
                    }
                    featureDao.insertPricePerUseItem(
                        PricePerUseEntity(
                            itemName = item,
                            purchasePrice = price,
                            usageCount = p.optInt("usageCount", 1).coerceAtLeast(1),
                            targetUses = p.optInt("targetUses", 30).coerceAtLeast(1),
                            createdAtMillis = ts
                        )
                    )
                    insertedCount++
                }
            }

            val summary = buildString {
                append("${mode.name}: $insertedCount restored")
                if (skippedCount > 0) append(", $skippedCount duplicates skipped")
                if (rejectedCount > 0) append(", $rejectedCount invalid rejected")
                if (attachmentsRestored > 0) append(", $attachmentsRestored attachments restored")
            }

            RestoreReport(
                mode = mode,
                insertedCount = insertedCount,
                skippedDuplicatesCount = skippedCount,
                rejectedCount = rejectedCount,
                attachmentsRestoredCount = attachmentsRestored,
                details = summary,
                restoredAttachmentMap = restoredAttachmentPaths.toMap()
            )
        }
    }

    suspend fun importBackupJson(jsonText: String): Int {
        return restoreBackupJson(jsonText, BackupRestoreMode.SAFE_MERGE).insertedCount
    }

    companion object {
        private val VALID_TX_TYPES = setOf("INCOME", "EXPENSE", "TRANSFER", "TRANSFER_IN", "TRANSFER_OUT", "BORROW", "LEND")
        private val ALLOWED_IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp")
        private val ALLOWED_IMAGE_MIMES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
        private const val MAX_SINGLE_ATTACHMENT_BYTES = 2 * 1024 * 1024
        private const val MAX_TOTAL_RESTORED_ATTACHMENT_BYTES = 12 * 1024 * 1024
    }
}
