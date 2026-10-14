package com.paysplit.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.paysplit.app.PaySplitApplication
import com.paysplit.app.domain.AnalyticsEngine
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.util.DateUtils
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Ежедневная проверка напоминаний:
 *  — вечернее напоминание записать расходы;
 *  — предупреждение, если остаток счёта меньше 20 % плана;
 *  — напоминание о годовом платеже за неделю;
 *  — поздравление при достижении цели накопления.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = PaySplitApplication.repository(applicationContext)
        val data = repository.data.first()
        val settings = data.settings
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val todayKey = LocalDateTime.now().toLocalDate().toString()
        val now = System.currentTimeMillis()

        // 1. Вечернее напоминание записать расходы
        if (settings.reminderEnabled) {
            val hasExpensesToday = data.expenses.any { DateUtils.isSameDay(it.date, now) }
            val alreadySent = prefs.getString(KEY_REMINDER_DATE, null) == todayKey
            if (!hasExpensesToday && !alreadySent) {
                NotificationHelper.showReminder(applicationContext)
                prefs.edit().putString(KEY_REMINDER_DATE, todayKey).apply()
            }
        }

        val monthStart = DateUtils.monthStart(now)
        val monthEnd = DateUtils.monthEnd(now)
        val balances = BalanceCalculator.compute(data, monthStart, monthEnd)

        // 2. Предупреждение: остаток счёта меньше 20 % плана
        if (settings.overspendAlertsEnabled) {
            val alreadySent = prefs.getString(KEY_OVERSPEND_DATE, null) == todayKey
            if (!alreadySent) {
                val risky = balances.firstOrNull { balance ->
                    balance.category.isActive &&
                        balance.category.monthlyPlan > 0.005 &&
                        balance.allocatedThisMonth > 0.005 &&
                        balance.balance in 0.0..(balance.category.monthlyPlan * 0.2)
                }
                if (risky != null) {
                    NotificationHelper.showOverspendWarning(
                        applicationContext,
                        risky.category.name,
                        risky.balance,
                        risky.category.monthlyPlan,
                    )
                    prefs.edit().putString(KEY_OVERSPEND_DATE, todayKey).apply()
                }
            }
        }

        // 3. Годовые платежи за неделю
        if (settings.annualAlertsEnabled) {
            data.categories.forEach { category ->
                val targetDate = category.targetDate ?: return@forEach
                val daysLeft = DateUtils.daysBetween(now, targetDate)
                if (daysLeft in 0..7) {
                    NotificationHelper.showAnnualPayment(
                        applicationContext,
                        category.name,
                        DateUtils.formatFullDate(targetDate),
                        daysLeft,
                    )
                }
            }
        }

        // 4. Достижение цели накопления
        if (settings.goalAlertsEnabled) {
            val notified = prefs.getStringSet(KEY_GOALS, emptySet()) ?: emptySet()
            AnalyticsEngine.savingsTargets(data).forEach { (category, balance) ->
                val target = category.targetAmount ?: return@forEach
                if (balance >= target && category.id.toString() !in notified) {
                    NotificationHelper.showGoalReached(applicationContext, category.name)
                    prefs.edit()
                        .putStringSet(KEY_GOALS, notified + category.id.toString())
                        .apply()
                }
            }
        }

        ReminderScheduler.scheduleNext(applicationContext, settings.reminderHour)
        return Result.success()
    }

    private companion object {
        const val PREFS = "paysplit_notifications"
        const val KEY_REMINDER_DATE = "reminder_date"
        const val KEY_OVERSPEND_DATE = "overspend_date"
        const val KEY_GOALS = "notified_goals"
    }
}

/** Планировщик ежедневной проверки напоминаний. */
object ReminderScheduler {

    private const val WORK_NAME = "paysplit_daily_reminder"

    /** Планирует запуск в ближайший час [hour] (или завтра, если время прошло). */
    fun scheduleNext(context: Context, hour: Int = 21) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
