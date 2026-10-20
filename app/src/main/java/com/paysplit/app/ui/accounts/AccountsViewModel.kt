package com.paysplit.app.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.BalanceAdjustmentEntity
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

data class AccountsUiState(
    val items: List<BalanceCalculator.CategoryBalance> = emptyList(),
    val totalPlan: Double = 0.0,
    val expectedIncome: Double = 0.0,
    val difference: Double = 0.0,
    val currency: String = "₽",
    val totalBalance: Double = 0.0,
)

/** Состояние редактора категории. */
data class AccountEditorState(
    val visible: Boolean = false,
    val editingId: Long = 0L,
    val name: String = "",
    val icon: String = "💰",
    val planText: String = "",
    val type: CategoryType = CategoryType.INSTANT_PAY,
    val targetText: String = "",
    val targetDate: Long? = null,
    val isActive: Boolean = true,
    val savedAmount: Double = 0.0,
) {
    val isEditing: Boolean get() = editingId != 0L
}

/** Состояние диалога коррекции баланса. */
data class BalanceAdjustState(
    val visible: Boolean = false,
    val categoryId: Long = 0L,
    val categoryName: String = "",
    val currentBalance: Double = 0.0,
    val icon: String = "💰",
)

class AccountsViewModel(private val repository: PaySplitRepository) : ViewModel() {

    private val editor = MutableStateFlow(AccountEditorState())
    private val adjustState = MutableStateFlow(BalanceAdjustState())

    val state: StateFlow<AccountsUiState> = repository.data.map { data ->
        val monthStart = DateUtils.monthStart(System.currentTimeMillis())
        val monthEnd = DateUtils.monthEnd(System.currentTimeMillis())
        val items = BalanceCalculator.compute(data, monthStart, monthEnd)
            .sortedBy { it.category.sortOrder }
        AccountsUiState(
            items = items,
            totalPlan = data.totalMonthlyPlan,
            expectedIncome = data.settings.expectedMonthlyIncome,
            difference = data.settings.expectedMonthlyIncome - data.totalMonthlyPlan,
            currency = data.settings.currency,
            totalBalance = BalanceCalculator.totalBalance(data),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    val editorState: StateFlow<AccountEditorState> = editor
    val balanceAdjustState: StateFlow<BalanceAdjustState> = adjustState

    // --- Редактор категории ---
    fun openNew() {
        editor.value = AccountEditorState(visible = true, icon = "💰")
    }

    fun openEdit(category: CategoryEntity, savedAmount: Double = 0.0) {
        editor.value = AccountEditorState(
            visible = true,
            editingId = category.id,
            name = category.name,
            icon = category.icon,
            planText = formatAmount(category.monthlyPlan),
            type = category.type,
            targetText = category.targetAmount?.let { formatAmount(it) } ?: "",
            targetDate = category.targetDate,
            isActive = category.isActive,
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
            name = name?.let { com.paysplit.app.ui.components.capitalizeFirst(it) } ?: current.name,
            icon = icon ?: current.icon,
            planText = planText ?: current.planText,
            type = type ?: current.type,
            targetText = targetText ?: current.targetText,
            targetDate = targetDate ?: current.targetDate,
            isActive = isActive ?: current.isActive,
        )
        if ((targetText != null || type != null) && next.type == CategoryType.SAVINGS) {
            next.copy(planText = autoPlanText(next))
        } else {
            next
        }
    }

    fun setEditorTargetDate(date: Long?) {
        editor.update { current ->
            val next = current.copy(targetDate = date)
            if (next.type == CategoryType.SAVINGS) next.copy(planText = autoPlanText(next)) else next
        }
    }

    private fun autoPlanText(state: AccountEditorState): String {
        val target = com.paysplit.app.ui.components.parseMoney(state.targetText) ?: return state.planText
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
            val existing = if (e.editingId != 0L) snapshot.categories.firstOrNull { it.id == e.editingId } else null
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

    // --- Коррекция баланса ---
    fun openBalanceAdjust(category: CategoryEntity, currentBalance: Double) {
        adjustState.value = BalanceAdjustState(
            visible = true,
            categoryId = category.id,
            categoryName = category.name,
            currentBalance = currentBalance,
            icon = category.icon,
        )
    }

    fun dismissBalanceAdjust() = adjustState.update { it.copy(visible = false) }

    fun confirmBalanceAdjust(amount: Double, comment: String?, date: Long = System.currentTimeMillis(), sticker: String? = null) {
        val cur = adjustState.value
        if (cur.categoryId == 0L || amount == 0.0) return
        viewModelScope.launch {
            repository.addAdjustment(
                BalanceAdjustmentEntity(
                    categoryId = cur.categoryId,
                    amount = amount,
                    date = date,
                    comment = comment?.trim()?.takeIf { it.isNotBlank() },
                    sticker = sticker?.takeIf { amount > 0 },
                )
            )
            adjustState.update { it.copy(visible = false) }
        }
    }

    fun setBalanceTo(amount: Double, comment: String?, date: Long = System.currentTimeMillis(), sticker: String? = null) {
        val cur = adjustState.value
        val delta = amount - cur.currentBalance
        if (delta == 0.0) {
            dismissBalanceAdjust()
            return
        }
        confirmBalanceAdjust(delta, comment, date, sticker)
    }

    /** Пополнение счёта из свободных средств — перевод из 💵 Свободные средства. */
    fun topUpFromFreeFunds(targetCategoryId: Long, amount: Double, sticker: String?, comment: String?, date: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val freeFunds = repository.ensureFreeFundsCategory()
            if (freeFunds.id == targetCategoryId) return@launch
            repository.addTransfer(
                com.paysplit.app.data.model.TransferEntity(
                    fromCategoryId = freeFunds.id,
                    toCategoryId = targetCategoryId,
                    amount = amount,
                    date = date,
                    comment = (comment?.trim()?.takeIf { it.isNotBlank() } ?: "Пополнение из свободных средств") + (sticker?.let { " $it" } ?: ""),
                )
            )
        }
    }

    // --- Список ---
    fun applyOrder(orderedIds: List<Long>) {
        viewModelScope.launch { repository.applyCategoryOrder(orderedIds) }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch { repository.deleteCategory(category.id) }
    }

    fun resetToTemplate() {
        viewModelScope.launch { repository.resetCategoriesToTemplate() }
    }

    fun deleteAdjustment(id: Long) {
        viewModelScope.launch { repository.deleteAdjustment(id) }
    }

    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')
}
