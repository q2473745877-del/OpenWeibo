package com.openweibo.app.ui.compose

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.ComposeVisibility
import com.openweibo.app.data.EditablePost
import com.openweibo.app.data.MediaFile
import com.openweibo.app.data.Session
import com.openweibo.app.data.WeiboApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

data class ComposeMediaItem(val file: MediaFile) {
    val id: String
        get() = file.uri.toString()

    val isVideo: Boolean
        get() = file.isVideo

    val isGif: Boolean
        get() = file.isGif
}

data class ComposeUiState(
    val content: String = "",
    val visibility: ComposeVisibility = ComposeVisibility.Public,
    val media: List<ComposeMediaItem> = emptyList(),
    val publishing: Boolean = false,
    val stage: String = "",
    val progress: Float = 0f,
    val error: String? = null,
    val published: Boolean = false,
    val authExpired: Boolean = false,
    val editing: Boolean = false,
    val existingImageCount: Int = 0
) {
    val canPublish: Boolean
        get() = !publishing &&
            (content.isNotBlank() || media.isNotEmpty() || (editing && existingImageCount > 0)) &&
            content.length <= ComposeViewModel.MAX_CONTENT_LENGTH
}

class ComposeViewModel(
    private val container: AppContainer,
    private val editingPost: EditablePost? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        ComposeUiState(
            content = editingPost?.text?.take(MAX_CONTENT_LENGTH).orEmpty(),
            editing = editingPost != null,
            existingImageCount = editingPost?.picIds?.size ?: 0
        )
    )
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    private var publishJob: Job? = null

    fun updateContent(value: String) {
        val clipped = value.take(MAX_CONTENT_LENGTH)
        _state.update {
            it.copy(
                content = clipped,
                error = if (value.length > MAX_CONTENT_LENGTH) {
                    "正文最多 ${MAX_CONTENT_LENGTH} 字"
                } else {
                    null
                }
            )
        }
    }

    fun setVisibility(visibility: ComposeVisibility) {
        if (editingPost != null) return
        _state.update { it.copy(visibility = visibility) }
    }

    fun addMedia(files: List<MediaFile>) {
        if (files.isEmpty()) return
        if (editingPost != null) {
            reportError("编辑微博暂不支持更换图片或视频")
            return
        }
        val current = _state.value
        if (current.publishing) return

        val videos = files.filter { it.isVideo }
        val images = files.filterNot { it.isVideo }
        if (videos.isNotEmpty() && images.isNotEmpty()) {
            reportError("视频与图片不能同时发布，请分开选择")
            return
        }
        if (videos.size > 1) {
            reportError("一次只能发布一个视频")
            return
        }
        val currentHasVideo = current.media.any { it.isVideo }
        val currentHasImages = current.media.any { !it.isVideo }
        if (videos.isNotEmpty() && currentHasImages) {
            reportError("视频与图片不能同时发布，请先移除图片")
            return
        }
        if (images.isNotEmpty() && currentHasVideo) {
            reportError("视频与图片不能同时发布，请先移除视频")
            return
        }

        val video = videos.firstOrNull()
        if (video != null) {
            if (video.size <= 0L) {
                reportError("无法读取 ${video.name} 的大小，请重新选择")
                return
            }
            if (video.size > MAX_VIDEO_BYTES) {
                reportError("${video.name} 超过 4GB 上限，请更换文件")
                return
            }
            val imagesKept = current.media.filterNot { it.isVideo }
            _state.update {
                it.copy(
                    media = imagesKept + ComposeMediaItem(video),
                    error = null
                )
            }
            return
        }

        val fresh = files.filter { file -> current.media.none { it.file.uri == file.uri } }
        val imageCount = current.media.count { !it.isVideo } + fresh.count { !it.isVideo }
        if (imageCount > MAX_IMAGES) {
            reportError("最多选择 $MAX_IMAGES 张图片（含 GIF）")
            return
        }
        val tooLargeImage = fresh.firstOrNull { !it.isVideo && it.size > MAX_IMAGE_BYTES }
        if (tooLargeImage != null) {
            reportError("${tooLargeImage.name} 超过 10MB，原图不会压缩，请更换文件")
            return
        }
        val unknownSizeImage = fresh.firstOrNull { !it.isVideo && it.size <= 0L }
        if (unknownSizeImage != null) {
            reportError("无法读取 ${unknownSizeImage.name} 的大小，请重新选择")
            return
        }

        if (fresh.isEmpty()) return
        _state.update {
            it.copy(
                media = it.media + fresh.map(::ComposeMediaItem),
                error = null
            )
        }
    }

    fun removeMedia(id: String) {
        if (_state.value.publishing) return
        _state.update { state ->
            state.copy(media = state.media.filterNot { it.id == id }, error = null)
        }
    }

    fun reportError(message: String) {
        if (message.isBlank()) return
        _state.update { it.copy(error = message) }
    }

    fun publish() {
        if (publishJob?.isActive == true) return
        val snapshot = _state.value
        if (snapshot.publishing) return
        if (editingPost != null) {
            if (snapshot.content.isBlank() && snapshot.existingImageCount == 0) {
                reportError("正文不能为空")
                return
            }
        } else if (snapshot.content.isBlank() && snapshot.media.isEmpty()) {
            reportError("写点什么，或添加图片 / GIF / 视频后再发布")
            return
        }
        if (snapshot.content.length > MAX_CONTENT_LENGTH) {
            reportError("正文最多 $MAX_CONTENT_LENGTH 字")
            return
        }
        val session = container.sessionManager.current
        if (session == null) {
            _state.update {
                it.copy(error = "登录状态已过期，请重新登录", authExpired = true)
            }
            return
        }

        publishJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    publishing = true,
                    stage = if (editingPost != null) "保存修改" else "准备上传",
                    progress = 0f,
                    error = null,
                    published = false,
                    authExpired = false
                )
            }
            try {
                val editTarget = editingPost
                val video = snapshot.media.firstOrNull { it.isVideo }
                when {
                    editTarget != null -> editPost(session, snapshot, editTarget)
                    video != null -> publishVideo(session, snapshot, video)
                    else -> publishImages(session, snapshot)
                }
                _state.update {
                    it.copy(
                        publishing = false,
                        stage = if (editingPost != null) "编辑成功" else "发布成功",
                        progress = 1f,
                        published = true
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthenticationException) {
                _state.update {
                    it.copy(
                        error = error.message ?: "登录状态已过期，请重新登录",
                        authExpired = true
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = errorMessage(error)) }
            } finally {
                _state.update {
                    if (it.publishing) it.copy(publishing = false) else it
                }
            }
        }
    }

    private suspend fun editPost(
        session: Session,
        snapshot: ComposeUiState,
        target: EditablePost
    ) {
        _state.update { it.copy(stage = "保存修改", progress = 0.4f) }
        val text = snapshot.content.trim().ifBlank {
            if (target.picIds.isNotEmpty()) "分享图片" else "分享微博"
        }
        container.api.editStatus(
            session = session,
            postId = target.id,
            content = text,
            picIds = target.picIds
        )
        _state.update { it.copy(stage = "编辑成功", progress = 1f) }
    }

    private suspend fun publishImages(session: Session, snapshot: ComposeUiState) {
        val images = snapshot.media
        val picIds = mutableListOf<String>()
        images.forEachIndexed { index, item ->
            val progress = if (images.isEmpty()) 0f else {
                index.toFloat() / images.size.toFloat() * 0.85f
            }
            _state.update {
                it.copy(
                    stage = "上传图片 ${index + 1}/${images.size}",
                    progress = progress
                )
            }
            val bytes = withContext(Dispatchers.IO) { readBytes(item.file) }
            val uploaded = container.api.uploadImage(
                session = session,
                bytes = bytes,
                fileName = item.file.name,
                mimeType = item.file.mimeType
            )
            picIds += uploaded.pid
            _state.update {
                it.copy(
                    progress = (index + 1).toFloat() / images.size.toFloat() * 0.85f
                )
            }
        }
        _state.update { it.copy(stage = "发布微博", progress = 0.92f) }
        container.api.publishStatus(
            session = session,
            content = snapshot.content,
            visibility = snapshot.visibility,
            picIds = picIds
        )
    }

    private suspend fun publishVideo(
        session: Session,
        snapshot: ComposeUiState,
        video: ComposeMediaItem
    ) {
        _state.update { it.copy(stage = "同步网页版会话", progress = 0f) }
        val desktopSession = container.api.prepareDesktopSession(session)
        _state.update { it.copy(stage = "上传视频 0%", progress = 0f) }
        val uploadedVideo = try {
            container.api.uploadVideo(desktopSession, video.file) { progress ->
                val percent = (progress * 100f).toInt().coerceIn(0, 100)
                _state.update {
                    it.copy(
                        stage = "上传视频 $percent%",
                        progress = (progress * 0.86f).coerceIn(0f, 0.86f)
                    )
                }
            }
        } catch (error: AuthenticationException) {
            throw WeiboApiException(
                -2,
                "视频发布需要有效的网页版会话：请到「我的」退出登录后重新登录，再试一次。"
            )
        }

        _state.update { it.copy(stage = "生成视频封面", progress = 0.9f) }
        var coverPid = ""
        var coverUrl = ""
        try {
            val coverBytes = withContext(Dispatchers.IO) { extractCover(video.file) }
            if (coverBytes != null) {
                val uploadedCover = container.api.uploadImage(
                    session = session,
                    bytes = coverBytes,
                    fileName = "openweibo-cover.jpg",
                    mimeType = "image/jpeg"
                )
                coverPid = uploadedCover.pid
                coverUrl = uploadedCover.url.ifBlank {
                    if (uploadedCover.pid.isNotBlank()) {
                        "https://wx1.sinaimg.cn/large/${uploadedCover.pid}.jpg"
                    } else {
                        ""
                    }
                }
            }
        } catch (_: Exception) {
            // Cover upload is optional; the post can still publish without it.
        }

        val firstLine = snapshot.content
            .lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .firstOrNull { it.isNotEmpty() }
            ?.take(30)
            .orEmpty()
        val title = when {
            firstLine.isBlank() -> "分享一段视频"
            firstLine.length < 6 -> "${firstLine} 微博视频".take(30)
            else -> firstLine
        }
        _state.update { it.copy(stage = "发布视频微博", progress = 0.96f) }
        try {
            container.api.publishVideoStatus(
                session = desktopSession,
                content = snapshot.content,
                visibility = snapshot.visibility,
                mediaId = uploadedVideo.mediaId,
                title = title,
                coverPid = coverPid,
                coverUrl = coverUrl
            )
        } catch (error: AuthenticationException) {
            throw WeiboApiException(
                -2,
                "网页版会话已失效，视频未能发布：请到「我的」退出登录后重新登录，再试一次。"
            )
        }
    }

    private fun readBytes(file: MediaFile): ByteArray {
        if (file.size > MAX_IMAGE_BYTES) {
            throw WeiboApiException(-1, "${file.name} 超过 10MB，原图不会压缩，请更换文件")
        }
        val stream = container.appContext.contentResolver.openInputStream(file.uri)
            ?: throw WeiboApiException(-1, "无法读取 ${file.name}，请重新选择")
        val output = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > MAX_IMAGE_BYTES) {
                    throw WeiboApiException(-1, "${file.name} 超过 10MB，原图不会压缩，请更换文件")
                }
            }
        }
        val bytes = output.toByteArray()
        if (bytes.isEmpty()) {
            throw WeiboApiException(-1, "${file.name} 内容为空，请重新选择")
        }
        return bytes
    }

    private fun extractCover(file: MediaFile): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(container.appContext, file.uri)
            val frame = retriever.getFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            val longest = max(frame.width, frame.height)
            val scaled = if (longest > MAX_COVER_SIDE && longest > 0) {
                val ratio = MAX_COVER_SIDE.toFloat() / longest.toFloat()
                val width = (frame.width * ratio).toInt().coerceAtLeast(1)
                val height = (frame.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(frame, width, height, true).also {
                    if (it !== frame) frame.recycle()
                }
            } else {
                frame
            }
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, output)
                scaled.recycle()
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun errorMessage(error: Exception): String {
        return when (error) {
            is WeiboApiException -> error.message
            is SecurityException -> "无法读取所选文件，请重新选择"
            is IOException -> error.message ?: "网络连接失败，请检查网络后重试"
            else -> error.message ?: if (editingPost != null) "编辑失败，请重试" else "发布失败，请重试"
        }
    }

    companion object {
        const val MAX_CONTENT_LENGTH = 2000
        const val MAX_IMAGES = 9
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        const val MAX_VIDEO_BYTES = 4L * 1024L * 1024L * 1024L
        private const val MAX_COVER_SIDE = 1280

        fun factory(
            container: AppContainer,
            editingPost: EditablePost? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ComposeViewModel(container, editingPost) as T
                }
            }
    }
}
