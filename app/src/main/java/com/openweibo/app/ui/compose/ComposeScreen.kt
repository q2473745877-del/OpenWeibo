package com.openweibo.app.ui.compose

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.openweibo.app.data.ComposeVisibility
import com.openweibo.app.data.MediaFile
import com.openweibo.app.data.Session
import com.openweibo.app.ui.components.Avatar
import com.openweibo.app.ui.components.FilterPill
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.PrimaryButton
import com.openweibo.app.ui.components.SectionLabel
import com.openweibo.app.ui.components.appPanel
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun ComposeScreen(
    viewModel: ComposeViewModel,
    session: Session,
    onClose: () -> Unit,
    onPublished: () -> Unit,
    onLoginExpired: () -> Unit,
    onEdited: (String) -> Unit = {}
) {
    val colors = AppTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    uris.mapNotNull { mediaFileFromUri(context, it) }
                }
                if (files.isEmpty()) {
                    viewModel.reportError("无法读取所选图片，请重新选择")
                } else {
                    viewModel.addMedia(files)
                }
            }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) { mediaFileFromUri(context, uri) }
                if (file == null) {
                    viewModel.reportError("无法读取所选视频，请重新选择")
                } else {
                    viewModel.addMedia(listOf(file))
                }
            }
        }
    }

    LaunchedEffect(state.published) {
        if (state.published) {
            if (state.editing) onEdited(state.content) else onPublished()
        }
    }
    LaunchedEffect(state.authExpired) {
        if (state.authExpired) onLoginExpired()
    }

    BackHandler {
        if (!state.publishing) onClose()
    }

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
                .imePadding()
        ) {
            ComposeTopBar(
                publishing = state.publishing,
                editing = state.editing,
                onClose = onClose
            )
            Hairline()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AuthorRow(
                    session = session,
                    visibility = state.visibility,
                    editing = state.editing
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .appPanel()
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = state.content,
                        onValueChange = viewModel::updateContent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 148.dp),
                        enabled = !state.publishing,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.text),
                        cursorBrush = SolidColor(colors.text),
                        decorationBox = { innerTextField ->
                            Box {
                                if (state.content.isBlank()) {
                                    Text(
                                        text = if (state.editing) "修改微博正文…" else "分享你的新鲜事…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.textFaint
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MonoLabel("TEXT / 支持话题、@好友和链接", size = 8.5)
                        Spacer(Modifier.weight(1f))
                        MonoLabel(
                            text = "${state.content.length} / ${ComposeViewModel.MAX_CONTENT_LENGTH}",
                            color = if (state.content.length > ComposeViewModel.MAX_CONTENT_LENGTH - 100) {
                                colors.highlight
                            } else {
                                colors.textFaint
                            },
                            size = 8.5
                        )
                    }
                }

                if (state.editing) {
                    SectionLabel("EDIT / 编辑模式")
                    EditNotice(existingImageCount = state.existingImageCount)
                } else {
                    SectionLabel("MEDIA / 原图 · GIF · 视频")
                    val media = state.media
                    if (media.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            media.chunked(3).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowItems.forEach { item ->
                                        MediaTile(
                                            item = item,
                                            onRemove = { viewModel.removeMedia(item.id) },
                                            enabled = !state.publishing,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - rowItems.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    val hasVideo = media.any { it.isVideo }
                    val imageCount = media.count { !it.isVideo }
                    if (hasVideo) {
                        AddMediaTile(
                            label = "更换视频",
                            icon = Icons.Outlined.VideoLibrary,
                            enabled = !state.publishing,
                            onClick = { videoPicker.launch(arrayOf("video/*")) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (imageCount < ComposeViewModel.MAX_IMAGES) {
                                AddMediaTile(
                                    label = "添加图片 / GIF",
                                    icon = Icons.Outlined.Image,
                                    enabled = !state.publishing,
                                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            AddMediaTile(
                                label = "添加视频",
                                icon = Icons.Outlined.VideoLibrary,
                                enabled = !state.publishing,
                                onClick = { videoPicker.launch(arrayOf("video/*")) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    SectionLabel("VISIBILITY / 谁可以看")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ComposeVisibility.entries.forEach { visibility ->
                            FilterPill(
                                label = visibility.label,
                                selected = state.visibility == visibility,
                                onClick = { viewModel.setVisibility(visibility) }
                            )
                        }
                    }
                    Text(
                        text = state.visibility.hint,
                        color = colors.textFaint,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )

                    UploadNotes(hasVideo = hasVideo)
                }

                state.error?.let { message ->
                    ErrorBanner(message = message)
                }

                if (state.publishing) {
                    UploadProgress(state = state)
                }

                Spacer(Modifier.height(4.dp))
            }

            Hairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MonoLabel(
                        text = when {
                            state.editing && state.existingImageCount > 0 ->
                                "${state.existingImageCount} MEDIA KEPT"
                            state.editing -> "TEXT EDIT"
                            state.media.any { it.isVideo } -> "1 VIDEO"
                            state.media.isNotEmpty() -> "${state.media.size} IMAGE${if (state.media.size > 1) "S" else ""}"
                            else -> "TEXT ONLY"
                        },
                        size = 8.5
                    )
                    Spacer(Modifier.height(3.dp))
                    MonoLabel(
                        text = when {
                            state.publishing -> "SAVING / 请勿关闭页面"
                            state.editing -> "TEXT ONLY / MEDIA UNCHANGED"
                            else -> "ORIGINAL BYTES / NO COMPRESSION"
                        },
                        color = if (state.publishing) colors.highlight else colors.textFaint,
                        size = 8
                    )
                }
                PrimaryButton(
                    label = when {
                        state.publishing && state.editing -> "保存中"
                        state.publishing -> "发布中"
                        state.editing -> "保存修改"
                        else -> "发布微博"
                    },
                    onClick = viewModel::publish,
                    enabled = state.canPublish,
                    loading = state.publishing,
                    modifier = Modifier.widthIn(min = 112.dp)
                )
            }
        }
    }
}

@Composable
private fun ComposeTopBar(
    publishing: Boolean,
    editing: Boolean,
    onClose: () -> Unit
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconAction(
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "返回",
            onClick = onClose,
            enabled = !publishing,
            tint = colors.textDim
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (editing) "编辑微博" else "发布微博",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text
            )
            Spacer(Modifier.height(3.dp))
            MonoLabel(
                if (editing) {
                    "EDIT / TEXT ONLY · MEDIA KEPT"
                } else {
                    "COMPOSE / ORIGINAL IMAGE · GIF · VIDEO"
                }
            )
        }
        MonoLabel(
            text = when {
                publishing && editing -> "SAVING"
                publishing -> "UPLOADING"
                else -> "READY"
            },
            color = if (publishing) colors.highlight else colors.textFaint,
            size = 8.5
        )
    }
}

@Composable
private fun AuthorRow(
    session: Session,
    visibility: ComposeVisibility,
    editing: Boolean
) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appPanel()
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            url = session.avatarUrl,
            name = session.screenName,
            size = 38.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.screenName,
                color = colors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            MonoLabel("@${session.uid}", size = 8.5)
        }
        Text(
            text = if (editing) "编辑中" else visibility.label,
            color = if (editing) colors.highlight else colors.textDim,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EditNotice(existingImageCount: Int) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appPanel()
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceAlt),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = colors.highlight,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "仅修改正文",
                color = colors.text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = if (existingImageCount > 0) {
                "这条微博原有的 $existingImageCount 张图片 / GIF 会保持不变，不会被重新压缩或删除。"
            } else {
                "这条微博没有配图或视频，修改后正文会直接更新。"
            },
            color = colors.textDim,
            fontSize = 11.5.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "可见范围、发布时间和互动数据不会改变；保存后微博会显示「已编辑」标记。",
            color = colors.textFaint,
            fontSize = 11.5.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun MediaTile(
    item: ComposeMediaItem,
    onRemove: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(colors.surfaceAlt)
            .border(1.dp, colors.border, shape)
    ) {
        if (item.isVideo) {
            val meta = rememberVideoMeta(item.file.uri)
            if (meta.thumbnail != null) {
                Image(
                    bitmap = meta.thumbnail.asImageBitmap(),
                    contentDescription = item.file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = colors.textFaint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            MediaBadge(
                text = if (meta.durationSeconds > 0) {
                    formatDuration(meta.durationSeconds)
                } else {
                    "视频"
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            )
        } else {
            AsyncImage(
                model = item.file.uri,
                contentDescription = item.file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.isGif) {
                MediaBadge(
                    text = "GIF",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(enabled = enabled, onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "移除",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun MediaBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Color.White,
        style = monoStyle(8, FontWeight.Medium, 0.4)
    )
}

@Composable
private fun AddMediaTile(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .height(62.dp)
            .clip(shape)
            .background(colors.surfaceAlt)
            .border(1.dp, colors.borderSoft, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.surfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.textDim,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            color = colors.textDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UploadNotes(hasVideo: Boolean) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appPanel(color = colors.surfaceAlt, borderColor = colors.borderSoft)
            .padding(12.dp)
    ) {
        MonoLabel("UPLOAD NOTES", color = colors.textFaint, size = 8.5)
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasVideo) {
                "视频按原始文件分片上传，本地不压缩、不转码；上传后由微博云端转码。发布前会尝试自动生成封面。视频与图片不能混发，单个视频不超过 4GB。"
            } else {
                "图片与 GIF 以原始字节上传，不压缩、不转码；GIF 会保留动画。单张不超过 10MB，最多 9 张，可与文字一起发布。"
            },
            color = colors.textFaint,
            fontSize = 11.5.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.dangerSoft)
            .border(1.dp, colors.danger.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = message,
            color = colors.danger,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun UploadProgress(state: ComposeUiState) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appPanel()
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(
                text = state.stage.uppercase(),
                color = colors.textDim,
                size = 8.5
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(state.progress * 100f).toInt().coerceIn(0, 100)}%",
                color = colors.text,
                style = monoStyle(10, FontWeight.Medium, 0.5)
            )
        }
        Spacer(Modifier.height(9.dp))
        LinearProgressIndicator(
            progress = { state.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = colors.text,
            trackColor = colors.borderSoft
        )
    }
}

private data class VideoMeta(
    val thumbnail: Bitmap?,
    val durationSeconds: Int
)

@Composable
private fun rememberVideoMeta(uri: Uri): VideoMeta {
    val context = LocalContext.current
    val meta = remember(uri) { mutableStateOf(VideoMeta(null, 0)) }
    LaunchedEffect(uri) {
        val loaded = withContext(Dispatchers.IO) { loadVideoMeta(context, uri) }
        meta.value = loaded
    }
    return meta.value
}

private fun loadVideoMeta(context: Context, uri: Uri): VideoMeta {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val duration = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.div(1000L)
            ?.toInt()
            ?: 0
        val frame = retriever.getFrameAtTime(
            0,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        )
        val thumbnail = frame?.let { bitmap ->
            val longest = max(bitmap.width, bitmap.height)
            if (longest > 720 && longest > 0) {
                val ratio = 720f / longest.toFloat()
                val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, width, height, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }
        }
        VideoMeta(thumbnail, duration)
    } catch (_: Exception) {
        VideoMeta(null, 0)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return "%d:%02d".format(minutes, remaining)
}

private fun mediaFileFromUri(context: Context, uri: Uri): MediaFile? {
    val resolver = context.contentResolver
    var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "media"
    var size = -1L
    try {
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex).ifBlank { displayName }
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    } catch (_: Exception) {
        // Fall through to the descriptor length below.
    }
    if (size <= 0L) {
        size = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }
    val mimeType = resolveMimeType(resolver.getType(uri), displayName)
    if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) {
        return null
    }
    try {
        resolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    } catch (_: Exception) {
        // Some providers grant only temporary access; the URI still works now.
    }
    return MediaFile(
        uri = uri,
        name = displayName,
        mimeType = mimeType,
        size = size
    )
}

private fun resolveMimeType(providerType: String?, name: String): String {
    val normalized = providerType?.lowercase()
    if (!normalized.isNullOrBlank() &&
        normalized != "application/octet-stream" &&
        normalized != "binary/octet-stream"
    ) {
        return normalized
    }
    val lower = name.lowercase()
    return when {
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
        lower.endsWith(".mov") -> "video/quicktime"
        lower.endsWith(".webm") -> "video/webm"
        lower.endsWith(".mkv") -> "video/x-matroska"
        lower.endsWith(".flv") -> "video/x-flv"
        else -> providerType ?: "application/octet-stream"
    }
}
