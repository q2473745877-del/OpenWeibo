package com.openweibo.app.ui.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweibo.app.data.ChatConversation
import com.openweibo.app.data.NotificationItem
import com.openweibo.app.data.NotificationKind
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.ui.components.Avatar
import com.openweibo.app.ui.components.EmptyState
import com.openweibo.app.ui.components.FilterPill
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.ScreenHeader
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Fires [onLoadMore] when the list nears its end, which is how older pages are
 * appended for every category (see [MessageViewModel.loadMore]).
 */
@Composable
private fun AutoLoadMore(listState: LazyListState, onLoadMore: () -> Unit) {
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
}

@Composable
fun MessageScreen(
    viewModel: MessageViewModel,
    onOpenThread: (uid: String, name: String, avatar: String) -> Unit,
    onOpenNotificationPost: (NotificationItem) -> Unit,
    onOpenUserByUid: (uid: String) -> Unit,
    onLoginExpired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab = state.tab
    val tabs = NotificationKind.entries
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(tab).coerceAtLeast(0)
    ) { tabs.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tab) { viewModel.ensureLoaded(tab) }
    LaunchedEffect(state.authExpired) {
        if (state.authExpired) onLoginExpired()
    }
    // A tab tap animates the pager; a swipe moves the pager and updates the tab.
    LaunchedEffect(pagerState.currentPage) {
        val target = tabs[pagerState.currentPage.coerceIn(0, tabs.lastIndex)]
        if (target != state.tab) viewModel.selectTab(target)
    }

    val slice = state.categories[tab]

    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 840.dp)
    ) {
        ScreenHeader(
            title = "消息",
            subtitle = "MESSAGES / ${tab.subtitle}",
            actions = {
                val busy = if (tab == NotificationKind.DirectMessage) {
                    state.chatLoading
                } else {
                    slice?.loading == true
                }
                if (busy) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            color = colors.textFaint,
                            strokeWidth = 1.5.dp
                        )
                    }
                } else {
                    IconAction(
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "刷新消息",
                        onClick = { viewModel.refreshCurrent() },
                        tint = colors.textDim
                    )
                }
            }
        )
        Hairline()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, kind ->
                FilterPill(
                    label = kind.label,
                    selected = kind == tab,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                )
            }
        }
        Hairline()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val kind = tabs[page.coerceIn(0, tabs.lastIndex)]
            LaunchedEffect(kind) { viewModel.ensureLoaded(kind) }
            when (kind) {
                NotificationKind.DirectMessage -> ChatConversationList(
                    conversations = state.conversations,
                    loading = state.chatLoading,
                    error = state.chatError,
                    onOpenThread = onOpenThread,
                    onRetry = { viewModel.refreshCurrent() },
                    onLoadMore = { viewModel.loadMore() }
                )

                else -> NotificationList(
                    kind = kind,
                    slice = state.categories[kind],
                    onOpenPost = onOpenNotificationPost,
                    onOpenUser = onOpenUserByUid,
                    onRetry = { viewModel.refreshCurrent() },
                    onLoadMore = { viewModel.loadMore() }
                )
            }
        }
    }
}

@Composable
private fun ChatConversationList(
    conversations: List<ChatConversation>,
    loading: Boolean,
    error: String?,
    onOpenThread: (uid: String, name: String, avatar: String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    AutoLoadMore(listState = listState, onLoadMore = onLoadMore)
    when {
        loading && conversations.isEmpty() -> LoadingRows()
        error != null && conversations.isEmpty() -> EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = "私信加载失败",
            body = error,
            actionLabel = "重试",
            onAction = onRetry
        )

        conversations.isEmpty() -> EmptyState(
            icon = Icons.Outlined.MailOutline,
            title = "还没有私信",
            body = "你与他人之间的私信会话会显示在这里。"
        )

        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items = conversations, key = { it.uid + it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = {
                        onOpenThread(
                            conversation.uid,
                            conversation.screenName,
                            conversation.avatarUrl
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationList(
    kind: NotificationKind,
    slice: CategoryState?,
    onOpenPost: (NotificationItem) -> Unit,
    onOpenUser: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    val colors = AppTheme.colors
    val items = slice?.items.orEmpty()
    val listState = rememberLazyListState()
    AutoLoadMore(listState = listState, onLoadMore = onLoadMore)
    when {
        slice == null || (slice.loading && items.isEmpty()) -> LoadingRows()

        slice.error != null && items.isEmpty() -> EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = "${kind.label}加载失败",
            body = slice.error,
            actionLabel = "重试",
            onAction = onRetry
        )

        items.isEmpty() -> EmptyState(
            icon = emptyIcon(kind),
            title = "暂时没有${kind.label}",
            body = emptyBody(kind)
        )

        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items = items, key = { it.id }) { item ->
                NotificationRow(
                    item = item,
                    onClick = { onOpenPost(item) },
                    onOpenUser = { onOpenUser(item.actorUid) }
                )
            }
            if (slice.loadingMore) {
                item(key = "loading-more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = colors.textFaint,
                            strokeWidth = 1.4.dp
                        )
                    }
                }
            } else if (!slice.endReached) {
                // Explicit affordance: AutoLoadMore usually beats the user to it, but
                // tapping guarantees older pages can always be reached.
                item(key = "load-more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onLoadMore)
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载更早的${kind.label}",
                            color = colors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (items.size >= 20) {
                item(key = "end") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        MonoLabel("已经到底了", size = 8.5)
                    }
                }
            }
        }
    }
}

private fun emptyIcon(kind: NotificationKind) = when (kind) {
    NotificationKind.Like -> Icons.Outlined.FavoriteBorder
    NotificationKind.Mention -> Icons.Outlined.MailOutline
    else -> Icons.Outlined.MailOutline
}

private fun emptyBody(kind: NotificationKind) = when (kind) {
    NotificationKind.Mention -> "有人 @ 你时，会出现在这里。"
    NotificationKind.Like -> "别人赞了你的微博后，会出现在这里。"
    NotificationKind.Comment -> "别人评论你的微博后，会出现在这里。"
    NotificationKind.DirectMessage -> "你与他人之间的私信会话会显示在这里。"
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    onClick: () -> Unit,
    onOpenUser: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Avatar(
            url = item.actorAvatar,
            name = item.actorName,
            size = 34.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onOpenUser)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.actorName,
                    color = colors.text,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.unread) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.danger)
                    )
                }
                Spacer(Modifier.weight(1f))
                MonoLabel(TimeUtils.relativeTime(item.createdAtMillis), size = 8)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.sourceText,
                color = colors.textFaint,
                style = monoStyle(8.5, letterSpacing = 0.6)
            )
            if (item.text.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = item.text,
                    color = colors.textDim,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val postText = item.post?.text?.takeIf { it.isNotBlank() }
            if (postText != null) {
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceAlt)
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = postText,
                        color = colors.textFaint,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ChatConversation, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.borderSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            url = conversation.avatarUrl,
            name = conversation.screenName,
            size = 38.dp,
            modifier = Modifier.clip(CircleShape)
        )
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.screenName,
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                MonoLabel(TimeUtils.relativeTime(conversation.updatedAtMillis), size = 8.5)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = conversation.lastMessage.ifBlank { "（暂无消息）" },
                color = colors.textDim,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (conversation.unreadCount > 0) {
            Spacer(Modifier.width(9.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.danger)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = conversation.unreadCount.toString(),
                    color = colors.onDanger,
                    style = monoStyle(8.5)
                )
            }
        }
    }
}

@Composable
private fun LoadingRows() {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface)
            ) {}
        }
    }
}
