package com.tracmio.app.data.services

import android.util.Base64
import com.tracmio.app.data.MoneyPaise
import com.tracmio.app.data.NativeLedgerCalculator
import com.tracmio.app.data.NativeTracmioUiState
import com.tracmio.app.data.local.CategoryEntity
import com.tracmio.app.data.security.TracmioSecurityEngine
import com.tracmio.app.ui.nativeapp.LiquidGlassSettings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NativeBackupService {
    const val BACKUP_FORMAT_VERSION = 2
    private const val MAX_SINGLE_ATTACHMENT_BYTES = 2 * 1024 * 1024L
    private const val MAX_TOTAL_ATTACHMENT_BYTES = 12 * 1024 * 1024L
    private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun buildFullBackupJson(
        state: NativeTracmioUiState,
        settings: LiquidGlassSettings,
        appFilesDir: File? = null,
        categories: List<CategoryEntity> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("app", "Tracmio")
        root.put("backupFormatVersion", BACKUP_FORMAT_VERSION)
        root.put("versionName", "16.13")
        root.put("versionCode", 30)
        root.put("exportedAtMillis", System.currentTimeMillis())

        val prefsObj = JSONObject().apply {
            put("userName", settings.userName)
            put("currencySymbol", settings.currencySymbol)
            put("themePreset", settings.themePreset)
            put("glassUiStyle", settings.glassUiStyle)
            put("wallpaperPreset", settings.wallpaperPreset)
            put("backgroundVisibility", settings.backgroundVisibility.toDouble())
            put("wallpaperBlurDp", settings.wallpaperBlurDp.toDouble())
            put("glowIntensity", settings.glowIntensity.toDouble())
            put("uiScale", settings.uiScale.toDouble())
            put("deviceMotionEnabled", settings.deviceMotionEnabled)
            put("animationStyle", settings.animationStyle)
            put("customPrimaryHex", settings.customPrimaryHex)
            put("customSecondaryHex", settings.customSecondaryHex)
            put("roundUpEnabled", settings.roundUpEnabled)
            put("customWallpaperPath", settings.customWallpaperPath ?: JSONObject.NULL)
            put("dailyReminderEnabled", state.dailyReminderEnabled)
            put("monthlyBackupEnabled", state.monthlyBackupEnabled)
        }
        root.put("appPreferences", prefsObj)

        val accountsArr = JSONArray()
        state.accounts.forEach { acc ->
            accountsArr.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.name)
                    put("accountType", acc.accountType)
                    put("type", acc.type)
                    put("balance", if (acc.balance != 0.0) acc.balance else MoneyPaise.toRupees(acc.balancePaise))
                    put("balancePaise", acc.balancePaise)
                    put("creditLimit", acc.creditLimit)
                    put("creditLimitPaise", acc.creditLimitPaise)
                    put("statementDay", acc.statementDay)
                    put("dueDay", acc.dueDay)
                    put("allocationsJson", acc.allocationsJson)
                    put("allocationsCount", NativeLedgerCalculator.parseAllocations(acc.allocationsJson).size)
                    put("accountNumber", acc.accountNumber)
                    put("isArchived", acc.isArchived)
                    put("colorHex", acc.colorHex)
                    put("createdAt", acc.createdAt)
                    put("updatedAt", acc.updatedAt)
                }
            )
        }
        root.put("accounts", accountsArr)

        val txArr = JSONArray()
        state.transactions.forEach { tx ->
            txArr.put(
                JSONObject().apply {
                    put("id", tx.id)
                    put("stableUid", tx.stableUid)
                    put("accountId", tx.accountId)
                    put("type", tx.type)
                    put("amount", tx.amount)
                    put("amountPaise", tx.amountPaise)
                    put("category", tx.category)
                    put("subcategory", tx.subcategory)
                    put("paymentMethod", tx.paymentMethod)
                    put("upiApp", tx.upiApp)
                    put("note", tx.note)
                    put("hashtags", tx.hashtags)
                    put("warrantyDate", tx.warrantyDate)
                    put("attachmentUri", tx.attachmentUri)
                    put("allocationId", tx.allocationId)
                    put("transferAccountId", tx.transferAccountId ?: JSONObject.NULL)
                    put("transferId", tx.transferId ?: JSONObject.NULL)
                    put("personId", tx.personId ?: JSONObject.NULL)
                    put("timestamp", tx.timestamp)
                }
            )
        }
        root.put("transactions", txArr)

        val budgetsArr = JSONArray()
        state.budgets.forEach { b ->
            budgetsArr.put(JSONObject().apply {
                put("id", b.id)
                put("category", b.category)
                put("monthlyLimit", if (b.monthlyLimit > 0.0) b.monthlyLimit else MoneyPaise.toRupees(b.limitPaise))
                put("limitPaise", b.limitPaise)
                put("month", b.month)
                put("createdAt", b.createdAt)
            })
        }
        root.put("budgets", budgetsArr)

        val goalsArr = JSONArray()
        state.goals.forEach { g ->
            goalsArr.put(JSONObject().apply {
                put("id", g.id)
                put("title", g.title.ifBlank { g.name })
                put("targetAmount", if (g.targetAmount > 0.0) g.targetAmount else MoneyPaise.toRupees(g.targetPaise))
                put("targetPaise", g.targetPaise)
                put("currentAmount", if (g.currentAmount > 0.0) g.currentAmount else MoneyPaise.toRupees(g.currentPaise))
                put("currentPaise", g.currentPaise)
                put("deadline", g.deadline)
                put("icon", g.icon)
                put("colorHex", g.colorHex)
                put("createdAt", g.createdAt)
            })
        }
        root.put("goals", goalsArr)

        val goalContribArr = JSONArray()
        state.goalContributions.forEach { c ->
            goalContribArr.put(JSONObject().apply {
                put("id", c.id)
                put("goalId", c.goalId)
                put("amount", c.amount)
                put("amountPaise", c.amountPaise)
                put("note", c.note)
                put("timestamp", c.timestamp)
            })
        }
        root.put("goalContributions", goalContribArr)

        val loansArr = JSONArray()
        state.loans.forEach { l ->
            loansArr.put(JSONObject().apply {
                put("id", l.id)
                put("name", l.name.ifBlank { l.lender })
                put("principal", if (l.principal > 0.0) l.principal else MoneyPaise.toRupees(l.principalPaise))
                put("principalPaise", l.principalPaise)
                put("interestRate", l.interestRate)
                put("tenureMonths", l.tenureMonths)
                put("monthlyPayment", if (l.monthlyPayment > 0.0) l.monthlyPayment else MoneyPaise.toRupees(l.emiPaise))
                put("emiPaise", l.emiPaise)
                put("startDateMillis", l.startDateMillis)
                put("isClosed", l.isClosed)
            })
        }
        root.put("loans", loansArr)

        val loanPayArr = JSONArray()
        state.loanPayments.forEach { lp ->
            loanPayArr.put(JSONObject().apply {
                put("id", lp.id)
                put("loanId", lp.loanId)
                put("amount", if (lp.amount > 0.0) lp.amount else MoneyPaise.toRupees(lp.amountPaise))
                put("amountPaise", lp.amountPaise)
                put("note", lp.note)
                put("timestamp", lp.timestamp)
            })
        }
        root.put("loanPayments", loanPayArr)

        val personsArr = JSONArray()
        state.persons.forEach { p ->
            personsArr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("phone", p.phone)
                put("upiId", p.upiId)
                put("netBalance", p.netBalance)
                put("netBalancePaise", p.netBalancePaise)
                put("notes", p.notes)
                put("createdAt", p.createdAt)
            })
        }
        root.put("persons", personsArr)

        val categoriesArr = JSONArray()
        categories.forEach { cat ->
            categoriesArr.put(JSONObject().apply {
                put("id", cat.id)
                put("name", cat.name)
                put("type", cat.type)
                put("icon", cat.icon)
                put("colorHex", cat.colorHex)
            })
        }
        root.put("categories", categoriesArr)

        val folderArr = JSONArray()
        state.trackerFolders.forEach { f ->
            folderArr.put(JSONObject().apply {
                put("id", f.id)
                put("folderName", f.folderName)
                put("icon", f.icon)
                put("colorHex", f.colorHex)
                put("sortOrder", f.sortOrder)
            })
        }
        root.put("trackerFolders", folderArr)

        val trackersArr = JSONArray()
        state.customTrackers.forEach { t ->
            trackersArr.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("trackerType", t.trackerType)
                put("targetValue", t.targetValue)
                put("currentValue", t.currentValue)
                put("unit", t.unit)
                put("icon", t.icon)
                put("colorHex", t.colorHex)
                put("folderId", t.folderId)
                put("isPinned", t.isPinned)
                put("updatedAt", t.updatedAt)
            })
        }
        root.put("customTrackers", trackersArr)

        val trackerEntriesArr = JSONArray()
        state.trackerEntries.forEach { e ->
            trackerEntriesArr.put(JSONObject().apply {
                put("id", e.id)
                put("trackerId", e.trackerId)
                put("deltaValue", e.deltaValue)
                put("note", e.note)
                put("attachmentPath", e.attachmentPath)
                put("timestamp", e.timestamp)
            })
        }
        root.put("trackerEntries", trackerEntriesArr)

        val subsArr = JSONArray()
        state.subscriptions.forEach { s ->
            subsArr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("amount", s.amount)
                put("amountPaise", s.amountPaise)
                put("billingCycle", s.billingCycle)
                put("nextDueDateMillis", s.nextDueDateMillis)
                put("category", s.category)
                put("isSimOrWifi", s.isSimOrWifi)
                put("validityDays", s.validityDays)
                put("isActive", s.isActive)
            })
        }
        root.put("subscriptions", subsArr)

        val acadArr = JSONArray()
        state.academicEvents.forEach { ev ->
            acadArr.put(JSONObject().apply {
                put("id", ev.id)
                put("title", ev.title)
                put("feeAmount", ev.feeAmount)
                put("paidAmount", ev.paidAmount)
                put("dueDate", ev.dueDate)
                put("semester", ev.semester)
                put("isPaid", ev.isPaid)
            })
        }
        root.put("academicEvents", acadArr)

        val remArr = JSONArray()
        state.reminders.forEach { r ->
            remArr.put(JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("amount", r.amount)
                put("dueDate", r.dueDate)
                put("timeHour", r.timeHour)
                put("timeMinute", r.timeMinute)
                put("isCompleted", r.isCompleted)
                put("isRecurring", r.isRecurring)
            })
        }
        root.put("reminders", remArr)

        val khataArr = JSONArray()
        state.khataEntries.forEach { k ->
            khataArr.put(JSONObject().apply {
                put("id", k.id)
                put("shopOrFriend", k.shopOrFriend)
                put("itemDescription", k.itemDescription)
                put("amount", k.amount)
                put("isSettled", k.isSettled)
                put("timestamp", k.timestamp)
            })
        }
        root.put("khataEntries", khataArr)

        val wishlistArr = JSONArray()
        state.wishlistCooldowns.forEach { w ->
            wishlistArr.put(JSONObject().apply {
                put("id", w.id)
                put("itemName", w.itemName)
                put("price", w.price)
                put("createdAtMillis", w.createdAtMillis)
                put("status", w.status)
            })
        }
        root.put("wishlistCooldowns", wishlistArr)

        val cashbackArr = JSONArray()
        state.cashbackRewards.forEach { c ->
            cashbackArr.put(JSONObject().apply {
                put("id", c.id)
                put("sourceApp", c.sourceApp)
                put("amount", c.amount)
                put("note", c.note)
                put("timestamp", c.timestamp)
            })
        }
        root.put("cashbackRewards", cashbackArr)

        val ppuArr = JSONArray()
        state.pricePerUseItems.forEach { p ->
            ppuArr.put(JSONObject().apply {
                put("id", p.id)
                put("itemName", p.itemName)
                put("purchasePrice", p.purchasePrice)
                put("usageCount", p.usageCount)
                put("targetUses", p.targetUses)
                put("createdAtMillis", p.createdAtMillis)
            })
        }
        root.put("pricePerUseItems", ppuArr)

        val chipsArr = JSONArray()
        state.quickSpendChips.forEach { c ->
            chipsArr.put(JSONObject().apply {
                put("id", c.id)
                put("emoji", c.emoji)
                put("title", c.title)
                put("amount", c.amount)
                put("category", c.category)
            })
        }
        root.put("quickSpendChips", chipsArr)

        val refundsArr = JSONArray()
        state.pendingRefunds.forEach { r ->
            refundsArr.put(JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("source", r.source)
                put("amount", r.amount)
                put("expectedDate", r.expectedDate)
                put("isReceived", r.isReceived)
            })
        }
        root.put("pendingRefunds", refundsArr)

        val pinnedArr = JSONArray()
        state.pinnedToolIds.forEach { pinnedArr.put(it) }
        root.put("pinnedToolIds", pinnedArr)

        val attachmentsArr = JSONArray()
        if (appFilesDir != null) {
            val rootCanon = appFilesDir.canonicalPath
            val candidatePaths = linkedSetOf<String>().apply {
                state.transactions.mapNotNull { it.attachmentUri.takeIf { p -> p.isNotBlank() } }.forEach(::add)
                state.trackerEntries.mapNotNull { it.attachmentPath.takeIf { p -> p.isNotBlank() } }.forEach(::add)
                settings.customWallpaperPath?.takeIf { it.isNotBlank() }?.let(::add)
            }
            var totalBytes = 0L
            for (path in candidatePaths) {
                val f = File(path)
                if (!f.exists() || !f.isFile) continue
                val canon = runCatching { f.canonicalPath }.getOrNull() ?: continue
                if (!canon.startsWith(rootCanon + File.separator)) continue
                val ext = f.extension.lowercase()
                if (ext !in ALLOWED_EXTENSIONS) continue
                val len = f.length()
                if (len <= 0L || len > MAX_SINGLE_ATTACHMENT_BYTES) continue
                if (totalBytes + len > MAX_TOTAL_ATTACHMENT_BYTES) break
                val bytes = runCatching { f.readBytes() }.getOrNull() ?: continue
                totalBytes += bytes.size
                val mime = if (ext == "png") "image/png" else if (ext == "webp") "image/webp" else "image/jpeg"
                attachmentsArr.put(
                    JSONObject().apply {
                        put("originalPath", path)
                        put("relativeName", f.name)
                        put("mimeType", mime)
                        put("dataB64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    }
                )
            }
        }
        root.put("attachments", attachmentsArr)

        return root.toString(2)
    }

    fun exportEncryptedBackupJson(
        state: NativeTracmioUiState,
        settings: LiquidGlassSettings,
        password: CharArray,
        appFilesDir: File? = null,
        categories: List<CategoryEntity> = emptyList()
    ): String {
        val plain = buildFullBackupJson(state, settings, appFilesDir, categories)
        return TracmioSecurityEngine.encryptBackup(plain, password)
    }
}
