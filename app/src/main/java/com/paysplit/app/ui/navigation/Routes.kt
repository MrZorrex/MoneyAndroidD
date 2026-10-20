package com.paysplit.app.ui.navigation

/** Маршруты навигации. */
object Routes {
    const val DASHBOARD = "dashboard"

    /** Экран расхода: expenseId = 0 — новый расход. */
    const val ADD_EXPENSE = "add_expense?expenseId={expenseId}"
    const val ARG_EXPENSE_ID = "expenseId"

    /** Экран поступления: incomeId = 0 — новое поступление. */
    const val ADD_INCOME = "add_income?incomeId={incomeId}"
    const val ARG_INCOME_ID = "incomeId"

    const val HISTORY = "history"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val CATEGORY_DETAIL = "category/{categoryId}"
    const val ARG_CATEGORY_ID = "categoryId"
    const val CATEGORIES = "categories"
    const val ACCOUNTS = "accounts"

    fun addIncome(): String = "add_income?incomeId=0"

    fun editIncome(incomeId: Long): String = "add_income?incomeId=$incomeId"

    fun addExpense(): String = "add_expense?expenseId=0"

    fun editExpense(expenseId: Long): String = "add_expense?expenseId=$expenseId"

    fun categoryDetail(categoryId: Long): String = "category/$categoryId"

    /** Экраны нижней панели (по началу маршрута) — 5 вкладок. */
    val bottomBarRoutes = setOf("dashboard", "accounts", "history", "analytics", "settings")
}
