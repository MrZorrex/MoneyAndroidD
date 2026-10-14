package com.paysplit.app.domain

import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.CategoryStatus
import com.paysplit.app.data.model.CategoryType
import kotlin.math.max

/**
 * Расчёт балансов счетов.
 *
 * Для категорий «Оплата сразу»: баланс = распределено за месяц − потрачено за месяц.
 * Для накопительных: баланс = все поступления за всё время − все расходы за всё время.
 * Переводы уменьшают счёт-источник и увеличиют счёт-получатель.
 */
object BalanceCalculator {

    data class CategoryBalance(
        val category: CategoryEntity,
        /** Распределено (с учётом переводов) в текущем месяце. */
        val allocatedThisMonth: Double,
        /** Потрачено (с учётом переводов) в текущем месяце. */
        val spentThisMonth: Double,
        /** Все поступления за всё время. */
        val allocatedAllTime: Double,
        /** Все расходы за всё время. */
        val spentAllTime: Double,
        /** Текущий баланс по правилам типа категории. */
        val balance: Double,
        /** Баланс за всё время (для накопительных совпадает с balance). */
        val allTimeBalance: Double,
        val status: CategoryStatus,
        /** Сколько ещё нужно распределить до плана в этом месяце. */
        val remainingToPlan: Double,
        /** Прогресс распределения за месяц (0..1). */
        val monthProgress: Float,
        /** Прогресс к целевой сумме (0..1), для накопительных с целью. */
        val targetProgress: Float,
    ) {
        val isOverspend: Boolean get() = balance < -0.005
    }

    /** Балансы всех категорий (включая неактивные). */
    fun compute(data: AppData, monthStart: Long, monthEnd: Long): List<CategoryBalance> {
        val inflowMonth = data.distributions.filter { it.date in monthStart..monthEnd }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        val outflowMonth = data.expenses.filter { it.date in monthStart..monthEnd }
            .groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val transferInMonth = data.transfers.filter { it.date in monthStart..monthEnd }
        val transferOutMonth = transferInMonth
        val inflowMonthWithTransfers = inflowMonth.toMutableMap()
        transferInMonth.forEach { t ->
            inflowMonthWithTransfers[t.toCategoryId] =
                (inflowMonthWithTransfers[t.toCategoryId] ?: 0.0) + t.amount
        }
        val outflowMonthWithTransfers = outflowMonth.toMutableMap()
        transferOutMonth.forEach { t ->
            outflowMonthWithTransfers[t.fromCategoryId] =
                (outflowMonthWithTransfers[t.fromCategoryId] ?: 0.0) + t.amount
        }

        val inflowAll = data.distributions.groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        val outflowAll = data.expenses.groupBy { it.categoryId }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
        val inflowAllWithTransfers = inflowAll.toMutableMap()
        val outflowAllWithTransfers = outflowAll.toMutableMap()
        data.transfers.forEach { t ->
            inflowAllWithTransfers[t.toCategoryId] =
                (inflowAllWithTransfers[t.toCategoryId] ?: 0.0) + t.amount
            outflowAllWithTransfers[t.fromCategoryId] =
                (outflowAllWithTransfers[t.fromCategoryId] ?: 0.0) + t.amount
        }

        return data.categories.map { category ->
            val allocMonth = inflowMonthWithTransfers[category.id] ?: 0.0
            val spendMonth = outflowMonthWithTransfers[category.id] ?: 0.0
            val allocAll = inflowAllWithTransfers[category.id] ?: 0.0
            val spendAll = outflowAllWithTransfers[category.id] ?: 0.0

            val balance = when (category.type) {
                CategoryType.INSTANT_PAY -> allocMonth - spendMonth
                CategoryType.SAVINGS -> allocAll - spendAll
            }

            val status = when {
                balance < -0.005 -> CategoryStatus.OVERSPEND
                category.monthlyPlan > 0.005 && allocMonth >= category.monthlyPlan - 0.005 ->
                    CategoryStatus.FULL
                allocMonth > 0.005 -> CategoryStatus.PARTIAL
                else -> CategoryStatus.NONE
            }

            val remaining = max(0.0, category.monthlyPlan - allocMonth)
            val monthProgress =
                if (category.monthlyPlan <= 0.005) 0f
                else (allocMonth / category.monthlyPlan).coerceIn(0.0, 1.0).toFloat()
            val targetProgress =
                if (category.targetAmount == null || category.targetAmount <= 0.005) 0f
                else (balance / category.targetAmount).coerceIn(0.0, 1.0).toFloat()

            CategoryBalance(
                category = category,
                allocatedThisMonth = allocMonth,
                spentThisMonth = spendMonth,
                allocatedAllTime = allocAll,
                spentAllTime = spendAll,
                balance = balance,
                allTimeBalance = allocAll - spendAll,
                status = status,
                remainingToPlan = remaining,
                monthProgress = monthProgress,
                targetProgress = targetProgress,
            )
        }
    }

    /** Общий баланс по всем счетам (доходы минус расходы за всё время). */
    fun totalBalance(data: AppData): Double {
        val income = data.distributions.sumOf { it.amount } + data.transfers.sumOf { it.amount }
        val expense = data.expenses.sumOf { it.amount } + data.transfers.sumOf { it.amount }
        return income - expense
    }

    /** Сумма, распределённая по счетам за месяц. */
    fun allocatedThisMonth(data: AppData, monthStart: Long, monthEnd: Long): Double =
        data.distributions.filter { it.date in monthStart..monthEnd }.sumOf { it.amount }

    /** Сумма расходов за месяц. */
    fun spentThisMonth(data: AppData, monthStart: Long, monthEnd: Long): Double =
        data.expenses.filter { it.date in monthStart..monthEnd }.sumOf { it.amount }

    /** Сумма поступлений (зарплат) за месяц. */
    fun incomeThisMonth(data: AppData, monthStart: Long, monthEnd: Long): Double =
        data.incomes.filter { it.date in monthStart..monthEnd }.sumOf { it.amount }

    /** Количество поступлений за месяц. */
    fun incomeCountThisMonth(data: AppData, monthStart: Long, monthEnd: Long): Int =
        data.incomes.count { it.date in monthStart..monthEnd }

    /** Расходы за всё время (для общего баланса). */
    fun totalSpentAllTime(data: AppData): Double = data.expenses.sumOf { it.amount }

    /** Поступления за всё время. */
    fun totalIncomeAllTime(data: AppData): Double = data.incomes.sumOf { it.amount }
}
