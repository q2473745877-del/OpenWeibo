package com.openweibo.app.ui.account

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openweibo.app.data.Session
import com.openweibo.app.data.ThemeMode
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.ui.components.Avatar
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.OutlineButton
import com.openweibo.app.ui.components.ScreenHeader
import com.openweibo.app.ui.components.SectionLabel
import com.openweibo.app.ui.components.SettingRow
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.monoStyle

@Composable
fun AccountScreen(
    session: Session,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    var confirmLogout by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 700.dp)
                .align(Alignment.TopCenter)
        ) {
            ScreenHeader(
                title = "我的",
                subtitle = "ACCOUNT / LOCAL SESSION"
            )
            Hairline()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                ProfilePanel(
                    session = session,
                    onOpenProfile = onOpenProfile,
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing
                )
                Spacer(Modifier.height(22.dp))
                SectionLabel("SYSTEM")
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appPanel()
                ) {
                    SettingRow(
                        title = "深色模式",
                        subtitle = "选择界面外观，修改后立即生效并保存在本机",
                        trailing = { ValueTag(themeModeLabel(themeMode)) }
                    )
                    Hairline()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 15.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeOption(
                            label = "跟随系统",
                            selected = themeMode == ThemeMode.System,
                            onClick = { onThemeModeChange(ThemeMode.System) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            label = "开启",
                            selected = themeMode == ThemeMode.Dark,
                            onClick = { onThemeModeChange(ThemeMode.Dark) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeModeOption(
                            label = "关闭",
                            selected = themeMode == ThemeMode.Light,
                            onClick = { onThemeModeChange(ThemeMode.Light) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Hairline()
                    SettingRow(
                        title = "信息流排序",
                        subtitle = "严格按微博发布时间倒序合并，不插入推荐内容",
                        trailing = { ValueTag("LATEST ↓") }
                    )
                    Hairline()
                    SettingRow(
                        title = "原图保存",
                        subtitle = "保存微博提供的最高清版本，不进行二次压缩",
                        trailing = { ValueTag("ORIGINAL") }
                    )
                    Hairline()
                    SettingRow(
                        title = "保存位置",
                        subtitle = "Android 10+ 图片写入 Pictures，视频写入 Movies，无需存储权限",
                        trailing = { ValueTag("OPENWEIBO") }
                    )
                }

                Spacer(Modifier.height(22.dp))
                SectionLabel("SESSION")
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appPanel()
                ) {
                    SettingRow(
                        title = "登录状态",
                        subtitle = "UID ${session.uid}",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(colors.success)
                                )
                                Spacer(Modifier.width(6.dp))
                                MonoLabel("CONNECTED", color = colors.textDim, size = 8.5)
                            }
                        }
                    )
                    Hairline()
                    SettingRow(
                        title = "会话存储",
                        subtitle = "Android Keystore · AES-GCM 加密，仅保存在本机",
                        trailing = {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = colors.textFaint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    )
                    Hairline()
                    SettingRow(
                        title = "我的关注",
                        subtitle = "查看你关注的账号，支持分页浏览",
                        onClick = onOpenFollowing,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MonoLabel(
                                    TimeUtils.formatCount(session.followingCount),
                                    size = 9
                                )
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textFaint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                    Hairline()
                    SettingRow(
                        title = "我的粉丝",
                        subtitle = "查看关注你的账号，支持分页浏览",
                        onClick = onOpenFollowers,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MonoLabel(
                                    TimeUtils.formatCount(session.followersCount),
                                    size = 9
                                )
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textFaint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                    Hairline()
                    SettingRow(
                        title = "退出登录",
                        subtitle = "清除本机保存的微博会话 Cookie",
                        onClick = { confirmLogout = true },
                        danger = true,
                        trailing = {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = colors.danger.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                Spacer(Modifier.height(22.dp))
                SectionLabel("ABOUT")
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appPanel()
                        .padding(15.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "OpenWeibo",
                            color = colors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(8.dp))
                        ValueTag("v0.9.1")
                        Spacer(Modifier.weight(1f))
                        MonoLabel("WINDOWS EDITION", size = 8)
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = "非官方微博客户端。界面为 OpenCode Windows 风格的本地优先阅读器；所有内容来自微博公开接口，登录凭证不会上传到任何第三方服务器。",
                        color = colors.textFaint,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonoLabel("SORT", size = 8)
                        Spacer(Modifier.width(6.dp))
                        Text("发布时间倒序", color = colors.textDim, fontSize = 10.5.sp)
                        Spacer(Modifier.width(14.dp))
                        MonoLabel("RENDER", size = 8)
                        Spacer(Modifier.width(6.dp))
                        Text("Geist / NATIVE", color = colors.textDim, fontSize = 10.5.sp)
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (confirmLogout) {
        LogoutDialog(
            onDismiss = { confirmLogout = false },
            onConfirm = {
                confirmLogout = false
                onLogout()
            }
        )
    }
}

@Composable
private fun ProfilePanel(
    session: Session,
    onOpenProfile: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit
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
                url = session.avatarUrl,
                name = session.screenName,
                size = 58.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenProfile)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.screenName,
                        color = colors.text,
                        fontSize = 20.sp,
                        lineHeight = 25.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onOpenProfile)
                    )
                    if (session.verified) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "已认证",
                            tint = colors.highlight,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonoLabel("UID ${session.uid}", size = 9)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(colors.textFaint)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "微博会话已连接",
                        color = colors.textFaint,
                        fontSize = 10.5.sp
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                MonoLabel("LOCAL", size = 7.5, color = colors.textDim)
            }
        }
        if (session.description.isNotBlank()) {
            Spacer(Modifier.height(13.dp))
            Text(
                text = session.description,
                color = colors.textDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ProfileStat(
                label = "粉丝",
                value = TimeUtils.formatCount(session.followersCount),
                onClick = onOpenFollowers
            )
            ProfileStat(
                label = "关注",
                value = TimeUtils.formatCount(session.followingCount),
                onClick = onOpenFollowing
            )
            ProfileStat(
                label = "微博",
                value = TimeUtils.formatCount(session.statusesCount),
                onClick = onOpenProfile
            )
        }
    }
}

@Composable
private fun ProfileStat(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val colors = AppTheme.colors
    Column(
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 2.dp, vertical = 2.dp)
        } else {
            Modifier
        }
    ) {
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
private fun ValueTag(text: String) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(colors.surfaceAlt)
            .border(1.dp, colors.borderSoft, RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = monoStyle(8, letterSpacing = 0.3),
            color = colors.textDim,
            maxLines = 1
        )
    }
}

@Composable
private fun ThemeModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) colors.accent else colors.surfaceAlt)
            .border(
                1.dp,
                if (selected) colors.accent else colors.borderSoft,
                shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.onAccent else colors.textDim,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> "跟随系统"
    ThemeMode.Dark -> "已开启"
    ThemeMode.Light -> "已关闭"
}

@Composable
private fun LogoutDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = AppTheme.colors
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
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = "退出当前账号？",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "这只会清除本机保存的微博登录会话，不会影响你的微博账号或已发布的内容。退出后需要重新登录才能查看关注时间线。",
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
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.danger)
                            .clickable(onClick = onConfirm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "退出登录",
                            color = colors.onDanger,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
