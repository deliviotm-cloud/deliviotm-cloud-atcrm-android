package tm.deliviotm.atcrm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        val prefs = context.getSharedPreferences("atcrm", Context.MODE_PRIVATE)
        if (prefs.getString("token", null).isNullOrBlank()) return
        PollScheduler.schedule(context)
        if (PollWatcher.isEnabled(context)) {
            ShiftWatchService.start(context)
        }
    }
}
