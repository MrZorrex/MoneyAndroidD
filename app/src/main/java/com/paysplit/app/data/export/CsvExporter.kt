package com.paysplit.app.data.export

import com.paysplit.app.data.model.AppData
import com.paysplit.app.data.model.TxType
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/** Экспорт всех операций в CSV (разделитель «;» — открывается в Excel с русской локалью). */
object CsvExporter {

    private const val SEPARATOR = ";"

    fun build(data: AppData): String {
        val sb = StringBuilder()
        sb.append("\uFEFF") // BOM, чтобы Excel определил UTF-8
        sb.append("Дата").append(SEPARATOR)
            .append("Тип").append(SEPARATOR)
            .append("Категория").append(SEPARATOR)
            .append("Название").append(SEPARATOR)
            .append("Сумма").append(SEPARATOR)
            .append("Комментарий").append('\n')

        data.distributions.sortedBy { it.date }.forEach { d ->
            val category = data.category(d.categoryId)
            sb.append(DateUtils.formatFullDate(d.date)).append(SEPARATOR)
                .append("Поступление").append(SEPARATOR)
                .append(escape(category?.name ?: "—")).append(SEPARATOR)
                .append("Распределение зарплаты").append(SEPARATOR)
                .append(Formatters.money(d.amount, "")).append(SEPARATOR)
                .append("").append('\n')
        }

        data.expenses.sortedBy { it.date }.forEach { e ->
            val category = data.category(e.categoryId)
            sb.append(DateUtils.formatFullDate(e.date)).append(SEPARATOR)
                .append("Расход").append(SEPARATOR)
                .append(escape(category?.name ?: "—")).append(SEPARATOR)
                .append(escape(e.title)).append(SEPARATOR)
                .append("-" + Formatters.money(e.amount, "")).append(SEPARATOR)
                .append(escape(e.comment ?: "")).append('\n')
        }

        data.transfers.sortedBy { it.date }.forEach { t ->
            val from = data.category(t.fromCategoryId)
            val to = data.category(t.toCategoryId)
            sb.append(DateUtils.formatFullDate(t.date)).append(SEPARATOR)
                .append("Перевод").append(SEPARATOR)
                .append(escape("${from?.name ?: "?"} → ${to?.name ?: "?"}")).append(SEPARATOR)
                .append("Перевод между счетами").append(SEPARATOR)
                .append(Formatters.money(t.amount, "")).append(SEPARATOR)
                .append(escape(t.comment ?: "")).append('\n')
        }

        // Итоги
        val income = data.distributions.sumOf { it.amount }
        val expense = data.expenses.sumOf { it.amount }
        sb.append('\n')
            .append("ИТОГО").append(SEPARATOR)
            .append("Поступления").append(SEPARATOR).append(SEPARATOR).append(SEPARATOR)
            .append(Formatters.money(income, "")).append(SEPARATOR).append('\n')
        sb.append("ИТОГО").append(SEPARATOR)
            .append("Расходы").append(SEPARATOR).append(SEPARATOR).append(SEPARATOR)
            .append("-" + Formatters.money(expense, "")).append(SEPARATOR).append('\n')
        sb.append("ИТОГО").append(SEPARATOR)
            .append("Баланс").append(SEPARATOR).append(SEPARATOR).append(SEPARATOR)
            .append(Formatters.money(income - expense, "")).append(SEPARATOR).append('\n')

        return sb.toString()
    }

    private fun escape(value: String): String =
        if (value.contains(SEPARATOR) || value.contains('\n') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Подпись типа операции для отчёта. */
    fun labelOf(type: TxType): String = when (type) {
        TxType.INCOME -> "Поступление"
        TxType.EXPENSE -> "Расход"
        TxType.TRANSFER_OUT -> "Перевод (списание)"
        TxType.TRANSFER_IN -> "Перевод (зачисление)"
    }
}
