package com.paysplit.app.domain

import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.ExpenseEntity
import com.paysplit.app.data.model.IncomeSource
import com.paysplit.app.util.DateUtils
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Аналитика: агрегаты по месяцам, категориям, тренды и прогнозы. */
object AnalyticsEngine {

    enum class Trend(val label: String) {
        UP("растёт"),
        DOWN("падает"),
        STABLE("стабильно"),
    }

    data class MonthStat(
        val yearMonth: YearMonth,
        val income: Double,
        val expense: Double,
    ) {
        val net: Double get() = income - expense
    }

    data class CategoryExpense(
        val categoryId: Long,
        val name: String,
        val icon: String,
        val amount: Double,
        /** Доля от общих расходов (0..1). */
        val share: Float,
    )

    data class FrequentExpense(
        val title: String,
        val count: Int,
        val total: Double,
        val icon: String,
    )

    data class PeriodStats(
        val from: Long,
        val to: Long,
        val months: List<MonthStat>,
        val totalIncome: Double,
        val totalExpense: Double,
        val net: Double,
        val avgIncome: Double,
        val avgExpense: Double,
        val byCategory: List<CategoryExpense>,
        val bySource: List<Pair<IncomeSource, Double>>,
        val topLargest: List<ExpenseEntity>,
        val topFrequent: List<FrequentExpense>,
        val incomeTrend: Trend,
        val expenseTrend: Trend,
    )

    /** Полная статистика за период [from]..[to]. */
    fun compute(data: AppData, from: Long, to: Long): PeriodStats {
        val incomes = data.incomes.filter { it.date in from..to }
        val expenses = data.expenses.filter { it.date in from..to }
        val distributions = data.distributions.filter { it.date in from..to }

        val months = buildMonthList(from, to).map { ym ->
            val mStart = DateUtils.millisOf(ym.atDay(1))
            val mEnd = DateUtils.millisOf(ym.atEndOfMonth()) + 86_400_000L - 1
            val adjIncome = data.adjustments.filter { it.date in mStart..mEnd && it.amount > 0 }.sumOf { it.amount }
            val adjExpense = data.adjustments.filter { it.date in mStart..mEnd && it.amount < 0 }.sumOf { -it.amount }
            MonthStat(
                yearMonth = ym,
                income = distributions.filter { it.date in mStart..mEnd }.sumOf { it.amount } + adjIncome,
                expense = expenses.filter { it.date in mStart..mEnd }.sumOf { it.amount } + adjExpense,
            )
        }

        val totalIncome = months.sumOf { it.income }
        val totalExpense = months.sumOf { it.expense }
        val activeMonths = max(1, months.size)

        val byCategory = expenses
            .groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val category = data.category(categoryId)
                CategoryExpense(
                    categoryId = categoryId,
                    name = category?.name ?: "Без категории",
                    icon = category?.icon ?: "❓",
                    amount = list.sumOf { it.amount },
                    share = 0f,
                )
            }
            .sortedByDescending { it.amount }
            .let { list ->
                val total = list.sumOf { it.amount }
                if (total <= 0.0) list
                else list.map { it.copy(share = (it.amount / total).toFloat()) }
            }

        val bySource = IncomeSource.entries.map { source ->
            source to incomes.filter { it.source == source }.sumOf { it.amount }
        }.filter { it.second > 0.005 }.sortedByDescending { it.second }

        val titleToCategory = expenses.associate { it.title to it.categoryId }
        val topFrequent = expenses
            .groupBy { it.title }
            .map { (title, list) ->
                FrequentExpense(
                    title = title,
                    count = list.size,
                    total = list.sumOf { it.amount },
                    icon = data.category(titleToCategory[title])?.icon ?: "🧾",
                )
            }
            .sortedWith(compareByDescending<FrequentExpense> { it.count }.thenByDescending { it.total })
            .take(5)

        return PeriodStats(
            from = from,
            to = to,
            months = months,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            net = totalIncome - totalExpense,
            avgIncome = totalIncome / activeMonths,
            avgExpense = totalExpense / activeMonths,
            byCategory = byCategory,
            bySource = bySource,
            topLargest = expenses.sortedByDescending { it.amount }.take(5),
            topFrequent = topFrequent,
            incomeTrend = trendOf(months.map { it.income }),
            expenseTrend = trendOf(months.map { it.expense }),
        )
    }

    /** Список месяцев, пересекающихся с периодом. */
    fun buildMonthList(from: Long, to: Long): List<YearMonth> {
        val start = DateUtils.yearMonth(from)
        val end = DateUtils.yearMonth(to)
        val result = mutableListOf<YearMonth>()
        var current = start
        while (!current.isAfter(end)) {
            result += current
            current = current.plusMonths(1)
        }
        return result
    }

    /** Тренд: сравниваем среднее первой половины периода со второй. */
    fun trendOf(values: List<Double>): Trend {
        if (values.size < 2) return Trend.STABLE
        val half = values.size / 2
        val first = values.take(half).average()
        val second = values.drop(half).average()
        val base = max(0.0001, (abs(first) + abs(second)) / 2)
        val diff = (second - first) / base
        return when {
            diff > 0.05 -> Trend.UP
            diff < -0.05 -> Trend.DOWN
            else -> Trend.STABLE
        }
    }

    /** Прогресс накоплений по месяцам (только накопительные счета). */
    fun savingsGrowth(
        data: AppData,
        months: List<YearMonth>,
    ): List<Pair<YearMonth, Double>> {
        val savingsIds = data.categories
            .filter { it.type == com.paysplit.app.data.model.CategoryType.SAVINGS }
            .map { it.id }
            .toSet()
        return months.map { ym ->
            val mEnd = DateUtils.millisOf(ym.atEndOfMonth()) + 86_400_000L - 1
            val inflow = data.distributions
                .filter { it.categoryId in savingsIds && it.date <= mEnd }
                .sumOf { it.amount }
            val transferIn = data.transfers.filter { it.toCategoryId in savingsIds && it.date <= mEnd }
                .sumOf { it.amount }
            val adjIn = data.adjustments.filter { it.categoryId in savingsIds && it.date <= mEnd && it.amount > 0 }.sumOf { it.amount }
            val outflow = data.expenses
                .filter { it.categoryId in savingsIds && it.date <= mEnd }
                .sumOf { it.amount }
            val transferOut = data.transfers
                .filter { it.fromCategoryId in savingsIds && it.date <= mEnd }
                .sumOf { it.amount }
            val adjOut = data.adjustments.filter { it.categoryId in savingsIds && it.date <= mEnd && it.amount < 0 }.sumOf { -it.amount }
            ym to (inflow + transferIn + adjIn - outflow - transferOut - adjOut)
        }
    }

    /**
     * Прогноз: сколько месяцев осталось до цели при текущем темпе накоплений.
     * Средний темп считается по распределениям за последние 3 месяца.
     */
    fun forecastMonths(
        current: Double,
        target: Double,
        monthlyRate: Double,
    ): Int? {
        if (target <= 0.0 || current >= target || monthlyRate <= 0.0) return null
        return ceil((target - current) / monthlyRate).toInt()
    }

    /** Среднемесячное поступление на накопительные счета за последние [months] месяцев. */
    fun savingsMonthlyRate(data: AppData, months: Int = 3): Double {
        val savingsIds = data.categories
            .filter { it.type == com.paysplit.app.data.model.CategoryType.SAVINGS }
            .map { it.id }
            .toSet()
        if (savingsIds.isEmpty()) return 0.0
        val from = DateUtils.monthStartMonthsBack(months - 1)
        val relevant = data.distributions.filter {
            it.categoryId in savingsIds && it.date >= from
        }
        return relevant.sumOf { it.amount } / max(1, months)
    }

    /** Текущий баланс накопительных счетов. */
    fun savingsBalance(data: AppData): Double {
        val savingsIds = data.categories
            .filter { it.type == com.paysplit.app.data.model.CategoryType.SAVINGS }
            .map { it.id }
            .toSet()
        val inflow = data.distributions.filter { it.categoryId in savingsIds }.sumOf { it.amount }
        val transferIn = data.transfers.filter { it.toCategoryId in savingsIds }.sumOf { it.amount }
        val adjIn = data.adjustments.filter { it.categoryId in savingsIds && it.amount > 0 }.sumOf { it.amount }
        val outflow = data.expenses.filter { it.categoryId in savingsIds }.sumOf { it.amount }
        val transferOut = data.transfers.filter { it.fromCategoryId in savingsIds }.sumOf { it.amount }
        val adjOut = data.adjustments.filter { it.categoryId in savingsIds && it.amount < 0 }.sumOf { -it.amount }
        return inflow + transferIn + adjIn - outflow - transferOut - adjOut
    }

    /** Накопительные счета с прогрессом к цели. */
    fun savingsTargets(data: AppData): List<Pair<CategoryEntity, Double>> =
        data.categories
            .filter {
                it.type == com.paysplit.app.data.model.CategoryType.SAVINGS &&
                    it.targetAmount != null && it.targetAmount > 0.0
            }
            .map { category ->
                val inflow = data.distributions.filter { it.categoryId == category.id }
                    .sumOf { it.amount } +
                    data.transfers.filter { it.toCategoryId == category.id }.sumOf { it.amount } +
                    data.adjustments.filter { it.categoryId == category.id && it.amount > 0 }.sumOf { it.amount }
                val outflow = data.expenses.filter { it.categoryId == category.id }
                    .sumOf { it.amount } +
                    data.transfers.filter { it.fromCategoryId == category.id }.sumOf { it.amount } +
                    data.adjustments.filter { it.categoryId == category.id && it.amount < 0 }.sumOf { -it.amount }
                category to (inflow - outflow)
            }
            .sortedBy { it.first.sortOrder }
}
