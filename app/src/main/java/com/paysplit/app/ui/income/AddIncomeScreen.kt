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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.PaySplitApplication
import com.paysplit.app.data.model.IncomeSource
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.CategorySettingsDialog
import com.paysplit.app.ui.components.ChipRow
import com.paysplit.app.ui.components.DateField
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

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
    var editingCategoryId by remember { mutableStateOf<Long?>(null) }

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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    text = "Источник",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
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
                    label = { Text("Комментарий", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
            }

            item {
                PayCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Распределение",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        if (state.manualMode) {
                            TextButton(onClick = viewModel::resetAuto, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                Text("Авто", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (state.excludedIds.isNotEmpty()) {
                            Text(
                                "${state.excludedIds.size} откл.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            TextButton(onClick = { viewModel.setAllEnabled(true) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                Text("Вкл.все", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (state.manualMode) "Ручная правка — меняете одну, остальные пересчитаются"
                        else "Снимите ☑ у лишнего счёта (Авто уже пополнен) — деньги уйдут остальным",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))

                    DistributionTable(
                        state = state,
                        viewModel = viewModel,
                        onEditCategory = { editingCategoryId = it },
                    )

                    if (state.surplus > 0.5) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "💵 Свободно: ${Formatters.money(state.surplus, state.currency)} — планы закрыты",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (state.undistributed > 0.5 && state.manualMode) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Нераспределено: ${Formatters.money(state.undistributed, state.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = PayColors.warning,
                            maxLines = 1,
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    InfoLine(label = "Распределено", value = Formatters.money(state.distributed, state.currency), bold = true)
                    InfoLine(label = "Поступление", value = Formatters.money(state.amount, state.currency))
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = hapticClick { viewModel.save() },
                        enabled = state.isAmountValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("✅ Подтвердить", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            if (state.manualMode) viewModel.resetAuto()
                            else viewModel.startManualAdjustment()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.manualMode) "↩️ Авто" else "✏️ Скорректировать", style = MaterialTheme.typography.labelSmall) }
                    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Отмена", style = MaterialTheme.typography.labelSmall) }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    val editingCategory = editingCategoryId?.let { state.categoryById[it] }
    if (editingCategory != null) {
        CategorySettingsDialog(
            category = editingCategory,
            currency = state.currency,
            onDismiss = { editingCategoryId = null },
            onSave = { updated ->
                if (updated.monthlyPlan != editingCategory.monthlyPlan || updated.isActive != editingCategory.isActive) viewModel.resetAuto()
                viewModel.saveCategory(updated)
                editingCategoryId = null
            },
        )
    }
}

private val TableCheckboxWidth = 28.dp
private val TableEditWidth = 28.dp
private val TablePlanWidth = 62.dp
private val TableAlreadyWidth = 62.dp
private val TableNowWidth = 68.dp

@Composable
private fun DistributionTable(
    state: AddIncomeUiState,
    viewModel: AddIncomeViewModel,
    onEditCategory: (Long) -> Unit,
) {
    val manualInputs = remember(state.manualMode) { mutableStateMapOf<Long, String>() }

    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        // Заголовок — строго те же ширины что и строки
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(TableCheckboxWidth))
            Text("", Modifier.width(24.dp)) // аватар placeholder
            Spacer(Modifier.width(4.dp))
            Text(
                "Счёт",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            Text("План", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(TablePlanWidth).padding(horizontal = 2.dp))
            Text("Уже", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(TableAlreadyWidth).padding(horizontal = 2.dp))
            Text("Сейчас", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(TableNowWidth).padding(horizontal = 2.dp))
            Spacer(Modifier.width(TableEditWidth))
        }
        Spacer(Modifier.height(4.dp))

        state.allocations.forEach { allocation ->
            val isExcluded = allocation.categoryId in state.excludedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .alpha(if (isExcluded) 0.45f else 1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = !isExcluded,
                    onCheckedChange = { viewModel.toggleCategory(allocation.categoryId) },
                    modifier = Modifier.size(20.dp).scale(0.85f),
                    colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.outline)
                )
                Spacer(Modifier.width(2.dp))
                CategoryAvatar(icon = allocation.icon, size = 22)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = allocation.categoryName,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 2.dp),
                )
                // План
                Text(
                    Formatters.money(allocation.plan, ""),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(TablePlanWidth).padding(horizontal = 2.dp),
                )
                // Уже
                Text(
                    Formatters.money(allocation.alreadyAllocated, ""),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(TableAlreadyWidth).padding(horizontal = 2.dp),
                )
                // Сейчас
                if (isExcluded) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.width(TableNowWidth).padding(horizontal = 2.dp),
                    )
                } else if (state.manualMode) {
                    val current = manualInputs.getOrPut(allocation.categoryId) { formatInput(allocation.amount) }
                    OutlinedTextField(
                        value = current,
                        onValueChange = { input ->
                            val filtered = input.filter { it.isDigit() }
                            manualInputs[allocation.categoryId] = filtered
                            com.paysplit.app.ui.components.parseMoney(filtered)?.let { value ->
                                viewModel.setManualAmount(allocation.categoryId, value)
                            } ?: run {
                                if (filtered.isEmpty()) viewModel.setManualAmount(allocation.categoryId, 0.0)
                            }
                        },
                        modifier = Modifier.width(TableNowWidth).padding(start = 2.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, textAlign = TextAlign.End),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                } else {
                    Text(
                        Formatters.money(allocation.amount, ""),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = PayColors.income,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.width(TableNowWidth).padding(horizontal = 2.dp),
                    )
                }
                // Карандаш — компактнее
                TextButton(
                    onClick = { onEditCategory(allocation.categoryId) },
                    modifier = Modifier.width(TableEditWidth).height(20.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    enabled = !isExcluded,
                ) {
                    Text("✏️", fontSize = 11.sp, modifier = Modifier.alpha(if (isExcluded) 0.3f else 1f))
                }
            }
        }

        if (state.allocations.isEmpty()) {
            Text("Нет счетов. Добавьте во вкладке Счета.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

private fun formatInput(value: Double): String = Math.round(value).toString()
