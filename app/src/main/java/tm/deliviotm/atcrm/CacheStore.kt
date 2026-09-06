package tm.deliviotm.atcrm

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class CacheStore(private val prefs: SharedPreferences) {
    var revision by mutableIntStateOf(0)
        private set

    fun putRows(key: String, rows: List<JsonRow>) {
        val arr = JSONArray()
        rows.forEach { arr.put(it.raw) }
        prefs.edit()
            .putString(cacheKey(key), arr.toString())
            .putLong(cacheTimeKey(key), System.currentTimeMillis())
            .apply()
        revision++
    }

    fun markChatRead(chatId: String) {
        if (chatId.isBlank()) return
        var any = false
        listOf("/workspace/chats", "/support/threads", "home_1").forEach { key ->
            val rows = getRows(key) ?: return@forEach
            var local = false
            val next = rows.map { row ->
                if (row.id != chatId) row
                else {
                    local = true
                    val o = JSONObject(row.raw.toString())
                    o.put("unreadCount", 0)
                    o.put("unread", 0)
                    row.copy(raw = o)
                }
            }
            if (local) {
                val arr = JSONArray()
                next.forEach { arr.put(it.raw) }
                prefs.edit()
                    .putString(cacheKey(key), arr.toString())
                    .putLong(cacheTimeKey(key), System.currentTimeMillis())
                    .apply()
                any = true
            }
        }
        if (any) revision++
    }

    fun markAllChatsRead() {
        var any = false
        listOf("/workspace/chats", "/support/threads", "home_1").forEach { key ->
            val rows = getRows(key) ?: return@forEach
            val next = rows.map { row ->
                val o = JSONObject(row.raw.toString())
                o.put("unreadCount", 0)
                o.put("unread", 0)
                row.copy(raw = o)
            }
            val arr = JSONArray()
            next.forEach { arr.put(it.raw) }
            prefs.edit()
                .putString(cacheKey(key), arr.toString())
                .putLong(cacheTimeKey(key), System.currentTimeMillis())
                .apply()
            any = true
        }
        if (any) revision++
    }

    fun getRows(key: String): List<JsonRow>? {
        val raw = prefs.getString(cacheKey(key), null) ?: return null
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(KassaApi.rowOf(o, i))
                }
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    fun cachedAt(key: String): Long = prefs.getLong(cacheTimeKey(key), 0L)

    private fun cacheKey(key: String) = "cache_${key.hashCode()}"
    private fun cacheTimeKey(key: String) = "cache_time_${key.hashCode()}"

    fun clearAll() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("cache_") || it.startsWith("cache_time_") }.forEach { e.remove(it) }
        e.apply()
        revision++
    }
}

object UserCache {
    private const val KEY = "cached_me"

    fun save(ctx: android.content.Context, user: AppUser) {
        val perms = JSONArray()
        user.permissions.forEach { perms.put(it) }
        val cities = JSONArray()
        user.allowedCityKeys.forEach { cities.put(it) }
        val o = JSONObject()
            .put("id", user.id)
            .put("username", user.username)
            .put("fullName", user.fullName)
            .put("role", user.role)
            .put("permissions", perms)
            .put("operationsActivityEnabled", user.operationsActivityEnabled)
            .put("allowedCityKeys", cities)
            .put("avatarUrl", user.avatarUrl)
            .put("phone", user.phone)
            .put("email", user.email)
        ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, o.toString())
            .apply()
    }

    fun load(ctx: android.content.Context): AppUser? {
        val s = ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return try {
            val o = JSONObject(s)
            val p = o.optJSONArray("permissions")
            val perms = buildList {
                if (p != null) for (i in 0 until p.length()) add(p.optString(i))
            }
            AppUser(
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
                avatarUrl = o.optString("avatarUrl"),
                phone = o.optString("phone"),
                email = o.optString("email"),
            )
        } catch (_: Exception) {
            null
        }
    }
}
