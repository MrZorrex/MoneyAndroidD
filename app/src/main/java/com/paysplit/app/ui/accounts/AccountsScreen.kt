package com.paysplit.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.data.model.CategoryType
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.AnimatedProgress
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.CategoryIconPicker
import com.paysplit.app.ui.components.ConfirmDialog
import com.paysplit.app.ui.components.DateField
import com.paysplit.app.ui.components.EmptyState
import com.paysplit.app.ui.components.ExtendedFab
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.StatusDot
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.Formatters

/**
 * ЭКРАН «Счета» — отдельная вкладка для управления счетами.
 * Компактный интерфейс, мелкий шрифт, больше информации на экране.
 */
@Composable
fun AccountsScreen(
    onCategoryClick: (Long) -> Unit,
    viewModel: AccountsViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editorState.collectAsStateWithLifecycle()
    val adjust by viewModel.balanceAdjustState.collectAsStateWithLifecycle()

    var categoryToDelete by remember { mutableStateOf<com.paysplit.app.domain.BalanceCalculator.CategoryBalance?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showFreeFundsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PayTopBar(
                title = "Счета",
                subtitle = "${state.items.size} всего · ${state.items.count { it.category.isActive }} активных · ${Formatters.money(state.totalBalance, state.currency)}",
            )
        },
        floatingActionButton = {
            ExtendedFab(
                text = "Новый счёт",
                icon = Icons.Filled.Add,
                onClick = hapticClick { viewModel.openNew() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                // Compact summary card
                PayCard {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Планы", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.money(state.totalPlan, state.currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text("Доход", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Formatters.money(state.expectedIncome, state.currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text("Баланс", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                Formatters.money(state.totalBalance, state.currency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (state.totalBalance >= 0) PayColors.income else PayColors.expense,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    AnimatedProgress(
                        progress = if (state.totalPlan > 0) (state.expectedIncome / state.totalPlan).toFloat().coerceIn(0f, 1f) else 0f,
                        height = 5,
                        color = if (state.difference >= 0) PayColors.income else PayColors.expense,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.difference >= 0) "✓ Свободно ${Formatters.money(state.difference, state.currency)}" else "⚠ Не хватает ${Formatters.money(-state.difference, state.currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.difference >= 0) PayColors.income else PayColors.expense,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (state.items.isEmpty()) {
                item {
                    EmptyState(icon = "🗂️", title = "Счетов нет", message = "Нажмите «Новый счёт» или сбросьте по шаблону")
                }
            } else {
                items(state.items, key = { it.category.id }) { item ->
                    CompactAccountCard(
                        item = item,
                        currency = state.currency,
                        onClick = { onCategoryClick(item.category.id) },
                        onEdit = { viewModel.openEdit(item.category, item.allTimeBalance) },
                        onAdjust = { viewModel.openBalanceAdjust(item.category, item.balance) },
                        onDelete = { categoryToDelete = item },
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = hapticClick { showFreeFundsDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("💵 Пополнить из свободных средств", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = hapticClick { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text("📦 Сбросить по шаблону", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (editor.visible) {
        AccountEditorDialog(
            editor = editor,
            currency = state.currency,
            onDismiss = viewModel::dismissEditor,
            onNameChange = { viewModel.updateEditor(name = it) },
            onIconChange = { viewModel.updateEditor(icon = it) },
            onPlanChange = { viewModel.updateEditor(planText = it) },
            onTypeChange = { viewModel.updateEditor(type = it) },
            onTargetChange = { viewModel.updateEditor(targetText = it) },
            onTargetDateChange = viewModel::setEditorTargetDate,
            onActiveChange = { viewModel.updateEditor(isActive = it) },
            onSave = viewModel::saveEditor,
        )
    }

    if (adjust.visible) {
        BalanceAdjustDialog(
            state = adjust,
            currency = state.currency,
            onDismiss = viewModel::dismissBalanceAdjust,
            onConfirmDelta = { delta, comment, date, sticker -> viewModel.confirmBalanceAdjust(delta, comment, date, sticker) },
            onConfirmNewBalance = { newBalance, comment, date, sticker -> viewModel.setBalanceTo(newBalance, comment, date, sticker) },
        )
    }

    if (showFreeFundsDialog) {
        FreeFundsDialog(
            accounts = state.items.filter { it.category.isActive },
            currency = state.currency,
            onDismiss = { showFreeFundsDialog = false },
            onConfirm = { targetId, amount, sticker, comment, date ->
                viewModel.topUpFromFreeFunds(targetId, amount, sticker, comment, date)
                showFreeFundsDialog = false
            },
        )
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = "Сброс по шаблону",
            text = "Все текущие счета и планы будут заменены шаблоном. Продолжить?",
            confirmText = "Сбросить",
            onConfirm = { viewModel.resetToTemplate(); showResetDialog = false },
            onDismiss = { showResetDialog = false },
        )
    }

    categoryToDelete?.let { item ->
        ConfirmDialog(
            title = "Удалить счёт",
            text = "Удалить «${item.category.name}»? Все операции счёта удалятся.",
            onConfirm = { viewModel.delete(item.category); categoryToDelete = null },
            onDismiss = { categoryToDelete = null },
        )
    }
}

@Composable
private fun CompactAccountCard(
    item: com.paysplit.app.domain.BalanceCalculator.CategoryBalance,
    currency: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit,
) {
    PayCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
            CategoryAvatar(icon = item.category.icon, size = 28)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    StatusDot(status = item.status)
                    if (!item.category.isActive) {
                        Text(" · off", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.category.type == CategoryType.SAVINGS) {
                        Text(" 🏦", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(
                    text = buildString {
                        append(if (item.category.type == CategoryType.SAVINGS) "Накоп. " else "Расх. ")
                        append("план ${Formatters.money(item.category.monthlyPlan, currency)}")
                        append(" · осталось ${Formatters.money(item.remainingToPlan, currency)}")
                        if (item.category.targetAmount != null) append(" · цель ${Formatters.money(item.category.targetAmount, currency)}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.money(item.balance, currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (item.balance < 0) PayColors.expense else PayColors.income,
                    maxLines = 1,
                )
                Text(
                    text = "${Formatters.money(item.spentThisMonth, currency)} трат",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        AnimatedProgress(progress = item.monthProgress, color = PayColors.ofStatus(item.status), height = 4)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Compact action buttons
            androidx.compose.material3.TextButton(
                onClick = hapticClick(onAdjust),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) { Text("⚖️ Коррекция", style = MaterialTheme.typography.labelSmall) }
            androidx.compose.material3.TextButton(
                onClick = hapticClick(onEdit),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) { Text("✏️", style = MaterialTheme.typography.labelSmall) }
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.TextButton(
                onClick = hapticClick(onDelete),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) { Text("🗑️", style = MaterialTheme.typography.labelSmall) }
            androidx.compose.material3.TextButton(
                onClick = hapticClick(onClick),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) { Text("Открыть ›", style = MaterialTheme.typography.labelSmall) }
        }
        if (item.isOverspend) {
            Spacer(Modifier.height(3.dp))
            Text("⚠ Перерасход ${Formatters.money(-item.balance, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorDialog(
    editor: AccountEditorState,
    currency: String,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onPlanChange: (String) -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onTargetChange: (String) -> Unit,
    onTargetDateChange: (Long?) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text(if (editor.isEditing) "Редактирование счёта" else "Новый счёт", style = MaterialTheme.typography.titleMedium) }
                item {
                    OutlinedTextField(
                        value = editor.name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название", style = MaterialTheme.typography.labelSmall) },
                        placeholder = { Text("Продукты, Авто…", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    )
                }
                item {
                    Text("Иконка", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    CategoryIconPicker(selected = editor.icon, onSelect = onIconChange)
                }
                item { MoneyInputField(value = editor.planText, onValueChange = onPlanChange, label = "План на месяц, $currency", isError = false) }
                item {
                    Text("Тип счёта", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = editor.type == CategoryType.INSTANT_PAY, onClick = { onTypeChange(CategoryType.INSTANT_PAY) }, label = { Text("💸 Оплата сразу", style = MaterialTheme.typography.labelSmall) })
                        FilterChip(selected = editor.type == CategoryType.SAVINGS, onClick = { onTypeChange(CategoryType.SAVINGS) }, label = { Text("🏦 Накопительный", style = MaterialTheme.typography.labelSmall) })
                    }
                    Text(
                        text = if (editor.type == CategoryType.SAVINGS) "Деньги копятся до цели. Остаток переносится." else "Средства тратятся сразу. Остаток переносится.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (editor.type == CategoryType.SAVINGS) {
                    item { MoneyInputField(value = editor.targetText, onValueChange = onTargetChange, label = "Целевая сумма, $currency") }
                    item {
                        com.paysplit.app.ui.components.OptionalDateField(
                            date = editor.targetDate,
                            onDateChange = onTargetDateChange,
                            label = "Дата цели",
                        )
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Активный счёт", style = MaterialTheme.typography.bodyMedium)
                            Text("Неактивные не участвуют в распределении", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = editor.isActive, onCheckedChange = onActiveChange)
                    }
                }
                item {
                    Button(onClick = onSave, enabled = editor.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("💾 Сохранить", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                }
                item { TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Отмена") } }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

object AdjustmentStickers {
    val replenish = listOf("💰", "💵", "💸", "🎁", "💎", "⭐", "🎉", "🏦", "🍀", "🚀", "💪", "🔥", "🌟", "🎯", "💳", "🪙", "🧧", "💚")
}

@Composable
private fun StickerPicker(selected: String, onSelect: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 44.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        gridItems(AdjustmentStickers.replenish) { sticker ->
            val isSelected = selected == sticker
            Surface(
                onClick = { onSelect(sticker) },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.aspectRatio(1f),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(sticker, fontSize = 20.sp) }
            }
        }
    }
}

/** Диалог коррекции баланса с комментарием — два понятных режима. */
@Composable
private fun BalanceAdjustDialog(
    state: BalanceAdjustState,
    currency: String,
    onDismiss: () -> Unit,
    onConfirmDelta: (Double, String?, Long, String?) -> Unit,
    onConfirmNewBalance: (Double, String?, Long, String?) -> Unit,
) {
    var mode by remember { mutableStateOf(0) } // 0 = delta, 1 = set final
    var amountText by remember { mutableStateOf("") }
    var isIncrease by remember { mutableStateOf(true) }
    var newBalanceText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var sticker by remember { mutableStateOf(AdjustmentStickers.replenish.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚖️ Коррекция баланса", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(icon = state.icon, size = 28)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(state.categoryName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Сейчас на счёте: ${Formatters.money(state.currentBalance, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Как это работает:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("• «На сумму» — прибавляет/списывает разницу. • «Установить» — задаёт точный новый баланс.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("➕➖ На сумму", style = MaterialTheme.typography.labelSmall) })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("🎯 Установить баланс", style = MaterialTheme.typography.labelSmall) })
                }
                if (mode == 0) {
                    Text("Режим «На сумму» — укажите сколько прибавить или списать:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = isIncrease, onClick = { isIncrease = true }, label = { Text("＋ Пополнить", style = MaterialTheme.typography.labelSmall) })
                        FilterChip(selected = !isIncrease, onClick = { isIncrease = false }, label = { Text("− Списать", style = MaterialTheme.typography.labelSmall) })
                    }
                    MoneyInputField(value = amountText, onValueChange = { amountText = it }, label = "Сумма коррекции, $currency")
                    if (isIncrease) {
                        Spacer(Modifier.height(4.dp))
                        Text("Стикер пополнения:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        StickerPicker(selected = sticker, onSelect = { sticker = it })
                    }
                    val parsed = com.paysplit.app.ui.components.parseMoney(amountText)
                    if (parsed != null && parsed > 0) {
                        val preview = if (isIncrease) state.currentBalance + parsed else state.currentBalance - parsed
                        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                            Text(
                                "Было ${Formatters.money(state.currentBalance, currency)} ${if (isIncrease) "+" else "−"} ${Formatters.money(parsed, currency)} → станет ${Formatters.money(preview, currency)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        Text("Пример: было 5 000 ₽, ввели 1 000 ₽ на пополнение → станет 6 000 ₽", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("Режим «Установить» — введите какой баланс должен стать:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyInputField(value = newBalanceText, onValueChange = { newBalanceText = it }, label = "Новый баланс, $currency")
                    val parsed = com.paysplit.app.ui.components.parseMoney(newBalanceText)
                    if (parsed != null) {
                        val delta = parsed - state.currentBalance
                        if (delta > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("Стикер пополнения:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            StickerPicker(selected = sticker, onSelect = { sticker = it })
                        }
                        androidx.compose.material3.Surface(color = if (delta >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Text("Было ${Formatters.money(state.currentBalance, currency)} → станет ${Formatters.money(parsed, currency)}", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    "Запишется: ${if (delta >= 0) "+" else ""}${Formatters.money(delta, currency)} ${if (delta >= 0) "пополнение" else "списание"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (delta >= 0) PayColors.income else PayColors.expense,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else {
                        Text("Пример: было 5 000 ₽, ввели 8 000 ₽ → запишется +3 000 ₽ с вашим комментарием", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = com.paysplit.app.ui.components.capitalizeFirst(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Комментарий", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("Напр.: нашёл наличные, исправил ошибку...", style = MaterialTheme.typography.labelSmall) },
                    singleLine = false,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                DateField(date = date, onDateChange = { date = it }, label = "Дата коррекции")
                Text("Коррекция попадёт в историю операций — её можно удалить оттуда.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val canSave = if (mode == 0) (com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0) > 0
            else com.paysplit.app.ui.components.parseMoney(newBalanceText) != null
            val parsedDelta = if (mode == 1) (com.paysplit.app.ui.components.parseMoney(newBalanceText) ?: state.currentBalance) - state.currentBalance else 0.0
            val needsSticker = if (mode == 0) isIncrease else parsedDelta > 0
            Button(
                onClick = {
                    if (mode == 0) {
                        val amount = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
                        if (amount > 0) onConfirmDelta(if (isIncrease) amount else -amount, commentText, date, if (isIncrease) sticker else null)
                    } else {
                        val nb = com.paysplit.app.ui.components.parseMoney(newBalanceText)
                        if (nb != null) onConfirmNewBalance(nb, commentText, date, if ((nb - state.currentBalance) > 0) sticker else null)
                    }
                },
                enabled = canSave
            ) { Text("Сохранить", style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", style = MaterialTheme.typography.labelMedium) } },
    )
}

/** Диалог пополнения из свободных средств — вертикальный выбор счёта + стикер. */
@Composable
private fun FreeFundsDialog(
    accounts: List<com.paysplit.app.domain.BalanceCalculator.CategoryBalance>,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, Double, String?, String?, Long) -> Unit,
) {
    var selectedId by remember { mutableStateOf(accounts.firstOrNull()?.category?.id ?: 0L) }
    var amountText by remember { mutableStateOf("") }
    var sticker by remember { mutableStateOf(AdjustmentStickers.replenish.first()) }
    var commentText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("💵 Пополнить из свободных", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Выберите счёт для пополнения (вертикальный список):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Вертикальный выбор счёта
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    androidx.compose.foundation.rememberScrollState().let { }
                    Column(modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        accounts.forEach { b ->
                            val selected = b.category.id == selectedId
                            Surface(
                                onClick = { selectedId = b.category.id },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    com.paysplit.app.ui.components.CategoryAvatar(icon = b.category.icon, size = 28)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(b.category.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                        Text("Баланс ${Formatters.money(b.balance, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (selected) Text("✓", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
                MoneyInputField(value = amountText, onValueChange = { amountText = it }, label = "Сумма пополнения, $currency")
                Text("Стикер пополнения:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StickerPicker(selected = sticker, onSelect = { sticker = it })
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = com.paysplit.app.ui.components.capitalizeFirst(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Комментарий", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("Напр.: из свободных средств...", style = MaterialTheme.typography.labelSmall) },
                    singleLine = false,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                DateField(date = date, onDateChange = { date = it }, label = "Дата пополнения")
            }
        },
        confirmButton = {
            val amount = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
            Button(
                onClick = { if (amount > 0 && selectedId != 0L) onConfirm(selectedId, amount, sticker, commentText, date) },
                enabled = amount > 0 && selectedId != 0L,
            ) { Text("Пополнить", style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", style = MaterialTheme.typography.labelMedium) } },
    )
}
