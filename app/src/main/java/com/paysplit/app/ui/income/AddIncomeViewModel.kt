package com.paysplit.app.ui.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.IncomeEntity
import com.paysplit.app.data.model.IncomeSource
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.domain.DistributionCalculator
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddIncomeUiState(
    val loading: Boolean = true,
    val isEditing: Boolean = false,
    val amountText: String = "",
    val amount: Double = 0.0,
    val date: Long = 0L,
    val source: IncomeSource = IncomeSource.OTHER,
    val comment: String = "",
    val allocations: List<DistributionCalculator.Allocation> = emptyList(),
    /** Свободные средства (поступление больше суммы остатков). */
    val surplus: Double = 0.0,
    /** Нераспределённый остаток при ручной правке. */
    val undistributed: Double = 0.0,
    val manualMode: Boolean = false,
    val currency: String = "₽",
    /** Все категории по id — для редактирования плана/картинки прямо на экране. */
    val categoryById: Map<Long, CategoryEntity> = emptyMap(),
    val saved: Boolean = false,
    val error: String? = null,
) {
    val isAmountValid: Boolean get() = amount > 0.0
    val distributed: Double get() = allocations.sumOf { it.amount }
}

class AddIncomeViewModel(
    private val repository: PaySplitRepository,
    private val incomeId: Long = 0L,
) : ViewModel() {

    private data class Form(
        val amountText: String = "",
        val date: Long = DateUtils.today(),
        val source: IncomeSource = IncomeSource.OTHER,
        val comment: String = "",
        val manual: Map<Long, Double>? = null,
        val initialized: Boolean = false,
    )

    private val form = MutableStateFlow(Form())
    private val saved = MutableStateFlow(false)

    val state: StateFlow<AddIncomeUiState> =
        combine(repository.data, form, saved) { data, f, isSaved ->
            buildState(data, f, isSaved)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AddIncomeUiState())

    init {
        viewModelScope.launch {
            if (incomeId != 0L) {
                val income = repository.getIncome(incomeId)
                if (income != null) {
                    form.value = Form(
                        amountText = formatAmount(income.amount),
                        date = income.date,
                        source = income.source,
                        comment = income.comment.orEmpty(),
                        manual = null,
                        initialized = true,
                    )
                    return@launch
                }
            }
            form.update { it.copy(initialized = true) }
        }
    }

    private fun buildState(data: AppData, f: Form, isSaved: Boolean): AddIncomeUiState {
        val amount = com.paysplit.app.ui.components.parseMoney(f.amountText) ?: 0.0
        val result = calculateResult(data, f, amount)
        val auto = result.allocations
        val allocations = if (f.manual != null) {
            auto.map { it.copy(amount = f.manual[it.categoryId] ?: 0.0) }
        } else {
            auto
        }
        val distributed = allocations.sumOf { it.amount }
        val editingIncome = if (incomeId != 0L) data.incomes.firstOrNull { it.id == incomeId } else null

        return AddIncomeUiState(
            loading = !f.initialized,
            isEditing = incomeId != 0L,
            amountText = f.amountText,
            amount = amount,
            date = f.date,
            source = f.source,
            comment = f.comment,
            allocations = allocations,
            surplus = if (f.manual != null) 0.0 else result.surplus,
            undistributed = max(0.0, amount - distributed),
            manualMode = f.manual != null,
            currency = data.settings.currency,
            categoryById = data.categories.associateBy { it.id },
            saved = isSaved,
            error = when {
                incomeId != 0L && editingIncome == null -> "Поступление не найдено"
                f.amountText.isNotBlank() && amount <= 0.0 -> "Введите сумму больше нуля"
                else -> null
            },
        )
    }

    /** Пропорциональный расчёт для текущей формы. */
    private fun calculateResult(
        data: AppData,
        f: Form,
        amount: Double,
    ): DistributionCalculator.Result {
        val monthStart = DateUtils.monthStart(f.date)
        val monthEnd = DateUtils.monthEnd(f.date)
        val inputs = data.categories
            .filter { it.isActive }
            .sortedBy { it.sortOrder }
            .map { category: CategoryEntity ->
                val allocated = data.distributions
                    .filter {
                        it.categoryId == category.id &&
                            it.date in monthStart..monthEnd &&
                            it.incomeId != incomeId
                    }
                    .sumOf { it.amount }
                DistributionCalculator.CategoryInput(
                    id = category.id,
                    name = category.name,
                    icon = category.icon,
                    monthlyPlan = category.monthlyPlan,
                    allocatedThisMonth = allocated,
                )
            }
        return DistributionCalculator.calculate(amount, inputs)
    }

    // --- Действия пользователя ---

    fun setAmount(text: String) = form.update { it.copy(amountText = text, manual = null) }

    fun setDate(date: Long) = form.update { it.copy(date = date, manual = null) }

    fun setSource(source: IncomeSource) = form.update { it.copy(source = source) }

    fun setComment(comment: String) = form.update { it.copy(comment = comment) }

    /** Переход в режим ручной корректировки. */
    fun startManualAdjustment() {
        viewModelScope.launch {
            val data = repository.data.first()
            val f = form.value
            val amount = com.paysplit.app.ui.components.parseMoney(f.amountText) ?: 0.0
            val auto = calculateResult(data, f, amount).allocations
            form.update { it.copy(manual = auto.associate { a -> a.categoryId to a.amount }) }
        }
    }

    /** Правка одной суммы с пересчётом остальных. */
    fun setManualAmount(categoryId: Long, value: Double) {
        viewModelScope.launch {
            val data = repository.data.first()
            val f = form.value
            val amount = com.paysplit.app.ui.components.parseMoney(f.amountText) ?: 0.0
            val auto = calculateResult(data, f, amount).allocations
            val amounts = DistributionCalculator.adjustManually(amount, auto, categoryId, value)
            val manual = auto.mapIndexed { index, allocation ->
                allocation.categoryId to amounts.getOrElse(index) { 0.0 }
            }.toMap()
            form.update { it.copy(manual = manual) }
        }
    }

    /** Вернуться к автоматическому расчёту. */
    fun resetAuto() = form.update { it.copy(manual = null) }

    /** Сохранение настроек категории прямо с экрана поступления. */
    fun saveCategory(updated: CategoryEntity) {
        viewModelScope.launch { repository.saveCategory(updated) }
    }

    fun save() {
        viewModelScope.launch {
            val data = repository.data.first()
            val f = form.value
            val amount = com.paysplit.app.ui.components.parseMoney(f.amountText)
            if (amount == null || amount <= 0.0) return@launch

            val auto = calculateResult(data, f, amount).allocations
            val allocations = if (f.manual != null) {
                auto.map { it.categoryId to (f.manual[it.categoryId] ?: 0.0) }
            } else {
                auto.map { it.categoryId to it.amount }
            }

            if (incomeId != 0L) {
                val existing = repository.getIncome(incomeId) ?: return@launch
                repository.updateIncomeWithDistribution(
                    existing.copy(
                        amount = amount,
                        date = f.date,
                        source = f.source,
                        comment = f.comment.ifBlank { null },
                    ),
                    allocations,
                )
            } else {
                repository.addIncomeWithDistribution(
                    IncomeEntity(
                        amount = amount,
                        date = f.date,
                        source = f.source,
                        comment = f.comment.ifBlank { null },
                    ),
                    allocations,
                )
            }
            saved.value = true
        }
    }

    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else Formatters.money(value, "").replace(' ', ' ').trim()
}
