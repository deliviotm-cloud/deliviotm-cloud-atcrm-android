package tm.deliviotm.atcrm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

private val periodBlue = Color(0xFF3B82F6)
private val periodGreen = Color(0xFF10B981)
private val ruWeekdays = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val ruMonthsLong = arrayOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
)
private val ruMonthsShort = arrayOf(
    "янв", "фев", "мар", "апр", "мая", "июн",
    "июл", "авг", "сен", "окт", "ноя", "дек",
)
private val kitchenHours = (9..23).toList() + (0..3).toList()
private val ruDay = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val ruDayShort = DateTimeFormatter.ofPattern("dd.MM")

private data class CompareOption(val value: String, val label: String)

private data class ActivityRow(
    val rowLabel: String,
    val ordersA: Int,
    val ordersB: Int,
) {
    val delta: Int get() = ordersB - ordersA
}

private data class ActivityTotals(
    val totalA: Int = 0,
    val totalB: Int = 0,
    val growthPct: Double = 0.0,
    val growthHoursPct: Double = 0.0,
    val peakRowA: String = "—",
    val peakRowB: String = "—",
    val peakOrdersA: Int = 0,
    val peakOrdersB: Int = 0,
)

private data class ActivityMoney(
    val turnoverA: Double = 0.0,
    val turnoverB: Double = 0.0,
    val profitA: Double = 0.0,
    val profitB: Double = 0.0,
)

private data class OrderPoint(
    val day: String,
    val month: String,
    val hour: Int,
    val amount: Double,
    val profit: Double,
)

@Composable
internal fun ReportsCompareActivityCard(
    summary: JSONObject?,
    dateFrom: String,
    dateTo: String,
) {
    val points = remember(summary) { orderPoints(summary) }
    val dayOpts = remember(summary, dateFrom, dateTo, points) { dayOptions(summary, dateFrom, dateTo, points) }
    val monthOpts = remember(summary, dateFrom, dateTo, points) { monthOptions(summary, dateFrom, dateTo, points) }
    val weekOpts = remember(summary, dateFrom, dateTo, points) { weekOptions(summary, dateFrom, dateTo, points) }

    var mode by remember { mutableStateOf("day") }
    var dayA by remember { mutableStateOf("") }
    var dayB by remember { mutableStateOf("") }
    var monthA by remember { mutableStateOf("") }
    var monthB by remember { mutableStateOf("") }
    var weekA by remember { mutableStateOf("") }
    var weekB by remember { mutableStateOf("") }
    var fortnightA by remember { mutableStateOf("") }
    var fortnightB by remember { mutableStateOf("") }

    LaunchedEffect(dayOpts) {
        val (a, b) = lastTwo(dayOpts)
        if (a.isNotBlank() && dayOpts.none { it.value == dayA }) dayA = a
        if (b.isNotBlank() && dayOpts.none { it.value == dayB }) dayB = b
        if (a.isNotBlank() && dayOpts.none { it.value == fortnightA }) fortnightA = a
        if (b.isNotBlank() && dayOpts.none { it.value == fortnightB }) fortnightB = b
    }
    LaunchedEffect(monthOpts) {
        val (a, b) = lastTwo(monthOpts)
        if (a.isNotBlank() && monthOpts.none { it.value == monthA }) monthA = a
        if (b.isNotBlank() && monthOpts.none { it.value == monthB }) monthB = b
    }
    LaunchedEffect(weekOpts) {
        val (a, b) = lastTwo(weekOpts)
        if (a.isNotBlank() && weekOpts.none { it.value == weekA }) weekA = a
        if (b.isNotBlank() && weekOpts.none { it.value == weekB }) weekB = b
    }

    val (period1, period2) = when (mode) {
        "monthHour", "monthByDayOfMonth" ->
            CompareField("Месяц 1", monthA, monthOpts) { monthA = it } to
                CompareField("Месяц 2", monthB, monthOpts) { monthB = it }
        "week" ->
            CompareField("Неделя 1", weekA, weekOpts) { weekA = it } to
                CompareField("Неделя 2", weekB, weekOpts) { weekB = it }
        "fortnight" ->
            CompareField("Начало 1 (14 дн.)", fortnightA, dayOpts) { fortnightA = it } to
                CompareField("Начало 2 (14 дн.)", fortnightB, dayOpts) { fortnightB = it }
        else ->
            CompareField("День 1", dayA, dayOpts) { dayA = it } to
                CompareField("День 2", dayB, dayOpts) { dayB = it }
    }
    val labelA = period1.options.find { it.value == period1.value }?.label ?: "Период 1"
    val labelB = period2.options.find { it.value == period2.value }?.label ?: "Период 2"
    val rows = remember(points, mode, dayA, dayB, monthA, monthB, weekA, weekB, fortnightA, fortnightB) {
        kitchenHoursIfNeeded(
            buildActivityRows(points, mode, dayA, dayB, monthA, monthB, weekA, weekB, fortnightA, fortnightB),
            mode,
        )
    }
    val totals = remember(rows) { activityTotals(rows) }
    val money = remember(points, mode, dayA, dayB, monthA, monthB, weekA, weekB, fortnightA, fortnightB) {
        activityMoney(points, mode, dayA, dayB, monthA, monthB, weekA, weekB, fortnightA, fortnightB)
    }

    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Когда больше всего заказов", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (points.isEmpty()) {
            Text(
                "Составьте отчёт за период — затем можно сравнивать дни, недели и часы активности.",
                color = AtColors.muted,
                fontSize = 13.sp,
            )
            return@Column
        }
        SiteFilterSelect(
            value = mode,
            items = listOf(
                "day" to "По дням (часы)",
                "week" to "Неделя (7 дн.)",
                "fortnight" to "14 дней",
                "monthHour" to "Месяц (по часам)",
                "monthByDayOfMonth" to "Месяц (по числам)",
            ),
            onChange = { mode = it },
            label = "Разрез",
        )
        SiteFilterSelect(value = period1.value, items = period1.options.map { it.value to it.label }, onChange = period1.onChange, label = period1.label)
        SiteFilterSelect(value = period2.value, items = period2.options.map { it.value to it.label }, onChange = period2.onChange, label = period2.label)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityKpiCard(labelA, totals.totalA.toString(), "Заказов за период 1", Modifier.weight(1f), periodBlue)
            ActivityKpiCard(labelB, totals.totalB.toString(), "Заказов за период 2", Modifier.weight(1f), periodGreen)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AtColors.glass).padding(12.dp)) {
            Text("Пик активности", color = AtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("$labelA: ${totals.peakRowA}", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("${totals.peakOrdersA} заказ(ов) в пике", color = AtColors.muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("$labelB: ${totals.peakRowB}", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("${totals.peakOrdersB} заказ(ов) в пике", color = AtColors.muted, fontSize = 12.sp)
        }

        DualActivityBars(
            title = chartTitle(mode),
            labelA = labelA,
            labelB = labelB,
            rows = rows,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GrowthDonut(totals.growthHoursPct)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val growth = totals.growthPct
                Text(
                    "${if (growth >= 0) "+" else ""}${"%.1f".format(growth)}%",
                    color = if (growth >= 0) AtColors.success else AtColors.danger,
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                )
                Text(
                    "Круг: доля строк, где период 2 > периода 1. Процент — изменение числа заказов.",
                    color = AtColors.muted,
                    fontSize = 11.sp,
                )
            }
        }

        MoneyCompareCard(labelA, labelB, money)

        Text(
            "${chartFoot(mode)} Итого: $labelA — ${totals.totalA}, $labelB — ${totals.totalB}.",
            color = AtColors.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
internal fun ReportsCompareFollowup(
    rangeLabel: String,
    presetLabel: String,
    series: List<JSONObject>,
    compareMonths: List<JSONObject>,
    establishmentId: String,
    topEstAnalytics: List<JSONObject>,
    payBreak: List<JSONObject>,
    monthlyUsers: List<JSONObject>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Динамика · период $rangeLabel · пресет: $presetLabel", color = AtColors.muted, fontSize = 13.sp)

        CompareValueBars(
            title = "График среднего чека",
            hint = "Синие полосы — средний чек по дням выбранного периода",
            rows = series.map { o ->
                CompareValueRow(
                    label = KassaApi.pick(o, "label", "period").ifBlank { "—" },
                    value = KassaApi.jsonNum(o, "avgCheck", "averageCheck", "aov"),
                    caption = KassaApi.tmt(KassaApi.jsonNum(o, "avgCheck", "averageCheck", "aov")),
                )
            },
        )

        val monthHint = if (establishmentId.isNotBlank()) "выбранное заведение" else "все заведения"
        CompareValueBars(
            title = "Сводные графики по месяцам (с июня)",
            hint = "Данные: $monthHint",
            rows = compareMonths.map { o ->
                val turnover = KassaApi.jsonNum(o, "turnover", "revenue")
                val profit = KassaApi.jsonNum(o, "profit", "netProfit")
                CompareValueRow(
                    label = KassaApi.pick(o, "label", "period").ifBlank { "—" },
                    value = turnover,
                    caption = "${KassaApi.tmt(turnover)} · ${KassaApi.tmt(profit)} приб.",
                )
            },
            emptyText = "Нет данных за выбранный период.",
        )

        ReportZebraTable(
            title = "Динамика по периодам",
            headers = listOf("Период", "Заказы", "Оборот (без доставки)"),
            weights = listOf(1.1f, 0.7f, 1.4f),
            rows = series.map { o ->
                listOf(
                    KassaApi.pick(o, "label").ifBlank { "—" },
                    KassaApi.prettyNumber(KassaApi.jsonNum(o, "ordersCount", "orders").toString()),
                    KassaApi.tmt(KassaApi.jsonNum(o, "turnover", "revenue")),
                )
            },
        )

        if (compareMonths.isNotEmpty()) {
            ReportZebraTable(
                title = if (establishmentId.isNotBlank()) "Сравнение по месяцам — заведение" else "Сравнение по месяцам — все заведения",
                headers = listOf("Период", "Заказы", "Оборот (без доставки)"),
                weights = listOf(1.1f, 0.7f, 1.4f),
                rows = compareMonths.map { o ->
                    listOf(
                        KassaApi.pick(o, "label").ifBlank { "—" },
                        KassaApi.prettyNumber(KassaApi.jsonNum(o, "ordersCount", "orders").toString()),
                        KassaApi.tmt(KassaApi.jsonNum(o, "turnover")),
                    )
                },
            )
        }

        TopEstablishmentsCard(topEstAnalytics)
        PaymentBreakdownCard(payBreak)
        UsersByMonthCard(monthlyUsers)
    }
}

private data class CompareField(
    val label: String,
    val value: String,
    val options: List<CompareOption>,
    val onChange: (String) -> Unit,
)

private data class CompareValueRow(val label: String, val value: Double, val caption: String)

@Composable
private fun ActivityKpiCard(title: String, value: String, hint: String, modifier: Modifier = Modifier, accent: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AtColors.glass)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Text(title, color = AtColors.muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = AtColors.text, fontWeight = FontWeight.Black, fontSize = 28.sp)
        Text(hint, color = AtColors.muted, fontSize = 11.sp)
    }
}

@Composable
private fun DualActivityBars(title: String, labelA: String, labelB: String, rows: List<ActivityRow>) {
    var showEmpty by remember { mutableStateOf(false) }
    val visible = if (showEmpty) rows else rows.filter { it.ordersA + it.ordersB > 0 }
    val hidden = rows.size - visible.size
    val maxVal = visible.maxOfOrNull { max(it.ordersA, it.ordersB) }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AtColors.glass).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendDot(periodBlue, labelA, Modifier.weight(1f))
            LegendDot(periodGreen, labelB, Modifier.weight(1f))
        }
        if (visible.isEmpty()) {
            Text("В выбранных периодах нет заказов в этих часах.", color = AtColors.muted, fontSize = 13.sp)
        }
        visible.forEach { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    row.rowLabel,
                    color = AtColors.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.width(86.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    DualBar(row.ordersA, maxVal, periodBlue)
                    DualBar(row.ordersB, maxVal, periodGreen)
                }
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.End) {
                    Text(row.ordersA.toString(), color = periodBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(row.ordersB.toString(), color = periodGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (hidden > 0 || showEmpty) {
            Text(
                if (showEmpty) "Только часы с заказами" else "Пустые часы · $hidden",
                color = AtColors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { showEmpty = !showEmpty },
            )
        }
    }
}

@Composable
private fun DualBar(value: Int, maxVal: Int, color: Color) {
    val pct = if (value > 0) (value.toFloat() / maxVal).coerceIn(0.04f, 1f) else 0f
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(AtColors.panel.copy(alpha = 0.7f))) {
        if (pct > 0f) {
            Box(Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(99.dp)).background(color))
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = AtColors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GrowthDonut(pct: Double) {
    val track = AtColors.stroke.copy(alpha = 0.35f)
    val fill = AtColors.success
    val text = AtColors.text
    Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeW = 11.dp.toPx()
            val inset = strokeW / 2f
            val arcSize = Size(size.width - strokeW, size.height - strokeW)
            val topLeft = Offset(inset, inset)
            val style = Stroke(width = strokeW, cap = StrokeCap.Round)
            drawArc(track, -90f, 360f, false, topLeft = topLeft, size = arcSize, style = style)
            val sweep = ((pct.coerceIn(0.0, 100.0) / 100.0) * 360.0).toFloat()
            if (sweep > 0f) {
                drawArc(fill, -90f, sweep, false, topLeft = topLeft, size = arcSize, style = style)
            }
        }
        Text("${pct.roundToInt()}%", color = text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun MoneyCompareCard(labelA: String, labelB: String, money: ActivityMoney) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AtColors.glass).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MoneyPeriodBlock(labelA, money.turnoverA, money.profitA)
        Box(Modifier.fillMaxWidth().height(1.dp).background(AtColors.stroke.copy(alpha = 0.45f)))
        MoneyPeriodBlock(labelB, money.turnoverB, money.profitB)
    }
}

@Composable
private fun MoneyPeriodBlock(label: String, turnover: Double, profit: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = AtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        MoneyRow("Оборот (без доставки)", KassaApi.tmt(turnover))
        MoneyRow("Чистая прибыль", KassaApi.tmt(profit))
    }
}

@Composable
private fun MoneyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun CompareValueBars(
    title: String,
    hint: String,
    rows: List<CompareValueRow>,
    emptyText: String = "Нет данных за выбранный период.",
) {
    val maxVal = rows.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (hint.isNotBlank()) Text(hint, color = AtColors.muted, fontSize = 12.sp)
        if (rows.isEmpty()) {
            Text(emptyText, color = AtColors.muted, fontSize = 13.sp)
        } else {
            rows.take(40).forEach { row ->
                val pct = if (row.value > 0) ((row.value / maxVal) * 100.0).coerceIn(4.0, 100.0) else 0.0
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(row.label, color = AtColors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(row.caption, color = AtColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(99.dp)).background(AtColors.glass)) {
                        if (pct > 0) {
                            Box(Modifier.fillMaxWidth((pct / 100.0).toFloat()).height(10.dp).background(AtColors.accent))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReportZebraTable(
    title: String,
    headers: List<String>,
    rows: List<List<String>>,
    weights: List<Float>,
    emptyText: String = "Нет данных за выбранный период.",
) {
    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        if (rows.isEmpty()) {
            Text(emptyText, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
            return@Column
        }
        TableRow(headers, weights, header = true, zebra = false)
        rows.take(60).forEachIndexed { i, cols ->
            TableRow(cols, weights, header = false, zebra = i % 2 == 0)
        }
    }
}

@Composable
private fun TableRow(cols: List<String>, weights: List<Float>, header: Boolean, zebra: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (zebra) AtColors.glass else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cols.forEachIndexed { i, c ->
            Text(
                c.ifBlank { "—" },
                color = if (header) AtColors.muted else AtColors.text,
                fontSize = if (header) 11.sp else 13.sp,
                fontWeight = if (header) FontWeight.SemiBold else if (i == 0) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(weights.getOrElse(i) { 1f }),
                maxLines = if (header) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopEstablishmentsCard(rows: List<JSONObject>) {
    val parsed = rows.map { o ->
        Triple(
            KassaApi.pick(o, "name", "title", "establishmentName").ifBlank { "—" },
            KassaApi.jsonNum(o, "ordersCount", "orders"),
            KassaApi.jsonNum(o, "turnover", "revenue"),
        )
    }
    val maxTurnover = parsed.maxOfOrNull { it.third }?.coerceAtLeast(1.0) ?: 1.0
    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Топ заведений (аналитика)", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (parsed.isEmpty()) {
            Text("Нет данных за выбранный период.", color = AtColors.muted, fontSize = 13.sp)
            return@Column
        }
        parsed.take(20).forEach { (name, orders, turnover) ->
            val pct = if (turnover > 0) ((turnover / maxTurnover) * 100.0).coerceIn(4.0, 100.0) else 0.0
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(AtColors.glass)) {
                    if (pct > 0) Box(Modifier.fillMaxWidth((pct / 100.0).toFloat()).height(8.dp).background(AtColors.accent))
                }
                Text(
                    "${KassaApi.prettyNumber(orders.toString())} зак. · ${KassaApi.tmt(turnover)}",
                    color = AtColors.muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun PaymentBreakdownCard(rows: List<JSONObject>) {
    val parsed = rows.map { o ->
        val kind = KassaApi.pick(o, "paymentType")
        val amount = if (kind.equals("ONLINE", true) && o.has("paidAmount")) {
            KassaApi.jsonNum(o, "paidAmount")
        } else KassaApi.jsonNum(o, "turnover", "amount")
        Triple(KassaApi.payLabel(kind), KassaApi.jsonNum(o, "ordersCount"), amount to KassaApi.jsonNum(o, "sharePct", "share"))
    }
    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Разбивка по типу оплаты", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (parsed.isEmpty()) {
            Text("Нет данных за выбранный период.", color = AtColors.muted, fontSize = 13.sp)
            return@Column
        }
        TableRow(listOf("Тип", "Заказы", "Оборот (без доставки)"), listOf(1.1f, 0.7f, 1.4f), header = true, zebra = false)
        parsed.forEachIndexed { i, (label, orders, moneyShare) ->
            TableRow(
                listOf(label, KassaApi.prettyNumber(orders.toString()), KassaApi.tmt(moneyShare.first)),
                listOf(1.1f, 0.7f, 1.4f),
                header = false,
                zebra = i % 2 == 0,
            )
            val share = moneyShare.second
            if (share > 0) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).background(AtColors.glass)) {
                    Box(Modifier.fillMaxWidth((share / 100.0).toFloat().coerceIn(0.04f, 1f)).height(6.dp).background(AtColors.accent))
                }
                Text("${share.roundToInt()}% доля", color = AtColors.muted, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun UsersByMonthCard(rows: List<JSONObject>) {
    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Пользователи по месяцам регистрации", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "По дате создания аккаунта в базе клиентов. «С заказом» — если бэкенд отдаёт поле.",
            color = AtColors.muted,
            fontSize = 12.sp,
        )
        if (rows.isEmpty()) {
            Text("Нет данных по пользователям.", color = AtColors.muted, fontSize = 13.sp)
            return@Column
        }
        TableRow(listOf("Месяц", "Пользователи", "С заказом в кассе"), listOf(1.1f, 1f, 1.2f), header = true, zebra = false)
        rows.take(36).forEachIndexed { i, o ->
            val users = KassaApi.jsonNum(o, "usersCount", "users")
            val withN = o.opt("newUsersWithOrderCount")
            val withTxt = if (withN == null || o.isNull("newUsersWithOrderCount")) {
                "—"
            } else {
                KassaApi.prettyNumber(KassaApi.jsonNum(o, "newUsersWithOrderCount").toString())
            }
            val delta = KassaApi.jsonNum(o, "deltaUsers", "delta").toInt()
            val deltaTxt = if (delta != 0) " (${if (delta >= 0) "+" else ""}$delta)" else ""
            TableRow(
                listOf(
                    KassaApi.pick(o, "label", "month").ifBlank { "—" },
                    KassaApi.prettyNumber(users.toString()) + deltaTxt,
                    withTxt,
                ),
                listOf(1.1f, 1f, 1.2f),
                header = false,
                zebra = i % 2 == 0,
            )
        }
    }
}

private fun lastTwo(opts: List<CompareOption>): Pair<String, String> {
    if (opts.isEmpty()) return "" to ""
    val b = opts.last().value
    val a = opts.getOrNull(opts.lastIndex - 1)?.value ?: b
    return a to b
}

private fun chartTitle(mode: String): String = when (mode) {
    "week" -> "Сравнение по дням недели (пн–вс)"
    "fortnight" -> "Сравнение по 14 дням"
    "monthHour" -> "Сравнение по часам месяца (09:00–03:00)"
    "monthByDayOfMonth" -> "Сравнение по числам месяца (1…31)"
    else -> "Сравнение по часам (09:00–03:00)"
}

private fun chartFoot(mode: String): String = when (mode) {
    "week" -> "Неделя с понедельника по воскресенье. В подписи — дата периода 1 / периода 2."
    "fortnight" -> "Каждая строка — календарный день: N-й день от начала периода 1 и 2."
    "monthHour" -> "Время в локальном часовом поясе устройства. Показаны часы с 09:00 до 03:00, включая пустые."
    "monthByDayOfMonth" -> "Для каждого числа месяца — заказы в этот день в выбранных месяцах."
    else -> "Время в локальном часовом поясе устройства. Показаны часы с 09:00 до 03:00, включая пустые."
}

private fun orderPoints(summary: JSONObject?): List<OrderPoint> {
    val items = KassaApi.eachObj(KassaApi.jsonArr(summary, "items", "orders", "rows"))
    return items.mapNotNull { o ->
        val zoned = zonedOf(KassaApi.pick(o, "orderDatetime", "datetime", "createdAt")) ?: return@mapNotNull null
        OrderPoint(
            day = zoned.toLocalDate().toString(),
            month = YearMonth.from(zoned.toLocalDate()).toString(),
            hour = zoned.hour,
            amount = KassaApi.jsonNum(o, "orderAmount", "amount"),
            profit = KassaApi.jsonNum(o, "commissionAmount", "profit", "netProfit"),
        )
    }
}

private fun rangeDays(summary: JSONObject?, dateFrom: String, dateTo: String): List<String> {
    val period = summary?.optJSONObject("period")
    val fromRaw = period?.let { KassaApi.pick(it, "dateFrom") }.orEmpty().ifBlank { dateFrom }
    val toRaw = period?.let { KassaApi.pick(it, "dateTo") }.orEmpty().ifBlank { dateTo }
    val from = runCatching { LocalDate.parse(fromRaw.take(10)) }.getOrNull() ?: return emptyList()
    val to = runCatching { LocalDate.parse(toRaw.take(10)) }.getOrNull() ?: return emptyList()
    if (to.isBefore(from)) return emptyList()
    val out = ArrayList<String>()
    var cur = from
    while (!cur.isAfter(to)) {
        out += cur.toString()
        cur = cur.plusDays(1)
    }
    return out
}

private fun dayOptions(summary: JSONObject?, dateFrom: String, dateTo: String, points: List<OrderPoint>): List<CompareOption> {
    if (points.isEmpty()) return emptyList()
    val keys = LinkedHashSet<String>()
    keys.addAll(rangeDays(summary, dateFrom, dateTo))
    points.forEach { keys.add(it.day) }
    return keys.sorted().map { CompareOption(it, ruDay.format(LocalDate.parse(it))) }
}

private fun monthOptions(summary: JSONObject?, dateFrom: String, dateTo: String, points: List<OrderPoint>): List<CompareOption> {
    if (points.isEmpty()) return emptyList()
    val keys = LinkedHashSet<String>()
    val days = rangeDays(summary, dateFrom, dateTo)
    days.forEach { keys.add(it.take(7)) }
    points.forEach { keys.add(it.month) }
    return keys.filter { it.length >= 7 }.sorted().map { key ->
        val ym = YearMonth.parse(key)
        CompareOption(key, "${ruMonthsLong[ym.monthValue - 1]} ${ym.year}")
    }
}

private fun weekOptions(summary: JSONObject?, dateFrom: String, dateTo: String, points: List<OrderPoint>): List<CompareOption> {
    if (points.isEmpty()) return emptyList()
    val keys = LinkedHashSet<String>()
    rangeDays(summary, dateFrom, dateTo).forEach { keys.add(mondayOf(it)) }
    points.forEach { keys.add(mondayOf(it.day)) }
    return keys.sorted().map { start ->
        val from = LocalDate.parse(start)
        val to = from.plusDays(6)
        CompareOption(start, "${ruDay.format(from)} — ${ruDay.format(to)}")
    }
}

private fun mondayOf(ymd: String): String {
    val d = LocalDate.parse(ymd)
    val shift = if (d.dayOfWeek == DayOfWeek.SUNDAY) -6 else DayOfWeek.MONDAY.value - d.dayOfWeek.value
    return d.plusDays(shift.toLong()).toString()
}

private fun addDays(ymd: String, n: Int): String = LocalDate.parse(ymd).plusDays(n.toLong()).toString()

private fun buildActivityRows(
    points: List<OrderPoint>,
    mode: String,
    dayA: String,
    dayB: String,
    monthA: String,
    monthB: String,
    weekA: String,
    weekB: String,
    fortnightA: String,
    fortnightB: String,
): List<ActivityRow> = when (mode) {
    "monthHour" -> if (monthA.isBlank() || monthB.isBlank()) emptyList() else hoursForMonths(points, monthA, monthB)
    "week" -> if (weekA.isBlank() || weekB.isBlank()) emptyList() else weekdays(points, weekA, weekB)
    "fortnight" -> if (fortnightA.isBlank() || fortnightB.isBlank()) emptyList() else fortnight(points, fortnightA, fortnightB)
    "monthByDayOfMonth" -> if (monthA.isBlank() || monthB.isBlank()) emptyList() else daysOfMonth(points, monthA, monthB)
    else -> if (dayA.isBlank() || dayB.isBlank()) emptyList() else hoursForDays(points, dayA, dayB)
}

private fun hoursForDays(points: List<OrderPoint>, dayA: String, dayB: String): List<ActivityRow> {
    val a = IntArray(24)
    val b = IntArray(24)
    for (p in points) {
        if (p.day == dayA) a[p.hour]++
        if (p.day == dayB) b[p.hour]++
    }
    return (0..23).map { h -> ActivityRow("%02d:00".format(h), a[h], b[h]) }
}

private fun hoursForMonths(points: List<OrderPoint>, monthA: String, monthB: String): List<ActivityRow> {
    val a = IntArray(24)
    val b = IntArray(24)
    for (p in points) {
        if (p.month == monthA) a[p.hour]++
        if (p.month == monthB) b[p.hour]++
    }
    return (0..23).map { h -> ActivityRow("%02d:00".format(h), a[h], b[h]) }
}

private fun weekdays(points: List<OrderPoint>, weekA: String, weekB: String): List<ActivityRow> {
    return (0 until 7).map { i ->
        val da = addDays(weekA, i)
        val db = addDays(weekB, i)
        val ca = points.count { it.day == da }
        val cb = points.count { it.day == db }
        val la = shortDay(da)
        val lb = shortDay(db)
        ActivityRow("${ruWeekdays[i]} ($la / $lb)", ca, cb)
    }
}

private fun fortnight(points: List<OrderPoint>, startA: String, startB: String): List<ActivityRow> {
    return (0 until 14).map { i ->
        val da = addDays(startA, i)
        val db = addDays(startB, i)
        val ca = points.count { it.day == da }
        val cb = points.count { it.day == db }
        ActivityRow("День ${i + 1}: ${ruDayShort.format(LocalDate.parse(da))} / ${ruDayShort.format(LocalDate.parse(db))}", ca, cb)
    }
}

private fun daysOfMonth(points: List<OrderPoint>, monthA: String, monthB: String): List<ActivityRow> {
    val ya = YearMonth.parse(monthA)
    val yb = YearMonth.parse(monthB)
    val maxDay = max(ya.lengthOfMonth(), yb.lengthOfMonth())
    return (1..maxDay).map { day ->
        val da = if (day <= ya.lengthOfMonth()) ya.atDay(day).toString() else ""
        val db = if (day <= yb.lengthOfMonth()) yb.atDay(day).toString() else ""
        val ca = if (da.isBlank()) 0 else points.count { it.day == da }
        val cb = if (db.isBlank()) 0 else points.count { it.day == db }
        ActivityRow("$day число", ca, cb)
    }
}

private fun kitchenHoursIfNeeded(rows: List<ActivityRow>, mode: String): List<ActivityRow> {
    if (mode != "day" && mode != "monthHour") return rows
    val byHour = HashMap<Int, ActivityRow>()
    for (row in rows) {
        val h = row.rowLabel.take(2).toIntOrNull() ?: continue
        byHour[h] = row
    }
    return kitchenHours.map { h ->
        val src = byHour[h]
        ActivityRow("%02d:00".format(h), src?.ordersA ?: 0, src?.ordersB ?: 0)
    }
}

private fun activityTotals(rows: List<ActivityRow>): ActivityTotals {
    if (rows.isEmpty()) return ActivityTotals()
    val totalA = rows.sumOf { it.ordersA }
    val totalB = rows.sumOf { it.ordersB }
    val growthPct = when {
        totalA > 0 -> (totalB - totalA) * 100.0 / totalA
        totalB > 0 -> 100.0
        else -> 0.0
    }
    val growthHoursPct = rows.count { it.delta > 0 } * 100.0 / rows.size
    val peakA = rows.maxBy { it.ordersA }
    val peakB = rows.maxBy { it.ordersB }
    return ActivityTotals(totalA, totalB, growthPct, growthHoursPct, peakA.rowLabel, peakB.rowLabel, peakA.ordersA, peakB.ordersB)
}

private fun activityMoney(
    points: List<OrderPoint>,
    mode: String,
    dayA: String,
    dayB: String,
    monthA: String,
    monthB: String,
    weekA: String,
    weekB: String,
    fortnightA: String,
    fortnightB: String,
): ActivityMoney {
    fun sumDays(days: Set<String>): Pair<Double, Double> {
        var t = 0.0
        var p = 0.0
        for (pt in points) {
            if (pt.day in days) {
                t += pt.amount
                p += pt.profit
            }
        }
        return t to p
    }
    fun sumMonths(month: String): Pair<Double, Double> {
        var t = 0.0
        var p = 0.0
        for (pt in points) {
            if (pt.month == month) {
                t += pt.amount
                p += pt.profit
            }
        }
        return t to p
    }
    return when (mode) {
        "day" -> {
            val a = sumDays(setOf(dayA))
            val b = sumDays(setOf(dayB))
            ActivityMoney(a.first, b.first, a.second, b.second)
        }
        "week" -> {
            val a = sumDays((0 until 7).map { addDays(weekA, it) }.toSet())
            val b = sumDays((0 until 7).map { addDays(weekB, it) }.toSet())
            ActivityMoney(a.first, b.first, a.second, b.second)
        }
        "fortnight" -> {
            val a = sumDays((0 until 14).map { addDays(fortnightA, it) }.toSet())
            val b = sumDays((0 until 14).map { addDays(fortnightB, it) }.toSet())
            ActivityMoney(a.first, b.first, a.second, b.second)
        }
        "monthHour", "monthByDayOfMonth" -> {
            val a = sumMonths(monthA)
            val b = sumMonths(monthB)
            ActivityMoney(a.first, b.first, a.second, b.second)
        }
        else -> ActivityMoney()
    }
}

private fun shortDay(ymd: String): String {
    val d = LocalDate.parse(ymd)
    return "${d.dayOfMonth} ${ruMonthsShort[d.monthValue - 1]}"
}

private fun zonedOf(raw: String): java.time.ZonedDateTime? {
    val t = raw.trim()
    if (t.isBlank()) return null
    runCatching { return Instant.parse(t).atZone(ZoneId.systemDefault()) }
    runCatching { return LocalDateTime.parse(t.replace(' ', 'T').take(19)).atZone(ZoneId.systemDefault()) }
    runCatching { return LocalDate.parse(t.take(10)).atStartOfDay(ZoneId.systemDefault()) }
    return null
}
