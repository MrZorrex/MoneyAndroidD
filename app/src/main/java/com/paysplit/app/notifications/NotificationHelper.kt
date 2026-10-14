package com.paysplit.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.paysplit.app.MainActivity
import com.paysplit.app.R
import com.paysplit.app.util.Formatters

/** Создание каналов и показ уведомлений. */
object NotificationHelper {

    const val CHANNEL_REMINDER = "channel_reminder"
    const val CHANNEL_ALERTS = "channel_alerts"

    private const val NOTIF_REMINDER_ID = 1001
    private const val NOTIF_OVERSPEND_ID = 1002
    private const val NOTIF_ANNUAL_ID = 1003
    private const val NOTIF_GOAL_ID = 1004

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val reminder = NotificationChannel(
            CHANNEL_REMINDER,
            context.getString(R.string.channel_reminder_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_reminder_desc)
        }
        manager.createNotificationChannel(reminder)

        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            context.getString(R.string.channel_alerts_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alerts_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(alerts)
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Вечернее напоминание записать расходы. */
    fun showReminder(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_paysplit)
            .setContentTitle(context.getString(R.string.notif_reminder_title))
            .setContentText(context.getString(R.string.notif_reminder_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_reminder_text)),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        notify(context, NOTIF_REMINDER_ID, notification)
    }

    /** Предупреждение: остаток по счёту меньше 20 % плана. */
    fun showOverspendWarning(
        context: Context,
        categoryName: String,
        balance: Double,
        plan: Double,
    ) {
        val text = "«$categoryName»: осталось ${Formatters.money(balance)} " +
            "из плана ${Formatters.money(plan)}. Пора сэкономить 💸"
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_paysplit)
            .setContentTitle(context.getString(R.string.notif_overspend_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        notify(context, NOTIF_OVERSPEND_ID + categoryName.hashCode() % 100, notification)
    }

    /** Напоминание о предстоящем годовом платеже. */
    fun showAnnualPayment(
        context: Context,
        categoryName: String,
        dateText: String,
        daysLeft: Long,
    ) {
        val text = "«$categoryName»: платёж $dateText (через $daysLeft дн.). " +
            "Проверьте, достаточно ли на счёте."
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_paysplit)
            .setContentTitle(context.getString(R.string.notif_annual_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        notify(context, NOTIF_ANNUAL_ID + categoryName.hashCode() % 100, notification)
    }

    /** Поздравление с достижением цели накопления. */
    fun showGoalReached(context: Context, categoryName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_paysplit)
            .setContentTitle(context.getString(R.string.notif_goal_title))
            .setContentText(context.getString(R.string.notif_goal_text, categoryName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        notify(context, NOTIF_GOAL_ID + categoryName.hashCode() % 100, notification)
    }

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // Проверка разрешения не требуется на API < 33; на 33+ она запрашивается на старте.
        runCatching { manager.notify(id, notification) }
    }
}
