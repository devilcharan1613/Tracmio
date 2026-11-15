package com.tracmio.app.data

import com.tracmio.app.data.local.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvParser {
    private val FORMULA_TRIGGER_CHARS = setOf('=', '+', '-', '@', '\t', '\r')

    fun sanitizeFormulaCell(raw: String): String {
        val clean = raw.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        if (clean.isEmpty()) return ""
        val firstNonSpace = clean.firstOrNull { it != ' ' }
        return if (clean.first() in FORMULA_TRIGGER_CHARS || (firstNonSpace != null && firstNonSpace in FORMULA_TRIGGER_CHARS)) {
            "'$clean"
        } else {
            clean
        }
    }

    private fun escapeCsvCell(raw: String): String {
        val safe = sanitizeFormulaCell(raw).replace("\"", "\"\"")
        return "\"$safe\""
    }

    fun exportTransactionsToCsv(transactions: List<TransactionEntity>): String {
        val sb = StringBuilder()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        sb.append("id,accountId,type,category,subcategory,amount,paymentMethod,upiApp,timestamp,date,note,hashtags\n")
        for (tx in transactions) {
            val formattedAmt = "%.2f".format(Locale.US, if (tx.amount.isFinite()) tx.amount else 0.0)
            val dateStr = dateFmt.format(Date(tx.timestamp))
            sb.append(
                listOf(
                    tx.id.toString(),
                    tx.accountId.toString(),
                    escapeCsvCell(tx.type),
                    escapeCsvCell(tx.category),
                    escapeCsvCell(tx.subcategory),
                    formattedAmt,
                    escapeCsvCell(tx.paymentMethod),
                    escapeCsvCell(tx.upiApp),
                    tx.timestamp.toString(),
                    escapeCsvCell(dateStr),
                    escapeCsvCell(tx.note),
                    escapeCsvCell(tx.hashtags)
                ).joinToString(",")
            ).append('\n')
        }
        return sb.toString()
    }

    fun parseCsvTransactions(csvText: String, defaultAccountId: Long = 1L): List<TransactionEntity> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return emptyList()
        val header = parseCsvRow(lines.first()).map { it.lowercase() }
        val hasDateColumn = header.getOrNull(9) == "date"

        val results = mutableListOf<TransactionEntity>()
        for (i in 1 until lines.size) {
            val cols = parseCsvRow(lines[i])
            if (cols.size < 4) continue
            val type = (cols.getOrNull(2) ?: "EXPENSE").uppercase()
            val category = (cols.getOrNull(3) ?: "").ifBlank { "Other" }
            val subcategory = cols.getOrNull(4) ?: ""
            val amount = cols.getOrNull(5)?.toDoubleOrNull()
                ?: cols.getOrNull(1)?.toDoubleOrNull()
                ?: 0.0
            val paymentMethod = (cols.getOrNull(6) ?: "").ifBlank { "Cash" }
            val upiApp = cols.getOrNull(7) ?: ""
            val timestamp = cols.getOrNull(8)?.toLongOrNull() ?: System.currentTimeMillis()
            val noteRaw = if (hasDateColumn) {
                cols.getOrNull(10) ?: ""
            } else {
                cols.getOrNull(9) ?: cols.getOrNull(4) ?: ""
            }
            val hashtags = if (hasDateColumn) cols.getOrNull(11) ?: "" else cols.getOrNull(10) ?: ""
            if (amount.isFinite() && amount > 0.0) {
                results.add(
                    TransactionEntity(
                        accountId = defaultAccountId,
                        type = if (type in setOf("INCOME", "EXPENSE", "TRANSFER", "TRANSFER_IN", "TRANSFER_OUT", "BORROW", "LEND")) type else "EXPENSE",
                        amount = MoneyPaise.roundRupees(amount),
                        amountPaise = MoneyPaise.toPaise(amount),
                        category = category,
                        subcategory = subcategory,
                        paymentMethod = paymentMethod,
                        upiApp = upiApp,
                        timestamp = timestamp,
                        note = noteRaw,
                        hashtags = hashtags
                    )
                )
            }
        }
        return results
    }

    internal fun parseCsvRow(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    cur.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out.add(cur.toString().trim())
                    cur.clear()
                }
                else -> cur.append(ch)
            }
            i++
        }
        out.add(cur.toString().trim())
        return out
    }
}
