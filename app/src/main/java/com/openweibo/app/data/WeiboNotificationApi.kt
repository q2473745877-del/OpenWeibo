package com.openweibo.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Mentions, received likes and received comments.
 *
 * All three endpoints were verified against a real logged-in session:
 *
 *  - `m.weibo.cn/message/mentionsAt?page=N` -> `{ok:1, data:[...]}`; when the user
 *    has no mentions Weibo returns `data:false` (a boolean, not an array).
 *  - `m.weibo.cn/message/cmt?page=N`       -> `{ok:1, data:[comment...]}`, each item
 *    carries `user` + `status`, where `status` is the commented post.
 *  - `m.weibo.cn/message/attitude?page=N`  -> `{ok:1, data:[attitude...]}`, each item
 *    carries `user`, an optional `comment`, and a `status`.
 *
 * They run on the mobile session (m.weibo.cn cookies), not the desktop one.
 */
class WeiboNotificationApi(
    private val json: Json,
    private val parser: WeiboParser
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(
        session: Session,
        kind: NotificationKind,
        page: Int
    ): NotificationPage = withContext(Dispatchers.IO) {
        if (kind == NotificationKind.DirectMessage) {
            throw WeiboApiException(-1, "私信请使用消息页的会话列表")
        }
        val cookies = mobileCookies(session)
        val path = when (kind) {
            NotificationKind.Mention -> "/message/mentionsAt"
            NotificationKind.Like -> "/message/attitude"
            NotificationKind.Comment -> "/message/cmt"
            NotificationKind.DirectMessage -> throw WeiboApiException(-1, "unsupported")
        }
        val root = get("https://m.weibo.cn$path?page=$page", cookies)
        parse(root, kind, page)
    }

    private fun parse(root: JsonElement, kind: NotificationKind, page: Int): NotificationPage {
        val objectValue = root as? JsonObject
            ?: throw WeiboApiException(-1, "微博返回了无法识别的数据")

        // `data` is either an array of entries or `false` when the feed is empty.
        val dataValue = objectValue["data"]
        val list: List<JsonElement> = when {
            dataValue is kotlinx.serialization.json.JsonArray -> dataValue
            // Beware: a plain boolean `false` means "no entries", not an error.
            dataValue is JsonPrimitive && dataValue.isString.not() -> emptyList()
            else -> objectValue["data"].asArr()
        }

        val items = list.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            when (kind) {
                NotificationKind.Mention -> parseMention(item)
                NotificationKind.Like -> parseLike(item)
                NotificationKind.Comment -> parseReceivedComment(item)
                NotificationKind.DirectMessage -> null
            }
        }
        val totalNumber = objectValue.str("total", "total_number").toIntOrNull()
        val hasMore = when {
            // `data:false` is a definitive "nothing here".
            dataValue is JsonPrimitive -> false
            totalNumber != null -> page * PAGE_SIZE < totalNumber
            else -> items.size >= PAGE_SIZE
        }
        return NotificationPage(
            items = items,
            nextPage = if (hasMore) page + 1 else null,
            hasMore = hasMore
        )
    }

    /** mentionsAt items are regular post objects. */
    private fun parseMention(item: JsonObject): NotificationItem? {
        val post = parser.parsePost(item) ?: return null
        return NotificationItem(
            id = "mention-" + post.id.ifBlank { post.bid },
            kind = NotificationKind.Mention,
            actorName = post.user.screenName,
            actorAvatar = post.user.avatarUrl,
            actorUid = post.user.id,
            text = post.text,
            sourceText = "提到了你",
            createdAtMillis = post.createdAtMillis,
            post = post
        )
    }

    /**
     * `/message/attitude` rows: who liked, plus the liked post (`status`) and,
     * for comment likes, the liked `comment`.
     */
    private fun parseLike(item: JsonObject): NotificationItem? {
        val status = item["status"].asObjOrNull()
        val comment = item["comment"].asObjOrNull()
        val user = item["user"].asObjOrNull()
        val post = status?.let { parser.parsePost(it) }

        val commentText = comment?.let { c ->
            if (c.str("deleted") == "1") "" else WeiboText.plain(c.str("text"))
        }.orEmpty()

        // A like can target a post or a comment; pick the more informative text.
        val likedText = when {
            commentText.isNotBlank() -> commentText
            post != null && post.text.isNotBlank() -> post.text
            else -> ""
        }
        val source = if (comment != null && commentText.isNotBlank()) {
            "赞了你的评论"
        } else {
            "赞了你的微博"
        }
        val name = user?.str("screen_name", "name").orEmpty().ifBlank { "有人" }
        val id = item.str("idStr", "id").ifBlank { "like-" + name.hashCode() + likedText.hashCode() }

        return NotificationItem(
            id = "like-$id",
            kind = NotificationKind.Like,
            actorName = name,
            actorAvatar = user?.str("profile_image_url", "avatar_hd").orEmpty(),
            actorUid = user?.str("id").orEmpty(),
            text = likedText,
            sourceText = source,
            createdAtMillis = parseTime(item.str("created_at")),
            post = post,
            commentId = comment?.str("id", "idstr").orEmpty()
        )
    }

    /** `/message/cmt` rows: the commenter, the comment, and the parent post. */
    private fun parseReceivedComment(item: JsonObject): NotificationItem? {
        val user = item["user"].asObjOrNull()
        val status = item["status"].asObjOrNull()
        val text = WeiboText.plain(item.str("text"))
        if (text.isBlank() && status == null) return null
        val post = status?.let { parser.parsePost(it) }
        return NotificationItem(
            id = "comment-" + item.str("id", "idstr").ifBlank {
                "c-" + text.hashCode()
            },
            kind = NotificationKind.Comment,
            actorName = user?.str("screen_name", "name").orEmpty().ifBlank { "有人" },
            actorAvatar = user?.str("profile_image_url", "avatar_hd").orEmpty(),
            actorUid = user?.str("id").orEmpty(),
            text = text,
            sourceText = if (post != null) "评论了你的微博" else "评论了你",
            createdAtMillis = parseTime(item.str("created_at")),
            post = post,
            commentId = item.str("id", "idstr")
        )
    }

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
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw WeiboApiException(-1, networkMessage(error), error)
        }
        response.use {
            val body = try {
                it.body?.string().orEmpty()
            } catch (error: IOException) {
                throw WeiboApiException(-1, "读取消息失败，请重试", error)
            }
            if (it.request.url.host.contains("passport") || body.contains("Sina Visitor System")) {
                throw AuthenticationException()
            }
            if (!it.isSuccessful) {
                val message = when (it.code) {
                    401, 403 -> "微博拒绝了本次请求，请重新登录后再试"
                    429, 418 -> "请求过于频繁，请稍后再试"
                    in 500..599 -> "微博服务器暂时不可用 (${it.code})"
                    else -> "消息接口请求失败 (${it.code})"
                }
                throw WeiboApiException(it.code, message)
            }
            val element = try {
                json.parseToJsonElement(body)
            } catch (error: Exception) {
                throw WeiboApiException(-1, "微博返回了无法识别的数据", error)
            }
            // The mobile endpoints answer {ok:0} for soft failures.
            val ok = (element as? JsonObject)?.get("ok")
            if (ok is JsonPrimitive) {
                val code = ok.content.toIntOrNull()
                val msg = (element as JsonObject).str("msg", "message")
                if (code == 0) {
                    if (msg.contains("登录")) throw AuthenticationException(msg)
                    if (msg.isNotBlank()) throw WeiboApiException(code, msg)
                }
            }
            return element
        }
    }

    private fun mobileCookies(session: Session): String {
        val cookies = session.cookies.replace("\r", "").replace("\n", "").trim()
        if (cookies.isBlank()) {
            throw AuthenticationException("登录信息为空，请重新登录")
        }
        return cookies
    }

    private fun parseTime(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        raw.toLongOrNull()?.let { value ->
            return if (value < 1_000_000_000_000L) value * 1000L else value
        }
        val patterns = listOf(
            "EEE MMM dd HH:mm:ss Z yyyy",
            "EEE MMM dd HH:mm:ss z yyyy",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = true
                format.parse(raw)?.let { return it.time }
            } catch (_: Exception) {
                // try next
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

    private companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        /** These mobile feeds return 10 entries per page. */
        const val PAGE_SIZE = 10
    }
}

// ------------------------------------------------------------- small JSON helpers

internal fun JsonObject.str(vararg keys: String): String {
    for (key in keys) {
        val value = this[key] ?: continue
        if (value is JsonPrimitive) return value.content
    }
    return ""
}

internal fun JsonElement?.asObjOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArr(): List<JsonElement> =
    (this as? kotlinx.serialization.json.JsonArray) ?: emptyList()
