package com.paysplit.app.domain

import com.paysplit.app.util.DateUtils
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * Подсказки для накопительных счетов: автоматический расчёт месячного плана
 * из целевой суммы и даты.
 */
object SavingsPlanner {

    /**
     * Сколько месячных взносов осталось с текущего месяца по месяц цели
     * включительно. Минимум 1 (цель в текущем месяце или в прошлом —
     * значит, нужно доложить всё сразу).
     */
    fun monthsLeft(targetDate: Long, now: Long = System.currentTimeMillis()): Int {
        val from = DateUtils.yearMonth(now)
        val to = DateUtils.yearMonth(targetDate)
        val months = (to.year - from.year) * 12 + (to.monthValue - from.monthValue) + 1
        return max(1, months)
    }

    /**
     * Сколько нужно откладывать в месяц, чтобы накопить [target] к [targetDate]
     * с учётом уже накопленного [saved].
     * Округляется вверх до целого рубля — лучше чуть перекопить, чем не успеть.
     */
    fun monthlyPlan(
        target: Double,
        saved: Double,
        targetDate: Long,
        now: Long = System.currentTimeMillis(),
    ): Double {
        if (target <= 0.0) return 0.0
        val remaining = max(0.0, target - max(0.0, saved))
        if (remaining <= 0.0) return 0.0
        return ceil(remaining / monthsLeft(targetDate, now))
    }

    /** Склонение слова «месяц»: 1 месяц, 3 месяца, 5 месяцев. */
    fun monthsWord(months: Int): String {
        val n = abs(months) % 100
        val d = n % 10
        return when {
            n in 11..14 -> "месяцев"
            d == 1 -> "месяц"
            d in 2..4 -> "месяца"
            else -> "месяцев"
        }
    }
}
