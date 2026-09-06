package tm.deliviotm.atcrm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val specPath: String,
    val raw: JSONObject,
    val at: Long,
) {
    fun row(): JsonRow = JsonRow(id, title, subtitle, raw)

    fun spec(): ModuleSpec {
        val tab = when {
            specPath.startsWith("/workspace/chats") -> "chats"
            specPath.startsWith("/support") -> "support"
            specPath.startsWith("/workspace/tasks") -> "tasks"
            specPath.startsWith("/calls") -> "callcenter"
            specPath.startsWith("/establishments") -> "establishments"
            else -> "operations"
        }
        return ModuleSpec(tab, title, specPath)
    }

    fun kindLabel(): String = when {
        specPath.contains("/chats") || specPath.contains("/threads") -> "чат"
        specPath.contains("/tasks") -> "задача"
        specPath.contains("/calls") -> "звонок"
        specPath.contains("/establishments") -> "точка"
        else -> "заказ"
    }
}

class RecentsStore(private val prefs: SharedPreferences) {
    fun push(row: JsonRow, specPath: String) {
        if (row.id.isBlank() && row.title.isBlank()) return
        val id = row.id.ifBlank { row.title }
        val next = JSONObject()
            .put("id", id)
            .put("title", row.title)
            .put("subtitle", row.subtitle.take(120))
            .put("specPath", specPath)
            .put("raw", row.raw)
            .put("at", System.currentTimeMillis())
        val kept = JSONArray()
        kept.put(next)
        listRaw().forEach { o ->
            if (o.optString("id") != id && kept.length() < 12) kept.put(o)
        }
        prefs.edit().putString(KEY, kept.toString()).apply()
    }

    fun list(): List<RecentItem> = listRaw().mapNotNull { o ->
        try {
            RecentItem(
                id = o.optString("id"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle"),
                specPath = o.optString("specPath").ifBlank { "/operations" },
                raw = o.optJSONObject("raw") ?: JSONObject(),
                at = o.optLong("at"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun listRaw(): List<JSONObject> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.optJSONObject(i) ?: continue)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY = "recents_v1"
    }
}

object HubBadges {
    fun work(cache: CacheStore): Int {
        val ops = cache.getRows(KassaApi.opsCacheKey())
            ?: cache.getRows("/operations?take=20")
            ?: cache.getRows("/operations?take=30")
            ?: cache.getRows("/operations?take=12")
            ?: cache.getRows("home_0")
            ?: emptyList()
        return ops.count { KassaApi.operationPriority(it) == 0 }
    }

    fun comms(cache: CacheStore): Int {
        val chats = cache.getRows("/workspace/chats") ?: cache.getRows("home_1") ?: emptyList()
        return chats.sumOf {
            KassaApi.pick(it.raw, "unreadCount", "unread", "count").toIntOrNull() ?: 0
        }
    }
}

object DeviceIntents {
    fun maps(ctx: Context, query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(q)}"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(q)}"))
        try {
            ctx.startActivity(geo)
        } catch (_: Exception) {
            ctx.startActivity(web)
        }
    }

    fun whatsapp(ctx: Context, phone: String) {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 6) return
        val uri = Uri.parse("https://wa.me/$digits")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun geoOrAddress(obj: JSONObject): String {
        val lat = KassaApi.pick(obj, "lat", "latitude", "geoLat", "coordLat")
        val lng = KassaApi.pick(obj, "lng", "lon", "lng", "longitude", "geoLng", "coordLng")
        val latN = lat.toDoubleOrNull()
        val lngN = lng.toDoubleOrNull()
        if (latN != null && lngN != null && latN != 0.0 && lngN != 0.0) return "$latN,$lngN"
        return addressOf(obj)
    }

    fun dial(ctx: Context, phone: String) {
        val p = phone.filter { it.isDigit() || it == '+' }
        if (p.isBlank()) return
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$p")))
    }

    fun sms(ctx: Context, phone: String) {
        val p = phone.trim()
        if (p.isBlank()) return
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$p")))
    }

    fun share(ctx: Context, text: String) {
        if (text.isBlank()) return
        ctx.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "ATCRM",
            ),
        )
    }

    fun copy(ctx: Context, text: String) {
        if (text.isBlank()) return
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("atcrm", text))
    }

    fun orderShareText(obj: JSONObject): String {
        val no = KassaApi.pick(obj, "orderNumber", "externalId", "id")
        val status = KassaApi.pick(obj, "status", "state")
        val client = KassaApi.pick(obj, "clientName", "fullName", "name")
        val phone = KassaApi.phoneOf(obj)
        val addr = listOf(
            KassaApi.pick(obj, "address"),
            KassaApi.pick(obj, "city"),
        ).filter { it.isNotBlank() }.distinct().joinToString(", ")
        val amount = KassaApi.pick(obj, "orderAmount", "amount", "total")
        return buildString {
            appendLine("ATCRM · заказ ${no.ifBlank { "—" }}")
            if (status.isNotBlank()) appendLine("Статус: $status")
            if (client.isNotBlank()) appendLine("Клиент: $client")
            if (phone.isNotBlank()) appendLine("Тел.: $phone")
            if (addr.isNotBlank()) appendLine("Адрес: $addr")
            if (amount.isNotBlank()) appendLine("Сумма: $amount")
        }.trim()
    }

    fun addressOf(obj: JSONObject): String =
        listOf(
            KassaApi.pick(obj, "address"),
            KassaApi.pick(obj, "city"),
            KassaApi.pick(obj, "establishmentName"),
        ).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

data class LastPlace(
    val kind: String,
    val title: String,
    val path: String,
    val tab: String,
    val hub: Int,
) {
    val label: String get() = when (kind) {
        "chat" -> "Чат · $title"
        "list" -> title
        "search" -> "Поиск"
        "outbox" -> "Очередь"
        "shift" -> "Журнал смены"
        "settings" -> "Настройки"
        else -> title
    }
}

object LastPlaces {
    fun save(ctx: Context, kind: String, title: String = "", path: String = "", tab: String = "", hub: Int = 0) {
        ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE).edit()
            .putString("last_kind", kind)
            .putString("last_title", title)
            .putString("last_path", path)
            .putString("last_tab", tab)
            .putInt("last_hub", hub)
            .apply()
    }

    fun load(ctx: Context): LastPlace? {
        val p = ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE)
        val kind = p.getString("last_kind", null) ?: return null
        if (kind == "home" || kind.isBlank()) return null
        return LastPlace(
            kind = kind,
            title = p.getString("last_title", "").orEmpty(),
            path = p.getString("last_path", "").orEmpty(),
            tab = p.getString("last_tab", "").orEmpty(),
            hub = p.getInt("last_hub", 0),
        )
    }
}

object BatteryHelper {
    fun isIgnored(ctx: Context): Boolean {
        return try {
            val pm = ctx.getSystemService(android.os.PowerManager::class.java)
            pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        } catch (_: Exception) {
            true
        }
    }

    fun requestIgnore(ctx: Context) {
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${ctx.packageName}"))
        try {
            if (ctx !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                ctx.startActivity(
                    Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
            }
        }
    }
}
