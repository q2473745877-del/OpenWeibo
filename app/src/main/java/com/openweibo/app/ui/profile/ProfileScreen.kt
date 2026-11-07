package com.openweibo.app.ui.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.data.VideoPreview
import com.openweibo.app.data.WeiboImage
import com.openweibo.app.data.WeiboPost
import com.openweibo.app.data.WeiboUser
import com.openweibo.app.ui.components.Avatar
import com.openweibo.app.ui.components.EmptyState
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.OutlineButton
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.feed.PostCard
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.Geist
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    initialUser: WeiboUser,
    viewModel: ProfileViewModel,
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
    val user = state.user ?: initialUser
    val listState = rememberLazyListState()

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
    LaunchedEffect(state.followMessage) {
        state.followMessage?.let { message ->
            onSnackbar(message)
            viewModel.consumeFollowMessage()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ProfileTopBar(
            user = user,
            busy = state.initialLoading || state.refreshing,
            onClose = onClose,
            onRefresh = { viewModel.refresh() }
        )
        Hairline()

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
                item(key = "profile-header", contentType = "header") {
                    ProfileHeader(
                        user = user,
                        state = state,
                        onToggleFollow = { viewModel.toggleFollow() }
                    )
                }

                when {
                    state.initialLoading && state.posts.isEmpty() -> {
                        item(key = "profile-loading") {
                            ProfileLoading("LOADING PROFILE TIMELINE")
                        }
                    }

                    state.error != null && state.posts.isEmpty() -> {
                        item(key = "profile-error") {
                            EmptyState(
                                icon = Icons.Outlined.CloudOff,
                                title = "暂时无法加载主页",
                                body = state.error.orEmpty(),
                                actionLabel = "重试",
                                onAction = { viewModel.refresh() }
                            )
                        }
                    }

                    state.posts.isEmpty() -> {
                        item(key = "profile-empty") {
                            EmptyState(
                                icon = Icons.Outlined.PersonOutline,
                                title = "没有可见微博",
                                body = "这个账号还没有公开微博，或者内容仅对部分人可见。"
                            )
                        }
                    }

                    else -> {
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
                    }
                }

                if (state.posts.isNotEmpty()) {
                    item(key = "profile-footer", contentType = "footer") {
                        ProfileFooter(
                            state = state,
                            onRetry = { viewModel.loadMore() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTopBar(
    user: WeiboUser,
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
            contentDescription = "返回",
            onClick = onClose,
            tint = colors.textDim
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.screenName,
                color = colors.text,
                fontFamily = Geist,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MonoLabel("PROFILE / UID ${user.id.ifBlank { "UNKNOWN" }}", size = 8.5)
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
                contentDescription = "刷新主页",
                onClick = onRefresh,
                tint = colors.textFaint,
                iconSize = 17.dp
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    user: WeiboUser,
    state: ProfileUiState,
    onToggleFollow: () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appPanel()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                url = user.avatarUrl,
                name = user.screenName,
                size = 62.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.screenName,
                        color = colors.text,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (user.verified) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "已认证",
                            tint = colors.highlight,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (user.verifiedReason.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = user.verifiedReason,
                        color = colors.highlight.copy(alpha = 0.75f),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.success)
                    )
                    Spacer(Modifier.width(6.dp))
                    MonoLabel("WEIBO PROFILE / NATIVE VIEW", size = 8)
                }
            }
        }

        if (user.description.isNotBlank()) {
            Spacer(Modifier.height(13.dp))
            Text(
                text = user.description,
                color = colors.textDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ProfileStat("粉丝", TimeUtils.formatCount(user.followersCount))
            ProfileStat("微博", TimeUtils.formatCount(user.statusesCount))
            if (user.followingCount > 0L) {
                ProfileStat("关注", TimeUtils.formatCount(user.followingCount))
            }
            Spacer(Modifier.weight(1f))
            ProfileStat(
                label = "已加载",
                value = state.posts.size.toString(),
                alignEnd = true
            )
        }
        if (!state.isSelf && user.id.isNotBlank()) {
            Spacer(Modifier.height(13.dp))
            FollowButton(
                following = user.following,
                mutual = user.isMutual,
                busy = state.followBusy,
                onClick = onToggleFollow
            )
        }
    }
}

@Composable
private fun FollowButton(
    following: Boolean,
    mutual: Boolean,
    busy: Boolean,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(9.dp)
    val background = if (following) colors.surfaceAlt else colors.accent
    val contentColor = if (following) colors.text else colors.onAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, if (following) colors.border else colors.accent, shape)
            .clickable(enabled = !busy, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = contentColor,
                strokeWidth = 1.5.dp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (following) "正在取消关注" else "正在关注",
                color = contentColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        } else {
            if (following) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.success)
                )
                Spacer(Modifier.width(7.dp))
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = when {
                    following && mutual -> "互相关注"
                    following -> "已关注"
                    else -> "关注"
                },
                color = contentColor,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            if (following) {
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "点击取消",
                    color = colors.textFaint,
                    style = monoStyle(8.5, letterSpacing = 0.4)
                )
            }
        }
    }
}

@Composable
private fun ProfileStat(
    label: String,
    value: String,
    alignEnd: Boolean = false
) {
    val colors = AppTheme.colors
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = value,
            color = colors.text,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(2.dp))
        MonoLabel(label, color = colors.textFaint, size = 8.5)
    }
}

@Composable
private fun ProfileLoading(label: String) {
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
private fun ProfileFooter(state: ProfileUiState, onRetry: () -> Unit) {
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
                    MonoLabel("LOADING PROFILE POSTS", size = 8.5)
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
                            .width(26.dp)
                            .background(colors.borderSoft)
                    )
                    Spacer(Modifier.width(9.dp))
                    MonoLabel("END OF PROFILE", size = 8.5)
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
                OutlineButton(label = "加载更多微博", onClick = onRetry)
            }
        }
    }
}
