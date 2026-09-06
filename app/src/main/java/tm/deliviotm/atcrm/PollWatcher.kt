package tm.deliviotm.atcrm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class PollDelta(
    val newOps: List<JsonRow> = emptyList(),
    val newChats: Int = 0,
    val chatSample: String = "",
    val chatPreview: String = "",
    val chatId: String = "",
    val taskAlerts: List<String> = emptyList(),
    val taskSampleId: String = "",
    val pendingCount: Int = 0,
    val unreadChats: Int = 0,
    val taskBadge: Int = 0,
    val newNotices: Int = 0,
    val noticeSample: String = "",
    val noticeId: String = "",
) {
    val hasAlert: Boolean get() =
        newOps.isNotEmpty() || newChats > 0 || taskAlerts.isNotEmpty() || newNotices > 0
}

object PollWatcher {
    @Volatile var appForeground: Boolean = false
    @Volatile var viewingPath: String = ""

    private val badgeFlow = MutableStateFlow(0)
    val badgeTotal: StateFlow<Int> = badgeFlow.asStateFlow()

    private const val PREFS = "atcrm"
    private const val KEY_STATE = "poll_state"
    private const val KEY_NOTIFY = "notify_enabled"
    private const val KEY_SNOOZE = "notify_snooze_until"
    private val lock = Any()

    fun isEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_NOTIFY, true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_NOTIFY, enabled).apply()
        if (!enabled) {
            badgeFlow.value = 0
            NotifyHelper.clearAllBadges(ctx)
        }
    }

    fun snoozeUntil(ctx: Context): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SNOOZE, 0L)

    fun isSnoozed(ctx: Context): Boolean = snoozeUntil(ctx) > System.currentTimeMillis()

    fun snoozeHour(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SNOOZE, System.currentTimeMillis() + 60 * 60 * 1000L)
            .apply()
    }

    fun clearSnooze(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SNOOZE).apply()
    }

    fun check(ctx: Context, api: KassaApi, token: String, notify: Boolean): PollDelta = synchronized(lock) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cache = CacheStore(prefs)
        val prev = loadState(prefs.getString(KEY_STATE, null))
        val viewing = viewingPath

        val pending = runCatching { api.getRows(KassaApi.operationsListPath("PENDING_REVIEW", 80), token) }
            .getOrDefault(emptyList())
        val active = runCatching { api.getRows(KassaApi.operationsListPath("ACTIVE", 80), token) }
            .getOrDefault(emptyList())
        val chats = runCatching { api.getRows("/workspace/chats", token) }.getOrDefault(emptyList())
        val support = runCatching { api.getRows("/support/threads?take=80", token) }.getOrDefault(emptyList())
        val tasks = runCatching { api.getRows(KassaApi.taskListPath(false), token) }.getOrDefault(emptyList())
        val notices = runCatching {
            KassaApi.parseNotifications(api.getObject("/workspace/notifications", token))
        }.getOrDefault(emptyList())

        cache.putRows(KassaApi.opsCacheKey(), if (pending.isNotEmpty()) pending else active)
        cache.putRows("/workspace/chats", chats)

        val chatHitsAll = buildList {
            chats.forEach { add(chatHit(it, support = false)) }
            support.forEach { add(chatHit(it, support = true)) }
        }
        val chatUnread = chatHitsAll.sumOf { it.unread }
        val chatSnaps = chatHitsAll.associate { it.key to ChatSnap(it.unread, it.stamp) }
        cache.putRows("/workspace/tasks", tasks)
        HotOrdersWidget.refresh(ctx, if (pending.isNotEmpty()) pending else active)

        val taskStatus = tasks.associate { it.id to taskFingerprint(it) }
        val unreadNotices = notices.filter { n ->
            !n.optBoolean("isRead", false) && KassaApi.pick(n, "id").isNotBlank()
        }
        val unreadTaskNotices = unreadNotices.count { n ->
            KassaApi.pick(n, "type", "kind").uppercase().let { it.startsWith("TASK") || it == "TASK_DEADLINE" }
        }
        val attentionTasks = tasks.count { row ->
            val col = KassaApi.taskColumn(row)
            col == "new" || col == "review" || col == "overdue"
        }
        val taskBadge = unreadTaskNotices.coerceAtLeast(attentionTasks)
        val noticeIds = unreadNotices.map { KassaApi.pick(it, "id") }.toSet()
        val seedNotices = prev == null || prev.noticeIds.isEmpty()
        val newNoticeRows = if (!seedNotices && prev != null) {
            unreadNotices.filter { KassaApi.pick(it, "id") !in prev.noticeIds }
        } else {
            emptyList()
        }
        val viewingInbox = viewing.substringBefore("?") == "/workspace/notifications"

        val newOps = if (prev != null) pending.filter { it.id.isNotBlank() && it.id !in prev.pendingIds } else emptyList()
        val seedChats = prev == null || prev.chats.isEmpty()
        val seedTasks = prev == null || prev.taskStatus.isEmpty()
        val unreadDelta = if (!seedChats && prev != null) {
            chatHitsAll.sumOf { hit ->
                if (chatViewingSuppresses(viewing, hit)) return@sumOf 0
                val was = prev.chats[hit.key]?.unread ?: 0
                (hit.unread - was).coerceAtLeast(0)
            }
        } else 0
        val apiHasUnread = chatHitsAll.any { it.unread > 0 }
        val stampHits = if (!seedChats && prev != null) {
            chatHitsAll.filter { hit ->
                if (chatViewingSuppresses(viewing, hit)) return@filter false
                val prevSnap = prev.chats[hit.key] ?: return@filter false
                hit.stamp.isNotBlank() && prevSnap.stamp.isNotBlank() && hit.stamp != prevSnap.stamp &&
                    (hit.unread > prevSnap.unread || (!apiHasUnread && hit.unread == 0 && prevSnap.unread == 0))
            }
        } else emptyList()
        val newChats = if (unreadDelta > 0) unreadDelta else if (!apiHasUnread) stampHits.size else 0
        val chatHits = chatHitsAll.filter { it.unread > 0 && !chatViewingSuppresses(viewing, it) }
        val chatSampleRow = chatHits.maxByOrNull { it.unread } ?: stampHits.firstOrNull()
        val chatSample = chatSampleRow?.title.orEmpty()
        val chatPreview = chatSampleRow?.preview.orEmpty()
        val chatOpenPath = chatSampleRow?.path.orEmpty()
        val taskAlerts = mutableListOf<String>()
        var taskSampleId = ""
        val onTaskList = viewing.substringBefore("?") == "/workspace/tasks"
        val openTaskId = taskIdFromPath(viewing)
        if (!seedTasks && prev != null) {
            for (row in tasks) {
                if (row.id.isBlank()) continue
                if (onTaskList || (openTaskId.isNotBlank() && row.id == openTaskId)) continue
                val nowFp = taskFingerprint(row)
                val was = prev.taskStatus[row.id]
                val nowStatus = taskStatusOf(row)
                val nowCol = KassaApi.taskColumn(row)
                if (was == null) {
                    taskAlerts += "Новая · ${row.title.ifBlank { "задача" }} (${KassaApi.taskStatusLabel(nowStatus)})"
                    if (taskSampleId.isBlank()) taskSampleId = row.id
                } else if (!was.contains("|")) {
                    continue
                } else if (was != nowFp) {
                    val wasStatus = was.substringBefore("|")
                    val labelNow = if (nowCol == "overdue") "Просрочена" else KassaApi.taskStatusLabel(nowStatus)
                    taskAlerts += "${row.title.ifBlank { "Задача" }}: ${KassaApi.taskStatusLabel(wasStatus)} → $labelNow"
                    if (taskSampleId.isBlank()) taskSampleId = row.id
                }
            }
        }

        val fire = notify && prev != null && isEnabled(ctx) && !isSnoozed(ctx)
        if (fire) {
            if (newOps.isNotEmpty()) {
                val sample = KassaApi.displayTitle(newOps.first()).ifBlank { newOps.first().title }
                NotifyHelper.notifyOperations(
                    ctx,
                    newOps.size,
                    sample,
                    newOps.first().id,
                    newOps.drop(1).take(5).map { KassaApi.displayTitle(it).ifBlank { it.title } },
                )
            }
            if (newChats > 0) {
                NotifyHelper.notifyChats(
                    ctx,
                    newChats,
                    chatSample,
                    chatOpenPath,
                    chatPreview,
                )
            }
            if (taskAlerts.isNotEmpty()) {
                NotifyHelper.notifyTasks(
                    ctx,
                    taskAlerts.size,
                    taskAlerts.first(),
                    taskSampleId,
                    taskAlerts.drop(1).take(5),
                )
            }
            if (newNoticeRows.isNotEmpty() && !viewingInbox) {
                val sample = newNoticeRows.first()
                NotifyHelper.notifyInbox(
                    ctx,
                    newNoticeRows.size,
                    KassaApi.noticeTitle(sample),
                    KassaApi.noticeBody(sample),
                    KassaApi.pick(sample, "id"),
                    newNoticeRows.drop(1).take(5).map { KassaApi.noticeTitle(it) },
                )
            }
        }

        val badge = pending.size + chatUnread + taskBadge + unreadNotices.size
        badgeFlow.value = badge
        if (isEnabled(ctx)) {
            NotifyHelper.setOutstandingBadge(ctx, pending.size, chatUnread, taskBadge + unreadNotices.size)
        } else {
            badgeFlow.value = 0
        }

        val next = State(
            pendingIds = pending.map { it.id }.filter { it.isNotBlank() }.toSet(),
            chats = chatSnaps,
            taskStatus = taskStatus,
            chatUnreadTotal = chatUnread,
            noticeIds = noticeIds,
        )
        prefs.edit().putString(KEY_STATE, saveState(next)).apply()
        return PollDelta(
            newOps = newOps,
            newChats = newChats,
            chatSample = chatSample,
            chatPreview = chatPreview,
            chatId = chatOpenPath,
            taskAlerts = taskAlerts,
            taskSampleId = taskSampleId,
            pendingCount = pending.size,
            unreadChats = chatUnread,
            taskBadge = taskBadge,
            newNotices = newNoticeRows.size,
            noticeSample = newNoticeRows.firstOrNull()?.let { KassaApi.noticeTitle(it) }.orEmpty(),
            noticeId = newNoticeRows.firstOrNull()?.let { KassaApi.pick(it, "id") }.orEmpty(),
        )
    }

    private data class ChatHit(
        val key: String,
        val path: String,
        val title: String,
        val preview: String,
        val unread: Int,
        val stamp: String,
        val support: Boolean,
    )

    private fun chatHit(row: JsonRow, support: Boolean): ChatHit {
        val id = row.id
        val prefix = if (support) "s:" else ""
        val path = if (support) "/support/threads/$id" else "/workspace/chats/$id"
        return ChatHit(
            key = prefix + id,
            path = path,
            title = KassaApi.chatTitle(row.raw).ifBlank { row.title },
            preview = KassaApi.lastMessagePreview(row.raw),
            unread = chatUnreadOf(row),
            stamp = chatStamp(row),
            support = support,
        )
    }

    private fun chatViewingSuppresses(viewing: String, hit: ChatHit): Boolean {
        val p = viewing.substringBefore("?")
        val openId = KassaApi.chatIdFromPath(viewing)
        return if (hit.support) {
            p == "/support/threads" || (p.startsWith("/support/threads/") && openId.isNotBlank() && hit.key == "s:$openId")
        } else {
            p == "/workspace/chats" || (p.startsWith("/workspace/chats/") && openId.isNotBlank() && hit.key == openId)
        }
    }

    private data class ChatSnap(val unread: Int, val stamp: String)
    private data class State(
        val pendingIds: Set<String>,
        val chats: Map<String, ChatSnap>,
        val taskStatus: Map<String, String>,
        val chatUnreadTotal: Int,
        val noticeIds: Set<String> = emptySet(),
    )

    private fun chatUnreadOf(row: JsonRow): Int = KassaApi.unreadCount(row)

    private fun chatStamp(row: JsonRow): String {
        val at = KassaApi.pick(row.raw, "lastMessageAt", "updatedAt", "updated_at")
        if (at.isNotBlank()) return at
        return KassaApi.lastMessagePreview(row.raw)
    }

    private fun taskStatusOf(row: JsonRow): String =
        KassaApi.pick(row.raw, "status", "state", "column", "stage").ifBlank { "NEW" }.uppercase()

    private fun taskFingerprint(row: JsonRow): String = "${taskStatusOf(row)}|${KassaApi.taskColumn(row)}"

    private fun taskIdFromPath(path: String): String {
        val p = path.substringBefore("?")
        if (!p.startsWith("/workspace/tasks/")) return ""
        return p.removePrefix("/workspace/tasks/").substringBefore("/")
    }

    private fun loadState(raw: String?): State? {
        if (raw.isNullOrBlank()) return null
        return try {
            val o = JSONObject(raw)
            if (o.has("pendingIds")) {
                val ids = o.optJSONArray("pendingIds") ?: org.json.JSONArray()
                val pending = buildSet {
                    for (i in 0 until ids.length()) add(ids.optString(i))
                }
                val chatsObj = o.optJSONObject("chats") ?: JSONObject()
                val chats = HashMap<String, ChatSnap>()
                chatsObj.keys().forEach { k ->
                    val c = chatsObj.optJSONObject(k)
                    chats[k] = ChatSnap(c?.optInt("unread") ?: 0, c?.optString("stamp").orEmpty())
                }
                val tasksObj = o.optJSONObject("taskStatus") ?: JSONObject()
                val tasks = HashMap<String, String>()
                tasksObj.keys().forEach { k -> tasks[k] = tasksObj.optString(k) }
                val noticeArr = o.optJSONArray("noticeIds") ?: org.json.JSONArray()
                val notices = buildSet {
                    for (i in 0 until noticeArr.length()) add(noticeArr.optString(i))
                }
                State(pending, chats, tasks, o.optInt("chatUnreadTotal"), notices)
            } else {
                val ids = o.optJSONArray("operationIds") ?: return null
                val set = buildSet {
                    for (i in 0 until ids.length()) add(ids.optString(i))
                }
                State(set, emptyMap(), emptyMap(), o.optInt("chatUnreadTotal"))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveState(state: State): String {
        val chats = JSONObject()
        state.chats.forEach { (id, snap) ->
            chats.put(id, JSONObject().put("unread", snap.unread).put("stamp", snap.stamp))
        }
        val tasks = JSONObject()
        state.taskStatus.forEach { (id, st) -> tasks.put(id, st) }
        return JSONObject()
            .put("chatUnreadTotal", state.chatUnreadTotal)
            .put("pendingIds", org.json.JSONArray(state.pendingIds.toList()))
            .put("chats", chats)
            .put("taskStatus", tasks)
            .put("noticeIds", org.json.JSONArray(state.noticeIds.toList()))
            .toString()
    }
}
