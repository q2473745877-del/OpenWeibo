package com.openweibo.app.ui.video

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.data.VideoPreview
import com.openweibo.app.data.VideoStream
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.OutlineButton
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.delay

private const val VIDEO_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: VideoPreview,
    title: String,
    cookies: String,
    onClose: () -> Unit,
    downloading: Boolean = false,
    downloadProgress: Float = -1f,
    onDownload: (VideoStream) -> Unit = {},
    onSnackbar: (String) -> Unit = {}
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var streamIndex by remember(video) { mutableIntStateOf(0) }
    var retryToken by remember(video) { mutableIntStateOf(0) }
    var playing by remember(video) { mutableStateOf(false) }
    var buffering by remember(video) { mutableStateOf(true) }
    var ended by remember(video) { mutableStateOf(false) }
    var errorMessage by remember(video) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(video) { mutableStateOf(true) }
    var muted by remember(video) { mutableStateOf(false) }
    var dragging by remember(video) { mutableStateOf(false) }
    var seekPosition by remember(video) { mutableFloatStateOf(0f) }
    var positionMs by remember(video) { mutableLongStateOf(0L) }
    var durationMs by remember(video) { mutableLongStateOf(0L) }
    var showQualityPicker by remember(video) { mutableStateOf(false) }
    var showDownloadPicker by remember(video) { mutableStateOf(false) }
    var pendingSeekMs by remember(video) { mutableLongStateOf(0L) }
    val currentStream = video.streams.getOrNull(streamIndex)

    val exoPlayer = remember(video) {
        val headers = buildMap {
            put("Referer", "https://weibo.com/")
            put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            if (cookies.isNotBlank()) put("Cookie", cookies.replace("\r", "").replace("\n", ""))
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(VIDEO_USER_AGENT)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )
            }
    }

    BackHandler {
        when {
            showQualityPicker -> showQualityPicker = false
            showDownloadPicker -> showDownloadPicker = false
            else -> onClose()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                ended = playbackState == Player.STATE_ENDED
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                if (streamIndex < video.streams.lastIndex) {
                    pendingSeekMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    streamIndex += 1
                } else {
                    errorMessage = error.message ?: "视频播放失败"
                    buffering = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, streamIndex, retryToken) {
        val stream = video.streams.getOrNull(streamIndex)
        if (stream == null || stream.url.isBlank()) {
            errorMessage = "微博没有返回可播放的视频地址"
            buffering = false
            return@LaunchedEffect
        }
        val resumeMs = pendingSeekMs
        pendingSeekMs = 0L
        errorMessage = null
        buffering = true
        ended = false
        exoPlayer.setMediaItem(MediaItem.fromUri(stream.url))
        exoPlayer.prepare()
        if (resumeMs > 0L) {
            exoPlayer.seekTo(resumeMs)
            positionMs = resumeMs
        } else {
            positionMs = 0L
        }
        exoPlayer.playWhenReady = true
        exoPlayer.volume = if (muted) 0f else 1f
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(exoPlayer, dragging) {
        while (true) {
            if (!dragging) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val playerDuration = exoPlayer.duration
                durationMs = if (playerDuration > 0L) {
                    playerDuration
                } else {
                    video.durationSeconds * 1000L
                }
            }
            delay(350)
        }
    }

    fun selectStream(index: Int) {
        if (index !in video.streams.indices) return
        showQualityPicker = false
        if (index == streamIndex) {
            if (errorMessage != null) retryToken += 1
            return
        }
        pendingSeekMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        streamIndex = index
        retryToken += 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030303))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
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
                    contentDescription = "关闭视频",
                    onClick = onClose,
                    tint = Color.White.copy(alpha = 0.86f)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "微博视频" },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MonoLabel(
                    text = "VIDEO / IN-APP PLAYER",
                    color = Color.White.copy(alpha = 0.36f),
                    size = 8.5
                )
            }
            if (video.streams.size > 1) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(7.dp))
                        .clickable { showQualityPicker = true }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentStream?.displayLabel ?: "AUTO",
                        color = Color.White.copy(alpha = 0.82f),
                        style = monoStyle(9, letterSpacing = 0.5)
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "选择视频分辨率",
                        tint = Color.White.copy(alpha = 0.54f),
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            IconAction(
                icon = Icons.Outlined.Download,
                contentDescription = if (downloading) "正在下载视频" else "下载视频",
                onClick = {
                    if (downloading) {
                        onSnackbar("正在下载视频，请稍候")
                    } else {
                        showQualityPicker = false
                        showDownloadPicker = true
                    }
                },
                tint = Color.White.copy(alpha = if (downloading) 0.34f else 0.82f),
                iconSize = 19.dp,
                enabled = !downloading
            )
            IconAction(
                icon = if (muted) {
                    Icons.AutoMirrored.Outlined.VolumeOff
                } else {
                    Icons.AutoMirrored.Outlined.VolumeUp
                },
                contentDescription = if (muted) "取消静音" else "静音",
                onClick = {
                    muted = !muted
                    exoPlayer.volume = if (muted) 0f else 1f
                },
                tint = Color.White.copy(alpha = 0.82f),
                iconSize = 19.dp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        keepScreenOn = true
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            controlsVisible = !controlsVisible
                        }
                    }
            )

            if (downloading) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.62f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Color.White.copy(alpha = 0.82f),
                        strokeWidth = 1.4.dp
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (downloadProgress >= 0f) {
                            "正在下载视频 ${(downloadProgress * 100).toInt()}%"
                        } else {
                            "正在下载视频"
                        },
                        color = Color.White.copy(alpha = 0.82f),
                        style = monoStyle(8.5, letterSpacing = 0.4)
                    )
                }
            }

            if (buffering && errorMessage == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(21.dp),
                        color = Color.White.copy(alpha = 0.8f),
                        strokeWidth = 1.8.dp
                    )
                }
            }

            errorMessage?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(13.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.66f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = "视频播放失败",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = message.take(160),
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(13.dp))
                    OutlineButton(
                        label = "重新加载",
                        onClick = {
                            streamIndex = 0
                            retryToken += 1
                        }
                    )
                }
            }

            if (controlsVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            IconAction(
                                icon = when {
                                    ended -> Icons.Outlined.PlayArrow
                                    playing -> Icons.Outlined.Pause
                                    else -> Icons.Outlined.PlayArrow
                                },
                                contentDescription = if (playing) "暂停" else "播放",
                                onClick = {
                                    if (ended) {
                                        exoPlayer.seekTo(0L)
                                        exoPlayer.play()
                                    } else if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                },
                                tint = Color.White,
                                iconSize = 23.dp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (ended) {
                                "已播放完毕"
                            } else if (playing) {
                                "正在播放"
                            } else {
                                "已暂停"
                            },
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.weight(1f))
                        MonoLabel(
                            text = "${TimeUtils.formatPlaybackPosition(positionMs)} / " +
                                TimeUtils.formatPlaybackPosition(durationMs),
                            color = Color.White.copy(alpha = 0.58f),
                            size = 9
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    val max = durationMs.coerceAtLeast(1L).toFloat()
                    val current = if (dragging) {
                        seekPosition
                    } else {
                        positionMs.toFloat().coerceIn(0f, max)
                    }
                    Slider(
                        value = current,
                        onValueChange = { value ->
                            dragging = true
                            seekPosition = value
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(seekPosition.toLong())
                            positionMs = seekPosition.toLong()
                            dragging = false
                        },
                        valueRange = 0f..max,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White.copy(alpha = 0.82f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "视频由微博 CDN 提供 · 应用内播放",
                            color = Color.White.copy(alpha = 0.3f),
                            style = monoStyle(8.5, letterSpacing = 0.3)
                        )
                        if (video.streams.size > 1) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .clickable { showQualityPicker = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "分辨率 " + (currentStream?.displayLabel ?: "AUTO"),
                                    color = Color.White.copy(alpha = 0.42f),
                                    style = monoStyle(8.5, letterSpacing = 0.5)
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.38f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (showQualityPicker) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .clickable { showQualityPicker = false }
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(Color(0xFF0C0C0E))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "选择分辨率",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        MonoLabel(
                            text = "${video.streams.size} SOURCES",
                            color = Color.White.copy(alpha = 0.34f),
                            size = 8.5
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    video.streams.forEachIndexed { index, stream ->
                        QualityOptionRow(
                            stream = stream,
                            selected = index == streamIndex,
                            onClick = { selectStream(index) }
                        )
                    }
                }
            }

            if (showDownloadPicker) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .clickable { showDownloadPicker = false }
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(Color(0xFF0C0C0E))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "下载视频",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        MonoLabel(
                            text = "MOVIES/OPENWEIBO",
                            color = Color.White.copy(alpha = 0.34f),
                            size = 8.5
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "选择要保存的清晰度，MP4 视频会直接写入系统相册。",
                        color = Color.White.copy(alpha = 0.38f),
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    video.streams.forEachIndexed { index, stream ->
                        DownloadOptionRow(
                            stream = stream,
                            selected = index == streamIndex,
                            onClick = {
                                showDownloadPicker = false
                                onDownload(stream)
                            }
                        )
                    }
                    if (video.streams.none { !it.isHls }) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "当前视频只返回了 HLS 流，暂不支持直接保存为 MP4；可以继续在应用内在线播放。",
                            color = Color.White.copy(alpha = 0.36f),
                            fontSize = 10.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityOptionRow(
    stream: VideoStream,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stream.displayLabel,
                color = Color.White.copy(alpha = if (selected) 0.94f else 0.78f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stream.formatLabel + if (selected) " · 当前播放" else "",
                color = Color.White.copy(alpha = 0.34f),
                style = monoStyle(8.5, letterSpacing = 0.4)
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "当前分辨率",
                tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun DownloadOptionRow(
    stream: VideoStream,
    selected: Boolean,
    onClick: () -> Unit
) {
    val downloadable = !stream.isHls
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(enabled = downloadable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stream.displayLabel,
                color = Color.White.copy(alpha = if (downloadable) 0.9f else 0.36f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    stream.isHls -> "HLS · 暂不支持离线保存"
                    stream.formatLabel == "MP4" -> "MP4 · 保存到 Movies/OpenWeibo"
                    else -> "视频流 · 保存为 MP4"
                } + if (selected) " · 当前播放" else "",
                color = Color.White.copy(alpha = if (downloadable) 0.34f else 0.24f),
                style = monoStyle(8.5, letterSpacing = 0.4)
            )
        }
        Icon(
            Icons.Outlined.Download,
            contentDescription = if (downloadable) "下载此清晰度" else "此清晰度不支持下载",
            tint = Color.White.copy(alpha = if (downloadable) 0.68f else 0.2f),
            modifier = Modifier.size(17.dp)
        )
    }
}
