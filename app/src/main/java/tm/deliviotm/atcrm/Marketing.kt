package tm.deliviotm.atcrm

import android.content.Context
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val MARKETING_TABS = listOf(
    "clients" to "Клиенты",
    "portraits" to "Портреты",
    "sms" to "SMS",
    "push" to "Push",
    "calendar" to "Календарь пушей",
    "content" to "Контент",
    "banners" to "Баннеры",
    "ads" to "Реклама",
    "promos" to "Промокоды",
    "qr" to "QR",
    "messengers" to "Мессенджеры",
)

private fun marketingHint(tab: String): String = when (tab) {
    "clients" -> "База клиентов: поиск, город, звонок"
    "portraits" -> "Портреты и инсайты по клиентам"
    "sms" -> "Рассылки маркетинга через шлюз Yeastar"
    "push" -> "Журнал отправленных пушей"
    "calendar" -> "Календарь пуш-кампаний"
    "content" -> "Контент-план публикаций"
    "banners" -> "Баннеры на подключённых заведениях"
    "ads" -> "Рекламные размещения заведений"
    "promos" -> "Статистика использования промокодов. Вкл/выкл — только в приложении, не в CRM."
    "qr" -> "QR-коды и ссылки"
    "messengers" -> "Telegram-бот поддержки"
    else -> "Клиенты, рассылки, промокоды, контент"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketingWorkspacePane(
    api: KassaApi,
    token: String?,
    initialTab: String = "clients",
    onBack: () -> Unit,
    onOpen: (JsonRow, ModuleSpec) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    var tab by remember { mutableStateOf(if (initialTab in MARKETING_TABS.map { it.first }) initialTab else "clients") }
    var smsSeg by remember { mutableStateOf("monitor") }
    var city by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var groups by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var selectedGroups by remember { mutableStateOf(setOf<String>()) }
    var smsText by remember { mutableStateOf("") }
    var smsPhones by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var groupPhones by remember { mutableStateOf("") }
    var clientPhones by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var selectedPromo by remember { mutableStateOf<JsonRow?>(null) }
    var smsBusy by remember { mutableStateOf(false) }

    LaunchedEffect(tab) { selectedPromo = null }

    fun pathForTab(): String = when (tab) {
        "portraits" -> "/reports/client-portraits"
        "push" -> "/push/logs?limit=50"
        "calendar" -> "/marketing/push-calendar"
        "content" -> "/marketing/content-plan"
        "banners" -> "/marketing/banner-calendar/connected-establishments"
        "ads" -> "/marketing/ads/establishments"
        "promos" -> "/marketing/promo-codes"
        "qr" -> "/qr"
        else -> "/clients"
    }

    LaunchedEffect(token, tab, tick) {
        val t = token ?: return@LaunchedEffect
        if (tab == "messengers") {
            loading = false
            return@LaunchedEffect
        }
        if (rows.isNotEmpty()) refreshing = true else loading = true
        err = null
        try {
            if (tab == "sms") {
                val bcObj = withContext(Dispatchers.IO) { api.getObject("/sms/broadcasts", t) }
                val all = KassaApi.pagedRows(bcObj).items.ifEmpty {
                    withContext(Dispatchers.IO) { api.getRows("/sms/broadcasts", t) }
                }
                val filtered = all.filter {
                    val d = KassaApi.pick(it.raw, "department").uppercase()
                    d.isBlank() || d == "MARKETING"
                }
                rows = if (filtered.isNotEmpty()) filtered else all
                incoming = withContext(Dispatchers.IO) {
                    KassaApi.pagedRows(api.getObject("/sms/incoming?take=50&skip=0", t)).items
                }
                groups = withContext(Dispatchers.IO) {
                    KassaApi.pagedRows(api.getObject("/sms/recipient-groups", t)).items.filter {
                        val d = KassaApi.pick(it.raw, "department").uppercase()
                        d.isBlank() || d == "MARKETING"
                    }
                }
                clientPhones = withContext(Dispatchers.IO) {
                    runCatching { api.getRows("/clients", t) }.getOrDefault(emptyList())
                        .map { KassaApi.phoneOf(it.raw) }
                        .filter { it.length >= 8 }
                        .distinct()
                        .sorted()
                }
            } else {
                var path = pathForTab()
                val query = q.trim()
                if (tab == "clients") {
                    val parts = mutableListOf<String>()
                    if (query.isNotBlank()) parts += "search=${java.net.URLEncoder.encode(query, "UTF-8")}"
                    if (city in listOf("ashgabat", "mary")) parts += "cityKey=$city"
                    path = if (parts.isEmpty()) "/clients" else "/clients?${parts.joinToString("&")}"
                }
                rows = withContext(Dispatchers.IO) { api.getRows(path, t) }
            }
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            loading = false
            refreshing = false
        }
    }

    val listRows = when {
        tab == "sms" && smsSeg == "incoming" -> incoming
        tab == "sms" && smsSeg == "groups" -> groups
        tab == "sms" && smsSeg == "pool" -> emptyList()
        else -> rows.filter { row ->
            marketingCityMatch(row, city) && marketingSearchMatch(row, q)
        }
    }
    val withPhone = rows.count { KassaApi.phoneOf(it.raw).isNotBlank() }
    val orders = rows.sumOf { KassaApi.jsonNum(it.raw, "ordersCount", "orderCount", "orders", "totalOrders") }

    fun specFor(row: JsonRow): ModuleSpec = when (tab) {
        "portraits" -> ModuleSpec("marketing", "Портреты", "/reports/client-portraits")
        "sms" -> ModuleSpec("marketing", "SMS", "/sms/broadcasts")
        "push" -> ModuleSpec("marketing", "Push", "/push/logs")
        "calendar" -> ModuleSpec("marketing", "Календарь пушей", "/marketing/push-calendar")
        "content" -> ModuleSpec("marketing", "Контент", "/marketing/content-plan")
        "banners" -> ModuleSpec("marketing", "Баннеры", "/marketing/banner-calendar/connected-establishments")
        "ads" -> ModuleSpec("marketing", "Реклама", "/marketing/ads/establishments")
        "promos" -> ModuleSpec("marketing", "Промокоды", "/marketing/promo-codes")
        "qr" -> ModuleSpec("marketing", "QR", "/qr")
        else -> ModuleSpec("marketing", "Клиенты", "/clients")
    }

    fun runSms(ok: String, block: suspend () -> Unit) {
        val t = token ?: return
        smsBusy = true
        err = null
        msg = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                msg = ok
                tick++
            } catch (e: Exception) {
                err = e.message
            } finally {
                smsBusy = false
            }
        }
    }

    fun dial(phone: String) {
        val digits = phone.filter { it.isDigit() || it == '+' }
        if (digits.isBlank()) return
        DeviceIntents.dial(ctx, digits)
        msg = "Набор на телефоне"
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Маркетинг", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SiteSectionHead("Маркетинг", marketingHint(tab)) }
                item {
                    SiteFilterSelect(
                        value = tab,
                        items = MARKETING_TABS,
                        onChange = { tab = it; q = ""; err = null; msg = null },
                        label = "Раздел",
                    )
                }
                if (tab == "clients") {
                    item {
                        SiteFilterSelect(
                            value = city,
                            items = listOf("" to "Все города", "ashgabat" to "Ашхабад", "mary" to "Мары"),
                            onChange = { city = it },
                            label = "Город",
                        )
                    }
                    item {
                        OutlinedTextField(
                            q, { q = it },
                            placeholder = { Text("ID, имя, email, телефон…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                        )
                    }
                } else if (tab != "sms" && tab != "messengers") {
                    item {
                        OutlinedTextField(
                            q, { q = it },
                            placeholder = { Text(marketingSearchHint(tab)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                        )
                    }
                }
                if (tab == "sms") {
                    item {
                        SiteFilterSelect(
                            value = smsSeg,
                            items = listOf(
                                "monitor" to "Рассылки",
                                "incoming" to "Ответы",
                                "groups" to "Группы",
                                "pool" to "Пул номеров",
                            ),
                            onChange = { smsSeg = it },
                            label = "SMS",
                        )
                    }
                    if (smsSeg == "monitor") {
                        item {
                            MarketingPrimaryBtn("Составить рассылку") { smsSeg = "groups" }
                        }
                    }
                    if (smsSeg == "pool" || smsSeg == "groups") {
                        item {
                            OutlinedTextField(
                                smsText, { smsText = it },
                                label = { Text("Текст SMS — как на сайте") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(),
                            )
                        }
                    }
                    if (smsSeg == "groups") {
                        item { Text("Новая группа", color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        item {
                            OutlinedTextField(
                                groupName, { groupName = it.take(120) },
                                label = { Text("Название группы") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(),
                            )
                        }
                        item {
                            OutlinedTextField(
                                groupPhones, { groupPhones = it },
                                label = { Text("Номера (по одному в строке)") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(),
                            )
                        }
                        if (clientPhones.isNotEmpty()) {
                            item {
                                Text(
                                    "Подставить номера клиентов (${clientPhones.size})",
                                    color = AtColors.accent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable { groupPhones = clientPhones.joinToString("\n") },
                                )
                            }
                        }
                        item {
                            MarketingPrimaryBtn(if (smsBusy) "Создание…" else "Создать группу", enabled = !smsBusy) {
                                val name = groupName.trim()
                                val phones = groupPhones.trim()
                                if (name.isBlank()) {
                                    err = "Укажите название группы"
                                    return@MarketingPrimaryBtn
                                }
                                if (phones.isBlank()) {
                                    err = "Вставьте номера для группы"
                                    return@MarketingPrimaryBtn
                                }
                                runSms("Группа создана") {
                                    val created = api.postJson(
                                        "/sms/recipient-groups",
                                        token!!,
                                        JSONObject().put("name", name).put("department", "MARKETING"),
                                    )
                                    val id = KassaApi.pick(created, "id").ifBlank {
                                        KassaApi.pick(created.optJSONObject("item") ?: JSONObject(), "id")
                                    }
                                    if (id.isNotBlank()) {
                                        api.postJson(
                                            "/sms/recipient-groups/$id/members/import-phones",
                                            token,
                                            JSONObject().put("text", phones).put("clearExisting", false),
                                        )
                                    }
                                }
                                groupName = ""
                                groupPhones = ""
                            }
                        }
                    }
                    if (smsSeg == "pool") {
                        item {
                            OutlinedTextField(
                                smsPhones, { smsPhones = it },
                                label = { Text("Пул номеров (по одному в строке)") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(),
                            )
                        }
                        if (clientPhones.isNotEmpty()) {
                            item {
                                Text(
                                    "Подставить номера клиентов (${clientPhones.size})",
                                    color = AtColors.accent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable { smsPhones = clientPhones.joinToString("\n") },
                                )
                            }
                        }
                        item {
                            MarketingPrimaryBtn(if (smsBusy) "Отправка…" else "Отправить по пулу", enabled = !smsBusy) {
                                if (smsText.isBlank() || smsPhones.isBlank()) {
                                    err = "Нужны текст и номера"
                                    return@MarketingPrimaryBtn
                                }
                                runSms("Рассылка запущена") {
                                    val body = JSONObject()
                                        .put("message", smsText.trim())
                                        .put("audienceMode", "ESTABLISHMENTS")
                                        .put("department", "MARKETING")
                                        .put("phonesText", smsPhones.trim())
                                    val created = api.postJson("/sms/broadcasts", token!!, body)
                                    val id = KassaApi.pick(created, "id").ifBlank {
                                        KassaApi.pick(created.optJSONObject("item") ?: JSONObject(), "id")
                                    }
                                    if (id.isNotBlank()) api.postJson("/sms/broadcasts/$id/start", token, JSONObject())
                                }
                            }
                        }
                    }
                    if (smsSeg == "groups") {
                        item {
                            MarketingPrimaryBtn(if (smsBusy) "Отправка…" else "Отправить в выбранные группы", enabled = !smsBusy) {
                                if (smsText.isBlank()) {
                                    err = "Введите текст SMS"
                                    return@MarketingPrimaryBtn
                                }
                                if (selectedGroups.isEmpty()) {
                                    err = "Отметьте группу"
                                    return@MarketingPrimaryBtn
                                }
                                runSms("Рассылка запущена") {
                                    val arr = JSONArray()
                                    selectedGroups.forEach { arr.put(it) }
                                    val body = JSONObject()
                                        .put("message", smsText.trim())
                                        .put("audienceMode", "GROUPS")
                                        .put("groupIds", arr)
                                        .put("runMode", "CONCURRENT")
                                        .put("department", "MARKETING")
                                    val created = api.postJson("/sms/broadcasts", token!!, body)
                                    val id = KassaApi.pick(created, "id").ifBlank {
                                        KassaApi.pick(created.optJSONObject("item") ?: JSONObject(), "id")
                                    }
                                    if (id.isNotBlank()) api.postJson("/sms/broadcasts/$id/start", token, JSONObject())
                                }
                            }
                        }
                    }
                }
                if (err != null) item { ActionBanner(err!!, error = true) }
                if (msg != null) item { ActionBanner(msg!!, error = false) }
                if (tab == "clients") {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MarketingKpi("всего", KassaApi.prettyNumber(rows.size.toString()), Modifier.weight(1f))
                            MarketingKpi("с телефоном", KassaApi.prettyNumber(withPhone.toString()), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MarketingKpi("в списке", KassaApi.prettyNumber(listRows.size.toString()), Modifier.weight(1f))
                            MarketingKpi("заказов", KassaApi.prettyNumber(orders.toString()), Modifier.weight(1f))
                        }
                    }
                }
                if (tab == "messengers") {
                    item { MarketingMessengersBlock(api = api, token = token, tick = tick) }
                } else {
                    if (loading && rows.isEmpty() && tab != "sms") item { LoadingCard() }
                    if (tab == "sms" && loading && rows.isEmpty() && incoming.isEmpty()) item { LoadingCard() }
                    if (!loading && listRows.isEmpty() && err == null && tab != "messengers" && !(tab == "sms" && smsSeg == "pool")) {
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp)) {
                                Text(marketingEmpty(tab, smsSeg), color = AtColors.text, fontWeight = FontWeight.SemiBold)
                                Text("Смените фильтр или обновите экран.", color = AtColors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                    if (tab == "sms" && smsSeg == "incoming") {
                        items(listRows, key = { "in-${it.id}" }) { row ->
                            SmsBubbleCard(row = row, incoming = true)
                        }
                    } else if (tab == "sms" && smsSeg == "monitor") {
                        items(listRows, key = { it.id }) { row ->
                            BroadcastCard(row) { label, path ->
                                runSms(label) { api.postJson(path, token!!, JSONObject()) }
                            }
                        }
                    } else if (tab != "messengers") {
                        items(listRows, key = { it.id }) { row ->
                            MarketingRow(
                                row = row,
                                tab = tab,
                                smsSeg = smsSeg,
                                selected = selectedGroups.contains(row.id),
                                onOpen = {
                                    if (tab == "promos") selectedPromo = row
                                    else onOpen(row, specFor(row))
                                },
                                onToggleGroup = {
                                    selectedGroups = if (selectedGroups.contains(row.id)) selectedGroups - row.id else selectedGroups + row.id
                                },
                                onDeleteGroup = if (tab == "sms" && smsSeg == "groups") {
                                    {
                                        runSms("Группа удалена") { api.deletePath("/sms/recipient-groups/${row.id}", token!!) }
                                        selectedGroups = selectedGroups - row.id
                                    }
                                } else {
                                    null
                                },
                                onDial = { dial(it) },
                            )
                        }
                    }
                }
            }
        }
    }
    selectedPromo?.let { promo ->
        MarketingPromoStatsOverlay(
            api = api,
            token = token,
            row = promo,
            onClose = { selectedPromo = null },
        )
    }
}

@Composable
private fun MarketingRow(
    row: JsonRow,
    tab: String,
    smsSeg: String,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleGroup: () -> Unit,
    onDeleteGroup: (() -> Unit)? = null,
    onDial: (String) -> Unit,
) {
    val title = when (tab) {
        "clients" -> marketingClientName(row).ifBlank { KassaApi.phoneOf(row.raw).ifBlank { row.title } }
        "promos" -> KassaApi.pick(row.raw, "code", "promoCode", "title").ifBlank { row.title }
        "sms" -> KassaApi.pick(row.raw, "name", "title").ifBlank { row.title }
        else -> KassaApi.pick(row.raw, "fullName", "name", "clientName", "title").ifBlank { row.title }
    }
    val phone = if (tab == "clients") marketingClientPhone(row) else KassaApi.phoneOf(row.raw)
    val avatar = title.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString()
        ?: if (phone.isNotBlank()) "☎" else "•"
    val city = when (KassaApi.pick(row.raw, "cityKey").lowercase()) {
        "ashgabat" -> "Ашхабад"
        "mary" -> "Мары"
        else -> ""
    }
    val orders = KassaApi.jsonNum(row.raw, "ordersCount", "orderCount", "orders", "totalOrders")
    val meta = when (tab) {
        "clients" -> listOf(phone, city, if (orders > 0) "${KassaApi.prettyNumber(orders.toString())} зак." else "")
        "portraits" -> listOf(
            KassaApi.pick(row.raw, "kind", "segment", "type"),
            KassaApi.pick(row.raw, "score", "priority", "impact"),
        )
        "sms" -> listOf(
            if (selected) "выбрана" else "",
            "участников: ${row.raw.optInt("memberCount", row.raw.optInt("count", 0))}",
        )
        "push", "calendar" -> listOf(
            prettyStatus(KassaApi.pick(row.raw, "status", "state")),
            KassaApi.pick(row.raw, "channel", "campaign", "campaignName"),
            KassaApi.prettyTime(KassaApi.pick(row.raw, "scheduledAt", "sentAt", "createdAt")),
        )
        "content" -> listOf(
            KassaApi.pick(row.raw, "channel", "platform", "kind"),
            prettyStatus(KassaApi.pick(row.raw, "status", "state")),
            KassaApi.prettyTime(KassaApi.pick(row.raw, "scheduledAt", "publishAt", "createdAt")),
        )
        "banners", "ads" -> listOf(
            city,
            prettyStatus(KassaApi.pick(row.raw, "status", "state", "connectionStatus")),
            KassaApi.pick(row.raw, "kind", "type"),
        )
        "promos" -> marketingPromoUsageParts(row.raw)
        "qr" -> listOf(
            KassaApi.pick(row.raw, "url", "link", "target", "payload"),
            prettyStatus(KassaApi.pick(row.raw, "status", "kind")),
        )
        else -> listOf(row.subtitle)
    }.filter { it.isNotBlank() }.joinToString(" · ")
    val chip = when (tab) {
        "clients" -> prettyStatus(KassaApi.pick(row.raw, "status", "segment", "kind"))
        "portraits" -> KassaApi.pick(row.raw, "score", "kind", "priority")
        "promos" -> marketingPromoUses(row.raw)?.let { "${KassaApi.prettyNumber(it.toString())} исп." } ?: "статистика"
        else -> prettyStatus(KassaApi.pick(row.raw, "status", "state"))
    }
    val body = if (tab == "portraits") {
        KassaApi.pick(row.raw, "text", "body", "recommendation", "insight", "description", "message")
    } else {
        ""
    }
    Column(Modifier.fillMaxWidth().atCard(16.dp).clickable(onClick = onOpen).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (tab == "portraits") "✦" else avatar,
                    color = AtColors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) Text(meta, color = AtColors.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (chip.isNotBlank()) {
                Text(chip, color = AtColors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (body.isNotBlank()) {
            Text(body, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (tab == "clients" && phone.isNotBlank()) {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AtColors.accent)
                    .clickable { onDial(phone) },
                contentAlignment = Alignment.Center,
            ) {
                Text("Позвонить · $phone", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        if (tab == "sms" && smsSeg == "groups") {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (selected) "Снять" else "Выбрать",
                    color = AtColors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onToggleGroup() },
                )
                if (onDeleteGroup != null) {
                    Text(
                        "Удалить",
                        color = AtColors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onDeleteGroup() },
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketingKpi(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.atCard(14.dp).padding(12.dp)) {
        Text(label, color = AtColors.muted, fontSize = 12.sp)
        Text(value.ifBlank { "0" }, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun MarketingPrimaryBtn(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) AtColors.accent else AtColors.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MarketingMessengersBlock(api: KassaApi, token: String?, tick: Int) {
    val scope = rememberCoroutineScope()
    var obj by remember { mutableStateOf<JSONObject?>(null) }
    var botToken by remember { mutableStateOf("") }
    var webhook by remember { mutableStateOf("") }
    var broadcast by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        err = null
        try {
            val o = withContext(Dispatchers.IO) { api.getObject("/support/admin/telegram", t) }
            obj = o
            webhook = KassaApi.pick(o, "webhookUrl").ifBlank { webhook }
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка"
        }
    }

    val o = obj
    val configured = o?.optBoolean("tokenConfigured", false) == true
    val preview = o?.let { KassaApi.pick(it, "tokenPreview") }.orEmpty()
    val pending = o?.opt("pendingUpdateCount")?.toString().orEmpty()
    val lastErr = o?.let { KassaApi.pick(it, "lastError") }.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (err != null) ActionBanner(err!!, error = true)
        if (msg != null) ActionBanner(msg!!, error = false)
        if (!loaded && err == null) LoadingCard()
        Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Telegram", color = AtColors.text, fontWeight = FontWeight.Bold)
            Text(
                "Токен: ${if (configured) preview.ifBlank { "настроен" } else "не настроен"}" +
                    (if (pending.isNotBlank() && pending != "null") " · ожидает обновлений: $pending" else "") +
                    (if (lastErr.isNotBlank()) " · ошибка: $lastErr" else ""),
                color = AtColors.muted,
                fontSize = 13.sp,
            )
            OutlinedTextField(
                botToken, { botToken = it },
                label = { Text("Токен бота") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            MarketingPrimaryBtn("Сохранить токен", enabled = !busy && botToken.isNotBlank()) {
                val t = token ?: return@MarketingPrimaryBtn
                busy = true
                err = null
                msg = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            api.postJson("/support/admin/telegram/set-token", t, JSONObject().put("token", botToken.trim()))
                        }
                        botToken = ""
                        msg = "Токен сохранён."
                    } catch (e: Exception) {
                        err = e.message
                    } finally {
                        busy = false
                    }
                }
            }
            OutlinedTextField(webhook, { webhook = it }, label = { Text("Webhook URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Text(
                "Обновить webhook",
                color = AtColors.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    val t = token ?: return@clickable
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                api.postJson("/support/admin/telegram/set-webhook", t, JSONObject().put("webhookUrl", webhook.trim()))
                            }
                            msg = "Webhook обновлён."
                        } catch (e: Exception) {
                            err = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            )
            OutlinedTextField(broadcast, { broadcast = it }, label = { Text("Рассылка во все диалоги") }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            Text(
                "Отправить рассылку",
                color = AtColors.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    val t = token ?: return@clickable
                    if (broadcast.isBlank()) return@clickable
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                api.postJson("/support/admin/telegram/broadcast", t, JSONObject().put("text", broadcast.trim()))
                            }
                            broadcast = ""
                            msg = "Рассылка отправлена."
                        } catch (e: Exception) {
                            err = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun MarketingPromoStatsOverlay(
    api: KassaApi,
    token: String?,
    row: JsonRow,
    onClose: () -> Unit,
) {
    var obj by remember { mutableStateOf(marketingUnwrapPromo(row.raw)) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(row.id, token) {
        val t = token
        if (t.isNullOrBlank() || row.id.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        err = null
        try {
            val merged = withContext(Dispatchers.IO) { marketingLoadPromoStats(api, t, row) }
            obj = merged
        } catch (e: Exception) {
            err = e.message
        } finally {
            loading = false
        }
    }
    val code = KassaApi.pick(obj, "code", "promoCode", "title", "name").ifBlank { row.title }
    val events = marketingPromoEvents(obj)
    val uses = marketingPromoUses(obj) ?: events.size.takeIf { it > 0 }?.toDouble()
    val unique = KassaApi.jsonNumOpt(obj, "uniqueClients", "uniqueUsers", "clientsCount", "uniqueCount", "distinctClients")
    val orders = KassaApi.jsonNumOpt(obj, "ordersCount", "orderCount", "orders", "totalOrders")
    val money = KassaApi.jsonNumOpt(obj, "totalDiscount", "discountSum", "savedAmount", "totalAmount")
    val lastRaw = KassaApi.pick(obj, "lastUsedAt", "lastUsed", "lastAppliedAt")
    val last = lastRaw.ifBlank {
        events.firstOrNull()?.let {
            KassaApi.prettyTime(KassaApi.pick(it, "usedAt", "appliedAt", "createdAt", "orderDatetime"))
        }.orEmpty()
    }.let { if (it.isBlank()) "" else KassaApi.prettyTime(it).ifBlank { it } }
    val pairs = marketingPromoPairs(obj)
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine(code.ifBlank { "Промокод" }, onClose)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (err != null) item { ActionBanner(err!!, error = true) }
            if (loading) item { LoadingCard() }
            item {
                Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(code, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(
                        "Статистика использования. Вкл/выкл — только в приложении, не в CRM.",
                        color = AtColors.muted,
                        fontSize = 13.sp,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarketingKpi("использований", uses?.let { KassaApi.prettyNumber(it.toString()) } ?: "—", Modifier.weight(1f))
                    MarketingKpi("уник. клиентов", unique?.let { KassaApi.prettyNumber(it.toString()) } ?: "—", Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarketingKpi("заказов", orders?.let { KassaApi.prettyNumber(it.toString()) } ?: "—", Modifier.weight(1f))
                    MarketingKpi("сумма скидок", money?.let { KassaApi.tmt(it) } ?: "—", Modifier.weight(1f))
                }
            }
            if (last.isNotBlank()) {
                item { Text("Последнее использование: $last", color = AtColors.muted, fontSize = 13.sp) }
            }
            if (pairs.isEmpty() && events.isEmpty() && !loading) {
                item {
                    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Статистики нет", color = AtColors.text, fontWeight = FontWeight.Bold)
                        Text(
                            "На сайте здесь те же цифры: сколько раз применили, сколько клиентов и заказов, сумма скидок.",
                            color = AtColors.muted,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else if (pairs.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Данные", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        pairs.forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(label, color = AtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.42f))
                                Text(value, color = AtColors.text, fontSize = 14.sp, modifier = Modifier.weight(0.58f))
                            }
                        }
                    }
                }
            }
            if (events.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Использования", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        events.forEach { item ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AtColors.bgDeep).padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(marketingPromoEventTitle(item), color = AtColors.text, fontWeight = FontWeight.SemiBold)
                                val meta = marketingPromoEventMeta(item)
                                if (meta.isNotBlank()) Text(meta, color = AtColors.muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun marketingLooksLikeName(raw: String): Boolean {
    val s = raw.trim()
    if (s.isBlank() || s.startsWith("+")) return false
    return s.any { it.isLetter() }
}

private fun marketingClientName(row: JsonRow): String {
    val first = KassaApi.pick(row.raw, "fullName", "name", "clientName", "firstName")
    val last = KassaApi.pick(row.raw, "lastName", "surname")
    val combined = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ")
    if (marketingLooksLikeName(combined)) return combined
    row.raw.optJSONObject("client")?.let { nested ->
        val n = listOf(
            KassaApi.pick(nested, "fullName", "name", "firstName"),
            KassaApi.pick(nested, "lastName"),
        ).filter { it.isNotBlank() }.joinToString(" ")
        if (marketingLooksLikeName(n)) return n
    }
    return ""
}

private fun marketingClientPhone(row: JsonRow): String {
    val direct = KassaApi.phoneOf(row.raw)
    if (direct.isNotBlank()) return direct
    val nested = row.raw.optJSONObject("client") ?: return ""
    return KassaApi.phoneOf(nested)
}

private fun marketingCityMatch(row: JsonRow, city: String): Boolean {
    if (city.isBlank()) return true
    val key = KassaApi.pick(row.raw, "cityKey").trim().lowercase()
    if (key.isBlank()) return true
    return key == city
}

private fun marketingSearchMatch(row: JsonRow, q: String): Boolean {
    val needle = q.trim()
    if (needle.isBlank()) return true
    val blob = listOf(
        row.title, row.subtitle, row.id,
        KassaApi.pick(row.raw, "fullName", "name", "clientName", "email", "phone", "cityKey", "code", "promoCode", "title", "message"),
    ).joinToString(" ")
    return blob.contains(needle, ignoreCase = true)
}

private fun marketingSearchHint(tab: String): String = when (tab) {
    "portraits" -> "Инсайт, сегмент…"
    "push", "calendar" -> "Кампания, статус…"
    "content" -> "Публикация, канал…"
    "banners", "ads" -> "Заведение…"
    "promos" -> "Код, использования…"
    "qr" -> "QR, ссылка…"
    else -> "Поиск"
}

private fun marketingEmpty(tab: String, smsSeg: String): String = when (tab) {
    "portraits" -> "Портретов нет"
    "sms" -> when (smsSeg) {
        "incoming" -> "Нет входящих SMS"
        "groups" -> "Нет групп. Создайте группу выше: название и номера."
        else -> "Нет SMS-рассылок. Создайте кампанию во вкладках «Группы» или «Пул номеров»."
    }
    "push" -> "Пушей нет"
    "calendar" -> "Календарь пуст"
    "content" -> "Контент-план пуст"
    "banners" -> "Баннеров нет"
    "ads" -> "Рекламных размещений нет"
    "promos" -> "Промокодов нет"
    "qr" -> "QR-кодов нет"
    else -> "Клиентов нет"
}

private fun marketingPromoUses(o: JSONObject): Double? {
    return KassaApi.jsonNumOpt(
        o,
        "usedCount", "usageCount", "uses", "timesUsed",
        "redemptionsCount", "appliedCount", "totalUses",
    )
}

private fun marketingPromoUsageParts(o: JSONObject): List<String> {
    val disc = KassaApi.pick(o, "discountPercent", "discount", "percent")
    val uses = marketingPromoUses(o)
    val unique = KassaApi.jsonNumOpt(o, "uniqueClients", "uniqueUsers", "clientsCount", "uniqueCount", "distinctClients")
    val orders = KassaApi.jsonNumOpt(o, "ordersCount", "orderCount", "orders", "totalOrders")
    val money = KassaApi.jsonNumOpt(o, "totalDiscount", "discountSum", "savedAmount", "totalAmount")
    val parts = listOf(
        if (disc.isNotBlank()) "скидка $disc%" else "",
        uses?.let { "${KassaApi.prettyNumber(it.toString())} исп." }.orEmpty(),
        unique?.let { "${KassaApi.prettyNumber(it.toString())} кл." }.orEmpty(),
        orders?.let { "${KassaApi.prettyNumber(it.toString())} зак." }.orEmpty(),
        money?.let { KassaApi.tmt(it) }.orEmpty(),
    ).filter { it.isNotBlank() }
    return parts.ifEmpty { listOf("статистика в карточке") }
}

private fun marketingUnwrapPromo(json: JSONObject): JSONObject {
    val items = json.optJSONArray("items")
    if (items != null && json.length() == 1) {
        val list = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
        if (list.any { marketingLooksLikeUsage(it) }) {
            return JSONObject().put("usages", items)
        }
        if (list.size == 1) return marketingUnwrapPromo(list.first())
    }
    val out = JSONObject()
    json.keys().asSequence().forEach { out.put(it, json.get(it)) }
    for (nest in listOf("item", "data", "promo", "promoCode", "result", "stats", "statistics", "summary", "report", "usage")) {
        val nested = json.optJSONObject(nest) ?: continue
        nested.keys().asSequence().forEach { k ->
            if (!out.has(k) || out.isNull(k)) out.put(k, nested.get(k))
        }
    }
    return out
}

private fun marketingMergePromo(base: JSONObject, extra: JSONObject): JSONObject {
    val out = marketingUnwrapPromo(base)
    val extraU = marketingUnwrapPromo(extra)
    extraU.keys().asSequence().forEach { k ->
        out.put(k, extraU.get(k))
    }
    val items = extra.optJSONArray("items")
    if (items != null && items.length() > 0 && !out.has("usages")) {
        val looks = (0 until items.length()).mapNotNull { items.optJSONObject(it) }.any { marketingLooksLikeUsage(it) }
        if (looks) out.put("usages", items)
    }
    return out
}

private fun marketingLooksLikeUsage(item: JSONObject): Boolean {
    if (KassaApi.pick(item, "orderNumber", "orderId", "usedAt", "appliedAt", "clientName", "clientId", "phone", "clientPhone").isNotBlank()) {
        return true
    }
    return KassaApi.jsonNumOpt(item, "discountAmount", "savedAmount") != null
}

private fun marketingPromoEvents(o: JSONObject): List<JSONObject> {
    val keys = listOf("usages", "usage", "redemptions", "orders", "events", "history", "rows", "applications", "items")
    for (k in keys) {
        val arr = o.optJSONArray(k) ?: continue
        val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        if (list.any { marketingLooksLikeUsage(it) }) return list
    }
    for (nest in listOf("stats", "statistics", "report", "data")) {
        val nested = o.optJSONObject(nest) ?: continue
        val inner = marketingPromoEvents(nested)
        if (inner.isNotEmpty()) return inner
    }
    return emptyList()
}

private fun marketingPromoPairs(o: JSONObject): List<Pair<String, String>> {
    val keys = listOf(
        "code", "promoCode", "name",
        "usedCount", "usageCount", "uses", "timesUsed", "redemptionsCount", "appliedCount", "totalUses",
        "uniqueClients", "uniqueUsers", "clientsCount", "uniqueCount", "distinctClients",
        "ordersCount", "orderCount", "orders", "totalOrders",
        "totalDiscount", "discountSum", "savedAmount", "totalAmount",
        "lastUsedAt", "lastUsed", "lastAppliedAt",
        "discount", "discountPercent", "percent", "discountAmount",
        "usageLimit", "maxUses",
        "startsAt", "validFrom", "endsAt", "validTo", "expiresAt",
        "createdAt", "updatedAt", "comment", "note",
    )
    val seen = mutableSetOf<String>()
    val out = mutableListOf<Pair<String, String>>()
    for (key in keys) {
        if (!seen.add(key)) continue
        val raw = KassaApi.pick(o, key)
        if (raw.isBlank()) continue
        out += KassaApi.fieldLabel(key) to KassaApi.formatFieldValue(key, raw, o)
    }
    return out
}

private fun marketingPromoEventTitle(item: JSONObject): String {
    item.optJSONObject("client")?.let { nested ->
        val n = listOf(
            KassaApi.pick(nested, "fullName", "name", "firstName"),
            KassaApi.pick(nested, "lastName"),
        ).filter { it.isNotBlank() }.joinToString(" ")
        if (n.isNotBlank()) return n
        val p = KassaApi.pick(nested, "phone", "mobile")
        if (p.isNotBlank()) return p
    }
    return KassaApi.pick(item, "clientName", "fullName", "name", "phone", "clientPhone", "orderNumber", "orderId")
        .ifBlank { "Использование" }
}

private fun marketingPromoEventMeta(item: JSONObject): String {
    val order = KassaApi.pick(item, "orderNumber", "orderId", "externalId")
    val whenText = KassaApi.prettyTime(KassaApi.pick(item, "usedAt", "appliedAt", "createdAt", "orderDatetime"))
    val money = KassaApi.jsonNumOpt(item, "discountAmount", "discount", "savedAmount", "amount")
    return listOf(
        if (order.isNotBlank()) "заказ $order" else "",
        money?.let { KassaApi.tmt(it) }.orEmpty(),
        whenText,
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun marketingLoadPromoStats(api: KassaApi, token: String, row: JsonRow): JSONObject {
    var merged = marketingUnwrapPromo(row.raw)
    val id = row.id
    val paths = listOf(
        "/marketing/promo-codes/$id",
        "/marketing/promo-codes/$id/stats",
        "/marketing/promo-codes/$id/usage",
        "/marketing/promo-codes/$id/usages",
        "/marketing/promo-codes/$id/statistics",
        "/marketing/promo-codes/$id/report",
    )
    var got = 0
    var lastErr: String? = null
    for (path in paths) {
        try {
            val extra = api.getObject(path, token)
            merged = marketingMergePromo(merged, extra)
            got += 1
        } catch (e: Exception) {
            lastErr = e.message
        }
    }
    if (got == 0 && lastErr != null) throw IllegalStateException(lastErr)
    return marketingUnwrapPromo(merged)
}
