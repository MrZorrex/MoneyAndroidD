package com.paysplit.app.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Форматирование денежных сумм. */
object Formatters {

    private val RU: Locale = Locale.forLanguageTag("ru-RU")

    private val symbols: DecimalFormatSymbols = DecimalFormatSymbols(RU)

    private val moneyFormat = DecimalFormat("#,##0.##", symbols)

    private val signedMoneyFormat = DecimalFormat("+#,##0.##;-#,##0.##", symbols)

    /** «12 345 ₽» / «12 345,50 ₽». */
    fun money(value: Double, currency: String = "₽"): String =
        moneyFormat.format(round2(value)) + " " + currency

    /** «+1 200 ₽» / «−1 200 ₽» (для истории операций). */
    fun signedMoney(value: Double, currency: String = "₽"): String =
        signedMoneyFormat.format(round2(value)) + " " + currency

    /** Компактный вид для осей графиков: «12,3К», «850», «1,2М». */
    fun moneyShort(value: Double): String {
        val v = abs(value)
        val sign = if (value < 0) "−" else ""
        return when {
            v >= 1_000_000 -> sign + trim(moneyFormat.format(round2(v / 1_000_000))) + "М"
            v >= 1_000 -> sign + trim(moneyFormat.format(round2(v / 1_000))) + "К"
            else -> sign + moneyFormat.format(round2(v))
        }
    }

    /** «45,5 %». */
    fun percent(fraction: Float): String {
        val pct = (fraction * 1000).roundToLong() / 10.0
        return moneyFormat.format(pct) + " %"
    }

    /** Убирает хвостовые нули и запятую. */
    private fun trim(s: String): String = s.trimEnd('0').trimEnd(',').trimEnd(' ')

    fun round2(value: Double): Double = Math.round(value * 100) / 100.0
}
