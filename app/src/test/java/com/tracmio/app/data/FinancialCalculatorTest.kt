package com.tracmio.app.data

import com.tracmio.app.data.local.AccountEntity
import com.tracmio.app.data.local.LoanEntity
import com.tracmio.app.data.local.LoanPaymentEntity
import com.tracmio.app.data.local.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinancialCalculatorTest {
    @Test
    fun accountBalanceAndMathEvaluatorWork() {
        val acc = AccountEntity(id = 1L, name = "Bank", accountType = "Bank", balance = 1000.0)
        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, type = "INCOME", amount = 500.0, category = "Family & Income"),
            TransactionEntity(id = 2L, accountId = 1L, type = "EXPENSE", amount = 250.0, category = "Food & Chai")
        )
        assertEquals(1250.0, FinancialCalculator.balance(acc, txs), 0.01)
        assertEquals(250.0, FinancialCalculator.netCashFlow(txs), 0.01)
        assertEquals(195.0, FinancialCalculator.evaluateMathExpression("120 + 45 + 30")!!, 0.01)
        assertEquals(150.0, FinancialCalculator.evaluateMathExpression("₹300 / 2")!!, 0.01)
    }

    @Test
    fun safeToSpendAndRunwayReflectLiveBalances() {
        val accounts = listOf(AccountEntity(id = 1L, name = "Savings", accountType = "Bank", balancePaise = 1_000_000L))
        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, type = "EXPENSE", amount = 1000.0, category = "Food & Chai")
        )
        val result = NativeLedgerCalculator.computeSafeToSpend(accounts, txs)
        assertTrue(result.remainingDaysInMonth > 0)
        assertEquals(9000.0, result.unallocatedBalance, 0.01)
        assertTrue(result.dailyAllowance > 0.0)

        val runway = NativeLedgerCalculator.computeEmergencyRunwayMonths(accounts, txs)
        assertEquals(9.0, runway, 0.01)
    }

    @Test
    fun emergencyRunwayRollingWindowExcludesIncomeAndTransfersAndSupports3M6M() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = nowCal.timeInMillis
        fun monthsAgo(m: Int): Long = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -m)
        }.timeInMillis

        val accounts = listOf(
            AccountEntity(id = 1L, name = "Bank", accountType = "Bank", balancePaise = 6_000_000L) // ₹60,000
        )
        val txs = listOf(
            // Inside 3M: total ₹12,000 across 3-month span -> ₹4,000/mo avg
            TransactionEntity(id = 1L, accountId = 1L, type = "EXPENSE", amount = 6000.0, category = "Food & Chai", timestamp = monthsAgo(0)),
            TransactionEntity(id = 2L, accountId = 1L, type = "EXPENSE", amount = 6000.0, category = "Food & Chai", timestamp = monthsAgo(2)),
            // Older (4 months ago, inside 6M only): ₹24,000
            TransactionEntity(id = 3L, accountId = 1L, type = "EXPENSE", amount = 24000.0, category = "Rent & Housing", timestamp = monthsAgo(4)),
            // Must be excluded: INCOME, TRANSFER_OUT, BORROW
            TransactionEntity(id = 4L, accountId = 1L, type = "INCOME", amount = 50000.0, category = "Salary", timestamp = monthsAgo(0)),
            TransactionEntity(id = 5L, accountId = 1L, type = "TRANSFER_OUT", amount = 10000.0, category = "Transfer", timestamp = monthsAgo(0)),
            TransactionEntity(id = 6L, accountId = 1L, type = "BORROW", amount = 5000.0, category = "Loan", timestamp = monthsAgo(0))
        )
        // Live bank balance = 60000 - 36000 + 50000 - 10000 + 5000 = 69000
        val runway3m = NativeLedgerCalculator.computeEmergencyRunway(accounts, txs, lookbackMonths = 3, nowMillis = now)
        assertEquals(4000.0, runway3m.averageMonthlyExpense, 0.01)

        val runway6m = NativeLedgerCalculator.computeEmergencyRunway(accounts, txs, lookbackMonths = 6, nowMillis = now)
        // Span from monthsAgo(4) to monthsAgo(0) is 5 months; total window expenses = 36000 -> 7200/mo
        assertEquals(7200.0, runway6m.averageMonthlyExpense, 0.01)
        assertTrue(runway3m.months > runway6m.months)
    }

    @Test
    fun cashDenominationAndDebtPayoffCalculatorsWork() {
        val lines = listOf(
            CashCountLine("NOTE", 500, 2),
            CashCountLine("NOTE", 100, 3),
            CashCountLine("COIN", 10, 5)
        )
        assertEquals(1350.0, NativeLedgerCalculator.computeCashDenominationTotal(lines), 0.01)
        assertEquals(5.0, NativeLedgerCalculator.computeRoundUpSpareChange(45.0, 10), 0.01)
        assertEquals(0.0, NativeLedgerCalculator.computeRoundUpSpareChange(100.0, 10), 0.01)

        val loans = listOf(
            LoanEntity(id = 1L, name = "Card", principal = 8000.0, principalPaise = 800_000L, interestRate = 24.0, monthlyPayment = 1000.0),
            LoanEntity(id = 2L, name = "Bike", principal = 22500.0, principalPaise = 2_250_000L, interestRate = 10.0, monthlyPayment = 1000.0),
            LoanEntity(id = 3L, name = "ClosedOld", principal = 50000.0, principalPaise = 5_000_000L, interestRate = 15.0, monthlyPayment = 2500.0, isClosed = true)
        )
        // Partial payment on Bike reduces remaining below Card (22500 - 15000 = 7500 < 8000)
        val payments = listOf(LoanPaymentEntity(loanId = 2L, amount = 15000.0, amountPaise = 1_500_000L))
        val avalanche = NativeLedgerCalculator.simulateDebtPayoff(loans, payments, 3500.0, isAvalanche = true)
        val snowball = NativeLedgerCalculator.simulateDebtPayoff(loans, payments, 3500.0, isAvalanche = false)
        assertFalse(avalanche.nonAmortizing)
        assertFalse(snowball.nonAmortizing)
        assertFalse("ClosedOld" in avalanche.orderOfPayoff)
        assertEquals("Card", avalanche.orderOfPayoff.first())
        assertEquals("Bike", snowball.orderOfPayoff.first())
        assertTrue(avalanche.totalInterestPaid < snowball.totalInterestPaid)

        val predatory = listOf(
            LoanEntity(id = 9L, name = "Spiral", principal = 100000.0, principalPaise = 10_000_000L, interestRate = 36.0, monthlyPayment = 500.0)
        )
        val nonAmort = NativeLedgerCalculator.simulateDebtPayoff(predatory, emptyList(), 0.0, isAvalanche = true)
        assertTrue(nonAmort.nonAmortizing)
    }

    @Test
    fun csvAndXlsxRoundTripAndMoneyPaiseWork() {
        assertEquals(1500L, MoneyPaise.toPaise(15.0))
        assertEquals(15.0, MoneyPaise.toRupees(1500L), 0.001)
        assertEquals(0L, MoneyPaise.toPaise(Double.NaN))
        assertEquals(0L, MoneyPaise.toPaise(Double.POSITIVE_INFINITY))
        assertEquals(0.0, MoneyPaise.roundRupees(Double.NaN), 0.0001)

        assertEquals("₹••••••", MoneyPaise.formatCurrency(1234.0, "₹", stealthMode = false, privacyMask = true))
        val decoy = MoneyPaise.formatCurrency(1234.0, "₹", stealthMode = true, privacyMask = false)
        assertTrue(decoy.startsWith("₹") && decoy != "₹1,234")

        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, type = "EXPENSE", amount = 120.0, category = "Food & Chai", note = "Tea #morning")
        )
        val csv = CsvParser.exportTransactionsToCsv(txs)
        val parsed = CsvParser.parseCsvTransactions(csv, defaultAccountId = 1L)
        assertEquals(1, parsed.size)
        assertEquals(120.0, parsed.first().amount, 0.01)

        val xlsx = XlsxExporter.createWorkbookBytes(emptyList(), txs)
        assertNotNull(xlsx)
        assertTrue(xlsx.size > 100)
    }

    @Test
    fun monthlyPaceFiltersCurrentMonthAndTransfersPreserveTotalBalance() {
        val now = 1_760_500_000_000L // mid-month timestamp
        val lastMonth = now - 35L * 86_400_000L
        val fromAcc = AccountEntity(id = 1L, name = "Bank", accountType = "Bank", balance = 5000.0)
        val toAcc = AccountEntity(id = 2L, name = "Cash", accountType = "Cash", balance = 500.0)

        val txs = listOf(
            TransactionEntity(id = 1L, accountId = 1L, type = "EXPENSE", amount = 8000.0, category = "Rent & Housing", timestamp = lastMonth),
            TransactionEntity(id = 2L, accountId = 1L, type = "EXPENSE", amount = 1200.0, category = "Food & Chai", paymentMethod = "UPI", upiApp = "GPay", timestamp = now - 2L * 86_400_000L),
            TransactionEntity(id = 3L, accountId = 1L, type = "TRANSFER_OUT", amount = 1000.0, category = "Transfer", transferAccountId = 2L, transferId = "xfer_1", timestamp = now - 2L * 86_400_000L),
            TransactionEntity(id = 4L, accountId = 2L, type = "TRANSFER_IN", amount = 1000.0, category = "Transfer", transferAccountId = 1L, transferId = "xfer_1", timestamp = now - 2L * 86_400_000L)
        )

        val combined = FinancialCalculator.balance(fromAcc, txs) + FinancialCalculator.balance(toAcc, txs)
        assertEquals(5000.0 + 500.0 - 9200.0, combined, 0.01)

        val pace = NativeIntelligenceCalculators.computeMonthlyPace(
            accounts = listOf(fromAcc, toAcc),
            transactions = txs,
            budgets = listOf(com.tracmio.app.data.local.BudgetEntity(category = "All", monthlyLimit = 10000.0)),
            nowMillis = now
        )
        assertEquals(1200.0, pace.spentSoFar, 0.01)

        val cmp = NativeIntelligenceCalculators.computeMonthComparison(txs, now)
        assertEquals(1200.0, cmp.thisMonthExpense, 0.01)
        assertEquals(8000.0, cmp.lastMonthExpense, 0.01)

        val streak = NativeIntelligenceCalculators.computeNoSpendStreakDays(txs, now)
        assertEquals(2, streak)

        val story = NativeIntelligenceCalculators.computeMoneyStory(txs, now)
        assertEquals("Food & Chai", story.topCategory)
        assertEquals("GPay", story.topPaymentMethod)
    }

    @Test
    fun spendingDistributionWindowsAndCategoryMonthBoundsWork() {
        val cal = java.util.Calendar.getInstance().apply {
            set(2025, java.util.Calendar.MAY, 15, 14, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val todayTx = TransactionEntity(id = 1L, accountId = 1L, type = "EXPENSE", amount = 100.0, category = "Food & Chai", timestamp = now - 3_600_000L)
        val fiveDaysAgoTx = TransactionEntity(id = 2L, accountId = 1L, type = "EXPENSE", amount = 250.0, category = "Transport", timestamp = now - 4L * 86_400_000L)
        val sameMonthEarlierTx = TransactionEntity(id = 3L, accountId = 1L, type = "EXPENSE", amount = 400.0, category = "Bills", timestamp = now - 12L * 86_400_000L)
        val sameYearPrevMonthTx = TransactionEntity(id = 4L, accountId = 1L, type = "EXPENSE", amount = 600.0, category = "Shopping", timestamp = now - 30L * 86_400_000L)
        val prevYearTx = TransactionEntity(id = 5L, accountId = 1L, type = "EXPENSE", amount = 900.0, category = "Travel", timestamp = now - 370L * 86_400_000L)
        val txs = listOf(todayTx, fiveDaysAgoTx, sameMonthEarlierTx, sameYearPrevMonthTx, prevYearTx)

        val daily = NativeLedgerCalculator.computeSpendingDistribution(txs, emptyList(), SpendingChartWindow.DAILY, now)
        assertEquals(100.0, daily["Food & Chai"] ?: 0.0, 0.01)
        assertEquals(1, daily.size)

        val weekly = NativeLedgerCalculator.computeSpendingDistribution(txs, emptyList(), SpendingChartWindow.WEEKLY, now)
        assertEquals(100.0, weekly["Food & Chai"] ?: 0.0, 0.01)
        assertEquals(250.0, weekly["Transport"] ?: 0.0, 0.01)
        assertEquals(2, weekly.size)

        val monthly = NativeLedgerCalculator.computeSpendingDistribution(txs, emptyList(), SpendingChartWindow.MONTHLY, now)
        assertEquals(400.0, monthly["Bills"] ?: 0.0, 0.01)
        assertEquals(3, monthly.size)

        val yearly = NativeLedgerCalculator.computeSpendingDistribution(txs, emptyList(), SpendingChartWindow.YEARLY, now)
        assertEquals(600.0, yearly["Shopping"] ?: 0.0, 0.01)
        assertEquals(4, yearly.size)

        val monthStart = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val foodInMonth = FinancialCalculator.categoryTotal(
            listOf(todayTx, TransactionEntity(id = 6L, accountId = 1L, type = "EXPENSE", amount = 500.0, category = "Food & Chai", timestamp = now - 40L * 86_400_000L)),
            "Food & Chai",
            monthStart,
            now
        )
        assertEquals(100.0, foodInMonth, 0.01)
    }

    @Test
    fun allocationAdjustmentReversesAndReappliesCleanly() {
        val allocJson = NativeLedgerCalculator.serializeAllocations(
            listOf(AccountAllocation(id = "a1", category = "Emergency", allocatedAmount = 2000.0, percentage = 20.0))
        )
        val acc = AccountEntity(id = 1L, name = "Bank", accountType = "Bank", balance = 5000.0, allocationsJson = allocJson)
        val afterSpend = NativeLedgerCalculator.adjustAllocationsForTransaction(acc, "a1", "EXPENSE", 450.0)
        assertEquals(1550.0, NativeLedgerCalculator.parseAllocations(afterSpend.allocationsJson).first().allocatedAmount, 0.01)

        val afterRevert = NativeLedgerCalculator.adjustAllocationsForTransaction(afterSpend, "a1", "INCOME", 450.0)
        assertEquals(2000.0, NativeLedgerCalculator.parseAllocations(afterRevert.allocationsJson).first().allocatedAmount, 0.01)
    }
}
