package com.openweibo.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.ChatMediaType
import com.openweibo.app.data.ChatMessage
import com.openweibo.app.data.MediaFile
import com.openweibo.app.data.WeiboApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class ChatThreadUiState(
    val peerUid: String = "",
    val peerName: String = "",
    val peerAvatar: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hasOlder: Boolean = true,
    val sending: Boolean = false,
    val error: String? = null,
    val authExpired: Boolean = false,
    val feedback: String? = null
)

class ChatThreadViewModel(
    private val container: AppContainer,
    private val peerUid: String,
    peerName: String,
    peerAvatar: String
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatThreadUiState(peerUid = peerUid, peerName = peerName, peerAvatar = peerAvatar)
    )
    val state: StateFlow<ChatThreadUiState> = _state.asStateFlow()

    private var started = false
    private var job: Job? = null

    /** Oldest message id seen so far; passed back as `since_id` to page older. */
    private var oldestCursor: String? = null

    fun start() {
        if (started) return
        started = true
        loadLatest()
    }

    fun refresh() = loadLatest()

    private fun loadLatest() {
        if (job?.isActive == true) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(
                loading = false, authExpired = true,
                error = "登录状态已失效，请重新登录"
            )
            return
        }
        _state.value = _state.value.copy(loading = _state.value.messages.isEmpty(), error = null)
        job = viewModelScope.launch {
            try {
                val page = container.chatApi.fetchMessages(session, peerUid, sinceId = null)
                oldestCursor = page.messages.firstOrNull { it.id.isNotBlank() }?.id
                _state.value = _state.value.copy(
                    messages = page.messages,
                    loading = false,
                    error = null,
                    hasOlder = page.messages.isNotEmpty(),
                    peerName = _state.value.peerName.ifBlank { page.peerTitle }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = messageFor(error),
                    authExpired = error is AuthenticationException
                )
            }
        }
    }

    /** Loads messages older than what is on screen (Weibo pages backwards). */
    fun loadOlder() {
        val current = _state.value
        if (current.loadingOlder || !current.hasOlder) return
        val cursor = oldestCursor ?: return
        val session = container.sessionManager.current ?: return
        _state.value = current.copy(loadingOlder = true)
        viewModelScope.launch {
            try {
                val page = container.chatApi.fetchMessages(session, peerUid, sinceId = cursor)
                val older = page.messages.filter { msg ->
                    current.messages.none { it.id == msg.id }
                }
                if (older.isEmpty()) {
                    _state.value = _state.value.copy(loadingOlder = false, hasOlder = false)
                } else {
                    oldestCursor = older.firstOrNull { it.id.isNotBlank() }?.id ?: cursor
                    _state.value = _state.value.copy(
                        messages = older + _state.value.messages,
                        loadingOlder = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loadingOlder = false,
                    feedback = messageFor(error)
                )
            }
        }
    }

    fun sendText(text: String) {
        val body = text.trim()
        if (body.isEmpty() || _state.value.sending) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(
                authExpired = true, feedback = "登录状态已失效，请重新登录"
            )
            return
        }
        _state.value = _state.value.copy(sending = true, feedback = null)
        viewModelScope.launch {
            try {
                // The `st` token is mandatory: /api/chat/send answers
                // errno:100006 "请求非法" without it.
                val token = container.chatApi.writeToken(session)
                val sent = container.chatApi.sendText(session, peerUid, body, token)
                appendSent(sent, body)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    feedback = messageFor(error),
                    authExpired = error is AuthenticationException
                )
            } finally {
                _state.value = _state.value.copy(sending = false)
            }
        }
    }

    fun sendMedia(file: MediaFile) {
        if (_state.value.sending) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(
                authExpired = true, feedback = "登录状态已失效，请重新登录"
            )
            return
        }
        _state.value = _state.value.copy(sending = true, feedback = null)
        viewModelScope.launch {
            try {
                val token = container.chatApi.writeToken(session)
                if (file.isVideo) {
                    val uploaded = container.api.uploadVideo(session = session, file = file)
                    container.chatApi.sendMedia(
                        session = session,
                        conversationUid = peerUid,
                        picId = null,
                        mediaId = uploaded.mediaId,
                        token = token
                    )
                    appendSent(null, "[视频]")
                } else {
                    val picId = container.chatApi.uploadChatImage(
                        session = session,
                        uri = file.uri,
                        fileName = file.name,
                        mimeType = file.mimeType,
                        token = token
                    )
                    container.chatApi.sendMedia(
                        session = session,
                        conversationUid = peerUid,
                        picId = picId,
                        mediaId = null,
                        token = token
                    )
                    appendSent(
                        null,
                        if (file.isGif) "[动图]" else "[图片]"
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    feedback = messageFor(error),
                    authExpired = error is AuthenticationException
                )
            } finally {
                _state.value = _state.value.copy(sending = false)
            }
        }
    }

    private fun appendSent(sent: ChatMessage?, fallbackText: String) {
        val message = sent ?: ChatMessage(
            id = "local-" + System.currentTimeMillis(),
            fromUid = "",
            fromName = "",
            text = fallbackText,
            createdAtMillis = System.currentTimeMillis(),
            outgoing = true
        )
        if (_state.value.messages.any { it.id == message.id && !message.id.startsWith("local-") }) {
            return
        }
        _state.value = _state.value.copy(
            messages = _state.value.messages + message.copy(outgoing = true),
            feedback = null
        )
    }

    fun consumeFeedback() {
        _state.value = _state.value.copy(feedback = null)
    }

    companion object {
        fun factory(
            container: AppContainer,
            peerUid: String,
            peerName: String,
            peerAvatar: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatThreadViewModel(container, peerUid, peerName, peerAvatar) as T
            }
        }
    }
}

internal fun messageFor(error: Exception): String = when (error) {
    is WeiboApiException -> error.message ?: "操作失败，请重试"
    is AuthenticationException -> error.message ?: "登录状态已失效，请重新登录"
    is IOException -> error.message ?: "网络连接失败，请检查网络后重试"
    else -> error.message ?: "操作失败，请重试"
}
