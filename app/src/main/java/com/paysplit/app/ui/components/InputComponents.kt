package com.paysplit.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysplit.app.util.DateUtils

/** Парсит введённую сумму; null — ввод некорректен. */
fun parseMoney(text: String): Double? {
    val normalized = text.trim().replace(',', '.').replace(' ', ' ')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    return if (value.isFinite() && value >= 0.0) value else null
}

/**
 * Большое поле ввода суммы с цифровой клавиатурой.
 * Принимает только цифры и один разделитель (запятая или точка).
 */
@Composable
fun MoneyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Сумма, ₽",
    isError: Boolean = false,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = buildString {
                input.forEach { ch ->
                    when {
                        ch.isDigit() -> append(ch)
                        (ch == '.' || ch == ',') && none { it == '.' || it == ',' } -> append('.')
                    }
                }
            }
            onValueChange(filtered)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
    )
}

/** Поле ввода целого числа (например, количество выплат в месяц). */
@Composable
fun OutlinedNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Число",
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(3)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
    )
}

/** Поле даты: нажатие открывает системный DatePicker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: Long,
    onDateChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Дата",
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = DateUtils.formatFullDate(date),
        onValueChange = {},
        readOnly = true,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        trailingIcon = {
            TextButton(onClick = { showDialog = true }) { Text("Изменить") }
        },
    )

    if (showDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = DateUtils.toUtcDateMillis(date))
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { utc ->
                            onDateChange(DateUtils.fromUtcDateMillis(utc))
                        }
                        showDialog = false
                    },
                ) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Ряд фильтр-чипов. */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() },
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
    ) {
        items(options) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/** Верхняя панель экрана. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/** Строка с полем ввода и подписью (для форм). */
@Composable
fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** Ряд кнопок внизу формы. */
@Composable
fun FormButtons(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/**
 * Поле необязательной даты: нажатие открывает системный DatePicker,
 * повторное открытие диалога позволяет очистить значение.
 */
@Composable
fun OptionalDateField(
    date: Long?,
    onDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Дата",
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = date?.let { DateUtils.formatFullDate(it) } ?: "Не выбрана",
        onValueChange = {},
        readOnly = true,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        trailingIcon = {
            Row {
                if (date != null) {
                    TextButton(onClick = { onDateChange(null) }) { Text("Очистить") }
                }
                TextButton(onClick = { showDialog = true }) { Text("Изменить") }
            }
        },
    )

    if (showDialog) {
        val state = rememberDatePickerState(initialSelectedDateMillis = DateUtils.toUtcDateMillis(date ?: System.currentTimeMillis()))
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onDateChange(DateUtils.fromUtcDateMillis(it)) }
                        showDialog = false
                    },
                ) { Text("Выбрать") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
