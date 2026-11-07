package com.openweibo.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

object WeiboParser {

    fun parseUser(raw: JsonObject?): WeiboUser? {
        if (raw == null) return null
        val screenName = raw.string("screen_name", "screenName", "name").trim()
        if (screenName.isBlank()) return null
        val id = raw.string("idstr", "id", "uid")
        val avatar = normalizeUrl(
            raw.string(
                "avatar_hd",
                "avatar_large",
                "profile_image_url",
                "profile_image_url_https"
            )
        )
        val verifiedType = raw.int("verified_type")
        val verified = raw.boolean("verified") ||
            raw.containsKey("verified_type") && verifiedType >= 0 && verifiedType != 220
        return WeiboUser(
            id = id,
            screenName = screenName,
            avatarUrl = avatar,
            verified = verified,
            verifiedReason = raw.string("verified_reason", "verifiedReason"),
            followersCount = raw.long("followers_count")
                ?: parseCount(raw.string("followers_count", "followers_count_str")),
            followingCount = raw.long("follow_count", "following_count", "friends_count")
                ?: parseCount(raw.string("follow_count", "following_count", "friends_count")),
            statusesCount = raw.long("statuses_count")
                ?: parseCount(raw.string("statuses_count", "statuses_count_str")),
            description = raw.string("description"),
            following = raw.boolean("following"),
            followsMe = raw.boolean("follow_me", "followMe")
        )
    }

    fun parseFeed(root: JsonElement): FeedPage {
        val posts = mutableListOf<WeiboPost>()
        var nextCursor: String? = null
        var hasExplicitMore = false

        when (root) {
            is JsonArray -> {
                val envelope = root.firstOrNull()?.asObjectOrNull()
                if (envelope != null) {
                    nextCursor = envelope.cursorString()
                    collectCards(envelope["card_group"], posts)
                    if (posts.isEmpty()) {
                        collectCards(envelope["cards"], posts)
                    }
                }
            }

            is JsonObject -> {
                val data = root["data"].asObjectOrNull()
                val statusArray = data?.get("statuses").asArrayOrNull()
                    ?: root["statuses"].asArrayOrNull()
                if (statusArray != null) {
                    statusArray.forEach { item ->
                        val objectValue = item.asObjectOrNull() ?: return@forEach
                        val mblog = objectValue["mblog"].asObjectOrNull() ?: objectValue
                        parsePost(mblog)?.let(posts::add)
                    }
                }

                if (posts.isEmpty()) {
                    collectCards(data?.get("cards"), posts)
                }
                if (posts.isEmpty()) {
                    collectCards(root["cards"], posts)
                }

                nextCursor = data?.cursorString()
                    ?: root.cursorString()
                    ?: data?.get("cardlistInfo").asObjectOrNull()?.cursorString()
                    ?: root["cardlistInfo"].asObjectOrNull()?.cursorString()

                hasExplicitMore = data?.get("loadMore")?.asBooleanOrNull()
                    ?: root["loadMore"]?.asBooleanOrNull()
                    ?: false
            }

            else -> Unit
        }

        val distinct = posts
            .distinctBy { it.id }
            .sortedWith(compareByDescending<WeiboPost> { it.createdAtMillis }.thenByDescending { it.id })

        val cursor = nextCursor?.takeIf { it.isNotBlank() && it != "0" && it != "-1" }
        return FeedPage(
            posts = distinct,
            nextCursor = cursor,
            hasMore = cursor != null && (distinct.isNotEmpty() || hasExplicitMore)
        )
    }

    fun parseSearchPage(root: JsonElement, page: Int): SearchPage {
        val posts = mutableListOf<WeiboPost>()
        val data = (root as? JsonObject)?.get("data").asObjectOrNull()
        if (data != null) {
            data["statuses"].asArrayOrNull()?.forEach { item ->
                val objectValue = item.asObjectOrNull() ?: return@forEach
                val mblog = objectValue["mblog"].asObjectOrNull() ?: objectValue
                parsePost(mblog)?.let(posts::add)
            }
            collectSearchCards(data["cards"], posts)
        }
        if (posts.isEmpty()) {
            collectSearchCards((root as? JsonObject)?.get("cards"), posts)
        }
        val distinct = posts.distinctBy { it.id.ifBlank { it.bid } }
        return SearchPage(
            posts = distinct,
            nextPage = page + 1,
            hasMore = distinct.isNotEmpty()
        )
    }

    fun parseProfilePage(root: JsonElement, page: Int, fallbackUser: WeiboUser?): ProfilePage {
        val data = (root as? JsonObject)?.get("data").asObjectOrNull()
            ?: return ProfilePage(null, emptyList(), page + 1, false)
        val posts = mutableListOf<WeiboPost>()
        data["statuses"].asArrayOrNull()?.forEach { item ->
            val objectValue = item.asObjectOrNull() ?: return@forEach
            val mblog = objectValue["mblog"].asObjectOrNull() ?: objectValue
            parsePost(mblog)?.let(posts::add)
        }
        collectCards(data["cards"], posts)
        if (posts.isEmpty()) {
            collectSearchCards(data["cards"], posts)
        }
        val distinct = posts.distinctBy { it.id.ifBlank { it.bid } }
        val sinceId = data["cardlistInfo"].asObjectOrNull()
            ?.string("since_id")
            ?.takeIf { it.isNotBlank() && it != "0" }
        val parsedUser = parseUser(data["userInfo"].asObjectOrNull())
        // The dedicated user-info endpoint carries follow_me/following; prefer its
        // relationship flags because the timeline container may return a reduced userInfo.
        val user = when {
            parsedUser == null -> fallbackUser
            fallbackUser == null -> parsedUser
            parsedUser.id == fallbackUser.id -> parsedUser.copy(
                following = fallbackUser.following,
                followsMe = fallbackUser.followsMe
            )
            else -> parsedUser
        }
        return ProfilePage(
            user = user,
            posts = distinct,
            nextPage = page + 1,
            hasMore = distinct.isNotEmpty(),
            nextSinceId = sinceId
        )
    }

    private fun collectCards(cardsElement: JsonElement?, target: MutableList<WeiboPost>) {
        val cards = cardsElement.asArrayOrNull() ?: return
        cards.forEach { cardElement ->
            val card = cardElement.asObjectOrNull() ?: return@forEach
            card["mblog"].asObjectOrNull()?.let { mblog ->
                parsePost(mblog)?.let(target::add)
            }
            card["card_group"].asArrayOrNull()?.forEach { groupElement ->
                val group = groupElement.asObjectOrNull() ?: return@forEach
                group["mblog"].asObjectOrNull()?.let { mblog ->
                    parsePost(mblog)?.let(target::add)
                }
            }
        }
    }

    private fun collectSearchCards(element: JsonElement?, target: MutableList<WeiboPost>) {
        when (element) {
            is JsonArray -> element.forEach { collectSearchCards(it, target) }
            is JsonObject -> {
                element["mblog"].asObjectOrNull()?.let { mblog ->
                    parsePost(mblog)?.let(target::add)
                }
                element["card_group"]?.let { collectSearchCards(it, target) }
                element["cards"]?.let { collectSearchCards(it, target) }
            }

            else -> Unit
        }
    }

    fun parsePost(raw: JsonObject): WeiboPost? {
        val id = raw.string("idstr", "id", "mid")
        val bid = raw.string("bid", "mblogid")
        if (id.isBlank() && bid.isBlank()) return null

        val user = parseUser(raw["user"].asObjectOrNull()) ?: return null
        val rawText = raw.string("text_raw").ifBlank { raw.string("text") }
        val text = WeiboText.plain(rawText)
        val createdAtRaw = raw.string("created_at", "createdAt")
        val createdAt = weiboCreatedAt(raw)

        val page = raw["page_info"].asObjectOrNull()
        val pageUrl = page?.string("page_url", "scheme").orEmpty()
        val video = parseVideo(page, pageUrl)
        val parsedImages = parseImages(raw).toMutableList()
        if (parsedImages.isEmpty()) {
            parsePageGif(page)?.let(parsedImages::add)
        }
        val images = parsedImages
        val retweetRaw = raw["retweeted_status"].asObjectOrNull()
        val retweet = parseRetweet(retweetRaw)

        val webUrl = raw.string("scheme", "url")
            .takeIf { it.startsWith("http") }
            ?: "https://m.weibo.cn/detail/${bid.ifBlank { id }}"

        return WeiboPost(
            id = id.ifBlank { bid },
            bid = bid,
            createdAtMillis = createdAt,
            createdAtRaw = createdAtRaw,
            text = text,
            source = WeiboText.sourceLabel(raw.string("source")),
            user = user,
            images = images,
            retweet = retweet,
            repostsCount = raw.int("reposts_count", "repostsCount"),
            commentsCount = raw.int("comments_count", "commentsCount"),
            likesCount = raw.int("attitudes_count", "likes_count", "like_counts"),
            isLongText = raw.boolean("isLongText", "is_long_text"),
            video = video,
            webUrl = webUrl
        )
    }

    fun parseCommentPage(root: JsonElement): CommentPage {
        val data = (root as? JsonObject)?.get("data").asObjectOrNull()
            ?: return CommentPage(emptyList(), null, 0, false)
        val rawComments = data["data"].asArrayOrNull()
            ?: data["comments"].asArrayOrNull().orEmpty()
        val comments = rawComments
            .mapNotNull { element -> element.asObjectOrNull()?.let(::parseComment) }
            .distinctBy { it.id }
        val total = data.int("total_number")
        val maxId = data.string("max_id")
        val maxIdType = data.string("max_id_type").ifBlank { "0" }
        val nextCursor = maxId
            .takeIf { it.isNotBlank() && it != "0" }
            ?.let { CommentCursor(maxId = it, maxIdType = maxIdType) }
        return CommentPage(
            comments = comments,
            nextCursor = nextCursor,
            total = total,
            hasMore = nextCursor != null && comments.isNotEmpty()
        )
    }

    fun parseComment(raw: JsonObject): WeiboComment? {
        val user = parseUser(raw["user"].asObjectOrNull()) ?: return null
        val id = raw.string("idstr", "id").ifBlank { raw.string("mid") }
        if (id.isBlank()) return null
        val text = WeiboText.plain(raw.string("text_raw").ifBlank { raw.string("text") })
        return WeiboComment(
            id = id,
            text = text,
            createdAtMillis = weiboCreatedAt(raw),
            user = user,
            likeCount = raw.int("like_count", "like_counts"),
            floorNumber = raw.int("floor_number"),
            replyCount = raw.int("total_number")
        )
    }

    fun parseRepostPage(root: JsonElement): RepostPage {
        val data = (root as? JsonObject)?.get("data").asObjectOrNull()
            ?: return RepostPage(emptyList(), 0, false)
        val rawReposts = data["data"].asArrayOrNull()
            ?: data["statuses"].asArrayOrNull()
            ?: data["list"].asArrayOrNull().orEmpty()
        val reposts = rawReposts
            .mapNotNull { element -> element.asObjectOrNull()?.let(::parseRepost) }
            .distinctBy { it.id }
        val total = data.int("total_number").takeIf { it > 0 } ?: data.int("total")
        return RepostPage(
            reposts = reposts,
            total = total,
            hasMore = reposts.isNotEmpty() && reposts.size >= 10
        )
    }

    private fun parseRepost(raw: JsonObject): WeiboRepost? {
        val user = parseUser(raw["user"].asObjectOrNull()) ?: return null
        val id = raw.string("idstr", "id").ifBlank { raw.string("mid") }
        if (id.isBlank()) return null
        return WeiboRepost(
            id = id,
            text = WeiboText.plain(
                raw.string("text_raw").ifBlank { raw.string("raw_text") }
                    .ifBlank { raw.string("text") }
            ),
            createdAtMillis = weiboCreatedAt(raw),
            user = user,
            source = WeiboText.sourceLabel(raw.string("source"))
        )
    }

    fun parseUserPage(root: JsonElement, page: Int): UserPage {
        val data = (root as? JsonObject)?.get("data").asObjectOrNull()
            ?: return UserPage(emptyList(), 0, page + 1, false)
        val collected = mutableListOf<WeiboUser>()
        val seen = mutableSetOf<String>()

        fun collect(element: JsonElement?) {
            when (element) {
                is JsonArray -> element.forEach(::collect)
                is JsonObject -> {
                    val userObject = element["user"].asObjectOrNull()
                        ?: element.takeIf { it.containsKey("screen_name") }
                    if (userObject != null) {
                        parseUser(userObject)?.let { user ->
                            val key = user.id.ifBlank { user.screenName }
                            if (seen.add(key)) collected += user
                        }
                    }
                    element["card_group"]?.let(::collect)
                    element["cards"]?.let(::collect)
                }

                else -> Unit
            }
        }

        val info = data["cardlistInfo"].asObjectOrNull()
        collect(data["cards"])
        if (info != null) {
            collect(info["cards"])
        }
        val total = info?.int("total")?.takeIf { it > 0 } ?: data.int("total")
        val nextSince = info?.string("since_id").orEmpty().toIntOrNull()
        val pageSize = collected.size.coerceAtLeast(1)
        val moreFromCursor = nextSince != null && nextSince > page
        val moreFromTotal = nextSince == null && (total <= 0 || page * pageSize < total)
        val hasMore = collected.isNotEmpty() && (moreFromCursor || moreFromTotal)
        return UserPage(
            users = collected.distinctBy { it.id.ifBlank { it.screenName } },
            total = total,
            nextPage = nextSince?.takeIf { it > page } ?: (page + 1),
            hasMore = hasMore
        )
    }

    private fun weiboCreatedAt(raw: JsonObject): Long {
        val value = raw.long("created_at_ms", "createdAtMs", "created_at_timestamp")
            ?: TimeUtils.parseWeiboTime(raw.string("created_at", "createdAt"))
        return if (value in 1..9_999_999_999L) value * 1000L else value
    }

    private fun parseRetweet(raw: JsonObject?): Retweet? {
        if (raw == null) return null
        val user = parseUser(raw["user"].asObjectOrNull()) ?: return null
        val text = WeiboText.plain(raw.string("text_raw").ifBlank { raw.string("text") })
        val page = raw["page_info"].asObjectOrNull()
        val id = raw.string("idstr", "id", "mid")
        val bid = raw.string("bid", "mblogid")
        val createdAtRaw = raw.string("created_at", "createdAt")
        val images = parseImages(raw).toMutableList()
        if (images.isEmpty()) {
            parsePageGif(page)?.let(images::add)
        }
        val webUrl = raw.string("scheme", "url")
            .takeIf { it.startsWith("http") }
            ?: "https://m.weibo.cn/detail/${bid.ifBlank { id }}"
        return Retweet(
            id = id.ifBlank { bid },
            bid = bid,
            user = user,
            text = text.ifBlank { "原微博已不可见" },
            source = WeiboText.sourceLabel(raw.string("source")),
            images = images,
            video = parseVideo(page, page?.string("page_url", "scheme").orEmpty()),
            createdAtMillis = weiboCreatedAt(raw),
            createdAtRaw = createdAtRaw,
            repostsCount = raw.int("reposts_count", "repostsCount"),
            commentsCount = raw.int("comments_count", "commentsCount"),
            likesCount = raw.int("attitudes_count", "likes_count", "like_counts"),
            isLongText = raw.boolean("isLongText", "is_long_text"),
            webUrl = webUrl
        )
    }

    private fun parseImages(raw: JsonObject): List<WeiboImage> {
        val result = mutableListOf<WeiboImage>()
        val pics = raw["pics"].asArrayOrNull()
        if (pics != null) {
            pics.forEach { element ->
                val pic = element.asObjectOrNull() ?: return@forEach
                val pid = pic.string("pid")
                val large = pic["large"].asObjectOrNull()
                val rawUrls = listOf(
                    large?.string("url").orEmpty(),
                    pic.string("original_pic"),
                    pic.string("url"),
                    pic.string("bmiddle_pic"),
                    pic.string("thumbnail_pic")
                ).filter { it.isNotBlank() }
                val isGif = pic.boolean("is_gif", "gif") ||
                    pic.string("type").equals("gif", ignoreCase = true) ||
                    rawUrls.any(::isGifUrl)
                val extension = imageExtension(rawUrls, if (isGif) "gif" else "jpg")
                val original = if (isGif) {
                    normalizeUrl(rawUrls.firstOrNull(::isGifUrl).orEmpty())
                        .ifBlank { deriveImage(pid, "large", "gif") }
                } else {
                    normalizeUrl(
                        large?.string("url").orEmpty().ifBlank {
                            pic.string("original_pic").ifBlank {
                                deriveImage(pid, "large", extension)
                            }
                        }
                    )
                }
                if (original.isNotBlank()) {
                    val rawThumb = normalizeUrl(
                        pic.string("url", "thumbnail_pic", "bmiddle_pic").ifBlank {
                            deriveImage(pid, "orj360", extension)
                        }
                    )
                    val thumb = if (isGif && !isGifUrl(rawThumb)) {
                        original
                    } else {
                        rawThumb
                    }
                    val geo = large?.get("geo").asObjectOrNull() ?: pic["geo"].asObjectOrNull()
                    result += WeiboImage(
                        thumbnailUrl = thumb.ifBlank { original },
                        originalUrl = original,
                        width = geo?.int("width") ?: 0,
                        height = geo?.int("height") ?: 0,
                        pid = pid,
                        isGif = isGif || isGifUrl(original)
                    )
                }
            }
        }

        val picInfos = raw["pic_infos"].asObjectOrNull()
        if (picInfos != null) {
            val orderedKeys = raw["pic_ids"].asArrayOrNull()
                ?.mapNotNull { it.asStringOrNull() }
                ?.filter { picInfos.containsKey(it) }
                ?.takeIf { it.isNotEmpty() }
                ?: picInfos.keys.toList()
            orderedKeys.forEach { key ->
                val info = picInfos[key].asObjectOrNull() ?: return@forEach
                val largest = info["largest"].asObjectOrNull()
                val large = info["large"].asObjectOrNull()
                val original = info["original"].asObjectOrNull()
                val bmiddle = info["bmiddle"].asObjectOrNull()
                val thumbnail = info["thumbnail"].asObjectOrNull()
                val rawUrls = listOf(
                    largest?.string("url").orEmpty(),
                    original?.string("url").orEmpty(),
                    large?.string("url").orEmpty(),
                    bmiddle?.string("url").orEmpty(),
                    thumbnail?.string("url").orEmpty()
                ).filter { it.isNotBlank() }
                val isGif = info.boolean("is_gif", "gif") ||
                    info.string("type").equals("gif", ignoreCase = true) ||
                    rawUrls.any(::isGifUrl)
                val extension = imageExtension(rawUrls, if (isGif) "gif" else "jpg")
                val originalUrl = if (isGif) {
                    normalizeUrl(rawUrls.firstOrNull(::isGifUrl).orEmpty())
                        .ifBlank { deriveImage(key, "large", "gif") }
                } else {
                    normalizeUrl(
                        largest?.string("url").orEmpty()
                            .ifBlank { original?.string("url").orEmpty() }
                            .ifBlank { large?.string("url").orEmpty() }
                            .ifBlank { deriveImage(key, "large", extension) }
                    )
                }
                if (originalUrl.isNotBlank()) {
                    val rawThumb = normalizeUrl(
                        bmiddle?.string("url").orEmpty()
                            .ifBlank { thumbnail?.string("url").orEmpty() }
                            .ifBlank { large?.string("url").orEmpty() }
                            .ifBlank { deriveImage(key, "orj360", extension) }
                    )
                    val thumbUrl = if (isGif && !isGifUrl(rawThumb)) {
                        originalUrl
                    } else {
                        rawThumb
                    }
                    val geo = largest?.get("geo").asObjectOrNull()
                        ?: large?.get("geo").asObjectOrNull()
                        ?: info["geo"].asObjectOrNull()
                    result += WeiboImage(
                        thumbnailUrl = thumbUrl.ifBlank { originalUrl },
                        originalUrl = originalUrl,
                        width = geo?.int("width") ?: 0,
                        height = geo?.int("height") ?: 0,
                        pid = key,
                        isGif = isGif || isGifUrl(originalUrl)
                    )
                }
            }
        }

        if (result.isEmpty()) {
            val originalPic = normalizeUrl(raw.string("original_pic"))
            val bmiddlePic = normalizeUrl(raw.string("bmiddle_pic"))
            val thumbnailPic = normalizeUrl(raw.string("thumbnail_pic"))
            if (originalPic.isNotBlank()) {
                val isGif = listOf(originalPic, bmiddlePic, thumbnailPic).any(::isGifUrl)
                result += WeiboImage(
                    thumbnailUrl = if (isGif) {
                        originalPic
                    } else {
                        thumbnailPic.ifBlank { bmiddlePic.ifBlank { originalPic } }
                    },
                    originalUrl = originalPic,
                    isGif = isGif
                )
            }
        }

        if (result.isEmpty()) {
            raw["pic_ids"].asArrayOrNull()?.forEach { element ->
                val pid = element.asStringOrNull().orEmpty()
                if (pid.isNotBlank()) {
                    result += WeiboImage(
                        thumbnailUrl = deriveImage(pid, "orj360", "jpg"),
                        originalUrl = deriveImage(pid, "large", "jpg"),
                        pid = pid
                    )
                }
            }
        }

        return result.distinctBy { it.originalUrl }
    }

    private fun parsePageGif(page: JsonObject?): WeiboImage? {
        if (page == null) return null
        val type = page.string("object_type", "objectType", "type")
        if (!type.contains("gif", ignoreCase = true)) return null
        val candidates = mutableListOf<String>()
        page["urls"].asObjectOrNull()?.values?.forEach { element ->
            (element as? JsonPrimitive)?.content?.let(candidates::add)
        }
        candidates += pageCoverUrl(page)
        candidates += page.string("page_url", "scheme")
        candidates += page.string("object_id")
        val gifUrl = candidates
            .map(::normalizeUrl)
            .firstOrNull(::isGifUrl)
        if (gifUrl != null) {
            return WeiboImage(
                thumbnailUrl = gifUrl,
                originalUrl = gifUrl,
                isGif = true
            )
        }
        val cover = pageCoverUrl(page)
        if (cover.isBlank()) return null
        return WeiboImage(
            thumbnailUrl = cover,
            originalUrl = cover
        )
    }

    private data class VideoCandidate(
        val url: String,
        val quality: Int,
        val label: String
    )

    private fun parseVideo(page: JsonObject?, pageUrl: String): VideoPreview? {
        if (page == null) return null
        val objectType = page.string("object_type", "objectType")
        val pageLink = page.string("page_url", "scheme").ifBlank { pageUrl }
        val media = page["media_info"].asObjectOrNull()
        val urls = page["urls"].asObjectOrNull()
        val candidates = mutableListOf<VideoCandidate>()

        fun addCandidate(rawUrl: String?, quality: Int, label: String = "") {
            val url = normalizeUrl(rawUrl)
            if (url.isBlank()) return
            val lower = url.lowercase()
            if (!lower.contains(".mp4") &&
                !lower.contains(".m3u8") &&
                !lower.contains("weibocdn") &&
                !lower.contains("video.sina")
            ) {
                return
            }
            val resolvedQuality = quality.takeIf { it > 0 } ?: qualityFromText(label, url)
            candidates += VideoCandidate(url, resolvedQuality, label)
        }

        val qualityKeys = linkedMapOf(
            "mp4_2160p_mp4" to 2160,
            "mp4_1080p_mp4" to 1080,
            "mp4_720p_mp4" to 720,
            "mp4_hd_mp4" to 720,
            "mp4_hd_url" to 720,
            "stream_url_hd" to 720,
            "mp4_sd_mp4" to 480,
            "mp4_sd_url" to 480,
            "stream_url" to 480,
            "mp4_ld_mp4" to 360,
            "mp4_ld_url" to 360
        )
        listOf(urls, media, page).forEach { objectValue ->
            if (objectValue == null) return@forEach
            qualityKeys.forEach { (key, quality) ->
                addCandidate(objectValue.string(key), quality, key)
            }
        }

        val playback = media?.get("playback_list").asArrayOrNull()
            ?: page["playback_list"].asArrayOrNull()
        playback?.forEach { element ->
            val item = element.asObjectOrNull() ?: return@forEach
            val meta = item["meta"].asObjectOrNull()
            val playInfo = item["play_info"].asObjectOrNull() ?: item
            val label = playInfo.string("label")
                .ifBlank { meta?.string("label").orEmpty() }
            val description = meta?.string("quality_desc").orEmpty()
                .ifBlank { playInfo.string("quality_desc") }
            val textQuality = qualityFromText(label, description, playInfo.string("url"))
            val quality = if (textQuality > 0) {
                textQuality
            } else {
                meta?.int("quality_index")?.takeIf { it > 0 } ?: 0
            }
            addCandidate(playInfo.string("url", "stream_url"), quality, label.ifBlank { description })
        }

        urls?.forEach { (key, element) ->
            if (!key.contains("mp4", ignoreCase = true) &&
                !key.contains("stream", ignoreCase = true) &&
                !key.contains("hls", ignoreCase = true)
            ) {
                return@forEach
            }
            val url = element.asStringOrNull().orEmpty()
            addCandidate(url, qualityFromText(key, url), key)
        }

        media?.forEach { (key, element) ->
            val url = element.asStringOrNull().orEmpty()
            val looksPlayable = url.contains(".mp4", ignoreCase = true) ||
                url.contains(".m3u8", ignoreCase = true) ||
                url.contains("label=mp4", ignoreCase = true)
            if (looksPlayable) {
                addCandidate(url, qualityFromText(key, url), key)
            }
        }

        val hasVideoMedia = media != null && (
            media["playback_list"].asArrayOrNull() != null ||
                media.string(
                    "stream_url",
                    "stream_url_hd",
                    "mp4_720p_mp4",
                    "mp4_hd_url",
                    "mp4_sd_url",
                    "mp4_ld_url"
                ).isNotBlank()
            )
        val looksLikeVideo = objectType == "11" ||
            objectType.contains("video", ignoreCase = true) ||
            pageLink.contains("video.weibo", ignoreCase = true) ||
            pageLink.contains("video.sina", ignoreCase = true) ||
            hasVideoMedia || candidates.isNotEmpty()
        if (!looksLikeVideo) return null

        val streams = candidates
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<VideoCandidate> { it.quality }
                    .thenByDescending { if (it.url.contains(".mp4", ignoreCase = true)) 1 else 0 }
            )
            .let { sorted ->
                // Keep one source per resolution so the in-player picker stays readable.
                val seenQuality = mutableSetOf<Int>()
                sorted.filter { candidate ->
                    candidate.quality <= 0 || seenQuality.add(candidate.quality)
                }
            }
            .take(8)
            .map { candidate ->
                VideoStream(
                    url = candidate.url,
                    label = candidate.label.ifBlank {
                        if (candidate.quality > 0) "${candidate.quality}P" else ""
                    },
                    quality = candidate.quality
                )
            }

        val rawDuration = media?.long("duration")
            ?: page.long("duration")
            ?: 0L
        val seconds = when {
            rawDuration <= 0L -> 0
            rawDuration > 10_000L -> (rawDuration / 1_000L).toInt()
            else -> rawDuration.toInt()
        }
        val width = media?.int("width", "video_width") ?: 0
        val height = media?.int("height", "video_height") ?: 0

        return VideoPreview(
            coverUrl = pageCoverUrl(page),
            pageUrl = normalizeUrl(pageLink),
            streams = streams,
            durationSeconds = seconds,
            width = width,
            height = height
        )
    }

    private fun pageCoverUrl(page: JsonObject): String {
        val direct = when (val pagePic = page["page_pic"]) {
            is JsonObject -> pagePic.string("url")
            is JsonPrimitive -> pagePic.asStringOrNull().orEmpty()
            else -> ""
        }
        if (direct.isNotBlank()) return normalizeUrl(direct)
        val media = page["media_info"].asObjectOrNull()
        val bigPic = media?.get("big_pic_info").asObjectOrNull()
            ?.get("pic_big").asObjectOrNull()
            ?: page["big_pic_info"].asObjectOrNull()?.get("pic_big").asObjectOrNull()
        return normalizeUrl(
            bigPic?.string("url").orEmpty()
                .ifBlank { media?.string("cover", "cover_url", "page_pic").orEmpty() }
        )
    }

    private fun qualityFromText(vararg values: String): Int {
        val text = values.joinToString(" ").lowercase()
        return when {
            text.contains("2160") || text.contains("4k") -> 2160
            text.contains("1080") -> 1080
            text.contains("720") || text.contains("hd") -> 720
            text.contains("540") -> 540
            text.contains("480") || text.contains("sd") -> 480
            text.contains("360") || text.contains("ld") -> 360
            else -> 0
        }
    }

    fun parseHotMobile(root: JsonElement): List<HotItem> {
        val data = (root as? JsonObject)?.get("data").asObjectOrNull() ?: return emptyList()
        val cards = data["cards"].asArrayOrNull() ?: return emptyList()
        val rawItems = mutableListOf<JsonObject>()
        cards.forEach { cardElement ->
            val card = cardElement.asObjectOrNull() ?: return@forEach
            val groups = card["card_group"].asArrayOrNull()
            if (groups != null) {
                groups.forEach { groupElement ->
                    groupElement.asObjectOrNull()?.let(rawItems::add)
                }
            } else if (card.containsKey("desc") || card.containsKey("word")) {
                rawItems += card
            }
        }

        val items = mutableListOf<HotItem>()
        rawItems.forEachIndexed { index, item ->
            val title = item.string("desc", "word", "note").trim()
            if (title.isBlank()) return@forEachIndexed
            val scheme = item.string("scheme", "url")
            val rankText = Regex("[?&]realpos=(\\d+)").find(scheme)?.groupValues?.getOrNull(1)
            val rank = rankText?.toIntOrNull() ?: (index + 1)
            val heat = item.long("desc_extr", "num", "raw_hot")
                ?.takeIf { it > 0L }
            val isAd = item.containsKey("promotion") ||
                scheme.contains("is_ad_pos=1") ||
                scheme.contains("adid=")
            val label = when {
                isAd -> "推广"
                item.string("label_name").isNotBlank() -> item.string("label_name")
                item.string("icon_desc").isNotBlank() -> item.string("icon_desc")
                item.string("flag").isNotBlank() -> item.string("flag")
                else -> null
            }
            val url = if (scheme.startsWith("http")) {
                scheme
            } else {
                "https://m.weibo.cn/search?containerid=" +
                    java.net.URLEncoder.encode("100103type=1&q=$title", "UTF-8")
            }
            items += HotItem(
                rank = rank,
                title = title,
                heat = heat,
                label = label,
                isAd = isAd,
                url = url
            )
        }

        return items
            .distinctBy { it.title }
            .sortedBy { it.rank }
            .mapIndexed { index, item -> item.copy(rank = index + 1) }
    }

    fun parseHotDesktop(root: JsonElement): List<HotItem> {
        val realtime = (root as? JsonObject)
            ?.get("data").asObjectOrNull()
            ?.get("realtime").asArrayOrNull()
            ?: return emptyList()
        val items = mutableListOf<HotItem>()
        realtime.forEachIndexed { index, element ->
            val item = element.asObjectOrNull() ?: return@forEachIndexed
            val word = item.string("word", "note").trim()
            if (word.isBlank()) return@forEachIndexed
            val rank = item.int("rank").takeIf { it > 0 } ?: (index + 1)
            val heat = item.long("num", "raw_hot")?.takeIf { it > 0L }
            val isAd = item.boolean("is_ad") || item.string("label_name") == "商单"
            val label = item.string("label_name").ifBlank { if (isAd) "推广" else "" }
                .ifBlank { null }
            items += HotItem(
                rank = rank,
                title = word,
                heat = heat,
                label = label,
                isAd = isAd,
                url = "https://s.weibo.com/weibo?q=" +
                    java.net.URLEncoder.encode("#$word#", "UTF-8")
            )
        }
        return items
            .distinctBy { it.title }
            .sortedBy { it.rank }
            .mapIndexed { index, item -> item.copy(rank = index + 1) }
    }

    private fun deriveImage(pid: String, size: String, extension: String): String {
        if (pid.isBlank()) return ""
        var cleanPid = pid.trim().substringBefore('?')
        listOf(".jpg", ".jpeg", ".png", ".gif", ".webp").forEach { suffix ->
            if (cleanPid.endsWith(suffix, ignoreCase = true)) {
                cleanPid = cleanPid.dropLast(suffix.length)
                return@forEach
            }
        }
        if (cleanPid.isBlank()) return ""
        val safeExtension = when (extension.lowercase()) {
            "gif", "png", "webp" -> extension.lowercase()
            else -> "jpg"
        }
        return "https://wx1.sinaimg.cn/$size/$cleanPid.$safeExtension"
    }

    private fun imageExtension(urls: List<String>, fallback: String): String {
        urls.forEach { url ->
            val extension = url.substringBefore('?')
                .substringAfterLast('.', "")
                .lowercase()
            if (extension in setOf("jpg", "jpeg", "png", "gif", "webp")) {
                return if (extension == "jpeg") "jpg" else extension
            }
        }
        return fallback
    }

    private fun isGifUrl(url: String): Boolean {
        return url.substringBefore('?').endsWith(".gif", ignoreCase = true)
    }

    private fun parseCount(value: String): Long {
        val text = value.trim()
        if (text.isBlank()) return 0L
        text.toLongOrNull()?.let { return it }
        val number = Regex("([0-9]+(?:\\.[0-9]+)?)")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?: return 0L
        val multiplier = when {
            text.contains("亿") -> 100_000_000.0
            text.contains("万") -> 10_000.0
            else -> 1.0
        }
        return (number * multiplier).toLong()
    }

    private fun normalizeUrl(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
            else -> trimmed
        }
    }

    private fun JsonObject.cursorString(): String? {
        val keys = listOf("next_cursor", "since_id", "max_id", "nextCursor")
        for (key in keys) {
            val value = string(key)
            if (value.isNotBlank() && value != "null") return value
        }
        return null
    }
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.content

private fun JsonElement?.asBooleanOrNull(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.booleanOrNull
}

private fun JsonObject.string(vararg keys: String): String {
    for (key in keys) {
        val value = this[key] ?: continue
        if (value is JsonPrimitive) {
            val text = value.content
            if (text.isNotBlank() && text != "null") return text
        }
    }
    return ""
}

private fun JsonObject.int(vararg keys: String): Int {
    for (key in keys) {
        val value = this[key] as? JsonPrimitive ?: continue
        value.intOrNull?.let { return it }
        value.content.toDoubleOrNull()?.let { return it.toInt() }
    }
    return 0
}

private fun JsonObject.long(vararg keys: String): Long? {
    for (key in keys) {
        val value = this[key] as? JsonPrimitive ?: continue
        value.longOrNull?.let { return it }
        value.content.toLongOrNull()?.let { return it }
    }
    return null
}

private fun JsonObject.boolean(vararg keys: String): Boolean {
    for (key in keys) {
        when (val value = this[key]) {
            is JsonPrimitive -> {
                value.booleanOrNull?.let { return it }
                if (value.content == "1") return true
                if (value.content == "0") return false
            }
            else -> Unit
        }
    }
    return false
}

private fun JsonObject.hasVideoPage(): Boolean = false
