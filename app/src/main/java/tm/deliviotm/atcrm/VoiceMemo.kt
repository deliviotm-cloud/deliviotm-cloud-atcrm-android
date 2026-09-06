package tm.deliviotm.atcrm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceMemo(private val ctx: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    var recording: Boolean = false
        private set
    var playing: Boolean = false
        private set

    fun start(file: File) {
        stopPlay()
        stopRecord()
        if (file.exists()) file.delete()
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(ctx)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioEncodingBitRate(96_000)
        rec.setAudioSamplingRate(44_100)
        rec.setOutputFile(file.absolutePath)
        rec.prepare()
        rec.start()
        recorder = rec
        recording = true
    }

    fun stopRecord() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        recording = false
    }

    fun durationMs(file: File): Int {
        if (!file.exists()) return 0
        return try {
            val p = MediaPlayer()
            p.setDataSource(file.absolutePath)
            p.prepare()
            val d = p.duration
            p.release()
            d.coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
    }

    fun positionMs(): Int = try { player?.currentPosition ?: 0 } catch (_: Exception) { 0 }

    fun play(file: File, onDone: () -> Unit) {
        stopPlay()
        if (!file.exists()) return
        val p = MediaPlayer()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
        }
        p.setDataSource(file.absolutePath)
        p.setOnCompletionListener {
            stopPlay()
            onDone()
        }
        p.prepare()
        p.start()
        player = p
        playing = true
    }

    fun stopPlay() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        playing = false
    }

    fun release() {
        stopRecord()
        stopPlay()
    }
}
