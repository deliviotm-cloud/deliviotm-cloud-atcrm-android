package tm.deliviotm.atcrm

import android.content.SharedPreferences

class ScrollStore(private val prefs: SharedPreferences) {
    fun get(key: String): Pair<Int, Int> {
        val raw = prefs.getString(prefKey(key), null) ?: return 0 to 0
        val parts = raw.split('|')
        val index = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val offset = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return index to offset
    }

    fun put(key: String, index: Int, offset: Int) {
        prefs.edit().putString(prefKey(key), "$index|$offset").apply()
    }

    private fun prefKey(key: String) = "scroll_${key.hashCode()}"
}
