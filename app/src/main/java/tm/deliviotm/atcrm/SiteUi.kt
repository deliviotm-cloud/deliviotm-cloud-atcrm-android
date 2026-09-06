package tm.deliviotm.atcrm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class DrawerItem(
    val label: String,
    val purpose: String,
    val spec: ModuleSpec? = null,
    val dashboard: Boolean = false,
    val icon: String = "•",
    val navTo: String = "",
)

internal data class DrawerSection(
    val title: String,
    val items: List<DrawerItem>,
)

internal fun siteDrawerSections(modules: List<ModuleSpec>, navOrder: List<String> = emptyList()): List<DrawerSection> {
    fun find(vararg needles: String): ModuleSpec? =
        modules.find { m -> needles.any { m.path.contains(it) || m.title.contains(it, true) } }
    fun exact(path: String): ModuleSpec? = modules.find { it.path == path || it.path.substringBefore("?") == path }

    val grouped = listOf(
        DrawerSection(
            "",
            listOf(DrawerItem("Dashboard", "Цифры и что сделать сейчас", dashboard = true, icon = "⌂", navTo = "/dashboard")),
        ),
        DrawerSection(
            "РАБОТА",
            listOfNotNull(
                find("/workspace/tasks")?.let { DrawerItem("Задачи", "Срок, канбан, проверка", it, icon = "✓", navTo = "/tasks") },
                find("/operations")?.takeIf { !it.path.contains("logistics") && !it.path.contains("activity") }
                    ?.let { DrawerItem("Операции", "Заказы за 30 дней", it, icon = "☰", navTo = "/operations") },
                find("/problem-orders")?.let { DrawerItem("Проблемные заказы", "Задержки по точкам", it, icon = "!", navTo = "/problem-orders") },
                find("/sales/establishments")?.let { DrawerItem("Отдел продаж", "Воронка, Excel, SMS", it, icon = "◎", navTo = "/sales") },
                find("/courier-fleet/couriers")?.let { DrawerItem("Курьеры", "Флот, доставки, зарплата", it, icon = "›", navTo = "/couriers") },
                find("/accounting")?.let { DrawerItem("Бухгалтерия", "Профиль ИП, документы, отчёты", it, icon = "₸", navTo = "/accounting") },
            ),
        ),
        DrawerSection(
            "СВЯЗЬ",
            listOfNotNull(
                find("/workspace/chats")?.let { DrawerItem("Чаты", "Переписка с сотрудниками", it, icon = "◉", navTo = "/chats") },
                find("/email/messages")?.let { DrawerItem("Почта", "Корпоративный ящик CRM", it, icon = "✉", navTo = "/mail") },
                find("/support")?.let { DrawerItem("Поддержка", "Telegram-обращения клиентов", it, icon = "?", navTo = "/support") },
                find("/calls")?.let { DrawerItem("Колл-центр", "Delivio Call и SMS", it, icon = "☎", navTo = "/callcenter") },
            ),
        ),
        DrawerSection(
            "АНАЛИТИКА",
            listOfNotNull(
                exact("/reports")?.let { DrawerItem("Отчёты", "Оборот, заказы, скачивания", it, icon = "▦", navTo = "/reports") },
                find("/ai/")?.let { DrawerItem("AI аналитика", "Инсайты и рекомендации", it, icon = "✦", navTo = "/ai") },
            ),
        ),
        DrawerSection(
            "АДМИН",
            listOfNotNull(
                find("/establishments")?.takeIf { !it.path.contains("sales") }?.let { DrawerItem("Заведения", "Справочник Delivio", it, icon = "⌖", navTo = "/establishments") },
                find("/clients")?.let { DrawerItem("Маркетинг", "Клиенты, рассылки, промокоды", it, icon = "♡", navTo = "/marketing") },
                find("/auth/me")?.let { DrawerItem("Настройки", "Мой профиль, меню, шлюзы, журнал", it, icon = "⚙", navTo = "/settings") }
                    ?: find("/users")?.let { DrawerItem("Настройки", "Мой профиль, меню, шлюзы, журнал", it, icon = "⚙", navTo = "/settings") },
            ),
        ),
    ).filter { it.items.isNotEmpty() }

    if (navOrder.isEmpty()) return grouped
    val all = grouped.flatMap { it.items }
    val byTo = all.associateBy { it.navTo.ifBlank { it.label } }
    val used = linkedSetOf<String>()
    val ordered = mutableListOf<DrawerItem>()
    for (to in navOrder) {
        val item = byTo[to] ?: continue
        if (used.add(to)) ordered += item
    }
    for (item in all) {
        val key = item.navTo.ifBlank { item.label }
        if (used.add(key)) ordered += item
    }
    return listOf(DrawerSection("", ordered)).filter { it.items.isNotEmpty() }
}

@Composable
internal fun SiteDrawerContent(
    user: AppUser?,
    sections: List<DrawerSection>,
    selectedDashboard: Boolean,
    selectedPath: String = "",
    onDashboard: () -> Unit,
    onOpen: (ModuleSpec) -> Unit,
    onLogout: () -> Unit,
    api: KassaApi? = null,
    token: String? = null,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(AtColors.panel)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.atcrm_mark),
                contentDescription = "AT CRM",
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text("AT CRM", color = AtColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        sections.forEach { section ->
            if (section.title.isNotBlank()) {
                Text(
                    section.title,
                    color = AtColors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            section.items.forEach { item ->
                val selected = drawerItemSelected(item, selectedDashboard, selectedPath)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 1.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) AtColors.accentSoft else Color.Transparent)
                        .clickable {
                            if (item.dashboard) onDashboard()
                            else item.spec?.let(onOpen)
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selected) "✓" else item.icon,
                        color = if (selected) AtColors.accent else AtColors.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        item.label,
                        color = if (selected) AtColors.accent else AtColors.text,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = AtColors.stroke, modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(
                name = user?.fullName ?: "A",
                avatarUrl = user?.avatarUrl.orEmpty(),
                api = api,
                token = token,
                size = 36.dp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(user?.fullName ?: "—", color = AtColors.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user?.role ?: "", color = AtColors.muted, fontSize = 12.sp)
            }
            Text("Выйти", color = AtColors.danger, modifier = Modifier.clickable(onClick = onLogout), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun drawerItemSelected(item: DrawerItem, selectedDashboard: Boolean, selectedPath: String): Boolean {
    if (item.dashboard) return selectedDashboard
    if (selectedDashboard) return false
    val cur = selectedPath.substringBefore("?").trimEnd('/')
    if (cur.isBlank()) return false
    val path = item.spec?.path?.substringBefore("?")?.trimEnd('/').orEmpty()
    if (path.isNotBlank() && (cur == path || cur.startsWith("$path/"))) return true
    return when (item.label) {
        "Отдел продаж" -> cur.contains("/sales") || cur.contains("/sms/broadcasts")
        "Курьеры" -> cur.contains("courier")
        "Бухгалтерия" -> cur.contains("accounting") || cur.contains("reconciliation")
        "Колл-центр" -> cur.contains("/calls") || cur.contains("/sms/logs") || cur.contains("/sms/incoming")
        "Маркетинг" -> cur.contains("/clients") || cur.contains("/marketing") || cur.contains("/qr") || (cur.contains("/push") && !cur.contains("/push/status"))
        "Настройки" -> cur.contains("/users") || cur.contains("/auth") || cur.contains("/settings") || cur.contains("app-stores") || cur.contains("/sms/status") || cur.contains("/email/status") || cur.contains("/push/status") || cur.contains("/support/admin/telegram")
        "Заведения" -> cur.startsWith("/establishments")
        "AI аналитика" -> cur.contains("/ai")
        "Почта" -> cur.contains("/email/messages") || cur.contains("/email/mailbox")
        "Отчёты" -> cur.contains("/reports")
        "Задачи" -> cur.contains("/workspace/tasks")
        "Операции" -> cur.contains("/operations")
        "Проблемные заказы" -> cur.contains("/problem-orders")
        "Чаты" -> cur.contains("/workspace/chats")
        "Поддержка" -> cur.contains("/support")
        else -> false
    }
}

@Composable
internal fun CrmShell(
    user: AppUser?,
    modules: List<ModuleSpec>,
    selectedDashboard: Boolean,
    selectedPath: String,
    onDashboard: () -> Unit,
    onOpen: (ModuleSpec) -> Unit,
    onLogout: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onBell: () -> Unit = onSettings,
    api: KassaApi? = null,
    token: String? = null,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val navOrder = NavOrderStore.load(ctx)
    val sections = remember(modules, navOrder) { siteDrawerSections(modules, navOrder) }
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = AtColors.glass) {
                SiteDrawerContent(
                    user = user,
                    sections = sections,
                    selectedDashboard = selectedDashboard,
                    selectedPath = selectedPath,
                    onDashboard = {
                        scope.launch { drawerState.close() }
                        onDashboard()
                    },
                    onOpen = {
                        scope.launch { drawerState.close() }
                        onOpen(it)
                    },
                    onLogout = onLogout,
                    api = api,
                    token = token,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(AtColors.bgDeep)) {
            SiteTopBar(
                user = user,
                onMenu = { scope.launch { drawerState.open() } },
                onSearch = onSearch,
                onSettings = onSettings,
                onBell = onBell,
                api = api,
                token = token,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

@Composable
internal fun SiteThemeToggle() {
    val dark = LocalAtPalette.current.isDark
    val toggle = LocalToggleTheme.current
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AtColors.glass)
            .border(1.dp, AtColors.stroke, RoundedCornerShape(10.dp))
            .clickable(onClick = toggle),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (dark) "☀️" else "🌙", fontSize = 16.sp)
    }
}

@Composable
internal fun SiteTopBar(
    user: AppUser?,
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onBell: () -> Unit = onSettings,
    api: KassaApi? = null,
    token: String? = null,
) {
    val bellCount by PollWatcher.badgeTotal.collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .background(AtColors.panel)
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) {
                Text("☰", color = AtColors.text, fontSize = 20.sp)
            }
            Row(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(AtColors.bgDeep)
                    .clickable(onClick = onSearch)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⌕  ", color = AtColors.muted, fontSize = 14.sp)
                Text("Поиск по разделам, клиентам, задачам…", color = AtColors.muted, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            SiteThemeToggle()
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onBell),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔔", fontSize = 14.sp)
                if (bellCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(AtColors.danger),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (bellCount > 9) "9+" else bellCount.toString(),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onSettings),
            ) {
                UserAvatar(
                    name = user?.fullName ?: "A",
                    avatarUrl = user?.avatarUrl.orEmpty(),
                    api = api,
                    token = token,
                    size = 32.dp,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        HorizontalDivider(color = AtColors.stroke, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun SitePulseCard(
    metric: PulseMetric,
    modifier: Modifier = Modifier,
) {
    val light = LocalAtPalette.current.bgDeep.red > 0.5f
    val (fill, edge) = pulseTint(metric.tint.ifBlank { pulseTintKey(metric.label) }, light)
    val delta = metric.delta.trim()
    val deltaLine = when {
        delta.isBlank() -> metric.hint.ifBlank { "за период" }
        delta.contains("прошл", ignoreCase = true) -> delta
        else -> "$delta к прошлому периоду"
    }
    Column(
        modifier
            .shadow(2.dp, RoundedCornerShape(12.dp), clip = false, ambientColor = Color(0x080F172A), spotColor = Color(0x0D0F172A))
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(1.dp, edge.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (metric.icon.isNotBlank()) {
                Text(metric.icon, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
            }
            Text(
                metric.label,
                color = AtColors.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            metric.value,
            color = AtColors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (metric.extra.isNotBlank()) {
            Text(
                metric.extra,
                color = AtColors.muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            deltaLine,
            color = if (delta.isNotBlank()) deltaColor(delta, metric.invertDelta) else AtColors.muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
internal fun SiteMiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AtColors.panel)
            .border(1.dp, AtColors.stroke, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(value, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        Text(label, color = AtColors.muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SiteFilterPanel(
    open: Boolean,
    summary: String,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AtColors.panel)
                .border(1.dp, AtColors.stroke, RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Фильтры", color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    summary,
                    color = AtColors.muted,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(if (open) "▴" else "▾", color = AtColors.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
        if (open) content()
    }
}

@Composable
internal fun SiteDateField(
    valueIso: String,
    onIso: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var text by remember(valueIso) { mutableStateOf(KassaApi.fmtDay(valueIso).ifBlank { valueIso }) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            KassaApi.parseDayInput(it)?.let(onIso)
        },
        label = { Text(label) },
        trailingIcon = { Text("📅", fontSize = 13.sp) },
        singleLine = true,
        modifier = modifier,
        colors = fieldColors(),
    )
}

@Composable
internal fun SiteFilterSelect(
    value: String,
    items: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val current = items.find { it.first == value }?.second ?: items.firstOrNull()?.second.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label.isNotBlank()) {
            Text(label, color = AtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AtColors.panel)
                    .border(1.dp, AtColors.stroke, RoundedCornerShape(12.dp))
                    .clickable { open = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(current, color = AtColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("▾", color = AtColors.muted, fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .background(AtColors.panel),
            ) {
                items.forEach { (key, title) ->
                    val selected = key == value
                    Text(
                        title,
                        color = if (selected) AtColors.accent else AtColors.text,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChange(key)
                                open = false
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SitePeriodDropdown(
    preset: String,
    rangeLabel: String,
    presets: List<Pair<String, String>>,
    onSelectPreset: (String) -> Unit,
    onApplyCustom: (String, String) -> Unit,
    customFrom: String = "",
    customTo: String = "",
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val currentRange = remember(preset, customFrom, customTo, rangeLabel) {
        KassaApi.dateRange(preset, customFrom = customFrom, customTo = customTo)
    }
    var draftFrom by remember(preset, rangeLabel) { mutableStateOf(currentRange.first) }
    var draftTo by remember(preset, rangeLabel) { mutableStateOf(currentRange.second) }
    val title = if (preset == "custom") "Свой период" else presets.find { it.first == preset }?.second ?: KassaApi.dashPeriodLabel(preset)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Период", color = AtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Box {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AtColors.panel)
                    .border(1.dp, AtColors.stroke, RoundedCornerShape(12.dp))
                    .clickable {
                        val (from, to) = KassaApi.dateRange(preset, customFrom = customFrom, customTo = customTo)
                        draftFrom = from
                        draftTo = to
                        open = true
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = AtColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("▾", color = AtColors.muted, fontSize = 12.sp)
                }
                Text(rangeLabel, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                modifier = Modifier
                    .width(320.dp)
                    .heightIn(max = 460.dp)
                    .background(AtColors.panel),
            ) {
                Column(
                    Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Свой период", color = AtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draftFrom,
                            onValueChange = { draftFrom = it },
                            label = { Text("С") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors(),
                        )
                        OutlinedTextField(
                            value = draftTo,
                            onValueChange = { draftTo = it },
                            label = { Text("По") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors(),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (KassaApi.isIsoDay(draftFrom) && KassaApi.isIsoDay(draftTo)) AtColors.accent else AtColors.glass,
                            )
                            .clickable(enabled = KassaApi.isIsoDay(draftFrom) && KassaApi.isIsoDay(draftTo)) {
                                onApplyCustom(draftFrom, draftTo)
                                open = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Применить", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = AtColors.stroke, modifier = Modifier.padding(vertical = 4.dp))
                    Text("Пресеты", color = AtColors.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    presets.forEach { (id, label) ->
                        val selected = preset == id
                        val optionRange = KassaApi.periodRangeLabel(id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AtColors.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable {
                                    onSelectPreset(id)
                                    open = false
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label, color = if (selected) AtColors.accent else AtColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(optionRange, color = AtColors.muted, fontSize = 11.sp)
                            }
                            if (selected) Text("✓", color = AtColors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

internal fun pulseTintKey(label: String): String = when {
    label.contains("Заказ", true) -> "mint"
    label.contains("Оборот", true) -> "teal"
    label.contains("Сумма", true) -> "violet"
    label.contains("Бесплат", true) -> "blue"
    label.contains("чек", true) -> "lavender"
    label.contains("прибыл", true) -> "peach"
    label.contains("комисс", true) -> "peach"
    label.contains("отмен", true) -> "violet"
    else -> "sky"
}

@Composable
@ReadOnlyComposable
internal fun deltaColor(delta: String, invert: Boolean = false): Color {
    val down = delta.startsWith("-")
    val up = delta.startsWith("+")
    val good = if (invert) down else up
    val bad = if (invert) up else down
    return when {
        good -> AtColors.success
        bad -> AtColors.danger
        else -> AtColors.muted
    }
}

private fun pulseTint(key: String, light: Boolean): Pair<Color, Color> {
    val (fill, edge) = when (key) {
        "mint" -> Color(0xFFE7F8EF) to Color(0xFF34D399)
        "teal" -> Color(0xFFE6F6F5) to Color(0xFF2DD4BF)
        "violet" -> Color(0xFFF0E9FF) to Color(0xFFA78BFA)
        "blue" -> Color(0xFFE7F0FF) to Color(0xFF60A5FA)
        "lavender" -> Color(0xFFEEE8FF) to Color(0xFF8B7CFF)
        "peach" -> Color(0xFFFFF0E8) to Color(0xFFFB923C)
        "ink" -> Color(0xFFEEF1F6) to Color(0xFF111827)
        else -> Color(0xFFE8F3FF) to Color(0xFF38BDF8)
    }
    return if (light) fill to edge else edge.copy(alpha = 0.16f) to edge
}

@Composable
internal fun SiteSectionHead(title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        Text(title, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 26.sp)
        if (hint.isNotBlank()) {
            Text(hint, color = AtColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp), lineHeight = 18.sp)
        }
    }
}

@Composable
internal fun SiteOpenTile(
    title: String,
    purpose: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .atCard(12.dp)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AtColors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("›", color = AtColors.muted, fontSize = 18.sp)
        }
        Text(purpose, color = AtColors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SiteSegmented(
    value: String,
    items: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (key, label) ->
            val selected = value == key
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) AtColors.accentSoft else AtColors.panel)
                    .border(
                        1.dp,
                        if (selected) AtColors.accent.copy(alpha = 0.45f) else AtColors.stroke,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable { onChange(key) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) AtColors.accent else AtColors.muted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
