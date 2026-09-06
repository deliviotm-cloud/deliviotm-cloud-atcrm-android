@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tm.deliviotm.atcrm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val radius = RoundedCornerShape(12.dp)
private val chipRadius = RoundedCornerShape(999.dp)
private val bubbleMine = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
private val bubbleOther = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

internal data class NavTarget(
    val hub: Int,
    val module: String?,
    val chatPath: String? = null,
    val chatTitle: String? = null,
    val opId: String? = null,
    val opTitle: String? = null,
    val taskId: String? = null,
    val taskTitle: String? = null,
    val openSearch: Boolean = false,
)

private data class LiveAlert(
    val text: String,
    val hub: Int,
    val module: String?,
    val opId: String = "",
    val title: String = "",
    val chatPath: String = "",
    val chatTitle: String = "",
    val taskId: String = "",
    val taskTitle: String = "",
)

private data class LocalPhoto(
    val key: String,
    val bitmap: android.graphics.Bitmap?,
    val caption: String,
    val filePath: String,
    val failed: Boolean = false,
    val voice: Boolean = false,
)

class MainActivity : FragmentActivity() {
    private val navRequest = mutableStateOf<NavTarget?>(null)
    internal val lastTouchMs = mutableLongStateOf(System.currentTimeMillis())
    internal var backgroundAt = 0L

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        lastTouchMs.longValue = System.currentTimeMillis()
        return super.dispatchTouchEvent(ev)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        navRequest.value = parseNav(intent)
        val prefs = getSharedPreferences("atcrm", MODE_PRIVATE)
        if (!prefs.getBoolean("force_prod_1620", false)) {
            val cur = prefs.getString("api_base", "").orEmpty()
            if (cur.isBlank() || cur.contains("172.22") || cur.contains(":3011")) {
                prefs.edit()
                    .putString("api_base", BuildConfig.API_BASE_PROD)
                    .remove("token")
                    .putBoolean("force_prod_1620", true)
                    .apply()
            } else {
                prefs.edit().putBoolean("force_prod_1620", true).apply()
            }
        }
        if (!prefs.contains("theme")) {
            prefs.edit().putString("theme", "dark").apply()
        }
        if (!prefs.getBoolean("ui_site_v1", false)) {
            prefs.edit().putBoolean("ui_site_v1", true).apply()
        }
        NotifyHelper.ensureChannels(this)
        setContent {
            var themeMode by remember { mutableStateOf(prefs.getString("theme", "dark") ?: "dark") }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val resolvedDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
            AtAppTheme(themeMode) {
                CompositionLocalProvider(
                    LocalToggleTheme provides {
                        val next = if (resolvedDark) "light" else "dark"
                        prefs.edit().putString("theme", next).apply()
                        themeMode = next
                    },
                ) {
                Box(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
                    App(
                        initialToken = prefs.getString("token", null),
                        initialApiBase = prefs.getString("api_base", BuildConfig.API_BASE) ?: BuildConfig.API_BASE,
                        saveToken = { prefs.edit().putString("token", it).apply() },
                        saveApiBase = { prefs.edit().putString("api_base", it).apply() },
                        clearToken = { prefs.edit().remove("token").apply() },
                        pendingNav = navRequest.value,
                        onNavConsumed = { navRequest.value = null },
                        themeMode = themeMode,
                        onThemeMode = {
                            prefs.edit().putString("theme", it).apply()
                            themeMode = it
                        },
                    )
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        navRequest.value = parseNav(intent)
    }

    private fun parseNav(intent: Intent?): NavTarget? {
        val src = intent ?: return null
        val data = src.data
        if (data != null && data.scheme == "atcrm") {
            val hub = data.getQueryParameter("hub")?.toIntOrNull() ?: 0
            val search = data.getQueryParameter("search")
            return NavTarget(
                hub = hub,
                module = data.getQueryParameter("module"),
                chatPath = data.getQueryParameter("chat"),
                chatTitle = data.getQueryParameter("title"),
                opId = data.getQueryParameter("op"),
                opTitle = data.getQueryParameter("opTitle"),
                taskId = data.getQueryParameter("task"),
                taskTitle = data.getQueryParameter("taskTitle"),
                openSearch = search != null,
            )
        }
        val openSearch = !src.getStringExtra("search").isNullOrBlank() || src.getBooleanExtra("open_search", false)
        val hub = src.getIntExtra(NotifyHelper.EXTRA_HUB, -1).takeIf { it >= 0 }
            ?: src.getStringExtra(NotifyHelper.EXTRA_HUB)?.toIntOrNull()
        if (hub == null && !openSearch) return null
        return NavTarget(
            hub = hub ?: 0,
            module = src.getStringExtra(NotifyHelper.EXTRA_MODULE),
            chatPath = src.getStringExtra(NotifyHelper.EXTRA_CHAT_PATH),
            chatTitle = src.getStringExtra(NotifyHelper.EXTRA_CHAT_TITLE),
            opId = src.getStringExtra(NotifyHelper.EXTRA_OP_ID),
            opTitle = src.getStringExtra(NotifyHelper.EXTRA_OP_TITLE),
            taskId = src.getStringExtra(NotifyHelper.EXTRA_TASK_ID),
            taskTitle = src.getStringExtra(NotifyHelper.EXTRA_TASK_TITLE),
            openSearch = openSearch,
        )
    }
}

private sealed class Screen {
    data object Login : Screen()
    data object Boot : Screen()
    data object Home : Screen()
    data class List(val spec: ModuleSpec) : Screen()
    data class Object(val spec: ModuleSpec) : Screen()
    data class Detail(
        val title: String,
        val json: JSONObject,
        val back: Screen,
        val operation: Boolean = false,
        val operationId: String = "",
        val task: Boolean = false,
        val taskId: String = "",
    ) : Screen()
    data class Chat(val title: String, val path: String, val back: Screen) : Screen()
    data object Settings : Screen()
    data object Search : Screen()
    data object Lock : Screen()
    data object Outbox : Screen()
    data object ShiftLog : Screen()
}

private data class Hub(val title: String, val items: List<ModuleSpec>)
private data class HomeMetric(val label: String, val value: String, val tone: Tone = Tone.Neutral)
private data class PulseSeriesPoint(
    val dayKey: String,
    val orders: Int,
    val turnover: Double?,
)
private data class HomeSnapshot(
    val metrics: List<HomeMetric> = emptyList(),
    val pulse: List<PulseMetric> = emptyList(),
    val primary: List<JsonRow> = emptyList(),
    val secondary: List<JsonRow> = emptyList(),
    val cities: List<DashCity> = emptyList(),
    val overdueTasks: Int = 0,
    val pulseSeries: List<PulseSeriesPoint> = emptyList(),
    val downloads: List<PulseMetric> = emptyList(),
    val downloadSeries: List<DownloadDay> = emptyList(),
    val dynamics: List<DynamicsDay> = emptyList(),
    val topEstablishments: List<TopEstablishment> = emptyList(),
    val monthlyUsers: List<MonthUsers> = emptyList(),
    val rangeLabel: String = "",
    val prevRangeLabel: String = "",
    val turnoverDelta: String = "",
    val ordersDelta: String = "",
)

private enum class Tone { Neutral, Good, Warn, Danger }

private data class MenuHub(
    val id: String,
    val title: String,
    val subtitle: String,
    val tabs: List<ModuleSpec>,
)

private val MENU_HUBS = listOf(
    MenuHub(
        "sales",
        "Отдел продаж",
        "CRM заведений: воронка, импорт/экспорт Excel, SMS",
        listOf(
            ModuleSpec("sales", "Заведения", "/sales/establishments?take=50"),
            ModuleSpec("sales", "SMS рассылка", "/sms/broadcasts"),
            ModuleSpec("sales", "Магазины", "/sales/establishments?take=50"),
            ModuleSpec("sales", "Рестораны", "/sales/establishments?take=50"),
            ModuleSpec("sales", "Города", "/sales/establishments/cities"),
        ),
    ),
    MenuHub(
        "couriers",
        "Курьерская служба",
        "Справочник курьеров, ежедневное исполнение, доставки и зарплата",
        listOf(
            ModuleSpec("courierFleet", "Курьеры", "/courier-fleet/couriers"),
            ModuleSpec("courierFleet", "Доставки", "/courier-fleet/deliveries"),
            ModuleSpec("courierFleet", "Зарплата", "/courier-fleet/payroll"),
        ),
    ),
    MenuHub(
        "accounting",
        "Бухгалтерия",
        "Профиль ИП, документы, продажи, отчёты",
        listOf(
            ModuleSpec("accounting", "Профиль", "/accounting/registration", ModuleSpec.Kind.OBJECT),
            ModuleSpec("accounting", "Контрагенты", "/accounting/counterparties"),
            ModuleSpec("accounting", "Номенклатура", "/accounting/items"),
            ModuleSpec("accounting", "Склады", "/accounting/warehouses"),
            ModuleSpec("accounting", "Реализация", "/accounting/sales-invoices"),
            ModuleSpec("accounting", "Поступление", "/accounting/purchase-invoices"),
            ModuleSpec("accounting", "Касса/Банк", "/accounting/cash-orders"),
            ModuleSpec("accounting", "Отчёты", "/accounting/reports/income-statement", ModuleSpec.Kind.OBJECT),
            ModuleSpec("accounting", "Декларации", "/accounting/tax-reports"),
            ModuleSpec("accounting", "Сотрудники", "/accounting/employees"),
            ModuleSpec("accounting", "Табель", "/accounting/timesheet"),
            ModuleSpec("accounting", "Календарь", "/accounting/calendar"),
            ModuleSpec("accounting", "Ведомость", "/accounting/payroll-runs"),
            ModuleSpec("accounting", "Расчётные", "/accounting/payroll-runs"),
            ModuleSpec("accounting", "HR", "/accounting/hr-events"),
            ModuleSpec("accounting", "Акты сверки", "/reconciliation-acts/profiles"),
        ),
    ),
    MenuHub(
        "callcenter",
        "Колл-центр",
        "Delivio Call (WebRTC/SIP) и SMS-шлюз — полный функционал",
        listOf(
            ModuleSpec("callcenter", "Недавние", "/calls/logs?take=40"),
            ModuleSpec("callcenter", "Контакты", "/calls/contacts"),
            ModuleSpec("callcenter", "Записи", "/calls/recordings"),
            ModuleSpec("callcenter", "Клавиши", "/calls/keypad"),
            ModuleSpec("callcenter", "SMS", "/sms/logs?take=50"),
        ),
    ),
    MenuHub(
        "marketing",
        "Маркетинг",
        "Клиенты, рассылки, промокоды, контент",
        listOf(
            ModuleSpec("marketing", "Клиенты", "/clients"),
            ModuleSpec("marketing", "Портреты", "/reports/client-portraits"),
            ModuleSpec("marketing", "SMS", "/sms/broadcasts"),
            ModuleSpec("marketing", "Push", "/push/logs?limit=50"),
            ModuleSpec("marketing", "Календарь пушей", "/marketing/push-calendar"),
            ModuleSpec("marketing", "Контент", "/marketing/content-plan"),
            ModuleSpec("marketing", "Баннеры", "/marketing/banner-calendar/connected-establishments"),
            ModuleSpec("marketing", "Реклама", "/marketing/ads/establishments"),
            ModuleSpec("marketing", "Промокоды", "/marketing/promo-codes"),
            ModuleSpec("marketing", "QR", "/qr"),
            ModuleSpec("marketing", "Мессенджеры", "/support/admin/telegram", ModuleSpec.Kind.OBJECT),
        ),
    ),
    MenuHub(
        "settings",
        "Настройки",
        "Мой профиль, меню, шлюзы, люди и журнал",
        listOf(
            ModuleSpec("settings", "Мой профиль", "/auth/me", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "Меню", "/settings/menu", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "Общие", "/settings/general", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "Монитор", "/settings/monitor", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "SMS (шлюз)", "/sms/status", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "Почта", "/email/status", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "Push (FCM)", "/push/status", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "Магазины приложений", "/app-stores/status", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "Мессенджеры", "/support/admin/telegram", ModuleSpec.Kind.OBJECT),
            ModuleSpec("settings", "Пользователи", "/users"),
            ModuleSpec("settings", "Журнал", "/settings/audit/logs?take=40"),
            ModuleSpec("settings", "Города (CRM)", "/sales/establishments/cities?all=1"),
        ),
    ),
    MenuHub(
        "establishments",
        "Заведения",
        "Справочник заведений Delivio",
        listOf(
            ModuleSpec("establishments", "Все", "/establishments"),
            ModuleSpec("establishments", "Рестораны", "/establishments"),
            ModuleSpec("establishments", "Магазины", "/establishments"),
        ),
    ),
    MenuHub(
        "ai",
        "AI аналитика",
        "Инсайты и рекомендации на основе агрегатов CRM (как на prod)",
        listOf(
            ModuleSpec("aiRecommendations", "AI аналитика", "/ai/recommendations"),
        ),
    ),
    MenuHub(
        "mail",
        "Почта",
        "Корпоративная почта CRM",
        listOf(
            ModuleSpec("mail", "Входящие", "/email/messages?take=50"),
            ModuleSpec("mail", "Ящик", "/email/mailbox", ModuleSpec.Kind.OBJECT),
        ),
    ),
)

private fun menuPath(path: String): String = path.substringBefore("?").trimEnd('/')

private fun menuHubFor(spec: ModuleSpec): MenuHub? {
    val clean = menuPath(spec.path)
    val byPathAndTab = MENU_HUBS.find { hub ->
        hub.tabs.any { it.tab == spec.tab && menuPath(it.path) == clean }
    }
    if (byPathAndTab != null) return byPathAndTab
    return MENU_HUBS.find { hub -> hub.tabs.any { menuPath(it.path) == clean } }
}

private val HUBS = listOf(
    Hub("Работа", listOf(
        ModuleSpec("tasks", "Задачи", "/workspace/tasks"),
        ModuleSpec("operations", "Операции", "/operations"),
        ModuleSpec("problemOrders", "Проблемные заказы", "/problem-orders"),
        ModuleSpec("sales", "Отдел продаж", "/sales/establishments?take=50"),
        ModuleSpec("courierFleet", "Курьеры", "/courier-fleet/couriers"),
        ModuleSpec("accounting", "Бухгалтерия", "/accounting"),
    )),
    Hub("Связь", listOf(
        ModuleSpec("chats", "Чаты", "/workspace/chats"),
        ModuleSpec("mail", "Почта", "/email/messages?take=50"),
        ModuleSpec("support", "Поддержка", "/support/threads"),
        ModuleSpec("callcenter", "Колл-центр", "/calls/keypad"),
    )),
    Hub("Цифры", listOf(
        ModuleSpec("reports", "Отчёты", "/reports"),
        ModuleSpec("aiRecommendations", "AI аналитика", "/ai/recommendations"),
        ModuleSpec("establishments", "Заведения", "/establishments"),
        ModuleSpec("marketing", "Маркетинг", "/clients"),
    )),
    Hub("Ещё", listOf(
        ModuleSpec("settings", "Настройки", "/auth/me", ModuleSpec.Kind.OBJECT),
    )),
)

private fun specByPath(path: String): ModuleSpec? {
    val clean = path.substringBefore("?").trimEnd('/')
    if (clean.isBlank()) return null
    HUBS.flatMap { it.items }.find { it.path.substringBefore("?").trimEnd('/') == clean }?.let { return it }
    MENU_HUBS.flatMap { it.tabs }.find { it.path.substringBefore("?").trimEnd('/') == clean }?.let { return it }
    return when {
        clean.startsWith("/operations") -> ModuleSpec("operations", "Операции", "/operations")
        clean.startsWith("/workspace/chats") -> ModuleSpec("chats", "Чаты", "/workspace/chats")
        clean.startsWith("/workspace/tasks") -> ModuleSpec("tasks", "Задачи", "/workspace/tasks")
        clean.startsWith("/support/threads") -> ModuleSpec("support", "Поддержка", "/support/threads")
        else -> null
    }
}

private fun hubIndexFor(spec: ModuleSpec): Int {
    val clean = spec.path.substringBefore("?").trimEnd('/')
    val i = HUBS.indexOfFirst { hub ->
        hub.items.any { it.path.substringBefore("?").trimEnd('/') == clean }
    }
    return i.coerceAtLeast(0)
}

private fun chatMessagesPath(raw: String): String {
    val s = raw.trim()
    if (s.isBlank()) return ""
    if (s.contains("/messages")) return s
    return when {
        s.contains("/support/threads") -> "$s/messages?take=80"
        s.startsWith("/workspace/chats") -> "$s/messages"
        s.startsWith("/") -> "$s/messages"
        else -> "/workspace/chats/$s/messages"
    }
}

@Composable
private fun App(
    initialToken: String?,
    initialApiBase: String,
    saveToken: (String) -> Unit,
    saveApiBase: (String) -> Unit,
    clearToken: () -> Unit,
    pendingNav: NavTarget? = null,
    onNavConsumed: () -> Unit = {},
    themeMode: String = "light",
    onThemeMode: (String) -> Unit = {},
) {
    var apiBase by remember { mutableStateOf(initialApiBase) }
    val api = remember(apiBase) { KassaApi(apiBase) }
    var token by remember { mutableStateOf(initialToken) }
    var user by remember { mutableStateOf<AppUser?>(null) }
    val ctx = LocalContext.current
    val cache = remember { CacheStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val recents = remember { RecentsStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val prefs = remember { ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keep_awake", false)) }
    var flagSecure by remember { mutableStateOf(prefs.getBoolean("flag_secure", false)) }
    var idleMin by remember { mutableIntStateOf(prefs.getInt("idle_lock_min", 0)) }
    val outbox = remember { OutboxStore.of(ctx) }
    val online = NetWatch.rememberOnline()
    var flushMsg by remember { mutableStateOf<String?>(null) }
    var liveAlert by remember { mutableStateOf<LiveAlert?>(null) }
    val pendingOutbox = remember(outbox.revision) { outbox.count() }
    var screen by remember {
        mutableStateOf(
            when {
                initialToken.isNullOrBlank() -> Screen.Login
                BiometricHelper.isEnabled(ctx) -> Screen.Lock
                else -> Screen.Boot
            },
        )
    }
    var hub by remember { mutableIntStateOf(prefs.getInt("home_hub", 0).coerceIn(0, 3)) }
    val scope = rememberCoroutineScope()
    BackHandler(enabled = screen !is Screen.Login && screen !is Screen.Boot && screen !is Screen.Lock && screen !is Screen.Home) {
        when (val s = screen) {
            is Screen.Detail -> screen = s.back
            is Screen.Chat -> screen = s.back
            else -> screen = Screen.Home
        }
    }
    LaunchedEffect(screen, hub) {
        PollWatcher.viewingPath = when (val s = screen) {
            is Screen.Chat -> s.path
            is Screen.List -> s.spec.path
            is Screen.Object -> s.spec.path
            is Screen.Detail -> when {
                s.task -> "/workspace/tasks/${s.taskId.ifBlank { KassaApi.pick(s.json, "id", "_id") }}"
                s.operation -> "/operations"
                else -> ""
            }
            else -> ""
        }
        when (val s = screen) {
            is Screen.Chat -> NotifyHelper.cancelChats(ctx)
            is Screen.List -> {
                if (s.spec.path.contains("chats") || s.spec.path.contains("support")) NotifyHelper.cancelChats(ctx)
                if (s.spec.path.startsWith("/workspace/tasks")) NotifyHelper.cancelTasks(ctx)
                if (s.spec.path.startsWith("/operations")) NotifyHelper.cancelOps(ctx)
            }
            is Screen.Detail -> {
                if (s.task) NotifyHelper.cancelTasks(ctx)
                if (s.operation) NotifyHelper.cancelOps(ctx)
            }
            else -> {}
        }
    }
    fun openChat(title: String, path: String, back: Screen) {
        val specPath = when {
            path.contains("/support/threads") -> "/support/threads"
            else -> "/workspace/chats"
        }
        recents.push(
            JsonRow(KassaApi.chatIdFromPath(path).ifBlank { title }, title, "", JSONObject()),
            specPath,
        )
        screen = Screen.Chat(title, path, back)
    }

    DisposableEffect(api) {
        val main = Handler(Looper.getMainLooper())
        api.onUnauthorized = {
            main.post {
                if (!token.isNullOrBlank()) {
                    token = null
                    user = null
                    clearToken()
                    PollScheduler.cancel(ctx)
                    ShiftWatchService.stop(ctx)
                    NotifyHelper.clearAllBadges(ctx)
                    screen = Screen.Login
                }
            }
        }
        onDispose { api.onUnauthorized = null }
    }

    LaunchedEffect(keepAwake) {
        val win = (ctx as? android.app.Activity)?.window ?: return@LaunchedEffect
        val flag = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        if (keepAwake) win.addFlags(flag) else win.clearFlags(flag)
    }

    LaunchedEffect(flagSecure) {
        val win = (ctx as? android.app.Activity)?.window ?: return@LaunchedEffect
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (flagSecure) win.addFlags(flag) else win.clearFlags(flag)
    }

    LaunchedEffect(token, apiBase) {
        if (token.isNullOrBlank()) {
            PollScheduler.cancel(ctx)
            return@LaunchedEffect
        }
        PollScheduler.schedule(ctx)
        try {
            withContext(Dispatchers.IO) { PollWatcher.check(ctx, api, token!!, notify = false) }
        } catch (_: Exception) {
        }
        while (isActive && !token.isNullOrBlank()) {
            val sec = prefs.getInt("poll_sec", 15).coerceIn(10, 60)
            delay(if (PollWatcher.appForeground) sec * 1000L else 60_000L)
            try {
                val delta = withContext(Dispatchers.IO) { PollWatcher.check(ctx, api, token!!, notify = true) }
                if (PollWatcher.appForeground && delta.hasAlert) {
                    liveAlert = when {
                        delta.newOps.isNotEmpty() -> {
                            val row = delta.newOps.first()
                            LiveAlert(
                                text = if (delta.newOps.size == 1) "Неподтверждённый заказ: ${row.title}" else "${delta.newOps.size} неподтверждённых заказов",
                                hub = 0,
                                module = "/operations",
                                opId = row.id,
                                title = row.title,
                            )
                        }
                        delta.newChats > 0 -> LiveAlert(
                            text = buildString {
                                append("Новое в чатах")
                                if (delta.chatSample.isNotBlank()) append(": ${delta.chatSample}")
                                if (delta.chatPreview.isNotBlank()) append(" — ${delta.chatPreview}")
                            },
                            hub = 1,
                            module = if (delta.chatId.contains("/support/")) "/support/threads" else "/workspace/chats",
                            chatPath = delta.chatId,
                            chatTitle = delta.chatSample,
                        )
                        else -> LiveAlert(
                            text = delta.taskAlerts.firstOrNull().orEmpty().ifBlank { "Изменения по задачам" },
                            hub = 0,
                            module = "/workspace/tasks",
                            taskId = delta.taskSampleId,
                            taskTitle = delta.taskAlerts.firstOrNull().orEmpty(),
                        )
                    }
                    Haptics.warn(ctx)
                    ChatMedia.beep()
                }
            } catch (_: Exception) {
            }
            if (NetWatch.online(ctx)) {
                try {
                    val n = withContext(Dispatchers.IO) { outbox.flush(api, token!!) }
                    if (n > 0) flushMsg = "Отправлено из очереди: $n"
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(online, token) {
        if (!online || token.isNullOrBlank()) return@LaunchedEffect
        try {
            val n = withContext(Dispatchers.IO) { outbox.flush(api, token!!) }
            if (n > 0) flushMsg = "Отправлено из очереди: $n"
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(idleMin, token, screen) {
        if (idleMin <= 0 || token.isNullOrBlank()) return@LaunchedEffect
        while (isActive) {
            delay(8_000)
            if (!BiometricHelper.isEnabled(ctx)) return@LaunchedEffect
            if (screen is Screen.Lock || screen is Screen.Login || screen is Screen.Boot) continue
            val last = (ctx as? MainActivity)?.lastTouchMs?.longValue ?: continue
            if (System.currentTimeMillis() - last >= idleMin * 60_000L) {
                screen = Screen.Lock
            }
        }
    }

    DisposableEffect(Unit) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        if (owner == null) {
            return@DisposableEffect onDispose { }
        }
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                PollWatcher.appForeground = true
                ShiftWatchService.stop(ctx)
            }
            if (event == Lifecycle.Event.ON_STOP) {
                PollWatcher.appForeground = false
                val tok = ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE).getString("token", null)
                if (!tok.isNullOrBlank() && PollWatcher.isEnabled(ctx)) {
                    ShiftWatchService.start(ctx)
                }
            }
        }
        PollWatcher.appForeground = true
        owner.lifecycle.addObserver(obs)
        onDispose {
            PollWatcher.appForeground = false
            owner.lifecycle.removeObserver(obs)
        }
    }

    DisposableEffect(idleMin) {
        val owner = ctx as? androidx.lifecycle.LifecycleOwner
        if (owner == null) {
            return@DisposableEffect onDispose { }
        }
        val obs = LifecycleEventObserver { _, event ->
            val act = ctx as? MainActivity
            if (event == Lifecycle.Event.ON_STOP) {
                act?.backgroundAt = System.currentTimeMillis()
            }
            if (event == Lifecycle.Event.ON_START) {
                val bg = act?.backgroundAt ?: 0L
                if (idleMin > 0 && bg > 0 && BiometricHelper.isEnabled(ctx) && !token.isNullOrBlank()) {
                    if (System.currentTimeMillis() - bg >= idleMin * 60_000L) {
                        if (screen !is Screen.Lock && screen !is Screen.Login) screen = Screen.Lock
                    }
                }
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    fun can(tab: String, u: AppUser): Boolean {
        if (u.role.equals("ADMIN", true)) return true
        if (u.permissions.isEmpty()) return true
        return u.permissions.any { it == tab || it == "tab.$tab" || it.endsWith(".$tab") || it.contains(tab) }
    }

    fun open(spec: ModuleSpec) {
        val hub = menuHubFor(spec)
        screen = if (hub != null && hub.tabs.size > 1) Screen.List(spec)
        else if (spec.kind == ModuleSpec.Kind.OBJECT) Screen.Object(spec)
        else Screen.List(spec)
    }

    fun resumeLastPlace() {
        val place = LastPlaces.load(ctx) ?: return
        hub = place.hub.coerceIn(0, 3)
        prefs.edit().putInt("home_hub", hub).apply()
        when (place.kind) {
            "list", "object" -> {
                val spec = HUBS.flatMap { it.items }.find { it.path == place.path }
                    ?: ModuleSpec(place.tab.ifBlank { "operations" }, place.title.ifBlank { "Раздел" }, place.path)
                open(spec)
            }
            "chat" -> if (place.path.isNotBlank()) {
                openChat(place.title.ifBlank { "Чат" }, place.path, Screen.Home)
            }
            "search" -> screen = Screen.Search
            "outbox" -> screen = Screen.Outbox
            "shift" -> screen = Screen.ShiftLog
            "settings" -> screen = Screen.Settings
        }
    }

    LaunchedEffect(screen, hub) {
        when (val s = screen) {
            is Screen.List -> LastPlaces.save(ctx, "list", s.spec.title, s.spec.path, s.spec.tab, hub)
            is Screen.Object -> LastPlaces.save(ctx, "object", s.spec.title, s.spec.path, s.spec.tab, hub)
            is Screen.Chat -> LastPlaces.save(ctx, "chat", s.title, s.path, hub = hub)
            Screen.Search -> LastPlaces.save(ctx, "search", hub = hub)
            Screen.Outbox -> LastPlaces.save(ctx, "outbox", hub = hub)
            Screen.ShiftLog -> LastPlaces.save(ctx, "shift", hub = hub)
            Screen.Settings -> LastPlaces.save(ctx, "settings", hub = hub)
            else -> {}
        }
    }

    LaunchedEffect(screen) {
        if (screen is Screen.Boot && !token.isNullOrBlank()) {
            try {
                user = withContext(Dispatchers.IO) { api.me(token!!) }
                UserCache.save(ctx, user!!)
                screen = Screen.Home
            } catch (e: SessionExpiredException) {
                token = null
                clearToken()
                ShiftWatchService.stop(ctx)
                PollScheduler.cancel(ctx)
                screen = Screen.Login
            } catch (_: Exception) {
                val cached = UserCache.load(ctx)
                if (cached != null) {
                    user = cached
                    screen = Screen.Home
                    Toast.makeText(ctx, "Офлайн — показаны сохранённые данные", Toast.LENGTH_SHORT).show()
                } else {
                    token = null
                    clearToken()
                    screen = Screen.Login
                }
            }
        }
    }

    fun openRowFrom(spec: ModuleSpec, row: JsonRow, back: Screen) {
        val meta = detailMeta(spec, row)
        val path = detailPath(spec, row.id)
        if (path == null || !path.contains("/messages")) {
            recents.push(row, spec.path)
        }
        if (path != null && path.contains("/messages") && token != null) {
            openChat(row.title, path, back)
        } else if (path != null && token != null) {
            scope.launch {
                val obj = try {
                    withContext(Dispatchers.IO) { api.getOne(path, token!!) }
                } catch (_: Exception) {
                    row.raw
                }
                val msgs = KassaApi.messageList(obj, user?.id.orEmpty())
                screen = if (msgs != null && !KassaApi.looksLikeOperation(obj) && !KassaApi.looksLikeTask(obj)) {
                    Screen.Chat(KassaApi.displayTitle(row), path, back)
                } else {
                    Screen.Detail(
                        row.title,
                        obj,
                        back,
                        operation = meta.operation || KassaApi.looksLikeOperation(obj),
                        operationId = meta.operationId.ifBlank { KassaApi.pick(obj, "id", "externalId", "orderId") },
                        task = meta.task || KassaApi.looksLikeTask(obj),
                        taskId = meta.taskId.ifBlank { KassaApi.pick(obj, "id", "_id") },
                    )
                }
            }
        } else {
            screen = Screen.Detail(
                row.title,
                row.raw,
                back,
                operation = meta.operation,
                operationId = meta.operationId,
                task = meta.task,
                taskId = meta.taskId,
            )
        }
    }

    fun applyIncomingNav(nav: NavTarget) {
        if (nav.openSearch) {
            screen = Screen.Search
            return
        }
        val chatPath = nav.chatPath?.trim().orEmpty()
        val opId = nav.opId?.trim().orEmpty()
        val taskId = nav.taskId?.trim().orEmpty()
        val module = nav.module?.trim().orEmpty()
        when {
            chatPath.isNotBlank() -> {
                val spec = specByPath(
                    if (chatPath.contains("/support/")) "/support/threads" else "/workspace/chats",
                ) ?: ModuleSpec("chats", "Чаты", "/workspace/chats")
                hub = hubIndexFor(spec)
                openChat(nav.chatTitle?.ifBlank { "Чат" } ?: "Чат", chatMessagesPath(chatPath), Screen.List(spec))
            }
            opId.isNotBlank() -> {
                val spec = specByPath("/operations") ?: ModuleSpec("operations", "Операции", "/operations")
                hub = hubIndexFor(spec)
                openRowFrom(
                    spec,
                    JsonRow(opId, nav.opTitle?.ifBlank { opId } ?: opId, "", JSONObject().put("id", opId)),
                    Screen.List(spec),
                )
            }
            taskId.isNotBlank() -> {
                val spec = specByPath("/workspace/tasks") ?: ModuleSpec("tasks", "Задачи", "/workspace/tasks")
                hub = hubIndexFor(spec)
                openRowFrom(
                    spec,
                    JsonRow(
                        taskId,
                        nav.taskTitle?.ifBlank { taskId } ?: taskId,
                        "",
                        JSONObject().put("id", taskId).put("title", nav.taskTitle.orEmpty()),
                    ),
                    Screen.List(spec),
                )
            }
            module.isNotBlank() -> {
                val spec = specByPath(module)
                if (spec != null) {
                    hub = hubIndexFor(spec)
                    open(spec)
                } else {
                    hub = nav.hub.coerceIn(0, 3)
                }
            }
            else -> hub = nav.hub.coerceIn(0, 3)
        }
        prefs.edit().putInt("home_hub", hub).apply()
    }

    LaunchedEffect(pendingNav, user, screen) {
        val nav = pendingNav ?: return@LaunchedEffect
        if (screen is Screen.Login || screen is Screen.Boot || screen is Screen.Lock) return@LaunchedEffect
        applyIncomingNav(nav)
        onNavConsumed()
    }

    fun logoutSession() {
        token = null
        user = null
        clearToken()
        PollScheduler.cancel(ctx)
        ShiftWatchService.stop(ctx)
        NotifyHelper.clearAllBadges(ctx)
        screen = Screen.Login
    }

    val authed = screen !is Screen.Login && screen !is Screen.Boot && screen !is Screen.Lock
    NotifySetupHost(active = authed)
    val drawerModules = HUBS.flatMap { it.items }.distinctBy { it.path }.filter { m -> user?.let { can(m.tab, it) } ?: true }
    val shellPath = when (val s = screen) {
        is Screen.List -> s.spec.path
        is Screen.Object -> s.spec.path
        is Screen.Detail -> when {
            s.operation -> "/operations"
            s.task -> "/workspace/tasks"
            else -> ""
        }
        is Screen.Chat -> s.path
        Screen.Settings -> "/auth/me"
        Screen.Search -> "/search"
        else -> ""
    }

    Column(Modifier.fillMaxSize()) {
        if (authed) {
            if (liveAlert != null) {
                val alert = liveAlert!!
                LiveAlertBanner(
                    alert = alert,
                    onOpen = {
                        liveAlert = null
                        applyIncomingNav(
                            NavTarget(
                                hub = alert.hub,
                                module = alert.module,
                                chatPath = alert.chatPath.ifBlank { null },
                                chatTitle = alert.chatTitle.ifBlank { null },
                                opId = alert.opId.ifBlank { null },
                                opTitle = alert.title.ifBlank { null },
                                taskId = alert.taskId.ifBlank { null },
                                taskTitle = alert.taskTitle.ifBlank { null },
                            ),
                        )
                    },
                    onTake = if (alert.opId.isNotBlank() && token != null) {
                        {
                            liveAlert = null
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        api.postJson(KassaApi.confirmLogisticsPath(alert.opId), token!!, JSONObject())
                                    }
                                    Haptics.tap(ctx)
                                    flushMsg = "Подтвердили «${alert.title}»"
                                } catch (e: Exception) {
                                    flushMsg = e.message ?: "Не удалось подтвердить"
                                }
                            }
                        }
                    } else {
                        null
                    },
                    onDismiss = { liveAlert = null },
                )
            }
            if (screen !is Screen.Outbox && screen !is Screen.ShiftLog) {
            if (!online) {
                NetStatusBanner(pendingOutbox, onOpen = { screen = Screen.Outbox })
            } else if (pendingOutbox > 0) {
                OutboxStatusBanner(pendingOutbox, onOpen = { screen = Screen.Outbox })
            } else if (flushMsg != null) {
                ActionBanner(flushMsg!!, error = false, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
    when (val s = screen) {
        Screen.Login, Screen.Boot -> LoginPane(s is Screen.Boot, apiBase = apiBase) { password, onErr, onBusy ->
            scope.launch {
                onBusy(true)
                try {
                    val (t, u) = withContext(Dispatchers.IO) { api.login(password) }
                    token = t
                    user = u
                    saveToken(t)
                    UserCache.save(ctx, u)
                    PollScheduler.schedule(ctx)
                    HotOrdersWidget.refresh(ctx)
                    screen = Screen.Home
                } catch (e: Exception) {
                    onErr(e.message ?: "Ошибка входа")
                } finally {
                    onBusy(false)
                }
            }
        }

        Screen.Lock -> LockPane(
            apiBase = apiBase,
            onUnlock = { screen = Screen.Boot },
            onPassword = { screen = Screen.Login },
        )

        else -> CrmShell(
            user = user,
            modules = drawerModules,
            selectedDashboard = s is Screen.Home,
            selectedPath = shellPath,
            onDashboard = { screen = Screen.Home },
            onOpen = { open(it) },
            onLogout = { logoutSession() },
            onSearch = { screen = Screen.Search },
            onSettings = { screen = Screen.Settings },
            api = api,
            token = token,
        ) {
        when (s) {
        Screen.Home -> HomePane(
            user = user,
            api = api,
            token = token,
            hub = hub,
            cache = cache,
            onHub = {
                hub = it
                prefs.edit().putInt("home_hub", it).apply()
            },
            modules = drawerModules,
            onOpen = { open(it) },
            onOpenRow = { spec, row -> openRowFrom(spec, row, Screen.Home) },
            onShiftLog = { screen = Screen.ShiftLog },
            onResumePlace = { resumeLastPlace() },
        )

        Screen.ShiftLog -> ShiftLogPane(onBack = { screen = Screen.Home })

        Screen.Outbox -> OutboxPane(
            api = api,
            token = token,
            onBack = { screen = Screen.Home },
        )

        Screen.Search -> GlobalSearchPane(
            api = api,
            token = token,
            cache = cache,
            onBack = { screen = Screen.Home },
            onOpen = { hit -> openRowFrom(hit.spec, hit.row, Screen.Search) },
        )

        Screen.Settings -> SettingsPane(
            apiBase = apiBase,
            user = user,
            themeMode = themeMode,
            onThemeMode = onThemeMode,
            keepAwake = keepAwake,
            onKeepAwake = {
                keepAwake = it
                prefs.edit().putBoolean("keep_awake", it).apply()
            },
            flagSecure = flagSecure,
            onFlagSecure = {
                flagSecure = it
                prefs.edit().putBoolean("flag_secure", it).apply()
            },
            idleMin = idleMin,
            onIdleMin = {
                idleMin = it
                prefs.edit().putInt("idle_lock_min", it).apply()
            },
            cache = cache,
            onBack = { screen = Screen.Home },
            onApply = { next ->
                if (next != apiBase) {
                    apiBase = next
                    saveApiBase(next)
                    token = null
                    user = null
                    clearToken()
                    ShiftWatchService.stop(ctx)
                    PollScheduler.cancel(ctx)
                    screen = Screen.Login
                } else {
                    screen = Screen.Home
                }
            },
        )

        is Screen.List -> if (s.spec.path == "/reports" || s.spec.path.startsWith("/reports/sales-analytics")) {
            ReportsPane(
                api = api,
                token = token,
                onBack = { screen = Screen.Home },
            )
        } else if (s.spec.path.startsWith("/workspace/chats")) {
            ChatsWorkspacePane(
                api = api,
                token = token,
                me = user,
                cache = cache,
                onBack = { screen = Screen.Home },
                onOpenChat = { title, path -> openChat(title, path, s) },
            )
        } else if (s.spec.path.startsWith("/ai/")) {
            AiAnalyticsPane(
                api = api,
                token = token,
                user = user,
                initialTab = if (s.spec.path.contains("marketing-advisor")) "ask" else "reco",
                onBack = { screen = Screen.Home },
                onOpenPortraits = { open(ModuleSpec("marketing", "Портреты", "/reports/client-portraits")) },
            )
        } else if (s.spec.path.startsWith("/problem-orders")) {
            ProblemOrdersWorkspacePane(
                api = api,
                token = token,
                me = user,
                cache = cache,
                initialTab = if (s.spec.path.contains("analytics") || s.spec.path.contains("dashboard")) "dashboard" else "journal",
                onBack = { screen = Screen.Home },
                onOpenRow = { row -> openRowFrom(s.spec, row, s) },
                onOpenOp = { row ->
                    val spec = ModuleSpec("operations", "Операции", "/operations")
                    openRowFrom(spec, row, s)
                },
            )
        } else if (s.spec.path.startsWith("/operations")) {
            OperationsWorkspacePane(
                api = api,
                token = token,
                me = user,
                cache = cache,
                initialTab = when {
                    s.spec.path.contains("logistics") -> "logistics"
                    s.spec.path.contains("activity") -> "activity"
                    else -> "orders"
                },
                onBack = { screen = Screen.Home },
                onOpenOp = { row -> openRowFrom(s.spec, row, s) },
            )
        } else if (s.spec.path.startsWith("/accounting") || s.spec.path.startsWith("/reconciliation")) {
            AccountingWorkspacePane(
                api = api,
                token = token,
                me = user,
                cache = cache,
                initialTab = KassaApi.accountingTabFromPath(s.spec.path, s.spec.title),
                onBack = { screen = Screen.Home },
            )
        } else if (KassaApi.isSettingsWorkspace(s.spec.path, s.spec.tab)) {
            SettingsWorkspacePane(
                api = api,
                token = token,
                me = user,
                initialTab = KassaApi.settingsTabFromPath(s.spec.path, s.spec.title),
                onBack = { screen = Screen.Home },
                onUserUpdated = { next ->
                    user = next
                    UserCache.save(ctx, next)
                },
            )
        } else if (KassaApi.isSalesWorkspace(s.spec.path, s.spec.tab)) {
            SalesWorkspacePane(
                api = api,
                token = token,
                me = user,
                cache = cache,
                initialTab = KassaApi.salesTabFromPath(s.spec.path, s.spec.title),
                initialKind = KassaApi.salesKindFromTitle(s.spec.title),
                onBack = { screen = Screen.Home },
            )
        } else if (s.spec.path.startsWith("/workspace/tasks")) {
            TasksWorkspacePane(
                api = api,
                token = token,
                me = user,
                cache = cache,
                onBack = { screen = Screen.Home },
                onOpenTask = { row -> openRowFrom(s.spec, row, s) },
            )
        } else if (KassaApi.isCallCenterWorkspace(s.spec.path, s.spec.tab)) {
            CallCenterWorkspacePane(
                api = api,
                token = token,
                initialTab = KassaApi.callCenterTabFromPath(s.spec.path, s.spec.title),
                onBack = { screen = Screen.Home },
            )
        } else if (s.spec.path.startsWith("/sms/logs") || s.spec.path.startsWith("/sms/incoming")) {
            CallCenterSmsPane(
                api = api,
                token = token,
                onBack = { screen = Screen.Home },
            )
        } else if (s.spec.path.startsWith("/sms/broadcasts") || s.spec.path.startsWith("/sms/recipient")) {
            SmsBroadcastsPane(
                api = api,
                token = token,
                department = if (s.spec.tab.equals("sales", true)) "SALES" else "MARKETING",
                title = "SMS рассылка",
                onBack = { screen = Screen.Home },
            )
        } else JsonListPane(
            rootSpec = s.spec,
            cacheKey = if (KassaApi.cacheablePath(s.spec.path)) s.spec.path else null,
            cache = cache,
            load = {
                val t = token ?: error("Нет сессии")
                withContext(Dispatchers.IO) { api.getRows(s.spec.path, t) }
            },
            onBack = { screen = Screen.Home },
            onOpenChat = { title, path -> openChat(title, path, s) },
            onOpen = { current, row -> openRowFrom(current, row, s) },
            api = api,
            token = token,
            me = user,
        )

        is Screen.Object -> if (s.spec.path.startsWith("/ai/")) {
            AiAnalyticsPane(
                api = api,
                token = token,
                user = user,
                initialTab = if (s.spec.path.contains("marketing-advisor")) "ask" else "reco",
                onBack = { screen = Screen.Home },
                onOpenPortraits = { open(ModuleSpec("marketing", "Портреты", "/reports/client-portraits")) },
            )
        } else if (s.spec.path.startsWith("/accounting") || s.spec.path.startsWith("/reconciliation")) {
            AccountingWorkspacePane(
                api = api,
                token = token,
                me = user,
                cache = cache,
                initialTab = KassaApi.accountingTabFromPath(s.spec.path, s.spec.title),
                onBack = { screen = Screen.Home },
            )
        } else if (KassaApi.isSettingsWorkspace(s.spec.path, s.spec.tab) || s.spec.path == "/auth/me") {
            SettingsWorkspacePane(
                api = api,
                token = token,
                me = user,
                initialTab = KassaApi.settingsTabFromPath(s.spec.path, s.spec.title),
                onBack = { screen = Screen.Home },
                onUserUpdated = { next ->
                    user = next
                    UserCache.save(ctx, next)
                },
            )
        } else if (KassaApi.isSalesWorkspace(s.spec.path, s.spec.tab)) {
            SalesWorkspacePane(
                api = api,
                token = token,
                me = user,
                cache = cache,
                initialTab = KassaApi.salesTabFromPath(s.spec.path, s.spec.title),
                initialKind = KassaApi.salesKindFromTitle(s.spec.title),
                onBack = { screen = Screen.Home },
            )
        } else JsonObjectPane(
            title = s.spec.title,
            load = { api.getObject(s.spec.path, token ?: error("Нет сессии")) },
            onBack = { screen = Screen.Home },
            token = token,
            api = api,
        )

        is Screen.Detail -> if (s.task || KassaApi.looksLikeTask(s.json)) {
            TaskDetailPane(
                title = s.title,
                taskId = s.taskId.ifBlank { KassaApi.pick(s.json, "id", "_id") },
                preload = s.json,
                api = api,
                token = token,
                me = user,
                onBack = { screen = s.back },
                onUpdated = { updated -> screen = s.copy(json = updated) },
            )
        } else if (KassaApi.looksLikeProblemOrder(s.json) && !s.operation) {
            ProblemOrderDetailPane(
                title = s.title,
                preload = s.json,
                api = api,
                token = token,
                onBack = { screen = s.back },
                onOpenOp = { row ->
                    val spec = ModuleSpec("operations", "Операции", "/operations")
                    openRowFrom(spec, row, s)
                },
            )
        } else if (s.operation || KassaApi.looksLikeOperation(s.json)) {
            OperationDetailPane(
                title = s.title,
                operationId = s.operationId.ifBlank { KassaApi.pick(s.json, "id", "externalId", "orderId") },
                preload = s.json,
                api = api,
                token = token,
                me = user,
                onBack = { screen = s.back },
                onUpdated = { updated -> screen = s.copy(json = updated) },
                onOpenChat = { title, path -> openChat(title, path, s) },
            )
        } else JsonObjectPane(
            title = s.title,
            load = { s.json },
            onBack = { screen = s.back },
            preload = s.json,
            operation = s.operation || KassaApi.looksLikeOperation(s.json),
            operationId = s.operationId.ifBlank { KassaApi.pick(s.json, "id", "externalId", "orderId") },
            task = s.task || KassaApi.looksLikeTask(s.json),
            taskId = s.taskId.ifBlank { KassaApi.pick(s.json, "id", "_id") },
            token = token,
            api = api,
            onUpdated = { updated ->
                screen = s.copy(json = updated)
            },
            onOpenChat = { title, path ->
                openChat(title, path, s)
            },
        )

        is Screen.Chat -> ChatPane(
            title = s.title,
            chatId = KassaApi.chatIdFromPath(s.path),
            path = s.path,
            load = {
                val t = token ?: error("Нет сессии")
                withContext(Dispatchers.IO) { api.getObject(s.path, t) }
            },
            meId = user?.id.orEmpty(),
            api = api,
            token = token,
            cache = cache,
            onBack = { screen = s.back },
            onSend = { text ->
                val t = token ?: error("Нет сессии")
                withContext(Dispatchers.IO) {
                    api.postJson(KassaApi.chatSendPath(s.path), t, JSONObject().put("text", text))
                }
            },
        )
        Screen.Login, Screen.Boot, Screen.Lock -> {}
        }
        }
    }
        }
    }
}

private data class DetailMeta(
    val operation: Boolean = false,
    val operationId: String = "",
    val task: Boolean = false,
    val taskId: String = "",
)

private fun detailMeta(spec: ModuleSpec, row: JsonRow): DetailMeta = DetailMeta(
    operation = spec.path.startsWith("/operations"),
    operationId = if (spec.path.startsWith("/operations")) row.id else "",
    task = spec.path.startsWith("/workspace/tasks"),
    taskId = if (spec.path.startsWith("/workspace/tasks")) row.id else "",
)

private fun detailPath(spec: ModuleSpec, id: String): String? {
    if (id.isBlank()) return null
    val enc = java.net.URLEncoder.encode(id, "UTF-8")
    return when {
        spec.path.startsWith("/operations") -> "/operations/one/$enc"

        spec.path.startsWith("/sales/establishments") -> "/sales/establishments/$enc"
        spec.path.startsWith("/workspace/tasks") -> "/workspace/tasks/$enc"
        spec.path.startsWith("/workspace/chats") -> "/workspace/chats/$enc/messages"
        spec.path.startsWith("/support/threads") -> "/support/threads/$enc/messages?take=80"
        spec.path.startsWith("/email/messages") -> "/email/messages/$enc"
        spec.path.startsWith("/courier-fleet/couriers") -> "/courier-fleet/couriers/$enc"
        spec.path.startsWith("/courier-fleet/deliveries") -> "/courier-fleet/deliveries/$enc"
        spec.path.startsWith("/users") && !spec.path.contains("custom-roles") -> "/users/$enc"
        spec.path.startsWith("/accounting/employees") -> "/accounting/employees/$enc"
        spec.path.startsWith("/accounting/payroll-runs") -> "/accounting/payroll-runs/$enc"
        spec.path.startsWith("/accounting/counterparties") -> "/accounting/counterparties/$enc"
        spec.path.startsWith("/accounting/sales-invoices") -> "/accounting/sales-invoices/$enc"
        spec.path.startsWith("/accounting/purchase-invoices") -> "/accounting/purchase-invoices/$enc"
        spec.path.startsWith("/accounting/cash-orders") -> "/accounting/cash-orders/$enc"
        spec.path.startsWith("/accounting/tax-reports") -> "/accounting/tax-reports/$enc"
        spec.path.startsWith("/clients") -> "/clients/$enc"
        spec.path.startsWith("/establishments") -> "/establishments/$enc"
        spec.path.startsWith("/calls") -> "/calls/$enc"
        spec.path.startsWith("/sms/broadcasts") -> "/sms/broadcasts/$enc"
        spec.path.startsWith("/qr") -> "/qr/$enc"
        spec.path.startsWith("/reconciliation-acts/profiles") -> "/reconciliation-acts/profiles/$enc"
        else -> null
    }
}

private val LoginNavy = Color(0xFF070B18)
private val LoginCard = Color(0xF2141828)

private data class LoginCopy(
    val title: String,
    val subtitle: String,
    val password: String,
    val placeholder: String,
    val submit: String,
    val submitting: String,
    val badge: String,
    val foot: String,
)

private fun loginCopy(tk: Boolean) = if (tk) LoginCopy(
    title = "AT CRM-e giriş",
    subtitle = "Delivio korporatiw dolandyryş paneli",
    password = "Parol",
    placeholder = "Kassa ulanyjysynyň paroly",
    submit = "Girmek",
    submitting = "Girilýär…",
    badge = "AT CRM esasy interfeýsi",
    foot = "Öňki wersiýa — /legacy/",
) else LoginCopy(
    title = "Вход в AT CRM",
    subtitle = "Корпоративная панель управления Delivio",
    password = "Пароль",
    placeholder = "Пароль пользователя кассы",
    submit = "Войти",
    submitting = "Вход…",
    badge = "Основной интерфейс AT CRM",
    foot = "Предыдущая версия — /legacy/",
)

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AtColors.text,
    unfocusedTextColor = AtColors.text,
    focusedBorderColor = AtColors.accent.copy(alpha = 0.85f),
    unfocusedBorderColor = AtColors.stroke,
    focusedContainerColor = AtColors.glass,
    unfocusedContainerColor = AtColors.glass,
    focusedLabelColor = AtColors.muted,
    unfocusedLabelColor = AtColors.muted,
    focusedPlaceholderColor = AtColors.muted,
    unfocusedPlaceholderColor = AtColors.muted,
    cursorColor = AtColors.accent,
)

@Composable
private fun LoginLangSwitch(tk: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AtColors.glass)
            .border(1.dp, AtColors.stroke, RoundedCornerShape(8.dp)),
    ) {
        listOf(false to "RU", true to "TK").forEach { (value, label) ->
            val on = tk == value
            Text(
                label,
                color = if (on) AtColors.text else AtColors.muted,
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) AtColors.accentSoft else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun LoginPane(loading: Boolean, apiBase: String, onLogin: (String, (String) -> Unit, (Boolean) -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE) }
    var tk by remember { mutableStateOf(prefs.getBoolean("login_tk", false)) }
    val copy = loginCopy(tk)
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val dark = LocalAtPalette.current.isDark
    Column(
        Modifier
            .fillMaxSize()
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0xFF050816), LoginNavy, Color(0xFF0A1228)))
                else Brush.verticalGradient(listOf(AtColors.bgDeep, AtColors.bgBase)),
            )
            .statusBarsPadding()
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (dark) LoginCard else AtColors.panel)
                .border(1.dp, AtColors.stroke, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(R.drawable.atcrm_mark),
                    contentDescription = "AT CRM — вход",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(104.dp),
                    contentScale = ContentScale.Fit,
                )
                Row(
                    Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SiteThemeToggle()
                    LoginLangSwitch(tk) {
                        tk = it
                        prefs.edit().putBoolean("login_tk", it).apply()
                    }
                }
            }
            Text(copy.title, color = AtColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text(copy.subtitle, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Text(envLabel(apiBase), color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .clip(chipRadius)
                    .background(if (dark) Color(0x2216A34A) else Color(0x1A16A34A))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("●  ", color = Color(0xFF16A34A), fontSize = 10.sp)
                Text(copy.badge, color = if (dark) Color(0xFFBBF7D0) else Color(0xFF166534), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(18.dp))
            if (loading) {
                CircularProgressIndicator(color = AtColors.accent)
                return@Column
            }
            Text(copy.password, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text(copy.placeholder) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = loginFieldColors(),
                shape = RoundedCornerShape(12.dp),
            )
            if (error != null) {
                Text(error!!, color = AtColors.danger, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), fontSize = 14.sp)
            }
            Button(
                onClick = { error = null; onLogin(password, { error = it }, { busy = it }) },
                enabled = !busy && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AtColors.accent, disabledContainerColor = AtColors.accent.copy(alpha = 0.35f)),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (busy) copy.submitting else copy.submit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(copy.foot, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun LockPane(apiBase: String, onUnlock: () -> Unit, onPassword: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    fun ask() {
        BiometricHelper.prompt(
            activity,
            onSuccess = onUnlock,
            onFail = { error = it },
        )
    }
    LaunchedEffect(Unit) { ask() }
    val dark = LocalAtPalette.current.isDark
    Column(
        Modifier
            .fillMaxSize()
            .background(
                if (dark) Brush.verticalGradient(listOf(Color(0xFF050816), LoginNavy, Color(0xFF0A1228)))
                else Brush.verticalGradient(listOf(AtColors.bgDeep, AtColors.bgBase)),
            )
            .statusBarsPadding()
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (dark) LoginCard else AtColors.panel)
                .border(1.dp, AtColors.stroke, RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                SiteThemeToggle()
            }
            Image(
                painter = painterResource(R.drawable.atcrm_mark),
                contentDescription = "AT CRM",
                modifier = Modifier.size(104.dp),
                contentScale = ContentScale.Fit,
            )
            Text("Сессия сохранена — можно продолжать работу", color = AtColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("Разблокируйте отпечатком, лицом или PIN", color = AtColors.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            Box(
                Modifier.clip(chipRadius).background(if (dark) Color(0x2216A34A) else Color(0x1A16A34A)).padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("●  Основной интерфейс AT CRM", color = if (dark) Color(0xFFBBF7D0) else Color(0xFF166534), fontSize = 12.sp)
            }
            if (error != null) {
                Text(error!!, color = AtColors.danger, modifier = Modifier.padding(top = 16.dp), fontSize = 14.sp)
            }
            Button(
                onClick = { error = null; ask() },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AtColors.accent),
            ) {
                Text("Разблокировать", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            TextButton(onClick = onPassword, modifier = Modifier.padding(top = 4.dp)) {
                Text("Войти паролем", color = AtColors.muted)
            }
        }
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AtColors.text,
    unfocusedTextColor = AtColors.text,
    focusedBorderColor = AtColors.accent.copy(alpha = 0.55f),
    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedContainerColor = AtColors.glass,
    unfocusedContainerColor = AtColors.glass,
    focusedLabelColor = AtColors.muted,
    unfocusedLabelColor = AtColors.muted,
    focusedPlaceholderColor = AtColors.muted,
    unfocusedPlaceholderColor = AtColors.muted,
    cursorColor = AtColors.accent,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePane(
    user: AppUser?,
    api: KassaApi,
    token: String?,
    hub: Int,
    cache: CacheStore,
    onHub: (Int) -> Unit,
    modules: List<ModuleSpec>,
    onOpen: (ModuleSpec) -> Unit,
    onOpenRow: (ModuleSpec, JsonRow) -> Unit,
    onShiftLog: () -> Unit = {},
    onResumePlace: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var pinTick by remember { mutableIntStateOf(0) }
    val pins = remember { PinsStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val drafts = remember { DraftsStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val recents = remember { RecentsStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val pinIds = remember(pinTick) { pins.ids() }
    var snapshot by remember { mutableStateOf<HomeSnapshot?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var cachedAt by remember { mutableStateOf(0L) }
    var refreshing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf("month") }
    var customFrom by remember { mutableStateOf("") }
    var customTo by remember { mutableStateOf("") }
    var cityKey by remember { mutableStateOf("") }
    var dynamicsMode by remember { mutableStateOf("turnover") }
    var dlPeriod by remember { mutableStateOf("12m") }
    var dlCustomFrom by remember { mutableStateOf("") }
    var dlCustomTo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var actingId by remember { mutableStateOf<String?>(null) }
    var actionErr by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var pendingProblem by remember { mutableStateOf<JsonRow?>(null) }
    var pendingDone by remember { mutableStateOf<JsonRow?>(null) }
    val pullState = rememberPullToRefreshState()
    val recentItems = remember(tick, snapshot, hub) { recents.list() }
    val lastPlace = remember(tick) { LastPlaces.load(ctx) }
    val workBadge = remember(tick, snapshot, cache.revision) { HubBadges.work(cache) }
    val commsBadge = remember(tick, snapshot, cache.revision) { HubBadges.comms(cache) }
    val shiftLog = remember { ShiftLogStore.of(ctx) }
    val shiftStats = remember(shiftLog.revision, actionMsg) { shiftLog.stats() }
    val undo = remember(shiftLog.revision, actionMsg) { shiftLog.lastUndo() }
    val scrollStore = remember { ScrollStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val homeScrollKey = "home_$hub"
    val homeSaved = remember(homeScrollKey) { scrollStore.get(homeScrollKey) }
    val homeListState = remember(homeScrollKey) {
        androidx.compose.foundation.lazy.LazyListState(homeSaved.first, homeSaved.second)
    }
    DisposableEffect(homeScrollKey) {
        onDispose {
            scrollStore.put(homeScrollKey, homeListState.firstVisibleItemIndex, homeListState.firstVisibleItemScrollOffset)
        }
    }

    fun undoLast() {
        val u = shiftLog.lastUndo() ?: return
        val t = token ?: return
        scope.launch {
            actingId = u.rowId
            actionErr = null
            try {
                withContext(Dispatchers.IO) {
                    enqueueOrPatch(ctx, api, t, u.path, u.prevStatus, "Отмена", prevStatus = u.newStatus, title = u.title, rowId = u.rowId, log = false)
                }
                snapshot = snapshot?.let { snap ->
                    snap.copy(
                        primary = snap.primary.map { if (it.id == u.rowId) KassaApi.withStatus(it, u.prevStatus) else it },
                        secondary = snap.secondary.map { if (it.id == u.rowId) KassaApi.withStatus(it, u.prevStatus) else it },
                    )
                }
                shiftLog.clearUndo()
                actionMsg = "Вернули «${u.title}»"
                Haptics.tap(ctx)
            } catch (e: Exception) {
                actionErr = e.message ?: "Не удалось отменить"
            } finally {
                actingId = null
            }
        }
    }

    fun confirmHome(row: JsonRow) {
        val id = KassaApi.confirmTargetId(row)
        val t = token ?: return
        if (id.isBlank()) return
        scope.launch {
            actingId = row.id
            actionErr = null
            actionMsg = null
            try {
                withContext(Dispatchers.IO) {
                    api.postJson(KassaApi.confirmLogisticsPath(id), t, JSONObject())
                }
                Haptics.tap(ctx)
                snapshot = snapshot?.let { snap ->
                    snap.copy(
                        primary = snap.primary.map { if (it.id == row.id) KassaApi.withStatus(it, "CREATED") else it },
                        secondary = snap.secondary.map { if (it.id == row.id) KassaApi.withStatus(it, "CREATED") else it },
                    )
                }
                actionMsg = "Подтверждено"
                ShiftLogStore.of(ctx).record("take", row.title, KassaApi.confirmLogisticsPath(id), KassaApi.pick(row.raw, "status"), "CREATED", row.id, undoable = false)
            } catch (e: Exception) {
                Haptics.warn(ctx)
                actionErr = e.message ?: "Не удалось подтвердить"
            } finally {
                actingId = null
            }
        }
    }

    fun fileProblemHome(row: JsonRow, comment: String) {
        val t = token ?: return
        scope.launch {
            actingId = row.id
            actionErr = null
            actionMsg = null
            try {
                withContext(Dispatchers.IO) {
                    api.postJson("/problem-orders", t, KassaApi.problemOrderBody(row, comment))
                }
                Haptics.tap(ctx)
                actionMsg = "Заказ в проблемных"
                ShiftLogStore.of(ctx).record("problem", row.title, "/problem-orders", KassaApi.pick(row.raw, "status"), "PROBLEM", row.id, undoable = false)
            } catch (e: Exception) {
                Haptics.warn(ctx)
                actionErr = e.message ?: "Не удалось занести проблему"
            } finally {
                actingId = null
            }
        }
    }
    fun patchHome(row: JsonRow, path: String, label: String, status: String, comment: String = "") {
        val id = row.id
        val t = token ?: return
        if (id.isBlank()) return
        scope.launch {
            actingId = id
            actionErr = null
            actionMsg = null
            try {
                val msg = withContext(Dispatchers.IO) {
                    enqueueOrPatch(
                        ctx, api, t, path, status, label,
                        prevStatus = KassaApi.pick(row.raw, "status", "state"),
                        title = row.title,
                        rowId = id,
                        comment = comment,
                    )
                }
                Haptics.tap(ctx)
                snapshot = snapshot?.let { snap ->
                    snap.copy(
                        primary = snap.primary.map { if (it.id == id) KassaApi.withStatus(it, status) else it },
                        secondary = snap.secondary.map { if (it.id == id) KassaApi.withStatus(it, status) else it },
                    )
                }
                actionMsg = msg
            } catch (e: Exception) {
                Haptics.warn(ctx)
                actionErr = e.message ?: "Не удалось обновить статус"
            } finally {
                actingId = null
            }
        }
    }

    LaunchedEffect(token, tick, period, cityKey, customFrom, customTo) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        err = null
        offline = false
        if (snapshot != null) refreshing = true
        try {
            val loaded = withContext(Dispatchers.IO) {
                buildHomeSnapshot(api, token, 0, period, cityKey, customFrom, customTo)
            }
            snapshot = loaded
            cache.putRows("home_0", loaded.primary + loaded.secondary)
            cache.putRows(KassaApi.opsCacheKey(), loaded.primary)
            HotOrdersWidget.refresh(ctx, loaded.primary)
        } catch (e: Exception) {
            val cached = cache.getRows("home_0") ?: cache.getRows("home_$hub")
            if (cached != null) {
                snapshot = HomeSnapshot(
                    metrics = listOf(
                        HomeMetric("Кэш", cached.size.toString(), Tone.Warn),
                        HomeMetric("Хаб", HUBS[hub].title, Tone.Neutral),
                    ),
                    primary = cached.take(4),
                    secondary = cached.drop(4).take(4),
                )
                cachedAt = cache.cachedAt("home_$hub")
                offline = true
                err = null
            } else {
                err = e.message
            }
        } finally {
            refreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { tick++ },
        state = pullState,
        modifier = Modifier.fillMaxSize().background(AtColors.bgDeep),
    ) {
            LazyColumn(
                state = homeListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (offline) {
                    item { OfflineBanner(cachedAt = cachedAt) }
                }
                if (actionMsg != null) {
                    item {
                        ActionBanner(
                            actionMsg!!,
                            error = false,
                            action = if (undo != null) "Отменить" else null,
                            onAction = if (undo != null) ({ undoLast() }) else null,
                        )
                    }
                }
                if (actionErr != null) {
                    item { ActionBanner(actionErr!!, error = true) }
                }
                item {
                    SiteSectionHead(
                        title = "Dashboard",
                        hint = "Те же KPI, динамика, топ точек и скачивания, что на сайте. Листайте вниз.",
                    )
                }
                item {
                    if (err != null) {
                        ErrorBlock(err!!, onRetry = { tick++ })
                    } else if (snapshot == null) {
                        LoadingCard()
                    } else {
                        AnalyticsBody(
                            snap = snapshot!!,
                            period = period,
                            customFrom = customFrom,
                            customTo = customTo,
                            cityKey = cityKey,
                            dlPeriod = dlPeriod,
                            dlCustomFrom = dlCustomFrom,
                            dlCustomTo = dlCustomTo,
                            dynamicsMode = dynamicsMode,
                            onPeriod = { period = it },
                            onPeriodCustom = { from, to ->
                                customFrom = from
                                customTo = to
                                period = "custom"
                            },
                            onCity = { cityKey = it },
                            onDlPeriod = { dlPeriod = it },
                            onDlPeriodCustom = { from, to ->
                                dlCustomFrom = from
                                dlCustomTo = to
                                dlPeriod = "custom"
                            },
                            onDynamicsMode = { dynamicsMode = it },
                            showWork = true,
                            modules = modules,
                            onOpen = onOpen,
                            onOpenRow = onOpenRow,
                        )
                    }
                }
            }
        }
    pendingProblem?.let { row ->
        ConfirmProblemDialog(
            title = row.title.ifBlank { "этот заказ" },
            onConfirm = { note ->
                fileProblemHome(row, note)
                pendingProblem = null
            },
            onDismiss = { pendingProblem = null },
        )
    }
    pendingDone?.let { row ->
        ConfirmDoneDialog(
            title = row.title.ifBlank { "этот заказ" },
            onConfirm = {
                patchHome(row, KassaApi.operationDetailPath(row.id), "Готово", "DONE")
                pendingDone = null
            },
            onDismiss = { pendingDone = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsPane(
    api: KassaApi,
    token: String?,
    onBack: () -> Unit,
) {
    var snapshot by remember { mutableStateOf<HomeSnapshot?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf("month") }
    var cityKey by remember { mutableStateOf("") }
    var dynamicsMode by remember { mutableStateOf("turnover") }
    var dlPeriod by remember { mutableStateOf("month") }
    var reportTab by remember { mutableStateOf("summary") }
    var crmRows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var cancelRows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(token, tick, period, cityKey, reportTab) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        err = null
        if (snapshot != null) refreshing = true
        try {
            snapshot = withContext(Dispatchers.IO) { buildHomeSnapshot(api, token, 0, period, cityKey) }
            if (reportTab == "cancelled" || reportTab == "orders") {
                cancelRows = withContext(Dispatchers.IO) {
                    runCatching { api.getRows(KassaApi.operationsListPath("CANCELLED", 80), token) }.getOrDefault(emptyList())
                }
            }
            if (reportTab == "salesCrm") {
                val (from, to) = KassaApi.dateRange(period)
                val q = "dateFrom=$from&dateTo=$to"
                crmRows = withContext(Dispatchers.IO) {
                    runCatching { api.getRows("/reports/sales-crm-establishments?$q", token) }.getOrDefault(emptyList())
                }
            }
        } catch (e: Exception) {
            err = e.message
        } finally {
            refreshing = false
        }
    }
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Отчёты", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SiteSectionHead(
                        title = "Отчёты",
                        hint = "Сводка, заказы, отмены, сравнение и CRM продаж — как на сайте",
                    )
                }
                item {
                    SiteSegmented(
                        value = reportTab,
                        items = listOf(
                            "summary" to "Сводка",
                            "orders" to "Заказы",
                            "cancelled" to "Отмены",
                            "compare" to "Сравнение",
                            "salesCrm" to "CRM продаж",
                        ),
                        onChange = { reportTab = it },
                    )
                }
                item {
                    if (err != null) {
                        ErrorBlock(err!!, onRetry = { tick++ })
                    } else if (snapshot == null) {
                        LoadingCard()
                    } else when (reportTab) {
                        "orders" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Операции за период", color = AtColors.muted, fontSize = 13.sp)
                            snapshot!!.primary.take(40).forEach { row ->
                                GenericRowCard(row = row, onOpen = {})
                            }
                            if (snapshot!!.primary.isEmpty()) {
                                EmptyStateCard("Нет заказов", "За выбранный период операций нет.")
                            }
                        }
                        "cancelled" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Отменённые заказы", color = AtColors.muted, fontSize = 13.sp)
                            cancelRows.take(40).forEach { row ->
                                GenericRowCard(row = row, onOpen = {})
                            }
                            if (cancelRows.isEmpty()) {
                                EmptyStateCard("Нет отмен", "Отменённых заказов за окно операций нет.")
                            }
                        }
                        "salesCrm" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Заведения CRM за ${snapshot!!.rangeLabel}", color = AtColors.muted, fontSize = 13.sp)
                            crmRows.take(60).forEach { row ->
                                EstablishmentCard(row = row, onOpen = {})
                            }
                            if (crmRows.isEmpty()) {
                                EmptyStateCard("Нет строк CRM", "Нет агрегата sales-crm за период.")
                            }
                        }
                        else -> AnalyticsBody(
                            snap = snapshot!!,
                            period = period,
                            customFrom = "",
                            customTo = "",
                            cityKey = cityKey,
                            dlPeriod = dlPeriod,
                            dlCustomFrom = "",
                            dlCustomTo = "",
                            dynamicsMode = dynamicsMode,
                            onPeriod = { period = it },
                            onPeriodCustom = { _, _ -> },
                            onCity = { cityKey = it },
                            onDlPeriod = { dlPeriod = it },
                            onDlPeriodCustom = { _, _ -> },
                            onDynamicsMode = { dynamicsMode = it },
                            showWork = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsBody(
    snap: HomeSnapshot,
    period: String,
    customFrom: String,
    customTo: String,
    cityKey: String,
    dlPeriod: String,
    dlCustomFrom: String,
    dlCustomTo: String,
    dynamicsMode: String,
    onPeriod: (String) -> Unit,
    onPeriodCustom: (String, String) -> Unit,
    onCity: (String) -> Unit,
    onDlPeriod: (String) -> Unit,
    onDlPeriodCustom: (String, String) -> Unit,
    onDynamicsMode: (String) -> Unit,
    showWork: Boolean = false,
    modules: List<ModuleSpec> = emptyList(),
    onOpen: (ModuleSpec) -> Unit = {},
    onOpenRow: (ModuleSpec, JsonRow) -> Unit = { _, _ -> },
) {
    val pulse = snap.pulse.ifEmpty {
        snap.metrics.map { PulseMetric(it.label, it.value, "сейчас") }
    }
    val cities = snap.cities.ifEmpty {
        listOf(DashCity("", "Все города"), DashCity("ashgabat", "Ашхабад"), DashCity("mary", "Мары"))
    }
    val (dlFrom, dlTo) = KassaApi.dateRange(dlPeriod, customFrom = dlCustomFrom, customTo = dlCustomTo)
    val (dlPrevFrom, dlPrevTo) = KassaApi.prevDateRange(dlPeriod, dlCustomFrom, dlCustomTo)
    val dlSlice = KassaApi.sliceDownloads(snap.downloadSeries, dlFrom, dlTo).ifEmpty { snap.downloadSeries }
    val dlPrevSlice = KassaApi.sliceDownloads(snap.downloadSeries, dlPrevFrom, dlPrevTo)
    val (dlApple, dlGoogle, dlTotal) = if (dlSlice.isNotEmpty()) KassaApi.downloadTotals(dlSlice) else Triple(0.0, 0.0, 0.0)
    val dlRows = if (dlSlice.isNotEmpty()) {
        val (pa, pg, pt) = if (dlPrevSlice.isNotEmpty()) KassaApi.downloadTotals(dlPrevSlice) else Triple(0.0, 0.0, 0.0)
        listOf(
            PulseMetric("App Store", KassaApi.prettyNumber(dlApple.toString()).ifBlank { "0" }, "", "", KassaApi.vsPrev(dlApple, pa), tint = "ink"),
            PulseMetric("Google Play", KassaApi.prettyNumber(dlGoogle.toString()).ifBlank { "0" }, "", "▶", KassaApi.vsPrev(dlGoogle, pg), tint = "mint"),
            PulseMetric("Всего", KassaApi.prettyNumber(dlTotal.toString()).ifBlank { "0" }, "", "Σ", KassaApi.vsPrev(dlTotal, pt), tint = "sky"),
        )
    } else snap.downloads
    val tasksMod = modules.find { it.path.startsWith("/workspace/tasks") }
    val problemsMod = modules.find { it.path.startsWith("/problem-orders") }
    val opsMod = modules.find { KassaApi.isOperationsList(it.path) }
    val chatsMod = modules.find { it.path.startsWith("/workspace/chats") }
    val reportsMod = modules.find { it.path.substringBefore("?") == "/reports" || it.path.startsWith("/reports") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SiteFilterSelect(
            value = cityKey,
            items = cities.map { it.key to it.name },
            onChange = onCity,
            label = "Город",
            modifier = Modifier.fillMaxWidth(),
        )
        SitePeriodDropdown(
            preset = period,
            rangeLabel = snap.rangeLabel.ifBlank { KassaApi.periodRangeLabel(period, customFrom, customTo) },
            presets = KassaApi.dashPeriodPresets,
            customFrom = customFrom,
            customTo = customTo,
            onSelectPreset = onPeriod,
            onApplyCustom = onPeriodCustom,
        )
        Text(
            "Полный отчёт · ${snap.rangeLabel.ifBlank { KassaApi.periodRangeLabel(period, customFrom, customTo) }}",
            color = AtColors.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(enabled = reportsMod != null) { reportsMod?.let(onOpen) },
        )
        pulse.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { metric -> SitePulseCard(metric, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (dlRows.isNotEmpty() || snap.downloadSeries.isNotEmpty()) {
            DownloadsCard(
                rows = dlRows,
                series = dlSlice.ifEmpty { snap.downloadSeries },
                period = dlPeriod,
                customFrom = dlCustomFrom,
                customTo = dlCustomTo,
                rangeLabel = KassaApi.periodRangeLabel(dlPeriod, dlCustomFrom, dlCustomTo),
                totalDelta = dlRows.lastOrNull()?.delta.orEmpty(),
                onPeriod = onDlPeriod,
                onPeriodCustom = onDlPeriodCustom,
            )
        }
        if (snap.monthlyUsers.isNotEmpty()) RegistrationsCard(snap.monthlyUsers)
        if (snap.dynamics.isNotEmpty()) {
            DynamicsCard(
                days = snap.dynamics,
                mode = dynamicsMode,
                delta = if (dynamicsMode == "turnover") snap.turnoverDelta else snap.ordersDelta,
                currentRange = snap.rangeLabel,
                previousRange = snap.prevRangeLabel,
                onMode = onDynamicsMode,
            )
        } else if (snap.pulseSeries.isNotEmpty()) {
            PulseOrdersChart(series = snap.pulseSeries, modifier = Modifier.fillMaxWidth())
        }
        if (snap.topEstablishments.isNotEmpty()) {
            TopEstablishmentsCard(rows = snap.topEstablishments, mode = dynamicsMode, onMode = onDynamicsMode)
        }
        if (showWork) {
            AttentionCard(
                overdue = snap.overdueTasks,
                problems = snap.primary.take(3),
                onOpenTasks = { tasksMod?.let(onOpen) },
                onOpenProblems = { problemsMod?.let(onOpen) },
                onOpenRow = { row ->
                    problemsMod?.let { onOpenRow(it, row) } ?: onOpenRow(opsMod ?: modules.first(), row)
                },
            )
            Text("Быстрый вход", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            listOfNotNull(tasksMod, opsMod, problemsMod, chatsMod).chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { spec ->
                        SiteOpenTile(
                            title = spec.title,
                            purpose = when {
                                spec.path.startsWith("/workspace/tasks") -> "Срок, канбан, проверка"
                                spec.path.startsWith("/problem-orders") -> "Задержки по точкам"
                                spec.path.startsWith("/workspace/chats") -> "Переписка с командой"
                                else -> "Заказы за период"
                            },
                            onClick = { onOpen(spec) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PulseOrdersChart(
    series: List<PulseSeriesPoint>,
    modifier: Modifier = Modifier,
) {
    val accent = AtColors.accent
    val stroke = AtColors.stroke
    val maxOrders = (series.maxOfOrNull { it.orders } ?: 0).coerceAtLeast(1)
    val n = series.size.coerceAtLeast(2)

    Column(modifier) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        ) {
            val w = size.width
            val h = size.height

            // Using raw px values inside Canvas avoids relying on Dp.toPx extensions.
            val padLeft = 10f
            val padRight = 8f
            val padTop = 10f
            val padBottom = 18f

            val chartW = (w - padLeft - padRight).coerceAtLeast(1f)
            val chartH = (h - padTop - padBottom).coerceAtLeast(1f)
            val baseline = padTop + chartH

            fun x(i: Int): Float = padLeft + i * chartW / (n - 1).coerceAtLeast(1)
            fun y(v: Int): Float =
                baseline - (v.coerceAtLeast(0) / maxOrders.toFloat()) * chartH

            // grid
            val gridSteps = 3
            for (s in 0..gridSteps) {
                val t = s / gridSteps.toFloat()
                val yy = baseline - t * chartH
                drawLine(
                    color = stroke.copy(alpha = 0.20f),
                    start = androidx.compose.ui.geometry.Offset(padLeft, yy),
                    end = androidx.compose.ui.geometry.Offset(w - padRight, yy),
                    strokeWidth = 1f,
                )
            }

            // line
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0 until n) {
                val v = series.getOrNull(i)?.orders ?: 0
                val xx = x(i)
                val yy = y(v)
                if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
            }
            drawPath(
                path = path,
                color = accent,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
            )

            // points
            for (i in 0 until n) {
                val v = series.getOrNull(i)?.orders ?: 0
                val xx = x(i)
                val yy = y(v)
                drawCircle(
                    color = accent,
                    radius = 4f,
                    center = androidx.compose.ui.geometry.Offset(xx, yy),
                )
            }
        }

        val idxs = listOf(0, (n - 1) / 2, n - 1).distinct()
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            idxs.forEach { i ->
                val dk = series.getOrNull(i)?.dayKey.orEmpty()
                val label = dk.takeIf { it.contains('-') }?.split('-')?.let { p ->
                    if (p.size == 3) "${p[2]}.${p[1]}" else dk
                } ?: dk
                Text(label, color = AtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DownloadsCard(
    rows: List<PulseMetric>,
    series: List<DownloadDay>,
    period: String,
    customFrom: String,
    customTo: String,
    rangeLabel: String,
    totalDelta: String,
    onPeriod: (String) -> Unit,
    onPeriodCustom: (String, String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp)) {
        Text("Скачивания приложения", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("App Store + Google Play · Delivio", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(10.dp))
        SitePeriodDropdown(
            preset = period,
            rangeLabel = rangeLabel,
            presets = KassaApi.dashDownloadPresets,
            customFrom = customFrom,
            customTo = customTo,
            onSelectPreset = onPeriod,
            onApplyCustom = onPeriodCustom,
        )
        if (totalDelta.isNotBlank()) {
            Text(
                totalDelta,
                color = deltaColor(totalDelta),
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Text(rangeLabel, color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.icon.ifBlank { "•" }, fontSize = 16.sp, modifier = Modifier.width(24.dp))
                Text(row.label, color = AtColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(row.value, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (row.delta.isNotBlank()) {
                    Text(
                        row.delta,
                        color = deltaColor(row.delta),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp).width(48.dp),
                    )
                }
            }
        }
        if (series.size >= 2) {
            Spacer(Modifier.height(8.dp))
            DownloadSeriesChart(series)
        }
    }
}

@Composable
private fun DownloadSeriesChart(series: List<DownloadDay>) {
    val accent = AtColors.accent
    val play = AtColors.success
    val ink = AtColors.text
    val stroke = AtColors.stroke
    val maxV = series.maxOf { maxOf(it.apple, it.google, it.total) }.coerceAtLeast(1.0)
    val n = series.size.coerceAtLeast(2)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val padL = 8f
        val padR = 8f
        val padT = 10f
        val padB = 8f
        val w = (size.width - padL - padR).coerceAtLeast(1f)
        val h = (size.height - padT - padB).coerceAtLeast(1f)
        fun x(i: Int) = padL + i * w / (n - 1).coerceAtLeast(1)
        fun y(v: Double) = padT + h - (v.coerceAtLeast(0.0) / maxV).toFloat() * h
        for (s in 0..3) {
            val yy = padT + s * h / 3f
            drawLine(stroke.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(padL, yy), androidx.compose.ui.geometry.Offset(size.width - padR, yy), 1f)
        }
        fun line(color: Color, pick: (DownloadDay) -> Double) {
            val path = androidx.compose.ui.graphics.Path()
            series.forEachIndexed { i, d ->
                val xx = x(i)
                val yy = y(pick(d))
                if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
        line(ink) { it.apple }
        line(play) { it.google }
        line(accent) { it.total }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot("App Store", ink)
        LegendDot("Google Play", play)
        LegendDot("Всего", accent)
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun DynamicsCard(
    days: List<DynamicsDay>,
    mode: String,
    delta: String,
    currentRange: String,
    previousRange: String,
    onMode: (String) -> Unit,
) {
    val currentColor = AtColors.accent
    val prevColor = AtColors.warning
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Динамика", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (delta.isNotBlank()) {
                Text("$delta к прошлому периоду", color = deltaColor(delta), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(10.dp))
        SiteSegmented(
            value = mode,
            items = listOf("turnover" to "Оборот без доставки", "orders" to "Заказы"),
            onChange = onMode,
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot("Текущий · $currentRange", currentColor)
        }
        LegendDot("Прошлый · $previousRange", prevColor)
        val maxV = days.maxOf {
            if (mode == "turnover") maxOf(it.currentTurnover, it.previousTurnover) else maxOf(it.currentOrders, it.previousOrders)
        }.coerceAtLeast(1.0)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(days, key = { it.key }) { day ->
                val cur = if (mode == "turnover") day.currentTurnover else day.currentOrders
                val prev = if (mode == "turnover") day.previousTurnover else day.previousOrders
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                    Box(Modifier.height(110.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Row(
                            Modifier.fillMaxWidth().height(110.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth().height((110 * (prev / maxV)).toFloat().dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(3.dp)).background(prevColor),
                            )
                            Box(
                                Modifier.weight(1f).fillMaxWidth().height((110 * (cur / maxV)).toFloat().dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(3.dp)).background(currentColor),
                            )
                        }
                    }
                    Text(day.label, color = AtColors.text, fontSize = 9.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    Text(day.prevLabel, color = AtColors.muted, fontSize = 8.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TopEstablishmentsCard(
    rows: List<TopEstablishment>,
    mode: String,
    onMode: (String) -> Unit,
) {
    val currentColor = AtColors.accent
    val prevColor = AtColors.warning
    val maxV = rows.maxOf {
        if (mode == "turnover") maxOf(it.turnover, it.prevTurnover) else maxOf(it.orders, it.prevOrders)
    }.coerceAtLeast(1.0)
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp)) {
        Text("Топ заведений", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(10.dp))
        SiteSegmented(
            value = mode,
            items = listOf("turnover" to "Оборот", "orders" to "Заказы"),
            onChange = onMode,
        )
        rows.take(8).forEachIndexed { idx, row ->
            val cur = if (mode == "turnover") row.turnover else row.orders
            val prev = if (mode == "turnover") row.prevTurnover else row.prevOrders
            val d = KassaApi.vsPrev(cur, prev)
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${idx + 1}  ${row.name}",
                        color = AtColors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (d.isNotBlank()) {
                        Text(d, color = deltaColor(d), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                CompareBar(prev, maxV, prevColor, KassaApi.compactNumber(prev))
                Spacer(Modifier.height(4.dp))
                CompareBar(cur, maxV, currentColor, KassaApi.compactNumber(cur))
            }
        }
    }
}

@Composable
private fun CompareBar(value: Double, maxV: Double, color: Color, label: String) {
    val frac = (value / maxV).toFloat().coerceIn(0.06f, 1f)
    Box(
        Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(color),
        )
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp).align(Alignment.CenterStart),
            maxLines = 1,
        )
    }
}

@Composable
private fun RegistrationsCard(points: List<MonthUsers>) {
    val accent = AtColors.accent
    val good = AtColors.success
    val warn = AtColors.warning
    val stroke = AtColors.stroke
    val maxUsers = points.maxOf { it.users }.coerceAtLeast(1.0)
    val n = points.size.coerceAtLeast(2)
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp)) {
        Text("Клиенты по регистрации", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("по месяцам · последние ${points.size}", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot("Регистрации", accent)
            LegendDot("% с заказом", good)
            LegendDot("% без заказа", warn)
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp)) {
            val padL = 10f
            val padR = 10f
            val padT = 12f
            val padB = 10f
            val w = (size.width - padL - padR).coerceAtLeast(1f)
            val h = (size.height - padT - padB).coerceAtLeast(1f)
            fun x(i: Int) = padL + i * w / (n - 1).coerceAtLeast(1)
            fun yUsers(v: Double) = padT + h - (v / maxUsers).toFloat() * h
            fun yPct(v: Double) = padT + h - (v / 100.0).toFloat() * h
            for (s in 0..3) {
                val yy = padT + s * h / 3f
                drawLine(stroke.copy(alpha = 0.22f), androidx.compose.ui.geometry.Offset(padL, yy), androidx.compose.ui.geometry.Offset(size.width - padR, yy), 1f)
            }
            fun line(color: Color, dashed: Boolean, yOf: (MonthUsers) -> Float) {
                val path = androidx.compose.ui.graphics.Path()
                points.forEachIndexed { i, p ->
                    val xx = x(i)
                    val yy = yOf(p)
                    if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                }
                drawPath(
                    path,
                    color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (dashed) 2.5f else 3.5f,
                        pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                    ),
                )
            }
            line(accent, false) { yUsers(it.users) }
            line(good, true) { yPct(it.withOrderPct) }
            line(warn, true) { yPct(it.withoutOrderPct) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, (n - 1) / 2, n - 1).distinct().forEach { i ->
                Text(points.getOrNull(i)?.label.orEmpty(), color = AtColors.muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AttentionCard(
    overdue: Int,
    problems: List<JsonRow>,
    onOpenTasks: () -> Unit,
    onOpenProblems: () -> Unit,
    onOpenRow: (JsonRow) -> Unit,
) {
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp)) {
        Text("Внимание", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Row(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AtColors.danger.copy(alpha = 0.10f))
                .border(1.dp, AtColors.danger.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenTasks)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(overdue.toString(), color = AtColors.danger, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("просроченных задач", color = AtColors.text, fontSize = 14.sp)
                Text("Открыть задачи", color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        if (problems.isNotEmpty()) {
            problems.forEach { row ->
                val title = KassaApi.orderNo(row.raw).ifBlank { row.title }
                val point = KassaApi.establishmentName(row.raw)
                val why = KassaApi.pick(row.raw, "comment", "delayReason", "reason", "note").ifBlank { row.subtitle }
                val whenRaw = KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "updatedAt", "orderDatetime", "datetime"))
                Column(
                    Modifier.fillMaxWidth().clickable { onOpenRow(row) }.padding(top = 12.dp),
                ) {
                    Text(
                        "▲  ${if (title.startsWith("№")) title else "№$title"}${if (point.isNotBlank()) " · $point" else ""}",
                        color = AtColors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (why.isNotBlank()) {
                        Text(why, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (whenRaw.isNotBlank()) {
                        Text(whenRaw, color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Text(
                "Все проблемные заказы",
                color = AtColors.accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 10.dp).clickable(onClick = onOpenProblems),
            )
        }
    }
}

private suspend fun buildHomeSnapshot(
    api: KassaApi,
    token: String,
    hub: Int,
    period: String = "month",
    cityKey: String = "",
    customFrom: String = "",
    customTo: String = "",
): HomeSnapshot = coroutineScope {
    when (hub) {
        0 -> {
            val hint = KassaApi.periodHint(period)
            val (from, to) = KassaApi.dateRange(period, customFrom = customFrom, customTo = customTo)
            val (prevFrom, prevTo) = KassaApi.prevDateRange(period, customFrom, customTo)
            val rangeLabel = KassaApi.periodRangeLabel(period, customFrom, customTo)
            val prevRangeLabel = "${KassaApi.fmtDay(prevFrom)} — ${KassaApi.fmtDay(prevTo)}"
            val analytics = async {
                try { api.getObject(KassaApi.salesAnalyticsPath(from, to, cityKey), token) } catch (_: Exception) { JSONObject() }
            }
            val analyticsPrev = async {
                try { api.getObject(KassaApi.salesAnalyticsPath(prevFrom, prevTo, cityKey), token) } catch (_: Exception) { JSONObject() }
            }
            val summary = async {
                try { api.getObject(KassaApi.reportsSummaryQuery(from, to, cityKey = cityKey), token) } catch (_: Exception) { JSONObject() }
            }
            val stores = async {
                try { api.getObject(KassaApi.appStoresStatsPath(from, to, sync = true), token) } catch (_: Exception) { JSONObject() }
            }
            val storesPrev = async {
                try { api.getObject(KassaApi.appStoresStatsPath(prevFrom, prevTo, sync = false), token) } catch (_: Exception) { JSONObject() }
            }
            val storesAll = async {
                try {
                    api.getObject(KassaApi.appStoresStatsPath("earliest", to, sync = false), token)
                } catch (_: Exception) {
                    try { api.getObject(KassaApi.appStoresStatsPath("2025-06-01", to, sync = false), token) } catch (_: Exception) { JSONObject() }
                }
            }
            val cities = async {
                try { api.getObject("/sales/establishments/cities", token) } catch (_: Exception) { JSONObject() }
            }
            val operations = async {
                try { api.getRows(KassaApi.operationsListPath("ACTIVE", 80), token) } catch (_: Exception) {
                    try { api.getRows("/operations", token) } catch (_: Exception) { emptyList() }
                }
            }
            val problems = async {
                try {
                    val q = if (cityKey.isNotBlank()) "?cityKey=${java.net.URLEncoder.encode(cityKey, "UTF-8")}" else ""
                    api.getRows("/problem-orders$q", token)
                } catch (_: Exception) { emptyList() }
            }
            val intake = async {
                try { api.getRows(KassaApi.logisticsIntakePath(80), token) } catch (_: Exception) { emptyList() }
            }
            val tasks = async {
                try { api.getRows("/workspace/tasks", token) } catch (_: Exception) { emptyList() }
            }
            val cur = analytics.await()
            val prev = analyticsPrev.await()
            val ops = operations.await()
            val prob = problems.await()
            val intakeRows = intake.await()
            val taskRows = tasks.await()
            val overdue = taskRows.count {
                val st = KassaApi.pick(it.raw, "status", "state").uppercase()
                st == "OVERDUE" || (!KassaApi.isTaskDone(it) && KassaApi.isOverdue(KassaApi.pick(it.raw, "dueAt", "deadline", "deadlineAt")))
            }
            val review = ops.filter { KassaApi.operationStatus(it) == "PENDING_REVIEW" || KassaApi.needsConfirm(it) }
            val hasAnalytics = cur.length() > 0 && (cur.has("totals") || cur.has("current") || cur.has("ordersCount") || cur.has("series"))
            val pulseCore = if (hasAnalytics) {
                KassaApi.pulseFromAnalytics(cur, prev.takeIf { it.length() > 0 }, rangeLabel)
            } else {
                KassaApi.parseReportPulse(summary.await(), hint)
            }.ifEmpty {
                listOf(
                    PulseMetric("Заказы", ops.size.toString(), hint, "📦"),
                    PulseMetric("Проблемные", prob.size.toString(), "нужно разобрать", "⚠"),
                    PulseMetric("На проверке", review.size.toString(), "подтвердить", "⏳"),
                    PulseMetric("Задачи", taskRows.size.toString(), if (overdue > 0) "просрочено: $overdue" else "назначено вам", "✓"),
                )
            }
            val storeCur = stores.await()
            val storePrev = storesPrev.await()
            val storeAll = storesAll.await()
            val downloads = KassaApi.downloadsFromStats(storeCur.takeIf { it.length() > 0 }, storePrev.takeIf { it.length() > 0 }, rangeLabel)
            val downloadSeries = KassaApi.parseDownloadSeries(storeAll.takeIf { it.length() > 0 } ?: storeCur)
            val pulse = buildList {
                addAll(pulseCore)
                if (review.isNotEmpty() && none { it.label.contains("проверк", true) }) {
                    add(PulseMetric("На проверке", review.size.toString(), "подтвердить", "⏳", tint = "blue"))
                }
                if (prob.isNotEmpty() && none { it.label.contains("проблем", true) }) {
                    add(PulseMetric("Проблемные", prob.size.toString(), "нужно разобрать", "⚠", tint = "violet"))
                }
                if (overdue > 0 && none { it.label.contains("задач", true) }) {
                    add(PulseMetric("Просроченные задачи", overdue.toString(), "в работе", "✓", tint = "peach"))
                }
            }
            val dynamics = KassaApi.buildDynamics(cur, prev.takeIf { it.length() > 0 }, from, to, prevFrom, prevTo)
            val top = KassaApi.parseTopEstablishments(cur, prev.takeIf { it.length() > 0 })
            val monthly = KassaApi.parseMonthlyUsers(cur)
            val cTotals = KassaApi.analyticsTotals(cur)
            val pTotals = KassaApi.analyticsTotals(prev)
            val turnoverDelta = KassaApi.vsPrev(KassaApi.jsonNum(cTotals, "turnover", "revenue"), KassaApi.jsonNum(pTotals, "turnover", "revenue"))
            val ordersDelta = KassaApi.vsPrev(KassaApi.jsonNum(cTotals, "ordersCount", "orders"), KassaApi.jsonNum(pTotals, "ordersCount", "orders"))

            val fromDate = java.time.LocalDate.parse(from)
            val toDate = java.time.LocalDate.parse(to)
            val dayKeys = generateSequence(fromDate) { d ->
                val n = d.plusDays(1)
                if (n.isAfter(toDate)) null else n
            }.map { it.toString() }.toList()
            fun parseMoney(raw: String): Double? {
                val t = raw.trim()
                if (t.isBlank()) return null
                val cleaned = t.replace("₽", "").replace("TMT", "").replace(" ", "").replace('\u00A0', ' ').replace(',', '.')
                return cleaned.replace(Regex("[^0-9+\\-\\.]"), "").toDoubleOrNull()
            }
            val ordersByDay = dayKeys.associateWith { 0 }.toMutableMap()
            val turnoverByDay = HashMap<String, Double>()
            val turnoverKnownDays = HashSet<String>()
            ops.forEach { op ->
                val dk = KassaApi.dayKey(KassaApi.operationWhen(op.raw))
                if (dk.isBlank() || !ordersByDay.containsKey(dk)) return@forEach
                ordersByDay[dk] = (ordersByDay[dk] ?: 0) + 1
                parseMoney(
                    KassaApi.pick(op.raw, "orderAmount", "amount", "turnoverWithoutDelivery", "turnover", "revenue", "gmv"),
                )?.let { v ->
                    turnoverByDay[dk] = (turnoverByDay[dk] ?: 0.0) + v
                    turnoverKnownDays += dk
                }
            }
            val series = if (dynamics.isNotEmpty()) {
                dynamics.map {
                    PulseSeriesPoint(it.key, it.currentOrders.toInt(), it.currentTurnover)
                }
            } else {
                dayKeys.map { dk ->
                    PulseSeriesPoint(
                        dayKey = dk,
                        orders = ordersByDay[dk] ?: 0,
                        turnover = if (turnoverKnownDays.contains(dk)) turnoverByDay[dk] else null,
                    )
                }
            }
            val queue = (prob + review + intakeRows.filter { KassaApi.needsConfirm(it) || KassaApi.needsRetry(it) })
                .distinctBy { it.id.ifBlank { KassaApi.orderNo(it.raw) } }
            HomeSnapshot(
                metrics = listOf(
                    HomeMetric("Операции", ops.size.toString(), Tone.Neutral),
                    HomeMetric("Проблемные", prob.size.toString(), if (prob.isNotEmpty()) Tone.Danger else Tone.Good),
                    HomeMetric("Задачи", if (overdue > 0) overdue.toString() else taskRows.size.toString(), if (overdue > 0) Tone.Warn else Tone.Good),
                ),
                pulse = pulse,
                primary = KassaApi.sortOperations(prob.ifEmpty { queue.ifEmpty { ops.take(5) } }),
                secondary = KassaApi.sortTasks(taskRows),
                cities = KassaApi.parseCities(cities.await()),
                overdueTasks = overdue,
                pulseSeries = series,
                downloads = downloads,
                downloadSeries = downloadSeries,
                dynamics = dynamics,
                topEstablishments = top,
                monthlyUsers = monthly,
                rangeLabel = rangeLabel,
                prevRangeLabel = prevRangeLabel,
                turnoverDelta = turnoverDelta,
                ordersDelta = ordersDelta,
            )
        }

        1 -> {
            val chats = async { api.getRows("/workspace/chats", token) }
            val support = async { api.getRows("/support/threads", token) }
            val calls = async {
                try {
                    api.getRows("/calls?take=20", token)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val chatRows = chats.await()
            val supportRows = support.await()
            val callRows = calls.await()
            val missed = callRows.count { KassaApi.callDirection(it.raw) == "missed" }
            HomeSnapshot(
                metrics = listOf(
                    HomeMetric("Чаты", chatRows.size.toString(), Tone.Neutral),
                    HomeMetric("Поддержка", supportRows.size.toString(), if (supportRows.isNotEmpty()) Tone.Warn else Tone.Good),
                    HomeMetric("Пропущено", missed.toString(), if (missed > 0) Tone.Danger else Tone.Good),
                ),
                primary = KassaApi.sortConversations(chatRows.ifEmpty { supportRows }),
                secondary = if (missed > 0) {
                    callRows.filter { KassaApi.callDirection(it.raw) == "missed" }.take(4)
                } else {
                    KassaApi.sortConversations(supportRows.ifEmpty { callRows })
                },
            )
        }

        2 -> {
            val est = async { api.getRows("/establishments", token) }
            val cities = async { api.getRows("/sales/establishments/cities", token) }
            val portraits = async { api.getRows("/reports/client-portraits", token) }
            val estRows = est.await()
            val cityRows = cities.await()
            val portraitRows = portraits.await()
            HomeSnapshot(
                metrics = listOf(
                    HomeMetric("Заведения", estRows.size.toString(), Tone.Neutral),
                    HomeMetric("Города", cityRows.size.toString(), Tone.Neutral),
                    HomeMetric("Портреты", portraitRows.size.toString(), Tone.Good),
                ),
                primary = estRows,
                secondary = cityRows.ifEmpty { portraitRows },
            )
        }

        else -> {
            val users = async { api.getRows("/users", token) }
            val audit = async { api.getRows("/settings/audit/logs?take=20", token) }
            val userRows = users.await()
            val auditRows = audit.await()
            HomeSnapshot(
                metrics = listOf(
                    HomeMetric("Пользователи", userRows.size.toString(), Tone.Neutral),
                    HomeMetric("Аудит", auditRows.size.toString(), Tone.Warn),
                    HomeMetric("Разделы", HUBS[3].items.size.toString(), Tone.Neutral),
                ),
                primary = auditRows,
                secondary = userRows,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JsonListPane(
    rootSpec: ModuleSpec,
    load: suspend () -> List<JsonRow>,
    onBack: () -> Unit,
    onOpen: (ModuleSpec, JsonRow) -> Unit,
    onOpenChat: ((String, String) -> Unit)? = null,
    cacheKey: String? = null,
    cache: CacheStore? = null,
    api: KassaApi? = null,
    token: String? = null,
    me: AppUser? = null,
) {
    var spec by remember(rootSpec.path, rootSpec.title) { mutableStateOf(rootSpec) }
    val hub = remember(rootSpec.path, rootSpec.tab) { menuHubFor(rootSpec) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JsonRow>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var actionErr by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var actingId by remember { mutableStateOf<String?>(null) }
    var offline by remember { mutableStateOf(false) }
    var cachedAt by remember { mutableStateOf(0L) }
    var refreshing by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var taskView by remember { mutableStateOf("kanban") }
    val filters = remember { FiltersStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    var filter by remember {
        val stored = filters.get(spec.path)
        val next = when {
            stored.equals("ALL", true) -> "all"
            KassaApi.isOperationsList(spec.path) && stored in setOf("urgent", "new", "mine") -> "all"
            else -> stored.ifBlank { "all" }
        }
        mutableStateOf(next)
    }
    var pinTick by remember { mutableIntStateOf(0) }
    val pins = remember { PinsStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val drafts = remember { DraftsStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val pinIds = remember(pinTick) { pins.ids() }
    var pendingProblem by remember { mutableStateOf<JsonRow?>(null) }
    var pendingDone by remember { mutableStateOf<JsonRow?>(null) }
    val shiftLog = remember { ShiftLogStore.of(ctx) }
    // Task create (minimal fields) + refresh.
    var createTaskOpen by remember { mutableStateOf(false) }
    var createTaskErr by remember { mutableStateOf<String?>(null) }
    var createTaskTitle by remember { mutableStateOf("") }
    var createTaskPriority by remember { mutableStateOf("MEDIUM") } // LOW|MEDIUM|HIGH
    var createTaskDuePreset by remember { mutableStateOf("none") } // none|today|tomorrow
    var createTaskBusy by remember { mutableStateOf(false) }
    val undo = remember(shiftLog.revision, actionMsg) { shiftLog.lastUndo() }
    val pullState = rememberPullToRefreshState()
    val scrollStore = remember { ScrollStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val scrollKey = "${spec.path}|$filter"
    val savedScroll = remember(scrollKey) { scrollStore.get(scrollKey) }
    val listState = remember(scrollKey) {
        androidx.compose.foundation.lazy.LazyListState(savedScroll.first, savedScroll.second)
    }
    DisposableEffect(scrollKey) {
        onDispose {
            scrollStore.put(scrollKey, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    fun undoLast() {
        val u = shiftLog.lastUndo() ?: return
        val client = api
        val t = token
        if (client == null || t.isNullOrBlank()) return
        scope.launch {
            actingId = u.rowId
            actionErr = null
            try {
                withContext(Dispatchers.IO) {
                    enqueueOrPatch(ctx, client, t, u.path, u.prevStatus, "Отмена", prevStatus = u.newStatus, title = u.title, rowId = u.rowId, log = false)
                }
                rows = rows?.map { if (it.id == u.rowId) KassaApi.withStatus(it, u.prevStatus) else it }
                shiftLog.clearUndo()
                actionMsg = "Вернули «${u.title}»"
                Haptics.tap(ctx)
            } catch (e: Exception) {
                actionErr = e.message ?: "Не удалось отменить"
            } finally {
                actingId = null
            }
        }
    }
    fun patchRow(row: JsonRow, path: String, label: String, status: String, comment: String = "") {
        val id = row.id
        val client = api
        val t = token
        if (id.isBlank() || client == null || t.isNullOrBlank()) return
        scope.launch {
            actingId = id
            actionErr = null
            actionMsg = null
            try {
                val msg = withContext(Dispatchers.IO) {
                    enqueueOrPatch(
                        ctx, client, t, path, status, label,
                        prevStatus = KassaApi.pick(row.raw, "status", "state"),
                        title = row.title,
                        rowId = id,
                        comment = comment,
                    )
                }
                Haptics.tap(ctx)
                rows = rows?.map { if (it.id == id) KassaApi.withStatus(it, status) else it }
                actionMsg = msg
            } catch (e: Exception) {
                Haptics.warn(ctx)
                actionErr = e.message ?: "Не удалось обновить статус"
            } finally {
                actingId = null
            }
        }
    }
    fun confirmRow(row: JsonRow) {
        val client = api
        val t = token
        val id = KassaApi.confirmTargetId(row)
        if (id.isBlank() || client == null || t.isNullOrBlank()) return
        scope.launch {
            actingId = row.id
            actionErr = null
            actionMsg = null
            try {
                withContext(Dispatchers.IO) {
                    client.postJson(KassaApi.confirmLogisticsPath(id), t, JSONObject())
                }
                Haptics.tap(ctx)
                rows = rows?.map { if (it.id == row.id) KassaApi.withStatus(it, "CREATED") else it }
                actionMsg = "Подтверждено"
                ShiftLogStore.of(ctx).record("take", row.title, KassaApi.confirmLogisticsPath(id), KassaApi.pick(row.raw, "status"), "CREATED", row.id, undoable = false)
            } catch (e: Exception) {
                Haptics.warn(ctx)
                actionErr = e.message ?: "Не удалось подтвердить"
            } finally {
                actingId = null
            }
        }
    }
    fun retryRow(row: JsonRow) {
        val client = api
        val t = token
        if (row.id.isBlank() || client == null || t.isNullOrBlank()) return
        scope.launch {
            actingId = row.id
            actionErr = null
            actionMsg = null
            try {
                withContext(Dispatchers.IO) {
                    client.postJson(KassaApi.retryProposalPath(row.id), t, JSONObject())
                }
                Haptics.tap(ctx)
                actionMsg = "Повтор отправлен"
                tick++
            } catch (e: Exception) {
                Haptics.warn(ctx)
                actionErr = e.message ?: "Не удалось повторить"
            } finally {
                actingId = null
            }
        }
    }
    fun fileProblemRow(row: JsonRow, comment: String) {
        val client = api
        val t = token
        if (client == null || t.isNullOrBlank()) return
        scope.launch {
            actingId = row.id
            actionErr = null
            actionMsg = null
            try {
                withContext(Dispatchers.IO) {
                    client.postJson("/problem-orders", t, KassaApi.problemOrderBody(row, comment))
                }
                Haptics.tap(ctx)
                actionMsg = "Заказ в проблемных"
                ShiftLogStore.of(ctx).record("problem", row.title, "/problem-orders", KassaApi.pick(row.raw, "status"), "PROBLEM", row.id, undoable = false)
            } catch (e: Exception) {
                Haptics.warn(ctx)
                actionErr = e.message ?: "Не удалось занести проблему"
            } finally {
                actingId = null
            }
        }
    }
    fun chatReadPath(id: String): String {
        return if (spec.path.startsWith("/support")) "/support/threads/$id/messages"
        else "/workspace/chats/$id/messages"
    }
    fun zeroUnread(row: JsonRow): JsonRow {
        val o = JSONObject(row.raw.toString())
        o.put("unreadCount", 0)
        o.put("unread", 0)
        return row.copy(raw = o)
    }
    fun yeastarDialFromList(phone: String) {
        val client = api
        val t = token
        if (phone.isBlank() || client == null || t.isNullOrBlank()) return
        scope.launch {
            actingId = phone
            actionErr = null
            actionMsg = null
            try {
                val prefs = ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)
                val from = prefs.getString("call_from_number", "").orEmpty()
                val announce = prefs.getBoolean("call_announce_recording", false)
                actionMsg = yeastarDial(ctx, client, t, phone, from, announce)
                Haptics.tap(ctx)
            } catch (e: Exception) {
                Haptics.warn(ctx)
                actionErr = e.message ?: "Не удалось набрать через АТС"
            } finally {
                actingId = null
            }
        }
    }
    fun markRead(row: JsonRow) {
        if (row.id.isBlank()) return
        cache?.markChatRead(row.id)
        rows = rows?.map { if (it.id == row.id) zeroUnread(it) else it }
        val client = api
        val t = token
        if (client == null || t.isNullOrBlank()) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { client.markChatRead(chatReadPath(row.id), t) }
            } catch (_: Exception) {
            }
        }
    }
    fun markAllRead() {
        val list = rows.orEmpty().filter { (KassaApi.pick(it.raw, "unreadCount", "unread", "count").toIntOrNull() ?: 0) > 0 }
        if (list.isEmpty()) return
        cache?.markAllChatsRead()
        rows = rows?.map { zeroUnread(it) }
        val client = api
        val t = token
        scope.launch {
            if (client != null && !t.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    list.forEach { row ->
                        try {
                            client.markChatRead(chatReadPath(row.id), t)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            actionMsg = "Прочитано: ${list.size}"
            Haptics.tap(ctx)
        }
    }
    val baseTake = remember(spec.path) { KassaApi.takeOf(spec.path).coerceAtLeast(20) }
    var take by remember(spec.path) { mutableIntStateOf(baseTake) }
    var exhausted by remember(spec.path) { mutableStateOf(false) }
    var remoteSearch by remember(spec.path) { mutableStateOf("") }
    var payFilter by remember(spec.path) { mutableStateOf("ALL") }
    LaunchedEffect(q, spec.path) {
        delay(280)
        remoteSearch = if (KassaApi.isOperationsList(spec.path) && KassaApi.looksLikeOrderSearch(q)) q.trim() else ""
    }
    val fetchFilter = if (KassaApi.isOperationsList(spec.path)) filter else "all"
    LaunchedEffect(spec.path, tick, take, fetchFilter, remoteSearch, payFilter) {
        if (spec.path.contains("/keypad")) {
            rows = emptyList()
            refreshing = false
            return@LaunchedEffect
        }
        if (tick == 0 && take == baseTake) rows = null
        err = null
        offline = false
        exhausted = false
        if (tick > 0 || take > baseTake) refreshing = true
        try {
            val loaded = if (api != null && !token.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    api.getRows(KassaApi.listFetchPath(spec.path, fetchFilter, take, remoteSearch, payFilter), token)
                }
            } else {
                load()
            }
            if (loaded.size < take) exhausted = true
            rows = loaded
            if (cacheKey != null && cache != null) {
                cache.putRows(cacheKey, loaded)
            }
            if (KassaApi.isOperationsList(spec.path)) {
                cache?.putRows(KassaApi.opsCacheKey(), loaded)
            }
        } catch (e: Exception) {
            val cached = if (cacheKey != null && cache != null) cache.getRows(cacheKey) else null
            if (cached != null && cacheKey != null && cache != null) {
                rows = cached
                cachedAt = cache.cachedAt(cacheKey)
                offline = true
                err = null
            } else {
                err = e.message
            }
        } finally {
            refreshing = false
        }
    }
    LaunchedEffect(spec.path, token) {
        val live = spec.path.startsWith("/operations") || spec.path.contains("chats") || spec.path.startsWith("/workspace/tasks") || spec.path.contains("support") || spec.path.startsWith("/problem-orders")
        if (!live || token.isNullOrBlank() || api == null) return@LaunchedEffect
        while (isActive) {
            val sec = ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE).getInt("poll_sec", 15).coerceIn(10, 60)
            delay(sec * 1000L)
            if (!PollWatcher.appForeground) continue
            try {
                val loaded = withContext(Dispatchers.IO) {
                    api.getRows(KassaApi.listFetchPath(spec.path, fetchFilter, take, remoteSearch, payFilter), token)
                }
                rows = loaded
                if (cacheKey != null && cache != null) cache.putRows(cacheKey, loaded)
            } catch (_: Exception) {
            }
        }
    }
    val shown = rows?.filter { row ->
        if (q.isBlank()) true
        else {
            val blob = listOf(
                row.title,
                row.subtitle,
                KassaApi.orderNo(row.raw),
                KassaApi.clientName(row.raw),
                KassaApi.establishmentName(row.raw),
                KassaApi.pick(row.raw, "phone", "clientPhone", "comment", "delayReason"),
            ).joinToString(" ")
            blob.contains(q, true)
        }
    }.orEmpty()
    val filtered = when {
        KassaApi.isOperationsList(spec.path) -> KassaApi.sortOperations(shown)
        spec.path.contains("logistics-intake") -> KassaApi.sortOperations(
            shown.filter { r ->
                filter != "review" || KassaApi.needsConfirm(r) || KassaApi.operationStatus(r) == "PENDING_REVIEW"
            },
        )
        spec.path.startsWith("/establishments") ||
            (spec.path.startsWith("/sales/establishments") && !spec.path.contains("cities")) -> {
            val want = when {
                spec.title == "Магазины" -> "store"
                spec.title == "Рестораны" -> "restaurant"
                filter == "store" || filter == "restaurant" -> filter
                else -> ""
            }
            if (want.isBlank()) shown
            else shown.filter { establishmentKind(it) == want }
        }

        spec.title == "Записи" && spec.path.startsWith("/calls") ->
            shown.filter { r ->
                val st = KassaApi.recordingStatusOf(r.raw)
                st == "READY" || KassaApi.pick(r.raw, "recordingUrl").isNotBlank()
            }

        spec.path.startsWith("/problem-orders") -> shown.filter { r ->
            val reason = KassaApi.pick(r.raw, "reason", "delayReason").uppercase()
            when (filter) {
                "restaurant" -> reason.contains("RESTAURANT")
                "store" -> reason.contains("STORE")
                "courier" -> reason.contains("COURIER")
                "other" -> reason.contains("OTHER") || reason.isBlank()
                else -> true
            }
        }

        spec.path.startsWith("/workspace/chats") || spec.path.startsWith("/support/threads") -> {
            KassaApi.sortConversations(shown.filter { r ->
                when (filter) {
                    "unread" -> KassaApi.unreadCount(r) > 0
                    "pinned" -> pinIds.contains(r.id)
                    else -> true
                }
            }).sortedByDescending { if (pinIds.contains(it.id)) 1 else 0 }
        }

        spec.path.startsWith("/calls") && !spec.path.contains("/contacts") ->
            shown.filter { r ->
                val dir = KassaApi.callDirection(r.raw)
                when (filter) {
                    "in" -> dir == "in"
                    "out" -> dir == "out"
                    "missed" -> dir == "missed"
                    else -> true
                }
            }

        spec.path.startsWith("/workspace/tasks") ->
            KassaApi.sortTasks(shown.filter { r ->
                val due = KassaApi.taskDueRaw(r)
                when (filter) {
                    "urgent" -> KassaApi.isTaskUrgent(r)
                    "today" -> !KassaApi.isTaskDone(r) && KassaApi.isDueToday(due)
                    "overdue" -> !KassaApi.isTaskDone(r) && KassaApi.isOverdue(due)
                    "done" -> KassaApi.isTaskDone(r)
                    "open" -> !KassaApi.isTaskDone(r)
                    "mine" -> me != null && KassaApi.assignedToMe(r, me) && !KassaApi.isTaskDone(r)
                    else -> !KassaApi.isTaskDone(r)
                }
            })

        else -> shown
    }
    val dialable = spec.path.contains("/calls")
    var autoArmed by remember { mutableStateOf(true) }
    LaunchedEffect(take, exhausted, refreshing) {
        if (refreshing) autoArmed = false
        else {
            delay(350)
            autoArmed = !exhausted
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                val total = listState.layoutInfo.totalItemsCount
                if (autoArmed && last >= 0 && total > 4 && last >= total - 3 && api != null && !token.isNullOrBlank()) {
                    autoArmed = false
                    take += 30
                }
            }
    }
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine(hub?.title ?: spec.title, onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
        Column(Modifier.fillMaxSize()) {
        if (!spec.path.contains("/keypad")) {
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text(searchPlaceholder(spec)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = fieldColors(),
            shape = RoundedCornerShape(10.dp),
        )
        }
        if (hub != null && hub.tabs.size > 1) {
            val selected = hub.tabs.find { it.title == spec.title }?.title
                ?: hub.tabs.find { menuPath(it.path) == menuPath(spec.path) }?.title
                ?: spec.title
            SiteSegmented(
                value = selected,
                items = hub.tabs.map { it.title to it.title },
                onChange = { title ->
                    hub.tabs.find { it.title == title }?.let { spec = it }
                    tick++
                    q = ""
                    filter = "all"
                },
            )
            Text(
                hub.subtitle,
                color = AtColors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (
            spec.path.startsWith("/establishments") ||
            (spec.path.startsWith("/sales/establishments") && !spec.path.contains("cities") && spec.title !in listOf("SMS", "SMS рассылка", "Магазины", "Рестораны"))
        ) {
            FilterChips(
                value = filter,
                items = listOf("all" to "Все", "restaurant" to "Рестораны", "store" to "Магазины"),
                onChange = {
                    filter = it
                    filters.put(spec.path, it)
                },
            )
        }
        if (KassaApi.isOperationsList(spec.path)) {
            FilterChips(
                value = filter,
                items = listOf(
                    "all" to "Все заказы",
                    "review" to "На проверке",
                    "created" to "Подтвержденные",
                    "cancelled" to "Отмененные",
                ),
                onChange = {
                    filter = it
                    filters.put(spec.path, it)
                    take = baseTake
                },
            )
            FilterChips(
                value = payFilter,
                items = listOf(
                    "ALL" to "Любая оплата",
                    "CASH" to "Наличные",
                    "ONLINE" to "Онлайн",
                    "MIXED" to "Смешанная",
                ),
                onChange = {
                    payFilter = it
                    take = baseTake
                },
            )
        } else if (spec.path.contains("logistics-intake")) {
            FilterChips(
                value = filter,
                items = listOf(
                    "all" to "Все события",
                    "review" to "На проверке",
                ),
                onChange = {
                    filter = it
                    filters.put(spec.path, it)
                },
            )
        } else if (spec.path.startsWith("/problem-orders")) {
            FilterChips(
                value = filter,
                items = listOf(
                    "all" to "Все",
                    "restaurant" to "Ресторан",
                    "store" to "Магазин",
                    "courier" to "Курьер",
                    "other" to "Другое",
                ),
                onChange = {
                    filter = it
                    filters.put(spec.path, it)
                },
            )
        } else if (spec.path.startsWith("/workspace/chats") || spec.path.startsWith("/support/threads")) {
            FilterChips(
                value = filter,
                items = listOf(
                    "all" to "Все",
                    "unread" to "Непрочитанные",
                    "pinned" to "Закреплённые",
                ),
                onChange = {
                    filter = it
                    filters.put(spec.path, it)
                },
            )
            val unreadN = rows.orEmpty().count { KassaApi.unreadCount(it) > 0 }
            if (unreadN > 0) {
                TextButton(
                    onClick = { markAllRead() },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("Прочитать все · $unreadN", color = AtColors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (spec.path.startsWith("/calls") && !spec.path.contains("/contacts") && !spec.path.contains("keypad")) {
            FilterChips(
                value = filter,
                items = listOf(
                    "all" to "Все",
                    "in" to "Входящие",
                    "out" to "Исходящие",
                    "missed" to "Пропущенные",
                ),
                onChange = {
                    filter = it
                    filters.put(spec.path, it)
                },
            )
        }
        when {
            spec.path.contains("/keypad") -> CallKeypadPane(api, token)
            err != null -> ErrorBlock(err!!, onRetry = { tick++ })
            rows == null -> LoadingListState(spec)
            filtered.isEmpty() && !spec.path.startsWith("/workspace/tasks") -> EmptyStateCard(
                title = "Ничего не найдено",
                subtitle = when {
                    spec.path.startsWith("/operations") -> "Нет операций за выбранный период. Смените фильтр или поиск."
                    spec.path.startsWith("/workspace/chats") || spec.path.startsWith("/support/threads") -> "Либо здесь пока тихо, либо фильтр отсеял диалоги."
                    spec.path.startsWith("/calls") -> "Нет звонков по этому фильтру."
                    spec.path.startsWith("/problem-orders") -> "Нет проблемных заказов по этой причине."
                    else -> "Смените запрос или обновите экран."
                },
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (offline) {
                    item {
                        OfflineBanner(cachedAt = cachedAt)
                    }
                }
                if (actionMsg != null) {
                    item {
                        ActionBanner(
                            actionMsg!!,
                            error = false,
                            action = if (undo != null) "Отменить" else null,
                            onAction = if (undo != null) ({ undoLast() }) else null,
                        )
                    }
                }
                if (actionErr != null) {
                    item { ActionBanner(actionErr!!, error = true) }
                }
                item {
                    SectionLead(spec)
                }
                if (spec.path.startsWith("/workspace/tasks")) {
                    val allTasks = rows.orEmpty()
                    val overdueN = allTasks.count { !KassaApi.isTaskDone(it) && KassaApi.isOverdue(KassaApi.taskDueRaw(it)) }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Показать",
                                color = AtColors.muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                "+ Поставить задачу",
                                color = AtColors.accent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    createTaskOpen = true
                                    createTaskErr = null
                                    createTaskTitle = ""
                                    createTaskPriority = "MEDIUM"
                                    createTaskDuePreset = "none"
                                },
                            )
                        }
                    }
                    item {
                        SiteSegmented(
                            value = filter,
                            items = listOf(
                                "urgent" to "Срочные",
                                "today" to "Сегодня",
                                "all" to "Все",
                                "done" to "Архив",
                            ),
                            onChange = {
                                filter = it
                                filters.put(spec.path, it)
                                take = baseTake
                            },
                        )
                    }
                    item { TaskBoardHeader(all = allTasks, shown = filtered.size) }
                    if (overdueN > 0 && filter != "done") {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AtColors.danger.copy(alpha = 0.10f))
                                    .border(1.dp, AtColors.danger.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        filter = "overdue"
                                        filters.put(spec.path, "overdue")
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(overdueN.toString(), color = AtColors.danger, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("просроченных задач", color = AtColors.text, fontSize = 14.sp)
                                    Text("Показать просроченные", color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    item {
                        SiteSegmented(
                            value = taskView,
                            items = listOf("kanban" to "Канбан", "list" to "Список"),
                            onChange = { taskView = it },
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "Пока задач нет по выбранным фильтрам",
                                subtitle = "Смените «Показать» или поставьте новую задачу.",
                            )
                        }
                    }
                } else {
                    item {
                        ListContextHeader(spec = spec, count = filtered.size)
                    }
                }
                if (spec.path.startsWith("/problem-orders")) {
                    item { ProblemOrderStats(rows = rows.orEmpty()) }
                }
                if (KassaApi.isOperationsList(spec.path) || spec.path.contains("logistics-intake")) {
                    item {
                        OperationQueueStats(rows = rows.orEmpty())
                    }
                }
                if (spec.path.startsWith("/workspace/tasks") && taskView == "kanban") {
                    item {
                        TaskKanbanBoard(
                            rows = filtered,
                            onOpen = { onOpen(spec, it) },
                            onSetStatus = { row, status, label ->
                                patchRow(row, KassaApi.taskDetailPath(row.id), label, status)
                            },
                        )
                    }
                } else {
                items(filtered) { row ->
                    when {
                        spec.path.startsWith("/problem-orders") ->
                            ProblemOrderCard(row = row, onOpen = { onOpen(spec, row) })
                        spec.path.startsWith("/operations") -> {
                            val chat = KassaApi.chatLinkFromOperation(row.raw)
                            OperationCard(
                                row = row,
                                onOpen = { onOpen(spec, row) },
                                onMessage = if (chat != null && onOpenChat != null) {
                                    { onOpenChat(chat.first, chat.second) }
                                } else {
                                    null
                                },
                                busy = actingId == row.id,
                                onConfirm = if (KassaApi.needsConfirm(row)) ({ confirmRow(row) }) else null,
                                onRetry = if (KassaApi.needsRetry(row)) ({ retryRow(row) }) else null,
                                onProblem = { pendingProblem = row },
                            )
                        }
                        spec.path.startsWith("/workspace/chats") || spec.path.startsWith("/support/threads") ->
                            ConversationCard(
                                row = row,
                                pinned = pinIds.contains(row.id),
                                hasDraft = drafts.has(row.id),
                                draftText = drafts.get(row.id).take(80),
                                onOpen = { onOpen(spec, row) },
                                onPin = {
                                    pins.toggle(row.id)
                                    pinTick++
                                },
                                onMarkRead = if (KassaApi.unreadCount(row) > 0) {
                                    { markRead(row) }
                                } else {
                                    null
                                },
                                api = api,
                                token = token,
                                meId = me?.id.orEmpty(),
                            )
                        spec.path.startsWith("/workspace/tasks") ->
                            TaskCard(
                                row = row,
                                onOpen = { onOpen(spec, row) },
                                busy = actingId == row.id,
                                onDone = if (!KassaApi.isTaskDone(row)) {
                                    { patchRow(row, KassaApi.taskDetailPath(row.id), "Готово", "DONE") }
                                } else {
                                    null
                                },
                            )
                        spec.path.startsWith("/calls") && !spec.path.contains("/contacts") ->
                            CallLogCard(row = row, onOpen = { onOpen(spec, row) }, onDial = {
                                val phone = KassaApi.phoneOf(row.raw)
                                if (phone.isNotBlank()) yeastarDialFromList(phone)
                            })
                        spec.path.startsWith("/email/messages") ->
                            MailCard(row = row, onOpen = { onOpen(spec, row) })
                        spec.path.startsWith("/clients") || spec.path.contains("client-portraits") ->
                            ClientCard(row = row, onOpen = { onOpen(spec, row) })
                        spec.path.startsWith("/ai/") ->
                            InsightCard(row = row, onOpen = { onOpen(spec, row) })
                        spec.path.contains("/audit") ->
                            AuditCard(row = row, onOpen = { onOpen(spec, row) })
                        spec.path.startsWith("/courier-fleet") ->
                            CourierCard(row = row, onOpen = { onOpen(spec, row) })
                        spec.path.startsWith("/users") || spec.path.startsWith("/accounting/employees") || spec.path.startsWith("/calls/contacts") ->
                            ContactCard(row = row, onOpen = { onOpen(spec, row) }, onDial = {
                                val phone = KassaApi.phoneOf(row.raw)
                                if (phone.isNotBlank()) yeastarDialFromList(phone)
                            })
                        spec.path.startsWith("/accounting") || spec.path.startsWith("/reconciliation") || spec.path.startsWith("/sms") || spec.path.startsWith("/push") || spec.path == "/qr" || spec.path.startsWith("/qr") ->
                            DocumentCard(row = row, onOpen = { onOpen(spec, row) })
                        spec.path.startsWith("/marketing") ->
                            ClientCard(row = row, onOpen = { onOpen(spec, row) })
                        spec.path.startsWith("/establishments") || spec.path.startsWith("/sales/establishments") ->
                            EstablishmentCard(row = row, onOpen = { onOpen(spec, row) })
                        dialable -> ContactCard(row = row, onOpen = { onOpen(spec, row) }, onDial = {
                            val phone = KassaApi.phoneOf(row.raw)
                            if (phone.isNotBlank()) yeastarDialFromList(phone)
                        })
                        else -> GenericRowCard(row = row, onOpen = { onOpen(spec, row) })
                    }
                }
                }
                if (!exhausted && (rows?.size ?: 0) >= take && api != null && !token.isNullOrBlank()) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            InlineActionChip("Загрузить ещё") { take += 30 }
                        }
                    }
                }
            }
        }
        }
        }
    }
    pendingProblem?.let { row ->
        ConfirmProblemDialog(
            title = row.title.ifBlank { "этот заказ" },
            onConfirm = { note ->
                fileProblemRow(row, note)
                pendingProblem = null
            },
            onDismiss = { pendingProblem = null },
        )
    }
    pendingDone?.let { row ->
        ConfirmDoneDialog(
            title = row.title.ifBlank { "этот заказ" },
            onConfirm = {
                patchRow(row, KassaApi.operationDetailPath(row.id), "Готово", "DONE")
                pendingDone = null
            },
            onDismiss = { pendingDone = null },
        )
    }

    if (createTaskOpen) {
        AlertDialog(
            onDismissRequest = {
                createTaskOpen = false
                createTaskErr = null
                createTaskBusy = false
            },
            containerColor = AtColors.panel,
            title = { Text("Создать задачу", color = AtColors.text, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = createTaskTitle,
                        onValueChange = { createTaskTitle = it },
                        placeholder = { Text("Название задачи") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        colors = fieldColors(),
                        shape = radius,
                    )
                    if (createTaskErr != null) {
                        Text(createTaskErr!!, color = AtColors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Приоритет", color = AtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    FilterChips(
                        value = createTaskPriority,
                        items = listOf(
                            "LOW" to "Низкий",
                            "MEDIUM" to "Средний",
                            "HIGH" to "Высокий",
                        ),
                        onChange = { createTaskPriority = it },
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Дедлайн", color = AtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    FilterChips(
                        value = createTaskDuePreset,
                        items = listOf(
                            "none" to "Без SLA",
                            "today" to "Сегодня 18:00",
                            "tomorrow" to "Завтра 18:00",
                        ),
                        onChange = { createTaskDuePreset = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val client = api
                        val t = token
                        val meId = me?.id
                        val title = createTaskTitle.trim()
                        if (client == null || t.isNullOrBlank() || meId.isNullOrBlank() || title.isBlank()) return@TextButton
                        createTaskErr = null
                        createTaskBusy = true
                        scope.launch {
                            try {
                                val zone = java.time.ZoneId.systemDefault()
                                val deadlineAt = when (createTaskDuePreset) {
                                    "today" -> java.time.ZonedDateTime.of(
                                        java.time.LocalDate.now(zone),
                                        java.time.LocalTime.of(18, 0),
                                        zone,
                                    ).toInstant().toString()
                                    "tomorrow" -> java.time.ZonedDateTime.of(
                                        java.time.LocalDate.now(zone).plusDays(1),
                                        java.time.LocalTime.of(18, 0),
                                        zone,
                                    ).toInstant().toString()
                                    else -> null
                                }
                                val body = JSONObject()
                                    .put("title", title)
                                    .put("description", "")
                                    .put("priority", createTaskPriority)
                                    .put("assigneeIds", org.json.JSONArray().put(meId))
                                    .put("watcherIds", org.json.JSONArray())
                                    .put("contextType", "NONE")
                                    .put("contextId", org.json.JSONObject.NULL)
                                    .put("checklistTitles", org.json.JSONArray())
                                deadlineAt?.let { body.put("deadlineAt", it) }

                                withContext(Dispatchers.IO) {
                                    client.postJson("/workspace/tasks", t, body)
                                }
                                createTaskOpen = false
                                createTaskTitle = ""
                                createTaskDuePreset = "none"
                                createTaskPriority = "MEDIUM"
                                actionMsg = "Задача создана"
                                tick++
                                Haptics.tap(ctx)
                            } catch (e: Exception) {
                                createTaskErr = e.message ?: "Не удалось создать задачу"
                            } finally {
                                createTaskBusy = false
                            }
                        }
                    },
                    enabled = !createTaskBusy && createTaskTitle.trim().isNotBlank() && api != null && token != null && me?.id?.isNotBlank() == true,
                ) {
                    if (createTaskBusy) CircularProgressIndicator(Modifier.size(18.dp), color = AtColors.accent, strokeWidth = 2.dp)
                    else Text("Создать", color = AtColors.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        createTaskOpen = false
                        createTaskErr = null
                    },
                ) { Text("Отмена", color = AtColors.muted, fontWeight = FontWeight.Medium) }
            },
        )
    }
}

@Composable
private fun JsonObjectPane(
    title: String,
    load: suspend () -> JSONObject,
    onBack: () -> Unit,
    preload: JSONObject? = null,
    operation: Boolean = false,
    operationId: String = "",
    task: Boolean = false,
    taskId: String = "",
    token: String? = null,
    api: KassaApi? = null,
    onUpdated: ((JSONObject) -> Unit)? = null,
    onOpenChat: ((String, String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var obj by remember { mutableStateOf(preload) }
    var err by remember { mutableStateOf<String?>(null) }
    var actionMsg by remember { mutableStateOf<String?>(null) }
    var acting by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    val shiftLog = remember { ShiftLogStore.of(ctx) }
    val undo = remember(shiftLog.revision, actionMsg) { shiftLog.lastUndo() }
    fun undoLast() {
        val u = shiftLog.lastUndo() ?: return
        val client = api
        val t = token
        if (client == null || t.isNullOrBlank()) return
        scope.launch {
            acting = true
            err = null
            try {
                withContext(Dispatchers.IO) {
                    enqueueOrPatch(ctx, client, t, u.path, u.prevStatus, "Отмена", prevStatus = u.newStatus, title = u.title, rowId = u.rowId, log = false)
                }
                obj = obj?.let { JSONObject(it.toString()).apply { put("status", u.prevStatus) } }
                shiftLog.clearUndo()
                actionMsg = "Вернули «${u.title}»"
                Haptics.tap(ctx)
            } catch (e: Exception) {
                err = e.message ?: "Не удалось отменить"
            } finally {
                acting = false
            }
        }
    }
    LaunchedEffect(title, preload, tick) {
        if (preload != null && tick == 0) {
            obj = preload
            return@LaunchedEffect
        }
        obj = null
        err = null
        try {
            obj = withContext(Dispatchers.IO) { load() }
        } catch (e: Exception) {
            err = e.message
        }
    }
    val heroKeys = listOf("status", "orderAmount", "amount", "total", "clientName", "phone", "clientPhone", "orderNumber", "externalId", "city", "address", "name")
    val pairs = obj?.let { KassaApi.flatten(it) }.orEmpty().filterNot { it.first.endsWith("rawPayload") || it.first.contains("password", true) }
    val hero = pairs.filter { p -> heroKeys.any { p.first.equals(it, true) || p.first.endsWith(".$it") } }
    val rest = pairs.filterNot { p -> hero.contains(p) }
    val phone = obj?.let { KassaApi.phoneOf(it) }.orEmpty()
    val profileTitle = obj?.let { KassaApi.pick(it, "name", "title", "clientName", "fullName", "orderNumber", "externalId") }.orEmpty()
    val subtitle = obj?.let {
        listOf(
            KassaApi.pick(it, "status", "city", "role"),
            KassaApi.pick(it, "address", "email", "phone"),
        ).filter { part -> part.isNotBlank() }.joinToString(" · ")
    }.orEmpty()
    val timeline = obj?.let { KassaApi.eventTimeline(it) }.orEmpty()
    val showOps = operation || obj?.let { KassaApi.looksLikeOperation(it) } == true
    val showTask = task || obj?.let { KassaApi.looksLikeTask(it) } == true
    val opId = operationId.ifBlank { obj?.let { KassaApi.pick(it, "id", "externalId", "orderId") }.orEmpty() }
    val taskIdResolved = taskId.ifBlank { obj?.let { KassaApi.pick(it, "id", "_id") }.orEmpty() }
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine(title, onBack, onRefresh = { tick++ })
        when {
            err != null && obj == null -> ErrorBlock(err!!, onRetry = { tick++ })
            obj == null -> LoadingDetailState()
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (err != null) {
                    item { ActionBanner(err!!, error = true) }
                }
                if (actionMsg != null) {
                    item {
                        ActionBanner(
                            actionMsg!!,
                            error = false,
                            action = if (undo != null) "Отменить" else null,
                            onAction = if (undo != null) ({ undoLast() }) else null,
                        )
                    }
                }
                if (profileTitle.isNotBlank()) {
                    item {
                        ProfileHeroCard(
                            title = profileTitle,
                            subtitle = subtitle,
                        )
                    }
                }
                if (showTask) {
                    item {
                        TaskSummaryBlock(obj!!)
                    }
                }
                if (showTask && taskIdResolved.isNotBlank() && token != null && api != null) {
                    item {
                        val stage = obj?.let { KassaApi.taskColumn(JsonRow("", "", "", it)) }.orEmpty()
                        TaskActionsRow(
                            busy = acting,
                            stage = stage,
                            onAction = { label, status ->
                                scope.launch {
                                    acting = true
                                    actionMsg = null
                                    err = null
                                    try {
                                        val msg = withContext(Dispatchers.IO) {
                                            enqueueOrPatch(
                                                ctx, api, token, KassaApi.taskDetailPath(taskIdResolved), status, label,
                                                prevStatus = KassaApi.pick(obj!!, "status", "state"),
                                                title = KassaApi.pick(obj!!, "title", "name", "subject").ifBlank { title },
                                                rowId = taskIdResolved,
                                            )
                                        }
                                        val merged = JSONObject(obj.toString()).apply {
                                            put("status", status)
                                        }
                                        obj = merged
                                        onUpdated?.invoke(merged)
                                        actionMsg = msg
                                        Haptics.tap(ctx)
                                        tick++
                                    } catch (e: Exception) {
                                        err = e.message ?: "Не удалось обновить задачу"
                                    } finally {
                                        acting = false
                                    }
                                }
                            },
                        )
                    }
                }
                if (showTask && obj != null) {
                    item {
                        TaskChecklistBlock(obj!!)
                    }
                    item {
                        val curStatus = KassaApi.pick(obj!!, "status", "state")
                        val taskTitle = KassaApi.pick(obj!!, "title", "name", "subject").ifBlank { title }
                        TaskCommentsBlock(
                            obj = obj!!,
                            canSend = token != null && api != null && taskIdResolved.isNotBlank(),
                            currentStatus = curStatus,
                            taskId = taskIdResolved,
                            taskTitle = taskTitle,
                            busy = acting,
                            onSend = { text ->
                                val tApi = api ?: return@TaskCommentsBlock
                                val tToken = token ?: return@TaskCommentsBlock
                                val bodyText = text.trim()
                                if (bodyText.isBlank()) return@TaskCommentsBlock
                                scope.launch {
                                    acting = true
                                    actionMsg = null
                                    err = null
                                    try {
                                        val msg = withContext(Dispatchers.IO) {
                                            enqueueOrPatch(
                                                ctx,
                                                tApi,
                                                tToken,
                                                KassaApi.taskDetailPath(taskIdResolved),
                                                curStatus,
                                                "Комментарий",
                                                prevStatus = curStatus,
                                                title = taskTitle,
                                                rowId = taskIdResolved,
                                                log = false,
                                                comment = bodyText,
                                            )
                                        }
                                        actionMsg = msg
                                        Haptics.tap(ctx)
                                        tick++
                                    } catch (e: Exception) {
                                        err = e.message ?: "Не удалось отправить комментарий"
                                    } finally {
                                        acting = false
                                    }
                                }
                            },
                        )
                    }
                }
                if (showOps) {
                    item {
                        OperationSummaryBlock(obj!!)
                    }
                }
                if (showOps && opId.isNotBlank() && token != null && api != null) {
                    item {
                        OperationActionsRow(
                            busy = acting,
                            onAction = { label, status, comment ->
                                scope.launch {
                                    acting = true
                                    actionMsg = null
                                    err = null
                                    try {
                                        val msg = withContext(Dispatchers.IO) {
                                            when (status) {
                                                "CONFIRM" -> {
                                                    api.postJson(KassaApi.confirmLogisticsPath(opId), token, JSONObject())
                                                    ShiftLogStore.of(ctx).record("take", title, KassaApi.confirmLogisticsPath(opId), KassaApi.pick(obj!!, "status"), "CREATED", opId, undoable = false)
                                                    "Подтверждено"
                                                }
                                                "PROBLEM" -> {
                                                    val row = JsonRow(opId, title, "", obj!!)
                                                    api.postJson("/problem-orders", token, KassaApi.problemOrderBody(row, comment))
                                                    ShiftLogStore.of(ctx).record("problem", title, "/problem-orders", KassaApi.pick(obj!!, "status"), "PROBLEM", opId, undoable = false)
                                                    "Заказ в проблемных"
                                                }
                                                else -> enqueueOrPatch(
                                                    ctx, api, token, KassaApi.operationDetailPath(opId), status, label,
                                                    prevStatus = KassaApi.pick(obj!!, "status", "state"),
                                                    title = title,
                                                    rowId = opId,
                                                    comment = comment,
                                                )
                                            }
                                        }
                                        val merged = JSONObject(obj.toString()).apply {
                                            if (status == "CONFIRM") put("status", "CREATED")
                                        }
                                        obj = merged
                                        onUpdated?.invoke(merged)
                                        actionMsg = msg
                                        Haptics.tap(ctx)
                                        tick++
                                    } catch (e: Exception) {
                                        err = e.message ?: "Не удалось обновить статус"
                                    } finally {
                                        acting = false
                                    }
                                }
                            },
                        )
                    }
                }
                if (showOps && opId.isNotBlank()) {
                    item {
                        LocalNotesBlock(id = opId)
                    }
                }
                if (timeline.isNotEmpty()) {
                    item {
                        OperationTimelineBlock(timeline)
                    }
                }
                if (phone.isNotBlank()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            InlineActionChip("Позвонить · $phone", filled = true) {
                                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                            }
                        }
                    }
                }
                if (showOps && obj != null) {
                    item {
                        OrderShareRow(
                            obj = obj!!,
                            onCopied = { actionMsg = "Скопировано в буфер" },
                        )
                    }
                }
                if (showOps && obj != null) {
                    item {
                        ReplyTemplatesBlock(
                            obj = obj!!,
                            title = title,
                            onOpenChat = onOpenChat,
                            onCopied = { actionMsg = "Шаблон скопирован" },
                        )
                    }
                }
                obj?.let { KassaApi.chatLinkFromOperation(it) }?.let { (chatTitle, chatPath) ->
                    if (onOpenChat != null) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                InlineActionChip("Написать клиенту") { onOpenChat(chatTitle, chatPath) }
                            }
                        }
                    }
                }
                items(hero.ifEmpty { rest.take(4) }) { (k, v) -> FieldCard(k, v, emphasize = true) }
                items(if (hero.isNotEmpty()) rest else pairs.drop(4)) { (k, v) -> FieldCard(k, v, emphasize = false) }
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InlineActionChip("JSON") {
                            DeviceIntents.copy(ctx, obj!!.toString(2))
                            actionMsg = "JSON скопирован"
                            Haptics.tap(ctx)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldCard(k: String, v: String, emphasize: Boolean) {
    val ctx = LocalContext.current
    val phone = v.filter { it.isDigit() || it == '+' }
    val looksPhone = (k.contains("phone", true) || k.contains("tel", true) || k.contains("mobile", true)) && phone.length >= 6
    val looksUrl = v.startsWith("http://", true) || v.startsWith("https://", true)
    val looksEmail = v.contains("@") && !v.contains(" ") && (k.contains("mail", true) || v.contains("."))
    val looksAddr = (k.contains("address", true) || k.contains("city", true) || k.contains("street", true)) && v.length > 4
    val hint = when {
        looksPhone -> "нажатие — копия · долгое — звонок"
        looksUrl -> "нажатие — копия · долгое — открыть"
        looksEmail -> "нажатие — копия · долгое — письмо"
        looksAddr -> "нажатие — копия · долгое — карта"
        else -> ""
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (emphasize) AtColors.accentSoft else AtColors.panel)
            .combinedClickable(
                onClick = {
                    DeviceIntents.copy(ctx, v)
                    Haptics.tap(ctx)
                    Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                },
                onLongClick = when {
                    looksPhone -> ({ ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) })
                    looksUrl -> ({
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v)))
                        } catch (_: Exception) {
                        }
                    })
                    looksEmail -> ({
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$v")))
                        } catch (_: Exception) {
                        }
                    })
                    looksAddr -> ({ DeviceIntents.maps(ctx, v) })
                    else -> null
                },
            )
            .padding(14.dp),
    ) {
        Text(prettyKey(k), color = AtColors.muted, fontSize = 11.sp)
        Text(
            v,
            color = if (k.contains("status", true) || k.contains("amount", true) || k.contains("total", true)) statusTint(v) else AtColors.text,
            fontSize = if (emphasize) 18.sp else 15.sp,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (hint.isNotBlank()) {
            Text(hint, color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun ChatRemoteImage(url: String, token: String?) {
    var bmp by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        bmp = withContext(Dispatchers.IO) { ChatMedia.fetchBitmap(url, token) }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp!!.asImageBitmap(),
            contentDescription = "фото",
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)).clickable { open = true },
            contentScale = ContentScale.Crop,
        )
        if (open) {
            Dialog(onDismissRequest = { open = false }) {
                Image(
                    bitmap = bmp!!.asImageBitmap(),
                    contentDescription = "фото",
                    modifier = Modifier.fillMaxWidth().clickable { open = false },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    } else {
        Text("фото…", fontSize = 12.sp, color = AtColors.muted)
    }
}


@Composable
private fun ChatBubbleStamp(mine: Boolean, time: String, tick: String = "") {
    val color = if (mine) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f) else AtColors.muted
    Text(listOf(time, tick).filter { it.isNotBlank() }.joinToString(" · "), color = color, fontSize = 11.sp)
}

@Composable
private fun ChatLocalAudio(path: String, key: String, mine: Boolean) {
    val ctx = LocalContext.current
    val player = remember(key) { VoiceMemo(ctx) }
    var playing by remember(key) { mutableStateOf(false) }
    DisposableEffect(key) { onDispose { player.release() } }
    val color = if (mine) androidx.compose.ui.graphics.Color.White else AtColors.accent
    TextButton(onClick = {
        val f = java.io.File(path)
        if (!f.exists()) return@TextButton
        if (playing) {
            player.stopPlay()
            playing = false
        } else {
            player.play(f) { playing = false }
            playing = true
        }
    }) {
        Text(if (playing) "■ стоп" else "▶ голосовое", color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun ChatRemoteAudio(
    url: String,
    token: String?,
    mine: Boolean,
    seed: String = url,
    hintSec: Int = 0,
    api: KassaApi? = null,
) {
    val ctx = LocalContext.current
    val player = remember { VoiceMemo(ctx) }
    var file by remember(url) { mutableStateOf<java.io.File?>(null) }
    var loadErr by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(url) {
        onDispose { player.release() }
    }
    LaunchedEffect(url) {
        file = withContext(Dispatchers.IO) { ChatMedia.downloadToFile(ctx, url, token) }
        loadErr = file == null
    }
    val color = if (mine) androidx.compose.ui.graphics.Color.White else AtColors.accent
    TextButton(
        onClick = {
            val f = file ?: return@TextButton
            if (playing) {
                player.stopPlay()
                playing = false
            } else {
                try {
                    player.play(f) { playing = false }
                    playing = true
                } catch (_: Exception) {
                    loadErr = true
                }
            }
        },
        enabled = file != null,
    ) {
        Text(
            when {
                loadErr -> "голос не загрузился"
                file == null -> "голос…"
                playing -> "■ стоп"
                else -> "▶ голосовое"
            },
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

private enum class ChatBarIcon { Clip, Camera, Mic, Gallery, Send, Stop }

private data class ChatComposerTone(
    val bar: Color,
    val field: Color,
    val icon: Color,
)

@Composable
private fun chatComposerTone(): ChatComposerTone {
    val pal = LocalAtPalette.current
    return if (pal.isDark) {
        ChatComposerTone(
            bar = pal.panel,
            field = Color(0xFF1E2736),
            icon = pal.muted,
        )
    } else {
        ChatComposerTone(
            bar = pal.panel,
            field = Color(0xFFE6E8F0),
            icon = pal.muted,
        )
    }
}

@Composable
private fun ChatRoundBtn(
    onClick: () -> Unit,
    enabled: Boolean = true,
    fill: Color,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ChatAttachAction(label: String, icon: ChatBarIcon, fill: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ChatRoundBtn(onClick = onClick, fill = fill) {
            ChatLineIcon(icon, AtColors.text)
        }
        Text(label, color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ChatLineIcon(kind: ChatBarIcon, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (kind) {
            ChatBarIcon.Clip -> {
                val p = Path().apply {
                    moveTo(w * 0.62f, h * 0.18f)
                    lineTo(w * 0.28f, h * 0.52f)
                    quadraticBezierTo(w * 0.12f, h * 0.68f, w * 0.30f, h * 0.82f)
                    quadraticBezierTo(w * 0.48f, h * 0.96f, w * 0.62f, h * 0.78f)
                    lineTo(w * 0.78f, h * 0.58f)
                    quadraticBezierTo(w * 0.90f, h * 0.44f, w * 0.72f, h * 0.32f)
                    lineTo(w * 0.42f, h * 0.62f)
                }
                drawPath(p, color = color, style = s)
            }
            ChatBarIcon.Camera -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.32f),
                    size = Size(w * 0.76f, h * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = s,
                )
                drawCircle(color = color, radius = w * 0.14f, center = Offset(w * 0.50f, h * 0.58f), style = s)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.38f, h * 0.18f),
                    size = Size(w * 0.24f, h * 0.16f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = s,
                )
            }
            ChatBarIcon.Mic -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.38f, h * 0.12f),
                    size = Size(w * 0.24f, h * 0.42f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = s,
                )
                val p = Path().apply {
                    moveTo(w * 0.28f, h * 0.50f)
                    quadraticBezierTo(w * 0.28f, h * 0.78f, w * 0.50f, h * 0.78f)
                    quadraticBezierTo(w * 0.72f, h * 0.78f, w * 0.72f, h * 0.50f)
                }
                drawPath(p, color = color, style = s)
                drawLine(color, Offset(w * 0.50f, h * 0.78f), Offset(w * 0.50f, h * 0.90f), strokeWidth = s.width, cap = StrokeCap.Round)
                drawLine(color, Offset(w * 0.36f, h * 0.90f), Offset(w * 0.64f, h * 0.90f), strokeWidth = s.width, cap = StrokeCap.Round)
            }
            ChatBarIcon.Gallery -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 0.64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = s,
                )
                drawCircle(color = color, radius = w * 0.08f, center = Offset(w * 0.32f, h * 0.38f), style = s)
                val p = Path().apply {
                    moveTo(w * 0.18f, h * 0.72f)
                    lineTo(w * 0.42f, h * 0.48f)
                    lineTo(w * 0.58f, h * 0.62f)
                    lineTo(w * 0.82f, h * 0.40f)
                }
                drawPath(p, color = color, style = s)
            }
            ChatBarIcon.Send -> {
                val p = Path().apply {
                    moveTo(w * 0.18f, h * 0.82f)
                    lineTo(w * 0.18f, h * 0.18f)
                    lineTo(w * 0.86f, h * 0.50f)
                    close()
                }
                drawPath(p, color = color)
            }
            ChatBarIcon.Stop -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(w * 0.28f, h * 0.28f),
                    size = Size(w * 0.44f, h * 0.44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatPane(
    title: String,
    chatId: String = "",
    path: String = "",
    load: suspend () -> JSONObject,
    meId: String,
    api: KassaApi? = null,
    token: String? = null,
    cache: CacheStore? = null,
    onBack: () -> Unit,
    onSend: suspend (String) -> Unit,
) {
    val ctx = LocalContext.current
    val pins = remember { PinsStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    val drafts = remember { DraftsStore(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)) }
    var pinned by remember { mutableStateOf(pins.isPinned(chatId)) }
    var obj by remember { mutableStateOf<JSONObject?>(null) }
    var loadErr by remember { mutableStateOf<String?>(null) }
    var sendErr by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(drafts.get(chatId)) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    var unreadMark by remember(chatId) { mutableIntStateOf(-1) }
    var chatQ by remember(chatId) { mutableStateOf("") }
    var tplTick by remember { mutableIntStateOf(0) }
    val templates = remember { TemplatesStore.of(ctx) }
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    var locals by remember(chatId) { mutableStateOf<List<LocalPhoto>>(emptyList()) }
    var reactTarget by remember { mutableStateOf<ChatMsg?>(null) }
    val workspaceThread = path.contains("/workspace/chats")

    fun dropPhoto(item: LocalPhoto) {
        ChatMedia.deleteOutboxFile(item.filePath)
        locals = locals.filter { it.key != item.key }
    }

    suspend fun transmitPhoto(
        key: String,
        file: java.io.File,
        caption: String,
        preview: android.graphics.Bitmap?,
        voice: Boolean = false,
    ) {
        val client = api
        val t = token
        val kind = if (voice) "Голос" else "Фото"
        try {
            if (client == null || t.isNullOrBlank()) {
                sendErr = "Нет сессии"
                locals = locals.map { if (it.key == key) it.copy(failed = true) else it }
                return
            }
            if (!NetWatch.online(ctx)) {
                OutboxStore.of(ctx).enqueue(
                    if (voice) "FILE" else "PHOTO",
                    path,
                    JSONObject()
                        .put("caption", caption)
                        .put("text", caption)
                        .put("mime", if (voice) "audio/mp4" else "image/jpeg"),
                    "$kind · ${title.ifBlank { "чат" }}",
                    file.absolutePath,
                )
                locals = locals.filter { it.key != key }
                if (caption.isNotBlank()) {
                    draft = ""
                    drafts.clear(chatId)
                }
                ShiftLogStore.of(ctx).record("chat", title, path, "", "", chatId, undoable = false)
                sendErr = "$kind в очереди — уйдёт, когда появится сеть"
                Haptics.tap(ctx)
                keyboard?.hide()
                return
            }
            val ok = withContext(Dispatchers.IO) {
                val mime = when {
                    voice -> "audio/mp4"
                    file.name.endsWith(".png", true) -> "image/png"
                    file.name.endsWith(".webp", true) -> "image/webp"
                    else -> "image/jpeg"
                }
                client.sendChatFile(path, t, file, mime, caption)
            }
            if (ok) {
                if (!voice) ChatMedia.deleteOutboxFile(file.absolutePath)
                locals = locals.filter { it.key != key }
                if (caption.isNotBlank()) {
                    draft = ""
                    drafts.clear(chatId)
                }
                ShiftLogStore.of(ctx).record("chat", title, path, "", "", chatId, undoable = false)
                tick++
                Haptics.tap(ctx)
                keyboard?.hide()
            } else {
                if (locals.none { it.key == key }) {
                    locals = locals + LocalPhoto(key, preview, caption, file.absolutePath, failed = true, voice = voice)
                } else {
                    locals = locals.map { if (it.key == key) it.copy(failed = true) else it }
                }
                sendErr = "Сервер не принял — нажмите, чтобы повторить"
                Haptics.warn(ctx)
            }
        } catch (e: Exception) {
            if (e is SessionExpiredException) throw e
            if (locals.none { it.key == key }) {
                locals = locals + LocalPhoto(key, preview, caption, file.absolutePath, failed = true, voice = voice)
            } else {
                locals = locals.map { if (it.key == key) it.copy(failed = true) else it }
            }
            sendErr = e.message ?: "Не удалось отправить"
            Haptics.warn(ctx)
        } finally {
            sending = false
        }
    }

    fun retryPhoto(item: LocalPhoto) {
        if (sending || item.filePath.isBlank()) return
        val f = java.io.File(item.filePath)
        if (!f.exists()) {
            sendErr = "Файл потерян"
            locals = locals.filter { it.key != item.key }
            return
        }
        scope.launch {
            sending = true
            sendErr = null
            locals = locals.map { if (it.key == item.key) it.copy(failed = false) else it }
            transmitPhoto(item.key, f, item.caption, item.bitmap, voice = item.voice)
        }
    }

    fun sendPhoto(uri: Uri) {
        val client = api
        val t = token
        if (client == null || t.isNullOrBlank() || sending) return
        scope.launch {
            sending = true
            sendErr = null
            val key = java.util.UUID.randomUUID().toString()
            var kept: java.io.File? = null
            try {
                val prepared = withContext(Dispatchers.IO) {
                    runCatching { ChatMedia.prepareJpeg(ctx, uri) }.getOrElse {
                        ChatMedia.keepUri(ctx, uri, key).first
                    }
                }
                kept = withContext(Dispatchers.IO) { ChatMedia.keepForOutbox(ctx, prepared, key) }
                if (prepared.absolutePath != kept.absolutePath) prepared.delete()
                val preview = withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(kept.absolutePath) }
                val caption = draft.trim()
                if (preview != null) {
                    locals = locals + LocalPhoto(key, preview, caption, kept.absolutePath)
                }
                transmitPhoto(key, kept, caption, preview)
            } catch (e: Exception) {
                ChatMedia.deleteOutboxFile(kept?.absolutePath.orEmpty())
                sendErr = e.message ?: "Не удалось отправить фото"
                Haptics.warn(ctx)
                sending = false
            }
        }
    }

    fun sendVoiceFile(src: java.io.File) {
        val client = api
        val t = token
        if (client == null || t.isNullOrBlank() || sending) return
        if (!src.exists() || src.length() <= 0L) {
            sendErr = "Пустая запись"
            return
        }
        scope.launch {
            sending = true
            sendErr = null
            val key = java.util.UUID.randomUUID().toString()
            var kept: java.io.File? = null
            try {
                kept = withContext(Dispatchers.IO) { ChatMedia.keepForOutbox(ctx, src, key) }
                val caption = draft.trim()
                locals = locals + LocalPhoto(key, null, caption, kept.absolutePath, voice = true)
                transmitPhoto(key, kept, caption, null, voice = true)
            } catch (e: Exception) {
                ChatMedia.deleteOutboxFile(kept?.absolutePath.orEmpty())
                sendErr = e.message ?: "Не удалось отправить голос"
                Haptics.warn(ctx)
                sending = false
            }
        }
    }

    fun sendAttachment(uri: Uri) {
        val client = api
        val t = token
        if (client == null || t.isNullOrBlank() || sending) return
        val mimeGuess = ctx.contentResolver.getType(uri)?.lowercase().orEmpty()
        if (mimeGuess.startsWith("image/")) {
            sendPhoto(uri)
            return
        }
        scope.launch {
            sending = true
            sendErr = null
            val key = java.util.UUID.randomUUID().toString()
            var kept: java.io.File? = null
            try {
                val (file, mime) = withContext(Dispatchers.IO) { ChatMedia.keepUri(ctx, uri, key) }
                kept = file
                val caption = draft.trim()
                locals = locals + LocalPhoto(key, null, caption.ifBlank { file.name }, file.absolutePath, voice = mime.startsWith("audio"))
                if (!NetWatch.online(ctx)) {
                    OutboxStore.of(ctx).enqueue(
                        "FILE",
                        path,
                        JSONObject().put("caption", caption).put("text", caption).put("mime", mime),
                        "Файл · ${title.ifBlank { "чат" }}",
                        file.absolutePath,
                    )
                    locals = locals.filter { it.key != key }
                    sendErr = "Файл в очереди — уйдёт, когда появится сеть"
                    Haptics.tap(ctx)
                    sending = false
                    return@launch
                }
                val ok = withContext(Dispatchers.IO) { client.sendChatFile(path, t, file, mime, caption) }
                if (ok) {
                    ChatMedia.deleteOutboxFile(file.absolutePath)
                    locals = locals.filter { it.key != key }
                    tick++
                    Haptics.tap(ctx)
                } else {
                    locals = locals.map { if (it.key == key) it.copy(failed = true) else it }
                    sendErr = "Сервер не принял файл — нажмите, чтобы повторить"
                    Haptics.warn(ctx)
                }
            } catch (e: Exception) {
                ChatMedia.deleteOutboxFile(kept?.absolutePath.orEmpty())
                sendErr = e.message ?: "Не удалось отправить файл"
                Haptics.warn(ctx)
            } finally {
                sending = false
            }
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendPhoto(uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) sendAttachment(uri)
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { sendPhoto(it) }
    }
    fun startCapture() {
        try {
            val f = java.io.File(ctx.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            f.createNewFile()
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            cameraUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            sendErr = e.message ?: "Камера недоступна"
        }
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCapture()
        else Toast.makeText(ctx, "Нужен доступ к камере", Toast.LENGTH_SHORT).show()
    }
    val voice = remember { VoiceMemo(ctx) }
    var recording by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var voiceFile by remember { mutableStateOf<java.io.File?>(null) }
    var voiceStartedAt by remember { mutableLongStateOf(0L) }
    var recNow by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        while (isActive) {
            recNow = System.currentTimeMillis()
            delay(200)
        }
    }
    DisposableEffect(chatId) {
        onDispose {
            voice.release()
            recording = false
        }
    }
    fun startVoice() {
        try {
            val f = java.io.File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            voice.start(f)
            voiceFile = f
            voiceStartedAt = System.currentTimeMillis()
            recording = true
            sendErr = null
        } catch (e: Exception) {
            sendErr = e.message ?: "Не удалось начать запись"
        }
    }
    fun stopVoiceAndSend() {
        voice.stopRecord()
        recording = false
        val f = voiceFile
        voiceFile = null
        val dur = System.currentTimeMillis() - voiceStartedAt
        if (f == null || dur < 800L || f.length() < 600L) {
            f?.delete()
            sendErr = "Слишком коротко — не отправили"
            Haptics.warn(ctx)
            return
        }
        sendVoiceFile(f)
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice()
        else Toast.makeText(ctx, "Нужен доступ к микрофону", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(chatId, token) {
        if (chatId.isBlank()) return@LaunchedEffect
        cache?.markChatRead(chatId)
        val t = token
        val client = api
        if (t.isNullOrBlank() || client == null || path.isBlank()) return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) { client.markChatRead(path, t) }
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(path, token) {
        if (path.isBlank() || token.isNullOrBlank()) return@LaunchedEffect
        while (isActive) {
            delay(8_000)
            if (!PollWatcher.appForeground) continue
            try {
                val fresh = load()
                obj = fresh
            } catch (_: Exception) {
            }
        }
    }

    fun sendNow() {
        val text = draft.trim()
        if (text.isBlank() || sending) return
        attachOpen = false
        scope.launch {
            sending = true
            sendErr = null
            try {
                if (!NetWatch.online(ctx)) {
                    OutboxStore.of(ctx).enqueue("POST", KassaApi.chatSendPath(path), JSONObject().put("text", text), "Сообщение")
                    ShiftLogStore.of(ctx).record("chat", title, path, "", "", chatId, undoable = false)
                    draft = ""
                    drafts.clear(chatId)
                    sendErr = "В очереди — уйдёт, когда появится сеть"
                    Haptics.tap(ctx)
                    keyboard?.hide()
                    return@launch
                }
                onSend(text)
                ShiftLogStore.of(ctx).record("chat", title, path, "", "", chatId, undoable = false)
                draft = ""
                drafts.clear(chatId)
                tick++
                Haptics.tap(ctx)
                keyboard?.hide()
            } catch (_: Exception) {
                OutboxStore.of(ctx).enqueue("POST", KassaApi.chatSendPath(path), JSONObject().put("text", text), "Сообщение")
                ShiftLogStore.of(ctx).record("chat", title, path, "", "", chatId, undoable = false)
                draft = ""
                drafts.clear(chatId)
                Haptics.warn(ctx)
                sendErr = "Не отправилось сразу — в очереди"
            } finally {
                sending = false
            }
        }
    }

    LaunchedEffect(chatId) {
        draft = drafts.get(chatId)
    }
    LaunchedEffect(draft, chatId) {
        delay(400)
        drafts.put(chatId, draft)
    }
    LaunchedEffect(title, tick) {
        if (tick == 0) obj = null
        loadErr = null
        if (tick > 0) refreshing = true
        try {
            obj = load()
        } catch (e: Exception) {
            loadErr = e.message
        } finally {
            refreshing = false
        }
    }
    val msgs = obj?.let { KassaApi.messageList(it, meId) }
    LaunchedEffect(obj, chatId) {
        if (unreadMark < 0 && obj != null) {
            unreadMark = KassaApi.pick(obj!!, "unreadCount", "unread").toIntOrNull() ?: 0
        }
    }
    LaunchedEffect(msgs?.size, chatQ, locals.size) {
        if (chatQ.isBlank()) {
            val last = (msgs?.size ?: 0) + locals.size
            if (last > 0) listState.scrollToItem(last - 1)
        }
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().background(AtColors.bgDeep)) {
        TopLine(
            title,
            onBack,
            onRefresh = { tick++ },
            onShare = {
                val text = chatTranscript(title, msgs.orEmpty())
                if (text.isNotBlank()) {
                    DeviceIntents.share(ctx, text)
                    Haptics.tap(ctx)
                }
            },
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
        when {
            loadErr != null -> ErrorBlock(loadErr!!, onRetry = { tick++ })
            obj == null -> LoadingDetailState()
            msgs == null -> {
                val pairs = obj?.let { KassaApi.flatten(it) }.orEmpty()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pairs) { (k, v) -> FieldCard(k, v, false) }
                }
            }
            msgs.isEmpty() && locals.isEmpty() -> EmptyStateCard(
                title = if (workspaceThread) "Нет сообщений — напишите первым" else "Сообщений пока нет",
                subtitle = if (workspaceThread) {
                    "Диалог открыт. Напишите коллеге первым."
                } else {
                    "Диалог существует, но история ещё пустая или недоступна в staging."
                },
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                Column(Modifier.fillMaxSize()) {
                    if (!workspaceThread) {
                        OutlinedTextField(
                            value = chatQ,
                            onValueChange = { chatQ = it },
                            placeholder = { Text("Найти в переписке") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                        val thread = if (chatQ.isBlank()) msgs else msgs.filter {
                        it.text.contains(chatQ, true) || it.author.contains(chatQ, true) ||
                            it.imageUrl.contains(chatQ, true) || it.audioUrl.contains(chatQ, true) ||
                            it.fileName.contains(chatQ, true)
                    }
                    val phone = obj?.let { KassaApi.pick(it, "phone", "clientPhone", "mobile", "tel", "msisdn") }.orEmpty()
                    if (!workspaceThread && phone.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                            InlineActionChip("Позвонить") {
                                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                            }
                            InlineActionChip("WhatsApp") { DeviceIntents.whatsapp(ctx, phone) }
                    }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val unreadStart = if (chatQ.isBlank() && unreadMark > 0) (thread.size - unreadMark).coerceAtLeast(0) else -1
                        if (chatQ.isNotBlank() && thread.isEmpty()) {
                            item {
                                EmptyStateCard("Ничего не найдено", "Смените запрос или очистите поиск.")
                            }
                        }
                        itemsIndexed(thread) { idx, m ->
                            val mine = m.mine
                            val day = KassaApi.dayLabel(m.time)
                            val prevDay = if (idx == 0) "" else KassaApi.dayLabel(thread[idx - 1].time)
                            if (day.isNotBlank() && day != prevDay) {
                                Text(
                                    day,
                                    color = AtColors.muted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                )
                            }
                            if (idx == unreadStart) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.weight(1f).height(1.dp).background(AtColors.accent.copy(alpha = 0.35f)))
                                    Text(
                                        "Новые · $unreadMark",
                                        color = AtColors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                    )
                                    Box(Modifier.weight(1f).height(1.dp).background(AtColors.accent.copy(alpha = 0.35f)))
                                }
                            }
                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                            ) {
                                if (!mine) {
                                    Text(m.author, color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    if (!mine) {
                                        UserAvatar(
                                            name = m.author,
                                            avatarUrl = m.avatarUrl.ifBlank { obj?.let { KassaApi.chatPeerAvatar(it, meId) }.orEmpty() },
                                            api = api,
                                            token = token,
                                            size = 28.dp,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Box(
                                        Modifier.widthIn(max = 300.dp)
                                            .clip(if (mine) bubbleMine else bubbleOther)
                                            .background(if (mine) AtColors.accent else AtColors.glass)
                                            .combinedClickable(
                                                onClick = {
                                                    if (m.imageUrl.isNotBlank()) {
                                                        val abs = api?.resolveMedia(m.imageUrl) ?: m.imageUrl
                                                        try {
                                                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(abs)))
                                                        } catch (_: Exception) {
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    Haptics.tap(ctx)
                                                    if (workspaceThread) {
                                                        reactTarget = m
                                                    } else {
                                                        DeviceIntents.copy(ctx, m.text.ifBlank { m.imageUrl })
                                                        Toast.makeText(ctx, "Сообщение скопировано", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Column {
                                            if (m.imageUrl.isNotBlank()) {
                                                ChatRemoteImage(
                                                    url = api?.resolveMedia(m.imageUrl) ?: m.imageUrl,
                                                    token = token,
                                                )
                                            }
                                            if (m.audioUrl.isNotBlank()) {
                                                ChatRemoteAudio(
                                                    url = api?.resolveMedia(m.audioUrl) ?: m.audioUrl,
                                                    token = token,
                                                    mine = mine,
                                                    seed = m.id.ifBlank { m.audioUrl },
                                                    hintSec = m.audioHintSec,
                                                    api = api,
                                                )
                                            }
                                            if (m.fileName.isNotBlank() && m.audioUrl.isBlank()) {
                                                Text(
                                                    "📎 ${m.fileName}",
                                                    color = if (mine) androidx.compose.ui.graphics.Color.White else AtColors.text,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(top = if (m.imageUrl.isNotBlank() || m.audioUrl.isNotBlank()) 8.dp else 0.dp),
                                                )
                                            }
                                            val voiceCaption = m.text.equals("🎤 голосовое", true) ||
                                                m.text.equals("голосовое", true)
                                            if (m.text.isNotBlank() && !(m.audioUrl.isNotBlank() && voiceCaption)) {
                                                Text(
                                                    m.text,
                                                    color = if (mine) androidx.compose.ui.graphics.Color.White else AtColors.text,
                                                    fontSize = 15.sp,
                                                    modifier = Modifier.padding(top = if (m.imageUrl.isNotBlank() || m.audioUrl.isNotBlank() || m.fileName.isNotBlank()) 8.dp else 0.dp),
                                                )
                                            } else if (m.imageUrl.isBlank() && m.audioUrl.isBlank() && m.fileName.isBlank()) {
                                                Text("—", color = if (mine) androidx.compose.ui.graphics.Color.White else AtColors.text, fontSize = 15.sp)
                                            }
                                            ChatBubbleStamp(mine = mine, time = m.time, tick = m.tick)
                                        }
                                    }
                                }
                                if (m.reactions.isNotEmpty()) {
                                    val grouped = m.reactions.groupingBy { it }.eachCount()
                                    Text(
                                        grouped.entries.joinToString("  ") { (emoji, n) -> if (n > 1) "$emoji$n" else emoji },
                                        color = AtColors.muted,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                                    )
                                }
                            }
                        }
                        items(locals, key = { it.key }) { p ->
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                Box(
                                    Modifier.widthIn(max = 300.dp)
                                        .clip(bubbleMine)
                                        .background(AtColors.accent.copy(alpha = if (p.failed) 0.55f else 0.9f))
                                        .combinedClickable(
                                            onClick = { if (p.failed && !sending) retryPhoto(p) },
                                            onLongClick = { dropPhoto(p) },
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Column {
                                        if (p.voice) {
                                            ChatLocalAudio(p.filePath, p.key, mine = true)
                                        } else if (p.bitmap != null) {
                                            Image(
                                                bitmap = p.bitmap.asImageBitmap(),
                                                contentDescription = "фото",
                                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)),
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                        if (p.caption.isNotBlank()) {
                                            Text(p.caption, color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                                        }
                                        Text(
                                            if (p.failed) "нажмите — повторить · долгое — удалить" else "отправляем…",
                                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                        ChatBubbleStamp(mine = true, time = "", tick = if (p.failed) "" else "sent")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        if (sendErr != null) {
            ActionBanner(sendErr!!, error = true, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        val composer = chatComposerTone()
        Column(Modifier.fillMaxWidth().background(composer.bar)) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(AtColors.stroke))
            if (attachOpen && !recording) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatAttachAction("Галерея", ChatBarIcon.Gallery, composer.field) {
                        attachOpen = false
                        if (!sending) pickImage.launch("image/*")
                    }
                    ChatAttachAction("Камера", ChatBarIcon.Camera, composer.field) {
                        attachOpen = false
                        if (!sending) askCamera.launch(Manifest.permission.CAMERA)
                    }
                    ChatAttachAction("Файл", ChatBarIcon.Clip, composer.field) {
                        attachOpen = false
                        if (!sending) pickFile.launch("*/*")
                    }
                    ChatAttachAction("Голос", ChatBarIcon.Mic, composer.field) {
                        attachOpen = false
                        if (!sending) askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatRoundBtn(
                    enabled = !sending && !recording,
                    fill = composer.field,
                    onClick = { if (!sending && !recording) attachOpen = !attachOpen },
                ) { ChatLineIcon(ChatBarIcon.Clip, composer.icon) }
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(composer.field)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        val recLabel = if (recording) {
                            val sec = ((recNow - voiceStartedAt).coerceAtLeast(0) / 1000L).toInt()
                            "Запись ${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
                        } else {
                            ""
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = if (recording) recLabel else draft,
                            onValueChange = { if (!recording) draft = it },
                            enabled = !recording,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = if (recording) AtColors.danger else AtColors.text,
                                fontSize = 16.sp,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(AtColors.accent),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendNow() }),
                            decorationBox = { inner ->
                                Box {
                                    if (!recording && draft.isEmpty()) {
                                        Text(
                                            if (drafts.has(chatId)) "Черновик" else "Сообщение",
                                            color = AtColors.muted,
                                            fontSize = 16.sp,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                when {
                    recording -> ChatRoundBtn(onClick = { stopVoiceAndSend() }, fill = AtColors.danger) {
                        ChatLineIcon(ChatBarIcon.Stop, Color.White)
                    }
                    draft.isNotBlank() -> ChatRoundBtn(
                        enabled = !sending,
                        onClick = { sendNow() },
                        fill = AtColors.accent,
                    ) {
                        ChatLineIcon(ChatBarIcon.Send, Color.White)
                    }
                    else -> ChatRoundBtn(
                        enabled = !sending,
                        fill = composer.field,
                        onClick = { if (!sending) askCamera.launch(Manifest.permission.CAMERA) },
                    ) { ChatLineIcon(ChatBarIcon.Camera, composer.icon) }
                }
            }
        }
        val custom = remember(tplTick) { templates.all() }
        if (!workspaceThread) {
            QuickReplyRow(
                items = (custom + ReplyTemplates.forContext(obj, title, path)).distinct(),
                onPick = { phrase -> draft = phrase },
                onLongClick = { phrase ->
                    templates.remove(phrase)
                    tplTick++
                    Toast.makeText(ctx, "Шаблон удален", Toast.LENGTH_SHORT).show()
                },
                onSave = if (draft.trim().length >= 3) {
                    {
                        templates.add(draft.trim())
                        tplTick++
                        Haptics.tap(ctx)
                        Toast.makeText(ctx, "Шаблон сохранён", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    null
                },
            )
        }
        val reacting = reactTarget
        if (reacting != null) {
            AlertDialog(
                onDismissRequest = { reactTarget = null },
                title = { Text("Реакция") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("👍", "❤️", "😂", "🔥", "✅").forEach { emoji ->
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AtColors.glass)
                                        .clickable {
                                            val t = token
                                            val client = api
                                            val id = chatId
                                            val msgId = reacting.id
                                            reactTarget = null
                                            if (client != null && !t.isNullOrBlank() && id.isNotBlank() && msgId.isNotBlank()) {
                                                scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            client.reactToMessage(t, id, msgId, emoji)
                                                        }
                                                        tick++
                                                    } catch (e: Exception) {
                                                        sendErr = e.message ?: "Не удалось поставить реакцию"
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(emoji, fontSize = 22.sp)
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                DeviceIntents.copy(ctx, reacting.text.ifBlank { reacting.fileName.ifBlank { reacting.imageUrl } })
                                reactTarget = null
                                Toast.makeText(ctx, "Сообщение скопировано", Toast.LENGTH_SHORT).show()
                            },
                        ) { Text("Копировать", color = AtColors.accent) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { reactTarget = null }) { Text("Закрыть", color = AtColors.muted) }
                },
                containerColor = AtColors.panel,
            )
        }
    }
}

@Composable
private fun HeaderCard(user: AppUser?, onLogout: () -> Unit, onSettings: () -> Unit, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(24.dp)).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.atcrm_mark),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AT CRM", color = AtColors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(user?.fullName ?: "—", color = AtColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user?.role ?: "", color = AtColors.muted, fontSize = 13.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSearch) { Text("⌕", color = AtColors.accent, fontSize = 20.sp) }
            TextButton(onClick = onSettings) { Text("⚙", color = AtColors.muted, fontSize = 18.sp) }
            TextButton(onClick = onLogout) { Text("Выход", color = AtColors.muted) }
        }
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AtColors.glass).border(1.dp, AtColors.stroke, RoundedCornerShape(24.dp)).padding(18.dp),
    ) {
        Text(title, color = AtColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MetricsStrip(metrics: List<HomeMetric>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.forEach { metric ->
            Column(
                Modifier.weight(1f).atCard(18.dp).padding(14.dp),
            ) {
                Text(metric.label, color = AtColors.muted, fontSize = 11.sp)
                Text(metric.value, color = metricColor(metric.tone), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}

@Composable
private fun SectionLead(spec: ModuleSpec) {
    Text(
        when {
            spec.path.contains("logistics-intake") -> "Заказы из логистики Delivio. Проверьте суммы и подтвердите."
            spec.path.startsWith("/problem-orders") -> "Задержки и комментарии операторов. Можно отфильтровать по причине."
            spec.path.startsWith("/operations") -> "Список операций с фильтрами, сортировкой и редактированием"
            spec.path.startsWith("/workspace/chats") -> "Workspace: переписка, файлы, реакции"
            spec.path.startsWith("/support/threads") -> "Telegram-обращения клиентов"
            spec.path.startsWith("/workspace/tasks") -> "Процесс: постановка → работа → проверка → закрытие. Несколько исполнителей на одну задачу."
            spec.path.startsWith("/sales") -> "CRM заведений: воронка, импорт/экспорт Excel, SMS"
            spec.path.startsWith("/courier") -> "Справочник курьеров, ежедневное исполнение, доставки и зарплата"
            spec.path.startsWith("/accounting") -> "Профиль ИП, документы, продажи, отчёты"
            spec.path.startsWith("/reconciliation") -> "Акты сверки по подключённым заведениям за период."
            spec.path.startsWith("/email") -> "Корпоративная почта CRM"
            spec.path.startsWith("/support") -> "Telegram-обращения клиентов"
            spec.path.startsWith("/calls") && spec.path.contains("keypad") -> "Набор через АТС Yeastar и SMS-шлюз — как на сайте, не с телефона"
            spec.path.startsWith("/calls") && spec.path.contains("/contacts") -> "Контакты колл-центра для звонка."
            spec.path.startsWith("/sms") -> "SMS-шлюз: рассылки, логи и статус портов."
            spec.path.startsWith("/calls") -> "Delivio Call (WebRTC/SIP) и SMS-шлюз — полный функционал"
            spec.path.startsWith("/ai/") -> "Инсайты и рекомендации на основе агрегатов CRM."
            spec.path.startsWith("/establishments") -> "Справочник заведений Delivio. Фильтр по типу."
            spec.path.startsWith("/clients") || spec.path.startsWith("/marketing") -> "Клиенты, сегменты, рассылки, баннеры, промокоды и QR."
            spec.path.startsWith("/users") -> "Кто заходит в CRM и какие роли выданы."
            spec.path.contains("audit") -> "Журнал действий сотрудников."
            spec.path.startsWith("/qr") -> "QR-коды заведений и кампаний."
            spec.path.startsWith("/push") -> "Push (FCM): логи отправок и статус."
            else -> "Список раздела «${spec.title}». Нажмите строку, чтобы открыть."
        },
        color = AtColors.muted,
        fontSize = 13.sp,
    )
}

@Composable
private fun ListContextHeader(spec: ModuleSpec, count: Int) {
    val what = when {
        spec.path.contains("logistics-intake") -> "событий логистики"
        spec.path.startsWith("/problem-orders") -> "проблемных заказов"
        spec.path.startsWith("/operations") -> "заказов"
        spec.path.contains("chats") || spec.path.contains("threads") -> "диалогов"
        spec.path.startsWith("/workspace/tasks") -> "задач"
        spec.path.startsWith("/email") -> "Письма"
        spec.path.startsWith("/sales") -> "заведений CRM"
        spec.path.startsWith("/courier") -> "курьеров"
        spec.path.startsWith("/accounting") -> "записей бухгалтерии"
        spec.path.startsWith("/reconciliation") -> "актов сверки"
        spec.path.startsWith("/clients") || spec.path.startsWith("/marketing") -> "записей маркетинга"
        spec.path.startsWith("/ai/") -> "рекомендаций"
        spec.path.startsWith("/establishments") -> "заведений"
        spec.path.startsWith("/users") -> "пользователей"
        spec.path.startsWith("/sms") -> "SMS"
        spec.path.startsWith("/push") -> "push-событий"
        spec.path.startsWith("/qr") -> "QR"
        spec.path.startsWith("/calls") && spec.path.contains("contact") -> "контактов"
        spec.path.startsWith("/calls") -> "звонков"
        else -> "записей"
    }
    Text(
        "Всего: $count · стр. 1",
        color = AtColors.text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
}

@Composable
private fun QuickActionsRow(hub: Int, modules: List<ModuleSpec>, onOpen: (ModuleSpec) -> Unit) {
    val picks = when (hub) {
        0 -> listOfNotNull(modules.find { KassaApi.isOperationsList(it.path) }, modules.find { it.path.startsWith("/problem-orders") }, modules.find { it.path.startsWith("/workspace/tasks") })
        1 -> listOfNotNull(modules.find { it.path.startsWith("/workspace/chats") }, modules.find { it.path.startsWith("/support/threads") }, modules.find { it.path.startsWith("/calls/contacts") })
        2 -> listOfNotNull(modules.find { it.path.startsWith("/establishments") }, modules.find { it.path.contains("client-portraits") }, modules.find { it.path.contains("cities") })
        else -> listOfNotNull(modules.find { it.path == "/settings" }, modules.find { it.path.startsWith("/users") }, modules.find { it.path.contains("audit") })
    }.take(3)
    if (picks.isEmpty()) return
    Column {
        SectionTitle("Быстрые действия")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            picks.forEach { module ->
                Box(
                    Modifier.weight(1f).atCard(18.dp, AtColors.accentSoft).clickable { onOpen(module) }.padding(14.dp),
                ) {
                    Column {
                        Text(module.title, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(shortActionHint(module), color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NextStepCard(hub: Int, snapshot: HomeSnapshot, modules: List<ModuleSpec>, onOpen: (ModuleSpec) -> Unit) {
    val (title, subtitle, target) = when (hub) {
        0 -> Triple(
            if (snapshot.primary.isNotEmpty()) "Следующий шаг: разобрать приоритет" else "Следующий шаг: открыть поток операций",
            if (snapshot.primary.isNotEmpty()) "Сначала проблемные и свежие задачи, потом остальной хвост." else "Начните со списка операций и проверьте активную очередь.",
            modules.find { it.path.contains("logistics-intake") } ?: modules.firstOrNull(),
        )
        1 -> Triple(
            if (snapshot.primary.isNotEmpty()) "Следующий шаг: ответить в диалогах" else "Следующий шаг: открыть коммуникации",
            "Сфокусируйтесь на последних касаниях, чтобы не терять ответ клиенту.",
            modules.find { it.path.startsWith("/workspace/chats") } ?: modules.firstOrNull(),
        )
        2 -> Triple(
            "Следующий шаг: проверить точки сети",
            "Провалитесь в заведения и посмотрите проблемные профили и активность.",
            modules.find { it.path.startsWith("/establishments") } ?: modules.firstOrNull(),
        )
        else -> Triple(
            "Следующий шаг: контроль доступа и системы",
            "Проверьте пользователей, аудит и служебные статусы.",
            modules.find { it.path == "/settings" } ?: modules.firstOrNull(),
        )
    }
    Column(
        Modifier.fillMaxWidth().atCard(20.dp).padding(16.dp),
    ) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        if (target != null) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                InlineActionChip("Перейти") { onOpen(target) }
            }
        }
    }
}

@Composable
private fun HomeRowCard(
    row: JsonRow,
    spec: ModuleSpec?,
    hub: Int,
    onOpenRow: (ModuleSpec, JsonRow) -> Unit,
    pinned: Boolean = false,
    onPin: (() -> Unit)? = null,
    hasDraft: Boolean = false,
    draftText: String = "",
    actingId: String? = null,
    onQuickOp: ((JsonRow, String, String) -> Unit)? = null,
    onQuickTask: ((JsonRow, String, String) -> Unit)? = null,
) {
    when (hub) {
        0 -> if (spec?.path?.startsWith("/workspace/tasks") == true) {
            TaskCard(
                row = row,
                onOpen = { spec.let { onOpenRow(it, row) } },
                busy = actingId == row.id,
                onDone = if (!KassaApi.isTaskDone(row) && onQuickTask != null) {
                    { onQuickTask(row, "DONE", "Готово") }
                } else {
                    null
                },
            )
        } else if (spec?.path?.startsWith("/problem-orders") == true) {
            ProblemOrderCard(row = row, onOpen = { spec.let { onOpenRow(it, row) } })
        } else {
            OperationCard(
                row = row,
                onOpen = { spec?.let { onOpenRow(it, row) } },
                busy = actingId == row.id,
                onConfirm = onQuickOp?.let { { it(row, "CONFIRM", "Подтвердить") } },
                onProblem = onQuickOp?.let { { it(row, "PROBLEM", "Проблема") } },
            )
        }
        1 -> {
            val dir = KassaApi.callDirection(row.raw)
            if (dir == "missed" || spec?.path?.startsWith("/calls") == true) {
                val ctx = LocalContext.current
                val phone = KassaApi.phoneOf(row.raw)
                CallLogCard(
                    row = row,
                    onOpen = { spec?.let { onOpenRow(it, row) } },
                    onDial = {
                        if (phone.isNotBlank()) {
                            ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        }
                    },
                )
            } else {
                ConversationCard(
                    row = row,
                    pinned = pinned,
                    hasDraft = hasDraft,
                    draftText = draftText,
                    onOpen = { spec?.let { onOpenRow(it, row) } },
                    onPin = onPin,
                )
            }
        }
        2 -> EstablishmentCard(row = row, onOpen = { spec?.let { onOpenRow(it, row) } })
        else -> GenericRowCard(row = row, onOpen = { spec?.let { onOpenRow(it, row) } })
    }
}

@Composable
private fun ModuleShortcutCard(module: ModuleSpec, onOpen: (ModuleSpec) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(radius).background(AtColors.panel).border(1.dp, AtColors.stroke, radius).clickable { onOpen(module) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
            Text(module.title.take(1), color = AtColors.accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(module.title, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(moduleHint(module), color = AtColors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = AtColors.muted, fontSize = 22.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OperationCard(
    row: JsonRow,
    onOpen: () -> Unit,
    onMessage: (() -> Unit)? = null,
    busy: Boolean = false,
    onConfirm: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onTake: (() -> Unit)? = null,
    onProblem: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val status = bestStatus(row)
    val amount = formatMoney(bestAmount(row))
    val client = KassaApi.clientName(row.raw)
    val point = KassaApi.establishmentName(row.raw)
    val phone = KassaApi.phoneOf(row.raw)
    val orderNo = KassaApi.orderNo(row.raw).ifBlank { row.title }
    val payment = KassaApi.paymentLabel(KassaApi.pick(row.raw, "paymentType", "payment"))
    val whenAt = KassaApi.prettyTime(KassaApi.operationWhen(row.raw))
    Column(
        Modifier
            .fillMaxWidth()
            .atCard(20.dp)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    DeviceIntents.copy(ctx, orderNo)
                    Haptics.tap(ctx)
                    Toast.makeText(ctx, "Номер скопирован", Toast.LENGTH_SHORT).show()
                },
            )
            .padding(16.dp),
    ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (orderNo.startsWith("№")) orderNo else "№ $orderNo",
                    color = AtColors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(prettyStatus(status.ifBlank { "CREATED" }))
            }
            val line2 = listOf(point.ifBlank { client }, amount).filter { it.isNotBlank() }.joinToString("  ·  ")
            if (line2.isNotBlank()) {
                Text(line2, color = AtColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
            }
            if (client.isNotBlank() && client != point) {
                Text(client, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            val foot = listOf(whenAt, payment, KassaApi.pick(row.raw, "phone", "clientPhone")).filter { it.isNotBlank() }.joinToString("  ·  ")
            if (foot.isNotBlank()) {
                Text(foot, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            val showConfirm = onConfirm != null && KassaApi.needsConfirm(row)
            val showRetry = onRetry != null && KassaApi.needsRetry(row)
            val showCall = phone.isNotBlank()
            val showProblem = onProblem != null && !KassaApi.isOperationDone(row)
            val showMsg = onMessage != null
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showConfirm) {
                    InlineActionChip(if (busy) "..." else "Подтвердить", filled = true) { if (!busy) onConfirm!!() }
                }
                if (showRetry) {
                    InlineActionChip(if (busy) "..." else "Повторить") { if (!busy) onRetry!!() }
                }
                if (showMsg) {
                    InlineActionChip("Написать") { onMessage!!() }
                }
                if (showCall) {
                    InlineActionChip(
                        "Позвонить",
                        onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) },
                        onLongClick = {
                            DeviceIntents.copy(ctx, phone)
                            Haptics.tap(ctx)
                            Toast.makeText(ctx, "Номер скопирован", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                if (showProblem) {
                    InlineActionChip("Проблема") { if (!busy) onProblem!!() }
                }
            }
    }
}

@Composable
private fun ProblemOrderCard(row: JsonRow, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val orderNo = KassaApi.orderNo(row.raw).ifBlank { row.title }
    val point = KassaApi.establishmentName(row.raw)
    val delayMin = KassaApi.problemDelayMinutes(row.raw)
    val reason = KassaApi.problemReasonLabel(KassaApi.pick(row.raw, "reason", "delayReason"))
    val comment = KassaApi.problemComment(row.raw)
    val whenAt = KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "updatedAt"))
    val operator = KassaApi.problemOperator(row.raw)
    Column(
        Modifier.fillMaxWidth().atCard(16.dp).clickable(onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (orderNo.startsWith("№")) orderNo else "№ $orderNo",
                color = AtColors.text,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusChip(reason.ifBlank { "Другое" })
        }
        if (point.isNotBlank()) {
            Text(point, color = AtColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            listOf(
                "${KassaApi.prettyNumber(delayMin.toString())} мин",
                KassaApi.delayHms(delayMin),
                whenAt,
                operator,
            ).filter { it.isNotBlank() }.joinToString(" · "),
            color = AtColors.muted,
            fontSize = 12.sp,
        )
        if (comment.isNotBlank() && comment != reason) {
            Text(comment, color = AtColors.muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InlineActionChip("Копировать №") {
                DeviceIntents.copy(ctx, orderNo)
                Haptics.tap(ctx)
                Toast.makeText(ctx, "Номер скопирован", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun TaskBoardHeader(all: List<JsonRow>, shown: Int) {
    val active = all.count { !KassaApi.isTaskDone(it) }
    val overdue = all.count { !KassaApi.isTaskDone(it) && KassaApi.isOverdue(KassaApi.taskDueRaw(it)) }
    val closed = all.count { KassaApi.isTaskDone(it) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Всего: $shown", color = AtColors.text, fontSize = 13.sp)
        Box(
            Modifier.clip(chipRadius).background(AtColors.success.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("Активных: $active", color = AtColors.success, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            Modifier.clip(chipRadius).background(AtColors.danger.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text("Просроченных: $overdue", color = AtColors.danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("Закрытых: $closed", color = AtColors.muted, fontSize = 13.sp)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TaskKanbanBoard(
    rows: List<JsonRow>,
    onOpen: (JsonRow) -> Unit,
    onSetStatus: (JsonRow, String, String) -> Unit,
) {
    val cols = listOf(
        "new" to "Новая",
        "progress" to "В работе",
        "review" to "На проверке",
        "done" to "Закрыто",
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        cols.forEach { (key, title) ->
            val items = rows.filter { KassaApi.taskColumn(it) == key }
            Column(
                Modifier.width(260.dp).atCard(12.dp).padding(10.dp),
            ) {
                Text(
                    "$title  ${items.size}",
                    color = AtColors.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (items.isEmpty()) {
                    Text("Пусто", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                } else {
                    items.take(12).forEach { row ->
                        TaskCard(
                            row = row,
                            onOpen = { onOpen(row) },
                            compact = true,
                            onSetStatus = { r, status, label -> onSetStatus(r, status, label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    row: JsonRow,
    onOpen: () -> Unit,
    busy: Boolean = false,
    onDone: (() -> Unit)? = null,
    compact: Boolean = false,
    onSetStatus: ((JsonRow, String, String) -> Unit)? = null,
) {
    val status = KassaApi.pick(row.raw, "status", "state").ifBlank { "В работе" }
    val dueRaw = KassaApi.taskDueRaw(row)
    val remaining = KassaApi.remainingLabel(dueRaw)
    val assignee = KassaApi.taskAssignees(row)
    val checklist = KassaApi.pick(row.raw, "checklistProgress", "checklist")
    val done = KassaApi.isTaskDone(row)
    val stage = KassaApi.taskColumn(row)
    val moves = when (stage) {
        "new" -> listOf(
            "IN_PROGRESS" to "В работу",
            "IN_REVIEW" to "На проверке",
        )
        "progress" -> listOf(
            "IN_REVIEW" to "На проверке",
            "DONE" to "Закрыть",
        )
        "review" -> listOf(
            "IN_PROGRESS" to "В работу",
            "DONE" to "Закрыть",
        )
        "done" -> listOf(
            "IN_PROGRESS" to "Открыть снова",
        )
        else -> emptyList()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 8.dp else 0.dp)
            .atCard(12.dp)
            .clickable { onOpen() }
            .padding(if (compact) 10.dp else 14.dp),
    ) {
        StatusChip(prettyStatus(status))
        Text(
            row.title,
            color = AtColors.text,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 14.sp else 16.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (assignee.isNotBlank()) {
            Text(assignee, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (remaining.isNotBlank()) {
            Text(remaining, color = AtColors.text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (checklist.isNotBlank() && checklist.length < 24) {
            Text("Чек-лист $checklist", color = AtColors.text, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (compact && onSetStatus != null && moves.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                moves.forEach { (targetStatus, label) ->
                    InlineActionChip(
                        text = label,
                        filled = targetStatus == "DONE",
                        onClick = { onSetStatus(row, targetStatus, label) },
                    )
                }
            }
        }
        Text(
            if (done) "Открыть" else KassaApi.taskNextLabel(row),
            color = AtColors.accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp).clickable { onOpen() },
        )
        if (onDone != null && !compact) {
            Row(Modifier.padding(top = 8.dp)) {
                InlineActionChip(if (busy) "..." else "Готово") { if (!busy) onDone() }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationCard(
    row: JsonRow,
    onOpen: () -> Unit,
    pinned: Boolean = false,
    onPin: (() -> Unit)? = null,
    hasDraft: Boolean = false,
    draftText: String = "",
    onMarkRead: (() -> Unit)? = null,
    api: KassaApi? = null,
    token: String? = null,
    meId: String = "",
) {
    val who = KassaApi.chatTitle(row.raw).ifBlank { KassaApi.displayTitle(row) }
    val last = KassaApi.previewText(row).ifBlank { row.subtitle }.ifBlank { "Нет сообщений — напишите первым" }
    val preview = if (hasDraft && draftText.isNotBlank()) "Черновик: $draftText" else last
    val stamp = KassaApi.pick(row.raw, "lastMessageAt", "updatedAt", "createdAt").let(KassaApi::prettyTime)
    val kind = KassaApi.chatKind(row.raw)
    val unread = KassaApi.unreadCount(row)
    val ctx = LocalContext.current
    val avatarUrl = KassaApi.chatPeerAvatar(row.raw, meId)
    Row(
        Modifier
            .fillMaxWidth()
            .atCard(16.dp, color = if (unread > 0) AtColors.accentSoft else AtColors.panel)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    when {
                        onMarkRead != null && unread > 0 -> {
                            onMarkRead()
                            Haptics.tap(ctx)
                            Toast.makeText(ctx, "Прочитано", Toast.LENGTH_SHORT).show()
                        }
                        onPin != null -> {
                            onPin()
                            Haptics.tap(ctx)
                        }
                    }
                },
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            UserAvatar(
                name = who,
                avatarUrl = avatarUrl,
                api = api,
                token = token,
                size = 44.dp,
            )
            if (pinned) {
                Text(
                    "★",
                    color = AtColors.warning,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    who,
                    color = AtColors.text,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (stamp.isNotBlank()) {
                    Text(stamp.takeLast(5), color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            val meta = listOfNotNull(
                kind.takeIf { it.isNotBlank() },
                if (hasDraft) "черновик" else null,
            ).joinToString(" · ")
            Text(
                preview,
                color = AtColors.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (meta.isNotBlank()) {
                Text(meta, color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
            }
        }
        if (unread > 0) {
            Box(
                Modifier.padding(start = 8.dp).clip(CircleShape).background(AtColors.accent).size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unread > 99) "99+" else unread.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun CallLogCard(row: JsonRow, onOpen: () -> Unit, onDial: () -> Unit) {
    val phone = KassaApi.phoneOf(row.raw)
    val dir = KassaApi.callDirection(row.raw)
    val dirLabel = KassaApi.callDirectionLabel(dir).ifBlank { KassaApi.pick(row.raw, "direction", "kind", "status").ifBlank { "Звонок" } }
    val duration = KassaApi.callDuration(row.raw)
    val stamp = KassaApi.pick(row.raw, "startedAt", "createdAt", "updatedAt", "time").let(KassaApi::prettyTime)
    val who = bestClient(row).ifBlank { row.title }
    val tone = when (dir) {
        "missed" -> AtColors.danger
        "in" -> AtColors.success
        "out" -> AtColors.accent
        else -> AtColors.muted
    }
    Column(
        Modifier.fillMaxWidth().atCard(20.dp).clickable { onOpen() }.padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Text(
                    when (dir) {
                        "missed" -> "×"
                        "in" -> "↓"
                        "out" -> "↑"
                        else -> "☎"
                    },
                    color = tone,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(who, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(dirLabel, phone, duration).filter { it.isNotBlank() }.joinToString(" · "),
                    color = AtColors.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (stamp.isNotBlank()) {
                Text(stamp.takeLast(5), color = AtColors.muted, fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (phone.isNotBlank()) InlineActionChip("Перезвонить") { onDial() }
            InlineActionChip("Карточка") { onOpen() }
        }
    }
}

@Composable
private fun ContactCard(row: JsonRow, onOpen: () -> Unit, onDial: () -> Unit) {
    val phone = KassaApi.phoneOf(row.raw)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)).clickable { onOpen() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
            Text(row.title.take(1), color = AtColors.accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.subtitle.ifBlank { phone }, color = AtColors.muted, fontSize = 12.sp)
        }
        if (phone.isNotBlank()) {
            InlineActionChip("Звонок") { onDial() }
        }
    }
}

@Composable
private fun EstablishmentCard(row: JsonRow, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val city = KassaApi.pick(row.raw, "city", "address")
    val status = KassaApi.pick(row.raw, "status", "state")
    val phone = KassaApi.phoneOf(row.raw)
    val mapQuery = DeviceIntents.geoOrAddress(row.raw)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(20.dp)).clickable { onOpen() }.padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (status.isNotBlank()) StatusChip(status)
        }
        if (city.isNotBlank()) {
            Text(city, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        if (phone.isNotBlank()) {
            Text(phone, color = AtColors.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (row.subtitle.isNotBlank()) {
            Text(row.subtitle, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (phone.isNotBlank()) {
                InlineActionChip("Позвонить") {
                    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }
            }
            if (mapQuery.isNotBlank()) {
                InlineActionChip("Карта") { DeviceIntents.maps(ctx, mapQuery) }
            }
            InlineActionChip("Профиль") { onOpen() }
        }
    }
}

@Composable
private fun MailCard(row: JsonRow, onOpen: () -> Unit) {
    val from = KassaApi.pick(row.raw, "from", "fromName", "sender", "mailbox").ifBlank { row.title }
    val subject = KassaApi.pick(row.raw, "subject", "title", "theme").ifBlank { row.title }
    val stamp = KassaApi.pick(row.raw, "date", "createdAt", "receivedAt", "updatedAt").let(KassaApi::prettyTime)
    val unread = KassaApi.pick(row.raw, "unread", "isUnread", "seen") in setOf("true", "1", "yes") ||
        (KassaApi.unreadCount(row) > 0)
    Column(
        Modifier
            .fillMaxWidth()
            .atCard(16.dp, if (unread) AtColors.accentSoft else AtColors.panel)
            .clickable { onOpen() }
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
                Text(from.take(1).uppercase(), color = AtColors.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(from, color = AtColors.text, fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subject, color = AtColors.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            }
            if (stamp.isNotBlank()) Text(stamp.takeLast(5), color = AtColors.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ClientCard(row: JsonRow, onOpen: () -> Unit) {
    val phone = KassaApi.phoneOf(row.raw)
    val city = KassaApi.pick(row.raw, "city", "cityKey", "address")
    val orders = KassaApi.pick(row.raw, "ordersCount", "orderCount", "orders", "totalOrders")
    val status = KassaApi.pick(row.raw, "status", "segment", "kind", "type")
    Column(Modifier.fillMaxWidth().atCard(18.dp).clickable { onOpen() }.padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
                Text(row.title.take(1).uppercase(), color = AtColors.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOf(phone, city, orders.takeIf { it.isNotBlank() }?.let { "$it зак." }).filter { !it.isNullOrBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                else if (row.subtitle.isNotBlank()) Text(row.subtitle, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (status.isNotBlank()) StatusChip(status)
        }
    }
}

@Composable
private fun InsightCard(row: JsonRow, onOpen: () -> Unit) {
    val score = KassaApi.pick(row.raw, "score", "priority", "impact", "kind")
    val body = KassaApi.pick(row.raw, "text", "body", "recommendation", "insight", "description").ifBlank { row.subtitle }
    Column(Modifier.fillMaxWidth().atCard(18.dp).clickable { onOpen() }.padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("✦", color = AtColors.accent, fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Text(row.title, color = AtColors.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (score.isNotBlank()) StatusChip(score)
        }
        if (body.isNotBlank()) {
            Text(body, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AuditCard(row: JsonRow, onOpen: () -> Unit) {
    val who = KassaApi.pick(row.raw, "userName", "actor", "fullName", "email", "user").ifBlank { row.title }
    val action = KassaApi.pick(row.raw, "action", "event", "type", "kind").ifBlank { row.subtitle }
    val stamp = KassaApi.pick(row.raw, "createdAt", "at", "time", "updatedAt").let(KassaApi::prettyTime)
    Column(Modifier.fillMaxWidth().atCard(16.dp).clickable { onOpen() }.padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(who, color = AtColors.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stamp.isNotBlank()) Text(stamp, color = AtColors.muted, fontSize = 11.sp)
        }
        if (action.isNotBlank()) Text(action, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CourierCard(row: JsonRow, onOpen: () -> Unit) {
    val phone = KassaApi.phoneOf(row.raw)
    val city = KassaApi.pick(row.raw, "city", "zone", "area")
    val status = KassaApi.pick(row.raw, "status", "state", "online")
    val pay = KassaApi.pick(row.raw, "amount", "payroll", "salary", "total")
    val trips = KassaApi.pick(row.raw, "deliveries", "trips", "ordersCount")
    Column(Modifier.fillMaxWidth().atCard(18.dp).clickable { onOpen() }.padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
                Text(row.title.take(1).uppercase(), color = AtColors.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOf(phone, city, trips.takeIf { it.isNotBlank() }?.let { "$it дост." }, pay).filter { !it.isNullOrBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = AtColors.muted, fontSize = 12.sp, maxLines = 2)
                else if (row.subtitle.isNotBlank()) Text(row.subtitle, color = AtColors.muted, fontSize = 12.sp, maxLines = 2)
            }
            if (status.isNotBlank()) StatusChip(status)
        }
    }
}

@Composable
private fun DocumentCard(row: JsonRow, onOpen: () -> Unit) {
    val status = KassaApi.pick(row.raw, "status", "state", "kind", "type", "role")
    val amount = KassaApi.pick(row.raw, "amount", "total", "sum", "turnover")
    val party = KassaApi.pick(row.raw, "counterparty", "counterpartyName", "partner", "employeeName", "clientName", "establishmentName")
    val stamp = KassaApi.pick(row.raw, "date", "period", "createdAt", "updatedAt", "number", "docNumber").let {
        if (it.contains("T") || it.contains("-")) KassaApi.prettyTime(it) else it
    }
    Column(Modifier.fillMaxWidth().atCard(18.dp).clickable { onOpen() }.padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
                Text(row.title.take(1).uppercase(), color = AtColors.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOf(party, amount.takeIf { it.isNotBlank() }?.let { "$it ₸" }, stamp).filter { !it.isNullOrBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                else if (row.subtitle.isNotBlank()) Text(row.subtitle, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (status.isNotBlank()) StatusChip(status)
        }
    }
}

@Composable
private fun GenericRowCard(row: JsonRow, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val phone = KassaApi.phoneOf(row.raw)
    val mapQuery = DeviceIntents.geoOrAddress(row.raw)
    val status = KassaApi.pick(row.raw, "status", "state", "role", "kind", "type")
    Column(
        Modifier
            .fillMaxWidth()
            .atCard(18.dp)
            .clickable { onOpen() }
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
                Text(row.title.take(1).uppercase(), color = AtColors.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (row.subtitle.isNotBlank()) {
                    Text(row.subtitle, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (status.isNotBlank()) StatusChip(status)
        }
        if (phone.isNotBlank() || mapQuery.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (phone.isNotBlank()) {
                    InlineActionChip("Позвонить") {
                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                    }
                    InlineActionChip("WhatsApp") { DeviceIntents.whatsapp(ctx, phone) }
                }
                if (mapQuery.isNotBlank()) {
                    InlineActionChip("Карта") { DeviceIntents.maps(ctx, mapQuery) }
                }
                InlineActionChip("Открыть") { onOpen() }
            }
        }
    }
}

@Composable
private fun HubNavIcon(selected: Boolean, count: Int) {
    BadgedBox(
        badge = {
            if (count > 0) {
                Badge(containerColor = AtColors.danger, contentColor = Color.White) {
                    Text(if (count > 99) "99+" else count.toString(), fontSize = 10.sp)
                }
            }
        },
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (selected) AtColors.accent else AtColors.muted.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun RecentCard(item: RecentItem, onOpen: () -> Unit) {
    val whenText = if (item.at > 0) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.at))
    } else {
        ""
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(16.dp)).clickable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(item.kindLabel())
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title.ifBlank { "Без названия" }, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, color = AtColors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (whenText.isNotBlank()) {
            Text(whenText, color = AtColors.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OrderShareRow(obj: JSONObject, onCopied: () -> Unit) {
    val ctx = LocalContext.current
    val addr = DeviceIntents.geoOrAddress(obj)
    val share = DeviceIntents.orderShareText(obj)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Text("Быстрые действия", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Карта, SMS, буфер и шаринг карточки заказа.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        val phone = KassaApi.phoneOf(obj)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (addr.isNotBlank()) {
                InlineActionChip("Карта") { DeviceIntents.maps(ctx, addr) }
            }
            if (phone.isNotBlank()) {
                InlineActionChip("SMS") { DeviceIntents.sms(ctx, phone) }
                InlineActionChip("WhatsApp") { DeviceIntents.whatsapp(ctx, phone) }
            }
            InlineActionChip("Копировать") {
                DeviceIntents.copy(ctx, share)
                onCopied()
            }
            InlineActionChip("Поделиться") { DeviceIntents.share(ctx, share) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InlineActionChip(
    text: String,
    filled: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(30.dp)
            .clip(chipRadius)
            .background(if (filled) AtColors.accent else AtColors.accentSoft)
            .then(
                if (onLongClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                else Modifier.clickable(onClick = onClick),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (filled) androidx.compose.ui.graphics.Color.White else AtColors.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FilterChips(
    value: String,
    items: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (key, label) ->
            val selected = value == key
            Box(
                Modifier
                    .height(30.dp)
                    .clip(chipRadius)
                    .background(
                        when {
                            selected -> AtColors.accent
                            else -> AtColors.panel
                        },
                    )
                    .border(0.5.dp, if (selected) AtColors.accent else AtColors.stroke, chipRadius)
                    .clickable { onChange(key) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) androidx.compose.ui.graphics.Color.White else AtColors.text,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GlobalSearchPane(
    api: KassaApi,
    token: String?,
    cache: CacheStore,
    onBack: () -> Unit,
    onOpen: (SearchHit) -> Unit,
) {
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    var recents by remember { mutableStateOf(SearchRecents.load(ctx)) }
    LaunchedEffect(q, token, retry) {
        err = null
        if (q.trim().length < 2) {
            hits = emptyList()
            loading = false
            recents = SearchRecents.load(ctx)
            return@LaunchedEffect
        }
        hits = GlobalSearch.searchCached(cache, q)
        if (token.isNullOrBlank()) return@LaunchedEffect
        loading = true
        delay(350)
        try {
            hits = withContext(Dispatchers.IO) { GlobalSearch.search(api, token, q) }
            SearchRecents.push(ctx, q)
            recents = SearchRecents.load(ctx)
        } catch (e: Exception) {
            err = e.message
            if (hits.isEmpty()) hits = GlobalSearch.searchCached(cache, q)
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Поиск", onBack)
        OutlinedTextField(
            value = q,
            onValueChange = { q = it },
            placeholder = { Text("Заказ, клиент, телефон, задача, чат…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            colors = fieldColors(),
            shape = RoundedCornerShape(10.dp),
        )
        Text(
            "Минимум 2 символа · заказы, задачи, чаты, заведения",
            color = AtColors.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )
        when {
            err != null && hits.isEmpty() -> ErrorBlock(err!!, onRetry = { retry++ })
            q.trim().length < 2 -> {
                if (recents.isNotEmpty()) {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Text("Недавние запросы", color = AtColors.muted, fontSize = 12.sp)
                        }
                        items(recents) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .atCard(16.dp)
                                    .clickable { q = item }
                                    .padding(14.dp),
                            ) {
                                Text(item, color = AtColors.text, fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    EmptyStateCard(
                        title = "Глобальный поиск",
                        subtitle = "Начните вводить номер заказа, имя клиента, телефон или тему задачи.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            loading && hits.isEmpty() -> LoadingCard()
            hits.isEmpty() -> EmptyStateCard(
                title = "Ничего не найдено",
                subtitle = "Попробуйте другой запрос или проверьте написание.",
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (loading) {
                    item {
                        Text("Обновляем…", color = AtColors.muted, fontSize = 12.sp)
                    }
                }
                items(hits) { hit ->
                    SearchHitCard(hit = hit, onOpen = { onOpen(hit) })
                }
            }
        }
    }
}

@Composable
private fun SearchHitCard(hit: SearchHit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .atCard(18.dp)
            .clickable { onOpen() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(hit.category)
                Spacer(Modifier.width(8.dp))
                Text(hit.row.title, color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hit.row.subtitle.isNotBlank()) {
                Text(hit.row.subtitle, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Text("›", color = AtColors.muted, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TaskSummaryBlock(obj: JSONObject) {
    val status = KassaApi.pick(obj, "status", "state")
    val title = KassaApi.pick(obj, "title", "name", "subject")
    val assignee = KassaApi.pick(obj, "assigneeName", "assignedTo", "username", "ownerName")
    val due = KassaApi.pick(obj, "dueAt", "deadline", "updatedAt").let(KassaApi::prettyTime)
    val desc = KassaApi.pick(obj, "description", "text", "note")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Карточка задачи", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            if (status.isNotBlank()) StatusChip(status)
        }
        if (title.isNotBlank()) SummaryPill("Задача", title, Modifier.fillMaxWidth())
        if (desc.isNotBlank()) SummaryPill("Описание", desc, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (assignee.isNotBlank()) SummaryPill("Исполнитель", assignee, Modifier.weight(1f))
            if (due.isNotBlank()) SummaryPill("Срок", due, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TaskActionsRow(
    busy: Boolean,
    stage: String,
    onAction: (label: String, status: String) -> Unit,
) {
    val actions = when (stage) {
        "new" -> listOf(
            "В работу" to "IN_PROGRESS",
            "На проверке" to "IN_REVIEW",
        )

        "progress" -> listOf(
            "На проверке" to "IN_REVIEW",
            "Закрыть" to "DONE",
        )

        "review" -> listOf(
            "В работу" to "IN_PROGRESS",
            "Закрыть" to "DONE",
        )

        "done" -> listOf(
            "Открыть снова" to "IN_PROGRESS",
        )

        else -> emptyList()
    }
    Column(
        Modifier.fillMaxWidth().atCard(18.dp).padding(14.dp),
    ) {
        Text("Действия по задаче", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        LazyRow(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(actions) { (label, status) ->
                InlineActionChip(label, filled = status == "DONE") { if (!busy) onAction(label, status) }
            }
        }
    }
}

@Composable
private fun TaskChecklistBlock(obj: JSONObject) {
    val arr = obj.optJSONArray("checklist")
        ?: obj.optJSONArray("checklistItems")
        ?: obj.optJSONArray("items")

    // Some endpoints return only "checklistProgress" string.
    val progressText = KassaApi.pick(obj, "checklistProgress", "checklist").trim()
    if (arr == null && progressText.isBlank()) return

    val items = arr?.let { a ->
        List(a.length()) { i ->
            val it = a.optJSONObject(i)
            val id = KassaApi.pick(it ?: JSONObject(), "id").ifBlank { a.optJSONObject(i)?.optString("key").orEmpty() }
            val title = KassaApi.pick(it ?: JSONObject(), "title", "name", "text", "label")
            val done = it?.optBoolean("isDone", false) == true || it?.optBoolean("done", false) == true || it?.optBoolean("checked", false) == true
            Triple(id, title, done)
        }
    }.orEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .atCard(18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Чек-лист", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (progressText.isNotBlank() && items.isEmpty()) {
            Text(progressText, color = AtColors.muted, fontSize = 13.sp)
        } else if (items.isNotEmpty()) {
            val total = items.size
            val doneN = items.count { it.third }
            Text("$doneN/$total выполнено", color = AtColors.muted, fontSize = 13.sp)
            items.forEach { (_, title, done) ->
                val icon = if (done) "✅" else "⬜"
                if (title.isNotBlank()) {
                    Text("$icon  $title", color = AtColors.text, fontSize = 13.sp)
                }
            }
        } else {
            Text("Пока пусто", color = AtColors.muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun TaskCommentsBlock(
    obj: JSONObject,
    canSend: Boolean,
    currentStatus: String,
    taskId: String,
    taskTitle: String,
    busy: Boolean,
    onSend: (String) -> Unit,
) {
    val arr = obj.optJSONArray("comments") ?: obj.optJSONArray("comment")
    val comments = arr?.let { a ->
        List(a.length()) { i ->
            val c = a.optJSONObject(i)
            val id = c?.optString("id").orEmpty()
            val authorObj = c?.optJSONObject("author")
            val author = authorObj?.optString("fullName")
                ?: authorObj?.optString("name")
                ?: authorObj?.optString("username")
                ?: c?.optString("authorName")
                ?: "—"
            val text = KassaApi.pick(c ?: JSONObject(), "text", "message", "comment", "body", "note")
            val createdAt = KassaApi.pick(c ?: JSONObject(), "createdAt", "time", "updatedAt").ifBlank { "" }
            val filesArr = c?.optJSONArray("files")
            val files = filesArr?.let { fa ->
                List(fa.length()) { fi ->
                    val f = fa.optJSONObject(fi)
                    f?.optString("originalName")
                        ?: f?.optString("fileName")
                        ?: f?.optString("name")
                        ?: ""
                }.filter { it.isNotBlank() }
            }.orEmpty()
            Triple(id, author, Pair(text, Pair(createdAt, files)))
        }
    }.orEmpty()

    var draft by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .atCard(18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Комментарии", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)

        if (comments.isEmpty()) {
            Text("Пока нет комментариев.", color = AtColors.muted, fontSize = 13.sp)
        } else {
            comments.take(10).forEach { (id, author, payload) ->
                val (text, createdAndFiles) = payload
                val (createdAt, files) = createdAndFiles
                Text(author, color = AtColors.muted, fontSize = 12.sp)
                if (text.isNotBlank()) {
                    Text(text, color = AtColors.text, fontSize = 13.sp)
                }
                if (files.isNotEmpty()) {
                    Text("Файлы: ${files.joinToString(", ")}", color = AtColors.muted, fontSize = 12.sp)
                }
                if (createdAt.isNotBlank()) {
                    Text(KassaApi.prettyTime(createdAt), color = AtColors.muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (comments.size > 10) {
                Text("… ещё ${comments.size - 10}", color = AtColors.muted, fontSize = 12.sp)
            }
        }

        if (canSend) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Написать комментарий") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                colors = fieldColors(),
                shape = radius,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineActionChip(
                    text = if (busy) "..." else "Отправить",
                    filled = true,
                    onClick = {
                        if (!busy) {
                            val t = draft.trim()
                            if (t.isNotBlank()) onSend(t)
                            draft = ""
                        }
                    },
                )
            }
        } else {
            Text("Отправка недоступна оффлайн.", color = AtColors.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsPane(
    apiBase: String,
    user: AppUser?,
    themeMode: String,
    onThemeMode: (String) -> Unit,
    keepAwake: Boolean,
    onKeepAwake: (Boolean) -> Unit,
    flagSecure: Boolean,
    onFlagSecure: (Boolean) -> Unit,
    idleMin: Int,
    onIdleMin: (Int) -> Unit,
    cache: CacheStore,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
) {
    val ctx = LocalContext.current
    var env by remember(apiBase) { mutableStateOf(if (apiBase.contains("172.22")) "staging" else "prod") }
    var notify by remember { mutableStateOf(if (PollWatcher.isEnabled(ctx)) "on" else "off") }
    var vibrate by remember { mutableStateOf(NotifyHelper.isVibrate(ctx)) }
    var bio by remember { mutableStateOf(if (BiometricHelper.isEnabled(ctx)) "on" else "off") }
    val bioAvailable = remember { BiometricHelper.available(ctx) }
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Настройки", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(20.dp)).padding(16.dp),
                ) {
                    Text("Уведомления", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Локальные оповещения о новых заказах и непрочитанных чатах.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    FilterChips(
                        value = notify,
                        items = listOf("on" to "Включены", "off" to "Выключены"),
                        onChange = {
                            notify = it
                            PollWatcher.setEnabled(ctx, it == "on")
                            if (it == "on") NotifySetup.start()
                            if (it == "off") {
                                ShiftWatchService.stop(ctx)
                                NotifyHelper.clearAllBadges(ctx)
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Вибрация", color = AtColors.muted, fontSize = 12.sp)
                    FilterChips(
                        value = if (vibrate) "on" else "off",
                        items = listOf("on" to "С вибрацией", "off" to "Только звук"),
                        onChange = {
                            vibrate = it == "on"
                            NotifyHelper.setVibrate(ctx, vibrate)
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    var notifyTick by remember { mutableIntStateOf(0) }
                    val canPost = remember(notifyTick, notify) { NotifyHelper.canPost(ctx) }
                    Text(
                        if (canPost) {
                            "Разрешение есть. Живые пуши на смене — локальный опрос заказов и чатов (не Firebase)."
                        } else {
                            "Разрешите уведомления — иначе пуши и значок на иконке не придут."
                        },
                        color = if (canPost) AtColors.success else AtColors.warning,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InlineActionChip("Проверить пуш", filled = true) {
                            NotifySetup.start()
                            notifyTick++
                            val ok = NotifyHelper.notifyTest(ctx)
                            Toast.makeText(
                                ctx,
                                if (ok) "Тестовое уведомление отправлено" else "Сначала разрешите уведомления",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        InlineActionChip("Разрешения") {
                            NotifySetup.start()
                            notifyTick++
                            runCatching { ctx.startActivity(NotifySetup.notifySettingsIntent(ctx)) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Не беспокоить", color = AtColors.muted, fontSize = 12.sp)
                    var snoozeTick by remember { mutableIntStateOf(0) }
                    val snoozed = remember(snoozeTick) { PollWatcher.isSnoozed(ctx) }
                    val leftMin = remember(snoozeTick) {
                        ((PollWatcher.snoozeUntil(ctx) - System.currentTimeMillis()).coerceAtLeast(0) / 60_000L).toInt()
                    }
                    FilterChips(
                        value = if (snoozed) "on" else "off",
                        items = listOf(
                            "on" to if (snoozed) "Ещё $leftMin мин" else "1 час",
                            "off" to "Сейчас",
                        ),
                        onChange = {
                            if (it == "on") PollWatcher.snoozeHour(ctx) else PollWatcher.clearSnooze(ctx)
                            snoozeTick++
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Опрос на смене", color = AtColors.muted, fontSize = 12.sp)
                    Text("Тот же интервал работает в свёрнутом приложении (дежурство).", color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
                    var pollSec by remember {
                        mutableStateOf(ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE).getInt("poll_sec", 15).toString())
                    }
                    FilterChips(
                        value = pollSec,
                        items = listOf("15" to "15 сек", "30" to "30 сек", "60" to "1 мин"),
                        onChange = {
                            pollSec = it
                            ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putInt("poll_sec", it.toIntOrNull() ?: 15)
                                .apply()
                        },
                    )
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(20.dp)).padding(16.dp),
                ) {
                    Text("Биометрия", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        if (bioAvailable) "Быстрая разблокировка сохранённой сессии." else "На этом устройстве биометрия недоступна.",
                        color = AtColors.muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (bioAvailable) {
                        Spacer(Modifier.height(12.dp))
                        FilterChips(
                            value = bio,
                            items = listOf("on" to "Включена", "off" to "Выключена"),
                            onChange = {
                                bio = it
                                BiometricHelper.setEnabled(ctx, it == "on")
                            },
                        )
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().atCard(20.dp).padding(16.dp),
                ) {
                    Text("Тема", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Как на сайте: ☀️ светлая, 🌙 тёмная. Кнопка в шапке переключает сразу.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    FilterChips(
                        value = themeMode,
                        items = listOf("dark" to "Тёмная", "light" to "Светлая", "system" to "Авто"),
                        onChange = onThemeMode,
                    )
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(20.dp)).padding(16.dp),
                ) {
                    Text("Экран на смене", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Не гасить дисплей, пока приложение открыто.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    FilterChips(
                        value = if (keepAwake) "on" else "off",
                        items = listOf("on" to "Всегда вкл.", "off" to "Обычный"),
                        onChange = { onKeepAwake(it == "on") },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Скрывать экран на скриншотах", color = AtColors.muted, fontSize = 12.sp)
                    FilterChips(
                        value = if (flagSecure) "on" else "off",
                        items = listOf("on" to "Скрывать", "off" to "Разрешить"),
                        onChange = { onFlagSecure(it == "on") },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Дежурство в фоне", color = AtColors.muted, fontSize = 12.sp)
                    var battTick by remember { mutableIntStateOf(0) }
                    val battOk = remember(battTick) { BatteryHelper.isIgnored(ctx) }
                    Text(
                        if (battOk) "Система не глушит опрос на смене." else "Система может усыплять дежурство. Разрешите работу в фоне.",
                        color = if (battOk) AtColors.success else AtColors.warning,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    FilterChips(
                        value = if (battOk) "on" else "off",
                        items = listOf("on" to if (battOk) "Разрешено" else "Разрешить", "off" to "Как есть"),
                        onChange = {
                            if (it == "on") NotifySetup.start()
                            battTick++
                        },
                    )
                    if (bioAvailable && bio == "on") {
                        Spacer(Modifier.height(10.dp))
                        Text("Автоблокировка", color = AtColors.muted, fontSize = 12.sp)
                        FilterChips(
                            value = idleMin.toString(),
                            items = listOf("0" to "Выкл", "1" to "1 мин", "5" to "5 мин", "15" to "15 мин"),
                            onChange = { onIdleMin(it.toIntOrNull() ?: 0) },
                        )
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(20.dp)).padding(16.dp),
                ) {
                    Text("Окружение API", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("При смене окружения потребуется повторный вход.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    FilterChips(
                        value = env,
                        items = listOf("prod" to "Production", "staging" to "Staging"),
                        onChange = { env = it },
                    )
                    Text(
                        if (env == "staging") BuildConfig.API_BASE_STAGING else BuildConfig.API_BASE_PROD,
                        color = AtColors.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(20.dp)).padding(16.dp),
                ) {
                    Text("Приложение", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    SummaryPill("Версия", BuildConfig.VERSION_NAME, Modifier.fillMaxWidth().padding(top = 10.dp))
                    val today = remember(ShiftLogStore.of(ctx).revision) { ShiftLogStore.of(ctx).stats() }
                    SummaryPill(
                        "Сегодня",
                        "взял ${today.takes} · проблем ${today.problems} · чатов ${today.chats}",
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    SummaryPill("Виджет", "ATCRM · Горящие — на рабочий стол", Modifier.fillMaxWidth().padding(top = 8.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InlineActionChip("На рабочий стол") {
                            try {
                                val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
                                if (Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported) {
                                    mgr.requestPinAppWidget(
                                        android.content.ComponentName(ctx, HotOrdersWidget::class.java),
                                        null,
                                        null,
                                    )
                                } else {
                                    Toast.makeText(ctx, "Добавьте виджет вручную с рабочего стола", Toast.LENGTH_SHORT).show()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(ctx, "Не удалось запросить виджет", Toast.LENGTH_SHORT).show()
                            }
                        }
                        InlineActionChip("Журнал") {
                            DeviceIntents.share(ctx, ShiftLogStore.of(ctx).exportToday())
                        }
                        InlineActionChip("Сбросить кэш") {
                            cache.clearAll()
                            Toast.makeText(ctx, "Кэш списков очищен", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (user != null) {
                        SummaryPill("Пользователь", user.fullName, Modifier.fillMaxWidth().padding(top = 8.dp))
                        SummaryPill("Роль", user.role, Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    InlineActionChip("Сохранить", filled = true) {
                        onApply(if (env == "staging") BuildConfig.API_BASE_STAGING else BuildConfig.API_BASE_PROD)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalNotesBlock(id: String) {
    val ctx = LocalContext.current
    val notes = remember { NotesStore.of(ctx) }
    val voice = remember { VoiceMemo(ctx) }
    var text by remember(id) { mutableStateOf(notes.text(id)) }
    var recording by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var hasVoice by remember(id) { mutableStateOf(notes.hasVoice(id)) }
    var err by remember { mutableStateOf<String?>(null) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                voice.start(notes.voiceFile(id))
                recording = true
                err = null
            } catch (e: Exception) {
                err = e.message ?: "Не удалось начать запись"
            }
        } else {
            err = "Нужен доступ к микрофону"
        }
    }
    DisposableEffect(id) {
        onDispose { voice.release() }
    }
    LaunchedEffect(text, id) {
        delay(450)
        notes.setText(id, text)
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)).padding(14.dp),
    ) {
        Text("Заметки смены", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Только на этом телефоне: текст и голосовая пометка к заказу.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Что уточнить, кому передать, что обещали…") },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            colors = fieldColors(),
            shape = radius,
            minLines = 2,
        )
        if (err != null) {
            Text(err!!, color = AtColors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InlineActionChip(
                when {
                    recording -> "Стоп"
                    else -> "Голос"
                },
            ) {
                if (recording) {
                    voice.stopRecord()
                    recording = false
                    hasVoice = notes.hasVoice(id)
                } else {
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            if (hasVoice && !recording) {
                InlineActionChip(if (playing) "Стоп ▶" else "Слушать") {
                    if (playing) {
                        voice.stopPlay()
                        playing = false
                    } else {
                        try {
                            voice.play(notes.voiceFile(id)) {
                                playing = false
                            }
                            playing = true
                        } catch (e: Exception) {
                            err = e.message
                        }
                    }
                }
                InlineActionChip("Удалить голос") {
                    voice.stopPlay()
                    notes.deleteVoice(id)
                    hasVoice = false
                    playing = false
                }
            }
        }
        if (recording) {
            Text("Идёт запись…", color = AtColors.warning, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        } else if (hasVoice) {
            Text("Голосовая заметка сохранена", color = AtColors.success, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun OperationActionsRow(busy: Boolean, onAction: (label: String, status: String, comment: String) -> Unit) {
    val actions = listOf(
        "Подтвердить" to "CONFIRM",
        "Проблема" to "PROBLEM",
    )
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)).padding(14.dp),
    ) {
        Text("Действия", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Быстрая смена статуса заказа", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(actions) { (label, status) ->
                InlineActionChip(label, filled = status == "CONFIRM") {
                    if (busy) return@InlineActionChip
                    if (status == "PROBLEM") confirm = label to status else onAction(label, status, "")
                }
            }
        }
    }
    confirm?.let { (label, status) ->
        if (status == "DONE") {
            ConfirmDoneDialog(
                title = "этот заказ",
                onConfirm = {
                    onAction(label, status, "")
                    confirm = null
                },
                onDismiss = { confirm = null },
            )
        } else {
            ConfirmProblemDialog(
                title = "этот заказ",
                onConfirm = { note ->
                    onAction(label, status, note)
                    confirm = null
                },
                onDismiss = { confirm = null },
            )
        }
    }
}

@Composable
private fun OperationTimelineBlock(events: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)).padding(16.dp),
    ) {
        Text("История", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        events.take(8).forEachIndexed { i, (title, detail) ->
            Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 10.dp else 8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(AtColors.accent).padding(top = 6.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (detail.isNotBlank()) {
                        Text(detail, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationQueueStats(rows: List<JsonRow>) {
    val review = rows.count { KassaApi.operationStatus(it) == "PENDING_REVIEW" || KassaApi.needsConfirm(it) }
    val created = rows.count { KassaApi.operationStatus(it) == "CREATED" }
    val rest = (rows.size - review - created).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QueueStatPill("Проверка", review.toString(), AtColors.warning)
        QueueStatPill("Подтв.", created.toString(), AtColors.success)
        QueueStatPill("Ещё", rest.toString(), AtColors.accent)
    }
}

@Composable
private fun RowScope.QueueStatPill(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        Text(label, color = AtColors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

@Composable
private fun ProblemOrderStats(rows: List<JsonRow>) {
    fun reasonOf(row: JsonRow) = KassaApi.pick(row.raw, "reason", "delayReason").uppercase()
    val restaurant = rows.count { reasonOf(it).contains("RESTAURANT") }
    val store = rows.count { reasonOf(it).contains("STORE") }
    val courier = rows.count { reasonOf(it).contains("COURIER") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QueueStatPill("Всего", rows.size.toString(), AtColors.danger)
        QueueStatPill("Рестор.", restaurant.toString(), AtColors.warning)
        QueueStatPill("Магаз.", store.toString(), AtColors.accent)
        QueueStatPill("Курьер", courier.toString(), AtColors.success)
    }
}

@Composable
private fun OperationSummaryBlock(obj: JSONObject) {
    val status = KassaApi.pick(obj, "status", "state")
    val amount = formatMoney(KassaApi.pick(obj, "orderAmount", "amount", "total"))
    val client = KassaApi.clientName(obj).ifBlank { KassaApi.pick(obj, "fullName", "name") }
    val address = KassaApi.establishmentName(obj)
    val created = KassaApi.prettyTime(KassaApi.operationWhen(obj))
    val manager = KassaApi.pick(obj, "managerName", "assignedTo", "courierName", "username")
    val phone = KassaApi.pick(obj, "phone", "clientPhone")
    val orderNo = KassaApi.pick(obj, "orderNumber", "externalId", "id")
    val comment = KassaApi.pick(obj, "comment", "note", "problem", "reason")
    Column(
        Modifier.fillMaxWidth().atCard(18.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Карточка операции", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (orderNo.isNotBlank()) {
                    Text("№ $orderNo", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (status.isNotBlank()) StatusChip(prettyStatus(status))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryPill("Клиент", client.ifBlank { "—" }, Modifier.weight(1f))
            SummaryPill("Сумма", amount.ifBlank { "—" }, Modifier.weight(1f))
        }
        if (address.isNotBlank()) {
            SummaryPill("Точка", address, Modifier.fillMaxWidth())
        }
        if (manager.isNotBlank() || phone.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (manager.isNotBlank()) SummaryPill("Ответственный", manager, Modifier.weight(1f))
                if (phone.isNotBlank()) SummaryPill("Телефон", phone, Modifier.weight(1f))
            }
        }
        if (comment.isNotBlank()) {
            SummaryPill("Комментарий", comment, Modifier.fillMaxWidth())
        }
        if (created.isNotBlank()) {
            SummaryPill("Обновлено", created, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SummaryPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(chipRadius).background(AtColors.glass).border(0.5.dp, AtColors.stroke, chipRadius).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = AtColors.muted, fontSize = 11.sp)
        Text(value, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ConversationHero(title: String, count: Int) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(14.dp),
    ) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("В ленте $count сообщ.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun ReplyTemplatesBlock(
    obj: JSONObject,
    title: String,
    onOpenChat: ((String, String) -> Unit)?,
    onCopied: () -> Unit,
) {
    val ctx = LocalContext.current
    val drafts = remember { DraftsStore(ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE)) }
    val phrases = remember(obj.toString(), title) { ReplyTemplates.forContext(obj, title) }
    val chat = KassaApi.chatLinkFromOperation(obj)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)).padding(vertical = 10.dp),
    ) {
        Text(
            "Шаблоны ответа",
            color = AtColors.text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Text(
            if (chat != null && onOpenChat != null) "Нажмите — фраза попадёт в черновик чата" else "Нажмите — скопируем в буфер",
            color = AtColors.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        QuickReplyRow(phrases, onPick = { phrase ->
            if (chat != null && onOpenChat != null) {
                drafts.put(KassaApi.chatIdFromPath(chat.second), phrase)
                onOpenChat(chat.first, chat.second)
            } else {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("atcrm", phrase))
                onCopied()
            }
        })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickReplyRow(
    items: List<String>,
    onPick: (String) -> Unit,
    onLongClick: ((String) -> Unit)? = null,
    onSave: (() -> Unit)? = null,
) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onSave != null) {
            item {
                Box(
                    Modifier
                        .height(24.dp)
                        .clip(chipRadius)
                        .background(AtColors.accentSoft)
                        .clickable(onClick = onSave)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋ шаблон", color = AtColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        items(items) { phrase ->
            Box(
                Modifier
                    .height(24.dp)
                    .clip(chipRadius)
                    .background(AtColors.glass)
                    .border(0.5.dp, AtColors.stroke, chipRadius)
                    .then(
                        if (onLongClick != null) Modifier.combinedClickable(onClick = { onPick(phrase) }, onLongClick = { onLongClick(phrase) })
                        else Modifier.clickable { onPick(phrase) },
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(phrase, color = AtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ProfileHeroCard(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
internal fun StatusChip(text: String) {
    val label = prettyStatus(text)
    val color = statusTint(text)
    Box(
        Modifier.clip(chipRadius).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ConfirmProblemDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtColors.panel,
        title = { Text("Занести в проблемные?", color = AtColors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("«$title» появится в разделе проблемных заказов CRM. Укажите, почему задержка.", color = AtColors.muted)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text("Причина задержки") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    colors = fieldColors(),
                    shape = radius,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(comment.trim()) }, enabled = comment.isNotBlank()) { Text("Сохранить", color = AtColors.danger, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = AtColors.muted) }
        },
    )
}

@Composable
private fun ConfirmDoneDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AtColors.panel,
        title = { Text("Отметить готовым?", color = AtColors.text, fontWeight = FontWeight.Bold) },
        text = { Text("«$title» закроется как выполненный. Отменить можно из журнала смены.", color = AtColors.muted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Готово", color = AtColors.success, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = AtColors.muted) }
        },
    )
}

@Composable
private fun LiveAlertBanner(
    alert: LiveAlert,
    onOpen: () -> Unit,
    onTake: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(AtColors.accentSoft).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(alert.text, color = AtColors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            TextButton(onClick = onDismiss) { Text("Скрыть", color = AtColors.muted, fontSize = 13.sp) }
            if (onTake != null) {
                TextButton(onClick = onTake) { Text("Подтвердить", color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
            TextButton(onClick = onOpen) { Text("Открыть", color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun ShiftLogPane(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { ShiftLogStore.of(ctx) }
    val events = remember(store.revision) { store.today() }
    val stats = remember(store.revision) { store.stats() }
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Журнал смены", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ShiftCard(stats = stats, canUndo = false, onUndo = {})
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    InlineActionChip("Поделиться журналом") { DeviceIntents.share(ctx, store.exportToday()) }
                }
            }
            if (events.isEmpty()) {
                item {
                    EmptyStateCard("Пока пусто", "Взятые заказы, проблемы, готово и сообщения появятся здесь.")
                }
            } else {
                items(events) { ev ->
                    val label = when (ev.kind) {
                        "take" -> "Взял"
                        "problem" -> "Проблема"
                        "done" -> "Готово"
                        "chat" -> "Сообщение"
                        else -> ev.kind.ifBlank { "Действие" }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(16.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusChip(label)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ev.title.ifBlank { "—" }, color = AtColors.text, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (ev.at > 0) {
                                Text(
                                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ev.at)),
                                    color = AtColors.muted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboxPane(
    api: KassaApi,
    token: String?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { OutboxStore.of(ctx) }
    val items = remember(store.revision) { store.items() }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    fun flushAll() {
        val t = token ?: return
        scope.launch {
            busy = true
            err = null
            try {
                val n = withContext(Dispatchers.IO) { store.flush(api, t) }
                msg = if (n > 0) "Отправлено: $n" else "Нечего отправлять или сеть не приняла"
                Haptics.tap(ctx)
            } catch (e: Exception) {
                err = e.message ?: "Не удалось отправить"
                Haptics.warn(ctx)
            } finally {
                busy = false
            }
        }
    }
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Очередь", onBack)
        if (msg != null) ActionBanner(msg!!, error = false, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (err != null) ActionBanner(err!!, error = true, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (items.isEmpty()) {
            EmptyStateCard(
                title = "Очередь пуста",
                subtitle = "Сюда попадают статусы, сообщения и фото, если не было сети.",
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        InlineActionChip(if (busy) "..." else "Отправить всё", filled = true) { if (!busy) flushAll() }
                        InlineActionChip("Сбросить") {
                            store.clear()
                            msg = "Очередь очищена"
                        }
                    }
                }
                items(items, key = { it.id }) { item ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(16.dp)).padding(14.dp),
                    ) {
                        Text(item.label.ifBlank { item.method }, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (item.method == "PHOTO") "фото · ${item.path}" else "${item.method} · ${item.path}",
                            color = AtColors.muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val caption = item.body.optString("caption").ifBlank { item.body.optString("text") }
                        if (caption.isNotBlank() && item.method != "PATCH") {
                            Text(caption, color = AtColors.text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        if (item.at > 0) {
                            Text(
                                java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.at)),
                                color = AtColors.muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                            InlineActionChip("Ещё раз") {
                                val t = token ?: return@InlineActionChip
                                scope.launch {
                                    busy = true
                                    err = null
                                    try {
                                        val ok = withContext(Dispatchers.IO) { store.flushOne(api, t, item.id) }
                                        msg = if (ok) "Отправлено: ${item.label}" else "Не ушло — проверьте сеть"
                                        if (ok) Haptics.tap(ctx) else Haptics.warn(ctx)
                                    } catch (e: Exception) {
                                        err = e.message
                                        Haptics.warn(ctx)
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                            InlineActionChip("Удалить") { store.remove(item.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetStatusBanner(pending: Int, onOpen: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(AtColors.warning.copy(alpha = 0.16f)).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text("Нет сети", color = AtColors.warning, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            if (pending > 0) "Очередь: $pending. Нажмите, чтобы посмотреть."
            else "Можно смотреть кэш и готовить статусы — отправим при связи.",
            color = AtColors.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun OutboxStatusBanner(pending: Int, onOpen: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(AtColors.accentSoft).clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text("Очередь: $pending", color = AtColors.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Нажмите, чтобы отправить или удалить неотправленное.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

private suspend fun enqueueOrPatch(
    ctx: Context,
    api: KassaApi,
    token: String,
    path: String,
    status: String,
    label: String,
    prevStatus: String = "",
    title: String = "",
    rowId: String = "",
    log: Boolean = true,
    comment: String = "",
): String {
    val body = JSONObject().put("status", status)
    if (comment.isNotBlank()) {
        body.put("comment", comment)
        body.put("note", comment)
        body.put("reason", comment)
        body.put("problemComment", comment)
    }
    val kind = when (status) {
        "IN_PROGRESS" -> "take"
        "PROBLEM" -> "problem"
        "DONE" -> "done"
        else -> "other"
    }
    val queued = !NetWatch.online(ctx)
    val result = if (queued) {
        OutboxStore.of(ctx).enqueue("PATCH", path, body, label)
        "$label — в очереди без сети"
    } else {
        try {
            api.patchJson(path, token, body)
            "$label — сохранено"
        } catch (e: Exception) {
            if (e is SessionExpiredException) throw e
            OutboxStore.of(ctx).enqueue("PATCH", path, body, label)
            "$label — в очереди"
        }
    }
    if (log) {
        ShiftLogStore.of(ctx).record(kind, title.ifBlank { label }, path, prevStatus, status, rowId, undoable = true)
    }
    return result
}

@Composable
private fun ShiftCard(
    stats: ShiftStats,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onOpen: (() -> Unit)? = null,
    onTakeNext: (() -> Unit)? = null,
    nextTitle: String = "",
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text("Смена сегодня", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(
            "Взял ${stats.takes} · проблем ${stats.problems} · готово ${stats.done} · сообщений ${stats.chats}",
            color = AtColors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (stats.lastTitle.isNotBlank()) {
            Text("Последнее: ${stats.lastTitle}", color = AtColors.text, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onTakeNext != null && nextTitle.isNotBlank()) {
            Text("Следующий: $nextTitle", color = AtColors.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onOpen != null || canUndo || onTakeNext != null) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (onTakeNext != null) InlineActionChip("Взять следующий") { onTakeNext() }
                if (onOpen != null) InlineActionChip("Журнал") { onOpen() }
                if (canUndo) InlineActionChip("Отменить последнее") { onUndo() }
            }
        }
    }
}

@Composable
internal fun ActionBanner(text: String, error: Boolean, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val color = if (error) AtColors.danger else AtColors.success
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (!error && action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun OfflineBanner(cachedAt: Long) {
    val stamp = if (cachedAt > 0) {
        java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(cachedAt))
    } else {
        "ранее"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AtColors.warning.copy(alpha = 0.12f)).padding(14.dp),
    ) {
        Text("Офлайн-кэш", color = AtColors.warning, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Сеть недоступна — показан сохранённый список от $stamp.", color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun ErrorBlock(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().atCard(18.dp).padding(18.dp),
    ) {
        Text("Не получилось загрузить", color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(message, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        if (onRetry != null) {
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) {
                Text("Повторить", color = AtColors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun LoadingCard() {
    Box(
        Modifier.fillMaxWidth().atCard(18.dp).padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AtColors.accent)
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)).padding(18.dp),
    ) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(subtitle, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LoadingListState(spec: ModuleSpec) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionLead(spec) }
        items(4) {
            Box(
                Modifier.fillMaxWidth().height(108.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)),
            )
        }
    }
}

@Composable
private fun LoadingDetailState() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(6) {
            Box(
                Modifier.fillMaxWidth().height(if (it == 0) 96.dp else 72.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).border(1.dp, AtColors.stroke, RoundedCornerShape(18.dp)),
            )
        }
    }
}

@Composable
internal fun TopLine(title: String, onBack: () -> Unit, onRefresh: (() -> Unit)? = null, onShare: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(AtColors.panel)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(start = 6.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AtColors.accentSoft)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", color = AtColors.accent, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            title,
            color = AtColors.text,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        if (onShare != null) {
            TextButton(onClick = onShare) { Text("Поделиться", color = AtColors.muted, fontSize = 13.sp) }
        }
        if (onRefresh != null) {
            TextButton(onClick = onRefresh) { Text("Обновить", color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
    }
}

private fun withUnreadZero(row: JsonRow): JsonRow {
    val o = JSONObject(row.raw.toString())
    o.put("unreadCount", 0)
    o.put("unread", 0)
    return row.copy(raw = o)
}

private fun chatTranscript(title: String, msgs: List<ChatMsg>): String {
    return buildString {
        appendLine("ATCRM · ${title.ifBlank { "чат" }}")
        if (msgs.isEmpty()) {
            appendLine("Переписка пустая")
            return@buildString
        }
        msgs.takeLast(40).forEach { m ->
            val who = if (m.mine) "Я" else m.author.ifBlank { "Клиент" }
            val body = m.text.ifBlank {
                when {
                    m.imageUrl.isNotBlank() -> "[фото]"
                    m.audioUrl.isNotBlank() -> "[голос]"
                    else -> ""
                }
            }
            if (body.isNotBlank()) {
                val t = KassaApi.prettyTime(m.time)
                appendLine(if (t.isNotBlank()) "$t $who: $body" else "$who: $body")
            }
        }
    }.trim()
}

private fun moduleHint(module: ModuleSpec): String = when {
    module.path.startsWith("/problem-orders") -> "задержки и комментарии"
    module.path.contains("logistics-intake") -> "подтвердить из Delivio"
    module.path.startsWith("/operations") -> "очередь заказов и статусы"
    module.path.startsWith("/workspace/chats") -> "ответы и непрочитанные"
    module.path.startsWith("/support/threads") -> "обращения и эскалации"
    module.path.startsWith("/establishments") -> "карточки точек и сети"
    module.path.startsWith("/users") -> "люди, роли и доступы"
    else -> "быстрый вход в раздел"
}

private fun shortActionHint(module: ModuleSpec): String = when {
    module.path.startsWith("/operations") && !module.path.contains("logistics") -> "заказы"
    module.path.contains("logistics-intake") -> "подтвердить"
    module.path.startsWith("/problem-orders") -> "задержки"
    module.path.startsWith("/workspace/tasks") -> "проверить"
    module.path.startsWith("/workspace/chats") -> "ответить"
    module.path.startsWith("/support/threads") -> "эскалации"
    module.path.startsWith("/calls/contacts") -> "обзвон"
    module.path.startsWith("/establishments") -> "точки"
    module.path.contains("client-portraits") -> "аналитика"
    module.path.contains("cities") -> "география"
    module.path == "/settings" -> "контроль"
    module.path.startsWith("/users") -> "команда"
    else -> "открыть"
}

private fun envLabel(apiBase: String): String = when {
    apiBase.contains("172.22") -> "Staging · 172.22.15.30"
    apiBase.contains("deliviotm.com") -> "Production · crm.deliviotm.com"
    else -> apiBase.removePrefix("http://").removePrefix("https://").substringBefore("/")
}

private fun formatMoney(raw: String): String {
    val s0 = raw.trim()
    if (s0.isBlank() || s0 == "—" || s0.equals("null", true)) return ""
    // keep digits + separators only
    val s = s0.replace(Regex("[^0-9.,]"), "").replace(',', '.')
    val v = s.toBigDecimalOrNull() ?: return s0
    val sign = if (v.signum() < 0) "-" else ""
    val abs = v.abs()
    // integer view (works for typical money fields)
    val intPart = abs.toBigInteger().toString()
    val grouped = intPart.reversed().chunked(3).joinToString(".").reversed()
    return "$sign$grouped ₽"
}

private fun urgencyLabel(status: String, subtitle: String): String {
    val s = "$status $subtitle".lowercase()
    return when {
        s.contains("problem") || s.contains("проблем") || s.contains("cancel") -> "срочно"
        s.contains("pending") || s.contains("wait") || s.contains("new") -> "взять"
        else -> ""
    }
}

private fun unreadLabel(row: JsonRow): String {
    val n = KassaApi.unreadCount(row)
    return if (n > 0) "$n" else ""
}

private fun establishmentKind(row: JsonRow): String {
    val t = KassaApi.pick(row.raw, "type", "kind", "establishmentType", "category").lowercase()
    return when {
        "store" in t || "магаз" in t || "shop" in t -> "store"
        "rest" in t || "рестор" in t || "cafe" in t -> "restaurant"
        else -> t
    }
}

private fun searchPlaceholder(spec: ModuleSpec): String = when {
    spec.path.startsWith("/operations") -> "ID клиента или № заказа (точное совпадение)"
    spec.path.startsWith("/problem-orders") -> "№ заказа, заведение, комментарий"
    spec.path.startsWith("/workspace/chats") -> "Поиск по сотрудникам..."
    spec.path.startsWith("/support/threads") -> "Поиск по диалогу, клиенту, теме"
    spec.path.startsWith("/workspace/tasks") -> "Поиск по названию/описанию"
    spec.path.startsWith("/email") -> "Тема, отправитель, дата"
    spec.path.startsWith("/clients") || spec.path.startsWith("/marketing") -> "ID, имя, email, телефон…"
    spec.path.startsWith("/sales") || spec.path.startsWith("/establishments") -> "Название, Delivio ID…"
    spec.path.startsWith("/courier") -> "Курьер, телефон, город"
    spec.path.startsWith("/accounting") || spec.path.startsWith("/reconciliation") -> "Документ, контрагент, сумма"
    spec.path.startsWith("/ai/") -> "Поиск по рекомендации"
    spec.path.startsWith("/users") -> "Имя, роль, логин"
    spec.path.contains("audit") -> "Кто, действие, время"
    spec.path.startsWith("/sms") -> "Номер, текст, рассылка"
    spec.path.startsWith("/push") -> "Кампания, статус"
    spec.path.startsWith("/qr") -> "Название QR, заведение"
    spec.path.startsWith("/calls") -> "Имя, телефон, дата"
    else -> "Поиск"
}

private fun bestStatus(row: JsonRow): String = KassaApi.pick(row.raw, "status", "state", "kind")
private fun bestAmount(row: JsonRow): String = KassaApi.pick(row.raw, "orderAmount", "amount", "total")
private fun bestClient(row: JsonRow): String = KassaApi.displayTitle(row)
private fun prettyKey(key: String): String = key.substringAfterLast('.').replaceFirstChar { it.uppercase() }

@Composable
@ReadOnlyComposable
private fun metricColor(tone: Tone) = when (tone) {
    Tone.Good -> AtColors.success
    Tone.Warn -> AtColors.warning
    Tone.Danger -> AtColors.danger
    Tone.Neutral -> AtColors.accent
}


// --- recovered from operations-workspace.kt ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationsWorkspacePane(
    api: KassaApi,
    token: String?,
    me: AppUser?,
    cache: CacheStore?,
    initialTab: String = "orders",
    onBack: () -> Unit,
    onOpenOp: (JsonRow) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(if (initialTab in listOf("orders", "logistics", "activity")) initialTab else "orders") }
    val today = remember { java.time.LocalDate.now().toString() }
    val storedPreset = remember { prefs.getString("ops_preset", "7d").orEmpty() }
    var preset by remember { mutableStateOf(storedPreset.ifBlank { "7d" }) }
    var dateFrom by remember {
        mutableStateOf(prefs.getString("ops_from", "").orEmpty().ifBlank { java.time.LocalDate.now().minusDays(7).toString() })
    }
    var dateTo by remember { mutableStateOf(prefs.getString("ops_to", "").orEmpty().ifBlank { today }) }
    var status by remember { mutableStateOf(prefs.getString("ops_status", "ALL").orEmpty().ifBlank { "ALL" }) }
    var pay by remember { mutableStateOf("ALL") }
    var estType by remember { mutableStateOf("ALL") }
    var cityKey by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("orderDatetime") }
    var sortDir by remember { mutableStateOf("desc") }
    var qDraft by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(1) }
    var pageSize by remember { mutableIntStateOf(50) }
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var pendingReview by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var cities by remember { mutableStateOf(listOf(DashCity("", "Все города"))) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var filtersOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var createBusy by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf<JsonRow?>(null) }
    var intake by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var pendingOps by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var intakePendingOnly by remember { mutableStateOf(true) }
    var intakeCity by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var activityTotal by remember { mutableIntStateOf(0) }
    var activityKind by remember { mutableStateOf("ALL") }
    var activityQ by remember { mutableStateOf("") }
    var activityQApplied by remember { mutableStateOf("") }
    val pullState = rememberPullToRefreshState()
    val showJournal = me?.operationsActivityEnabled == true || me?.role?.equals("ADMIN", true) == true

    fun applyPreset(next: String) {
        preset = next
        if (next == "custom") return
        val range = KassaApi.dateRange(next)
        dateFrom = range.first
        dateTo = range.second
        page = 1
    }

    LaunchedEffect(preset, dateFrom, dateTo, status) {
        prefs.edit()
            .putString("ops_preset", preset)
            .putString("ops_from", dateFrom)
            .putString("ops_to", dateTo)
            .putString("ops_status", status)
            .apply()
    }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { api.pingOpsActivity(token, "TAB") }
            runCatching { api.getObject("/sales/establishments/cities", token) }.getOrNull()
        }?.let { o ->
            val parsed = KassaApi.parseCities(o)
            if (parsed.isNotEmpty()) cities = parsed
        }
    }

    LaunchedEffect(token, tick, dateFrom, dateTo, status, pay, estType, cityKey, q, page, sortBy, sortDir, tab) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        if (tab != "orders") return@LaunchedEffect
        if (!dateFrom.matches(Regex("""\d{4}-\d{2}-\d{2}""")) || !dateTo.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return@LaunchedEffect
        err = null
        if (loaded) refreshing = true
        try {
            val pack = withContext(Dispatchers.IO) {
                val raw = api.getObject(
                    KassaApi.operationsQuery(dateFrom, dateTo, status, page, pageSize, pay, estType, q, cityKey, sortBy, sortDir),
                    token,
                )
                val pending = if (status == "CANCELLED") {
                    emptyList()
                } else {
                    runCatching {
                        api.getRows(
                            KassaApi.operationsQuery(
                                java.time.LocalDate.now().minusDays(30).toString(),
                                java.time.LocalDate.now().toString(),
                                "PENDING_REVIEW",
                                1,
                                200,
                                pay,
                                estType,
                                q,
                                cityKey,
                            ),
                            token,
                        )
                    }.getOrDefault(emptyList())
                }
                raw to pending
            }
            val pageData = KassaApi.pagedRows(pack.first)
            rows = pageData.items
            pendingReview = pack.second.filter { KassaApi.operationStatus(it) == "PENDING_REVIEW" || KassaApi.needsConfirm(it) }
            total = pageData.total
            cache?.putRows(KassaApi.opsCacheKey(), (pendingReview + pageData.items).distinctBy { it.id })
            loaded = true
        } catch (e: Exception) {
            err = e.message
            cache?.getRows(KassaApi.opsCacheKey())?.let {
                rows = it
                loaded = true
            }
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(token, tick, tab, intakePendingOnly, intakeCity) {
        if (token.isNullOrBlank() || tab != "logistics") return@LaunchedEffect
        err = null
        if (loaded) refreshing = true
        try {
            val pack = withContext(Dispatchers.IO) {
                val events = runCatching { api.getRows(KassaApi.logisticsIntakePath(200, "ALL", intakeCity), token) }.getOrDefault(emptyList())
                val pending = runCatching {
                    api.getRows(KassaApi.operationsQuery(java.time.LocalDate.now().minusDays(30).toString(), java.time.LocalDate.now().toString(), "PENDING_REVIEW", 1, 200), token)
                }.getOrDefault(emptyList())
                events to pending
            }
            intake = pack.first
            pendingOps = pack.second
            loaded = true
        } catch (e: Exception) {
            err = e.message
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(token, tick, tab, activityKind, activityQApplied) {
        if (token.isNullOrBlank() || tab != "activity") return@LaunchedEffect
        err = null
        if (loaded) refreshing = true
        try {
            val o = withContext(Dispatchers.IO) {
                api.getObject(KassaApi.activityQuery(80, 0, activityKind, activityQApplied), token)
            }
            val arr = o.optJSONArray("items") ?: o.optJSONArray("data")
            activity = if (arr != null) KassaApi.eachObj(arr) else emptyList()
            activityTotal = o.optInt("total", activity.size)
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Не удалось загрузить журнал"
            activity = emptyList()
        } finally {
            refreshing = false
        }
    }

    val pages = ((total + pageSize - 1) / pageSize).coerceAtLeast(1)
    val safePage = page.coerceIn(1, pages)
    val pendingIds = remember(pendingReview) { pendingReview.map { it.id }.filter { it.isNotBlank() }.toSet() }
    val pinnedPending = remember(pendingReview, status) {
        if (status == "CANCELLED") emptyList() else pendingReview
    }
    val listRows = remember(rows, pendingIds, status) {
        if (status == "PENDING_REVIEW") rows
        else rows.filter { it.id.isBlank() || it.id !in pendingIds }
    }

    fun pendingFor(row: JsonRow): JsonRow? {
        val num = KassaApi.orderNo(row.raw)
        if (num.isBlank()) return null
        return pendingOps.find { KassaApi.orderNo(it.raw) == num }
    }

    val intakeShown = remember(intake, intakePendingOnly, pendingOps) {
        val dedup = LinkedHashMap<String, JsonRow>()
        intake.sortedByDescending { KassaApi.pick(it.raw, "createdAt") }.forEach { row ->
            val key = KassaApi.orderNo(row.raw).ifBlank { row.id }
            val prev = dedup[key]
            if (prev == null) dedup[key] = row
        }
        val list = dedup.values.toList()
        if (!intakePendingOnly) list
        else list.filter { r ->
            val pending = pendingFor(r) ?: pendingOps.find { KassaApi.orderNo(it.raw) == KassaApi.orderNo(r.raw) }
            KassaApi.needsConfirm(r) || KassaApi.needsRetry(r) || (pending != null && KassaApi.operationStatus(pending) == "PENDING_REVIEW") ||
                KassaApi.pick(r.raw, "proposalError").isNotBlank()
        }
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Операции", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SiteSectionHead("Операции", "Список операций с фильтрами, сортировкой и редактированием")
                }
                item {
                    SiteSegmented(
                        value = tab,
                        items = buildList {
                            add("orders" to "Заказы")
                            add("logistics" to "Логистика")
                            if (showJournal) add("activity" to "Журнал")
                        },
                        onChange = { tab = it; loaded = tab == "orders" && rows.isNotEmpty() },
                    )
                }
                if (err != null) item { ActionBanner(err!!, error = true) }
                if (msg != null) item { ActionBanner(msg!!, error = false) }
                when (tab) {
                    "orders" -> {
                        item {
                            Box(
                                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent)
                                    .clickable { creating = true },
                                contentAlignment = Alignment.Center,
                            ) { Text("+ Создать операцию", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                        item {
                            val confirmedN = rows.count { KassaApi.operationStatus(it) == "CREATED" }
                            val cancelledN = rows.count { KassaApi.operationStatus(it) == "CANCELLED" }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SiteMiniStat("всего", total.toString(), Modifier.weight(1f))
                                SiteMiniStat("на проверке", pendingReview.size.toString(), Modifier.weight(1f))
                                SiteMiniStat("подтвержд.", confirmedN.toString(), Modifier.weight(1f))
                                SiteMiniStat("отмены", cancelledN.toString(), Modifier.weight(1f))
                            }
                        }
                        item {
                            val statusLabel = when (status) {
                                "PENDING_REVIEW" -> "На проверке"
                                "CREATED" -> "Подтверждённые"
                                "CANCELLED" -> "Отменённые"
                                else -> "Все заказы"
                            }
                            val payLabel = when (pay) {
                                "CASH" -> "Наличные"
                                "ONLINE" -> "Онлайн"
                                "MIXED" -> "Смешанная"
                                else -> "Любая оплата"
                            }
                            val cityLabel = cities.find { it.key == cityKey }?.name ?: "Все города"
                            val periodLabel = if (preset == "custom") {
                                "${KassaApi.fmtDay(dateFrom)} — ${KassaApi.fmtDay(dateTo)}"
                            } else {
                                KassaApi.dashPeriodLabel(preset)
                            }
                            SiteFilterPanel(
                                open = filtersOpen,
                                summary = listOf(periodLabel, statusLabel, payLabel, cityLabel).joinToString(" · "),
                                onToggle = { filtersOpen = !filtersOpen },
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SitePeriodDropdown(
                                        preset = preset,
                                        rangeLabel = KassaApi.periodRangeLabel(preset, dateFrom, dateTo),
                                        presets = KassaApi.dashPeriodPresets,
                                        customFrom = dateFrom,
                                        customTo = dateTo,
                                        onSelectPreset = { applyPreset(it); page = 1 },
                                        onApplyCustom = { from, to ->
                                            dateFrom = from
                                            dateTo = to
                                            preset = "custom"
                                            page = 1
                                        },
                                    )
                                    SiteFilterSelect(
                                        value = status,
                                        items = listOf(
                                            "ALL" to "Все заказы",
                                            "PENDING_REVIEW" to "На проверке",
                                            "CREATED" to "Подтверждённые",
                                            "CANCELLED" to "Отменённые",
                                        ),
                                        onChange = { status = it; page = 1 },
                                        label = "Статус",
                                    )
                                    SiteFilterSelect(
                                        value = pay,
                                        items = listOf("ALL" to "Любая", "CASH" to "Наличные", "ONLINE" to "Онлайн", "MIXED" to "Смешанная"),
                                        onChange = { pay = it; page = 1 },
                                        label = "Оплата",
                                    )
                                    SiteFilterSelect(
                                        value = estType,
                                        items = listOf("ALL" to "Все", "RESTAURANT" to "Рестораны", "STORE" to "Магазины"),
                                        onChange = { estType = it; page = 1 },
                                        label = "Тип заведения",
                                    )
                                    if (cities.size > 1) {
                                        SiteFilterSelect(
                                            value = cityKey,
                                            items = cities.map { it.key to it.name },
                                            onChange = { cityKey = it; page = 1 },
                                            label = "Город",
                                        )
                                    }
                                    OutlinedTextField(
                                        value = qDraft,
                                        onValueChange = { qDraft = it },
                                        placeholder = { Text("ID клиента или № заказа (точное совпадение)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = fieldColors(),
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        InlineActionChip("Найти", filled = true) { q = qDraft.trim(); page = 1; tick++ }
                                        InlineActionChip("Сброс") {
                                            qDraft = ""
                                            q = ""
                                            status = "ALL"
                                            pay = "ALL"
                                            estType = "ALL"
                                            cityKey = ""
                                            applyPreset("7d")
                                            page = 1
                                            tick++
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SiteFilterSelect(
                                            value = sortBy,
                                            items = listOf("orderDatetime" to "Дата заказа", "establishmentName" to "Заведение"),
                                            onChange = {
                                                sortBy = it
                                                sortDir = if (it == "establishmentName") "asc" else "desc"
                                                page = 1
                                            },
                                            label = "Сортировка",
                                            modifier = Modifier.weight(1f),
                                        )
                                        SiteFilterSelect(
                                            value = sortDir,
                                            items = listOf("desc" to "Сначала новые", "asc" to "Сначала старые"),
                                            onChange = { sortDir = it; page = 1 },
                                            label = "Порядок",
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        if (!loaded && err == null) {
                            item { LoadingCard() }
                        } else if (listRows.isEmpty() && pinnedPending.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    "Нет операций за выбранный период. Расширьте «Дату с / по» или сбросьте поиск.",
                                    "Смените фильтр статуса или период.",
                                )
                            }
                        } else {
                            if (pinnedPending.isNotEmpty() && status != "PENDING_REVIEW") {
                                item {
                                    Text(
                                        "На проверке · ${pinnedPending.size}",
                                        color = AtColors.text,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                    )
                                }
                                items(pinnedPending, key = { "pending-${it.id}" }) { row ->
                                    OpsOrderCard(row = row, onOpen = { onOpenOp(row) })
                                }
                                if (listRows.isNotEmpty()) {
                                    item {
                                        Text(
                                            if (status == "CREATED") "Подтверждённые" else "Остальные заказы",
                                            color = AtColors.text,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                }
                            }
                            items(listRows, key = { it.id }) { row ->
                                OpsOrderCard(row = row, onOpen = { onOpenOp(row) })
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    InlineActionChip("Назад") { if (page > 1) page -= 1 }
                                    Text("Всего: $total · стр. $safePage", color = AtColors.muted, fontSize = 13.sp)
                                    InlineActionChip("Вперёд") { if (page < pages) page += 1 }
                                }
                            }
                        }
                    }
                    "logistics" -> {
                        item {
                            SiteSectionHead("Заказы из логистики Delivio", "Бот синхронизирует принятые заказы. Проверьте суммы и подтвердите операцию.")
                        }
                        item {
                            Text("Показано: ${intakeShown.size} из ${intake.size}", color = AtColors.muted, fontSize = 13.sp)
                            SiteFilterSelect(
                                value = if (intakePendingOnly) "pending" else "all",
                                items = listOf("pending" to "Только на проверке", "all" to "Все события заказа"),
                                onChange = { intakePendingOnly = it == "pending" },
                                label = "Показ",
                            )
                        }
                        if (cities.size > 1) {
                            item {
                                SiteFilterSelect(
                                    value = intakeCity,
                                    items = cities.map { it.key to it.name },
                                    onChange = { intakeCity = it },
                                    label = "Город",
                                )
                            }
                        }
                        if (intakeShown.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    if (intakePendingOnly) "Нет заказов на проверке." else "Пока нет событий intake.",
                                    "Потяните вниз, чтобы обновить.",
                                )
                            }
                        } else {
                            items(intakeShown, key = { it.id }) { row ->
                                val pending = pendingOps.find { KassaApi.orderNo(it.raw) == KassaApi.orderNo(row.raw) }
                                LogisticsIntakeCard(
                                    row = row,
                                    pending = pending,
                                    busy = false,
                                    onOpen = { onOpenOp(pending ?: row) },
                                    onConfirm = {
                                        val op = pending ?: row
                                        pendingConfirm = op
                                    },
                                    onRetry = {
                                        val t = token ?: return@LogisticsIntakeCard
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) { api.postJson(KassaApi.retryProposalPath(row.id), t, JSONObject()) }
                                                msg = "Повтор отправлен"
                                                tick++
                                            } catch (e: Exception) {
                                                err = e.message ?: "Ошибка повтора"
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        item {
                            SiteSectionHead("Кто заходил и что менял", "Видно только вам. Входы на вкладку, открытие карточек, создание, правки, отмены и удаления.")
                        }
                        item {
                            SiteFilterSelect(
                                value = activityKind,
                                items = listOf("ALL" to "Все", "VIEWS" to "Входы", "EDITS" to "Изменения", "CANCELS" to "Отмены"),
                                onChange = { activityKind = it },
                                label = "Тип события",
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = activityQ,
                                onValueChange = { activityQ = it },
                                placeholder = { Text("Имя или № заказа") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(),
                            )
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InlineActionChip("Найти", filled = true) { activityQApplied = activityQ.trim() }
                                InlineActionChip("Сброс") { activityQ = ""; activityQApplied = "" }
                            }
                        }
                        if (activity.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    "Пока нет записей. Журнал начнёт заполняться после открытия вкладки коллегами и правок заказов.",
                                    err ?: "",
                                )
                            }
                        } else {
                            items(activity, key = { KassaApi.pick(it, "id").ifBlank { it.toString() } }) { n ->
                                Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(KassaApi.prettyTime(KassaApi.pick(n, "createdAt", "at")), color = AtColors.muted, fontSize = 12.sp)
                                    val who = n.optJSONObject("user")?.let { KassaApi.pick(it, "fullName", "username") }.orEmpty()
                                        .ifBlank { KassaApi.pick(n, "fullName", "username", "actorName") }
                                    Text(who.ifBlank { "—" }, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                                    Text(KassaApi.activityActionLabel(KassaApi.pick(n, "action", "kind", "type")), color = AtColors.accent, fontSize = 13.sp)
                                    val order = KassaApi.pick(n, "orderNumber")
                                    if (order.isNotBlank()) Text("#$order", color = AtColors.text, fontSize = 13.sp)
                                    val summary = KassaApi.pick(n, "summary", "what", "detail")
                                    Text(
                                        summary.ifBlank {
                                            if (KassaApi.pick(n, "action") == "OPERATIONS_TAB_OPENED") "Открыл вкладку «Операции»" else "—"
                                        },
                                        color = AtColors.muted,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            item { Text("Показано ${activity.size} из $activityTotal", color = AtColors.muted, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        OpCreateDialog(
            api = api,
            token = token,
            busy = createBusy,
            onDismiss = { if (!createBusy) creating = false },
            onSubmit = { body ->
                val t = token ?: return@OpCreateDialog
                createBusy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.postJson("/operations", t, body) }
                        creating = false
                        msg = "Операция создана"
                        tab = "orders"
                        tick++
                        Haptics.tap(ctx)
                    } catch (e: Exception) {
                        err = e.message ?: "Не удалось создать"
                    } finally {
                        createBusy = false
                    }
                }
            },
        )
    }
    pendingConfirm?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            containerColor = AtColors.panel,
            title = { Text("Подтвердить операцию?", color = AtColors.text, fontWeight = FontWeight.Bold) },
            text = { Text("Подтвердить операцию по заказу ${KassaApi.orderNo(row.raw).ifBlank { row.title }}?", color = AtColors.muted) },
            confirmButton = {
                TextButton(onClick = {
                    val t = token ?: return@TextButton
                    val id = row.id
                    pendingConfirm = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.postJson(KassaApi.confirmLogisticsPath(id), t, JSONObject()) }
                            msg = "Подтверждено"
                            tick++
                            Haptics.tap(ctx)
                        } catch (e: Exception) {
                            err = e.message ?: "Ошибка подтверждения"
                        }
                    }
                }) { Text("Подтвердить", color = AtColors.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pendingConfirm = null }) { Text("Отмена", color = AtColors.muted) } },
        )
    }
}

@Composable
private fun OpsOrderCard(row: JsonRow, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val o = row.raw
    val status = KassaApi.operationStatus(row)
    val orderNo = KassaApi.orderNo(o).ifBlank { row.title }
    val est = KassaApi.establishmentName(o)
    val estType = KassaApi.estTypeLabel(KassaApi.opEstType(o))
    val pay = KassaApi.paymentLabel(KassaApi.pick(o, "paymentType", "payment"))
    val whenAt = KassaApi.prettyTime(KassaApi.operationWhen(o))
    val userId = KassaApi.opUserId(o)
    val without = KassaApi.opMoney(o, "orderAmount", "amount")
    val delivery = KassaApi.opMoney(o, "deliveryAmount", "delivery")
    val with = KassaApi.opWithDelivery(o)
    val commission = KassaApi.opMoney(o, "commissionAmount")
    val payout = KassaApi.opMoney(o, "payoutAmount")
    val loyalty = KassaApi.opMoney(o, "loyaltyDiscountPercent")
    val promo = KassaApi.opMoney(o, "promoDiscountPercent")
    val free = o.optBoolean("isPlatformFreeDelivery", false)
    Column(
        Modifier.fillMaxWidth().atCard(16.dp).clickable(onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (orderNo.startsWith("№") || orderNo.startsWith("#")) orderNo else "№ $orderNo",
                color = AtColors.text,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusChip(prettyStatus(status.ifBlank { "CREATED" }))
        }
        if (est.isNotBlank()) Text(est, color = AtColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(
            listOf(estType.takeIf { it != "—" }, pay, whenAt, if (userId.isNotBlank()) "ID $userId" else "").filter { !it.isNullOrBlank() }.joinToString(" · "),
            color = AtColors.muted,
            fontSize = 12.sp,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyChip("Без дост.", KassaApi.tmt(without))
            MoneyChip("Доставка", KassaApi.tmt(delivery))
            MoneyChip("С дост.", KassaApi.tmt(with))
            if (commission > 0) MoneyChip("Комис.", KassaApi.tmt(commission))
            MoneyChip("К выплате", KassaApi.tmt(payout))
        }
        val extras = buildList {
            if (loyalty > 0) add("Лояльность: ${KassaApi.prettyNumber(loyalty.toString())}%")
            if (promo > 0) add("Промо: ${KassaApi.prettyNumber(promo.toString())}%")
            if (free) add("Беспл. дост.: ${KassaApi.tmt(KassaApi.opMoney(o, "platformDeliveryCompensation", "deliveryAmount"))}")
        }
        if (extras.isNotEmpty()) Text(extras.joinToString(" · "), color = AtColors.accent, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InlineActionChip("Копировать №") {
                DeviceIntents.copy(ctx, orderNo)
                Haptics.tap(ctx)
                Toast.makeText(ctx, "Номер скопирован", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun MoneyChip(label: String, value: String) {
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).background(AtColors.glass).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, color = AtColors.muted, fontSize = 10.sp)
        Text(value, color = AtColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LogisticsIntakeCard(
    row: JsonRow,
    pending: JsonRow?,
    busy: Boolean,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
) {
    val o = pending?.raw ?: row.raw
    val status = pending?.let { KassaApi.operationStatus(it) }.orEmpty().ifBlank { KassaApi.pick(row.raw, "status") }
    val err = KassaApi.pick(row.raw, "proposalError")
    val toneLabel = when {
        pending != null && KassaApi.operationStatus(pending) == "PENDING_REVIEW" -> "На проверке"
        err.isNotBlank() -> "Ошибка"
        else -> prettyStatus(status.ifBlank { "NEW" })
    }
    Column(
        Modifier.fillMaxWidth().atCard(14.dp).clickable(onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "№ ${KassaApi.orderNo(row.raw).ifBlank { row.title }}",
                color = AtColors.text,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            StatusChip(toneLabel)
        }
        Text(KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt", "orderDatetime")), color = AtColors.muted, fontSize = 12.sp)
        val client = KassaApi.opUserId(o).ifBlank { KassaApi.opUserId(row.raw) }
        val est = KassaApi.pick(row.raw, "establishmentTitle").ifBlank { KassaApi.establishmentName(o) }
        if (client.isNotBlank()) Text("Клиент: $client", color = AtColors.text, fontSize = 13.sp)
        if (est.isNotBlank()) Text(est, color = AtColors.text, fontSize = 13.sp)
        val city = KassaApi.cityLabel(KassaApi.pick(o, "cityKey").ifBlank { KassaApi.pick(row.raw, "cityKey") })
        if (city != "—") Text(city, color = AtColors.muted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyChip("Без дост.", KassaApi.tmt(KassaApi.opMoney(o, "orderAmount")))
            MoneyChip("К выплате", KassaApi.tmt(KassaApi.opMoney(o, "payoutAmount")))
        }
        if (err.isNotBlank()) Text(err, color = AtColors.danger, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (pending != null || KassaApi.needsConfirm(row)) {
                InlineActionChip(if (busy) "…" else "Подтвердить", filled = true) { if (!busy) onConfirm() }
            }
            if (err.isNotBlank() || KassaApi.needsRetry(row)) {
                InlineActionChip(if (busy) "…" else "Повторить") { if (!busy) onRetry() }
            }
        }
    }
}

@Composable
private fun OpCreateDialog(
    api: KassaApi,
    token: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (JSONObject) -> Unit,
) {
    var orderNo by remember { mutableStateOf("") }
    var estQ by remember { mutableStateOf("") }
    var estId by remember { mutableStateOf("") }
    var estName by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var amount by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf("0") }
    var pay by remember { mutableStateOf("CASH") }
    var clientId by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(estQ) {
        if (estQ.trim().length < 2 || token.isNullOrBlank()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        val enc = java.net.URLEncoder.encode(estQ.trim(), "UTF-8")
        hits = withContext(Dispatchers.IO) {
            runCatching { api.getRows("/establishments?q=$enc", token) }.getOrElse {
                runCatching { api.getRows("/sales/establishments?q=$enc", token) }.getOrDefault(emptyList())
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp),
        ) {
            Text("Новая операция", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(orderNo, { orderNo = it }, label = { Text("№ заказа") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(estQ, { estQ = it; estId = ""; estName = "" }, label = { Text("Заведение") }, placeholder = { Text("Поиск по названию") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                hits.take(8).forEach { e ->
                    val name = KassaApi.pick(e.raw, "name", "title").ifBlank { e.title }
                    Text(
                        name,
                        color = AtColors.text,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AtColors.glass).clickable {
                            estId = e.id
                            estName = name
                            estQ = name
                            hits = emptyList()
                        }.padding(10.dp),
                    )
                }
                Text("Оплата", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(value = pay, items = listOf("CASH" to "Наличные", "ONLINE" to "Онлайн", "MIXED" to "Смешанная"), onChange = { pay = it })
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма без доставки") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(delivery, { delivery = it }, label = { Text("Доставка") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(clientId, { clientId = it.filter { ch -> ch.isDigit() }.take(4) }, label = { Text("ID пользователя (4 цифры)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                err?.let { Text(it, color = AtColors.danger, fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.glass).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("Отмена", color = AtColors.muted, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent).clickable(enabled = !busy) {
                        if (orderNo.trim().isBlank()) { err = "Укажите номер заказа"; return@clickable }
                        if (estId.isBlank()) { err = "Выберите заведение"; return@clickable }
                        val amt = amount.replace(',', '.').toDoubleOrNull()
                        if (amt == null || amt <= 0) { err = "Укажите сумму больше 0"; return@clickable }
                        if (clientId.isNotBlank() && clientId.length != 4) { err = "ID пользователя: ровно 4 цифры или оставьте пустым"; return@clickable }
                        val body = JSONObject()
                            .put("orderNumber", orderNo.trim())
                            .put("establishmentId", estId)
                            .put("paymentType", pay)
                            .put("deliveryPaymentType", pay)
                            .put("orderAmount", amt)
                            .put("commissionBaseAmount", amt)
                            .put("deliveryAmount", delivery.replace(',', '.').toDoubleOrNull() ?: 0.0)
                            .put("orderDatetime", java.time.Instant.now().toString())
                        if (clientId.length == 4) body.put("appClientExternalUserId", clientId)
                        onSubmit(body)
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(if (busy) "Создание…" else "Создать", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun OperationDetailPane(
    title: String,
    operationId: String,
    preload: JSONObject?,
    api: KassaApi,
    token: String?,
    me: AppUser?,
    onBack: () -> Unit,
    onUpdated: (JSONObject) -> Unit,
    onOpenChat: ((String, String) -> Unit)?,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var obj by remember { mutableStateOf(preload) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var problemOpen by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(operationId, tick) {
        if (token.isNullOrBlank() || operationId.isBlank()) return@LaunchedEffect
        if (obj == null) obj = preload
        refreshing = obj != null
        try {
            val next = withContext(Dispatchers.IO) {
                val o = api.getObject(KassaApi.operationDetailPath(operationId), token)
                api.pingOpsActivity(token, "CARD", operationId, KassaApi.pick(o, "orderNumber"))
                o
            }
            obj = next
        } catch (e: Exception) {
            if (obj == null) err = e.message
        } finally {
            refreshing = false
        }
    }

    val o = obj
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine(title.ifBlank { "Операция" }, onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                err != null && o == null -> ErrorBlock(err!!, onRetry = { tick++ })
                o == null -> LoadingCard()
                else -> {
                    val orderNo = KassaApi.orderNo(o).ifBlank { title }
                    val status = KassaApi.pick(o, "status")
                    val lines = KassaApi.opLineItems(o)
                    val phone = KassaApi.phoneOf(o)
                    val chat = KassaApi.chatLinkFromOperation(o)
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (err != null) item { ActionBanner(err!!, error = true) }
                        if (msg != null) item { ActionBanner(msg!!, error = false) }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusChip(prettyStatus(status.ifBlank { "CREATED" }))
                                    Text(KassaApi.paymentLabel(KassaApi.pick(o, "paymentType")), color = AtColors.muted, fontSize = 13.sp)
                                }
                                Text("№ $orderNo", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                Text(KassaApi.establishmentName(o).ifBlank { "—" }, color = AtColors.text, fontSize = 15.sp)
                                Text(
                                    listOf(
                                        KassaApi.estTypeLabel(KassaApi.opEstType(o)),
                                        KassaApi.prettyTime(KassaApi.operationWhen(o)),
                                        KassaApi.opUserId(o).takeIf { it.isNotBlank() }?.let { "ID $it" },
                                    ).filter { !it.isNullOrBlank() && it != "—" }.joinToString(" · "),
                                    color = AtColors.muted,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Суммы", color = AtColors.text, fontWeight = FontWeight.Bold)
                                val without = KassaApi.opMoney(o, "orderAmount", "amount")
                                val delivery = KassaApi.opMoney(o, "deliveryAmount")
                                val commission = KassaApi.opMoney(o, "commissionAmount")
                                val payout = KassaApi.opMoney(o, "payoutAmount")
                                ReportKpiGrid(
                                    listOf(
                                        PulseMetric("Без дост.", KassaApi.tmt(without), "", "₸"),
                                        PulseMetric("Доставка", KassaApi.tmt(delivery), "", "›"),
                                        PulseMetric("С дост.", KassaApi.tmt(KassaApi.opWithDelivery(o)), "", "Σ"),
                                        PulseMetric("К выплате", KassaApi.tmt(payout), if (commission > 0) "комис. ${KassaApi.tmt(commission)}" else "", "✓"),
                                    ),
                                )
                                val extras = buildList {
                                    val loyalty = KassaApi.opMoney(o, "loyaltyDiscountPercent")
                                    val promo = KassaApi.opMoney(o, "promoDiscountPercent")
                                    if (loyalty > 0) add("Лояльность: ${KassaApi.prettyNumber(loyalty.toString())}%")
                                    if (promo > 0) add("Промо: ${KassaApi.prettyNumber(promo.toString())}%")
                                    if (o.optBoolean("isPlatformFreeDelivery", false)) add("Бесплатная доставка платформы")
                                }
                                if (extras.isNotEmpty()) Text(extras.joinToString(" · "), color = AtColors.accent, fontSize = 13.sp)
                            }
                        }
                        if (lines.isNotEmpty()) {
                            item {
                                Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Состав заказа", color = AtColors.text, fontWeight = FontWeight.Bold)
                                    lines.forEach { line ->
                                        val name = KassaApi.pick(line, "dishName", "name", "title")
                                        val qty = KassaApi.pick(line, "quantity").ifBlank { "1" }
                                        val sum = KassaApi.tmt(KassaApi.jsonNum(line, "lineTotal", "amount"))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("$qty × $name", color = AtColors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                            Text(sum, color = AtColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Действия", color = AtColors.text, fontWeight = FontWeight.Bold)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (status.equals("PENDING_REVIEW", true) || KassaApi.needsConfirm(JsonRow(operationId, "", "", o))) {
                                        InlineActionChip("Подтвердить", filled = true) {
                                            val t = token ?: return@InlineActionChip
                                            busy = true
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { api.postJson(KassaApi.confirmLogisticsPath(operationId), t, JSONObject()) }
                                                    msg = "Подтверждено"
                                                    tick++
                                                } catch (e: Exception) { err = e.message } finally { busy = false }
                                            }
                                        }
                                    }
                                    InlineActionChip("Редактировать") { editing = true }
                                    InlineActionChip("Проблема") { problemOpen = true }
                                    if (phone.isNotBlank()) {
                                        InlineActionChip("Позвонить") { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                                    }
                                    if (chat != null && onOpenChat != null) {
                                        InlineActionChip("Написать") { onOpenChat(chat.first, chat.second) }
                                    }
                                    InlineActionChip("Копировать №") {
                                        DeviceIntents.copy(ctx, orderNo)
                                        msg = "Номер скопирован"
                                    }
                                }
                            }
                        }
                        item {
                            Box(
                                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFDC2626)).clickable { deleteOpen = true },
                                contentAlignment = Alignment.Center,
                            ) { Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
    if (editing && o != null) {
        OpEditDialog(
            obj = o,
            busy = busy,
            onDismiss = { editing = false },
            onSave = { body ->
                val t = token ?: return@OpEditDialog
                busy = true
                scope.launch {
                    try {
                        val enc = java.net.URLEncoder.encode(operationId, "UTF-8")
                        val next = withContext(Dispatchers.IO) { api.patchJson("/operations/$enc", t, body) }
                        obj = if (next.has("id") || next.has("orderNumber")) next else JSONObject(o.toString()).apply {
                            body.keys().forEach { put(it, body.opt(it)) }
                        }
                        onUpdated(obj!!)
                        editing = false
                        msg = "Операция успешно сохранена"
                        tick++
                    } catch (e: Exception) {
                        err = e.message ?: "Ошибка сохранения"
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = AtColors.panel,
            title = { Text("Удалить операцию?", color = AtColors.text, fontWeight = FontWeight.Bold) },
            text = { Text("Удалить операцию по заказу ${KassaApi.orderNo(o ?: JSONObject())}? Действие нельзя отменить.", color = AtColors.muted) },
            confirmButton = {
                TextButton(onClick = {
                    val t = token ?: return@TextButton
                    deleteOpen = false
                    scope.launch {
                        try {
                            val enc = java.net.URLEncoder.encode(operationId, "UTF-8")
                            withContext(Dispatchers.IO) { api.deletePath("/operations/$enc", t) }
                            onBack()
                        } catch (e: Exception) { err = e.message ?: "Ошибка удаления" }
                    }
                }) { Text("Удалить", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Отмена", color = AtColors.muted) } },
        )
    }
    if (problemOpen) {
        ConfirmProblemDialog(
            title = KassaApi.orderNo(o ?: JSONObject()).ifBlank { "этот заказ" },
            onConfirm = { note ->
                val t = token ?: return@ConfirmProblemDialog
                problemOpen = false
                scope.launch {
                    try {
                        val row = JsonRow(operationId, title, "", o ?: JSONObject())
                        withContext(Dispatchers.IO) { api.postJson("/problem-orders", t, KassaApi.problemOrderBody(row, note)) }
                        msg = "Заказ в проблемных"
                    } catch (e: Exception) { err = e.message }
                }
            },
            onDismiss = { problemOpen = false },
        )
    }
}

@Composable
private fun OpEditDialog(
    obj: JSONObject,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (JSONObject) -> Unit,
) {
    var amount by remember { mutableStateOf(KassaApi.opMoney(obj, "orderAmount", "amount").toString()) }
    var delivery by remember { mutableStateOf(KassaApi.opMoney(obj, "deliveryAmount").toString()) }
    var pay by remember { mutableStateOf(KassaApi.pick(obj, "paymentType").ifBlank { "CASH" }) }
    var loyalty by remember { mutableStateOf(KassaApi.opMoney(obj, "loyaltyDiscountPercent").toString()) }
    var promo by remember { mutableStateOf(KassaApi.opMoney(obj, "promoDiscountPercent").toString()) }
    var free by remember { mutableStateOf(obj.optBoolean("isPlatformFreeDelivery", false)) }
    var err by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp),
        ) {
            Text("Редактировать", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Оплата", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(value = pay, items = listOf("CASH" to "Наличные", "ONLINE" to "Онлайн", "MIXED" to "Смешанная"), onChange = { pay = it })
                OutlinedTextField(amount, { amount = it }, label = { Text("Сумма без доставки") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(delivery, { delivery = it }, label = { Text("Доставка") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(loyalty, { loyalty = it }, label = { Text("Лояльность %") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(promo, { promo = it }, label = { Text("Промо %") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                Row(Modifier.fillMaxWidth().clickable { free = !free }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (free) "☑" else "☐", modifier = Modifier.width(22.dp), color = AtColors.accent)
                    Text("Бесплатная доставка платформы", color = AtColors.text)
                }
                err?.let { Text(it, color = AtColors.danger) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.glass).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("Отмена", color = AtColors.muted, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent).clickable(enabled = !busy) {
                        val amt = amount.replace(',', '.').toDoubleOrNull()
                        if (amt == null || amt < 0) { err = "Укажите сумму"; return@clickable }
                        val del = delivery.replace(',', '.').toDoubleOrNull() ?: 0.0
                        val body = JSONObject()
                            .put("paymentType", pay)
                            .put("deliveryPaymentType", pay)
                            .put("orderAmount", amt)
                            .put("commissionBaseAmount", amt)
                            .put("deliveryAmount", del)
                            .put("loyaltyDiscountPercent", loyalty.replace(',', '.').toDoubleOrNull() ?: 0.0)
                            .put("promoDiscountPercent", promo.replace(',', '.').toDoubleOrNull() ?: 0.0)
                            .put("isPlatformFreeDelivery", free)
                        onSave(body)
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(if (busy) "Сохранение…" else "Сохранить", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}


// --- recovered from tasks-workspace.kt ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksWorkspacePane(
    api: KassaApi,
    token: String?,
    me: AppUser?,
    cache: CacheStore?,
    onBack: () -> Unit,
    onOpenTask: (JsonRow) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var users by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var templates by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var activity by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var notices by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var serverQ by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var view by remember { mutableStateOf("kanban") }
    var column by remember { mutableStateOf("new") }
    var assigneeFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var priorityFilter by remember { mutableStateOf("ALL") }
    var sortMode by remember { mutableStateOf("priority") }
    var duePreset by remember { mutableStateOf("ALL") }
    var showFilters by remember { mutableStateOf(false) }
    var mineOnly by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var createBusy by remember { mutableStateOf(false) }
    var pendingStatus by remember { mutableStateOf<Pair<JsonRow, String>?>(null) }
    var reportText by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var bulkBusy by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(q) {
        delay(280)
        serverQ = q.trim()
    }

    LaunchedEffect(token, tick, serverQ) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        err = null
        if (loaded) refreshing = true
        try {
            val pack = withContext(Dispatchers.IO) {
                val active = runCatching { api.getRows(KassaApi.taskListPath(false, serverQ), token) }.getOrDefault(emptyList())
                val archived = runCatching { api.getRows(KassaApi.taskListPath(true, serverQ), token) }.getOrDefault(emptyList())
                val merged = LinkedHashMap<String, JsonRow>()
                (active + archived).forEach { if (it.id.isNotBlank()) merged[it.id] = it }
                val u = runCatching { api.getRows("/workspace/users", token) }.getOrElse {
                    runCatching { api.getRows("/users", token) }.getOrDefault(emptyList())
                }
                val tplObj = runCatching { api.getObject("/workspace/task-templates", token) }.getOrNull()
                val nObj = runCatching { api.getObject("/workspace/notifications", token) }.getOrNull()
                val nArr = nObj?.optJSONArray("items") ?: nObj?.optJSONArray("data")
                val nList = if (nArr != null) KassaApi.eachObj(nArr) else emptyList()
                Triple(merged.values.toList(), u, Triple(KassaApi.parseTaskTemplates(tplObj), nList, KassaApi.unreadTaskActivity(nList)))
            }
            rows = pack.first
            users = pack.second
            templates = pack.third.first.ifEmpty { builtinTaskTemplates() }
            notices = pack.third.second
            activity = pack.third.third
            cache?.putRows("/workspace/tasks", pack.first)
            loaded = true
        } catch (e: Exception) {
            err = e.message
            cache?.getRows("/workspace/tasks")?.let {
                rows = it
                loaded = true
            }
        } finally {
            refreshing = false
        }
    }

    val query = q.trim().lowercase()
    val filtered = remember(rows, filter, query, me, assigneeFilter, statusFilter, priorityFilter, duePreset, mineOnly, sortMode, serverQ) {
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val startToday = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val endToday = startToday + 86_400_000
        val week = now + 7 * 86_400_000L
        rows.filter { row ->
            val status = KassaApi.pick(row.raw, "status", "state").uppercase()
            val done = KassaApi.isTaskDone(row)
            val dueRaw = KassaApi.taskDueRaw(row)
            val dueAt = dueRaw.takeIf { it.isNotBlank() }?.let { raw ->
                try {
                    java.time.Instant.parse(raw).toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
            val matchQuick = when (filter) {
                "urgent" -> !done && dueAt != null && dueAt < endToday
                "today" -> !done && dueAt != null && dueAt >= now && dueAt < endToday
                "archived" -> status == "ARCHIVED" || status == "DONE"
                else -> status != "ARCHIVED"
            }
            if (!matchQuick) return@filter false
            if (statusFilter != "ALL" && status != statusFilter && !(statusFilter == "OVERDUE" && KassaApi.taskColumn(row) == "overdue")) return@filter false
            if (priorityFilter != "ALL" && KassaApi.pick(row.raw, "priority").uppercase() != priorityFilter) return@filter false
            if (assigneeFilter != "ALL" && assigneeFilter !in KassaApi.taskPeopleIds(row.raw, "assignees")) return@filter false
            if (mineOnly && me != null && !KassaApi.assignedToMe(row, me) && KassaApi.taskCreatorId(row.raw) != me.id) return@filter false
            if (duePreset == "OVERDUE" && !KassaApi.taskIsOverdue(row)) return@filter false
            if (duePreset == "TODAY" && (dueAt == null || dueAt < startToday || dueAt >= endToday)) return@filter false
            if (duePreset == "WEEK" && (dueAt == null || dueAt < now || dueAt >= week)) return@filter false
            if (query.isBlank()) true
            else {
                val hay = listOf(
                    row.title,
                    row.subtitle,
                    KassaApi.pick(row.raw, "description", "text"),
                    KassaApi.taskAssignees(row),
                    KassaApi.taskContextLabel(row.raw),
                ).joinToString(" ").lowercase()
                hay.contains(query)
            }
        }.sortedWith(
            when (sortMode) {
                "deadline" -> compareBy<JsonRow> {
                    val raw = KassaApi.taskDueRaw(it)
                    if (raw.isBlank()) Long.MAX_VALUE else runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrDefault(Long.MAX_VALUE)
                }
                "newest" -> compareByDescending { KassaApi.pick(it.raw, "updatedAt", "createdAt", "id") }
                else -> compareBy<JsonRow> { KassaApi.taskPriorityRank(it) }.thenBy { KassaApi.taskDueRaw(it).ifBlank { "zzz" } }
            },
        )
    }
    val activeN = rows.count { !KassaApi.isTaskDone(it) && KassaApi.pick(it.raw, "status").uppercase() != "ARCHIVED" }
    val overdueN = rows.count { KassaApi.taskIsOverdue(it) }
    val todayN = rows.count {
        !KassaApi.isTaskDone(it) && !KassaApi.taskIsOverdue(it) && KassaApi.isDueToday(KassaApi.taskDueRaw(it))
    }
    val closedN = rows.count { KassaApi.isTaskDone(it) }
    val colItems = filtered.filter { KassaApi.taskColumn(it) == column }

    fun setStatus(row: JsonRow, status: String, report: String = "") {
        val t = token ?: return
        val from = KassaApi.pick(row.raw, "status", "state").uppercase()
        if (from == "IN_PROGRESS" && status == "IN_REVIEW" && report.isBlank()) {
            pendingStatus = row to status
            reportText = ""
            return
        }
        if (from == "IN_REVIEW" && status == "DONE" && !KassaApi.canCompleteTaskReview(row.raw, me)) {
            err = "Завершить задачу на проверке может только постановщик."
            return
        }
        if (from == "IN_REVIEW" && status == "IN_PROGRESS" && pendingStatus == null) {
            pendingStatus = row to status
            reportText = ""
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { api.setTaskStatus(t, row.id, status, report) }
                msg = "Статус: ${KassaApi.taskStatusLabel(status)}"
                pendingStatus = null
                tick++
                Haptics.tap(ctx)
            } catch (e: Exception) {
                err = e.message ?: "Не удалось сменить статус"
            }
        }
    }

    fun bulk(status: String) {
        val t = token ?: return
        if (selected.isEmpty()) return
        bulkBusy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    selected.forEach { id -> runCatching { api.setTaskStatus(t, id, status) } }
                }
                selected = emptySet()
                msg = "Обновлено: ${KassaApi.taskStatusLabel(status)}"
                tick++
            } catch (e: Exception) {
                err = e.message
            } finally {
                bulkBusy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Задачи", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                err != null && !loaded -> ErrorBlock(err!!, onRetry = { tick++ })
                !loaded -> LoadingCard()
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        SiteSectionHead("Задачи", "Процесс: постановка → работа → проверка → закрытие. Несколько исполнителей на одну задачу.")
                    }
                    item {
                        ReportKpiGrid(
                            listOf(
                                PulseMetric("Всего", rows.size.toString(), "по текущей выборке", "✓"),
                                PulseMetric("Активные", activeN.toString(), "не закрыты", "◉"),
                                PulseMetric("Просроченные", overdueN.toString(), if (overdueN > 0) "нажмите баннер" else "в срок", "!", tint = if (overdueN > 0) "danger" else ""),
                                PulseMetric("Закрытые", closedN.toString(), "выполнены / архив", "▣"),
                            ),
                        )
                    }
                    if (overdueN > 0 || todayN > 0) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(AtColors.danger.copy(alpha = 0.10f))
                                    .border(1.dp, AtColors.danger.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                                    .clickable { filter = if (overdueN > 0) "urgent" else "today" }
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text("Дедлайны", color = AtColors.text, fontWeight = FontWeight.Bold)
                                if (overdueN > 0) Text("Просрочено: $overdueN", color = AtColors.danger, fontWeight = FontWeight.SemiBold)
                                if (todayN > 0) Text("Сегодня: $todayN", color = AtColors.text, fontSize = 13.sp)
                            }
                        }
                    }
                    item {
                        Box(
                            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent)
                                .clickable { creating = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("+ Поставить задачу", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = q,
                            onValueChange = { q = it },
                            placeholder = { Text("Поиск по названию/описанию") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                    item {
                        Text("Показать", color = AtColors.muted, fontSize = 12.sp)
                        SiteSegmented(
                            value = filter,
                            items = listOf(
                                "urgent" to "Срочные",
                                "today" to "Сегодня",
                                "all" to "Все",
                                "archived" to "Архив",
                            ),
                            onChange = { filter = it },
                        )
                    }
                    item {
                        SiteSegmented(
                            value = if (mineOnly) "mine" else "everyone",
                            items = listOf("everyone" to "Все сотрудники", "mine" to "Мои"),
                            onChange = { mineOnly = it == "mine" },
                        )
                    }
                    if (filter != "all") {
                        item {
                            Text(
                                if (filter == "archived") "Показан только архив — поиск и сортировка ниже."
                                else "Фильтр «${when (filter) { "urgent" -> "Срочные"; "today" -> "Сегодня"; else -> filter }}» скрывает остальные задачи с канбана.",
                                color = AtColors.muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    item {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AtColors.glass)
                                .clickable { showFilters = !showFilters }.padding(12.dp),
                        ) {
                            Text(if (showFilters) "Фильтры ▾" else "Фильтры ▸", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (showFilters) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Исполнитель", color = AtColors.muted, fontSize = 12.sp)
                                SiteSegmented(
                                    value = assigneeFilter,
                                    items = listOf("ALL" to "Все") + users.take(12).map {
                                        it.id to KassaApi.pick(it.raw, "fullName", "name").ifBlank { it.title }
                                    }.filter { it.first.isNotBlank() },
                                    onChange = { assigneeFilter = it },
                                )
                                Text("Статус", color = AtColors.muted, fontSize = 12.sp)
                                SiteSegmented(
                                    value = statusFilter,
                                    items = listOf(
                                        "ALL" to "Все",
                                        "NEW" to "Новая",
                                        "IN_PROGRESS" to "В работе",
                                        "IN_REVIEW" to "На проверке",
                                        "DONE" to "Выполнена",
                                        "OVERDUE" to "Просрочена",
                                        "ARCHIVED" to "Архив",
                                    ),
                                    onChange = { statusFilter = it },
                                )
                                Text("Приоритет", color = AtColors.muted, fontSize = 12.sp)
                                SiteSegmented(
                                    value = priorityFilter,
                                    items = listOf("ALL" to "Любой", "HIGH" to "Высокий", "MEDIUM" to "Средний", "LOW" to "Низкий"),
                                    onChange = { priorityFilter = it },
                                )
                                Text("Срок (точнее)", color = AtColors.muted, fontSize = 12.sp)
                                SiteSegmented(
                                    value = duePreset,
                                    items = listOf("ALL" to "Все", "OVERDUE" to "Просрочено", "TODAY" to "Сегодня", "WEEK" to "7 дней"),
                                    onChange = { duePreset = it },
                                )
                                Text("Сортировка", color = AtColors.muted, fontSize = 12.sp)
                                SiteSegmented(
                                    value = sortMode,
                                    items = listOf("priority" to "По приоритету", "deadline" to "По дедлайну", "newest" to "Новые сверху"),
                                    onChange = { sortMode = it },
                                )
                            }
                        }
                    }
                    item {
                        SiteSegmented(
                            value = view,
                            items = listOf("kanban" to "Канбан", "list" to "Список"),
                            onChange = { view = it; if (it != "list") selected = emptySet() },
                        )
                    }
                    val unreadN = notices.count { !it.optBoolean("isRead", false) && KassaApi.pick(it, "type").uppercase().startsWith("TASK") }
                    if (unreadN > 0) {
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Уведомления", color = AtColors.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    InlineActionChip("Прочитать все") {
                                        val t = token ?: return@InlineActionChip
                                        scope.launch {
                                            runCatching { withContext(Dispatchers.IO) { api.markNotificationsScope(t, "tasks") } }
                                            tick++
                                        }
                                    }
                                }
                                notices.filter { !it.optBoolean("isRead", false) }.take(5).forEach { n ->
                                    val text = KassaApi.pick(n, "title", "text", "message").ifBlank { "Событие по задаче" }
                                    Text(text, color = AtColors.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (err != null) item { ActionBanner(err!!, error = true) }
                    if (msg != null) item { ActionBanner(msg!!, error = false) }
                    if (view == "list" && filtered.isNotEmpty()) {
                        item {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Выбрано: ${selected.size}", color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                                InlineActionChip("Массово → В работе", filled = false) { if (!bulkBusy) bulk("IN_PROGRESS") }
                                InlineActionChip("Массово → Выполнена") { if (!bulkBusy) bulk("DONE") }
                                InlineActionChip("Массово → Архив") { if (!bulkBusy) bulk("ARCHIVED") }
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                "Пока задач нет по выбранным фильтрам.",
                                "Смените «Показать» или поставьте новую задачу.",
                            )
                        }
                    } else if (view == "kanban") {
                        item {
                            SiteSegmented(
                                value = column,
                                items = listOf(
                                    "new" to "Новая",
                                    "progress" to "В работе",
                                    "review" to "На проверке",
                                    "overdue" to "Просрочена",
                                    "done" to "Выполнена",
                                ).map { (k, label) ->
                                    val n = filtered.count { KassaApi.taskColumn(it) == k }
                                    k to "$label $n"
                                },
                                onChange = { column = it },
                            )
                        }
                        if (colItems.isEmpty()) {
                            item { EmptyStateCard("Пусто", "В этой колонке нет задач.") }
                        } else {
                            items(colItems, key = { it.id }) { row ->
                                WorkspaceTaskCard(
                                    row = row,
                                    all = rows,
                                    activity = activity[row.id] ?: 0,
                                    selectable = false,
                                    selected = false,
                                    onToggleSelect = {},
                                    onOpen = { onOpenTask(row) },
                                    onStatus = { status -> setStatus(row, status) },
                                )
                            }
                        }
                    } else {
                        item {
                            Text("Список: нажмите на строку для карточки. Чек-лист и комментарии — внутри.", color = AtColors.muted, fontSize = 12.sp)
                        }
                        items(filtered, key = { it.id }) { row ->
                            WorkspaceTaskCard(
                                row = row,
                                all = rows,
                                activity = activity[row.id] ?: 0,
                                selectable = true,
                                selected = row.id in selected,
                                onToggleSelect = {
                                    selected = if (row.id in selected) selected - row.id else selected + row.id
                                },
                                onOpen = { onOpenTask(row) },
                                onStatus = { status -> setStatus(row, status) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TaskCreateDialog(
            api = api,
            token = token,
            users = users,
            templates = templates,
            meId = me?.id.orEmpty(),
            busySaving = createBusy,
            onDismiss = { if (!createBusy) creating = false },
            onTemplates = { templates = it },
            onSubmit = { body ->
                val t = token ?: return@TaskCreateDialog
                createBusy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.postJson("/workspace/tasks", t, body) }
                        creating = false
                        msg = "Задача создана"
                        tick++
                        Haptics.tap(ctx)
                    } catch (e: Exception) {
                        err = e.message ?: "Не удалось создать задачу"
                    } finally {
                        createBusy = false
                    }
                }
            },
        )
    }
    pendingStatus?.let { (row, status) ->
        val review = status == "IN_REVIEW"
        AlertDialog(
            onDismissRequest = { pendingStatus = null },
            containerColor = AtColors.panel,
            title = { Text(if (review) "Отчёт перед проверкой" else "Вернуть в работу", color = AtColors.text, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (review) "Кратко опишите, что сделано — отчёт увидит постановщик."
                        else "Можно указать, что нужно доработать (необязательно).",
                        color = AtColors.muted,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = reportText,
                        onValueChange = { reportText = it },
                        placeholder = { Text(if (review) "Что сделано, что осталось, риски…" else "Что исправить…") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                        colors = fieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (review && reportText.trim().isBlank()) {
                            err = "Нужен отчёт, чтобы отправить задачу на проверку"
                            return@TextButton
                        }
                        setStatus(row, status, reportText)
                    },
                ) { Text(if (review) "На проверку → На проверке" else "Вернуть в работу", color = AtColors.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingStatus = null }) { Text("Отмена", color = AtColors.muted) }
            },
        )
    }
}

private fun builtinTaskTemplates(): List<JSONObject> {
    fun tpl(id: String, label: String, vararg checks: String, types: List<String>): JSONObject {
        val arr = org.json.JSONArray()
        checks.forEach { arr.put(it) }
        val ctx = org.json.JSONArray()
        types.forEach { ctx.put(it) }
        return JSONObject().put("id", id).put("label", label).put("title", label).put("checklist", arr).put("contextTypes", ctx)
    }
    return listOf(
        tpl("lead-new", "Стадия: Новый лид", "Проверить входящие данные", "Связаться с клиентом", "Зафиксировать next step", types = listOf("CONTACT", "DEAL", "NONE")),
        tpl("qualification", "Стадия: Квалификация", "Уточнить потребность", "Проверить бюджет/срок", "Назначить демо/созвон", types = listOf("CONTACT", "DEAL")),
        tpl("review", "Стадия: Проверка/согласование", "Собрать согласования", "Проверить комплектность", "Передать на финальную проверку", types = listOf("DEAL", "ENTITY")),
        tpl("sales-pricing-sync", "Продажи: Синхронизация цен/меню", "Запросить актуальный прайс/меню", "Сверить различия с текущей карточкой", "Передать изменения в публикацию", types = listOf("ENTITY")),
    )
}

@Composable
private fun WorkspaceTaskCard(
    row: JsonRow,
    all: List<JsonRow>,
    activity: Int,
    selectable: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val status = KassaApi.pick(row.raw, "status", "state")
    val due = KassaApi.taskDueRaw(row)
    val remaining = KassaApi.remainingLabel(due)
    val assignee = KassaApi.taskAssignees(row)
    val items = KassaApi.checklistItems(row.raw)
    val commentsN = KassaApi.taskCommentCount(row.raw)
    val overdue = KassaApi.taskIsOverdue(row)
    val stage = KassaApi.taskColumn(row)
    val priority = KassaApi.taskPriorityLabel(KassaApi.pick(row.raw, "priority"))
    val ctx = KassaApi.taskContextLabel(row.raw)
    val sibling = KassaApi.siblingStats(all, row)
    val rawStatus = status.uppercase()
    val moves = when {
        rawStatus == "ARCHIVED" -> listOf("DONE" to "Из архива → Выполнена")
        stage == "new" -> listOf("IN_PROGRESS" to "Далее: В работе")
        stage == "progress" -> listOf("IN_REVIEW" to "На проверку")
        stage == "review" -> listOf("IN_PROGRESS" to "Вернуть в работу", "DONE" to "Принять")
        stage == "overdue" -> listOf("IN_PROGRESS" to "В работу")
        stage == "done" -> listOf("ARCHIVED" to "Архив", "IN_PROGRESS" to "Из архива → В работе")
        else -> emptyList()
    }
    Column(
        Modifier.fillMaxWidth().atCard(14.dp).clickable(onClick = onOpen).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectable) {
                Text(
                    if (selected) "☑" else "☐",
                    color = if (selected) AtColors.accent else AtColors.muted,
                    modifier = Modifier.clickable { onToggleSelect() }.padding(end = 4.dp),
                )
            }
            StatusChip(KassaApi.taskStatusLabel(status))
            if (overdue) StatusChip("Просрочено")
            if (activity > 0) StatusChip(if (activity > 1) "Новое · $activity" else "Новое")
            Text("Приоритет: $priority", color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Text(row.title.ifBlank { "Без названия" }, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (ctx.isNotBlank()) Text(ctx, color = AtColors.accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val who = assignee.ifBlank { "Без исполнителя" }
        Text(who, color = AtColors.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val bits = buildList {
            if (remaining.isNotBlank()) add(remaining) else add("Срок: —")
            sibling?.let { add("Связанные: принято ${it.first}/${it.second}") }
            if (items.isNotEmpty()) add("Чек-лист ${items.count { it.third }}/${items.size}")
            add(if (commentsN == 0) "Нет комм." else "$commentsN комм.")
        }
        Text(bits.joinToString(" · "), color = AtColors.text, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            moves.forEach { (st, label) ->
                InlineActionChip(label, filled = st == "DONE") { onStatus(st) }
            }
        }
    }
}

@Composable
private fun TaskCreateDialog(
    api: KassaApi,
    token: String?,
    users: List<JsonRow>,
    templates: List<JSONObject>,
    meId: String,
    busySaving: Boolean,
    onDismiss: () -> Unit,
    onTemplates: (List<JSONObject>) -> Unit,
    onSubmit: (JSONObject) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var autoSla by remember { mutableStateOf(false) }
    var duePreset by remember { mutableStateOf("none") }
    var contextType by remember { mutableStateOf("NONE") }
    var contextId by remember { mutableStateOf("") }
    var contextTitle by remember { mutableStateOf("") }
    var estQ by remember { mutableStateOf("") }
    var estHits by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var estBusy by remember { mutableStateOf(false) }
    var assignees by remember { mutableStateOf(if (meId.isBlank()) emptySet() else setOf(meId)) }
    var watchers by remember { mutableStateOf(emptySet<String>()) }
    var peopleQ by remember { mutableStateOf("") }
    var checklist by remember { mutableStateOf("") }
    var templateId by remember { mutableStateOf("") }
    var ownOpen by remember { mutableStateOf(false) }
    var ownLabel by remember { mutableStateOf("") }
    var ownTitle by remember { mutableStateOf("") }
    var ownCheck by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val visibleTpl = templates.filter { KassaApi.templateMatchesContext(it, contextType) }.ifEmpty { templates }

    LaunchedEffect(estQ, contextType) {
        if (contextType != "ENTITY" || estQ.trim().length < 2 || token.isNullOrBlank()) {
            estHits = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        estBusy = true
        try {
            val q = java.net.URLEncoder.encode(estQ.trim(), "UTF-8")
            estHits = withContext(Dispatchers.IO) {
                runCatching { api.getRows("/sales/establishments?q=$q", token) }.getOrDefault(emptyList())
            }
        } catch (_: Exception) {
            estHits = emptyList()
        } finally {
            estBusy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp),
        ) {
            Text("Постановка задачи", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Шаблоны", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(
                    value = templateId,
                    items = listOf("" to "Нет") + visibleTpl.map {
                        KassaApi.pick(it, "id") to KassaApi.pick(it, "label", "title", "name").ifBlank { "Шаблон" }
                    }.filter { it.first.isNotBlank() }.take(8),
                    onChange = { id ->
                        templateId = id
                        val t = templates.find { KassaApi.pick(it, "id") == id } ?: return@SiteSegmented
                        val tTitle = KassaApi.pick(t, "title", "label")
                        if (tTitle.isNotBlank()) title = tTitle
                        val lines = KassaApi.templateChecklistLines(t)
                        if (lines.isNotEmpty()) checklist = lines.joinToString("\n")
                    },
                )
                InlineActionChip(if (ownOpen) "Скрыть свой шаблон" else "+ Свой шаблон") { ownOpen = !ownOpen }
                if (ownOpen) {
                    OutlinedTextField(ownLabel, { ownLabel = it }, label = { Text("Короткое название кнопки") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = true)
                    OutlinedTextField(ownTitle, { ownTitle = it }, label = { Text("Название задачи") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors(), singleLine = true)
                    OutlinedTextField(ownCheck, { ownCheck = it }, label = { Text("Чек-лист (по строке)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), colors = fieldColors())
                    InlineActionChip("Сохранить шаблон") {
                        val tkn = token ?: return@InlineActionChip
                        if (ownLabel.trim().isBlank() || ownTitle.trim().isBlank()) {
                            err = "Укажите короткое название кнопки и название задачи"
                            return@InlineActionChip
                        }
                        val id = "custom-${System.currentTimeMillis().toString(36)}"
                        val checks = org.json.JSONArray()
                        ownCheck.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { checks.put(it) }
                        val types = org.json.JSONArray().put(contextType).put("NONE")
                        val obj = JSONObject()
                            .put("id", id)
                            .put("label", ownLabel.trim())
                            .put("title", ownTitle.trim())
                            .put("checklist", checks)
                            .put("contextTypes", types)
                            .put("custom", true)
                        val next = templates.filter { KassaApi.pick(it, "id") != id } + obj
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { api.saveTaskTemplates(tkn, next) }
                                onTemplates(next)
                                templateId = id
                                title = ownTitle.trim()
                                checklist = ownCheck
                                ownOpen = false
                            } catch (e: Exception) {
                                err = e.message ?: "Не удалось сохранить шаблон"
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; if (it.isNotBlank()) err = null },
                    placeholder = { Text("Поставить задачу…") },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                )
                Text("Контекст", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(
                    value = contextType,
                    items = listOf("NONE" to "Без привязки", "CONTACT" to "Контакт", "DEAL" to "Сделка", "ENTITY" to "Заведение"),
                    onChange = { contextType = it; contextId = ""; contextTitle = ""; estQ = ""; templateId = "" },
                )
                if (contextType == "ENTITY") {
                    OutlinedTextField(
                        value = estQ,
                        onValueChange = { estQ = it; contextId = ""; contextTitle = "" },
                        label = { Text("Заведение (поиск по названию или ID)") },
                        placeholder = { Text("Начните вводить название или ID…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                    if (estBusy) Text("Поиск заведений…", color = AtColors.muted, fontSize = 12.sp)
                    estHits.take(8).forEach { e ->
                        val name = KassaApi.pick(e.raw, "title", "name").ifBlank { e.title }
                        val delivio = KassaApi.pick(e.raw, "delivioId", "id")
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AtColors.glass)
                                .clickable {
                                    contextId = e.id
                                    contextTitle = name
                                    estQ = name
                                    estHits = emptyList()
                                }.padding(10.dp),
                        ) {
                            Text(name, color = AtColors.text, fontWeight = FontWeight.SemiBold)
                            Text("ID: ${delivio.ifBlank { e.id }}", color = AtColors.muted, fontSize = 12.sp)
                        }
                    }
                } else if (contextType != "NONE") {
                    OutlinedTextField(contextId, { contextId = it }, label = { Text("ID контекста") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                    OutlinedTextField(contextTitle, { contextTitle = it }, label = { Text("Название контекста") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                }
                OutlinedTextField(peopleQ, { peopleQ = it }, placeholder = { Text("Поиск сотрудника") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                Text("Исполнители", color = AtColors.muted, fontSize = 12.sp)
                val people = users.filter { it.id.isNotBlank() }.filter {
                    val n = KassaApi.pick(it.raw, "fullName", "name", "username").ifBlank { it.title }
                    peopleQ.isBlank() || n.lowercase().contains(peopleQ.trim().lowercase())
                }
                people.forEach { u ->
                    val name = KassaApi.pick(u.raw, "fullName", "name").ifBlank { u.title }
                    val role = KassaApi.pick(u.raw, "role")
                    val on = assignees.contains(u.id)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (on) AtColors.accentSoft else Color.Transparent)
                            .clickable {
                                assignees = if (on) assignees - u.id else assignees + u.id
                                if (!on) watchers = watchers - u.id
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (on) "☑" else "☐", color = if (on) AtColors.accent else AtColors.muted, modifier = Modifier.width(22.dp))
                        Text(if (role.isBlank()) name else "$name ($role)", color = AtColors.text, fontSize = 14.sp)
                    }
                }
                if (assignees.isEmpty()) Text("Выберите исполнителей", color = AtColors.danger, fontSize = 12.sp)
                if (assignees.size > 1) {
                    Text("Выбрано ${assignees.size} исполнителей — будет создано ${assignees.size} отдельных задач (у каждой свой статус и проверка).", color = AtColors.muted, fontSize = 12.sp)
                }
                Text("Наблюдатели (необяз.)", color = AtColors.muted, fontSize = 12.sp)
                people.filter { it.id !in assignees }.take(40).forEach { u ->
                    val name = KassaApi.pick(u.raw, "fullName", "name").ifBlank { u.title }
                    val on = watchers.contains(u.id)
                    Row(
                        Modifier.fillMaxWidth().clickable { watchers = if (on) watchers - u.id else watchers + u.id }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (on) "☑" else "☐", modifier = Modifier.width(22.dp), color = AtColors.muted)
                        Text(name, color = AtColors.text, fontSize = 13.sp)
                    }
                }
                Text("Приоритет", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(
                    value = if (autoSla) "AUTO" else priority,
                    items = listOf("LOW" to "Низкий", "MEDIUM" to "Средний", "HIGH" to "Высокий", "AUTO" to "Авто по SLA"),
                    onChange = {
                        if (it == "AUTO") autoSla = true else { autoSla = false; priority = it }
                    },
                )
                Text("Дедлайн", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(
                    value = duePreset,
                    items = listOf("none" to "Без дедлайна", "today" to "Сегодня 18:00", "tomorrow" to "Завтра 18:00"),
                    onChange = { duePreset = it },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    colors = fieldColors(),
                    maxLines = 6,
                )
                OutlinedTextField(
                    value = checklist,
                    onValueChange = { checklist = it },
                    label = { Text("Чек-лист (каждый пункт с новой строки)") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    colors = fieldColors(),
                )
                err?.let { Text(it, color = AtColors.danger, fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.glass).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("Отмена", color = AtColors.muted, fontWeight = FontWeight.SemiBold) }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent).clickable(enabled = !busySaving) {
                        if (title.trim().isBlank()) { err = "Укажите название задачи"; return@clickable }
                        if (assignees.isEmpty()) { err = "Выберите хотя бы одного исполнителя"; return@clickable }
                        if (contextType == "ENTITY" && contextId.isBlank()) { err = "Выберите заведение из поиска"; return@clickable }
                        val deadline = KassaApi.taskDeadlineIso(duePreset)
                        val prio = if (autoSla) KassaApi.autoPriorityByDeadline(deadline) else priority
                        val checks = org.json.JSONArray()
                        checklist.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { checks.put(it) }
                        val body = JSONObject()
                            .put("title", title.trim())
                            .put("priority", prio)
                            .put("assigneeIds", KassaApi.idsToJsonArray(assignees))
                            .put("watcherIds", KassaApi.idsToJsonArray(watchers.filter { it !in assignees }))
                            .put("contextType", contextType)
                            .put("checklistTitles", checks)
                        if (description.trim().isNotBlank()) body.put("description", description.trim())
                        if (deadline != null) body.put("deadlineAt", deadline)
                        if (contextType != "NONE") {
                            if (contextId.isNotBlank()) body.put("contextId", contextId.trim())
                            if (contextTitle.isNotBlank()) body.put("contextTitle", contextTitle.trim())
                        }
                        onSubmit(body)
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(if (busySaving) "Создание…" else "Создать задачу", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun TaskDetailPane(
    title: String,
    taskId: String,
    preload: JSONObject?,
    api: KassaApi,
    token: String?,
    me: AppUser?,
    onBack: () -> Unit,
    onUpdated: (JSONObject) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var obj by remember { mutableStateOf(preload) }
    var users by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var siblings by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var checkDraft by remember { mutableStateOf("") }
    var commentDraft by remember { mutableStateOf("") }
    var reportOpen by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }
    var pendingStatus by remember { mutableStateOf("") }
    var deleteOpen by remember { mutableStateOf(false) }
    var editCreator by remember { mutableStateOf("") }
    var editAssignees by remember { mutableStateOf(setOf<String>()) }
    var editWatchers by remember { mutableStateOf(setOf<String>()) }
    var editDue by remember { mutableStateOf("keep") }
    var savingMeta by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(taskId, tick) {
        if (token.isNullOrBlank() || taskId.isBlank()) return@LaunchedEffect
        if (obj == null) obj = preload
        refreshing = obj != null
        try {
            val next = withContext(Dispatchers.IO) {
                val o = api.getObject(KassaApi.taskDetailPath(taskId), token)
                val u = runCatching { api.getRows("/workspace/users", token) }.getOrElse {
                    runCatching { api.getRows("/users", token) }.getOrDefault(emptyList())
                }
                val all = runCatching { api.getRows(KassaApi.taskListPath(true), token) }.getOrDefault(emptyList())
                runCatching {
                    val nObj = api.getObject("/workspace/notifications", token)
                    val arr = nObj.optJSONArray("items") ?: nObj.optJSONArray("data")
                    if (arr != null) {
                        KassaApi.eachObj(arr).filter {
                            !it.optBoolean("isRead", false) &&
                                it.optJSONObject("payload")?.let { p -> KassaApi.pick(p, "taskId") } == taskId
                        }.forEach { n ->
                            val id = KassaApi.pick(n, "id")
                            if (id.isNotBlank()) runCatching { api.markNotificationRead(token, id) }
                        }
                    }
                }
                Triple(o, u, all)
            }
            obj = next.first
            users = next.second
            val gid = KassaApi.pick(next.first, "siblingGroupId")
            siblings = if (gid.isBlank()) emptyList() else next.third.filter { KassaApi.pick(it.raw, "siblingGroupId") == gid }
            editCreator = KassaApi.taskCreatorId(next.first)
            editAssignees = KassaApi.taskPeopleIds(next.first, "assignees").toSet()
            editWatchers = KassaApi.taskPeopleIds(next.first, "watchers").toSet()
            editDue = "keep"
        } catch (e: Exception) {
            if (obj == null) err = e.message
        } finally {
            refreshing = false
        }
    }

    fun reload() { tick++ }

    fun applyStatus(status: String, report: String = "") {
        val t = token ?: return
        val o = obj ?: return
        val from = KassaApi.pick(o, "status").uppercase()
        if (status == "IN_REVIEW" && from == "IN_PROGRESS" && report.isBlank()) {
            pendingStatus = status
            reportText = ""
            reportOpen = true
            return
        }
        if (status == "IN_PROGRESS" && from == "IN_REVIEW" && !reportOpen) {
            pendingStatus = status
            reportText = ""
            reportOpen = true
            return
        }
        if (status == "DONE" && from == "IN_REVIEW" && !KassaApi.canCompleteTaskReview(o, me)) {
            err = "Завершить задачу на проверке может только постановщик."
            return
        }
        busy = true
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) { api.setTaskStatus(t, taskId, status, report) }
                obj = KassaApi.aiPayload(next).takeIf { it.has("id") || it.has("status") } ?: JSONObject(o.toString()).put("status", status)
                onUpdated(obj!!)
                msg = "Статус: ${KassaApi.taskStatusLabel(status)}"
                reportOpen = false
                reload()
                Haptics.tap(ctx)
            } catch (e: Exception) {
                err = e.message
            } finally {
                busy = false
            }
        }
    }

    val o = obj
    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine(title.ifBlank { "Задача" }, onBack, onRefresh = { tick = tick + 1 })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick = tick + 1 },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                err != null && o == null -> ErrorBlock(err!!, onRetry = { tick++ })
                o == null -> LoadingCard()
                else -> {
                    val row = JsonRow(taskId, KassaApi.pick(o, "title", "name"), "", o)
                    val stage = KassaApi.taskColumn(row)
                    val check = KassaApi.checklistItems(o)
                    val history = KassaApi.statusHistory(o)
                    val comments = o.optJSONArray("comments")
                    val rawStatus = KassaApi.pick(o, "status").uppercase()
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (err != null) item { ActionBanner(err!!, error = true) }
                        if (msg != null) item { ActionBanner(msg!!, error = false) }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StatusChip(KassaApi.taskStatusLabel(KassaApi.pick(o, "status")))
                                    if (KassaApi.taskIsOverdue(row)) StatusChip("Просрочено")
                                }
                                Text(KassaApi.pick(o, "title", "name").ifBlank { title }, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                val desc = KassaApi.pick(o, "description", "text")
                                if (desc.isNotBlank()) Text(desc, color = AtColors.text, fontSize = 14.sp)
                                val ctxLine = KassaApi.taskContextLabel(o)
                                if (ctxLine.isNotBlank()) Text(ctxLine, color = AtColors.accent, fontSize = 13.sp)
                                Text("Постановщик: ${KassaApi.taskCreatorName(o).ifBlank { "—" }}", color = AtColors.muted, fontSize = 13.sp)
                                Text("Исполнители: ${KassaApi.taskAssignees(row).ifBlank { "Без исполнителя" }}", color = AtColors.muted, fontSize = 13.sp)
                                val watch = KassaApi.taskWatchers(row)
                                if (watch.isNotBlank()) Text("Наблюдатели: $watch", color = AtColors.muted, fontSize = 13.sp)
                                val due = KassaApi.prettyTime(KassaApi.taskDueRaw(row))
                                Text(
                                    if (due.isNotBlank()) "Срок: $due · ${KassaApi.remainingLabel(KassaApi.taskDueRaw(row))}" else "Срок: —",
                                    color = AtColors.text,
                                    fontSize = 13.sp,
                                )
                                Text("Приоритет: ${KassaApi.taskPriorityLabel(KassaApi.pick(o, "priority"))}", color = AtColors.muted, fontSize = 13.sp)
                            }
                        }
                        if (siblings.size >= 2) {
                            val doneN = siblings.count { KassaApi.isTaskDone(it) }
                            val reviewN = siblings.count { KassaApi.taskColumn(it) == "review" }
                            item {
                                Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "Связанные задачи группы: принято $doneN/${siblings.size}" + if (reviewN > 0) " · на проверке $reviewN" else "",
                                        color = AtColors.text,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                    )
                                    Text("Каждый исполнитель ведёт свою копию; общая колонка «На проверке» не блокирует остальных.", color = AtColors.muted, fontSize = 12.sp)
                                }
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Действия по задаче", color = AtColors.text, fontWeight = FontWeight.Bold)
                                val actions = when {
                                    rawStatus == "ARCHIVED" -> listOf("DONE" to "Из архива → Выполнена")
                                    stage == "new" -> listOf("IN_PROGRESS" to "Далее: В работе")
                                    stage == "progress" -> listOf("IN_REVIEW" to "На проверку")
                                    stage == "review" -> listOf("DONE" to "Принять", "IN_PROGRESS" to "Вернуть в работу")
                                    stage == "overdue" -> listOf("IN_PROGRESS" to "В работу")
                                    stage == "done" -> listOf("ARCHIVED" to "Архив", "IN_PROGRESS" to "В работе")
                                    else -> emptyList()
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    actions.forEach { (st, label) ->
                                        InlineActionChip(label, filled = st == "DONE") { if (!busy) applyStatus(st) }
                                    }
                                }
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Параметры задачи — меняйте прямо здесь", color = AtColors.text, fontWeight = FontWeight.Bold)
                                Text("Постановщик", color = AtColors.muted, fontSize = 12.sp)
                                SiteSegmented(
                                    value = editCreator,
                                    items = users.take(16).map {
                                        it.id to KassaApi.pick(it.raw, "fullName", "name").ifBlank { it.title }
                                    }.filter { it.first.isNotBlank() },
                                    onChange = { editCreator = it },
                                )
                                Text("Исполнители", color = AtColors.muted, fontSize = 12.sp)
                                users.take(30).forEach { u ->
                                    val name = KassaApi.pick(u.raw, "fullName", "name").ifBlank { u.title }
                                    val on = u.id in editAssignees
                                    Row(Modifier.fillMaxWidth().clickable {
                                        editAssignees = if (on) editAssignees - u.id else editAssignees + u.id
                                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (on) "☑" else "☐", modifier = Modifier.width(22.dp), color = if (on) AtColors.accent else AtColors.muted)
                                        Text(name, color = AtColors.text, fontSize = 13.sp)
                                    }
                                }
                                Text("Наблюдатели", color = AtColors.muted, fontSize = 12.sp)
                                users.filter { it.id !in editAssignees }.take(20).forEach { u ->
                                    val name = KassaApi.pick(u.raw, "fullName", "name").ifBlank { u.title }
                                    val on = u.id in editWatchers
                                    Row(Modifier.fillMaxWidth().clickable {
                                        editWatchers = if (on) editWatchers - u.id else editWatchers + u.id
                                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (on) "☑" else "☐", modifier = Modifier.width(22.dp), color = AtColors.muted)
                                        Text(name, color = AtColors.text, fontSize = 13.sp)
                                    }
                                }
                                Text("Дедлайн", color = AtColors.muted, fontSize = 12.sp)
                                SiteSegmented(
                                    value = editDue,
                                    items = listOf("keep" to "Не менять", "today" to "Сегодня 18:00", "tomorrow" to "Завтра 18:00", "none" to "Без дедлайна"),
                                    onChange = { editDue = it },
                                )
                                Box(
                                    Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent)
                                        .clickable(enabled = !savingMeta) {
                                            val t = token ?: return@clickable
                                            if (editAssignees.isEmpty()) { err = "Нужен хотя бы один исполнитель"; return@clickable }
                                            if (editCreator.isBlank()) { err = "Выберите постановщика"; return@clickable }
                                            savingMeta = true
                                            scope.launch {
                                                try {
                                                    val body = JSONObject()
                                                        .put("assigneeIds", KassaApi.idsToJsonArray(editAssignees))
                                                        .put("watcherIds", KassaApi.idsToJsonArray(editWatchers.filter { it !in editAssignees }))
                                                        .put("createdById", editCreator)
                                                    when (editDue) {
                                                        "none" -> body.put("deadlineAt", "")
                                                        "today", "tomorrow" -> body.put("deadlineAt", KassaApi.taskDeadlineIso(editDue))
                                                    }
                                                    withContext(Dispatchers.IO) { api.patchTask(t, taskId, body) }
                                                    msg = "Изменения сохранены"
                                                    reload()
                                                } catch (e: Exception) {
                                                    err = e.message ?: "Не удалось сохранить"
                                                } finally {
                                                    savingMeta = false
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text(if (savingMeta) "Сохранение…" else "Сохранить изменения", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val doneN = check.count { it.third }
                                Text("Чек-лист ${if (check.isNotEmpty()) "$doneN/${check.size}" else ""}", color = AtColors.text, fontWeight = FontWeight.Bold)
                                if (check.isEmpty()) Text("Нет пунктов", color = AtColors.muted, fontSize = 13.sp)
                                check.forEach { (id, label, done) ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = id.isNotBlank() && !busy) {
                                            val t = token ?: return@clickable
                                            busy = true
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { api.setTaskChecklistDone(t, taskId, id, !done) }
                                                    reload()
                                                } catch (e: Exception) { err = e.message } finally { busy = false }
                                            }
                                        }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(if (done) "✅" else "⬜", modifier = Modifier.width(28.dp))
                                        Text(label, color = AtColors.text, fontSize = 14.sp)
                                    }
                                }
                                OutlinedTextField(
                                    value = checkDraft,
                                    onValueChange = { checkDraft = it },
                                    placeholder = { Text("Новый пункт чек-листа") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = fieldColors(),
                                )
                                InlineActionChip("Добавить") {
                                    val t = token ?: return@InlineActionChip
                                    if (checkDraft.trim().isBlank()) return@InlineActionChip
                                    busy = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { api.addTaskChecklistItem(t, taskId, checkDraft) }
                                            checkDraft = ""
                                            reload()
                                        } catch (e: Exception) { err = e.message } finally { busy = false }
                                    }
                                }
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Комментарии", color = AtColors.text, fontWeight = FontWeight.Bold)
                                val n = comments?.length() ?: 0
                                if (n == 0) Text("Нет комментариев", color = AtColors.muted, fontSize = 13.sp)
                                else {
                                    for (i in 0 until n) {
                                        val c = comments?.optJSONObject(i) ?: continue
                                        val text = KassaApi.pick(c, "text", "message", "body")
                                        if (text.startsWith("[STATUS_CHANGE]")) continue
                                        val author = c.optJSONObject("author")?.let { KassaApi.pick(it, "fullName", "name", "username") }.orEmpty()
                                            .ifBlank { KassaApi.pick(c, "authorName") }
                                        Text(author.ifBlank { "—" }, color = AtColors.muted, fontSize = 12.sp)
                                        Text(text, color = AtColors.text, fontSize = 14.sp)
                                        Text(KassaApi.prettyTime(KassaApi.pick(c, "createdAt")), color = AtColors.muted, fontSize = 11.sp)
                                    }
                                }
                                OutlinedTextField(
                                    value = commentDraft,
                                    onValueChange = { commentDraft = it },
                                    placeholder = { Text("Комментарий… @логин чтобы упомянуть") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = fieldColors(),
                                    maxLines = 4,
                                )
                                InlineActionChip("Отправить") {
                                    val t = token ?: return@InlineActionChip
                                    if (commentDraft.trim().isBlank()) return@InlineActionChip
                                    busy = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { api.addTaskComment(t, taskId, commentDraft) }
                                            commentDraft = ""
                                            reload()
                                        } catch (e: Exception) { err = e.message } finally { busy = false }
                                    }
                                }
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Кто менял статус", color = AtColors.text, fontWeight = FontWeight.Bold)
                                if (history.isEmpty()) Text("Пока нет записей о смене статуса.", color = AtColors.muted, fontSize = 13.sp)
                                else history.forEach { Text(it, color = AtColors.text, fontSize = 13.sp) }
                            }
                        }
                        item {
                            Box(
                                Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFDC2626)).clickable { deleteOpen = true },
                                contentAlignment = Alignment.Center,
                            ) { Text("Удалить", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
    if (reportOpen) {
        AlertDialog(
            onDismissRequest = { reportOpen = false },
            containerColor = AtColors.panel,
            title = { Text(if (pendingStatus == "IN_REVIEW") "Отчёт перед проверкой" else "Вернуть в работу", color = AtColors.text, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (pendingStatus == "IN_REVIEW") "Кратко опишите, что сделано — отчёт увидит постановщик."
                        else "Можно указать, что нужно доработать (необязательно).",
                        color = AtColors.muted,
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = reportText,
                        onValueChange = { reportText = it },
                        placeholder = { Text(if (pendingStatus == "IN_REVIEW") "Что сделано, что осталось, риски…" else "Что исправить…") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pendingStatus == "IN_REVIEW" && reportText.trim().isBlank()) {
                        err = "Нужен отчёт, чтобы отправить задачу на проверку"
                        return@TextButton
                    }
                    applyStatus(pendingStatus, reportText)
                }) { Text("Отправить", color = AtColors.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { reportOpen = false }) { Text("Отмена", color = AtColors.muted) } },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = AtColors.panel,
            title = { Text("Удалить задачу?", color = AtColors.text, fontWeight = FontWeight.Bold) },
            text = { Text("Это действие нельзя отменить.", color = AtColors.muted) },
            confirmButton = {
                TextButton(onClick = {
                    val t = token ?: return@TextButton
                    deleteOpen = false
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { api.deletePath(KassaApi.taskDetailPath(taskId), t) }
                            onBack()
                        } catch (e: Exception) { err = e.message } finally { busy = false }
                    }
                }) { Text("Удалить", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Отмена", color = AtColors.muted) } },
        )
    }
}


// --- recovered from chats-workspace.kt ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatsWorkspacePane(
    api: KassaApi,
    token: String?,
    me: AppUser?,
    cache: CacheStore?,
    onBack: () -> Unit,
    onOpenChat: (String, String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var chats by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var users by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var creatingGroup by remember { mutableStateOf(false) }
    var groupTitle by remember { mutableStateOf("") }
    var groupIds by remember { mutableStateOf(setOf<String>()) }
    var groupBusy by remember { mutableStateOf(false) }
    var openingUser by remember { mutableStateOf<String?>(null) }
    val pullState = rememberPullToRefreshState()
    val meId = me?.id.orEmpty()

    LaunchedEffect(token, tick) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        err = null
        if (loaded) refreshing = true
        try {
            val (nextChats, nextUsers) = withContext(Dispatchers.IO) {
                val c = runCatching { api.getRows("/workspace/chats", token) }.getOrDefault(emptyList())
                val u = runCatching { api.getRows("/workspace/users", token) }.getOrElse {
                    runCatching { api.getRows("/users", token) }.getOrDefault(emptyList())
                }
                c to u
            }
            chats = nextChats
            users = nextUsers
            cache?.putRows("/workspace/chats", nextChats)
            loaded = true
        } catch (e: Exception) {
            err = e.message
            val cached = cache?.getRows("/workspace/chats")
            if (cached != null) {
                chats = cached
                loaded = true
            }
        } finally {
            refreshing = false
        }
    }

    fun otherId(chat: JsonRow): String {
        val other = KassaApi.otherParticipant(chat.raw, meId) ?: return ""
        return KassaApi.pick(other, "id", "userId")
    }

    val directByUser = remember(chats, meId) {
        chats.filter {
            KassaApi.pick(it.raw, "type", "kind").uppercase().let { t -> t == "DIRECT" || t == "PERSONAL" || t == "DM" }
        }.associateBy { otherId(it) }.filterKeys { it.isNotBlank() }
    }
    val groups = remember(chats, meId) {
        chats.filter {
            val t = KassaApi.pick(it.raw, "type", "kind").uppercase()
            t != "DIRECT" && t != "PERSONAL" && t != "DM"
        }.sortedByDescending { KassaApi.pick(it.raw, "updatedAt", "lastMessageAt", "createdAt") }
    }
    val query = q.trim().lowercase()
    val people = remember(users, meId, query, directByUser) {
        users.filter { it.id != meId }.sortedWith(
            compareByDescending<JsonRow> { u ->
                KassaApi.pick(directByUser[u.id]?.raw ?: JSONObject(), "updatedAt", "lastMessageAt")
            }.thenBy { KassaApi.pick(it.raw, "fullName", "name").ifBlank { it.title } },
        ).filter { u ->
            if (query.isBlank()) true
            else {
                val name = KassaApi.pick(u.raw, "fullName", "name").ifBlank { u.title }
                val user = KassaApi.pick(u.raw, "username", "login")
                val role = KassaApi.workspaceRoleLabel(KassaApi.pick(u.raw, "role"))
                name.contains(query, true) || user.contains(query, true) || role.contains(query, true) ||
                    KassaApi.pick(u.raw, "role").contains(query, true)
            }
        }
    }
    val visibleGroups = if (query.isBlank()) groups else groups.filter {
        KassaApi.workspaceChatTitle(it.raw, meId).contains(query, true)
    }

    fun openExisting(row: JsonRow) {
        val title = KassaApi.workspaceChatTitle(row.raw, meId).ifBlank { row.title }
        onOpenChat(title, "/workspace/chats/${row.id}/messages")
    }

    fun openUser(user: JsonRow) {
        val existing = directByUser[user.id]
        if (existing != null) {
            openExisting(existing)
            return
        }
        val t = token ?: return
        openingUser = user.id
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) { api.createDirectChat(t, user.id) }
                val name = KassaApi.pick(user.raw, "fullName", "name").ifBlank { user.title }
                onOpenChat(name, "/workspace/chats/$id/messages")
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Не удалось открыть диалог"
                Haptics.warn(ctx)
            } finally {
                openingUser = null
            }
        }
    }

    fun createGroup() {
        val title = groupTitle.trim()
        if (title.isBlank()) {
            err = "Укажите название группы"
            return
        }
        if (groupIds.isEmpty()) {
            err = "Выберите хотя бы одного участника"
            return
        }
        val t = token ?: return
        groupBusy = true
        err = null
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) { api.createGroupChat(t, title, groupIds.toList()) }
                creatingGroup = false
                groupTitle = ""
                groupIds = emptySet()
                onOpenChat(title, "/workspace/chats/$id/messages")
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Не удалось создать группу"
            } finally {
                groupBusy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Чаты", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                err != null && !loaded -> ErrorBlock(err!!, onRetry = { tick++ })
                !loaded -> LoadingCard()
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SiteSectionHead("Чаты", "Workspace: переписка, файлы, реакции")
                    }
                    item {
                        OutlinedTextField(
                            value = q,
                            onValueChange = { q = it },
                            placeholder = { Text("Поиск по сотрудникам...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AtColors.panel)
                                .border(1.dp, AtColors.stroke, RoundedCornerShape(10.dp))
                                .clickable {
                                    creatingGroup = !creatingGroup
                                    err = null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (creatingGroup) "Скрыть" else "Новая группа", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (creatingGroup) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Название группы", color = AtColors.muted, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = groupTitle,
                                    onValueChange = { groupTitle = it },
                                    placeholder = { Text("Например: Маркетинг") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = fieldColors(),
                                    enabled = !groupBusy,
                                )
                                Text("Участники", color = AtColors.muted, fontSize = 12.sp)
                                users.filter { it.id != meId }.forEach { u ->
                                    val name = KassaApi.pick(u.raw, "fullName", "name").ifBlank { u.title }
                                    val on = groupIds.contains(u.id)
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (on) AtColors.accentSoft else Color.Transparent)
                                            .clickable {
                                                groupIds = if (on) groupIds - u.id else groupIds + u.id
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(if (on) "☑" else "☐", color = if (on) AtColors.accent else AtColors.muted, modifier = Modifier.width(22.dp))
                                        Text(name, color = AtColors.text, fontSize = 14.sp)
                                    }
                                }
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AtColors.accent)
                                        .clickable(enabled = !groupBusy) { createGroup() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (groupBusy) "Создание…" else "Создать группу", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (err != null) {
                        item { ActionBanner(err!!, error = true) }
                    }
                    if (visibleGroups.isEmpty() && people.isEmpty()) {
                        item {
                            EmptyStateCard(
                                if (query.isBlank()) "Нет сотрудников" else "Никого не нашли",
                                if (query.isBlank()) "Выберите сотрудника" else "Смените запрос поиска по сотрудникам.",
                            )
                        }
                    }
                    items(visibleGroups, key = { "g-${it.id}" }) { row ->
                        WorkspaceThreadRow(
                            title = KassaApi.workspaceChatTitle(row.raw, meId),
                            preview = KassaApi.lastMessagePreview(row.raw),
                            online = false,
                            opening = false,
                            unread = KassaApi.unreadCount(row),
                            onClick = { openExisting(row) },
                        )
                    }
                    items(people, key = { "u-${it.id}" }) { user ->
                        val chat = directByUser[user.id]
                        val name = KassaApi.pick(user.raw, "fullName", "name").ifBlank { user.title }
                        val preview = when {
                            openingUser == user.id -> "Открываем…"
                            chat != null -> KassaApi.lastMessagePreview(chat.raw)
                            else -> KassaApi.workspaceRoleLabel(KassaApi.pick(user.raw, "role")).ifBlank { user.subtitle }
                        }
                        val presence = KassaApi.pick(user.raw, "lastPresenceAt", "lastSeenAt", "onlineAt")
                        WorkspaceThreadRow(
                            title = name,
                            preview = preview.ifBlank { "Нет сообщений" },
                            online = KassaApi.isPresenceOnline(presence) || KassaApi.pick(user.raw, "online", "isOnline") in setOf("true", "1"),
                            opening = openingUser == user.id,
                            unread = chat?.let { KassaApi.unreadCount(it) } ?: 0,
                            onClick = { if (openingUser == null) openUser(user) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceThreadRow(
    title: String,
    preview: String,
    online: Boolean,
    opening: Boolean,
    unread: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .atCard(14.dp)
            .clickable(enabled = !opening, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(AtColors.accentSoft), contentAlignment = Alignment.Center) {
            Text(KassaApi.chatInitials(title), color = AtColors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = AtColors.text,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (online) {
                    Text(" ●", color = AtColors.success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                preview,
                color = AtColors.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (unread > 0) {
            Box(
                Modifier.padding(start = 8.dp).clip(CircleShape).background(AtColors.accent).size(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unread > 99) "99+" else unread.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// --- recovered from ai-analytics-pane.kt ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiAnalyticsPane(
    api: KassaApi,
    token: String?,
    user: AppUser?,
    initialTab: String = "reco",
    onBack: () -> Unit,
    onOpenPortraits: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("atcrm", android.content.Context.MODE_PRIVATE) }
    val stored = remember {
        val from = prefs.getString("ai_date_from", "").orEmpty()
        val to = prefs.getString("ai_date_to", "").orEmpty()
        val preset = prefs.getString("ai_preset", "30d").orEmpty()
        if (from.matches(Regex("""\d{4}-\d{2}-\d{2}""")) && to.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            Triple(preset.ifBlank { "custom" }, from, to)
        } else {
            val range = KassaApi.aiPresetRange("30d")
            Triple("30d", range.first, range.second)
        }
    }
    var preset by remember { mutableStateOf(stored.first) }
    var dateFrom by remember { mutableStateOf(stored.second) }
    var dateTo by remember { mutableStateOf(stored.third) }
    var cityKey by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(if (initialTab == "ask") "ask" else "reco") }
    var minOrderDate by remember { mutableStateOf("") }
    var cities by remember { mutableStateOf(listOf(DashCity("", "Все города"))) }
    var payload by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var lastAdvisor by remember { mutableStateOf<JSONObject?>(null) }
    var snapshotOpen by remember { mutableStateOf(false) }
    var dormantOpen by remember { mutableStateOf(false) }
    var ragLabel by remember { mutableStateOf("") }
    var ragText by remember { mutableStateOf("") }
    var ragBusy by remember { mutableStateOf(false) }
    var ragMsg by remember { mutableStateOf<String?>(null) }
    var ragStats by remember { mutableStateOf<JSONObject?>(null) }
    var fbStatus by remember { mutableStateOf<JSONObject?>(null) }
    var fbBusy by remember { mutableStateOf(false) }
    var fbMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    val isAdmin = user?.role.equals("ADMIN", true)
    val canPortraits = isAdmin || user?.permissions.orEmpty().any {
        it.contains("analytics", true) || it.contains("reports", true) || it.contains("ai", true)
    } || user?.permissions.isNullOrEmpty()

    fun applyPreset(next: String) {
        preset = next
        if (next == "custom") return
        val range = KassaApi.aiPresetRange(next, minOrderDate)
        dateFrom = range.first
        dateTo = range.second
    }

    LaunchedEffect(preset, dateFrom, dateTo) {
        prefs.edit()
            .putString("ai_preset", preset)
            .putString("ai_date_from", dateFrom)
            .putString("ai_date_to", dateTo)
            .apply()
    }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { api.getAiObject("/ai/data-bounds", token) }.getOrNull()
        }?.let { bounds ->
            minOrderDate = KassaApi.pick(bounds, "minOrderDate", "minDate", "from").take(10)
        }
        withContext(Dispatchers.IO) {
            runCatching { api.getObject("/sales/establishments/cities", token) }.getOrNull()
        }?.let { o ->
            val parsed = KassaApi.parseCities(o)
            if (parsed.isNotEmpty()) cities = parsed
        }
        if (isAdmin) {
            ragStats = withContext(Dispatchers.IO) {
                runCatching { api.getAiObject("/ai/marketing-rag/stats", token) }.getOrNull()
            }
            fbStatus = withContext(Dispatchers.IO) {
                runCatching { api.getAiObject("/ai/firebase-push-reports/status", token) }.getOrNull()
            }
        }
    }

    LaunchedEffect(token, tick, dateFrom, dateTo, cityKey) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        if (!dateFrom.matches(Regex("""\d{4}-\d{2}-\d{2}""")) || !dateTo.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return@LaunchedEffect
        if (dateFrom > dateTo) {
            err = "Дата «с» не может быть позже даты «по»."
            return@LaunchedEffect
        }
        err = null
        if (loaded) refreshing = true
        try {
            val raw = withContext(Dispatchers.IO) {
                api.getAiObject(KassaApi.aiRecommendationsQuery(dateFrom, dateTo, cityKey), token)
            }
            payload = KassaApi.aiPayload(raw)
            loaded = true
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            err = if (msg.contains("403") || msg.contains("доступ", true) || msg.contains("access", true)) {
                "$msg. Нужно право «AI аналитика» или allowlist на сервере."
            } else msg.ifBlank { "Ошибка загрузки" }
        } finally {
            refreshing = false
        }
    }

    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val t = token ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        fbBusy = true
        fbMsg = null
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val tmp = java.io.File(ctx.cacheDir, "firebase-push.csv")
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("Не удалось прочитать CSV")
                    tmp
                }
                val res = withContext(Dispatchers.IO) { api.importFirebasePushCsv(t, file) }
                fbMsg = "Импорт: ${KassaApi.pick(res, "imported").ifBlank { "—" }}, пропущено: ${KassaApi.pick(res, "skipped").ifBlank { "—" }}"
                fbStatus = withContext(Dispatchers.IO) {
                    runCatching { api.getAiObject("/ai/firebase-push-reports/status", t) }.getOrNull()
                }
            } catch (e: Exception) {
                err = e.message ?: "Ошибка импорта CSV"
            } finally {
                fbBusy = false
            }
        }
    }

    fun askNow() {
        val text = question.trim()
        val t = token
        if (text.isBlank() || t.isNullOrBlank() || asking) return
        asking = true
        err = null
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.askMarketingAdvisor(t, text, history, dateFrom, dateTo, cityKey)
                }
                val body = KassaApi.aiPayload(res)
                lastAdvisor = body
                val reply = KassaApi.pick(body, "replyMarkdown", "reply", "text", "markdown")
                history = history + ("user" to text) + ("assistant" to reply.ifBlank { "—" })
                question = ""
                tab = "ask"
            } catch (e: Exception) {
                err = e.message ?: "Ошибка запроса к маркетологу"
            } finally {
                asking = false
            }
        }
    }

    fun ingestRag() {
        val t = token ?: return
        if (ragText.trim().length < 10 || ragBusy) return
        ragBusy = true
        ragMsg = null
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) { api.ingestMarketingRag(t, ragText.trim(), ragLabel.trim()) }
                val n = KassaApi.pick(res, "embeddedChunks", "chunkCount").ifBlank {
                    KassaApi.jsonNum(res, "embeddedChunks", "chunkCount").toInt().toString()
                }
                ragMsg = "Загружено: $n чанков"
                ragText = ""
                ragStats = withContext(Dispatchers.IO) {
                    runCatching { api.getAiObject("/ai/marketing-rag/stats", t) }.getOrNull()
                }
            } catch (e: Exception) {
                err = e.message ?: "Ошибка RAG"
            } finally {
                ragBusy = false
            }
        }
    }

    val snap = payload?.optJSONObject("snapshot")
    val funnel = snap?.optJSONObject("acquisitionFunnel")
    val sms = snap?.optJSONObject("marketingSms")
    val sleeping = KassaApi.jsonNum(snap?.optJSONObject("clientMix"), "reactivationPool60d").toInt()
    val markdown = KassaApi.pick(payload, "recommendationsMarkdown", "markdown", "text")
    val llmError = KassaApi.pick(payload, "llmError", "error")
    val generated = KassaApi.pick(payload, "generatedAt", "updatedAt")
    val source = KassaApi.aiSourceLabel(payload)
    val showFunnel = funnel != null && (
        KassaApi.jsonNum(funnel, "downloadsTotal") > 0 || KassaApi.jsonNum(funnel, "installs") > 0
    )
    val showSms = sms != null && KassaApi.jsonNum(sms, "campaignsInPeriod") > 0
    val partners = remember(snap, dormantOpen) { KassaApi.dormantPartners(snap) }
    val tabs = buildList {
        add("reco" to "Рекомендации")
        add("ask" to "Спросить AI")
        if (isAdmin) add("admin" to "Админ")
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("AI аналитика", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing || asking,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SiteSectionHead("AI аналитика", "Инсайты и рекомендации на основе агрегатов CRM (как на prod)")
                }
                item {
                    SiteSegmented(value = tab, items = tabs, onChange = { tab = it })
                }
                item {
                    Text("Период", color = AtColors.muted, fontSize = 12.sp)
                    SiteSegmented(
                        value = preset,
                        items = listOf(
                            "30d" to "30 дней",
                            "90d" to "90 дней",
                            "6m" to "6 месяцев",
                            "ytd" to "С начала года",
                            "all" to "За всё время",
                            "custom" to "Свой",
                        ),
                        onChange = { applyPreset(it) },
                    )
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dateFrom,
                            onValueChange = {
                                dateFrom = it
                                preset = KassaApi.aiMatchedPreset(it, dateTo, minOrderDate)
                            },
                            label = { Text("Дата с") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        OutlinedTextField(
                            value = dateTo,
                            onValueChange = {
                                dateTo = it
                                preset = KassaApi.aiMatchedPreset(dateFrom, it, minOrderDate)
                            },
                            label = { Text("Дата по") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }
                item {
                    Text("Город", color = AtColors.muted, fontSize = 12.sp)
                    SiteSegmented(
                        value = cityKey,
                        items = cities.map { it.key to it.name },
                        onChange = { cityKey = it },
                    )
                }
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AtColors.accent)
                            .clickable(enabled = !refreshing) { tick++ },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (refreshing) "Анализ…" else "Обновить анализ",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (payload != null) {
                    item {
                        Text(
                            "Источник: $source · обновлено ${KassaApi.prettyTime(generated).ifBlank { generated.ifBlank { "—" } }}",
                            color = AtColors.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (err != null) {
                    item { ActionBanner(err!!, error = true) }
                }

                if (tab == "reco") {
                    if (!loaded && err == null) {
                        item { LoadingCard() }
                    } else if (refreshing && payload == null) {
                        item { Text("Анализ данных кассы…", color = AtColors.muted, fontSize = 13.sp) }
                    }
                    if (llmError.isNotBlank()) {
                        item { ActionBanner(llmError, error = true) }
                    }
                    if (showFunnel && funnel != null) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("Движение пользователя: скачивание → регистрация → первый заказ", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "Скачивания — App Store / Google Play (без разбивки по городу; скрыты при фильтре Ашхабад/Мары). Регистрации — аккаунты в приложении (TM, +993) выбранного города. Первый заказ — из этой когорты.",
                                    color = AtColors.muted,
                                    fontSize = 12.sp,
                                )
                                val dl = KassaApi.jsonNum(funnel, "downloadsTotal")
                                val apple = KassaApi.jsonNum(funnel, "downloadsApple")
                                val play = KassaApi.jsonNum(funnel, "downloadsGoogle")
                                val installs = KassaApi.jsonNum(funnel, "installs")
                                val first = KassaApi.jsonNum(funnel, "firstOrdersFromInstalls")
                                val pctDi = if (funnel.has("pctDownloadToInstall") && !funnel.isNull("pctDownloadToInstall")) KassaApi.jsonNum(funnel, "pctDownloadToInstall") else null
                                val pctIf = if (funnel.has("pctInstallToFirstOrder") && !funnel.isNull("pctInstallToFirstOrder")) KassaApi.jsonNum(funnel, "pctInstallToFirstOrder") else null
                                val pctDf = if (funnel.has("pctDownloadToFirstOrder") && !funnel.isNull("pctDownloadToFirstOrder")) KassaApi.jsonNum(funnel, "pctDownloadToFirstOrder") else null
                                val avgDays = if (funnel.has("avgDaysInstallToFirstOrder") && !funnel.isNull("avgDaysInstallToFirstOrder")) KassaApi.jsonNum(funnel, "avgDaysInstallToFirstOrder") else null
                                ReportKpiGrid(
                                    listOf(
                                        PulseMetric("Скачивания", KassaApi.prettyNumber(dl.toLong().toString()), "App Store ${KassaApi.prettyNumber(apple.toLong().toString())} · Play ${KassaApi.prettyNumber(play.toLong().toString())}", "↓"),
                                        PulseMetric("Регистрации в приложении", KassaApi.prettyNumber(installs.toLong().toString()), "аккаунты +993 · ${KassaApi.aiPct(pctDi)} от скачиваний", "☺"),
                                        PulseMetric("Первый заказ", KassaApi.prettyNumber(first.toLong().toString()), "${KassaApi.aiPct(pctDf)} от скачиваний · ср. ${avgDays?.toInt()?.toString() ?: "—"} дн. до заказа", "★"),
                                        PulseMetric("Все новые покупатели сервиса за период", KassaApi.prettyNumber(KassaApi.jsonNum(funnel, "firstOrdersInPeriod").toLong().toString()), "конверсия установка → заказ ${KassaApi.aiPct(pctIf)}", "◎"),
                                    ),
                                )
                            }
                        }
                    }
                    if (showSms && sms != null) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("SMS (маркетинг) за период", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                ReportKpiGrid(
                                    listOf(
                                        PulseMetric("Рассылок", KassaApi.prettyNumber(KassaApi.jsonNum(sms, "campaignsInPeriod").toLong().toString()), "", "✉"),
                                        PulseMetric("SMS", KassaApi.prettyNumber(KassaApi.jsonNum(sms, "totalSentLinked").toLong().toString()), "", "☎"),
                                        PulseMetric("CR", "${KassaApi.jsonNum(sms, "conversionPct")}%", "", "%"),
                                        PulseMetric("Заказы", KassaApi.tmt(KassaApi.jsonNum(sms, "ordersAmount")), "", "₸"),
                                    ),
                                )
                            }
                        }
                    }
                    if (sleeping > 0 && canPortraits) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AtColors.accentSoft)
                                    .border(1.dp, AtColors.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .clickable { onOpenPortraits() }
                                    .padding(14.dp),
                            ) {
                                Text("Открыть $sleeping «спящих» клиентов в портрете →", color = AtColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                    if (markdown.isNotBlank()) {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InlineActionChip("Копировать") {
                                    DeviceIntents.copy(ctx, markdown)
                                    Toast.makeText(ctx, "Рекомендации скопированы", Toast.LENGTH_SHORT).show()
                                }
                                InlineActionChip("Поделиться") { DeviceIntents.share(ctx, markdown) }
                            }
                        }
                        items(KassaApi.parseAiMarkdown(markdown)) { block ->
                            AiMarkdownBlock(block)
                        }
                        if (partners.isNotEmpty()) {
                            item {
                                Text(
                                    if (dormantOpen) "Скрыть витрины без заказов" else "Показать ${KassaApi.dormantCount(snap)} витрин без заказов",
                                    color = AtColors.accent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { dormantOpen = !dormantOpen },
                                )
                            }
                            if (dormantOpen) {
                                item {
                                    val hint = KassaApi.dormantCriteria(snap)
                                    ReportTableCard(
                                        title = hint.ifBlank { "Подключённые без заказов" },
                                        headers = listOf("Заведение", "С нач. года", "За период", "Посл. заказ"),
                                        rows = partners.take(40).map { p ->
                                            listOf(
                                                KassaApi.pick(p, "name", "kassaName", "crmTitle").ifBlank { "—" },
                                                KassaApi.pick(p, "ordersYtd").ifBlank { KassaApi.jsonNum(p, "ordersYtd").toInt().toString() },
                                                KassaApi.pick(p, "ordersInPeriod").ifBlank { KassaApi.jsonNum(p, "ordersInPeriod").toInt().toString() },
                                                KassaApi.fmtDay(KassaApi.pick(p, "lastOrderAt").take(10)).ifBlank { "—" },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    } else if (loaded && !refreshing) {
                        item {
                            EmptyStateCard(
                                "Нажмите «Обновить анализ» или измените период.",
                                "AI даёт диагноз и действия по срезу CRM, не просто список цифр.",
                            )
                        }
                    }
                    if (snap != null) {
                        item {
                            Text(
                                if (snapshotOpen) "Скрыть снимок данных (JSON)" else "Снимок данных (JSON)",
                                color = AtColors.accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { snapshotOpen = !snapshotOpen },
                            )
                        }
                        if (snapshotOpen) {
                            item {
                                Text(
                                    snap.toString(2),
                                    color = AtColors.muted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth().atCard(12.dp).padding(12.dp),
                                )
                            }
                        }
                    }
                }

                if (tab == "ask") {
                    item {
                        Column(
                            Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Спросите о данных", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "AI даёт диагноз и действия по срезу CRM (не просто список цифр). История — до 16 реплик.",
                                color = AtColors.muted,
                                fontSize = 12.sp,
                            )
                            SiteSegmented(
                                value = "",
                                items = listOf(
                                    "orders" to "Почему просели заказы?",
                                    "sleep" to "Что делать со спящими?",
                                    "funnel" to "Где теряем конверсию?",
                                ),
                                onChange = {
                                    question = when (it) {
                                        "sleep" -> "Что делать со спящими клиентами в первую очередь?"
                                        "funnel" -> "Где теряем конверсию скачивание → регистрация → первый заказ?"
                                        else -> "Почему просели заказы и что делать в первую очередь?"
                                    }
                                },
                            )
                            OutlinedTextField(
                                value = question,
                                onValueChange = { question = it },
                                placeholder = { Text("Например: почему просели заказы и что делать в первую очередь?") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                                colors = fieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                maxLines = 6,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (question.isBlank() || asking) AtColors.glass else AtColors.accent)
                                        .clickable(enabled = question.isNotBlank() && !asking) { askNow() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(if (asking) "Думаем…" else "Спросить AI", color = if (question.isBlank() || asking) AtColors.muted else Color.White, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    Modifier
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AtColors.panel)
                                        .border(1.dp, AtColors.stroke, RoundedCornerShape(12.dp))
                                        .clickable {
                                            history = emptyList()
                                            lastAdvisor = null
                                        }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Очистить диалог", color = AtColors.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    val advisorErr = KassaApi.pick(lastAdvisor, "llmError")
                    if (advisorErr.isNotBlank()) {
                        item { ActionBanner(advisorErr, error = true) }
                    }
                    if (history.isEmpty()) {
                        item {
                            EmptyStateCard("Спросите AI", "Готовые вопросы сверху или свой формулировка — ответ строится по текущему периоду и городу.")
                        }
                    } else {
                        itemsIndexed(history) { _, turn ->
                            val mine = turn.first == "user"
                            Column(
                                Modifier.fillMaxWidth(),
                                horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                            ) {
                                Text(if (mine) "Вы" else "AI", color = AtColors.muted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                                Column(
                                    Modifier
                                        .fillMaxWidth(0.94f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (mine) AtColors.accentSoft else AtColors.glass)
                                        .padding(12.dp),
                                ) {
                                    if (mine) {
                                        Text(turn.second, color = AtColors.text, fontSize = 14.sp)
                                    } else {
                                        KassaApi.parseAiMarkdown(turn.second).forEach { AiMarkdownBlock(it, tight = true) }
                                    }
                                }
                            }
                        }
                        lastAdvisor?.let { adv ->
                            val rag = adv.optJSONObject("rag")
                            item {
                                val used = KassaApi.pick(rag, "chunksUsedInPrompt").ifBlank { KassaApi.jsonNum(rag, "chunksUsedInPrompt").toInt().takeIf { it > 0 }?.toString().orEmpty() }
                                val total = KassaApi.pick(rag, "chunksInStore").ifBlank { KassaApi.jsonNum(rag, "chunksInStore").toInt().takeIf { it > 0 }?.toString().orEmpty() }
                                val ragLine = if (used.isNotBlank() || total.isNotBlank()) " · RAG: ${used.ifBlank { "—" }}/${total.ifBlank { "—" }} чанков" else ""
                                Text(
                                    "${KassaApi.aiSourceLabel(adv)} · ${KassaApi.prettyTime(KassaApi.pick(adv, "generatedAt"))}$ragLine",
                                    color = AtColors.muted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }

                if (tab == "admin" && isAdmin) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Админ: RAG и Firebase push", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            val chunks = KassaApi.pick(ragStats, "totalChunks").ifBlank {
                                KassaApi.jsonNum(ragStats, "totalChunks").toInt().toString()
                            }
                            val fbConfigured = KassaApi.pick(fbStatus, "configured") in setOf("true", "1") || fbStatus?.optBoolean("configured") == true
                            val fbRows = KassaApi.pick(fbStatus, "rowCount").ifBlank { KassaApi.jsonNum(fbStatus, "rowCount").toInt().toString() }
                            Text(
                                "RAG чанков в базе: ${chunks.ifBlank { "—" }}" +
                                    if (fbStatus != null) " · Firebase CSV: ${if (fbConfigured) "$fbRows строк" else "не загружен"}" else "",
                                color = AtColors.muted,
                                fontSize = 12.sp,
                            )
                            OutlinedTextField(
                                value = ragLabel,
                                onValueChange = { ragLabel = it },
                                label = { Text("Метка источника RAG") },
                                placeholder = { Text("например: контент-план март") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(),
                            )
                            OutlinedTextField(
                                value = ragText,
                                onValueChange = { ragText = it },
                                label = { Text("Текст для RAG (≥10 символов)") },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                                colors = fieldColors(),
                                maxLines = 8,
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (ragBusy || ragText.trim().length < 10) AtColors.glass else AtColors.accent)
                                    .clickable(enabled = !ragBusy && ragText.trim().length >= 10) { ingestRag() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (ragBusy) "Загрузка…" else "Загрузить в RAG", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AtColors.panel)
                                    .border(1.dp, AtColors.stroke, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !fbBusy) { pickCsv.launch("*/*") },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (fbBusy) "Импорт CSV…" else "Импорт Firebase CSV", color = AtColors.text, fontWeight = FontWeight.SemiBold)
                            }
                            ragMsg?.let { Text(it, color = AtColors.success, fontSize = 13.sp) }
                            fbMsg?.let { Text(it, color = AtColors.success, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMarkdownBlock(block: AiMdBlock, tight: Boolean = false) {
    when (block) {
        is AiMdBlock.Heading -> Text(
            KassaApi.humanText(block.text),
            color = AtColors.text,
            fontWeight = FontWeight.Bold,
            fontSize = if (tight) 15.sp else 16.sp,
            modifier = Modifier.padding(top = if (tight) 4.dp else 2.dp),
        )
        is AiMdBlock.Paragraph -> AiMdRichText(block.text, if (tight) 13.sp else 14.sp)
        is AiMdBlock.ListItems -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { idx, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (block.numbered) "${idx + 1}." else "•", color = AtColors.accent, fontWeight = FontWeight.Bold)
                    AiMdRichText(item, 14.sp, Modifier.weight(1f))
                }
            }
        }
        is AiMdBlock.Table -> ReportTableCard("", block.headers, block.rows)
    }
}

@Composable
private fun AiMdRichText(text: String, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val annotated = remember(text) {
        buildAnnotatedString {
            val regex = Regex("""\*\*(.+?)\*\*""")
            var last = 0
            for (m in regex.findAll(text)) {
                if (m.range.first > last) append(text.substring(last, m.range.first))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
                last = m.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }
    Text(annotated, color = AtColors.text, fontSize = size, modifier = modifier)
}


// --- recovered from new-reports.kt ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportKpiGrid(metrics: List<PulseMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { metric -> SitePulseCard(metric, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun ReportTableCard(title: String, headers: List<String>, rows: List<List<String>>) {
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (rows.isEmpty()) {
            Text("Нет данных за выбранный период.", color = AtColors.muted, fontSize = 13.sp)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                headers.forEach { h ->
                    Text(h, color = AtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }
            rows.take(40).forEach { cols ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cols.forEach { c ->
                        Text(c.ifBlank { "—" }, color = AtColors.text, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportBarCard(title: String, bars: List<Pair<String, Double>>) {
    val max = bars.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        bars.take(24).forEach { (label, value) ->
            val pct = ((value / max) * 100.0).coerceIn(3.0, 100.0)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.width(72.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(99.dp)).background(AtColors.glass)) {
                    Box(Modifier.fillMaxWidth(pct.toFloat() / 100f).fillMaxSize().background(AtColors.accent))
                }
                Text(KassaApi.tmt(value), color = AtColors.text, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ReportLineCard(title: String, lines: List<String>) {
    Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        lines.filter { it.isNotBlank() }.forEach { line ->
            Text(line, color = AtColors.muted, fontSize = 12.sp)
        }
    }
}


// --- recovered from failed patch (LogisticsIntakeSheet) ---

@Composable
private fun LogisticsIntakeSheet(
    row: JsonRow,
    pending: JsonRow?,
    related: List<JSONObject>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onOpenOp: () -> Unit,
) {
    val o = pending?.raw ?: row.raw
    val err = KassaApi.pick(row.raw, "proposalError")
    val tone = KassaApi.intakeTone(row.raw, pending?.raw, related)
    val lines = KassaApi.parseIntakeBodyItems(row.raw)
    val phone = KassaApi.pick(row.raw, "clientPhone")
    val address = KassaApi.extraAmountValue(row.raw, "clientAddress")
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Заказ ${KassaApi.orderNo(row.raw)}", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                StatusChip(tone.label)
            }
            Text(
                "${KassaApi.intakeEventCaption(row.raw)} · ${KassaApi.prettyTime(KassaApi.pick(row.raw, "createdAt"))}",
                color = AtColors.muted,
                fontSize = 12.sp,
            )
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (err.isNotBlank()) Text(err, color = AtColors.danger, fontSize = 13.sp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AtColors.glass).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Суммы", color = AtColors.text, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyChip("Без доставки", KassaApi.tmt(KassaApi.opMoney(o, "orderAmount")))
                        MoneyChip("Доставка", KassaApi.tmt(KassaApi.opMoney(o, "deliveryAmount")))
                        MoneyChip("К выплате", KassaApi.tmt(KassaApi.opMoney(o, "payoutAmount")))
                    }
                    val pct = KassaApi.opMoney(o, "commissionPercent")
                    val comm = KassaApi.opMoney(o, "commissionAmount")
                    if (pct > 0 || comm > 0) Text("Комиссия ${KassaApi.prettyNumber(pct.toString())}% · ${KassaApi.tmt(comm)}", color = AtColors.muted, fontSize = 12.sp)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AtColors.glass).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Клиент", color = AtColors.text, fontWeight = FontWeight.Bold)
                    Text("ID: ${KassaApi.pick(row.raw, "clientExternalId").ifBlank { "—" }}", color = AtColors.text, fontSize = 13.sp)
                    Text("Телефон: ${phone.ifBlank { "—" }}", color = AtColors.text, fontSize = 13.sp)
                    Text("Адрес: ${address.ifBlank { "—" }}", color = AtColors.text, fontSize = 13.sp)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AtColors.glass).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Заведение", color = AtColors.text, fontWeight = FontWeight.Bold)
                    Text(KassaApi.pick(row.raw, "establishmentTitle").ifBlank { "—" }, color = AtColors.text, fontSize = 13.sp)
                    val delivioId = KassaApi.pick(row.raw, "establishmentExternalId")
                    if (delivioId.isNotBlank()) Text("Delivio ID: $delivioId", color = AtColors.muted, fontSize = 12.sp)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AtColors.glass).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Состав заказа", color = AtColors.text, fontWeight = FontWeight.Bold)
                    if (lines.isEmpty()) {
                        Text("Состав не снят ботом — откройте карточку в Delivio или обновите intake.", color = AtColors.muted, fontSize = 13.sp)
                    } else {
                        lines.forEach { line ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${KassaApi.pick(line, "quantity")} × ${KassaApi.pick(line, "dishName", "name")}",
                                    color = AtColors.text,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(KassaApi.pick(line, "lineTotal").ifBlank { "—" }, color = AtColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InlineActionChip("Закрыть") { onDismiss() }
                if (pending != null) InlineActionChip("Карточка") { onOpenOp() }
                if (err.isNotBlank()) InlineActionChip("Повторить") { onRetry() }
                if (pending != null && KassaApi.operationStatus(pending) == "PENDING_REVIEW") {
                    InlineActionChip("Подтвердить заказ", filled = true) { onConfirm() }
                }
            }
        }
    }
}

@Composable


// --- recovered from failed patch (ProblemOrdersWorkspacePane) ---

private fun ProblemOrdersWorkspacePane(
    api: KassaApi,
    token: String?,
    me: AppUser?,
    cache: CacheStore?,
    initialTab: String = "journal",
    onBack: () -> Unit,
    onOpenRow: (JsonRow) -> Unit,
    onOpenOp: (JsonRow) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("atcrm", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(if (initialTab == "dashboard") "dashboard" else "journal") }
    val defaultCities = remember(me) { KassaApi.opsCityOptions(me) }
    var cityKey by remember {
        mutableStateOf(
            defaultCities.singleOrNull { it.key.isNotBlank() }?.key
                ?: prefs.getString("problems_city", "").orEmpty(),
        )
    }
    var cities by remember { mutableStateOf(defaultCities) }
    var reason by remember { mutableStateOf("ALL") }
    var qDraft by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var analytics by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var createBusy by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(cityKey) { prefs.edit().putString("problems_city", cityKey).apply() }

    LaunchedEffect(token, me) {
        cities = KassaApi.opsCityOptions(me)
        if (cities.size == 1 && cities[0].key.isNotBlank()) cityKey = cities[0].key
        else if (cityKey.isNotBlank() && cities.none { it.key == cityKey }) cityKey = ""
    }

    LaunchedEffect(token, tick, cityKey) {
        if (token.isNullOrBlank()) return@LaunchedEffect
        err = null
        if (loaded) refreshing = true
        try {
            val pack = withContext(Dispatchers.IO) {
                val list = runCatching { api.getRows(KassaApi.problemOrdersPath(cityKey), token) }.getOrDefault(emptyList())
                val dash = runCatching { api.getObject(KassaApi.problemAnalyticsPath(cityKey), token) }.getOrNull()
                list to dash
            }
            rows = pack.first.sortedByDescending { KassaApi.pick(it.raw, "createdAt") }
            analytics = pack.second
            cache?.putRows("/problem-orders", pack.first)
            loaded = true
        } catch (e: Exception) {
            err = e.message ?: "Ошибка"
            cache?.getRows("/problem-orders")?.let {
                rows = it
                loaded = true
            }
        } finally {
            refreshing = false
        }
    }

    val filtered = remember(rows, reason, q) {
        val needle = q.trim().lowercase()
        rows.filter { row ->
            val r = KassaApi.pick(row.raw, "reason").uppercase()
            val reasonOk = reason == "ALL" || r == reason
            val hay = listOf(
                KassaApi.orderNo(row.raw),
                KassaApi.establishmentName(row.raw),
                KassaApi.problemComment(row.raw),
            ).joinToString(" ").lowercase()
            reasonOk && (needle.isBlank() || hay.contains(needle))
        }
    }
    val totalKpi = analytics?.optInt("total", rows.size) ?: rows.size
    val avgDelay = analytics?.let { KassaApi.jsonNum(it, "avgDelayMinutes") } ?: run {
        if (rows.isEmpty()) 0.0 else rows.map { KassaApi.problemDelayMinutes(it.raw) }.average()
    }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine("Проблемные заказы", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    SiteSectionHead("Проблемные заказы", "Заказы с задержками и комментариями операторов")
                }
                item {
                    SiteSegmented(
                        value = tab,
                        items = listOf("journal" to "Журнал", "dashboard" to "Дашборд"),
                        onChange = { tab = it },
                    )
                }
                if (err != null) item { ActionBanner(err!!, error = true) }
                if (msg != null) item { ActionBanner(msg!!, error = false) }
                item {
                    Box(
                        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent)
                            .clickable { creating = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("+ Занести проблемный заказ", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                if (cities.size > 1) {
                    item {
                        Text("Город", color = AtColors.muted, fontSize = 12.sp)
                        SiteSegmented(
                            value = cityKey,
                            items = cities.take(8).map { it.key to it.name },
                            onChange = { cityKey = it },
                        )
                    }
                }
                if (tab == "dashboard") {
                    item {
                        SiteSectionHead("Дашборд проблемных заказов", "")
                    }
                    item {
                        ReportKpiGrid(
                            listOf(
                                PulseMetric("Всего проблемных заказов", totalKpi.toString(), if (cityKey.isBlank()) "все города" else KassaApi.cityLabel(cityKey), "⚠", tint = "danger"),
                                PulseMetric("Средняя задержка (мин)", KassaApi.prettyNumber(avgDelay.toString()), "по выбранному городу", "⏱", tint = "warn"),
                            ),
                        )
                    }
                    val byReason = KassaApi.problemAnalyticsList(analytics, "byReason")
                    item {
                        Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Причина", color = AtColors.text, fontWeight = FontWeight.Bold)
                            if (byReason.isEmpty()) {
                                Text("Нет данных", color = AtColors.muted, fontSize = 13.sp)
                            } else {
                                byReason.forEach { row ->
                                    val key = KassaApi.pick(row, "reason").ifBlank { "OTHER" }
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                            reason = key
                                            tab = "journal"
                                        }.padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(KassaApi.problemReasonLabel(key), color = AtColors.text, fontSize = 14.sp)
                                        Text(row.optInt("count", 0).toString(), color = AtColors.text, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    val byEst = KassaApi.problemAnalyticsList(analytics, "byEstablishment")
                    item {
                        Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Заведение", color = AtColors.text, fontWeight = FontWeight.Bold)
                            if (byEst.isEmpty()) {
                                Text("Нет данных", color = AtColors.muted, fontSize = 13.sp)
                            } else {
                                byEst.take(40).forEach { row ->
                                    val name = KassaApi.pick(row, "establishmentName", "name").ifBlank { "—" }
                                    val count = row.optInt("count", 0)
                                    val avg = KassaApi.jsonNum(row, "avgDelayMinutes")
                                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text(name, color = AtColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text("Случаев: $count · средн. задержка ${KassaApi.prettyNumber(avg.toString())} мин", color = AtColors.muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    val byOrder = KassaApi.problemAnalyticsList(analytics, "byOrder")
                    item {
                        Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("По заказам", color = AtColors.text, fontWeight = FontWeight.Bold)
                            if (byOrder.isEmpty()) {
                                Text("Пока нет данных по заказам", color = AtColors.muted, fontSize = 13.sp)
                            } else {
                                byOrder.take(50).forEach { row ->
                                    val num = KassaApi.pick(row, "orderNumber")
                                    val count = row.optInt("count", 0)
                                    val avg = KassaApi.jsonNum(row, "avgDelayMinutes")
                                    val max = KassaApi.jsonNum(row, "maxDelayMinutes")
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                            qDraft = num
                                            q = num
                                            tab = "journal"
                                        }.padding(vertical = 4.dp),
                                    ) {
                                        Text("№ $num", color = AtColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text("Случаев: $count · средн. ${KassaApi.prettyNumber(avg.toString())} · макс. ${KassaApi.prettyNumber(max.toString())} мин", color = AtColors.muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text("Причина", color = AtColors.muted, fontSize = 12.sp)
                        SiteSegmented(
                            value = reason,
                            items = listOf(
                                "ALL" to "Все",
                                "RESTAURANT" to "Ресторан",
                                "STORE" to "Магазин",
                                "COURIER" to "Курьер",
                                "OTHER" to "Другое",
                            ),
                            onChange = { reason = it },
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = qDraft,
                            onValueChange = { qDraft = it },
                            placeholder = { Text("№ заказа, заведение, комментарий") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(),
                            shape = RoundedCornerShape(10.dp),
                        )
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InlineActionChip("Найти", filled = true) { q = qDraft.trim() }
                            InlineActionChip("Сброс") { qDraft = ""; q = "" }
                        }
                    }
                    item {
                        Text("Всего: ${filtered.size}", color = AtColors.muted, fontSize = 13.sp)
                    }
                    if (!loaded && err == null) {
                        item { LoadingCard() }
                    } else if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                if (token.isNullOrBlank()) "Нет сессии CRM" else "Нет записей по выбранным фильтрам",
                                "Смените причину, город или сбросьте поиск.",
                            )
                        }
                    } else {
                        items(filtered, key = { it.id.ifBlank { it.title } }) { row ->
                            ProblemOrderCard(row = row, onOpen = { onOpenRow(row) })
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ProblemCreateDialog(
            api = api,
            token = token,
            busy = createBusy,
            cityKey = cityKey,
            onDismiss = { if (!createBusy) creating = false },
            onSubmit = { body ->
                val t = token ?: return@ProblemCreateDialog
                createBusy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { api.postJson("/problem-orders", t, body) }
                        creating = false
                        msg = "Сохранено."
                        tick++
                        Haptics.tap(ctx)
                    } catch (e: Exception) {
                        err = e.message ?: "Не удалось сохранить"
                    } finally {
                        createBusy = false
                    }
                }
            },
        )
    }
}


// --- recovered from failed patch (ProblemCreateDialog) ---

@Composable
private fun ProblemCreateDialog(
    api: KassaApi,
    token: String?,
    busy: Boolean,
    cityKey: String,
    onDismiss: () -> Unit,
    onSubmit: (JSONObject) -> Unit,
) {
    var orderNo by remember { mutableStateOf("") }
    var estQ by remember { mutableStateOf("") }
    var estId by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<JsonRow>>(emptyList()) }
    var delay by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("RESTAURANT") }
    var comment by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()

    LaunchedEffect(estQ) {
        if (estQ.trim().length < 2 || token.isNullOrBlank()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        delay(280)
        val enc = java.net.URLEncoder.encode(estQ.trim(), "UTF-8")
        hits = withContext(Dispatchers.IO) {
            runCatching { api.getRows("/establishments?q=$enc", token) }.getOrElse {
                runCatching { api.getRows("/sales/establishments?q=$enc", token) }.getOrDefault(emptyList())
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(12.dp).clip(RoundedCornerShape(18.dp)).background(AtColors.panel).padding(16.dp),
        ) {
            Text("Занести проблемный заказ", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(orderNo, { orderNo = it }, label = { Text("Номер заказа") }, placeholder = { Text("Номер заказа") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(estQ, { estQ = it; estId = "" }, label = { Text("Заведение") }, placeholder = { Text("Выберите заведение") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                hits.take(8).forEach { e ->
                    val name = KassaApi.pick(e.raw, "name", "title").ifBlank { e.title }
                    Text(
                        name,
                        color = AtColors.text,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AtColors.glass).clickable {
                            estId = e.id
                            estQ = name
                            hits = emptyList()
                        }.padding(10.dp),
                    )
                }
                OutlinedTextField(delay, { delay = it.filter { ch -> ch.isDigit() }.take(4) }, label = { Text("Задержка (мин)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                Text("Причина", color = AtColors.muted, fontSize = 12.sp)
                SiteSegmented(
                    value = reason,
                    items = listOf(
                        "RESTAURANT" to "Ресторан",
                        "STORE" to "Магазин",
                        "COURIER" to "Курьер",
                        "OTHER" to "Другое",
                    ),
                    onChange = { reason = it },
                )
                OutlinedTextField(
                    comment,
                    { if (it.length <= 1000) comment = it },
                    label = { Text("Причина задержки (обязательно)") },
                    placeholder = { Text("Кратко опишите, почему возникла задержка") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors(),
                    minLines = 3,
                )
                err?.let { Text(it, color = AtColors.danger, fontSize = 13.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.glass).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("Отмена", color = AtColors.muted, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(AtColors.accent).clickable(enabled = !busy) {
                        val mins = delay.toIntOrNull() ?: 0
                        if (orderNo.trim().isBlank() || estId.isBlank() || mins < 1 || comment.trim().isBlank()) {
                            err = "Заполните номер, заведение, задержку (≥1) и причину задержки."
                            return@clickable
                        }
                        onSubmit(KassaApi.problemCreateBody(orderNo, estId, mins, reason, comment, cityKey))
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(if (busy) "Сохранение…" else "Сохранить проблемный заказ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
        }
    }
}


// --- recovered from failed patch (ProblemOrderDetailPane) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProblemOrderDetailPane(
    title: String,
    preload: JSONObject,
    api: KassaApi,
    token: String?,
    onBack: () -> Unit,
    onOpenOp: (JsonRow) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val o = preload
    val orderNo = KassaApi.orderNo(o).ifBlank { title }
    val delayMin = KassaApi.problemDelayMinutes(o)
    val reason = KassaApi.problemReasonLabel(KassaApi.pick(o, "reason"))
    val comment = KassaApi.problemComment(o)
    val phone = KassaApi.phoneOf(o)
    var finding by remember { mutableStateOf(false) }
    var findErr by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
        TopLine(if (orderNo.isNotBlank()) "№ $orderNo" else "Проблемный заказ", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(reason.ifBlank { "Другое" })
                        Text("${KassaApi.prettyNumber(delayMin.toString())} мин · ${KassaApi.delayHms(delayMin)}", color = AtColors.muted, fontSize = 13.sp)
                    }
                    Text("№ $orderNo", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(KassaApi.establishmentName(o).ifBlank { "—" }, color = AtColors.text, fontSize = 15.sp)
                    val city = KassaApi.cityLabel(KassaApi.pick(o, "cityKey"))
                    Text(
                        listOf(
                            KassaApi.prettyTime(KassaApi.pick(o, "createdAt")),
                            KassaApi.problemOperator(o).takeIf { it.isNotBlank() },
                            city.takeIf { it != "—" },
                        ).filter { !it.isNullOrBlank() }.joinToString(" · "),
                        color = AtColors.muted,
                        fontSize = 13.sp,
                    )
                }
            }
            item {
                Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Причина задержки", color = AtColors.text, fontWeight = FontWeight.Bold)
                    Text(comment.ifBlank { "—" }, color = AtColors.text, fontSize = 14.sp)
                }
            }
            if (findErr != null) item { ActionBanner(findErr!!, error = true) }
            item {
                Column(Modifier.fillMaxWidth().atCard(14.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Действия", color = AtColors.text, fontWeight = FontWeight.Bold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InlineActionChip("Копировать №") {
                            DeviceIntents.copy(ctx, orderNo)
                            Toast.makeText(ctx, "Номер скопирован", Toast.LENGTH_SHORT).show()
                            Haptics.tap(ctx)
                        }
                        InlineActionChip(if (finding) "Поиск…" else "Открыть операцию") {
                            val t = token ?: return@InlineActionChip
                            if (orderNo.isBlank()) return@InlineActionChip
                            finding = true
                            findErr = null
                            scope.launch {
                                try {
                                    val hit = withContext(Dispatchers.IO) {
                                        val from = java.time.LocalDate.now().minusDays(90).toString()
                                        val to = java.time.LocalDate.now().toString()
                                        val list = api.getRows(
                                            KassaApi.operationsQuery(from, to, "ALL", 1, 20, orderSearch = orderNo),
                                            t,
                                        )
                                        list.firstOrNull { KassaApi.orderNo(it.raw) == orderNo } ?: list.firstOrNull()
                                    }
                                    if (hit == null) findErr = "Операция № $orderNo не найдена за 90 дней"
                                    else onOpenOp(hit)
                                } catch (e: Exception) {
                                    findErr = e.message ?: "Не удалось открыть операцию"
                                } finally {
                                    finding = false
                                }
                            }
                        }
                        if (phone.isNotBlank()) {
                            InlineActionChip("Позвонить") { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
                        }
                    }
                }
            }
        }
    }
}

