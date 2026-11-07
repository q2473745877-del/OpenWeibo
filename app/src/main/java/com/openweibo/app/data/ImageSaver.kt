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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ImageSaver(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    fun hasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun saveImages(
        images: List<WeiboImage>,
        postId: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): SaveResult = withContext(Dispatchers.IO) {
        val uniqueImages = images.distinctBy { it.originalUrl }
        var saved = 0
        var failed = 0
        var firstError: String? = null
        uniqueImages.forEachIndexed { index, image ->
            try {
                saveOne(image, postId, index)
                saved++
            } catch (error: Exception) {
                failed++
                if (firstError == null) {
                    firstError = error.message ?: "保存失败"
                }
            } finally {
                onProgress(index + 1, uniqueImages.size)
            }
        }
        SaveResult(saved = saved, failed = failed, firstError = firstError)
    }

    private fun saveOne(image: WeiboImage, postId: String, index: Int) {
        if (!hasStoragePermission()) {
            throw IOException("没有存储权限，无法保存图片")
        }
        val request = Request.Builder()
            .url(image.originalUrl)
            .get()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .header("Referer", "https://weibo.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载原图失败 (${response.code})")
            }
            val body = response.body ?: throw IOException("图片内容为空")
            val contentType = body.contentType()?.toString()?.lowercase(Locale.US)
            val extension = extensionFor(image.originalUrl, contentType)
            val mime = mimeFor(extension)
            val fileName = fileName(postId, index, extension)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToMediaStore(fileName, mime, body.byteStream())
            } else {
                @Suppress("DEPRECATION")
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val folder = File(base, "OpenWeibo").apply { mkdirs() }
                val file = uniqueFile(folder, fileName)
                file.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf(mime),
                    null
                )
            }
        }
    }

    private fun saveToMediaStore(fileName: String, mime: String, input: java.io.InputStream) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/OpenWeibo"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri: Uri = resolver.insert(collection, values)
            ?: throw IOException("系统相册无法创建图片")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                input.use { source -> source.copyTo(output) }
            } ?: throw IOException("系统相册无法写入图片")
            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
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

    private fun fileName(postId: String, index: Int, extension: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safePost = postId.filter { it.isLetterOrDigit() }.takeLast(14).ifBlank { "weibo" }
        val millis = System.currentTimeMillis().toString().takeLast(4)
        return "OpenWeibo_${safePost}_${index + 1}_${stamp}_$millis.$extension"
    }

    private fun extensionFor(url: String, contentType: String?): String {
        return when {
            contentType?.contains("png") == true -> "png"
            contentType?.contains("gif") == true -> "gif"
            contentType?.contains("webp") == true -> "webp"
            contentType?.contains("jpeg") == true || contentType?.contains("jpg") == true -> "jpg"
            url.substringBefore('?').endsWith(".png", ignoreCase = true) -> "png"
            url.substringBefore('?').endsWith(".gif", ignoreCase = true) -> "gif"
            url.substringBefore('?').endsWith(".webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
    }

    private fun mimeFor(extension: String): String {
        return when (extension) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
