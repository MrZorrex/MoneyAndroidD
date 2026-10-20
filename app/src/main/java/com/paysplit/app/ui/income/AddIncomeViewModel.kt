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
    /** Id счетов, исключённых пользователем из этого распределения. */
    val excludedIds: Set<Long> = emptySet(),
    /** Все активные счета (для отображения отключённых с 0). */
    val allActiveCategories: List<CategoryEntity> = emptyList(),
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
    val enabledCount: Int get() = allocations.count { it.categoryId !in excludedIds }
    val disabledCount: Int get() = excludedIds.size
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
        val excludedIds: Set<Long> = emptySet(),
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
                    // При редактировании восстанавливаем предыдущее распределение как manual + исключённые
                    val allDists = repository.data.first().distributions.filter { it.incomeId == incomeId }
                    val allocatedIds = allDists.map { it.categoryId }.toSet()
                    // Считаем исключёнными те активные счета, у которых нет распределения для этого income
                    val activeIds = repository.data.first().categories.filter { it.isActive }.map { it.id }.toSet()
                    val excluded = activeIds - allocatedIds
                    form.value = Form(
                        amountText = formatAmount(income.amount),
                        date = income.date,
                        source = income.source,
                        comment = income.comment.orEmpty(),
                        manual = null,
                        excludedIds = excluded,
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
        // result.allocations — только для включённых счетов
        val includedMap = result.allocations.associateBy { it.categoryId }

        // Все активные счета по порядку
        val allActive = data.categories.filter { it.isActive }.sortedBy { it.sortOrder }

        // Собираем список для UI: включённые — из result, исключённые — с 0
        // уже_распределено считаем как в BalanceCalculator: распределения + входящие переводы + положительные коррекции
        val monthStart = DateUtils.monthStart(f.date)
        val monthEnd = DateUtils.monthEnd(f.date)
        fun allocatedFor(catId: Long): Double {
            val d = data.distributions.filter { it.categoryId == catId && it.date in monthStart..monthEnd && it.incomeId != incomeId }.sumOf { it.amount }
            val tIn = data.transfers.filter { it.toCategoryId == catId && it.date in monthStart..monthEnd }.sumOf { it.amount }
            val adjPos = data.adjustments.filter { it.categoryId == catId && it.date in monthStart..monthEnd && it.amount > 0 }.sumOf { it.amount }
            return d + tIn + adjPos
        }
        val uiAllocations = allActive.map { cat ->
            if (cat.id in f.excludedIds) {
                val allocated = allocatedFor(cat.id)
                DistributionCalculator.Allocation(
                    categoryId = cat.id,
                    categoryName = cat.name,
                    icon = cat.icon,
                    plan = cat.monthlyPlan,
                    alreadyAllocated = allocated,
                    remainder = max(0.0, cat.monthlyPlan - allocated),
                    amount = 0.0,
                )
            } else {
                includedMap[cat.id] ?: run {
                    val allocated = allocatedFor(cat.id)
                    DistributionCalculator.Allocation(
                        categoryId = cat.id,
                        categoryName = cat.name,
                        icon = cat.icon,
                        plan = cat.monthlyPlan,
                        alreadyAllocated = allocated,
                        remainder = max(0.0, cat.monthlyPlan - allocated),
                        amount = 0.0,
                    )
                }
            }
        }

        // Применяем ручные значения если есть
        val allocations = if (f.manual != null) {
            uiAllocations.map { it.copy(amount = f.manual[it.categoryId] ?: 0.0) }
        } else {
            uiAllocations
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
            excludedIds = f.excludedIds,
            allActiveCategories = allActive,
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

    /** Пропорциональный расчёт для текущей формы с учётом исключённых счетов. */
    private fun calculateResult(
        data: AppData,
        f: Form,
        amount: Double,
    ): DistributionCalculator.Result {
        val monthStart = DateUtils.monthStart(f.date)
        val monthEnd = DateUtils.monthEnd(f.date)
        val inputs = data.categories
            .filter { it.isActive && it.id !in f.excludedIds }
            .sortedBy { it.sortOrder }
            .map { category: CategoryEntity ->
                val d = data.distributions.filter { it.categoryId == category.id && it.date in monthStart..monthEnd && it.incomeId != incomeId }.sumOf { it.amount }
                val tIn = data.transfers.filter { it.toCategoryId == category.id && it.date in monthStart..monthEnd }.sumOf { it.amount }
                val adjPos = data.adjustments.filter { it.categoryId == category.id && it.date in monthStart..monthEnd && it.amount > 0 }.sumOf { it.amount }
                val allocated = d + tIn + adjPos
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

    fun setComment(comment: String) = form.update {
        it.copy(comment = com.paysplit.app.ui.components.capitalizeFirst(comment))
    }

    /** Вкл/выкл счёт в распределении. */
    fun toggleCategory(categoryId: Long) {
        form.update { cur ->
            val newExcluded = if (categoryId in cur.excludedIds) cur.excludedIds - categoryId else cur.excludedIds + categoryId
            cur.copy(excludedIds = newExcluded, manual = null)
        }
    }

    fun setAllEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val data = repository.data.first()
            val allIds = data.categories.filter { it.isActive }.map { it.id }.toSet()
            form.update { it.copy(excludedIds = if (enabled) emptySet() else allIds, manual = null) }
        }
    }

    /** Переход в режим ручной корректировки. */
    fun startManualAdjustment() {
        viewModelScope.launch {
            val data = repository.data.first()
            val f = form.value
            val amount = com.paysplit.app.ui.components.parseMoney(f.amountText) ?: 0.0
            val auto = calculateResult(data, f, amount).allocations
            // Для ручного режима учитываем и исключённые (0)
            val allActive = data.categories.filter { it.isActive && it.id !in f.excludedIds }
            val manualMap = mutableMapOf<Long, Double>()
            // Добавляем включённые из auto
            auto.forEach { manualMap[it.categoryId] = it.amount }
            // Исключённые → 0
            f.excludedIds.forEach { manualMap[it] = 0.0 }
            // На случай если какой-то активный не попал в auto (остаток 0)
            if (manualMap.size < allActive.size) {
                allActive.forEach { cat ->
                    if (cat.id !in manualMap) manualMap[cat.id] = 0.0
                }
            }
            form.update { it.copy(manual = manualMap) }
        }
    }

    /** Правка одной суммы с пересчётом остальных (только для включённых). */
    fun setManualAmount(categoryId: Long, value: Double) {
        viewModelScope.launch {
            val data = repository.data.first()
            val f = form.value
            val amount = com.paysplit.app.ui.components.parseMoney(f.amountText) ?: 0.0
            // Если пытаются править исключённый — сначала включаем его
            val effectiveExcluded = if (categoryId in f.excludedIds && value > 0.005) f.excludedIds - categoryId else f.excludedIds
            val effectiveForm = f.copy(excludedIds = effectiveExcluded)
            val auto = calculateResult(data, effectiveForm, amount).allocations
            // adjustManually ожидает список allocations только для включённых
            val amounts = DistributionCalculator.adjustManually(amount, auto, categoryId, value)
            val manual = auto.mapIndexed { index, allocation ->
                allocation.categoryId to amounts.getOrElse(index) { 0.0 }
            }.toMap().toMutableMap()
            // Добавляем исключённые как 0
            effectiveExcluded.forEach { id -> manual[id] = 0.0 }
            form.update { it.copy(excludedIds = effectiveExcluded, manual = manual) }
        }
    }

    /** Вернуться к автоматическому расчёту. */
    fun resetAuto() = form.update { it.copy(manual = null) }

    /** Сброс исключений — включить все счета. */
    fun resetExcluded() = form.update { it.copy(excludedIds = emptySet(), manual = null) }

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
            // Собираем финальные аллокации: для включённых — из auto/manual, для исключённых — 0
            var finalAllocations: List<Pair<Long, Double>> = if (f.manual != null) {
                // manual уже содержит 0 для исключённых
                f.manual.map { (id, v) -> id to v }
            } else {
                val includedPairs = auto.map { it.categoryId to it.amount }
                val excludedPairs = f.excludedIds.map { it to 0.0 }
                includedPairs + excludedPairs
            }

            // Излишек (доход > планов) — записываем на «Свободные средства» чтобы он показывался в балансе и был доступен для перевода
            val allocatedSum = finalAllocations.sumOf { it.second }
            val surplus = amount - allocatedSum
            if (surplus > 0.5) {
                val freeFunds = repository.ensureFreeFundsCategory()
                // если уже есть аллокация на свободные — суммируем, иначе добавляем
                val existingFree = finalAllocations.firstOrNull { it.first == freeFunds.id }
                finalAllocations = if (existingFree != null) {
                    finalAllocations.map { if (it.first == freeFunds.id) it.first to (it.second + surplus) else it }
                } else {
                    finalAllocations + (freeFunds.id to surplus)
                }
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
                    finalAllocations,
                )
            } else {
                repository.addIncomeWithDistribution(
                    IncomeEntity(
                        amount = amount,
                        date = f.date,
                        source = f.source,
                        comment = f.comment.ifBlank { null },
                    ),
                    finalAllocations,
                )
            }
            saved.value = true
        }
    }

    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else Formatters.money(value, "").replace(' ', ' ').trim()
}
