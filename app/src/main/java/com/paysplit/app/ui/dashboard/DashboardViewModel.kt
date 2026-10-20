package com.paysplit.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.domain.AnalyticsEngine
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.domain.TransactionBuilder
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

data class DashboardUiState(
    val loading: Boolean = true,
    val monthLabel: String = "",
    val expectedIncome: Double = 0.0,
    val receivedThisMonth: Double = 0.0,
    val incomeCount: Int = 0,
    val paydayCount: Int = 2,
    val totalBalance: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthExpense: Double = 0.0,
    val balances: List<BalanceCalculator.CategoryBalance> = emptyList(),
    val expenseByCategory: List<AnalyticsEngine.CategoryExpense> = emptyList(),
    val recentTransactions: List<UiTransaction> = emptyList(),
    val currency: String = "₽",
) {
    /** Прогресс получения зарплаты за месяц (0..1). */
    val incomeProgress: Float
        get() = if (expectedIncome <= 0.0) 0f
        else (receivedThisMonth / expectedIncome).coerceIn(0.0, 1.0).toFloat()

    val monthNet: Double get() = monthIncome - monthExpense
}

class DashboardViewModel(private val repository: PaySplitRepository) : ViewModel() {

    val state: StateFlow<DashboardUiState> = repository.data.map { data ->
        val now = System.currentTimeMillis()
        val monthStart = DateUtils.monthStart(now)
        val monthEnd = DateUtils.monthEnd(now)

        val balances = BalanceCalculator.compute(data, monthStart, monthEnd)
            .sortedWith(compareBy({ !it.category.isActive }, { it.category.sortOrder }))

        val stats = AnalyticsEngine.compute(data, monthStart, monthEnd)

        DashboardUiState(
            loading = false,
            monthLabel = DateUtils.formatMonth(DateUtils.yearMonth(now)),
            expectedIncome = data.settings.expectedMonthlyIncome,
            receivedThisMonth = BalanceCalculator.incomeThisMonth(data, monthStart, monthEnd),
            incomeCount = BalanceCalculator.incomeCountThisMonth(data, monthStart, monthEnd),
            paydayCount = data.settings.paydayCount,
            totalBalance = BalanceCalculator.totalBalance(data),
            monthIncome = stats.totalIncome,
            monthExpense = stats.totalExpense,
            balances = balances,
            expenseByCategory = stats.byCategory,
            recentTransactions = TransactionBuilder.build(data, monthStart, monthEnd).take(12),
            currency = data.settings.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** Форматирование для подзаголовка прогресса. */
    fun progressSubtitle(state: DashboardUiState): String {
        val received = Formatters.money(state.receivedThisMonth, state.currency)
        val plan = if (state.expectedIncome > 0) {
            Formatters.money(state.expectedIncome, state.currency)
        } else {
            "~" + Formatters.money(
                max(0.0, state.balances.sumOf { it.category.monthlyPlan }),
                state.currency,
            )
        }
        return "Получено " + received + " из " + plan
    }

    /** Сохранение настроек счёта (в т.ч. суммы на месяц) прямо с главной. */
    fun saveCategory(updated: com.paysplit.app.data.model.CategoryEntity) {
        viewModelScope.launch { repository.saveCategory(updated) }
    }

    /** Перевод средств между счетами: расход на одном счёте и доход на другом. */
    fun transfer(fromCategoryId: Long, toCategoryId: Long, amount: Double) {
        viewModelScope.launch {
            repository.addTransfer(
                com.paysplit.app.data.model.TransferEntity(
                    fromCategoryId = fromCategoryId,
                    toCategoryId = toCategoryId,
                    amount = amount,
                    date = System.currentTimeMillis(),
                    comment = "Перевод между счетами",
                ),
            )
        }
    }
}
