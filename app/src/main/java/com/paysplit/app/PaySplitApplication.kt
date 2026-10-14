package com.paysplit.app

import android.app.Application
import android.content.Context
import com.paysplit.app.data.backup.BackupManager
import com.paysplit.app.data.local.AppDatabase
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.notifications.NotificationHelper
import com.paysplit.app.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Простейший контейнер зависимостей (приложение одмодульное, DI не нужен). */
class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val repository: PaySplitRepository by lazy { PaySplitRepository(database) }
    val backupManager: BackupManager by lazy { BackupManager(appContext, database) }
}

class PaySplitApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        appScope.launch {
            val repository = container.repository
            repository.ensureSettings()
            val settings = repository.data.first().settings
            if (settings.reminderEnabled || settings.overspendAlertsEnabled ||
                settings.annualAlertsEnabled || settings.goalAlertsEnabled
            ) {
                ReminderScheduler.scheduleNext(this@PaySplitApplication, settings.reminderHour)
            }
        }
    }

    companion object {
        fun repository(context: Context): PaySplitRepository =
            (context.applicationContext as PaySplitApplication).container.repository

        fun container(context: Context): AppContainer =
            (context.applicationContext as PaySplitApplication).container
    }
}
