package com.paysplit.app.data.repository

import androidx.room.withTransaction
import com.paysplit.app.data.local.AppDatabase
import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.BalanceAdjustmentEntity
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.DistributionEntity
import com.paysplit.app.data.model.ExpenseEntity
import com.paysplit.app.data.model.IncomeEntity
import com.paysplit.app.data.model.SettingsEntity
import com.paysplit.app.data.model.TransferEntity
import com.paysplit.app.domain.DefaultCategories
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Единая точка доступа к данным. Отдаёт поток [data] со снимком всей базы —
 * на него подписаны все экраны, а аналитика считается обычными функциями.
 */
class PaySplitRepository(private val db: AppDatabase) {

    private val categoryDao = db.categoryDao()
    private val incomeDao = db.incomeDao()
    private val expenseDao = db.expenseDao()
    private val transferDao = db.transferDao()
    private val adjustmentDao = db.balanceAdjustmentDao()
    private val settingsDao = db.settingsDao()

    private data class CoreData(
        val categories: List<CategoryEntity>,
        val incomes: List<IncomeEntity>,
        val expenses: List<ExpenseEntity>,
        val distributions: List<DistributionEntity>,
    )

    private val core: Flow<CoreData> = combine(
        categoryDao.observeAll(),
        incomeDao.observeAll(),
        expenseDao.observeAll(),
        incomeDao.observeDistributions(),
    ) { categories, incomes, expenses, distributions ->
        CoreData(categories, incomes, expenses, distributions)
    }

    /** Полный снимок базы данных. */
    val data: Flow<AppData> = combine(
        combine(core, transferDao.observeAll()) { c, transfers -> c to transfers },
        combine(adjustmentDao.observeAll(), settingsDao.observe()) { adj, settings -> adj to settings }
    ) { (c, transfers), (adjustments, settings) ->
        AppData(
            categories = c.categories,
            incomes = c.incomes,
            expenses = c.expenses,
            distributions = c.distributions,
            transfers = transfers,
            adjustments = adjustments,
            settings = settings ?: SettingsEntity(),
        )
    }

    val expenseTitles: Flow<List<String>> = expenseDao.observeTitles()

    // ---------------- Поступления и распределение ----------------

    /** Сохраняет поступление и его распределение по счетам одной транзакцией. */
    suspend fun addIncomeWithDistribution(
        income: IncomeEntity,
        allocations: List<Pair<Long, Double>>,
    ): Long = db.withTransaction {
        val incomeId = incomeDao.insert(income)
        incomeDao.insertDistributions(
            allocations
                .filter { it.second > 0.004 }
                .map { (categoryId, amount) ->
                    DistributionEntity(
                        incomeId = incomeId,
                        categoryId = categoryId,
                        amount = amount,
                        date = income.date,
                    )
                },
        )
        incomeId
    }

    /** Изменяет поступление и перезаписывает его распределение. */
    suspend fun updateIncomeWithDistribution(
        income: IncomeEntity,
        allocations: List<Pair<Long, Double>>,
    ) = db.withTransaction {
        incomeDao.update(income)
        incomeDao.deleteDistributions(income.id)
        incomeDao.insertDistributions(
            allocations
                .filter { it.second > 0.004 }
                .map { (categoryId, amount) ->
                    DistributionEntity(
                        incomeId = income.id,
                        categoryId = categoryId,
                        amount = amount,
                        date = income.date,
                    )
                },
        )
    }

    suspend fun deleteIncome(id: Long) {
        incomeDao.deleteById(id)
    }

    suspend fun getIncome(id: Long): IncomeEntity? = incomeDao.getById(id)

    // ---------------- Расходы ----------------

    suspend fun addExpense(expense: ExpenseEntity): Long = expenseDao.insert(expense)

    suspend fun updateExpense(expense: ExpenseEntity) {
        expenseDao.update(expense)
    }

    suspend fun deleteExpense(id: Long) {
        expenseDao.deleteById(id)
    }

    suspend fun getExpense(id: Long): ExpenseEntity? = expenseDao.getById(id)

    // ---------------- Переводы ----------------

    /** Перевод между счетами: расход с одного + доход на другой. */
    suspend fun addTransfer(transfer: TransferEntity): Long = transferDao.insert(transfer)

    suspend fun deleteTransfer(id: Long) {
        transferDao.deleteById(id)
    }

    suspend fun getTransfer(id: Long): TransferEntity? = transferDao.getById(id)

    // ---------------- Коррекции баланса ----------------

    suspend fun addAdjustment(adjustment: BalanceAdjustmentEntity): Long =
        adjustmentDao.insert(adjustment)

    suspend fun deleteAdjustment(id: Long) {
        adjustmentDao.deleteById(id)
    }

    suspend fun getAdjustment(id: Long): BalanceAdjustmentEntity? = adjustmentDao.getById(id)

    // ---------------- Категории ----------------

    suspend fun saveCategory(category: CategoryEntity): Long =
        if (category.id == 0L) {
            val nextOrder = categoryDao.maxSortOrder() + 1
            categoryDao.insert(category.copy(sortOrder = nextOrder))
        } else {
            categoryDao.update(category)
            category.id
        }

    suspend fun deleteCategory(id: Long) {
        categoryDao.getById(id)?.let { categoryDao.delete(it) }
    }

    /** Сохраняет новый порядок категорий (drag & drop). */
    suspend fun applyCategoryOrder(orderedIds: List<Long>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id ->
                categoryDao.getById(id)?.let { category ->
                    if (category.sortOrder != index) {
                        categoryDao.update(category.copy(sortOrder = index))
                    }
                }
            }
        }
    }

    /** Сброс категорий к шаблону по умолчанию (удаляет все счета и их операции). */
    suspend fun resetCategoriesToTemplate() {
        db.withTransaction {
            categoryDao.deleteAll()
            categoryDao.insertAll(DefaultCategories.create())
        }
    }

    // ---------------- Настройки ----------------

    suspend fun updateSettings(block: (SettingsEntity) -> SettingsEntity) {
        val current = settingsDao.get() ?: SettingsEntity()
        settingsDao.upsert(block(current))
    }

    suspend fun ensureSettings() {
        if (settingsDao.get() == null) {
            settingsDao.upsert(SettingsEntity())
        }
    }

    // ---------------- Экспорт / резервные копии ----------------

    /** Снимок всех данных для экспорта в CSV/PDF. */
    suspend fun snapshot(): AppData = db.withTransaction {
        AppData(
            categories = categoryDao.getAll(),
            incomes = incomeDao.getAll(),
            expenses = expenseDao.getAll(),
            distributions = incomeDao.getAllDistributions(),
            transfers = transferDao.getAll(),
            adjustments = adjustmentDao.getAll(),
            settings = settingsDao.get() ?: SettingsEntity(),
        )
    }

    /**
     * Удаление всех операций и категорий.
     * Настройки приложения (тема, PIN, уведомления и т.д.) сохраняются,
     * онбординг остаётся пройденным.
     */
    suspend fun clearAllData() {
        // Снимок настроек делается ДО очистки: после clearAllTables читать уже нечего.
        val settings = settingsDao.get() ?: SettingsEntity()
        db.withTransaction {
            db.clearAllTables()
            settingsDao.upsert(settings.copy(onboardingCompleted = true))
        }
    }

    /**
     * Полный сброс приложения к состоянию первого запуска:
     * удаляются все операции, категории и настройки, онбординг считается
     * непройденным — после сброса снова показывается стартовый мастер.
     */
    suspend fun fullReset() {
        db.withTransaction {
            db.clearAllTables()
            settingsDao.upsert(SettingsEntity())
        }
    }
}
