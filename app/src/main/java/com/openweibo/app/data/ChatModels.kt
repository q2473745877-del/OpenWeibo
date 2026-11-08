package com.openweibo.app.data

/**
 * One direct-message conversation row.
 *
 * Comes from `GET m.weibo.cn/message/msglist`, whose entries carry
 * `user{id, screen_name, avatar_large}`, `text`, `created_at` and `unread`.
 */
data class ChatConversation(
    val id: String,
    val uid: String,
    val screenName: String,
    val avatarUrl: String,
    val lastMessage: String,
    val updatedAtMillis: Long,
    val unreadCount: Int
)

/**
 * A single direct message.
 *
 * `GET m.weibo.cn/api/chat/list` messages carry sender/recipient ids, text and a
 * `media_type`, so direction is derived from `sender_id == my uid`.
 */
data class ChatMessage(
    val id: String,
    val fromUid: String,
    val fromName: String,
    val text: String,
    val createdAtMillis: Long,
    val outgoing: Boolean,
    val mediaUrl: String? = null,
    val mediaType: ChatMediaType? = null
)

enum class ChatMediaType { Image, Gif, Video }

/**
 * One page of history. `since_id` pages backwards on Weibo, so [nextCursor] is the
 * oldest message id and should be passed back in to fetch earlier messages.
 */
data class ChatPage(
    val messages: List<ChatMessage>,
    val nextCursor: String?,
    val peerTitle: String = ""
)
