package com.paysplit.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.backup.BackupManager
import com.paysplit.app.data.export.CsvExporter
import com.paysplit.app.data.export.FileShareHelper
import com.paysplit.app.data.export.PdfReportGenerator
import com.paysplit.app.data.model.SettingsEntity
import com.paysplit.app.data.model.ThemeMode
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.notifications.ReminderScheduler
import com.paysplit.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.YearMonth

data class SettingsUiState(
    val settings: SettingsEntity = SettingsEntity(),
    val busy: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val repository: PaySplitRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<SettingsUiState> =
        combine(repository.data.map { it.settings }, busy, message) { settings, isBusy, msg ->
            SettingsUiState(settings = settings, busy = isBusy, message = msg)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // --- Профиль ---

    fun setExpectedIncome(value: Double) = updateSettings { it.copy(expectedMonthlyIncome = value) }

    fun setPaydayCount(count: Int) = updateSettings {
        it.copy(paydayCount = count.coerceIn(1, 10))
    }

    // --- Безопасность ---

    fun setPin(pin: String?) = updateSettings {
        it.copy(pinHash = pin?.let { value -> hashPin(value) })
    }

    fun verifyPin(pin: String, hash: String?): Boolean =
        hash != null && hashPin(pin) == hash

    fun setBiometric(enabled: Boolean) = updateSettings { it.copy(biometricEnabled = enabled) }

    // --- Оформление ---

    fun setTheme(mode: ThemeMode) = updateSettings { it.copy(theme = mode) }

    fun setDynamicColors(enabled: Boolean) = updateSettings { it.copy(dynamicColors = enabled) }

    // --- Уведомления ---

    fun setReminderEnabled(enabled: Boolean) = updateSettings {
        it.copy(reminderEnabled = enabled)
    }

    fun setReminderHour(hour: Int) = updateSettings {
        it.copy(reminderHour = hour.coerceIn(0, 23))
    }

    fun setOverspendAlerts(enabled: Boolean) =
        updateSettings { it.copy(overspendAlertsEnabled = enabled) }

    fun setAnnualAlerts(enabled: Boolean) =
        updateSettings { it.copy(annualAlertsEnabled = enabled) }

    fun setGoalAlerts(enabled: Boolean) = updateSettings { it.copy(goalAlertsEnabled = enabled) }

    // --- Данные ---

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                val data = repository.snapshot()
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "exports").mkdirs()
                    val target = File(context.cacheDir, "exports/paysplit_transactions.csv")
                    target.writeText(CsvExporter.build(data))
                    target
                }
                FileShareHelper.shareFile(context, file, "text/csv")
                message.value = "CSV готов: ${file.name}"
            }.onFailure { message.value = "Ошибка экспорта: ${it.message}" }
            busy.value = false
        }
    }

    fun exportPdf(context: Context, month: YearMonth = DateUtils.yearMonth(System.currentTimeMillis())) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                val data = repository.snapshot()
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "exports").mkdirs()
                    val target = File(context.cacheDir, "exports/${PdfReportGenerator.fileName(month)}")
                    target.outputStream().use { PdfReportGenerator.generate(data, month, it) }
                    target
                }
                FileShareHelper.shareFile(context, file, "application/pdf")
                message.value = "Отчёт готов: ${file.name}"
            }.onFailure { message.value = "Ошибка экспорта: ${it.message}" }
            busy.value = false
        }
    }

    fun backup(target: Uri) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                backupManager.backupTo(target)
                message.value = "Резервная копия сохранена"
            }.onFailure { message.value = "Ошибка: ${it.message}" }
            busy.value = false
        }
    }

    fun restore(source: Uri) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                backupManager.restoreFrom(source)
                message.value = "Данные восстановлены. Приложение будет перезапущено."
            }.onFailure { message.value = "Ошибка восстановления: ${it.message}" }
            busy.value = false
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                repository.clearAllData()
                message.value = "Все данные удалены"
            }.onFailure { message.value = "Ошибка: ${it.message}" }
            busy.value = false
        }
    }

    /** Полный сброс приложения — как при первом запуске (включая настройки). */
    fun fullReset() {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                repository.fullReset()
                message.value = "Приложение сброшено к первому запуску"
            }.onFailure { message.value = "Ошибка: ${it.message}" }
            busy.value = false
        }
    }

    fun resetCategories() {
        viewModelScope.launch {
            busy.value = true
            runCatching {
                repository.resetCategoriesToTemplate()
                message.value = "Категории сброшены к шаблону"
            }.onFailure { message.value = "Ошибка: ${it.message}" }
            busy.value = false
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    // --- Вспомогательное ---

    private fun updateSettings(block: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch { repository.updateSettings(block) }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("PaySplit::$pin").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
