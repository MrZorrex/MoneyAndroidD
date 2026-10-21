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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val monthLabel: String = "",
    val totalBalance: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthExpense: Double = 0.0,
    val balances: List<BalanceCalculator.CategoryBalance> = emptyList(),
    val expenseByCategory: List<AnalyticsEngine.CategoryExpense> = emptyList(),
    val recentTransactions: List<UiTransaction> = emptyList(),
    val currency: String = "₽",
    val freeAmount: Double = 0.0,
)

class DashboardViewModel(private val repository: PaySplitRepository) : ViewModel() {

    val state: StateFlow<DashboardUiState> = repository.data.map { data ->
        val now = System.currentTimeMillis()
        val monthStart = DateUtils.monthStart(now)
        val monthEnd = DateUtils.monthEnd(now)

        val balances = BalanceCalculator.compute(data, monthStart, monthEnd)
            .sortedWith(compareBy({ !it.category.isActive }, { it.category.sortOrder }))

        val stats = AnalyticsEngine.compute(data, monthStart, monthEnd)

        // Свободные средства — баланс категории 💵
        val freeAmount = balances.firstOrNull {
            it.category.name == "Свободные средства" || it.category.icon == "💵"
        }?.balance ?: 0.0

        DashboardUiState(
            loading = false,
            monthLabel = DateUtils.formatMonth(DateUtils.yearMonth(now)),
            totalBalance = BalanceCalculator.totalBalance(data),
            monthIncome = stats.totalIncome,
            monthExpense = stats.totalExpense,
            balances = balances,
            expenseByCategory = stats.byCategory,
            recentTransactions = TransactionBuilder.build(data, monthStart, monthEnd).take(5),
            currency = data.settings.currency,
            freeAmount = freeAmount,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

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

    /** Распределение свободных средств по счетам — переводы из 💵 Свободные средства. */
    fun distributeFreeFunds(allocations: List<Pair<Long, Double>>) {
        viewModelScope.launch {
            val freeFunds = repository.ensureFreeFundsCategory()
            val now = System.currentTimeMillis()
            allocations.forEach { (catId, amount) ->
                if (amount > 0.5 && catId != freeFunds.id) {
                    repository.addTransfer(
                        com.paysplit.app.data.model.TransferEntity(
                            fromCategoryId = freeFunds.id,
                            toCategoryId = catId,
                            amount = amount,
                            date = now,
                            comment = "Распределение свободных средств",
                        )
                    )
                }
            }
        }
    }
}
