package tm.deliviotm.atcrm

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUser(
    val id: String,
    val username: String,
    val fullName: String,
    val role: String,
    val permissions: List<String>,
    val operationsActivityEnabled: Boolean = false,
    val allowedCityKeys: List<String> = emptyList(),
    val avatarUrl: String = "",
    val phone: String = "",
    val email: String = "",
)

data class IntakeTone(
    val label: String,
    val tone: String,
)

data class PagedRows(
    val items: List<JsonRow>,
    val total: Int,
)

data class JsonRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val raw: JSONObject,
)

data class PulseMetric(
    val label: String,
    val value: String,
    val hint: String = "",
    val icon: String = "",
    val delta: String = "",
    val extra: String = "",
    val invertDelta: Boolean = false,
    val tint: String = "",
)

data class DashCity(
    val key: String,
    val name: String,
)

sealed class AiMdBlock {
    data class Heading(val text: String) : AiMdBlock()
    data class Paragraph(val text: String) : AiMdBlock()
    data class ListItems(val items: List<String>, val numbered: Boolean) : AiMdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : AiMdBlock()
}

data class DynamicsDay(
    val key: String,
    val label: String,
    val prevLabel: String,
    val currentOrders: Double,
    val previousOrders: Double,
    val currentTurnover: Double,
    val previousTurnover: Double,
)

data class DownloadDay(
    val date: String,
    val apple: Double,
    val google: Double,
    val total: Double,
)

data class TopEstablishment(
    val name: String,
    val orders: Double,
    val prevOrders: Double,
    val turnover: Double,
    val prevTurnover: Double,
)

data class MonthUsers(
    val label: String,
    val users: Double,
    val withOrderPct: Double,
    val withoutOrderPct: Double,
)

data class ChatMsg(
    val id: String,
    val text: String,
    val author: String,
    val mine: Boolean,
    val time: String,
    val imageUrl: String = "",
    val audioUrl: String = "",
    val fileName: String = "",
    val reactions: List<String> = emptyList(),
    val tick: String = "",
    val audioHintSec: Int = 0,
    val avatarUrl: String = "",
)

data class ModuleSpec(
    val tab: String,
    val title: String,
    val path: String,
    val kind: Kind = Kind.LIST,
) {
    enum class Kind { LIST, OBJECT }
}

class SessionExpiredException : RuntimeException("Сессия истекла — войдите снова")

class KassaApi(private val baseUrl: String) {
    var onUnauthorized: (() -> Unit)? = null
    var accountingOrgId: String? = null
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val aiClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private fun url(path: String): String {
        val b = baseUrl.trimEnd('/')
        val cleaned = stripTake(path)
        return if (cleaned.startsWith("/")) b + cleaned else "$b/$cleaned"
    }

    private fun builder(path: String, token: String? = null): Request.Builder {
        val b = Request.Builder().url(url(path))
        if (!token.isNullOrBlank()) b.header("Authorization", "Bearer $token")
        val org = accountingOrgId?.trim().orEmpty()
        if (org.isNotBlank() && (path.startsWith("/accounting") || path.startsWith("/reconciliation"))) {
            b.header("X-Accounting-Org-Id", org)
        }
        return b
    }

    fun login(password: String): Pair<String, AppUser> {
        val body = JSONObject().put("password", password.trim()).toString()
        val r = client.newCall(builder("/auth/login").post(body.toRequestBody(jsonType)).build()).execute()
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw RuntimeException(err(text, r.code, "Неверный пароль"))
        val o = JSONObject(text)
        return o.getString("accessToken") to parseUser(o.getJSONObject("user"))
    }

    fun me(token: String): AppUser {
        val r = client.newCall(builder("/auth/me", token).get().build()).execute()
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) fail(r.code, text, "Сессия недействительна")
        val o = JSONObject(text)
        val user = if (o.opt("user") is JSONObject) o.getJSONObject("user") else o
        return parseUser(user)
    }

    fun getRaw(path: String, token: String): String {
        val r = client.newCall(builder(path, token).get().build()).execute()
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) fail(r.code, text, "Ошибка ${r.code}")
        return text
    }

    fun getObject(path: String, token: String): JSONObject {
        val text = getRaw(path, token).trim()
        return if (text.startsWith("[")) JSONObject().put("items", JSONArray(text)) else JSONObject(text)
    }

    fun getRows(path: String, token: String): List<JsonRow> {
        return rowsFrom(getRaw(path, token))
    }

    fun getOne(path: String, token: String): JSONObject {
        val text = getRaw(path, token).trim()
        return if (text.startsWith("{")) JSONObject(text) else JSONObject().put("value", text)
    }

    fun postJson(path: String, token: String, body: JSONObject): JSONObject {
        return requestJson("POST", path, token, body)
    }

    fun dialCall(
        token: String,
        toNumber: String,
        fromNumber: String = "",
        announceRecording: Boolean = false,
    ): JSONObject {
        val body = JSONObject()
            .put("toNumber", toNumber.trim())
            .put("announceRecording", announceRecording)
        if (fromNumber.isNotBlank()) body.put("fromNumber", fromNumber.trim())
        return postJson("/calls/dial", token, body)
    }

    fun sendGatewaySms(
        token: String,
        destination: String,
        message: String,
        gsmPort: String = "",
        portsScope: String = "callcenter",
    ): JSONObject {
        val body = JSONObject()
            .put("destination", destination.trim())
            .put("message", message)
            .put("portsScope", portsScope)
        if (gsmPort.isNotBlank()) body.put("gsmPort", gsmPort.trim())
        val o = postJson("/sms/send", token, body)
        if (o.has("ok") && !o.isNull("ok") && !o.optBoolean("ok", true)) {
            throw RuntimeException(
                pick(o, "responsePreview").ifBlank { apiMessage(o) }.ifBlank { "Ошибка отправки SMS" },
            )
        }
        return o
    }

    fun patchJson(path: String, token: String, body: JSONObject): JSONObject {
        return requestJson("PATCH", path, token, body)
    }

    fun putJson(path: String, token: String, body: JSONObject): JSONObject {
        return requestJson("PUT", path, token, body)
    }

    fun deletePath(path: String, token: String) {
        val r = client.newCall(builder(path, token).delete().build()).execute()
        val text = r.body?.string().orEmpty()
        if (r.code == 204 || r.isSuccessful) return
        fail(r.code, text, "Не удалось удалить")
    }

    fun postDownload(path: String, token: String, body: JSONObject): Pair<ByteArray, String> {
        val r = client.newCall(
            builder(path, token)
                .header("x-client-time", java.time.Instant.now().toString())
                .post(body.toString().toRequestBody(jsonType))
                .build(),
        ).execute()
        val bytes = r.body?.bytes() ?: ByteArray(0)
        if (!r.isSuccessful) fail(r.code, bytes.decodeToString(), "Не удалось скачать")
        val disp = r.header("Content-Disposition").orEmpty()
        val named = Regex("filename\\*?=(?:UTF-8''|\"?)([^\"\\s;]+)").find(disp)?.groupValues?.getOrNull(1)
            ?.replace("+", " ")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            .orEmpty()
        return bytes to named.ifBlank { "export.bin" }
    }

    fun getDownload(path: String, token: String): Pair<ByteArray, String> {
        val r = client.newCall(builder(path, token).get().build()).execute()
        val bytes = r.body?.bytes() ?: ByteArray(0)
        if (!r.isSuccessful) fail(r.code, bytes.decodeToString(), "Не удалось скачать")
        val disp = r.header("Content-Disposition").orEmpty()
        val named = Regex("filename\\*?=(?:UTF-8''|\"?)([^\"\\s;]+)").find(disp)?.groupValues?.getOrNull(1)
            ?.replace("+", " ")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            .orEmpty()
        return bytes to named.ifBlank { "export.bin" }
    }

    fun fetchWorkspaceBytes(raw: String, token: String): ByteArray? {
        val path = workspaceApiPath(raw) ?: return null
        return try {
            val (bytes, _) = getDownload(path, token)
            bytes.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun workspaceApiPath(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return null
        val cut = when {
            s.contains("/workspace/files/") -> {
                val id = s.substringAfter("/workspace/files/").substringBefore("?").trimStart('/')
                if (id.isBlank()) null else "/workspace/files/$id"
            }
            s.contains("/support/") && s.contains("/files") -> {
                val i = s.indexOf("/support/")
                if (i < 0) null else s.substring(i).substringBefore("?")
            }
            s.startsWith("/workspace/") || s.startsWith("/support/") -> s.substringBefore("?")
            else -> null
        }
        return cut
    }

    fun uploadMultipart(
        path: String,
        token: String,
        file: File,
        field: String = "file",
        mime: String = "application/octet-stream",
        fields: Map<String, String> = emptyMap(),
    ): JSONObject {
        val (ok, text) = postMultipart(path, token, file, field, fields, mime.toMediaType())
        if (!ok) throw RuntimeException(err(text, 0, "Не удалось загрузить файл"))
        val trimmed = text.trim()
        return if (trimmed.startsWith("{")) JSONObject(trimmed) else JSONObject().put("ok", true).put("raw", trimmed)
    }

    fun setTaskStatus(token: String, taskId: String, status: String, statusReport: String = ""): JSONObject {
        val enc = java.net.URLEncoder.encode(taskId, "UTF-8")
        val body = JSONObject().put("status", status)
        if (statusReport.isNotBlank()) body.put("statusReport", statusReport.trim())
        return postJson("/workspace/tasks/$enc/status", token, body)
    }

    fun addTaskChecklistItem(token: String, taskId: String, title: String): JSONObject {
        val enc = java.net.URLEncoder.encode(taskId, "UTF-8")
        return postJson("/workspace/tasks/$enc/checklist", token, JSONObject().put("title", title.trim()))
    }

    fun setTaskChecklistDone(token: String, taskId: String, itemId: String, done: Boolean): JSONObject {
        val encTask = java.net.URLEncoder.encode(taskId, "UTF-8")
        val encItem = java.net.URLEncoder.encode(itemId, "UTF-8")
        return patchJson("/workspace/tasks/$encTask/checklist/$encItem", token, JSONObject().put("isDone", done))
    }

    fun addTaskComment(token: String, taskId: String, text: String): JSONObject {
        val enc = java.net.URLEncoder.encode(taskId, "UTF-8")
        return postJson("/workspace/tasks/$enc/comments", token, JSONObject().put("text", text.trim()))
    }

    fun pingOpsActivity(token: String, kind: String, entityId: String = "", orderNumber: String = "") {
        try {
            val body = JSONObject().put("kind", kind)
            if (entityId.isNotBlank()) body.put("entityId", entityId)
            if (orderNumber.isNotBlank()) body.put("orderNumber", orderNumber)
            postJson("/operations/activity", token, body)
        } catch (_: Exception) {
        }
    }

    fun patchTask(token: String, taskId: String, body: JSONObject): JSONObject {
        val enc = java.net.URLEncoder.encode(taskId, "UTF-8")
        return patchJson("/workspace/tasks/$enc", token, body)
    }

    fun saveTaskTemplates(token: String, items: List<JSONObject>): JSONObject {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        return putJson("/workspace/task-templates", token, JSONObject().put("items", arr))
    }

    fun markNotificationsScope(token: String, scope: String) {
        postJson("/workspace/notifications/mark-read", token, JSONObject().put("scope", scope))
    }

    fun markNotificationRead(token: String, id: String) {
        val enc = java.net.URLEncoder.encode(id, "UTF-8")
        postJson("/workspace/notifications/$enc/read", token, JSONObject())
    }

    fun markChatRead(path: String, token: String) {
        val id = chatIdFromPath(path)
        if (id.isBlank()) return
        val candidates = if (path.contains("/support/")) {
            listOf("/support/threads/$id/read", "/support/threads/$id/messages/read")
        } else {
            listOf("/workspace/chats/$id/read", "/workspace/chats/$id/messages/read")
        }
        for (p in candidates) {
            try {
                val r = client.newCall(
                    builder(p, token).post("{}".toRequestBody(jsonType)).build(),
                ).execute()
                r.body?.close()
                if (r.isSuccessful) return
            } catch (_: Exception) {
            }
        }
    }

    fun createDirectChat(token: String, userId: String): String {
        val body = JSONObject()
            .put("type", "DIRECT")
            .put("participantIds", JSONArray().put(userId))
        val o = postJson("/workspace/chats", token, body)
        val nested = o.optJSONObject("chat") ?: o.optJSONObject("item") ?: o.optJSONObject("data")
        val id = pick(o, "id").ifBlank { nested?.let { pick(it, "id") }.orEmpty() }
        if (id.isBlank()) error("Не удалось открыть диалог")
        return id
    }

    fun createGroupChat(token: String, title: String, participantIds: List<String>): String {
        val arr = JSONArray()
        participantIds.forEach { arr.put(it) }
        val body = JSONObject().put("type", "GROUP").put("title", title).put("participantIds", arr)
        val o = postJson("/workspace/chats", token, body)
        val nested = o.optJSONObject("chat") ?: o.optJSONObject("item") ?: o.optJSONObject("data")
        val id = pick(o, "id").ifBlank { nested?.let { pick(it, "id") }.orEmpty() }
        if (id.isBlank()) error("Не удалось создать группу")
        return id
    }

    fun reactToMessage(token: String, chatId: String, messageId: String, emoji: String) {
        val encChat = java.net.URLEncoder.encode(chatId, "UTF-8")
        val encMsg = java.net.URLEncoder.encode(messageId, "UTF-8")
        postJson("/workspace/chats/$encChat/messages/$encMsg/react", token, JSONObject().put("emoji", emoji))
    }

    fun getAiObject(path: String, token: String): JSONObject {
        val r = aiClient.newCall(builder(path, token).get().build()).execute()
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) fail(r.code, text, "Ошибка ${r.code}")
        val trimmed = text.trim()
        return when {
            trimmed.startsWith("[") -> JSONObject().put("items", JSONArray(trimmed))
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> JSONObject().put("value", trimmed)
        }
    }

    fun postAiJson(path: String, token: String, body: JSONObject): JSONObject {
        val r = aiClient.newCall(
            builder(path, token).post(body.toString().toRequestBody(jsonType)).build(),
        ).execute()
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) fail(r.code, text, "Не удалось выполнить")
        val trimmed = text.trim()
        return if (trimmed.startsWith("{")) JSONObject(trimmed) else JSONObject().put("ok", true)
    }

    fun askMarketingAdvisor(
        token: String,
        message: String,
        history: List<Pair<String, String>>,
        dateFrom: String,
        dateTo: String,
        cityKey: String,
    ): JSONObject {
        val hist = JSONArray()
        history.takeLast(16).forEach { (role, content) ->
            hist.put(JSONObject().put("role", role).put("content", content))
        }
        val body = JSONObject()
            .put("message", message)
            .put("history", hist)
            .put("dateFrom", dateFrom)
            .put("dateTo", dateTo)
        if (cityKey.isNotBlank()) body.put("cityKey", cityKey)
        return postAiJson("/ai/marketing-advisor", token, body)
    }

    fun ingestMarketingRag(token: String, text: String, sourceLabel: String): JSONObject {
        val body = JSONObject().put("text", text)
        if (sourceLabel.isNotBlank()) body.put("sourceLabel", sourceLabel)
        return postAiJson("/ai/marketing-rag/ingest", token, body)
    }

    fun importFirebasePushCsv(token: String, file: File): JSONObject {
        val (ok, text) = postMultipart(
            "/ai/firebase-push-reports/import",
            token,
            file,
            "file",
            emptyMap(),
            "text/csv".toMediaType(),
        )
        if (!ok) error(err(text, 0, "Ошибка импорта CSV"))
        val trimmed = text.trim()
        return if (trimmed.startsWith("{")) JSONObject(trimmed) else JSONObject().put("ok", true)
    }

    fun resolveMedia(raw: String): String {
        val s = raw.trim()
        if (s.isBlank()) return ""
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        val path = if (s.startsWith("/api/")) s.removePrefix("/api") else s
        if (
            path.startsWith("/workspace/") ||
            path.startsWith("/support/") ||
            path.startsWith("/auth/") ||
            path.startsWith("/users/") ||
            path.startsWith("/calls/") ||
            path.startsWith("/uploads") ||
            path.startsWith("/files")
        ) {
            return url(if (path.startsWith("/")) path else "/$path")
        }
        val origin = baseUrl.substringBefore("/api").trimEnd('/')
        return if (path.startsWith("/")) origin + path else "$origin/$path"
    }

    fun sendChatImage(chatPath: String, token: String, file: File, caption: String): Boolean {
        return sendChatFile(chatPath, token, file, "image/jpeg", caption)
    }

    fun sendChatFile(chatPath: String, token: String, file: File, mime: String, caption: String): Boolean {
        if (!file.exists() || file.length() <= 0L) error("Пустой файл")
        val id = chatIdFromPath(chatPath)
        if (id.isBlank()) error("Нет id чата")
        val mediaType = runCatching { mime.ifBlank { "application/octet-stream" }.toMediaType() }
            .getOrElse { "application/octet-stream".toMediaType() }
        val filename = uploadFileName(file, mime)
        val encId = java.net.URLEncoder.encode(id, "UTF-8")
        if (chatPath.contains("/support/")) {
            val fields = LinkedHashMap<String, String>()
            if (caption.isNotBlank()) fields["caption"] = caption
            val (ok, text) = postMultipart(
                "/support/threads/$encId/send-media",
                token,
                file,
                "file",
                fields,
                mediaType,
                filename,
            )
            if (!ok) error(err(text, 0, "Сервер не принял файл"))
            return true
        }
        val created = postJson(
            "/workspace/chats/$encId/messages",
            token,
            JSONObject().also { if (caption.isNotBlank()) it.put("text", caption) },
        )
        val messageId = pick(created, "id", "_id").ifBlank {
            created.optJSONObject("data")?.let { pick(it, "id", "_id") }.orEmpty()
        }
        if (messageId.isBlank()) error("Сервер не вернул id сообщения")
        val encMsg = java.net.URLEncoder.encode(messageId, "UTF-8")
        val (ok, text) = postMultipart(
            "/workspace/chats/$encId/messages/$encMsg/files",
            token,
            file,
            "file",
            emptyMap(),
            mediaType,
            filename,
        )
        if (!ok) error(err(text, 0, "Сервер не принял файл"))
        return true
    }

    private fun uploadFileName(file: File, mime: String): String {
        val raw = file.name.ifBlank { "file" }
        if (raw.contains('.')) return raw
        val ext = when {
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac") -> "m4a"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("pdf") -> "pdf"
            mime.contains("ogg") || mime.contains("opus") -> "ogg"
            else -> "bin"
        }
        return "$raw.$ext"
    }

    private fun postMultipart(
        path: String,
        token: String,
        file: File,
        fileField: String,
        fields: Map<String, String>,
        imageType: okhttp3.MediaType,
        filename: String = file.name,
    ): Pair<Boolean, String> {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (k, v) -> if (v.isNotBlank()) mb.addFormDataPart(k, v) }
        mb.addFormDataPart(fileField, filename.ifBlank { file.name.ifBlank { "file.bin" } }, file.asRequestBody(imageType))
        val r = client.newCall(builder(path, token).post(mb.build()).build()).execute()
        val text = r.body?.string().orEmpty()
        if (r.code == 401) {
            try {
                onUnauthorized?.invoke()
            } catch (_: Exception) {
            }
            throw SessionExpiredException()
        }
        return r.isSuccessful to text
    }

    private fun extractUploadedUrl(text: String): String {
        val t = text.trim()
        return try {
            when {
                t.startsWith("{") -> {
                    val o = JSONObject(t)
                    pick(o, "url", "path", "location", "fileUrl", "imageUrl", "href", "src").ifBlank {
                        val f = o.optJSONObject("file") ?: o.optJSONObject("data")
                        f?.let { pick(it, "url", "path", "location") }.orEmpty()
                    }
                }
                t.startsWith("http") || t.startsWith("/") -> t.lineSequence().first().trim()
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun requestJson(method: String, path: String, token: String, body: JSONObject): JSONObject {
        val b = builder(path, token)
        if (method.equals("POST", true)) b.header("x-client-time", java.time.Instant.now().toString())
        val r = client.newCall(
            b.method(method, body.toString().toRequestBody(jsonType)).build(),
        ).execute()
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) fail(r.code, text, "Не удалось выполнить")
        val trimmed = text.trim()
        return if (trimmed.startsWith("{")) JSONObject(trimmed) else JSONObject().put("ok", true)
    }

    private fun fail(code: Int, text: String, fallback: String): Nothing {
        if (code == 401) {
            try {
                onUnauthorized?.invoke()
            } catch (_: Exception) {
            }
            throw SessionExpiredException()
        }
        throw RuntimeException(err(text, code, fallback))
    }

    companion object {
        /** Most CRM list routes reject `take` (Zod). Logistics intake still requires it. */
        fun stripTake(path: String): String {
            if (path.contains("logistics-intake")) return path
            val clean = path.substringBefore("?").trimEnd('/')
            if (clean.startsWith("/sms") || clean.startsWith("/calls")) return path
            if (clean == "/sales/establishments" || clean == "/sales/establishments/deleted" || clean == "/sales/establishments/export") return path
            var p = path.replace(Regex("[?&]take=\\d+"), "")
            p = p.replace("?&", "?").replace("&&", "&")
            while (p.endsWith("?") || p.endsWith("&")) p = p.dropLast(1)
            return p
        }

        fun isOperationsList(path: String): Boolean {
            val c = path.substringBefore("?").trimEnd('/')
            return c == "/operations"
        }

        fun opsCacheKey(): String = "/operations"

        fun looksLikeOrderSearch(q: String): Boolean {
            val t = q.trim()
            if (t.length < 3) return false
            return t.all { it.isDigit() || it == '-' }
        }

        const val DASH_MIN_DATE = "2025-06-01"

        fun isIsoDay(value: String): Boolean = value.matches(Regex("""\d{4}-\d{2}-\d{2}"""))

        fun dashPeriodLabel(preset: String): String = when (preset) {
            "7d" -> "Последние 7 дней"
            "30d" -> "Последние 30 дней"
            "90d" -> "Последние 90 дней"
            "3m" -> "Последние 3 месяца"
            "6m" -> "Последние 6 месяцев"
            "12m" -> "Последние 12 месяцев"
            "last_week" -> "Последняя неделя"
            "last_month" -> "Последний месяц"
            "month" -> "Текущий месяц"
            "ytd" -> "С начала года"
            "all" -> "За всё время"
            else -> "Свой период"
        }

        val dashPeriodPresets: List<Pair<String, String>> = listOf(
            "7d", "30d", "90d", "12m", "last_week", "last_month", "month", "ytd", "all",
        ).map { it to dashPeriodLabel(it) }

        val dashDownloadPresets: List<Pair<String, String>> = listOf(
            "3m", "6m", "12m", "last_month", "ytd", "all",
        ).map { it to dashPeriodLabel(it) }

        fun dateRange(
            preset: String,
            operationsWindow: Boolean = false,
            customFrom: String = "",
            customTo: String = "",
        ): Pair<String, String> {
            val today = java.time.LocalDate.now()
            if (operationsWindow) return today.minusDays(30).toString() to today.toString()
            if (preset == "custom" && isIsoDay(customFrom) && isIsoDay(customTo)) {
                val a = java.time.LocalDate.parse(customFrom)
                val b = java.time.LocalDate.parse(customTo)
                return (if (a <= b) a else b).toString() to (if (a <= b) b else a).toString()
            }
            val minAll = runCatching { java.time.LocalDate.parse(DASH_MIN_DATE) }.getOrDefault(today)
            return when (preset) {
                "7d" -> today.minusDays(6).toString() to today.toString()
                "30d" -> today.minusDays(29).toString() to today.toString()
                "90d" -> today.minusDays(89).toString() to today.toString()
                "3m" -> today.withDayOfMonth(1).minusMonths(2).toString() to today.toString()
                "6m" -> today.withDayOfMonth(1).minusMonths(5).toString() to today.toString()
                "12m" -> today.withDayOfMonth(1).minusMonths(11).toString() to today.toString()
                "last_week" -> {
                    val monday = today.with(java.time.DayOfWeek.MONDAY)
                    val lastSun = monday.minusDays(1)
                    lastSun.minusDays(6).toString() to lastSun.toString()
                }
                "last_month" -> {
                    val firstThis = today.withDayOfMonth(1)
                    firstThis.minusMonths(1).toString() to firstThis.minusDays(1).toString()
                }
                "ytd" -> today.withDayOfYear(1).toString() to today.toString()
                "all" -> (if (minAll.isBefore(today)) minAll else today).toString() to today.toString()
                else -> today.withDayOfMonth(1).toString() to today.toString()
            }
        }

        fun periodHint(preset: String): String = when (preset) {
            "7d" -> "за 7 дней"
            "30d" -> "за 30 дней"
            "90d" -> "за 90 дней"
            "3m" -> "за 3 месяца"
            "6m" -> "за 6 месяцев"
            "12m" -> "за 12 месяцев"
            "last_week" -> "за прошлую неделю"
            "last_month" -> "за прошлый месяц"
            "ytd" -> "с начала года"
            "all" -> "за всё время"
            "custom" -> "за выбранный период"
            else -> "за этот месяц"
        }

        fun periodRangeLabel(preset: String, customFrom: String = "", customTo: String = ""): String {
            val (from, to) = dateRange(preset, customFrom = customFrom, customTo = customTo)
            return "${fmtDay(from)} — ${fmtDay(to)}"
        }

        fun fmtDay(ymd: String): String {
            val p = ymd.trim().take(10).split("-")
            if (p.size < 3) return ymd
            return "${p[2]}.${p[1]}.${p[0]}"
        }

        fun parseDayInput(raw: String): String? {
            val t = raw.trim()
            if (t.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return t
            val m = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{4})""").matchEntire(t) ?: return null
            val d = m.groupValues[1].padStart(2, '0')
            val mo = m.groupValues[2].padStart(2, '0')
            return "${m.groupValues[3]}-$mo-$d"
        }

        fun ruDateTime(raw: String, withSeconds: Boolean = true): String {
            if (raw.isBlank()) return "—"
            val zoned = parseZoned(raw) ?: return raw.replace('T', ' ').take(19)
            val pattern = if (withSeconds) "dd.MM.yyyy, HH:mm:ss" else "dd.MM.yyyy, HH:mm"
            return zoned.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern(pattern))
        }

        fun ruDateRange(fromRaw: String, toRaw: String): String {
            val a = fromRaw.trim().take(10)
            val b = toRaw.trim().take(10)
            if (a.isBlank() || b.isBlank()) return "—"
            return "${fmtDay(a)} — ${fmtDay(b)}"
        }

        fun fmtDayShort(ymd: String): String {
            val p = ymd.split("-")
            if (p.size < 3) return ymd
            return "${p[2]}.${p[1]}"
        }

        fun aiRecommendationsQuery(dateFrom: String, dateTo: String, cityKey: String): String {
            val q = StringBuilder("/ai/recommendations?dateFrom=${enc(dateFrom)}&dateTo=${enc(dateTo)}&lang=ru")
            if (cityKey.isNotBlank()) q.append("&cityKey=${enc(cityKey)}")
            return q.toString()
        }

        fun aiPresetRange(preset: String, minOrderDate: String = ""): Pair<String, String> {
            val today = java.time.LocalDate.now()
            val to = today.toString()
            val from = when (preset) {
                "90d" -> today.minusDays(89)
                "6m" -> today.minusMonths(6)
                "ytd" -> today.withDayOfYear(1)
                "all" -> runCatching { java.time.LocalDate.parse(minOrderDate.take(10)) }.getOrNull()
                    ?: java.time.LocalDate.of(2020, 1, 1)
                else -> today.minusDays(29)
            }
            return from.toString() to to
        }

        fun aiMatchedPreset(dateFrom: String, dateTo: String, minOrderDate: String = ""): String {
            for (key in listOf("30d", "90d", "6m", "ytd", "all")) {
                val range = aiPresetRange(key, minOrderDate)
                if (range.first == dateFrom && range.second == dateTo) return key
            }
            return "custom"
        }

        fun aiSourceLabel(o: JSONObject?): String {
            if (o == null) return "глубокий анализ данных кассы"
            val model = pick(o, "model")
            val source = pick(o, "source").lowercase()
            return when {
                model.startsWith("ollama:") -> "Ollama (${model.removePrefix("ollama:")})"
                source == "openai" -> "OpenAI (${model.ifBlank { "—" }})"
                else -> "глубокий анализ данных кассы"
            }
        }

        fun aiPct(n: Double?): String {
            if (n == null || n.isNaN()) return "—"
            val v = if (n == n.toLong().toDouble()) n.toLong().toString() else "%.1f".format(n).trimEnd('0').trimEnd('.')
            return "$v%"
        }

        fun aiPayload(o: JSONObject): JSONObject {
            return o.optJSONObject("data") ?: o.optJSONObject("result") ?: o
        }

        fun dormantPartners(snapshot: JSONObject?): List<JSONObject> {
            val crm = snapshot?.optJSONObject("salesCrm") ?: snapshot?.optJSONObject("crm") ?: return emptyList()
            val all = crm.optJSONArray("dormantConnectedAll")
            val samples = crm.optJSONArray("dormantConnectedSamples")
            val arr = if (all != null && all.length() > 0) all else samples ?: return emptyList()
            return eachObj(arr)
        }

        fun dormantCount(snapshot: JSONObject?): Int {
            val crm = snapshot?.optJSONObject("salesCrm") ?: snapshot?.optJSONObject("crm") ?: return 0
            val n = jsonNum(crm, "dormantConnectedCount")
            if (n > 0) return n.toInt()
            return dormantPartners(snapshot).size
        }

        fun dormantCriteria(snapshot: JSONObject?): String {
            val crm = snapshot?.optJSONObject("salesCrm") ?: snapshot?.optJSONObject("crm") ?: return ""
            val r = crm.optJSONObject("dormantCriteria") ?: return ""
            val year = pick(r, "year")
            if (year.isBlank()) return "Критерий: без заказов за выбранный период"
            val grace = jsonNum(r, "graceDaysAfterConnection").toInt()
            return buildString {
                append("Критерий: без заказов за выбранный период")
                if (grace > 0) append(", grace $grace дн.")
            }
        }

        fun parseAiMarkdown(raw: String): List<AiMdBlock> {
            val lines = raw.replace("\r\n", "\n").split("\n")
            val out = ArrayList<AiMdBlock>()
            var i = 0
            fun splitRow(line: String): List<String> = line.trim()
                .removePrefix("|").removeSuffix("|")
                .split("|")
                .map { it.trim() }
            fun isSep(line: String): Boolean {
                val t = line.replace("|", " ").trim()
                return t.contains("---")
            }
            while (i < lines.size) {
                val line = lines[i]
                if (line.trim().isEmpty()) {
                    i++
                    continue
                }
                when {
                    line.startsWith("### ") -> {
                        out += AiMdBlock.Heading(line.removePrefix("### ").trim())
                        i++
                    }
                    line.startsWith("## ") -> {
                        out += AiMdBlock.Heading(line.removePrefix("## ").trim())
                        i++
                    }
                    line.startsWith("# ") -> {
                        out += AiMdBlock.Heading(line.removePrefix("# ").trim())
                        i++
                    }
                    line.trim().startsWith("|") && i + 1 < lines.size && isSep(lines[i + 1]) -> {
                        val headers = splitRow(line)
                        i += 2
                        val rows = ArrayList<List<String>>()
                        while (i < lines.size && lines[i].trim().startsWith("|") && !isSep(lines[i])) {
                            rows += splitRow(lines[i])
                            i++
                        }
                        out += AiMdBlock.Table(headers, rows)
                    }
                    Regex("""^\d+\.\s""").containsMatchIn(line.trim()) -> {
                        val items = ArrayList<String>()
                        while (i < lines.size) {
                            val n = lines[i].trim()
                            if (Regex("""^\d+\.\s""").containsMatchIn(n)) {
                                items += n.replace(Regex("""^\d+\.\s+"""), "")
                                i++
                            } else if (n.isEmpty() && i + 1 < lines.size && Regex("""^\d+\.\s""").containsMatchIn(lines[i + 1].trim())) {
                                i++
                            } else break
                        }
                        out += AiMdBlock.ListItems(items, numbered = true)
                    }
                    Regex("""^[-•]\s""").containsMatchIn(line.trim()) -> {
                        val items = ArrayList<String>()
                        while (i < lines.size && Regex("""^[-•]\s""").containsMatchIn(lines[i].trim())) {
                            items += lines[i].trim().replace(Regex("""^[-•]\s+"""), "")
                            i++
                        }
                        out += AiMdBlock.ListItems(items, numbered = false)
                    }
                    else -> {
                        val buf = ArrayList<String>()
                        buf += line
                        i++
                        while (i < lines.size) {
                            val n = lines[i]
                            if (n.trim().isEmpty() || n.startsWith("#") || n.trim().startsWith("|") ||
                                Regex("""^\d+\.\s""").containsMatchIn(n.trim()) ||
                                Regex("""^[-•]\s""").containsMatchIn(n.trim())
                            ) break
                            buf += n
                            i++
                        }
                        out += AiMdBlock.Paragraph(buf.joinToString(" "))
                    }
                }
            }
            return out
        }

        fun dayKeys(from: String, to: String): List<String> {
            val start = runCatching { java.time.LocalDate.parse(from) }.getOrNull() ?: return emptyList()
            val end = runCatching { java.time.LocalDate.parse(to) }.getOrNull() ?: return emptyList()
            if (end.isBefore(start)) return emptyList()
            return generateSequence(start) { d ->
                val n = d.plusDays(1)
                if (n.isAfter(end)) null else n
            }.map { it.toString() }.toList()
        }

        fun jsonArr(o: JSONObject?, vararg keys: String): JSONArray {
            if (o == null) return JSONArray()
            for (k in keys) {
                val a = o.optJSONArray(k)
                if (a != null) return a
            }
            return JSONArray()
        }

        fun eachObj(arr: JSONArray): List<JSONObject> {
            val out = ArrayList<JSONObject>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                out += obj
            }
            return out
        }

        fun prevDateRange(preset: String, customFrom: String = "", customTo: String = ""): Pair<String, String> {
            val (from, to) = dateRange(preset, customFrom = customFrom, customTo = customTo)
            val fromD = java.time.LocalDate.parse(from)
            val toD = java.time.LocalDate.parse(to)
            when (preset) {
                "last_month", "month", "" -> {
                    val start = fromD.withDayOfMonth(1)
                    return start.minusMonths(1).toString() to start.minusDays(1).toString()
                }
                "last_week" -> return fromD.minusDays(7).toString() to toD.minusDays(7).toString()
                "ytd" -> {
                    val prevFrom = java.time.LocalDate.of(fromD.year - 1, 1, 1)
                    val prevTo = minOf(toD.minusYears(1), java.time.LocalDate.of(fromD.year - 1, 12, 31))
                    return prevFrom.toString() to prevTo.toString()
                }
            }
            val days = java.time.temporal.ChronoUnit.DAYS.between(fromD, toD) + 1
            val prevTo = fromD.minusDays(1)
            val prevFrom = prevTo.minusDays(days - 1)
            return prevFrom.toString() to prevTo.toString()
        }

        fun vsPrev(current: Double, previous: Double): String {
            if (previous <= 0.0 && current <= 0.0) return ""
            if (previous <= 0.0) return "+100%"
            val n = kotlin.math.round((current - previous) / previous * 100.0).toInt()
            return if (n > 0) "+$n%" else "$n%"
        }

        fun jsonNum(o: JSONObject?, vararg keys: String): Double {
            if (o == null) return 0.0
            for (k in keys) {
                if (!o.has(k) || o.isNull(k)) continue
                when (val v = o.opt(k)) {
                    is Number -> return v.toDouble()
                    is String -> {
                        val t = v.replace(" ", "").replace('\u00A0', ' ').replace(',', '.')
                            .replace(Regex("[^0-9+\\-.]"), "")
                        t.toDoubleOrNull()?.let { return it }
                    }
                }
            }
            return 0.0
        }

        fun salesAnalyticsPath(from: String, to: String, cityKey: String = ""): String {
            val q = StringBuilder(
                "/reports/sales-analytics?period=month" +
                    "&dateFrom=${enc(isoDayStart(from))}&dateTo=${enc(isoDayEnd(to))}" +
                    "&dateBasis=business&paymentType=ALL&establishmentType=ALL",
            )
            if (cityKey.isNotBlank()) q.append("&cityKey=${enc(cityKey)}")
            return q.toString()
        }

        fun analyticsPresetRange(preset: String): Pair<String, String> {
            val today = java.time.LocalDate.now()
            val from = when (preset) {
                "7d" -> today.minusDays(6)
                "30d" -> today.minusDays(29)
                "3m" -> today.withDayOfMonth(1).minusMonths(2)
                "6m" -> today.withDayOfMonth(1).minusMonths(5)
                "12m" -> {
                    val year = if (today.monthValue >= 6) today.year else today.year - 1
                    java.time.LocalDate.of(year, 6, 1)
                }
                else -> today.withDayOfMonth(1)
            }
            return from.toString() to today.toString()
        }

        fun reportsSummaryQuery(
            from: String,
            to: String,
            paymentType: String = "ALL",
            establishmentType: String = "ALL",
            cityKey: String = "",
            establishmentId: String = "",
        ): String {
            val q = StringBuilder(
                "/reports/summary?dateFrom=${enc(isoDayStart(from))}&dateTo=${enc(isoDayEnd(to))}" +
                    "&paymentType=${enc(paymentType.ifBlank { "ALL" })}" +
                    "&establishmentType=${enc(establishmentType.ifBlank { "ALL" })}" +
                    "&dateBasis=business",
            )
            appendReportFilters(q, cityKey, establishmentId)
            return q.toString()
        }

        fun reportsAnalyticsQuery(
            period: String,
            from: String,
            to: String,
            paymentType: String = "ALL",
            establishmentType: String = "ALL",
            cityKey: String = "",
            establishmentId: String = "",
        ): String {
            val q = StringBuilder(
                "/reports/sales-analytics?period=${enc(period.ifBlank { "month" })}" +
                    "&dateFrom=${enc(from)}&dateTo=${enc(to)}" +
                    "&dateBasis=business&paymentType=${enc(paymentType.ifBlank { "ALL" })}" +
                    "&establishmentType=${enc(establishmentType.ifBlank { "ALL" })}",
            )
            appendReportFilters(q, cityKey, establishmentId)
            return q.toString()
        }

        fun reportsProfitFinanceQuery(
            period: String,
            from: String,
            to: String,
            paymentType: String = "ALL",
            establishmentType: String = "ALL",
            cityKey: String = "",
            establishmentId: String = "",
        ): String {
            val q = StringBuilder(
                "/reports/profit-finance?period=${enc(period.ifBlank { "month" })}" +
                    "&dateFrom=${enc(from)}&dateTo=${enc(to)}" +
                    "&dateBasis=business&paymentType=${enc(paymentType.ifBlank { "ALL" })}" +
                    "&establishmentType=${enc(establishmentType.ifBlank { "ALL" })}",
            )
            appendReportFilters(q, cityKey, establishmentId)
            return q.toString()
        }

        fun reportsExportQuery(
            from: String,
            to: String,
            paymentType: String = "ALL",
            establishmentType: String = "ALL",
            cityKey: String = "",
            establishmentId: String = "",
        ): String {
            val q = StringBuilder(
                "/reports/export.xlsx?dateFrom=${enc(isoDayStart(from))}&dateTo=${enc(isoDayEnd(to))}" +
                    "&paymentType=${enc(paymentType.ifBlank { "ALL" })}" +
                    "&establishmentType=${enc(establishmentType.ifBlank { "ALL" })}" +
                    "&dateBasis=business",
            )
            appendReportFilters(q, cityKey, establishmentId)
            return q.toString()
        }

        private fun appendReportFilters(q: StringBuilder, cityKey: String, establishmentId: String) {
            val city = cityKey.trim().lowercase()
            if (city == "ashgabat" || city == "mary") q.append("&cityKey=${enc(city)}")
            if (establishmentId.isNotBlank()) q.append("&establishmentId=${enc(establishmentId)}")
        }

        fun jsonNumOpt(o: JSONObject?, vararg keys: String): Double? {
            if (o == null) return null
            for (k in keys) {
                if (!o.has(k) || o.isNull(k)) continue
                return jsonNum(o, k)
            }
            return null
        }

        fun reportsSalesCrmQuery(from: String, to: String, groupBy: String = "week"): String {
            return "/reports/sales-crm-establishments?dateFrom=${enc(from)}&dateTo=${enc(to)}" +
                "&groupBy=${enc(groupBy.ifBlank { "week" })}&dateField=connectedAt"
        }

        fun tmt(n: Double): String {
            if (!n.isFinite()) return "—"
            return "${ruGrouped(n, 2)} TMT"
        }

        fun payLabel(raw: String): String = when (raw.uppercase()) {
            "CASH" -> "Наличные"
            "ONLINE" -> "Онлайн"
            "MIXED" -> "Смешанная"
            "ALL", "" -> "Все"
            else -> raw
        }

        fun cityLabel(raw: String): String = when (raw.lowercase()) {
            "ashgabat" -> "Ашхабад"
            "mary" -> "Мары"
            "" -> "—"
            else -> raw
        }

        fun estTypeLabel(raw: String): String = when (raw.uppercase()) {
            "RESTAURANT" -> "Ресторан"
            "STORE", "SHOP" -> "Магазин"
            else -> raw.ifBlank { "—" }
        }

        fun nestedEstablishment(o: JSONObject): JSONObject? = o.optJSONObject("establishment")

        fun nestedEstablishmentName(o: JSONObject): String {
            nestedEstablishment(o)?.let { nest ->
                val n = pick(nest, "name", "title")
                if (n.isNotBlank()) return n
            }
            return pick(o, "establishmentName", "establishment")
        }

        fun nestedEstablishmentType(o: JSONObject): String {
            val raw = nestedEstablishment(o)?.let { pick(it, "type", "kind") }
                .orEmpty().ifBlank { pick(o, "establishmentType", "type", "kind") }
            return estTypeLabel(raw)
        }

        fun expenseKindLabel(raw: String): String = when (raw) {
            "free_delivery" -> "Бесплатная доставка"
            "loyalty" -> "Лояльность"
            "platform_discount" -> "Скидка платформы"
            else -> raw.ifBlank { "—" }
        }

        fun appStoresStatsPath(from: String, to: String, sync: Boolean = false): String {
            return "/app-stores/stats?dateFrom=${enc(from)}&dateTo=${enc(to)}&sync=${if (sync) "1" else "0"}"
        }

        fun analyticsTotals(o: JSONObject): JSONObject {
            return o.optJSONObject("totals") ?: o.optJSONObject("current") ?: o.optJSONObject("summary") ?: o
        }

        fun pulseFromAnalytics(cur: JSONObject, prev: JSONObject?, hint: String): List<PulseMetric> {
            val c = analyticsTotals(cur)
            val p = prev?.let { analyticsTotals(it) }
            fun footer(current: Double, previous: Double, profit: Boolean = false): Pair<String, String> {
                val raw = vsPrev(current, previous)
                val line = when {
                    profit && raw.isNotBlank() -> "комиссия · $raw к прошлому"
                    profit -> "комиссия"
                    raw.isNotBlank() -> "$hint · $raw к прошлому"
                    else -> hint
                }
                return raw to line
            }
            fun countTile(label: String, icon: String, invert: Boolean, tint: String, vararg keys: String): PulseMetric {
                val n = jsonNum(c, *keys)
                val (raw, line) = footer(n, jsonNum(p, *keys))
                return PulseMetric(label, prettyNumber(n.toLong().toString()).ifBlank { "0" }, line, icon, raw, "", invert, tint)
            }
            fun moneyTile(
                label: String,
                icon: String,
                tint: String,
                extra: String = "",
                profit: Boolean = false,
                vararg keys: String,
            ): PulseMetric {
                val n = jsonNum(c, *keys)
                val (raw, line) = footer(n, jsonNum(p, *keys), profit)
                return PulseMetric(label, tmt(n), line, icon, raw, extra, false, tint)
            }
            val gross = jsonNumOpt(c, "profitBeforeDeductions", "grossProfit", "profitGross")
            val profitExtra = if (gross != null) "валовая прибыль: ${tmt(gross)}" else ""
            return listOf(
                countTile("Заказы", "📦", false, "mint", "ordersCount", "orders"),
                moneyTile("Оборот (без доставки)", "💰", "teal", "", false, "turnover", "revenue", "turnoverWithoutDelivery"),
                moneyTile("Сумма доставок", "🚚", "violet", "", false, "deliveryTotal", "deliveryAmount", "deliverySum"),
                countTile("Бесплатные доставки", "🎁", true, "blue", "freeDeliveryCount", "freeDeliveries"),
                moneyTile("Средний чек", "🧾", "lavender", "", false, "avgCheck", "averageCheck", "aov"),
                moneyTile("Чистая прибыль", "📈", "peach", profitExtra, true, "profit", "netProfit"),
            )
        }

        fun downloadsFromStats(cur: JSONObject?, prev: JSONObject?, hint: String): List<PulseMetric> {
            if (cur == null || cur.length() == 0) return emptyList()
            val c = cur.optJSONObject("totals") ?: cur
            val p = prev?.optJSONObject("totals") ?: prev
            fun one(label: String, icon: String, tint: String, vararg keys: String): PulseMetric {
                val n = jsonNum(c, *keys)
                val d = vsPrev(n, jsonNum(p, *keys))
                return PulseMetric(label, prettyNumber(n.toString()).ifBlank { "0" }, hint, icon, d, tint = tint)
            }
            val apple = jsonNum(c, "apple", "appStore", "ios")
            val google = jsonNum(c, "google", "play", "android")
            val total = jsonNum(c, "total", "downloads").let { if (it > 0) it else apple + google }
            val prevTotal = run {
                val pa = jsonNum(p, "apple", "appStore", "ios")
                val pg = jsonNum(p, "google", "play", "android")
                val pt = jsonNum(p, "total", "downloads")
                if (pt > 0) pt else pa + pg
            }
            return listOf(
                one("App Store", "", "ink", "apple", "appStore", "ios"),
                one("Google Play", "▶", "mint", "google", "play", "android"),
                PulseMetric(
                    "Всего",
                    prettyNumber(total.toString()).ifBlank { "0" },
                    hint,
                    "Σ",
                    vsPrev(total, prevTotal),
                    tint = "sky",
                ),
            )
        }

        fun sliceDownloads(series: List<DownloadDay>, from: String, to: String): List<DownloadDay> {
            if (series.isEmpty()) return emptyList()
            val hasIso = series.any { it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            if (!hasIso) return series
            return series.filter { it.date >= from && it.date <= to }
        }

        fun downloadTotals(series: List<DownloadDay>): Triple<Double, Double, Double> {
            val apple = series.sumOf { it.apple }
            val google = series.sumOf { it.google }
            val total = series.sumOf { if (it.total > 0) it.total else it.apple + it.google }
            return Triple(apple, google, total)
        }

        fun parseDownloadSeries(o: JSONObject?): List<DownloadDay> {
            if (o == null || o.length() == 0) return emptyList()
            return eachObj(jsonArr(o, "series", "days", "items")).mapNotNull { item ->
                val date = pick(item, "date", "day", "dayKey")
                val apple = jsonNum(item, "apple", "appStore", "ios")
                val google = jsonNum(item, "google", "play", "android")
                val total = jsonNum(item, "total", "downloads").let { if (it > 0) it else apple + google }
                if (date.isBlank() && apple == 0.0 && google == 0.0 && total == 0.0) null
                else DownloadDay(date.ifBlank { pick(item, "label") }, apple, google, total)
            }
        }

        private fun seriesLookup(o: JSONObject?): Map<String, JSONObject> {
            val map = LinkedHashMap<String, JSONObject>()
            for (item in eachObj(jsonArr(o, "series", "days", "items"))) {
                val date = pick(item, "date", "day", "dayKey")
                val label = pick(item, "label")
                if (date.isNotBlank()) {
                    map[date] = item
                    map[fmtDayShort(date)] = item
                }
                if (label.isNotBlank()) map[label] = item
            }
            return map
        }

        fun buildDynamics(
            cur: JSONObject?,
            prev: JSONObject?,
            from: String,
            to: String,
            prevFrom: String,
            prevTo: String,
        ): List<DynamicsDay> {
            val curMap = seriesLookup(cur)
            val prevMap = seriesLookup(prev)
            val curDays = dayKeys(from, to)
            val prevDays = dayKeys(prevFrom, prevTo)
            if (curDays.isEmpty() && curMap.isEmpty()) return emptyList()
            val n = maxOf(curDays.size, prevDays.size, 1)
            val out = ArrayList<DynamicsDay>(n)
            for (i in 0 until n) {
                val cDay = curDays.getOrNull(i)
                val pDay = prevDays.getOrNull(i)
                val c = cDay?.let { curMap[it] ?: curMap[fmtDayShort(it)] }
                    ?: curMap.values.elementAtOrNull(i)
                val p = pDay?.let { prevMap[it] ?: prevMap[fmtDayShort(it)] }
                    ?: prevMap.values.elementAtOrNull(i)
                if (c == null && p == null) continue
                out += DynamicsDay(
                    key = cDay ?: "prev-${pDay ?: i}",
                    label = cDay?.let { fmtDayShort(it) } ?: c?.let { pick(it, "label") }.orEmpty().ifBlank { "—" },
                    prevLabel = pDay?.let { fmtDayShort(it) } ?: p?.let { pick(it, "label") }.orEmpty().ifBlank { "—" },
                    currentOrders = jsonNum(c, "ordersCount", "orders"),
                    previousOrders = jsonNum(p, "ordersCount", "orders"),
                    currentTurnover = jsonNum(c, "turnover", "revenue"),
                    previousTurnover = jsonNum(p, "turnover", "revenue"),
                )
            }
            return out
        }

        fun parseTopEstablishments(cur: JSONObject?, prev: JSONObject?): List<TopEstablishment> {
            val prevMap = eachObj(jsonArr(prev, "topEstablishments")).associateBy {
                pick(it, "establishmentId", "id", "name")
            }
            return eachObj(jsonArr(cur, "topEstablishments")).map { e ->
                val id = pick(e, "establishmentId", "id", "name")
                val p = prevMap[id]
                TopEstablishment(
                    name = pick(e, "name", "title", "establishment").ifBlank { id.ifBlank { "Точка" } },
                    orders = jsonNum(e, "ordersCount", "orders"),
                    prevOrders = jsonNum(p, "ordersCount", "orders"),
                    turnover = jsonNum(e, "turnover", "revenue"),
                    prevTurnover = jsonNum(p, "turnover", "revenue"),
                )
            }
        }

        fun parseMonthlyUsers(o: JSONObject?): List<MonthUsers> {
            return eachObj(jsonArr(o, "monthlyUsersComparison", "monthlyUsers", "usersByMonth")).map { item ->
                val users = jsonNum(item, "usersCount", "users", "registrations")
                val withN = jsonNum(item, "newUsersWithOrderCount", "withOrderCount")
                val withPct = when {
                    item.has("newUsersWithOrderPct") && !item.isNull("newUsersWithOrderPct") ->
                        jsonNum(item, "newUsersWithOrderPct", "withOrderPct")
                    item.has("withOrderPct") && !item.isNull("withOrderPct") -> jsonNum(item, "withOrderPct")
                    users > 0 -> withN / users * 100.0
                    else -> 0.0
                }
                MonthUsers(
                    label = pick(item, "label", "month", "axisPrimary").ifBlank { pick(item, "date") },
                    users = users,
                    withOrderPct = withPct,
                    withoutOrderPct = maxOf(0.0, 100.0 - withPct),
                )
            }
        }

        fun parseCities(o: JSONObject?): List<DashCity> {
            val defaults = listOf(
                DashCity("", "Все города"),
                DashCity("ashgabat", "Ашхабад"),
                DashCity("mary", "Мары"),
            )
            val extra = eachObj(jsonArr(o, "items", "cities", "data")).mapNotNull { item ->
                val key = pick(item, "cityKey", "key", "value", "slug")
                val name = pick(item, "name", "title", "label", "city")
                if (key.isBlank() && name.isBlank()) null
                else DashCity(key.ifBlank { name.lowercase() }, name.ifBlank { key })
            }
            val seen = LinkedHashSet<String>()
            return (defaults + extra).filter { seen.add(it.key.ifBlank { it.name }) }
        }

        fun compactNumber(n: Double): String {
            val abs = kotlin.math.abs(n)
            return when {
                abs >= 1_000_000 -> "${prettyNumber((n / 1_000_000).toString())} млн"
                abs >= 10_000 -> prettyNumber(kotlin.math.round(n).toString())
                n == n.toLong().toDouble() -> prettyNumber(n.toLong().toString())
                else -> prettyNumber(n.toString())
            }
        }

        fun remainingLabel(raw: String): String {
            val key = dayKey(raw)
            if (key.isBlank()) return ""
            val today = java.time.LocalDate.now()
            val due = runCatching { java.time.LocalDate.parse(key) }.getOrNull() ?: return prettyTime(raw)
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            return when {
                days < 0 -> "Просрочено: ${-days} дн."
                days == 0L -> "Сегодня"
                else -> "Осталось: $days дн."
            }
        }

        fun taskColumn(row: JsonRow): String {
            val s = pick(row.raw, "status", "state", "column", "stage").uppercase()
            return when {
                s == "DONE" || s == "ARCHIVED" || s == "CLOSED" || s == "COMPLETE" -> "done"
                s == "OVERDUE" || s.contains("ПРОСРОЧ") || taskIsOverdue(row) -> "overdue"
                s == "IN_REVIEW" || s.contains("REVIEW") || s.contains("ПРОВЕР") -> "review"
                s == "IN_PROGRESS" || s.contains("PROGRESS") || s.contains("РАБОТ") -> "progress"
                else -> "new"
            }
        }

        fun taskListPath(includeArchived: Boolean, q: String = ""): String {
            val parts = ArrayList<String>()
            if (includeArchived) parts += "archived=1"
            val query = q.trim()
            if (query.isNotBlank()) parts += "q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            return if (parts.isEmpty()) "/workspace/tasks" else "/workspace/tasks?" + parts.joinToString("&")
        }

        fun taskStatusLabel(raw: String): String = when (raw.uppercase()) {
            "NEW" -> "Новая"
            "IN_PROGRESS" -> "В работе"
            "IN_REVIEW" -> "На проверке"
            "DONE" -> "Выполнена"
            "OVERDUE" -> "Просрочена"
            "ARCHIVED" -> "Архив"
            else -> prettyStatusFallback(raw)
        }

        private fun prettyStatusFallback(raw: String): String = raw.ifBlank { "Новая" }

        fun taskCreatorId(o: JSONObject): String {
            val c = o.optJSONObject("createdBy") ?: o.optJSONObject("creator")
            return pick(o, "createdById", "creatorId").ifBlank { c?.let { pick(it, "id", "userId") }.orEmpty() }
        }

        fun taskCreatorName(o: JSONObject): String {
            val c = o.optJSONObject("createdBy") ?: o.optJSONObject("creator")
            return pick(o, "creatorName", "createdByName").ifBlank {
                c?.let { pick(it, "fullName", "name", "username") }.orEmpty()
            }
        }

        fun taskPriorityLabel(raw: String): String = when (raw.uppercase()) {
            "LOW" -> "Низкий"
            "HIGH", "URGENT" -> "Высокий"
            else -> "Средний"
        }

        fun taskDeadlineIso(preset: String): String? {
            val zone = java.time.ZoneId.systemDefault()
            val at = java.time.LocalTime.of(18, 0)
            val day = when (preset) {
                "today" -> java.time.LocalDate.now(zone)
                "tomorrow" -> java.time.LocalDate.now(zone).plusDays(1)
                else -> return null
            }
            return java.time.ZonedDateTime.of(day, at, zone).toInstant().toString()
        }

        fun autoPriorityByDeadline(iso: String?): String {
            if (iso.isNullOrBlank()) return "MEDIUM"
            val at = parseZoned(iso)?.toInstant()?.toEpochMilli() ?: return "MEDIUM"
            val hours = (at - System.currentTimeMillis()) / 3_600_000.0
            return when {
                hours <= 24 -> "HIGH"
                hours <= 72 -> "MEDIUM"
                else -> "LOW"
            }
        }

        fun checklistItems(o: JSONObject): List<Triple<String, String, Boolean>> {
            val arr = o.optJSONArray("checklist") ?: o.optJSONArray("checklistItems") ?: return emptyList()
            val out = ArrayList<Triple<String, String, Boolean>>()
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                val id = pick(it, "id", "key", "_id")
                val title = pick(it, "title", "name", "text", "label")
                val done = it.optBoolean("isDone", false) || it.optBoolean("done", false) || it.optBoolean("checked", false)
                if (title.isNotBlank() || id.isNotBlank()) out += Triple(id, title, done)
            }
            return out
        }

        fun taskPeopleIds(o: JSONObject, key: String): List<String> {
            val arr = o.optJSONArray(key) ?: return emptyList()
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val item = arr.opt(i)
                val id = when (item) {
                    is JSONObject -> pick(item, "userId", "id").ifBlank {
                        item.optJSONObject("user")?.let { pick(it, "id", "userId") }.orEmpty()
                    }
                    else -> item?.toString().orEmpty()
                }
                if (id.isNotBlank() && id != "null" && id !in out) out += id
            }
            return out
        }

        fun taskWatchers(row: JsonRow): String {
            val arr = row.raw.optJSONArray("watchers") ?: return ""
            val names = (0 until arr.length()).mapNotNull { i ->
                val item = arr.opt(i)
                when (item) {
                    is JSONObject -> {
                        val nested = item.optJSONObject("user")
                        pick(item, "fullName", "name", "username").ifBlank {
                            nested?.let { pick(it, "fullName", "name", "username") }.orEmpty()
                        }.ifBlank { null }
                    }
                    else -> item?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                }
            }
            return names.joinToString(", ")
        }

        fun taskContextLabel(o: JSONObject): String {
            val type = pick(o, "contextType").uppercase().ifBlank { "NONE" }
            if (type == "NONE" || type.isBlank()) return ""
            val kind = when (type) {
                "CONTACT" -> "Контакт"
                "DEAL" -> "Сделка"
                "ENTITY" -> "Заведение"
                else -> type
            }
            val title = pick(o, "contextTitle")
            val id = pick(o, "contextId")
            return when {
                title.isNotBlank() && id.isNotBlank() -> "$kind: $title ($id)"
                title.isNotBlank() -> "$kind: $title"
                id.isNotBlank() -> "$kind: $id"
                else -> kind
            }
        }

        fun taskCommentCount(o: JSONObject): Int {
            val arr = o.optJSONArray("comments") ?: return 0
            var n = 0
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val text = pick(c, "text", "message", "body")
                if (!text.startsWith("[STATUS_CHANGE]")) n++
            }
            return n
        }

        fun canCompleteTaskReview(o: JSONObject, me: AppUser?): Boolean {
            if (me == null) return false
            if (me.role.equals("ADMIN", true)) return true
            val creator = taskCreatorId(o)
            return creator.isBlank() || creator == me.id
        }

        fun siblingStats(all: List<JsonRow>, row: JsonRow): Triple<Int, Int, Int>? {
            val gid = pick(row.raw, "siblingGroupId")
            if (gid.isBlank()) return null
            val group = all.filter { pick(it.raw, "siblingGroupId") == gid }
            if (group.size < 2) return null
            val done = group.count { isTaskDone(it) }
            val review = group.count { taskColumn(it) == "review" }
            return Triple(done, group.size, review)
        }

        fun parseTaskTemplates(o: JSONObject?): List<JSONObject> {
            if (o == null) return emptyList()
            val arr = o.optJSONArray("items") ?: o.optJSONArray("data") ?: o.optJSONArray("templates") ?: return emptyList()
            return eachObj(arr)
        }

        fun unreadTaskActivity(items: List<JSONObject>): Map<String, Int> {
            val out = HashMap<String, Int>()
            for (n in items) {
                if (n.optBoolean("isRead", false)) continue
                val type = pick(n, "type", "kind").uppercase()
                if (!type.startsWith("TASK") && type != "TASK_DEADLINE") continue
                val payload = n.optJSONObject("payload")
                val id = payload?.let { pick(it, "taskId", "id") }.orEmpty().ifBlank { pick(n, "taskId") }
                if (id.isNotBlank()) out[id] = (out[id] ?: 0) + 1
            }
            return out
        }

        fun templateChecklistLines(t: JSONObject): List<String> {
            val arr = t.optJSONArray("checklist") ?: t.optJSONArray("items") ?: return emptyList()
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val line = arr.optJSONObject(i)?.let { pick(it, "title", "text", "label") }
                    ?: arr.optString(i)
                if (line.isNotBlank()) out += line.trim()
            }
            return out
        }

        fun templateMatchesContext(t: JSONObject, contextType: String): Boolean {
            val arr = t.optJSONArray("contextTypes") ?: return true
            if (arr.length() == 0) return true
            for (i in 0 until arr.length()) {
                if (arr.optString(i).equals(contextType, true) || arr.optString(i).equals("NONE", true)) return true
            }
            return false
        }

        fun idsToJsonArray(ids: Collection<String>): JSONArray {
            val arr = JSONArray()
            ids.filter { it.isNotBlank() }.distinct().forEach { arr.put(it) }
            return arr
        }

        fun statusHistory(o: JSONObject): List<String> {
            val arr = o.optJSONArray("comments") ?: return emptyList()
            val rx = Regex("""^\[STATUS_CHANGE\]\s*(?:(.+?):\s*)?([A-Z_]+)\s*→\s*([A-Z_]+)\s*$""")
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val text = pick(c, "text", "message", "body")
                val m = rx.find(text.trim()) ?: continue
                val author = m.groupValues[1].ifBlank {
                    val a = c.optJSONObject("author")
                    a?.let { pick(it, "fullName", "name", "username") }.orEmpty().ifBlank { pick(c, "authorName") }
                }
                out += "${author.ifBlank { "—" }}: ${taskStatusLabel(m.groupValues[2])} → ${taskStatusLabel(m.groupValues[3])}"
            }
            return out
        }

        fun taskAssignees(row: JsonRow): String {
            val one = pick(row.raw, "assigneeName", "assignedTo", "ownerName", "username")
            val arr = row.raw.optJSONArray("assignees") ?: row.raw.optJSONArray("assigneeNames")
            if (arr != null && arr.length() > 0) {
                val names = (0 until arr.length()).mapNotNull { i ->
                    val item = arr.opt(i)
                    when (item) {
                        is JSONObject -> pick(item, "fullName", "name", "username").ifBlank { null }
                        else -> item?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                    }
                }
                if (names.isNotEmpty()) return names.joinToString(", ")
            }
            return one
        }

        fun taskNextLabel(row: JsonRow): String = when (taskColumn(row)) {
            "new" -> "Далее: В работу"
            "progress" -> "Далее: На проверку"
            "review" -> "Далее: Закрыть"
            else -> "Открыть"
        }

        private fun isoDayStart(ymd: String): String =
            java.time.LocalDate.parse(ymd).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString()

        private fun isoDayEnd(ymd: String): String =
            java.time.LocalDate.parse(ymd).atTime(23, 59, 59, 999_000_000)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toString()

        private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

        fun operationsListPath(
            status: String = "ACTIVE",
            pageSize: Int = 80,
            orderSearch: String? = null,
            paymentType: String = "ALL",
        ): String {
            val (from, to) = dateRange("30d", operationsWindow = true)
            return operationsQuery(from, to, status, 1, pageSize, paymentType, orderSearch = orderSearch.orEmpty())
        }

        fun operationsQuery(
            dateFrom: String,
            dateTo: String,
            status: String,
            page: Int,
            pageSize: Int = 50,
            paymentType: String = "ALL",
            establishmentType: String = "ALL",
            orderSearch: String = "",
            cityKey: String = "",
            sortBy: String = "orderDatetime",
            sortDir: String = "desc",
        ): String {
            val size = pageSize.coerceIn(25, 500)
            val pg = page.coerceAtLeast(1)
            val pay = paymentType.ifBlank { "ALL" }
            val st = status.ifBlank { "CREATED" }
            val q = StringBuilder(
                "/operations?dateFrom=${enc(isoDayStart(dateFrom))}&dateTo=${enc(isoDayEnd(dateTo))}" +
                    "&page=$pg&pageSize=$size&paymentType=${enc(pay)}&status=${enc(st)}" +
                    "&sortBy=${enc(sortBy.ifBlank { "orderDatetime" })}&sortDir=${enc(sortDir.ifBlank { "desc" })}",
            )
            if (establishmentType.isNotBlank() && establishmentType != "ALL") {
                q.append("&establishmentType=${enc(establishmentType)}")
            }
            val search = orderSearch.trim()
            if (search.isNotBlank()) q.append("&orderSearch=${enc(search)}")
            if (cityKey.isNotBlank()) q.append("&cityKey=${enc(cityKey)}")
            return q.toString()
        }

        fun pagedRows(o: JSONObject): PagedRows {
            val arr = firstJsonArray(o)
            val items = if (arr != null) {
                eachObj(arr).mapIndexed { i, x -> rowOf(x, i) }
            } else if (o.has("orderNumber") || o.has("id")) {
                listOf(rowOf(o, 0))
            } else {
                emptyList()
            }
            val total = when {
                o.has("total") && !o.isNull("total") -> o.optInt("total", items.size)
                o.has("count") && !o.isNull("count") -> o.optInt("count", items.size)
                else -> items.size
            }
            return PagedRows(items, total)
        }

        fun activityQuery(take: Int = 80, skip: Int = 0, kind: String = "ALL", q: String = ""): String {
            val p = StringBuilder("/operations/activity?take=${take.coerceIn(20, 200)}&skip=${skip.coerceAtLeast(0)}")
            if (kind.isNotBlank() && kind != "ALL") p.append("&kind=${enc(kind)}")
            if (q.trim().isNotBlank()) p.append("&q=${enc(q.trim())}")
            return p.toString()
        }

        fun activityActionLabel(raw: String): String = when (raw.uppercase()) {
            "OPERATIONS_TAB_OPENED", "TAB", "VIEW" -> "Открыл вкладку"
            "OPERATION_VIEWED", "CARD" -> "Открыл карточку"
            "OPERATION_CREATED", "CREATE" -> "Создал заказ"
            "OPERATION_UPDATED", "UPDATE", "EDIT" -> "Изменил заказ"
            "OPERATION_PROPOSED", "PROPOSE" -> "Предложил заказ"
            "OPERATION_CONFIRMED", "CONFIRM" -> "Подтвердил заказ"
            "OPERATION_DELETED", "DELETE" -> "Удалил заказ"
            "OPERATION_CANCELLED", "CANCEL" -> "Отменил заказ"
            else -> raw.ifBlank { "Действие" }
        }

        fun activityActorName(n: JSONObject): String {
            n.optJSONObject("actor")?.let { a ->
                val name = pick(a, "fullName", "username", "name")
                if (name.isNotBlank()) return name
            }
            n.optJSONObject("user")?.let { u ->
                val name = pick(u, "fullName", "username", "name")
                if (name.isNotBlank()) return name
            }
            return pick(n, "fullName", "username", "actorName", "actor").ifBlank { "—" }
        }

        fun opsCityOptions(me: AppUser?): List<DashCity> {
            val all = listOf(
                DashCity("", "Все города"),
                DashCity("ashgabat", "Ашхабад"),
                DashCity("mary", "Мары"),
            )
            val allowed = (me?.allowedCityKeys ?: emptyList())
                .map { it.trim().lowercase() }
                .filter { it == "ashgabat" || it == "mary" }
                .distinct()
            if (allowed.isEmpty()) return all
            val filtered = all.filter { it.key.isEmpty() || allowed.contains(it.key) }
            return if (allowed.size == 1) filtered.filter { it.key.isNotEmpty() } else filtered
        }

        fun intakeCityKey(event: JSONObject, pending: JSONObject?): String {
            val raw = pick(pending ?: JSONObject(), "cityKey").ifBlank { pick(event, "cityKey") }.trim().lowercase()
            return if (raw == "mary") "mary" else "ashgabat"
        }

        fun intakeTone(event: JSONObject, pending: JSONObject?, related: List<JSONObject>): IntakeTone {
            if (pending != null && pick(pending, "status").equals("PENDING_REVIEW", true)) {
                return IntakeTone("На проверке", "pending")
            }
            if (pick(event, "proposalError").isNotBlank()) return IntakeTone("Ошибка", "error")
            val num = pick(event, "orderNumber")
            if (num.isNotBlank() && related.any { pick(it, "orderNumber") == num && pick(it, "status").equals("CANCELLED", true) }) {
                return IntakeTone("Отменён", "muted")
            }
            if (
                (num.isNotBlank() && related.any { pick(it, "orderNumber") == num && pick(it, "status").equals("CREATED", true) }) ||
                pick(event, "operationId").isNotBlank()
            ) {
                return IntakeTone("Подтверждён", "ok")
            }
            if (pick(event, "statusTo").equals("GREEN", true)) return IntakeTone("В доставке", "muted")
            return IntakeTone("—", "muted")
        }

        fun intakeEventCaption(o: JSONObject): String {
            val kind = pick(o, "eventKind")
            val from = pick(o, "statusFrom")
            val to = pick(o, "statusTo")
            return when {
                kind.equals("DETAIL_CHANGE", true) -> "${to.ifBlank { "—" }}: изменение суммы или состава"
                from.isNotBlank() -> "$from → ${to.ifBlank { "—" }}"
                to.isNotBlank() -> to
                else -> "—"
            }
        }

        fun extraAmountValue(o: JSONObject, key: String): String {
            val extra = o.optJSONObject("extraAmounts") ?: return ""
            val v = extra.opt(key) ?: return pick(extra, key)
            return when (v) {
                JSONObject.NULL -> ""
                is Boolean -> if (v) "да" else "нет"
                else -> v.toString().trim()
            }
        }

        fun additionalSalesHints(o: JSONObject): List<String> {
            val arr = o.optJSONArray("additionalSalesItems")
            val items = if (arr != null) eachObj(arr).filter { jsonNum(it, "amount") > 0 } else emptyList()
            if (items.isNotEmpty()) {
                return items.map { row ->
                    val amt = tmt(jsonNum(row, "amount"))
                    if (pick(row, "kind").equals("PLATFORM_COVER", true)) "Покрытие из комиссии: $amt" else "Доп. продажи: $amt"
                }
            }
            val amt = jsonNum(o, "additionalSalesAmount")
            val kind = pick(o, "additionalSalesKind")
            if (amt > 0 && kind.isNotBlank() && !kind.equals("NONE", true)) {
                return listOf(
                    if (kind.equals("PLATFORM_COVER", true)) "Покрытие из комиссии: ${tmt(amt)}" else "Доп. продажи: ${tmt(amt)}",
                )
            }
            return emptyList()
        }

        fun platformDiscountHint(o: JSONObject): String? {
            val lines = opLineItems(o)
            var amount = 0.0
            val pcts = mutableListOf<Double>()
            for (line in lines) {
                if (!pick(line, "discountSource").equals("PLATFORM", true)) continue
                val pct = jsonNum(line, "discountPercent")
                if (pct <= 0) continue
                val net = jsonNum(line, "lineTotal")
                val grossRaw = jsonNum(line, "lineTotalGross")
                val gross = if (grossRaw > 0) grossRaw else if (pct < 100) net / (1 - pct / 100.0) else net
                amount += kotlin.math.max(0.0, gross - net)
                pcts += kotlin.math.round(pct * 100.0) / 100.0
            }
            amount = kotlin.math.round(amount * 100.0) / 100.0
            if (amount <= 0 && pcts.isEmpty()) return null
            val unique = pcts.map { String.format(java.util.Locale.US, "%.2f", it) }.distinct()
            return if (unique.size == 1) "Скидка платформы: ${unique[0]}%" else "Скидка платформы: ${tmt(amount)}"
        }

        fun parseIntakeBodyItems(o: JSONObject): List<JSONObject> {
            val arr = o.optJSONArray("bodyItems") ?: return emptyList()
            return eachObj(arr).map { item ->
                val cells = item.optJSONArray("cells")
                fun cell(i: Int): String = cells?.opt(i)?.toString().orEmpty().trim().trim('"', '\'')
                val name = pick(item, "name", "dishName", "title").ifBlank { cell(0) }.trim().trim('"', '\'')
                JSONObject()
                    .put("dishName", name.ifBlank { "—" })
                    .put("name", name.ifBlank { "—" })
                    .put("quantity", pick(item, "quantity").ifBlank { cell(1).ifBlank { "—" } })
                    .put("weightMl", pick(item, "weightMl").ifBlank { cell(2).ifBlank { "—" } })
                    .put("unitPrice", pick(item, "unitPrice").ifBlank { cell(3) })
                    .put("lineTotal", pick(item, "lineTotal").ifBlank { cell(4) })
            }
        }

        fun dedupeIntake(events: List<JsonRow>, keepAll: Boolean): List<JsonRow> {
            val sortedDesc = events.sortedByDescending { pick(it.raw, "createdAt") }
            if (keepAll) return sortedDesc
            val map = LinkedHashMap<String, JsonRow>()
            for (row in events) {
                val key = orderNo(row.raw).ifBlank { row.id }
                val prev = map[key]
                if (prev == null) {
                    map[key] = row
                    continue
                }
                fun score(r: JsonRow): Int {
                    var s = 0
                    if (pick(r.raw, "operationId").isNotBlank()) s += 4
                    s += if (pick(r.raw, "proposalError").isNotBlank()) -2 else 1
                    if (pick(r.raw, "establishmentTitle").isNotBlank()) s += 1
                    return s
                }
                fun whenMs(r: JsonRow): Long =
                    runCatching { java.time.Instant.parse(pick(r.raw, "createdAt")).toEpochMilli() }.getOrDefault(0L)
                val prevMs = whenMs(prev)
                val nextMs = whenMs(row)
                if (nextMs > prevMs || (nextMs == prevMs && score(row) > score(prev))) map[key] = row
            }
            return map.values.sortedByDescending { pick(it.raw, "createdAt") }
        }

        fun opMoney(o: JSONObject, vararg keys: String): Double = jsonNum(o, *keys)

        fun opWithDelivery(o: JSONObject): Double {
            val with = jsonNum(o, "orderAmountWithDelivery", "totalWithDelivery")
            if (with > 0) return with
            return jsonNum(o, "orderAmount", "amount") + jsonNum(o, "deliveryAmount", "delivery")
        }

        fun opLineItems(o: JSONObject): List<JSONObject> {
            val arr = o.optJSONArray("lineItems") ?: o.optJSONArray("items") ?: return emptyList()
            return eachObj(arr)
        }

        fun opUserId(o: JSONObject): String = pick(o, "appClientExternalUserId", "clientExternalId", "userId", "externalUserId")

        fun establishmentIdOf(o: JSONObject): String {
            nestedEstablishment(o)?.let { nested ->
                val id = pick(nested, "id")
                if (id.isNotBlank()) return id
            }
            return pick(o, "establishmentId")
        }

        fun orderNumberDigitCount(settings: JSONObject?): Int {
            val n = when {
                settings == null -> 5
                settings.has("orderNumberDigits") && !settings.isNull("orderNumberDigits") ->
                    settings.optInt("orderNumberDigits", 5)
                else -> 5
            }
            return n.coerceIn(3, 8)
        }

        fun parseMoneyInput(raw: String): Double? =
            raw.replace('\u00A0', ' ').replace(" ", "").replace(',', '.').trim()
                .takeIf { it.isNotBlank() }?.toDoubleOrNull()

        fun operationLocalDate(iso: String): String {
            val z = parseZoned(iso) ?: return ""
            return z.toLocalDate().toString()
        }

        fun operationLocalTimeHms(iso: String): String {
            val z = parseZoned(iso) ?: return ""
            return "%02d:%02d:%02d".format(z.hour, z.minute, z.second)
        }

        fun operationDatetimeIso(date: String, timeHms: String): String {
            val day = date.trim()
            val time = timeHms.trim()
            if (day.isBlank()) throw IllegalArgumentException("Укажите дату заказа")
            val m = Regex("""^([01]?\d|2[0-3]):([0-5]\d):([0-5]\d)$""").matchEntire(time)
                ?: throw IllegalArgumentException("Время укажите в формате ЧЧ:ММ:СС, например 12:43:16")
            return try {
                val local = java.time.LocalDate.parse(day).atTime(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                )
                local.atZone(java.time.ZoneId.systemDefault()).toInstant().toString()
            } catch (_: Exception) {
                throw IllegalArgumentException("Некорректная дата и время заказа")
            }
        }

        fun additionalSalesRows(o: JSONObject): List<JSONObject> {
            val arr = o.optJSONArray("additionalSalesItems")
            val mapped = if (arr != null) {
                eachObj(arr).mapNotNull { row ->
                    val kind = pick(row, "kind").uppercase()
                    if (kind != "CLIENT_SURCHARGE" && kind != "PLATFORM_COVER") return@mapNotNull null
                    val amount = jsonNum(row, "amount")
                    if (amount <= 0) return@mapNotNull null
                    val pay = if (pick(row, "paymentType").uppercase() == "ONLINE") "ONLINE" else "CASH"
                    JSONObject().put("amount", amount).put("kind", kind).put("paymentType", pay)
                }
            } else emptyList()
            if (mapped.isNotEmpty()) return mapped
            val amount = jsonNum(o, "additionalSalesAmount")
            val kind = pick(o, "additionalSalesKind").uppercase()
            if (amount > 0 && (kind == "CLIENT_SURCHARGE" || kind == "PLATFORM_COVER")) {
                val pay = if (pick(o, "additionalSalesPaymentType").uppercase() == "ONLINE") "ONLINE" else "CASH"
                return listOf(JSONObject().put("amount", amount).put("kind", kind).put("paymentType", pay))
            }
            return emptyList()
        }

        fun lineItemsForPatch(items: List<JSONObject>, fallbackPayment: String): JSONArray {
            val payFallback = if (fallbackPayment.equals("CASH", true)) "CASH" else "ONLINE"
            val arr = JSONArray()
            for (item in items) {
                val src = pick(item, "discountSource").uppercase()
                val discountSource = if (src == "PLATFORM" || src == "MERCHANT") src else "NONE"
                val pay = pick(item, "paymentType").uppercase().ifBlank { payFallback }
                val row = JSONObject()
                    .put("dishName", pick(item, "dishName", "name", "title").ifBlank { "Доп. позиция" })
                    .put("quantity", kotlin.math.max(1, jsonNum(item, "quantity", "qty").toInt()))
                    .put("lineTotal", jsonNum(item, "lineTotal", "amount"))
                    .put("discountPercent", jsonNum(item, "discountPercent"))
                    .put("discountSource", discountSource)
                    .put("paymentType", if (pay == "CASH") "CASH" else "ONLINE")
                val id = pick(item, "id")
                if (id.isNotBlank()) row.put("id", id)
                if (item.has("unitPrice") && !item.isNull("unitPrice")) {
                    row.put("unitPrice", jsonNum(item, "unitPrice"))
                }
                arr.put(row)
            }
            return arr
        }

        /**
         * Same PATCH body the production website sends from Операции → Редактирование.
         * A partial body (amounts only) is rejected by the API, so edits look like they never save.
         */
        fun operationPatchBody(
            original: JSONObject,
            orderNumber: String,
            orderNumberDigits: Int,
            establishmentId: String,
            paymentType: String,
            deliveryPaymentType: String,
            orderAmountRaw: String,
            commissionBaseRaw: String,
            deliveryAmountRaw: String,
            loyaltyRaw: String,
            promoRaw: String,
            isPlatformFreeDelivery: Boolean,
            compensationRaw: String,
            additionalSales: List<JSONObject>,
            orderDate: String,
            orderTimeHms: String,
            appClientExternalUserId: String,
            lineItems: List<JSONObject>,
        ): JSONObject {
            val number = orderNumber.trim()
            if (!Regex("^\\d{$orderNumberDigits}$").matches(number)) {
                throw IllegalArgumentException("Номер заказа: ровно $orderNumberDigits цифр")
            }
            val estId = establishmentId.trim().ifBlank { establishmentIdOf(original) }
            if (estId.isBlank()) throw IllegalArgumentException("Выберите заведение")
            val orderAmount = parseMoneyInput(orderAmountRaw)
            if (orderAmount == null || orderAmount <= 0) {
                throw IllegalArgumentException("Сумма без доставки должна быть больше 0")
            }
            val deliveryAmount = parseMoneyInput(deliveryAmountRaw)
            if (deliveryAmount == null || deliveryAmount < 0) {
                throw IllegalArgumentException("Сумма доставки должна быть 0 или больше")
            }
            val loyalty = parseMoneyInput(loyaltyRaw) ?: 0.0
            val promo = parseMoneyInput(promoRaw) ?: 0.0
            if (loyalty < 0) throw IllegalArgumentException("Программа лояльности: укажите процент 0 или больше")
            if (promo < 0) throw IllegalArgumentException("Промо: укажите процент 0 или больше")
            val compensation = if (isPlatformFreeDelivery) parseMoneyInput(compensationRaw) else 0.0
            if (isPlatformFreeDelivery && (compensation == null || compensation < 0)) {
                throw IllegalArgumentException("Укажите сумму бесплатной доставки от платформы (0 или больше)")
            }
            val extras = JSONArray()
            additionalSales.forEachIndexed { index, row ->
                val amount = jsonNum(row, "amount").takeIf { it > 0 } ?: parseMoneyInput(pick(row, "amount")) ?: 0.0
                if (amount <= 0) {
                    throw IllegalArgumentException("Доп. продажи (строка ${index + 1}): укажите сумму больше 0 или удалите строку")
                }
                val kind = pick(row, "kind").uppercase().ifBlank { "CLIENT_SURCHARGE" }
                val pay = if (pick(row, "paymentType").uppercase() == "ONLINE") "ONLINE" else "CASH"
                extras.put(JSONObject().put("amount", amount).put("kind", kind).put("paymentType", pay))
            }
            val client = appClientExternalUserId.filter { it.isDigit() }.take(4)
            if (client.isNotEmpty() && client.length != 4) {
                throw IllegalArgumentException("ID пользователя: ровно 4 цифры или оставьте пустым")
            }
            val pay = paymentType.trim().uppercase().ifBlank { pick(original, "paymentType").ifBlank { "CASH" } }
            val delPayRaw = deliveryPaymentType.trim().uppercase().ifBlank {
                pick(original, "deliveryPaymentType").ifBlank { if (pay == "CASH") "CASH" else "ONLINE" }
            }
            val commissionBase = parseMoneyInput(commissionBaseRaw)?.takeIf { it > 0 } ?: orderAmount
            val body = JSONObject()
                .put("orderNumber", number)
                .put("establishmentId", estId)
                .put("paymentType", pay)
                .put("deliveryPaymentType", if (delPayRaw == "CASH") "CASH" else "ONLINE")
                .put("orderAmount", orderAmount)
                .put("commissionBaseAmount", commissionBase)
                .put("deliveryAmount", deliveryAmount)
                .put("loyaltyDiscountPercent", loyalty)
                .put("promoDiscountPercent", promo)
                .put("isPlatformFreeDelivery", isPlatformFreeDelivery)
                .put("platformDeliveryCompensation", if (isPlatformFreeDelivery) compensation else 0)
                .put("additionalSalesItems", extras)
                .put("orderDatetime", operationDatetimeIso(orderDate, orderTimeHms))
            if (client.isEmpty()) body.put("appClientExternalUserId", JSONObject.NULL)
            else body.put("appClientExternalUserId", client)
            if (lineItems.isNotEmpty()) {
                body.put("lineItems", lineItemsForPatch(lineItems, pay))
            }
            return body
        }

        fun lineGross(lineTotal: Double, discountPercent: Double, discountSource: String): Double {
            if (!discountSource.equals("NONE", true) && discountPercent > 0 && discountPercent < 100 && lineTotal > 0) {
                return kotlin.math.round(lineTotal / (1 - discountPercent / 100.0) * 100.0) / 100.0
            }
            return kotlin.math.round(lineTotal * 100.0) / 100.0
        }

        fun orderAmountFromLines(items: List<JSONObject>): String {
            if (items.isEmpty()) return ""
            val sum = items.fold(0.0) { acc, item ->
                val net = jsonNum(item, "lineTotal", "amount")
                val pct = jsonNum(item, "discountPercent")
                val src = pick(item, "discountSource").ifBlank { "NONE" }
                acc + lineGross(net, pct, src)
            }
            return (kotlin.math.round(sum * 100.0) / 100.0).toString()
        }

        fun paymentTypeFromLines(
            items: List<JSONObject>,
            deliveryAmount: Double,
            deliveryPaymentType: String,
            freeDelivery: Boolean,
            extras: List<JSONObject>,
        ): String {
            var online = 0.0
            var cash = 0.0
            fun add(amount: Double, pay: String) {
                if (pay.equals("CASH", true)) cash += amount else online += amount
            }
            items.forEach { add(jsonNum(it, "lineTotal", "amount"), pick(it, "paymentType").ifBlank { "ONLINE" }) }
            val delivery = if (freeDelivery) 0.0 else deliveryAmount
            if (delivery > 0) add(delivery, deliveryPaymentType)
            extras.forEach { row ->
                val amt = jsonNum(row, "amount")
                if (amt > 0 && pick(row, "kind").equals("CLIENT_SURCHARGE", true)) {
                    add(amt, pick(row, "paymentType"))
                }
            }
            online = kotlin.math.round(online * 100.0) / 100.0
            cash = kotlin.math.round(cash * 100.0) / 100.0
            return when {
                online > 0 && cash > 0 -> "MIXED"
                cash > 0 -> "CASH"
                else -> "ONLINE"
            }
        }

        fun rescaleLineQty(item: JSONObject, newQty: Int): JSONObject {
            val next = JSONObject(item.toString())
            val oldQty = kotlin.math.max(1, jsonNum(item, "quantity", "qty").toInt())
            val qty = kotlin.math.max(1, newQty)
            next.put("quantity", qty)
            if (qty == oldQty) return next
            val unit = jsonNum(item, "unitPrice")
            val total = jsonNum(item, "lineTotal", "amount")
            val computed = if (unit > 0) unit * qty else total / oldQty * qty
            val rounded = kotlin.math.round(computed * 100.0) / 100.0
            next.put("lineTotal", rounded)
            if (qty > 0) next.put("unitPrice", kotlin.math.round(rounded / qty * 100.0) / 100.0)
            return next
        }

        fun isAccountingPath(path: String): Boolean {
            val c = path.substringBefore("?").trimEnd('/')
            return c == "/accounting" || c.startsWith("/accounting/") || c.startsWith("/reconciliation")
        }

        fun accountingTabFromPath(path: String, title: String = ""): String {
            val c = path.substringBefore("?").trimEnd('/')
            return when {
                c.contains("counterpart") -> "counterparties"
                c.contains("/items") -> "items"
                c.contains("warehouse") -> "warehouses"
                c.contains("sales-invoice") || c.endsWith("/sales") -> "sales"
                c.contains("purchase") -> "purchases"
                c.contains("cash-order") || c.endsWith("/cash") -> "cash"
                c.contains("tax-report") || c.contains("declaration") -> "declarations"
                c.contains("report") -> "reports"
                c.contains("employee") -> "employees"
                c.contains("timesheet") -> "timesheet"
                c.contains("calendar") -> "calendar"
                c.contains("hr-event") || c.endsWith("/hr") -> "hr"
                c.contains("payroll") && (title.contains("Расчёт", true) || c.contains("slip")) -> "slips"
                c.contains("payroll") -> "payroll"
                c.contains("reconciliation") -> "reconciliation"
                else -> "registration"
            }
        }

        fun isSettingsWorkspace(path: String, tab: String = ""): Boolean {
            if (tab.equals("settings", true)) return true
            val c = path.substringBefore("?").trimEnd('/')
            return c == "/auth/me" ||
                c.startsWith("/settings") ||
                c.startsWith("/app-stores") ||
                c == "/sms/status" ||
                c == "/email/status" ||
                c == "/push/status" ||
                c == "/users" ||
                c.startsWith("/users/")
        }

        fun settingsTabFromPath(path: String, title: String = ""): String {
            val c = path.substringBefore("?").trimEnd('/')
            val t = title.trim().lowercase()
            return when {
                c == "/auth/me" || t.contains("профиль") -> "profile"
                c.endsWith("/menu") || t == "меню" -> "menu"
                c.endsWith("/general") || t == "общие" -> "general"
                c.contains("/monitor") || t.contains("монитор") -> "monitor"
                c == "/sms/status" || t.contains("sms") -> "sms"
                c == "/email/status" || t.contains("почт") || t == "email" -> "email"
                c == "/push/status" || t.contains("push") -> "push"
                c.contains("app-stores") || t.contains("магазин") -> "app_stores"
                c.contains("telegram") || t.contains("мессенджер") -> "messengers"
                c.startsWith("/users") || t.contains("пользовател") -> "users"
                c.contains("audit") || t.contains("журнал") || t.contains("аудит") -> "audit"
                c.contains("cities") || t.contains("город") -> "sales_cities"
                else -> "profile"
            }
        }

        fun isSalesWorkspace(path: String, tab: String = ""): Boolean {
            if (tab.equals("settings", true)) return false
            val c = path.substringBefore("?").trimEnd('/')
            if (c.startsWith("/sales")) return true
            return tab == "sales" && (c.startsWith("/sms/broadcasts") || c.startsWith("/sms/recipient"))
        }

        fun salesTabFromPath(path: String, title: String = ""): String {
            val c = path.substringBefore("?").trimEnd('/')
            return when {
                c.contains("/sms") || title.contains("SMS", true) -> "sms"
                c.contains("cities") || title.contains("Город", true) -> "cities"
                else -> "establishments"
            }
        }

        fun salesKindFromTitle(title: String): String = when (title.trim()) {
            "Магазины" -> "SHOP"
            "Рестораны" -> "RESTAURANT"
            else -> "ALL"
        }

        fun salesStatusLabel(raw: String): String = when (raw.trim().uppercase()) {
            "NEW" -> "Новый"
            "CALL_DONE" -> "Был созвон"
            "PRESENTATION_DONE" -> "Провели презентацию"
            "THINKING" -> "Ожидание"
            "AWAITING_MENU" -> "Ждём меню"
            "CONNECTED" -> "Подключен"
            "DECLINED" -> "Отказались"
            "BLACKLIST" -> "Черный список"
            else -> raw.ifBlank { "—" }
        }

        fun salesKindLabel(raw: String): String = when (raw.trim().uppercase()) {
            "SHOP", "STORE" -> "Магазин"
            "RESTAURANT" -> "Ресторан"
            else -> raw.ifBlank { "—" }
        }

        fun salesCommissionModelLabel(raw: String): String = when (raw.trim().uppercase()) {
            "MERCHANT_DISCOUNT" -> "Комиссия"
            "MARKUP" -> "Наценка"
            "HYBRID" -> "Гибрид"
            else -> raw.ifBlank { "Комиссия" }
        }

        fun salesAgreementLabel(o: JSONObject): String = when {
            o.optBoolean("withoutAgreement", false) -> "Без договора"
            o.optBoolean("hasAgreementPdf", false) || (o.optJSONArray("agreementDocuments")?.length() ?: 0) > 0 -> "Да"
            o.optBoolean("agreementAtEstablishment", false) -> "В заведении"
            else -> "—"
        }

        fun salesCityName(o: JSONObject): String {
            o.optJSONObject("city")?.let { nested ->
                val name = pick(nested, "name", "title")
                if (name.isNotBlank()) return name
            }
            return pick(o, "cityName", "city")
        }

        fun salesManagerName(o: JSONObject): String {
            o.optJSONObject("assignedUser")?.let { nested ->
                val name = pick(nested, "fullName", "name", "username")
                if (name.isNotBlank()) return name
            }
            return pick(o, "assignedUserName", "managerName", "manager")
        }

        fun salesContacts(o: JSONObject): List<JSONObject> {
            val arr = o.optJSONArray("contacts") ?: return emptyList()
            return eachObj(arr)
        }

        fun salesContactLine(o: JSONObject): String {
            val parts = salesContacts(o).map { c ->
                listOf(pick(c, "phoneDisplay", "phone"), pick(c, "lprName", "name")).filter { it.isNotBlank() }.joinToString(" / ")
            }.filter { it.isNotBlank() }
            if (parts.isNotEmpty()) return parts.joinToString(" · ")
            return phoneOf(o)
        }

        fun salesEstablishmentsQuery(
            take: Int,
            skip: Int,
            q: String,
            status: String,
            cityId: String,
            kind: String,
            assignedUserId: String,
            mine: Boolean,
            marketplace: String,
            deleted: Boolean,
            sortBy: String = "",
            sortDir: String = "asc",
        ): String {
            val p = StringBuilder(if (deleted) "/sales/establishments/deleted" else "/sales/establishments")
            p.append("?take=${take.coerceIn(20, 200)}&skip=${skip.coerceAtLeast(0)}")
            if (q.trim().isNotBlank()) p.append("&q=${enc(q.trim())}")
            if (status.isNotBlank() && status != "ALL") p.append("&status=${enc(status)}")
            if (cityId.isNotBlank()) p.append("&cityId=${enc(cityId)}")
            if (kind.isNotBlank() && kind != "ALL") p.append("&kind=${enc(kind)}")
            if (assignedUserId.isNotBlank()) p.append("&assignedUserId=${enc(assignedUserId)}")
            if (mine) p.append("&mine=1")
            if (marketplace == "1" || marketplace == "0") p.append("&marketplace=$marketplace")
            if (sortBy.isNotBlank()) p.append("&sortBy=${enc(sortBy)}&sortDir=${enc(sortDir.ifBlank { "asc" })}")
            return p.toString()
        }

        val salesFunnelStatuses = listOf(
            "ALL" to "Все",
            "NEW" to "Новый",
            "CALL_DONE" to "Был созвон",
            "PRESENTATION_DONE" to "Провели презентацию",
            "THINKING" to "Ожидание",
            "AWAITING_MENU" to "Ждём меню",
            "CONNECTED" to "Подключен",
            "DECLINED" to "Отказались",
            "BLACKLIST" to "Черный список",
        )

        fun accountingDocStatus(raw: String): String = when (raw.trim().uppercase()) {
            "DRAFT" -> "Черновик"
            "POSTED" -> "Проведён"
            "VOID" -> "Аннулирован"
            "SUBMITTED" -> "На утверждении"
            "APPROVED" -> "Утверждена"
            "PAID" -> "Выплачена"
            "READY" -> "Рассчитана"
            else -> raw.ifBlank { "—" }
        }

        fun counterpartyKindLabel(raw: String): String = when (raw.trim().uppercase()) {
            "CUSTOMER" -> "Покупатель"
            "SUPPLIER" -> "Поставщик"
            "BOTH" -> "Оба"
            else -> raw.ifBlank { "—" }
        }

        fun itemKindLabel(raw: String): String = when (raw.trim().uppercase()) {
            "GOOD", "GOODS" -> "Товар"
            "SERVICE" -> "Услуга"
            else -> raw.ifBlank { "—" }
        }

        fun employeeKindLabel(raw: String): String = when (raw.trim().uppercase()) {
            "OFFICE" -> "Офис"
            "COURIER_LINKED" -> "Курьер"
            "OUTSOURCE" -> "Аутсорс"
            else -> raw.ifBlank { "—" }
        }

        fun hrEventLabel(raw: String): String = when (raw.trim().uppercase()) {
            "HIRE" -> "Приём"
            "VACATION" -> "Отпуск"
            "SICK_LEAVE" -> "Больничный"
            "TERMINATION" -> "Увольнение"
            "TRANSFER" -> "Перевод"
            else -> raw.ifBlank { "—" }
        }

        fun calendarKindLabel(raw: String): String = when (raw.trim().uppercase()) {
            "WORKING" -> "Рабочий"
            "SHORT_DAY" -> "Сокращённый"
            "WEEKEND" -> "Выходной"
            "HOLIDAY" -> "Праздник"
            else -> raw.ifBlank { "—" }
        }

        fun cashDirectionLabel(raw: String): String = when (raw.trim().uppercase()) {
            "IN" -> "Приход"
            "OUT" -> "Расход"
            else -> raw.ifBlank { "—" }
        }

        fun paymentMethodLabel(raw: String): String = when (raw.trim().uppercase()) {
            "CREDIT" -> "В долг"
            "CASH" -> "Наличные"
            "BANK" -> "Банк"
            else -> raw.ifBlank { "—" }
        }

        fun monthDays(month: String): Int {
            return try {
                val p = month.trim().take(7)
                val y = p.substring(0, 4).toInt()
                val m = p.substring(5, 7).toInt()
                java.time.YearMonth.of(y, m).lengthOfMonth()
            } catch (_: Exception) {
                31
            }
        }

        fun monthRange(month: String): Pair<String, String> {
            val p = month.trim().take(7).ifBlank { java.time.YearMonth.now().toString() }
            return try {
                val ym = java.time.YearMonth.parse(p)
                ym.atDay(1).toString() to ym.atEndOfMonth().toString()
            } catch (_: Exception) {
                val t = java.time.LocalDate.now()
                t.withDayOfMonth(1).toString() to t.toString()
            }
        }

        fun opEstType(o: JSONObject): String {
            nestedEstablishment(o)?.let { n ->
                val t = pick(n, "type", "kind", "establishmentType")
                if (t.isNotBlank()) return t
            }
            return pick(o, "establishmentType", "type")
        }

        fun reportsSummaryPath(preset: String): String {
            val (from, to) = dateRange(preset)
            return "/reports/summary?dateFrom=${enc(isoDayStart(from))}&dateTo=${enc(isoDayEnd(to))}" +
                "&paymentType=ALL&establishmentType=ALL&dateBasis=business"
        }

        fun logisticsIntakePath(pageSize: Int = 200, transition: String = "ALL", cityKey: String = ""): String {
            val size = pageSize.coerceIn(20, 200)
            val q = StringBuilder("/operations/logistics-intake?take=$size&skip=0&transition=${enc(transition.ifBlank { "ALL" })}")
            if (cityKey.isNotBlank()) q.append("&cityKey=${enc(cityKey)}")
            return q.toString()
        }

        fun listFetchPath(specPath: String, filter: String, pageSize: Int, query: String = "", paymentType: String = "ALL"): String {
            val clean = specPath.substringBefore("?").trimEnd('/')
            val size = pageSize.coerceIn(20, 200)
            return when {
                isOperationsList(clean) -> {
                    val status = when (filter) {
                        "created" -> "CREATED"
                        "review" -> "PENDING_REVIEW"
                        "cancelled" -> "CANCELLED"
                        else -> "ACTIVE"
                    }
                    val search = query.trim().takeIf { looksLikeOrderSearch(it) }
                    operationsListPath(status, size, search, paymentType)
                }
                clean.contains("logistics-intake") -> logisticsIntakePath(size)
                else -> withTake(specPath, size)
            }
        }

        fun parseReportPulse(o: JSONObject, hint: String): List<PulseMetric> {
            val cur = o.optJSONObject("totals") ?: o.optJSONObject("current") ?: o.optJSONObject("summary") ?: o
            val range = periodRangeLabel(
                when {
                    hint.contains("7") -> "7d"
                    hint.contains("30") -> "30d"
                    else -> "month"
                },
            ).ifBlank { hint }
            val gross = jsonNumOpt(cur, "profitBeforeDeductions", "grossProfit", "profitGross")
            return listOf(
                PulseMetric("Заказы", prettyNumber(jsonNum(cur, "ordersCount", "orders").toLong().toString()).ifBlank { "0" }, range, "📦"),
                PulseMetric("Оборот (без доставки)", tmt(jsonNum(cur, "turnover", "revenue", "turnoverWithoutDelivery")), range, "💰"),
                PulseMetric("Сумма доставок", tmt(jsonNum(cur, "deliveryTotal", "deliveryAmount", "deliverySum")), range, "🚚"),
                PulseMetric("Бесплатные доставки", prettyNumber(jsonNum(cur, "freeDeliveryCount", "freeDeliveries").toLong().toString()).ifBlank { "0" }, range, "🎁", invertDelta = true),
                PulseMetric("Средний чек", tmt(jsonNum(cur, "avgCheck", "averageCheck", "aov")), range, "🧾"),
                PulseMetric(
                    "Чистая прибыль",
                    tmt(jsonNum(cur, "profit", "netProfit")),
                    range,
                    "📈",
                    extra = if (gross != null) "валовая прибыль: ${tmt(gross)}" else "",
                ),
            )
        }

        fun parseNotifications(o: JSONObject?): List<JSONObject> {
            if (o == null || o.length() == 0) return emptyList()
            val arr = o.optJSONArray("items")
                ?: o.optJSONArray("notifications")
                ?: o.optJSONArray("data")
                ?: firstJsonArray(o)
            return if (arr != null) eachObj(arr) else emptyList()
        }

        fun noticeTitle(o: JSONObject?): String {
            val title = pick(o, "title", "subject", "heading", "name")
            if (title.isNotBlank()) return title
            return pick(o, "type", "kind").ifBlank { "Уведомление" }
        }

        fun noticeBody(o: JSONObject?): String =
            pick(o, "body", "message", "text", "preview", "content", "description")

        private val ARRAY_KEYS = listOf(
            "items", "data", "operations", "rows", "results", "users", "threads", "messages",
            "couriers", "establishments", "contacts", "employees", "logs", "tasks", "chats",
            "notifications", "calls", "deliveries", "payroll", "events", "clients", "invoices",
            "counterparties", "warehouses", "recommendations", "promoCodes", "banners",
            "profiles", "broadcasts", "groups", "logs", "portraits", "ads", "codes",
            "organizations", "units", "slips", "recordings", "messages",
            "campaigns", "outgoing", "incoming", "sms", "recipients",
        )

        fun firstJsonArray(o: JSONObject): JSONArray? {
            var empty: JSONArray? = null
            fun consider(arr: JSONArray): JSONArray? {
                if (arr.length() > 0) return arr
                if (empty == null) empty = arr
                return null
            }
            for (k in ARRAY_KEYS) {
                if (o.opt(k) is JSONArray) {
                    consider(o.getJSONArray(k))?.let { return it }
                }
            }
            for (k in ARRAY_KEYS) {
                val nested = o.optJSONObject(k) ?: continue
                for (k2 in ARRAY_KEYS) {
                    if (nested.opt(k2) is JSONArray) {
                        consider(nested.getJSONArray(k2))?.let { return it }
                    }
                }
            }
            return empty
        }

        fun apiMessage(o: JSONObject): String {
            val m = o.opt("message")
            if (m is JSONArray) {
                return (0 until m.length()).map { m.optString(it) }.filter { it.isNotBlank() && it != "null" }.joinToString("; ")
            }
            return pick(o, "message", "responsePreview", "error", "detail").ifBlank { "" }
        }

        fun rowsFrom(text: String): List<JsonRow> {
            val trimmed = text.trim()
            val arr: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> firstJsonArray(JSONObject(trimmed)) ?: JSONArray().put(JSONObject(trimmed))
            }
            val out = ArrayList<JsonRow>(arr.length())
            for (i in 0 until arr.length()) {
                val x = arr.optJSONObject(i) ?: continue
                out += rowOf(x, i)
            }
            return out
        }

        fun rowOf(x: JSONObject, i: Int): JsonRow {
            val id = pick(x, "id", "callLogId", "externalId", "_id", "uuid", "orderId", "threadId", "chatId")
            val title = displayTitle(x, fallback = "Запись ${i + 1}")
            val sub = previewText(x).ifBlank {
                listOf(
                    pickOrEmpty(x, "status", "kind", "role", "state", "city"),
                    pickOrEmpty(x, "orderAmount", "amount", "phone"),
                ).filter { it.isNotBlank() && !looksLikeId(it) }.joinToString(" · ")
            }
            return JsonRow(id.ifBlank { "$i" }, title, sub, x)
        }

        fun looksLikeId(s: String): Boolean {
            val t = s.trim()
            if (t.length < 8) return false
            if (t.startsWith("{") || t.startsWith("[")) return true
            val hexish = t.replace("-", "")
            if (hexish.length in 32..36 && hexish.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return true
            if (t.length >= 36 && t.count { it == '-' } >= 4) return true
            return false
        }

        fun humanText(raw: String): String {
            val t = raw.trim()
            if (t.isBlank() || t == "null" || t == "—" || t == "...") return ""
            if (t.startsWith("{") || t.startsWith("[")) {
                return try {
                    if (t.startsWith("[")) {
                        val a = JSONArray(t)
                        val first = a.optJSONObject(0) ?: return ""
                        humanText(pick(first, "text", "body", "content", "message", "html"))
                    } else {
                        val o = JSONObject(t)
                        humanText(pick(o, "text", "body", "content", "message", "html", "name", "title"))
                    }
                } catch (_: Exception) {
                    ""
                }
            }
            if (looksLikeId(t)) return ""
            return t
        }

        fun displayTitle(o: JSONObject, fallback: String = "Диалог"): String {
            val candidates = listOf(
                pick(o, "clientName", "customerName", "contactName", "fullName"),
                pick(o, "orderNumber"),
                pick(o, "destination", "fromNumber", "toNumber", "from", "to"),
                pick(o, "phone", "clientPhone"),
                pick(o, "subject", "title", "name", "username", "label"),
                pick(o, "message", "text", "body"),
            )
            return candidates.map { humanText(it) }.firstOrNull { it.isNotBlank() } ?: fallback
        }

        fun displayTitle(row: JsonRow): String {
            val fromRaw = displayTitle(row.raw, fallback = "")
            if (fromRaw.isNotBlank()) return fromRaw
            val fromTitle = humanText(row.title)
            return fromTitle.ifBlank { "Диалог" }
        }

        fun previewText(o: JSONObject): String {
            val lastObj = o.optJSONObject("lastMessage") ?: o.optJSONObject("last") ?: o.optJSONObject("preview")
            if (lastObj != null) {
                val t = humanText(pick(lastObj, "text", "body", "content", "message", "html"))
                if (t.isNotBlank()) return t
                if (pick(lastObj, "imageUrl", "image", "audioUrl", "voiceUrl").isNotBlank()) return "Вложение"
            }
            val last = o.opt("lastMessage")
            if (last is String) {
                val t = humanText(last)
                if (t.isNotBlank()) return t
            }
            return humanText(pick(o, "text", "message", "body", "subject", "email", "snippet"))
        }

        fun previewText(row: JsonRow): String {
            val t = previewText(row.raw)
            if (t.isNotBlank()) return t
            return humanText(row.subtitle)
        }

        fun pulseMetrics(vararg objects: JSONObject): List<PulseMetric> {
            val out = ArrayList<PulseMetric>()
            val seen = HashSet<String>()
            val rules = listOf(
                Triple(listOf("заказ", "order", "orderscount"), "Заказы", "📦"),
                Triple(listOf("оборот", "turnover", "revenue", "gmv", "withoutDelivery", "without_delivery"), "Оборот (без доставки)", "💰"),
                Triple(listOf("сумма достав", "deliverysum", "deliveryamount", "deliveryfee", "delivery_amount"), "Сумма доставки", "🚚"),
                Triple(listOf("бесплат", "freedeliver", "free_deliver"), "Бесплатные доставки", "🎁"),
                Triple(listOf("средний чек", "avgcheck", "averagecheck", "aov", "avg_check"), "Средний чек", "🧾"),
                Triple(listOf("чист", "netprofit", "profit", "net_profit"), "Чистая прибыль", "📈"),
                Triple(listOf("скачив", "download", "install"), "Скачивания", "📲"),
            )
            for (o in objects) {
                if (o.length() == 0) continue
                for ((k, v) in flatten(o)) {
                    val key = k.lowercase()
                    val num = prettyNumber(v)
                    if (num.isBlank()) continue
                    val rule = rules.firstOrNull { (needles, _, _) -> needles.any { key.contains(it.lowercase()) } } ?: continue
                    val label = rule.second
                    if (!seen.add(label)) continue
                    out += PulseMetric(label, num, "за этот месяц", rule.third)
                }
            }
            return out
        }

        fun prettyNumber(raw: String): String {
            val t = raw.trim().replace(" ", "").replace('\u00A0', ' ').replace(',', '.')
            val n = t.toDoubleOrNull() ?: return if (raw.any { it.isDigit() } && raw.length < 24) raw else ""
            if (n == n.toLong().toDouble() && kotlin.math.abs(n) < 1_000_000_000) {
                return ruGrouped(n.toLong().toDouble(), 0)
            }
            return ruGrouped(n, 2)
        }

        fun ruGrouped(n: Double, fractionDigits: Int): String {
            if (!n.isFinite()) return "—"
            val nf = java.text.NumberFormat.getNumberInstance(java.util.Locale("ru", "RU"))
            nf.minimumFractionDigits = fractionDigits
            nf.maximumFractionDigits = fractionDigits
            return nf.format(n).replace('\u00A0', ' ').replace('\u202F', ' ')
        }

        fun messageList(o: JSONObject, meId: String): List<ChatMsg>? {
            val arr = when {
                o.opt("messages") is JSONArray -> o.getJSONArray("messages")
                o.opt("items") is JSONArray -> o.getJSONArray("items")
                o.opt("data") is JSONArray -> o.getJSONArray("data")
                else -> return null
            }
            if (arr.length() == 0) return emptyList()
            val first = arr.optJSONObject(0) ?: return null
            val looks = first.has("text") || first.has("body") || first.has("content") ||
                first.has("message") || first.has("html") || first.has("imageUrl") ||
                first.has("attachments") || first.has("image") || first.has("fileUrl") ||
                first.has("audioUrl") || first.has("voiceUrl") || first.has("sender") ||
                first.has("senderId") || first.has("files") || first.has("reactions")
            if (!looks) return null
            val out = ArrayList<ChatMsg>(arr.length())
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val authorObj = m.optJSONObject("author") ?: m.optJSONObject("user")
                    ?: m.optJSONObject("from") ?: m.optJSONObject("sender")
                val authorId = pick(m, "authorId", "userId", "fromId", "senderId").ifBlank {
                    authorObj?.let { pick(it, "id", "userId") }.orEmpty()
                }
                val author = pick(m, "authorName", "username", "fromName").ifBlank {
                    authorObj?.let { pick(it, "fullName", "username", "name") }.orEmpty()
                }
                val image = imageOf(m)
                val audio = audioOf(m)
                val fileName = fileNameOf(m)
                val text = humanText(pick(m, "text", "body", "content", "message", "html"))
                val reactions = reactionEmojis(m)
                val mine = m.optBoolean("mine", false) ||
                    (authorId.isNotBlank() && authorId == meId)
                out += ChatMsg(
                    id = pick(m, "id", "_id").ifBlank { "$i" },
                    text = text.ifBlank { if (image.isNotBlank() || audio.isNotBlank() || fileName.isNotBlank()) "" else "—" },
                    author = author.ifBlank { "—" },
                    mine = mine,
                    time = pick(m, "createdAt", "sentAt", "time", "updatedAt"),
                    imageUrl = if (audio.isNotBlank() && image == audio) "" else image,
                    audioUrl = audio,
                    fileName = fileName,
                    reactions = reactions,
                    tick = if (mine) tickOf(m) else "",
                    audioHintSec = audioDurationHint(m),
                    avatarUrl = avatarUrlOf(authorObj).ifBlank { avatarUrlOf(m) },
                )
            }
            return out
        }

        fun workspaceFilePath(id: String): String {
            if (id.isBlank()) return ""
            return "/workspace/files/" + java.net.URLEncoder.encode(id, "UTF-8")
        }

        fun fileObjects(m: JSONObject): List<JSONObject> {
            val out = ArrayList<JSONObject>()
            val arr = when {
                m.opt("files") is JSONArray -> m.getJSONArray("files")
                m.opt("attachments") is JSONArray -> m.getJSONArray("attachments")
                m.opt("media") is JSONArray -> m.getJSONArray("media")
                else -> null
            }
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    if (o != null) {
                        out += o
                        continue
                    }
                    val id = arr.optString(i)
                    if (id.isNotBlank()) out += JSONObject().put("id", id)
                }
            }
            m.optJSONObject("file")?.let { out += it }
            m.optJSONObject("attachment")?.let { out += it }
            m.optJSONObject("audio")?.let { out += it }
            return out
        }

        fun firstFile(m: JSONObject): JSONObject? = fileObjects(m).firstOrNull()

        fun tickOf(m: JSONObject): String {
            val raw = pick(m, "outboundDelivery", "deliveryStatus", "readStatus", "ticketStatus").lowercase()
            when {
                raw == "read" || raw.contains("прочит") -> return "read"
                raw == "delivered" || raw.contains("достав") -> return "delivered"
                raw == "sent" || raw == "sending" || raw.contains("отправ") -> return "sent"
            }
            if (m.optBoolean("isRead", false) || pick(m, "readAt", "seenAt").isNotBlank()) return "read"
            if (m.optBoolean("delivered", false) || pick(m, "deliveredAt").isNotBlank()) return "delivered"
            val st = pick(m, "status", "state").lowercase()
            return when {
                st == "read" || st.contains("read") -> "read"
                st == "delivered" || st.contains("deliver") -> "delivered"
                else -> "sent"
            }
        }

        fun audioDurationHint(m: JSONObject): Int {
            val direct = pick(m, "duration", "durationSec", "audioDuration").toIntOrNull()
            if (direct != null && direct > 0) return direct
            val f = firstFile(m) ?: return 0
            val named = pick(f, "duration", "durationSec").toIntOrNull()
            if (named != null && named > 0) return named
            val bytes = f.optLong("sizeBytes", 0L).takeIf { it > 0 } ?: f.optLong("size", 0L)
            if (bytes <= 0L) return 0
            return (bytes / 12_000L).toInt().coerceAtLeast(1)
        }

        fun bubbleClock(raw: String): String {
            if (raw.isBlank()) return ""
            val zoned = parseZoned(raw) ?: return prettyTime(raw)
            return "%02d:%02d".format(zoned.hour, zoned.minute)
        }

        fun imageOf(m: JSONObject): String {
            val direct = pick(m, "imageUrl", "image", "photoUrl", "fileUrl", "attachmentUrl", "mediaUrl", "thumbnailUrl", "src")
            if (looksLikeImageUrl(direct) && !looksLikeAudioUrl(direct)) return direct
            for (nested in fileObjects(m) + listOfNotNull(m.optJSONObject("image"))) {
                val mime = pick(nested, "mime", "mimeType", "contentType")
                val kind = pick(nested, "kind", "type")
                val name = pick(nested, "originalName", "name", "filename")
                val u = pick(nested, "url", "path", "src", "href", "fileUrl")
                if (isAudioFile(mime, name, u, kind)) continue
                if (looksLikeImageUrl(u)) return u
                if (looksLikeImageMime(mime) || looksLikeImageUrl(name)) {
                    val id = pick(nested, "id", "fileId", "_id")
                    if (u.isNotBlank()) return u
                    if (id.isNotBlank()) return workspaceFilePath(id)
                }
            }
            val url = pick(m, "url")
            return if (looksLikeImageUrl(url) && !url.contains("/workspace/") && !url.contains("/support/")) url else ""
        }

        fun reactionEmojis(m: JSONObject): List<String> {
            val raw = m.opt("reactions") ?: return emptyList()
            val out = ArrayList<String>()
            when (raw) {
                is JSONArray -> {
                    for (i in 0 until raw.length()) {
                        val o = raw.optJSONObject(i)
                        val emoji = if (o != null) pick(o, "emoji", "reaction", "value") else raw.optString(i)
                        if (emoji.isNotBlank()) out += emoji
                    }
                }
                is JSONObject -> {
                    val keys = raw.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k.isNotBlank() && k != "count" && k != "total") out += k
                    }
                }
            }
            return out
        }

        fun fileNameOf(m: JSONObject): String {
            for (nested in fileObjects(m)) {
                val mime = pick(nested, "mime", "mimeType", "contentType")
                val name = pick(nested, "originalName", "name", "filename", "title")
                if (isAudioFile(mime, name, pick(nested, "url", "path", "src", "href", "fileUrl"), pick(nested, "kind", "type"))) continue
                if (looksLikeImageUrl(name) || looksLikeImageMime(mime)) continue
                if (name.isNotBlank()) return name
            }
            return ""
        }

        fun audioOf(m: JSONObject): String {
            val direct = pick(m, "audioUrl", "voiceUrl", "voice", "audio")
            if (looksLikeAudioUrl(direct)) return direct
            for (nested in fileObjects(m)) {
                val mime = pick(nested, "mime", "mimeType", "contentType")
                val kind = pick(nested, "kind", "type")
                val name = pick(nested, "originalName", "name", "filename")
                val u = pick(nested, "url", "path", "src", "href", "fileUrl")
                val id = pick(nested, "id", "fileId", "_id")
                if (!isAudioFile(mime, name, u, kind)) continue
                if (u.isNotBlank()) return u
                if (id.isNotBlank() && !id.startsWith("local-") && !id.startsWith("tmp-")) return workspaceFilePath(id)
            }
            val url = pick(m, "url", "fileUrl")
            return if (looksLikeAudioUrl(url)) url else ""
        }

        fun isAudioFile(mime: String, name: String, url: String, kind: String = ""): Boolean {
            val k = kind.lowercase()
            if (k == "voice" || k == "audio" || k.contains("voice")) return true
            if (looksLikeAudioMime(mime) || looksLikeAudioUrl(url) || looksLikeAudioUrl(name)) return true
            val n = name.lowercase()
            return n.contains("voice") || n.contains("voicememo")
        }

        fun looksLikeAudioUrl(s: String): Boolean {
            val l = s.lowercase().trim()
            if (l.isBlank() || l == "null") return false
            return l.endsWith(".m4a") || l.endsWith(".aac") || l.endsWith(".mp3") ||
                l.endsWith(".ogg") || l.endsWith(".wav") || l.endsWith(".oga") || l.endsWith(".opus") ||
                l.endsWith(".webm") ||
                l.contains("audio/") || l.contains("/audio") || l.contains("voice-") ||
                l.contains("voice_") || l.contains("voicememo") || l.contains("voice_note")
        }

        fun looksLikeAudioMime(s: String): Boolean {
            val l = s.lowercase()
            if (l.startsWith("video/")) return false
            return l.startsWith("audio/") || l.contains("aac") || l == "mpeg" ||
                l.contains("m4a") || l.contains("ogg") || l.contains("opus") ||
                l.contains("webm") && l.contains("audio")
        }

        fun looksLikeImageMime(s: String): Boolean {
            val l = s.lowercase()
            return l.startsWith("image/") || l.contains("jpeg") || l.contains("jpg") ||
                l.contains("png") || l.contains("webp") || l.contains("gif")
        }

        fun looksLikeImageUrl(s: String): Boolean {
            val l = s.lowercase().trim()
            if (l.isBlank() || l == "null") return false
            if (looksLikeAudioUrl(l) || l.contains("/workspace/files/")) return false
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") ||
                l.endsWith(".webp") || l.endsWith(".gif") ||
                l.contains("/upload") || l.contains("image") ||
                ((l.startsWith("http://") || l.startsWith("https://")) && !l.contains("/files/"))
        }

        fun flatten(o: JSONObject, prefix: String = ""): List<Pair<String, String>> {
            val out = ArrayList<Pair<String, String>>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.opt(k)
                val name = if (prefix.isEmpty()) k else "$prefix.$k"
                when (v) {
                    null, JSONObject.NULL -> out += name to "—"
                    is JSONObject -> out += flatten(v, name)
                    is JSONArray -> out += name to "${v.length()} эл."
                    else -> out += name to v.toString()
                }
            }
            return out
        }

        fun pick(o: JSONObject?, vararg keys: String): String {
            if (o == null) return ""
            for (k in keys) {
                val s = o.opt(k)?.toString()?.trim().orEmpty()
                if (s.isNotBlank() && s != "null") return s
            }
            return ""
        }

        fun pickOrEmpty(o: JSONObject?, vararg keys: String): String = pick(o, *keys)

        fun phoneOf(o: JSONObject): String {
            val raw = pick(
                o,
                "phone", "clientPhone", "mobile", "tel", "msisdn",
                "destination", "fromNumber", "toNumber", "from", "to",
            ).ifBlank {
                o.optJSONObject("client")?.let { pick(it, "phone", "mobile") }.orEmpty()
            }
            return raw.filter { it.isDigit() || it == '+' }
        }

        fun prettyTime(raw: String): String {
            if (raw.isBlank()) return ""
            val zoned = parseZoned(raw) ?: return raw.replace('T', ' ').take(16)
            val d = zoned.toLocalDate()
            val today = java.time.LocalDate.now()
            val time = "%02d:%02d".format(zoned.hour, zoned.minute)
            val months = arrayOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
            val month = months.getOrNull(d.monthValue - 1) ?: d.monthValue.toString()
            return when {
                d == today -> "сегодня, $time"
                d == today.minusDays(1) -> "вчера, $time"
                d.year == today.year -> "${d.dayOfMonth} $month, $time"
                else -> "${d.dayOfMonth} $month ${d.year}, $time"
            }
        }

        private fun parseZoned(raw: String): java.time.ZonedDateTime? {
            val t = raw.trim()
            try {
                return java.time.Instant.parse(t).atZone(java.time.ZoneId.systemDefault())
            } catch (_: Exception) {
            }
            try {
                val local = java.time.LocalDateTime.parse(t.replace(' ', 'T').take(19))
                return local.atZone(java.time.ZoneId.systemDefault())
            } catch (_: Exception) {
            }
            return null
        }

        fun paymentLabel(raw: String): String = when (raw.trim().uppercase()) {
            "CASH" -> "Наличные"
            "ONLINE" -> "Онлайн"
            "MIXED" -> "Смешанная"
            else -> raw
        }

        fun problemReasonLabel(raw: String): String = when (raw.trim().uppercase()) {
            "RESTAURANT" -> "Ресторан"
            "STORE" -> "Магазин"
            "COURIER" -> "Курьер"
            "OTHER" -> "Другое"
            else -> raw.ifBlank { "Другое" }
        }

        fun operationStatus(row: JsonRow): String =
            pick(row.raw, "status", "state").trim().uppercase()

        fun establishmentName(o: JSONObject): String {
            o.optJSONObject("establishment")?.let { nested ->
                val n = pick(nested, "name", "title", "label")
                if (n.isNotBlank()) return n
            }
            return pick(o, "establishmentName", "pointName", "city", "address")
        }

        fun clientName(o: JSONObject): String {
            val fromRoot = pick(o, "clientName", "customerName", "contactName")
            if (fromRoot.isNotBlank() && fromRoot != pick(o, "orderNumber", "externalId")) return fromRoot
            o.optJSONObject("client")?.let { nested ->
                val n = pick(nested, "name", "fullName", "clientName")
                if (n.isNotBlank()) return n
            }
            return ""
        }

        fun orderNo(o: JSONObject): String = pick(o, "orderNumber", "externalId")

        fun operationWhen(o: JSONObject): String = pick(o, "orderDatetime", "createdAt", "updatedAt")

        fun confirmTargetId(row: JsonRow): String =
            pick(row.raw, "operationId", "id").ifBlank { row.id }

        fun confirmLogisticsPath(id: String): String =
            "/operations/${java.net.URLEncoder.encode(id, "UTF-8")}/confirm-logistics"

        fun retryProposalPath(id: String): String =
            "/operations/logistics-intake/${java.net.URLEncoder.encode(id, "UTF-8")}/retry-proposal"

        fun problemOrdersPath(cityKey: String = ""): String {
            val q = if (cityKey.isBlank()) "" else "?cityKey=${enc(cityKey)}"
            return "/problem-orders$q"
        }

        fun problemAnalyticsPath(cityKey: String = ""): String {
            val q = if (cityKey.isBlank()) "" else "?cityKey=${enc(cityKey)}"
            return "/problem-orders/analytics$q"
        }

        fun delayHms(minutes: Double): String {
            val sec = kotlin.math.max(0, kotlin.math.round(minutes * 60.0).toInt())
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }

        fun problemDelayMinutes(o: JSONObject): Double = jsonNum(o, "delayMinutes", "delay")

        fun problemOperator(o: JSONObject): String {
            o.optJSONObject("reportedBy")?.let { u ->
                val n = pick(u, "fullName", "username", "name")
                if (n.isNotBlank()) return n
            }
            o.optJSONObject("createdBy")?.let { u ->
                val n = pick(u, "fullName", "username", "name")
                if (n.isNotBlank()) return n
            }
            return pick(o, "operatorName", "username", "createdByName", "reportedBy")
        }

        fun problemComment(o: JSONObject): String = pick(o, "comment", "delayReason", "note")

        fun looksLikeProblemOrder(o: JSONObject): Boolean {
            if (o.has("delayMinutes") && !o.has("payoutAmount") && !o.has("commissionAmount")) return true
            if (o.has("reportedBy") && pick(o, "reason").isNotBlank() && !o.has("lineItems")) return true
            return false
        }

        fun problemAnalyticsList(o: JSONObject?, key: String): List<JSONObject> {
            if (o == null) return emptyList()
            return when (val v = o.opt(key)) {
                is JSONArray -> eachObj(v)
                is JSONObject -> {
                    val keys = v.keys()
                    buildList {
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val item = v.optJSONObject(k)
                            if (item != null) {
                                if (!item.has("reason") && !item.has("establishmentName") && !item.has("orderNumber")) {
                                    item.put("reason", k)
                                }
                                add(item)
                            } else {
                                add(JSONObject().put("reason", k).put("count", jsonNum(v, k)))
                            }
                        }
                    }
                }
                else -> emptyList()
            }
        }

        fun problemCreateBody(
            orderNumber: String,
            establishmentId: String,
            delayMinutes: Int,
            reason: String,
            comment: String,
            cityKey: String = "",
        ): JSONObject {
            val body = JSONObject()
                .put("orderNumber", orderNumber.trim())
                .put("establishmentId", establishmentId)
                .put("delayMinutes", delayMinutes)
                .put("reason", reason)
                .put("comment", comment.trim())
            if (cityKey.isNotBlank()) body.put("cityKey", cityKey)
            return body
        }

        fun problemOrderBody(row: JsonRow, comment: String): JSONObject {
            val o = row.raw
            val estId = pick(o, "establishmentId").ifBlank {
                o.optJSONObject("establishment")?.let { pick(it, "id") }.orEmpty()
            }
            val body = JSONObject()
                .put("orderNumber", orderNo(o).ifBlank { row.title })
                .put("delayMinutes", 1)
                .put("reason", "OTHER")
                .put("comment", comment.ifBlank { "Проблема с заказом" })
            if (estId.isNotBlank()) body.put("establishmentId", estId)
            return body
        }

        fun needsConfirm(row: JsonRow): Boolean {
            val s = operationStatus(row)
            if (s == "PENDING_REVIEW" || s == "PENDING") return true
            if (pick(row.raw, "proposalError").isNotBlank()) return false
            val opId = pick(row.raw, "operationId")
            val statusTo = pick(row.raw, "statusTo")
            return opId.isBlank() && statusTo.isNotBlank() && !statusTo.equals("GREEN", true)
        }

        fun needsRetry(row: JsonRow): Boolean {
            val err = row.raw.opt("proposalError")
            return when (err) {
                null, JSONObject.NULL, false, "" -> pick(row.raw, "proposalError").isNotBlank()
                is Boolean -> err
                else -> err.toString().isNotBlank() && err.toString() != "false"
            }
        }

        fun dayKey(raw: String): String {
            if (raw.length >= 10 && raw[4] == '-') return raw.take(10)
            return ""
        }

        fun dayLabel(raw: String): String {
            val key = dayKey(raw)
            if (key.isBlank()) return ""
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val today = fmt.format(java.util.Date())
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val yesterday = fmt.format(cal.time)
            return when (key) {
                today -> "Сегодня"
                yesterday -> "Вчера"
                else -> "${key.substring(8, 10)}.${key.substring(5, 7)}"
            }
        }

        fun isDueToday(raw: String): Boolean {
            val key = dayKey(raw)
            if (key.isBlank()) return false
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            return key == today
        }

        fun isTaskUrgent(row: JsonRow): Boolean {
            if (isTaskDone(row)) return false
            val p = pick(row.raw, "priority").uppercase()
            if (p.contains("HIGH") || p.contains("URGENT") || p.contains("СРОЧ") || p.contains("ВЫСОК")) return true
            return isOverdue(taskDueRaw(row))
        }

        fun isOverdue(raw: String): Boolean {
            val key = dayKey(raw)
            if (key.isBlank()) return false
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            return key < today
        }

        fun taskIsOverdue(row: JsonRow): Boolean {
            if (isTaskDone(row)) return false
            val s = pick(row.raw, "status", "state").uppercase()
            if (s == "OVERDUE") return true
            val raw = taskDueRaw(row)
            val at = parseZoned(raw)?.toInstant()?.toEpochMilli()
            if (at != null) return at < System.currentTimeMillis()
            return isOverdue(raw)
        }

        fun taskDueEndOfToday(row: JsonRow): Boolean {
            if (isTaskDone(row)) return false
            val raw = taskDueRaw(row)
            val at = parseZoned(raw)?.toInstant()?.toEpochMilli() ?: return isDueToday(raw) || isOverdue(raw)
            val zone = java.time.ZoneId.systemDefault()
            val end = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return at < end
        }

        fun taskPriorityRank(row: JsonRow): Int = when (pick(row.raw, "priority").uppercase()) {
            "HIGH", "URGENT" -> 0
            "LOW" -> 2
            else -> 1
        }

        fun looksLikeOperation(o: JSONObject): Boolean {
            val hasOrder = pick(o, "orderNumber", "externalId", "orderId", "orderAmount", "amount").isNotBlank()
            val hasClient = pick(o, "clientName", "clientPhone", "phone").isNotBlank()
            return hasOrder || (hasClient && pick(o, "status", "state").isNotBlank())
        }

        fun operationPriority(row: JsonRow): Int {
            val s = operationStatus(row)
            return when {
                s == "PENDING_REVIEW" || s == "PENDING" || needsRetry(row) -> 0
                s == "CREATED" -> 1
                s.contains("CANCEL") || s.contains("PROBLEM") -> 2
                else -> 3
            }
        }

        fun sortOperations(rows: List<JsonRow>): List<JsonRow> =
            rows.sortedWith(compareBy({ operationPriority(it) }, { pick(it.raw, "updatedAt", "createdAt") }))

        fun isOpenForTake(row: JsonRow): Boolean {
            val s = operationStatus(row)
            if (s.isBlank()) return true
            return s == "CREATED" || s == "PENDING_REVIEW" || s == "PENDING" || s == "ACTIVE"
        }

        fun isOperationDone(row: JsonRow): Boolean {
            val s = pick(row.raw, "status", "state").lowercase()
            return s.contains("done") || s.contains("complete") || s.contains("cancel") ||
                s.contains("закрыт") || s.contains("готов")
        }

        fun sortConversations(rows: List<JsonRow>): List<JsonRow> =
            rows.sortedWith(
                compareByDescending<JsonRow> { unreadCount(it) }
                    .thenByDescending {
                        pick(it.raw, "lastMessageAt", "updatedAt", "createdAt")
                    },
            )

        fun chatSendPath(path: String): String {
            val clean = path.substringBefore("?").trimEnd('/')
            return when {
                clean.endsWith("/messages") -> clean
                clean.contains("/workspace/chats/") -> "$clean/messages"
                clean.contains("/support/threads/") -> "$clean/messages"
                else -> clean
            }
        }

        fun operationDetailPath(id: String): String {
            val enc = java.net.URLEncoder.encode(id, "UTF-8")
            return "/operations/one/$enc"
        }

        fun chatLinkFromOperation(o: JSONObject): Pair<String, String>? {
            val chatId = pick(o, "chatId", "workspaceChatId", "threadId", "supportThreadId")
            if (chatId.isBlank()) return null
            val enc = java.net.URLEncoder.encode(chatId, "UTF-8")
            val title = pick(o, "clientName", "fullName", "name", "orderNumber", "externalId").ifBlank { "Клиент" }
            val path = when {
                pick(o, "supportThreadId").isNotBlank() -> "/support/threads/$enc/messages?take=80"
                pick(o, "kind", "channel").contains("support", true) -> "/support/threads/$enc/messages?take=80"
                else -> "/workspace/chats/$enc/messages"
            }
            return title to path
        }

        fun cacheablePath(path: String): Boolean =
            path.startsWith("/operations") || path.startsWith("/workspace/chats") ||
                path.startsWith("/support/threads") || path.startsWith("/workspace/tasks") ||
                path.startsWith("/calls") || path.startsWith("/establishments") ||
                path.startsWith("/users") || path.startsWith("/sales/") ||
                path.startsWith("/courier-fleet") || path.startsWith("/accounting") ||
                path.startsWith("/email") || path.startsWith("/reports") ||
                path.startsWith("/problem-orders") || path.startsWith("/clients") || path.startsWith("/ai/") ||
                path.startsWith("/marketing") || path.startsWith("/sms") || path.startsWith("/push") ||
                path.startsWith("/qr") || path.startsWith("/reconciliation")

        fun chatIdFromPath(path: String): String {
            val parts = path.substringBefore("?").trim('/').split('/')
            val idx = parts.indexOfFirst { it == "chats" || it == "threads" }
            return if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else ""
        }

        fun callDirection(o: org.json.JSONObject): String {
            val raw = pick(o, "direction", "kind", "type", "status", "state", "outcome").lowercase()
            val missedFlag = o.optBoolean("missed", false) ||
                pick(o, "outcomeLabel", "outcome").lowercase().contains("miss")
            return when {
                missedFlag || raw.contains("miss") || raw.contains("пропущ") || raw.contains("no_answer") -> "missed"
                raw == "inbound" || raw.contains("incoming") || raw.contains("входя") -> "in"
                raw == "outbound" || raw.contains("outgoing") || raw.contains("исходя") -> "out"
                raw.contains("in") -> "in"
                raw.contains("out") -> "out"
                else -> ""
            }
        }

        fun callDirectionLabel(dir: String): String = when (dir) {
            "missed" -> "Пропущен"
            "in" -> "Входящий"
            "out" -> "Исходящий"
            else -> ""
        }

        fun callDuration(o: org.json.JSONObject): String {
            val raw = pick(o, "duration", "durationSec", "durationSeconds", "talkTime", "length")
            val n = raw.toIntOrNull() ?: return raw
            if (n <= 0) return ""
            val m = n / 60
            val s = n % 60
            return if (m > 0) "${m}м ${s}с" else "${s}с"
        }

        fun callPeerNumber(o: org.json.JSONObject): String {
            val dir = callDirection(o)
            val raw = when (dir) {
                "in", "missed" -> pick(o, "fromNumber", "from", "number", "phone", "toNumber")
                "out" -> pick(o, "toNumber", "to", "destination", "number", "phone", "fromNumber")
                else -> pick(o, "number", "phone", "toNumber", "fromNumber", "destination")
            }
            return raw.filter { it.isDigit() || it == '+' }
        }

        fun callLogIdOf(o: org.json.JSONObject): String = pick(o, "callLogId", "id", "_id")

        fun recordingStatusOf(o: org.json.JSONObject): String {
            val st = pick(o, "recordingStatus", "recordStatus").uppercase()
            if (st.isNotBlank()) return st
            val url = pick(o, "recordingUrl", "recordUrl")
            return if (url.isNotBlank()) "READY" else "NONE"
        }

        fun recordingStatusLabel(raw: String): String = when (raw.uppercase()) {
            "READY" -> "есть запись"
            "PROCESSING" -> "обрабатывается"
            "FAILED" -> "ошибка обработки"
            "DELETED" -> "удалено (истёк срок хранения)"
            else -> "нет записи"
        }

        fun isCallCenterWorkspace(path: String, tab: String = ""): Boolean {
            val c = path.substringBefore("?").trimEnd('/')
            if (tab.equals("callcenter", true)) {
                return c.startsWith("/calls") || c.startsWith("/sms/logs") || c.startsWith("/sms/incoming")
            }
            return c.startsWith("/calls")
        }

        fun callCenterTabFromPath(path: String, title: String = ""): String {
            val c = path.substringBefore("?").trimEnd('/')
            val t = title.trim().lowercase()
            return when {
                c.contains("keypad") || t.contains("клавиш") || t.contains("клавиатур") -> "keypad"
                c.contains("contact") || t.contains("контакт") -> "contacts"
                c.contains("recording") || t.contains("запис") -> "recordings"
                c.startsWith("/sms") || t == "sms" -> "sms"
                c.contains("logs") || t.contains("недавн") || t.contains("звонк") -> "recent"
                else -> "keypad"
            }
        }

        fun sortTasks(rows: List<JsonRow>): List<JsonRow> =
            rows.sortedWith(
                compareBy<JsonRow> {
                    val s = pick(it.raw, "status", "state").lowercase()
                    when {
                        s.contains("done") || s.contains("closed") || s.contains("complete") -> 2
                        s.contains("progress") || s.contains("open") -> 0
                        else -> 1
                    }
                }.thenByDescending {
                    pick(it.raw, "dueAt", "deadline", "updatedAt", "createdAt")
                },
            )

        fun assignedToMe(row: JsonRow, user: AppUser): Boolean {
            val names = pick(
                row.raw,
                "assigneeName", "assignedTo", "managerName", "username",
                "courierName", "ownerName", "operatorName",
            ).lowercase()
            val ids = pick(row.raw, "assigneeId", "assignedToId", "managerId", "userId", "courierId", "operatorId")
            val meName = user.fullName.lowercase()
            val meUser = user.username.lowercase()
            if ((meName.isNotBlank() && names.contains(meName)) ||
                (meUser.isNotBlank() && names.contains(meUser)) ||
                (user.id.isNotBlank() && ids == user.id)
            ) return true
            val arr = row.raw.optJSONArray("assignees") ?: return false
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val uid = pick(item, "userId", "id").ifBlank {
                    item.optJSONObject("user")?.let { pick(it, "id", "userId") }.orEmpty()
                }
                if (uid.isNotBlank() && uid == user.id) return true
            }
            return false
        }

        fun takeOf(path: String): Int {
            val m = Regex("[?&]take=(\\d+)").find(path)
            return m?.groupValues?.get(1)?.toIntOrNull() ?: 30
        }

        fun withTake(path: String, take: Int): String {
            return if (path.contains("take=")) path.replace(Regex("take=\\d+"), "take=$take")
            else if (path.contains("?")) "$path&take=$take"
            else "$path?take=$take"
        }

        fun withStatus(row: JsonRow, status: String): JsonRow {
            val o = JSONObject(row.raw.toString())
            o.put("status", status)
            return row.copy(raw = o)
        }

        fun isTaskDone(row: JsonRow): Boolean {
            val s = pick(row.raw, "status", "state").uppercase()
            return s == "DONE" || s == "ARCHIVED" || s == "CLOSED" || s == "COMPLETE" ||
                s.contains("DONE") || s.contains("ARCHIV") || s.contains("CLOSED") || s.contains("COMPLETE")
        }

        fun taskDueRaw(row: JsonRow): String = pick(row.raw, "dueAt", "deadline", "deadlineAt", "dueDate")

        fun unreadCount(row: JsonRow): Int {
            val raw = pick(row.raw, "unreadCount", "unreadMessages", "unread")
            if (raw.isBlank()) {
                val flag = pick(row.raw, "hasUnread", "isUnread").lowercase()
                return if (flag == "true" || flag == "1") 1 else 0
            }
            return raw.toIntOrNull()?.coerceAtLeast(0) ?: 0
        }

        fun chatTitle(o: JSONObject): String {
            val named = listOf(
                pick(o, "title", "name", "peerName", "employeeName", "fullName", "clientName", "contactName"),
            ).map { humanText(it) }.firstOrNull { it.isNotBlank() && !it.all { ch -> ch.isDigit() || ch == ' ' } }
            if (!named.isNullOrBlank()) return named
            val members = o.optJSONArray("members") ?: o.optJSONArray("participants") ?: o.optJSONArray("users")
            if (members != null) {
                for (i in 0 until members.length()) {
                    val m = members.optJSONObject(i) ?: continue
                    val n = humanText(pick(m, "fullName", "name", "username", "title"))
                    if (n.isNotBlank() && !n.all { it.isDigit() }) return n
                }
            }
            val kind = chatKind(o)
            return kind.ifBlank { "Диалог" }
        }

        fun chatKind(o: JSONObject): String {
            val t = pick(o, "type", "kind", "chatType", "scope", "channel", "source").lowercase()
            return when {
                t.contains("global") || t.contains("общий") -> "Общий чат"
                t.contains("dept") || t.contains("department") || t.contains("отдел") ->
                    pick(o, "departmentKey", "department").ifBlank { "Отдел" }
                t.contains("group") || t.contains("групп") -> {
                    val n = (o.optJSONArray("participants") ?: o.optJSONArray("members") ?: o.optJSONArray("users"))?.length() ?: 0
                    if (n > 0) "Группа · $n уч." else "Группа"
                }
                t.contains("direct") || t.contains("personal") || t.contains("личн") || t.contains("dm") || t.contains("private") -> "Личный"
                t.contains("support") || t.contains("поддерж") -> "Поддержка"
                t.contains("telegram") -> "Telegram"
                t.contains("whats") -> "WhatsApp"
                t.contains("email") || t.contains("почт") -> "Почта"
                else -> ""
            }
        }

        fun workspaceChatTitle(o: JSONObject, meId: String = ""): String {
            val titled = humanText(pick(o, "title"))
            if (titled.isNotBlank()) return titled
            val type = pick(o, "type", "kind").uppercase()
            when (type) {
                "GLOBAL" -> return "Общий чат"
                "DEPARTMENT" -> return pick(o, "departmentKey", "department").ifBlank { "Отдел" }
                "DIRECT", "PERSONAL", "DM" -> {
                    val other = otherParticipant(o, meId)
                    val name = other?.let { pick(it, "fullName", "name", "username") }.orEmpty()
                    if (name.isNotBlank()) return name
                }
            }
            return chatTitle(o).ifBlank { "Личный" }
        }

        fun otherParticipant(o: JSONObject, meId: String): JSONObject? {
            val members = o.optJSONArray("members") ?: o.optJSONArray("participants") ?: o.optJSONArray("users") ?: return null
            var fallback: JSONObject? = null
            for (i in 0 until members.length()) {
                val row = members.optJSONObject(i) ?: continue
                val user = row.optJSONObject("user") ?: row
                val uid = pick(row, "userId").ifBlank { pick(user, "id", "userId") }
                if (fallback == null) fallback = user
                if (meId.isNotBlank() && uid == meId) continue
                if (meId.isBlank() || uid != meId) return user
            }
            return fallback
        }

        fun avatarUrlOf(o: JSONObject?): String {
            if (o == null) return ""
            val direct = pick(o, "avatarUrl", "avatar", "photoUrl", "profilePhotoUrl")
            if (direct.isNotBlank()) return direct
            val img = pick(o, "imageUrl")
            if (img.contains("avatar", true) || img.contains("/users/")) return img
            o.optJSONObject("user")?.let { nested ->
                val v = pick(nested, "avatarUrl", "avatar", "photoUrl")
                if (v.isNotBlank()) return v
            }
            o.optJSONObject("sender")?.let { nested ->
                val v = pick(nested, "avatarUrl", "avatar", "photoUrl")
                if (v.isNotBlank()) return v
            }
            o.optJSONObject("author")?.let { nested ->
                val v = pick(nested, "avatarUrl", "avatar", "photoUrl")
                if (v.isNotBlank()) return v
            }
            val fileId = pick(o, "avatarFileId", "photoFileId", "avatarId")
            return if (fileId.isNotBlank()) workspaceFilePath(fileId) else ""
        }

        fun chatPeerAvatar(o: JSONObject, meId: String = ""): String {
            otherParticipant(o, meId)?.let { peer ->
                val v = avatarUrlOf(peer)
                if (v.isNotBlank()) return v
            }
            return avatarUrlOf(o)
        }

        fun lastMessagePreview(o: JSONObject): String {
            val last = o.optJSONObject("lastMessage") ?: o.optJSONObject("last") ?: o.optJSONObject("preview")
            if (last != null) {
                val t = humanText(pick(last, "text", "body", "content", "message"))
                if (t.isNotBlank()) return t
                val files = last.optJSONArray("files") ?: last.optJSONArray("attachments")
                if (files != null && files.length() > 0) {
                    val f = files.optJSONObject(0)
                    val name = f?.let { pick(it, "originalName", "name") }.orEmpty()
                    return "📎 ${name.ifBlank { "файл" }}"
                }
                return "Нет сообщений"
            }
            val preview = previewText(o)
            return preview.ifBlank { "Нет сообщений" }
        }

        fun isPresenceOnline(raw: String): Boolean {
            val ms = runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
                ?: runCatching {
                    java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
                }.getOrNull()
                ?: raw.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000 else it }
            if (ms == null) return false
            return System.currentTimeMillis() - ms < 90_000L
        }

        fun workspaceRoleLabel(role: String): String = when (role.uppercase()) {
            "ADMIN" -> "Администрация"
            "COLLCENTER", "CALLCENTER" -> "Колл-центр"
            "OPERATOR", "HEAD_COURIER" -> "Операции"
            "SALES_HEAD", "SALES_SENIOR_MANAGER", "SALES_MANAGER",
            "SALES_MANAGER_ESTABLISHMENTS", "SALES_MANAGER_FLORISTS", "SALES_MANAGER_STORES",
            "SALES_CONTENT_ESTABLISHMENTS", "SALES_ROKSANA", "SALES_ROMA",
            -> "Отдел продаж"
            "ACCOUNTANT", "FINANCE" -> "Бухгалтерия"
            "MARKETING" -> "Маркетинг"
            else -> role
        }

        fun chatInitials(name: String): String {
            val parts = name.trim().split(Regex("\\s+")).filter { part -> part.any { it.isLetter() } }
            if (parts.isEmpty()) return "Ч"
            val a = parts[0].first { it.isLetter() }
            val b = parts.getOrNull(1)?.firstOrNull { it.isLetter() }
            return buildString {
                append(a.uppercaseChar())
                if (b != null) append(b.uppercaseChar())
            }
        }

        fun looksLikeTask(o: JSONObject): Boolean {
            if (looksLikeOperation(o)) return false
            val hasTitle = pick(o, "title", "name", "subject").isNotBlank()
            val hasTaskMeta = pick(o, "dueAt", "deadline", "assigneeName", "assignedTo").isNotBlank()
            val status = pick(o, "status", "state").lowercase()
            val statusLooks = status.contains("task") || status.contains("open") || status.contains("progress") || status.contains("done")
            return hasTitle && (hasTaskMeta || statusLooks)
        }

        fun taskDetailPath(id: String): String {
            val enc = java.net.URLEncoder.encode(id, "UTF-8")
            return "/workspace/tasks/$enc"
        }

        fun eventTimeline(o: JSONObject): List<Pair<String, String>> {
            val arr = when {
                o.opt("events") is JSONArray -> o.getJSONArray("events")
                o.opt("history") is JSONArray -> o.getJSONArray("history")
                o.opt("timeline") is JSONArray -> o.getJSONArray("timeline")
                o.opt("audit") is JSONArray -> o.getJSONArray("audit")
                else -> return emptyList()
            }
            val out = ArrayList<Pair<String, String>>(arr.length())
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val title = pick(e, "title", "action", "status", "type", "event", "name").ifBlank { "Событие" }
                val detail = pick(e, "text", "message", "comment", "description", "note").ifBlank {
                    pick(e, "authorName", "username", "userName")
                }
                val time = pick(e, "createdAt", "time", "updatedAt", "at")
                val line = listOf(detail, prettyTime(time)).filter { it.isNotBlank() }.joinToString(" · ")
                out += title to line.ifBlank { "—" }
            }
            return out
        }

        private fun parseUser(o: JSONObject): AppUser {
            val perms = mutableListOf<String>()
            val p = o.optJSONArray("permissions")
            if (p != null) for (i in 0 until p.length()) perms += p.optString(i)
            return AppUser(
                id = o.optString("id"),
                username = o.optString("username"),
                fullName = o.optString("fullName", o.optString("username")),
                role = o.optString("role"),
                permissions = perms,
                operationsActivityEnabled = o.optBoolean("operationsActivityEnabled", false),
                allowedCityKeys = buildList {
                    val arr = o.optJSONArray("allowedCityKeys")
                    if (arr != null) for (i in 0 until arr.length()) {
                        val v = arr.optString(i).trim()
                        if (v.isNotBlank()) add(v)
                    }
                },
                avatarUrl = avatarUrlOf(o),
                phone = pick(o, "phone", "mobile"),
                email = pick(o, "email", "personalEmail", "mailboxEmail"),
            )
        }

        private fun err(text: String, code: Int, fallback: String): String {
            return try {
                val o = JSONObject(text)
                when (val m = o.opt("message")) {
                    is JSONArray -> (0 until m.length()).joinToString("; ") { m.optString(it) }
                    is String -> m
                    else -> fallback
                }.ifBlank { fallback }
            } catch (_: Exception) {
                if (code == 401) fallback else "$fallback ($code)"
            }
        }
    }

    private fun parseUser(o: JSONObject) = Companion.parseUser(o)
    private fun err(text: String, code: Int, fallback: String) = Companion.err(text, code, fallback)
}
