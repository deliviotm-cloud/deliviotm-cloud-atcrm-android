package tm.deliviotm.atcrm

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class OutboxItem(
    val id: String,
    val method: String,
    val path: String,
    val body: JSONObject,
    val label: String,
    val at: Long,
    val filePath: String = "",
)

class OutboxStore(private val prefs: SharedPreferences) {
    var revision by mutableIntStateOf(0)
        private set

    fun count(): Int = listRaw().size

    fun items(): List<OutboxItem> = listRaw().map { o ->
        OutboxItem(
            id = o.optString("id"),
            method = o.optString("method"),
            path = o.optString("path"),
            body = JSONObject(o.optString("body", "{}")),
            label = o.optString("label"),
            at = o.optLong("at"),
            filePath = o.optString("file"),
        )
    }

    fun remove(id: String) {
        if (id.isBlank()) return
        val left = JSONArray()
        listRaw().forEach { o ->
            if (o.optString("id") == id) {
                ChatMedia.deleteOutboxFile(o.optString("file"))
            } else {
                left.put(o)
            }
        }
        prefs.edit().putString(KEY, left.toString()).apply()
        revision++
    }

    fun clear() {
        listRaw().forEach { ChatMedia.deleteOutboxFile(it.optString("file")) }
        prefs.edit().remove(KEY).apply()
        revision++
    }

    fun enqueue(method: String, path: String, body: JSONObject, label: String, filePath: String = "") {
        val bodyText = body.toString()
        val dup = listRaw().any { o ->
            o.optString("method") == method &&
                o.optString("path") == path &&
                o.optString("body") == bodyText &&
                o.optString("file") == filePath
        }
        if (dup) return
        val item = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("method", method)
            .put("path", path)
            .put("body", body.toString())
            .put("label", label)
            .put("at", System.currentTimeMillis())
            .put("file", filePath)
        val arr = JSONArray()
        listRaw().forEach { arr.put(it) }
        arr.put(item)
        prefs.edit().putString(KEY, arr.toString()).apply()
        revision++
    }

    fun flush(api: KassaApi, token: String): Int {
        val left = JSONArray()
        var ok = 0
        listRaw().forEach { item ->
            try {
                sendItem(api, token, item)
                ok++
            } catch (e: Exception) {
                if (e is SessionExpiredException) throw e
                left.put(item)
            }
        }
        prefs.edit().putString(KEY, left.toString()).apply()
        if (ok > 0 || left.length() != listRaw().size) revision++
        return ok
    }

    fun flushOne(api: KassaApi, token: String, id: String): Boolean {
        val item = listRaw().find { it.optString("id") == id } ?: return false
        return try {
            sendItem(api, token, item)
            remove(id)
            true
        } catch (e: Exception) {
            if (e is SessionExpiredException) throw e
            false
        }
    }

    private fun sendItem(api: KassaApi, token: String, item: JSONObject) {
        val method = item.optString("method")
        val path = item.optString("path")
        val body = JSONObject(item.optString("body", "{}"))
        val filePath = item.optString("file")
        when (method) {
            "PHOTO", "FILE" -> {
                val file = File(filePath)
                if (!file.exists() || file.length() <= 0L) error("Файл потерян")
                val caption = body.optString("caption").ifBlank { body.optString("text") }
                val mime = body.optString("mime").ifBlank {
                    if (method == "PHOTO") "image/jpeg" else "audio/mp4"
                }
                val sent = api.sendChatFile(path, token, file, mime, caption)
                if (!sent) error("Сервер не принял файл")
                ChatMedia.deleteOutboxFile(filePath)
            }
            "POST" -> api.postJson(path, token, body)
            else -> api.patchJson(path, token, body)
        }
    }

    private fun listRaw(): List<JSONObject> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
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
        private const val KEY = "outbox_v1"
        @Volatile private var instance: OutboxStore? = null
        fun of(ctx: Context): OutboxStore {
            return instance ?: synchronized(this) {
                instance ?: OutboxStore(ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE)).also { instance = it }
            }
        }
    }
}
