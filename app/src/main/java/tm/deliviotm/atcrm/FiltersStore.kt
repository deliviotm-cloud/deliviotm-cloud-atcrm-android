package tm.deliviotm.atcrm

import android.content.SharedPreferences

class FiltersStore(private val prefs: SharedPreferences) {
    fun get(path: String): String = prefs.getString(key(path), "all") ?: "all"

    fun put(path: String, value: String) {
        prefs.edit().putString(key(path), value).apply()
    }

    private fun key(path: String) = "filter_${path.hashCode()}"
}
