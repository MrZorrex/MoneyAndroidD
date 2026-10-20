package com.paysplit.app.domain

import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.CategoryType

/** Предустановленный шаблон категорий (12 счетов). */
object DefaultCategories {

    private data class Template(
        val icon: String,
        val name: String,
        val plan: Double,
        val type: CategoryType,
    )

    private val templates = listOf(
        Template("🏠", "Жильё", 25000.0, CategoryType.INSTANT_PAY),
        Template("🛒", "Продукты", 25000.0, CategoryType.INSTANT_PAY),
        Template("📱", "Связь и интернет", 1840.0, CategoryType.SAVINGS),
        Template("🅿️", "Гараж", 2083.0, CategoryType.SAVINGS),
        Template("⛽", "Авто: бензин", 7500.0, CategoryType.INSTANT_PAY),
        Template("🔧", "Авто: ТО и ремонт", 3000.0, CategoryType.SAVINGS),
        Template("📋", "Авто: страховки", 6000.0, CategoryType.SAVINGS),
        Template("💰", "Подушка безопасности", 4000.0, CategoryType.SAVINGS),
        Template("📈", "Инвестиции", 4000.0, CategoryType.SAVINGS),
        Template("🏥", "Здоровье", 5000.0, CategoryType.SAVINGS),
        Template("🎉", "Безболезненные траты", 5000.0, CategoryType.INSTANT_PAY),
        Template("🔄", "Резерв", 1577.0, CategoryType.SAVINGS),
        Template("💵", "Свободные средства", 0.0, CategoryType.SAVINGS),
    )

    /** Готовые категории для вставки в базу. */
    fun create(): List<CategoryEntity> = templates.mapIndexed { index, t ->
        CategoryEntity(
            name = t.name,
            icon = t.icon,
            monthlyPlan = t.plan,
            type = t.type,
            targetAmount = null,
            isActive = true,
            sortOrder = index,
        )
    }

    /** Сумма всех планов шаблона. */
    val totalPlan: Double get() = templates.sumOf { it.plan }

    /** Набор эмодзи для выбора иконки категории. */
    val iconPalette: List<String> = listOf(
        "🏠", "🛒", "📱", "🅿️", "⛽", "🔧", "📋", "💰", "📈", "🏥",
        "🎉", "🔄", "🍔", "☕", "🚌", "🚕", "✈️", "🏖️", "🎁", "🐶",
        "🐱", "👶", "🎓", "📚", "💊", "🏋️", "🎮", "🎬", "🎵", "👕",
        "💡", "💧", "🔥", "🧹", "🛠️", "📦", "🏦", "💳", "🧾", "❓",
    )
}
