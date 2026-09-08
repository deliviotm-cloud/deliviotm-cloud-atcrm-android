package tm.deliviotm.atcrm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourierFleetWorkspacePane(
    api: KassaApi,
    token: String?,
    initialTab: String = "couriers",
    onBack: () -> Unit,
    onOpen: (JsonRow, ModuleSpec) -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    var tab by remember { mutableStateOf(initialTab) }
    var status by remember { mutableStateOf("all") }
    var q by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(token, tab, tick) {
        val t = token ?: return@LaunchedEffect
        if (rows.isNotEmpty()) refreshing = true else loading = true
        err = null
        try {
            val path = when (tab) {
                "deliveries" -> "/courier-fleet/deliveries?take=200"
                "payroll" -> "/courier-fleet/payroll?take=200"
                else -> "/courier-fleet/couriers?take=200"
            }
            rows = withContext(Dispatchers.IO) { api.getRows(path, t) }
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            loading = false
            refreshing = false
        }
    }

    val visible = rows.filter { row ->
        courierStatusMatch(row, tab, status) && courierSearchMatch(row, q)
    }
    val activeN = rows.count { courierIsActive(it) }
    val trips = rows.sumOf { KassaApi.jsonNum(it.raw, "deliveriesMonth", "deliveries", "trips", "ordersCount") }
    val pay = rows.sumOf { KassaApi.jsonNum(it.raw, "monthlySalary", "salary", "amount", "payroll", "total") }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Курьеры", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SiteSectionHead("Курьеры", "Справочник курьеров, доставки и зарплата") }
                item {
                    SiteFilterSelect(
                        value = tab,
                        items = listOf("couriers" to "Курьеры", "deliveries" to "Доставки", "payroll" to "Зарплата"),
                        onChange = { tab = it; status = "all" },
                        label = "Раздел",
                    )
                }
                item {
                    SiteFilterSelect(
                        value = status,
                        items = if (tab == "couriers") {
                            listOf("all" to "Все", "active" to "Активные", "off" to "Выключенные")
                        } else {
                            listOf("all" to "Все", "progress" to "В работе", "done" to "Доставлены", "cancel" to "Отмены")
                        },
                        onChange = { status = it },
                        label = "Статус",
                    )
                }
                item {
                    OutlinedTextField(
                        q, { q = it },
                        placeholder = { Text("Курьер, телефон, город") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                }
                if (tab == "couriers") {
                    item {
                        Box(
                            Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent)
                                .clickable { creating = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+ Курьер", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (err != null) item { ActionBanner(err!!, error = true) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CourierKpi("всего", KassaApi.prettyNumber(rows.size.toString()), Modifier.weight(1f))
                        if (tab == "couriers") {
                            CourierKpi("активных", KassaApi.prettyNumber(activeN.toString()), Modifier.weight(1f))
                        } else {
                            CourierKpi("в списке", KassaApi.prettyNumber(visible.size.toString()), Modifier.weight(1f))
                        }
                    }
                }
                if (tab == "couriers") {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CourierKpi("доставок / мес.", KassaApi.prettyNumber(trips.toString()), Modifier.weight(1f))
                            CourierKpi("оклады", if (pay > 0) KassaApi.tmt(pay) else "0", Modifier.weight(1f))
                        }
                    }
                }
                if (loading && rows.isEmpty()) item { LoadingCard() }
                if (!loading && visible.isEmpty() && err == null) {
                    item {
                        Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp)) {
                            Text(
                                when (tab) {
                                    "deliveries" -> "Доставок нет"
                                    "payroll" -> "Начислений нет"
                                    else -> "Курьеров нет"
                                },
                                color = AtColors.text,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("Смените фильтр или обновите экран.", color = AtColors.muted, fontSize = 12.sp)
                        }
                    }
                }
                items(visible, key = { it.id }) { row ->
                    val spec = when (tab) {
                        "deliveries" -> ModuleSpec("courierFleet", "Доставки", "/courier-fleet/deliveries")
                        "payroll" -> ModuleSpec("courierFleet", "Зарплата", "/courier-fleet/payroll")
                        else -> ModuleSpec("courierFleet", "Курьеры", "/courier-fleet/couriers")
                    }
                    CourierFleetRow(row = row, tab = tab, onOpen = { onOpen(row, spec) })
                }
            }
        }
    }
    if (creating) {
        CourierCreateDialog(
            api = api,
            token = token,
            onClose = { creating = false },
            onCreated = {
                creating = false
                tick++
            },
        )
    }
}

@Composable
private fun CourierFleetRow(row: JsonRow, tab: String, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val title = KassaApi.pick(row.raw, "fullName", "courierName", "name").ifBlank { row.title }
    val phone = KassaApi.phoneOf(row.raw)
    val city = when (KassaApi.pick(row.raw, "cityKey", "city").lowercase()) {
        "ashgabat" -> "Ашхабад"
        "mary" -> "Мары"
        else -> KassaApi.pick(row.raw, "city", "zone")
    }
    val trips = KassaApi.jsonNum(row.raw, "deliveriesMonth", "deliveries", "trips", "ordersCount")
    val pay = KassaApi.jsonNum(row.raw, "monthlySalary", "salary", "amount", "payroll", "total")
    val status = if (tab == "couriers") {
        if (courierIsActive(row)) "Активен" else "Выключен"
    } else {
        prettyStatus(KassaApi.pick(row.raw, "status", "state"))
    }
    val meta = when (tab) {
        "deliveries" -> listOf(
            KassaApi.pick(row.raw, "courierName", "courier", "fullName"),
            KassaApi.pick(row.raw, "address", "deliveryAddress"),
            if (pay > 0) KassaApi.tmt(pay) else "",
            KassaApi.prettyTime(KassaApi.pick(row.raw, "deliveredAt", "createdAt", "orderDatetime")),
        )
        "payroll" -> listOf(
            KassaApi.pick(row.raw, "period", "statsMonth", "month"),
            if (pay > 0) KassaApi.tmt(pay) else "",
            if (trips > 0) "${KassaApi.prettyNumber(trips.toString())} дост." else "",
        )
        else -> listOf(
            phone,
            city,
            if (trips > 0) "${KassaApi.prettyNumber(trips.toString())} дост." else "",
            if (pay > 0) KassaApi.tmt(pay) else "",
        )
    }.filter { it.isNotBlank() }.joinToString(" · ")
    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(14.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
                Text(title.take(1).uppercase(), color = AtColors.accent, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) Text(meta, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (status.isNotBlank()) {
                Text(status, color = AtColors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (tab == "couriers" && phone.isNotBlank()) {
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AtColors.accent)
                    .clickable { DeviceIntents.dial(ctx, phone) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Позвонить · $phone", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun CourierKpi(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.atCard(14.dp).padding(12.dp)) {
        Text(label, color = AtColors.muted, fontSize = 12.sp)
        Text(value.ifBlank { "0" }, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun CourierCreateDialog(
    api: KassaApi,
    token: String?,
    onClose: () -> Unit,
    onCreated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable(enabled = !busy) { onClose() }) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AtColors.panel)
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Новый курьер", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("ФИО и телефон как в Delivio. Оклад — в TMT за месяц.", color = AtColors.muted, fontSize = 12.sp)
            if (err != null) ActionBanner(err!!, error = true)
            OutlinedTextField(fullName, { fullName = it }, label = { Text("ФИО") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(salary, { salary = it }, label = { Text("Оклад, TMT") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent)
                    .clickable(enabled = !busy) {
                        if (fullName.isBlank()) {
                            err = "Укажите ФИО"
                            return@clickable
                        }
                        val t = token ?: return@clickable
                        busy = true
                        scope.launch {
                            try {
                                val body = JSONObject().put("fullName", fullName.trim()).put("phone", phone.trim())
                                salary.replace(",", ".").replace(" ", "").toDoubleOrNull()?.let { body.put("monthlySalary", it) }
                                withContext(Dispatchers.IO) { api.postJson("/courier-fleet/couriers", t, body) }
                                onCreated()
                            } catch (e: Exception) {
                                err = e.message ?: "Не удалось создать"
                            } finally {
                                busy = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (busy) "Создание…" else "Создать", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text("Закрыть", color = AtColors.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onClose() }.padding(8.dp))
        }
    }
}

private fun courierIsActive(row: JsonRow): Boolean {
    if (row.raw.has("isActive")) return row.raw.optBoolean("isActive", true)
    val s = KassaApi.pick(row.raw, "status", "state", "online").lowercase()
    if (s.isBlank()) return true
    return !(s.contains("off") || s.contains("inactive") || s.contains("block") || s.contains("выкл"))
}

private fun courierStatusMatch(row: JsonRow, tab: String, status: String): Boolean {
    if (status == "all") return true
    if (tab == "couriers") {
        val on = courierIsActive(row)
        return if (status == "active") on else !on
    }
    val s = (prettyStatus(KassaApi.pick(row.raw, "status", "state")) + " " + KassaApi.pick(row.raw, "status", "state")).lowercase()
    return when (status) {
        "done" -> s.contains("готов") || s.contains("done") || s.contains("deliver") || s.contains("complete")
        "cancel" -> s.contains("отмен") || s.contains("cancel")
        "progress" -> s.contains("работ") || s.contains("progress") || s.contains("wait") || s.contains("new")
        else -> true
    }
}

private fun courierSearchMatch(row: JsonRow, q: String): Boolean {
    val n = q.trim()
    if (n.isBlank()) return true
    return listOf(
        row.title,
        row.subtitle,
        KassaApi.pick(row.raw, "fullName", "name", "courierName", "phone", "city", "cityKey", "address", "orderNumber"),
    ).any { it.contains(n, true) }
}
