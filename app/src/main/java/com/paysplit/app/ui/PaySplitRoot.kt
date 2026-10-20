package com.paysplit.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.paysplit.app.PaySplitApplication
import com.paysplit.app.R
import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.ThemeMode
import com.paysplit.app.ui.accounts.AccountsScreen
import com.paysplit.app.ui.analytics.AnalyticsScreen
import com.paysplit.app.ui.categories.CategoriesScreen
import com.paysplit.app.ui.category.CategoryDetailScreen
import com.paysplit.app.ui.dashboard.DashboardScreen
import com.paysplit.app.ui.expense.AddExpenseScreen
import com.paysplit.app.ui.history.HistoryScreen
import com.paysplit.app.ui.income.AddIncomeScreen
import com.paysplit.app.ui.lock.LockScreen
import com.paysplit.app.ui.navigation.Routes
import com.paysplit.app.ui.onboarding.OnboardingScreen
import com.paysplit.app.ui.settings.SettingsScreen
import com.paysplit.app.ui.theme.PaySplitTheme

/** Фабрика ViewModel, доступная всем экранам. */
val LocalViewModelFactory: ProvidableCompositionLocal<ViewModelProvider.Factory> =
    staticCompositionLocalOf { error("Фабрика ViewModel не предоставлена") }

/**
 * Корневой composable: онбординг → экран блокировки → основной интерфейс.
 */
@Composable
fun PaySplitRoot() {
    val context = LocalContext.current
    val container = remember(context) { PaySplitApplication.container(context) }
    val repository = remember(container) { container.repository }
    val factory = remember(container) {
        PaySplitViewModelFactory(container.repository, container.backupManager)
    }
    val state by repository.data.collectAsStateWithLifecycle(initialValue = null)

    PaySplitTheme(
        themeMode = state?.settings?.theme ?: ThemeMode.SYSTEM,
        dynamicColor = state?.settings?.dynamicColors ?: true,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val data = state
            when {
                data == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                !data.isOnboardingCompleted ->
                    CompositionLocalProvider(LocalViewModelFactory provides factory) {
                        OnboardingScreen()
                    }

                else -> MainFlow(data = data, factory = factory)
            }
        }
    }
}

@Composable
private fun MainFlow(data: AppData, factory: ViewModelProvider.Factory) {
    var unlocked by rememberSaveable(data.isLockEnabled) { mutableStateOf(!data.isLockEnabled) }

    // Блокировка при уходе приложения в фон
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (data.isLockEnabled) unlocked = false
    }

    CompositionLocalProvider(LocalViewModelFactory provides factory) {
        if (!unlocked) {
            LockScreen(onUnlocked = { unlocked = true })
        } else {
            MainScaffold()
        }
    }
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: Any,
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore('?')
    val showBottomBar = currentRoute in Routes.bottomBarRoutes

    val items = listOf(
        BottomItem(Routes.DASHBOARD, "Главная", Icons.Filled.Home),
        BottomItem(Routes.ACCOUNTS, "Счета", R.drawable.ic_wallet),
        BottomItem(Routes.HISTORY, "История", Icons.Filled.DateRange),
        BottomItem(Routes.ANALYTICS, "Аналитика", R.drawable.ic_chart),
        BottomItem(Routes.SETTINGS, "Настройки", Icons.Filled.Settings),
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route.substringBefore('?'),
                            onClick = { navController.navigateToTab(item.route) },
                            icon = {
                                when (val icon = item.icon) {
                                    is ImageVector -> Icon(icon, contentDescription = item.label)
                                    else -> Icon(
                                        painter = painterResource(icon as Int),
                                        contentDescription = item.label,
                                    )
                                }
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = {
                slideInHorizontally(tween(260)) { it / 8 } + fadeIn(tween(260))
            },
            exitTransition = {
                slideOutHorizontally(tween(260)) { -it / 8 } + fadeOut(tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(tween(260)) { -it / 8 } + fadeIn(tween(260))
            },
            popExitTransition = {
                slideOutHorizontally(tween(260)) { it / 8 } + fadeOut(tween(200))
            },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onAddIncome = { navController.navigate(Routes.addIncome()) },
                    onAddExpense = { navController.navigate(Routes.addExpense()) },
                    onCategoryClick = { id -> navController.navigate(Routes.categoryDetail(id)) },
                    onOpenAccounts = { navController.navigateToTab(Routes.ACCOUNTS) },
                )
            }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(
                    onCategoryClick = { id -> navController.navigate(Routes.categoryDetail(id)) },
                )
            }

            composable(
                route = Routes.ADD_INCOME,
                arguments = listOf(
                    navArgument(Routes.ARG_INCOME_ID) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                val incomeId = entry.arguments?.getLong(Routes.ARG_INCOME_ID) ?: 0L
                AddIncomeScreen(
                    incomeId = incomeId,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.ADD_EXPENSE,
                arguments = listOf(
                    navArgument(Routes.ARG_EXPENSE_ID) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { entry ->
                val expenseId = entry.arguments?.getLong(Routes.ARG_EXPENSE_ID) ?: 0L
                AddExpenseScreen(
                    expenseId = expenseId,
                    onDone = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.CATEGORY_DETAIL,
                arguments = listOf(
                    navArgument(Routes.ARG_CATEGORY_ID) { type = NavType.LongType },
                ),
            ) { entry ->
                val categoryId = entry.arguments?.getLong(Routes.ARG_CATEGORY_ID) ?: 0L
                CategoryDetailScreen(
                    categoryId = categoryId,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.HISTORY) {
                HistoryScreen()
            }

            composable(Routes.ANALYTICS) { AnalyticsScreen() }

            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
        }
    }
}

/** Переход по нижней панели с сохранением состояния. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
