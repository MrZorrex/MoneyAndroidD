package com.paysplit.app.data.model

/** Тип категории-счёта. */
enum class CategoryType {
    /** Оплата сразу: план обнуляется каждый месяц. */
    INSTANT_PAY,

    /** Накопительный: баланс копится из месяца в месяц. */
    SAVINGS,
}

/** Тема оформления. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Источник поступления (чем была получена зарплата). */
enum class IncomeSource(val label: String) {
    ADVANCE("Аванс"),
    MAIN("Основная"),
    BONUS("Премия"),
    OTHER("Другое"),
}

/** Тип записи в истории операций. */
enum class TxType {
    /** Распределение зарплаты на счёт (приход). */
    INCOME,

    /** Расход со счёта. */
    EXPENSE,

    /** Перевод: уход со счёта-источника. */
    TRANSFER_OUT,

    /** Перевод: приход на счёт-получатель. */
    TRANSFER_IN,
}

/** Период для фильтра истории. */
enum class HistoryPeriod(val label: String) {
    THIS_MONTH("Этот месяц"),
    LAST_MONTH("Прошлый месяц"),
    THREE_MONTHS("3 месяца"),
    HALF_YEAR("Полгода"),
    YEAR("Год"),
    ALL("Всё время"),
    CUSTOM("Свой период"),
}

/** Фильтр по типу операций в истории. */
enum class TxFilter(val label: String) {
    ALL("Все"),
    INCOME("Поступления"),
    EXPENSE("Расходы"),
    TRANSFERS("Переводы"),
}

/** Период для экрана аналитики. */
enum class AnalyticsPeriod(val label: String, val months: Int) {
    MONTH("Месяц", 1),
    QUARTER("Квартал", 3),
    HALF_YEAR("Полгода", 6),
    YEAR("Год", 12),
}

/** Статус наполненности категории в текущем месяце. */
enum class CategoryStatus {
    /** Распределено полностью (по плану). */
    FULL,

    /** Распределено частично. */
    PARTIAL,

    /** Распределение ещё не начато. */
    NONE,

    /** Перерасход: расходы превысили поступления. */
    OVERSPEND,
}
