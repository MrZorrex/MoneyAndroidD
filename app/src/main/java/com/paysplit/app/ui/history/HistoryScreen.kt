package com.paysplit.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.paysplit.app.data.model.HistoryPeriod
import com.paysplit.app.data.model.TxFilter
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.data.model.TxType
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.ConfirmDialog
import com.paysplit.app.ui.components.DateField
import com.paysplit.app.ui.components.EmptyState
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.StatTile
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/**
 * История операций — компактный верхний блок + единый скролл.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = LocalViewModelFactory.current),
    onEditIncome: (Long) -> Unit = {},
    onEditExpense: (Long) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var transactionForAction by remember { mutableStateOf<UiTransaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<UiTransaction?>(null) }
    var transactionToEdit by remember { mutableStateOf<UiTransaction?>(null) }

    Scaffold(
        topBar = { PayTopBar(title = "История", subtitle = "Все приходы, уходы и изменения") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Компактный верхний блок — фильтры
            item {
                PayCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(6.dp)) {
                        Text(
                            text = "Период",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PeriodFilterRow(
                            selected = state.filters.period,
                            onSelect = viewModel::setPeriod,
                        )
                        if (state.filters.period == HistoryPeriod.CUSTOM) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    DateField(
                                        date = state.filters.customFrom,
                                        onDateChange = { viewModel.setCustomRange(it, state.filters.customTo) },
                                        label = "С даты",
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    DateField(
                                        date = state.filters.customTo,
                                        onDateChange = { viewModel.setCustomRange(state.filters.customFrom, it) },
                                        label = "По дату",
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Тип",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TypeFilterRow(
                            selected = state.filters.type,
                            onSelect = viewModel::setType,
                        )
                        if (state.categories.isNotEmpty()) {
                            Text(
                                text = "Категория",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CategoryFilterRow(
                                categories = state.categories,
                                selected = state.filters.categoryId,
                                onSelect = viewModel::setCategory,
                            )
                        }
                    }
                }
            }

            // Итоги — компактно
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatTile(
                        title = "Доходы",
                        value = Formatters.money(state.totalIncome, state.currency),
                        valueColor = PayColors.income,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        title = "Расходы",
                        value = Formatters.money(state.totalExpense, state.currency),
                        valueColor = PayColors.expense,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        title = "Итог",
                        value = Formatters.money(state.balance, state.currency),
                        valueColor = if (state.balance >= 0) PayColors.income else PayColors.expense,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.groups.isEmpty()) {
                item {
                    EmptyState(
                        icon = "🔍",
                        title = "Операций не найдено",
                        message = "Измените фильтры или добавьте операции",
                    )
                }
            } else {
                state.groups.forEach { group ->
                    item(key = "day-${group.dayStart}", contentType = "dayHeader") {
                        DayHeader(group = group, currency = state.currency)
                    }
                    items(
                        items = group.transactions,
                        key = { "${it.type}-${it.id}-${it.date}" },
                        contentType = { "txRow" },
                    ) { transaction ->
                        HistoryRow(
                            transaction = transaction,
                            currency = state.currency,
                            onClick = { transactionForAction = transaction },
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Диалог выбора действия: изменить / удалить
    transactionForAction?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionForAction = null },
            title = { Text(tx.title, style = MaterialTheme.typography.titleSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${Formatters.money(tx.amount, state.currency)} · ${DateUtils.formatFullDate(tx.date)}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        historySubtitle(tx),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        transactionForAction = null
                        when (tx.type) {
                            TxType.INCOME -> onEditIncome(tx.sourceId)
                            TxType.EXPENSE -> onEditExpense(tx.sourceId)
                            TxType.TRANSFER_OUT, TxType.TRANSFER_IN -> transactionToEdit = tx
                            TxType.ADJUSTMENT -> transactionToEdit = tx
                        }
                    }
                ) { Text("Изменить") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { transactionToDelete = tx; transactionForAction = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { transactionForAction = null }) { Text("Отмена") }
                }
            }
        )
    }

    transactionToDelete?.let { transaction ->
        ConfirmDialog(
            title = "Удаление операции",
            text = "Удалить «${transaction.title}» на " +
                "${Formatters.money(transaction.amount, state.currency)} " +
                "от ${DateUtils.formatFullDate(transaction.date)}?",
            confirmText = "Удалить",
            onConfirm = {
                viewModel.delete(transaction)
                transactionToDelete = null
            },
            onDismiss = { transactionToDelete = null },
        )
    }

    // Диалоги редактирования для переводов и коррекций
    transactionToEdit?.let { tx ->
        when (tx.type) {
            TxType.TRANSFER_OUT, TxType.TRANSFER_IN -> {
                TransferEditDialog(
                    transaction = tx,
                    currency = state.currency,
                    categories = state.categories,
                    onDismiss = { transactionToEdit = null },
                    onConfirm = { updated ->
                        viewModel.updateTransfer(updated)
                        transactionToEdit = null
                    }
                )
            }
            TxType.ADJUSTMENT -> {
                AdjustmentEditDialog(
                    transaction = tx,
                    currency = state.currency,
                    onDismiss = { transactionToEdit = null },
                    onConfirm = { updated ->
                        viewModel.updateAdjustment(updated)
                        transactionToEdit = null
                    }
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun PeriodFilterRow(
    selected: HistoryPeriod,
    onSelect: (HistoryPeriod) -> Unit,
) {
    val labels = mapOf(
        HistoryPeriod.THIS_MONTH to "Месяц",
        HistoryPeriod.LAST_MONTH to "Прошлый",
        HistoryPeriod.THREE_MONTHS to "3 мес",
        HistoryPeriod.HALF_YEAR to "6 мес",
        HistoryPeriod.YEAR to "Год",
        HistoryPeriod.ALL to "Всё",
        HistoryPeriod.CUSTOM to "Свой",
    )
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
    ) {
        items(labels.keys.toList()) { period ->
            androidx.compose.material3.FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(labels[period] ?: period.name, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun TypeFilterRow(selected: TxFilter, onSelect: (TxFilter) -> Unit) {
    val labels = mapOf(
        TxFilter.ALL to "Все",
        TxFilter.INCOME to "Доходы",
        TxFilter.EXPENSE to "Расходы",
        TxFilter.TRANSFERS to "Переводы",
        TxFilter.ADJUSTMENT to "Коррекции",
    )
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
    ) {
        items(labels.keys.toList()) { type ->
            androidx.compose.material3.FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(labels[type] ?: type.name, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    categories: List<com.paysplit.app.data.model.CategoryEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
    ) {
        item {
            androidx.compose.material3.FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Все", style = MaterialTheme.typography.labelSmall) },
            )
        }
        items(categories, key = { it.id }) { category ->
            androidx.compose.material3.FilterChip(
                selected = selected == category.id,
                onClick = { onSelect(category.id) },
                label = { Text("${category.icon} ${category.name}", style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup, currency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DateUtils.formatFullDate(group.dayStart),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.weight(1f))
        if (group.income > 0) {
            Text(
                text = "+" + Formatters.money(group.income, currency),
                style = MaterialTheme.typography.labelSmall,
                color = PayColors.income,
            )
            Spacer(Modifier.width(6.dp))
        }
        if (group.expense > 0) {
            Text(
                text = "−" + Formatters.money(group.expense, currency),
                style = MaterialTheme.typography.labelSmall,
                color = PayColors.expense,
            )
        }
    }
}

private fun historySubtitle(transaction: UiTransaction): String {
    val parts = mutableListOf(transaction.categoryName)
    if (!DateUtils.isMidnight(transaction.date)) {
        parts += DateUtils.formatTime(transaction.date)
    }
    transaction.comment?.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}

@Composable
private fun HistoryRow(
    transaction: UiTransaction,
    currency: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(icon = transaction.icon, size = 32)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
            Text(
                text = historySubtitle(transaction),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = (if (transaction.isIncoming) "+" else "−") +
                Formatters.money(transaction.amount, currency),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (transaction.isIncoming) PayColors.income else PayColors.expense,
        )
    }
}

@Composable
private fun TransferEditDialog(
    transaction: UiTransaction,
    currency: String,
    categories: List<com.paysplit.app.data.model.CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (com.paysplit.app.data.model.TransferEntity) -> Unit,
) {
    // Загружаем полный трансфер по sourceId
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { com.paysplit.app.PaySplitApplication.repository(context) }
    var transfer by remember { mutableStateOf<com.paysplit.app.data.model.TransferEntity?>(null) }

    LaunchedEffect(transaction.sourceId) {
        transfer = repository.getTransfer(transaction.sourceId)
    }

    if (transfer == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Загрузка...") }, text = { Text("Идёт загрузка перевода") }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
        return
    }

    var amountText by remember(transfer) { mutableStateOf(transfer!!.amount.toLong().toString()) }
    var commentText by remember(transfer) { mutableStateOf(transfer!!.comment ?: "") }
    var fromId by remember(transfer) { mutableStateOf(transfer!!.fromCategoryId) }
    var toId by remember(transfer) { mutableStateOf(transfer!!.toCategoryId) }
    var date by remember(transfer) { mutableStateOf(transfer!!.date) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить перевод", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Списать с:", style = MaterialTheme.typography.labelSmall)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.filter { it.isActive }.forEach { cat ->
                        val sel = cat.id == fromId
                        androidx.compose.material3.Surface(onClick = { fromId = cat.id }, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("${cat.icon} ${cat.name}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)); if (sel) Text("✓") }
                        }
                    }
                }
                Text("Зачислить на:", style = MaterialTheme.typography.labelSmall)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.filter { it.isActive }.forEach { cat ->
                        val sel = cat.id == toId
                        androidx.compose.material3.Surface(onClick = { toId = cat.id }, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("${cat.icon} ${cat.name}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)); if (sel) Text("✓") }
                        }
                    }
                }
                MoneyInputField(value = amountText, onValueChange = { amountText = it }, label = "Сумма, $currency")
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Комментарий") },
                    singleLine = true,
                )
                DateField(date = date, onDateChange = { date = it }, label = "Дата")
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
                if (amount > 0 && fromId != toId) {
                    onConfirm(transfer!!.copy(amount = amount, fromCategoryId = fromId, toCategoryId = toId, comment = commentText.ifBlank { null }, date = date))
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AdjustmentEditDialog(
    transaction: UiTransaction,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (com.paysplit.app.data.model.BalanceAdjustmentEntity) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember(context) { com.paysplit.app.PaySplitApplication.repository(context) }
    var adj by remember { mutableStateOf<com.paysplit.app.data.model.BalanceAdjustmentEntity?>(null) }

    LaunchedEffect(transaction.sourceId) {
        adj = repository.getAdjustment(transaction.sourceId)
    }

    if (adj == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("Загрузка...") }, text = { Text("Загрузка коррекции") }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
        return
    }

    var amountText by remember(adj) { mutableStateOf(kotlin.math.abs(adj!!.amount).toLong().toString()) }
    var isIncrease by remember(adj) { mutableStateOf((adj!!.amount) >= 0) }
    var commentText by remember(adj) { mutableStateOf(adj!!.comment ?: "") }
    var date by remember(adj) { mutableStateOf(adj!!.date) }
    var sticker by remember(adj) { mutableStateOf(adj!!.sticker ?: "💰") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить коррекцию", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.FilterChip(selected = isIncrease, onClick = { isIncrease = true }, label = { Text("＋ Пополнить") })
                    androidx.compose.material3.FilterChip(selected = !isIncrease, onClick = { isIncrease = false }, label = { Text("− Списать") })
                }
                MoneyInputField(value = amountText, onValueChange = { amountText = it }, label = "Сумма, $currency")
                if (isIncrease) {
                    Text("Стикер:", style = MaterialTheme.typography.labelSmall)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(44.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        gridItems(listOf("💰","💵","💸","🎁","💎","⭐","🎉","🏦","🍀","🚀","💪","🔥","🌟","🎯","💳","🪙")) { s ->
                            val sel = s == sticker
                            androidx.compose.material3.Surface(onClick = { sticker = s }, shape = androidx.compose.foundation.shape.CircleShape, color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.aspectRatio(1f)) {
                                Box(contentAlignment = Alignment.Center) { Text(s, fontSize = 18.sp) }
                            }
                        }
                    }
                }
                OutlinedTextField(value = commentText, onValueChange = { commentText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Комментарий") })
                DateField(date = date, onDateChange = { date = it }, label = "Дата")
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
                if (amount > 0) {
                    onConfirm(adj!!.copy(amount = if (isIncrease) amount else -amount, comment = commentText.ifBlank { null }, date = date, sticker = if (isIncrease) sticker else null))
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
