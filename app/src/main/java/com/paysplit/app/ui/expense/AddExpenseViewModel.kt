package com.paysplit.app.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.ExpenseEntity
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddExpenseUiState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val amountText: String = "",
    val amount: Double = 0.0,
    val title: String = "",
    val comment: String = "",
    val date: Long = 0L,
    val categoryId: Long = 0L,
    val categories: List<CategoryEntity> = emptyList(),
    val selectedBalance: Double = 0.0,
    val isOverBalance: Boolean = false,
    val titleSuggestions: List<String> = emptyList(),
    val balances: List<BalanceCalculator.CategoryBalance> = emptyList(),
    val templates: List<String> = emptyList(),
    val currency: String = "₽",
    val saved: Boolean = false,
    val savedAndNew: Boolean = false,
    val error: String? = null,
) {
    val isAmountValid: Boolean get() = amount > 0.0
    val isTitleValid: Boolean get() = title.isNotBlank()
}

class AddExpenseViewModel(
    private val repository: PaySplitRepository,
    private val expenseId: Long = 0L,
) : ViewModel() {

    private data class Form(
        val amountText: String = "",
        val title: String = "",
        val comment: String = "",
        val date: Long = DateUtils.today(),
        val categoryId: Long = 0L,
        val initialized: Boolean = false,
    )

    private val form = MutableStateFlow(Form())
    private val saved = MutableStateFlow(false)
    private val savedAndNew = MutableStateFlow(false)

    val state: StateFlow<AddExpenseUiState> =
        combine(repository.data, form, saved, savedAndNew) { data, f, isSaved, isSavedAndNew ->
            buildState(data, f, isSaved, isSavedAndNew)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddExpenseUiState())

    init {
        viewModelScope.launch {
            val data = repository.data.first()
            if (expenseId != 0L) {
                val expense = repository.getExpense(expenseId)
                if (expense != null) {
                    form.value = Form(
                        amountText = formatAmount(expense.amount),
                        title = expense.title,
                        comment = expense.comment.orEmpty(),
                        date = expense.date,
                        categoryId = expense.categoryId,
                        initialized = true,
                    )
                    return@launch
                }
            }
            val firstCategory = data.categories.firstOrNull { it.isActive }?.id ?: 0L
            form.update { it.copy(categoryId = firstCategory, initialized = true) }
        }
    }

    private fun buildState(
        data: AppData,
        f: Form,
        isSaved: Boolean,
        isSavedAndNew: Boolean,
    ): AddExpenseUiState {
        val amount = com.paysplit.app.ui.components.parseMoney(f.amountText) ?: 0.0
        val monthStart = DateUtils.monthStart(f.date)
        val monthEnd = DateUtils.monthEnd(f.date)
        val balance = BalanceCalculator.compute(data, monthStart, monthEnd)
            .firstOrNull { it.category.id == f.categoryId }

        return AddExpenseUiState(
            loading = !f.initialized,
            isEditing = expenseId != 0L,
            amountText = f.amountText,
            amount = amount,
            title = f.title,
            comment = f.comment,
            date = f.date,
            categoryId = f.categoryId,
            categories = data.categories.filter { it.isActive }.sortedBy { it.sortOrder },
            selectedBalance = balance?.balance ?: 0.0,
            isOverBalance = balance != null && amount > balance.balance + 0.005,
            balances = BalanceCalculator.compute(data, monthStart, monthEnd),
            titleSuggestions = data.expenses
                .map { it.title }
                .distinct()
                .sorted()
                .takeLast(20)
                .reversed(),
            templates = data.settings.quickTemplates,
            currency = data.settings.currency,
            saved = isSaved,
            savedAndNew = isSavedAndNew,
            error = when {
                f.amountText.isNotBlank() && amount <= 0.0 -> "Введите сумму больше нуля"
                f.title.isNotBlank() && f.categoryId == 0L -> "Выберите счёт"
                else -> null
            },
        )
    }

    fun setAmount(text: String) = form.update { it.copy(amountText = text) }

    fun setTitle(title: String) = form.update { it.copy(title = title) }

    fun setComment(comment: String) = form.update { it.copy(comment = comment) }

    fun setDate(date: Long) = form.update { it.copy(date = date) }

    fun setCategory(categoryId: Long) = form.update { it.copy(categoryId = categoryId) }

    /** Применение быстрого шаблона: заполняет название и, если возможно, счёт. */
    fun applyTemplate(template: String) {
        viewModelScope.launch {
            val data = repository.data.first()
            val category = data.categories.firstOrNull { candidate ->
                candidate.isActive && (
                    candidate.name.contains(template, ignoreCase = true) ||
                        template.contains(candidate.name, ignoreCase = true)
                    )
            }
            form.update {
                it.copy(
                    title = if (it.title.isBlank()) template else it.title,
                    categoryId = category?.id ?: it.categoryId,
                )
            }
        }
    }

    /** Сохранить (и опционально сразу начать новый расход). */
    fun save(saveAndNew: Boolean = false) {
        viewModelScope.launch {
            val data = repository.data.first()
            val f = form.value
            val amount = com.paysplit.app.ui.components.parseMoney(f.amountText)
            if (amount == null || amount <= 0.0 || f.title.isBlank() || f.categoryId == 0L) {
                return@launch
            }
            if (expenseId != 0L) {
                val existing = repository.getExpense(expenseId) ?: return@launch
                repository.updateExpense(
                    existing.copy(
                        categoryId = f.categoryId,
                        amount = amount,
                        date = f.date,
                        title = f.title.trim(),
                        comment = f.comment.ifBlank { null },
                    ),
                )
                saved.value = true
            } else {
                repository.addExpense(
                    ExpenseEntity(
                        categoryId = f.categoryId,
                        amount = amount,
                        date = f.date,
                        title = f.title.trim(),
                        comment = f.comment.ifBlank { null },
                    ),
                )
                if (saveAndNew) {
                    form.update {
                        it.copy(amountText = "", title = "", comment = "")
                    }
                    savedAndNew.value = true
                } else {
                    saved.value = true
                }
            }
        }
    }

    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else value.toString().replace('.', ',')
}
