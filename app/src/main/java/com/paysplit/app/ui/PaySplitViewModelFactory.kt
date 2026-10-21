package com.paysplit.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.paysplit.app.data.backup.BackupManager
import com.paysplit.app.data.repository.PaySplitRepository
import com.paysplit.app.ui.accounts.AccountsViewModel
import com.paysplit.app.ui.analytics.AnalyticsViewModel
import com.paysplit.app.ui.categories.CategoriesViewModel
import com.paysplit.app.ui.dashboard.DashboardViewModel
import com.paysplit.app.ui.history.HistoryViewModel
import com.paysplit.app.ui.onboarding.OnboardingViewModel
import com.paysplit.app.ui.settings.SettingsViewModel

/**
 * Единая фабрика ViewModel. Приложение одмодульное, зависимости создаются вручную.
 *
 * Экраны с параметрами (поступление, расход, счёт, блокировка) создают свои
 * ViewModel напрямую: им нужны идентификаторы из маршрута навигации.
 */
class PaySplitViewModelFactory(
    private val repository: PaySplitRepository,
    private val backupManager: BackupManager,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(repository)

            modelClass.isAssignableFrom(AccountsViewModel::class.java) ->
                AccountsViewModel(repository)

            modelClass.isAssignableFrom(CategoriesViewModel::class.java) ->
                CategoriesViewModel(repository)

            modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                HistoryViewModel(repository)

            modelClass.isAssignableFrom(AnalyticsViewModel::class.java) ->
                AnalyticsViewModel(repository)

            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(repository, backupManager)

            modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
                OnboardingViewModel(repository)

            else -> error("Неизвестная ViewModel: ${modelClass.name}")
        }
        return viewModel as T
    }
}
