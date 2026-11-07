package com.openweibo.app.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class VideoSaver(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun hasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun saveVideo(
        stream: VideoStream,
        title: String,
        cookies: String,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        if (!hasStoragePermission()) {
            throw IOException("没有存储权限，无法保存视频")
        }
        if (stream.isHls) {
            throw IOException("HLS 流暂不支持直接下载，请切换到 MP4 清晰度")
        }
        val url = stream.url.trim()
        if (url.isBlank()) throw IOException("微博没有返回可下载的视频地址")

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://weibo.com/")
            .apply {
                if (cookies.isNotBlank()) {
                    header("Cookie", cookies.replace("\r", "").replace("\n", ""))
                }
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载视频失败 (${response.code})")
            }
            val body = response.body ?: throw IOException("视频内容为空")
            val contentType = body.contentType()?.toString()?.lowercase(Locale.US).orEmpty()
            if (contentType.contains("mpegurl") || contentType.startsWith("text/")) {
                throw IOException("当前清晰度是 HLS 流，暂不支持直接保存")
            }
            val fileName = buildFileName(title, stream)
            val total = body.contentLength()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(
                    fileName = fileName,
                    mime = if (contentType.contains("webm")) "video/webm" else "video/mp4",
                    input = body.byteStream(),
                    total = total,
                    onProgress = onProgress
                )
            } else {
                @Suppress("DEPRECATION")
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val folder = File(base, "OpenWeibo").apply { mkdirs() }
                val file = uniqueFile(folder, fileName)
                file.outputStream().use { output ->
                    body.byteStream().use { input ->
                        copyWithProgress(input, output, total, onProgress)
                    }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
            }
            fileName
        }
    }

    private fun saveToMediaStore(
        fileName: String,
        mime: String,
        input: InputStream,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/OpenWeibo"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri: Uri = resolver.insert(collection, values)
            ?: throw IOException("系统媒体库无法创建视频")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                input.use { source ->
                    copyWithProgress(source, output, total, onProgress)
                }
            } ?: throw IOException("系统媒体库无法写入视频")
            val done = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        total: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val buffer = ByteArray(64 * 1024)
        var downloaded = 0L
        var lastReported = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            if (downloaded - lastReported >= 512 * 1024) {
                onProgress(downloaded, total)
                lastReported = downloaded
            }
        }
        output.flush()
        onProgress(downloaded, total)
    }

    private fun buildFileName(title: String, stream: VideoStream): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeTitle = title
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(26)
            .ifBlank { "weibo_video" }
        val quality = stream.displayLabel
            .filter { it.isLetterOrDigit() }
            .take(8)
            .ifBlank { "AUTO" }
        val millis = System.currentTimeMillis().toString().takeLast(4)
        return "OpenWeibo_${safeTitle}_${quality}_${stamp}_$millis.mp4"
    }

    private fun uniqueFile(folder: File, name: String): File {
        var candidate = File(folder, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var count = 2
        while (candidate.exists()) {
            candidate = File(folder, "${base}_$count$extension")
            count++
        }
        return candidate
    }

    companion object {
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
