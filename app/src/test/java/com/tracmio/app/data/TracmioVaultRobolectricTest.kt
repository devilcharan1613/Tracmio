package com.tracmio.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tracmio.app.DailyReminderReceiver
import com.tracmio.app.data.local.*
import com.tracmio.app.data.repository.FinanceRepository
import com.tracmio.app.data.security.AppLockManager
import com.tracmio.app.data.services.NativeBackupService
import com.tracmio.app.ui.nativeapp.LiquidGlassSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TracmioVaultRobolectricTest {
    @Test
    fun notificationCatalogHas100ValidItems() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawJson = context.assets.open("notifications.json").bufferedReader().use { it.readText() }
        val parsed = DailyReminderReceiver.parseNotificationCatalogJson(rawJson)
        assertEquals(100, parsed.size)
        assertTrue(parsed.all { it.title.isNotBlank() && it.body.isNotBlank() })
    }

    @Test
    fun appLockEnableVerifyAndRecoveryWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPrefs = context.getSharedPreferences("test_vault_applock_prefs", Context.MODE_PRIVATE)
        testPrefs.edit().clear().commit()
        val lockMgr = AppLockManager(context, securePrefsProvider = { testPrefs })
        val recoveryKey = lockMgr.enableLock("2580", biometricEnabled = true, "Pet?", "Rocky")
        assertTrue(recoveryKey.startsWith("TRAC-"))
        assertEquals("", lockMgr.getState().recoveryKey)
        assertTrue(lockMgr.verifyPin("2580"))
        assertFalse(lockMgr.verifyPin("0000"))

        assertTrue(lockMgr.verifyRecoveryAndReset("rocky", "1357"))
        assertTrue(lockMgr.verifyPin("1357"))
        assertTrue(lockMgr.verifyRecoveryAndReset(recoveryKey, null))
        assertFalse(lockMgr.getState().isEnabled)
    }

    @Test
    fun roomDatabaseBackupSafeMergeDeduplicatesAndFullRestorePreservesAllEntitiesAndAttachments() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val attDir = File(context.filesDir, "tracker_attachments").apply { mkdirs() }
        val receiptFile = File(attDir, "receipt_test.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4))
        }
        val db = Room.inMemoryDatabaseBuilder(context, TracmioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val freshDb = Room.inMemoryDatabaseBuilder(context, TracmioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = FinanceRepository(db)
            val accId = repo.insertAccount(AccountEntity(name = "Primary Bank", accountType = "Bank", balance = 5000.0, balancePaise = 500_000L))
            val personId = repo.insertPerson(PersonEntity(name = "Aarav", upiId = "aarav@upi", netBalance = 400.0, netBalancePaise = 40_000L))
            val txId = repo.insertTransaction(
                TransactionEntity(
                    accountId = accId,
                    personId = personId,
                    type = "EXPENSE",
                    amount = 150.0,
                    amountPaise = 15_000L,
                    category = "Food & Chai",
                    note = "Lunch #office",
                    attachmentUri = receiptFile.absolutePath,
                    timestamp = 1_700_000_000_000L
                )
            )
            repo.upsertBudget("Food & Chai", 4000.0, "2026-11")
            val goalId = repo.insertGoal(GoalEntity(name = "Laptop", title = "Laptop", targetAmount = 50000.0, currentAmount = 5000.0))
            repo.insertGoalContribution(GoalContributionEntity(goalId = goalId, amount = 500.0, amountPaise = 50_000L, note = "Seed"))
            val loanId = repo.insertLoan(LoanEntity(name = "Bike", principal = 25000.0, interestRate = 9.5, monthlyPayment = 1800.0))
            repo.insertLoanPayment(LoanPaymentEntity(loanId = loanId, amount = 1800.0, amountPaise = 180_000L))
            repo.insertCategory(CategoryEntity(name = "Study", type = "EXPENSE"))
            val folderId = repo.insertTrackerFolder(TrackerFolderEntity(folderName = "Health"))
            val trackerId = repo.insertCustomTracker(CustomTrackerEntity(title = "Water", trackerType = "HABIT", targetValue = 8.0, folderId = folderId))
            repo.insertTrackerEntry(CustomTrackerEntryEntity(trackerId = trackerId, deltaValue = 2.0, note = "Morning"))
            repo.insertKhataEntry(KhataEntryEntity(shopOrFriend = "Raju Kirana", itemDescription = "Groceries", amount = 320.0))
            repo.insertSubscription(SubscriptionEntity(name = "Jio 5G", amount = 299.0, billingCycle = "28d", isSimOrWifi = true, validityDays = 28))
            repo.insertAcademicEvent(AcademicEventEntity(title = "Sem Fee", feeAmount = 12000.0, dueDate = "2026-12-01"))
            repo.insertReminder(ReminderItemEntity(title = "Pay Rent", amount = 8000.0, dueDate = "2026-12-05"))
            repo.insertWishlistCooldown(WishlistCooldownEntity(itemName = "Watch", price = 4500.0))
            repo.insertCashbackReward(CashbackRewardEntity(sourceApp = "GPay", amount = 45.0))
            repo.insertPricePerUseItem(PricePerUseEntity(itemName = "Shoes", purchasePrice = 3000.0, usageCount = 10))

            val fetched = repo.getTransactionById(txId)
            assertNotNull(fetched)
            assertEquals(1_700_000_000_000L, fetched!!.timestamp)
            assertTrue(repo.runDatabaseHealthCheck(1, 1, 1).isHealthy)

            val uiState = NativeTracmioUiState(
                accounts = repo.accounts.first(),
                transactions = repo.transactions.first(),
                budgets = repo.budgets.first(),
                goals = repo.goals.first(),
                goalContributions = repo.goalContributions.first(),
                loans = repo.loans.first(),
                loanPayments = repo.loanPayments.first(),
                persons = repo.persons.first(),
                customTrackers = repo.customTrackers.first(),
                trackerEntries = repo.trackerEntries.first(),
                trackerFolders = repo.trackerFolders.first(),
                subscriptions = repo.subscriptions.first(),
                academicEvents = repo.academicEvents.first(),
                reminders = repo.reminders.first(),
                khataEntries = repo.khataEntries.first(),
                wishlistCooldowns = repo.wishlistCooldowns.first(),
                cashbackRewards = repo.cashbackRewards.first(),
                pricePerUseItems = repo.pricePerUseItems.first(),
                pinnedToolIds = listOf("khata", "cash_counter")
            )
            val backupJson = NativeBackupService.buildFullBackupJson(
                state = uiState,
                settings = LiquidGlassSettings(wallpaperBlurDp = 14f, deviceMotionEnabled = true),
                appFilesDir = context.filesDir,
                categories = repo.categories.first()
            )
            assertTrue(backupJson.contains("Primary Bank"))
            assertTrue(backupJson.contains("Lunch #office"))
            assertTrue(backupJson.contains("receipt_test.jpg"))

            // 1. Safe Merge into same populated DB must deduplicate and insert 0 duplicate records
            val mergeReport = repo.restoreBackupJson(backupJson, FinanceRepository.BackupRestoreMode.SAFE_MERGE, context.filesDir)
            assertEquals(0, mergeReport.insertedCount)
            assertEquals(1, repo.accounts.first().size)
            assertEquals(1, repo.transactions.first().size)

            // 2. Restore into fresh DB restores all entities, remaps personId, and rejects traversal attachments
            val craftedObj = JSONObject(backupJson).apply {
                val atts = getJSONArray("attachments")
                atts.put(JSONObject().apply {
                    put("originalPath", "/etc/passwd")
                    put("relativeName", "../../evil.jpg")
                    put("mimeType", "image/jpeg")
                    put("dataB64", "AAAA")
                })
                atts.put(JSONObject().apply {
                    put("originalPath", "/tmp/bad.apk")
                    put("relativeName", "bad.apk")
                    put("mimeType", "application/vnd.android.package-archive")
                    put("dataB64", "AAAA")
                })
            }.toString()

            val freshRepo = FinanceRepository(freshDb)
            val freshReport = freshRepo.restoreBackupJson(craftedObj, FinanceRepository.BackupRestoreMode.CLEAN_REPLACE, context.filesDir)
            assertTrue(freshReport.insertedCount >= 18)
            assertEquals(2, freshReport.rejectedCount)
            assertEquals(1, freshReport.attachmentsRestoredCount)
            assertEquals(1, freshRepo.accounts.first().size)
            assertEquals(1, freshRepo.transactions.first().size)
            assertEquals(1, freshRepo.persons.first().size)
            assertEquals(freshRepo.persons.first().first().id, freshRepo.transactions.first().first().personId)
            assertEquals(1, freshRepo.goalContributions.first().size)
            assertEquals(1, freshRepo.loanPayments.first().size)
            assertEquals(1, freshRepo.customTrackers.first().size)
            assertEquals(1, freshRepo.trackerEntries.first().size)
            assertEquals(1, freshRepo.trackerFolders.first().size)
            assertEquals(1, freshRepo.academicEvents.first().size)
            assertEquals(1, freshRepo.reminders.first().size)
            assertEquals(1, freshRepo.wishlistCooldowns.first().size)
            assertEquals(1, freshRepo.cashbackRewards.first().size)
            assertEquals(1, freshRepo.pricePerUseItems.first().size)
            assertFalse(File(context.filesDir, "evil.jpg").exists())
        } finally {
            db.close()
            freshDb.close()
            receiptFile.delete()
        }
    }

    @Test
    fun monthlyBackupPrefsAndReminderChannelWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferencesAndThemeStore(context)
        assertFalse(prefs.isMonthlyBackupEnabled())
        prefs.saveMonthlyBackupEnabled(true)
        assertTrue(prefs.isMonthlyBackupEnabled())

        com.tracmio.app.TracmioReminderScheduler.ensureChannel(context)
        com.tracmio.app.TracmioReminderScheduler.scheduleMonthlyBackupReminder(context, enabled = true)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = nm.getNotificationChannel(com.tracmio.app.TracmioReminderScheduler.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(com.tracmio.app.TracmioReminderScheduler.CHANNEL_NAME, channel.name.toString())

        val accounts = listOf(AccountEntity(id = 1L, name = "Main", accountType = "Bank", balance = 4000.0))
        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, type = "INCOME", amount = 1000.0, category = "Salary", note = "Bonus"),
            TransactionEntity(id = 2L, accountId = 1L, type = "EXPENSE", amount = 250.0, category = "Food & Chai", note = "Tea")
        )
        val liveTotal = accounts.sumOf { FinancialCalculator.balance(it, txs) }
        assertEquals(4750.0, liveTotal, 0.01)
    }
}
