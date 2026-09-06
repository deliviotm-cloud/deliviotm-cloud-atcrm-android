package tm.deliviotm.atcrm

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Сам показывает системные окна: уведомления, дежурство (игнор батареи),
 * полноэкранные пуши и автозапуск на Xiaomi/Samsung/OPPO/Vivo/Huawei.
 */
object NotifySetup {
    private const val PREFS = "atcrm"
    private const val KEY_OEM = "perm_oem_asked_v1"

    private val kicks = MutableStateFlow(0)
    val ticks = kicks.asStateFlow()
    @Volatile var ranThisProcess: Boolean = false

    fun start() {
        kicks.value = kicks.value + 1
    }

    fun notificationsOn(ctx: Context): Boolean {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun batteryOk(ctx: Context): Boolean = BatteryHelper.isIgnored(ctx)

    fun fullScreenOk(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        return try {
            ctx.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
        } catch (_: Exception) {
            true
        }
    }

    fun allOk(ctx: Context): Boolean = notificationsOn(ctx) && batteryOk(ctx) && fullScreenOk(ctx)

    internal fun oemAsked(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OEM, false)

    internal fun markOemAsked(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OEM, true).apply()
    }

    fun needsAutostartOem(): Boolean {
        val brand = Build.MANUFACTURER.lowercase()
        return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
            brand.contains("oppo") || brand.contains("realme") || brand.contains("vivo") ||
            brand.contains("iqoo") || brand.contains("huawei") || brand.contains("honor") ||
            brand.contains("oneplus")
    }

    fun notifySettingsIntent(ctx: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${ctx.packageName}"))
        }
    }

    fun batteryIntent(ctx: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${ctx.packageName}"))
    }

    fun fullScreenIntent(ctx: Context): Intent? {
        if (Build.VERSION.SDK_INT < 34) return null
        return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.parse("package:${ctx.packageName}"))
    }

    fun oemIntent(ctx: Context): Intent? {
        val pkg = ctx.packageName
        val brand = Build.MANUFACTURER.lowercase()
        val candidates = buildList {
            if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
                add(Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT))
                add(
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity",
                        ),
                    ),
                )
                add(
                    Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setComponent(
                            ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.permissions.PermissionsEditorActivity",
                            ),
                        )
                        .putExtra("extra_pkgname", pkg),
                )
                add(
                    Intent().setComponent(
                        ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                    ).putExtra("package_name", pkg).putExtra("package_label", "AT CRM"),
                )
            }
            if (brand.contains("samsung")) {
                add(
                    Intent().setComponent(
                        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                    ),
                )
                add(
                    Intent().setComponent(
                        ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                    ),
                )
            }
            if (brand.contains("huawei") || brand.contains("honor")) {
                add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                        ),
                    ),
                )
                add(
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
                        ),
                    ),
                )
            }
            if (brand.contains("oppo") || brand.contains("realme")) {
                add(
                    Intent().setComponent(
                        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    ),
                )
                add(
                    Intent().setComponent(
                        ComponentName("com.oplus.battery", "com.oplus.powermanager.startupapp.StartupAppListActivity"),
                    ),
                )
            }
            if (brand.contains("vivo") || brand.contains("iqoo")) {
                add(
                    Intent().setComponent(
                        ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                        ),
                    ),
                )
            }
            if (brand.contains("oneplus")) {
                add(
                    Intent().setComponent(
                        ComponentName(
                            "com.oneplus.security",
                            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                        ),
                    ),
                )
            }
        }
        return candidates.firstOrNull { intent ->
            ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }
}

private enum class PermStep {
    NotifyRuntime,
    NotifySettings,
    Battery,
    FullScreen,
    Oem,
}

@Composable
fun NotifySetupHost(active: Boolean) {
    val ctx = LocalContext.current
    val tick by NotifySetup.ticks.collectAsState()
    val queue = remember { mutableStateOf<List<PermStep>>(emptyList()) }
    val running = remember { mutableStateOf(false) }

    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val rest = queue.value.drop(1)
        val act = ctx as? Activity
        val needSettings = !granted && !NotifySetup.notificationsOn(ctx) && act != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !act.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
        queue.value = if (NotifySetup.notificationsOn(ctx)) {
            rest
        } else if (needSettings) {
            listOf(PermStep.NotifySettings) + rest
        } else {
            rest
        }
    }
    val pageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (queue.value.firstOrNull() == PermStep.Oem) NotifySetup.markOemAsked(ctx)
        queue.value = queue.value.drop(1)
    }

    fun buildQueue(force: Boolean): List<PermStep> {
        if (!PollWatcher.isEnabled(ctx)) return emptyList()
        val steps = ArrayList<PermStep>()
        if (!NotifySetup.notificationsOn(ctx)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                steps += PermStep.NotifyRuntime
            } else {
                steps += PermStep.NotifySettings
            }
        }
        if (!NotifySetup.batteryOk(ctx)) steps += PermStep.Battery
        if (!NotifySetup.fullScreenOk(ctx) && NotifySetup.fullScreenIntent(ctx) != null) {
            steps += PermStep.FullScreen
        }
        val oem = NotifySetup.oemIntent(ctx)
        if (oem != null && NotifySetup.needsAutostartOem() && (force || !NotifySetup.oemAsked(ctx))) {
            steps += PermStep.Oem
        }
        return steps
    }

    val lastTick = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    LaunchedEffect(active, tick) {
        if (!active) return@LaunchedEffect
        val forced = tick > lastTick.intValue && tick > 0
        lastTick.intValue = tick
        if (!forced && NotifySetup.ranThisProcess) return@LaunchedEffect
        if (!forced && NotifySetup.allOk(ctx) && (NotifySetup.oemIntent(ctx) == null || NotifySetup.oemAsked(ctx) || !NotifySetup.needsAutostartOem())) {
            NotifySetup.ranThisProcess = true
            return@LaunchedEffect
        }
        delay(if (forced) 250L else 800L)
        NotifySetup.ranThisProcess = true
        queue.value = buildQueue(force = forced)
        running.value = true
    }

    LaunchedEffect(queue.value, running.value) {
        if (!running.value) return@LaunchedEffect
        val step = queue.value.firstOrNull()
        if (step == null) {
            running.value = false
            return@LaunchedEffect
        }
        val act = ctx as? Activity
        when (step) {
            PermStep.NotifyRuntime -> {
                if (NotifySetup.notificationsOn(ctx)) {
                    queue.value = queue.value.drop(1)
                } else {
                    notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermStep.NotifySettings -> {
                if (NotifySetup.notificationsOn(ctx)) {
                    queue.value = queue.value.drop(1)
                } else {
                    pageLauncher.launch(NotifySetup.notifySettingsIntent(ctx))
                }
            }
            PermStep.Battery -> {
                if (NotifySetup.batteryOk(ctx)) {
                    queue.value = queue.value.drop(1)
                } else if (act != null) {
                    runCatching { pageLauncher.launch(NotifySetup.batteryIntent(ctx)) }
                        .onFailure {
                            BatteryHelper.requestIgnore(ctx)
                            queue.value = queue.value.drop(1)
                        }
                } else {
                    BatteryHelper.requestIgnore(ctx)
                    queue.value = queue.value.drop(1)
                }
            }
            PermStep.FullScreen -> {
                if (NotifySetup.fullScreenOk(ctx)) {
                    queue.value = queue.value.drop(1)
                } else {
                    val intent = NotifySetup.fullScreenIntent(ctx)
                    if (intent != null) pageLauncher.launch(intent) else queue.value = queue.value.drop(1)
                }
            }
            PermStep.Oem -> {
                val intent = NotifySetup.oemIntent(ctx)
                if (intent == null) {
                    NotifySetup.markOemAsked(ctx)
                    queue.value = queue.value.drop(1)
                } else {
                    runCatching { pageLauncher.launch(intent) }
                        .onFailure {
                            NotifySetup.markOemAsked(ctx)
                            queue.value = queue.value.drop(1)
                        }
                }
            }
        }
    }
}
