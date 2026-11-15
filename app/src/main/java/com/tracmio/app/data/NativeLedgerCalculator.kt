package com.tracmio.app.data

import com.tracmio.app.data.local.AccountEntity
import com.tracmio.app.data.local.LoanEntity
import com.tracmio.app.data.local.LoanPaymentEntity
import com.tracmio.app.data.local.TransactionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object NativeLedgerCalculator {
    data class SafeToSpendResult(
        val remainingToday: Double,
        val dailyAllowance: Double,
        val remainingDaysInMonth: Int,
        val spentToday: Double = 0.0,
        val unallocatedBalance: Double = 0.0
    )

    data class TodaySummary(val todayTxCount: Int, val todayExpense: Double)

    data class EmergencyRunwayResult(
        val months: Double,
        val liquidSavings: Double,
        val averageMonthlyExpense: Double,
        val lookbackMonths: Int,
        val activeMonthsCount: Int
    )

    data class HashtagSummary(
        val tag: String,
        val totalExpense: Double,
        val totalIncome: Double,
        val count: Int
    )

    data class DebtPayoffSimulation(
        val totalMonths: Int,
        val totalInterestPaid: Double,
        val orderOfPayoff: List<String>,
        val nonAmortizing: Boolean = false
    )

    fun computeSafeToSpend(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): SafeToSpendResult {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        val remainingDays = (daysInMonth - currentDay + 1).coerceAtLeast(1)

        var unallocatedTotalPaise = 0L
        for (acc in accounts) {
            if (acc.isArchived || acc.accountType.equals("Credit Card", true)) continue
            val balPaise = MoneyPaise.toPaise(FinancialCalculator.balance(acc, transactions).coerceAtLeast(0.0))
            val allocatedPaise = parseAllocations(acc.allocationsJson).sumOf { MoneyPaise.toPaise(it.allocatedAmount) }
            unallocatedTotalPaise += (balPaise - allocatedPaise).coerceAtLeast(0L)
        }
        val unallocatedTotal = MoneyPaise.toRupees(unallocatedTotalPaise)

        val todaySummary = computeTodayExpenseSummary(accounts, transactions, nowMillis)
        val dailyAllowance = MoneyPaise.roundRupees(unallocatedTotal / remainingDays.toDouble())
        val remainingToday = MoneyPaise.roundRupees((dailyAllowance - todaySummary.todayExpense).coerceAtLeast(0.0))

        return SafeToSpendResult(
            remainingToday = remainingToday,
            dailyAllowance = dailyAllowance,
            remainingDaysInMonth = remainingDays,
            spentToday = todaySummary.todayExpense,
            unallocatedBalance = unallocatedTotal
        )
    }

    fun computeTodayExpenseSummary(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): TodaySummary {
        val todayKey = dayKey(nowMillis)
        val todayExpenses = transactions.filter {
            it.type.equals("EXPENSE", true) && dayKey(it.timestamp) == todayKey && it.amount.isFinite() && it.amount > 0.0
        }
        val sumPaise = todayExpenses.sumOf { if (it.amountPaise > 0L) it.amountPaise else MoneyPaise.toPaise(it.amount) }
        return TodaySummary(
            todayTxCount = todayExpenses.size,
            todayExpense = MoneyPaise.toRupees(sumPaise)
        )
    }

    fun computeEmergencyRunway(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        lookbackMonths: Int = 3,
        nowMillis: Long = System.currentTimeMillis()
    ): EmergencyRunwayResult {
        val safeLookback = lookbackMonths.coerceIn(1, 12)
        val liquidSavingsPaise = accounts.filter {
            !it.isArchived && !it.accountType.equals("Credit Card", true)
        }.sumOf { MoneyPaise.toPaise(FinancialCalculator.balance(it, transactions).coerceAtLeast(0.0)) }
        val liquidSavings = MoneyPaise.toRupees(liquidSavingsPaise)

        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val nowYear = nowCal.get(Calendar.YEAR)
        val nowMonth = nowCal.get(Calendar.MONTH)

        val windowStartCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(safeLookback - 1))
        }
        val windowStartMillis = windowStartCal.timeInMillis

        // Strictly consider EXPENSE rows within [windowStartMillis, nowMillis]; exclude INCOME, TRANSFER*, BORROW, LEND.
        val windowExpenses = transactions.filter { tx ->
            tx.type.equals("EXPENSE", true) &&
                tx.timestamp in windowStartMillis..nowMillis &&
                tx.amount.isFinite() &&
                tx.amount > 0.0
        }

        if (windowExpenses.isEmpty()) {
            return EmergencyRunwayResult(
                months = if (liquidSavings > 0.0) 12.0 else 0.0,
                liquidSavings = liquidSavings,
                averageMonthlyExpense = 0.0,
                lookbackMonths = safeLookback,
                activeMonthsCount = 0
            )
        }

        val txCal = Calendar.getInstance()
        var earliestMonthIndex = nowYear * 12 + nowMonth
        var totalWindowExpensePaise = 0L
        val distinctMonthKeys = mutableSetOf<Int>()

        for (tx in windowExpenses) {
            txCal.timeInMillis = tx.timestamp
            val monthIndex = txCal.get(Calendar.YEAR) * 12 + txCal.get(Calendar.MONTH)
            if (monthIndex < earliestMonthIndex) earliestMonthIndex = monthIndex
            distinctMonthKeys.add(monthIndex)
            totalWindowExpensePaise += if (tx.amountPaise > 0L) tx.amountPaise else MoneyPaise.toPaise(tx.amount)
        }

        val currentMonthIndex = nowYear * 12 + nowMonth
        val spanMonths = (currentMonthIndex - earliestMonthIndex + 1).coerceIn(1, safeLookback)
        val avgMonthlyExpense = MoneyPaise.roundRupees(MoneyPaise.toRupees(totalWindowExpensePaise) / spanMonths.toDouble())
        val runwayMonths = if (avgMonthlyExpense <= 0.0) {
            if (liquidSavings > 0.0) 12.0 else 0.0
        } else {
            (liquidSavings / avgMonthlyExpense).coerceAtLeast(0.1)
        }

        return EmergencyRunwayResult(
            months = runwayMonths,
            liquidSavings = liquidSavings,
            averageMonthlyExpense = avgMonthlyExpense,
            lookbackMonths = safeLookback,
            activeMonthsCount = distinctMonthKeys.size
        )
    }

    fun computeEmergencyRunwayMonths(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        lookbackMonths: Int = 3,
        nowMillis: Long = System.currentTimeMillis()
    ): Double {
        return computeEmergencyRunway(accounts, transactions, lookbackMonths, nowMillis).months
    }

    fun loanRemainingPaise(loan: LoanEntity, payments: List<LoanPaymentEntity>): Long {
        val principalPaise = if (loan.principalPaise > 0L) loan.principalPaise else MoneyPaise.toPaise(loan.principal)
        val paid = payments.filter { it.loanId == loan.id }.sumOf {
            if (it.amountPaise > 0L) it.amountPaise else MoneyPaise.toPaise(it.amount)
        }
        return (principalPaise - paid).coerceAtLeast(0L)
    }

    fun dayKey(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    fun computeSpendingDistribution(
        transactions: List<TransactionEntity>,
        categories: List<String>,
        window: SpendingChartWindow,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<String, Double> {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val curYear = nowCal.get(Calendar.YEAR)
        val curMonth = nowCal.get(Calendar.MONTH)
        val todayKey = dayKey(nowMillis)
        val weekStartMillis = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val txCal = Calendar.getInstance()
        val paiseMap = linkedMapOf<String, Long>()
        transactions.filter { tx ->
            if (!tx.type.equals("EXPENSE", true) || !tx.amount.isFinite() || tx.amount <= 0.0) return@filter false
            txCal.timeInMillis = tx.timestamp
            when (window) {
                SpendingChartWindow.DAILY -> dayKey(tx.timestamp) == todayKey
                SpendingChartWindow.WEEKLY -> tx.timestamp in weekStartMillis..nowMillis
                SpendingChartWindow.MONTHLY -> txCal.get(Calendar.YEAR) == curYear && txCal.get(Calendar.MONTH) == curMonth
                SpendingChartWindow.YEARLY -> txCal.get(Calendar.YEAR) == curYear
            }
        }.forEach { tx ->
            val txPaise = if (tx.amountPaise > 0L) tx.amountPaise else MoneyPaise.toPaise(tx.amount)
            paiseMap[tx.category] = (paiseMap[tx.category] ?: 0L) + txPaise
        }
        return paiseMap.mapValues { (_, p) -> MoneyPaise.toRupees(p) }
    }

    fun computeCashDenominationTotal(lines: List<CashCountLine>): Double {
        return lines.sumOf { (it.value.coerceAtLeast(0) * it.count.coerceAtLeast(0)).toDouble() }
    }

    fun summarizeHashtags(transactions: List<TransactionEntity>): List<HashtagSummary> {
        val regex = Regex("#[A-Za-z0-9_]+")
        val map = mutableMapOf<String, Triple<Long, Long, Int>>()
        for (tx in transactions) {
            val combined = "${tx.note} ${tx.hashtags}"
            val tags = regex.findAll(combined).map { it.value.lowercase() }.toSet()
            val amtPaise = if (tx.amountPaise > 0L) tx.amountPaise else MoneyPaise.toPaise(tx.amount)
            for (tag in tags) {
                val cur = map[tag] ?: Triple(0L, 0L, 0)
                val exp = if (tx.type.equals("EXPENSE", true)) cur.first + amtPaise else cur.first
                val inc = if (tx.type.equals("INCOME", true)) cur.second + amtPaise else cur.second
                map[tag] = Triple(exp, inc, cur.third + 1)
            }
        }
        return map.entries.map { (tag, triple) ->
            HashtagSummary(tag, MoneyPaise.toRupees(triple.first), MoneyPaise.toRupees(triple.second), triple.third)
        }.sortedByDescending { it.totalExpense + it.totalIncome }
    }

    fun serializeAllocations(allocations: List<AccountAllocation>): String {
        val arr = JSONArray()
        for (a in allocations) {
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("category", a.category)
            obj.put("allocatedAmount", MoneyPaise.roundRupees(a.allocatedAmount))
            obj.put("percentage", a.percentage)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun parseAllocations(json: String?): List<AccountAllocation> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            val list = mutableListOf<AccountAllocation>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val amt = obj.optDouble("allocatedAmount", 0.0).takeIf { it.isFinite() } ?: 0.0
                val pct = obj.optDouble("percentage", 0.0).takeIf { it.isFinite() } ?: 0.0
                list.add(
                    AccountAllocation(
                        id = obj.optString("id", "${i + 1}"),
                        category = obj.optString("category", "Bucket"),
                        allocatedAmount = MoneyPaise.roundRupees(amt),
                        percentage = pct
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun applyAutoSplitToAllocations(account: AccountEntity, incomeAmount: Double): AccountEntity {
        val current = parseAllocations(account.allocationsJson)
        if (current.isEmpty() || !incomeAmount.isFinite() || incomeAmount <= 0.0) return account
        val updated = current.map { alloc ->
            if (alloc.percentage > 0.0) {
                val add = MoneyPaise.roundRupees(incomeAmount * (alloc.percentage / 100.0))
                alloc.copy(allocatedAmount = MoneyPaise.roundRupees(alloc.allocatedAmount + add))
            } else {
                alloc
            }
        }
        return account.copy(allocationsJson = serializeAllocations(updated))
    }

    fun adjustAllocationsForTransaction(
        account: AccountEntity,
        allocationId: String,
        txType: String,
        amount: Double
    ): AccountEntity {
        if (allocationId.isBlank() || !amount.isFinite() || amount <= 0.0) return account
        val current = parseAllocations(account.allocationsJson)
        val updated = current.map { alloc ->
            if (alloc.id == allocationId || alloc.category.equals(allocationId, true)) {
                val next = if (txType.equals("INCOME", true)) {
                    alloc.allocatedAmount + amount
                } else {
                    (alloc.allocatedAmount - amount).coerceAtLeast(0.0)
                }
                alloc.copy(allocatedAmount = MoneyPaise.roundRupees(next))
            } else {
                alloc
            }
        }
        return account.copy(allocationsJson = serializeAllocations(updated))
    }

    fun computeRoundUpSpareChange(amount: Double, roundTo: Int = 10): Double {
        if (roundTo <= 1 || !amount.isFinite() || amount <= 0.0) return 0.0
        val roundToPaise = roundTo.toLong() * 100L
        val amtPaise = MoneyPaise.toPaise(amount)
        val remPaise = amtPaise % roundToPaise
        return if (remPaise == 0L) 0.0 else MoneyPaise.toRupees(roundToPaise - remPaise)
    }

    fun simulateDebtPayoff(
        loans: List<LoanEntity>,
        payments: List<LoanPaymentEntity>,
        extraMonthlyPayment: Double,
        isAvalanche: Boolean
    ): DebtPayoffSimulation {
        data class MutableLoan(
            val name: String,
            var balancePaise: Long,
            val minPaymentPaise: Long,
            val monthlyRate: Double,
            val apr: Double
        )

        val activeUnsorted = loans.filter { !it.isClosed }.mapNotNull { loan ->
            val remPaise = loanRemainingPaise(loan, payments)
            if (remPaise <= 0L) return@mapNotNull null
            val rawMinPaise = if (loan.emiPaise > 0L) loan.emiPaise else MoneyPaise.toPaise(loan.monthlyPayment)
            val safeMinPaise = rawMinPaise.coerceAtLeast(10_000L) // minimum ₹100 floor if unconfigured
            val safeApr = loan.interestRate.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            MutableLoan(
                name = loan.name.ifBlank { loan.lender }.ifBlank { "Loan #${loan.id}" },
                balancePaise = remPaise,
                minPaymentPaise = safeMinPaise,
                monthlyRate = (safeApr / 100.0) / 12.0,
                apr = safeApr
            )
        }

        if (activeUnsorted.isEmpty()) {
            return DebtPayoffSimulation(totalMonths = 0, totalInterestPaid = 0.0, orderOfPayoff = emptyList(), nonAmortizing = false)
        }

        val active = if (isAvalanche) {
            activeUnsorted.sortedWith(compareByDescending<MutableLoan> { it.apr }.thenBy { it.balancePaise }).toMutableList()
        } else {
            activeUnsorted.sortedWith(compareBy<MutableLoan> { it.balancePaise }.thenByDescending { it.apr }).toMutableList()
        }

        val extraPaise = if (extraMonthlyPayment.isFinite() && extraMonthlyPayment > 0.0) MoneyPaise.toPaise(extraMonthlyPayment) else 0L
        // Total monthly debt budget remains constant as smaller/higher-APR debts are paid off (minimum payment rollover).
        val totalMonthlyBudgetPaise = active.sumOf { it.minPaymentPaise } + extraPaise

        var months = 0
        var totalInterestPaise = 0L
        val order = mutableListOf<String>()
        var nonAmortizing = false

        while (active.any { it.balancePaise > 0L } && months < 600) {
            val prevTotalPaise = active.sumOf { it.balancePaise }
            months++

            // 1. Accrue monthly interest on remaining active balances
            for (l in active) {
                if (l.balancePaise <= 0L) continue
                val interestPaise = Math.round(l.balancePaise * l.monthlyRate)
                if (interestPaise > 0L) {
                    totalInterestPaise += interestPaise
                    l.balancePaise += interestPaise
                }
            }

            // 2. Pay minimum payments across active loans; freed minimums stay in the monthly budget pool
            var spentThisMonthPaise = 0L
            for (l in active) {
                if (l.balancePaise <= 0L) continue
                val minPay = l.minPaymentPaise.coerceAtMost(l.balancePaise)
                l.balancePaise -= minPay
                spentThisMonthPaise += minPay
                if (l.balancePaise == 0L && l.name !in order) {
                    order.add(l.name)
                }
            }

            // 3. Roll over extra payment + freed minimum payments onto priority targets
            var rolloverAvailablePaise = (totalMonthlyBudgetPaise - spentThisMonthPaise).coerceAtLeast(0L)
            for (l in active) {
                if (l.balancePaise > 0L && rolloverAvailablePaise > 0L) {
                    val apply = rolloverAvailablePaise.coerceAtMost(l.balancePaise)
                    l.balancePaise -= apply
                    rolloverAvailablePaise -= apply
                    if (l.balancePaise == 0L && l.name !in order) {
                        order.add(l.name)
                    }
                }
            }

            val afterTotalPaise = active.sumOf { it.balancePaise }
            if (afterTotalPaise >= prevTotalPaise && months >= 12) {
                nonAmortizing = true
                break
            }
        }

        if (active.any { it.balancePaise > 0L }) {
            nonAmortizing = true
        }

        return DebtPayoffSimulation(
            totalMonths = months.coerceAtLeast(1),
            totalInterestPaid = MoneyPaise.toRupees(totalInterestPaise),
            orderOfPayoff = order,
            nonAmortizing = nonAmortizing
        )
    }
}
