package tm.deliviotm.atcrm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class TemplatesStore(private val prefs: SharedPreferences) {
    fun all(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
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

    fun add(text: String) {
        val t = text.trim()
        if (t.length < 3) return
        val next = JSONArray()
        next.put(t)
        all().forEach { s ->
            if (s != t && next.length() < 12) next.put(s)
        }
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    fun remove(text: String) {
        val next = JSONArray()
        all().forEach { s ->
            if (s != text) next.put(s)
        }
        prefs.edit().putString(KEY, next.toString()).apply()
    }

    companion object {
        private const val KEY = "reply_templates_v1"
        fun of(ctx: Context): TemplatesStore =
            TemplatesStore(ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE))
    }
}
