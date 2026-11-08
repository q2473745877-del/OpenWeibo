package com.openweibo.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.data.VideoPreview
import com.openweibo.app.data.WeiboImage
import com.openweibo.app.data.WeiboPost
import com.openweibo.app.data.WeiboUser
import com.openweibo.app.ui.components.EmptyState
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.ScreenHeader
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onOpenImages: (String, List<WeiboImage>, Int, String) -> Unit,
    onSaveImages: (String, List<WeiboImage>) -> Unit,
    onOpenComments: (WeiboPost) -> Unit,
    onOpenReposts: (WeiboPost) -> Unit,
    onOpenVideo: (VideoPreview, String) -> Unit,
    onOpenUser: (WeiboUser) -> Unit,
    currentUid: String,
    onManagePost: (WeiboPost) -> Unit,
    onSnackbar: (String) -> Unit,
    onLoginExpired: () -> Unit,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    LaunchedEffect(Unit) {
        viewModel.start()
    }

    LaunchedEffect(state.loadMoreError) {
        state.loadMoreError?.let(onSnackbar)
    }

    // Any completed top-of-timeline refresh (toolbar, pull-to-refresh, tab double-tap,
    // or post-publish) bumps refreshCount; jump back to the newest post when it changes.
    LaunchedEffect(state.refreshCount) {
        if (state.refreshCount > 0) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState, state.posts.size, state.endReached, state.loadingMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .filter { index -> index >= state.posts.size - 3 && state.posts.isNotEmpty() }
            .collect {
                if (!state.endReached && !state.loadingMore) {
                    viewModel.loadMore()
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
        ) {
            ScreenHeader(
                title = "时间线",
                subtitle = subtitleFor(state),
                actions = {
                    IconAction(
                        icon = Icons.Outlined.Edit,
                        contentDescription = "发布微博",
                        onClick = onCompose,
                        tint = AppTheme.colors.textDim
                    )
                    AnimatedVisibility(visible = state.refreshing) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AppTheme.colors.textFaint,
                                strokeWidth = 1.6.dp
                            )
                        }
                    }
                    AnimatedVisibility(visible = !state.refreshing) {
                        IconAction(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = "刷新时间线",
                            onClick = { viewModel.refresh() },
                            tint = AppTheme.colors.textDim
                        )
                    }
                }
            )
            Hairline()

            when {
                state.initialLoading && state.posts.isEmpty() -> {
                    SkeletonFeed()
                }

                state.authExpired && state.posts.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.Lock,
                        title = "登录已过期",
                        body = state.error ?: "微博会话已失效，重新登录后即可继续查看关注时间线。",
                        actionLabel = "重新登录",
                        onAction = onLoginExpired
                    )
                }

                state.error != null && state.posts.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "暂时无法加载",
                        body = state.error.orEmpty(),
                        actionLabel = "重试",
                        onAction = { viewModel.refresh() }
                    )
                }

                state.posts.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.Forum,
                        title = "时间线还没有内容",
                        body = "关注一些账号后，这里会按发布时间展示最新微博。",
                        actionLabel = "刷新",
                        onAction = { viewModel.refresh() }
                    )
                }

                else -> {
                    Box(Modifier.fillMaxSize()) {
                        PullToRefreshBox(
                            isRefreshing = state.refreshing,
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 8.dp,
                                    bottom = 22.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (state.authExpired) {
                                    item(key = "expired", contentType = "banner") {
                                        SessionExpiredBanner(
                                            message = state.error ?: "登录已过期，请重新登录",
                                            onRelogin = onLoginExpired
                                        )
                                    }
                                }
                                items(
                                    items = state.posts,
                                    key = { it.id.ifBlank { it.bid } },
                                    contentType = { "post" }
                                ) { post ->
                                    PostCard(
                                        post = post,
                                        onOpenImages = onOpenImages,
                                        onSaveImages = onSaveImages,
                                        onSnackbar = onSnackbar,
                                        onOpenComments = onOpenComments,
                                        onOpenReposts = onOpenReposts,
                                        onOpenVideo = onOpenVideo,
                                        onOpenUser = onOpenUser,
                                        onOpenOriginal = onOpenComments,
                                        currentUid = currentUid,
                                        onManagePost = onManagePost
                                    )
                                }
                                item(key = "footer", contentType = "footer") {
                                    FeedFooter(
                                        state = state,
                                        onRetry = { viewModel.loadMore() }
                                    )
                                }
                            }
                        }

                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showTop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 22.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AppTheme.colors.surfaceRaised)
                    .border(1.dp, AppTheme.colors.border, CircleShape)
                    .clickable {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "回到顶部",
                    tint = AppTheme.colors.textDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun subtitleFor(state: FeedUiState): String {
    val base = "FOLLOWING / SORTED BY TIME"
    val count = if (state.posts.isEmpty()) "" else " · ${state.posts.size} POSTS"
    val updated = state.lastUpdated?.let {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
        " · UPDATED $time"
    }.orEmpty()
    return base + count + updated
}

@Composable
private fun SessionExpiredBanner(message: String, onRelogin: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.dangerSoft)
            .border(1.dp, colors.danger.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = message,
            color = colors.danger,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "重新登录",
            color = colors.text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onRelogin)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FeedFooter(state: FeedUiState, onRetry: () -> Unit) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            state.loadingMore -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = colors.textFaint,
                        strokeWidth = 1.4.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    MonoLabel("LOADING OLDER POSTS", size = 8.5)
                }
            }

            state.loadMoreError != null -> {
                Text(
                    text = state.loadMoreError.orEmpty(),
                    color = colors.textFaint,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "重试",
                    color = colors.textDim,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            state.endReached -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .height(1.dp)
                            .width(28.dp)
                            .background(colors.borderSoft)
                    )
                    Spacer(Modifier.width(9.dp))
                    MonoLabel("END OF TIMELINE", size = 8.5)
                    Spacer(Modifier.width(9.dp))
                    Box(
                        Modifier
                            .height(1.dp)
                            .width(28.dp)
                            .background(colors.borderSoft)
                    )
                }
            }

            else -> {
                MonoLabel("SCROLL TO LOAD OLDER", size = 8.5)
            }
        }
    }
}

@Composable
private fun SkeletonFeed() {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(4) { index ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .appPanel()
                    .padding(15.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(39.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt)
                    )
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Box(
                            Modifier
                                .width((92 + index * 18).dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceAlt)
                        )
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier
                                .width(68.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.borderSoft)
                        )
                    }
                }
                Spacer(Modifier.height(15.dp))
                repeat(if (index % 2 == 0) 3 else 2) { line ->
                    Box(
                        Modifier
                            .fillMaxWidth(if (line == 2) 0.62f else 1f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.borderSoft)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (index % 2 == 0) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(colors.surfaceAlt)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                Hairline()
            }
        }
    }
}
