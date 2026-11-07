package com.openweibo.app.ui.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweibo.app.data.VideoPreview
import com.openweibo.app.data.WeiboImage
import com.openweibo.app.data.WeiboPost
import com.openweibo.app.data.WeiboUser
import com.openweibo.app.ui.components.EmptyState
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.feed.PostCard
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.Geist
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    viewModel: SearchViewModel,
    onClose: () -> Unit,
    onOpenImages: (String, List<WeiboImage>, Int, String) -> Unit,
    onSaveImages: (String, List<WeiboImage>) -> Unit,
    onOpenComments: (WeiboPost) -> Unit,
    onOpenReposts: (WeiboPost) -> Unit,
    onOpenVideo: (VideoPreview, String) -> Unit,
    onOpenUser: (WeiboUser) -> Unit,
    currentUid: String,
    onManagePost: (WeiboPost) -> Unit,
    onSnackbar: (String) -> Unit,
    onLoginExpired: () -> Unit
) {
    val colors = AppTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    LaunchedEffect(Unit) {
        viewModel.start()
    }
    LaunchedEffect(state.authExpired) {
        if (state.authExpired) onLoginExpired()
    }
    LaunchedEffect(state.loadMoreError) {
        state.loadMoreError?.let(onSnackbar)
    }
    LaunchedEffect(state.error) {
        if (state.posts.isNotEmpty()) {
            state.error?.let(onSnackbar)
        }
    }
    LaunchedEffect(listState, state.posts.size, state.endReached, state.loadingMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                if (state.posts.isNotEmpty() &&
                    !state.endReached &&
                    !state.loadingMore &&
                    !state.refreshing
                ) {
                    viewModel.loadMore()
                }
            }
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            SearchTopBar(
                query = query,
                postCount = state.posts.size,
                busy = state.initialLoading || state.refreshing,
                onClose = onClose,
                onRefresh = { viewModel.refresh() }
            )
            Hairline()

            when {
                state.initialLoading && state.posts.isEmpty() -> {
                    SearchLoading()
                }

                state.authExpired && state.posts.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.Lock,
                        title = "登录已过期",
                        body = state.error ?: "微博会话已失效，重新登录后即可继续搜索。",
                        actionLabel = "重新登录",
                        onAction = onLoginExpired
                    )
                }

                state.error != null && state.posts.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "搜索暂时不可用",
                        body = state.error.orEmpty(),
                        actionLabel = "重试",
                        onAction = { viewModel.refresh() }
                    )
                }

                state.posts.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "没有找到相关微博",
                        body = "换个关键词，或者稍后刷新再试。",
                        actionLabel = "刷新",
                        onAction = { viewModel.refresh() }
                    )
                }

                else -> {
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                top = 10.dp,
                                bottom = 26.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "search-summary", contentType = "summary") {
                                SearchSummary(
                                    query = query,
                                    postCount = state.posts.size
                                )
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
                            item(key = "search-footer", contentType = "footer") {
                                SearchFooter(
                                    state = state,
                                    onRetry = { viewModel.loadMore() }
                                )
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
                    .background(colors.surfaceRaised)
                    .border(1.dp, colors.border, CircleShape)
                    .clickable {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "回到顶部",
                    tint = colors.textDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    postCount: Int,
    busy: Boolean,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconAction(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "返回热搜",
            onClick = onClose,
            tint = colors.textDim
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = query.ifBlank { "热搜" },
                color = colors.text,
                fontFamily = Geist,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MonoLabel(
                text = if (postCount > 0) {
                    "HOT SEARCH / $postCount POSTS"
                } else {
                    "HOT SEARCH / WEIBO"
                },
                size = 8.5
            )
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
                contentDescription = "刷新搜索结果",
                onClick = onRefresh,
                tint = colors.textFaint,
                iconSize = 17.dp
            )
        }
    }
}

@Composable
private fun SearchSummary(query: String, postCount: Int) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appPanel()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.success)
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            MonoLabel("NATIVE SEARCH / 微博正文", size = 8)
            Spacer(Modifier.height(4.dp))
            Text(
                text = query,
                color = colors.text,
                fontFamily = Geist,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        MonoLabel("$postCount 条", size = 8.5)
    }
}

@Composable
private fun SearchFooter(state: SearchUiState, onRetry: () -> Unit) {
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
                    MonoLabel("LOADING SEARCH RESULTS", size = 8.5)
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
                    MonoLabel("END OF SEARCH", size = 8.5)
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
                MonoLabel("SCROLL TO LOAD MORE", size = 8.5)
            }
        }
    }
}

@Composable
private fun SearchLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .appPanel()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = AppTheme.colors.textFaint,
                strokeWidth = 1.5.dp
            )
            Spacer(Modifier.width(10.dp))
            MonoLabel("SEARCHING WEIBO", size = 9)
        }
    }
}
