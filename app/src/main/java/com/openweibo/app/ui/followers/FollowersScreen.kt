package com.openweibo.app.ui.followers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweibo.app.data.Session
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.data.WeiboUser
import com.openweibo.app.ui.components.Avatar
import com.openweibo.app.ui.components.EmptyState
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.OutlineButton
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.Geist
import com.openweibo.app.ui.theme.monoStyle

@Composable
fun FollowersScreen(
    session: Session,
    viewModel: FollowersViewModel,
    onClose: () -> Unit,
    onOpenUser: (WeiboUser) -> Unit,
    onLoginExpired: () -> Unit,
    mode: FollowersMode = FollowersMode.Followers
) {
    val colors = AppTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val copy = followersCopy(mode)

    LaunchedEffect(Unit) {
        viewModel.start(refresh = true)
    }
    LaunchedEffect(state.authExpired) {
        if (state.authExpired) onLoginExpired()
    }
    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        FollowersTopBar(
            session = session,
            copy = copy,
            busy = state.loading,
            onClose = onClose,
            onRefresh = { viewModel.refresh() }
        )
        Hairline()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.loading && state.users.isEmpty() -> {
                    FollowersSkeleton()
                }

                state.error != null && state.users.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = copy.errorTitle,
                        body = state.error.orEmpty(),
                        actionLabel = "重试",
                        onAction = { viewModel.refresh() }
                    )
                }

                state.users.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Outlined.PeopleOutline,
                        title = copy.emptyTitle,
                        body = copy.emptyBody
                    )
                }

                else -> {
                    FollowersList(
                        state = state,
                        copy = copy,
                        onOpenUser = onOpenUser,
                        onLoadMore = { viewModel.loadMore() }
                    )
                }
            }
        }
    }
}

private data class FollowersCopy(
    val title: String,
    val english: String,
    val emptyTitle: String,
    val emptyBody: String,
    val errorTitle: String,
    val totalUnit: String,
    val loadingLabel: String,
    val endLabel: String,
    val loadMoreLabel: String,
    val refreshDescription: String
)

private fun followersCopy(mode: FollowersMode): FollowersCopy = when (mode) {
    FollowersMode.Followers -> FollowersCopy(
        title = "粉丝",
        english = "FOLLOWERS",
        emptyTitle = "还没有粉丝",
        emptyBody = "当其他微博用户关注你后，他们会出现在这里。",
        errorTitle = "粉丝列表暂时不可用",
        totalUnit = "位粉丝",
        loadingLabel = "LOADING FOLLOWERS",
        endLabel = "END OF FOLLOWERS",
        loadMoreLabel = "加载更多粉丝",
        refreshDescription = "刷新粉丝列表"
    )

    FollowersMode.Following -> FollowersCopy(
        title = "我的关注",
        english = "FOLLOWING",
        emptyTitle = "还没有关注",
        emptyBody = "你关注的微博账号会出现在这里。",
        errorTitle = "关注列表暂时不可用",
        totalUnit = "个关注",
        loadingLabel = "LOADING FOLLOWING",
        endLabel = "END OF FOLLOWING",
        loadMoreLabel = "加载更多关注",
        refreshDescription = "刷新关注列表"
    )
}

@Composable
private fun FollowersTopBar(
    session: Session,
    copy: FollowersCopy,
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
                text = copy.title,
                color = colors.text,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold
            )
            MonoLabel("${copy.english} / @${session.screenName}", size = 8.5)
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
                contentDescription = copy.refreshDescription,
                onClick = onRefresh,
                tint = colors.textFaint,
                iconSize = 17.dp
            )
        }
    }
}

@Composable
private fun FollowersList(
    state: FollowersUiState,
    copy: FollowersCopy,
    onOpenUser: (WeiboUser) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary", contentType = "summary") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .appPanel()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AppTheme.colors.success)
                )
                Spacer(Modifier.width(8.dp))
                MonoLabel(
                    text = if (state.total > 0) {
                        "共 ${TimeUtils.formatCount(state.total)} ${copy.totalUnit}"
                    } else {
                        "${copy.english} LIST"
                    },
                    color = AppTheme.colors.textDim,
                    size = 9
                )
                Spacer(Modifier.weight(1f))
                MonoLabel("已加载 ${state.users.size}", size = 8.5)
            }
        }

        items(
            items = state.users,
            key = { it.id.ifBlank { it.screenName } },
            contentType = { "user" }
        ) { user ->
            UserRow(user = user, onClick = { onOpenUser(user) })
            Hairline(
                modifier = Modifier.padding(start = 54.dp),
                color = AppTheme.colors.borderSoft
            )
        }

        item(key = "footer", contentType = "footer") {
            FollowersFooter(
                state = state,
                copy = copy,
                onLoadMore = onLoadMore
            )
        }
    }
}

@Composable
private fun UserRow(user: WeiboUser, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(
            url = user.avatarUrl,
            name = user.screenName,
            size = 42.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.screenName,
                    color = colors.text,
                    fontFamily = Geist,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
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
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            if (user.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = user.description,
                    color = colors.textDim,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MonoLabel("粉丝 ${TimeUtils.formatCount(user.followersCount)}", size = 8)
                MonoLabel("微博 ${TimeUtils.formatCount(user.statusesCount)}", size = 8)
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier
                .padding(top = 4.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun FollowersFooter(
    state: FollowersUiState,
    copy: FollowersCopy,
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
            state.loadingMore -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = colors.textFaint,
                        strokeWidth = 1.4.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    MonoLabel(copy.loadingLabel, size = 8.5)
                }
            }

            state.loadMoreError != null -> {
                Text(
                    text = state.loadMoreError,
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

            state.endReached -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .height(1.dp)
                            .width(26.dp)
                            .background(colors.borderSoft)
                    )
                    Spacer(Modifier.width(9.dp))
                    MonoLabel(copy.endLabel, size = 8)
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
                OutlineButton(label = copy.loadMoreLabel, onClick = onLoadMore)
            }
        }
    }
}

@Composable
private fun FollowersSkeleton() {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(7) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .appPanel()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceAlt)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .width((90 + index * 9).dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceAlt)
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.borderSoft)
                    )
                }
            }
        }
    }
}
