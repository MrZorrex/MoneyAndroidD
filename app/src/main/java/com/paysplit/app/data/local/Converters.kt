package com.paysplit.app.data.local

import androidx.room.TypeConverter
import com.paysplit.app.data.model.CategoryType
import com.paysplit.app.data.model.IncomeSource
import com.paysplit.app.data.model.ThemeMode

/** Конвертеры Room для enum-ов и списков строк. */
class Converters {

    @TypeConverter
    fun fromCategoryType(value: CategoryType): String = value.name

    @TypeConverter
    fun toCategoryType(value: String): CategoryType =
        runCatching { CategoryType.valueOf(value) }.getOrDefault(CategoryType.INSTANT_PAY)

    @TypeConverter
    fun fromThemeMode(value: ThemeMode): String = value.name

    @TypeConverter
    fun toThemeMode(value: String): ThemeMode =
        runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.SYSTEM)

    @TypeConverter
    fun fromIncomeSource(value: IncomeSource): String = value.name

    @TypeConverter
    fun toIncomeSource(value: String): IncomeSource =
        runCatching { IncomeSource.valueOf(value) }.getOrDefault(IncomeSource.OTHER)

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(SEPARATOR)

    private companion object {
        /** Символ-разделитель, который не встречается в названиях трат. */
        const val SEPARATOR = "\u001F"
    }
}
