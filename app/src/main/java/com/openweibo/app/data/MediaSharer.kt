package com.openweibo.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Downloads media into the app cache and hands it to other apps.
 *
 * Sharing is intentionally explicit: the system chooser plus direct targets for
 * QQ, WeChat and the Weibo client, matching what the user asked for.
 */
class MediaSharer(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    private val cacheDir: File
        get() = File(context.cacheDir, "shared").apply { mkdirs() }

    /** Downloads [url] into the share cache. Existing files are reused by name. */
    suspend fun downloadForShare(url: String, suggestedName: String): File =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) throw IOException("图片地址为空")
            val cache = cacheDir
            pruneOldFiles(cache)
            val target = uniqueFile(cache, sanitize(suggestedName))
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Referer", "https://weibo.com/")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("下载图片失败 (${response.code})")
                }
                val body = response.body ?: throw IOException("图片内容为空")
                target.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            if (target.length() <= 0L) {
                target.delete()
                throw IOException("下载的图片为空")
            }
            target
        }

    /** Generic system share sheet with image / GIF / video mime detection. */
    fun shareToSystem(file: File, caption: String? = null) {
        val uri = uriFor(file)
        val mime = mimeFor(file)
        val intent = buildSendIntent(uri, mime, caption)
            .setAction(Intent.ACTION_SEND)
        launch(intent, clip(file, mime))
    }

    /**
     * Direct share targets. Returns false when the target app is not installed so the
     * caller can tell the user instead of silently doing nothing.
     */
    fun shareTo(target: ShareTarget, file: File, caption: String? = null): Boolean {
        val uri = uriFor(file)
        val mime = mimeFor(file)
        val base = buildSendIntent(uri, mime, caption)
        val intent = when (target) {
            ShareTarget.QQ -> base.setPackage(PACKAGE_QQ)
            ShareTarget.WECHAT -> base.setPackage(PACKAGE_WECHAT)
            ShareTarget.WEIBO -> base.setPackage(PACKAGE_WEIBO)
        }
        if (!isInstalled(target)) return false
        return launch(intent, clip(file, mime))
    }

    fun isInstalled(target: ShareTarget): Boolean {
        val packages = when (target) {
            ShareTarget.QQ -> listOf(PACKAGE_QQ, PACKAGE_QQ_TIM)
            ShareTarget.WECHAT -> listOf(PACKAGE_WECHAT, PACKAGE_WECHAT_MOMENTS_ONLY)
            ShareTarget.WEIBO -> listOf(PACKAGE_WEIBO)
        }
        return packages.any { installed(it) }
    }

    /** Installed share targets, used to only offer what the device can actually handle. */
    fun availableTargets(): List<ShareTarget> =
        ShareTarget.entries.filter { isInstalled(it) }

    private fun installed(pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun buildSendIntent(uri: Uri, mime: String, caption: String?): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!caption.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, caption)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }

    private fun clip(file: File, mime: String): ClipData =
        ClipData.newUri(context.contentResolver, file.name, uriFor(file))

    private fun launch(intent: Intent, clipData: ClipData): Boolean = try {
        intent.clipData = clipData
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
        true
    } catch (_: Exception) {
        false
    }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    private fun mimeFor(file: File): String = when (file.extension.lowercase(Locale.US)) {
        "gif" -> "image/gif"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "m4v" -> "video/x-m4v"
        else -> "image/jpeg"
    }

    private fun sanitize(name: String): String {
        val cleaned = name.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        return cleaned.ifBlank { "openweibo_share.jpg" }
    }

    private fun uniqueFile(folder: File, name: String): File {
        val candidate = File(folder, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 2
        var next = candidate
        while (next.exists()) {
            next = File(folder, "${base}_$index$ext")
            index++
        }
        return next
    }

    /** Keeps the share cache bounded; shares are one-shot so old files are disposable. */
    private fun pruneOldFiles(folder: File) {
        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS
        folder.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
        const val PACKAGE_QQ = "com.tencent.mobileqq"
        const val PACKAGE_QQ_TIM = "com.tencent.tim"
        const val PACKAGE_WECHAT = "com.tencent.mm"
        const val PACKAGE_WECHAT_MOMENTS_ONLY = "com.tencent.mm"
        const val PACKAGE_WEIBO = "com.sina.weibo"
    }
}

enum class ShareTarget(val label: String) {
    QQ("QQ"),
    WECHAT("微信"),
    WEIBO("微博好友")
}
