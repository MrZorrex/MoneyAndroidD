package com.paysplit.app.domain

import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.CategoryStatus
import com.paysplit.app.data.model.CategoryType
import kotlin.math.max

/**
 * Расчёт балансов счетов.
 *
 * Для категорий «Оплата сразу»: баланс = распределено за месяц − потрачено за месяц + коррекции.
 * Для накопительных: баланс = все поступления за всё время − все расходы за всё время + коррекции.
 * Переводы и коррекции уменьшают/увеличивают баланс соответственно дате.
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

        // Коррекции баланса: положительные — приход, отрицательные — расход
        val adjustmentMonth = data.adjustments.filter { it.date in monthStart..monthEnd }
            .groupBy { it.categoryId }
            .mapValues { e -> e.value.sumOf { it.amount } }
        // Для накопительных учитываем все коррекции, для instant — только месячные
        val adjustmentMonthPositive = adjustmentMonth.mapValues { max(0.0, it.value) }
        val adjustmentMonthNegative = adjustmentMonth.mapValues { -minOf(0.0, it.value) }

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
        // Все коррекции для all-time
        val adjustmentAll = data.adjustments.groupBy { it.categoryId }
            .mapValues { e -> e.value.sumOf { it.amount } }
        // Добавляем коррекции к all-time
        adjustmentAll.forEach { (catId, delta) ->
            if (delta >= 0) {
                inflowAllWithTransfers[catId] = (inflowAllWithTransfers[catId] ?: 0.0) + delta
            } else {
                outflowAllWithTransfers[catId] = (outflowAllWithTransfers[catId] ?: 0.0) + (-delta)
            }
        }
        // Добавляем месячные коррекции к месячным показателям
        // (разделяем на приход/расход)
        val inflowMonthFinal = inflowMonthWithTransfers.toMutableMap()
        val outflowMonthFinal = outflowMonthWithTransfers.toMutableMap()
        adjustmentMonth.forEach { (catId, delta) ->
            if (delta >= 0) {
                inflowMonthFinal[catId] = (inflowMonthFinal[catId] ?: 0.0) + delta
            } else {
                outflowMonthFinal[catId] = (outflowMonthFinal[catId] ?: 0.0) + (-delta)
            }
        }

        return data.categories.map { category ->
            val allocMonth = inflowMonthFinal[category.id] ?: 0.0
            val spendMonth = outflowMonthFinal[category.id] ?: 0.0
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

            val remaining = category.monthlyPlan - allocMonth
            val monthProgress =
                if (category.monthlyPlan <= 0.005) 0f
                else (allocMonth / category.monthlyPlan).toFloat().coerceAtLeast(0f)
            val targetProgress =
                if (category.targetAmount == null || category.targetAmount <= 0.005) 0f
                else (balance / category.targetAmount).toFloat().coerceAtLeast(0f)

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

    /** Общий баланс — сумма всех поступлений (по требованию: все доходы). */
    fun totalBalance(data: AppData): Double = data.incomes.sumOf { it.amount }

    /** Сумма, распределённая по счетам за месяц. */
    fun allocatedThisMonth(data: AppData, monthStart: Long, monthEnd: Long): Double =
        data.distributions.filter { it.date in monthStart..monthEnd }.sumOf { it.amount } +
            data.adjustments.filter { it.date in monthStart..monthEnd && it.amount > 0 }.sumOf { it.amount }

    /** Сумма расходов за месяц. */
    fun spentThisMonth(data: AppData, monthStart: Long, monthEnd: Long): Double =
        data.expenses.filter { it.date in monthStart..monthEnd }.sumOf { it.amount } +
            data.adjustments.filter { it.date in monthStart..monthEnd && it.amount < 0 }.sumOf { -it.amount }

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
