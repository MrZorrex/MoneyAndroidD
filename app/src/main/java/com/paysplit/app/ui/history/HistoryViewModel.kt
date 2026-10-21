package com.paysplit.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.HistoryPeriod
import com.paysplit.app.data.model.TxFilter
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.domain.TransactionBuilder
import com.paysplit.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryFilters(
    val period: HistoryPeriod = HistoryPeriod.THIS_MONTH,
    val type: TxFilter = TxFilter.ALL,
    val categoryId: Long? = null,
    val customFrom: Long = 0L,
    val customTo: Long = 0L,
)

data class HistoryUiState(
    val filters: HistoryFilters = HistoryFilters(),
    val from: Long = 0L,
    val to: Long = 0L,
    val groups: List<DayGroup> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val currency: String = "₽",
)

data class DayGroup(
    val dayStart: Long,
    val transactions: List<UiTransaction>,
) {
    val income: Double get() = transactions.filter { it.isIncoming }.sumOf { it.amount }
    val expense: Double get() = transactions.filter { !it.isIncoming }.sumOf { it.amount }
}

class HistoryViewModel(private val repository: PaySplitRepository) : ViewModel() {

    private val filters = MutableStateFlow(HistoryFilters())

    val state: StateFlow<HistoryUiState> =
        combine(repository.data, filters) { data, f ->
            val (from, to) = rangeFor(f)
            val transactions = TransactionBuilder.build(data, from, to, f.type, f.categoryId)
            // Доход — сумма всех поступлений (IncomeEntity), а не распределений
            val income = data.incomes.filter { it.date in from..to }.sumOf { it.amount }
            val expense = data.expenses.filter {
                it.date in from..to && (f.categoryId == null || it.categoryId == f.categoryId)
            }.sumOf { it.amount }

            HistoryUiState(
                filters = f,
                from = from,
                to = to,
                groups = transactions
                    .groupBy { DateUtils.dayStartDaysBack(0, it.date) }
                    .toList()
                    .sortedByDescending { it.first }
                    .map { (day, list) -> DayGroup(day, list) },
                categories = data.categories.sortedBy { it.sortOrder },
                totalIncome = income,
                totalExpense = expense,
                balance = income - expense,
                currency = data.settings.currency,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setPeriod(period: HistoryPeriod) {
        filters.update {
            if (period == HistoryPeriod.CUSTOM && it.customFrom == 0L) {
                it.copy(
                    period = period,
                    customFrom = DateUtils.monthStartMonthsBack(2),
                    customTo = DateUtils.today(),
                )
            } else {
                it.copy(period = period)
            }
        }
    }

    fun setType(type: TxFilter) = filters.update { it.copy(type = type) }

    fun setCategory(categoryId: Long?) = filters.update { it.copy(categoryId = categoryId) }

    fun setCustomRange(from: Long, to: Long) {
        // Нормализация: начало не должно быть позже конца.
        val (start, end) = if (from <= to) from to to else to to from
        filters.update {
            it.copy(period = HistoryPeriod.CUSTOM, customFrom = start, customTo = end)
        }
    }

    fun delete(transaction: UiTransaction) {
        viewModelScope.launch {
            when (transaction.type) {
                com.paysplit.app.data.model.TxType.EXPENSE ->
                    repository.deleteExpense(transaction.sourceId)

                com.paysplit.app.data.model.TxType.TRANSFER_OUT,
                com.paysplit.app.data.model.TxType.TRANSFER_IN,
                -> repository.deleteTransfer(transaction.sourceId)

                com.paysplit.app.data.model.TxType.INCOME ->
                    repository.deleteIncome(transaction.sourceId)

                com.paysplit.app.data.model.TxType.ADJUSTMENT ->
                    repository.deleteAdjustment(transaction.sourceId)
            }
        }
    }

    fun updateTransfer(transfer: com.paysplit.app.data.model.TransferEntity) {
        viewModelScope.launch { repository.updateTransfer(transfer) }
    }

    fun updateAdjustment(adjustment: com.paysplit.app.data.model.BalanceAdjustmentEntity) {
        viewModelScope.launch { repository.updateAdjustment(adjustment) }
    }

    private fun rangeFor(f: HistoryFilters): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (f.period) {
            HistoryPeriod.THIS_MONTH ->
                DateUtils.monthStart(now) to DateUtils.monthEnd(now)

            HistoryPeriod.LAST_MONTH ->
                DateUtils.monthStartMonthsBack(1, now) to DateUtils.monthEndMonthsBack(1, now)

            HistoryPeriod.THREE_MONTHS ->
                DateUtils.monthStartMonthsBack(2, now) to now

            HistoryPeriod.HALF_YEAR ->
                DateUtils.monthStartMonthsBack(5, now) to now

            HistoryPeriod.YEAR ->
                DateUtils.monthStartMonthsBack(11, now) to now

            HistoryPeriod.ALL -> 0L to Long.MAX_VALUE

            HistoryPeriod.CUSTOM -> {
                val from = if (f.customFrom == 0L) {
                    DateUtils.monthStartMonthsBack(2, now)
                } else {
                    f.customFrom
                }
                // customTo — начало выбранных суток: расширяем до конца суток,
                // чтобы операции последнего дня тоже попадали в выборку.
                val to = if (f.customTo == 0L) {
                    now
                } else {
                    f.customTo + 24L * 60 * 60 * 1000 - 1
                }
                from to to
            }
        }
    }
}
