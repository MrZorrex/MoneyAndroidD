package com.paysplit.app.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.PaySplitApplication
import com.paysplit.app.data.model.CategoryType
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.ui.components.AnimatedProgress
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.ChipRow
import com.paysplit.app.ui.components.ConfirmDialog
import com.paysplit.app.ui.components.EmptyState
import com.paysplit.app.ui.components.AreaLineChart
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.MoneyValue
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.SectionHeader
import com.paysplit.app.ui.components.StatTile
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/**
 * ЭКРАН 5 — Детали счёта (категории).
 */
@Composable
fun CategoryDetailScreen(
    categoryId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { PaySplitApplication.repository(context) }
    val viewModel: CategoryDetailViewModel = viewModel(
        factory = remember(categoryId, repository) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CategoryDetailViewModel(repository, categoryId) as T
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showExpenseDialog by remember { mutableStateOf(false) }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<UiTransaction?>(null) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            PayTopBar(
                title = state.categoryName,
                subtitle = if (state.balance?.category?.type == CategoryType.SAVINGS) {
                    "Накопительный счёт"
                } else {
                    "Расходный счёт"
                },
                onBack = onBack,
            )
        },
    ) { padding ->
        val balance = state.balance
        if (balance == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Счёт не найден",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                PayCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(icon = state.categoryIcon, size = 48)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Текущий баланс",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MoneyValue(
                                value = balance.balance,
                                color = if (balance.balance < 0) PayColors.expense
                                else PayColors.income,
                                currency = state.currency,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    AnimatedProgress(
                        progress = balance.monthProgress,
                        color = PayColors.ofStatus(balance.status),
                        height = 8,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Распределено " +
                                Formatters.money(balance.allocatedThisMonth, state.currency) +
                                " из " +
                                Formatters.money(balance.category.monthlyPlan, state.currency),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (balance.isOverspend) {
                                "⚠️ Перерасход " +
                                    Formatters.money(-balance.balance, state.currency)
                            } else {
                                "Осталось " +
                                    Formatters.money(balance.remainingToPlan, state.currency)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (balance.isOverspend) PayColors.expense
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        title = "Поступления",
                        value = Formatters.money(
                            state.monthlyStats.lastOrNull()?.income ?: 0.0,
                            state.currency,
                        ),
                        modifier = Modifier.weight(1f),
                        valueColor = PayColors.income,
                    )
                    StatTile(
                        title = "Расходы",
                        value = Formatters.money(
                            state.monthlyStats.lastOrNull()?.expense ?: 0.0,
                            state.currency,
                        ),
                        modifier = Modifier.weight(1f),
                        valueColor = PayColors.expense,
                    )
                }
            }

            item {
                PayCard {
                    SectionHeader(
                        title = "Баланс за 6 месяцев",
                        subtitle = "На конец каждого месяца",
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.monthlyBalance.any { it.second != 0.0 }) {
                        AreaLineChart(
                            values = state.monthlyBalance.map { it.second },
                            labels = state.monthLabels,
                            height = 140,
                            lineColor = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        EmptyState(
                            icon = "📈",
                            title = "Недостаточно данных",
                            message = "График появится после первых операций",
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = hapticClick { showExpenseDialog = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("➖ Трата") }
                    OutlinedButton(
                        onClick = hapticClick { showTopUpDialog = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("➕ Пополнить") }
                }
            }

            item {
                SectionHeader(
                    title = "Операции счёта",
                    subtitle = "Всего: ${state.transactions.size}",
                )
            }

            if (state.transactions.isEmpty()) {
                item {
                    EmptyState(
                        icon = "🧾",
                        title = "Операций пока нет",
                        message = "Добавьте расход или пополните счёт",
                    )
                }
            } else {
                val grouped = state.transactions.groupBy {
                    DateUtils.formatMonthShort(DateUtils.yearMonth(it.date))
                }
                grouped.forEach { (month, transactions) ->
                    item(key = "header-$month") {
                        Text(
                            text = month,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(transactions, key = { "${it.type}-${it.id}-${it.date}" }) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            currency = state.currency,
                            onDelete = { transactionToDelete = transaction },
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = hapticClick { showDeleteDialog = true },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("🗑️ Удалить счёт «${state.categoryName}»")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showExpenseDialog) {
        QuickExpenseDialog(
            onDismiss = { showExpenseDialog = false },
            onConfirm = { title, amount ->
                viewModel.addQuickExpense(title, amount)
                showExpenseDialog = false
            },
        )
    }

    if (showTopUpDialog) {
        TopUpDialog(
            accounts = state.otherCategories,
            currency = state.currency,
            onDismiss = { showTopUpDialog = false },
            onConfirm = { fromId, amount ->
                viewModel.topUp(fromId, amount)
                showTopUpDialog = false
            },
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Удаление счёта",
            text = "Вместе со счётом «${state.categoryName}» будут удалены все его " +
                "операции и распределения. Действие нельзя отменить. " +
                "Вы можете вместо этого отключить счёт в настройках категорий.",
            confirmText = "Удалить",
            onConfirm = {
                viewModel.deleteCategory()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false },
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
                viewModel.deleteTransaction(transaction)
                transactionToDelete = null
            },
            onDismiss = { transactionToDelete = null },
        )
    }
}

@Composable
private fun TransactionRow(
    transaction: UiTransaction,
    currency: String,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDelete() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(icon = transaction.icon, size = 36)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = DateUtils.formatShortDate(transaction.date) +
                    (transaction.comment?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = (if (transaction.isIncoming) "+" else "−") +
                Formatters.money(transaction.amount, currency),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (transaction.isIncoming) PayColors.income else PayColors.expense,
        )
    }
}

/** Диалог быстрого расхода с этого счёта. */
@Composable
private fun QuickExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➖ Быстрая трата") },
        text = {
            Column {
                MoneyInputField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Сумма, ₽",
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
                    if (amount > 0 && title.isNotBlank()) onConfirm(title.trim(), amount)
                },
                enabled = (com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0) > 0 &&
                    title.isNotBlank(),
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Диалог пополнения счёта переводом с другого счёта. */
@Composable
private fun TopUpDialog(
    accounts: List<Pair<Long, String>>,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (Long, Double) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var fromId by remember { mutableStateOf(accounts.firstOrNull()?.first ?: 0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➕ Пополнить счёт") },
        text = {
            Column {
                Text(
                    text = "Списать со счёта:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = accounts.map { it.first },
                    selected = fromId,
                    onSelect = { fromId = it },
                    label = { id -> accounts.firstOrNull { it.first == id }?.second ?: "?" },
                )
                Spacer(Modifier.height(12.dp))
                MoneyInputField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Сумма перевода, $currency",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
                    if (amount > 0 && fromId != 0L) onConfirm(fromId, amount)
                },
                enabled = (com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0) > 0 &&
                    fromId != 0L,
            ) { Text("Перевести") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
