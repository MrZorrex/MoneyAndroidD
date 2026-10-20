package com.paysplit.app.domain

import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.TxFilter
import com.paysplit.app.data.model.TxType
import com.paysplit.app.data.model.UiTransaction

/** Преобразование сущностей базы в единый список операций для UI. */
object TransactionBuilder {

    /** Все операции за период с учётом фильтров. */
    fun build(
        data: AppData,
        from: Long,
        to: Long,
        filter: TxFilter = TxFilter.ALL,
        categoryId: Long? = null,
    ): List<UiTransaction> {
        val result = mutableListOf<UiTransaction>()

        if (filter == TxFilter.ALL || filter == TxFilter.INCOME) {
            data.distributions
                .filter { it.date in from..to && (categoryId == null || it.categoryId == categoryId) }
                .forEach { d ->
                    val category = data.category(d.categoryId)
                    result += UiTransaction(
                        id = d.id,
                        date = d.date,
                        type = TxType.INCOME,
                        amount = d.amount,
                        categoryId = d.categoryId,
                        categoryName = category?.name ?: "Без категории",
                        icon = category?.icon ?: "💰",
                        title = "Распределение зарплаты",
                        comment = null,
                        sourceId = d.incomeId,
                    )
                }
        }

        if (filter == TxFilter.ALL || filter == TxFilter.EXPENSE) {
            data.expenses
                .filter { it.date in from..to && (categoryId == null || it.categoryId == categoryId) }
                .forEach { e ->
                    val category = data.category(e.categoryId)
                    result += UiTransaction(
                        id = e.id,
                        date = e.date,
                        type = TxType.EXPENSE,
                        amount = e.amount,
                        categoryId = e.categoryId,
                        categoryName = category?.name ?: "Без категории",
                        icon = category?.icon ?: "🧾",
                        title = e.title,
                        comment = e.comment,
                        sourceId = e.id,
                    )
                }
        }

        if (filter == TxFilter.ALL || filter == TxFilter.TRANSFERS) {
            data.transfers
                .filter { it.date in from..to }
                .forEach { t ->
                    val fromCategory = data.category(t.fromCategoryId)
                    val toCategory = data.category(t.toCategoryId)
                    if (categoryId == null || categoryId == t.fromCategoryId) {
                        result += UiTransaction(
                            id = t.id,
                            date = t.date,
                            type = TxType.TRANSFER_OUT,
                            amount = t.amount,
                            categoryId = t.fromCategoryId,
                            categoryName = fromCategory?.name ?: "?",
                            icon = fromCategory?.icon ?: "↔️",
                            title = "Перевод → ${toCategory?.name ?: "?"}",
                            comment = t.comment,
                            sourceId = t.id,
                        )
                    }
                    if (categoryId == null || categoryId == t.toCategoryId) {
                        result += UiTransaction(
                            id = t.id,
                            date = t.date,
                            type = TxType.TRANSFER_IN,
                            amount = t.amount,
                            categoryId = t.toCategoryId,
                            categoryName = toCategory?.name ?: "?",
                            icon = toCategory?.icon ?: "↔️",
                            title = "Перевод ← ${fromCategory?.name ?: "?"}",
                            comment = t.comment,
                            sourceId = t.id,
                        )
                    }
                }
        }

        if (filter == TxFilter.ALL || filter == TxFilter.ADJUSTMENT) {
            data.adjustments
                .filter { it.date in from..to && (categoryId == null || it.categoryId == categoryId) }
                .forEach { adj ->
                    val category = data.category(adj.categoryId)
                    val isPositive = adj.amount >= 0
                    result += UiTransaction(
                        id = adj.id,
                        date = adj.date,
                        type = TxType.ADJUSTMENT,
                        amount = kotlin.math.abs(adj.amount),
                        categoryId = adj.categoryId,
                        categoryName = category?.name ?: "Без категории",
                        icon = category?.icon ?: "⚖️",
                        title = if (isPositive) "Коррекция +" else "Коррекция −",
                        comment = adj.comment,
                        sourceId = adj.id,
                    )
                }
        }

        return result.sortedWith(
            compareByDescending<UiTransaction> { it.date }.thenByDescending { it.id },
        )
    }

    /** Операции конкретного счёта за всё время. */
    fun forCategory(data: AppData, categoryId: Long): List<UiTransaction> =
        build(data, from = 0L, to = Long.MAX_VALUE, filter = TxFilter.ALL, categoryId = categoryId)
}
