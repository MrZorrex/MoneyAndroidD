package com.paysplit.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paysplit.app.data.model.AnalyticsPeriod
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.domain.AnalyticsEngine
import com.paysplit.app.util.DateUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class AnalyticsUiState(
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val stats: AnalyticsEngine.PeriodStats? = null,
    val savings: List<Pair<CategoryEntity, Double>> = emptyList(),
    val savingsGrowth: List<Pair<YearMonth, Double>> = emptyList(),
    /** Прогноз в месяцах до цели по каждому счёту. */
    val forecast: Map<Long, Int> = emptyMap(),
    val savingsTotal: Double = 0.0,
    val savingsMonthlyRate: Double = 0.0,
    val monthLabels: List<String> = emptyList(),
    val currency: String = "₽",
)

class AnalyticsViewModel(private val repository: PaySplitRepository) : ViewModel() {

    var period: AnalyticsPeriod = AnalyticsPeriod.MONTH
        private set

    val state: StateFlow<AnalyticsUiState> = repository.data.map { data ->
        val to = System.currentTimeMillis()
        val from = DateUtils.monthStartMonthsBack(period.months - 1, to)
        val stats = AnalyticsEngine.compute(data, from, to)
        val months = AnalyticsEngine.buildMonthList(from, to)
        val savings = AnalyticsEngine.savingsTargets(data)
        val rate = AnalyticsEngine.savingsMonthlyRate(data)
        val growth = AnalyticsEngine.savingsGrowth(data, months)

        AnalyticsUiState(
            period = period,
            stats = stats,
            savings = savings,
            savingsGrowth = growth,
            forecast = savings.mapNotNull { (category, balance) ->
                val target = category.targetAmount ?: return@mapNotNull null
                val monthsLeft = AnalyticsEngine.forecastMonths(balance, target, rate)
                if (monthsLeft == null) null else category.id to monthsLeft
            }.toMap(),
            savingsTotal = AnalyticsEngine.savingsBalance(data),
            savingsMonthlyRate = rate,
            monthLabels = months.map { DateUtils.formatMonthShort(it) },
            currency = data.settings.currency,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun setPeriod(value: AnalyticsPeriod) {
        period = value
    }
}
