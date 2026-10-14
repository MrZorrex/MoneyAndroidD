package com.paysplit.app.ui.history

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
import androidx.compose.material3.MaterialTheme
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
import com.paysplit.app.data.model.HistoryPeriod
import com.paysplit.app.data.model.TxFilter
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.ConfirmDialog
import com.paysplit.app.ui.components.EmptyState
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.StatTile
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/**
 * ЭКРАН 7 — История операций с фильтрами.
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var transactionToDelete by remember { mutableStateOf<UiTransaction?>(null) }

    Scaffold(
        topBar = { PayTopBar(title = "История операций", subtitle = "Все доходы и расходы") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // --- Фильтры ---
            PayCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Период",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                PeriodFilterRow(
                    selected = state.filters.period,
                    onSelect = viewModel::setPeriod,
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Тип операций",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TypeFilterRow(
                    selected = state.filters.type,
                    onSelect = viewModel::setType,
                )

                if (state.categories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Категория",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    CategoryFilterRow(
                        categories = state.categories,
                        selected = state.filters.categoryId,
                        onSelect = viewModel::setCategory,
                    )
                }
            }

            // --- Итоги ---
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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

            Spacer(Modifier.height(8.dp))

            if (state.groups.isEmpty()) {
                EmptyState(
                    icon = "🔍",
                    title = "Операций не найдено",
                    message = "Измените фильтры или добавьте операции",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    state.groups.forEach { group ->
                        item(key = "day-${group.dayStart}") {
                            DayHeader(group = group, currency = state.currency)
                        }
                        items(
                            items = group.transactions,
                            key = { "${it.type}-${it.id}-${it.date}" },
                        ) { transaction ->
                            HistoryRow(
                                transaction = transaction,
                                currency = state.currency,
                                onClick = { transactionToDelete = transaction },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(labels.keys.toList()) { period ->
            androidx.compose.material3.FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(labels[period] ?: period.name) },
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
    )
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(labels.keys.toList()) { type ->
            androidx.compose.material3.FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(labels[type] ?: type.name) },
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
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            androidx.compose.material3.FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("Все категории") },
            )
        }
        items(categories, key = { it.id }) { category ->
            androidx.compose.material3.FilterChip(
                selected = selected == category.id,
                onClick = { onSelect(category.id) },
                label = { Text("${category.icon} ${category.name}") },
            )
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup, currency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DateUtils.formatFullDate(group.dayStart),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.weight(1f))
        if (group.income > 0) {
            Text(
                text = "+" + Formatters.money(group.income, currency),
                style = MaterialTheme.typography.labelLarge,
                color = PayColors.income,
            )
            Spacer(Modifier.width(8.dp))
        }
        if (group.expense > 0) {
            Text(
                text = "−" + Formatters.money(group.expense, currency),
                style = MaterialTheme.typography.labelLarge,
                color = PayColors.expense,
            )
        }
    }
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(icon = transaction.icon, size = 38)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${transaction.categoryName} · ${DateUtils.formatTime(transaction.date)}",
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
