package com.openweibo.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.FeedPage
import com.openweibo.app.data.PostChange
import com.openweibo.app.data.TimeUtils
import com.openweibo.app.data.WeiboApiException
import com.openweibo.app.data.WeiboPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class FeedUiState(
    val posts: List<WeiboPost> = emptyList(),
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val authExpired: Boolean = false,
    val endReached: Boolean = false,
    val lastUpdated: Long? = null
)

class FeedViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var cursor: String? = null
    private var started = false
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.postChanges.collect { change ->
                _state.value = when (change) {
                    is PostChange.Deleted -> _state.value.copy(
                        posts = _state.value.posts.filterNot { it.id == change.id }
                    )
                    is PostChange.Edited -> _state.value.copy(
                        posts = _state.value.posts.map { post ->
                            if (post.id == change.post.id) change.post else post
                        }
                    )
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        if (loadJob?.isActive == true) return
        val session = container.sessionManager.current
        if (session == null) {
            _state.value = _state.value.copy(
                initialLoading = false,
                refreshing = false,
                authExpired = true,
                error = "登录状态已失效，请重新登录"
            )
            return
        }
        val hasPosts = _state.value.posts.isNotEmpty()
        _state.value = _state.value.copy(
            initialLoading = !hasPosts,
            refreshing = hasPosts,
            error = null,
            loadMoreError = null,
            authExpired = false
        )
        loadJob = viewModelScope.launch {
            try {
                val page = container.api.fetchFeed(session.cookies, null)
                applyFirstPage(page)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                handleError(error, initial = !hasPosts)
                if (!initial) {
                    // Keep an explicit error line for header feedback without hiding existing posts.
                }
            } finally {
                _state.value = _state.value.copy(initialLoading = false, refreshing = false)
            }
        }
    }

    fun loadMore() {
        if (loadJob?.isActive == true) return
        val current = _state.value
        if (current.initialLoading || current.refreshing || current.loadingMore || current.endReached) return
        val nextCursor = cursor ?: return
        val session = container.sessionManager.current ?: run {
            _state.value = current.copy(authExpired = true, error = "登录状态已失效，请重新登录")
            return
        }
        _state.value = current.copy(loadingMore = true, loadMoreError = null)
        loadJob = viewModelScope.launch {
            try {
                val page = container.api.fetchFeed(session.cookies, nextCursor)
                appendPage(page, requestedCursor = nextCursor)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is AuthenticationException) {
                    _state.value = _state.value.copy(
                        authExpired = true,
                        error = authMessage(error),
                        loadingMore = false
                    )
                } else {
                    _state.value = _state.value.copy(
                        loadMoreError = errorMessage(error),
                        loadingMore = false
                    )
                }
            } finally {
                _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    private suspend fun applyFirstPage(page: FeedPage) {
        val sorted = withContext(Dispatchers.Default) {
            sortChronologically(page.posts)
        }
        cursor = page.nextCursor
        _state.value = _state.value.copy(
            posts = sorted,
            initialLoading = false,
            refreshing = false,
            error = null,
            loadMoreError = null,
            endReached = !page.hasMore || page.nextCursor == null,
            authExpired = false,
            lastUpdated = System.currentTimeMillis()
        )
    }

    private suspend fun appendPage(page: FeedPage, requestedCursor: String) {
        val existing = _state.value.posts
        val merged = withContext(Dispatchers.Default) {
            sortChronologically(existing + page.posts)
        }
        val nextCursor = page.nextCursor
        val noProgress = nextCursor.isNullOrBlank() ||
            nextCursor == requestedCursor ||
            nextCursor == cursor
        cursor = nextCursor
        _state.value = _state.value.copy(
            posts = merged,
            loadingMore = false,
            loadMoreError = null,
            endReached = !page.hasMore || noProgress || page.posts.isEmpty(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun handleError(error: Exception, initial: Boolean) {
        if (error is AuthenticationException) {
            _state.value = _state.value.copy(
                authExpired = true,
                error = authMessage(error),
                initialLoading = false,
                refreshing = false
            )
        } else {
            _state.value = _state.value.copy(
                error = if (initial || _state.value.posts.isEmpty()) errorMessage(error) else null,
                loadMoreError = if (_state.value.posts.isNotEmpty()) errorMessage(error) else null,
                initialLoading = false,
                refreshing = false
            )
        }
    }

    private fun sortChronologically(posts: List<WeiboPost>): List<WeiboPost> {
        return posts
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<WeiboPost> {
                    if (it.createdAtMillis > 0L) it.createdAtMillis else numericId(it.id)
                }.thenByDescending { numericId(it.id) }
            )
    }

    private fun numericId(id: String): Long = id.toLongOrNull() ?: 0L

    private fun errorMessage(error: Exception): String {
        return when (error) {
            is WeiboApiException -> error.message
            is IOException -> error.message ?: "网络连接失败，请检查网络后重试"
            else -> error.message ?: "加载失败，请重试"
        }
    }

    private fun authMessage(error: Exception): String {
        return (error as? AuthenticationException)?.message
            ?: "登录状态已过期，请重新登录"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FeedViewModel(container) as T
                }
            }
    }
}
