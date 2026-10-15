package com.paysplit.app.ui.dashboard

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.data.model.CategoryStatus
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.AnimatedProgress
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.CategorySettingsDialog
import com.paysplit.app.ui.components.DonutChart
import com.paysplit.app.ui.components.DonutLegend
import com.paysplit.app.ui.components.DonutSlice
import com.paysplit.app.ui.components.EmptyState
import com.paysplit.app.ui.components.InfoRow
import com.paysplit.app.ui.components.MoneyValue
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.SectionHeader
import com.paysplit.app.ui.components.StatusDot
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.ui.theme.SemanticColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/**
 * ЭКРАН 2 — Главный экран (Dashboard).
 */
@Composable
fun DashboardScreen(
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onCategoryClick: (Long) -> Unit,
    onOpenCategories: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTransfer by remember { mutableStateOf(false) }
    var editingCategoryId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                OutlinedButton(
                    onClick = hapticClick { showTransfer = true },
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    Text("↔️ Перевод")
                }
                ExtendedFloatingActionButton(
                    text = { Text(text = "Пришла зарплата!", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    onClick = hapticClick(onAddIncome),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
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
                Spacer(Modifier.height(8.dp))
                MonthHeaderCard(state = state)
            }

            item {
                PayCard {
                    SectionHeader(
                        title = "Сводка за месяц",
                        subtitle = state.monthLabel,
                        trailing = {
                            Text(
                                text = if (state.monthNet >= 0) "▲" else "▼",
                                color = if (state.monthNet >= 0) PayColors.income
                                else PayColors.expense,
                            )
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            InfoRow(
                                label = "Доходы",
                                value = Formatters.money(state.monthIncome, state.currency),
                                valueColor = PayColors.income,
                            )
                            InfoRow(
                                label = "Расходы",
                                value = Formatters.money(state.monthExpense, state.currency),
                                valueColor = PayColors.expense,
                            )
                            InfoRow(
                                label = "Остаток",
                                value = Formatters.money(state.monthNet, state.currency),
                                valueColor = if (state.monthNet >= 0) PayColors.income
                                else PayColors.expense,
                            )
                            InfoRow(
                                label = "Общий баланс",
                                value = Formatters.money(state.totalBalance, state.currency),
                                valueColor = if (state.totalBalance >= 0) PayColors.income
                                else PayColors.expense,
                            )
                        }
                        if (state.expenseByCategory.isNotEmpty()) {
                            DonutChart(
                                slices = state.expenseByCategory.mapIndexed { index, item ->
                                    DonutSlice(
                                        value = item.amount,
                                        color = SemanticColors.chartPalette[
                                            index % SemanticColors.chartPalette.size
                                        ],
                                        label = item.name,
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                                total = state.monthExpense,
                                strokeWidth = 18.dp,
                                chartSize = 150.dp,
                            )
                        }
                    }
                    if (state.expenseByCategory.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        DonutLegend(
                            slices = state.expenseByCategory.mapIndexed { index, item ->
                                DonutSlice(
                                    value = item.amount,
                                    color = SemanticColors.chartPalette[
                                        index % SemanticColors.chartPalette.size
                                    ],
                                    label = "${item.icon} ${item.name}",
                                )
                            },
                            currency = state.currency,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "Счета",
                        subtitle = "${state.balances.count { it.category.isActive }} активных",
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onOpenCategories) { Text("Управлять") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = hapticClick(onAddExpense)) { Text("➕ Расход") }
                }
            }

            if (state.balances.isEmpty()) {
                item {
                    EmptyState(
                        icon = "🗂️",
                        title = "Счетов пока нет",
                        message = "Добавьте категории, чтобы распределять зарплату",
                    )
                }
            }

            items(state.balances, key = { it.category.id }) { balance ->
                CategoryBalanceCard(
                    balance = balance,
                    currency = state.currency,
                    onClick = { onCategoryClick(balance.category.id) },
                    onEdit = { editingCategoryId = balance.category.id },
                )
            }

            if (state.recentTransactions.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Последние операции",
                        subtitle = "За текущий месяц",
                    )
                }
                items(state.recentTransactions, key = { "${it.type}-${it.id}" }) { transaction ->
                    RecentTransactionRow(transaction = transaction, currency = state.currency)
                }
            }

            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (showTransfer) {
        TransferDialog(
            viewModel = viewModel,
            onDismiss = { showTransfer = false },
        )
    }

    val editingBalance = editingCategoryId?.let { id ->
        state.balances.firstOrNull { it.category.id == id }
    }
    val editingCategory = editingBalance?.category
    if (editingCategory != null) {
        CategorySettingsDialog(
            category = editingCategory,
            currency = state.currency,
            currentBalance = editingBalance?.allTimeBalance ?: 0.0,
            onDismiss = { editingCategoryId = null },
            onSave = {
                viewModel.saveCategory(it)
                editingCategoryId = null
            },
        )
    }
}

@Composable
private fun MonthHeaderCard(state: DashboardUiState) {
    PayCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = state.monthLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Поступлений: ${state.incomeCount} из ${state.paydayCount} в этом месяце",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        AnimatedProgress(
            progress = state.incomeProgress,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            height = 10,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = progressSubtitle(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Общий баланс",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                MoneyValue(
                    value = state.totalBalance,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    currency = state.currency,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Остаток месяца",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                MoneyValue(
                    value = state.monthNet,
                    color = if (state.monthNet >= 0) PayColors.income else PayColors.expense,
                    currency = state.currency,
                )
            }
        }
    }
}

private fun progressSubtitle(state: DashboardUiState): String {
    val plan = if (state.expectedIncome > 0) {
        Formatters.money(state.expectedIncome, state.currency)
    } else {
        "~" + Formatters.money(
            state.balances.sumOf { it.category.monthlyPlan },
            state.currency,
        )
    }
    return "Получено ${Formatters.money(state.receivedThisMonth, state.currency)} из $plan"
}

@Composable
private fun CategoryBalanceCard(
    balance: BalanceCalculator.CategoryBalance,
    currency: String,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val statusColor = PayColors.ofStatus(balance.status)
    val click = hapticClick(onClick)
    PayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (balance.status == CategoryStatus.OVERSPEND) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(icon = balance.category.icon)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = balance.category.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (balance.category.type == com.paysplit.app.data.model.CategoryType.SAVINGS) {
                        "Накопительный" + (balance.category.targetAmount?.let {
                            " · цель ${Formatters.money(it, currency)}"
                        } ?: "")
                    } else {
                        "Оплата сразу"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusDot(status = balance.status)
        }

        Spacer(Modifier.height(10.dp))
        AnimatedProgress(
            progress = balance.monthProgress,
            color = statusColor,
            height = 6,
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Распределено ${Formatters.money(balance.allocatedThisMonth, currency)}" +
                    " из ${Formatters.money(balance.category.monthlyPlan, currency)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Осталось ${Formatters.money(balance.remainingToPlan, currency)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoneyValue(
                value = balance.balance,
                color = if (balance.balance < 0) PayColors.expense else PayColors.income,
                style = MaterialTheme.typography.titleLarge,
                currency = currency,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Трат за месяц: ${balance.spentThisMonth.let {
                    Formatters.money(it, currency)
                }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onEdit,
                colors = ButtonDefaults.textButtonColors(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 0.dp,
                ),
            ) { Text("✏️ План") }
            Button(
                onClick = click,
                colors = ButtonDefaults.textButtonColors(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 0.dp,
                ),
            ) { Text("Открыть") }
        }

        if (balance.isOverspend) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "⚠️ Перерасход: расходы превысили поступления на " +
                    Formatters.money(-balance.balance, currency),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RecentTransactionRow(transaction: UiTransaction, currency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CategoryAvatar(icon = transaction.icon, size = 36)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${transaction.categoryName} · ${DateUtils.formatShortDate(transaction.date)}",
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

/** Диалог перевода средств между счетами. */
@Composable
private fun TransferDialog(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var amountText by remember { mutableStateOf("") }
    var fromId by remember { mutableStateOf(state.balances.firstOrNull()?.category?.id ?: 0L) }
    var toId by remember {
        mutableStateOf(state.balances.getOrNull(1)?.category?.id ?: 0L)
    }
    val accounts = state.balances.filter { it.category.isActive }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("↔️ Перевод между счетами") },
        text = {
            Column {
                Text(
                    text = "Списать со счёта:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                com.paysplit.app.ui.components.ChipRow(
                    options = accounts.map { it.category.id },
                    selected = fromId,
                    onSelect = { fromId = it },
                    label = { id ->
                        accounts.firstOrNull { it.category.id == id }?.category?.name ?: "?"
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Зачислить на счёт:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                com.paysplit.app.ui.components.ChipRow(
                    options = accounts.map { it.category.id },
                    selected = toId,
                    onSelect = { toId = it },
                    label = { id ->
                        accounts.firstOrNull { it.category.id == id }?.category?.name ?: "?"
                    },
                )
                Spacer(Modifier.height(12.dp))
                com.paysplit.app.ui.components.MoneyInputField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = "Сумма перевода, ${state.currency}",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
                    if (amount > 0 && fromId != toId && fromId != 0L && toId != 0L) {
                        viewModel.transfer(fromId, toId, amount)
                        onDismiss()
                    }
                },
            ) { Text("Перевести") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
