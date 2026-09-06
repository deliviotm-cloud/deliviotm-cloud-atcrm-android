package tm.deliviotm.atcrm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import org.json.JSONObject

class NotifyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TAKE -> handleTake(context, intent)
            ACTION_REPLY -> handleReply(context, intent)
            ACTION_READ -> handleRead(context, intent)
        }
    }

    private fun handleTake(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (id.isBlank()) return
        val pending = goAsync()
        Thread {
            try {
                val prefs = context.getSharedPreferences("atcrm", Context.MODE_PRIVATE)
                val token = prefs.getString("token", null)
                val base = prefs.getString("api_base", BuildConfig.API_BASE) ?: BuildConfig.API_BASE
                if (token.isNullOrBlank()) {
                    NotifyHelper.notifyTakeFailed(context, "Нет сессии — откройте приложение")
                    return@Thread
                }
                val api = KassaApi(base)
                val path = KassaApi.confirmLogisticsPath(id)
                val body = JSONObject()
                if (!NetWatch.online(context)) {
                    OutboxStore.of(context).enqueue("POST", path, body, "Подтвердить")
                    ShiftLogStore.of(context).record("take", title.ifBlank { id }, path, "", "CREATED", id, undoable = false)
                    NotifyHelper.notifyTaken(context, title.ifBlank { id })
                    return@Thread
                }
                api.postJson(path, token, body)
                ShiftLogStore.of(context).record("take", title.ifBlank { id }, path, "", "CREATED", id, undoable = false)
                NotifyHelper.notifyTaken(context, title.ifBlank { id })
            } catch (_: Exception) {
                NotifyHelper.notifyTakeFailed(context, "Не удалось подтвердить заказ")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handleReply(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_CHAT_TITLE).orEmpty()
        if (text.isBlank() || chatId.isBlank()) return
        val pending = goAsync()
        Thread {
            try {
                val prefs = context.getSharedPreferences("atcrm", Context.MODE_PRIVATE)
                val token = prefs.getString("token", null)
                val base = prefs.getString("api_base", BuildConfig.API_BASE) ?: BuildConfig.API_BASE
                if (token.isNullOrBlank()) {
                    NotifyHelper.notifyReplyFailed(context, "Нет сессии — откройте приложение")
                    return@Thread
                }
                val path = chatSendPath(chatId)
                val body = JSONObject().put("text", text)
                if (!NetWatch.online(context)) {
                    OutboxStore.of(context).enqueue("POST", path, body, "Сообщение", "")
                    ShiftLogStore.of(context).record("chat", title.ifBlank { "Чат" }, path, "", "", chatId, undoable = false)
                    NotifyHelper.notifyReplied(context, text)
                    return@Thread
                }
                KassaApi(base).postJson(path, token, body)
                ShiftLogStore.of(context).record("chat", title.ifBlank { "Чат" }, path, "", "", chatId, undoable = false)
                NotifyHelper.notifyReplied(context, text)
            } catch (_: Exception) {
                try {
                    val path = chatSendPath(chatId)
                    OutboxStore.of(context).enqueue("POST", path, JSONObject().put("text", text), "Сообщение", "")
                    NotifyHelper.notifyReplied(context, "В очереди: $text")
                } catch (_: Exception) {
                    NotifyHelper.notifyReplyFailed(context, "Не удалось отправить ответ")
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handleRead(context: Context, intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        if (chatId.isBlank()) return
        val pending = goAsync()
        Thread {
            try {
                val prefs = context.getSharedPreferences("atcrm", Context.MODE_PRIVATE)
                val token = prefs.getString("token", null)
                val base = prefs.getString("api_base", BuildConfig.API_BASE) ?: BuildConfig.API_BASE
                if (token.isNullOrBlank()) return@Thread
                val path = chatSendPath(chatId)
                KassaApi(base).markChatRead(path, token)
                CacheStore(prefs).markChatRead(KassaApi.chatIdFromPath(path).ifBlank { chatId })
                NotifyHelper.notifyRead(context)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_TAKE = "tm.deliviotm.atcrm.TAKE_ORDER"
        const val ACTION_REPLY = "tm.deliviotm.atcrm.REPLY_CHAT"
        const val ACTION_READ = "tm.deliviotm.atcrm.READ_CHAT"
        const val EXTRA_ID = "op_id"
        const val EXTRA_TITLE = "op_title"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_CHAT_TITLE = "chat_title"
        const val KEY_REPLY = "atcrm_reply_text"

        fun chatSendPath(chatId: String): String {
            val raw = chatId.trim()
            return when {
                raw.contains("/messages") || raw.contains("/workspace/") || raw.contains("/support/") ->
                    KassaApi.chatSendPath(raw)
                else -> "/workspace/chats/$raw/messages"
            }
        }
    }
}
