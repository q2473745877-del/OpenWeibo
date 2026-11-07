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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class AuthenticationException(message: String = "登录状态已过期，请重新登录") : IOException(message)

class WeiboApiException(
    val code: Int,
    override val message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class WeiboApi(
    private val client: OkHttpClient = defaultClient(),
    private val contentResolver: ContentResolver? = null,
    private val desktopCookieProvider: () -> String = { "" }
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val videoClient: OkHttpClient = client.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    suspend fun validateLogin(
        rawCookies: String,
        desktopCookies: String = ""
    ): Session = withContext(Dispatchers.IO) {
        val cookies = sanitizeCookies(rawCookies)
        val configRoot = executeMobileJson(
            "/api/config",
            cookies = cookies,
            extraCookies = null
        )
        val data = configRoot.asObject()?.get("data").asObject()
            ?: throw AuthenticationException("微博没有返回登录信息，请重试")
        val login = data["login"].asBool()
            || data.stringValue("login").equals("true", ignoreCase = true)
        if (!login) throw AuthenticationException("还没有检测到已登录的微博账号")

        val uid = data.stringValue("uid").ifBlank {
            (data["user"].asObject()?.stringValue("id", "idstr")).orEmpty()
        }
        if (uid.isBlank()) throw AuthenticationException("登录成功，但未能读取账号 ID，请重试")

        var user = data["user"].asObject()
        if (user == null || user.stringValue("screen_name").isBlank()) {
            user = fetchUserInfo(cookies, uid)
        }
        val parsedUser = WeiboParser.parseUser(user)
        Session(
            cookies = cookies,
            uid = uid,
            screenName = parsedUser?.screenName?.ifBlank { "微博用户" } ?: "微博用户",
            avatarUrl = parsedUser?.avatarUrl.orEmpty(),
            verified = parsedUser?.verified ?: false,
            followersCount = user?.longValue("followers_count") ?: 0L,
            followingCount = user?.longValue("follow_count", "following_count", "friends_count")
                ?: parsedUser?.followingCount ?: 0L,
            statusesCount = user?.longValue("statuses_count") ?: 0L,
            description = user?.stringValue("description").orEmpty(),
            xsrfToken = data.stringValue("st", "stoken"),
            desktopCookies = normalizeCookieHeader(desktopCookies)
        )
    }

    suspend fun refreshSessionCounts(session: Session): Session = withContext(Dispatchers.IO) {
        val configRoot = executeMobileJson("/api/config", session.cookies)
        val data = configRoot.asObject()?.get("data").asObject()
            ?: return@withContext session
        val login = data["login"].asBool() ||
            data.stringValue("login").equals("true", ignoreCase = true)
        if (!login) throw AuthenticationException()

        val uid = data.stringValue("uid").ifBlank { session.uid }
        var user = data["user"].asObject()
        if (user == null ||
            user.stringValue("screen_name").isBlank() ||
            user.longValue("follow_count", "following_count", "friends_count") == null
        ) {
            user = fetchUserInfo(session.cookies, uid) ?: user
        }
        if (user == null) return@withContext session

        return@withContext session.copy(
            followersCount = user.longValue("followers_count") ?: session.followersCount,
            followingCount = user.longValue("follow_count", "following_count", "friends_count")
                ?: session.followingCount,
            statusesCount = user.longValue("statuses_count") ?: session.statusesCount
        )
    }

    suspend fun fetchFeed(cookies: String, cursor: String?): FeedPage = withContext(Dispatchers.IO) {
        val safeCookies = sanitizeCookies(cookies)
        val efforts = mutableListOf<Pair<String?, String?>>()
        if (cursor.isNullOrBlank()) {
            efforts += null to null
            efforts += null to "legacy"
        } else {
            efforts += "max_id" to cursor
            efforts += "next_cursor" to cursor
        }

        var lastFailure: Exception? = null
        var emptyPage: FeedPage? = null
        var legacyAttempted = false
        for ((parameter, value) in efforts) {
            if (value == "legacy") {
                if (legacyAttempted) continue
                legacyAttempted = true
                try {
                    val page = fetchLegacyFriendsFeed(safeCookies)
                    if (page.posts.isNotEmpty()) return@withContext page
                    if (emptyPage == null) emptyPage = page
                } catch (error: AuthenticationException) {
                    throw error
                } catch (error: Exception) {
                    lastFailure = error
                }
                continue
            }

            try {
                val builder = "https://m.weibo.cn/feed/friends".toHttpUrl().newBuilder()
                if (parameter != null && value != null) {
                    builder.addQueryParameter(parameter, value)
                }
                val root = executeMobileJson(builder.build().toString(), safeCookies)
                val page = WeiboParser.parseFeed(root)
                if (page.posts.isNotEmpty()) {
                    return@withContext page
                }
                if (emptyPage == null) emptyPage = page
            } catch (error: AuthenticationException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
            }
        }

        when {
            emptyPage != null -> emptyPage!!
            lastFailure != null -> throw lastFailure!!
            else -> FeedPage(emptyList(), null, false)
        }
    }

    private fun fetchLegacyFriendsFeed(cookies: String): FeedPage {
        val root = executeMobileJson(
            "https://m.weibo.cn/index/friends?format=cards",
            cookies
        )
        return WeiboParser.parseFeed(root)
    }

    suspend fun fetchSearch(
        session: Session,
        query: String,
        page: Int
    ): SearchPage = withContext(Dispatchers.IO) {
        val keyword = query.trim()
        if (keyword.isBlank()) throw WeiboApiException(-1, "搜索关键词不能为空")
        val containerId = "100103type=1&q=$keyword"
        val url = "https://m.weibo.cn/api/container/getIndex".toHttpUrl().newBuilder()
            .addQueryParameter("containerid", containerId)
            .addQueryParameter("page_type", "searchall")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("luicode", "10000011")
            .build()
            .toString()
        val root = executeMobileJson(url, session.cookies)
        WeiboParser.parseSearchPage(root, page)
    }

    suspend fun fetchProfile(
        session: Session,
        uid: String,
        page: Int,
        sinceId: String? = null
    ): ProfilePage = withContext(Dispatchers.IO) {
        val containers = listOf(
            "107603$uid",
            "230413${uid}_-_WEIBO_SECOND_PROFILE_WEIBO"
        )
        val containersToUse = if (sinceId.isNullOrBlank()) {
            containers
        } else {
            listOf(containers.first())
        }
        var lastError: Exception? = null
        var fallbackUser: WeiboUser? = null
        var emptyPage: ProfilePage? = null

        if (page == 1) {
            try {
                val infoRoot = executeMobileJson(
                    "https://m.weibo.cn/api/container/getIndex?type=uid&value=$uid",
                    session.cookies
                )
                fallbackUser = WeiboParser.parseUser(
                    infoRoot.asObject()
                        ?.get("data").asObject()
                        ?.get("userInfo").asObject()
                )
            } catch (error: AuthenticationException) {
                throw error
            } catch (_: Exception) {
                // The posts endpoint often returns userInfo as well.
            }
        }

        for (container in containersToUse) {
            try {
                val urlBuilder = "https://m.weibo.cn/api/container/getIndex".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("type", "uid")
                    .addQueryParameter("value", uid)
                    .addQueryParameter("containerid", container)
                    .addQueryParameter("luicode", "10000011")
                    .addQueryParameter("lfid", "100505$uid")
                if (sinceId.isNullOrBlank()) {
                    urlBuilder.addQueryParameter("page", page.toString())
                } else {
                    urlBuilder.addQueryParameter("since_id", sinceId)
                }
                val url = urlBuilder.build().toString()
                val root = executeMobileJson(url, session.cookies)
                val dataPresent = (root as? JsonObject)?.get("data") is JsonObject
                val parsed = WeiboParser.parseProfilePage(root, page, fallbackUser)
                if (parsed.posts.isNotEmpty()) return@withContext parsed
                if (dataPresent && emptyPage == null) {
                    emptyPage = parsed
                }
            } catch (error: AuthenticationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }

        emptyPage?.let { return@withContext it }
        fallbackUser?.let { user ->
            return@withContext ProfilePage(user, emptyList(), page + 1, false)
        }
        throw lastError ?: WeiboApiException(-1, "主页加载失败，请重试")
    }

    suspend fun fetchHotSearch(
        cookies: String,
        category: HotCategory
    ): HotPage = withContext(Dispatchers.IO) {
        val safeCookies = sanitizeCookies(cookies)
        val container = "106003type=25&t=3&disable_hot=1&filter_type=${category.filterType}"
        try {
            val builder = "https://m.weibo.cn/api/container/getIndex".toHttpUrl().newBuilder()
                .addQueryParameter("containerid", container)
                .addQueryParameter("luicode", "10000011")
                .addQueryParameter("lfid", "231583")
            val root = executeMobileJson(builder.build().toString(), safeCookies)
            val items = WeiboParser.parseHotMobile(root)
            if (items.isNotEmpty()) {
                return@withContext HotPage(items, System.currentTimeMillis())
            }
            throw WeiboApiException(-1, "热搜暂时为空，请稍后刷新")
        } catch (primary: Exception) {
            if (primary is AuthenticationException) throw primary
            // The desktop board is a useful public fallback and does not need cookies.
            try {
                val root = executeDesktopJson("https://weibo.com/ajax/side/hotSearch")
                val items = WeiboParser.parseHotDesktop(root)
                if (items.isNotEmpty()) {
                    return@withContext HotPage(items, System.currentTimeMillis())
                }
            } catch (_: Exception) {
                // Surface the original error below.
            }
            throw primary
        }
    }

    suspend fun fetchComments(
        session: Session,
        statusId: String,
        cursor: CommentCursor?
    ): CommentPage = withContext(Dispatchers.IO) {
        val builder = "https://m.weibo.cn/comments/hotflow".toHttpUrl().newBuilder()
            .addQueryParameter("id", statusId)
            .addQueryParameter("mid", statusId)
            .addQueryParameter("max_id_type", cursor?.maxIdType ?: "0")
            .addQueryParameter("luicode", "10000011")
        if (cursor != null) {
            builder.addQueryParameter("max_id", cursor.maxId)
        }
        val root = executeMobileJson(builder.build().toString(), session.cookies)
        WeiboParser.parseCommentPage(root)
    }

    suspend fun fetchReposts(
        session: Session,
        statusId: String,
        page: Int
    ): RepostPage = withContext(Dispatchers.IO) {
        val endpoints = listOf(
            "https://m.weibo.cn/api/statuses/repostTimeline",
            "https://m.weibo.cn/statuses/repostTimeline"
        )
        var lastError: Exception? = null
        for (endpoint in endpoints) {
            try {
                val url = endpoint.toHttpUrl().newBuilder()
                    .addQueryParameter("id", statusId)
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("luicode", "10000011")
                    .build()
                    .toString()
                val root = executeMobileJson(url, session.cookies)
                return@withContext WeiboParser.parseRepostPage(root)
            } catch (error: AuthenticationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: WeiboApiException(-1, "转发列表加载失败，请重试")
    }

    suspend fun fetchFollowers(
        session: Session,
        uid: String,
        page: Int
    ): UserPage = fetchUserList(
        session = session,
        uid = uid,
        page = page,
        containerId = "231051_-_fans_-_$uid",
        preferPage = false,
        fallback = "粉丝列表加载失败，请重试"
    )

    suspend fun fetchFollowing(
        session: Session,
        uid: String,
        page: Int
    ): UserPage = fetchUserList(
        session = session,
        uid = uid,
        page = page,
        containerId = "231051_-_followers_-_$uid",
        preferPage = true,
        fallback = "关注列表加载失败，请重试"
    )

    private suspend fun fetchUserList(
        session: Session,
        uid: String,
        page: Int,
        containerId: String,
        preferPage: Boolean,
        fallback: String
    ): UserPage = withContext(Dispatchers.IO) {
        val parameters = if (preferPage) {
            listOf("page" to page.toString(), "since_id" to page.toString())
        } else {
            listOf("since_id" to page.toString(), "page" to page.toString())
        }
        var emptyPage: UserPage? = null
        var lastError: Exception? = null
        for ((key, value) in parameters) {
            try {
                val url = "https://m.weibo.cn/api/container/getIndex".toHttpUrl().newBuilder()
                    .addQueryParameter("containerid", containerId)
                    .addQueryParameter(key, value)
                    .addQueryParameter("luicode", "10000011")
                    .addQueryParameter("lfid", "100505$uid")
                    .build()
                    .toString()
                val root = executeMobileJson(url, session.cookies)
                val parsed = WeiboParser.parseUserPage(root, page)
                if (parsed.users.isNotEmpty()) return@withContext parsed
                if (emptyPage == null) emptyPage = parsed
            } catch (error: AuthenticationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        emptyPage ?: throw lastError ?: WeiboApiException(-1, fallback)
    }

    suspend fun fetchLongText(
        session: Session,
        statusId: String
    ): String = withContext(Dispatchers.IO) {
        val id = statusId.trim()
        if (id.isBlank()) throw WeiboApiException(-1, "无法识别这条微博")
        val url = "https://m.weibo.cn/statuses/extend".toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .build()
            .toString()
        val root = executeMobileJson(url, session.cookies)
        val objectValue = root.asObject() ?: throw WeiboApiException(-1, "全文加载失败，请重试")
        val ok = objectValue["ok"].asInt()
        if (ok != null && ok != 1) {
            val message = objectValue.stringValue("msg", "message")
                .ifBlank { "全文加载失败，请重试" }
            if (message.contains("登录")) throw AuthenticationException(message)
            throw WeiboApiException(ok, message)
        }
        val html = objectValue["data"].asObject()
            ?.stringValue("longTextContent")
            .orEmpty()
        val text = WeiboText.plain(html)
        if (text.isBlank()) throw WeiboApiException(-1, "没有获取到完整内容，请重试")
        text
    }

    suspend fun postComment(
        session: Session,
        statusId: String,
        content: String
    ): WeiboComment? = withContext(Dispatchers.IO) {
        val root = executeFormPost(
            path = "/api/comments/create",
            session = session,
            fields = listOf(
                "content" to content,
                "mid" to statusId,
                "id" to statusId
            ),
            referer = "https://m.weibo.cn/detail/$statusId"
        )
        ensureWriteOk(root, "评论发送失败，请重试")
        WeiboParser.parseComment(
            root.asObject()?.get("data").asObject() ?: return@withContext null
        )
    }

    suspend fun repostStatus(
        session: Session,
        statusId: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        val fields = mutableListOf(
            "id" to statusId,
            "mid" to statusId
        )
        if (content.isNotBlank()) {
            fields += "content" to content.trim()
        }
        val root = executeFormPost(
            path = "/api/statuses/repost",
            session = session,
            fields = fields,
            referer = "https://m.weibo.cn/compose/repost?id=$statusId"
        )
        ensureWriteOk(root, "转发失败，请重试")
        true
    }

    suspend fun setFollowing(
        session: Session,
        uid: String,
        follow: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val targetUid = uid.trim()
        if (targetUid.isBlank()) throw WeiboApiException(-1, "无法识别该用户")
        val fallbackMessage = if (follow) "关注失败，请重试" else "取消关注失败，请重试"
        // Weibo's mobile unfollow endpoint uses the historical "destory" typo.
        val paths = if (follow) {
            listOf("/api/friendships/create")
        } else {
            listOf("/api/friendships/destory", "/api/friendships/destroy")
        }
        var lastError: Exception? = null
        for (path in paths) {
            try {
                val root = executeFormPost(
                    path = path,
                    session = session,
                    fields = listOf("uid" to targetUid),
                    referer = "https://m.weibo.cn/u/$targetUid"
                )
                ensureWriteOk(root, fallbackMessage)
                return@withContext true
            } catch (error: AuthenticationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: WeiboApiException(-1, fallbackMessage)
    }

    suspend fun uploadImage(
        session: Session,
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): UploadedImage = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) throw WeiboApiException(-1, "图片内容为空，请重新选择")
        if (bytes.size > MAX_IMAGE_BYTES) {
            throw WeiboApiException(-1, "图片超过微博 10MB 上限，请更换原图")
        }
        val token = fetchWriteToken(session)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("type", "json")
            .addFormDataPart("_spr", "screen:1080x2340")
            .addFormDataPart("st", token)
            .addFormDataPart(
                "pic",
                safeFileName(fileName),
                bytes.toRequestBody(mimeType.ifBlank { "image/jpeg" }.toMediaType())
            )
            .build()
        val request = Request.Builder()
            .url("https://m.weibo.cn/api/statuses/uploadPic")
            .post(body)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://m.weibo.cn/compose/")
            .header("Origin", "https://m.weibo.cn")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("MWeibo-Pwa", "1")
            .header("Cookie", cookieWithXsrf(session.cookies, token))
            .header("X-XSRF-TOKEN", token)
            .build()
        val root = executeJson(client, request)
        val objectValue = root.asObject() ?: throw WeiboApiException(-1, "图片上传失败，请重试")
        val data = objectValue["data"].asObject()
        val pid = objectValue.stringValue("pic_id", "pid")
            .ifBlank { data?.stringValue("pid", "pic_id").orEmpty() }
        if (pid.isBlank()) {
            val message = objectValue.stringValue("msg", "message", "error")
                .ifBlank { data?.stringValue("msg", "message", "error").orEmpty() }
            if (message.contains("登录")) throw AuthenticationException(message)
            throw WeiboApiException(
                objectValue["code"].asInt() ?: data?.get("code").asInt() ?: -1,
                message.ifBlank { "图片上传失败，请重试" }
            )
        }
        val url = objectValue.stringValue("bmiddle_pic", "large_pic", "original_pic", "url")
            .ifBlank { data?.stringValue("bmiddle_pic", "large_pic", "original_pic", "url").orEmpty() }
        UploadedImage(pid = pid, url = url)
    }

    suspend fun publishStatus(
        session: Session,
        content: String,
        visibility: ComposeVisibility,
        picIds: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        val text = content.trim().ifBlank { if (picIds.isNotEmpty()) "分享图片" else "分享微博" }
        val fields = mutableListOf(
            "content" to text,
            "visible" to visibility.value.toString()
        )
        if (picIds.isNotEmpty()) {
            fields += "picId" to picIds.joinToString(",")
        }
        val root = executeFormPost(
            path = "/api/statuses/update",
            session = session,
            fields = fields,
            referer = "https://m.weibo.cn/compose/"
        )
        ensureWriteOk(root, "微博发布失败，请重试")
        true
    }

    suspend fun loadEditablePost(session: Session, postId: String): EditablePost =
        withContext(Dispatchers.IO) {
            val id = postId.trim()
            if (id.isBlank()) throw WeiboApiException(-1, "无法读取这条微博")
            val root = executeMobileJson("/statuses/show?id=$id", session.cookies)
            val objectValue = root.asObject()
                ?: throw WeiboApiException(-1, "无法读取这条微博")
            val ok = objectValue["ok"].asInt()
            if (ok != null && ok <= 0) {
                val message = objectValue.stringValue("msg", "message")
                    .ifBlank { "无法读取这条微博" }
                if (message.contains("登录")) throw AuthenticationException(message)
                throw WeiboApiException(ok, message)
            }
            val data = objectValue["data"].asObject()
                ?: throw WeiboApiException(-1, "无法读取这条微博")
            if (!data["can_edit"].asBool()) {
                throw WeiboApiException(-1, "这条微博暂时无法编辑（可能已超过可编辑时限）")
            }
            val text = data.stringValue("raw_text").ifBlank {
                WeiboText.plain(data.stringValue("text"))
            }
            val picIds = data.stringList("pic_ids")
            if (text.isBlank() && picIds.isEmpty()) {
                throw WeiboApiException(-1, "没有读取到微博正文，请重试")
            }
            if (text.length > MAX_EDIT_TEXT_LENGTH) {
                throw WeiboApiException(
                    -1,
                    "这条微博正文超过 $MAX_EDIT_TEXT_LENGTH 字，暂不支持编辑"
                )
            }
            EditablePost(
                id = id,
                text = text,
                picIds = picIds
            )
        }

    /**
     * Edits only the text of an existing post. Existing picture IDs are sent
     * back unchanged because the mobile composer never drops media silently.
     */
    suspend fun editStatus(
        session: Session,
        postId: String,
        content: String,
        picIds: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        val id = postId.trim()
        val text = content.trim()
        if (id.isBlank()) throw WeiboApiException(-1, "无法读取这条微博")
        if (text.isBlank()) throw WeiboApiException(-1, "正文不能为空")
        val fields = mutableListOf(
            "content" to text,
            "editId" to id
        )
        if (picIds.isNotEmpty()) {
            fields += "picId" to picIds.joinToString(",")
        }
        val root = executeFormPost(
            path = "/api/statuses/update",
            session = session,
            fields = fields,
            referer = "https://m.weibo.cn/compose/?editId=$id"
        )
        ensureWriteOk(root, "编辑微博失败，请重试")
        true
    }

    suspend fun deleteStatus(session: Session, postId: String): Boolean =
        withContext(Dispatchers.IO) {
            val id = postId.trim()
            if (id.isBlank()) throw WeiboApiException(-1, "无法读取这条微博")
            val root = executeFormPost(
                path = "/profile/delMyblog",
                session = session,
                fields = listOf("mid" to id),
                referer = "https://m.weibo.cn/profile/${session.uid}"
            )
            val objectValue = root.asObject()
                ?: throw WeiboApiException(-1, "删除微博失败，请重试")
            val ok = objectValue["ok"].asInt() ?: -1
            if (ok <= 0) {
                val message = objectValue.stringValue("msg", "message")
                    .ifBlank { "删除微博失败，请重试" }
                if (message.contains("登录")) throw AuthenticationException(message)
                throw WeiboApiException(ok, message)
            }
            true
        }

    /**
     * Uploads the original video file through the desktop multimedia protocol
     * used by weibo.com. Videos are chunked and each chunk is sent as binary.
     */
    suspend fun uploadVideo(
        session: Session,
        file: MediaFile,
        onProgress: (Float) -> Unit = {}
    ): UploadedVideo = withContext(Dispatchers.IO) {
        if (file.size <= 0L) throw WeiboApiException(-1, "无法读取视频大小，请重新选择")
        if (file.size > MAX_VIDEO_BYTES) throw WeiboApiException(-1, "视频超过 4GB 上限，请更换文件")
        val resolver = contentResolver
            ?: throw WeiboApiException(-1, "无法读取视频文件，请重新选择")
        val xsrf = desktopXsrf(session.desktopCookies)
        val dispatch = desktopPostForm(
            url = "https://weibo.com/ajax/multimedia/dispatch",
            session = session,
            xsrf = xsrf,
            fields = listOf(
                "source" to DESKTOP_UPLOAD_SOURCE,
                "types" to "video",
                "version" to "4",
                "auth_accept" to "video",
                "size" to file.size.toString(),
                "timeout" to "180000"
            ),
            referer = "https://weibo.com/upload/channel"
        )
        val dispatchObject = dispatch.asObject()
            ?: throw WeiboApiException(-1, "视频上传初始化失败，请重试")
        val dispatchOk = dispatchObject["ok"]
        if (dispatchOk.asInt() != 1 && !dispatchOk.asBool()) {
            val message = dispatchObject.stringValue("msg", "message", "error")
            if (message.contains("登录")) throw AuthenticationException(message)
            throw WeiboApiException(
                dispatchObject["code"].asInt() ?: -1,
                message.ifBlank { "视频上传初始化失败，请稍后重试" }
            )
        }
        val videoUrls = dispatchObject["data"].asObject()?.get("video").asObject()
            ?: throw WeiboApiException(-1, "视频上传通道暂时不可用，请稍后重试")
        val initUrl = videoUrls.stringValue("init_url")
        val uploadUrl = videoUrls.stringValue("upload_url")
        if (initUrl.isBlank() || uploadUrl.isBlank()) {
            throw WeiboApiException(-1, "视频上传通道暂时不可用，请稍后重试")
        }

        val sessionId = UUID.randomUUID().toString().replace("-", "")
        val initBoundary = "----OpenWeibo$sessionId"
        val initBody = buildString {
            append("--").append(initBoundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"biz_file\"\r\n\r\n")
            append("{\"mediaprops\":\"{\\\"screenshot\\\":1}\"}")
            append("\r\n--").append(initBoundary).append("--\r\n")
        }
        val initRequest = desktopRequestBuilder(
            url = appendQuery(
                initUrl,
                listOf(
                    "source" to DESKTOP_UPLOAD_SOURCE,
                    "size" to file.size.toString(),
                    "name" to file.name,
                    "type" to "video",
                    "client" to "web",
                    "session_id" to sessionId
                )
            ),
            session = session,
            xsrf = xsrf,
            body = initBody.toRequestBody(
                "multipart/mixed; boundary=$initBoundary".toMediaType()
            ),
            referer = "https://weibo.com/upload/channel"
        ).build()
        val initRoot = executeJson(videoClient, initRequest)
        val initRootObject = initRoot.asObject()
            ?: throw WeiboApiException(-1, "视频上传初始化失败，请重试")
        val initData = initRootObject["data"].asObject()
            ?.takeIf { it.stringValue("media_id").isNotBlank() }
            ?: initRootObject
        val mediaId = initData.stringValue("media_id")
        val uploadId = initData.stringValue("upload_id")
        val uploadAuth = initData.stringValue("auth")
        if (mediaId.isBlank() || uploadId.isBlank() || uploadAuth.isBlank()) {
            val message = initData.stringValue("error", "msg", "message")
            if (message.contains("登录")) throw AuthenticationException(message)
            throw WeiboApiException(
                initData["error_code"].asInt() ?: -1,
                message.ifBlank { "视频上传初始化失败，请稍后重试" }
            )
        }

        val strategy = initData["strategy"].asObject()
        val chunkKb = strategy?.longValue("chunk_size")?.takeIf { it in 1L..32768L } ?: 8192L
        val chunkSize = (chunkKb * 1024L).coerceAtMost(file.size)
        val chunkCount = ((file.size + chunkSize - 1L) / chunkSize).toInt().coerceAtLeast(1)
        val buffer = ByteArray(chunkSize.toInt())
        val input = resolver.openInputStream(file.uri)
            ?: throw WeiboApiException(-1, "无法读取视频文件，请重新选择")
        input.use { stream ->
            for (index in 0 until chunkCount) {
                val start = index.toLong() * chunkSize
                val expected = minOf(chunkSize, file.size - start).toInt()
                var read = 0
                while (read < expected) {
                    val count = stream.read(buffer, read, expected - read)
                    if (count <= 0) {
                        throw WeiboApiException(-1, "视频读取中断，请重新选择")
                    }
                    read += count
                }
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                val chunkUrl = appendQuery(
                    uploadUrl,
                    listOf(
                        "source" to DESKTOP_UPLOAD_SOURCE,
                        "upload_id" to uploadId,
                        "media_id" to mediaId,
                        "upload_protocol" to "binary",
                        "type" to "video",
                        "client" to "web",
                        "check" to md5Hex(chunk),
                        "index" to index.toString(),
                        "size" to chunk.size.toString(),
                        "start_loc" to start.toString(),
                        "count" to chunkCount.toString()
                    )
                )
                val request = desktopRequestBuilder(
                    url = chunkUrl,
                    session = session,
                    xsrf = xsrf,
                    body = chunk.toRequestBody(OCTET_STREAM),
                    referer = "https://weibo.com/upload/channel"
                ).header("X-Up-Auth", uploadAuth).build()
                ensureChunkOk(executeRaw(videoClient, request))
                onProgress((index + 1).toFloat() / chunkCount.toFloat())
            }
        }

        val checkUrl = videoUrls.stringValue("check_url")
        if (checkUrl.isNotBlank()) {
            val checkFields = listOf(
                "source" to DESKTOP_UPLOAD_SOURCE,
                "upload_id" to uploadId,
                "media_id" to mediaId,
                "upload_protocol" to "binary",
                "count" to chunkCount.toString(),
                "action" to "finish",
                "size" to file.size.toString(),
                "client" to "web",
                "status" to ""
            )
            val checkBody = FormBody.Builder().apply {
                checkFields.forEach { (key, value) -> add(key, value) }
            }.build()
            val checkRequest = desktopRequestBuilder(
                url = checkUrl,
                session = session,
                xsrf = xsrf,
                body = checkBody,
                referer = "https://weibo.com/upload/channel"
            ).header("X-Up-Auth", uploadAuth).build()
            val checkRoot = executeJson(videoClient, checkRequest)
            val checkObject = checkRoot.asObject()
            val checkResult = checkObject?.get("result")
            val checked = checkResult.asBool() ||
                checkResult.asInt() == 1 ||
                checkObject?.stringValue("result").equals("true", ignoreCase = true)
            if (!checked) {
                val message = checkObject?.stringValue("msg", "message", "error").orEmpty()
                throw WeiboApiException(-1, message.ifBlank { "视频校验失败，请重试" })
            }
        }
        UploadedVideo(mediaId = mediaId, title = file.name)
    }

    suspend fun publishVideoStatus(
        session: Session,
        content: String,
        visibility: ComposeVisibility,
        mediaId: String,
        title: String,
        coverPid: String,
        coverUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        val xsrf = desktopXsrf(session.desktopCookies)
        val mediaJson = buildJsonObject {
            put(
                "titles",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("title", title.ifBlank { "视频" })
                            put("default", "true")
                        }
                    )
                }
            )
            if (coverPid.isNotBlank() || coverUrl.isNotBlank()) {
                put(
                    "covers",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                if (coverPid.isNotBlank()) {
                                    put("pid", coverPid)
                                } else {
                                    put("url", coverUrl)
                                }
                            }
                        )
                    }
                )
            }
            put("type", "video")
            put("media_id", mediaId)
            put("resource", buildJsonObject { put("video_tail", false) })
        }.toString()
        val root = desktopPostForm(
            url = "https://weibo.com/ajax/statuses/update",
            session = session,
            xsrf = xsrf,
            fields = listOf(
                "content" to content.trim().ifBlank { "分享视频" },
                "pic_id" to "",
                "visible" to visibility.value.toString(),
                "share_id" to "",
                "media" to mediaJson,
                "vote" to ""
            ),
            referer = "https://weibo.com/"
        )
        val objectValue = root.asObject()
            ?: throw WeiboApiException(-1, "视频微博发布失败，请重试")
        val okElement = objectValue["ok"]
        val ok = okElement.asInt()
        if ((ok != null && ok > 0) || okElement.asBool()) return@withContext true
        val message = objectValue.stringValue("msg", "message", "error")
        if (message.contains("登录")) throw AuthenticationException(message)
        throw WeiboApiException(
            objectValue["code"].asInt() ?: ok ?: -1,
            message.ifBlank { "视频微博发布失败，请重试" }
        )
    }

    private fun desktopPostForm(
        url: String,
        session: Session,
        xsrf: String,
        fields: List<Pair<String, String>>,
        referer: String
    ): JsonElement {
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        val request = desktopRequestBuilder(
            url = url,
            session = session,
            xsrf = xsrf,
            body = body,
            referer = referer
        ).build()
        return executeJson(videoClient, request)
    }

    private fun desktopRequestBuilder(
        url: String,
        session: Session,
        xsrf: String,
        body: RequestBody,
        referer: String
    ): Request.Builder {
        val cookies = session.desktopCookies
            .replace("\r", "")
            .replace("\n", "")
            .trim()
        return Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", referer)
            .header("Origin", "https://weibo.com")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-XSRF-TOKEN", xsrf)
            .header("client-version", "v1.1.255")
            .header("server-version", "v2026.10.29.2")
            .header("Cookie", cookies)
    }

    private fun desktopXsrf(cookies: String): String {
        val normalized = normalizeCookieHeader(cookies)
        val xsrf = Regex("(?:^|;\\s*)XSRF-TOKEN=([^;]+)")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
        val sub = Regex("(?:^|;\\s*)SUB=([^;]*)")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
        if (xsrf.isNullOrBlank() || sub.isNullOrBlank()) {
            throw WeiboApiException(
                -2,
                "视频发布需要网页版登录会话：请到「我的」退出登录后重新登录，再试一次。"
            )
        }
        return xsrf
    }

    /**
     * Combines stored weibo.com cookies with the live WebView cookie jar and,
     * when the XSRF token is missing, visits weibo.com once to seed it.
     */
    suspend fun prepareDesktopSession(session: Session): Session = withContext(Dispatchers.IO) {
        val stored = normalizeCookieHeader(session.desktopCookies)
        val live = runCatching { normalizeCookieHeader(desktopCookieProvider()) }
            .getOrDefault("")
        var merged = mergeCookieHeaders(stored, live)
        if (!merged.contains("SUB=")) {
            val mobile = normalizeCookieHeader(session.cookies)
            val passportCookies = mobile.split(";")
                .map { it.trim() }
                .filter { part ->
                    val name = part.substringBefore("=", "")
                    name == "SUB" || name == "SUBP" || name == "SSOLoginState" ||
                        name == "ALF" || name == "SCF" || name == "Apache"
                }
                .joinToString("; ")
            merged = mergeCookieHeaders(merged, passportCookies)
        }
        if (!hasXsrfCookie(merged)) {
            merged = seedDesktopCookies(merged)
        }
        session.copy(desktopCookies = merged)
    }

    private fun hasXsrfCookie(cookies: String): Boolean {
        return Regex("(?:^|;\\s*)XSRF-TOKEN=([^;]+)").find(cookies) != null
    }

    private fun mergeCookieHeaders(base: String, extra: String): String {
        if (base.isBlank()) return normalizeCookieHeader(extra)
        if (extra.isBlank()) return normalizeCookieHeader(base)
        val result = LinkedHashMap<String, String>()
        fun ingest(raw: String) {
            raw.split(";").forEach { part ->
                val item = part.trim()
                if (item.isEmpty()) return@forEach
                val name = item.substringBefore("=", "").trim()
                if (name.isEmpty()) return@forEach
                result[name] = item
            }
        }
        ingest(base)
        ingest(extra)
        return result.values.joinToString("; ")
    }

    private fun seedDesktopCookies(cookies: String): String {
        val request = Request.Builder()
            .url("https://weibo.com/")
            .get()
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Cookie", cookies)
            .build()
        return try {
            videoClient.newCall(request).execute().use { response ->
                var merged = cookies
                response.headers.values("Set-Cookie").forEach { header ->
                    val pair = header.substringBefore(";").trim()
                    if (pair.contains("=")) {
                        merged = mergeCookieHeaders(merged, pair)
                    }
                }
                merged
            }
        } catch (_: IOException) {
            cookies
        }
    }

    private fun appendQuery(url: String, params: List<Pair<String, String>>): String {
        val absolute = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "https://weibo.com$url"
            else -> "https://weibo.com/$url"
        }
        val builder = absolute.toHttpUrl().newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }

    private fun ensureChunkOk(body: String) {
        if (body.isBlank()) return
        val objectValue = runCatching {
            json.parseToJsonElement(body).asObject()
        }.getOrNull() ?: return
        val message = objectValue.stringValue("error", "msg", "message")
        if (message.isBlank() ||
            message.equals("false", ignoreCase = true) ||
            message == "0" ||
            objectValue["ok"].asInt() == 1
        ) {
            return
        }
        if (message.contains("登录")) throw AuthenticationException(message)
        throw WeiboApiException(objectValue["code"].asInt() ?: -1, message)
    }

    private fun executeRaw(client: OkHttpClient, request: Request): String {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw WeiboApiException(-1, networkMessage(error), error)
        }
        response.use {
            val body = try {
                it.body?.string().orEmpty()
            } catch (error: IOException) {
                throw WeiboApiException(-1, "读取微博数据失败，请重试", error)
            }
            if (it.request.url.host.contains("passport") || body.contains("Sina Visitor System")) {
                throw AuthenticationException()
            }
            if (!it.isSuccessful) {
                val message = when (it.code) {
                    429, 418 -> "微博请求过于频繁，请稍后再试"
                    401, 403 -> "微博拒绝了本次请求，请稍后重试或重新登录"
                    in 500..599 -> "微博服务器暂时不可用 (${it.code})"
                    else -> "微博接口请求失败 (${it.code})"
                }
                throw WeiboApiException(it.code, message)
            }
            return body
        }
    }

    private fun md5Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val builder = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            if (value < 0x10) builder.append('0')
            builder.append(Integer.toHexString(value))
        }
        return builder.toString()
    }

    private fun safeFileName(name: String): String {
        val cleaned = name.trim()
            .replace("\"", "")
            .replace("\r", "")
            .replace("\n", "")
        if (cleaned.isBlank()) return "image.jpg"
        return if (cleaned.length > 120) cleaned.takeLast(120) else cleaned
    }

    private fun normalizeCookieHeader(cookies: String): String {
        return cookies.replace("\r", "").replace("\n", "").trim()
    }

    private fun executeFormPost(
        path: String,
        session: Session,
        fields: List<Pair<String, String>>,
        referer: String
    ): JsonElement {
        val token = fetchWriteToken(session)
        val body = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
            add("st", token)
            add("_spr", "screen:1080x2340")
        }.build()
        val url = if (path.startsWith("http")) {
            path
        } else {
            "https://m.weibo.cn${if (path.startsWith("/")) path else "/$path"}"
        }
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
            .header("Cookie", cookieWithXsrf(session.cookies, token))
            .header("X-XSRF-TOKEN", token)
            .build()
        return executeJson(client, request)
    }

    private fun fetchWriteToken(session: Session): String {
        val root = executeMobileJson("/api/config", session.cookies)
        val data = root.asObject()?.get("data").asObject()
        val login = data?.get("login").asBool() == true ||
            data?.stringValue("login").equals("true", ignoreCase = true) ||
            data?.stringValue("uid").orEmpty().isNotBlank()
        if (!login) throw AuthenticationException()
        val freshToken = data?.stringValue("st", "stoken").orEmpty()
        if (freshToken.isNotBlank()) return freshToken
        val cookieToken = Regex("(?:^|;\\s*)XSRF-TOKEN=([^;]+)")
            .find(session.cookies)
            ?.groupValues
            ?.getOrNull(1)
        if (!cookieToken.isNullOrBlank()) return cookieToken
        if (session.xsrfToken.isNotBlank()) return session.xsrfToken
        throw AuthenticationException("登录状态不完整，请重新登录")
    }

    private fun cookieWithXsrf(cookies: String, token: String): String {
        val parts = cookies.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("XSRF-TOKEN=") }
        return (parts + "XSRF-TOKEN=$token").joinToString("; ")
    }

    private fun ensureWriteOk(root: JsonElement, fallback: String) {
        val objectValue = root.asObject() ?: throw WeiboApiException(-1, fallback)
        val ok = objectValue["ok"].asInt()
        if (ok == 1) return
        val message = objectValue.stringValue("msg", "message").ifBlank { fallback }
        if (message.contains("登录")) throw AuthenticationException(message)
        throw WeiboApiException(ok ?: -1, message)
    }

    private fun fetchUserInfo(cookies: String, uid: String): JsonObject? {
        return try {
            val root = executeMobileJson(
                "https://m.weibo.cn/api/container/getIndex?type=uid&value=$uid",
                cookies
            )
            root.asObject()
                ?.get("data").asObject()
                ?.get("userInfo").asObject()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun executeMobileJson(
        pathOrUrl: String,
        cookies: String,
        extraCookies: String? = null
    ): JsonElement {
        val url = if (pathOrUrl.startsWith("http")) {
            pathOrUrl
        } else {
            "https://m.weibo.cn${if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"}"
        }
        val xsrf = Regex("(?:^|;\\s*)XSRF-TOKEN=([^;]+)").find(cookies)?.groupValues?.getOrNull(1)
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://m.weibo.cn/")
            .header("Origin", "https://m.weibo.cn")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("MWeibo-Pwa", "1")
            .header(
                "Cookie",
                buildString {
                    append(cookies.replace("\r", "").replace("\n", "").trim())
                    if (!extraCookies.isNullOrBlank()) {
                        if (isNotEmpty()) append("; ")
                        append(extraCookies.trim())
                    }
                }
            )
        if (!xsrf.isNullOrBlank()) {
            requestBuilder.header("x-xsrf-token", xsrf)
        }
        return executeJson(client, requestBuilder.build())
    }

    private fun executeDesktopJson(url: String): JsonElement {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://weibo.com/")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        return executeJson(client, request)
    }

    private fun executeJson(client: OkHttpClient, request: Request): JsonElement {
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw WeiboApiException(-1, networkMessage(error), error)
        }
        response.use {
            return parseResponse(it)
        }
    }

    private fun parseResponse(response: Response): JsonElement {
        val body = try {
            response.body?.string().orEmpty()
        } catch (error: IOException) {
            throw WeiboApiException(-1, "读取微博数据失败，请重试", error)
        }
        val finalHost = response.request.url.host
        if (finalHost.contains("passport") || body.contains("Sina Visitor System")) {
            throw AuthenticationException()
        }
        if (!response.isSuccessful) {
            val message = when (response.code) {
                429, 418 -> "微博请求过于频繁，请稍后再试"
                403 -> "微博拒绝了本次请求，请稍后重试或重新登录"
                in 500..599 -> "微博服务器暂时不可用 (${response.code})"
                else -> "微博接口请求失败 (${response.code})"
            }
            throw WeiboApiException(response.code, message)
        }
        if (body.isBlank()) throw WeiboApiException(-1, "微博返回了空数据")
        val element = try {
            json.parseToJsonElement(body)
        } catch (error: Exception) {
            if (body.contains("登录", ignoreCase = true) || body.contains("passport")) {
                throw AuthenticationException()
            }
            throw WeiboApiException(-1, "微博返回了无法识别的数据", error)
        }
        val root = element as? JsonObject
        val ok = root?.get("ok").asInt()
        val code = root?.get("code").asInt()
        val message = root?.stringValue("msg", "message").orEmpty()
        if (ok == -100 || code == -100) {
            throw AuthenticationException(if (message.isNotBlank()) message else "登录状态已过期，请重新登录")
        }
        if (ok == 0 && message.contains("登录")) {
            throw AuthenticationException(message)
        }
        return element
    }

    private fun networkMessage(error: IOException): String {
        val text = error.message.orEmpty()
        return when {
            text.contains("timed out", ignoreCase = true) -> "网络连接超时，请检查网络后重试"
            text.contains("Unable to resolve host", ignoreCase = true) -> "无法连接微博，请检查网络"
            else -> "网络请求失败，请检查网络后重试"
        }
    }

    private fun sanitizeCookies(cookies: String): String {
        val cleaned = cookies
            .replace("\r", "")
            .replace("\n", "")
            .trim()
        if (cleaned.isBlank()) throw AuthenticationException("登录信息为空，请重新登录")
        return cleaned
    }

    companion object {
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val DESKTOP_UPLOAD_SOURCE = "339644097"
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        private const val MAX_VIDEO_BYTES = 4L * 1024L * 1024L * 1024L
        private const val MAX_EDIT_TEXT_LENGTH = 2000
        private val OCTET_STREAM = "application/octet-stream".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull

private fun JsonElement?.asBool(): Boolean {
    val primitive = this as? JsonPrimitive ?: return false
    return primitive.booleanOrNull ?: (primitive.content == "1")
}

private fun JsonObject.stringValue(vararg keys: String): String {
    for (key in keys) {
        val primitive = this[key] as? JsonPrimitive ?: continue
        val value = primitive.content
        if (value.isNotBlank() && value != "null") return value
    }
    return ""
}

private fun JsonObject.stringList(key: String): List<String> {
    val array = this[key] as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        (element as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}

private fun JsonObject.longValue(vararg keys: String): Long? {
    for (key in keys) {
        val primitive = this[key] as? JsonPrimitive ?: continue
        primitive.content.toLongOrNull()?.let { return it }
    }
    return null
}
