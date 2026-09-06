package tm.deliviotm.atcrm

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Красный кружок с числом на иконке. Pixel/Android 8+ берёт число из уведомления
 * ([NotifyHelper.updateBadge]); Samsung/Xiaomi/Huawei — через эти интенты.
 */
object LauncherBadge {
    fun apply(ctx: Context, count: Int) {
        val n = count.coerceAtLeast(0)
        val pkg = ctx.packageName
        val cls = MainActivity::class.java.name
        runCatching { samsung(ctx, pkg, cls, n) }
        runCatching { broadcast(ctx, pkg, cls, n) }
        runCatching { xiaomi(ctx, pkg, cls, n) }
        runCatching { huawei(ctx, pkg, cls, n) }
        runCatching { sony(ctx, pkg, cls, n) }
        runCatching { oppo(ctx, pkg, n) }
        runCatching { vivo(ctx, pkg, cls, n) }
        runCatching { htc(ctx, pkg, cls, n) }
        ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE)
            .edit()
            .putInt("badge_total", n)
            .apply()
    }

    private fun broadcast(ctx: Context, pkg: String, cls: String, count: Int) {
        val i = Intent("android.intent.action.BADGE_COUNT_UPDATE")
            .putExtra("badge_count", count)
            .putExtra("badge_count_package_name", pkg)
            .putExtra("badge_count_class_name", cls)
        ctx.sendBroadcast(i)
    }

    private fun samsung(ctx: Context, pkg: String, cls: String, count: Int) {
        val uri = Uri.parse("content://com.sec.badge/apps?notify=true")
        val cv = android.content.ContentValues().apply {
            put("package", pkg)
            put("class", cls)
            put("badgecount", count)
        }
        ctx.contentResolver.insert(uri, cv)
    }

    private fun xiaomi(ctx: Context, pkg: String, cls: String, count: Int) {
        val i = Intent("android.intent.action.APPLICATION_MESSAGE_UPDATE")
            .putExtra("android.intent.extra.update_application_component_name", "$pkg/$cls")
            .putExtra(
                "android.intent.extra.update_application_message_text",
                if (count == 0) "" else count.toString(),
            )
        ctx.sendBroadcast(i)
    }

    private fun huawei(ctx: Context, pkg: String, cls: String, count: Int) {
        val extra = Bundle().apply {
            putString("package", pkg)
            putString("class", cls)
            putInt("badgenumber", count)
        }
        ctx.contentResolver.call(
            Uri.parse("content://com.huawei.android.launcher.settings/badge/"),
            "change_badge",
            null,
            extra,
        )
    }

    private fun sony(ctx: Context, pkg: String, cls: String, count: Int) {
        val i = Intent("com.sonyericsson.home.action.UPDATE_BADGE")
            .putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", cls)
            .putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", count > 0)
            .putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", count.toString())
            .putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", pkg)
        ctx.sendBroadcast(i)
    }

    private fun oppo(ctx: Context, pkg: String, count: Int) {
        val i = Intent("com.oppo.unsettledevent")
            .putExtra("pakeageName", pkg)
            .putExtra("number", count)
            .putExtra("upgradeNumber", count)
        ctx.sendBroadcast(i)
    }

    private fun vivo(ctx: Context, pkg: String, cls: String, count: Int) {
        val i = Intent("launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM")
            .putExtra("packageName", pkg)
            .putExtra("className", cls)
            .putExtra("notificationNum", count)
        ctx.sendBroadcast(i)
    }

    private fun htc(ctx: Context, pkg: String, cls: String, count: Int) {
        val i = Intent("com.htc.launcher.action.UPDATE_SHORTCUT")
            .putExtra("packagename", pkg)
            .putExtra("count", count)
        ctx.sendBroadcast(i)
        val i2 = Intent("com.htc.launcher.action.SET_NOTIFICATION")
            .putExtra("com.htc.launcher.extra.COMPONENT", "$pkg/$cls")
            .putExtra("com.htc.launcher.extra.COUNT", count)
        ctx.sendBroadcast(i2)
    }
}
