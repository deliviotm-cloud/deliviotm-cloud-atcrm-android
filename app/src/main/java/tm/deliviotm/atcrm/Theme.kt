package tm.deliviotm.atcrm

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AtPalette(
    val bgDeep: Color,
    val bgBase: Color,
    val panel: Color,
    val glass: Color,
    val stroke: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
) {
    companion object {
        val Dark = AtPalette(
            bgDeep = Color(0xFF0B0F17),
            bgBase = Color(0xFF0E1420),
            panel = Color(0xE6141B28),
            glass = Color(0xB8121826),
            stroke = Color(0x3394A3B8),
            text = Color(0xFFDBE4EE),
            muted = Color(0xFF8B9CB3),
            accent = Color(0xFF4B8DF8),
            accentSoft = Color(0x1A3B82F6),
            danger = Color(0xFFF87171),
            success = Color(0xFF4ADE80),
            warning = Color(0xFFFB923C),
        )
        /** Matches crm.deliviotm.com mobile web: off-white canvas, white cards, periwinkle accents. */
        val Light = AtPalette(
            bgDeep = Color(0xFFF3F4F8),
            bgBase = Color(0xFFEDEFF5),
            panel = Color(0xFFFFFFFF),
            glass = Color(0xFFF7F8FC),
            stroke = Color(0x140F172A),
            text = Color(0xFF111827),
            muted = Color(0xFF6B7280),
            accent = Color(0xFF5B7CFA),
            accentSoft = Color(0xFFE8EEFE),
            danger = Color(0xFFE11D48),
            success = Color(0xFF059669),
            warning = Color(0xFFD97706),
        )
    }
}

internal val LocalAtPalette = staticCompositionLocalOf { AtPalette.Light }
internal val LocalToggleTheme = staticCompositionLocalOf { {} }

internal val AtPalette.isDark: Boolean
    get() = bgDeep.red < 0.45f && bgDeep.green < 0.45f

object AtColors {
    val bgDeep: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.bgDeep
    val bgBase: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.bgBase
    val panel: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.panel
    val glass: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.glass
    val stroke: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.stroke
    val text: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.text
    val muted: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.muted
    val accent: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.accent
    val accentSoft: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.accentSoft
    val danger: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.danger
    val success: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.success
    val warning: Color @Composable @ReadOnlyComposable get() = LocalAtPalette.current.warning
}

private val AtDarkScheme = darkColorScheme(
    primary = AtPalette.Dark.accent,
    onPrimary = Color.White,
    background = AtPalette.Dark.bgDeep,
    onBackground = AtPalette.Dark.text,
    surface = AtPalette.Dark.panel,
    onSurface = AtPalette.Dark.text,
    surfaceVariant = AtPalette.Dark.glass,
    onSurfaceVariant = AtPalette.Dark.muted,
    error = AtPalette.Dark.danger,
    outline = AtPalette.Dark.stroke,
)

private val AtLightScheme = lightColorScheme(
    primary = AtPalette.Light.accent,
    onPrimary = Color.White,
    background = AtPalette.Light.bgDeep,
    onBackground = AtPalette.Light.text,
    surface = AtPalette.Light.panel,
    onSurface = AtPalette.Light.text,
    surfaceVariant = AtPalette.Light.glass,
    onSurfaceVariant = AtPalette.Light.muted,
    error = AtPalette.Light.danger,
    outline = AtPalette.Light.stroke,
)

@Composable
fun AtAppTheme(mode: String, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val light = when (mode) {
        "light" -> true
        "dark" -> false
        else -> !systemDark
    }
    val palette = if (light) AtPalette.Light else AtPalette.Dark
    CompositionLocalProvider(LocalAtPalette provides palette) {
        MaterialTheme(
            colorScheme = if (light) AtLightScheme else AtDarkScheme,
            typography = Typography(),
            content = content,
        )
    }
}

@Composable
fun Modifier.atCard(radius: Dp = 12.dp, color: Color = LocalAtPalette.current.panel): Modifier = this
    .shadow(
        elevation = 2.dp,
        shape = RoundedCornerShape(radius),
        clip = false,
        ambientColor = Color(0x080F172A),
        spotColor = Color(0x0D0F172A),
    )
    .clip(RoundedCornerShape(radius))
    .background(color)
    .border(0.5.dp, LocalAtPalette.current.stroke, RoundedCornerShape(radius))

fun prettyStatus(raw: String): String {
    val s = raw.trim()
    val l = s.lowercase().replace(' ', '_').replace('-', '_')
    return when {
        l.isBlank() -> "В работе"
        l == "pending_review" || l == "pendingreview" -> "На проверке"
        l == "created" || l == "confirmed" -> "Подтверждён"
        l.contains("cancel") || l.contains("отмен") -> "Отменён"
        l.contains("problem") || l.contains("проблем") -> "Проблема"
        l.contains("progress") || l.contains("in_work") || l.contains("взят") -> "В работе"
        l.contains("done") || l.contains("complete") || l.contains("готов") || l.contains("closed") -> "Готово"
        l.contains("wait") || l.contains("hold") || l.contains("ждет") -> "Ждёт"
        l.contains("whats") -> "WhatsApp"
        l.contains("telegram") -> "Telegram"
        l.contains("direct") -> "Личный"
        s.any { it.isDigit() } && s.length > 8 -> s
        l.any { it in 'а'..'я' } -> s.replaceFirstChar { it.uppercase() }
        else -> s.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

@Composable
@ReadOnlyComposable
fun statusTint(raw: String): Color {
    val s = raw.lowercase()
    return when {
        s.contains("cancel") || s.contains("fail") || s.contains("error") || s.contains("problem") || s.contains("ошиб") || s.contains("проблем") || s.contains("отмен") -> AtColors.danger
        s.contains("pending_review") || s.contains("pending") || s.contains("wait") || s.contains("hold") || s.contains("ждет") -> AtColors.warning
        s.contains("created") || s.contains("done") || s.contains("ok") || s.contains("success") || s.contains("paid") || s.contains("delivered") || s.contains("подтвержд") -> AtColors.success
        else -> AtColors.accent
    }
}
