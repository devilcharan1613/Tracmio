package com.tracmio.app.data.services

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.tracmio.app.data.FinancialCalculator
import com.tracmio.app.data.MoneyPaise
import com.tracmio.app.data.local.AccountEntity
import com.tracmio.app.data.local.TransactionEntity
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NativeStatementPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 40f
    private const val MAX_LINES_PER_PAGE = 36
    private const val MAX_LINE_CHARS = 78

    data class PdfLine(
        val text: String,
        val isSectionHeader: Boolean = false,
        val isBold: Boolean = false
    )

    data class PdfPagePlan(
        val pageNumber: Int,
        val totalPages: Int,
        val title: String,
        val subtitle: String,
        val lines: List<PdfLine>,
        val footer: String
    )

    data class StatementPdfPlan(
        val jobTitle: String,
        val generatedLabel: String,
        val pages: List<PdfPagePlan>,
        val totalIncome: Double,
        val totalExpense: Double,
        val netBalance: Double
    )

    data class PdfRenderResult(
        val bytes: ByteArray,
        val pageCount: Int
    )

    fun buildStatementPlan(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        currencySymbol: String = "₹",
        generatedMillis: Long = System.currentTimeMillis()
    ): StatementPdfPlan {
        val dateFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val txDateFmt = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val generatedLabel = "Generated: ${dateFmt.format(Date(generatedMillis))}"

        val totalBal = MoneyPaise.roundRupees(accounts.sumOf { FinancialCalculator.balance(it, transactions) })
        val totalIncome = MoneyPaise.roundRupees(
            transactions.filter { it.type.equals("INCOME", true) }.sumOf { it.amount }
        )
        val totalExpense = MoneyPaise.roundRupees(
            transactions.filter { it.type.equals("EXPENSE", true) }.sumOf { it.amount }
        )

        val accountNames = accounts.associate { it.id to it.name }
        val allLines = mutableListOf<PdfLine>()

        allLines += PdfLine(
            "Net Balance (${accounts.size} accounts): $currencySymbol${"%,.2f".format(totalBal)}",
            isSectionHeader = true,
            isBold = true
        )
        allLines += PdfLine(
            "Total Income: $currencySymbol${"%,.2f".format(totalIncome)}   |   Total Expense: $currencySymbol${"%,.2f".format(totalExpense)}",
            isBold = true
        )
        allLines += PdfLine(" ")

        if (accounts.isNotEmpty()) {
            allLines += PdfLine("ACCOUNT BALANCES", isSectionHeader = true, isBold = true)
            accounts.forEach { acc ->
                val liveBal = FinancialCalculator.balance(acc, transactions)
                val lineText = "• ${acc.name} (${acc.accountType}): $currencySymbol${"%,.2f".format(liveBal)}"
                wrapText(lineText, MAX_LINE_CHARS).forEach { chunk ->
                    allLines += PdfLine(chunk)
                }
            }
            allLines += PdfLine(" ")
        }

        allLines += PdfLine("LEDGER TRANSACTIONS (${transactions.size} total)", isSectionHeader = true, isBold = true)
        if (transactions.isEmpty()) {
            allLines += PdfLine("No transactions recorded in this statement.")
        } else {
            transactions.forEachIndexed { index, tx ->
                val d = txDateFmt.format(Date(tx.timestamp))
                val accName = accountNames[tx.accountId] ?: "Acct #${tx.accountId}"
                val method = if (tx.upiApp.isNotBlank()) "${tx.paymentMethod} (${tx.upiApp})" else tx.paymentMethod
                val primaryLine = "${index + 1}. $d | ${tx.type} | ${tx.category} | $currencySymbol${"%,.2f".format(tx.amount)} | $accName | $method"
                wrapText(primaryLine, MAX_LINE_CHARS).forEach { chunk ->
                    allLines += PdfLine(chunk, isBold = false)
                }
                val extraParts = listOfNotNull(
                    tx.subcategory.takeIf { it.isNotBlank() }?.let { "Subcategory: $it" },
                    tx.note.takeIf { it.isNotBlank() }?.let { "Note: ${tx.note}" },
                    tx.hashtags.takeIf { it.isNotBlank() }?.let { "Tags: ${tx.hashtags}" },
                    tx.warrantyDate.takeIf { it.isNotBlank() }?.let { "Warranty: ${tx.warrantyDate}" }
                )
                if (extraParts.isNotEmpty()) {
                    wrapText("   " + extraParts.joinToString(" · "), MAX_LINE_CHARS).forEach { chunk ->
                        allLines += PdfLine(chunk)
                    }
                }
            }
        }

        val pages = paginateLines(
            title = "TRACMIO · OFFLINE FINANCE STATEMENT (v16.13)",
            subtitle = generatedLabel,
            lines = allLines
        )

        return StatementPdfPlan(
            jobTitle = "Tracmio Ledger Statement",
            generatedLabel = generatedLabel,
            pages = pages,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netBalance = totalBal
        )
    }

    fun buildReceiptPlan(
        tx: TransactionEntity,
        accountName: String,
        currencySymbol: String = "₹",
        generatedMillis: Long = System.currentTimeMillis()
    ): StatementPdfPlan {
        val dateFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val generatedLabel = "Generated: ${dateFmt.format(Date(generatedMillis))}"
        val txTimeStr = dateFmt.format(Date(tx.timestamp))
        val lines = mutableListOf<PdfLine>().apply {
            add(PdfLine("RECEIPT SUMMARY", isSectionHeader = true, isBold = true))
            add(PdfLine("Transaction ID: #${tx.id}   |   UID: ${tx.stableUid.take(12)}"))
            add(PdfLine("Date & Time: $txTimeStr"))
            add(PdfLine("Account: $accountName"))
            add(PdfLine("Type: ${tx.type}   |   Amount: $currencySymbol${"%,.2f".format(tx.amount)}", isBold = true))
            add(PdfLine("Category: ${tx.category}${if (tx.subcategory.isNotBlank()) " / ${tx.subcategory}" else ""}"))
            add(PdfLine("Payment Method: ${tx.paymentMethod}${if (tx.upiApp.isNotBlank()) " (${tx.upiApp})" else ""}"))
            if (tx.warrantyDate.isNotBlank()) add(PdfLine("Warranty / Return Deadline: ${tx.warrantyDate}"))
            if (tx.hashtags.isNotBlank()) {
                wrapText("Hashtags: ${tx.hashtags}", MAX_LINE_CHARS).forEach { add(PdfLine(it)) }
            }
            if (tx.note.isNotBlank()) {
                add(PdfLine(" "))
                add(PdfLine("NOTES", isSectionHeader = true, isBold = true))
                wrapText(tx.note, MAX_LINE_CHARS).forEach { add(PdfLine(it)) }
            }
        }
        val pages = paginateLines(
            title = "TRACMIO · TRANSACTION RECEIPT (v16.13)",
            subtitle = generatedLabel,
            lines = lines
        )
        return StatementPdfPlan(
            jobTitle = "Tracmio Receipt #${tx.id}",
            generatedLabel = generatedLabel,
            pages = pages,
            totalIncome = if (tx.type.equals("INCOME", true)) tx.amount else 0.0,
            totalExpense = if (tx.type.equals("EXPENSE", true)) tx.amount else 0.0,
            netBalance = tx.amount
        )
    }

    fun buildCustomReportPlan(
        title: String,
        reportLines: List<Pair<String, Boolean>>,
        generatedMillis: Long = System.currentTimeMillis()
    ): StatementPdfPlan {
        val dateFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val generatedLabel = "Generated: ${dateFmt.format(Date(generatedMillis))}"
        val lines = mutableListOf<PdfLine>()
        reportLines.forEach { (text, emphasized) ->
            wrapText(text, MAX_LINE_CHARS).forEach { chunk ->
                lines += PdfLine(chunk, isSectionHeader = emphasized, isBold = emphasized)
            }
        }
        if (lines.isEmpty()) {
            lines += PdfLine("No report lines available.")
        }
        val pages = paginateLines(
            title = title.uppercase().ifBlank { "TRACMIO · OFFLINE REPORT (v16.13)" },
            subtitle = generatedLabel,
            lines = lines
        )
        return StatementPdfPlan(
            jobTitle = title.ifBlank { "Tracmio Report" },
            generatedLabel = generatedLabel,
            pages = pages,
            totalIncome = 0.0,
            totalExpense = 0.0,
            netBalance = 0.0
        )
    }

    private fun paginateLines(
        title: String,
        subtitle: String,
        lines: List<PdfLine>
    ): List<PdfPagePlan> {
        val chunks = if (lines.isEmpty()) listOf(emptyList()) else lines.chunked(MAX_LINES_PER_PAGE)
        val totalPages = chunks.size.coerceAtLeast(1)
        return chunks.mapIndexed { idx, pageLines ->
            val pageNumber = idx + 1
            PdfPagePlan(
                pageNumber = pageNumber,
                totalPages = totalPages,
                title = title,
                subtitle = subtitle,
                lines = pageLines,
                footer = "Tracmio Offline Vault (v16.13) · Page $pageNumber of $totalPages"
            )
        }
    }

    internal fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val out = mutableListOf<String>()
        var remaining = text
        while (remaining.length > maxChars) {
            val splitAt = remaining.lastIndexOf(' ', startIndex = maxChars).takeIf { it > maxChars / 3 } ?: maxChars
            out += remaining.substring(0, splitAt).trimEnd()
            remaining = remaining.substring(splitAt).trimStart()
        }
        if (remaining.isNotEmpty()) out += remaining
        return out
    }

    fun renderPlanToPdfBytes(plan: StatementPdfPlan): PdfRenderResult {
        val doc = PdfDocument()
        try {
            val titlePaint = Paint().apply {
                color = Color.rgb(0, 140, 128)
                textSize = 15f
                isFakeBoldText = true
            }
            val subPaint = Paint().apply {
                color = Color.GRAY
                textSize = 10f
            }
            val sectionPaint = Paint().apply {
                color = Color.rgb(0, 120, 110)
                textSize = 11.5f
                isFakeBoldText = true
            }
            val boldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10.5f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 10.5f
            }

            plan.pages.forEach { pagePlan ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pagePlan.pageNumber).create()
                val page = doc.startPage(pageInfo)
                val canvas: Canvas = page.canvas

                var y = 44f
                canvas.drawText(pagePlan.title, MARGIN_X, y, titlePaint)
                y += 16f
                canvas.drawText(pagePlan.subtitle, MARGIN_X, y, subPaint)
                y += 10f
                canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, subPaint)
                y += 20f

                pagePlan.lines.forEach { line ->
                    val paint = when {
                        line.isSectionHeader -> sectionPaint
                        line.isBold -> boldPaint
                        else -> bodyPaint
                    }
                    canvas.drawText(line.text, MARGIN_X, y, paint)
                    y += 17f
                }

                canvas.drawLine(MARGIN_X, PAGE_HEIGHT - 46f, PAGE_WIDTH - MARGIN_X, PAGE_HEIGHT - 46f, subPaint)
                canvas.drawText(pagePlan.footer, MARGIN_X, PAGE_HEIGHT - 28f, subPaint)
                doc.finishPage(page)
            }

            val bos = ByteArrayOutputStream()
            doc.writeTo(bos)
            val bytes = bos.toByteArray()
            check(bytes.isNotEmpty()) { "Generated PDF stream is empty" }
            return PdfRenderResult(bytes = bytes, pageCount = plan.pages.size)
        } finally {
            doc.close()
        }
    }

    fun buildStatementPdfResult(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        currencySymbol: String = "₹"
    ): PdfRenderResult = renderPlanToPdfBytes(buildStatementPlan(accounts, transactions, currencySymbol))

    fun buildReceiptPdfResult(
        tx: TransactionEntity,
        accountName: String,
        currencySymbol: String = "₹"
    ): PdfRenderResult = renderPlanToPdfBytes(buildReceiptPlan(tx, accountName, currencySymbol))

    fun buildCustomReportPdfResult(
        title: String,
        lines: List<Pair<String, Boolean>>
    ): PdfRenderResult = renderPlanToPdfBytes(buildCustomReportPlan(title, lines))

    fun buildStatementPdfBytes(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        currencySymbol: String = "₹"
    ): ByteArray = buildStatementPdfResult(accounts, transactions, currencySymbol).bytes
}
