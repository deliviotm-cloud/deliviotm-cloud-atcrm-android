package tm.deliviotm.atcrm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

object NotifyHelper {
    const val EXTRA_HUB = "atcrm_open_hub"
    const val EXTRA_MODULE = "atcrm_open_module"
    const val EXTRA_CHAT_PATH = "atcrm_open_chat"
    const val EXTRA_CHAT_TITLE = "atcrm_open_chat_title"
    const val EXTRA_OP_ID = "atcrm_open_op"
    const val EXTRA_OP_TITLE = "atcrm_open_op_title"
    const val EXTRA_TASK_ID = "atcrm_open_task"
    const val EXTRA_TASK_TITLE = "atcrm_open_task_title"

    const val ID_OPS = 1001
    const val ID_CHATS = 1002
    const val ID_TASKS = 1008
    const val ID_INBOX = 1009
    const val ID_TEST = 1010
    const val ID_BADGE = 1099

    private const val CHANNEL_OPS = "atcrm_operations_v2"
    private const val CHANNEL_OPS_QUIET = "atcrm_operations_quiet"
    private const val CHANNEL_OPS_HEADS = "atcrm_operations_heads"
    private const val CHANNEL_CHATS = "atcrm_chats_v2"
    private const val CHANNEL_CHATS_QUIET = "atcrm_chats_quiet"
    private const val CHANNEL_CHATS_HEADS = "atcrm_chats_heads"
    const val CHANNEL_WATCH = "atcrm_watch"
    private const val KEY_VIBRATE = "notify_vibrate"

    fun isVibrate(ctx: Context): Boolean =
        ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE).getBoolean(KEY_VIBRATE, true)

    fun setVibrate(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE).edit().putBoolean(KEY_VIBRATE, enabled).apply()
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(opsChannel(CHANNEL_OPS, "Операции", vibrate = true))
        mgr.createNotificationChannel(opsChannel(CHANNEL_OPS_QUIET, "Операции без вибрации", vibrate = false))
        mgr.createNotificationChannel(headsChannel(CHANNEL_OPS_HEADS, "Срочные заказы"))
        mgr.createNotificationChannel(chatChannel(CHANNEL_CHATS, "Чаты", vibrate = true))
        mgr.createNotificationChannel(chatChannel(CHANNEL_CHATS_QUIET, "Чаты без вибрации", vibrate = false))
        mgr.createNotificationChannel(headsChannel(CHANNEL_CHATS_HEADS, "Срочные чаты"))
        mgr.createNotificationChannel(
            NotificationChannel("atcrm_tasks_heads", "Задачи", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Новые задачи и смена статуса"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 160, 80, 160)
                setShowBadge(true)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel("atcrm_tasks_quiet", "Задачи без вибрации", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Задачи без вибрации"
                enableVibration(false)
                setShowBadge(true)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel("atcrm_inbox_heads", "Уведомления CRM", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Колокол: системные события из AT CRM"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 160, 80, 160)
                setShowBadge(true)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel("atcrm_inbox_quiet", "Уведомления без вибрации", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Колокол без вибрации"
                enableVibration(false)
                setShowBadge(true)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel("atcrm_badge", "Значок приложения", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Красный кружок с числом на иконке"
                setShowBadge(true)
                enableVibration(false)
                setSound(null, null)
            },
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, "Дежурство", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Фоновый опрос заказов и чатов, пока приложение свёрнуто"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
    }

    fun notifyOperations(ctx: Context, count: Int, sample: String, operationId: String = "", more: List<String> = emptyList()) {
        if (count <= 0 || !canPost(ctx)) return
        ensureChannels(ctx)
        val intent = launchIntent(
            ctx,
            hub = 0,
            module = "/operations",
            opId = operationId.takeIf { it.isNotBlank() },
            opTitle = sample.takeIf { it.isNotBlank() },
        )
        val menu = "Работа → Операции"
        val title = if (count == 1) "Новый неподтверждённый заказ" else "$count неподтверждённых заказов"
        val collapsed = sample.ifBlank { "На проверке — подтвердите в Операциях" }
        val builder = NotificationCompat.Builder(
            ctx,
            if (isVibrate(ctx)) CHANNEL_OPS_HEADS else CHANNEL_OPS_QUIET,
        )
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        applyMenuExpand(
            builder,
            menu = menu,
            title = title,
            collapsed = collapsed,
            expanded = collapsed,
            lines = buildList {
                add(collapsed)
                addAll(more.filter { it.isNotBlank() && it != collapsed })
            },
            openLabel = "Открыть в Операциях",
            open = intent,
        )
        applyLauncherExtras(builder, count)
        if (!PollWatcher.appForeground) {
            builder.setFullScreenIntent(intent, true)
        }
        if (operationId.isNotBlank() && count == 1) {
            val take = Intent(ctx, NotifyActionReceiver::class.java)
                .setAction(NotifyActionReceiver.ACTION_TAKE)
                .putExtra(NotifyActionReceiver.EXTRA_ID, operationId)
                .putExtra(NotifyActionReceiver.EXTRA_TITLE, sample)
            val pi = PendingIntent.getBroadcast(
                ctx,
                1101,
                take,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Взять", pi)
        }
        post(ctx, ID_OPS, builder.build())
    }

    fun notifyTaken(ctx: Context, title: String) {
        ensureChannels(ctx)
        NotificationManagerCompat.from(ctx).cancel(ID_OPS)
        val n = NotificationCompat.Builder(ctx, CHANNEL_OPS_QUIET)
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentTitle("Заказ подтверждён")
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        post(ctx, 1003, n)
    }

    fun notifyTakeFailed(ctx: Context, reason: String) {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_OPS_QUIET)
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentTitle("Не подтвердилось")
            .setContentText(reason)
            .setContentIntent(launchIntent(ctx, hub = 0, module = "/operations"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        post(ctx, 1004, n)
    }

    fun notifyReplied(ctx: Context, text: String) {
        ensureChannels(ctx)
        NotificationManagerCompat.from(ctx).cancel(ID_CHATS)
        val n = NotificationCompat.Builder(ctx, CHANNEL_CHATS_QUIET)
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentTitle("Ответ отправлен")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        post(ctx, 1005, n)
    }

    fun notifyOutboxFlushed(ctx: Context, count: Int) {
        if (count <= 0 || !canPost(ctx)) return
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_OPS_QUIET)
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentTitle("Очередь отправлена")
            .setContentText("Ушло из офлайна: $count")
            .setContentIntent(launchIntent(ctx, hub = 0, module = null))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        post(ctx, 1007, n)
    }

    fun notifyRead(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_CHATS)
    }

    fun notifyReplyFailed(ctx: Context, reason: String) {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_CHATS)
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentTitle("Ответ не ушёл")
            .setContentText(reason)
            .setContentIntent(launchIntent(ctx, hub = 1, module = "/workspace/chats"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        post(ctx, 1006, n)
    }

    fun notifyChats(ctx: Context, count: Int, sample: String, chatId: String = "", preview: String = "") {
        if (count <= 0 || !canPost(ctx)) return
        ensureChannels(ctx)
        val chatModule = when {
            chatId.contains("/support/") -> "/support/threads"
            else -> "/workspace/chats"
        }
        val menu = if (chatId.contains("/support/")) "Связь → Поддержка" else "Связь → Чаты"
        val openLabel = if (chatId.contains("/support/")) "Открыть в Поддержке" else "Открыть в Чатах"
        val intent = launchIntent(
            ctx,
            hub = 1,
            module = chatModule,
            chatPath = chatId.ifBlank { null },
            chatTitle = sample.ifBlank { null },
        )
        val title = when {
            count == 1 && sample.isNotBlank() -> sample
            count == 1 -> "Новое сообщение"
            else -> "$count непрочитанных в чатах"
        }
        val text = preview.ifBlank { sample }.ifBlank { "Есть диалоги, ждущие ответа" }
        val builder = NotificationCompat.Builder(
            ctx,
            if (isVibrate(ctx)) CHANNEL_CHATS_HEADS else CHANNEL_CHATS_QUIET,
        )
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        applyMenuExpand(
            builder,
            menu = menu,
            title = title,
            collapsed = text,
            expanded = if (sample.isNotBlank() && preview.isNotBlank() && sample != preview) "$sample\n$text" else text,
            openLabel = openLabel,
            open = intent,
        )
        applyLauncherExtras(builder, count)
        if (chatId.isNotBlank()) {
            val replyIntent = Intent(ctx, NotifyActionReceiver::class.java)
                .setAction(NotifyActionReceiver.ACTION_REPLY)
                .putExtra(NotifyActionReceiver.EXTRA_CHAT_ID, chatId)
                .putExtra(NotifyActionReceiver.EXTRA_CHAT_TITLE, sample)
            val replyPi = PendingIntent.getBroadcast(
                ctx,
                1102,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutableFlags(),
            )
            val remote = RemoteInput.Builder(NotifyActionReceiver.KEY_REPLY)
                .setLabel("Ответ клиенту")
                .build()
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Ответить", replyPi)
                    .addRemoteInput(remote)
                    .setAllowGeneratedReplies(true)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build(),
            )
            val readIntent = Intent(ctx, NotifyActionReceiver::class.java)
                .setAction(NotifyActionReceiver.ACTION_READ)
                .putExtra(NotifyActionReceiver.EXTRA_CHAT_ID, chatId)
            val readPi = PendingIntent.getBroadcast(
                ctx,
                1103,
                readIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Прочитано", readPi)
        }
        post(ctx, ID_CHATS, builder.build())
    }

    fun notifyTasks(ctx: Context, count: Int, sample: String, taskId: String = "", more: List<String> = emptyList()) {
        if (count <= 0 || !canPost(ctx)) return
        ensureChannels(ctx)
        val intent = launchIntent(
            ctx,
            hub = 0,
            module = "/workspace/tasks",
            taskId = taskId.takeIf { it.isNotBlank() },
            taskTitle = sample.takeIf { it.isNotBlank() },
        )
        val n = NotificationCompat.Builder(
            ctx,
            if (isVibrate(ctx)) "atcrm_tasks_heads" else "atcrm_tasks_quiet",
        )
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        applyMenuExpand(
            n,
            menu = "Работа → Задачи",
            title = if (count == 1) "Задача" else "$count изменений по задачам",
            collapsed = sample.ifBlank { "Новые задачи или смена статуса" },
            expanded = sample.ifBlank { "Новые задачи или смена статуса" },
            lines = buildList {
                add(sample)
                addAll(more)
            },
            openLabel = "Открыть в Задачах",
            open = intent,
        )
        applyLauncherExtras(n, count)
        post(ctx, ID_TASKS, n.build())
    }

    fun notifyInbox(
        ctx: Context,
        count: Int,
        sample: String,
        preview: String = "",
        noticeId: String = "",
        more: List<String> = emptyList(),
    ) {
        if (count <= 0 || !canPost(ctx)) return
        ensureChannels(ctx)
        val intent = launchIntent(ctx, hub = 0, module = "/workspace/notifications")
        val title = if (count == 1) sample.ifBlank { "Новое уведомление" } else "$count новых уведомлений"
        val text = preview.ifBlank { sample }.ifBlank { "Откройте колокол в AT CRM" }
        val builder = NotificationCompat.Builder(
            ctx,
            if (isVibrate(ctx)) "atcrm_inbox_heads" else "atcrm_inbox_quiet",
        )
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setNumber(count)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        applyMenuExpand(
            builder,
            menu = "Уведомления",
            title = title,
            collapsed = text,
            expanded = text,
            lines = buildList {
                add(text)
                addAll(more)
            },
            openLabel = "Открыть колокол",
            open = intent,
        )
        applyLauncherExtras(builder, count)
        post(ctx, ID_INBOX, builder.build())
        noticeId
    }

    fun notifyTest(ctx: Context): Boolean {
        if (!canPost(ctx)) return false
        ensureChannels(ctx)
        val intent = launchIntent(ctx, hub = 0, module = "/workspace/notifications")
        val n = NotificationCompat.Builder(ctx, if (isVibrate(ctx)) CHANNEL_OPS_HEADS else CHANNEL_OPS_QUIET)
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentTitle("Проверка уведомлений")
            .setContentText("Пуши AT CRM работают. Колокол покажет новые события.")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        post(ctx, ID_TEST, n)
        return true
    }

    fun setOutstandingBadge(ctx: Context, pendingOps: Int, unreadChats: Int, taskAlerts: Int) {
        rememberCount(ctx, "badge_ops", pendingOps)
        rememberCount(ctx, "badge_chats", unreadChats)
        rememberCount(ctx, "badge_tasks", taskAlerts)
        refreshBadge(ctx)
    }

    fun cancelOps(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_OPS)
    }

    fun cancelChats(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_CHATS)
    }

    fun cancelTasks(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_TASKS)
    }

    fun clearAllBadges(ctx: Context) {
        NotificationManagerCompat.from(ctx).cancel(ID_OPS)
        NotificationManagerCompat.from(ctx).cancel(ID_CHATS)
        NotificationManagerCompat.from(ctx).cancel(ID_TASKS)
        NotificationManagerCompat.from(ctx).cancel(ID_INBOX)
        NotificationManagerCompat.from(ctx).cancel(ID_TEST)
        NotificationManagerCompat.from(ctx).cancel(ID_BADGE)
        rememberCount(ctx, "badge_ops", 0)
        rememberCount(ctx, "badge_chats", 0)
        rememberCount(ctx, "badge_tasks", 0)
        LauncherBadge.apply(ctx, 0)
    }

    private fun rememberCount(ctx: Context, key: String, value: Int) {
        ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE).edit().putInt(key, value.coerceAtLeast(0)).apply()
    }

    private fun refreshBadge(ctx: Context) {
        val prefs = ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE)
        val total = prefs.getInt("badge_ops", 0) + prefs.getInt("badge_chats", 0) + prefs.getInt("badge_tasks", 0)
        ensureChannels(ctx)
        if (total <= 0) {
            NotificationManagerCompat.from(ctx).cancel(ID_BADGE)
            LauncherBadge.apply(ctx, 0)
            return
        }
        val ops = prefs.getInt("badge_ops", 0)
        val chats = prefs.getInt("badge_chats", 0)
        val tasks = prefs.getInt("badge_tasks", 0)
        val parts = buildList {
            if (ops > 0) add("$ops заказ.")
            if (chats > 0) add("$chats чат.")
            if (tasks > 0) add("$tasks задач.")
        }
        val menuLines = buildList {
            if (ops > 0) add("Работа → Операции · $ops")
            if (chats > 0) add("Связь → Чаты · $chats")
            if (tasks > 0) add("Работа → Задачи · $tasks")
        }
        val builder = NotificationCompat.Builder(ctx, "atcrm_badge")
            .setSmallIcon(R.drawable.ic_stat_atcrm)
            .setContentTitle("AT CRM")
            .setContentText(parts.joinToString(" · ").ifBlank { "$total уведомлений" })
            .setSubText(menuLines.joinToString(" · ").ifBlank { "Меню" })
            .setNumber(total)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setContentIntent(badgeIntent(ctx, ops, chats, tasks))
            .setSilent(true)
            .setOngoing(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        if (menuLines.isNotEmpty()) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle("Где произошли события")
                .setSummaryText("Меню AT CRM")
            menuLines.forEach { inbox.addLine(it) }
            builder.setStyle(inbox)
        }
        applyLauncherExtras(builder, total)
        post(ctx, ID_BADGE, builder.build())
        LauncherBadge.apply(ctx, total)
    }

    private fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (!canPost(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun applyMenuExpand(
        builder: NotificationCompat.Builder,
        menu: String,
        title: String,
        collapsed: String,
        expanded: String,
        lines: List<String> = emptyList(),
        openLabel: String,
        open: PendingIntent,
    ) {
        builder
            .setSubText(menu)
            .setContentTitle(title)
            .setContentText(collapsed)
            .addAction(0, openLabel, open)
        val unique = lines.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (unique.size > 1) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText(menu)
            inbox.addLine("Меню: $menu")
            unique.take(6).forEach { inbox.addLine(it) }
            builder.setStyle(inbox)
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .setSummaryText(menu)
                    .bigText("Меню: $menu\n\n${expanded.ifBlank { collapsed }}"),
            )
        }
    }

    private fun applyLauncherExtras(builder: NotificationCompat.Builder, count: Int) {
        val extras = android.os.Bundle().apply {
            putInt("app_badge_count", count)
            putString("app_badge_count", count.toString())
            putInt("notification_count", count)
            putString("hw_bt_count", count.toString())
        }
        builder.addExtras(extras)
    }

    private fun headsChannel(id: String, name: String): NotificationChannel {
        return NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Полноэкранные и верхние оповещения на смене"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 200, 80, 200)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
    }

    private fun opsChannel(id: String, name: String, vibrate: Boolean): NotificationChannel {
        return NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Новые неподтверждённые заказы"
            enableVibration(vibrate)
            if (vibrate) vibrationPattern = longArrayOf(0, 180, 90, 180)
            setShowBadge(true)
        }
    }

    private fun chatChannel(id: String, name: String, vibrate: Boolean): NotificationChannel {
        return NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Непрочитанные диалоги"
            enableVibration(vibrate)
            if (vibrate) vibrationPattern = longArrayOf(0, 120, 80, 120)
            setShowBadge(true)
        }
    }

    internal fun appPendingIntent(
        ctx: Context,
        requestCode: Int,
        hub: Int,
        module: String? = null,
        chatPath: String? = null,
        chatTitle: String? = null,
        opId: String? = null,
        opTitle: String? = null,
        taskId: String? = null,
        taskTitle: String? = null,
    ): PendingIntent = launchIntent(
        ctx, hub, module, chatPath, chatTitle, opId, opTitle, taskId, taskTitle, requestCode,
    )

    private fun launchIntent(
        ctx: Context,
        hub: Int,
        module: String?,
        chatPath: String? = null,
        chatTitle: String? = null,
        opId: String? = null,
        opTitle: String? = null,
        taskId: String? = null,
        taskTitle: String? = null,
        requestCode: Int? = null,
    ): PendingIntent {
        val intent = openAppIntent(ctx, hub, module, chatPath, chatTitle, opId, opTitle, taskId, taskTitle)
        val req = requestCode ?: when {
            !chatPath.isNullOrBlank() || module.orEmpty().contains("/chats") || module.orEmpty().contains("/support") -> 2102
            !opId.isNullOrBlank() || module.orEmpty().startsWith("/operations") -> 2101
            !taskId.isNullOrBlank() || module.orEmpty().startsWith("/workspace/tasks") -> 2108
            else -> 2100 + hub
        }
        return PendingIntent.getActivity(
            ctx,
            req,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun openAppIntent(
        ctx: Context,
        hub: Int,
        module: String? = null,
        chatPath: String? = null,
        chatTitle: String? = null,
        opId: String? = null,
        opTitle: String? = null,
        taskId: String? = null,
        taskTitle: String? = null,
    ): Intent {
        val uri = Uri.Builder()
            .scheme("atcrm")
            .authority("open")
            .appendQueryParameter("hub", hub.toString())
            .apply {
                if (!module.isNullOrBlank()) appendQueryParameter("module", module)
                if (!chatPath.isNullOrBlank()) appendQueryParameter("chat", chatPath)
                if (!chatTitle.isNullOrBlank()) appendQueryParameter("title", chatTitle)
                if (!opId.isNullOrBlank()) appendQueryParameter("op", opId)
                if (!opTitle.isNullOrBlank()) appendQueryParameter("opTitle", opTitle)
                if (!taskId.isNullOrBlank()) appendQueryParameter("task", taskId)
                if (!taskTitle.isNullOrBlank()) appendQueryParameter("taskTitle", taskTitle)
            }
            .build()
        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(uri)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            .putExtra(EXTRA_HUB, hub)
        if (!module.isNullOrBlank()) intent.putExtra(EXTRA_MODULE, module)
        if (!chatPath.isNullOrBlank()) intent.putExtra(EXTRA_CHAT_PATH, chatPath)
        if (!chatTitle.isNullOrBlank()) intent.putExtra(EXTRA_CHAT_TITLE, chatTitle)
        if (!opId.isNullOrBlank()) intent.putExtra(EXTRA_OP_ID, opId)
        if (!opTitle.isNullOrBlank()) intent.putExtra(EXTRA_OP_TITLE, opTitle)
        if (!taskId.isNullOrBlank()) intent.putExtra(EXTRA_TASK_ID, taskId)
        if (!taskTitle.isNullOrBlank()) intent.putExtra(EXTRA_TASK_TITLE, taskTitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            intent.identifier = uri.toString()
        }
        return intent
    }

    private fun badgeIntent(ctx: Context, ops: Int, chats: Int, tasks: Int): PendingIntent {
        return when {
            ops > 0 -> launchIntent(ctx, hub = 0, module = "/operations", requestCode = 2099)
            chats > 0 -> launchIntent(ctx, hub = 1, module = "/workspace/chats", requestCode = 2099)
            tasks > 0 -> launchIntent(ctx, hub = 0, module = "/workspace/tasks", requestCode = 2099)
            else -> launchIntent(ctx, hub = 0, module = null, requestCode = 2099)
        }
    }

    private fun mutableFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
    }
}
