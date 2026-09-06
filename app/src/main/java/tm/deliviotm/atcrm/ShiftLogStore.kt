package tm.deliviotm.atcrm

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ShiftUndo(
    val path: String,
    val prevStatus: String,
    val newStatus: String,
    val title: String,
    val rowId: String,
)

data class ShiftStats(
    val takes: Int,
    val problems: Int,
    val done: Int,
    val chats: Int,
    val lastTitle: String,
)

data class ShiftEvent(
    val kind: String,
    val title: String,
    val at: Long,
)

class ShiftLogStore(private val prefs: SharedPreferences) {
    var revision by mutableIntStateOf(0)
        private set

    fun stats(): ShiftStats {
        val day = dayKey()
        var takes = 0
        var problems = 0
        var done = 0
        var chats = 0
        var last = ""
        events().forEach { o ->
            if (o.optString("day") != day) return@forEach
            last = o.optString("title")
            when (o.optString("kind")) {
                "take" -> takes++
                "problem" -> problems++
                "done" -> done++
                "chat" -> chats++
            }
        }
        return ShiftStats(takes, problems, done, chats, last)
    }

    fun today(): List<ShiftEvent> {
        val day = dayKey()
        return events().mapNotNull { o ->
            if (o.optString("day") != day) null
            else ShiftEvent(
                kind = o.optString("kind"),
                title = o.optString("title"),
                at = o.optLong("at"),
            )
        }
    }

    fun exportToday(): String {
        val day = dayKey()
        val sb = StringBuilder("ATCRM журнал $day\n\n")
        today().forEach { ev ->
            val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ev.at))
            sb.append("$t  ${ev.kind}  ${ev.title}\n")
        }
        if (today().isEmpty()) sb.append("Пока пусто\n")
        return sb.toString().trimEnd()
    }

    fun lastUndo(): ShiftUndo? {
        val o = prefs.getString(KEY_UNDO, null) ?: return null
        return try {
            val j = JSONObject(o)
            if (j.optString("day") != dayKey()) return null
            val prev = j.optString("prev")
            if (prev.isBlank()) return null
            ShiftUndo(
                path = j.optString("path"),
                prevStatus = prev,
                newStatus = j.optString("next"),
                title = j.optString("title"),
                rowId = j.optString("rowId"),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearUndo() {
        prefs.edit().remove(KEY_UNDO).apply()
        revision++
    }

    fun record(kind: String, title: String, path: String, prev: String, next: String, rowId: String, undoable: Boolean) {
        val day = dayKey()
        val item = JSONObject()
            .put("day", day)
            .put("kind", kind)
            .put("title", title)
            .put("at", System.currentTimeMillis())
        val kept = JSONArray()
        kept.put(item)
        events().forEach { o ->
            if (kept.length() < 80) kept.put(o)
        }
        val ed = prefs.edit().putString(KEY_EVENTS, kept.toString())
        if (undoable && prev.isNotBlank() && path.isNotBlank() && prev != next) {
            ed.putString(
                KEY_UNDO,
                JSONObject()
                    .put("day", day)
                    .put("path", path)
                    .put("prev", prev)
                    .put("next", next)
                    .put("title", title)
                    .put("rowId", rowId)
                    .toString(),
            )
        }
        ed.apply()
        revision++
    }

    private fun events(): List<JSONObject> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(arr.optJSONObject(i) ?: continue)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_EVENTS = "shift_events_v1"
        private const val KEY_UNDO = "shift_undo_v1"
        @Volatile private var instance: ShiftLogStore? = null
        fun of(ctx: Context): ShiftLogStore {
            return instance ?: synchronized(this) {
                instance ?: ShiftLogStore(ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE)).also { instance = it }
            }
        }
        fun dayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
