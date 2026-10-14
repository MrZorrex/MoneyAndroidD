package com.paysplit.app.data.model

/**
 * Полный снимок базы данных. При изменении любой таблицы поток переиспускается,
 * а вся аналитика считается из этого снимка обычными функциями (см. пакет domain).
 */
data class AppData(
    val categories: List<CategoryEntity> = emptyList(),
    val incomes: List<IncomeEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val distributions: List<DistributionEntity> = emptyList(),
    val transfers: List<TransferEntity> = emptyList(),
    val settings: SettingsEntity = SettingsEntity(),
) {
    val isOnboardingCompleted: Boolean get() = settings.onboardingCompleted
    val isLockEnabled: Boolean get() = settings.pinHash != null || settings.biometricEnabled

    fun category(id: Long?): CategoryEntity? = categories.firstOrNull { it.id == id }

    /** Сумма планов всех активных категорий. */
    val totalMonthlyPlan: Double get() = categories.filter { it.isActive }.sumOf { it.monthlyPlan }
}

/**
 * Единая операция для отображения в истории и на экране счёта.
 */
data class UiTransaction(
    val id: Long,
    val date: Long,
    val type: TxType,
    val amount: Double,
    val categoryId: Long?,
    val categoryName: String,
    val icon: String,
    val title: String,
    val comment: String? = null,
    /** id исходной сущности (IncomeEntity / ExpenseEntity / TransferEntity). */
    val sourceId: Long,
) {
    val isIncoming: Boolean get() = type == TxType.INCOME || type == TxType.TRANSFER_IN
}
