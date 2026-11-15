package com.tracmio.app.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

class VaultSecurityException(message: String, cause: Throwable? = null) : SecurityException(message, cause)

sealed class VaultOpenResult {
    data class Success(val database: TracmioDatabase) : VaultOpenResult()
    data class Failure(val title: String, val detail: String) : VaultOpenResult()
}

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        GoalEntity::class,
        GoalContributionEntity::class,
        LoanEntity::class,
        LoanPaymentEntity::class,
        PersonEntity::class,
        CategoryEntity::class,
        CustomTrackerEntity::class,
        CustomTrackerEntryEntity::class,
        TrackerFolderEntity::class,
        SubscriptionEntity::class,
        AcademicEventEntity::class,
        ReminderItemEntity::class,
        KhataEntryEntity::class,
        WishlistCooldownEntity::class,
        CashbackRewardEntity::class,
        PricePerUseEntity::class
    ],
    version = 10,
    exportSchema = true
)
abstract class TracmioDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun financeDao(): FinanceDao
    abstract fun customTrackerDao(): CustomTrackerDao
    abstract fun featureDao(): FeatureDao

    companion object {
        const val DATABASE_NAME = "tracmio_database"
        private const val SECURE_KEY_PREFS = "tracmio_db_key_secure"
        private const val LEGACY_KEY_PREFS = "tracmio_db_key_store"
        private const val KEY_PASSPHRASE_B64 = "db_passphrase_b64"

        @Volatile
        private var INSTANCE: TracmioDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureCoreSchemaTablesAndColumns(db)
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10
        )

        fun ensureCoreSchemaTablesAndColumns(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    accountType TEXT NOT NULL,
                    type TEXT NOT NULL,
                    balance REAL NOT NULL,
                    balancePaise INTEGER NOT NULL,
                    creditLimit REAL NOT NULL,
                    creditLimitPaise INTEGER NOT NULL,
                    statementDay INTEGER NOT NULL,
                    dueDay INTEGER NOT NULL,
                    allocationsJson TEXT NOT NULL,
                    accountNumber TEXT NOT NULL,
                    isArchived INTEGER NOT NULL,
                    colorHex TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            addMissingColumns(
                db, "accounts",
                listOf(
                    "accountType" to "TEXT NOT NULL DEFAULT 'Bank'",
                    "type" to "TEXT NOT NULL DEFAULT 'Bank'",
                    "balance" to "REAL NOT NULL DEFAULT 0.0",
                    "balancePaise" to "INTEGER NOT NULL DEFAULT 0",
                    "creditLimit" to "REAL NOT NULL DEFAULT 0.0",
                    "creditLimitPaise" to "INTEGER NOT NULL DEFAULT 0",
                    "statementDay" to "INTEGER NOT NULL DEFAULT 1",
                    "dueDay" to "INTEGER NOT NULL DEFAULT 15",
                    "allocationsJson" to "TEXT NOT NULL DEFAULT '[]'",
                    "accountNumber" to "TEXT NOT NULL DEFAULT ''",
                    "isArchived" to "INTEGER NOT NULL DEFAULT 0",
                    "colorHex" to "TEXT NOT NULL DEFAULT '#00F5D4'",
                    "createdAt" to "INTEGER NOT NULL DEFAULT 0",
                    "updatedAt" to "INTEGER NOT NULL DEFAULT 0"
                )
            )

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    stableUid TEXT NOT NULL,
                    accountId INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    amount REAL NOT NULL,
                    amountPaise INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    note TEXT NOT NULL,
                    hashtags TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    transferAccountId INTEGER,
                    transferId TEXT,
                    subcategory TEXT NOT NULL,
                    personId INTEGER,
                    paymentMethod TEXT NOT NULL,
                    upiApp TEXT NOT NULL,
                    warrantyDate TEXT NOT NULL,
                    attachmentUri TEXT NOT NULL,
                    allocationId TEXT NOT NULL
                )
            """.trimIndent())
            addMissingColumns(
                db, "transactions",
                listOf(
                    "stableUid" to "TEXT NOT NULL DEFAULT ''",
                    "amountPaise" to "INTEGER NOT NULL DEFAULT 0",
                    "category" to "TEXT NOT NULL DEFAULT 'Other'",
                    "note" to "TEXT NOT NULL DEFAULT ''",
                    "hashtags" to "TEXT NOT NULL DEFAULT ''",
                    "timestamp" to "INTEGER NOT NULL DEFAULT 0",
                    "transferAccountId" to "INTEGER",
                    "transferId" to "TEXT",
                    "subcategory" to "TEXT NOT NULL DEFAULT ''",
                    "personId" to "INTEGER",
                    "paymentMethod" to "TEXT NOT NULL DEFAULT 'Cash'",
                    "upiApp" to "TEXT NOT NULL DEFAULT ''",
                    "warrantyDate" to "TEXT NOT NULL DEFAULT ''",
                    "attachmentUri" to "TEXT NOT NULL DEFAULT ''",
                    "allocationId" to "TEXT NOT NULL DEFAULT ''"
                )
            )
            db.execSQL("UPDATE transactions SET stableUid = lower(hex(randomblob(16))) WHERE stableUid = ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type_timestamp ON transactions (type, timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_accountId ON transactions (accountId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_category ON transactions (category)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS budgets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    category TEXT NOT NULL,
                    monthlyLimit REAL NOT NULL,
                    limitPaise INTEGER NOT NULL,
                    month TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            addMissingColumns(
                db, "budgets",
                listOf(
                    "monthlyLimit" to "REAL NOT NULL DEFAULT 0.0",
                    "limitPaise" to "INTEGER NOT NULL DEFAULT 0",
                    "month" to "TEXT NOT NULL DEFAULT ''",
                    "createdAt" to "INTEGER NOT NULL DEFAULT 0"
                )
            )

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS goals (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    title TEXT NOT NULL,
                    targetAmount REAL NOT NULL,
                    targetPaise INTEGER NOT NULL,
                    currentAmount REAL NOT NULL,
                    currentPaise INTEGER NOT NULL,
                    deadline TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    colorHex TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            addMissingColumns(
                db, "goals",
                listOf(
                    "name" to "TEXT NOT NULL DEFAULT ''",
                    "title" to "TEXT NOT NULL DEFAULT ''",
                    "targetPaise" to "INTEGER NOT NULL DEFAULT 0",
                    "currentPaise" to "INTEGER NOT NULL DEFAULT 0",
                    "deadline" to "TEXT NOT NULL DEFAULT ''",
                    "icon" to "TEXT NOT NULL DEFAULT '🎯'",
                    "colorHex" to "TEXT NOT NULL DEFAULT '#00F5D4'",
                    "createdAt" to "INTEGER NOT NULL DEFAULT 0"
                )
            )

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS goal_contributions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    goalId INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    amountPaise INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())
            addMissingColumns(db, "goal_contributions", listOf("amountPaise" to "INTEGER NOT NULL DEFAULT 0", "note" to "TEXT NOT NULL DEFAULT ''"))

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS loans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    lender TEXT NOT NULL,
                    principal REAL NOT NULL,
                    principalPaise INTEGER NOT NULL,
                    interestRate REAL NOT NULL,
                    tenureMonths INTEGER NOT NULL,
                    monthlyPayment REAL NOT NULL,
                    emiPaise INTEGER NOT NULL,
                    startDateMillis INTEGER NOT NULL,
                    isClosed INTEGER NOT NULL
                )
            """.trimIndent())
            addMissingColumns(
                db, "loans",
                listOf(
                    "name" to "TEXT NOT NULL DEFAULT ''",
                    "lender" to "TEXT NOT NULL DEFAULT ''",
                    "principalPaise" to "INTEGER NOT NULL DEFAULT 0",
                    "tenureMonths" to "INTEGER NOT NULL DEFAULT 12",
                    "emiPaise" to "INTEGER NOT NULL DEFAULT 0",
                    "startDateMillis" to "INTEGER NOT NULL DEFAULT 0",
                    "isClosed" to "INTEGER NOT NULL DEFAULT 0"
                )
            )

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS loan_payments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    loanId INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    amountPaise INTEGER NOT NULL,
                    note TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())
            addMissingColumns(db, "loan_payments", listOf("amountPaise" to "INTEGER NOT NULL DEFAULT 0", "note" to "TEXT NOT NULL DEFAULT ''"))

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS persons (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    phone TEXT NOT NULL,
                    upiId TEXT NOT NULL,
                    netBalance REAL NOT NULL,
                    netBalancePaise INTEGER NOT NULL,
                    notes TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            addMissingColumns(
                db, "persons",
                listOf(
                    "phone" to "TEXT NOT NULL DEFAULT ''",
                    "upiId" to "TEXT NOT NULL DEFAULT ''",
                    "netBalancePaise" to "INTEGER NOT NULL DEFAULT 0",
                    "notes" to "TEXT NOT NULL DEFAULT ''",
                    "createdAt" to "INTEGER NOT NULL DEFAULT 0"
                )
            )

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    colorHex TEXT NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS custom_trackers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    trackerType TEXT NOT NULL,
                    targetValue REAL NOT NULL,
                    currentValue REAL NOT NULL,
                    unit TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    colorHex TEXT NOT NULL,
                    folderId INTEGER NOT NULL,
                    isPinned INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tracker_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    trackerId INTEGER NOT NULL,
                    deltaValue REAL NOT NULL,
                    note TEXT NOT NULL,
                    attachmentPath TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tracker_folders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    folderName TEXT NOT NULL,
                    icon TEXT NOT NULL,
                    colorHex TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS subscriptions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    amount REAL NOT NULL,
                    amountPaise INTEGER NOT NULL,
                    billingCycle TEXT NOT NULL,
                    nextDueDateMillis INTEGER NOT NULL,
                    category TEXT NOT NULL,
                    isSimOrWifi INTEGER NOT NULL,
                    validityDays INTEGER NOT NULL,
                    isActive INTEGER NOT NULL
                )
            """.trimIndent())
            addMissingColumns(db, "subscriptions", listOf("amountPaise" to "INTEGER NOT NULL DEFAULT 0"))

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS academic_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    feeAmount REAL NOT NULL,
                    paidAmount REAL NOT NULL,
                    dueDate TEXT NOT NULL,
                    semester TEXT NOT NULL,
                    isPaid INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS reminders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    amount REAL NOT NULL,
                    dueDate TEXT NOT NULL,
                    timeHour INTEGER NOT NULL,
                    timeMinute INTEGER NOT NULL,
                    isCompleted INTEGER NOT NULL,
                    isRecurring INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS khata_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    shopOrFriend TEXT NOT NULL,
                    itemDescription TEXT NOT NULL,
                    amount REAL NOT NULL,
                    isSettled INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS wishlist_cooldowns (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    itemName TEXT NOT NULL,
                    price REAL NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    status TEXT NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cashback_rewards (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceApp TEXT NOT NULL,
                    amount REAL NOT NULL,
                    note TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS price_per_use_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    itemName TEXT NOT NULL,
                    purchasePrice REAL NOT NULL,
                    usageCount INTEGER NOT NULL,
                    targetUses INTEGER NOT NULL,
                    createdAtMillis INTEGER NOT NULL
                )
            """.trimIndent())
        }

        private fun addMissingColumns(
            db: SupportSQLiteDatabase,
            tableName: String,
            columns: List<Pair<String, String>>
        ) {
            val existing = mutableSetOf<String>()
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) existing.add(cursor.getString(nameIndex))
                }
            }
            for ((colName, colDef) in columns) {
                if (colName !in existing) {
                    db.execSQL("ALTER TABLE $tableName ADD COLUMN $colName $colDef")
                }
            }
        }

        private fun openSecureKeyPrefs(context: Context): SharedPreferences {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            return androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                SECURE_KEY_PREFS,
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        internal fun resolveOrProvisionPassphrase(
            context: Context,
            securePrefs: SharedPreferences
        ): Pair<ByteArray, Boolean> {
            val existingSecure = securePrefs.getString(KEY_PASSPHRASE_B64, null)
            if (!existingSecure.isNullOrBlank()) {
                val decoded = Base64.decode(existingSecure, Base64.NO_WRAP)
                if (decoded.size >= 16) return decoded to false
            }

            val legacyPrefs = context.getSharedPreferences(LEGACY_KEY_PREFS, Context.MODE_PRIVATE)
            val legacyPass = legacyPrefs.getString(KEY_PASSPHRASE_B64, null)
            if (!legacyPass.isNullOrBlank()) {
                val decoded = Base64.decode(legacyPass, Base64.NO_WRAP)
                if (decoded.size >= 16) {
                    val committed = securePrefs.edit().putString(KEY_PASSPHRASE_B64, legacyPass).commit()
                    check(committed) { "Failed to persist migrated database key to Keystore-backed storage." }
                    return decoded to true
                }
            }

            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val encoded = Base64.encodeToString(raw, Base64.NO_WRAP)
            val committed = securePrefs.edit().putString(KEY_PASSPHRASE_B64, encoded).commit()
            check(committed) { "Failed to persist new database key to Keystore-backed storage." }
            return raw to false
        }

        fun openVaultOrFail(
            context: Context,
            nativeLoader: () -> Unit = { System.loadLibrary("sqlcipher") },
            encryptedPrefsProvider: ((Context) -> SharedPreferences)? = null,
            verifyOpenedDatabase: Boolean = true
        ): VaultOpenResult {
            INSTANCE?.let { return VaultOpenResult.Success(it) }
            return synchronized(this) {
                INSTANCE?.let { return VaultOpenResult.Success(it) }
                val appCtx = context.applicationContext

                val cipherError = runCatching { nativeLoader() }.exceptionOrNull()
                if (cipherError != null) {
                    return@synchronized VaultOpenResult.Failure(
                        title = "Encrypted Vault Engine Unavailable",
                        detail = "SQLCipher native encryption library failed to initialize (${cipherError.localizedMessage ?: "missing native binary"}). Tracmio refused to open the vault in unencrypted mode to protect your financial records."
                    )
                }

                val securePrefs = runCatching {
                    encryptedPrefsProvider?.invoke(appCtx) ?: openSecureKeyPrefs(appCtx)
                }.getOrElse { err ->
                    return@synchronized VaultOpenResult.Failure(
                        title = "Android Keystore Unavailable",
                        detail = "Could not unwrap encrypted vault key from Android Keystore (${err.localizedMessage ?: err.javaClass.simpleName}). Existing database files were left untouched. Retry unlock or restore from an encrypted backup."
                    )
                }

                val (passphrase, migratedFromLegacy) = runCatching {
                    resolveOrProvisionPassphrase(appCtx, securePrefs)
                }.getOrElse { err ->
                    return@synchronized VaultOpenResult.Failure(
                        title = "Vault Key Provisioning Failed",
                        detail = err.localizedMessage ?: "Unable to provision or migrate encrypted database key."
                    )
                }

                val db = runCatching {
                    val factory = SupportOpenHelperFactory(passphrase)
                    Room.databaseBuilder(appCtx, TracmioDatabase::class.java, DATABASE_NAME)
                        .addMigrations(*ALL_MIGRATIONS)
                        .openHelperFactory(factory)
                        .build()
                }.getOrElse { err ->
                    return@synchronized VaultOpenResult.Failure(
                        title = "Encrypted Database Build Failed",
                        detail = err.localizedMessage ?: "Unable to construct encrypted Room database."
                    )
                }

                if (verifyOpenedDatabase) {
                    val verifyError = runCatching {
                        db.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use { c ->
                            c.moveToFirst()
                        }
                    }.exceptionOrNull()
                    if (verifyError != null) {
                        runCatching { db.close() }
                        return@synchronized VaultOpenResult.Failure(
                            title = "Encrypted Vault Verification Failed",
                            detail = "Could not verify encrypted database handle (${verifyError.localizedMessage ?: verifyError.javaClass.simpleName}). Existing data was preserved without modification."
                        )
                    }
                }

                if (migratedFromLegacy) {
                    appCtx.getSharedPreferences(LEGACY_KEY_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .remove(KEY_PASSPHRASE_B64)
                        .commit()
                }

                INSTANCE = db
                VaultOpenResult.Success(db)
            }
        }

        fun getInstance(context: Context): TracmioDatabase {
            return when (val result = openVaultOrFail(context)) {
                is VaultOpenResult.Success -> result.database
                is VaultOpenResult.Failure -> throw VaultSecurityException("${result.title}: ${result.detail}")
            }
        }

        internal fun clearInstanceForTesting() {
            synchronized(this) {
                INSTANCE?.let { runCatching { it.close() } }
                INSTANCE = null
            }
        }
    }
}
