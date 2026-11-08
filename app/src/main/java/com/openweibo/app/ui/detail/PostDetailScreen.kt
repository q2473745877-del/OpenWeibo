package com.openweibo.app.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.data.VideoPreview
import com.openweibo.app.data.WeiboComment
import com.openweibo.app.data.WeiboImage
import com.openweibo.app.data.WeiboPost
import com.openweibo.app.data.WeiboRepost
import com.openweibo.app.data.WeiboUser
import com.openweibo.app.ui.components.Avatar
import com.openweibo.app.ui.components.EmptyState
import com.openweibo.app.ui.components.FilterPill
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.OutlineButton
import com.openweibo.app.ui.components.PrimaryButton
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.feed.PostCard
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.Geist
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.launch

@Composable
fun PostDetailScreen(
    post: WeiboPost,
    viewModel: PostDetailViewModel,
    initialTab: DetailTab,
    onClose: () -> Unit,
    onOpenImages: (String, List<WeiboImage>, Int, String) -> Unit,
    onSaveImages: (String, List<WeiboImage>) -> Unit,
    onOpenVideo: (VideoPreview, String) -> Unit,
    onOpenUser: (WeiboUser) -> Unit,
    onOpenPost: (WeiboPost) -> Unit,
    currentUid: String,
    onManagePost: (WeiboPost) -> Unit,
    onSnackbar: (String) -> Unit,
    onLoginExpired: () -> Unit
) {
    val colors = AppTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    var commentDraft by rememberSaveable { mutableStateOf("") }
    var repostDraft by rememberSaveable { mutableStateOf("") }
    var showRepostDialog by remember { mutableStateOf(false) }
    val commentsListState = rememberLazyListState()
    val repostsListState = rememberLazyListState()
    val pagerState = rememberPagerState(
        initialPage = if (initialTab == DetailTab.Comments) 0 else 1
    ) { 2 }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.start(initialTab)
    }
    LaunchedEffect(pagerState.currentPage) {
        val targetTab = if (pagerState.currentPage == 0) {
            DetailTab.Comments
        } else {
            DetailTab.Reposts
        }
        if (state.tab != targetTab) {
            viewModel.selectTab(targetTab)
        }
        if (targetTab == DetailTab.Reposts) {
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(state.authExpired) {
        if (state.authExpired) onLoginExpired()
    }
    LaunchedEffect(state.feedback) {
        state.feedback?.let { message ->
            onSnackbar(message)
            viewModel.consumeFeedback()
        }
    }
    LaunchedEffect(state.commentResetToken) {
        if (state.commentResetToken > 0) commentDraft = ""
    }

    fun selectTab(tab: DetailTab) {
        viewModel.selectTab(tab)
        scope.launch {
            pagerState.animateScrollToPage(
                if (tab == DetailTab.Comments) 0 else 1
            )
        }
    }

    BackHandler(enabled = !showRepostDialog, onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        DetailTopBar(
            title = "微博详情",
            subtitle = "@${post.user.screenName}",
            busy = when (state.tab) {
                DetailTab.Comments -> state.comments.loading
                DetailTab.Reposts -> state.reposts.loading
            },
            onClose = onClose,
            onRefresh = { viewModel.refresh() },
            onRepost = { showRepostDialog = true }
        )
        Hairline()

        // The post itself lives outside the pager so switching between comments and
        // reposts never slides the content being read.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            PostCard(
                post = post,
                onOpenImages = onOpenImages,
                onSaveImages = onSaveImages,
                onSnackbar = onSnackbar,
                onOpenComments = { _ -> },
                onOpenReposts = { _ -> selectTab(DetailTab.Reposts) },
                onOpenVideo = onOpenVideo,
                onOpenUser = onOpenUser,
                onOpenOriginal = onOpenPost,
                currentUid = currentUid,
                onManagePost = onManagePost
            )
        }

        DetailTabs(
            selected = state.tab,
            commentCount = if (state.comments.total > 0) state.comments.total else post.commentsCount,
            repostCount = if (state.reposts.total > 0) state.reposts.total else post.repostsCount,
            onSelect = ::selectTab
        )
        Hairline()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> CommentList(
                    listState = commentsListState,
                    state = state.comments,
                    selectedTab = state.tab,
                    onSelectTab = ::selectTab,
                    onOpenImages = onOpenImages,
                    onSaveImages = onSaveImages,
                    onOpenVideo = onOpenVideo,
                    onOpenUser = onOpenUser,
                    onOpenPost = onOpenPost,
                    currentUid = currentUid,
                    onManagePost = onManagePost,
                    onSnackbar = onSnackbar,
                    onOpenReposts = { selectTab(DetailTab.Reposts) },
                    onRetry = { viewModel.refresh() },
                    onLoadMore = { viewModel.loadMore() }
                )

                else -> RepostList(
                    listState = repostsListState,
                    state = state.reposts,
                    selectedTab = state.tab,
                    onSelectTab = ::selectTab,
                    onOpenImages = onOpenImages,
                    onSaveImages = onSaveImages,
                    onOpenVideo = onOpenVideo,
                    onOpenUser = onOpenUser,
                    onOpenPost = onOpenPost,
                    currentUid = currentUid,
                    onManagePost = onManagePost,
                    onSnackbar = onSnackbar,
                    onOpenComments = { selectTab(DetailTab.Comments) },
                    onRetry = { viewModel.refresh() },
                    onLoadMore = { viewModel.loadMore() }
                )
            }
        }

        when (state.tab) {
            DetailTab.Comments -> CommentComposer(
                value = commentDraft,
                onValueChange = { commentDraft = it.take(140) },
                posting = state.postingComment,
                onSend = {
                    if (commentDraft.isNotBlank()) {
                        viewModel.submitComment(commentDraft)
                    }
                }
            )

            DetailTab.Reposts -> RepostActionBar(
                posting = state.postingRepost,
                onRepost = { showRepostDialog = true }
            )
        }
    }

    if (showRepostDialog) {
        RepostDialog(
            post = post,
            value = repostDraft,
            onValueChange = { repostDraft = it.take(140) },
            posting = state.postingRepost,
            onDismiss = { showRepostDialog = false },
            onConfirm = {
                showRepostDialog = false
                viewModel.submitRepost(repostDraft)
            }
        )
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    subtitle: String,
    busy: Boolean,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onRepost: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconAction(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "返回",
            onClick = onClose,
            tint = colors.textDim
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text,
                fontSize = 16.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
            MonoLabel(subtitle, size = 8.5)
        }
        if (busy) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.textFaint,
                    strokeWidth = 1.5.dp
                )
            }
        } else {
            IconAction(
                icon = Icons.Outlined.Refresh,
                contentDescription = "刷新",
                onClick = onRefresh,
                tint = colors.textFaint,
                iconSize = 17.dp
            )
        }
        IconAction(
            icon = Icons.Outlined.Repeat,
            contentDescription = "转发这条微博",
            onClick = onRepost,
            tint = colors.textDim,
            iconSize = 18.dp
        )
    }
}

@Composable
private fun DetailTabs(
    selected: DetailTab,
    commentCount: Int,
    repostCount: Int,
    onSelect: (DetailTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterPill(
            label = "评论 ${TimeUtils.formatCount(commentCount)}",
            selected = selected == DetailTab.Comments,
            onClick = { onSelect(DetailTab.Comments) }
        )
        FilterPill(
            label = "转发 ${TimeUtils.formatCount(repostCount)}",
            selected = selected == DetailTab.Reposts,
            onClick = { onSelect(DetailTab.Reposts) }
        )
        Spacer(Modifier.weight(1f))
        MonoLabel(
            text = if (selected == DetailTab.Comments) "COMMENTS" else "REPOSTS",
            size = 8.5
        )
    }
}

@Composable
private fun CommentList(
    listState: LazyListState,
    state: CommentListState,
    selectedTab: DetailTab,
    onSelectTab: (DetailTab) -> Unit,
    onOpenImages: (String, List<WeiboImage>, Int, String) -> Unit,
    onSaveImages: (String, List<WeiboImage>) -> Unit,
    onOpenVideo: (VideoPreview, String) -> Unit,
    onOpenUser: (WeiboUser) -> Unit,
    onOpenPost: (WeiboPost) -> Unit,
    currentUid: String,
    onManagePost: (WeiboPost) -> Unit,
    onSnackbar: (String) -> Unit,
    onOpenReposts: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    AutoLoadMore(
        listState = listState,
        enabled = state.items.isNotEmpty() && !state.loading && !state.loadingMore &&
            !state.endReached && state.loadMoreError == null,
        onLoadMore = onLoadMore
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            state.loading && state.items.isEmpty() -> {
                item(key = "comments-loading", contentType = "state") {
                    LoadingPanel("正在加载评论")
                }
            }

            state.error != null && state.items.isEmpty() -> {
                item(key = "comments-error", contentType = "state") {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "评论暂时不可用",
                        body = state.error.orEmpty(),
                        actionLabel = "重试",
                        onAction = onRetry
                    )
                }
            }

            state.items.isEmpty() -> {
                item(key = "comments-empty", contentType = "state") {
                    EmptyState(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "还没有评论",
                        body = "来抢第一条评论，或者稍后刷新看看。"
                    )
                }
            }

            else -> {
                items(
                    items = state.items,
                    key = { it.id },
                    contentType = { "comment" }
                ) { comment ->
                    CommentRow(comment = comment, onOpenUser = onOpenUser)
                    Hairline(
                        modifier = Modifier.padding(start = 44.dp),
                        color = AppTheme.colors.borderSoft
                    )
                }
            }
        }

        if (state.items.isNotEmpty()) {
            item(key = "comments-footer", contentType = "footer") {
                DetailFooter(
                    loadingMore = state.loadingMore,
                    loadMoreError = state.loadMoreError,
                    endReached = state.endReached,
                    endLabel = "END OF COMMENTS",
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun RepostList(
    listState: LazyListState,
    state: RepostListState,
    selectedTab: DetailTab,
    onSelectTab: (DetailTab) -> Unit,
    onOpenImages: (String, List<WeiboImage>, Int, String) -> Unit,
    onSaveImages: (String, List<WeiboImage>) -> Unit,
    onOpenVideo: (VideoPreview, String) -> Unit,
    onOpenUser: (WeiboUser) -> Unit,
    onOpenPost: (WeiboPost) -> Unit,
    currentUid: String,
    onManagePost: (WeiboPost) -> Unit,
    onSnackbar: (String) -> Unit,
    onOpenComments: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    AutoLoadMore(
        listState = listState,
        enabled = state.items.isNotEmpty() && !state.loading && !state.loadingMore &&
            !state.endReached && state.loadMoreError == null,
        onLoadMore = onLoadMore
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            state.loading && state.items.isEmpty() -> {
                item(key = "reposts-loading", contentType = "state") {
                    LoadingPanel("正在加载转发")
                }
            }

            state.error != null && state.items.isEmpty() -> {
                item(key = "reposts-error", contentType = "state") {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "转发列表暂时不可用",
                        body = state.error.orEmpty(),
                        actionLabel = "重试",
                        onAction = onRetry
                    )
                }
            }

            state.items.isEmpty() -> {
                item(key = "reposts-empty", contentType = "state") {
                    EmptyState(
                        icon = Icons.Outlined.Repeat,
                        title = "还没有人转发",
                        body = "成为第一个转发这条微博的人。"
                    )
                }
            }

            else -> {
                items(
                    items = state.items,
                    key = { it.id },
                    contentType = { "repost" }
                ) { repost ->
                    RepostRow(repost = repost, onOpenUser = onOpenUser)
                    Hairline(
                        modifier = Modifier.padding(start = 44.dp),
                        color = AppTheme.colors.borderSoft
                    )
                }
            }
        }

        if (state.items.isNotEmpty()) {
            item(key = "reposts-footer", contentType = "footer") {
                DetailFooter(
                    loadingMore = state.loadingMore,
                    loadMoreError = state.loadMoreError,
                    endReached = state.endReached,
                    endLabel = "END OF REPOSTS",
                    onLoadMore = onLoadMore
                )
            }
        }
    }
}

@Composable
private fun CommentRow(comment: WeiboComment, onOpenUser: (WeiboUser) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(
            url = comment.user.avatarUrl,
            name = comment.user.screenName,
            size = 32.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onOpenUser(comment.user) }
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user.screenName,
                    color = colors.textDim,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { onOpenUser(comment.user) }
                )
                if (comment.user.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "已认证",
                        tint = colors.highlight,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                MonoLabel(TimeUtils.relativeTime(comment.createdAtMillis), size = 8.5)
            }
            if (comment.text.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = comment.text,
                    color = colors.text,
                    fontFamily = Geist,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
            if (comment.likeCount > 0 || comment.floorNumber > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (comment.likeCount > 0) {
                        MonoLabel("赞 ${TimeUtils.formatCount(comment.likeCount)}", size = 8)
                    }
                    if (comment.floorNumber > 0) {
                        if (comment.likeCount > 0) Spacer(Modifier.width(10.dp))
                        MonoLabel("${comment.floorNumber} 楼", size = 8)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepostRow(repost: WeiboRepost, onOpenUser: (WeiboUser) -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(
            url = repost.user.avatarUrl,
            name = repost.user.screenName,
            size = 32.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onOpenUser(repost.user) }
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = repost.user.screenName,
                    color = colors.textDim,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { onOpenUser(repost.user) }
                )
                if (repost.user.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "已认证",
                        tint = colors.highlight,
                        modifier = Modifier.size(11.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                MonoLabel(TimeUtils.relativeTime(repost.createdAtMillis), size = 8.5)
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = repost.text.ifBlank { "转发微博" },
                color = if (repost.text.isBlank()) colors.textFaint else colors.text,
                fontFamily = Geist,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            if (repost.source.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                MonoLabel(repost.source, size = 8)
            }
        }
    }
}

@Composable
private fun AutoLoadMore(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit
) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val shouldLoadMore by remember {
        derivedStateOf {
            if (!currentEnabled) return@derivedStateOf false
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) currentOnLoadMore()
    }
}

@Composable
private fun LoadingPanel(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appPanel()
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = AppTheme.colors.textFaint,
            strokeWidth = 1.5.dp
        )
        Spacer(Modifier.width(9.dp))
        MonoLabel(label, size = 9)
    }
}

@Composable
private fun DetailFooter(
    loadingMore: Boolean,
    loadMoreError: String?,
    endReached: Boolean,
    endLabel: String,
    onLoadMore: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            loadingMore -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = colors.textFaint,
                        strokeWidth = 1.4.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    MonoLabel("LOADING", size = 8.5)
                }
            }

            loadMoreError != null -> {
                Text(
                    text = loadMoreError,
                    color = colors.textFaint,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "重试",
                    color = colors.textDim,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onLoadMore)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            endReached -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .height(1.dp)
                            .width(26.dp)
                            .background(colors.borderSoft)
                    )
                    Spacer(Modifier.width(9.dp))
                    MonoLabel(endLabel, size = 8)
                    Spacer(Modifier.width(9.dp))
                    Box(
                        Modifier
                            .height(1.dp)
                            .width(26.dp)
                            .background(colors.borderSoft)
                    )
                }
            }

            else -> {
                OutlineButton(label = "加载更多", onClick = onLoadMore)
            }
        }
    }
}

@Composable
private fun CommentComposer(
    value: String,
    onValueChange: (String) -> Unit,
    posting: Boolean,
    onSend: () -> Unit
) {
    val colors = AppTheme.colors
    val canSend = value.isNotBlank() && !posting
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chrome)
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp, max = 112.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceAlt)
                    .border(1.dp, colors.borderSoft, RoundedCornerShape(10.dp))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                textStyle = TextStyle(
                    color = colors.text,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = Geist
                ),
                cursorBrush = SolidColor(colors.text),
                maxLines = 4,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "写评论…",
                                color = colors.textFaint,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                MonoLabel("${value.length}/140", size = 8)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 58.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (canSend) colors.accent else colors.surfaceAlt)
                        .then(
                            if (canSend) Modifier.clickable(onClick = onSend) else Modifier
                        )
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (posting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = colors.textDim,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Text(
                            text = "发送",
                            color = if (canSend) colors.onAccent else colors.textFaint,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepostActionBar(posting: Boolean, onRepost: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chrome)
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoLabel("REPOST THIS POST", size = 8.5)
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                label = if (posting) "正在转发…" else "转发",
                onClick = onRepost,
                enabled = !posting,
                loading = posting,
                modifier = Modifier.width(112.dp)
            )
        }
    }
}

@Composable
private fun RepostDialog(
    post: WeiboPost,
    value: String,
    onValueChange: (String) -> Unit,
    posting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = AppTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 430.dp)
                    .fillMaxWidth()
                    .appPanel(color = colors.surfaceRaised)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp)
            ) {
                Text(
                    text = "转发微博",
                    color = colors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                MonoLabel("REPOST / @${post.user.screenName}", size = 8.5)
                Spacer(Modifier.height(13.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.surfaceAlt)
                        .border(1.dp, colors.borderSoft, RoundedCornerShape(9.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = post.text.ifBlank { "转发微博" },
                        color = colors.textDim,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceAlt)
                        .border(1.dp, colors.borderSoft, RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 10.dp),
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontFamily = Geist
                    ),
                    cursorBrush = SolidColor(colors.text),
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    text = "说点什么…（可留空直接转发）",
                                    color = colors.textFaint,
                                    fontSize = 12.5.sp,
                                    lineHeight = 20.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Spacer(Modifier.height(5.dp))
                MonoLabel("${value.length}/140", size = 8)
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlineButton(
                        label = "取消",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        label = if (posting) "正在转发…" else "转发",
                        onClick = onConfirm,
                        enabled = !posting,
                        loading = posting,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
