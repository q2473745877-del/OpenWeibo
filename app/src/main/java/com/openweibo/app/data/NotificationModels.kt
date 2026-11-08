package com.openweibo.app.data

/**
 * A notification-style entry shown under the "消息" tab.
 *
 * Weibo exposes these as separate endpoints; [NotificationKind] keeps the UI
 * generic while still letting each tab use its own request shape.
 */
enum class NotificationKind(val label: String, val subtitle: String) {
    DirectMessage("私信", "DIRECT MESSAGES"),
    Mention("提到我的", "MENTIONS"),
    Like("收到的赞", "LIKES"),
    Comment("收到的评论", "COMMENTS RECEIVED")
}

/** One row in the mention / like / comment feeds. */
data class NotificationItem(
    val id: String,
    val kind: NotificationKind,
    val actorName: String,
    val actorAvatar: String,
    val actorUid: String,
    val text: String,
    val sourceText: String,
    val createdAtMillis: Long,
    val post: WeiboPost? = null,
    val commentId: String = "",
    val unread: Boolean = false
)

data class NotificationPage(
    val items: List<NotificationItem>,
    val nextPage: Int?,
    val hasMore: Boolean
)
