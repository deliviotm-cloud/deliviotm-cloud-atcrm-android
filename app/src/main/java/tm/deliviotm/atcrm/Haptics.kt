package tm.deliviotm.atcrm

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

object Haptics {
    fun tap(ctx: Context) = pulse(ctx, 18, heavy = false)

    fun warn(ctx: Context) = pulse(ctx, 40, heavy = true)

    private fun pulse(ctx: Context, ms: Long, heavy: Boolean) {
        val vibrator = ctx.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 29) {
            val effect = if (heavy) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
