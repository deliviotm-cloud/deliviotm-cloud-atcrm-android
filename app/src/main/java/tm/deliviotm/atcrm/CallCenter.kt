package tm.deliviotm.atcrm

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal suspend fun yeastarDial(
    ctx: Context,
    api: KassaApi,
    token: String,
    toNumber: String,
    fromNumber: String = "",
    announceRecording: Boolean = false,
): String {
    val to = toNumber.trim()
    if (to.isBlank()) error("Укажите номер")
    val n = withContext(Dispatchers.IO) {
        api.dialCall(token, to, fromNumber, announceRecording)
    }
    val uri = KassaApi.pick(n, "dialUri")
    val msg = KassaApi.apiMessage(n)
    if (uri.startsWith("tel:", ignoreCase = true)) {
        val phone = uri.removePrefix("tel:").removePrefix("TEL:")
        DeviceIntents.dial(ctx, phone.ifBlank { to })
        return msg.ifBlank { "АТС не настроена — открыт набор на телефоне" }
    }
    if (n.has("ok") && !n.isNull("ok") && !n.optBoolean("ok", true) && uri.isBlank()) {
        error(msg.ifBlank { "Не удалось набрать через АТС" })
    }
    return msg.ifBlank { "Вызов отправлен на АТС Yeastar" }
}

internal fun smsPeer(row: JsonRow, incoming: Boolean): String {
    return if (incoming) {
        KassaApi.pick(row.raw, "fromNumber", "from", "phone", "sender", "destination").ifBlank { row.title }
    } else {
        KassaApi.pick(row.raw, "destination", "toNumber", "to", "phone").ifBlank { row.title }
    }
}

internal fun smsBody(row: JsonRow): String {
    return KassaApi.pick(row.raw, "message", "text", "body").ifBlank { row.subtitle }
}

internal fun smsPortMeta(row: JsonRow): String {
    val port = KassaApi.pick(row.raw, "gsmPort", "port")
    return if (port.isNotBlank()) "порт $port" else ""
}

internal fun smsOkFlag(row: JsonRow): Boolean? {
    return when {
        row.raw.has("ok") && !row.raw.isNull("ok") -> row.raw.optBoolean("ok")
        else -> null
    }
}

private fun smsQuotaLine(quota: JSONObject?): String {
    if (quota == null || quota.length() == 0) return ""
    val max = when {
        quota.has("maxTotal") -> quota.optInt("maxTotal")
        quota.has("max") -> quota.optInt("max")
        else -> {
            val per = quota.optInt("maxPerPort", 60)
            val n = quota.optInt("enabledPortCount", 4)
            n * per
        }
    }
    val used = when {
        quota.has("usedTotal") -> quota.optInt("usedTotal")
        quota.has("used") -> quota.optInt("used")
        else -> 0
    }
    return "Осталось SMS: ${max - used} / $max"
}

private fun smsGatewayLine(status: JSONObject?): String {
    if (status == null) return "Шлюз: …"
    val configured = status.optBoolean("configured", false)
    val reachable = status.optBoolean("reachable", true)
    return "Шлюз: " + when {
        !configured -> "не настроен"
        !reachable -> "недоступен"
        else -> "подключён"
    }
}

private fun callBackendLine(status: JSONObject?): String {
    if (status == null) return ""
    return when (KassaApi.pick(status, "dialBackend").lowercase()) {
        "http" -> "Исходящие через АТС Yeastar"
        "tel" -> "Только tel: — звонок уйдёт с телефона"
        else -> "Исходящий набор не настроен на сервере"
    }
}

private fun gsmPortsFrom(status: JSONObject?): Map<String, Boolean> {
    val nested = status?.optJSONObject("gsmPortsCallcenter")
        ?: status?.optJSONObject("gsmPortsEnabled")
        ?: status?.optJSONObject("gsmPortsBroadcast")
    return mapOf(
        "port1" to (nested?.optBoolean("port1", true) ?: true),
        "port2" to (nested?.optBoolean("port2", true) ?: true),
        "port3" to (nested?.optBoolean("port3", false) ?: false),
        "port4" to (nested?.optBoolean("port4", false) ?: false),
    )
}

private fun smsByteHint(text: String): String {
    val bytes = text.toByteArray(Charsets.UTF_8).size
    val ascii = text.all { it.code <= 127 }
    val first = if (ascii) 160 else 70
    val next = if (ascii) 153 else 67
    val parts = when {
        text.isBlank() -> 0
        text.length <= first -> 1
        else -> (text.length + next - 1) / next
    }
    return if (parts <= 1) "$bytes байт" else "$bytes байт · ~$parts SMS у оператора (склеится)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallKeypadPane(api: KassaApi?, token: String?, embedded: Boolean = false) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("") }
    var fromNumber by remember { mutableStateOf(prefs.getString("call_from_number", "").orEmpty()) }
    var announce by remember { mutableStateOf(prefs.getBoolean("call_announce_recording", false)) }
    var smsText by remember { mutableStateOf("") }
    var showSms by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    val keys = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to ""),
    )

    LaunchedEffect(token) {
        if (api == null || token.isNullOrBlank()) return@LaunchedEffect
        status = withContext(Dispatchers.IO) {
            runCatching { api.getObject("/calls/status", token) }.getOrNull()
        }
    }

    fun runDial() {
        val client = api
        val t = token
        if (digits.isBlank()) {
            err = "Укажите номер"
            return
        }
        if (client == null || t.isNullOrBlank()) {
            err = "Нет сессии"
            return
        }
        busy = true
        err = null
        msg = null
        scope.launch {
            try {
                prefs.edit()
                    .putString("call_from_number", fromNumber.trim())
                    .putBoolean("call_announce_recording", announce)
                    .apply()
                msg = yeastarDial(ctx, client, t, digits, fromNumber, announce)
            } catch (e: Exception) {
                err = e.message ?: "Ошибка набора"
            } finally {
                busy = false
            }
        }
    }

    fun runSms() {
        val client = api
        val t = token
        if (digits.isBlank() || smsText.isBlank()) {
            err = "Нужны номер и текст SMS"
            return
        }
        if (client == null || t.isNullOrBlank()) {
            err = "Нет сессии"
            return
        }
        busy = true
        err = null
        msg = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { client.sendGatewaySms(t, digits, smsText.trim()) }
                msg = "SMS отправлено через шлюз Yeastar"
                smsText = ""
            } catch (e: Exception) {
                err = e.message ?: "Ошибка отправки SMS"
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(AtColors.bgDeep),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!embedded) {
            item { SiteSectionHead("Клавиши", "Набор через АТС Yeastar, SMS — через GSM-шлюз, не с телефона") }
        }
        if (callBackendLine(status).isNotBlank()) {
            item {
                Text(callBackendLine(status), color = AtColors.muted, fontSize = 13.sp)
            }
        }
        if (err != null) item { ActionBanner(err!!, error = true) }
        if (msg != null) item { ActionBanner(msg!!, error = false) }
        item {
            OutlinedTextField(
                fromNumber,
                {
                    fromNumber = it
                    prefs.edit().putString("call_from_number", it).apply()
                },
                label = { Text("Номер АТС / с какого звонить") },
                placeholder = { Text("необязательно") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        }
        item {
            Text(
                digits.ifBlank { "Введите номер" },
                color = if (digits.isBlank()) AtColors.muted else AtColors.text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (digits.isNotBlank()) {
            item {
                Text(
                    "Стереть",
                    color = AtColors.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { digits = digits.dropLast(1) }
                        .padding(8.dp),
                )
            }
        }
        keys.forEach { row ->
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (key, letters) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AtColors.panel)
                                .border(1.dp, AtColors.stroke, RoundedCornerShape(16.dp))
                                .clickable { digits += key },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(key, color = AtColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            if (letters.isNotBlank()) {
                                Text(letters, color = AtColors.muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                if (announce) "☑ Сообщить о записи разговора" else "☐ Сообщить о записи разговора",
                color = AtColors.text,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        announce = !announce
                        prefs.edit().putBoolean("call_announce_recording", announce).apply()
                    }
                    .padding(8.dp),
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AtColors.success.copy(alpha = 0.18f))
                        .clickable(enabled = !busy) { runDial() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (busy) "Набор…" else "☎  Звонок", color = AtColors.success, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AtColors.accentSoft)
                        .clickable(enabled = !busy) {
                            if (digits.isBlank()) err = "Укажите номер"
                            else showSms = !showSms
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("SMS", color = AtColors.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showSms) {
            item {
                OutlinedTextField(
                    smsText,
                    { smsText = it },
                    label = { Text("Текст SMS — ${smsByteHint(smsText)}") },
                    placeholder = { Text("Текст сообщения…") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
            }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AtColors.accent)
                        .clickable(enabled = !busy) { runSms() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (busy) "Отправка…" else "Отправить SMS через Yeastar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Text(
                "Как на сайте: звонок уходит на АТС Yeastar (POST /calls/dial), SMS — на GSM-порты, не через приложение телефона.",
                color = AtColors.muted,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallCenterSmsPane(
    api: KassaApi,
    token: String?,
    onBack: () -> Unit,
    embedded: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    var tab by remember { mutableStateOf("outgoing") }
    var outgoing by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var outTotal by remember { mutableIntStateOf(0) }
    var inTotal by remember { mutableIntStateOf(0) }
    var outPage by remember { mutableIntStateOf(1) }
    var inPage by remember { mutableIntStateOf(1) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var quota by remember { mutableStateOf<JSONObject?>(null) }
    var ports by remember { mutableStateOf(mapOf("port1" to true, "port2" to true, "port3" to false, "port4" to false)) }
    var orderNo by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var gsmPort by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val pageSize = 50

    fun load() {
        val t = token ?: return
        scope.launch {
            if (loaded) refreshing = true else refreshing = false
            err = null
            try {
                val outSkip = (outPage - 1) * pageSize
                val inSkip = (inPage - 1) * pageSize
                val st = withContext(Dispatchers.IO) { runCatching { api.getObject("/sms/status", t) }.getOrNull() }
                val qt = withContext(Dispatchers.IO) { runCatching { api.getObject("/sms/quota?scope=callcenter", t) }.getOrNull() }
                val outObj = withContext(Dispatchers.IO) { api.getObject("/sms/logs?skip=$outSkip&take=$pageSize", t) }
                val inObj = withContext(Dispatchers.IO) { api.getObject("/sms/incoming?skip=$inSkip&take=$pageSize", t) }
                val outPageRows = KassaApi.pagedRows(outObj)
                val inPageRows = KassaApi.pagedRows(inObj)
                status = st
                quota = qt
                ports = gsmPortsFrom(st)
                outgoing = outPageRows.items
                incoming = inPageRows.items
                outTotal = outPageRows.total
                inTotal = inPageRows.total
                val outPages = kotlin.math.max(1, (outTotal + pageSize - 1) / pageSize)
                val inPages = kotlin.math.max(1, (inTotal + pageSize - 1) / pageSize)
                if (outPage > outPages) outPage = outPages
                if (inPage > inPages) inPage = inPages
                loaded = true
            } catch (e: Exception) {
                err = e.message ?: "Ошибка загрузки SMS"
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(token, tick, outPage, inPage) {
        if (!token.isNullOrBlank()) load()
    }

    fun savePorts(next: Map<String, Boolean>) {
        val t = token ?: return
        val on = next.values.count { it }
        if (on == 0) {
            err = "Нужен хотя бы один активный порт"
            return
        }
        busy = true
        scope.launch {
            try {
                val body = JSONObject()
                    .put("port1", next.getValue("port1"))
                    .put("port2", next.getValue("port2"))
                    .put("port3", next.getValue("port3"))
                    .put("port4", next.getValue("port4"))
                withContext(Dispatchers.IO) { api.patchJson("/sms/gsm-ports?scope=callcenter", t, body) }
                ports = next
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Не удалось сохранить порты"
            } finally {
                busy = false
            }
        }
    }

    fun generateFromOrder() {
        val t = token ?: return
        val num = orderNo.filter { it.isDigit() }
        if (num.isBlank()) {
            err = "Укажите номер заказа"
            return
        }
        busy = true
        err = null
        scope.launch {
            try {
                val o = withContext(Dispatchers.IO) { api.getObject("/sms/order-body?orderNumber=$num", t) }
                val text = KassaApi.pick(o, "message", "text", "body").replace("\r\n", "\n").trim()
                if (text.isBlank()) {
                    err = "Пустой текст SMS"
                } else {
                    message = text
                    val phone = KassaApi.pick(o, "establishmentPhone", "clientPhone", "phone")
                    if (phone.isNotBlank()) destination = phone
                    msg = "Текст SMS по заказу $num"
                }
            } catch (e: Exception) {
                err = e.message ?: "Заказ не найден"
            } finally {
                busy = false
            }
        }
    }

    fun sendSms() {
        val t = token ?: return
        if (destination.isBlank() || message.isBlank()) {
            err = "Нужны номер и текст"
            return
        }
        busy = true
        err = null
        msg = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.sendGatewaySms(t, destination, message.trim(), gsmPort) }
                message = ""
                msg = "SMS отправлено через шлюз Yeastar"
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Ошибка отправки"
            } finally {
                busy = false
            }
        }
    }

    val gatewayOk = status?.optBoolean("configured", false) == true
    val list = if (tab == "incoming") incoming else outgoing

    Column(Modifier.fillMaxSize().background(if (embedded) Color.Transparent else AtColors.bgDeep)) {
        if (!embedded) TopLine("SMS колл-центра", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { SiteSectionHead("SMS", "Шлюз Yeastar GSM: исходящие, входящие и отправка. Не SMS с телефона.") }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(smsGatewayLine(status))
                        val q = smsQuotaLine(quota)
                        if (q.isNotBlank()) StatusChip(q)
                    }
                }
                if (err != null) item { ActionBanner(err!!, error = true) }
                if (msg != null) item { ActionBanner(msg!!, error = false) }
                if (!loaded && err == null) item { LoadingCard() }

                item { Text("GSM-порты (SIM)", color = AtColors.text, fontWeight = FontWeight.Bold) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("port1" to "Порт 1", "port2" to "Порт 2", "port3" to "Порт 3", "port4" to "Порт 4").forEach { (key, label) ->
                            val on = ports[key] == true
                            val disabled = !gatewayOk || (on && ports.values.count { it } == 1) || busy
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) AtColors.accentSoft else AtColors.panel)
                                    .border(1.dp, if (on) AtColors.accent.copy(alpha = 0.45f) else AtColors.stroke, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !disabled) { savePorts(ports + (key to !on)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (on) "☑ $label" else label, color = if (on) AtColors.accent else AtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (!gatewayOk) {
                    item { Text("Сначала настройте YEASTAR_SMS_* на сервере", color = AtColors.muted, fontSize = 12.sp) }
                }

                item { Text("SMS по заказу", color = AtColors.text, fontWeight = FontWeight.Bold) }
                item {
                    OutlinedTextField(
                        orderNo,
                        { orderNo = it.filter { ch -> ch.isDigit() }.take(12) },
                        label = { Text("Номер заказа") },
                        placeholder = { Text("6 цифр") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                }
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AtColors.panel)
                            .border(1.dp, AtColors.stroke, RoundedCornerShape(12.dp))
                            .clickable(enabled = !busy) { generateFromOrder() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Сформировать SMS", color = AtColors.accent, fontWeight = FontWeight.SemiBold)
                    }
                }

                item { Text("Отправка SMS", color = AtColors.text, fontWeight = FontWeight.Bold) }
                item {
                    OutlinedTextField(
                        destination,
                        { destination = it },
                        label = { Text("Номер получателя") },
                        placeholder = { Text("+993…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                }
                item {
                    Text("Порт GSM", color = AtColors.muted, fontSize = 12.sp)
                    val portChoices = buildList {
                        add("" to "Авто")
                        if (ports["port1"] == true) add("1" to "Порт 1")
                        if (ports["port2"] == true) add("2" to "Порт 2")
                        if (ports["port3"] == true) add("3" to "Порт 3")
                        if (ports["port4"] == true) add("4" to "Порт 4")
                    }
                    SiteSegmented(value = gsmPort, items = portChoices, onChange = { gsmPort = it })
                }
                item {
                    OutlinedTextField(
                        message,
                        { message = it },
                        label = { Text("Текст SMS — ${smsByteHint(message)}") },
                        placeholder = { Text("Текст сообщения…") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                }
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (busy || destination.isBlank() || message.isBlank()) AtColors.panel else AtColors.accent)
                            .clickable(enabled = !busy && destination.isNotBlank() && message.isNotBlank()) { sendSms() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (busy) "Отправка…" else "Отправить SMS",
                            color = if (busy || destination.isBlank() || message.isBlank()) AtColors.muted else Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                item {
                    SiteSegmented(
                        value = tab,
                        items = listOf(
                            "outgoing" to "Исходящие ($outTotal)",
                            "incoming" to "Входящие ($inTotal)",
                        ),
                        onChange = { tab = it },
                    )
                }
                if (loaded && list.isEmpty()) {
                    item {
                        Text(
                            if (tab == "incoming") "Нет входящих" else "Нет исходящих",
                            color = AtColors.muted,
                            fontSize = 13.sp,
                        )
                    }
                }
                items(list, key = { "${tab}-${it.id}" }) { row ->
                    SmsBubbleCard(row = row, incoming = tab == "incoming")
                }
                if (tab == "outgoing" && outTotal > pageSize) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Стр. $outPage · всего $outTotal", color = AtColors.muted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (outPage > 1) InlineActionChip("Назад") { outPage -= 1 }
                                if (outPage * pageSize < outTotal) InlineActionChip("Ещё") { outPage += 1 }
                            }
                        }
                    }
                }
                if (tab == "incoming" && inTotal > pageSize) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Стр. $inPage · всего $inTotal", color = AtColors.muted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (inPage > 1) InlineActionChip("Назад") { inPage -= 1 }
                                if (inPage * pageSize < inTotal) InlineActionChip("Ещё") { inPage += 1 }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SmsBubbleCard(row: JsonRow, incoming: Boolean) {
    val ctx = LocalContext.current
    val peer = smsPeer(row, incoming)
    val text = smsBody(row)
    val time = KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "time", "at"))
    val meta = smsPortMeta(row)
    val ok = smsOkFlag(row)
    var expanded by remember(row.id) { mutableStateOf(false) }
    val long = text.length > 120 || text.contains('\n')
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (incoming) AtColors.panel else AtColors.accentSoft)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOf(time, peer, meta).filter { it.isNotBlank() }.joinToString(" · "),
                color = AtColors.muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ok == true) StatusChip("OK")
                if (ok == false) StatusChip("Ошибка")
                InlineActionChip("⧉") {
                    DeviceIntents.copy(ctx, text.ifBlank { peer })
                    Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                }
            }
        }
        Text(
            text.ifBlank { "—" },
            color = AtColors.text,
            fontSize = 14.sp,
            maxLines = if (expanded || !long) 20 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (long) {
            Text(
                if (expanded) "Свернуть" else "Ещё",
                color = AtColors.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmsBroadcastsPane(
    api: KassaApi,
    token: String?,
    department: String,
    title: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    var tab by remember { mutableStateOf("broadcasts") }
    var broadcasts by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var groups by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var smsText by remember { mutableStateOf("") }
    var phonesText by remember { mutableStateOf("") }
    var selectedGroups by remember { mutableStateOf(setOf<String>()) }

    fun reload() {
        val t = token ?: return
        scope.launch {
            if (loaded) refreshing = true
            err = null
            try {
                val bcObj = withContext(Dispatchers.IO) { api.getObject("/sms/broadcasts", t) }
                val all = KassaApi.pagedRows(bcObj).items.ifEmpty {
                    withContext(Dispatchers.IO) { api.getRows("/sms/broadcasts", t) }
                }
                val dep = department.uppercase()
                val filtered = all.filter {
                    val d = KassaApi.pick(it.raw, "department").uppercase()
                    d.isBlank() || d == dep || dep == "ALL"
                }
                broadcasts = if (filtered.isNotEmpty()) filtered else all
                incoming = withContext(Dispatchers.IO) {
                    KassaApi.pagedRows(api.getObject("/sms/incoming?take=50&skip=0", t)).items
                }
                groups = withContext(Dispatchers.IO) {
                    val g = KassaApi.pagedRows(api.getObject("/sms/recipient-groups", t)).items
                    g.filter {
                        val d = KassaApi.pick(it.raw, "department").uppercase()
                        d.isBlank() || d == dep || dep == "ALL"
                    }
                }
                loaded = true
            } catch (e: Exception) {
                err = e.message ?: "Ошибка загрузки рассылок"
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(token, tick, department) {
        if (!token.isNullOrBlank()) reload()
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
        TopLine(title, onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SiteSectionHead(title, "Рассылки и ответы через шлюз Yeastar, как на сайте") }
                item {
                    SiteSegmented(
                        value = tab,
                        items = listOf(
                            "broadcasts" to "Рассылки (${broadcasts.size})",
                            "incoming" to "Ответы (${incoming.size})",
                            "send" to "Отправить",
                        ),
                        onChange = { tab = it },
                    )
                }
                if (err != null) item { ActionBanner(err!!, error = true) }
                if (msg != null) item { ActionBanner(msg!!, error = false) }
                if (!loaded && err == null) item { LoadingCard() }

                if (tab == "broadcasts") {
                    if (loaded && broadcasts.isEmpty()) {
                        item { Text("Нет рассылок. Создайте кампанию во вкладке «Отправить».", color = AtColors.muted, fontSize = 13.sp) }
                    }
                    items(broadcasts, key = { it.id }) { row ->
                        BroadcastCard(row) { label, path ->
                            runAct(label) { api.postJson(path, token!!, JSONObject()) }
                        }
                    }
                }
                if (tab == "incoming") {
                    if (loaded && incoming.isEmpty()) item { Text("Нет входящих SMS", color = AtColors.muted, fontSize = 13.sp) }
                    items(incoming, key = { "in-${it.id}" }) { row ->
                        SmsBubbleCard(row = row, incoming = true)
                    }
                }
                if (tab == "send") {
                    item {
                        OutlinedTextField(
                            smsText,
                            { smsText = it },
                            label = { Text("Текст SMS — ${smsByteHint(smsText)}") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                        )
                    }
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
                    if (groups.isNotEmpty()) {
                        item { Text("Группы", color = AtColors.text, fontWeight = FontWeight.Bold) }
                        items(groups, key = { "g-${it.id}" }) { g ->
                            val on = selectedGroups.contains(g.id)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .atCard(14.dp)
                                    .clickable { selectedGroups = if (on) selectedGroups - g.id else selectedGroups + g.id }
                                    .padding(14.dp),
                            ) {
                                Text(KassaApi.pick(g.raw, "name", "title").ifBlank { g.title }, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(
                                        if (on) "выбрана" else "",
                                        "участников: ${g.raw.optInt("memberCount", g.raw.optInt("count", 0))}",
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = AtColors.muted,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(AtColors.accent)
                                .clickable(enabled = !busy) {
                                    if (smsText.isBlank()) {
                                        err = "Введите текст SMS"
                                        return@clickable
                                    }
                                    runAct("Рассылка запущена") {
                                        val body = JSONObject().put("message", smsText.trim()).put("department", department)
                                        when {
                                            selectedGroups.isNotEmpty() -> {
                                                body.put("audienceMode", "GROUPS")
                                                val arr = org.json.JSONArray()
                                                selectedGroups.forEach { arr.put(it) }
                                                body.put("groupIds", arr)
                                                body.put("runMode", "CONCURRENT")
                                            }
                                            phonesText.isNotBlank() -> {
                                                body.put("audienceMode", "ESTABLISHMENTS")
                                                body.put("phonesText", phonesText.trim())
                                            }
                                            else -> error("Отметьте группу или вставьте номера")
                                        }
                                        val created = api.postJson("/sms/broadcasts", token!!, body)
                                        val id = KassaApi.pick(created, "id").ifBlank {
                                            KassaApi.pick(created.optJSONObject("item") ?: JSONObject(), "id")
                                        }
                                        if (id.isNotBlank()) api.postJson("/sms/broadcasts/$id/start", token, JSONObject())
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (busy) "Отправка…" else "Запустить рассылку", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BroadcastCard(row: JsonRow, onAct: (String, String) -> Unit) {
    val st = KassaApi.pick(row.raw, "status")
    val title = KassaApi.pick(row.raw, "message", "title", "name").ifBlank { row.title }
    val meta = listOf(
        salesBroadcastStatusLabel(st),
        "${row.raw.optInt("recipientsTotal", row.raw.optInt("total", row.raw.optInt("recipients", 0)))} получ.",
        KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "scheduledAt", "startedAt")),
        KassaApi.pick(row.raw, "department"),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AtColors.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            StatusChip(salesBroadcastStatusLabel(st))
        }
        Text(meta, color = AtColors.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (st.equals("DRAFT", true) || st.equals("WAITING_QUEUE", true) || st.equals("SCHEDULED", true)) {
                InlineActionChip("Старт") { onAct("Запущена", "/sms/broadcasts/${row.id}/start") }
            }
            if (st.equals("RUNNING", true) || st.contains("WAIT", true) || st.equals("SCHEDULED", true)) {
                InlineActionChip("Стоп") { onAct("Остановлена", "/sms/broadcasts/${row.id}/cancel") }
            }
            InlineActionChip("Повтор ошибок") { onAct("Повтор", "/sms/broadcasts/${row.id}/retry-failed") }
        }
    }
}

internal fun salesBroadcastStatusLabel(raw: String): String = when (raw.trim().uppercase()) {
    "DRAFT" -> "Черновик"
    "WAITING_QUEUE", "QUEUED" -> "В очереди"
    "SCHEDULED" -> "Запланирована"
    "RUNNING" -> "Идёт"
    "DONE", "COMPLETED", "FINISHED" -> "Готово"
    "CANCELLED", "CANCELED" -> "Остановлена"
    "FAILED" -> "Ошибка"
    else -> raw.ifBlank { "—" }
}

private val CALL_CENTER_TABS = listOf(
    "recent" to "Недавние",
    "contacts" to "Контакты",
    "recordings" to "Записи",
    "keypad" to "Клавиши",
    "sms" to "SMS",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallCenterWorkspacePane(
    api: KassaApi,
    token: String?,
    initialTab: String = "keypad",
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE) }
    val pullState = rememberPullToRefreshState()
    val player = remember { VoiceMemo(ctx) }
    var tab by remember {
        mutableStateOf(if (initialTab in CALL_CENTER_TABS.map { it.first }) initialTab else "keypad")
    }
    var logs by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var logsTotal by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var contacts by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactNote by remember { mutableStateOf("") }
    var playingId by remember { mutableStateOf<String?>(null) }
    var loadingId by remember { mutableStateOf<String?>(null) }
    val pageSize = 40

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    fun load() {
        val t = token ?: return
        scope.launch {
            if (loaded) refreshing = true
            err = null
            try {
                val skip = (page - 1) * pageSize
                val pack = withContext(Dispatchers.IO) {
                    val st = runCatching { api.getObject("/calls/status", t) }.getOrNull()
                    val logObj = runCatching { api.getObject("/calls/logs?skip=$skip&take=$pageSize", t) }.getOrNull()
                    val people = runCatching { api.getRows("/calls/contacts", t) }.getOrDefault(emptyList())
                    Triple(st, logObj, people)
                }
                status = pack.first
                val paged = pack.second?.let { KassaApi.pagedRows(it) }
                logs = paged?.items.orEmpty()
                logsTotal = paged?.total ?: logs.size
                contacts = pack.third
                val pages = kotlin.math.max(1, (logsTotal + pageSize - 1) / pageSize)
                if (page > pages) page = pages
                loaded = true
            } catch (e: Exception) {
                err = e.message ?: "Ошибка загрузки колл-центра"
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(token, tick, page) {
        if (!token.isNullOrBlank()) load()
    }

    fun dialNumber(raw: String) {
        val t = token ?: return
        val phone = raw.filter { it.isDigit() || it == '+' }
        if (phone.isBlank()) {
            err = "Укажите номер"
            return
        }
        busy = true
        err = null
        msg = null
        scope.launch {
            try {
                val from = prefs.getString("call_from_number", "").orEmpty()
                val announce = prefs.getBoolean("call_announce_recording", false)
                msg = yeastarDial(ctx, api, t, phone, from, announce)
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Ошибка набора"
            } finally {
                busy = false
            }
        }
    }

    fun playRecording(row: JsonRow) {
        val id = KassaApi.callLogIdOf(row.raw).ifBlank { row.id }
        if (id.isBlank()) return
        if (playingId == id) {
            player.stopPlay()
            playingId = null
            return
        }
        val t = token ?: return
        loadingId = id
        err = null
        scope.launch {
            try {
                val url = api.resolveMedia("/calls/recordings/${java.net.URLEncoder.encode(id, "UTF-8")}/download")
                    .ifBlank { KassaApi.pick(row.raw, "recordingUrl") }
                val file = withContext(Dispatchers.IO) { ChatMedia.downloadToFile(ctx, url, t) }
                    ?: error("Не удалось скачать запись")
                player.stopPlay()
                player.play(file) { playingId = null }
                playingId = id
            } catch (e: Exception) {
                err = e.message ?: "Не удалось проиграть запись"
                playingId = null
            } finally {
                loadingId = null
            }
        }
    }

    fun shareRecording(row: JsonRow) {
        val id = KassaApi.callLogIdOf(row.raw).ifBlank { row.id }
        if (id.isBlank()) return
        val t = token ?: return
        loadingId = id
        err = null
        scope.launch {
            try {
                val url = api.resolveMedia("/calls/recordings/${java.net.URLEncoder.encode(id, "UTF-8")}/download")
                val file = withContext(Dispatchers.IO) { ChatMedia.downloadToFile(ctx, url, t) }
                    ?: error("Не удалось скачать запись")
                val named = File(ctx.cacheDir, "call-$id.mp3")
                file.copyTo(named, overwrite = true)
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", named)
                ctx.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("audio/*")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "Скачать запись",
                    ),
                )
            } catch (e: Exception) {
                err = e.message ?: "Не удалось скачать запись"
            } finally {
                loadingId = null
            }
        }
    }

    fun createContact() {
        val t = token ?: return
        if (contactName.isBlank() || contactPhone.isBlank()) {
            err = "Укажите имя и телефон"
            return
        }
        busy = true
        err = null
        msg = null
        scope.launch {
            try {
                val body = JSONObject()
                    .put("name", contactName.trim())
                    .put("phone", contactPhone.trim())
                if (contactNote.isNotBlank()) body.put("note", contactNote.trim())
                withContext(Dispatchers.IO) { api.postJson("/calls/contacts", t, body) }
                contactName = ""
                contactPhone = ""
                contactNote = ""
                msg = "Контакт добавлен"
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Не удалось сохранить контакт"
            } finally {
                busy = false
            }
        }
    }

    fun deleteContact(id: String) {
        val t = token ?: return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.deletePath("/calls/contacts/${java.net.URLEncoder.encode(id, "UTF-8")}", t) }
                msg = "Контакт удалён"
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Не удалось удалить"
            } finally {
                busy = false
            }
        }
    }

    val hint = when (tab) {
        "recent" -> "Журнал звонков АТС. Нажмите номер, чтобы перезвонить через Yeastar."
        "contacts" -> "Справочник колл-центра. Звонок уходит на АТС, не с SIM телефона."
        "recordings" -> "Записи разговоров: прослушать или скачать, как на сайте."
        "keypad" -> "Клавиши: набор через АТС Yeastar и SMS-шлюз."
        else -> "SMS-шлюз колл-центра: порты SIM, отправка, входящие и исходящие."
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Колл-центр", onBack, onRefresh = { tick++ })
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SiteSegmented(value = tab, items = CALL_CENTER_TABS, onChange = { tab = it })
            Text(hint, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            if (callBackendLine(status).isNotBlank() && tab != "sms") {
                Text(callBackendLine(status), color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "keypad" -> CallKeypadPane(api, token, embedded = true)
                "sms" -> CallCenterSmsPane(api, token, onBack = onBack, embedded = true)
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { tick++ },
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (err != null) item { ActionBanner(err!!, error = true) }
                        if (msg != null) item { ActionBanner(msg!!, error = false) }
                        if (!loaded && err == null) item { LoadingCard() }
                        when (tab) {
                            "recent" -> {
                                if (logs.isEmpty() && loaded) {
                                    item { Text("Недавних звонков пока нет.", color = AtColors.muted, fontSize = 13.sp) }
                                }
                                items(logs, key = { it.id.ifBlank { it.hashCode().toString() } }) { row ->
                                    CallCenterLogCard(
                                        row = row,
                                        showRecording = true,
                                        playing = playingId == row.id,
                                        loading = loadingId == row.id,
                                        onDial = { dialNumber(KassaApi.callPeerNumber(row.raw)) },
                                        onPlay = { playRecording(row) },
                                        onDownload = { shareRecording(row) },
                                    )
                                }
                            }
                            "recordings" -> {
                                if (logs.isEmpty() && loaded) {
                                    item { Text("Записей пока нет.", color = AtColors.muted, fontSize = 13.sp) }
                                }
                                items(logs, key = { "rec-${it.id.ifBlank { it.hashCode().toString() }}" }) { row ->
                                    CallCenterLogCard(
                                        row = row,
                                        showRecording = true,
                                        playing = playingId == row.id,
                                        loading = loadingId == row.id,
                                        onDial = { dialNumber(KassaApi.callPeerNumber(row.raw)) },
                                        onPlay = { playRecording(row) },
                                        onDownload = { shareRecording(row) },
                                    )
                                }
                            }
                            "contacts" -> {
                                item {
                                    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Новый контакт", color = AtColors.text, fontWeight = FontWeight.Bold)
                                        OutlinedTextField(contactName, { contactName = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                                        OutlinedTextField(contactPhone, { contactPhone = it }, label = { Text("Телефон") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                                        OutlinedTextField(contactNote, { contactNote = it }, label = { Text("Примечание") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                                        Box(
                                            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent).clickable(enabled = !busy) { createContact() },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(if (busy) "Сохранение…" else "Добавить", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                if (contacts.isEmpty() && loaded) {
                                    item { Text("Контактов пока нет.", color = AtColors.muted, fontSize = 13.sp) }
                                }
                                items(contacts, key = { it.id.ifBlank { it.title } }) { row ->
                                    val phone = KassaApi.phoneOf(row.raw).ifBlank { KassaApi.pick(row.raw, "phone", "number") }
                                    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(row.title.ifBlank { phone.ifBlank { "Контакт" } }, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                                        if (phone.isNotBlank()) Text(phone, color = AtColors.muted, fontSize = 13.sp)
                                        val note = KassaApi.pick(row.raw, "note", "comment")
                                        if (note.isNotBlank()) Text(note, color = AtColors.muted, fontSize = 12.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (phone.isNotBlank()) InlineActionChip("Позвонить", filled = true) { dialNumber(phone) }
                                            InlineActionChip("Удалить") { deleteContact(row.id) }
                                        }
                                    }
                                }
                            }
                        }
                        if (tab == "recent" || tab == "recordings") {
                            item {
                                Text("Всего: $logsTotal · стр. $page", color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            if (logsTotal > pageSize) {
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (page > 1) InlineActionChip("Назад") { page -= 1 }
                                        if (page * pageSize < logsTotal) InlineActionChip("Ещё") { page += 1 }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallCenterLogCard(
    row: JsonRow,
    showRecording: Boolean,
    playing: Boolean,
    loading: Boolean,
    onDial: () -> Unit,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    val dir = KassaApi.callDirection(row.raw)
    val phone = KassaApi.callPeerNumber(row.raw)
    val stamp = KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "startedAt", "time"))
    val duration = KassaApi.callDuration(row.raw)
    val who = phone.ifBlank { row.title.ifBlank { "—" } }
    val rec = KassaApi.recordingStatusOf(row.raw)
    val ready = rec == "READY" || KassaApi.pick(row.raw, "recordingUrl", "recordUrl").isNotBlank()
    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (dir) {
                    "in" -> "Входящий"
                    "missed" -> "Пропущенный"
                    else -> "Исходящий"
                },
                color = if (dir == "missed") AtColors.danger else AtColors.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (stamp.isNotBlank()) Text(stamp, color = AtColors.muted, fontSize = 12.sp)
        }
        Text(who, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (duration.isNotBlank()) Text("Длительность: $duration", color = AtColors.muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (phone.isNotBlank()) InlineActionChip("Позвонить", filled = true, onClick = onDial)
        }
        if (showRecording) {
            if (ready) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InlineActionChip(
                        when {
                            loading -> "Загрузка…"
                            playing -> "Стоп"
                            else -> "Прослушать"
                        },
                        filled = playing,
                        onClick = onPlay,
                    )
                    InlineActionChip("Скачать запись", onClick = onDownload)
                }
            } else {
                Text("Запись: ${KassaApi.recordingStatusLabel(rec)}", color = AtColors.muted, fontSize = 12.sp)
            }
        }
    }
}
