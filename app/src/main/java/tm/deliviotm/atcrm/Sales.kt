package tm.deliviotm.atcrm

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

private data class SalesContactEdit(
    val phone: String,
    val lpr: String,
    val sms: Boolean,
)

private data class SalesRateEdit(
    val date: String,
    val model: String,
    val commission: String,
    val markup: String,
)

private data class SalesReconEdit(
    val establishmentId: String,
    val actCode: String = "",
    val contractNumber: String = "",
    val ink: String = "",
    val legalAddress: String = "",
    val bankName: String = "",
    val bankAccount: String = "",
    val bankMfo: String = "",
    val bankCorrAccount: String = "",
    val email: String = "",
    val signatoryTitle: String = "Директор",
    val directorFullName: String = "",
    val directorShortName: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SalesWorkspacePane(
    api: KassaApi,
    token: String?,
    me: AppUser?,
    cache: CacheStore?,
    initialTab: String = "establishments",
    initialKind: String = "ALL",
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(if (initialTab in listOf("establishments", "sms", "cities")) initialTab else "establishments") }
    var kind by remember { mutableStateOf(if (initialKind in listOf("SHOP", "RESTAURANT")) initialKind else "ALL") }
    var status by remember { mutableStateOf("ALL") }
    var cityId by remember { mutableStateOf("") }
    var assigneeId by remember { mutableStateOf("") }
    var marketplace by remember { mutableStateOf("ALL") }
    var mine by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    var qDraft by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var skip by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var cities by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var assignees by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var smsTargets by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var smsGroups by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var broadcasts by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var effect by remember { mutableStateOf<JSONObject?>(null) }
    var smsSeg by remember { mutableStateOf("monitor") }
    var selectedTargets by remember { mutableStateOf(setOf<String>()) }
    var selectedGroups by remember { mutableStateOf(setOf<String>()) }
    var smsText by remember { mutableStateOf("") }
    var phonesText by remember { mutableStateOf("") }
    var groupName by remember { mutableStateOf("") }
    var smsPhones by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var stepups by remember { mutableStateOf<Map<String, JSONObject>>(emptyMap()) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }
    val pullState = rememberPullToRefreshState()
    val pageSize = 80

    val pickXlsx = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val t = token ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        err = null
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { copyUri(ctx, uri, "sales-import.xlsx") }
                val res = withContext(Dispatchers.IO) {
                    api.uploadMultipart(
                        "/sales/crm/import",
                        t,
                        file,
                        "file",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    )
                }
                msg = "Импорт: ${KassaApi.pick(res, "imported").ifBlank { "—" }} ок, ${KassaApi.pick(res, "skipped").ifBlank { "0" }} пропущено"
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Ошибка импорта"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(qDraft) {
        kotlinx.coroutines.delay(280)
        q = qDraft.trim()
        skip = 0
    }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        cities = withContext(Dispatchers.IO) { runCatching { api.getRows("/sales/establishments/cities", token) }.getOrDefault(emptyList()) }
        assignees = withContext(Dispatchers.IO) { runCatching { api.getRows("/sales/establishments/assignees", token) }.getOrDefault(emptyList()) }
    }

    LaunchedEffect(token, tick, tab, kind, status, cityId, assigneeId, marketplace, mine, deleted, q, skip, smsSeg) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        err = null
        if (loaded) refreshing = true
        try {
            when (tab) {
                "establishments" -> {
                    val raw = withContext(Dispatchers.IO) {
                        api.getObject(
                            KassaApi.salesEstablishmentsQuery(
                                pageSize, skip, q, status, cityId, kind, assigneeId, mine,
                                if (marketplace == "ALL") "" else marketplace, deleted,
                            ),
                            token,
                        )
                    }
                    val page = KassaApi.pagedRows(raw)
                    rows = if (skip == 0) page.items else rows + page.items
                    total = page.total
                    val hl = withContext(Dispatchers.IO) {
                        runCatching { api.getObject("/sales/establishments/hard-stepup/highlights", token) }.getOrNull()
                    }
                    val map = LinkedHashMap<String, JSONObject>()
                    hl?.optJSONArray("items")?.let { arr ->
                        KassaApi.eachObj(arr).forEach { item ->
                            val id = KassaApi.pick(item, "establishmentId", "id")
                            if (id.isNotBlank()) map[id] = item
                        }
                    }
                    stepups = map
                    cache?.putRows("/sales/establishments", rows)
                }
                "sms" -> {
                    val kindQ = if (kind == "ALL") "SHOP" else kind
                    smsTargets = withContext(Dispatchers.IO) {
                        runCatching { api.getRows("/sales/establishments/sms-broadcast-targets?kind=$kindQ&status=CONNECTED", token) }.getOrDefault(emptyList())
                    }
                    val groups = withContext(Dispatchers.IO) {
                        val o = runCatching { api.getObject("/sms/recipient-groups", token) }.getOrNull()
                        if (o != null) KassaApi.pagedRows(o).items else api.getRows("/sms/recipient-groups", token)
                    }
                    smsGroups = groups.filter {
                        val dep = KassaApi.pick(it.raw, "department").uppercase()
                        dep.isBlank() || dep == "SALES"
                    }
                    val allBc = withContext(Dispatchers.IO) {
                        val o = api.getObject("/sms/broadcasts", token)
                        val page = KassaApi.pagedRows(o)
                        if (page.items.isNotEmpty()) page.items else api.getRows("/sms/broadcasts", token)
                    }
                    val salesBc = allBc.filter {
                        val dep = KassaApi.pick(it.raw, "department").uppercase()
                        val mode = KassaApi.pick(it.raw, "audienceMode").uppercase()
                        dep == "SALES" || dep.isBlank() || mode == "ESTABLISHMENTS" || mode == "GROUPS"
                    }
                    broadcasts = if (salesBc.isNotEmpty()) salesBc else allBc
                    smsPhones = smsTargets.associate { row ->
                        row.id to KassaApi.pick(row.raw, "defaultPhone", "smsPhone", "phoneDisplay").ifBlank { KassaApi.phoneOf(row.raw) }
                    }
                    incoming = withContext(Dispatchers.IO) {
                        val o = api.getObject("/sms/incoming?skip=0&take=50", token)
                        val page = KassaApi.pagedRows(o)
                        if (page.items.isNotEmpty()) page.items else api.getRows("/sms/incoming?skip=0&take=50", token)
                    }
                    effect = withContext(Dispatchers.IO) {
                        runCatching { api.getObject("/sms/broadcasts/effectiveness-report?department=SALES&attributionDays=7", token) }.getOrNull()
                    }
                }
                "cities" -> {
                    rows = withContext(Dispatchers.IO) {
                        runCatching { api.getRows("/sales/establishments/cities?all=1", token) }.getOrDefault(emptyList())
                    }
                }
            }
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка"
            if (tab == "establishments") cache?.getRows("/sales/establishments")?.let { rows = it; loaded = true }
        } finally {
            refreshing = false
        }
    }

    fun runAct(ok: String, block: suspend () -> Unit) {
        val t = token ?: return
        busy = true
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
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Отдел продаж", onBack, onRefresh = { skip = 0; tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { skip = 0; tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SiteSectionHead("Отдел продаж", "CRM заведений: воронка, импорт/экспорт Excel, SMS") }
                item {
                    val section = when {
                        tab == "sms" -> "sms"
                        tab == "cities" -> "cities"
                        kind == "SHOP" -> "shops"
                        kind == "RESTAURANT" -> "restaurants"
                        else -> "establishments"
                    }
                    SiteFilterSelect(
                        value = section,
                        items = listOf(
                            "establishments" to "Заведения",
                            "sms" to "SMS рассылка",
                            "shops" to "Магазины",
                            "restaurants" to "Рестораны",
                            "cities" to "Города",
                        ),
                        onChange = {
                            when (it) {
                                "sms" -> { tab = "sms" }
                                "cities" -> { tab = "cities" }
                                "shops" -> { tab = "establishments"; kind = "SHOP" }
                                "restaurants" -> { tab = "establishments"; kind = "RESTAURANT" }
                                else -> { tab = "establishments"; kind = "ALL" }
                            }
                            skip = 0
                        },
                        label = "Раздел",
                    )
                }
                if (err != null) item { ActionBanner(err!!, error = true) }
                if (msg != null) item { ActionBanner(msg!!, error = false) }
                if (!loaded && err == null) item { LoadingCard() }

                if (tab == "establishments") {
                    item {
                        OutlinedTextField(qDraft, { qDraft = it }, label = { Text("Поиск") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    }
                    item {
                        Text("Статус воронки", color = AtColors.muted, fontSize = 12.sp)
                        SiteSegmented(value = status, items = KassaApi.salesFunnelStatuses, onChange = { status = it; skip = 0 })
                    }
                    if (cities.isNotEmpty()) {
                        item {
                            Text("Город", color = AtColors.muted, fontSize = 12.sp)
                            SiteSegmented(
                                value = cityId,
                                items = listOf("" to "Все") + cities.map { it.id to KassaApi.pick(it.raw, "name", "title").ifBlank { it.title } },
                                onChange = { cityId = it; skip = 0 },
                            )
                        }
                    }
                    item {
                        SiteSegmented(
                            value = marketplace,
                            items = listOf("ALL" to "Все", "1" to "Только МП", "0" to "Без МП"),
                            onChange = { marketplace = it; skip = 0 },
                        )
                    }
                    if (assignees.isNotEmpty()) {
                        item {
                            Text("Менеджер", color = AtColors.muted, fontSize = 12.sp)
                            SiteSegmented(
                                value = assigneeId,
                                items = listOf("" to "Все") + assignees.map { it.id to KassaApi.pick(it.raw, "fullName", "name").ifBlank { it.title } },
                                onChange = { assigneeId = it; skip = 0 },
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (mine) "☑ Только мои" else "☐ Только мои", color = AtColors.text, modifier = Modifier.clickable { mine = !mine; skip = 0 })
                            Text(if (deleted) "☑ Удалённые" else "☐ Удалённые", color = AtColors.text, modifier = Modifier.clickable { deleted = !deleted; skip = 0 })
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SalesBtn("+ Заведение", Modifier.weight(1f)) { creating = true }
                            SalesChip("Импорт Excel") { pickXlsx.launch("*/*") }
                            SalesChip("Экспорт Excel") {
                                val t = token ?: return@SalesChip
                                busy = true
                                scope.launch {
                                    try {
                                        val path = KassaApi.salesEstablishmentsQuery(
                                            pageSize, 0, q, status, cityId, kind, assigneeId, mine,
                                            if (marketplace == "ALL") "" else marketplace, false,
                                        ).replace("/sales/establishments", "/sales/establishments/export")
                                        val (bytes, name) = withContext(Dispatchers.IO) { api.getDownload(path, t) }
                                        val file = File(ctx.cacheDir, name.ifBlank { "sales.xlsx" })
                                        file.writeBytes(bytes)
                                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Экспорт Excel"))
                                        msg = "Экспорт готов"
                                    } catch (e: Exception) {
                                        err = e.message
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    }
                    item { Text("$total заведений", color = AtColors.muted, fontSize = 12.sp) }
                    items(rows, key = { it.id }) { row ->
                        val st = KassaApi.pick(row.raw, "status")
                        val step = stepups[row.id]
                        val stepLabel = if (step != null) {
                            val from = KassaApi.pick(step, "fromPercent").ifBlank { step.optJSONObject("plan")?.let { KassaApi.pick(it, "fromPercent") }.orEmpty() }
                            val to = KassaApi.pick(step, "toPercent").ifBlank { step.optJSONObject("plan")?.let { KassaApi.pick(it, "toPercent") }.orEmpty() }
                            if (from.isNotBlank() || to.isNotBlank()) "step-up $from% → $to%" else "step-up"
                        } else ""
                        AccLikeCard(
                            title = KassaApi.pick(row.raw, "title", "name").ifBlank { row.title },
                            meta = listOf(
                                KassaApi.salesKindLabel(KassaApi.pick(row.raw, "kind")),
                                KassaApi.pick(row.raw, "delivioId").takeIf { it.isNotBlank() }?.let { "ID $it" },
                                KassaApi.pick(row.raw, "commissionLabel", "commissionNote", "commissionTermsLabel"),
                                KassaApi.salesCityName(row.raw),
                                KassaApi.salesContactLine(row.raw),
                                KassaApi.salesManagerName(row.raw),
                                KassaApi.salesAgreementLabel(row.raw),
                                if (row.raw.optBoolean("isMarketplace", false)) "МП" else "",
                                if (row.raw.optBoolean("isThinkingOverdue", false) || row.raw.optBoolean("isOverdue", false)) "просрочка >30 дн." else "",
                                stepLabel,
                            ).mapNotNull { it?.takeIf { s -> s.isNotBlank() && s != "—" } }.joinToString(" · "),
                            status = KassaApi.salesStatusLabel(st),
                            onOpen = if (deleted) null else ({ openId = row.id }),
                        )
                    }
                    if (rows.size < total) {
                        item { SalesChip("Загрузить ещё") { skip = rows.size } }
                    }
                }

                if (tab == "sms") {
                    item {
                        SiteSegmented(
                            value = kind,
                            items = listOf("SHOP" to "Магазины", "RESTAURANT" to "Рестораны"),
                            onChange = { kind = if (it == "ALL") "SHOP" else it },
                        )
                    }
                    item {
                        SiteSegmented(
                            value = smsSeg,
                            items = listOf("monitor" to "Рассылки", "incoming" to "Ответы", "groups" to "Группы", "pool" to "Пул номеров"),
                            onChange = { smsSeg = it },
                        )
                    }
                    if (smsSeg == "groups" || smsSeg == "pool") {
                    item {
                        OutlinedTextField(smsText, { smsText = it }, label = { Text("Текст SMS") }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    }
                    }
                    if (smsSeg == "groups") {
                        item {
                            OutlinedTextField(groupName, { groupName = it.take(120) }, label = { Text("Название группы") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        }
                        item { Text("Готовы ${smsTargets.count { (smsPhones[it.id] ?: "").isNotBlank() || it.raw.optBoolean("smsReady", false) }} · выбрано ${selectedTargets.size}", color = AtColors.muted, fontSize = 12.sp) }
                        items(smsTargets, key = { it.id }) { row ->
                            val phone = smsPhones[row.id].orEmpty()
                            val ready = phone.isNotBlank() || row.raw.optBoolean("smsReady", false)
                            val on = selectedTargets.contains(row.id)
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(KassaApi.pick(row.raw, "title", "name").ifBlank { row.title }, color = AtColors.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (on) StatusChip("Выбрано") else if (!ready) StatusChip("Нет номера")
                                }
                                OutlinedTextField(
                                    phone,
                                    { value -> smsPhones = smsPhones + (row.id to value) },
                                    label = { Text("Номер для SMS") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = fieldColors(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SalesChip(if (on) "Снять" else "Выбрать") {
                                        if (phone.isBlank() && !ready) { err = "Сначала сохраните номер"; return@SalesChip }
                                        selectedTargets = if (on) selectedTargets - row.id else selectedTargets + row.id
                                    }
                                    SalesChip("Сохранить номер") {
                                        val value = smsPhones[row.id].orEmpty().trim()
                                        if (value.isBlank()) { err = "Укажите номер телефона"; return@SalesChip }
                                        runAct("Номер для SMS сохранён") {
                                            api.patchJson("/sales/establishments/${row.id}/sms-phone", token!!, JSONObject().put("phoneDisplay", value))
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            SalesBtn("Создать группу из выбранных", Modifier.fillMaxWidth()) {
                                if (groupName.isBlank()) { err = "Укажите название группы"; return@SalesBtn }
                                if (selectedTargets.isEmpty()) { err = "Отметьте заведения"; return@SalesBtn }
                                val phones = selectedTargets.map { smsPhones[it].orEmpty() }.filter { it.isNotBlank() }.joinToString("\n")
                                runAct("Группа создана") {
                                    val created = api.postJson("/sms/recipient-groups", token!!, JSONObject().put("name", groupName.trim()).put("department", "SALES"))
                                    val id = KassaApi.pick(created, "id").ifBlank { KassaApi.pick(created.optJSONObject("item") ?: JSONObject(), "id") }
                                    if (id.isNotBlank() && phones.isNotBlank()) {
                                        api.postJson("/sms/recipient-groups/$id/members/import-phones", token, JSONObject().put("text", phones).put("clearExisting", false))
                                    }
                                }
                            }
                        }
                        items(smsGroups, key = { it.id }) { g ->
                            val on = selectedGroups.contains(g.id)
                            AccLikeCard(
                                title = KassaApi.pick(g.raw, "name", "title").ifBlank { g.title },
                                meta = "участников: ${g.raw.optInt("memberCount", g.raw.optInt("count", 0))}",
                                status = if (on) "Выбрана" else "",
                                onOpen = { selectedGroups = if (on) selectedGroups - g.id else selectedGroups + g.id },
                                actions = listOf("Удалить" to {
                                    runAct("Группа удалена") { api.deletePath("/sms/recipient-groups/${g.id}", token!!) }
                                }),
                            )
                        }
                        item {
                            SalesBtn("Отправить сейчас", Modifier.fillMaxWidth()) {
                                if (smsText.isBlank()) { err = "Введите текст SMS"; return@SalesBtn }
                                if (selectedGroups.isEmpty()) { err = "Отметьте группу"; return@SalesBtn }
                                runAct("Рассылка запущена") {
                                    val created = api.postJson(
                                        "/sms/broadcasts",
                                        token!!,
                                        JSONObject()
                                            .put("message", smsText.trim())
                                            .put("audienceMode", "GROUPS")
                                            .put("groupIds", JSONArray(selectedGroups.toList()))
                                            .put("runMode", "CONCURRENT"),
                                    )
                                    val id = KassaApi.pick(created, "id")
                                    if (id.isNotBlank()) runCatching { api.postJson("/sms/broadcasts/$id/start", token, JSONObject()) }
                                }
                            }
                        }
                    }
                    if (smsSeg == "pool") {
                        item { Text("Пул номеров CONNECTED. Вставьте список или отметьте заведения.", color = AtColors.muted, fontSize = 13.sp) }
                        item {
                            OutlinedTextField(
                                phonesText,
                                { phonesText = it },
                                label = { Text("Пул номеров (по одному в строке)") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(),
                            )
                        }
                        items(smsTargets, key = { "p-${it.id}" }) { row ->
                            val phone = smsPhones[row.id].orEmpty()
                            val on = selectedTargets.contains(row.id)
                            AccLikeCard(
                                title = KassaApi.pick(row.raw, "title", "name").ifBlank { row.title },
                                meta = phone.ifBlank { "Без №" },
                                status = if (on) "Выбрано" else "",
                                onOpen = { if (phone.isNotBlank()) selectedTargets = if (on) selectedTargets - row.id else selectedTargets + row.id },
                            )
                        }
                        item {
                            SalesBtn("Отправить по выбранным / пулу", Modifier.fillMaxWidth()) {
                                val picked = selectedTargets.map { smsPhones[it].orEmpty() }.filter { it.isNotBlank() }.joinToString("\n")
                                val phones = phonesText.trim().ifBlank { picked }
                                if (smsText.isBlank() || phones.isBlank()) { err = "Нужны текст и номера"; return@SalesBtn }
                                runAct("Рассылка запущена") {
                                    val created = api.postJson(
                                        "/sms/broadcasts",
                                        token!!,
                                        JSONObject()
                                            .put("message", smsText.trim())
                                            .put("audienceMode", "ESTABLISHMENTS")
                                            .put("department", "SALES")
                                            .put("phonesText", phones),
                                    )
                                    val id = KassaApi.pick(created, "id")
                                    if (id.isNotBlank()) runCatching { api.postJson("/sms/broadcasts/$id/start", token, JSONObject()) }
                                }
                            }
                        }
                    }
                    if (smsSeg == "monitor") {
                        val kpi = effect
                        item {
                            ReportKpiGrid(
                                listOf(
                                    PulseMetric("Рассылок", broadcasts.size.toString(), "SALES", "✉"),
                                    PulseMetric("Доставлено", (kpi?.optInt("delivered", 0) ?: 0).toString(), "", "✓"),
                                    PulseMetric("Ответы", incoming.size.toString(), "", "↩"),
                                ),
                            )
                        }
                        if (loaded && broadcasts.isEmpty()) {
                            item { Text("Нет SMS-рассылок. Создайте кампанию во вкладках «Группы» или «Пул номеров».", color = AtColors.muted, fontSize = 13.sp) }
                        }
                        items(broadcasts, key = { it.id }) { row ->
                            val st = KassaApi.pick(row.raw, "status")
                            AccLikeCard(
                                title = KassaApi.pick(row.raw, "message", "title", "name").ifBlank { row.title },
                                meta = listOf(
                                    salesBroadcastStatus(st),
                                    "${row.raw.optInt("recipientsTotal", row.raw.optInt("total", row.raw.optInt("recipients", 0)))} получ.",
                                    KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "scheduledAt")),
                                    KassaApi.pick(row.raw, "department"),
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                                status = salesBroadcastStatus(st),
                                actions = buildList {
                                    if (st.equals("DRAFT", true) || st.equals("WAITING_QUEUE", true) || st.equals("SCHEDULED", true)) {
                                        add("Старт" to { runAct("Запущена") { api.postJson("/sms/broadcasts/${row.id}/start", token!!, JSONObject()) } })
                                    }
                                    if (st.equals("RUNNING", true) || st.contains("WAIT", true) || st.equals("SCHEDULED", true)) {
                                        add("Стоп" to { runAct("Остановлена") { api.postJson("/sms/broadcasts/${row.id}/cancel", token!!, JSONObject()) } })
                                    }
                                    add("Повтор ошибок" to { runAct("Повтор") { api.postJson("/sms/broadcasts/${row.id}/retry-failed", token!!, JSONObject()) } })
                                },
                            )
                        }
                    }
                    if (smsSeg == "incoming") {
                        if (loaded && incoming.isEmpty()) {
                            item { Text("Нет входящих SMS", color = AtColors.muted, fontSize = 13.sp) }
                        }
                        items(incoming, key = { "in-${it.id}" }) { row ->
                            AccLikeCard(
                                title = KassaApi.pick(row.raw, "fromNumber", "from", "phone", "sender").ifBlank { row.title },
                                meta = listOf(
                                    KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "time")),
                                    KassaApi.pick(row.raw, "message", "text", "body").ifBlank { row.subtitle },
                                ).filter { it.isNotBlank() }.joinToString(" · "),
                            )
                        }
                    }
                }

                if (tab == "cities") {
                    item { SalesBtn("+ Город", Modifier.fillMaxWidth()) { creating = true } }
                    items(rows, key = { it.id }) { row ->
                        val archived = row.raw.optBoolean("archived", false)
                        AccLikeCard(
                            title = KassaApi.pick(row.raw, "name", "title").ifBlank { row.title },
                            meta = listOf(
                                KassaApi.pick(row.raw, "delivioId").takeIf { it.isNotBlank() }?.let { "Delivio $it" },
                                KassaApi.pick(row.raw, "cityKey").takeIf { it.isNotBlank() }?.let { "ключ $it" },
                            ).filterNotNull().joinToString(" · "),
                            status = if (archived) "скрыт" else "активен",
                            actions = listOf(
                                (if (archived) "Вернуть" else "Скрыть") to {
                                    runAct(if (archived) "Возвращён" else "Скрыт") {
                                        api.patchJson("/sales/establishments/cities/${row.id}", token!!, JSONObject().put("archived", !archived))
                                    }
                                },
                            ),
                        )
                    }
                }
                if (busy) item { Text("Выполняется…", color = AtColors.muted, fontSize = 13.sp) }
            }
        }
    }

    if (creating && tab == "establishments") {
        SalesCreateEstDialog(
            cities = cities,
            kind = if (kind == "ALL") "SHOP" else kind,
            onDismiss = { creating = false },
            onSave = { body ->
                runAct("Заведение создано") { api.postJson("/sales/establishments", token!!, body) }
                creating = false
            },
        )
    }
    if (creating && tab == "cities") {
        SalesCreateCityDialog(
            onDismiss = { creating = false },
            onSave = { body ->
                runAct("Город создан") { api.postJson("/sales/establishments/cities", token!!, body) }
                creating = false
            },
        )
    }
    openId?.let { id ->
        if (!token.isNullOrBlank()) {
            SalesEstCardDialog(
                api = api,
                token = token,
                estId = id,
                cities = cities,
                assignees = assignees,
                onDismiss = { openId = null },
                onChanged = { skip = 0; tick++ },
            )
        }
    }
}

@Composable
private fun SalesCreateEstDialog(
    cities: List<JsonRow>,
    kind: String,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var cityId by remember { mutableStateOf(cities.firstOrNull()?.id.orEmpty()) }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var lpr by remember { mutableStateOf("") }
    var estKind by remember { mutableStateOf(kind) }
    var err by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Добавить заведение", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SiteSegmented(value = estKind, items = listOf("SHOP" to "Магазин", "RESTAURANT" to "Ресторан"), onChange = { estKind = it })
            OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            if (cities.isNotEmpty()) {
                Text("Город", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(value = cityId, items = cities.map { it.id to KassaApi.pick(it.raw, "name").ifBlank { it.title } }, onChange = { cityId = it })
            }
            OutlinedTextField(address, { address = it }, label = { Text("Адрес") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(lpr, { lpr = it }, label = { Text("ЛПР") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            err?.let { Text(it, color = AtColors.danger, fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SalesChip("Отмена") { onDismiss() }
                SalesBtn("Создать") {
                    if (title.isBlank()) { err = "Укажите название"; return@SalesBtn }
                    if (cityId.isBlank()) { err = "Выберите город"; return@SalesBtn }
                    val contacts = JSONArray()
                    if (phone.isNotBlank() || lpr.isNotBlank()) {
                        contacts.put(JSONObject().put("phoneDisplay", phone.trim()).put("lprName", lpr.trim()))
                    }
                    onSave(JSONObject().put("title", title.trim()).put("kind", estKind).put("cityId", cityId).put("address", address.trim()).put("contacts", contacts))
                }
            }
        }
    }
}

@Composable
private fun SalesCreateCityDialog(onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var name by remember { mutableStateOf("") }
    var delivioId by remember { mutableStateOf("") }
    var cityKey by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Новый город", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(name, { name = it }, label = { Text("Город") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(delivioId, { delivioId = it.filter { ch -> ch.isDigit() } }, label = { Text("ID в Delivio") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            OutlinedTextField(cityKey, { cityKey = it }, label = { Text("Ключ бота (необязательно)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            err?.let { Text(it, color = AtColors.danger, fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SalesChip("Отмена") { onDismiss() }
                SalesBtn("Создать") {
                    val id = delivioId.toIntOrNull()
                    if (name.isBlank()) { err = "Укажите город"; return@SalesBtn }
                    if (id == null || id <= 0) { err = "ID в Delivio — целое > 0"; return@SalesBtn }
                    val body = JSONObject().put("name", name.trim()).put("delivioId", id)
                    if (cityKey.isNotBlank()) body.put("cityKey", cityKey.trim())
                    onSave(body)
                }
            }
        }
    }
}

@Composable
private fun SalesEstCardDialog(
    api: KassaApi,
    token: String,
    estId: String,
    cities: List<JsonRow>,
    assignees: List<JsonRow>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var obj by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var statusOpen by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var delivioId by remember { mutableStateOf("") }
    var billingId by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var cityId by remember { mutableStateOf("") }
    var marketplace by remember { mutableStateOf(false) }
    var assignedUserId by remember { mutableStateOf("") }
    var cooperation by remember { mutableStateOf("") }
    var withoutAgreement by remember { mutableStateOf(false) }
    var agreementAtEst by remember { mutableStateOf(false) }
    var legalEntity by remember { mutableStateOf("") }
    var agreementDate by remember { mutableStateOf("") }
    var registrationNumber by remember { mutableStateOf("") }
    var agreementContact by remember { mutableStateOf("") }
    var agreementInfo by remember { mutableStateOf("") }
    var agreementStatus by remember { mutableStateOf("") }
    var agreementPay by remember { mutableStateOf("") }
    var agreementComment by remember { mutableStateOf("") }
    var adminLogin by remember { mutableStateOf("") }
    var credentials by remember { mutableStateOf("") }
    var cashier by remember { mutableStateOf("") }
    var internalNote by remember { mutableStateOf("") }
    var commissionNote by remember { mutableStateOf("") }
    var hardLock by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<SalesContactEdit>>(emptyList()) }
    var rates by remember { mutableStateOf<List<SalesRateEdit>>(emptyList()) }
    var registeredAt by remember { mutableStateOf("") }
    var connectedAt by remember { mutableStateOf("") }
    var fileComments by remember { mutableStateOf("") }
    var firstOrderAt by remember { mutableStateOf("") }
    var recon by remember { mutableStateOf<SalesReconEdit?>(null) }
    var loadTick by remember { mutableIntStateOf(0) }
    val isNew = obj?.let { KassaApi.pick(it, "status").equals("NEW", true) } == true
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        err = null
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { copyUri(ctx, uri, "agreement.pdf") }
                withContext(Dispatchers.IO) {
                    api.uploadMultipart("/sales/establishments/$estId/agreement-documents", token, file, "file", "application/pdf")
                }
                msg = "PDF соглашения загружен"
                loadTick++
                onChanged()
            } catch (e: Exception) {
                err = e.message ?: "Не удалось загрузить PDF"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(estId, loadTick) {
        try {
            val o = withContext(Dispatchers.IO) { api.getObject("/sales/establishments/$estId", token) }
            obj = o
            title = KassaApi.pick(o, "title", "name")
            delivioId = KassaApi.pick(o, "delivioId").filter { it.isDigit() }
            billingId = KassaApi.pick(o, "billingId")
            address = KassaApi.pick(o, "address")
            cityId = KassaApi.pick(o, "cityId").ifBlank { o.optJSONObject("city")?.let { KassaApi.pick(it, "id") }.orEmpty() }
            marketplace = o.optBoolean("isMarketplace", false)
            assignedUserId = KassaApi.pick(o, "assignedUserId").ifBlank { o.optJSONObject("assignedUser")?.let { KassaApi.pick(it, "id") }.orEmpty() }
            cooperation = KassaApi.pick(o, "cooperationFormat")
            withoutAgreement = o.optBoolean("withoutAgreement", false)
            agreementAtEst = o.optBoolean("agreementAtEstablishment", false)
            legalEntity = KassaApi.pick(o, "legalEntity")
            agreementDate = KassaApi.pick(o, "agreementDate").take(10)
            registrationNumber = KassaApi.pick(o, "registrationNumber")
            agreementContact = KassaApi.pick(o, "agreementContactPerson")
            agreementInfo = KassaApi.pick(o, "agreementContactInfo")
            agreementStatus = KassaApi.pick(o, "agreementStatus")
            agreementPay = KassaApi.pick(o, "agreementPaymentTerms")
            agreementComment = KassaApi.pick(o, "agreementComment")
            adminLogin = KassaApi.pick(o, "adminLogin")
            credentials = KassaApi.pick(o, "credentialsNote")
            cashier = KassaApi.pick(o, "cashierProgram")
            internalNote = KassaApi.pick(o, "internalNote")
            commissionNote = KassaApi.pick(o, "commissionNote")
            hardLock = o.optBoolean("hardCommissionLocked", false)
            registeredAt = KassaApi.pick(o, "registeredAt", "createdAt").take(10)
            connectedAt = KassaApi.pick(o, "connectedAt").take(10)
            fileComments = KassaApi.pick(o, "fileComments")
            firstOrderAt = KassaApi.pick(o, "firstOrderAt").take(10)
            contacts = KassaApi.salesContacts(o).map {
                SalesContactEdit(KassaApi.pick(it, "phoneDisplay", "phone"), KassaApi.pick(it, "lprName", "name"), it.optBoolean("isDefaultSms", false))
            }.ifEmpty { listOf(SalesContactEdit("", "", false)) }
            val hist = o.optJSONArray("commissionHistory")
            rates = if (hist != null) KassaApi.eachObj(hist).map {
                SalesRateEdit(
                    KassaApi.pick(it, "effectiveFrom").take(10),
                    KassaApi.pick(it, "model").ifBlank { "MERCHANT_DISCOUNT" },
                    KassaApi.jsonNum(it, "merchantDiscountPercent").toString(),
                    KassaApi.jsonNum(it, "markupPercent").toString(),
                )
            } else emptyList()
            val delivioDigits = delivioId.filter { it.isDigit() }
            if (delivioDigits.isNotBlank()) {
                val to = java.time.LocalDate.now()
                val from = to.minusMonths(12)
                recon = withContext(Dispatchers.IO) {
                    runCatching {
                        val profiles = api.getRows("/reconciliation-acts/profiles?dateFrom=$from&dateTo=$to", token)
                        val match = profiles.firstOrNull { row ->
                            KassaApi.pick(row.raw, "delivioId").filter { ch -> ch.isDigit() } == delivioDigits
                        } ?: profiles.firstOrNull { row ->
                            KassaApi.pick(row.raw, "establishmentName", "title").equals(title, true)
                        }
                        match?.let { row ->
                            val src = row.raw
                            SalesReconEdit(
                                establishmentId = KassaApi.pick(src, "establishmentId", "id").ifBlank { row.id },
                                actCode = KassaApi.pick(src, "actCode", "delivioId"),
                                contractNumber = KassaApi.pick(src, "contractNumber", "salesRegistrationNumber"),
                                ink = KassaApi.pick(src, "ink"),
                                legalAddress = KassaApi.pick(src, "legalAddress", "salesAddress"),
                                bankName = KassaApi.pick(src, "bankName"),
                                bankAccount = KassaApi.pick(src, "bankAccount"),
                                bankMfo = KassaApi.pick(src, "bankMfo"),
                                bankCorrAccount = KassaApi.pick(src, "bankCorrAccount"),
                                email = KassaApi.pick(src, "email"),
                                signatoryTitle = KassaApi.pick(src, "signatoryTitle").ifBlank { "Директор" },
                                directorFullName = KassaApi.pick(src, "directorFullName", "salesAgreementContactPerson"),
                                directorShortName = KassaApi.pick(src, "directorShortName"),
                            )
                        }
                    }.getOrNull()
                }
            }
        } catch (e: Exception) {
            err = e.message
        }
    }

    fun patchBody(): JSONObject {
        val arr = JSONArray()
        contacts.filter { it.phone.isNotBlank() || it.lpr.isNotBlank() }.forEach {
            arr.put(JSONObject().put("phoneDisplay", it.phone.trim()).put("lprName", it.lpr.trim()).put("isDefaultSms", it.sms))
        }
        if (isNew) {
            return JSONObject().put("title", title.trim()).put("internalNote", internalNote).put("contacts", arr)
        }
        val hist = JSONArray()
        rates.forEach { r ->
            hist.put(
                JSONObject()
                    .put("effectiveFrom", salesIsoDay(r.date))
                    .put("model", r.model)
                    .put("merchantDiscountPercent", KassaApi.parseMoneyInput(r.commission) ?: 0.0)
                    .put("markupPercent", KassaApi.parseMoneyInput(r.markup) ?: 0.0),
            )
        }
        return JSONObject()
            .put("title", title.trim())
            .put("address", address.trim())
            .put("cityId", cityId)
            .put("delivioId", delivioId.ifBlank { JSONObject.NULL })
            .put("billingId", billingId.ifBlank { JSONObject.NULL })
            .put("isMarketplace", marketplace)
            .put("withoutAgreement", withoutAgreement)
            .put("agreementAtEstablishment", agreementAtEst)
            .put("hardCommissionLocked", hardLock)
            .put("assignedUserId", assignedUserId.ifBlank { JSONObject.NULL })
            .put("adminLogin", adminLogin)
            .put("credentialsNote", credentials)
            .put("cashierProgram", cashier)
            .put("commissionHistory", hist)
            .put("commissionNote", commissionNote)
            .put("internalNote", internalNote)
            .put("fileComments", fileComments.ifBlank { JSONObject.NULL })
            .put("cooperationFormat", cooperation)
            .put("legalEntity", legalEntity)
            .put("agreementDate", salesIsoDay(agreementDate))
            .put("registeredAt", salesIsoDay(registeredAt))
            .put("connectedAt", salesIsoDay(connectedAt))
            .put("registrationNumber", registrationNumber)
            .put("agreementContactPerson", agreementContact)
            .put("agreementContactInfo", agreementInfo)
            .put("agreementStatus", agreementStatus)
            .put("agreementPaymentTerms", agreementPay)
            .put("agreementComment", agreementComment)
            .put("contacts", arr)
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(10.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(14.dp)) {
            val st = obj?.let { KassaApi.pick(it, "status") }.orEmpty()
            Text("Карточка заведения", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(KassaApi.salesStatusLabel(st) + if (isNew) " · новый лид" else "", color = AtColors.muted, fontSize = 13.sp)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (obj == null && err == null) LoadingCard()
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                contacts.forEachIndexed { i, c ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AtColors.glass).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(c.phone, { value -> contacts = contacts.toMutableList().also { list -> list[i] = c.copy(phone = value) } }, label = { Text("Телефон") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(c.lpr, { value -> contacts = contacts.toMutableList().also { list -> list[i] = c.copy(lpr = value) } }, label = { Text("ЛПР") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        Text(if (c.sms) "☑ Для рассылки" else "☐ Для рассылки", color = AtColors.text, modifier = Modifier.clickable {
                            contacts = contacts.mapIndexed { j, x -> x.copy(sms = j == i) }
                        })
                    }
                }
                Text("+ пара телефон / ЛПР", color = AtColors.accent, modifier = Modifier.clickable { contacts = contacts + SalesContactEdit("", "", false) })
                OutlinedTextField(internalNote, { internalNote = it }, label = { Text("Внутренняя заметка") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                if (!isNew) {
                    OutlinedTextField(delivioId, { delivioId = it.filter { ch -> ch.isDigit() } }, label = { Text("ID заведения") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(billingId, { billingId = it }, label = { Text("ID биллинга") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(registeredAt, { registeredAt = it }, label = { Text("Дата регистрации (ГГГГ-ММ-ДД)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(connectedAt, { connectedAt = it }, label = { Text("Дата подключения") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    if (firstOrderAt.isNotBlank()) Text("Первый заказ: $firstOrderAt", color = AtColors.muted, fontSize = 12.sp)
                    Text(if (marketplace) "☑ Маркетплейс" else "☐ Маркетплейс", color = AtColors.text, modifier = Modifier.clickable { marketplace = !marketplace })
                    OutlinedTextField(address, { address = it }, label = { Text("Адрес") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    if (cities.isNotEmpty()) {
                        Text("Город", color = AtColors.muted, fontSize = 12.sp)
                        SiteSegmented(value = cityId, items = cities.map { it.id to KassaApi.pick(it.raw, "name").ifBlank { it.title } }, onChange = { cityId = it })
                    }
                    OutlinedTextField(cooperation, { cooperation = it }, label = { Text("Формат сотрудничества") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    if (assignees.isNotEmpty()) {
                        Text("Менеджер", color = AtColors.muted, fontSize = 12.sp)
                        SiteSegmented(value = assignedUserId, items = listOf("" to "—") + assignees.map { it.id to KassaApi.pick(it.raw, "fullName", "name").ifBlank { it.title } }, onChange = { assignedUserId = it })
                    }
                    Text("Договор", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                    Text(if (withoutAgreement) "☑ Без договора" else "☐ Без договора", color = AtColors.text, modifier = Modifier.clickable { withoutAgreement = !withoutAgreement })
                    Text(if (agreementAtEst) "☑ В заведении" else "☐ В заведении", color = AtColors.text, modifier = Modifier.clickable { agreementAtEst = !agreementAtEst })
                    OutlinedTextField(legalEntity, { legalEntity = it }, label = { Text("Юридическое лицо") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(agreementDate, { agreementDate = it }, label = { Text("Дата соглашения") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(registrationNumber, { registrationNumber = it }, label = { Text("Рег. номер") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(agreementContact, { agreementContact = it }, label = { Text("Контактное лицо") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(agreementInfo, { agreementInfo = it }, label = { Text("Телефон / почта (соглашение)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(agreementStatus, { agreementStatus = it }, label = { Text("Статус соглашения") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(agreementPay, { agreementPay = it }, label = { Text("Оплата (условия)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(agreementComment, { agreementComment = it }, label = { Text("Комментарий соглашения") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    Text("PDF соглашения", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                    val docs = run {
                        val arr = obj?.optJSONArray("agreementDocuments")
                        if (arr != null && arr.length() > 0) KassaApi.eachObj(arr)
                        else if (obj?.optBoolean("hasAgreementPdf", false) == true) listOf(JSONObject().put("id", "legacy").put("fileName", obj?.optString("agreementPdfFileName", "agreement.pdf")))
                        else emptyList()
                    }
                    docs.forEach { doc ->
                        val docId = KassaApi.pick(doc, "id")
                        val name = KassaApi.pick(doc, "fileName", "name").ifBlank { "agreement.pdf" }
                        AccLikeCard(
                            title = name,
                            meta = KassaApi.prettyTime(KassaApi.pick(doc, "uploadedAt")),
                            actions = listOf(
                                "Открыть" to {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val path = if (docId.isBlank() || docId == "legacy") {
                                                "/sales/establishments/$estId/agreement-pdf"
                                            } else {
                                                "/sales/establishments/$estId/agreement-documents/$docId"
                                            }
                                            val (bytes, fname) = withContext(Dispatchers.IO) { api.getDownload(path, token) }
                                            val file = File(ctx.cacheDir, fname.ifBlank { name })
                                            file.writeBytes(bytes)
                                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "PDF"))
                                        } catch (e: Exception) {
                                            err = e.message
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                                "Удалить" to {
                                    if (docId.isBlank() || docId == "legacy") {
                                        err = "Старый PDF удаляется с карточки на сайте"
                                    } else {
                                        busy = true
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { api.deletePath("/sales/establishments/$estId/agreement-documents/$docId", token) }
                                                msg = "PDF удалён"
                                                loadTick++
                                                onChanged()
                                            } catch (e: Exception) {
                                                err = e.message
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                },
                            ),
                        )
                    }
                    SalesChip("Добавить PDF") { pickPdf.launch("application/pdf") }
                    Text("Комиссия", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(commissionNote, { commissionNote = it }, label = { Text("Ставка / комментарий") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    Text(if (hardLock) "☑ % не изменяем (потолок)" else "☐ % не изменяем (потолок)", color = AtColors.text, modifier = Modifier.clickable { hardLock = !hardLock })
                    rates.forEachIndexed { i, r ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AtColors.glass).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(r.date, { value -> rates = rates.toMutableList().also { list -> list[i] = r.copy(date = value) } }, label = { Text("Дата") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                            SiteSegmented(value = r.model, items = listOf("MERCHANT_DISCOUNT" to "Комиссия", "MARKUP" to "Наценка", "HYBRID" to "Гибрид"), onChange = { value -> rates = rates.toMutableList().also { list -> list[i] = r.copy(model = value) } })
                            OutlinedTextField(r.commission, { value -> rates = rates.toMutableList().also { list -> list[i] = r.copy(commission = value) } }, label = { Text("% комиссии") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                            OutlinedTextField(r.markup, { value -> rates = rates.toMutableList().also { list -> list[i] = r.copy(markup = value) } }, label = { Text("% наценки") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                            Text("Удалить строку", color = AtColors.danger, modifier = Modifier.clickable { rates = rates.filterIndexed { j, _ -> j != i } })
                        }
                    }
                    Text("+ строка ставки", color = AtColors.accent, modifier = Modifier.clickable {
                        rates = rates + SalesRateEdit(java.time.LocalDate.now().toString(), "MERCHANT_DISCOUNT", "15", "0")
                    })
                    Text("Доступы", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(adminLogin, { adminLogin = it }, label = { Text("Логин админки") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(credentials, { credentials = it }, label = { Text("Пароль / ЛК") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(cashier, { cashier = it }, label = { Text("Кассовая программа") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(fileComments, { fileComments = it }, label = { Text("Комментарии к файлам") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    val reconNow = recon
                    if (reconNow != null) {
                        Text("Реквизиты для акта сверки", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(reconNow.actCode, { recon = reconNow.copy(actCode = it) }, label = { Text("Код акта") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.contractNumber, { recon = reconNow.copy(contractNumber = it) }, label = { Text("Договор") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.ink, { recon = reconNow.copy(ink = it) }, label = { Text("ИНК") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.legalAddress, { recon = reconNow.copy(legalAddress = it) }, label = { Text("Юр. адрес") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.bankName, { recon = reconNow.copy(bankName = it) }, label = { Text("Банк") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.bankAccount, { recon = reconNow.copy(bankAccount = it) }, label = { Text("Счёт") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.bankCorrAccount, { recon = reconNow.copy(bankCorrAccount = it) }, label = { Text("Корр. счёт") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.bankMfo, { recon = reconNow.copy(bankMfo = it) }, label = { Text("МФО") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.email, { recon = reconNow.copy(email = it) }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.signatoryTitle, { recon = reconNow.copy(signatoryTitle = it) }, label = { Text("Должность подписанта") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.directorFullName, { recon = reconNow.copy(directorFullName = it) }, label = { Text("ФИО директора") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(reconNow.directorShortName, { recon = reconNow.copy(directorShortName = it) }, label = { Text("ФИО кратко") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        SalesChip("Сохранить реквизиты") {
                            val r = recon ?: return@SalesChip
                            if (r.establishmentId.isBlank()) { err = "Нет профиля сверки"; return@SalesChip }
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        api.putJson(
                                            "/reconciliation-acts/profiles/${r.establishmentId}",
                                            token,
                                            JSONObject()
                                                .put("actCode", r.actCode)
                                                .put("contractNumber", r.contractNumber)
                                                .put("ink", r.ink)
                                                .put("legalAddress", r.legalAddress)
                                                .put("bankName", r.bankName)
                                                .put("bankAccount", r.bankAccount)
                                                .put("bankMfo", r.bankMfo)
                                                .put("bankCorrAccount", r.bankCorrAccount)
                                                .put("email", r.email)
                                                .put("signatoryTitle", r.signatoryTitle.ifBlank { "Директор" })
                                                .put("directorFullName", r.directorFullName)
                                                .put("directorShortName", r.directorShortName),
                                        )
                                    }
                                    msg = "Реквизиты сверки сохранены"
                                } catch (e: Exception) {
                                    err = e.message
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                    val events = obj?.optJSONArray("statusEvents")
                    if (events != null && events.length() > 0) {
                        Text("История статусов и комментарии", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                        KassaApi.eachObj(events).take(20).forEach { ev ->
                            Text(
                                "${KassaApi.salesStatusLabel(KassaApi.pick(ev, "fromStatus"))} → ${KassaApi.salesStatusLabel(KassaApi.pick(ev, "toStatus"))} · ${KassaApi.pick(ev, "comment")}",
                                color = AtColors.muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                err?.let { Text(it, color = AtColors.danger, fontSize = 13.sp) }
                msg?.let { Text(it, color = AtColors.success, fontSize = 13.sp) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalesChip("Закрыть") { onDismiss() }
                    SalesBtn("Сохранить") {
                        busy = true
                        err = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { api.patchJson("/sales/establishments/$estId", token, patchBody()) }
                                msg = "Сохранено"
                                loadTick++
                                onChanged()
                            } catch (e: Exception) {
                                err = e.message
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SalesChip("Сменить статус") { statusOpen = true }
                    val phone = contacts.firstOrNull { it.phone.isNotBlank() }?.phone.orEmpty()
                    if (phone.isNotBlank()) {
                        SalesChip("Позвонить") { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.filter { it.isDigit() || it == '+' }}"))) }
                    }
                    SalesChip("Удалить") {
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { api.deletePath("/sales/establishments/$estId", token) }
                                onChanged()
                                onDismiss()
                            } catch (e: Exception) {
                                err = e.message
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
        }
    }
    if (statusOpen) {
        SalesStatusDialog(
            current = obj?.let { KassaApi.pick(it, "status") }.orEmpty(),
            onDismiss = { statusOpen = false },
            onSave = { to, comment ->
                busy = true
                err = null
                scope.launch {
                    try {
                        if (to == "CONNECTED" && delivioId.isBlank()) {
                            err = "Для «Подключен» нужен ID заведения"
                            busy = false
                            return@launch
                        }
                        if (to == "CONNECTED" && commissionNote.isBlank() && rates.isEmpty()) {
                            withContext(Dispatchers.IO) {
                                api.patchJson("/sales/establishments/$estId", token, JSONObject().put("delivioId", delivioId).put("commissionNote", comment))
                            }
                        } else if (to == "CONNECTED") {
                            withContext(Dispatchers.IO) {
                                api.patchJson("/sales/establishments/$estId", token, JSONObject().put("delivioId", delivioId).put("commissionNote", commissionNote.ifBlank { comment }))
                            }
                        }
                        withContext(Dispatchers.IO) {
                            api.postJson("/sales/establishments/$estId/status", token, JSONObject().put("toStatus", to).put("comment", comment))
                        }
                        statusOpen = false
                        msg = "Статус обновлён"
                        loadTick++
                        onChanged()
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

@Composable
private fun SalesStatusDialog(current: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var to by remember { mutableStateOf(KassaApi.salesFunnelStatuses.firstOrNull { it.first != "ALL" && it.first != current }?.first ?: "CALL_DONE") }
    var comment by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Сменить статус", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Сейчас: ${KassaApi.salesStatusLabel(current)}", color = AtColors.muted, fontSize = 13.sp)
            SiteSegmented(value = to, items = KassaApi.salesFunnelStatuses.filter { it.first != "ALL" }, onChange = { to = it })
            OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий (обязательно)") }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
            err?.let { Text(it, color = AtColors.danger, fontSize = 13.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SalesChip("Отмена") { onDismiss() }
                SalesBtn("Сменить") {
                    if (comment.isBlank()) { err = "Нужен комментарий"; return@SalesBtn }
                    onSave(to, comment.trim())
                }
            }
        }
    }
}

@Composable
private fun SalesBtn(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun SalesChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accentSoft).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

@Composable
private fun AccLikeCard(
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
        if (meta.isNotBlank()) Text(meta, color = AtColors.muted, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (actions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { (label, click) -> InlineActionChip(label, onClick = click) }
            }
        }
    }
}

private fun copyUri(ctx: Context, uri: Uri, name: String): File {
    val dest = File(ctx.cacheDir, name)
    ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        ?: error("Не удалось прочитать файл")
    if (dest.length() <= 0L) error("Пустой файл")
    return dest
}

private fun salesIsoDay(raw: String): Any {
    val t = raw.trim()
    if (t.isBlank()) return JSONObject.NULL
    val iso = when {
        Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(t) -> t.take(10)
        Regex("""^\d{2}\.\d{2}\.\d{4}$""").matches(t.take(10)) -> {
            val p = t.take(10).split('.')
            "${p[2]}-${p[1]}-${p[0]}"
        }
        else -> t.take(10)
    }
    return "${iso}T00:00:00.000Z"
}

private fun salesBroadcastStatus(raw: String): String = when (raw.trim().uppercase()) {
    "DRAFT" -> "Черновик"
    "WAITING_QUEUE", "QUEUED" -> "В очереди"
    "SCHEDULED" -> "Запланирована"
    "RUNNING" -> "Идёт"
    "DONE", "COMPLETED", "FINISHED" -> "Готово"
    "CANCELLED", "CANCELED" -> "Остановлена"
    "FAILED" -> "Ошибка"
    else -> raw.ifBlank { "—" }
}
