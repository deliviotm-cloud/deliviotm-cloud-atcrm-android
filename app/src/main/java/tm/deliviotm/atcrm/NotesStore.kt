package tm.deliviotm.atcrm

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class NotesStore(private val prefs: SharedPreferences, private val filesDir: File) {
    fun text(id: String): String {
        if (id.isBlank()) return ""
        return prefs.getString(textKey(id), "").orEmpty()
    }

    fun setText(id: String, value: String) {
        if (id.isBlank()) return
        val ed = prefs.edit()
        if (value.isBlank()) ed.remove(textKey(id)) else ed.putString(textKey(id), value)
        ed.apply()
    }

    fun voiceFile(id: String): File {
        val dir = File(filesDir, "voice")
        if (!dir.exists()) dir.mkdirs()
        val safe = id.hashCode().toUInt().toString()
        return File(dir, "op_$safe.m4a")
    }

    fun hasVoice(id: String): Boolean = id.isNotBlank() && voiceFile(id).exists() && voiceFile(id).length() > 0

    fun deleteVoice(id: String) {
        voiceFile(id).delete()
    }

    companion object {
        fun of(ctx: Context): NotesStore =
            NotesStore(ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE), ctx.filesDir)

        private fun textKey(id: String) = "note_$id"
    }
}
