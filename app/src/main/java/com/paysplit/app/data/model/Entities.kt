package com.paysplit.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Категория = накопительный счёт.
 * Именно на счета распределяется зарплата и с них списываются расходы.
 */
@Entity(
    tableName = "categories",
    indices = [Index("sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    /** План на месяц, ₽. */
    val monthlyPlan: Double,
    val type: CategoryType,
    /** Целевая сумма для накопительных счетов (необязательна). */
    val targetAmount: Double? = null,
    /** Ожидаемая дата достижения цели / годового платежа (необязательна). */
    val targetDate: Long? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Поступление денег (зарплата, премия, аванс). */
@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Общая сумма поступления, ₽. */
    val amount: Double,
    /** Дата поступления (мс). */
    val date: Long,
    /** Комментарий: «Аванс», «Основная», «Премия»… */
    val comment: String? = null,
    val source: IncomeSource = IncomeSource.OTHER,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Распределение части поступления на конкретный счёт.
 * Баланс счёта = сумма всех распределений − сумма всех расходов.
 */
@Entity(
    tableName = "distributions",
    foreignKeys = [
        ForeignKey(
            entity = IncomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["incomeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("incomeId"), Index("categoryId")],
)
data class DistributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val incomeId: Long,
    val categoryId: Long,
    /** Сколько распределено на этот счёт, ₽. */
    val amount: Double,
    /** Дата поступления (дублируется для удобных выборок по месяцам). */
    val date: Long,
)

/** Расход, привязанный к счёту-категории. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index("date")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val amount: Double,
    val date: Long,
    /** Название траты: «Пятёрочка», «Стоматолог». */
    val title: String,
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Перевод средств между счетами.
 * Логически создаёт две операции: расход с одного счёта и доход на другой.
 */
@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromCategoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["toCategoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fromCategoryId"), Index("toCategoryId")],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromCategoryId: Long,
    val toCategoryId: Long,
    val amount: Double,
    val date: Long,
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Корректировка баланса счёта.
 * Позволяет вручную изменить текущую сумму счёта с комментарием
 * (например: «нашёл в кармане», «ошибка учёта», «снял наличные без чека»).
 * Положительная сумма — пополнение, отрицательная — списание.
 */
@Entity(
    tableName = "balance_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId"), Index("date")],
)
data class BalanceAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    /** Сумма коррекции: + пополнение, − списание. */
    val amount: Double,
    val date: Long,
    /** Комментарий пользователя к корректировке. */
    val comment: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Настройки приложения (единственная строка с id = 1). */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    /** Примерный доход в месяц, ₽. */
    val expectedMonthlyIncome: Double = 0.0,
    /** Сколько раз в месяц приходит зарплата. */
    val paydayCount: Int = 2,
    /** Хеш PIN-кода (с солью). null — PIN не установлен. */
    val pinHash: String? = null,
    /** Вход по отпечатку пальца. */
    val biometricEnabled: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Material You (динамические цвета). */
    val dynamicColors: Boolean = true,
    val currency: String = "₽",
    /** Онбординг пройден. */
    val onboardingCompleted: Boolean = false,
    // --- Уведомления ---
    val reminderEnabled: Boolean = true,
    /** Час вечернего напоминания (0..23). */
    val reminderHour: Int = 21,
    val overspendAlertsEnabled: Boolean = true,
    val annualAlertsEnabled: Boolean = true,
    val goalAlertsEnabled: Boolean = true,
    /** Быстрые шаблоны трат. */
    @ColumnInfo(name = "quick_templates")
    val quickTemplates: List<String> = listOf("Продукты", "Бензин", "Обед", "Аптека"),
) {
    companion object {
        const val SINGLETON_ID = 1L
    }
}
