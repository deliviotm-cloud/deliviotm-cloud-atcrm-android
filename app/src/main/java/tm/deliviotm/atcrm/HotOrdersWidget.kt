package tm.deliviotm.atcrm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class HotOrdersWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val rows = loadRows(context)
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views(context, rows)) }
    }

    companion object {
        fun refresh(ctx: Context, rows: List<JsonRow>? = null) {
            val data = rows ?: loadRows(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, HotOrdersWidget::class.java))
            if (ids.isEmpty()) return
            val rv = views(ctx, data)
            ids.forEach { mgr.updateAppWidget(it, rv) }
        }

        private fun loadRows(ctx: Context): List<JsonRow> {
            val cache = CacheStore(ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE))
            val ops = cache.getRows(KassaApi.opsCacheKey())
                ?: cache.getRows("/operations?take=20")
                ?: cache.getRows("/operations?take=30")
                ?: cache.getRows("/operations?take=12")
                ?: cache.getRows("home_0")
                ?: emptyList()
            return KassaApi.sortOperations(ops)
        }

        private fun views(ctx: Context, rows: List<JsonRow>): RemoteViews {
            val urgent = rows.filter { KassaApi.operationPriority(it) == 0 }
            val top = urgent.ifEmpty { rows }.take(3)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_hot)
            rv.setTextViewText(
                R.id.widget_title,
                if (urgent.isNotEmpty()) "ATCRM · ${urgent.size} срочно" else "ATCRM · Очередь",
            )
            rv.setTextViewText(R.id.widget_line1, line(top, 0))
            rv.setTextViewText(R.id.widget_line2, line(top, 1))
            rv.setTextViewText(R.id.widget_line3, line(top, 2))
            rv.setTextViewText(
                R.id.widget_foot,
                if (rows.isEmpty()) "Нет кэша — откройте приложение" else "Всего в срезе: ${rows.size}",
            )
            val listOpen = NotifyHelper.appPendingIntent(ctx, 3101, hub = 0, module = "/operations")
            rv.setOnClickPendingIntent(R.id.widget_root, listOpen)
            rv.setOnClickPendingIntent(R.id.widget_foot, listOpen)
            rv.setOnClickPendingIntent(R.id.widget_line1, NotifyHelper.appPendingIntent(ctx, 3201, hub = 0, module = "/operations", opId = top.getOrNull(0)?.id, opTitle = top.getOrNull(0)?.title))
            rv.setOnClickPendingIntent(R.id.widget_line2, NotifyHelper.appPendingIntent(ctx, 3202, hub = 0, module = "/operations", opId = top.getOrNull(1)?.id, opTitle = top.getOrNull(1)?.title))
            rv.setOnClickPendingIntent(R.id.widget_line3, NotifyHelper.appPendingIntent(ctx, 3203, hub = 0, module = "/operations", opId = top.getOrNull(2)?.id, opTitle = top.getOrNull(2)?.title))
            val first = top.firstOrNull()
            if (first != null) {
                rv.setViewVisibility(R.id.widget_take, View.VISIBLE)
                rv.setTextViewText(R.id.widget_take, "Подтвердить · ${first.title.take(28)}")
                val take = Intent(ctx, NotifyActionReceiver::class.java)
                    .setAction(NotifyActionReceiver.ACTION_TAKE)
                    .putExtra(NotifyActionReceiver.EXTRA_ID, first.id)
                    .putExtra(NotifyActionReceiver.EXTRA_TITLE, first.title)
                rv.setOnClickPendingIntent(
                    R.id.widget_take,
                    PendingIntent.getBroadcast(
                        ctx,
                        3301,
                        take,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                rv.setViewVisibility(R.id.widget_take, View.GONE)
            }
            return rv
        }

        private fun line(rows: List<JsonRow>, i: Int): String {
            val row = rows.getOrNull(i) ?: return "—"
            val status = KassaApi.pick(row.raw, "status", "state").ifBlank { "в работе" }
            return "${row.title} · $status"
        }
    }
}
