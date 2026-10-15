package com.paysplit.app.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.data.model.AnalyticsPeriod
import com.paysplit.app.domain.AnalyticsEngine
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.AnimatedProgress
import com.paysplit.app.ui.components.AreaLineChart
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.DonutChart
import com.paysplit.app.ui.components.DonutLegend
import com.paysplit.app.ui.components.DonutSlice
import com.paysplit.app.ui.components.DualLineChart
import com.paysplit.app.ui.components.EmptyState
import com.paysplit.app.ui.components.InfoRow
import com.paysplit.app.ui.components.MoneyValue
import com.paysplit.app.ui.components.MonthlyColumnChart
import com.paysplit.app.ui.components.MonthlyCompareColumnChart
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.SectionHeader
import com.paysplit.app.ui.components.SignedBarChart
import com.paysplit.app.ui.components.StatTile
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.ui.theme.SemanticColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/**
 * ЭКРАН 8 — Аналитика.
 */
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Обзор", "Расходы", "Накопления", "Топ", "Тренды")

    Scaffold(
        topBar = {
            PayTopBar(
                title = "Аналитика",
                subtitle = "Графики и статистика",
                actions = {
                    Text(
                        text = state.period.label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            val stats = state.stats
            if (stats == null) {
                EmptyState(
                    icon = "📊",
                    title = "Недостаточно данных",
                    message = "Добавьте операции, чтобы построить аналитику",
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    PeriodSelector(
                        selected = state.period,
                        onSelect = viewModel::setPeriod,
                    )
                }

                when (selectedTab) {
                    0 -> OverviewTab(state, stats)
                    1 -> ExpenseTab(state, stats)
                    2 -> SavingsTab(state)
                    3 -> TopTab(state, stats)
                    4 -> TrendTab(state, stats)
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: AnalyticsPeriod,
    onSelect: (AnalyticsPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnalyticsPeriod.entries.forEach { period ->
            androidx.compose.material3.FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(period.label) },
            )
        }
    }
}

// --- Вкладка «Обзор» ---
private fun androidx.compose.foundation.lazy.LazyListScope.OverviewTab(
    state: AnalyticsUiState,
    stats: AnalyticsEngine.PeriodStats,
) {
    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                title = "Итоги за ${state.period.label.lowercase()}",
                subtitle = "С ${DateUtils.formatShortDate(stats.from)} " +
                    "по ${DateUtils.formatShortDate(stats.to)}",
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    title = "Доходы",
                    value = Formatters.money(stats.totalIncome, state.currency),
                    valueColor = PayColors.income,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatTile(
                    title = "Расходы",
                    value = Formatters.money(stats.totalExpense, state.currency),
                    valueColor = PayColors.expense,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatTile(
                    title = "Итог",
                    value = Formatters.money(stats.net, state.currency),
                    valueColor = if (stats.net >= 0) PayColors.income else PayColors.expense,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            InfoRow(
                label = "В среднем доходов в месяц",
                value = Formatters.money(stats.avgIncome, state.currency),
            )
            InfoRow(
                label = "В среднем расходов в месяц",
                value = Formatters.money(stats.avgExpense, state.currency),
            )
            InfoRow(
                label = "Норма сбережений",
                value = if (stats.totalIncome > 0) {
                    Formatters.percent(
                        ((stats.net / stats.totalIncome).toFloat()).coerceIn(0f, 1f),
                    )
                } else {
                    "—"
                },
            )
        }
    }

    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title = "По месяцам", subtitle = "Поступления и расходы")
            Spacer(Modifier.height(12.dp))
            MonthlyCompareColumnChart(
                income = stats.months.map { it.income },
                expense = stats.months.map { it.expense },
                labels = state.monthLabels,
                height = 220.dp,
            )
        }
    }

    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                title = "Остаток по месяцам",
                subtitle = "Доходы минус расходы",
            )
            Spacer(Modifier.height(12.dp))
            SignedBarChart(
                values = stats.months.map { it.net },
                labels = state.monthLabels,
                height = 180.dp,
            )
        }
    }
}

// --- Вкладка «Расходы» ---
private fun androidx.compose.foundation.lazy.LazyListScope.ExpenseTab(
    state: AnalyticsUiState,
    stats: AnalyticsEngine.PeriodStats,
) {
    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                title = "Расходы по категориям",
                subtitle = "Всего " + Formatters.money(stats.totalExpense, state.currency),
            )
            Spacer(Modifier.height(12.dp))
            if (stats.byCategory.isNotEmpty()) {
                DonutChart(
                    slices = stats.byCategory.mapIndexed { index, item ->
                        DonutSlice(
                            value = item.amount,
                            color = SemanticColors.chartPalette[
                                index % SemanticColors.chartPalette.size
                            ],
                            label = "${item.icon} ${item.name}",
                        )
                    },
                    total = stats.totalExpense,
                    strokeWidth = 22.dp,
                    centerContent = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = Formatters.moneyShort(stats.totalExpense),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "расходы",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                DonutLegend(
                    slices = stats.byCategory.mapIndexed { index, item ->
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
            } else {
                EmptyState(icon = "🍩", title = "Нет расходов", message = "За период расходов нет")
            }
        }
    }

    items(stats.byCategory.size) { index ->
        val item = stats.byCategory[index]
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(icon = item.icon, size = 40)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                    AnimatedProgress(progress = item.share, height = 6)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${Formatters.percent(item.share)} от всех расходов",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                MoneyValue(
                    value = item.amount,
                    color = PayColors.expense,
                    currency = state.currency,
                )
            }
        }
    }
}

// --- Вкладка «Накопления» ---
private fun androidx.compose.foundation.lazy.LazyListScope.SavingsTab(state: AnalyticsUiState) {
    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(
                title = "Накопительные счета",
                subtitle = "Отложено за всё время",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = Formatters.money(state.savingsTotal, state.currency),
                style = MaterialTheme.typography.headlineMedium,
                color = PayColors.income,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Средняя скорость: " +
                    Formatters.money(state.savingsMonthlyRate, state.currency) + " в месяц",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (state.savingsGrowth.any { it.second != 0.0 }) {
                AreaLineChart(
                    values = state.savingsGrowth.map { it.second },
                    labels = state.monthLabels,
                    height = 180.dp,
                )
            } else {
                EmptyState(
                    icon = "📈",
                    title = "Нет данных",
                    message = "Пополните накопительные счета",
                )
            }
        }
    }

    if (state.savings.isEmpty()) {
        item {
            EmptyState(
                icon = "🏦",
                title = "Накопительных счетов нет",
                message = "Создайте счёт типа «Накопительный» в категориях",
            )
        }
    } else {
        items(state.savings.size) { index ->
            val (category, balance) = state.savings[index]
            val target = category.targetAmount ?: 0.0
            val monthsLeft = state.forecast[category.id]
            PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(icon = category.icon, size = 44)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = category.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Накоплено " +
                                Formatters.money(balance, state.currency) +
                                " из " + Formatters.money(target, state.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MoneyValue(
                        value = balance,
                        color = PayColors.income,
                        currency = state.currency,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AnimatedProgress(
                    progress = if (target > 0) (balance / target).toFloat().coerceIn(0f, 1f) else 0f,
                    color = PayColors.income,
                    height = 8,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (target > 0 && balance >= target) {
                        "🎉 Цель достигнута!"
                    } else if (monthsLeft != null) {
                        "До цели примерно $monthsLeft мес." +
                            (category.targetDate?.let {
                                " · цель к ${DateUtils.formatShortDate(it)}"
                            } ?: "")
                    } else {
                        "Цель не задана или скорость накопления нулевая"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- Вкладка «Топ» ---
private fun androidx.compose.foundation.lazy.LazyListScope.TopTab(
    state: AnalyticsUiState,
    stats: AnalyticsEngine.PeriodStats,
) {
    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title = "Самые большие траты", subtitle = "За выбранный период")
            Spacer(Modifier.height(8.dp))
            if (stats.topLargest.isEmpty()) {
                EmptyState(icon = "🐘", title = "Нет данных", message = "Расходов за период нет")
            } else {
                stats.topLargest.forEachIndexed { index, expense ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expense.title,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = DateUtils.formatShortDate(expense.date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = Formatters.money(expense.amount, state.currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PayColors.expense,
                        )
                    }
                }
            }
        }
    }

    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title = "Чаще всего", subtitle = "Повторяющиеся траты")
            Spacer(Modifier.height(8.dp))
            if (stats.topFrequent.isEmpty()) {
                EmptyState(icon = "🔁", title = "Нет данных", message = "Расходов за период нет")
            } else {
                stats.topFrequent.forEach { frequent ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = frequent.icon, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = frequent.title,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "${frequent.count} раз · " +
                                    Formatters.money(frequent.total, state.currency),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title = "Структура доходов", subtitle = "Источники поступлений")
            Spacer(Modifier.height(8.dp))
            if (stats.bySource.isEmpty()) {
                EmptyState(icon = "💼", title = "Нет данных", message = "Поступлений за период нет")
            } else {
                stats.bySource.forEach { (source, amount) ->
                    InfoRow(label = source.label, value = Formatters.money(amount, state.currency))
                }
            }
        }
    }
}

// --- Вкладка «Тренды» ---
private fun androidx.compose.foundation.lazy.LazyListScope.TrendTab(
    state: AnalyticsUiState,
    stats: AnalyticsEngine.PeriodStats,
) {
    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title = "Доходы и расходы", subtitle = "Динамика по месяцам")
            Spacer(Modifier.height(12.dp))
            DualLineChart(
                first = stats.months.map { it.income },
                second = stats.months.map { it.expense },
                labels = state.monthLabels,
                height = 220.dp,
            )
        }
    }

    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title = "Тренды", subtitle = "На основе последних месяцев")
            Spacer(Modifier.height(8.dp))
            TrendRow(
                label = "Доходы",
                trend = stats.incomeTrend,
                value = stats.avgIncome,
                currency = state.currency,
            )
            TrendRow(
                label = "Расходы",
                trend = stats.expenseTrend,
                value = stats.avgExpense,
                currency = state.currency,
                invertColors = true,
            )
        }
    }

    item {
        PayCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title = "Объём по месяцам", subtitle = "Столбцы доходов")
            Spacer(Modifier.height(12.dp))
            MonthlyColumnChart(
                values = stats.months.map { it.income },
                labels = state.monthLabels,
                height = 200.dp,
            )
        }
    }
}

@Composable
private fun TrendRow(
    label: String,
    trend: AnalyticsEngine.Trend,
    value: Double,
    currency: String,
    invertColors: Boolean = false,
) {
    val growing = trend == AnalyticsEngine.Trend.UP
    val positive = if (invertColors) !growing else growing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "В среднем " + Formatters.money(value, currency),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val arrow = when (trend) {
            AnalyticsEngine.Trend.UP -> "↑"
            AnalyticsEngine.Trend.DOWN -> "↓"
            AnalyticsEngine.Trend.STABLE -> "→"
        }
        Text(
            text = "$arrow ${trend.label}",
            style = MaterialTheme.typography.titleMedium,
            color = if (positive) PayColors.income else PayColors.expense,
        )
    }
}
