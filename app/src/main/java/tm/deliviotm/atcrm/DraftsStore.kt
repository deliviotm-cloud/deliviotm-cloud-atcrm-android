package tm.deliviotm.atcrm

import android.content.SharedPreferences

class DraftsStore(private val prefs: SharedPreferences) {
    fun get(id: String): String {
        if (id.isBlank()) return ""
        return prefs.getString(key(id), "").orEmpty()
    }

    fun put(id: String, text: String) {
        if (id.isBlank()) return
        val trimmed = text.trim()
        val ed = prefs.edit()
        if (trimmed.isBlank()) ed.remove(key(id)) else ed.putString(key(id), text)
        ed.apply()
    }

    fun clear(id: String) {
        if (id.isBlank()) return
        prefs.edit().remove(key(id)).apply()
    }

    fun has(id: String): Boolean = get(id).isNotBlank()

    private fun key(id: String) = "draft_$id"
}
