package com.openweibo.app.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openweibo.app.data.ShareTarget
import com.openweibo.app.data.WeiboImage
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.feed.NetworkImage
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.monoStyle

/** What the long-press menu asked the host screen to do with the current image. */
sealed interface ShareAction {
    data class ToTarget(val target: ShareTarget) : ShareAction
    data object System : ShareAction
}

@Composable
fun ImageViewer(
    images: List<WeiboImage>,
    initialIndex: Int,
    title: String,
    onSaveCurrent: (WeiboImage) -> Unit,
    onSaveAll: (List<WeiboImage>) -> Unit,
    onShare: (WeiboImage, ShareAction) -> Unit,
    availableShareTargets: List<ShareTarget>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) return
    val safeIndex = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeIndex) { images.size }
    val current = images[pagerState.currentPage.coerceIn(0, images.lastIndex)]
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = !menuOpen, onBack = onClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030303))
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            ) {
                IconAction(
                    icon = Icons.Outlined.Close,
                    contentDescription = "关闭大图",
                    onClick = onClose,
                    tint = Color.White.copy(alpha = 0.86f)
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                MonoLabel(
                    text = if (current.isGif) "GIF IMAGE / VIEWER" else "ORIGINAL IMAGE / VIEWER",
                    color = Color.White.copy(alpha = 0.36f),
                    size = 8.5
                )
            }
            IconAction(
                icon = Icons.Outlined.Download,
                contentDescription = "保存这张原图",
                onClick = { onSaveCurrent(current) },
                tint = Color.White.copy(alpha = 0.9f),
                iconSize = 19.dp
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            beyondViewportPageCount = 1
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page, onClose) {
                        detectTapGestures(
                            onTap = { onClose() },
                            onLongPress = { menuOpen = true }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                NetworkImage(
                    url = images[page].originalUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    showProgress = true,
                    placeholderColor = Color(0xFF030303)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    images.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 15.dp else 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) {
                                        Color.White.copy(alpha = 0.88f)
                                    } else {
                                        Color.White.copy(alpha = 0.22f)
                                    }
                                )
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White.copy(alpha = 0.65f),
                    style = monoStyle(10, letterSpacing = 0.7)
                )
                Spacer(Modifier.size(14.dp))
                Text(
                    text = imageSizeLabel(current),
                    color = Color.White.copy(alpha = 0.4f),
                    style = monoStyle(9, letterSpacing = 0.5)
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.size(7.dp))
                Text(
                    text = "保存的是微博提供的高清原图，不会进行二次压缩",
                    color = Color.White.copy(alpha = 0.36f),
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "长按图片可保存或分享",
                    color = Color.White.copy(alpha = 0.28f),
                    style = monoStyle(8.5, letterSpacing = 0.5)
                )
            }
        }
    }

    if (menuOpen) {
        ShareSheet(
            image = current,
            imageCount = images.size,
            targets = availableShareTargets,
            onSaveCurrent = {
                menuOpen = false
                onSaveCurrent(current)
            },
            onSaveAll = {
                menuOpen = false
                onSaveAll(images)
            },
            onShare = { action ->
                menuOpen = false
                onShare(current, action)
            },
            onDismiss = { menuOpen = false }
        )
    }
}

@Composable
private fun ShareSheet(
    image: WeiboImage,
    imageCount: Int,
    targets: List<ShareTarget>,
    onSaveCurrent: () -> Unit,
    onSaveAll: () -> Unit,
    onShare: (ShareAction) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF141416))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = if (image.isGif) "GIF 动图" else "原图",
                color = Color.White.copy(alpha = 0.42f),
                style = monoStyle(8.5, letterSpacing = 0.8),
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )

            SheetItem(label = "保存此图片", onClick = onSaveCurrent)
            if (imageCount > 1) {
                SheetItem(label = "保存所有图片（共 $imageCount 张）", onClick = onSaveAll)
            }

            if (targets.isNotEmpty()) {
                Spacer(Modifier.size(6.dp))
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "分享图片",
                    color = Color.White.copy(alpha = 0.42f),
                    style = monoStyle(8.5, letterSpacing = 0.8),
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                )
                targets.forEach { target ->
                    SheetItem(
                        label = "分享至${target.label}",
                        onClick = { onShare(ShareAction.ToTarget(target)) }
                    )
                }
            }

            Spacer(Modifier.size(6.dp))
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            SheetItem(label = "更多分享…", onClick = { onShare(ShareAction.System) })
        }
    }
}

@Composable
private fun SheetItem(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.5.sp,
            lineHeight = 19.sp
        )
    }
}

private fun imageSizeLabel(image: WeiboImage): String {
    return if (image.width > 0 && image.height > 0) {
        "${image.width} × ${image.height}"
    } else {
        "原图"
    }
}
