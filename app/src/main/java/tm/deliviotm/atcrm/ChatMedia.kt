package tm.deliviotm.atcrm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ExifInterface
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object ChatMedia {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, Bitmap>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 80
    }

    fun prepareJpeg(ctx: Context, uri: Uri): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxSide = 1600
        val w = bounds.outWidth.coerceAtLeast(1)
        val h = bounds.outHeight.coerceAtLeast(1)
        while (w / sample > maxSide * 2 || h / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("Не удалось прочитать фото")
        val oriented = applyExif(ctx, uri, decoded)
        val scaled = scale(oriented, maxSide)
        if (scaled != oriented) oriented.recycle()
        val out = File(ctx.cacheDir, "chat_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        if (scaled != oriented) scaled.recycle()
        return out
    }

    fun keepForOutbox(ctx: Context, src: File, id: String): File {
        val dir = File(ctx.filesDir, "outbox_photos").apply { mkdirs() }
        val ext = src.extension.ifBlank { "bin" }.lowercase()
        val dest = File(dir, "${id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.$ext")
        if (src.canonicalPath != dest.canonicalPath) src.copyTo(dest, overwrite = true)
        return dest
    }

    fun keepUri(ctx: Context, uri: Uri, id: String): Pair<File, String> {
        val mime = ctx.contentResolver.getType(uri)?.lowercase().orEmpty().ifBlank { "application/octet-stream" }
        val ext = when {
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("pdf") -> "pdf"
            mime.contains("mpeg") || mime.contains("mp4") || mime.contains("m4a") -> "m4a"
            mime.contains("audio") -> "m4a"
            mime.contains("plain") -> "txt"
            else -> "bin"
        }
        val dir = File(ctx.filesDir, "outbox_photos").apply { mkdirs() }
        val dest = File(dir, "${id.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.$ext")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { input.copyTo(it) }
        } ?: error("Не удалось прочитать файл")
        if (dest.length() <= 0L) error("Пустой файл")
        return dest to mime
    }

    fun downloadToFile(ctx: Context, url: String, token: String?): File? {
        if (url.isBlank()) return null
        return try {
            val b = Request.Builder().url(url)
            if (!token.isNullOrBlank()) b.header("Authorization", "Bearer $token")
            val r = http.newCall(b.build()).execute()
            if (!r.isSuccessful) return null
            val ctype = r.header("Content-Type").orEmpty().lowercase()
            val ext = when {
                ctype.contains("webm") -> "webm"
                ctype.contains("ogg") || ctype.contains("opus") -> "ogg"
                ctype.contains("mpeg") || ctype.contains("mp3") -> "mp3"
                ctype.contains("wav") -> "wav"
                ctype.contains("aac") || ctype.contains("mp4") || ctype.contains("m4a") -> "m4a"
                url.contains(".webm") -> "webm"
                url.contains(".ogg") || url.contains(".opus") -> "ogg"
                url.contains(".mp3") -> "mp3"
                url.contains(".aac") -> "aac"
                url.contains(".wav") -> "wav"
                url.contains(".m4a") || url.contains("/workspace/files/") -> "m4a"
                else -> "m4a"
            }
            val out = File(ctx.cacheDir, "play_${url.hashCode().toUInt()}.$ext")
            r.body?.byteStream()?.use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            } ?: return null
            if (out.length() <= 0L) null else out
        } catch (_: Exception) {
            null
        }
    }

    fun deleteOutboxFile(path: String) {
        if (path.isBlank()) return
        try {
            File(path).delete()
        } catch (_: Exception) {
        }
    }

    private fun applyExif(ctx: Context, uri: Uri, src: Bitmap): Bitmap {
        val orientation = try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return src
        }
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated != src) src.recycle()
        return rotated
    }

    fun fetchBitmap(url: String, token: String?): Bitmap? {
        if (url.isBlank()) return null
        synchronized(cacheLock) {
            cache[url]?.let { if (!it.isRecycled) return it }
        }
        return try {
            val b = Request.Builder().url(url)
            if (!token.isNullOrBlank()) b.header("Authorization", "Bearer $token")
            val r = http.newCall(b.build()).execute()
            if (!r.isSuccessful) return null
            val bmp = r.body?.byteStream()?.use { BitmapFactory.decodeStream(it) } ?: return null
            synchronized(cacheLock) { cache[url] = bmp }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun beep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 400)
        } catch (_: Exception) {
        }
    }

    private fun scale(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = maxOf(w, h)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1), true)
    }
}
