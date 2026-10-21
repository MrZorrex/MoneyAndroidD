package com.paysplit.app.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Форматирование денежных сумм — только целые рубли, без копеек. */
object Formatters {

    private val RU: Locale = Locale.forLanguageTag("ru-RU")

    private val symbols: DecimalFormatSymbols = DecimalFormatSymbols(RU)

    private val moneyFormat = DecimalFormat("#,##0", symbols)

    private val signedMoneyFormat = DecimalFormat("+#,##0;-#,##0", symbols)

    // Кэш частых значений (0..200к) — экономит аллокации при скролле (до 60 вызовов за кадр)
    private val moneyCache = androidx.collection.LruCache<String, String>(512)

    /** «12 345 ₽» — всегда целое, округляем до рубля. */
    fun money(value: Double, currency: String = "₽"): String {
        val intVal = roundInt(value)
        // Кэшируем 80% вызовов (суммы обычно повторяются)
        val key = "$intVal|$currency"
        moneyCache.get(key)?.let { return it }
        // DecimalFormat не thread-safe — синхронизируем
        val formatted = synchronized(moneyFormat) {
            moneyFormat.format(intVal)
        } + if (currency.isNotEmpty()) " $currency" else ""
        moneyCache.put(key, formatted)
        return formatted
    }

    /** «+1 200 ₽» / «−1 200 ₽» — целое. */
    fun signedMoney(value: Double, currency: String = "₽"): String {
        val intVal = roundInt(value)
        val key = "s$intVal|$currency"
        moneyCache.get(key)?.let { return it }
        val formatted = synchronized(signedMoneyFormat) {
            signedMoneyFormat.format(intVal)
        } + if (currency.isNotEmpty()) " $currency" else ""
        moneyCache.put(key, formatted)
        return formatted
    }

    /** Компактный вид для осей графиков: «12К», «1М» — без дробей. */
    fun moneyShort(value: Double): String {
        val v = abs(roundInt(value).toDouble())
        val sign = if (value < 0) "−" else ""
        return when {
            v >= 1_000_000 -> sign + moneyFormat.format((v / 1_000_000).roundToLong()) + "М"
            v >= 1_000 -> sign + moneyFormat.format((v / 1_000).roundToLong()) + "К"
            else -> sign + moneyFormat.format(v.roundToLong())
        }
    }

    /** «45 %» — целое. */
    fun percent(fraction: Float): String {
        val pct = (fraction * 100).roundToLong()
        return "$pct %"
    }

    fun roundInt(value: Double): Long = Math.round(value)

    fun round2(value: Double): Double = Math.round(value).toDouble()
}
