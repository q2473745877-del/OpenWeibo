package com.openweibo.app.ui.hot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweibo.app.data.HotCategory
import com.openweibo.app.data.HotItem
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.ui.components.EmptyState
import com.openweibo.app.ui.components.FilterPill
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.ScreenHeader
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.GeistMono
import com.openweibo.app.ui.theme.monoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotSearchScreen(
    viewModel: HotSearchViewModel,
    onOpenItem: (HotItem) -> Unit,
    onSnackbar: (String) -> Unit,
    onLoginExpired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.start()
    }
    LaunchedEffect(state.error) {
        if (state.items.isNotEmpty() && !state.error.isNullOrBlank()) {
            onSnackbar(state.error.orEmpty())
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .align(Alignment.TopCenter)
        ) {
            ScreenHeader(
                title = "热搜",
                subtitle = hotSubtitle(state),
                actions = {
                    AnimatedVisibility(visible = state.loading || state.refreshing) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AppTheme.colors.textFaint,
                                strokeWidth = 1.6.dp
                            )
                        }
                    }
                    AnimatedVisibility(visible = !state.loading && !state.refreshing) {
                        IconAction(
                            icon = Icons.Outlined.Refresh,
                            contentDescription = "刷新热搜",
                            onClick = { viewModel.refresh() },
                            tint = AppTheme.colors.textDim
                        )
                    }
                }
            )
            Hairline()
            CategoryStrip(
                selected = state.category,
                onSelect = { category ->
                    if (category != state.category) viewModel.selectCategory(category)
                }
            )
            Hairline()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .appPanel()
                ) {
                    HotBoardHeader(state = state)
                    Hairline()
                    when {
                        state.loading && state.items.isEmpty() -> {
                            HotSkeleton()
                        }

                        state.authExpired && state.items.isEmpty() -> {
                            EmptyState(
                                icon = Icons.Outlined.Lock,
                                title = "登录已过期",
                                body = state.error ?: "重新登录后即可查看实时热搜。",
                                actionLabel = "重新登录",
                                onAction = onLoginExpired
                            )
                        }

                        state.error != null && state.items.isEmpty() -> {
                            EmptyState(
                                icon = Icons.Outlined.CloudOff,
                                title = "热搜暂时不可用",
                                body = state.error.orEmpty(),
                                actionLabel = "重试",
                                onAction = { viewModel.refresh() }
                            )
                        }

                        state.items.isEmpty() -> {
                            EmptyState(
                                icon = Icons.Outlined.CloudOff,
                                title = "热搜榜为空",
                                body = "稍后再刷新看看，微博的榜单正在更新。",
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
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    itemsIndexed(
                                        items = state.items,
                                        key = { _, item -> "${state.category.name}-${item.rank}-${item.title}" },
                                        contentType = { _, _ -> "hot" }
                                    ) { index, item ->
                                        HotRow(
                                            item = item,
                                            onClick = { onOpenItem(item) }
                                        )
                                        if (index != state.items.lastIndex) {
                                            Hairline(
                                                modifier = Modifier.padding(start = 49.dp, end = 13.dp),
                                                color = AppTheme.colors.borderSoft
                                            )
                                        }
                                    }
                                    item(key = "hot-footer", contentType = "footer") {
                                        HotFooter()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hotSubtitle(state: HotUiState): String {
    val updated = state.updatedAt?.let {
        " · " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    }.orEmpty()
    return "TRENDING / ${state.category.english}$updated"
}

@Composable
private fun CategoryStrip(
    selected: HotCategory,
    onSelect: (HotCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HotCategory.entries.forEach { category ->
            FilterPill(
                label = category.label,
                selected = selected == category,
                onClick = { onSelect(category) }
            )
        }
    }
}

@Composable
private fun HotBoardHeader(state: HotUiState) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.success)
        )
        Spacer(Modifier.width(8.dp))
        MonoLabel("LIVE BOARD", color = colors.textDim, size = 9)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "·",
            color = colors.textFaint,
            fontSize = 10.sp
        )
        Spacer(Modifier.width(10.dp))
        MonoLabel("${state.items.size} 条", size = 9)
        Spacer(Modifier.weight(1f))
        MonoLabel(
            text = state.updatedAt?.let {
                "UPDATED " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
            } ?: "WAITING",
            size = 8.5
        )
    }
}

@Composable
private fun HotRow(item: HotItem, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val topThree = item.rank <= 3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.rank.toString().padStart(2, '0'),
            color = when {
                item.rank == 1 -> colors.highlight
                topThree -> colors.textDim
                else -> colors.textFaint
            },
            fontFamily = GeistMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(30.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    color = colors.text,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    fontWeight = if (topThree) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                val label = item.label?.takeIf { it.isNotBlank() && it != "0" }
                if (label != null) {
                    Spacer(Modifier.width(7.dp))
                    HotLabel(label = label, isAd = item.isAd)
                }
            }
            val heat = TimeUtils.formatHeat(item.heat)
            if (heat != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = heat,
                    style = monoStyle(8.5),
                    color = colors.textFaint
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun HotLabel(label: String, isAd: Boolean) {
    val colors = AppTheme.colors
    val color = if (isAd) colors.textFaint else colors.highlight
    Text(
        text = label,
        color = color,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
private fun HotFooter() {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .height(1.dp)
                .weight(1f)
                .background(colors.borderSoft)
        )
        Spacer(Modifier.width(10.dp))
        MonoLabel("WEIBO TRENDING · REALTIME", size = 8)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .height(1.dp)
                .weight(1f)
                .background(colors.borderSoft)
        )
    }
}

@Composable
private fun HotSkeleton() {
    val colors = AppTheme.colors
    Column {
        repeat(10) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(18.dp)
                        .height(9.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.borderSoft)
                )
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth((0.52f + index % 3 * 0.12f).coerceAtMost(0.85f))
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceAlt)
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(52.dp)
                            .height(7.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.borderSoft)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(colors.borderSoft)
                )
            }
            if (index < 9) {
                Hairline(Modifier.padding(start = 49.dp, end = 13.dp))
            }
        }
    }
}
