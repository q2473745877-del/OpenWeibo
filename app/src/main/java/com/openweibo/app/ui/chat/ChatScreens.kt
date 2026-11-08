package com.openweibo.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweibo.app.data.ChatConversation
import com.openweibo.app.data.ChatMediaType
import com.openweibo.app.data.ChatMessage
import com.openweibo.app.data.MediaFile
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.ui.components.Avatar
import com.openweibo.app.ui.components.EmptyState
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.ScreenHeader
import com.openweibo.app.ui.feed.NetworkImage
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.Geist
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChatThreadScreen(
    viewModel: ChatThreadViewModel,
    onBack: () -> Unit,
    onSnackbar: (String) -> Unit,
    onLoginExpired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.start() }
    // Only jump to the newest message when the list grows at the *end*, so loading
    // older history (prepended) doesn't yank the viewport away.
    var lastCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.messages.size) {
        val size = state.messages.size
        if (size > lastCount && lastCount > 0 && !state.loadingOlder) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
        lastCount = size
    }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            onSnackbar(it)
            viewModel.consumeFeedback()
        }
    }
    LaunchedEffect(state.authExpired) {
        if (state.authExpired) onLoginExpired()
    }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "media"
            val mime = resolver.getType(uri).orEmpty()
            val size = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull() ?: 0L
            viewModel.sendMedia(
                MediaFile(
                    uri = uri,
                    name = name,
                    mimeType = mime,
                    size = size
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
                tint = colors.textDim
            )
            Avatar(
                url = state.peerAvatar,
                name = state.peerName,
                size = 30.dp,
                modifier = Modifier.clip(CircleShape)
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.peerName.ifBlank { "私信" },
                    color = colors.text,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MonoLabel("DIRECT MESSAGE", size = 8)
            }
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.textFaint,
                    strokeWidth = 1.4.dp
                )
            } else {
                IconAction(
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "刷新私信",
                    onClick = { viewModel.refresh() },
                    tint = colors.textFaint,
                    iconSize = 17.dp
                )
            }
        }
        Hairline()

        when {
            state.loading && state.messages.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.textFaint,
                        strokeWidth = 1.5.dp
                    )
                }
            }

            state.error != null && state.messages.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "私信加载失败",
                    body = state.error.orEmpty(),
                    actionLabel = "重试",
                    onAction = { viewModel.refresh() }
                )
            }

            state.messages.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.MailOutline,
                    title = "还没有消息",
                    body = "发送第一条私信开始对话。"
                )
            }

            else -> {
                // Weibo pages DM history backwards (since_id), so older messages are
                // fetched when the user scrolls to the very top.
                LaunchedEffect(listState) {
                    snapshotFlow {
                        listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0
                    }
                        .distinctUntilChanged()
                        .collect { atTop -> if (atTop) viewModel.loadOlder() }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = 10.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "older") {
                        OlderHistoryHeader(
                            loading = state.loadingOlder,
                            hasOlder = state.hasOlder,
                            onLoadOlder = { viewModel.loadOlder() }
                        )
                    }
                    items(items = state.messages, key = { it.id }) { message ->
                        MessageBubble(message = message, peerAvatar = state.peerAvatar)
                    }
                }
            }
        }

        Hairline()
        ChatComposer(
            value = draft,
            onValueChange = { draft = it.take(2000) },
            sending = state.sending,
            onSend = {
                if (draft.isNotBlank()) {
                    viewModel.sendText(draft)
                    draft = ""
                }
            },
            onPickMedia = {
                pickMedia.launch(arrayOf("image/*", "video/*"))
            }
        )
    }
}

@Composable
private fun OlderHistoryHeader(
    loading: Boolean,
    hasOlder: Boolean,
    onLoadOlder: () -> Unit
) {
    val colors = AppTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = hasOlder && !loading, onClick = onLoadOlder)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = colors.textFaint,
                    strokeWidth = 1.3.dp
                )
                Spacer(Modifier.width(7.dp))
                MonoLabel("正在加载更早的私信", size = 8.5)
            }

            hasOlder -> Text(
                text = "更早前的私信",
                color = colors.textDim,
                fontSize = 11.5.sp
            )

            else -> MonoLabel("没有更早的私信了", size = 8.5)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, peerAvatar: String) {
    val colors = AppTheme.colors
    val outgoing = message.outgoing
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        if (!outgoing) {
            Avatar(
                url = peerAvatar,
                name = message.fromName,
                size = 28.dp,
                modifier = Modifier.clip(CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (outgoing) colors.surfaceRaised else colors.surface)
                    .border(
                        1.dp,
                        if (outgoing) colors.border else colors.borderSoft,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 11.dp, vertical = 8.dp)
            ) {
                Column {
                    if (message.mediaUrl != null && message.mediaType != null) {
                        if (message.mediaType == ChatMediaType.Video) {
                            MediaPlaceholder(label = "视频")
                        } else {
                            NetworkImage(
                                url = message.mediaUrl,
                                modifier = Modifier
                                    .widthIn(max = 200.dp)
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                showProgress = true
                            )
                        }
                        if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            color = if (outgoing) colors.text else colors.textDim,
                            fontFamily = Geist,
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            MonoLabel(TimeUtils.relativeTime(message.createdAtMillis), size = 7.5)
        }
    }
}

@Composable
private fun MediaPlaceholder(label: String) {
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppTheme.colors.surfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        MonoLabel(label, size = 9)
    }
}

@Composable
private fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
    onPickMedia: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        IconAction(
            icon = Icons.Outlined.AttachFile,
            contentDescription = "发送图片、动图或视频",
            onClick = onPickMedia,
            tint = colors.textDim,
            enabled = !sending
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(11.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp)
        ) {
            if (value.isEmpty()) {
                Text(
                    text = "发送私信…",
                    color = colors.textFaint,
                    fontSize = 13.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = colors.text,
                    fontFamily = Geist,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.text),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 20.dp, max = 110.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (value.isBlank() || sending) colors.surfaceAlt else colors.accent)
                .clickable(enabled = value.isNotBlank() && !sending, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = colors.textDim,
                    strokeWidth = 1.5.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "发送",
                    tint = if (value.isBlank()) colors.textFaint else colors.onAccent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
