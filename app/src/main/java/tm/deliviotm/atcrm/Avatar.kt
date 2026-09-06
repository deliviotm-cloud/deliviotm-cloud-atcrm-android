package tm.deliviotm.atcrm

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun UserAvatar(
    name: String,
    avatarUrl: String,
    api: KassaApi? = null,
    token: String? = null,
    size: Dp = 40.dp,
    round: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val resolved = remember(avatarUrl, api) {
        when {
            avatarUrl.isBlank() -> ""
            api != null -> api.resolveMedia(avatarUrl)
            else -> avatarUrl
        }
    }
    var bmp by remember(resolved, token) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(resolved, token) {
        bmp = if (resolved.isBlank()) null
        else withContext(Dispatchers.IO) { ChatMedia.fetchBitmap(resolved, token) }
    }
    val shape = if (round) CircleShape else RoundedCornerShape(12.dp)
    val initials = KassaApi.chatInitials(name.ifBlank { "A" }).ifBlank { name.take(1).uppercase() }
    Box(
        modifier.size(size).clip(shape).background(AtColors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        val photo = bmp
        if (photo != null && !photo.isRecycled) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                initials,
                color = AtColors.accent,
                fontWeight = FontWeight.Bold,
                fontSize = when {
                    size >= 64.dp -> 22.sp
                    size >= 40.dp -> 14.sp
                    else -> 11.sp
                },
            )
        }
    }
}
