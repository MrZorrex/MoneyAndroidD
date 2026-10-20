package com.paysplit.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.AnimatedProgress
import com.paysplit.app.ui.components.CategoryAvatar
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
 * Главный экран — компактная статистика + история трат.
 * Счета вынесены в отдельную вкладку «Счета».
 */
@Composable
fun DashboardScreen(
    onAddIncome: () -> Unit,
    onAddExpense: () -> Unit,
    onCategoryClick: (Long) -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTransfer by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = hapticClick { onAddExpense() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) { Text("➕ Траты", style = MaterialTheme.typography.labelMedium) }
                FilledTonalButton(
                    onClick = hapticClick { showTransfer = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) { Text("↔️ Перевод", style = MaterialTheme.typography.labelMedium) }
                ExtendedFloatingActionButton(
                    text = { Text("Пришла зарплата!", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                MonthHeaderCompact(state = state)
            }

            item {
                SummaryCompactCard(state = state)
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Счета", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${state.balances.count { it.category.isActive }} активных · ${Formatters.money(state.balances.sumOf { it.balance.coerceAtLeast(0.0) }, state.currency)} на счетах",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = hapticClick(onOpenAccounts), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Все ›", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (state.balances.isEmpty()) {
                item { EmptyState(icon = "🗂️", title = "Счетов нет", message = "Добавьте счета во вкладке «Счета»") }
            } else {
                item {
                    AccountsPreviewRow(
                        balances = state.balances,
                        currency = state.currency,
                        onCategoryClick = onCategoryClick,
                    )
                }
            }

            // История — все приходы, уходы и изменения
            if (state.recentTransactions.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "История",
                        subtitle = "Все приходы, уходы и изменения · ${state.recentTransactions.size} за месяц",
                        trailing = {
                            if (state.recentTransactions.size >= 10) {
                                Text(
                                    text = "${state.recentTransactions.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    )
                }
                val grouped = state.recentTransactions.groupBy { DateUtils.localDate(it.date) }
                grouped.forEach { (date, list) ->
                    item(key = "day-$date") {
                        Text(
                            text = DateUtils.formatRelativeDay(DateUtils.millisOf(date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(list, key = { "${it.type}-${it.id}-${it.date}" }) { tx ->
                        CompactTransactionRow(transaction = tx, currency = state.currency)
                    }
                }
            } else {
                item {
                    PayCard {
                        Text("История пуста", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text("Нажмите «Пришла зарплата», «➕ Траты» или сделайте коррекцию — всё появится здесь", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(Modifier.height(96.dp)) }
        }
    }

    if (showTransfer) {
        TransferDialog(viewModel = viewModel, onDismiss = { showTransfer = false })
    }
}

@Composable
private fun MonthHeaderCompact(state: DashboardUiState) {
    PayCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(state.monthLabel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Поступлений: ${state.incomeCount} из ${state.paydayCount} · ${progressSubtitle(state)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${(state.incomeProgress * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(6.dp))
        AnimatedProgress(
            progress = state.incomeProgress,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            height = 6,
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Общий баланс", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                MoneyValue(value = state.totalBalance, color = MaterialTheme.colorScheme.onPrimaryContainer, currency = state.currency, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text("Остаток месяца", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    text = Formatters.money(state.monthNet, state.currency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (state.monthNet >= 0) PayColors.income else PayColors.expense,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun progressSubtitle(state: DashboardUiState): String {
    val plan = if (state.expectedIncome > 0) Formatters.money(state.expectedIncome, state.currency)
    else "~" + Formatters.money(state.balances.sumOf { it.category.monthlyPlan }, state.currency)
    return "${Formatters.money(state.receivedThisMonth, state.currency)} из $plan"
}

@Composable
private fun SummaryCompactCard(state: DashboardUiState) {
    PayCard {
        SectionHeader(title = "Сводка за месяц", subtitle = state.monthLabel)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow(label = "Доходы", value = Formatters.money(state.monthIncome, state.currency), valueColor = PayColors.income)
                InfoRow(label = "Расходы", value = Formatters.money(state.monthExpense, state.currency), valueColor = PayColors.expense)
                InfoRow(label = "Остаток", value = Formatters.money(state.monthNet, state.currency), valueColor = if (state.monthNet >= 0) PayColors.income else PayColors.expense)
            }
            if (state.expenseByCategory.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    val chartSize = maxWidth.coerceAtMost(120.dp)
                    DonutChart(
                        slices = state.expenseByCategory.mapIndexed { index, item ->
                            DonutSlice(
                                value = item.amount,
                                color = SemanticColors.chartPalette[index % SemanticColors.chartPalette.size],
                                label = item.name,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        total = state.monthExpense,
                        strokeWidth = 14.dp,
                        chartSize = chartSize,
                    )
                }
            }
        }
        if (state.expenseByCategory.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            DonutLegend(
                slices = state.expenseByCategory.mapIndexed { index, item ->
                    DonutSlice(
                        value = item.amount,
                        color = SemanticColors.chartPalette[index % SemanticColors.chartPalette.size],
                        label = "${item.icon} ${item.name}",
                    )
                },
                currency = state.currency,
            )
        }
    }
}

@Composable
private fun AccountsPreviewRow(
    balances: List<BalanceCalculator.CategoryBalance>,
    currency: String,
    onCategoryClick: (Long) -> Unit,
) {
    // Вертикальный список счетов вместо горизонтальной прокрутки
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        balances.forEach { b ->
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth().clickable { onCategoryClick(b.category.id) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (b.status == com.paysplit.app.data.model.CategoryStatus.OVERSPEND) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(icon = b.category.icon, size = 28)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.category.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(4.dp))
                            StatusDot(status = b.status)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(Formatters.money(b.balance, currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (b.balance < 0) PayColors.expense else PayColors.income, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        AnimatedProgress(progress = b.monthProgress, color = PayColors.ofStatus(b.status), height = 4)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${(b.monthProgress * 100).toInt()}% · ${Formatters.money(b.remainingToPlan, currency)} ост.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("›", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CompactTransactionRow(transaction: UiTransaction, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(icon = transaction.icon, size = 28)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${transaction.categoryName} · ${DateUtils.formatShortDate(transaction.date)}" + (transaction.comment?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = (if (transaction.isIncoming) "+" else "−") + Formatters.money(transaction.amount, currency),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = when (transaction.type) {
                com.paysplit.app.data.model.TxType.ADJUSTMENT -> if (transaction.title.contains("+")) PayColors.income else PayColors.expense
                else -> if (transaction.isIncoming) PayColors.income else PayColors.expense
            },
            maxLines = 1,
        )
    }
}

/** Диалог перевода средств между счетами — вертикальный выбор. */
@Composable
private fun TransferDialog(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var amountText by remember { mutableStateOf("") }
    var fromId by remember { mutableStateOf(state.balances.firstOrNull()?.category?.id ?: 0L) }
    var toId by remember { mutableStateOf(state.balances.getOrNull(1)?.category?.id ?: 0L) }
    val accounts = state.balances.filter { it.category.isActive }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("↔️ Перевод", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Списать (вертикальный список):", style = MaterialTheme.typography.labelSmall)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    accounts.forEach { b ->
                        val selected = b.category.id == fromId
                        androidx.compose.material3.Surface(
                            onClick = { fromId = b.category.id },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryAvatar(icon = b.category.icon, size = 24)
                                Spacer(Modifier.width(8.dp))
                                Text(b.category.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                if (selected) Text("✓", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Text("Зачислить:", style = MaterialTheme.typography.labelSmall)
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    accounts.forEach { b ->
                        val selected = b.category.id == toId
                        androidx.compose.material3.Surface(
                            onClick = { toId = b.category.id },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryAvatar(icon = b.category.icon, size = 24)
                                Spacer(Modifier.width(8.dp))
                                Text(b.category.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                if (selected) Text("✓", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                com.paysplit.app.ui.components.MoneyInputField(value = amountText, onValueChange = { amountText = it }, label = "Сумма, ${state.currency}")
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
                if (amount > 0 && fromId != toId && fromId != 0L && toId != 0L) {
                    viewModel.transfer(fromId, toId, amount)
                    onDismiss()
                }
            }) { Text("Перевести", style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", style = MaterialTheme.typography.labelMedium) } },
    )
}
