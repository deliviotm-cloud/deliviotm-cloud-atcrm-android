package tm.deliviotm.atcrm

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal val SETTINGS_TABS = listOf(
    "profile" to "Мой профиль",
    "menu" to "Меню",
    "general" to "Общие",
    "monitor" to "Монитор",
    "sms" to "SMS (шлюз)",
    "email" to "Почта",
    "push" to "Push (FCM)",
    "app_stores" to "Магазины приложений",
    "messengers" to "Мессенджеры",
    "users" to "Пользователи",
    "audit" to "Журнал",
    "sales_cities" to "Города (CRM)",
)

internal data class SiteNavItem(
    val to: String,
    val label: String,
    val icon: String,
)

internal val SITE_NAV_ITEMS = listOf(
    SiteNavItem("/dashboard", "Dashboard", "⌂"),
    SiteNavItem("/tasks", "Задачи", "✓"),
    SiteNavItem("/operations", "Операции", "☰"),
    SiteNavItem("/problem-orders", "Проблемные заказы", "!"),
    SiteNavItem("/sales", "Отдел продаж", "◎"),
    SiteNavItem("/couriers", "Курьеры", "›"),
    SiteNavItem("/accounting", "Бухгалтерия", "₸"),
    SiteNavItem("/chats", "Чаты", "◉"),
    SiteNavItem("/mail", "Почта", "✉"),
    SiteNavItem("/support", "Поддержка", "?"),
    SiteNavItem("/callcenter", "Колл-центр", "☎"),
    SiteNavItem("/reports", "Отчёты", "▦"),
    SiteNavItem("/ai", "AI аналитика", "✦"),
    SiteNavItem("/establishments", "Заведения", "⌖"),
    SiteNavItem("/marketing", "Маркетинг", "♡"),
    SiteNavItem("/settings", "Настройки", "⚙"),
)

internal object NavOrderStore {
    private const val PREF = "atcrm"
    const val KEY = "at-crm-nav-order"

    fun load(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.startsWith("/") }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, order: List<String>) {
        val arr = JSONArray()
        order.forEach { arr.put(it) }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun pathsFrom(o: JSONObject): List<String> {
        val arr = o.optJSONArray("paths") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).takeIf { it.startsWith("/") }
        }
    }

    fun sync(ctx: Context, api: KassaApi, token: String, fromUser: List<String> = emptyList()): List<String> {
        if (fromUser.isNotEmpty()) save(ctx, fromUser)
        val local = load(ctx)
        return try {
            val remote = pathsFrom(api.getObject("/auth/me/nav-order", token))
            if (remote.isNotEmpty()) {
                save(ctx, remote)
                remote
            } else {
                if (local.isNotEmpty()) {
                    api.putJson("/auth/me/nav-order", token, JSONObject().put("paths", JSONArray(local)))
                }
                local
            }
        } catch (_: Exception) {
            local
        }
    }

    fun saveAll(ctx: Context, api: KassaApi?, token: String?, order: List<String>) {
        save(ctx, order)
        val a = api ?: return
        val t = token ?: return
        try {
            a.putJson("/auth/me/nav-order", t, JSONObject().put("paths", JSONArray(order)))
        } catch (_: Exception) {
        }
    }

    fun clearAll(ctx: Context, api: KassaApi?, token: String?) {
        clear(ctx)
        val a = api ?: return
        val t = token ?: return
        try {
            a.putJson("/auth/me/nav-order", t, JSONObject().put("paths", JSONArray()))
        } catch (_: Exception) {
        }
    }
}

private val USER_ROLES = listOf(
    "ADMIN" to "Администратор",
    "OPERATOR" to "Оператор",
    "OBSERVER" to "Наблюдатель",
    "CASHIER" to "Кассир",
    "HEAD_COURIER" to "Нач. курьерской службы",
    "COLLCENTER" to "Колл-центр",
    "SALES_HEAD" to "Руководитель отдела продаж",
    "SALES_SENIOR_MANAGER" to "Старший менеджер",
    "SALES_MANAGER_ESTABLISHMENTS" to "Менеджер по заведениям",
    "SALES_MANAGER_STORES" to "Менеджер по магазинам",
    "SALES_CONTENT_ESTABLISHMENTS" to "Контент-менеджер заведений",
    "SALES_CONTENT_STORES" to "Контент-менеджер магазинов",
    "CUSTOM" to "Пользовательская роль",
)

private fun settingsHint(tab: String): String = when (tab) {
    "profile" -> "Фото, контакты и смена пароля"
    "menu" -> "Порядок пунктов бокового меню: общий для сайта и приложений"
    "general" -> "Номер заказа и офисная сеть CRM"
    "monitor" -> "Системные показатели CRM"
    "sms" -> "Шлюз SMS: порты, квота и отправка"
    "email" -> "SMTP и исходящая почта CRM"
    "push" -> "Firebase Cloud Messaging"
    "app_stores" -> "Отдельный импорт CSV: iOS (App Store) и Android (Google Play)"
    "messengers" -> "Telegram-бот поддержки"
    "users" -> "Кто заходит в CRM и какие роли выданы"
    "audit" -> "Аудит действий в CRM"
    "sales_cities" -> "Справочник для отдела продаж и бота логистики Delivio"
    else -> ""
}

internal fun settingsTabsFor(me: AppUser?): List<Pair<String, String>> {
    return SETTINGS_TABS.filter { (id, _) -> canSettingsSection(me, id) }.ifEmpty { SETTINGS_TABS.take(2) }
}

internal fun canSettingsSection(me: AppUser?, id: String): Boolean {
    if (id == "profile" || id == "menu") return true
    if (me == null) return false
    if (me.role.equals("ADMIN", true)) return true
    val perms = me.permissions
    if (perms.isEmpty()) return true
    fun has(key: String) = perms.any { it == key || it.endsWith(".$key") || it.contains(key) }
    return when (id) {
        "general" -> has("settings.read")
        "monitor" -> has("settings.monitor")
        "sms" -> has("sms.use")
        "email" -> has("email.admin")
        "push" -> has("push.marketing")
        "app_stores" -> has("settings.write") || has("settings.read")
        "messengers" -> has("support.telegram_admin")
        "users" -> has("users.admin")
        "audit" -> has("settings.audit")
        "sales_cities" -> has("sales.cities.admin")
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsWorkspacePane(
    api: KassaApi,
    token: String?,
    me: AppUser?,
    initialTab: String = "profile",
    onBack: () -> Unit,
    onUserUpdated: (AppUser) -> Unit,
) {
    val allowed = remember(me) { settingsTabsFor(me) }
    var tab by remember {
        mutableStateOf(if (initialTab in allowed.map { it.first }) initialTab else allowed.firstOrNull()?.first ?: "profile")
    }
    var tick by remember { mutableIntStateOf(0) }
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Настройки", onBack, onRefresh = { tick++ })
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SiteFilterSelect(value = tab, items = allowed, onChange = { tab = it }, label = "Раздел")
            Text(
                settingsHint(tab),
                color = AtColors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "profile" -> ProfileMePane(
                    api = api,
                    token = token,
                    onBack = onBack,
                    onUserUpdated = onUserUpdated,
                    embedded = true,
                )
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { tick++ },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                SettingsSectionBody(
                    tab = tab,
                    api = api,
                    token = token,
                    tick = tick,
                    onRefreshing = { refreshing = it },
                    onReload = { tick++ },
                )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionBody(
    tab: String,
    api: KassaApi,
    token: String?,
    tick: Int,
    onRefreshing: (Boolean) -> Unit,
    onReload: () -> Unit,
) {
    when (tab) {
        "menu" -> SettingsMenuPane(api, token)
        "general" -> SettingsGeneralPane(api, token, tick, onRefreshing)
        "monitor" -> SettingsMonitorPane(api, token, tick, onRefreshing)
        "sms" -> SettingsSmsPane(api, token, tick, onRefreshing, onReload)
        "email" -> SettingsEmailPane(api, token, tick, onRefreshing)
        "push" -> SettingsPushPane(api, token, tick, onRefreshing)
        "app_stores" -> SettingsAppStoresPane(api, token, tick, onRefreshing)
        "messengers" -> SettingsMessengersPane(api, token, tick, onRefreshing)
        "users" -> SettingsUsersPane(api, token, tick, onRefreshing, onReload)
        "audit" -> SettingsAuditPane(api, token, tick, onRefreshing)
        "sales_cities" -> SettingsCitiesPane(api, token, tick, onRefreshing, onReload)
        else -> Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun SettingsMenuPane(api: KassaApi, token: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf(NavOrderStore.load(ctx).ifEmpty { SITE_NAV_ITEMS.map { it.to } }) }
    var msg by remember { mutableStateOf<String?>(null) }
    val byTo = remember { SITE_NAV_ITEMS.associateBy { it.to } }
    val visible = order.mapNotNull { byTo[it] } + SITE_NAV_ITEMS.filter { it.to !in order.toSet() }

    LaunchedEffect(token) {
        val t = token ?: return@LaunchedEffect
        val synced = withContext(Dispatchers.IO) { NavOrderStore.sync(ctx, api, t) }
        order = synced.ifEmpty { SITE_NAV_ITEMS.map { it.to } }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SiteSectionHead("Меню", "Порядок пунктов бокового меню: общий для сайта и приложений") }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        item {
            Text(
                "Нажмите ▲ / ▼, чтобы переставить. Порядок общий для сайта, Android и iPhone.",
                color = AtColors.muted,
                fontSize = 12.sp,
            )
        }
        items(visible, key = { it.to }) { item ->
            val idx = visible.indexOfFirst { it.to == item.to }
            Row(
                Modifier.fillMaxWidth().atCard(14.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.icon, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                Text(item.label, color = AtColors.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "▲",
                    color = if (idx > 0) AtColors.accent else AtColors.muted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = idx > 0) {
                            val next = visible.map { it.to }.toMutableList()
                            val i = next.indexOf(item.to)
                            if (i > 0) {
                                next[i] = next[i - 1].also { next[i - 1] = next[i] }
                                order = next
                                scope.launch {
                                    withContext(Dispatchers.IO) { NavOrderStore.saveAll(ctx, api, token, next) }
                                    msg = "Порядок меню сохранён — так же на сайте"
                                }
                            }
                        }
                        .padding(8.dp),
                )
                Text(
                    "▼",
                    color = if (idx < visible.lastIndex) AtColors.accent else AtColors.muted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = idx < visible.lastIndex) {
                            val next = visible.map { it.to }.toMutableList()
                            val i = next.indexOf(item.to)
                            if (i in 0 until next.lastIndex) {
                                next[i] = next[i + 1].also { next[i + 1] = next[i] }
                                order = next
                                scope.launch {
                                    withContext(Dispatchers.IO) { NavOrderStore.saveAll(ctx, api, token, next) }
                                    msg = "Порядок меню сохранён — так же на сайте"
                                }
                            }
                        }
                        .padding(8.dp),
                )
            }
        }
        item {
            SettingsGhostBtn("Сбросить к порядку по умолчанию") {
                scope.launch {
                    withContext(Dispatchers.IO) { NavOrderStore.clearAll(ctx, api, token) }
                    order = SITE_NAV_ITEMS.map { it.to }
                    msg = "Сброшено к порядку по умолчанию"
                }
            }
        }
    }
}

@Composable
private fun SettingsGeneralPane(api: KassaApi, token: String?, tick: Int, onRefreshing: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("5") }
    var cidrs by remember { mutableStateOf("10.20.15.0/24") }
    var effective by remember { mutableStateOf("") }
    var clientIp by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            val o = withContext(Dispatchers.IO) { api.getObject("/settings", t) }
            digits = KassaApi.pick(o, "orderNumberDigits").ifBlank { "5" }
            val stored = KassaApi.pick(o, "officeNetworkCidrs")
            effective = KassaApi.pick(o, "officeNetworkCidrsEffective")
            cidrs = stored.ifBlank { effective.ifBlank { "10.20.15.0/24" } }
            clientIp = KassaApi.pick(o, "clientIpSeen")
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SiteSectionHead("Общие", "Номер заказа и офисная сеть CRM") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        if (!loaded && err == null) item { LoadingCard() }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    digits, { digits = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Количество цифр в номере заказа") },
                    supportingText = { Text("Допустимо от 4 до 8. Номер должен состоять ровно из стольких цифр.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                OutlinedTextField(
                    cidrs, { cidrs = it },
                    label = { Text("Офисные сети CRM (IP / CIDR)") },
                    placeholder = { Text("10.20.15.0/24") },
                    supportingText = {
                        Text("Сети, из которых сотрудники без «доступа извне» входят в CRM. Несколько сетей — через запятую.")
                    },
                    minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                if (effective.isNotBlank()) Text("Сейчас действует: $effective", color = AtColors.muted, fontSize = 12.sp)
                if (clientIp.isNotBlank()) Text("Ваш IP сейчас (как видит CRM): $clientIp", color = AtColors.muted, fontSize = 12.sp)
                SettingsPrimaryBtn(if (busy) "Сохранение…" else "Сохранить", enabled = !busy) {
                    val n = digits.toIntOrNull()
                    if (n == null || n < 4 || n > 8) {
                        err = "Допустимо от 4 до 8 цифр"
                        return@SettingsPrimaryBtn
                    }
                    val t = token ?: return@SettingsPrimaryBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            val body = JSONObject().put("orderNumberDigits", n).put("officeNetworkCidrs", cidrs.trim())
                            val saved = withContext(Dispatchers.IO) { api.patchJson("/settings", t, body) }
                            effective = KassaApi.pick(saved, "officeNetworkCidrsEffective", "officeNetworkCidrs")
                            msg = "Настройки сохранены. Действует: ${effective.ifBlank { cidrs.trim() }}"
                        } catch (e: Exception) {
                            err = e.message ?: "Ошибка сохранения"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMonitorPane(api: KassaApi, token: String?, tick: Int, onRefreshing: (Boolean) -> Unit) {
    var obj by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var updatedAt by remember { mutableStateOf("") }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            obj = withContext(Dispatchers.IO) { api.getObject("/settings/monitor", t) }
            updatedAt = java.time.LocalTime.now().toString().take(8)
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    val o = obj
    val kpis = listOf(
        "Заведений всего" to o?.opt("establishmentsTotal"),
        "Ресторанов" to o?.opt("restaurantsTotal"),
        "Магазинов" to o?.opt("storesTotal"),
        "Операций" to o?.opt("operationsTotal"),
        "Изменено заведений сегодня" to o?.opt("establishmentsUpdatedToday"),
    )
    val changes = o?.let { jsonObjects(it, "recentChanges") }.orEmpty()
    val edits = o?.let { jsonObjects(it, "recentEstablishmentEdits") }.orEmpty()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SiteSectionHead("Монитор", "Системные показатели CRM") }
        item { Text("Фон обновляется при «Обновить», страница не перезагружается.", color = AtColors.muted, fontSize = 12.sp) }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (!loaded && err == null) item { LoadingCard() }
        if (o != null) {
            items(kpis.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f).atCard(14.dp).padding(14.dp)) {
                            Text(label, color = AtColors.muted, fontSize = 12.sp)
                            Text(
                                KassaApi.prettyNumber(value?.toString().orEmpty()).ifBlank { value?.toString() ?: "—" },
                                color = AtColors.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Последнее изм. заведений: ${KassaApi.prettyTime(KassaApi.pick(o, "lastEstablishmentUpdate")).ifBlank { "—" }}", color = AtColors.text, fontSize = 13.sp)
                    Text("Последняя операция: ${KassaApi.prettyTime(KassaApi.pick(o, "lastOperationCreatedAt")).ifBlank { "—" }}", color = AtColors.text, fontSize = 13.sp)
                    if (updatedAt.isNotBlank()) Text("Последнее обновление монитора: $updatedAt", color = AtColors.muted, fontSize = 12.sp)
                }
            }
            if (changes.isNotEmpty()) {
                item { Text("Последние изменения", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                items(changes.take(40)) { row ->
                    val who = KassaApi.pick(row, "actorName").ifBlank { actorName(row) }
                    val what = KassaApi.pick(row, "actionLabel", "action")
                    val subject = listOf(
                        KassaApi.pick(row, "orderNumber").takeIf { it.isNotBlank() }?.let { "#$it" },
                        KassaApi.pick(row, "subject").takeIf { it.isNotBlank() && it != "—" },
                    ).filterNotNull().joinToString(" · ").ifBlank { "—" }
                    Column(Modifier.fillMaxWidth().atCard(12.dp).padding(12.dp)) {
                        Text("${KassaApi.prettyTime(KassaApi.pick(row, "createdAt"))} · $who", color = AtColors.muted, fontSize = 12.sp)
                        Text(what.ifBlank { "Изменение" }, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                        Text("$subject\n${KassaApi.pick(row, "summary").ifBlank { "—" }}", color = AtColors.muted, fontSize = 12.sp)
                    }
                }
            } else if (edits.isNotEmpty()) {
                item { Text("Последние изменения %", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                items(edits.take(40)) { row ->
                    val prev = KassaApi.pick(row, "previousPercent")
                    val cur = KassaApi.pick(row, "commissionPercent")
                    val pct = if (prev.isNotBlank() && prev != cur) "$prev% → $cur%" else "${cur.ifBlank { "—" }}%"
                    Column(Modifier.fillMaxWidth().atCard(12.dp).padding(12.dp)) {
                        Text(KassaApi.pick(row, "name").ifBlank { "Заведение" }, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                        Text("${KassaApi.pick(row, "type")} · $pct · ${KassaApi.pick(row, "actorName").ifBlank { "—" }}", color = AtColors.muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSmsPane(
    api: KassaApi,
    token: String?,
    tick: Int,
    onRefreshing: (Boolean) -> Unit,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var quota by remember { mutableStateOf<JSONObject?>(null) }
    var ports by remember { mutableStateOf(mapOf("port1" to true, "port2" to false, "port3" to false, "port4" to false)) }
    var dest by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            val st = withContext(Dispatchers.IO) { runCatching { api.getObject("/sms/status", t) }.getOrNull() }
            val qt = withContext(Dispatchers.IO) { runCatching { api.getObject("/sms/quota?scope=broadcast", t) }.getOrNull() }
            status = st
            quota = qt
            val nested = st?.optJSONObject("gsmPortsBroadcast") ?: st?.optJSONObject("gsmPortsEnabled")
            if (nested != null) {
                ports = mapOf(
                    "port1" to nested.optBoolean("port1", true),
                    "port2" to nested.optBoolean("port2", false),
                    "port3" to nested.optBoolean("port3", false),
                    "port4" to nested.optBoolean("port4", false),
                )
            }
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    val used = KassaApi.pick(quota ?: JSONObject(), "usedTotal", "used")
    val max = KassaApi.pick(quota ?: JSONObject(), "maxTotal", "max")
    val enabledN = KassaApi.pick(quota ?: JSONObject(), "enabledPortCount")

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SiteSectionHead("SMS (шлюз)", "Шлюз SMS: порты, квота и отправка") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        if (!loaded && err == null) item { LoadingCard() }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Статус шлюза", color = AtColors.text, fontWeight = FontWeight.Bold)
                val ready = status?.optBoolean("ready", status?.optBoolean("ok", false) == true) == true ||
                    KassaApi.pick(status ?: JSONObject(), "status", "state").contains("ok", true)
                Text(
                    if (status == null) "—" else if (ready) "готов" else KassaApi.pick(status!!, "status", "state").ifBlank { "настроен" },
                    color = AtColors.text,
                )
                if (used.isNotBlank() || max.isNotBlank()) {
                    Text("Квота: ${used.ifBlank { "0" }} / ${max.ifBlank { "—" }}${if (enabledN.isNotBlank()) " · портов: $enabledN" else ""}", color = AtColors.muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("GSM-порты (рассылка)", color = AtColors.text, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ports.keys.sorted().forEach { key ->
                        val on = ports[key] == true
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (on) AtColors.accentSoft else AtColors.panel)
                                .border(1.dp, if (on) AtColors.accent.copy(alpha = 0.45f) else AtColors.stroke, RoundedCornerShape(999.dp))
                                .clickable(enabled = !busy) {
                                    val next = ports.toMutableMap()
                                    next[key] = !on
                                    if (next.values.none { it }) {
                                        err = "Нужен хотя бы один активный порт"
                                        return@clickable
                                    }
                                    val t = token ?: return@clickable
                                    busy = true
                                    scope.launch {
                                        try {
                                            val body = JSONObject()
                                            next.forEach { (k, v) -> body.put(k, v) }
                                            withContext(Dispatchers.IO) { api.patchJson("/sms/gsm-ports?scope=broadcast", t, body) }
                                            ports = next
                                            msg = "Порты сохранены"
                                            onReload()
                                        } catch (e: Exception) {
                                            err = e.message ?: "Ошибка сохранения портов"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(key.removePrefix("port"), color = if (on) AtColors.accent else AtColors.muted, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Отправить SMS", color = AtColors.text, fontWeight = FontWeight.Bold)
                OutlinedTextField(dest, { dest = it }, label = { Text("Номер") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(text, { text = it }, label = { Text("Текст") }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                SettingsPrimaryBtn(if (busy) "…" else "Отправить", enabled = !busy && dest.isNotBlank() && text.isNotBlank()) {
                    val t = token ?: return@SettingsPrimaryBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.sendGatewaySms(t, dest, text, portsScope = "broadcast") }
                            msg = "SMS отправлено"
                            text = ""
                        } catch (e: Exception) {
                            err = e.message ?: "Ошибка отправки"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsEmailPane(api: KassaApi, token: String?, tick: Int, onRefreshing: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf("GMAIL") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("587") }
    var from by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(false) }
    var testTo by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            val o = withContext(Dispatchers.IO) { api.getObject("/email/status", t) }
            provider = KassaApi.pick(o, "provider").ifBlank { "GMAIL" }
            host = KassaApi.pick(o, "smtpHost")
            port = KassaApi.pick(o, "smtpPort").ifBlank { "587" }
            from = KassaApi.pick(o, "fromAddress")
            user = KassaApi.pick(o, "smtpUser")
            enabled = o.optBoolean("enabled", false)
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SiteSectionHead("Почта", "SMTP и исходящая почта CRM") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        if (!loaded && err == null) item { LoadingCard() }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Провайдер", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(
                    value = provider,
                    items = listOf("GMAIL" to "Gmail", "YANDEX" to "Yandex", "MAILRU" to "Mail.ru", "CUSTOM" to "Свой SMTP"),
                    onChange = { provider = it },
                )
                OutlinedTextField(from, { from = it }, label = { Text("От кого") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(user, { user = it }, label = { Text("SMTP-логин") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(
                    password, { password = it }, label = { Text("SMTP-пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                if (provider == "CUSTOM") {
                    OutlinedTextField(host, { host = it }, label = { Text("SMTP-хост") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(port, { port = it.filter { ch -> ch.isDigit() } }, label = { Text("Порт") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                }
                SiteSegmented(
                    value = if (enabled) "on" else "off",
                    items = listOf("on" to "Включена", "off" to "Выключена"),
                    onChange = { enabled = it == "on" },
                )
                SettingsPrimaryBtn(if (busy) "…" else "Сохранить", enabled = !busy) {
                    val t = token ?: return@SettingsPrimaryBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            val body = JSONObject()
                                .put("provider", provider)
                                .put("fromAddress", from.trim())
                                .put("smtpUser", user.trim())
                                .put("enabled", enabled)
                            if (provider == "CUSTOM") {
                                body.put("smtpHost", host.trim()).put("smtpPort", port.toIntOrNull() ?: 587)
                            }
                            if (password.isNotBlank()) body.put("smtpPassword", password)
                            withContext(Dispatchers.IO) { api.patchJson("/email/config", t, body) }
                            password = ""
                            msg = "Настройки почты сохранены"
                        } catch (e: Exception) {
                            err = e.message ?: "Ошибка сохранения"
                        } finally {
                            busy = false
                        }
                    }
                }
                SettingsGhostBtn("Проверить SMTP") {
                    val t = token ?: return@SettingsGhostBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            val o = withContext(Dispatchers.IO) { api.postJson("/email/verify", t, JSONObject()) }
                            msg = KassaApi.pick(o, "message").ifBlank { "SMTP-подключение успешно" }
                        } catch (e: Exception) {
                            err = e.message ?: "SMTP не прошёл проверку"
                        } finally {
                            busy = false
                        }
                    }
                }
                OutlinedTextField(testTo, { testTo = it }, label = { Text("Тестовое письмо на") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                SettingsGhostBtn("Отправить тест") {
                    val t = token ?: return@SettingsGhostBtn
                    if (testTo.isBlank()) {
                        err = "Укажите адрес"
                        return@SettingsGhostBtn
                    }
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.postJson("/email/test", t, JSONObject().put("to", testTo.trim())) }
                            msg = "Тестовое письмо отправлено"
                        } catch (e: Exception) {
                            err = e.message ?: "Не удалось отправить тест"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPushPane(api: KassaApi, token: String?, tick: Int, onRefreshing: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var json by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            status = withContext(Dispatchers.IO) { runCatching { api.getObject("/push/status", t) }.getOrNull() }
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    val st = status
    val fb = st?.optJSONObject("firebase")
    val ready = st?.optBoolean("fcmReady", false) == true
    val project = fb?.let { KassaApi.pick(it, "projectId") }.orEmpty()
    val email = fb?.let { KassaApi.pick(it, "clientEmail") }.orEmpty()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SiteSectionHead("Push (FCM)", "Firebase Cloud Messaging") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        if (!loaded && err == null) item { LoadingCard() }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Интеграция Firebase", color = AtColors.text, fontWeight = FontWeight.Bold)
                Text(
                    "Статус FCM: ${if (st == null) "—" else if (ready) "готов" else "не настроен"}" +
                        (if (project.isNotBlank()) " · project: $project" else "") +
                        (if (email.isNotBlank()) " · $email" else ""),
                    color = AtColors.muted,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    json, { json = it },
                    label = { Text("JSON сервисного аккаунта") },
                    placeholder = { Text("{ \"type\": \"service_account\", ... }") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                SettingsPrimaryBtn(if (busy) "Сохранение…" else "Сохранить и включить FCM", enabled = !busy && json.isNotBlank()) {
                    val t = token ?: return@SettingsPrimaryBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                api.postJson("/push/firebase-credentials", t, JSONObject().put("serviceAccountJson", json.trim()))
                            }
                            json = ""
                            msg = "FCM сохранён"
                        } catch (e: Exception) {
                            err = e.message ?: "Ошибка сохранения"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAppStoresPane(api: KassaApi, token: String?, tick: Int, onRefreshing: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var issuerId by remember { mutableStateOf("") }
    var keyId by remember { mutableStateOf("") }
    var bundleId by remember { mutableStateOf("") }
    var vendorNumber by remember { mutableStateOf("") }
    var pem by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var reportBucket by remember { mutableStateOf("") }
    var playJson by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun applyStatus(o: JSONObject) {
        status = o
        val asc = o.optJSONObject("appStore") ?: JSONObject()
        val play = o.optJSONObject("play") ?: JSONObject()
        issuerId = KassaApi.pick(asc, "issuerId")
        keyId = KassaApi.pick(asc, "keyId")
        bundleId = KassaApi.pick(asc, "bundleId")
        vendorNumber = KassaApi.pick(asc, "vendorNumber")
        packageName = KassaApi.pick(play, "packageName")
        reportBucket = KassaApi.pick(play, "reportBucket")
    }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            applyStatus(withContext(Dispatchers.IO) { api.getObject("/app-stores/status", t) })
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    val pickIos = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        importCsv(ctx, api, token, uri, "APPLE", scope, { busy = it }, { err = it }, { msg = it })
    }
    val pickPlay = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        importCsv(ctx, api, token, uri, "GOOGLE", scope, { busy = it }, { err = it }, { msg = it })
    }

    val asc = status?.optJSONObject("appStore")
    val play = status?.optJSONObject("play")
    val ascOk = asc?.optBoolean("configured", false) == true
    val playOk = play?.optBoolean("configured", false) == true
    val ascKey = asc?.optBoolean("keyStored", false) == true
    val playKey = play?.optBoolean("keyStored", false) == true

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SiteSectionHead("Магазины приложений", "Отдельный импорт CSV: iOS (App Store) и Android (Google Play)") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        if (!loaded && err == null) item { LoadingCard() }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("App Store Connect — ключи API (опционально)", color = AtColors.text, fontWeight = FontWeight.Bold)
                Text(
                    "App Store Connect: ${if (ascOk) "привязан" else "не настроен"}" + if (ascKey) " · ключ сохранён" else "",
                    color = AtColors.muted,
                    fontSize = 12.sp,
                )
                OutlinedTextField(issuerId, { issuerId = it }, label = { Text("Issuer ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(keyId, { keyId = it }, label = { Text("Key ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(bundleId, { bundleId = it }, label = { Text("Bundle ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(vendorNumber, { vendorNumber = it }, label = { Text("Vendor Number") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(
                    pem, { pem = it },
                    label = { Text(".p8") },
                    placeholder = { Text(if (ascKey) "Ключ уже сохранён — вставьте новый .p8 только для замены" else "-----BEGIN PRIVATE KEY-----") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                SettingsPrimaryBtn(if (busy) "…" else "Сохранить App Store", enabled = !busy) {
                    val t = token ?: return@SettingsPrimaryBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            val body = JSONObject()
                                .put("issuerId", issuerId.trim())
                                .put("keyId", keyId.trim())
                                .put("bundleId", bundleId.trim())
                                .put("vendorNumber", vendorNumber.trim())
                            if (pem.isNotBlank()) body.put("privateKeyPem", pem.trim())
                            val saved = withContext(Dispatchers.IO) { api.patchJson("/app-stores/app-store", t, body) }
                            applyStatus(saved)
                            pem = ""
                            msg = "App Store Connect сохранён."
                        } catch (e: Exception) {
                            err = e.message ?: "Ошибка сохранения App Store"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Google Play — ключи API (опционально)", color = AtColors.text, fontWeight = FontWeight.Bold)
                Text(
                    "Google Play: ${if (playOk) "привязан" else "не настроен"}" +
                        (play?.let { KassaApi.pick(it, "projectId") }?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                        if (playKey) " · ключ сохранён" else "",
                    color = AtColors.muted,
                    fontSize = 12.sp,
                )
                OutlinedTextField(packageName, { packageName = it }, label = { Text("Package name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(reportBucket, { reportBucket = it }, label = { Text("Report bucket") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(
                    playJson, { playJson = it },
                    label = { Text("JSON сервисного аккаунта") },
                    placeholder = { Text(if (playKey) "JSON уже сохранён — вставьте новый только для замены" else "{ ... }") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                SettingsPrimaryBtn(if (busy) "…" else "Сохранить Google Play", enabled = !busy) {
                    val t = token ?: return@SettingsPrimaryBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            val body = JSONObject().put("packageName", packageName.trim()).put("reportBucket", reportBucket.trim())
                            if (playJson.isNotBlank()) body.put("serviceAccountJson", playJson.trim())
                            val saved = withContext(Dispatchers.IO) { api.patchJson("/app-stores/play", t, body) }
                            applyStatus(saved)
                            playJson = ""
                            msg = "Google Play сохранён."
                        } catch (e: Exception) {
                            err = e.message ?: "Ошибка сохранения Google Play"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Импорт отчётов (CSV)", color = AtColors.text, fontWeight = FontWeight.Bold)
                Text("iOS и Android загружаются отдельно — разные файлы в разные блоки.", color = AtColors.muted, fontSize = 12.sp)
                SettingsGhostBtn("Загрузить в iOS") { pickIos.launch("text/*") }
                SettingsGhostBtn("Загрузить в Android") { pickPlay.launch("text/*") }
            }
        }
    }
}

private fun importCsv(
    ctx: Context,
    api: KassaApi,
    token: String?,
    uri: Uri?,
    platform: String,
    scope: kotlinx.coroutines.CoroutineScope,
    setBusy: (Boolean) -> Unit,
    setErr: (String?) -> Unit,
    setMsg: (String?) -> Unit,
) {
    val t = token ?: return
    if (uri == null) {
        setErr("Выберите CSV-файл")
        return
    }
    setBusy(true)
    setErr(null)
    setMsg(null)
    scope.launch {
        try {
            val file = withContext(Dispatchers.IO) { copySettingsUri(ctx, uri, if (platform == "APPLE") "ios.csv" else "play.csv") }
            val path = if (platform == "APPLE") "/app-stores/import/apple" else "/app-stores/import/google"
            val res = withContext(Dispatchers.IO) { api.uploadMultipart(path, t, file, "file", "text/csv") }
            val days = KassaApi.pick(res, "days")
            val total = KassaApi.pick(res, "totalDownloads", "total")
            val from = KassaApi.pick(res, "from")
            val to = KassaApi.pick(res, "to")
            val platformLabel = if (platform == "APPLE") "iOS / App Store" else "Android / Google Play"
            val warn = KassaApi.pick(res, "warning")
            val line = "$platformLabel: импортировано ${days.ifBlank { "—" }} дн., сумма ${total.ifBlank { "—" }} (${from.ifBlank { "—" }} — ${to.ifBlank { "—" }})"
            setMsg(if (warn.isNotBlank()) "$line\n\n$warn" else line)
        } catch (e: Exception) {
            setErr(e.message ?: "Ошибка импорта CSV")
        } finally {
            setBusy(false)
        }
    }
}

@Composable
private fun SettingsMessengersPane(api: KassaApi, token: String?, tick: Int, onRefreshing: (Boolean) -> Unit) {
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
        onRefreshing(loaded)
        err = null
        try {
            val o = withContext(Dispatchers.IO) { api.getObject("/support/admin/telegram", t) }
            obj = o
            webhook = KassaApi.pick(o, "webhookUrl").ifBlank { webhook }
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка"
        } finally {
            onRefreshing(false)
        }
    }

    val o = obj
    val configured = o?.optBoolean("tokenConfigured", false) == true
    val preview = o?.let { KassaApi.pick(it, "tokenPreview") }.orEmpty()
    val pending = o?.opt("pendingUpdateCount")?.toString().orEmpty()
    val lastErr = o?.let { KassaApi.pick(it, "lastError") }.orEmpty()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SiteSectionHead("Мессенджеры", "Telegram-бот поддержки") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        if (!loaded && err == null) item { LoadingCard() }
        item {
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
                    placeholder = { Text("123456:ABC…") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                SettingsPrimaryBtn("Сохранить токен", enabled = !busy && botToken.isNotBlank()) {
                    val t = token ?: return@SettingsPrimaryBtn
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
                            err = e.message ?: "Не удалось сохранить токен"
                        } finally {
                            busy = false
                        }
                    }
                }
                OutlinedTextField(webhook, { webhook = it }, label = { Text("Webhook URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                SettingsGhostBtn("Обновить webhook") {
                    val t = token ?: return@SettingsGhostBtn
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
                            err = e.message ?: "Не удалось обновить webhook"
                        } finally {
                            busy = false
                        }
                    }
                }
                OutlinedTextField(broadcast, { broadcast = it }, label = { Text("Рассылка во все диалоги") }, minLines = 3, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                SettingsGhostBtn("Отправить рассылку") {
                    val t = token ?: return@SettingsGhostBtn
                    if (broadcast.isBlank()) return@SettingsGhostBtn
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
                            err = e.message ?: "Не удалось отправить рассылку"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsUsersPane(
    api: KassaApi,
    token: String?,
    tick: Int,
    onRefreshing: (Boolean) -> Unit,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("OPERATOR") }
    var openId by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            rows = withContext(Dispatchers.IO) { api.getRows("/users", t) }
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    val filtered = rows.filter { row ->
        val n = q.trim()
        if (n.isBlank()) true
        else listOf(row.title, row.subtitle, KassaApi.pick(row.raw, "username", "role", "fullName", "email"))
            .any { it.contains(n, true) }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SiteSectionHead("Пользователи", "Кто заходит в CRM и какие роли выданы") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        item {
            OutlinedTextField(q, { q = it }, placeholder = { Text("Имя, роль, логин") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Новый пользователь", color = AtColors.text, fontWeight = FontWeight.Bold)
                OutlinedTextField(fullName, { fullName = it }, label = { Text("ФИО") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(username, { username = it }, label = { Text("Логин") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(
                    password, { password = it }, label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                )
                Text("Роль доступа", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(value = role, items = USER_ROLES.take(6), onChange = { role = it })
                SettingsPrimaryBtn("Создать", enabled = !busy) {
                    if (fullName.isBlank() || username.isBlank() || password.length < 6) {
                        err = "Заполните ФИО, логин и пароль (не короче 6 символов)"
                        return@SettingsPrimaryBtn
                    }
                    val t = token ?: return@SettingsPrimaryBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                api.postJson(
                                    "/users",
                                    t,
                                    JSONObject()
                                        .put("fullName", fullName.trim())
                                        .put("username", username.trim())
                                        .put("password", password)
                                        .put("role", role),
                                )
                            }
                            fullName = ""
                            username = ""
                            password = ""
                            role = "OPERATOR"
                            msg = "Пользователь создан"
                            onReload()
                        } catch (e: Exception) {
                            err = e.message ?: "Ошибка создания пользователя"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
        item { Text("Всего: ${filtered.size}", color = AtColors.text, fontWeight = FontWeight.SemiBold) }
        if (!loaded && err == null) item { LoadingCard() }
        items(filtered, key = { it.id }) { row ->
            val active = row.raw.optBoolean("isActive", true)
            val roleCode = KassaApi.pick(row.raw, "role")
            val roleLabel = USER_ROLES.find { it.first == roleCode }?.second ?: roleCode
            val uname = KassaApi.pick(row.raw, "username")
            Column(Modifier.fillMaxWidth().atCard(14.dp).clickable { openId = if (openId == row.id) null else row.id }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        name = row.title.ifBlank { uname },
                        avatarUrl = KassaApi.avatarUrlOf(row.raw),
                        api = api,
                        token = token,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row.title.ifBlank { uname.ifBlank { "Пользователь" } }, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                        Text("@$uname · $roleLabel${if (!active) " · выключен" else ""}", color = AtColors.muted, fontSize = 12.sp)
                    }
                }
                if (openId == row.id) {
                    Spacer(Modifier.height(10.dp))
                    SettingsGhostBtn(if (active) "Выключить" else "Включить") {
                        val t = token ?: return@SettingsGhostBtn
                        busy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    api.patchJson("/users/${row.id}/status", t, JSONObject().put("isActive", !active))
                                }
                                msg = "Статус обновлён"
                                onReload()
                            } catch (e: Exception) {
                                err = e.message ?: "Не удалось обновить статус"
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAuditPane(api: KassaApi, token: String?, tick: Int, onRefreshing: (Boolean) -> Unit) {
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var entity by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(token, tick, entity, action) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            val q = StringBuilder("/settings/audit/logs?take=200&skip=0")
            if (entity.isNotBlank()) q.append("&entity=").append(java.net.URLEncoder.encode(entity.trim(), "UTF-8"))
            if (action.isNotBlank()) q.append("&action=").append(java.net.URLEncoder.encode(action.trim(), "UTF-8"))
            val o = withContext(Dispatchers.IO) { api.getObject(q.toString(), t) }
            val packed = KassaApi.pagedRows(o)
            rows = packed.items.ifEmpty { KassaApi.rowsFrom(o.toString()) }
            total = packed.total.takeIf { it > 0 } ?: rows.size
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SiteSectionHead("Журнал", "Аудит действий в CRM") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(entity, { entity = it }, label = { Text("Сущность") }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors())
                OutlinedTextField(action, { action = it }, label = { Text("Действие") }, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors())
            }
        }
        item { Text("Всего: $total", color = AtColors.text, fontWeight = FontWeight.SemiBold) }
        if (!loaded && err == null) item { LoadingCard() }
        items(rows, key = { it.id.ifBlank { it.hashCode().toString() } }) { row ->
            val who = actorName(row.raw)
            val ent = KassaApi.pick(row.raw, "entity", "entityType")
            val act = KassaApi.pick(row.raw, "action", "event")
            val eid = KassaApi.pick(row.raw, "entityId")
            val stamp = KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "at"))
            Column(Modifier.fillMaxWidth().atCard(12.dp).padding(12.dp)) {
                Text("$stamp · ${who.ifBlank { "—" }}", color = AtColors.muted, fontSize = 12.sp)
                Text("${ent.ifBlank { "—" }} · ${act.ifBlank { row.title }}", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                if (eid.isNotBlank()) Text(eid, color = AtColors.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsCitiesPane(
    api: KassaApi,
    token: String?,
    tick: Int,
    onRefreshing: (Boolean) -> Unit,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var delivioId by remember { mutableStateOf("") }
    var cityKey by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(token, tick) {
        val t = token ?: return@LaunchedEffect
        onRefreshing(loaded)
        err = null
        try {
            rows = withContext(Dispatchers.IO) { api.getRows("/sales/establishments/cities?all=1", t) }
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка загрузки"
        } finally {
            onRefreshing(false)
        }
    }

    val active = rows.count { !it.raw.optBoolean("archived", false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SiteSectionHead("Города (CRM)", "Справочник для отдела продаж и бота логистики Delivio") }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        item {
            Text("Менеджеры выбирают город только из списка. Активных городов: $active. Порядок — по алфавиту.", color = AtColors.muted, fontSize = 12.sp)
        }
        item {
            Text("После сохранения бот ходит на /be/logistic/city/<id>. Ашхабад — отдельный юнит.", color = AtColors.muted, fontSize = 12.sp)
        }
        item {
            Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Новый город") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(delivioId, { delivioId = it.filter { ch -> ch.isDigit() } }, label = { Text("ID в Delivio") }, supportingText = { Text("Число из адреса delivio.com.tm/be/logistic/city/11") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(cityKey, { cityKey = it }, label = { Text("Ключ бота") }, placeholder = { Text("mary, turkmenabat…") }, supportingText = { Text("Необязательно. Пусто — из названия.") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                SettingsPrimaryBtn("Добавить", enabled = !busy && name.isNotBlank() && (delivioId.toIntOrNull() ?: 0) > 0) {
                    val t = token ?: return@SettingsPrimaryBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            val body = JSONObject().put("name", name.trim()).put("delivioId", delivioId.toInt())
                            if (cityKey.isNotBlank()) body.put("cityKey", cityKey.trim())
                            withContext(Dispatchers.IO) { api.postJson("/sales/establishments/cities", t, body) }
                            name = ""
                            delivioId = ""
                            cityKey = ""
                            msg = "Город добавлен"
                            onReload()
                        } catch (e: Exception) {
                            err = e.message ?: "Не удалось добавить город"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
        if (!loaded && err == null) item { LoadingCard() }
        items(rows.sortedBy { it.title.lowercase() }, key = { it.id }) { row ->
            val archived = row.raw.optBoolean("archived", false)
            val did = KassaApi.pick(row.raw, "delivioId", "id")
            val key = KassaApi.pick(row.raw, "cityKey", "key")
            Column(Modifier.fillMaxWidth().atCard(12.dp).padding(12.dp)) {
                Text(row.title.ifBlank { KassaApi.pick(row.raw, "name") }, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf("ID $did", key.takeIf { it.isNotBlank() }?.let { "ключ $it" }, if (archived) "скрыт" else "активен")
                        .filterNotNull().joinToString(" · "),
                    color = AtColors.muted,
                    fontSize = 12.sp,
                )
                SettingsGhostBtn(if (archived) "Вернуть в список" else "Скрыть из выбора") {
                    val t = token ?: return@SettingsGhostBtn
                    busy = true
                    err = null
                    msg = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                api.patchJson("/sales/establishments/cities/${row.id}", t, JSONObject().put("archived", !archived))
                            }
                            msg = if (archived) "Город снова в списке" else "Город скрыт из выбора"
                            onReload()
                        } catch (e: Exception) {
                            err = e.message ?: "Не удалось обновить город"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPrimaryBtn(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) AtColors.accent else AtColors.panel)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else AtColors.muted, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsGhostBtn(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AtColors.panel)
            .border(1.dp, AtColors.stroke, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = AtColors.accent, fontWeight = FontWeight.SemiBold)
    }
}

private fun jsonObjects(o: JSONObject, key: String): List<JSONObject> {
    val a = o.optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}

private fun actorName(raw: JSONObject): String {
    raw.optJSONObject("actor")?.let {
        return KassaApi.pick(it, "fullName", "username", "name")
    }
    return KassaApi.pick(raw, "actorName", "actor", "userName", "fullName", "email")
}

private fun copySettingsUri(ctx: Context, uri: Uri, name: String): File {
    val dest = File(ctx.cacheDir, name)
    ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        ?: error("Не удалось прочитать файл")
    if (dest.length() <= 0L) error("Пустой файл")
    return dest
}
