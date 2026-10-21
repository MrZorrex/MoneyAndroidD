package com.paysplit.app.domain

import com.paysplit.app.util.Formatters.round2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Расчёт распределения поступления по счетам.
 *
 * ЛОГИКА:
 *   остаток_категории = план_месяц − уже_распределено (не меньше 0)
 *   доля = остаток_категории / сумма_всех_остатков
 *   перевод = поступление × доля
 *
 * Если поступление больше суммы всех остатков — каждый счёт получает ровно свой
 * остаток, а разница показывается как «Свободные средства».
 */
object DistributionCalculator {

    /** Входные данные по одной категории. */
    data class CategoryInput(
        val id: Long,
        val name: String,
        val icon: String,
        val monthlyPlan: Double,
        val allocatedThisMonth: Double,
    )

    /** Результат расчёта по одной категории. */
    data class Allocation(
        val categoryId: Long,
        val categoryName: String,
        val icon: String,
        val plan: Double,
        val alreadyAllocated: Double,
        /** Сколько ещё нужно распределить до плана. */
        val remainder: Double,
        /** Сколько предлагается перевести этим поступлением. */
        val amount: Double,
    )

    data class Result(
        val allocations: List<Allocation>,
        /** Свободные средства (если поступление покрыло все остатки). */
        val surplus: Double,
    ) {
        val distributedTotal: Double get() = allocations.sumOf { it.amount }
    }

    /** Автоматический пропорциональный расчёт. */
    fun calculate(income: Double, categories: List<CategoryInput>): Result {
        if (categories.isEmpty()) return Result(emptyList(), max(0.0, income))

        val remainders = categories.map { max(0.0, it.monthlyPlan - it.allocatedThisMonth) }
        val totalRemainder = remainders.sum()

        if (income <= 0.0 || totalRemainder <= 0.005) {
            return Result(
                categories.mapIndexed { i, c ->
                    Allocation(
                        categoryId = c.id,
                        categoryName = c.name,
                        icon = c.icon,
                        plan = c.monthlyPlan,
                        alreadyAllocated = c.allocatedThisMonth,
                        remainder = remainders[i],
                        amount = 0.0,
                    )
                },
                surplus = max(0.0, income),
            )
        }

        val capped = min(income, totalRemainder)
        val raw = categories.mapIndexed { i, _ -> capped * (remainders[i] / totalRemainder) }
        val amounts = roundPreservingSum(raw, capped)

        return Result(
            categories.mapIndexed { i, c ->
                Allocation(
                    categoryId = c.id,
                    categoryName = c.name,
                    icon = c.icon,
                    plan = c.monthlyPlan,
                    alreadyAllocated = c.allocatedThisMonth,
                    remainder = remainders[i],
                    amount = amounts[i],
                )
            },
            surplus = round2(income - capped),
        )
    }

    /**
     * Пересчёт после ручной правки одной из сумм: «нехватка»/«излишек»
     * перераспределяется между остальными категориями пропорционально их остаткам.
     */
    fun adjustManually(
        income: Double,
        allocations: List<Allocation>,
        changedCategoryId: Long,
        newAmount: Double,
    ): List<Double> {
        if (allocations.isEmpty()) return emptyList()
        val lockedAmount = newAmount.coerceIn(0.0, max(0.0, income))
        val others = allocations.filter { it.categoryId != changedCategoryId }
        val othersRemainder = others.sumOf { max(0.0, it.remainder - 0.0) }
        val rest = income - lockedAmount

        val result = DoubleArray(allocations.size)
        val indexOf = allocations.mapIndexed { i, a -> a.categoryId to i }.toMap()
        result[indexOf.getValue(changedCategoryId)] = lockedAmount

        if (rest <= 0.0) {
            others.forEach { result[indexOf.getValue(it.categoryId)] = 0.0 }
            return result.toList()
        }

        if (othersRemainder <= 0.005 || rest <= othersRemainder) {
            // Распределяем остаток денег пропорционально остаткам по плану.
            if (othersRemainder <= 0.005) {
                // Все планы закрыты — делим поровну.
                val each = rest / others.size
                others.forEach { result[indexOf.getValue(it.categoryId)] = round2(each) }
            } else {
                val raw = others.map { rest * (max(0.0, it.remainder) / othersRemainder) }
                val rounded = roundPreservingSum(raw, rest)
                others.forEachIndexed { i, a ->
                    result[indexOf.getValue(a.categoryId)] = rounded[i]
                }
            }
        } else {
            // Денег больше, чем нужно остальным счетам: закрываем планы,
            // излишек добавляем последнему (он попадёт в «свободные средства»).
            val raw = others.map { max(0.0, it.remainder) }
            val rounded = roundPreservingSum(raw, othersRemainder)
            others.forEachIndexed { i, a ->
                result[indexOf.getValue(a.categoryId)] = rounded[i]
            }
        }
        return result.toList()
    }

    /**
     * Округление до копеек с сохранением точной суммы:
     * сначала всё усекается вниз, затем «лишние» копейки достаются тем,
     * у кого наибольшая дробная часть.
     */
    fun roundPreservingSum(values: List<Double>, total: Double): List<Double> {
        if (values.isEmpty()) return emptyList()
        val target = (total * 100).roundToLong()
        val cents = values.map { (it * 100).toLong() }
        var delta = (target - cents.sum()).toInt()

        val result = cents.toMutableList()
        if (delta > 0) {
            val order = values.indices.sortedByDescending { frac(values[it]) }
            var i = 0
            while (delta > 0 && order.isNotEmpty()) {
                result[order[i % order.size]] += 1
                delta--
                i++
            }
        } else if (delta < 0) {
            val order = values.indices.sortedBy { frac(values[it]) }
            var i = 0
            while (delta < 0 && order.isNotEmpty()) {
                val idx = order[i % order.size]
                if (result[idx] > 0) {
                    result[idx] -= 1
                    delta++
                }
                i++
                if (i > order.size * 1000) break
            }
        }
        return result.map { it / 100.0 }
    }

    private fun frac(value: Double): Double {
        val scaled = value * 100
        return scaled - floor(scaled)
    }
}
