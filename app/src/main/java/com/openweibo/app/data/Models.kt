package com.openweibo.app.data

data class Session(
    val cookies: String,
    val uid: String,
    val screenName: String,
    val avatarUrl: String,
    val verified: Boolean,
    val followersCount: Long,
    val statusesCount: Long,
    val description: String,
    val xsrfToken: String = "",
    val followingCount: Long = 0L,
    val desktopCookies: String = ""
)

data class WeiboUser(
    val id: String,
    val screenName: String,
    val avatarUrl: String,
    val verified: Boolean = false,
    val verifiedReason: String = "",
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val statusesCount: Long = 0L,
    val description: String = "",
    val following: Boolean = false,
    val followsMe: Boolean = false
) {
    val isMutual: Boolean
        get() = following && followsMe
}

data class WeiboImage(
    val thumbnailUrl: String,
    val originalUrl: String,
    val width: Int = 0,
    val height: Int = 0,
    val pid: String = "",
    val isGif: Boolean = false
) {
    val aspectRatio: Float
        get() {
            if (width <= 0 || height <= 0) return 4f / 3f
            return (width.toFloat() / height.toFloat()).coerceIn(0.58f, 2.2f)
        }
}

data class VideoStream(
    val url: String,
    val label: String = "",
    val quality: Int = 0
) {
    val displayLabel: String
        get() = when {
            quality >= 2160 -> "4K"
            quality > 0 -> "${quality}P"
            label.isNotBlank() -> label.uppercase()
            else -> "AUTO"
        }

    val formatLabel: String
        get() {
            val clean = url.substringBefore('?').lowercase()
            return when {
                clean.endsWith(".m3u8") -> "HLS"
                clean.endsWith(".mp4") -> "MP4"
                clean.contains("weibocdn") || clean.contains("video.sina") -> "CDN"
                else -> "STREAM"
            }
        }

    val isHls: Boolean
        get() = formatLabel == "HLS"
}

data class VideoPreview(
    val coverUrl: String,
    val pageUrl: String,
    val streams: List<VideoStream> = emptyList(),
    val durationSeconds: Int = 0,
    val width: Int = 0,
    val height: Int = 0
) {
    val streamUrl: String
        get() = streams.firstOrNull()?.url.orEmpty()

    val aspectRatio: Float
        get() {
            if (width <= 0 || height <= 0) return 16f / 9f
            return (width.toFloat() / height.toFloat()).coerceIn(0.5f, 2.4f)
        }
}

data class Retweet(
    val id: String,
    val bid: String = "",
    val user: WeiboUser,
    val text: String,
    val source: String,
    val images: List<WeiboImage>,
    val video: VideoPreview?,
    val createdAtMillis: Long = 0L,
    val createdAtRaw: String = "",
    val repostsCount: Int = 0,
    val commentsCount: Int = 0,
    val likesCount: Int = 0,
    val isLongText: Boolean = false,
    val webUrl: String = ""
) {
    fun asPost(): WeiboPost = WeiboPost(
        id = id,
        bid = bid,
        createdAtMillis = createdAtMillis,
        createdAtRaw = createdAtRaw,
        text = text,
        source = source,
        user = user,
        images = images,
        retweet = null,
        repostsCount = repostsCount,
        commentsCount = commentsCount,
        likesCount = likesCount,
        isLongText = isLongText,
        video = video,
        webUrl = webUrl.ifBlank {
            "https://m.weibo.cn/detail/${bid.ifBlank { id }}"
        }
    )
}

data class WeiboPost(
    val id: String,
    val bid: String,
    val createdAtMillis: Long,
    val createdAtRaw: String,
    val text: String,
    val source: String,
    val user: WeiboUser,
    val images: List<WeiboImage>,
    val retweet: Retweet?,
    val repostsCount: Int,
    val commentsCount: Int,
    val likesCount: Int,
    val isLongText: Boolean,
    val video: VideoPreview?,
    val webUrl: String
) {
    val allImages: List<WeiboImage>
        get() = (images + retweet?.images.orEmpty()).distinctBy { it.originalUrl }

    val hasMedia: Boolean
        get() = images.isNotEmpty() || video != null || retweet?.images?.isNotEmpty() == true || retweet?.video != null
}

/**
 * Local notifications emitted after an own post is edited or deleted so every
 * open timeline, profile and search list can update without a full refresh.
 */
sealed interface PostChange {
    data class Deleted(val id: String) : PostChange
    data class Edited(val post: WeiboPost) : PostChange
}

data class FeedPage(
    val posts: List<WeiboPost>,
    val nextCursor: String?,
    val hasMore: Boolean
)

enum class HotCategory(
    val label: String,
    val filterType: String,
    val english: String
) {
    Realtime("热搜", "realtimehot", "REALTIME"),
    Entertainment("文娱", "fun", "ENTERTAINMENT"),
    Social("社会", "social", "SOCIAL"),
    Tech("科技", "technologynav", "TECHNOLOGY"),
    Sports("体育", "sportnav", "SPORTS"),
    Life("生活", "lifenav", "LIFE"),
    Games("游戏", "gamenav", "GAMES")
}

data class HotItem(
    val rank: Int,
    val title: String,
    val heat: Long?,
    val label: String?,
    val isAd: Boolean,
    val url: String
)

data class HotPage(
    val items: List<HotItem>,
    val updatedAt: Long
)

data class WeiboComment(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    val user: WeiboUser,
    val likeCount: Int = 0,
    val floorNumber: Int = 0,
    val replyCount: Int = 0
)

data class CommentCursor(
    val maxId: String,
    val maxIdType: String = "0"
)

data class CommentPage(
    val comments: List<WeiboComment>,
    val nextCursor: CommentCursor?,
    val total: Int,
    val hasMore: Boolean
)

data class WeiboRepost(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    val user: WeiboUser,
    val source: String = "微博"
)

data class RepostPage(
    val reposts: List<WeiboRepost>,
    val total: Int,
    val hasMore: Boolean
)

data class UserPage(
    val users: List<WeiboUser>,
    val total: Int,
    val nextPage: Int,
    val hasMore: Boolean
)

data class SearchPage(
    val posts: List<WeiboPost>,
    val nextPage: Int,
    val hasMore: Boolean
)

data class ProfilePage(
    val user: WeiboUser?,
    val posts: List<WeiboPost>,
    val nextPage: Int,
    val hasMore: Boolean,
    val nextSinceId: String? = null
)

data class SaveResult(
    val saved: Int,
    val failed: Int,
    val firstError: String? = null
)
