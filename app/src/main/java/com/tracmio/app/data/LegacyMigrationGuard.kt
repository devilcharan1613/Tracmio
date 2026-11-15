package com.tracmio.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.tracmio.app.data.local.AccountEntity
import com.tracmio.app.data.local.TransactionEntity
import com.tracmio.app.data.repository.FinanceRepository
import com.tracmio.app.data.security.TracmioSecurityEngine
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object LegacyMigrationGuard {
    private const val MIGRATION_PREFS = "tracmio_migration_guard"
    private const val KEY_MIGRATED_V16_13 = "migrated_v16_13"
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private const val MAX_LEGACY_DB_BYTES = 50L * 1024L * 1024L

    suspend fun runOneTimeLegacyMigrationIfNeeded(
        context: Context,
        repository: FinanceRepository
    ): Boolean {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED_V16_13, false)) return false

        val legacyDbFile = context.getDatabasePath("fintrack_database")
        if (!legacyDbFile.exists()) {
            prefs.edit().putBoolean(KEY_MIGRATED_V16_13, true).commit()
            return true
        }

        val backupCopy = File(context.filesDir, "fintrack_database_legacy.bak")
        runCatching { legacyDbFile.copyTo(backupCopy, overwrite = true) }

        val importResult = runCatching {
            importLegacySqliteFile(legacyDbFile, repository)
        }
        if (importResult.isSuccess) {
            prefs.edit().putBoolean(KEY_MIGRATED_V16_13, true).commit()
            return true
        }
        return false
    }

    suspend fun importLegacyJsonSnapshotToRepo(
        json: String,
        repository: FinanceRepository
    ): FinanceRepository.RestoreReport {
        val obj = JSONObject(json)
        val hasAccountsOrTx = obj.optJSONArray("accounts") != null || obj.optJSONArray("transactions") != null
        require(hasAccountsOrTx) { "Legacy JSON file has no accounts or transactions arrays." }

        val normalized = JSONObject().apply {
            put("app", "Tracmio-Legacy-JSON")
            put("backupFormatVersion", 1)
            put("accounts", normalizeLegacyAccounts(obj.optJSONArray("accounts")))
            put("transactions", normalizeLegacyTransactions(obj.optJSONArray("transactions")))
            obj.optJSONArray("budgets")?.let { put("budgets", it) }
            obj.optJSONArray("goals")?.let { put("goals", it) }
            obj.optJSONArray("loans")?.let { put("loans", it) }
            obj.optJSONArray("persons")?.let { put("persons", it) }
        }
        return repository.restoreBackupJson(
            jsonText = normalized.toString(),
            mode = FinanceRepository.BackupRestoreMode.SAFE_MERGE
        )
    }

    suspend fun importLegacySqliteDbUri(
        context: Context,
        uri: Uri,
        repository: FinanceRepository
    ): FinanceRepository.RestoreReport {
        val tempFile = File(context.cacheDir, "legacy_import_${System.currentTimeMillis()}.db")
        try {
            var copied = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        copied += n
                        require(copied <= MAX_LEGACY_DB_BYTES) { "Legacy database exceeds 50 MB limit." }
                        output.write(buf, 0, n)
                    }
                }
            } ?: throw IllegalArgumentException("Unable to read selected legacy database file.")

            return importLegacySqliteFile(tempFile, repository)
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    suspend fun importLegacySqliteFile(
        dbFile: File,
        repository: FinanceRepository
    ): FinanceRepository.RestoreReport {
        require(dbFile.exists() && dbFile.length() >= 100L) { "Selected file is not a valid SQLite database." }
        val header = dbFile.inputStream().use { ins ->
            ByteArray(SQLITE_MAGIC.size).also { buf ->
                val read = ins.read(buf)
                require(read == SQLITE_MAGIC.size) { "Unreadable SQLite header." }
            }
        }
        require(header.contentEquals(SQLITE_MAGIC)) {
            "File is not an unencrypted SQLite 3 database (SQLite magic header mismatch)."
        }

        val jsonPayload = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { sqlite ->
            val tables = mutableSetOf<String>()
            sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) tables.add(c.getString(0).lowercase())
            }
            require("accounts" in tables || "transactions" in tables) {
                "Legacy database does not contain accounts or transactions tables."
            }

            val root = JSONObject()
            root.put("app", "Tracmio-Legacy-SQLite")
            root.put("backupFormatVersion", 1)

            if ("accounts" in tables) {
                val arr = JSONArray()
                sqlite.rawQuery("SELECT * FROM accounts", null).use { c ->
                    while (c.moveToNext()) {
                        arr.put(JSONObject().apply {
                            c.getColumnIndex("id").takeIf { it >= 0 }?.let { put("id", c.getLong(it)) }
                            val name = c.getColumnIndex("name").takeIf { it >= 0 }?.let { c.getString(it) } ?: "Legacy Account"
                            val accType = c.getColumnIndex("accountType").takeIf { it >= 0 }?.let { c.getString(it) }
                                ?: c.getColumnIndex("type").takeIf { it >= 0 }?.let { c.getString(it) }
                                ?: "Bank"
                            val bal = c.getColumnIndex("balance").takeIf { it >= 0 }?.let { c.getDouble(it) } ?: 0.0
                            put("name", name)
                            put("accountType", accType)
                            put("balance", bal)
                            c.getColumnIndex("creditLimit").takeIf { it >= 0 }?.let { put("creditLimit", c.getDouble(it)) }
                        })
                    }
                }
                root.put("accounts", arr)
            }

            if ("transactions" in tables) {
                val arr = JSONArray()
                sqlite.rawQuery("SELECT * FROM transactions", null).use { c ->
                    while (c.moveToNext()) {
                        arr.put(JSONObject().apply {
                            c.getColumnIndex("stableUid").takeIf { it >= 0 }?.let { put("stableUid", c.getString(it)) }
                            c.getColumnIndex("accountId").takeIf { it >= 0 }?.let { put("accountId", c.getLong(it)) }
                            val txType = c.getColumnIndex("type").takeIf { it >= 0 }?.let { c.getString(it) } ?: "EXPENSE"
                            val amt = c.getColumnIndex("amount").takeIf { it >= 0 }?.let { c.getDouble(it) } ?: 0.0
                            val cat = c.getColumnIndex("category").takeIf { it >= 0 }?.let { c.getString(it) } ?: "Other"
                            val note = c.getColumnIndex("note").takeIf { it >= 0 }?.let { c.getString(it) } ?: ""
                            val ts = c.getColumnIndex("timestamp").takeIf { it >= 0 }?.let { c.getLong(it) } ?: System.currentTimeMillis()
                            put("type", txType)
                            put("amount", amt)
                            put("category", cat)
                            put("note", note)
                            put("timestamp", ts)
                            c.getColumnIndex("paymentMethod").takeIf { it >= 0 }?.let { put("paymentMethod", c.getString(it)) }
                            c.getColumnIndex("upiApp").takeIf { it >= 0 }?.let { put("upiApp", c.getString(it)) }
                            c.getColumnIndex("hashtags").takeIf { it >= 0 }?.let { put("hashtags", c.getString(it)) }
                        })
                    }
                }
                root.put("transactions", arr)
            }

            if ("budgets" in tables) {
                val arr = JSONArray()
                sqlite.rawQuery("SELECT * FROM budgets", null).use { c ->
                    while (c.moveToNext()) {
                        arr.put(JSONObject().apply {
                            c.getColumnIndex("category").takeIf { it >= 0 }?.let { put("category", c.getString(it)) }
                            val lim = c.getColumnIndex("monthlyLimit").takeIf { it >= 0 }?.let { c.getDouble(it) }
                                ?: c.getColumnIndex("limitAmount").takeIf { it >= 0 }?.let { c.getDouble(it) }
                                ?: 0.0
                            put("monthlyLimit", lim)
                            c.getColumnIndex("month").takeIf { it >= 0 }?.let { put("month", c.getString(it)) }
                        })
                    }
                }
                root.put("budgets", arr)
            }

            root.toString()
        }

        return repository.restoreBackupJson(
            jsonText = jsonPayload,
            mode = FinanceRepository.BackupRestoreMode.SAFE_MERGE
        )
    }

    private fun normalizeLegacyAccounts(arr: JSONArray?): JSONArray {
        val out = JSONArray()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            out.put(JSONObject().apply {
                put("id", a.optLong("id", (i + 1).toLong()))
                put("name", TracmioSecurityEngine.sanitizeInput(a.optString("name", "Legacy Account ${i + 1}"), 80))
                put("accountType", TracmioSecurityEngine.sanitizeInput(a.optString("accountType", a.optString("type", "Bank")), 40))
                put("balance", a.optDouble("balance", 0.0))
                put("creditLimit", a.optDouble("creditLimit", 0.0))
            })
        }
        return out
    }

    private fun normalizeLegacyTransactions(arr: JSONArray?): JSONArray {
        val out = JSONArray()
        if (arr == null) return out
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            out.put(JSONObject().apply {
                if (t.has("stableUid")) put("stableUid", t.optString("stableUid", ""))
                put("accountId", t.optLong("accountId", 1L))
                put("type", t.optString("type", "EXPENSE").uppercase())
                put("amount", t.optDouble("amount", 0.0))
                put("category", TracmioSecurityEngine.sanitizeInput(t.optString("category", "Other"), 60))
                put("note", TracmioSecurityEngine.sanitizeInput(t.optString("note", ""), 200))
                put("timestamp", t.optLong("timestamp", System.currentTimeMillis()))
            })
        }
        return out
    }

    fun parseLegacyJsonSnapshot(json: String): Pair<List<AccountEntity>, List<TransactionEntity>> {
        val obj = JSONObject(json)
        val accounts = mutableListOf<AccountEntity>()
        val transactions = mutableListOf<TransactionEntity>()
        val accArr = obj.optJSONArray("accounts")
        if (accArr != null) {
            for (i in 0 until accArr.length()) {
                val a = accArr.getJSONObject(i)
                val bal = a.optDouble("balance", 0.0)
                accounts.add(
                    AccountEntity(
                        id = a.optLong("id", (i + 1).toLong()),
                        name = a.optString("name", "Account ${i + 1}"),
                        accountType = a.optString("accountType", a.optString("type", "Bank")),
                        balance = bal,
                        balancePaise = MoneyPaise.toPaise(bal)
                    )
                )
            }
        }
        val txArr = obj.optJSONArray("transactions")
        if (txArr != null) {
            for (i in 0 until txArr.length()) {
                val t = txArr.getJSONObject(i)
                val amt = t.optDouble("amount", 0.0)
                transactions.add(
                    TransactionEntity(
                        accountId = t.optLong("accountId", 1L),
                        type = t.optString("type", "EXPENSE"),
                        amount = amt,
                        amountPaise = MoneyPaise.toPaise(amt),
                        category = t.optString("category", "Other"),
                        note = t.optString("note", "")
                    )
                )
            }
        }
        return accounts to transactions
    }
}
