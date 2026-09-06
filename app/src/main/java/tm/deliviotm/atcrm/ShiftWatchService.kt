package tm.deliviotm.atcrm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Пока приложение свёрнуто — опрашивает API чаще, чем WorkManager (минимум 15 мин).
 * Без FCM это единственный способ «живых» уведомлений на смене.
 */
class ShiftWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun startFg() {
        NotifyHelper.ensureChannels(this)
        val open = PendingIntent.getActivity(
            this,
            77,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, NotifyHelper.CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentTitle("AT CRM на смене")
            .setContentText("Слежу за заказами на проверке, задачами и чатами")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ServiceCompat.startForeground(
                    this,
                    7701,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(7701, n)
            }
        } catch (_: Exception) {
            startForeground(7701, n)
        }
    }

    private suspend fun runLoop() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "atcrm:watch").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
        while (scope.isActive) {
            val prefs = getSharedPreferences("atcrm", MODE_PRIVATE)
            val tok = prefs.getString("token", null)
            if (tok.isNullOrBlank() || !PollWatcher.isEnabled(this)) {
                stopSelf()
                return
            }
            val base = prefs.getString("api_base", BuildConfig.API_BASE) ?: BuildConfig.API_BASE
            val api = KassaApi(base)
            try {
                PollWatcher.check(this@ShiftWatchService, api, tok, notify = true)
                val flushed = OutboxStore.of(this@ShiftWatchService).flush(api, tok)
                if (flushed > 0 && !PollWatcher.appForeground) {
                    NotifyHelper.notifyOutboxFlushed(this@ShiftWatchService, flushed)
                }
            } catch (_: Exception) {
            }
            try {
                if (wake?.isHeld == true) wake?.release()
            } catch (_: Exception) {
            }
            val wait = prefs.getInt("poll_sec", 15).coerceIn(15, 60) * 1000L
            delay(wait)
            try {
                wake?.acquire(10 * 60 * 1000L)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, ShiftWatchService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) {
            }
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, ShiftWatchService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
