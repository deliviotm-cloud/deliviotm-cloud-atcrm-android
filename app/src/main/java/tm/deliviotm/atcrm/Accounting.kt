package tm.deliviotm.atcrm

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val ACC_TABS = listOf(
    "registration" to "Профиль",
    "counterparties" to "Контрагенты",
    "items" to "Номенклатура",
    "warehouses" to "Склады",
    "sales" to "Реализация",
    "purchases" to "Поступление",
    "cash" to "Касса/Банк",
    "reports" to "Отчёты",
    "declarations" to "Декларации",
    "employees" to "Сотрудники",
    "timesheet" to "Табель",
    "calendar" to "Календарь",
    "payroll" to "Ведомость",
    "slips" to "Расчётные",
    "hr" to "HR",
    "reconciliation" to "Акты сверки",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountingWorkspacePane(
    api: KassaApi,
    token: String?,
    me: AppUser?,
    cache: CacheStore?,
    initialTab: String = "registration",
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val allowed = remember(me) { accountingTabsFor(me) }
    var tab by remember {
        mutableStateOf(if (initialTab in allowed.map { it.first }) initialTab else allowed.firstOrNull()?.first ?: "registration")
    }
    var month by remember { mutableStateOf(prefs.getString("acc_month", "").orEmpty().ifBlank { java.time.YearMonth.now().toString() }) }
    var orgId by remember { mutableStateOf(prefs.getString("acc_org", "").orEmpty()) }
    var orgs by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var extra by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<AccForm?>(null) }
    var reportKind by remember { mutableStateOf("income") }
    var reportFrom by remember { mutableStateOf(java.time.LocalDate.now().withDayOfMonth(1).withMonth(1).toString()) }
    var reportTo by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var cityKey by remember { mutableStateOf(prefs.getString("acc_city", "").orEmpty()) }
    val cities = remember(me) { KassaApi.opsCityOptions(me) }
    val pullState = rememberPullToRefreshState()

    fun persistOrg(id: String) {
        orgId = id
        api.accountingOrgId = id.ifBlank { null }
        prefs.edit().putString("acc_org", id).apply()
    }

    LaunchedEffect(month, cityKey) {
        prefs.edit().putString("acc_month", month).putString("acc_city", cityKey).apply()
    }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) { runCatching { api.getObject("/accounting/status", token) } }
            var list = withContext(Dispatchers.IO) { runCatching { api.getRows("/accounting/organizations", token) }.getOrDefault(emptyList()) }
            if (list.isEmpty()) {
                val body = JSONObject()
                    .put("id", "org_${System.currentTimeMillis().toString(36)}")
                    .put("name", "Основная организация")
                    .put("enabledProducts", JSONArray().put("ENTREPRENEUR"))
                withContext(Dispatchers.IO) { runCatching { api.postJson("/accounting/organizations", token, body) } }
                list = withContext(Dispatchers.IO) { runCatching { api.getRows("/accounting/organizations", token) }.getOrDefault(emptyList()) }
            }
            orgs = list
            val chosen = list.firstOrNull { it.id == orgId }?.id ?: list.firstOrNull()?.id.orEmpty()
            persistOrg(chosen)
        } catch (e: Exception) {
            err = e.message
        }
    }

    LaunchedEffect(token, orgId, tab, tick, month, reportKind, reportFrom, reportTo, cityKey) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        api.accountingOrgId = orgId.ifBlank { null }
        err = null
        if (loaded) refreshing = true
        try {
            val pack = withContext(Dispatchers.IO) { loadAccountingTab(api, token, tab, month, reportKind, reportFrom, reportTo, cityKey) }
            rows = pack.rows
            detail = pack.detail
            extra = pack.extra
            loaded = true
            cache?.putRows("/accounting/$tab", pack.rows)
        } catch (e: Exception) {
            err = e.message ?: "Ошибка"
            cache?.getRows("/accounting/$tab")?.let {
                rows = it
                loaded = true
            }
        } finally {
            refreshing = false
        }
    }

    fun runAction(label: String, block: suspend () -> Unit) {
        val t = token ?: return
        busy = true
        err = null
        msg = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                msg = label
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Ошибка"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Бухгалтерия", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SiteSectionHead("Бухгалтерия", "Профиль ИП, документы, продажи, отчёты") }
                if (orgs.size > 1) {
                    item {
                        Text("Организация", color = AtColors.muted, fontSize = 12.sp)
                        SiteSegmented(
                            value = orgId,
                            items = orgs.take(8).map { it.id to KassaApi.pick(it.raw, "name", "title").ifBlank { it.title } },
                            onChange = {
                                persistOrg(it)
                                tick++
                            },
                        )
                    }
                }
                if (tab in listOf("employees", "timesheet", "calendar", "payroll", "slips")) {
                    item {
                        OutlinedTextField(
                            month,
                            { month = it.filter { ch -> ch.isDigit() || ch == '-' }.take(7) },
                            label = { Text("Месяц (ГГГГ-ММ)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                        )
                    }
                }
                item {
                    SiteSegmented(value = tab, items = allowed, onChange = { tab = it; form = null })
                }
                if (err != null) item { ActionBanner(err!!, error = true) }
                if (msg != null) item { ActionBanner(msg!!, error = false) }
                if (!loaded && err == null) item { LoadingCard() }

                when (tab) {
                    "registration" -> {
                        val profile = detail?.optJSONObject("profile") ?: detail
                        item { AccPrimary("+ Сохранить профиль") { form = AccForm.Profile(profile ?: JSONObject()) } }
                        if (profile != null) {
                            item {
                                AccInfoCard(
                                    listOf(
                                        "ФИО" to KassaApi.pick(profile, "fullName", "name"),
                                        "ИНН" to KassaApi.pick(profile, "taxId"),
                                        "Режим" to if (KassaApi.pick(profile, "taxRegime").equals("GENERAL", true)) "Общая" else "Патент",
                                        "Телефон" to KassaApi.pick(profile, "phone"),
                                        "Адрес" to KassaApi.pick(profile, "address"),
                                    ),
                                )
                            }
                        }
                        val checklist = extra?.optJSONArray("checklist") ?: detail?.optJSONArray("checklist")
                        if (checklist != null && checklist.length() > 0) {
                            item {
                                Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Что вести дальше", color = AtColors.text, fontWeight = FontWeight.Bold)
                                    KassaApi.eachObj(checklist).forEach { row ->
                                        val nextTab = KassaApi.pick(row, "tab", "id")
                                        val label = KassaApi.pick(row, "label", "title", "name").ifBlank { nextTab }
                                        val ready = row.optBoolean("ready", false) || row.optBoolean("done", false)
                                        Row(
                                            Modifier.fillMaxWidth().clickable(enabled = nextTab.isNotBlank()) { if (nextTab.isNotBlank()) tab = nextTab }.padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(label, color = AtColors.text, fontSize = 14.sp)
                                            Text(if (ready) "готово" else "открыть", color = if (ready) AtColors.success else AtColors.accent, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "counterparties" -> {
                        item { AccPrimary("+ Контрагент") { form = AccForm.Counterparty(null) } }
                        items(rows, key = { it.id }) { row ->
                            AccEntityCard(
                                title = KassaApi.pick(row.raw, "name", "title").ifBlank { row.title },
                                meta = listOf(
                                    KassaApi.counterpartyKindLabel(KassaApi.pick(row.raw, "kind")),
                                    KassaApi.pick(row.raw, "taxId", "inn"),
                                    KassaApi.pick(row.raw, "phone"),
                                ).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                                status = if (row.raw.optBoolean("isActive", true)) "Активен" else "Выкл",
                                onOpen = { form = AccForm.Counterparty(row.raw) },
                            )
                        }
                    }
                    "items" -> {
                        item { AccPrimary("+ Номенклатура") { form = AccForm.Item(null) } }
                        items(rows, key = { it.id }) { row ->
                            AccEntityCard(
                                title = KassaApi.pick(row.raw, "name", "title").ifBlank { row.title },
                                meta = listOf(
                                    KassaApi.itemKindLabel(KassaApi.pick(row.raw, "kind")),
                                    KassaApi.pick(row.raw, "unitCode", "unit"),
                                    "НДС ${KassaApi.prettyNumber(KassaApi.jsonNum(row.raw, "vatRate").toString())}%",
                                    KassaApi.tmt(KassaApi.jsonNum(row.raw, "salePrice", "price")),
                                ).joinToString(" · "),
                                onOpen = { form = AccForm.Item(row.raw) },
                            )
                        }
                    }
                    "warehouses" -> {
                        item { AccPrimary("+ Склад") { form = AccForm.Warehouse(null) } }
                        items(rows, key = { it.id }) { row ->
                            AccEntityCard(
                                title = KassaApi.pick(row.raw, "name", "title").ifBlank { row.title },
                                meta = KassaApi.pick(row.raw, "responsiblePersonName", "responsibleName").ifBlank { "МОЛ не указан" },
                                status = if (row.raw.optBoolean("isActive", true)) "Активен" else "Выкл",
                                onOpen = { form = AccForm.Warehouse(row.raw) },
                            )
                        }
                    }
                    "sales", "purchases" -> {
                        val mode = if (tab == "sales") "sales" else "purchases"
                        item { AccPrimary(if (mode == "sales") "+ Реализация" else "+ Поступление") { form = AccForm.Invoice(mode, null) } }
                        items(rows, key = { it.id }) { row ->
                            val status = KassaApi.pick(row.raw, "status")
                            AccEntityCard(
                                title = listOf(
                                    KassaApi.pick(row.raw, "number", "docNumber").takeIf { it.isNotBlank() }?.let { "№ $it" },
                                    KassaApi.pick(row.raw, "counterpartyName", "name"),
                                ).filterNotNull().joinToString(" · ").ifBlank { row.title },
                                meta = listOf(
                                    KassaApi.pick(row.raw, "docDate", "date"),
                                    KassaApi.tmt(KassaApi.jsonNum(row.raw, "amount", "total", "sum")),
                                    KassaApi.paymentMethodLabel(KassaApi.pick(row.raw, "paymentMethod")),
                                ).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                                status = KassaApi.accountingDocStatus(status),
                                actions = buildList {
                                    if (status.equals("DRAFT", true)) {
                                        add("Изменить" to { form = AccForm.Invoice(mode, row.raw) })
                                        add("Провести" to {
                                            runAction("Проведено") {
                                                api.postJson("/accounting/${if (mode == "sales") "sales-invoices" else "purchase-invoices"}/${row.id}/post", tkn(token), JSONObject())
                                            }
                                        })
                                    }
                                    if (status.equals("POSTED", true)) {
                                        add("Аннулировать" to {
                                            runAction("Аннулировано") {
                                                api.postJson("/accounting/${if (mode == "sales") "sales-invoices" else "purchase-invoices"}/${row.id}/void", tkn(token), JSONObject())
                                            }
                                        })
                                    }
                                },
                            )
                        }
                    }
                    "cash" -> {
                        item { AccPrimary("+ Кассовый ордер") { form = AccForm.Cash(null) } }
                        items(rows, key = { it.id }) { row ->
                            val status = KassaApi.pick(row.raw, "status")
                            AccEntityCard(
                                title = listOf(
                                    KassaApi.cashDirectionLabel(KassaApi.pick(row.raw, "direction")),
                                    KassaApi.tmt(KassaApi.jsonNum(row.raw, "amount")),
                                ).joinToString(" · "),
                                meta = listOf(
                                    KassaApi.pick(row.raw, "number", "docNumber"),
                                    KassaApi.pick(row.raw, "docDate", "date"),
                                    KassaApi.pick(row.raw, "counterpartyName"),
                                    KassaApi.pick(row.raw, "purpose"),
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                                status = KassaApi.accountingDocStatus(status),
                                actions = buildList {
                                    if (status.equals("DRAFT", true)) {
                                        add("Провести" to {
                                            runAction("Проведено") { api.postJson("/accounting/cash-orders/${row.id}/post", tkn(token), JSONObject()) }
                                        })
                                    }
                                    if (status.equals("POSTED", true)) {
                                        add("Аннулировать" to {
                                            runAction("Аннулировано") { api.postJson("/accounting/cash-orders/${row.id}/void", tkn(token), JSONObject()) }
                                        })
                                    }
                                },
                            )
                        }
                    }
                    "reports" -> {
                        item {
                            SiteSegmented(
                                value = reportKind,
                                items = listOf("income" to "Доходы/расходы", "trial" to "ОСВ"),
                                onChange = { reportKind = it },
                            )
                        }
                        item {
                            OutlinedTextField(reportFrom, { reportFrom = it }, label = { Text("С даты") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        }
                        item {
                            OutlinedTextField(reportTo, { reportTo = it }, label = { Text("По дату") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        }
                        val o = detail
                        if (reportKind == "income" && o != null) {
                            item {
                                ReportKpiGrid(
                                    listOf(
                                        PulseMetric("Доходы", KassaApi.tmt(KassaApi.jsonNum(o, "income")), "", "↑"),
                                        PulseMetric("Расходы", KassaApi.tmt(KassaApi.jsonNum(o, "expense")), "", "↓"),
                                        PulseMetric("Прибыль", KassaApi.tmt(KassaApi.jsonNum(o, "profit")), "", "Σ"),
                                    ),
                                )
                            }
                            item {
                                ReportTableCard(
                                    "Доходы",
                                    listOf("Показатель", "Сумма"),
                                    KassaApi.eachObj(o.optJSONArray("incomeRows") ?: JSONArray()).map {
                                        listOf(KassaApi.pick(it, "label", "name", "title"), KassaApi.tmt(KassaApi.jsonNum(it, "amount", "sum")))
                                    },
                                )
                            }
                            item {
                                ReportTableCard(
                                    "Расходы",
                                    listOf("Показатель", "Сумма"),
                                    KassaApi.eachObj(o.optJSONArray("expenseRows") ?: JSONArray()).map {
                                        listOf(KassaApi.pick(it, "label", "name", "title"), KassaApi.tmt(KassaApi.jsonNum(it, "amount", "sum")))
                                    },
                                )
                            }
                        } else if (o != null) {
                            item {
                                ReportTableCard(
                                    "Оборотно-сальдовая",
                                    listOf("Счёт", "Наименование", "Дебет", "Кредит"),
                                    KassaApi.eachObj(o.optJSONArray("rows") ?: JSONArray()).map {
                                        listOf(
                                            KassaApi.pick(it, "account", "code"),
                                            KassaApi.pick(it, "name", "title", "label"),
                                            KassaApi.tmt(KassaApi.jsonNum(it, "debit", "totalDebit")),
                                            KassaApi.tmt(KassaApi.jsonNum(it, "credit", "totalCredit")),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    "declarations" -> {
                        item { AccPrimary("Рассчитать по бланку №27") { form = AccForm.Declaration } }
                        items(rows, key = { it.id }) { row ->
                            AccEntityCard(
                                title = KassaApi.pick(row.raw, "title", "formCode", "name").ifBlank { row.title },
                                meta = listOf(
                                    KassaApi.pick(row.raw, "period", "year"),
                                    KassaApi.accountingDocStatus(KassaApi.pick(row.raw, "status")),
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                                status = KassaApi.accountingDocStatus(KassaApi.pick(row.raw, "status")),
                                actions = listOf(
                                    "Отметить сданной" to {
                                        runAction("Отмечена сданной") { api.postJson("/accounting/tax-reports/${row.id}/submit", tkn(token), JSONObject()) }
                                    },
                                ),
                            )
                        }
                    }
                    "employees" -> {
                        item { AccPrimary("+ Сотрудник") { form = AccForm.Employee(null) } }
                        items(rows, key = { it.id }) { row ->
                            val active = row.raw.optBoolean("isActive", true)
                            AccEntityCard(
                                title = KassaApi.pick(row.raw, "fullName", "name").ifBlank { row.title },
                                meta = listOf(
                                    KassaApi.employeeKindLabel(KassaApi.pick(row.raw, "kind")),
                                    KassaApi.pick(row.raw, "position"),
                                    KassaApi.tmt(KassaApi.jsonNum(row.raw, "monthlySalary")),
                                ).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                                status = if (active) "Активен" else "Выкл",
                                actions = listOf(
                                    "Изменить" to { form = AccForm.Employee(row.raw) },
                                    (if (active) "Выкл" else "Вкл") to {
                                        runAction(if (active) "Выключен" else "Включён") {
                                            api.patchJson("/accounting/employees/${row.id}", tkn(token), JSONObject().put("isActive", !active))
                                        }
                                    },
                                ),
                            )
                        }
                    }
                    "timesheet" -> {
                        item {
                            AccPrimary("Импорт курьеров") {
                                runAction("Импорт выполнен") { api.postJson("/accounting/timesheet/import-courier?month=$month", tkn(token), JSONObject()) }
                            }
                        }
                        val days = KassaApi.monthDays(month)
                        val staff = rows.filter { !KassaApi.pick(it.raw, "kind").equals("OUTSOURCE", true) }
                        items(staff, key = { it.id }) { emp ->
                            val map = timesheetDayMap(detail, emp.id)
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(KassaApi.pick(emp.raw, "fullName", "name").ifBlank { emp.title }, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                                Text(KassaApi.employeeKindLabel(KassaApi.pick(emp.raw, "kind")), color = AtColors.muted, fontSize = 12.sp)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (1..days).forEach { d ->
                                        val cell = map[d]
                                        val frac = cell?.let { KassaApi.jsonNum(it, "dayFraction") } ?: 0.0
                                        val mark = when {
                                            frac >= 1 -> "1"
                                            frac >= 0.5 -> "½"
                                            cell != null -> "0"
                                            else -> "·"
                                        }
                                        Box(
                                            Modifier.width(28.dp).height(32.dp).clip(RoundedCornerShape(8.dp)).background(AtColors.glass)
                                                .clickable {
                                                    form = AccForm.TimesheetDay(emp.id, KassaApi.pick(emp.raw, "fullName", "name").ifBlank { emp.title }, d, month, cell)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(d.toString(), color = AtColors.muted, fontSize = 9.sp)
                                                Text(mark, color = AtColors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "calendar" -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AccChip("Импорт ТМ 2026") {
                                    runAction("Календарь импортирован") { api.postJson("/accounting/calendar/import-tm-2026", tkn(token), JSONObject()) }
                                }
                            }
                        }
                        items(rows, key = { it.id.ifBlank { KassaApi.pick(it.raw, "workDate") } }) { row ->
                            AccEntityCard(
                                title = KassaApi.pick(row.raw, "workDate", "date").ifBlank { row.title },
                                meta = listOf(
                                    KassaApi.calendarKindLabel(KassaApi.pick(row.raw, "kind")),
                                    KassaApi.pick(row.raw, "title", "name"),
                                ).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                                onOpen = { form = AccForm.CalendarDay(row.raw) },
                            )
                        }
                    }
                    "payroll" -> {
                        val run = detail
                        val status = run?.let { KassaApi.pick(it, "status") }.orEmpty()
                        val runId = run?.let { KassaApi.pick(it, "id") }.orEmpty()
                        item {
                            when {
                                run == null -> AccPrimary("Создать сводную ведомость") {
                                    runAction("Черновик ведомости создан") {
                                        api.postJson("/accounting/payroll-runs", tkn(token), JSONObject().put("period", month).put("title", "Сводная ведомость $month"))
                                    }
                                }
                                status.equals("DRAFT", true) -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AccPrimary("Пересчитать") { runAction("Ведомость пересчитана") { api.postJson("/accounting/payroll-runs/$runId/build", tkn(token), JSONObject()) } }
                                        AccChip("Отправить на утверждение") { runAction("Отправлена") { api.postJson("/accounting/payroll-runs/$runId/submit", tkn(token), JSONObject()) } }
                                    }
                                }
                                status.equals("SUBMITTED", true) -> AccPrimary("Утвердить") {
                                    runAction("Утверждена") { api.postJson("/accounting/payroll-runs/$runId/approve", tkn(token), JSONObject()) }
                                }
                                status.equals("APPROVED", true) -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AccPrimary("Отметить выплату") { runAction("Отмечена выплаченной") { api.postJson("/accounting/payroll-runs/$runId/mark-paid", tkn(token), JSONObject()) } }
                                        AccChip("Персональные листы") { runAction("Листы созданы") { api.postJson("/accounting/payroll-runs/$runId/individual-slips", tkn(token), JSONObject()) } }
                                    }
                                }
                                status.equals("PAID", true) -> AccChip("Персональные листы") {
                                    runAction("Листы созданы") { api.postJson("/accounting/payroll-runs/$runId/individual-slips", tkn(token), JSONObject()) }
                                }
                            }
                        }
                        if (run != null) {
                            item {
                                Text("Статус: ${KassaApi.accountingDocStatus(status)}", color = AtColors.muted, fontSize = 13.sp)
                            }
                            val lines = KassaApi.eachObj(run.optJSONArray("lines") ?: JSONArray())
                            items(lines.mapIndexed { i, o -> KassaApi.rowOf(o, i) }, key = { it.id.ifBlank { it.title } }) { line ->
                                AccEntityCard(
                                    title = KassaApi.pick(line.raw, "employeeName", "fullName", "name").ifBlank { line.title },
                                    meta = listOf(
                                        "дней ${KassaApi.prettyNumber(KassaApi.jsonNum(line.raw, "workDays", "days").toString())}",
                                        "начисл. ${KassaApi.tmt(KassaApi.jsonNum(line.raw, "accrued"))}",
                                        "карта ${KassaApi.tmt(KassaApi.jsonNum(line.raw, "cardAmount"))}",
                                        "нал. ${KassaApi.tmt(KassaApi.jsonNum(line.raw, "cashAmount"))}",
                                    ).joinToString(" · "),
                                    onOpen = if (status.equals("DRAFT", true)) ({ form = AccForm.PayrollLine(runId, line.raw) }) else null,
                                )
                            }
                        }
                    }
                    "slips" -> {
                        items(rows, key = { it.id }) { row ->
                            AccEntityCard(
                                title = KassaApi.pick(row.raw, "employeeName", "title", "name").ifBlank { row.title },
                                meta = listOf(
                                    "дней ${KassaApi.prettyNumber(KassaApi.jsonNum(row.raw, "workDays").toString())}",
                                    KassaApi.tmt(KassaApi.jsonNum(row.raw, "netPay", "accrued")),
                                    KassaApi.accountingDocStatus(KassaApi.pick(row.raw, "status")),
                                ).joinToString(" · "),
                                actions = listOf(
                                    "Подписать" to {
                                        runAction("Подписан") { api.postJson("/accounting/payroll-runs/${row.id}/sign", tkn(token), JSONObject()) }
                                    },
                                ),
                            )
                        }
                    }
                    "hr" -> {
                        item { AccPrimary("+ HR-событие") { form = AccForm.Hr } }
                        items(rows, key = { it.id }) { row ->
                            AccEntityCard(
                                title = KassaApi.hrEventLabel(KassaApi.pick(row.raw, "type", "kind")),
                                meta = listOf(
                                    KassaApi.pick(row.raw, "employeeName", "fullName"),
                                    KassaApi.pick(row.raw, "startDate", "date"),
                                    KassaApi.pick(row.raw, "endDate"),
                                    KassaApi.pick(row.raw, "comment", "note"),
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                            )
                        }
                    }
                    "reconciliation" -> {
                        if (cities.size > 1) {
                            item {
                                Text("Город учёта", color = AtColors.muted, fontSize = 12.sp)
                                SiteSegmented(value = cityKey, items = cities.take(8).map { it.key to it.name }, onChange = { cityKey = it })
                            }
                        }
                        item {
                            AccPrimary("Экспорт отмеченных") {
                                val ids = rows.filter { it.raw.optBoolean("_checked", false) }.map { it.id }
                                val use = ids.ifEmpty { rows.map { it.id } }
                                val t = token ?: return@AccPrimary
                                val range = KassaApi.monthRange(month)
                                busy = true
                                scope.launch {
                                    try {
                                        val (bytes, name) = withContext(Dispatchers.IO) {
                                            api.postDownload(
                                                "/reconciliation-acts/export",
                                                t,
                                                JSONObject()
                                                    .put("dateFrom", range.first)
                                                    .put("dateTo", range.second)
                                                    .put("establishmentIds", JSONArray(use))
                                                    .apply { if (cityKey.isNotBlank()) put("cityKey", cityKey) },
                                            )
                                        }
                                        val file = File(ctx.cacheDir, name.ifBlank { "acts.zip" })
                                        file.writeBytes(bytes)
                                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                                        ctx.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                                "Акты сверки",
                                            ),
                                        )
                                        msg = "Экспорт готов"
                                    } catch (e: Exception) {
                                        err = e.message
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                        items(rows, key = { it.id }) { row ->
                            AccEntityCard(
                                title = KassaApi.establishmentName(row.raw).ifBlank { KassaApi.pick(row.raw, "name", "title").ifBlank { row.title } },
                                meta = listOf(
                                    KassaApi.estTypeLabel(KassaApi.opEstType(row.raw)),
                                    KassaApi.pick(row.raw, "actCode"),
                                    KassaApi.pick(row.raw, "directorFullName"),
                                ).filter { it.isNotBlank() && it != "—" }.joinToString(" · "),
                                onOpen = { form = AccForm.Recon(row.raw) },
                            )
                        }
                    }
                }
                if (busy) item { Text("Выполняется…", color = AtColors.muted, fontSize = 13.sp) }
            }
        }
    }

    form?.let { current ->
        AccFormHost(
            form = current,
            api = api,
            token = token ?: return,
            orgId = orgId,
            month = month,
            employees = rows,
            onDismiss = { form = null },
            onSaved = {
                form = null
                msg = "Сохранено"
                tick++
            },
            onFail = { err = it },
        )
    }
}

private fun tkn(token: String?): String = token ?: error("Нет сессии")

private fun accountingTabsFor(me: AppUser?): List<Pair<String, String>> {
    val perms = me?.permissions.orEmpty()
    if (perms.isEmpty() || perms.any { it == "tab.accounting" || it.endsWith(".accounting") && !it.contains("tab.accounting.") }) return ACC_TABS
    val filtered = ACC_TABS.filter { (id, _) ->
        val aliases = listOf("tab.accounting.$id", "accounting.$id")
        val extra = if (id == "warehouses") listOf("tab.accounting.items") else emptyList()
        perms.any { p -> (aliases + extra).any { p == it || p.contains(it) } }
    }
    return filtered.ifEmpty { ACC_TABS }
}

private data class AccPack(
    val rows: List<JsonRow>,
    val detail: JSONObject? = null,
    val extra: JSONObject? = null,
)

private fun loadAccountingTab(
    api: KassaApi,
    token: String,
    tab: String,
    month: String,
    reportKind: String,
    reportFrom: String,
    reportTo: String,
    cityKey: String,
): AccPack {
    return when (tab) {
        "registration" -> {
            val o = api.getObject("/accounting/registration", token)
            AccPack(emptyList(), o, o)
        }
        "counterparties" -> AccPack(api.getRows("/accounting/counterparties?activeOnly=1", token))
        "items" -> AccPack(api.getRows("/accounting/items?activeOnly=1", token))
        "warehouses" -> AccPack(api.getRows("/accounting/warehouses?activeOnly=1", token))
        "sales" -> AccPack(api.getRows("/accounting/sales-invoices", token))
        "purchases" -> AccPack(api.getRows("/accounting/purchase-invoices", token))
        "cash" -> AccPack(api.getRows("/accounting/cash-orders", token))
        "reports" -> {
            val path = if (reportKind == "trial") {
                "/accounting/reports/trial-balance?from=$reportFrom&to=$reportTo"
            } else {
                "/accounting/reports/income-statement?from=$reportFrom&to=$reportTo"
            }
            AccPack(emptyList(), api.getObject(path, token))
        }
        "declarations" -> AccPack(api.getRows("/accounting/tax-reports", token))
        "employees" -> AccPack(api.getRows("/accounting/employees?activeOnly=1", token))
        "timesheet" -> {
            val sheet = api.getObject("/accounting/timesheet?month=$month", token)
            val emps = api.getRows("/accounting/employees?activeOnly=1", token)
            val att = runCatching { api.getObject("/accounting/attendance?month=$month", token) }.getOrNull()
            AccPack(emps, sheet, att)
        }
        "calendar" -> AccPack(api.getRows("/accounting/calendar?month=$month", token))
        "payroll" -> {
            val list = api.getRows("/accounting/payroll-runs?period=$month", token).filter {
                KassaApi.pick(it.raw, "type").uppercase() in listOf("", "CONSOLIDATED")
            }
            val open = list.firstOrNull { !KassaApi.pick(it.raw, "status").equals("PAID", true) } ?: list.firstOrNull()
            val one = open?.let { runCatching { api.getObject("/accounting/payroll-runs/${it.id}", token) }.getOrNull() }
            AccPack(list, one)
        }
        "slips" -> AccPack(
            api.getRows("/accounting/payroll-runs?period=$month", token).filter {
                KassaApi.pick(it.raw, "type").equals("INDIVIDUAL", true)
            },
        )
        "hr" -> AccPack(api.getRows("/accounting/hr-events", token))
        "reconciliation" -> {
            val range = KassaApi.monthRange(month)
            val q = StringBuilder("/reconciliation-acts/profiles?dateFrom=${range.first}&dateTo=${range.second}")
            if (cityKey.isNotBlank()) q.append("&cityKey=${java.net.URLEncoder.encode(cityKey, "UTF-8")}")
            AccPack(api.getRows(q.toString(), token))
        }
        else -> AccPack(emptyList())
    }
}

private fun timesheetDayMap(sheet: JSONObject?, employeeId: String): Map<Int, JSONObject> {
    val arr = sheet?.optJSONArray("items") ?: sheet?.optJSONArray("days") ?: return emptyMap()
    val out = LinkedHashMap<Int, JSONObject>()
    KassaApi.eachObj(arr).forEach { row ->
        val emp = KassaApi.pick(row, "employeeId")
        if (emp.isNotBlank() && emp != employeeId) return@forEach
        val date = KassaApi.pick(row, "workDate", "date")
        val day = date.takeLast(2).toIntOrNull() ?: return@forEach
        out[day] = row
    }
    return out
}

private sealed class AccForm {
    data class Profile(val src: JSONObject) : AccForm()
    data class Counterparty(val src: JSONObject?) : AccForm()
    data class Item(val src: JSONObject?) : AccForm()
    data class Warehouse(val src: JSONObject?) : AccForm()
    data class Invoice(val mode: String, val src: JSONObject?) : AccForm()
    data class Cash(val src: JSONObject?) : AccForm()
    data object Declaration : AccForm()
    data class Employee(val src: JSONObject?) : AccForm()
    data class TimesheetDay(val employeeId: String, val name: String, val day: Int, val month: String, val cell: JSONObject?) : AccForm()
    data class CalendarDay(val src: JSONObject) : AccForm()
    data class PayrollLine(val runId: String, val line: JSONObject) : AccForm()
    data object Hr : AccForm()
    data class Recon(val src: JSONObject) : AccForm()
}

@Composable
private fun AccFormHost(
    form: AccForm,
    api: KassaApi,
    token: String,
    orgId: String,
    month: String,
    employees: List<JsonRow>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onFail: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val fields = remember(form) { AccFields.from(form, month) }
    var state by remember { mutableStateOf(fields) }
    var counterparties by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var warehouses by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var empOpts by remember { mutableStateOf(employees) }

    LaunchedEffect(form) {
        counterparties = withContext(Dispatchers.IO) { runCatching { api.getRows("/accounting/counterparties?activeOnly=1", token) }.getOrDefault(emptyList()) }
        warehouses = withContext(Dispatchers.IO) { runCatching { api.getRows("/accounting/warehouses?activeOnly=1", token) }.getOrDefault(emptyList()) }
        if (empOpts.isEmpty()) {
            empOpts = withContext(Dispatchers.IO) { runCatching { api.getRows("/accounting/employees?activeOnly=1", token) }.getOrDefault(emptyList()) }
        }
    }

    fun save() {
        busy = true
        err = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { persistAccForm(api, token, form, state, orgId) }
                onSaved()
            } catch (e: Exception) {
                err = e.message
                onFail(e.message ?: "Ошибка")
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp),
        ) {
            Text(fields.title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.inputs.forEach { input ->
                    when (input.kind) {
                        "segment" -> {
                            Text(input.label, color = AtColors.muted, fontSize = 12.sp)
                            SiteSegmented(
                                value = state.values[input.key].orEmpty(),
                                items = input.options,
                                onChange = { state = state.copy(values = state.values + (input.key to it)) },
                            )
                        }
                        "check" -> {
                            val on = state.values[input.key] == "1"
                            Row(Modifier.fillMaxWidth().clickable { state = state.copy(values = state.values + (input.key to if (on) "0" else "1")) }.padding(vertical = 6.dp)) {
                                Text(if (on) "☑  ${input.label}" else "☐  ${input.label}", color = AtColors.text)
                            }
                        }
                        "pick" -> {
                            val source = when (input.key) {
                                "counterpartyId" -> counterparties
                                "warehouseId" -> warehouses
                                "employeeId", "responsibleEmployeeId" -> empOpts
                                else -> emptyList()
                            }
                            Text(input.label, color = AtColors.muted, fontSize = 12.sp)
                            val current = state.values[input.key].orEmpty()
                            source.take(12).forEach { row ->
                                val name = KassaApi.pick(row.raw, "fullName", "name", "title").ifBlank { row.title }
                                val selected = row.id == current
                                Text(
                                    if (selected) "● $name" else "○ $name",
                                    color = if (selected) AtColors.accent else AtColors.text,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AtColors.glass).clickable {
                                        state = state.copy(values = state.values + (input.key to row.id))
                                    }.padding(10.dp),
                                )
                            }
                        }
                        else -> OutlinedTextField(
                            state.values[input.key].orEmpty(),
                            { state = state.copy(values = state.values + (input.key to it)) },
                            label = { Text(input.label) },
                            singleLine = input.kind != "area",
                            minLines = if (input.kind == "area") 3 else 1,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                        )
                    }
                }
                err?.let { Text(it, color = AtColors.danger, fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.glass).clickable(enabled = !busy, onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("Отмена", color = AtColors.muted, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent).clickable(enabled = !busy, onClick = { save() }),
                    contentAlignment = Alignment.Center,
                ) { Text(if (busy) "Сохранение…" else "Сохранить", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private data class AccInput(
    val key: String,
    val label: String,
    val kind: String = "text",
    val options: List<Pair<String, String>> = emptyList(),
)

private data class AccFields(
    val title: String,
    val inputs: List<AccInput>,
    val values: Map<String, String>,
) {
    companion object {
        fun from(form: AccForm, month: String): AccFields {
            fun v(o: JSONObject?, vararg keys: String, fallback: String = "") =
                o?.let { KassaApi.pick(it, *keys) }.orEmpty().ifBlank { fallback }
            return when (form) {
                is AccForm.Profile -> AccFields(
                    "Профиль ИП",
                    listOf(
                        AccInput("fullName", "ФИО"),
                        AccInput("regNo", "Рег. номер"),
                        AccInput("phone", "Телефон"),
                        AccInput("address", "Адрес", "area"),
                        AccInput("taxOffice", "Налоговая служба"),
                        AccInput("taxId", "ИНН (12 цифр)"),
                        AccInput("taxRegisteredAt", "Дата постановки"),
                        AccInput("pensionFund", "Пенсионный фонд"),
                        AccInput("pensionCode", "Код ПФ"),
                        AccInput("pensionRegisteredAt", "Дата в ПФ"),
                        AccInput("taxRegime", "Режим", "segment", listOf("PATENT" to "Патент", "GENERAL" to "Общая")),
                        AccInput("hasEmployees", "Есть сотрудники", "check"),
                        AccInput("hasBankAccount", "Есть расчётный счёт", "check"),
                    ),
                    mapOf(
                        "fullName" to v(form.src, "fullName", "name"),
                        "regNo" to v(form.src, "regNo"),
                        "phone" to v(form.src, "phone"),
                        "address" to v(form.src, "address"),
                        "taxOffice" to v(form.src, "taxOffice"),
                        "taxId" to v(form.src, "taxId"),
                        "taxRegisteredAt" to v(form.src, "taxRegisteredAt"),
                        "pensionFund" to v(form.src, "pensionFund"),
                        "pensionCode" to v(form.src, "pensionCode"),
                        "pensionRegisteredAt" to v(form.src, "pensionRegisteredAt"),
                        "taxRegime" to v(form.src, "taxRegime", fallback = "PATENT"),
                        "hasEmployees" to if (form.src.optBoolean("hasEmployees", false)) "1" else "0",
                        "hasBankAccount" to if (form.src.optBoolean("hasBankAccount", false)) "1" else "0",
                    ),
                )
                is AccForm.Counterparty -> AccFields(
                    if (form.src == null) "Новый контрагент" else "Контрагент",
                    listOf(
                        AccInput("kind", "Тип", "segment", listOf("CUSTOMER" to "Покупатель", "SUPPLIER" to "Поставщик", "BOTH" to "Оба")),
                        AccInput("name", "Название"),
                        AccInput("taxId", "INN"),
                        AccInput("regNo", "Рег. номер"),
                        AccInput("phone", "Телефон"),
                        AccInput("email", "Email"),
                        AccInput("address", "Адрес"),
                        AccInput("note", "Заметка", "area"),
                    ),
                    mapOf(
                        "kind" to v(form.src, "kind", fallback = "CUSTOMER"),
                        "name" to v(form.src, "name"),
                        "taxId" to v(form.src, "taxId"),
                        "regNo" to v(form.src, "regNo"),
                        "phone" to v(form.src, "phone"),
                        "email" to v(form.src, "email"),
                        "address" to v(form.src, "address"),
                        "note" to v(form.src, "note"),
                    ),
                )
                is AccForm.Item -> AccFields(
                    if (form.src == null) "Новая номенклатура" else "Номенклатура",
                    listOf(
                        AccInput("kind", "Тип", "segment", listOf("GOOD" to "Товар", "SERVICE" to "Услуга")),
                        AccInput("sku", "Артикул"),
                        AccInput("name", "Наименование"),
                        AccInput("unitCode", "Ед."),
                        AccInput("vatRate", "НДС %"),
                        AccInput("salePrice", "Цена"),
                    ),
                    mapOf(
                        "kind" to v(form.src, "kind", fallback = "GOOD"),
                        "sku" to v(form.src, "sku"),
                        "name" to v(form.src, "name"),
                        "unitCode" to v(form.src, "unitCode", fallback = "шт"),
                        "vatRate" to v(form.src, "vatRate", fallback = "0"),
                        "salePrice" to v(form.src, "salePrice", "price", fallback = "0"),
                    ),
                )
                is AccForm.Warehouse -> AccFields(
                    if (form.src == null) "Новый склад" else "Склад",
                    listOf(
                        AccInput("name", "Название"),
                        AccInput("responsiblePersonName", "МОЛ (ФИО)"),
                        AccInput("isActive", "Активен", "check"),
                    ),
                    mapOf(
                        "name" to v(form.src, "name"),
                        "responsiblePersonName" to v(form.src, "responsiblePersonName"),
                        "isActive" to if (form.src?.optBoolean("isActive", true) != false) "1" else "0",
                    ),
                )
                is AccForm.Invoice -> AccFields(
                    if (form.mode == "sales") "Реализация" else "Поступление",
                    listOf(
                        AccInput("docDate", "Дата"),
                        AccInput("counterpartyId", "Контрагент", "pick"),
                        AccInput("warehouseId", "Склад", "pick"),
                        AccInput("paymentMethod", "Оплата", "segment", listOf("CREDIT" to "В долг", "CASH" to "Наличные", "BANK" to "Банк")),
                        AccInput("lineName", "Позиция"),
                        AccInput("qty", "Кол-во"),
                        AccInput("price", "Цена"),
                        AccInput("vatRate", "НДС %"),
                    ),
                    mapOf(
                        "docDate" to v(form.src, "docDate", "date", fallback = java.time.LocalDate.now().toString()),
                        "counterpartyId" to v(form.src, "counterpartyId"),
                        "warehouseId" to v(form.src, "warehouseId"),
                        "paymentMethod" to v(form.src, "paymentMethod", fallback = "CASH"),
                        "lineName" to (form.src?.optJSONArray("lines")?.optJSONObject(0)?.let { KassaApi.pick(it, "name") } ?: ""),
                        "qty" to (form.src?.optJSONArray("lines")?.optJSONObject(0)?.let { KassaApi.jsonNum(it, "qty", "quantity").toString() } ?: "1"),
                        "price" to (form.src?.optJSONArray("lines")?.optJSONObject(0)?.let { KassaApi.jsonNum(it, "price").toString() } ?: "0"),
                        "vatRate" to (form.src?.optJSONArray("lines")?.optJSONObject(0)?.let { KassaApi.jsonNum(it, "vatRate").toString() } ?: "0"),
                    ),
                )
                is AccForm.Cash -> AccFields(
                    "Кассовый ордер",
                    listOf(
                        AccInput("docDate", "Дата"),
                        AccInput("direction", "Направление", "segment", listOf("IN" to "Приход", "OUT" to "Расход")),
                        AccInput("account", "Счёт", "segment", listOf("CASH" to "Касса", "BANK" to "Банк")),
                        AccInput("counterpartyId", "Контрагент", "pick"),
                        AccInput("amount", "Сумма"),
                        AccInput("purpose", "Назначение"),
                    ),
                    mapOf(
                        "docDate" to v(form.src, "docDate", fallback = java.time.LocalDate.now().toString()),
                        "direction" to v(form.src, "direction", fallback = "IN"),
                        "account" to v(form.src, "account", fallback = "CASH"),
                        "counterpartyId" to v(form.src, "counterpartyId"),
                        "amount" to v(form.src, "amount", fallback = "0"),
                        "purpose" to v(form.src, "purpose"),
                    ),
                )
                AccForm.Declaration -> AccFields(
                    "Декларация ИП (бланк №27)",
                    listOf(
                        AccInput("formCode", "Период", "segment", listOf("INCOME_TAX_IP_H1" to "1 полугодие", "INCOME_TAX_IP_H2" to "2 полугодие", "INCOME_TAX_IP_YEAR" to "Год")),
                        AccInput("year", "Год"),
                        AccInput("ratePercent", "Ставка %"),
                        AccInput("prepaid", "Аванс"),
                    ),
                    mapOf(
                        "formCode" to "INCOME_TAX_IP_H1",
                        "year" to java.time.LocalDate.now().year.toString(),
                        "ratePercent" to "10",
                        "prepaid" to "0",
                    ),
                )
                is AccForm.Employee -> AccFields(
                    if (form.src == null) "Новый сотрудник" else "Сотрудник",
                    listOf(
                        AccInput("kind", "Тип", "segment", listOf("OFFICE" to "Офис", "COURIER_LINKED" to "Курьер", "OUTSOURCE" to "Аутсорс")),
                        AccInput("fullName", "ФИО"),
                        AccInput("phone", "Телефон"),
                        AccInput("position", "Должность"),
                        AccInput("department", "Отдел"),
                        AccInput("monthlySalary", "Оклад"),
                        AccInput("cardAmountFixed", "На карту"),
                        AccInput("cashAmountFixed", "Наличные"),
                        AccInput("hiredAt", "Дата приёма"),
                        AccInput("attendanceMode", "Режим", "segment", listOf("DAILY" to "Ежедневно", "SHIFT" to "Смены")),
                        AccInput("note", "Заметка", "area"),
                    ),
                    mapOf(
                        "kind" to v(form.src, "kind", fallback = "OFFICE"),
                        "fullName" to v(form.src, "fullName"),
                        "phone" to v(form.src, "phone"),
                        "position" to v(form.src, "position"),
                        "department" to v(form.src, "department"),
                        "monthlySalary" to v(form.src, "monthlySalary", fallback = "0"),
                        "cardAmountFixed" to v(form.src, "cardAmountFixed", fallback = "0"),
                        "cashAmountFixed" to v(form.src, "cashAmountFixed", fallback = "0"),
                        "hiredAt" to v(form.src, "hiredAt", fallback = java.time.LocalDate.now().toString()),
                        "attendanceMode" to v(form.src, "attendanceMode", fallback = "DAILY"),
                        "note" to v(form.src, "note"),
                    ),
                )
                is AccForm.TimesheetDay -> AccFields(
                    "${form.name} · день ${form.day}",
                    listOf(
                        AccInput("clockInHm", "Приход ЧЧ:ММ"),
                        AccInput("clockOutHm", "Уход ЧЧ:ММ"),
                        AccInput("dayFraction", "Доля дня", "segment", listOf("1" to "1", "0.5" to "0.5", "0" to "0")),
                        AccInput("markDayOff", "Выходной", "check"),
                        AccInput("markVacation", "Отпуск", "check"),
                        AccInput("clear", "Очистить", "check"),
                    ),
                    mapOf(
                        "clockInHm" to v(form.cell, "clockInHm", fallback = "09:00"),
                        "clockOutHm" to v(form.cell, "clockOutHm", fallback = "18:00"),
                        "dayFraction" to (form.cell?.let { KassaApi.jsonNum(it, "dayFraction").toString() } ?: "1"),
                        "markDayOff" to "0",
                        "markVacation" to "0",
                        "clear" to "0",
                    ),
                )
                is AccForm.CalendarDay -> AccFields(
                    "День календаря",
                    listOf(
                        AccInput("workDate", "Дата"),
                        AccInput("kind", "Тип", "segment", listOf("WORKING" to "Рабочий", "SHORT_DAY" to "Сокр.", "WEEKEND" to "Выходной", "HOLIDAY" to "Праздник")),
                        AccInput("title", "Название"),
                    ),
                    mapOf(
                        "workDate" to v(form.src, "workDate", "date"),
                        "kind" to v(form.src, "kind", fallback = "WORKING"),
                        "title" to v(form.src, "title", "name"),
                    ),
                )
                is AccForm.PayrollLine -> AccFields(
                    "Строка ведомости",
                    listOf(
                        AccInput("accrued", "Начислено"),
                        AccInput("cardAmount", "На карту"),
                        AccInput("cashAmount", "Наличные"),
                    ),
                    mapOf(
                        "accrued" to KassaApi.jsonNum(form.line, "accrued").toString(),
                        "cardAmount" to KassaApi.jsonNum(form.line, "cardAmount").toString(),
                        "cashAmount" to KassaApi.jsonNum(form.line, "cashAmount").toString(),
                    ),
                )
                AccForm.Hr -> AccFields(
                    "HR-событие",
                    listOf(
                        AccInput("employeeId", "Сотрудник", "pick"),
                        AccInput("type", "Тип", "segment", listOf("HIRE" to "Приём", "VACATION" to "Отпуск", "SICK_LEAVE" to "Больничный", "TERMINATION" to "Увольнение", "TRANSFER" to "Перевод")),
                        AccInput("startDate", "С даты"),
                        AccInput("endDate", "По дату"),
                        AccInput("comment", "Комментарий", "area"),
                    ),
                    mapOf(
                        "employeeId" to "",
                        "type" to "VACATION",
                        "startDate" to java.time.LocalDate.now().toString(),
                        "endDate" to "",
                        "comment" to "",
                    ),
                )
                is AccForm.Recon -> AccFields(
                    "Реквизиты акта сверки",
                    listOf(
                        AccInput("actCode", "Код акта"),
                        AccInput("contractNumber", "Договор"),
                        AccInput("ink", "ИНК"),
                        AccInput("legalAddress", "Юр. адрес"),
                        AccInput("bankName", "Банк"),
                        AccInput("bankAccount", "Счёт"),
                        AccInput("bankMfo", "МФО"),
                        AccInput("bankCorrAccount", "Корр. счёт"),
                        AccInput("email", "Email"),
                        AccInput("signatoryTitle", "Должность подписанта"),
                        AccInput("directorFullName", "Директор (полностью)"),
                        AccInput("directorShortName", "Директор (кратко)"),
                    ),
                    listOf("actCode", "contractNumber", "ink", "legalAddress", "bankName", "bankAccount", "bankMfo", "bankCorrAccount", "email", "signatoryTitle", "directorFullName", "directorShortName")
                        .associateWith { v(form.src, it, fallback = if (it == "signatoryTitle") "Директор" else "") },
                )
            }
        }
    }
}

private fun persistAccForm(api: KassaApi, token: String, form: AccForm, state: AccFields, orgId: String) {
    fun s(key: String) = state.values[key].orEmpty().trim()
    fun n(key: String) = KassaApi.parseMoneyInput(s(key)) ?: 0.0
    fun b(key: String) = s(key) == "1"
    when (form) {
        is AccForm.Profile -> {
            val body = JSONObject()
            listOf("fullName", "regNo", "phone", "address", "taxOffice", "taxId", "taxRegisteredAt", "pensionFund", "pensionCode", "pensionRegisteredAt", "taxRegime").forEach {
                body.put(it, s(it))
            }
            body.put("hasEmployees", b("hasEmployees"))
            body.put("hasBankAccount", b("hasBankAccount"))
            val codes = form.src.optJSONArray("activityCodes") ?: JSONArray()
            body.put("activityCodes", codes)
            api.putJson("/accounting/registration/profile", token, body)
        }
        is AccForm.Counterparty -> {
            val body = JSONObject()
                .put("kind", s("kind"))
                .put("name", s("name"))
                .put("taxId", s("taxId"))
                .put("regNo", s("regNo"))
                .put("address", s("address"))
                .put("phone", s("phone"))
                .put("email", s("email"))
                .put("note", s("note"))
            val id = form.src?.let { KassaApi.pick(it, "id") }.orEmpty()
            if (id.isBlank()) api.postJson("/accounting/counterparties", token, body)
            else api.patchJson("/accounting/counterparties/$id", token, body)
        }
        is AccForm.Item -> {
            val body = JSONObject()
                .put("kind", s("kind"))
                .put("sku", s("sku"))
                .put("name", s("name"))
                .put("unitCode", s("unitCode").ifBlank { "шт" })
                .put("vatRate", n("vatRate"))
                .put("salePrice", n("salePrice"))
            val id = form.src?.let { KassaApi.pick(it, "id") }.orEmpty()
            if (id.isBlank()) api.postJson("/accounting/items", token, body)
            else api.patchJson("/accounting/items/$id", token, body)
        }
        is AccForm.Warehouse -> {
            val mol = s("responsiblePersonName")
            val body = JSONObject()
                .put("name", s("name"))
                .put("isActive", b("isActive"))
            if (mol.isBlank()) body.put("responsiblePersonName", JSONObject.NULL) else body.put("responsiblePersonName", mol)
            if (orgId.isNotBlank()) body.put("organizationId", orgId)
            val id = form.src?.let { KassaApi.pick(it, "id") }.orEmpty()
            if (id.isBlank()) api.postJson("/accounting/warehouses", token, body)
            else api.patchJson("/accounting/warehouses/$id", token, body)
        }
        is AccForm.Invoice -> {
            val line = JSONObject()
                .put("name", s("lineName").ifBlank { "Позиция" })
                .put("unitCode", "шт")
                .put("qty", n("qty").coerceAtLeast(1.0))
                .put("price", n("price"))
                .put("vatRate", n("vatRate"))
            val body = JSONObject()
                .put("docDate", s("docDate"))
                .put("counterpartyId", s("counterpartyId"))
                .put("paymentMethod", s("paymentMethod"))
                .put("lines", JSONArray().put(line))
            if (s("warehouseId").isNotBlank()) body.put("warehouseId", s("warehouseId"))
            val base = if (form.mode == "sales") "/accounting/sales-invoices" else "/accounting/purchase-invoices"
            val id = form.src?.let { KassaApi.pick(it, "id") }.orEmpty()
            if (id.isBlank()) api.postJson(base, token, body) else api.patchJson("$base/$id", token, body)
        }
        is AccForm.Cash -> {
            val body = JSONObject()
                .put("docDate", s("docDate"))
                .put("direction", s("direction"))
                .put("account", s("account"))
                .put("amount", n("amount"))
            if (s("counterpartyId").isNotBlank()) body.put("counterpartyId", s("counterpartyId"))
            if (s("purpose").isNotBlank()) body.put("purpose", s("purpose"))
            val id = form.src?.let { KassaApi.pick(it, "id") }.orEmpty()
            if (id.isBlank()) api.postJson("/accounting/cash-orders", token, body)
            else api.patchJson("/accounting/cash-orders/$id", token, body)
        }
        AccForm.Declaration -> {
            api.postJson(
                "/accounting/tax-reports/build-ip-income",
                token,
                JSONObject()
                    .put("formCode", s("formCode"))
                    .put("year", s("year").toIntOrNull() ?: java.time.LocalDate.now().year)
                    .put("ratePercent", n("ratePercent"))
                    .put("prepaid", n("prepaid")),
            )
        }
        is AccForm.Employee -> {
            val body = JSONObject()
                .put("kind", s("kind"))
                .put("attendanceMode", s("attendanceMode"))
                .put("fullName", s("fullName"))
                .put("phone", s("phone"))
                .put("position", s("position"))
                .put("department", s("department"))
                .put("monthlySalary", n("monthlySalary"))
                .put("cardAmountFixed", n("cardAmountFixed"))
                .put("cashAmountFixed", n("cashAmountFixed"))
                .put("hiredAt", s("hiredAt"))
                .put("note", s("note"))
            val id = form.src?.let { KassaApi.pick(it, "id") }.orEmpty()
            if (id.isBlank()) api.postJson("/accounting/employees", token, body)
            else api.patchJson("/accounting/employees/$id", token, body)
        }
        is AccForm.TimesheetDay -> {
            val date = "${form.month}-${form.day.toString().padStart(2, '0')}"
            val body = JSONObject().put("employeeId", form.employeeId).put("workDate", date)
            when {
                b("clear") -> body.put("clear", true)
                b("markVacation") -> body.put("markVacation", true)
                b("markDayOff") -> body.put("markDayOff", true)
                else -> {
                    body.put("clockInHm", s("clockInHm"))
                    if (s("clockOutHm").isNotBlank()) body.put("clockOutHm", s("clockOutHm"))
                    body.put("dayFraction", n("dayFraction"))
                }
            }
            api.postJson("/accounting/attendance/admin-day", token, body)
        }
        is AccForm.CalendarDay -> {
            val body = JSONObject().put("workDate", s("workDate")).put("kind", s("kind"))
            if (s("title").isNotBlank()) body.put("title", s("title"))
            api.postJson("/accounting/calendar/days", token, body)
        }
        is AccForm.PayrollLine -> {
            val lineId = KassaApi.pick(form.line, "id")
            api.patchJson(
                "/accounting/payroll-runs/${form.runId}/lines/$lineId",
                token,
                JSONObject().put("accrued", n("accrued")).put("cardAmount", n("cardAmount")).put("cashAmount", n("cashAmount")),
            )
        }
        AccForm.Hr -> {
            val body = JSONObject()
                .put("employeeId", s("employeeId"))
                .put("type", s("type"))
                .put("startDate", s("startDate"))
            if (s("endDate").isNotBlank()) body.put("endDate", s("endDate"))
            if (s("comment").isNotBlank()) body.put("comment", s("comment"))
            api.postJson("/accounting/hr-events", token, body)
        }
        is AccForm.Recon -> {
            val id = KassaApi.pick(form.src, "establishmentId", "id")
            val body = JSONObject()
            listOf("actCode", "contractNumber", "ink", "legalAddress", "bankName", "bankAccount", "bankMfo", "bankCorrAccount", "email", "signatoryTitle", "directorFullName", "directorShortName").forEach {
                body.put(it, s(it))
            }
            api.putJson("/reconciliation-acts/profiles/$id", token, body)
        }
    }
}

@Composable
private fun AccPrimary(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
private fun AccChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accentSoft).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

@Composable
private fun AccInfoCard(pairs: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pairs.filter { it.second.isNotBlank() }.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(k, color = AtColors.muted, fontSize = 13.sp)
                Text(v, color = AtColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun AccEntityCard(
    title: String,
    meta: String = "",
    status: String = "",
    onOpen: (() -> Unit)? = null,
    actions: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Column(
        Modifier.fillMaxWidth().atCard(14.dp).then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AtColors.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (status.isNotBlank()) StatusChip(status)
        }
        if (meta.isNotBlank()) Text(meta, color = AtColors.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (actions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { (label, click) -> InlineActionChip(label, onClick = click) }
            }
        }
    }
}
