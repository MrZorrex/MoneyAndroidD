package com.paysplit.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.SettingsEntity
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.domain.DefaultCategories
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Int = 0,
    val totalSteps: Int = 4,
    val incomeText: String = "",
    val paydayText: String = "2",
    val useTemplate: Boolean = true,
    val finished: Boolean = false,
) {
    val income: Double get() = incomeText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val paydays: Int get() = paydayText.toIntOrNull()?.coerceIn(1, 10) ?: 2
    val canProceed: Boolean
        get() = when (step) {
            0, 3 -> true
            1 -> income > 0.0
            2 -> paydayText.toIntOrNull() != null
            else -> true
        }
}

class OnboardingViewModel(private val repository: PaySplitRepository) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setIncome(text: String) = _state.update {
        it.copy(incomeText = text.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' })
    }

    fun setPaydays(text: String) = _state.update {
        it.copy(paydayText = text.filter { ch -> ch.isDigit() }.take(2))
    }

    fun setUseTemplate(value: Boolean) = _state.update { it.copy(useTemplate = value) }

    fun next() = _state.update {
        if (it.step < it.totalSteps - 1) it.copy(step = it.step + 1) else it
    }

    fun back() = _state.update {
        if (it.step > 0) it.copy(step = it.step - 1) else it
    }

    /** Завершение онбординга: сохраняем настройки и, при желании, шаблон категорий. */
    fun finish() {
        viewModelScope.launch {
            val state = _state.value
            repository.updateSettings { settings: SettingsEntity ->
                settings.copy(
                    expectedMonthlyIncome = state.income,
                    paydayCount = state.paydays,
                    onboardingCompleted = true,
                )
            }
            if (state.useTemplate) {
                repository.resetCategoriesToTemplate()
            }
            _state.update { it.copy(finished = true) }
        }
    }
}
