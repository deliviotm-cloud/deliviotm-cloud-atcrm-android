package tm.deliviotm.atcrm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileMePane(
    api: KassaApi,
    token: String?,
    onBack: () -> Unit,
    onUserUpdated: (AppUser) -> Unit,
    embedded: Boolean = false,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    var obj by remember { mutableStateOf<JSONObject?>(null) }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    fun applyUser(o: JSONObject) {
        obj = o
        val user = o.optJSONObject("user") ?: o
        fullName = KassaApi.pick(user, "fullName", "username")
        phone = KassaApi.pick(user, "phone", "mobile")
        email = KassaApi.pick(user, "email", "personalEmail")
        avatarUrl = KassaApi.avatarUrlOf(user)
        val parsed = try {
            // parse via /auth/me shape
            api.me(token ?: return)
        } catch (_: Exception) {
            null
        }
        parsed?.let(onUserUpdated)
    }

    fun reload() {
        val t = token ?: return
        scope.launch {
            if (loaded) refreshing = true
            err = null
            try {
                val o = withContext(Dispatchers.IO) { api.getObject("/auth/me", t) }
                obj = o
                val user = o.optJSONObject("user") ?: o
                fullName = KassaApi.pick(user, "fullName", "username")
                phone = KassaApi.pick(user, "phone", "mobile")
                email = KassaApi.pick(user, "email", "personalEmail")
                avatarUrl = KassaApi.avatarUrlOf(user)
                val me = withContext(Dispatchers.IO) { api.me(t) }
                onUserUpdated(me)
                loaded = true
            } catch (e: Exception) {
                err = e.message ?: "Не удалось загрузить профиль"
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(token, tick) {
        if (!token.isNullOrBlank()) reload()
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val t = token
        if (uri == null || t.isNullOrBlank()) return@rememberLauncherForActivityResult
        busy = true
        err = null
        msg = null
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { ChatMedia.prepareJpeg(ctx, uri) }
                val uploaded = withContext(Dispatchers.IO) {
                    api.uploadMultipart("/auth/me/avatar", t, file, "file", "image/jpeg")
                }
                val userObj = uploaded.optJSONObject("user") ?: uploaded
                avatarUrl = KassaApi.avatarUrlOf(userObj).ifBlank { avatarUrl }
                val me = withContext(Dispatchers.IO) { api.me(t) }
                onUserUpdated(me)
                msg = "Фото профиля обновлено"
                tick++
            } catch (e: Exception) {
                err = e.message ?: "Не удалось загрузить фото"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(if (embedded) Color.Transparent else AtColors.bgDeep)) {
        if (!embedded) TopLine("Мой профиль", onBack, onRefresh = { tick++ })
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { tick++ },
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!embedded) item { SiteSectionHead("Мой профиль", "Фото, контакты и смена пароля") }
                if (err != null) item { ActionBanner(err!!, error = true) }
                if (msg != null) item { ActionBanner(msg!!, error = false) }
                if (!loaded && err == null) item { LoadingCard() }

                item {
                    Row(
                        Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        UserAvatar(
                            name = fullName.ifBlank { "A" },
                            avatarUrl = avatarUrl,
                            api = api,
                            token = token,
                            size = 72.dp,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(fullName.ifBlank { "—" }, color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            val uname = obj?.optJSONObject("user")?.let { KassaApi.pick(it, "username") }
                                ?: obj?.let { KassaApi.pick(it, "username") }.orEmpty()
                            if (uname.isNotBlank()) Text("@$uname", color = AtColors.muted, fontSize = 13.sp)
                            Text(
                                if (busy) "Загрузка…" else "Загрузить фото",
                                color = AtColors.accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !busy) { pickPhoto.launch("image/*") }
                                    .padding(vertical = 4.dp),
                            )
                            Text("JPEG / PNG / WebP / GIF, как на сайте", color = AtColors.muted, fontSize = 11.sp)
                        }
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Данные", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        OutlinedTextField(fullName, { fullName = it }, label = { Text("ФИО") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(phone, { phone = it }, label = { Text("Телефон") }, placeholder = { Text("+993…") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        OutlinedTextField(email, { email = it }, label = { Text("Личная почта") }, placeholder = { Text("name@example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AtColors.accent)
                                .clickable(enabled = !busy) {
                                    val t = token ?: return@clickable
                                    busy = true
                                    err = null
                                    msg = null
                                    scope.launch {
                                        try {
                                            val body = JSONObject()
                                                .put("fullName", fullName.trim())
                                                .put("phone", phone.trim())
                                                .put("email", email.trim())
                                            withContext(Dispatchers.IO) { api.patchJson("/auth/me", t, body) }
                                            val me = withContext(Dispatchers.IO) { api.me(t) }
                                            onUserUpdated(me)
                                            msg = "Профиль сохранён"
                                            tick++
                                        } catch (e: Exception) {
                                            err = e.message ?: "Не удалось сохранить"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (busy) "Сохранение…" else "Сохранить", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().atCard(16.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Смена пароля", color = AtColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Свой пароль меняете здесь. Админ может сбросить чужой пароль в «Настройки → Пользователи».",
                            color = AtColors.muted,
                            fontSize = 12.sp,
                        )
                        OutlinedTextField(
                            currentPass, { currentPass = it }, label = { Text("Текущий пароль") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        )
                        OutlinedTextField(
                            newPass, { newPass = it }, label = { Text("Новый пароль") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        )
                        OutlinedTextField(
                            confirmPass, { confirmPass = it }, label = { Text("Повтор нового пароля") },
                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AtColors.panel)
                                .clickable(enabled = !busy) {
                                    if (newPass != confirmPass) {
                                        err = "Пароли не совпадают"
                                        return@clickable
                                    }
                                    if (newPass.length < 6) {
                                        err = "Пароль не короче 6 символов"
                                        return@clickable
                                    }
                                    val t = token ?: return@clickable
                                    busy = true
                                    err = null
                                    msg = null
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                api.postJson(
                                                    "/auth/me/password",
                                                    t,
                                                    JSONObject().put("currentPassword", currentPass).put("newPassword", newPass),
                                                )
                                            }
                                            currentPass = ""
                                            newPass = ""
                                            confirmPass = ""
                                            msg = "Пароль изменён"
                                        } catch (e: Exception) {
                                            err = e.message ?: "Не удалось сменить пароль"
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (busy) "…" else "Сменить пароль", color = AtColors.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
