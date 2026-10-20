package com.paysplit.app.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.ExpenseEntity
import com.paysplit.app.data.model.TransferEntity
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.domain.AnalyticsEngine
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.domain.TransactionBuilder
import com.paysplit.app.util.DateUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class CategoryDetailUiState(
    val loading: Boolean = true,
    val deleted: Boolean = false,
    val categoryId: Long = 0L,
    val balance: BalanceCalculator.CategoryBalance? = null,
    val transactions: List<UiTransaction> = emptyList(),
    /** Баланс счёта на конец каждого из последних 6 месяцев. */
    val monthlyBalance: List<Pair<YearMonth, Double>> = emptyList(),
    /** Поступления и расходы по месяцам. */
    val monthlyStats: List<AnalyticsEngine.MonthStat> = emptyList(),
    val monthLabels: List<String> = emptyList(),
    val categoryName: String = "Счёт",
    val categoryIcon: String = "🗂️",
    /** Другие активные счета: идентификатор и название. */
    val otherCategories: List<Pair<Long, String>> = emptyList(),
    val currency: String = "₽",
)

class CategoryDetailViewModel(
    private val repository: PaySplitRepository,
    private val categoryId: Long,
) : ViewModel() {

    val state: StateFlow<CategoryDetailUiState> = repository.data.map { data ->
        val monthStart = DateUtils.monthStart(System.currentTimeMillis())
        val monthEnd = DateUtils.monthEnd(System.currentTimeMillis())
        val balance = BalanceCalculator.compute(data, monthStart, monthEnd)
            .firstOrNull { it.category.id == categoryId }

        val months = (5 downTo 0).map { DateUtils.yearMonth(System.currentTimeMillis()).minusMonths(it.toLong()) }
        val monthlyBalance = months.map { ym ->
            val mEnd = DateUtils.millisOf(ym.atEndOfMonth()) + 86_400_000L - 1
            val inflow = data.distributions.filter { it.categoryId == categoryId && it.date <= mEnd }
                .sumOf { it.amount }
            val transferIn = data.transfers.filter { it.toCategoryId == categoryId && it.date <= mEnd }
                .sumOf { it.amount }
            val adjIn = data.adjustments.filter { it.categoryId == categoryId && it.date <= mEnd && it.amount > 0 }
                .sumOf { it.amount }
            val outflow = data.expenses.filter { it.categoryId == categoryId && it.date <= mEnd }
                .sumOf { it.amount }
            val transferOut = data.transfers.filter { it.fromCategoryId == categoryId && it.date <= mEnd }
                .sumOf { it.amount }
            val adjOut = data.adjustments.filter { it.categoryId == categoryId && it.date <= mEnd && it.amount < 0 }
                .sumOf { -it.amount }
            ym to (inflow + transferIn + adjIn - outflow - transferOut - adjOut)
        }
        val monthlyStats = months.map { ym ->
            val mStart = DateUtils.millisOf(ym.atDay(1))
            val mEnd = DateUtils.millisOf(ym.atEndOfMonth()) + 86_400_000L - 1
            val adjIncome = data.adjustments.filter { it.categoryId == categoryId && it.date in mStart..mEnd && it.amount > 0 }.sumOf { it.amount }
            val adjExpense = data.adjustments.filter { it.categoryId == categoryId && it.date in mStart..mEnd && it.amount < 0 }.sumOf { -it.amount }
            AnalyticsEngine.MonthStat(
                yearMonth = ym,
                income = data.distributions.filter { it.categoryId == categoryId && it.date in mStart..mEnd }
                    .sumOf { it.amount } +
                    data.transfers.filter { it.toCategoryId == categoryId && it.date in mStart..mEnd }
                        .sumOf { it.amount } + adjIncome,
                expense = data.expenses.filter { it.categoryId == categoryId && it.date in mStart..mEnd }
                    .sumOf { it.amount } +
                    data.transfers.filter { it.fromCategoryId == categoryId && it.date in mStart..mEnd }
                        .sumOf { it.amount } + adjExpense,
            )
        }

        CategoryDetailUiState(
            loading = false,
            deleted = false,
            categoryId = categoryId,
            balance = balance,
            transactions = TransactionBuilder.forCategory(data, categoryId),
            categoryName = data.category(categoryId)?.name ?: "Счёт",
            categoryIcon = data.category(categoryId)?.icon ?: "🗂️",
            otherCategories = data.categories
                .filter { it.isActive && it.id != categoryId }
                .sortedBy { it.sortOrder }
                .map { it.id to it.name },
            monthlyBalance = monthlyBalance,
            monthlyStats = monthlyStats,
            monthLabels = months.map { DateUtils.formatMonthShort(it) },
            currency = data.settings.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryDetailUiState())

    fun deleteCategory() {
        viewModelScope.launch { repository.deleteCategory(categoryId) }
    }

    /** Сохранение настроек счёта (название, картинка, план на месяц и т.д.). */
    fun saveCategory(updated: com.paysplit.app.data.model.CategoryEntity) {
        viewModelScope.launch { repository.saveCategory(updated.copy(id = categoryId)) }
    }

    fun deleteTransaction(transaction: UiTransaction) {
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

    /** Коррекция баланса с комментарием. */
    fun adjustBalance(amount: Double, comment: String?, date: Long = System.currentTimeMillis(), sticker: String? = null) {
        viewModelScope.launch {
            repository.addAdjustment(
                com.paysplit.app.data.model.BalanceAdjustmentEntity(
                    categoryId = categoryId,
                    amount = amount,
                    date = date,
                    comment = comment,
                    sticker = sticker?.takeIf { amount > 0 },
                )
            )
        }
    }

    fun setBalanceTo(newBalance: Double, comment: String?, currentBalance: Double, date: Long = System.currentTimeMillis(), sticker: String? = null) {
        val delta = newBalance - currentBalance
        if (delta != 0.0) adjustBalance(delta, comment, date, sticker)
    }

    /** Быстрый расход с этого счёта. */
    fun addQuickExpense(title: String, amount: Double) {
        viewModelScope.launch {
            repository.addExpense(
                ExpenseEntity(
                    categoryId = categoryId,
                    amount = amount,
                    date = System.currentTimeMillis(),
                    title = title,
                    comment = "Быстрый расход",
                ),
            )
        }
    }

    /** Пополнение счёта переводом с другого счёта. */
    fun topUp(fromCategoryId: Long, amount: Double) {
        viewModelScope.launch {
            repository.addTransfer(
                TransferEntity(
                    fromCategoryId = fromCategoryId,
                    toCategoryId = categoryId,
                    amount = amount,
                    date = System.currentTimeMillis(),
                    comment = "Пополнение счёта",
                ),
            )
        }
    }
}
