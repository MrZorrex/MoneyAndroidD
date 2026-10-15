package com.paysplit.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.CategoryType
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.domain.SavingsPlanner
import com.paysplit.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val items: List<BalanceCalculator.CategoryBalance> = emptyList(),
    val totalPlan: Double = 0.0,
    val expectedIncome: Double = 0.0,
    val difference: Double = 0.0,
    val currency: String = "₽",
)

/** Состояние редактора категории. */
data class CategoryEditorState(
    val visible: Boolean = false,
    val editingId: Long = 0L,
    val name: String = "",
    val icon: String = "💰",
    val planText: String = "",
    val type: CategoryType = CategoryType.INSTANT_PAY,
    val targetText: String = "",
    val targetDate: Long? = null,
    val isActive: Boolean = true,
    val isSavings: Boolean = false,
    /** Уже накоплено — вычитается из цели при авторасчёте месячного плана. */
    val savedAmount: Double = 0.0,
) {
    val isEditing: Boolean get() = editingId != 0L
}

class CategoriesViewModel(private val repository: PaySplitRepository) : ViewModel() {

    private val editor = MutableStateFlow(CategoryEditorState())

    val state: StateFlow<CategoriesUiState> = repository.data.map { data ->
        val monthStart = DateUtils.monthStart(System.currentTimeMillis())
        val monthEnd = DateUtils.monthEnd(System.currentTimeMillis())
        val items = BalanceCalculator.compute(data, monthStart, monthEnd)
            .sortedBy { it.category.sortOrder }
        CategoriesUiState(
            items = items,
            totalPlan = data.totalMonthlyPlan,
            expectedIncome = data.settings.expectedMonthlyIncome,
            difference = data.settings.expectedMonthlyIncome - data.totalMonthlyPlan,
            currency = data.settings.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    val editorState: StateFlow<CategoryEditorState> = editor

    // --- Редактор ---

    fun openNew() {
        editor.value = CategoryEditorState(visible = true, icon = "💰")
    }

    fun openEdit(category: CategoryEntity, savedAmount: Double = 0.0) {
        editor.value = CategoryEditorState(
            visible = true,
            editingId = category.id,
            name = category.name,
            icon = category.icon,
            planText = formatAmount(category.monthlyPlan),
            type = category.type,
            targetText = category.targetAmount?.let { formatAmount(it) } ?: "",
            targetDate = category.targetDate,
            isActive = category.isActive,
            isSavings = category.type == CategoryType.SAVINGS,
            savedAmount = savedAmount,
        )
    }

    fun dismissEditor() = editor.update { it.copy(visible = false) }

    fun updateEditor(
        name: String? = null,
        icon: String? = null,
        planText: String? = null,
        type: CategoryType? = null,
        targetText: String? = null,
        targetDate: Long? = null,
        isActive: Boolean? = null,
    ) = editor.update { current ->
        val next = current.copy(
            name = name?.let { com.paysplit.app.ui.components.capitalizeFirst(it) }
                ?: current.name,
            icon = icon ?: current.icon,
            planText = planText ?: current.planText,
            type = type ?: current.type,
            targetText = targetText ?: current.targetText,
            targetDate = targetDate ?: current.targetDate,
            isActive = isActive ?: current.isActive,
            isSavings = (type ?: current.type) == CategoryType.SAVINGS,
        )
        // Цель или тип изменились — пересчитываем месячный план автоматически.
        // Ручная правка самого плана (planText) не перезаписывается.
        if ((targetText != null || type != null) && next.type == CategoryType.SAVINGS) {
            next.copy(planText = autoPlanText(next))
        } else {
            next
        }
    }

    /** Установка даты цели (null — очистить) с пересчётом месячного плана. */
    fun setEditorTargetDate(date: Long?) {
        editor.update { current ->
            val next = current.copy(targetDate = date)
            if (next.type == CategoryType.SAVINGS) next.copy(planText = autoPlanText(next))
            else next
        }
    }

    /**
     * Авторасчёт месячного плана из цели и даты.
     * Если посчитать нельзя (нет цели/даты) — возвращает текущее значение поля,
     * ручной ввод пользователя не трогаем.
     */
    private fun autoPlanText(state: CategoryEditorState): String {
        val target = com.paysplit.app.ui.components.parseMoney(state.targetText)
            ?: return state.planText
        val date = state.targetDate ?: return state.planText
        if (target <= 0.0) return state.planText
        return formatAmount(SavingsPlanner.monthlyPlan(target, state.savedAmount, date))
    }

    fun saveEditor() {
        viewModelScope.launch {
            val e = editor.value
            val plan = com.paysplit.app.ui.components.parseMoney(e.planText) ?: 0.0
            val target = com.paysplit.app.ui.components.parseMoney(e.targetText)
            val snapshot = repository.snapshot()
            val existing = if (e.editingId != 0L) {
                snapshot.categories.firstOrNull { it.id == e.editingId }
            } else {
                null
            }
            val nextSortOrder = (snapshot.categories.maxOfOrNull { it.sortOrder } ?: -1) + 1
            val category = CategoryEntity(
                id = e.editingId,
                name = e.name.trim().ifBlank { "Счёт" },
                icon = e.icon,
                monthlyPlan = plan,
                type = e.type,
                targetAmount = if (e.type == CategoryType.SAVINGS) target else null,
                targetDate = if (e.type == CategoryType.SAVINGS) e.targetDate else null,
                isActive = e.isActive,
                sortOrder = existing?.sortOrder ?: nextSortOrder,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            repository.saveCategory(category)
            editor.update { it.copy(visible = false) }
        }
    }

    // --- Список ---

    /** Перемещение категории при перетаскивании. */
    fun move(from: Int, to: Int) {
        viewModelScope.launch {
            val data = repository.data.first()
            val ids = data.categories.sortedBy { it.sortOrder }.map { it.id }.toMutableList()
            if (from !in ids.indices || to !in ids.indices) return@launch
            ids.add(to, ids.removeAt(from))
            repository.applyCategoryOrder(ids)
        }
    }

    /** Сохранить порядок после перетаскивания. */
    fun applyOrder(orderedIds: List<Long>) {
        viewModelScope.launch { repository.applyCategoryOrder(orderedIds) }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch { repository.deleteCategory(category.id) }
    }

    fun resetToTemplate() {
        viewModelScope.launch { repository.resetCategoriesToTemplate() }
    }

    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else value.toString().replace('.', ',')
}
