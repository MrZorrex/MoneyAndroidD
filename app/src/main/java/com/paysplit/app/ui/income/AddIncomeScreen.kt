package com.paysplit.app.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.PaySplitApplication
import com.paysplit.app.data.model.IncomeSource
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.ChipRow
import com.paysplit.app.ui.components.DateField
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/**
 * ЭКРАН 3 — Ввод поступления («Пришла зарплата!»).
 * После ввода суммы автоматически считается распределение по счетам.
 */
@Composable
fun AddIncomeScreen(
    incomeId: Long,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { PaySplitApplication.repository(context) }
    val viewModel: AddIncomeViewModel = viewModel(
        factory = remember(incomeId, repository) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AddIncomeViewModel(repository, incomeId) as T
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            PayTopBar(
                title = if (state.isEditing) "Изменить поступление" else "Пришла зарплата!",
                subtitle = DateUtils.formatFullDate(state.date),
                onBack = onDone,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MoneyInputField(
                    value = state.amountText,
                    onValueChange = viewModel::setAmount,
                    label = "Сумма поступления, ${state.currency}",
                    isError = state.error != null,
                    supportingText = state.error,
                    imeAction = ImeAction.Done,
                )
            }

            item {
                DateField(date = state.date, onDateChange = viewModel::setDate)
            }

            item {
                Text(
                    text = "Откуда поступление",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = IncomeSource.entries.toList(),
                    selected = state.source,
                    onSelect = viewModel::setSource,
                    label = { it.label },
                )
            }

            item {
                OutlinedTextField(
                    value = state.comment,
                    onValueChange = viewModel::setComment,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Комментарий (необязательно)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }

            item {
                PayCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Распределение по счетам",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.manualMode) {
                            TextButton(onClick = viewModel::resetAuto) {
                                Text("Автоматически")
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.manualMode) {
                            "Ручная корректировка: при изменении одной суммы остальные пересчитываются"
                        } else {
                            "Рассчитано пропорционально остаткам по плану"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    DistributionTable(
                        state = state,
                        viewModel = viewModel,
                    )

                    if (state.surplus > 0.005) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "💵 Свободные средства: " +
                                    Formatters.money(state.surplus, state.currency) +
                                    " — все планы на месяц закрыты",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    if (state.undistributed > 0.005 && state.manualMode) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Нераспределено: " +
                                Formatters.money(state.undistributed, state.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PayColors.warning,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    InfoLine(
                        label = "Распределено",
                        value = Formatters.money(state.distributed, state.currency),
                        bold = true,
                    )
                    InfoLine(
                        label = "Поступление",
                        value = Formatters.money(state.amount, state.currency),
                    )
                }
            }

            item {
                Column {
                    Button(
                        onClick = hapticClick { viewModel.save() },
                        enabled = state.isAmountValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "✅ Подтвердить",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (state.manualMode) viewModel.resetAuto()
                            else viewModel.startManualAdjustment()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.manualMode) "↩️ Автоматический расчёт" else "✏️ Скорректировать")
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onDone,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("❌ Отмена") }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DistributionTable(
    state: AddIncomeUiState,
    viewModel: AddIncomeViewModel,
) {
    val manualInputs = remember { mutableStateMapOf<Long, String>() }

    Column {
        // Заголовок таблицы
        Row(modifier = Modifier.fillMaxWidth()) {
            HeaderCell("Категория", 2.2f)
            HeaderCell("План", 1f, TextAlign.End)
            HeaderCell("Уже", 1f, TextAlign.End)
            HeaderCell("Сейчас", 1.2f, TextAlign.End)
        }
        Spacer(Modifier.height(6.dp))

        state.allocations.forEach { allocation ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryAvatar(
                    icon = allocation.icon,
                    size = 32,
                    modifier = Modifier.weight(2.2f),
                )
                Cell(
                    text = Formatters.money(allocation.plan, ""),
                    weight = 1f,
                    align = TextAlign.End,
                )
                Cell(
                    text = Formatters.money(allocation.alreadyAllocated, ""),
                    weight = 1f,
                    align = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.manualMode) {
                    val current = manualInputs.getOrPut(allocation.categoryId) {
                        formatInput(allocation.amount)
                    }
                    OutlinedTextField(
                        value = current,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() || it == '.' || it == ',' }
                            manualInputs[allocation.categoryId] = filtered
                            com.paysplit.app.ui.components.parseMoney(filtered)?.let { value ->
                                viewModel.setManualAmount(allocation.categoryId, value)
                            }
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .padding(start = 4.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                    )
                } else {
                    Cell(
                        text = Formatters.money(allocation.amount, ""),
                        weight = 1.2f,
                        align = TextAlign.End,
                        bold = true,
                        color = PayColors.income,
                    )
                }
            }
        }

        if (state.allocations.isEmpty()) {
            Text(
                text = "Нет активных категорий. Добавьте их в разделе «Категории».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// RowScope нужен, чтобы Modifier.weight() работал внутри этих ячеек таблицы:
// weight — расширение именно на RowScope/ColumnScope.
@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 2.dp),
    )
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start,
    bold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        textAlign = align,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 2.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun formatInput(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else value.toString().replace('.', ',')
