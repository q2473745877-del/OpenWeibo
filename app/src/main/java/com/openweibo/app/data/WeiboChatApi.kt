package com.openweibo.app.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Direct messages ("私信").
 *
 * Protocol verified against a real logged-in session (mobile web):
 *
 *  - Conversation list: `GET m.weibo.cn/message/msglist`
 *      -> `{ok:1, data:[{user:{id,screen_name,avatar_large,...}, created_at,
 *          scheme:"/message/chat?uid=...", unread, text}]}`
 *  - History: `GET m.weibo.cn/api/chat/list?count=10&uid=<peer>&unfollowing=0`
 *      -> `{ok:1, data:{msgs:[{created_at, dm_type, id, text, media_type,
 *          sender_id, sender_screen_name, recipient_id, ...}],
 *          users:{uid:{...}}, total_number, title, last_read_mid}}`
 *  - Older page: same URL plus `&since_id=<oldest msg id>` (Weibo pages backwards).
 *  - Send: `POST m.weibo.cn/api/chat/send` with form fields `uid` + `content`
 *      **and the `st` token from /api/config**; without `st` the server answers
 *      `errno:100006 请求非法`.
 *
 * Everything runs on the mobile session (m.weibo.cn cookies).
 */
class WeiboChatApi(
    private val contentResolver: ContentResolver,
    private val json: Json
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    /** `st` is required for write calls; cache it per call chain to avoid refetching. */
    suspend fun writeToken(session: Session): String = withContext(Dispatchers.IO) {
        val root = get("https://m.weibo.cn/api/config", session.cookies)
        val data = (root as? JsonObject)?.get("data").asObjOrNull()
        val login = data?.str("login").equals("true", ignoreCase = true) ||
            data?.str("login") == "1"
        val uid = data?.str("uid").orEmpty()
        if (!login && uid.isBlank()) throw AuthenticationException()
        val token = data?.str("st").orEmpty()
        if (token.isBlank()) throw AuthenticationException("登录状态不完整，请重新登录")
        token
    }

    // ---------------------------------------------------------------- conversations

    suspend fun fetchConversations(session: Session, page: Int = 1): List<ChatConversation> =
        withContext(Dispatchers.IO) {
            // The list endpoint has no paging; it returns all recent threads.
            val root = get("https://m.weibo.cn/message/msglist", session.cookies)
            val objectValue = root as? JsonObject
                ?: throw WeiboApiException(-1, "私信列表返回了无法识别的数据")
            ensureOk(objectValue, "私信列表加载失败")
            val list = objectValue["data"].asArr()
            list.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val user = item["user"].asObjOrNull() ?: return@mapNotNull null
                val uid = user.str("id")
                if (uid.isBlank()) return@mapNotNull null
                ChatConversation(
                    id = uid,
                    uid = uid,
                    screenName = user.str("screen_name", "name").ifBlank { "微博用户" },
                    avatarUrl = user.str("avatar_large", "profile_image_url", "avatar_hd"),
                    lastMessage = item.str("text"),
                    updatedAtMillis = parseFlexibleTime(item.str("created_at")),
                    unreadCount = item.str("unread").toIntOrNull() ?: 0
                )
            }
        }

    // -------------------------------------------------------------------- messages

    suspend fun fetchMessages(
        session: Session,
        conversationUid: String,
        sinceId: String? = null
    ): ChatPage = withContext(Dispatchers.IO) {
        if (conversationUid.isBlank()) throw WeiboApiException(-1, "无法识别私信对象")
        val url = buildString {
            append("https://m.weibo.cn/api/chat/list?count=10")
            append("&uid=").append(conversationUid)
            append("&unfollowing=0")
            if (!sinceId.isNullOrBlank()) append("&since_id=").append(sinceId)
        }
        val root = get(url, session.cookies)
        val objectValue = root as? JsonObject
            ?: throw WeiboApiException(-1, "私信返回了无法识别的数据")
        ensureOk(objectValue, "私信加载失败")
        val data = objectValue["data"].asObjOrNull()
        val msgs = data?.get("msgs").asArr() ?: emptyList()
        val selfUid = data?.get("users").asObjOrNull()
            ?.keys?.firstOrNull { it == myUidFromSession(session) }
            .orEmpty()
        val messages = msgs.mapNotNull { parseMessage(it as? JsonObject, selfUid) }
        // `since_id` pages *backwards*: older messages come in the same shape.
        ChatPage(
            messages = messages,
            nextCursor = messages.firstOrNull { it.id.isNotBlank() }?.id,
            peerTitle = data?.str("title").orEmpty()
        )
    }

    private fun myUidFromSession(session: Session): String = session.uid

    /** Sends a text message. Returns the echoed message when Weibo provides one. */
    suspend fun sendText(
        session: Session,
        conversationUid: String,
        text: String,
        token: String
    ): ChatMessage? = withContext(Dispatchers.IO) {
        val body = text.trim()
        if (body.isEmpty()) throw WeiboApiException(-1, "私信内容不能为空")
        if (conversationUid.isBlank()) throw WeiboApiException(-1, "无法识别私信对象")
        val root = postForm(
            url = "https://m.weibo.cn/api/chat/send",
            cookies = session.cookies,
            fields = listOf(
                "uid" to conversationUid,
                "content" to body,
                "st" to token
            ),
            referer = "https://m.weibo.cn/message/chat?uid=$conversationUid"
        )
        val objectValue = root as? JsonObject
            ?: throw WeiboApiException(-1, "私信发送失败，请重试")
        ensureOk(objectValue, "私信发送失败，请重试")
        val data = objectValue["data"].asObjOrNull()
        val first = data?.get("msgs").asArr()?.firstOrNull()?.asObjOrNull()
        first?.let { parseMessage(it, session.uid) }
    }

    /**
     * Uploads local media and sends it as a DM.
     *
     * Images and GIFs go through the mobile picture upload endpoint that the
     * composer already uses; videos reuse the public dispatch pipeline.
     */
    suspend fun uploadChatImage(
        session: Session,
        uri: Uri,
        fileName: String,
        mimeType: String,
        token: String
    ): String = withContext(Dispatchers.IO) {
        val bytes = readBytes(uri)
        if (bytes.isEmpty()) throw WeiboApiException(-1, "图片内容为空，请重新选择")
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw WeiboApiException(-1, "图片超过 10MB，请更换")
        }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("type", "json")
            .addFormDataPart("_spr", "screen:1080x2340")
            .addFormDataPart("st", token)
            .addFormDataPart(
                "pic",
                fileName.ifBlank { "image.jpg" },
                bytes.toRequestBody(mimeType.ifBlank { "image/jpeg" }.toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url("https://m.weibo.cn/api/statuses/uploadPic")
            .post(multipart)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://m.weibo.cn/compose/")
            .header("Origin", "https://m.weibo.cn")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("MWeibo-Pwa", "1")
            .header("Cookie", cookieWithXsrf(session.cookies, token))
            .header("X-XSRF-TOKEN", token)
            .build()
        val root = execute(request)
        val objectValue = root as? JsonObject
            ?: throw WeiboApiException(-1, "图片上传失败，请重试")
        val ok = objectValue["ok"].asIntOrNull()
        if (ok != null && ok != 1) {
            val msg = objectValue.str("msg", "message")
            if (msg.contains("登录")) throw AuthenticationException(msg)
            throw WeiboApiException(ok, msg.ifBlank { "图片上传失败，请重试" })
        }
        val data = objectValue["data"].asObjOrNull()
        data?.str("pic_id", "pid").orEmpty().ifBlank {
            objectValue.str("pic_id", "pid")
        }.ifBlank { throw WeiboApiException(-1, "图片上传失败，请重试") }
    }

    /** Sends an already-uploaded picture (or video) as a DM. */
    suspend fun sendMedia(
        session: Session,
        conversationUid: String,
        picId: String?,
        mediaId: String?,
        token: String,
        caption: String = ""
    ): ChatMessage? = withContext(Dispatchers.IO) {
        if (conversationUid.isBlank()) throw WeiboApiException(-1, "无法识别私信对象")
        if (picId.isNullOrBlank() && mediaId.isNullOrBlank()) {
            throw WeiboApiException(-1, "没有可发送的媒体")
        }
        val fields = mutableListOf(
            "uid" to conversationUid,
            "st" to token
        )
        if (caption.isNotBlank()) fields += "content" to caption
        if (!picId.isNullOrBlank()) fields += "pic_id" to picId
        if (!mediaId.isNullOrBlank()) {
            fields += "media_id" to mediaId
            fields += "media_type" to "video"
        }
        val root = postForm(
            url = "https://m.weibo.cn/api/chat/send",
            cookies = session.cookies,
            fields = fields,
            referer = "https://m.weibo.cn/message/chat?uid=$conversationUid"
        )
        val objectValue = root as? JsonObject
            ?: throw WeiboApiException(-1, "私信发送失败，请重试")
        ensureOk(objectValue, "私信发送失败，请重试")
        objectValue["data"].asObjOrNull()
            ?.get("msgs").asArr()?.firstOrNull()?.asObjOrNull()
            ?.let { parseMessage(it, session.uid) }
    }

    // ------------------------------------------------------------------- internals

    private fun readBytes(uri: Uri): ByteArray =
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw WeiboApiException(-1, "无法读取所选文件")

    private fun get(url: String, cookies: String): JsonElement {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://m.weibo.cn/message")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("MWeibo-Pwa", "1")
            .header("Cookie", cookies)
            .build()
        return execute(request)
    }

    private fun postForm(
        url: String,
        cookies: String,
        fields: List<Pair<String, String>>,
        referer: String
    ): JsonElement {
        val body = FormBody.Builder().apply {
            fields.forEach { (k, v) -> add(k, v) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", referer)
            .header("Origin", "https://m.weibo.cn")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("MWeibo-Pwa", "1")
            .header("Cookie", cookies)
            .build()
        return execute(request)
    }

    private fun execute(request: Request): JsonElement {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw WeiboApiException(-1, networkMessage(error), error)
        }
        response.use {
            val text = try {
                it.body?.string().orEmpty()
            } catch (error: IOException) {
                throw WeiboApiException(-1, "读取私信数据失败，请重试", error)
            }
            if (it.request.url.host.contains("passport") || text.contains("Sina Visitor System")) {
                throw AuthenticationException()
            }
            if (!it.isSuccessful && it.code != 403) {
                val message = when (it.code) {
                    429, 418 -> "请求过于频繁，请稍后再试"
                    in 500..599 -> "微博服务器暂时不可用 (${it.code})"
                    else -> "私信接口请求失败 (${it.code})"
                }
                throw WeiboApiException(it.code, message)
            }
            if (text.isBlank()) throw WeiboApiException(-1, "微博返回了空数据")
            val element = try {
                json.parseToJsonElement(text)
            } catch (error: Exception) {
                throw WeiboApiException(-1, "微博返回了无法识别的数据", error)
            }
            // Weibo answers `errno` for logical failures even with HTTP 200/403.
            val objectValue = element as? JsonObject
            val errno = objectValue?.str("errno").orEmpty()
            if (errno.isNotBlank() && errno != "0" && objectValue != null) {
                val msg = objectValue.str("msg", "message", "extra")
                if (errno == "100006") {
                    throw WeiboApiException(-1, "请求被微博拒绝，请稍后重试或重新登录")
                }
                if (msg.contains("登录")) throw AuthenticationException(msg)
                throw WeiboApiException(errno.toIntOrNull() ?: -1, msg.ifBlank { "操作失败，请重试" })
            }
            return element
        }
    }

    private fun ensureOk(objectValue: JsonObject, fallback: String) {
        val ok = objectValue["ok"].asIntOrNull()
        if (ok == null || ok == 1) return
        val msg = objectValue.str("msg", "message", "extra")
        if (msg.contains("登录")) throw AuthenticationException(msg)
        throw WeiboApiException(ok, msg.ifBlank { fallback })
    }

    private fun cookieWithXsrf(cookies: String, token: String): String {
        val parts = cookies.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("XSRF-TOKEN=") }
        return (parts + "XSRF-TOKEN=$token").joinToString("; ")
    }

    private fun parseMessage(item: JsonObject?, selfUid: String): ChatMessage? {
        if (item == null) return null
        val id = item.str("id", "idstr")
        val text = item.str("text")
        val senderId = item.str("sender_id")
        val senderName = item.str("sender_screen_name")
        val mediaType = item.str("media_type").toIntOrNull() ?: 0
        val dmType = item.str("dm_type").toIntOrNull() ?: 1
        val mediaUrl = item.str("media_url", "pic_url")
            .ifBlank { item["pic"].asObjOrNull()?.str("url").orEmpty() }
        if (id.isBlank() && text.isBlank() && mediaUrl.isBlank()) return null

        val type = when {
            mediaType == 0 && dmType != 1 -> ChatMediaType.Video
            mediaUrl.contains(".gif", ignoreCase = true) -> ChatMediaType.Gif
            mediaUrl.isNotBlank() -> ChatMediaType.Image
            mediaType > 0 -> ChatMediaType.Image
            else -> null
        }
        return ChatMessage(
            id = id.ifBlank { "local-" + text.hashCode() + senderId },
            fromUid = senderId,
            fromName = senderName,
            text = text,
            createdAtMillis = parseFlexibleTime(item.str("created_at")),
            outgoing = selfUid.isNotBlank() && senderId == selfUid,
            mediaUrl = mediaUrl.ifBlank { null },
            mediaType = type
        )
    }

    /** Weibo mixes epoch seconds/millis with "EEE MMM dd HH:mm:ss Z yyyy" and "MM-dd". */
    private fun parseFlexibleTime(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        raw.toLongOrNull()?.let { v ->
            return if (v < 1_000_000_000_000L) v * 1000L else v
        }
        for (pattern in listOf("EEE MMM dd HH:mm:ss Z yyyy", "yyyy-MM-dd HH:mm:ss")) {
            try {
                val f = SimpleDateFormat(pattern, Locale.US)
                f.isLenient = true
                f.parse(raw)?.let { return it.time }
            } catch (_: Exception) {
                // next
            }
        }
        // "MM-dd" style used by the conversation list: assume current year.
        val m = Regex("""^(\d{2})-(\d{2})$""").find(raw.trim())
        if (m != null) {
            val month = m.groupValues[1].toIntOrNull()
            val day = m.groupValues[2].toIntOrNull()
            if (month != null && day != null) {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.MONTH, month - 1)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                return cal.timeInMillis
            }
        }
        return System.currentTimeMillis()
    }

    private fun networkMessage(error: IOException): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("timed out", ignoreCase = true) -> "网络连接超时，请检查网络后重试"
            text.contains("Unable to resolve host", ignoreCase = true) -> "无法连接微博，请检查网络"
            else -> "网络请求失败，请检查网络后重试"
        }
    }

    private fun JsonElement?.asIntOrNull(): Int? =
        (this as? JsonPrimitive)?.content?.toIntOrNull()

    private companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    }
}
