package com.openweibo.app.data

import android.net.Uri

/**
 * Visibility values understood by both the mobile composer and the desktop
 * statuses/update endpoint.
 */
enum class ComposeVisibility(
    val value: Int,
    val label: String,
    val hint: String
) {
    Public(0, "公开", "所有人可见"),
    Followers(10, "关注可见", "仅关注我的人可见"),
    Friends(6, "好友可见", "仅互相关注的人可见"),
    Private(1, "仅自己", "仅自己可见")
}

/**
 * A local file selected through the Storage Access Framework. The URI is kept
 * untouched so images are uploaded as their original bytes and GIFs stay
 * animated.
 */
data class MediaFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video/", ignoreCase = true) ||
            name.endsWith(".mkv", ignoreCase = true) ||
            name.endsWith(".flv", ignoreCase = true)

    val isGif: Boolean
        get() = mimeType.equals("image/gif", ignoreCase = true) ||
            name.endsWith(".gif", ignoreCase = true)
}

data class UploadedImage(
    val pid: String,
    val url: String = ""
)

data class UploadedVideo(
    val mediaId: String,
    val title: String = ""
)

/**
 * Status returned by `m.weibo.cn/statuses/show` when the current account is
 * allowed to edit it. Existing picture IDs are sent back unchanged so editing
 * the text does not remove the original images.
 */
data class EditablePost(
    val id: String,
    val text: String,
    val picIds: List<String> = emptyList()
)
