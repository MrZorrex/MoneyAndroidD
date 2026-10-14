package com.paysplit.app.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.paysplit.app.data.model.AppData
import com.paysplit.app.domain.AnalyticsEngine
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters
import java.io.OutputStream
import java.time.YearMonth
import kotlin.math.min

/** Отчёт за месяц в формате PDF (без сторонних библиотек). */
object PdfReportGenerator {

    private const val PAGE_WIDTH = 595 // A4, 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    fun generate(data: AppData, month: YearMonth, out: OutputStream) {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        drawReport(page.canvas, data, month)
        document.finishPage(page)
        document.writeTo(out)
        document.close()
    }

    private fun drawReport(canvas: Canvas, data: AppData, month: YearMonth) {
        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(15, 61, 51)
        }
        val headerPaint = Paint().apply {
            isAntiAlias = true
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.rgb(15, 61, 51)
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.rgb(33, 33, 33)
        }
        val smallPaint = Paint().apply {
            isAntiAlias = true
            textSize = 9f
            color = Color.rgb(120, 120, 120)
        }
        val linePaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 0.8f
            color = Color.rgb(200, 200, 200)
        }

        var y = MARGIN + 20f

        canvas.drawText("PaySplit — отчёт", MARGIN, y, titlePaint)
        y += 22f
        canvas.drawText(DateUtils.formatMonth(month), MARGIN, y, headerPaint)
        y += 20f

        val monthStart = DateUtils.millisOf(month.atDay(1))
        val monthEnd = DateUtils.millisOf(month.atEndOfMonth()) + 86_400_000L - 1
        val income = BalanceCalculator.incomeThisMonth(data, monthStart, monthEnd)
        val spent = BalanceCalculator.spentThisMonth(data, monthStart, monthEnd)
        val stats = AnalyticsEngine.compute(data, monthStart, monthEnd)

        canvas.drawText(
            "Доходы: ${Formatters.money(income)}    Расходы: ${Formatters.money(spent)}" +
                "    Баланс: ${Formatters.money(income - spent)}",
            MARGIN, y, textPaint,
        )
        y += 26f

        // --- Таблица счетов ---
        canvas.drawText("Счета", MARGIN, y, headerPaint)
        y += 16f
        val balances = BalanceCalculator.compute(data, monthStart, monthEnd)
        val colX = floatArrayOf(MARGIN, MARGIN + 170f, MARGIN + 270f, MARGIN + 370f, MARGIN + 460f)
        val headers = listOf("Счёт", "План", "Распределено", "Потрачено", "Баланс")
        headers.forEachIndexed { i, h -> canvas.drawText(h, colX[i], y, smallPaint) }
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 16f

        balances.forEach { balance ->
            if (y > PAGE_HEIGHT - MARGIN - 60f) return@forEach
            val name = "${balance.category.icon} ${balance.category.name}".take(28)
            canvas.drawText(name, colX[0], y, textPaint)
            canvas.drawText(Formatters.money(balance.category.monthlyPlan, ""), colX[1], y, textPaint)
            canvas.drawText(Formatters.money(balance.allocatedThisMonth, ""), colX[2], y, textPaint)
            canvas.drawText(Formatters.money(balance.spentThisMonth, ""), colX[3], y, textPaint)
            canvas.drawText(Formatters.money(balance.balance, ""), colX[4], y, textPaint)
            y += 15f
        }

        y += 14f

        // --- Расходы по категориям ---
        canvas.drawText("Расходы по категориям", MARGIN, y, headerPaint)
        y += 16f
        stats.byCategory.take(10).forEach { item ->
            if (y > PAGE_HEIGHT - MARGIN - 30f) return@forEach
            canvas.drawText("${item.icon} ${item.name}".take(34), MARGIN, y, textPaint)
            canvas.drawText(
                "${Formatters.money(item.amount)} (${Formatters.percent(item.share)})",
                MARGIN + 260f, y, textPaint,
            )
            y += 15f
        }

        y += 14f

        // --- Топ-5 крупнейших трат ---
        canvas.drawText("Самые крупные траты", MARGIN, y, headerPaint)
        y += 16f
        stats.topLargest.forEach { expense ->
            if (y > PAGE_HEIGHT - MARGIN - 20f) return@forEach
            val category = data.category(expense.categoryId)
            canvas.drawText(
                "${category?.icon ?: "🧾"} ${expense.title}".take(34),
                MARGIN, y, textPaint,
            )
            canvas.drawText(
                Formatters.money(expense.amount),
                MARGIN + 300f, y, textPaint,
            )
            y += 15f
        }

        y += 20f
        canvas.drawText(
            "Средний расход за месяц: ${Formatters.money(stats.avgExpense)}",
            MARGIN, y, textPaint,
        )
        y += 14f
        canvas.drawText(
            "Средний доход за месяц: ${Formatters.money(stats.avgIncome)}",
            MARGIN, y, textPaint,
        )

        canvas.drawText(
            "Отчёт сформирован приложением PaySplit (оффлайн, без отправки данных)",
            MARGIN, PAGE_HEIGHT - MARGIN, smallPaint,
        )
    }

    /** Имя файла отчёта. */
    fun fileName(month: YearMonth): String =
        "paysplit_report_${month.year}-${month.monthValue.toString().padStart(2, '0')}.pdf"

    fun limit(value: Int, max: Int): Int = min(value, max)
}
