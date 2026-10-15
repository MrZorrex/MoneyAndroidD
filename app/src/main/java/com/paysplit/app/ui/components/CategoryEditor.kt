package com.paysplit.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.CategoryType
import com.paysplit.app.domain.SavingsPlanner

/**
 * Общий набор иконок для счетов.
 * Используется везде, где можно выбрать или поменять картинку категории:
 * в списке категорий, в настройках счёта и на экране «Пришла зарплата».
 */
object CategoryIcons {

    val all: List<String> = listOf(
        "💰", "🏠", "🛒", "📱", "💡", "🧾", "💳", "🏦",
        "🚗", "⛽", "🔧", "📋", "🅿️", "🚌", "🚕", "✈️",
        "🚇", "🚲", "🏍️", "🧳", "⚽", "🎣", "📷", "💻",
        "💊", "🏥", "🏋️", "🦷", "👓", "👶", "🐱", "🐶",
        "🎓", "📚", "🎒", "☕", "🍔", "🍕", "🍺", "🎁",
        "🎉", "🎮", "🎬", "🎵", "🏖️", "💐", "💍", "🧸",
        "👕", "💧", "🔥", "🧹", "🛠️", "📦", "💼", "📈",
        "🔄", "❓",
    )
}

/**
 * Выбор иконки счёта: видна текущая иконка и кнопка,
 * по которой открывается развёрнутая сетка всех иконок.
 */
@Composable
fun CategoryIconPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(icon = selected, size = 48)
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = { expanded = true }) {
            Text("🖼️ Выбрать иконку")
        }
    }

    if (expanded) {
        AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("Иконка счёта") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 56.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(CategoryIcons.all) { icon ->
                        val isSelected = selected == icon
                        Surface(
                            onClick = {
                                onSelect(icon)
                                expanded = false
                            },
                            shape = CircleShape,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.aspectRatio(1f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = icon, fontSize = 26.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { expanded = false }) { Text("Готово") }
            },
        )
    }
}

/**
 * Диалог настроек одного счёта: название, картинка, сумма на месяц,
 * тип счёта, цель и активность.
 *
 * Используется на экране деталей счёта и на экране «Пришла зарплата»,
 * где план/картинку/название нужно менять прямо по месту.
 */
@Composable
fun CategorySettingsDialog(
    category: CategoryEntity,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
    /** Уже накоплено — вычитается из цели при авторасчёте месячного плана. */
    currentBalance: Double = 0.0,
) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    var icon by remember(category.id) { mutableStateOf(category.icon) }
    var planText by remember(category.id) { mutableStateOf(formatEditorAmount(category.monthlyPlan)) }
    var type by remember(category.id) { mutableStateOf(category.type) }
    var targetText by remember(category.id) {
        mutableStateOf(category.targetAmount?.let { formatEditorAmount(it) } ?: "")
    }
    var targetDate by remember(category.id) { mutableStateOf(category.targetDate) }
    var isActive by remember(category.id) { mutableStateOf(category.isActive) }

    // Авторасчёт месячного плана из цели и даты. null — посчитать нельзя,
    // поле плана не трогаем (сохраняем ручной ввод пользователя).
    fun autoPlanFor(targetInput: String, date: Long?, t: CategoryType): String? {
        if (t != CategoryType.SAVINGS || date == null) return null
        val target = parseMoney(targetInput) ?: return null
        if (target <= 0.0) return null
        return formatEditorAmount(SavingsPlanner.monthlyPlan(target, currentBalance, date))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚙️ Настройки счёта") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = capitalizeFirst(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )

                Column {
                    Text(
                        text = "Картинка",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    CategoryIconPicker(selected = icon, onSelect = { icon = it })
                }

                MoneyInputField(
                    value = planText,
                    onValueChange = { planText = it },
                    label = "Собирать в месяц, $currency",
                )
                Text(
                    text = "Сколько хотите выделять на этот счёт каждый месяц. " +
                        "Зарплата распределяется пропорционально этим суммам.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column {
                    Text(
                        text = "Тип счёта",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == CategoryType.INSTANT_PAY,
                            onClick = { type = CategoryType.INSTANT_PAY },
                            label = { Text("💸 Оплата сразу") },
                        )
                        FilterChip(
                            selected = type == CategoryType.SAVINGS,
                            onClick = {
                                type = CategoryType.SAVINGS
                                autoPlanFor(targetText, targetDate, CategoryType.SAVINGS)?.let {
                                    planText = it
                                }
                            },
                            label = { Text("🏦 Накопительный") },
                        )
                    }
                }

                if (type == CategoryType.SAVINGS) {
                    MoneyInputField(
                        value = targetText,
                        onValueChange = {
                            targetText = it
                            autoPlanFor(it, targetDate, type)?.let { plan -> planText = plan }
                        },
                        label = "Целевая сумма, $currency",
                    )
                    OptionalDateField(
                        date = targetDate,
                        onDateChange = {
                            targetDate = it
                            autoPlanFor(targetText, it, type)?.let { plan -> planText = plan }
                        },
                        label = "Дата цели",
                    )
                    SavingsAutoPlanHint(
                        targetText = targetText,
                        targetDate = targetDate,
                        savedAmount = currentBalance,
                        currency = currency,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Активный счёт",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Неактивные счета не участвуют в распределении зарплаты",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val plan = parseMoney(planText) ?: category.monthlyPlan
                    val target = parseMoney(targetText)
                    onSave(
                        category.copy(
                            name = name.trim().ifBlank { category.name },
                            icon = icon,
                            monthlyPlan = plan,
                            type = type,
                            targetAmount = if (type == CategoryType.SAVINGS) target else null,
                            targetDate = if (type == CategoryType.SAVINGS) targetDate else null,
                            isActive = isActive,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
            ) { Text("💾 Сохранить", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun formatEditorAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else value.toString().replace('.', ',')

/** Подсказка: сколько месяцев до цели и какой план нужен в месяц. */
@Composable
private fun SavingsAutoPlanHint(
    targetText: String,
    targetDate: Long?,
    savedAmount: Double,
    currency: String,
) {
    val target = parseMoney(targetText) ?: return
    if (target <= 0.0 || targetDate == null) return
    val months = SavingsPlanner.monthsLeft(targetDate)
    val plan = SavingsPlanner.monthlyPlan(target, savedAmount, targetDate)
    Text(
        text = "💡 До цели осталось: $months ${SavingsPlanner.monthsWord(months)} — " +
            "нужно откладывать " +
            "${com.paysplit.app.util.Formatters.money(plan, currency)} в месяц. " +
            "План пересчитывается сам при изменении цели или даты.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
