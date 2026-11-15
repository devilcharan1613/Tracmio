package com.tracmio.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.tracmio.app.data.local.*
import com.tracmio.app.data.repository.FinanceRepository
import com.tracmio.app.data.security.AppLockManager
import com.tracmio.app.data.security.TracmioSecurityEngine
import com.tracmio.app.data.services.NativeStatementPdf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class TracmioAuditRemediationRobolectricTest {

    @Test
    fun vaultOpenFailsClosedWhenSqlCipherOrKeystoreUnavailable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TracmioDatabase.clearInstanceForTesting()

        val cipherFail = TracmioDatabase.openVaultOrFail(
            context = context,
            nativeLoader = { throw UnsatisfiedLinkError("libsqlcipher.so missing in test") }
        )
        assertTrue(cipherFail is VaultOpenResult.Failure)
        assertTrue((cipherFail as VaultOpenResult.Failure).title.contains("Encrypted Vault Engine"))

        val keystoreFail = TracmioDatabase.openVaultOrFail(
            context = context,
            nativeLoader = {},
            encryptedPrefsProvider = { throw SecurityException("Android Keystore locked") }
        )
        assertTrue(keystoreFail is VaultOpenResult.Failure)
        assertTrue((keystoreFail as VaultOpenResult.Failure).title.contains("Android Keystore"))

        // Legacy passphrase non-destructive copy: legacy key stays until DB open succeeds
        val legacyPrefs = context.getSharedPreferences("tracmio_db_key_store", Context.MODE_PRIVATE)
        val securePrefs = context.getSharedPreferences("test_secure_db_key", Context.MODE_PRIVATE)
        legacyPrefs.edit().clear().commit()
        securePrefs.edit().clear().commit()

        val legacyPass = Base64.encodeToString(ByteArray(32) { 0x42 }, Base64.NO_WRAP)
        legacyPrefs.edit().putString("db_passphrase_b64", legacyPass).commit()

        val (resolved, migrated) = TracmioDatabase.resolveOrProvisionPassphrase(context, securePrefs)
        assertTrue(migrated)
        assertEquals(32, resolved.size)
        assertEquals(legacyPass, securePrefs.getString("db_passphrase_b64", null))
        assertEquals(legacyPass, legacyPrefs.getString("db_passphrase_b64", null))
    }

    @Test
    fun roomMigrationsV1ToV10UpgradeSchemaWithoutDataLoss() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.cacheDir, "migration_v1_v10_test_${System.currentTimeMillis()}.db")
        if (dbFile.exists()) dbFile.delete()

        // Create minimal v1 schema with existing user data
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, balance REAL NOT NULL DEFAULT 0.0)")
            sqlite.execSQL("INSERT INTO accounts (name, balance) VALUES ('Legacy Salary Bank', 25000.0)")
            sqlite.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountId INTEGER NOT NULL, type TEXT NOT NULL, amount REAL NOT NULL, category TEXT NOT NULL)")
            sqlite.execSQL("INSERT INTO transactions (accountId, type, amount, category) VALUES (1, 'EXPENSE', 450.0, 'Food & Chai')")
            sqlite.version = 1
        }

        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    TracmioDatabase.ensureCoreSchemaTablesAndColumns(db)
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    var v = oldVersion
                    while (v < newVersion) {
                        val m = TracmioDatabase.ALL_MIGRATIONS.firstOrNull { it.startVersion == v && it.endVersion == v + 1 }
                            ?: error("Missing migration $v -> ${v + 1}")
                        m.migrate(db)
                        v++
                    }
                }
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        try {
            val upgraded = helper.writableDatabase
            upgraded.query("SELECT name, balance, accountType, allocationsJson FROM accounts").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Legacy Salary Bank", c.getString(0))
                assertEquals(25000.0, c.getDouble(1), 0.01)
                assertEquals("Bank", c.getString(2))
                assertEquals("[]", c.getString(3))
            }
            upgraded.query("SELECT amount, amountPaise, stableUid FROM transactions").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(450.0, c.getDouble(0), 0.01)
            }
            val tableNames = mutableSetOf<String>()
            upgraded.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                while (c.moveToNext()) tableNames.add(c.getString(0))
            }
            listOf(
                "accounts", "transactions", "budgets", "goals", "goal_contributions",
                "loans", "loan_payments", "persons", "categories", "custom_trackers",
                "tracker_entries", "tracker_folders", "subscriptions", "academic_events",
                "reminders", "khata_entries", "wishlist_cooldowns", "cashback_rewards",
                "price_per_use_items"
            ).forEach { expected ->
                assertTrue("Missing table $expected after v1->v10 migration", expected in tableNames)
            }
        } finally {
            helper.close()
            dbFile.delete()
        }
    }

    @Test
    fun databaseHealthCheckDetectsAndRepairsRealOrphansDuplicateUidsAndInvalidAmounts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TracmioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = FinanceRepository(db)
            val accId = repo.insertAccount(AccountEntity(name = "Main", accountType = "Bank", balance = 5000.0))
            repo.insertTransaction(
                TransactionEntity(stableUid = "uid-dup-1", accountId = accId, type = "EXPENSE", amount = 100.0, amountPaise = 10_000L, category = "Food & Chai")
            )

            // Inject real corruption: orphan tx + duplicate stableUid + negative amount
            val sqlite = db.openHelper.writableDatabase
            sqlite.execSQL(
                "INSERT INTO transactions (stableUid, accountId, type, amount, amountPaise, category, note, hashtags, timestamp, subcategory, paymentMethod, upiApp, warrantyDate, attachmentUri, allocationId) " +
                    "VALUES ('uid-dup-1', 99999, 'EXPENSE', -50.0, -5000, 'Other', '', '', 1700000000, '', 'Cash', '', '', '', '')"
            )
            sqlite.execSQL(
                "INSERT INTO transactions (stableUid, accountId, type, amount, amountPaise, category, note, hashtags, timestamp, subcategory, paymentMethod, upiApp, warrantyDate, attachmentUri, allocationId) " +
                    "VALUES ('uid-dup-1', $accId, 'EXPENSE', 200.0, 20000, 'Other', '', '', 1700000001, '', 'Cash', '', '', '', '')"
            )

            val unhealthy = repo.runDatabaseHealthCheck(1, 3, 0)
            assertFalse(unhealthy.isHealthy)
            assertEquals("ok", unhealthy.pragmaIntegrity.lowercase())
            assertEquals(1, unhealthy.orphanedTransactions)
            assertTrue(unhealthy.duplicateUidCount >= 1)
            assertTrue(unhealthy.invalidAmountCount >= 1)

            val repairedCount = repo.repairOrphanRecords()
            assertTrue(repairedCount >= 2)

            val repairedHealth = repo.runDatabaseHealthCheck(1, 2, 0)
            assertTrue(repairedHealth.isHealthy)
            assertEquals(0, repairedHealth.orphanedTransactions)
            assertEquals(0, repairedHealth.duplicateUidCount)
            assertEquals(0, repairedHealth.invalidAmountCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun appLockEnforcesBruteForceLockoutLegacyHashUpgradeAndKeystoreFailClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_applock_lockout_${System.currentTimeMillis()}", Context.MODE_PRIVATE)
        var nowMs = 1_000_000L

        val mgr = AppLockManager(
            context = context,
            securePrefsProvider = { prefs },
            timeProvider = { nowMs }
        )
        assertTrue(mgr.isSecureStorageAvailable)
        val recKey = mgr.enableLock("2580", biometricEnabled = true, "Pet?", "Bruno")
        assertTrue(recKey.startsWith("TRAC-"))
        assertEquals("", mgr.getState().recoveryKey)
        assertEquals("", mgr.getState().securityAnswerHash)

        // 5 failed attempts trigger 30s lockout
        repeat(5) { assertFalse(mgr.verifyPin("0000")) }
        assertTrue(mgr.getRemainingLockoutSeconds() > 0)
        assertFalse(mgr.verifyPin("2580")) // locked out even for correct PIN!

        // After 31s cooldown passes, correct PIN unlocks and resets counter
        nowMs += 31_000L
        assertEquals(0, mgr.getRemainingLockoutSeconds())
        assertTrue(mgr.verifyPin("2580"))
        assertEquals(0, mgr.getFailedAttempts())

        // Lockout resets cleanly on biometric success
        repeat(5) { assertFalse(mgr.verifyPin("1111")) }
        assertTrue(mgr.getRemainingLockoutSeconds() > 0)
        mgr.resetLockoutOnBiometricSuccess()
        assertEquals(0, mgr.getRemainingLockoutSeconds())
        assertTrue(mgr.verifyPin("2580"))

        // Legacy SHA-256 hash is accepted once and transparently upgraded to PBKDF2
        val legacySha = MessageDigest.getInstance("SHA-256")
            .digest("tracmio_pin_salt:4321".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        prefs.edit().putString("pin_hash", legacySha).commit()
        assertTrue(mgr.verifyPin("4321"))
        val upgradedHash = prefs.getString("pin_hash", "") ?: ""
        assertTrue(upgradedHash.startsWith("pbkdf2_sha256$"))

        // Fail-closed when Keystore cannot open
        val brokenMgr = AppLockManager(
            context = context,
            securePrefsProvider = { throw SecurityException("Keystore unavailable") }
        )
        assertFalse(brokenMgr.isSecureStorageAvailable)
        assertFalse(brokenMgr.getState().secureStorageAvailable)
        assertTrue(runCatching { brokenMgr.enableLock("1234", false, "Q", "A") }.isFailure)
    }

    @Test
    fun budgetUpsertMergesByCategoryAndMonthAndAccountDeleteCascades() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TracmioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = FinanceRepository(db)
            repo.upsertBudget("Food & Chai", 3000.0, "2026-11")
            repo.upsertBudget("Food & Chai", 4500.0, "2026-11")
            repo.upsertBudget("Food & Chai", 5000.0, "2026-12")
            val budgets = repo.budgets.first()
            assertEquals(2, budgets.size)
            assertEquals(4500.0, budgets.first { it.month == "2026-11" }.monthlyLimit, 0.01)

            val acc1Id = repo.insertAccount(AccountEntity(name = "Temp Bank", accountType = "Bank", balance = 1000.0))
            repo.insertTransaction(TransactionEntity(accountId = acc1Id, type = "EXPENSE", amount = 100.0, category = "Food & Chai"))
            val accEntity = repo.getAccountById(acc1Id)!!
            repo.deleteAccount(accEntity)
            assertEquals(0, repo.transactions.first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun legacyMigrationGuardImportsLegacyJsonAndRealSqliteDb() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TracmioDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val legacyDbFile = File(context.cacheDir, "legacy_fintrack_${System.currentTimeMillis()}.db")
        val fakeBadFile = File(context.cacheDir, "not_a_db_${System.currentTimeMillis()}.db")
        try {
            val repo = FinanceRepository(db)

            val legacyJson = """
                {
                  "accounts": [{"id": 10, "name": "Old Wallet", "accountType": "Wallet", "balance": 1200}],
                  "transactions": [{"accountId": 10, "type": "EXPENSE", "amount": 250, "category": "Food & Chai", "note": "Legacy snack", "stableUid": "legacy-uid-1"}]
                }
            """.trimIndent()
            val jsonReport = LegacyMigrationGuard.importLegacyJsonSnapshotToRepo(legacyJson, repo)
            assertEquals(2, jsonReport.insertedCount)

            SQLiteDatabase.openOrCreateDatabase(legacyDbFile, null).use { sqlite ->
                sqlite.execSQL("CREATE TABLE accounts (id INTEGER PRIMARY KEY, name TEXT, accountType TEXT, balance REAL)")
                sqlite.execSQL("INSERT INTO accounts (id, name, accountType, balance) VALUES (20, 'FinTrack Savings', 'Bank', 9500.0)")
                sqlite.execSQL("CREATE TABLE transactions (id INTEGER PRIMARY KEY, stableUid TEXT, accountId INTEGER, type TEXT, amount REAL, category TEXT, note TEXT, timestamp INTEGER)")
                sqlite.execSQL("INSERT INTO transactions (id, stableUid, accountId, type, amount, category, note, timestamp) VALUES (1, 'sqlite-uid-1', 20, 'INCOME', 1500.0, 'Salary', 'Legacy payroll', 1700000000)")
                sqlite.execSQL("CREATE TABLE budgets (id INTEGER PRIMARY KEY, category TEXT, monthlyLimit REAL, month TEXT)")
                sqlite.execSQL("INSERT INTO budgets (id, category, monthlyLimit, month) VALUES (1, 'Rent & Housing', 12000.0, '2026-11')")
            }
            val dbReport = LegacyMigrationGuard.importLegacySqliteFile(legacyDbFile, repo)
            assertEquals(3, dbReport.insertedCount)
            assertEquals(2, repo.accounts.first().size)
            assertEquals(2, repo.transactions.first().size)
            assertEquals(1, repo.budgets.first().size)

            fakeBadFile.writeText("NOT A SQLITE DATABASE")
            assertTrue(runCatching { LegacyMigrationGuard.importLegacySqliteFile(fakeBadFile, repo) }.isFailure)
        } finally {
            db.close()
            legacyDbFile.delete()
            fakeBadFile.delete()
        }
    }

    @Test
    fun pdfStatementPlanPaginatesAllTransactionsAndCsvXlsxNeutralizeFormulas() {
        val acc = AccountEntity(id = 1L, name = "Main Bank", accountType = "Bank", balance = 50000.0)
        val txs = (1..65).map { idx ->
            TransactionEntity(
                id = idx.toLong(),
                accountId = 1L,
                type = if (idx % 5 == 0) "INCOME" else "EXPENSE",
                amount = 100.0 + idx,
                category = "Category $idx",
                paymentMethod = "UPI",
                upiApp = "GPay",
                note = if (idx == 1) "=CMD|' /C calc'!A0" else "Note item $idx",
                timestamp = 1_700_000_000_000L + idx * 60_000L
            )
        }

        val plan = NativeStatementPdf.buildStatementPlan(listOf(acc), txs, "₹", 1_700_000_000_000L)
        assertTrue("Expected multi-page plan for 65 transactions", plan.pages.size >= 3)
        val flattenedLines = plan.pages.flatMap { it.lines.map { line -> line.text } }.joinToString("\n")
        assertTrue(flattenedLines.contains("1."))
        assertTrue(flattenedLines.contains("65."))
        plan.pages.forEachIndexed { i, page ->
            assertTrue(page.footer.contains("Page ${i + 1} of ${plan.pages.size}"))
        }

        val receiptPlan = NativeStatementPdf.buildReceiptPlan(txs.first(), "Main Bank", "₹")
        assertEquals(1, receiptPlan.pages.size)
        assertEquals("Tracmio Offline Vault (v16.13) · Page 1 of 1", receiptPlan.pages.first().footer)

        val csv = CsvParser.exportTransactionsToCsv(txs)
        assertTrue(csv.contains("'=CMD|' /C calc'!A0"))
        assertFalse(csv.contains("\"=CMD"))

        val xlsxBytes = XlsxExporter.createWorkbookBytes(listOf(acc), txs)
        var sheet1Xml = ""
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    sheet1Xml = zis.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        assertTrue(sheet1Xml.contains("Date"))
        assertTrue(sheet1Xml.contains("Account"))
        assertTrue(sheet1Xml.contains("Main Bank"))
        assertTrue(sheet1Xml.contains("&apos;=CMD"))
        assertFalse(sheet1Xml.contains("<t>=CMD"))
    }
}
