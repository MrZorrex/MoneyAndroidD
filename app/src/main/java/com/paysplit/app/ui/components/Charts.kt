package com.paysplit.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.paysplit.app.util.Formatters

// ---------------------------------------------------------------------------
// Обёртки над библиотекой Vico (столбчатые и линейные графики)
// ---------------------------------------------------------------------------

private val LabelsKey = ExtraStore.Key<List<String>>()

private val ShortValueFormatter = CartesianValueFormatter { _, value, _ ->
    Formatters.moneyShort(value.toDouble())
}

/** Столбчатый график: одна серия значений с подписями месяцев. */
@Composable
fun MonthlyColumnChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 200.dp,
) {
    if (values.isEmpty()) {
        EmptyState("📊", "Нет данных", "Добавьте операции, чтобы построить график")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, labels) {
        modelProducer.runTransaction {
            columnSeries { series(values) }
            extras { it[LabelsKey] = labels }
        }
    }
    val labelFormatter = remember(labels) {
        CartesianValueFormatter { context, x, _ ->
            context.model.extraStore[LabelsKey].getOrNull(x.toInt()) ?: ""
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = fill(barColor), thickness = 14.dp),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = ShortValueFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = labelFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

/** Столбчатый график «поступления vs расходы» по месяцам. */
@Composable
fun MonthlyCompareColumnChart(
    income: List<Double>,
    expense: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    incomeColor: Color = MaterialTheme.colorScheme.primary,
    expenseColor: Color = MaterialTheme.colorScheme.error,
    height: Dp = 220.dp,
) {
    if (income.isEmpty()) {
        EmptyState("📊", "Нет данных", "Добавьте операции, чтобы построить график")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(income, expense, labels) {
        modelProducer.runTransaction {
            columnSeries {
                series(income)
                series(expense)
            }
            extras { it[LabelsKey] = labels }
        }
    }
    val labelFormatter = remember(labels) {
        CartesianValueFormatter { context, x, _ ->
            context.model.extraStore[LabelsKey].getOrNull(x.toInt()) ?: ""
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = fill(incomeColor), thickness = 10.dp),
                    rememberLineComponent(fill = fill(expenseColor), thickness = 10.dp),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = ShortValueFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = labelFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

/** Линейный график одной серии (с заливкой области). */
@Composable
fun AreaLineChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 200.dp,
) {
    if (values.isEmpty()) {
        EmptyState("📈", "Нет данных", "Недостаточно данных для графика")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, labels) {
        modelProducer.runTransaction {
            lineSeries { series(values) }
            extras { it[LabelsKey] = labels }
        }
    }
    val labelFormatter = remember(labels) {
        CartesianValueFormatter { context, x, _ ->
            context.model.extraStore[LabelsKey].getOrNull(x.toInt()) ?: ""
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(lineColor)),
                        areaFill = LineCartesianLayer.AreaFill.single(
                            fill(lineColor.copy(alpha = 0.25f)),
                        ),
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = ShortValueFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = labelFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

/** Линейный график двух серий (доходы vs расходы). */
@Composable
fun DualLineChart(
    first: List<Double>,
    second: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    firstColor: Color = MaterialTheme.colorScheme.primary,
    secondColor: Color = MaterialTheme.colorScheme.error,
    height: Dp = 220.dp,
) {
    if (first.isEmpty()) {
        EmptyState("📈", "Нет данных", "Недостаточно данных для графика")
        return
    }
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(first, second, labels) {
        modelProducer.runTransaction {
            lineSeries {
                series(first)
                series(second)
            }
            extras { it[LabelsKey] = labels }
        }
    }
    val labelFormatter = remember(labels) {
        CartesianValueFormatter { context, x, _ ->
            context.model.extraStore[LabelsKey].getOrNull(x.toInt()) ?: ""
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(firstColor)),
                    ),
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(fill(secondColor)),
                    ),
                ),
            ),
            startAxis = VerticalAxis.rememberStart(valueFormatter = ShortValueFormatter),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = labelFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        scrollState = rememberVicoScrollState(scrollEnabled = false),
    )
}

// ---------------------------------------------------------------------------
// Круговая диаграмма расходов (Compose Canvas)
// ---------------------------------------------------------------------------

data class DonutSlice(
    val value: Double,
    val color: Color,
    val label: String,
)

/**
 * Кольцевая диаграмма расходов.
 *
 * Гарантии отрисовки:
 * — нулевые значения не рисуются и не влияют на доли;
 * — один сектор занимает дугу по своей доле (при 100 % — полное кольцо);
 * — между секторами оставляется видимый зазор, цвета не сливаются;
 * — под секторами всегда есть приглушённое кольцо-подложка.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    total: Double = slices.sumOf { it.value },
    strokeWidth: Dp = 26.dp,
    chartSize: Dp = 180.dp,
    gapDegrees: Float = 3f,
    trackColor: Color = Color.Gray.copy(alpha = 0.18f),
    centerContent: @Composable (() -> Unit)? = null,
) {
    val visible = remember(slices) { slices.filter { it.value > 0.0 } }
    val sum = visible.sumOf { it.value }
    val effectiveTotal = maxOf(total, sum)

    val animation = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(visible) {
        animation.snapTo(0f)
        animation.animateTo(1f, tween(durationMillis = 700))
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(chartSize)) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)
            val style = Stroke(width = stroke, cap = StrokeCap.Butt)

            // Подложка: полное кольцо (видно и при пустых данных).
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style,
            )
            if (sum <= 0.0 || visible.isEmpty() || effectiveTotal <= 0.0) return@Canvas
            val progress = animation.value
            if (progress <= 0f) return@Canvas

            if (visible.size == 1) {
                val sweep = (sum / effectiveTotal * 360.0).toFloat() * progress
                drawArc(
                    color = visible.first().color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = style,
                )
                return@Canvas
            }

            val available = 360f - gapDegrees * visible.size
            var startAngle = -90f + gapDegrees / 2f
            visible.forEach { slice ->
                val fullSweep = (slice.value / effectiveTotal * available).toFloat()
                val sweep = fullSweep * progress
                if (sweep > 0.3f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = style,
                    )
                }
                startAngle += fullSweep + gapDegrees
            }
        }
        if (centerContent != null) centerContent()
    }
}

/**
 * Легенда под круговой диаграммой.
 * Проценты считаются методом наибольшего остатка с точностью до десятых,
 * поэтому в сумме всегда дают ровно 100 %.
 */
@Composable
fun DonutLegend(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    currency: String = "₽",
) {
    val visible = remember(slices) { slices.filter { it.value > 0.0 } }
    val percents = remember(visible) { sharePercentsTenths(visible.map { it.value }) }
    Column(modifier = modifier.fillMaxWidth()) {
        visible.forEachIndexed { index, slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(slice.color),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatters.money(slice.value, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Formatters.percent(percents.getOrElse(index) { 0 } / 1000f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Доли значений в десятых долях процента (978 = 97,8 %).
 * Метод наибольшего остатка: в сумме всегда ровно 1000 (= 100 %).
 */
private fun sharePercentsTenths(values: List<Double>): List<Int> {
    if (values.isEmpty()) return emptyList()
    val sum = values.sum()
    if (sum <= 0.0) return List(values.size) { 0 }
    val exact = values.map { it / sum * 1000.0 }
    val floored = exact.map { it.toInt() }.toMutableList()
    var rest = 1000 - floored.sum()
    val order = exact.indices.sortedByDescending { exact[it] - floored[it] }
    var i = 0
    while (rest > 0 && order.isNotEmpty()) {
        floored[order[i % order.size]] += 1
        rest--
        i++
    }
    return floored
}

// ---------------------------------------------------------------------------
// Столбчатая диаграмма разницы (доходы − расходы) с цветом по знаку
// ---------------------------------------------------------------------------

@Composable
fun SignedBarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    positiveColor: Color = MaterialTheme.colorScheme.primary,
    negativeColor: Color = MaterialTheme.colorScheme.error,
    height: Dp = 200.dp,
) {
    if (values.isEmpty()) {
        EmptyState("📊", "Нет данных", "Недостаточно данных для графика")
        return
    }
    val animation by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "bars",
    )
    val maxAbs = values.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val zeroY = size.height / 2f
            val barWidth = size.width / (values.size * 1.6f)
            val spacing = size.width / values.size

            // Нулевая линия
            drawLine(
                color = Color.Gray.copy(alpha = 0.4f),
                start = Offset(0f, zeroY),
                end = Offset(size.width, zeroY),
                strokeWidth = 1.5f,
            )

            values.forEachIndexed { index, value ->
                val centerX = spacing * index + spacing / 2f
                val barHeight =
                    (kotlin.math.abs(value) / maxAbs * (size.height / 2f - 12f) * animation)
                        .toFloat()
                val top = if (value >= 0) zeroY - barHeight else zeroY
                drawRoundRect(
                    color = if (value >= 0) positiveColor else negativeColor,
                    topLeft = Offset(centerX - barWidth / 2f, top),
                    size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}
