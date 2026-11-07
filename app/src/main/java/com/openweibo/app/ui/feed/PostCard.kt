package com.openweibo.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.openweibo.app.data.Retweet
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.data.VideoPreview
import com.openweibo.app.data.WeiboImage
import com.openweibo.app.data.WeiboPost
import com.openweibo.app.data.WeiboUser
import com.openweibo.app.ui.components.Avatar
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.Geist
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PostCard(
    post: WeiboPost,
    onOpenImages: (String, List<WeiboImage>, Int, String) -> Unit,
    onSaveImages: (String, List<WeiboImage>) -> Unit,
    onSnackbar: (String) -> Unit,
    onOpenComments: (WeiboPost) -> Unit = {},
    onOpenReposts: (WeiboPost) -> Unit = {},
    onOpenVideo: (VideoPreview, String) -> Unit = { _, _ -> },
    onOpenUser: (WeiboUser) -> Unit = {},
    onOpenOriginal: (WeiboPost) -> Unit = {},
    currentUid: String = "",
    onManagePost: (WeiboPost) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(15.dp)
    val allImages = remember(post) { post.allImages }
    val canManage = currentUid.isNotBlank() && sameUid(post.user.id, currentUid)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .appPanel(shape = shape)
            .clickable { onOpenComments(post) }
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                url = post.user.avatarUrl,
                name = post.user.screenName,
                size = 39.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenUser(post.user) }
            )
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.user.screenName,
                        color = colors.text,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpenUser(post.user) }
                    )
                    if (post.user.verified) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "已认证",
                            tint = colors.highlight,
                            modifier = Modifier.size(12.5.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel(TimeUtils.relativeTime(post.createdAtMillis), size = 9)
                    if (post.source.isNotBlank()) {
                        Text(
                            text = "  ·  ",
                            style = monoStyle(9),
                            color = colors.textFaint
                        )
                        Text(
                            text = post.source,
                            style = monoStyle(9),
                            color = colors.textFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                    }
                }
            }
            if (canManage) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onManagePost(post) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = "管理微博",
                        tint = colors.textFaint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            ExpandablePostText(
                text = post.text,
                isLongText = post.isLongText,
                statusId = post.id,
                color = colors.text,
                fontSize = 15.sp,
                lineHeight = 23.5.sp,
                collapsedMaxLines = 9,
                compact = false,
                onSnackbar = onSnackbar
            )
        }

        if (post.images.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ImageGrid(
                images = post.images,
                onOpen = { index ->
                    onOpenImages(post.id, post.images, index, post.user.screenName)
                }
            )
        } else if (post.video != null) {
            Spacer(Modifier.height(12.dp))
            VideoCard(
                video = post.video,
                onOpen = { onOpenVideo(post.video, post.user.screenName) }
            )
        }

        post.retweet?.let { retweet ->
            Spacer(Modifier.height(12.dp))
            RetweetCard(
                retweet = retweet,
                onOpenImages = { index ->
                    onOpenImages(post.id, retweet.images, index, retweet.user.screenName)
                },
                onOpenVideo = {
                    retweet.video?.let { video ->
                        onOpenVideo(video, retweet.user.screenName)
                    }
                },
                onOpenUser = onOpenUser,
                onOpenOriginal = {
                    if (retweet.id.isNotBlank()) {
                        onOpenOriginal(retweet.asPost())
                    } else {
                        onSnackbar("原微博暂时无法打开")
                    }
                },
                onSnackbar = onSnackbar
            )
        }

        Spacer(Modifier.height(13.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSoft)
        )
        Spacer(Modifier.height(9.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FooterCount(
                    label = "转",
                    count = post.repostsCount,
                    onClick = { onOpenReposts(post) }
                )
                FooterCount(
                    label = "评",
                    count = post.commentsCount,
                    onClick = { onOpenComments(post) }
                )
                Text(
                    text = "赞 ${TimeUtils.formatCount(post.likesCount)}",
                    style = monoStyle(9.5),
                    color = colors.textFaint,
                    maxLines = 1
                )
            }
            Spacer(Modifier.weight(1f))
            if (allImages.isNotEmpty()) {
                SaveAllButton(
                    count = allImages.size,
                    onClick = { onSaveImages(post.id, allImages) }
                )
            }
        }
    }
}

@Composable
private fun FooterCount(
    label: String,
    count: Int,
    onClick: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 28.dp)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label ${TimeUtils.formatCount(count)}",
            style = monoStyle(9.5),
            color = colors.textDim,
            maxLines = 1
        )
    }
}

@Composable
private fun SaveAllButton(count: Int, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Download,
            contentDescription = null,
            tint = colors.textDim,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "保存原图 · $count",
            color = colors.textDim,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExpandablePostText(
    text: String,
    isLongText: Boolean,
    statusId: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    collapsedMaxLines: Int,
    compact: Boolean,
    onSnackbar: (String) -> Unit
) {
    val loader = LocalLongTextLoader.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(statusId, text, collapsedMaxLines) { mutableStateOf(false) }
    var fullText by remember(statusId, text) { mutableStateOf<String?>(null) }
    var loading by remember(statusId, text) { mutableStateOf(false) }
    var canExpand by remember(statusId, text) { mutableStateOf(false) }
    var loadFailed by remember(statusId, text) { mutableStateOf(false) }
    val displayText = fullText ?: text

    fun toggle() {
        if (loading) return
        if (!expanded && isLongText && fullText == null) {
            val activeLoader = loader
            if (activeLoader == null || statusId.isBlank()) {
                expanded = true
                return
            }
            loading = true
            loadFailed = false
            scope.launch {
                try {
                    val loaded = activeLoader.load(statusId)
                    if (loaded.isNotBlank()) {
                        fullText = loaded
                        expanded = true
                    } else {
                        loadFailed = true
                        onSnackbar("没有获取到完整内容，请重试")
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    loadFailed = true
                    onSnackbar(error.message ?: "全文加载失败，请重试")
                } finally {
                    loading = false
                }
            }
        } else {
            expanded = !expanded
        }
    }

    Text(
        text = displayText,
        color = color,
        fontFamily = Geist,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout ->
            if (!expanded && fullText == null) {
                canExpand = layout.hasVisualOverflow
            }
        }
    )
    if (canExpand || expanded || isLongText) {
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = !loading) { toggle() }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 10.dp else 11.dp),
                    color = AppTheme.colors.textFaint,
                    strokeWidth = 1.2.dp
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = when {
                    loading -> "加载全文中…"
                    expanded -> "收起"
                    loadFailed -> "重试全文"
                    else -> "展开全文"
                },
                color = AppTheme.colors.textDim,
                fontSize = if (compact) 10.5.sp else 11.5.sp,
                fontWeight = FontWeight.Medium
            )
            if (!loading) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = AppTheme.colors.textFaint,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp)
                )
            }
        }
    }
}

private fun Modifier.mediaFrame(
    aspectRatio: Float,
    minHeight: Dp,
    maxHeight: Dp
): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) {
        constraints.maxWidth
    } else {
        constraints.minWidth
    }
    val height = (width / aspectRatio)
        .roundToInt()
        .coerceIn(minHeight.roundToPx(), maxHeight.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) {
        placeable.place(0, 0)
    }
}

@Composable
private fun RetweetCard(
    retweet: Retweet,
    onOpenImages: (Int) -> Unit,
    onOpenVideo: () -> Unit,
    onOpenUser: (WeiboUser) -> Unit,
    onOpenOriginal: () -> Unit,
    onSnackbar: (String) -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surfaceAlt)
            .border(1.dp, colors.borderSoft, RoundedCornerShape(11.dp))
            .clickable(enabled = retweet.id.isNotBlank(), onClick = onOpenOriginal)
            .padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                url = retweet.user.avatarUrl,
                name = retweet.user.screenName,
                size = 21.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenUser(retweet.user) }
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = retweet.user.screenName,
                color = colors.textDim,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onOpenUser(retweet.user) }
            )
            if (retweet.user.verified) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = colors.highlight,
                    modifier = Modifier.size(10.5.dp)
                )
            }
            if (retweet.id.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                MonoLabel("原文 ↗", size = 8)
            }
        }
        if (retweet.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            ExpandablePostText(
                text = retweet.text,
                isLongText = retweet.isLongText,
                statusId = retweet.id,
                color = colors.textDim,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                collapsedMaxLines = 8,
                compact = true,
                onSnackbar = onSnackbar
            )
        }
        if (retweet.images.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            ImageGrid(
                images = retweet.images,
                onOpen = onOpenImages,
                compact = true
            )
        } else if (retweet.video != null) {
            Spacer(Modifier.height(9.dp))
            VideoCard(
                video = retweet.video,
                onOpen = onOpenVideo,
                compact = true
            )
        }
    }
}

@Composable
fun ImageGrid(
    images: List<WeiboImage>,
    onOpen: (Int) -> Unit,
    compact: Boolean = false
) {
    if (images.isEmpty()) return
    val spacing = if (compact) 3.dp else 4.dp
    val shape = RoundedCornerShape(if (compact) 7.dp else 10.dp)
    val display = images.take(9)

    if (display.size == 1) {
        val single = display.first()
        val maxHeight = if (compact) 210.dp else 360.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .mediaFrame(single.aspectRatio, 160.dp, maxHeight)
                .clip(shape)
                .clickable { onOpen(0) }
        ) {
            NetworkImage(
                url = single.thumbnailUrl,
                modifier = Modifier.fillMaxSize()
            )
            ImageHint(
                label = if (single.isGif) "GIF" else "原图",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            )
        }
        return
    }

    val columns = when {
        display.size == 2 || display.size == 4 -> 2
        else -> 3
    }
    val rows = (display.size + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    if (index < display.size) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(shape)
                                .clickable { onOpen(index) }
                        ) {
                            NetworkImage(
                                url = display[index].thumbnailUrl,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (display[index].isGif) {
                                GifBadge(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                )
                            }
                            if (index == display.lastIndex && images.size > display.size) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.48f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${images.size - display.size}",
                                        color = Color.White,
                                        fontFamily = Geist,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GifBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = "GIF",
            color = Color.White.copy(alpha = 0.92f),
            style = monoStyle(7.5, letterSpacing = 0.5)
        )
    }
}

@Composable
private fun ImageHint(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ZoomIn,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.9f),
            style = monoStyle(8.5, letterSpacing = 0.4)
        )
    }
}

@Composable
private fun VideoCard(
    video: VideoPreview,
    onOpen: () -> Unit,
    compact: Boolean = false
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(if (compact) 7.dp else 10.dp)
    val duration = TimeUtils.formatDuration(video.durationSeconds)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 170.dp else 210.dp)
            .clip(shape)
            .clickable(onClick = onOpen)
    ) {
        if (video.coverUrl.isNotBlank()) {
            NetworkImage(
                url = video.coverUrl,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceAlt)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.16f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(if (compact) 38.dp else 46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = "播放视频",
                tint = Color.White,
                modifier = Modifier.size(if (compact) 22.dp else 26.dp)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VIDEO",
                color = Color.White.copy(alpha = 0.75f),
                style = monoStyle(7.5, letterSpacing = 0.6)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.58f))
                .padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.danger)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = if (duration.isBlank()) "视频 · 应用内播放" else "视频 · $duration",
                color = Color.White.copy(alpha = 0.9f),
                style = monoStyle(8.5, letterSpacing = 0.4)
            )
        }
    }
}

@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showProgress: Boolean = false,
    placeholderColor: Color? = null
) {
    val colors = AppTheme.colors
    val placeholder = placeholderColor ?: colors.surfaceAlt
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(showProgress) }

    Box(
        modifier = modifier.background(placeholder),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            onLoading = if (showProgress) {
                { loading = true }
            } else {
                null
            },
            onSuccess = if (showProgress) {
                { loading = false }
            } else {
                null
            },
            onError = {
                loading = false
                failed = true
            }
        )
        if (failed) {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = "图片加载失败",
                tint = colors.textFaint,
                modifier = Modifier.size(20.dp)
            )
        } else if (showProgress && loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(15.dp),
                color = colors.textFaint,
                strokeWidth = 1.5.dp
            )
        }
    }
}

private fun sameUid(left: String, right: String): Boolean {
    if (left.isBlank() || right.isBlank()) return false
    fun normalize(value: String): String = value.trim().trimStart('0').ifBlank { "0" }
    return normalize(left) == normalize(right)
}
