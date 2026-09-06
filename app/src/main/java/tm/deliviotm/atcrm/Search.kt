package tm.deliviotm.atcrm

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray

data class SearchHit(
    val category: String,
    val spec: ModuleSpec,
    val row: JsonRow,
)

object SearchRecents {
    private const val KEY = "search_recents_v1"

    fun load(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotBlank()) add(s)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun push(ctx: Context, query: String) {
        val q = query.trim()
        if (q.length < 2) return
        val next = JSONArray()
        next.put(q)
        load(ctx).forEach { old ->
            if (!old.equals(q, true) && next.length() < 8) next.put(old)
        }
        ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE).edit().putString(KEY, next.toString()).apply()
    }
}

object GlobalSearch {
    private val sources = listOf(
        Triple("Заказы", ModuleSpec("operations", "Операции", "/operations"), KassaApi.operationsListPath("ACTIVE", 80)),
        Triple("Проблемные", ModuleSpec("problemOrders", "Проблемные", "/problem-orders"), "/problem-orders"),
        Triple("Логистика", ModuleSpec("operations", "Логистика", "/operations/logistics-intake"), KassaApi.logisticsIntakePath(80)),
        Triple("Задачи", ModuleSpec("tasks", "Задачи", "/workspace/tasks"), "/workspace/tasks"),
        Triple("Чаты", ModuleSpec("chats", "Чаты", "/workspace/chats"), "/workspace/chats"),
        Triple("Поддержка", ModuleSpec("support", "Поддержка", "/support/threads"), "/support/threads"),
        Triple("Почта", ModuleSpec("mail", "Почта", "/email/messages?take=50"), "/email/messages?take=50"),
        Triple("Звонки", ModuleSpec("callcenter", "Недавние", "/calls/logs?take=40"), "/calls/logs?take=40"),
        Triple("Контакты", ModuleSpec("callcenter", "Колл-центр", "/calls/contacts"), "/calls/contacts"),
        Triple("Заведения", ModuleSpec("establishments", "Заведения", "/establishments"), "/establishments"),
        Triple("Продажи", ModuleSpec("sales", "Продажи", "/sales/establishments?take=50"), "/sales/establishments?take=50"),
        Triple("Курьеры", ModuleSpec("courierFleet", "Курьеры", "/courier-fleet/couriers"), "/courier-fleet/couriers"),
        Triple("Доставки", ModuleSpec("courierFleet", "Доставки", "/courier-fleet/deliveries"), "/courier-fleet/deliveries"),
        Triple("Сотрудники", ModuleSpec("accounting", "Сотрудники", "/accounting/employees"), "/accounting/employees"),
        Triple("Контрагенты", ModuleSpec("accounting", "Контрагенты", "/accounting/counterparties"), "/accounting/counterparties"),
        Triple("Клиенты", ModuleSpec("marketing", "Клиенты", "/clients"), "/clients"),
        Triple("SMS", ModuleSpec("callcenter", "SMS", "/sms/logs?take=50"), "/sms/logs?take=50"),
        Triple("Пользователи", ModuleSpec("settings", "Пользователи", "/users"), "/users"),
        Triple("Портреты", ModuleSpec("aiRecommendations", "Портреты", "/reports/client-portraits"), "/reports/client-portraits"),
        Triple("Маркетинг", ModuleSpec("marketing", "Маркетинг", "/clients"), "/clients"),
        Triple("AI", ModuleSpec("aiRecommendations", "AI аналитика", "/ai/recommendations"), "/ai/recommendations"),
    )

    suspend fun search(api: KassaApi, token: String, query: String): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return coroutineScope {
            val chunks = sources.map { (cat, spec, path) ->
                async {
                    val rows = runCatching { api.getRows(path, token) }.getOrDefault(emptyList())
                    rows.filter { matches(it, q) }.map { SearchHit(cat, spec, it) }
                }
            }
            chunks.flatMap { it.await() }
        }
    }

    fun searchCached(cache: CacheStore, query: String): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        return sources.flatMap { (cat, spec, path) ->
            cache.getRows(path).orEmpty()
                .filter { matches(it, q) }
                .map { SearchHit(cat, spec, it) }
        }
    }

    private fun matches(row: JsonRow, q: String): Boolean {
        if ("${row.title} ${row.subtitle}".lowercase().contains(q)) return true
        val blob = listOf(
            KassaApi.pick(row.raw, "phone", "clientPhone", "clientName", "orderNumber", "externalId", "establishmentName"),
            KassaApi.pick(row.raw, "description", "text", "email", "address", "city"),
        ).joinToString(" ").lowercase()
        return blob.contains(q)
    }
}
