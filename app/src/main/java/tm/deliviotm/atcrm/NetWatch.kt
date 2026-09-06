package tm.deliviotm.atcrm

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

object NetWatch {
    fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Composable
    fun rememberOnline(): Boolean {
        val ctx = LocalContext.current
        var online by remember { mutableStateOf(online(ctx)) }
        DisposableEffect(ctx) {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            if (cm == null) {
                return@DisposableEffect onDispose { }
            }
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    online = true
                }
                override fun onLost(network: Network) {
                    online = online(ctx)
                }
            }
            try {
                cm.registerDefaultNetworkCallback(cb)
            } catch (_: Exception) {
            }
            onDispose {
                try {
                    cm.unregisterNetworkCallback(cb)
                } catch (_: Exception) {
                }
            }
        }
        return online
    }
}
