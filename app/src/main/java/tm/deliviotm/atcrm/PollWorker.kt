package tm.deliviotm.atcrm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class PollWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("atcrm", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return Result.success()
        val apiBase = prefs.getString("api_base", BuildConfig.API_BASE) ?: BuildConfig.API_BASE
        return try {
            PollWatcher.check(applicationContext, KassaApi(apiBase), token, notify = true)
            try {
                val n = OutboxStore.of(applicationContext).flush(KassaApi(apiBase), token)
                if (n > 0 && !PollWatcher.appForeground) {
                    NotifyHelper.notifyOutboxFlushed(applicationContext, n)
                }
            } catch (_: Exception) {
            }
            scheduleSoon(applicationContext)
            Result.success()
        } catch (_: Exception) {
            scheduleSoon(applicationContext)
            Result.retry()
        }
    }

    companion object {
        private const val SOON = "atcrm_poll_soon"

        fun scheduleSoon(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<PollWorker>()
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(SOON, ExistingWorkPolicy.KEEP, req)
        }
    }
}
