package com.openweibo.app.ui.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.ChatConversation
import com.openweibo.app.data.NotificationItem
import com.openweibo.app.data.NotificationKind
import com.openweibo.app.data.WeiboApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Per-tab slice of state; each category pages independently. */
data class CategoryState(
    val items: List<NotificationItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val endReached: Boolean = false,
    val nextPage: Int? = 1
)

data class MessageUiState(
    val tab: NotificationKind = NotificationKind.DirectMessage,
    val conversations: List<ChatConversation> = emptyList(),
    val chatLoading: Boolean = false,
    val chatError: String? = null,
    val chatLoaded: Boolean = false,
    val categories: Map<NotificationKind, CategoryState> = emptyMap(),
    val authExpired: Boolean = false,
    val feedback: String? = null
)

class MessageViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(MessageUiState())
    val state: StateFlow<MessageUiState> = _state.asStateFlow()

    private var chatJob: Job? = null
    private val categoryJobs = mutableMapOf<NotificationKind, Job>()

    fun selectTab(kind: NotificationKind) {
        _state.value = _state.value.copy(tab = kind)
        ensureLoaded(kind)
    }

    fun ensureLoaded(kind: NotificationKind = _state.value.tab) {
        if (kind == NotificationKind.DirectMessage) {
            if (!_state.value.chatLoaded && chatJob?.isActive != true) loadConversations()
        } else {
            val slice = _state.value.categories[kind]
            if (slice?.loaded != true && categoryJobs[kind]?.isActive != true) {
                loadCategory(kind, page = 1, append = false)
            }
        }
    }

    fun refreshCurrent() {
        val kind = _state.value.tab
        if (kind == NotificationKind.DirectMessage) {
            _state.value = _state.value.copy(chatLoaded = false)
            loadConversations()
        } else {
            loadCategory(kind, page = 1, append = false)
        }
    }

    fun loadMore() {
        val kind = _state.value.tab
        // The conversation list endpoint has no paging, so nothing to append there.
        if (kind == NotificationKind.DirectMessage) return
        val slice = _state.value.categories[kind] ?: return
        if (slice.loadingMore || slice.endReached || slice.nextPage == null) return
        loadCategory(kind, page = slice.nextPage, append = true)
    }

    // ------------------------------------------------------------------ 私信会话

    private fun loadConversations() {
        if (chatJob?.isActive == true) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true, chatLoading = false)
            return
        }
        _state.value = _state.value.copy(
            chatLoading = _state.value.conversations.isEmpty(),
            chatError = null
        )
        chatJob = viewModelScope.launch {
            try {
                val list = container.chatApi.fetchConversations(session)
                _state.value = _state.value.copy(
                    conversations = list,
                    chatLoading = false,
                    chatError = null,
                    chatLoaded = true
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    chatLoading = false,
                    chatError = messageText(error),
                    authExpired = error is AuthenticationException,
                    chatLoaded = true
                )
            }
        }
    }

    // ------------------------------------------------------------ 通知类分类数据

    private fun loadCategory(kind: NotificationKind, page: Int, append: Boolean) {
        if (categoryJobs[kind]?.isActive == true) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true)
            return
        }
        updateCategory(kind) {
            it.copy(loading = !append, loadingMore = append, error = null)
        }
        categoryJobs[kind] = viewModelScope.launch {
            try {
                val result = container.notificationApi.fetch(session, kind, page)
                updateCategory(kind) { current ->
                    val merged = if (append) {
                        (current.items + result.items).distinctBy { it.id }
                    } else {
                        result.items
                    }
                    current.copy(
                        items = merged,
                        loading = false,
                        loadingMore = false,
                        error = null,
                        loaded = true,
                        endReached = !result.hasMore,
                        nextPage = result.nextPage
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateCategory(kind) {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = messageText(error),
                        loaded = true
                    )
                }
                if (error is AuthenticationException) {
                    _state.value = _state.value.copy(authExpired = true)
                }
            }
        }
    }

    private fun updateCategory(
        kind: NotificationKind,
        transform: (CategoryState) -> CategoryState
    ) {
        val current = _state.value.categories[kind] ?: CategoryState()
        _state.value = _state.value.copy(
            categories = _state.value.categories + (kind to transform(current))
        )
    }

    fun consumeFeedback() {
        _state.value = _state.value.copy(feedback = null)
    }

    private fun messageText(error: Exception): String = when (error) {
        is WeiboApiException -> error.message ?: "加载失败，请重试"
        is AuthenticationException -> error.message ?: "登录状态已失效，请重新登录"
        is IOException -> error.message ?: "网络连接失败，请检查网络后重试"
        else -> error.message ?: "加载失败，请重试"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MessageViewModel(container) as T
                }
            }
    }
}
