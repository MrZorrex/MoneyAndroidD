package com.paysplit.app.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Расчёт распределения поступления по счетам — только целые рубли.
 *
 * ЛОГИКА:
 *   остаток = план − уже_распределено (≥0, округляем до целого)
 *   доля = остаток / сумма_остатков
 *   перевод = поступление × доля → округляем до целых с сохранением суммы
 */
object DistributionCalculator {

    data class CategoryInput(
        val id: Long,
        val name: String,
        val icon: String,
        val monthlyPlan: Double,
        val allocatedThisMonth: Double,
    )

    data class Allocation(
        val categoryId: Long,
        val categoryName: String,
        val icon: String,
        val plan: Double,
        val alreadyAllocated: Double,
        val remainder: Double,
        val amount: Double,
    )

    data class Result(
        val allocations: List<Allocation>,
        val surplus: Double,
    ) {
        val distributedTotal: Double get() = allocations.sumOf { it.amount }
    }

    fun calculate(income: Double, categories: List<CategoryInput>): Result {
        val incomeInt = Math.round(income).toDouble()
        if (categories.isEmpty()) return Result(emptyList(), max(0.0, incomeInt))

        val remainders = categories.map { max(0.0, Math.round(it.monthlyPlan - it.allocatedThisMonth).toDouble()) }
        val totalRemainder = remainders.sum()

        if (incomeInt <= 0.0 || totalRemainder <= 0.5) {
            return Result(
                categories.mapIndexed { i, c ->
                    Allocation(c.id, c.name, c.icon, Math.round(c.monthlyPlan).toDouble(), Math.round(c.allocatedThisMonth).toDouble(), remainders[i], 0.0)
                },
                surplus = max(0.0, incomeInt),
            )
        }

        val capped = min(incomeInt, totalRemainder)
        val raw = categories.mapIndexed { i, _ -> capped * (remainders[i] / totalRemainder) }
        val amounts = roundPreservingSum(raw, capped)

        return Result(
            categories.mapIndexed { i, c ->
                Allocation(c.id, c.name, c.icon, Math.round(c.monthlyPlan).toDouble(), Math.round(c.allocatedThisMonth).toDouble(), remainders[i], amounts[i])
            },
            surplus = Math.round(incomeInt - capped).toDouble(),
        )
    }

    fun adjustManually(
        income: Double,
        allocations: List<Allocation>,
        changedCategoryId: Long,
        newAmount: Double,
    ): List<Double> {
        val incomeInt = Math.round(income).toDouble()
        val newAmountInt = Math.round(newAmount).toDouble()
        if (allocations.isEmpty()) return emptyList()
        val lockedAmount = newAmountInt.coerceIn(0.0, max(0.0, incomeInt))
        val others = allocations.filter { it.categoryId != changedCategoryId }
        val othersRemainder = others.sumOf { max(0.0, it.remainder) }
        val rest = incomeInt - lockedAmount

        val result = DoubleArray(allocations.size)
        val indexOf = allocations.mapIndexed { i, a -> a.categoryId to i }.toMap()
        result[indexOf.getValue(changedCategoryId)] = lockedAmount

        if (rest <= 0.0) {
            others.forEach { result[indexOf.getValue(it.categoryId)] = 0.0 }
            return result.toList()
        }

        if (othersRemainder <= 0.5 || rest <= othersRemainder) {
            if (othersRemainder <= 0.5) {
                val each = Math.round(rest / others.size).toDouble()
                // Для поровну нужно точное сохранение суммы в целых
                val rawEach = others.map { rest / others.size }
                val rounded = roundPreservingSum(rawEach, rest)
                others.forEachIndexed { i, a -> result[indexOf.getValue(a.categoryId)] = rounded[i] }
            } else {
                val raw = others.map { rest * (max(0.0, it.remainder) / othersRemainder) }
                val rounded = roundPreservingSum(raw, rest)
                others.forEachIndexed { i, a -> result[indexOf.getValue(a.categoryId)] = rounded[i] }
            }
        } else {
            val raw = others.map { max(0.0, it.remainder) }
            val rounded = roundPreservingSum(raw, othersRemainder)
            others.forEachIndexed { i, a -> result[indexOf.getValue(a.categoryId)] = rounded[i] }
        }
        return result.toList()
    }

    /** Округление до целых рублей с сохранением суммы. */
    fun roundPreservingSum(values: List<Double>, total: Double): List<Double> {
        if (values.isEmpty()) return emptyList()
        val target = Math.round(total)
        val ints = values.map { it.toLong() } // floor
        var delta = (target - ints.sum()).toInt()
        val result = ints.toMutableList()
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
        return result.map { it.toDouble() }
    }

    private fun frac(value: Double): Double = value - floor(value)
}
