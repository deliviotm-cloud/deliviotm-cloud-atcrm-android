package tm.deliviotm.atcrm

import android.content.SharedPreferences

class PinsStore(private val prefs: SharedPreferences) {
    fun ids(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    fun isPinned(id: String): Boolean = id.isNotBlank() && ids().contains(id)

    fun toggle(id: String): Boolean {
        if (id.isBlank()) return false
        val next = ids().toMutableSet()
        val pinned = if (next.contains(id)) {
            next.remove(id)
            false
        } else {
            next.add(id)
            true
        }
        prefs.edit().putStringSet(KEY, next).apply()
        return pinned
    }

    companion object {
        private const val KEY = "pinned_chats"
    }
}
