package com.openweibo.app.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openweibo.app.data.WeiboPost
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.OutlineButton
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.theme.AppTheme

/**
 * Action sheet shown from the "..." button on an own post. Editing only the
 * text is supported for original posts without video; deleting is available
 * for every own post.
 */
@Composable
fun PostManageDialog(
    post: WeiboPost,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppTheme.colors
    val canEdit = post.video == null && post.retweet == null
    val editHint = when {
        post.video != null -> "含视频的微博暂不支持编辑"
        post.retweet != null -> "转发微博暂不支持编辑"
        else -> "修改文字；现有图片 / GIF 保持不变"
    }
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(26.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .appPanel(color = colors.surfaceRaised)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = null,
                            tint = colors.textDim,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "管理微博",
                            color = colors.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        MonoLabel("@${post.user.screenName}", size = 8.5)
                    }
                }

                if (post.text.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = post.text,
                        color = colors.textFaint,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceAlt)
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                ManageAction(
                    icon = Icons.Outlined.Edit,
                    title = "编辑正文",
                    caption = editHint,
                    enabled = canEdit,
                    onClick = onEdit
                )
                Spacer(Modifier.height(8.dp))
                ManageAction(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "删除微博",
                    caption = "删除后无法恢复，评论和转发也会一并消失",
                    enabled = true,
                    danger = true,
                    onClick = onDelete
                )
                Spacer(Modifier.height(16.dp))
                OutlineButton(
                    label = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ManageAction(
    icon: ImageVector,
    title: String,
    caption: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    val tint = when {
        !enabled -> colors.textFaint
        danger -> colors.danger
        else -> colors.text
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (danger && enabled) colors.dangerSoft else colors.surfaceAlt)
            .border(1.dp, if (danger && enabled) colors.danger.copy(alpha = 0.35f) else colors.borderSoft, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (danger && enabled) colors.danger.copy(alpha = 0.14f) else colors.surfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                color = colors.textFaint,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun DeletePostDialog(
    post: WeiboPost,
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = AppTheme.colors
    BackHandler(enabled = !deleting, onBack = onDismiss)
    Dialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(enabled = !deleting, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .appPanel(color = colors.surfaceRaised)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.dangerSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = "删除这条微博？",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(13.dp))
                if (post.text.isNotBlank()) {
                    Text(
                        text = post.text,
                        color = colors.textDim,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceAlt)
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    )
                    Spacer(Modifier.height(11.dp))
                }
                Text(
                    text = "删除后无法恢复，这条微博下的评论和转发也会一并消失。",
                    color = colors.textFaint,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlineButton(
                        label = "取消",
                        onClick = onDismiss,
                        enabled = !deleting,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (deleting) colors.dangerSoft else colors.danger)
                            .clickable(enabled = !deleting, onClick = onConfirm),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (deleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = colors.danger,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(
                            text = if (deleting) "删除中" else "删除",
                            color = if (deleting) colors.danger else colors.onDanger,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
