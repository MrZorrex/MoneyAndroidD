package com.paysplit.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Утилиты для работы с датами (java.time, доступен с API 26). */
object DateUtils {

    private val RU: Locale = Locale.forLanguageTag("ru-RU")

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun millisOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun today(): Long = millisOf(LocalDate.now(zone))

    fun yearMonth(millis: Long): YearMonth = YearMonth.from(localDate(millis))

    /** Начало месяца, в котором находится момент времени. */
    fun monthStart(millis: Long): Long = millisOf(yearMonth(millis).atDay(1))

    /** Конец месяца (включительно, 23:59:59.999). */
    fun monthEnd(millis: Long): Long =
        millisOf(yearMonth(millis).atEndOfMonth()) + 24L * 60 * 60 * 1000 - 1

    /** Начало месяца, отстоящего на [monthsBack] месяцев назад. */
    fun monthStartMonthsBack(monthsBack: Int, from: Long = today()): Long =
        millisOf(yearMonth(from).minusMonths(monthsBack.toLong()).atDay(1))

    /** Конец месяца, отстоящего на [monthsBack] месяцев назад. */
    fun monthEndMonthsBack(monthsBack: Int, from: Long = today()): Long =
        monthEnd(monthStartMonthsBack(monthsBack, from))

    /** Начало дня, отстоящего на [daysBack] дней назад. */
    fun dayStartDaysBack(daysBack: Int, from: Long = today()): Long =
        millisOf(localDate(from).minusDays(daysBack.toLong()))

    private val fullDateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM yyyy", RU)

    private val shortDateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", RU)

    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", RU)

    private val dayMonthFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMMM", RU)

    fun formatFullDate(millis: Long): String = fullDateFormat.format(localDate(millis))

    fun formatShortDate(millis: Long): String = shortDateFormat.format(localDate(millis))

    fun formatDayMonth(millis: Long): String = dayMonthFormat.format(localDate(millis))

    /**
     * Время «HH:mm» для момента времени.
     * Важно: форматируется именно время суток, а не дата — предыдущая реализация
     * передавала LocalDate в форматтер времени и падала с DateTimeException,
     * из-за чего закрывался экран истории при наличии операций.
     */
    fun formatTime(millis: Long): String =
        timeFormat.format(Instant.ofEpochMilli(millis).atZone(zone).toLocalTime())

    /**
     * true, если у момента времени полночь (00:00) — т.е. хранится только дата,
     * выбранная в календаре, без конкретного времени.
     */
    fun isMidnight(millis: Long): Boolean {
        val time = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return time.hour == 0 && time.minute == 0
    }

    /** Начало суток, в которых находится момент времени. */
    fun dayStart(millis: Long): Long = millisOf(localDate(millis))

    /** «Октябрь 2026». */
    fun formatMonth(yearMonth: YearMonth): String {
        val month = yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, RU)
        return month.replaceFirstChar { it.uppercase(RU) } + " " + yearMonth.year
    }

    /** «окт 26» — подписи на осях графиков. */
    fun formatMonthShort(yearMonth: YearMonth): String {
        val month = yearMonth.month.getDisplayName(TextStyle.SHORT, RU)
        return month + " " + (yearMonth.year % 100).toString().padStart(2, '0')
    }

    /** «сегодня» / «вчера» / «14 октября». */
    fun formatRelativeDay(millis: Long, today: Long = DateUtils.today()): String {
        val date = localDate(millis)
        val todayDate = localDate(today)
        return when (date) {
            todayDate -> "сегодня"
            todayDate.minusDays(1) -> "вчера"
            else -> fullDateFormat.format(date)
        }
    }

    fun isSameDay(a: Long, b: Long): Boolean = localDate(a) == localDate(b)

    /** Сколько дней от [fromMillis] до [toMillis] (может быть отрицательным). */
    fun daysBetween(fromMillis: Long, toMillis: Long): Long =
        java.time.temporal.ChronoUnit.DAYS.between(localDate(fromMillis), localDate(toMillis))

    /** Material 3 DatePicker возвращает полночь в UTC — переводим в локальные сутки. */
    fun fromUtcDateMillis(utcMillis: Long): Long =
        millisOf(Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate())

    /** Обратное преобразование: локальные сутки → полночь в UTC. */
    fun toUtcDateMillis(localMillis: Long): Long =
        localDate(localMillis).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
}
