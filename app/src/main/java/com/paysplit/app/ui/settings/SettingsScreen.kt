package com.paysplit.app.ui.settings

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.data.model.ThemeMode
import com.paysplit.app.notifications.ReminderScheduler
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.ChipRow
import com.paysplit.app.ui.components.ConfirmDialog
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.OutlinedNumberField
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.SectionHeader
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.util.Formatters
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * ЭКРАН 9 — Настройки.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showPinDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showFullResetDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    var fullResetConfirmText by remember { mutableStateOf("") }
    var pendingRestart by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
        if (pendingRestart && message.startsWith("Данные восстановлены")) {
            // Room открывает новый файл базы только при перезапуске процесса.
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        pendingRestart = false
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) viewModel.backup(uri)
    }
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestart = true
            viewModel.restore(uri)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { PayTopBar(title = "Настройки", subtitle = "PaySplit") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // --- Распределение зарплаты ---
            SettingsSection(title = "💡 Распределение зарплаты") {
                Text(
                    text = "Каждое поступление распределяется по планам ваших счетов. " +
                        "Если сумма больше планов — излишек попадает в счёт 💵 Свободные средства. " +
                        "Оттуда его можно распределить по счетам кнопкой «Распределить» на главном экране или через пополнение из свободных в разделе «Счета».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- Безопасность ---
            SettingsSection(title = "🔒 Безопасность") {
                Text(
                    text = if (state.settings.pinHash != null) {
                        "PIN-код установлен"
                    } else {
                        "PIN-код не установлен"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = hapticClick { showPinDialog = true }) {
                    Text(
                        text = if (state.settings.pinHash != null) "Сменить PIN-код"
                        else "Установить PIN-код",
                    )
                }
                if (state.settings.pinHash != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            viewModel.setPin(null)
                            viewModel.setBiometric(false)
                        },
                    ) { Text("Отключить PIN-код") }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Вход по отпечатку пальца",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Быстрое разблокирование приложения",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.biometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                authenticateWithBiometric(
                                    context = context,
                                    onSuccess = { viewModel.setBiometric(true) },
                                    onError = { viewModel.setBiometric(false) },
                                )
                            } else {
                                viewModel.setBiometric(false)
                            }
                        },
                    )
                }
            }

            // --- Оформление ---
            SettingsSection(title = "🎨 Оформление") {
                ChipRow(
                    options = ThemeMode.entries.toList(),
                    selected = state.settings.theme,
                    onSelect = viewModel::setTheme,
                    label = { themeLabel(it) },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Material You",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Цвета из обоев системы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.dynamicColors,
                        onCheckedChange = viewModel::setDynamicColors,
                    )
                }
            }

            // --- Уведомления ---
            SettingsSection(title = "🔔 Уведомления") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Вечернее напоминание",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Напомнить внести сегодняшние расходы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.reminderEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setReminderEnabled(enabled)
                            if (enabled) {
                                ReminderScheduler.scheduleNext(
                                    context,
                                    state.settings.reminderHour,
                                )
                            } else {
                                ReminderScheduler.cancel(context)
                            }
                        },
                    )
                }
                if (state.settings.reminderEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Время напоминания: " +
                            formatHour(state.settings.reminderHour),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = state.settings.reminderHour.toFloat(),
                        onValueChange = { value -> viewModel.setReminderHour(value.toInt()) },
                        valueRange = 18f..23f,
                        steps = 4,
                        onValueChangeFinished = {
                            ReminderScheduler.scheduleNext(
                                context,
                                state.settings.reminderHour,
                            )
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                NotificationToggle(
                    title = "Перерасход счёта",
                    checked = state.settings.overspendAlertsEnabled,
                    onCheckedChange = viewModel::setOverspendAlerts,
                )
                NotificationToggle(
                    title = "Годовые платежи",
                    checked = state.settings.annualAlertsEnabled,
                    onCheckedChange = viewModel::setAnnualAlerts,
                )
                NotificationToggle(
                    title = "Достижение целей",
                    checked = state.settings.goalAlertsEnabled,
                    onCheckedChange = viewModel::setGoalAlerts,
                )
            }

            // --- Данные ---
            SettingsSection(title = "💾 Данные") {
                Button(
                    onClick = hapticClick { viewModel.exportCsv(context) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📄 Экспорт в CSV") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = hapticClick { viewModel.exportPdf(context, YearMonth.now()) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("📊 PDF-отчёт за ${monthName(YearMonth.now())}")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        createDocumentLauncher.launch("paysplit_backup_${System.currentTimeMillis()}.db")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("💾 Создать резервную копию") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("♻️ Восстановить из копии") }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = hapticClick { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📦 Сбросить категории к шаблону") }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = hapticClick { showDeleteAllDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🗑️ Удалить все данные") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = hapticClick { showFullResetDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("🔄 Полный сброс приложения") }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Вернуть приложение к состоянию первого запуска: удаляются " +
                        "все операции, категории и настройки, снова показывается " +
                        "стартовый мастер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- О приложении ---
            SettingsSection(title = "ℹ️ О приложении") {
                Text(
                    text = "PaySplit 1.0.0",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Все данные хранятся только на этом устройстве. " +
                        "Приложение работает полностью офлайн: без интернета, " +
                        "серверов, рекламы и сбора статистики.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPinDialog) {
        PinDialog(
            hasExistingPin = state.settings.pinHash != null,
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                viewModel.setPin(pin)
                showPinDialog = false
            },
            onVerify = { pin -> viewModel.verifyPin(pin, state.settings.pinHash) },
        )
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = "Сброс категорий",
            text = "Все категории будут заменены шаблоном. Операции сохранятся.",
            confirmText = "Сбросить",
            onConfirm = {
                viewModel.resetCategories()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
        )
    }

    if (showDeleteAllDialog) {
        DeleteAllDialog(
            confirmText = deleteConfirmText,
            onConfirmTextChange = { deleteConfirmText = it },
            onDismiss = {
                showDeleteAllDialog = false
                deleteConfirmText = ""
            },
            onConfirm = {
                viewModel.resetAll()
                showDeleteAllDialog = false
                deleteConfirmText = ""
            },
        )
    }

    if (showFullResetDialog) {
        DeleteAllDialog(
            confirmText = fullResetConfirmText,
            onConfirmTextChange = { fullResetConfirmText = it },
            onDismiss = {
                showFullResetDialog = false
                fullResetConfirmText = ""
            },
            onConfirm = {
                viewModel.fullReset()
                showFullResetDialog = false
                fullResetConfirmText = ""
            },
            title = "Полный сброс приложения",
            text = "Будут удалены ВСЕ данные: доходы, расходы, переводы, категории " +
                "и настройки (включая PIN-код). Приложение вернётся к состоянию " +
                "первого запуска и покажет стартовый мастер. Действие необратимо.",
            confirmButtonText = "Сбросить всё",
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    PayCard(modifier = Modifier.padding(vertical = 6.dp)) {
        SectionHeader(title = title)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun NotificationToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Диалог установки или смены PIN-кода. */
@Composable
private fun PinDialog(
    hasExistingPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onVerify: (String) -> Boolean,
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var repeatPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (hasExistingPin) "Смена PIN-кода" else "Новый PIN-код")
        },
        text = {
            Column {
                if (hasExistingPin) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Текущий PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("Новый PIN (4–6 цифр)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = repeatPin,
                    onValueChange = { repeatPin = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("Повторите PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        hasExistingPin && !onVerify(oldPin) ->
                            error = "Текущий PIN введён неверно"

                        newPin.length < 4 ->
                            error = "PIN должен содержать минимум 4 цифры"

                        newPin != repeatPin ->
                            error = "PIN-коды не совпадают"

                        else -> onConfirm(newPin)
                    }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Диалог удаления всех данных: требуется ввести слово «УДАЛИТЬ». */
@Composable
private fun DeleteAllDialog(
    confirmText: String,
    onConfirmTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String = "Удаление всех данных",
    text: String = "Будут удалены все доходы, расходы, переводы и категории. " +
        "Настройки приложения сохранятся. Действие необратимо.",
    confirmButtonText: String = "Удалить всё",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Для подтверждения введите слово УДАЛИТЬ",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = onConfirmTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = confirmText.isNotEmpty() && confirmText != "УДАЛИТЬ",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmText == "УДАЛИТЬ",
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(confirmButtonText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Запуск системного запроса отпечатка пальца. */
private fun authenticateWithBiometric(
    context: Context,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {
    val activity = context as? FragmentActivity ?: run {
        onError()
        return
    }
    val manager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
    if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onError()
        return
    }
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(code: Int, message: CharSequence) {
            onError()
        }
    }
    val prompt = BiometricPrompt(
        activity,
        executor,
        callback,
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("PaySplit")
            .setSubtitle("Подтвердите вход по отпечатку пальца")
            .setNegativeButtonText("Отмена")
            .setAllowedAuthenticators(authenticators)
            .build(),
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Как в системе"
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
}

private fun formatSettingsAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else value.toString().replace('.', ',')

private fun formatHour(hour: Int): String = "${hour.toString().padStart(2, '0')}:00"

private fun monthName(yearMonth: YearMonth): String =
    yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
