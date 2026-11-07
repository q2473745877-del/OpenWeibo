package com.openweibo.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.PostChange
import com.openweibo.app.data.WeiboApiException
import com.openweibo.app.data.WeiboPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException

data class SearchUiState(
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

class SearchViewModel(
    private val container: AppContainer,
    private val query: String
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var nextPage = 1
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
        refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        val session = container.sessionManager.current ?: run {
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
                val page = container.api.fetchSearch(session, query, 1)
                nextPage = page.nextPage
                _state.value = _state.value.copy(
                    posts = page.posts,
                    initialLoading = false,
                    refreshing = false,
                    error = null,
                    loadMoreError = null,
                    authExpired = false,
                    endReached = !page.hasMore || page.posts.isEmpty(),
                    lastUpdated = System.currentTimeMillis()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                handleError(error)
            } finally {
                _state.value = _state.value.copy(initialLoading = false, refreshing = false)
            }
        }
    }

    fun loadMore() {
        if (loadJob?.isActive == true) return
        val current = _state.value
        if (current.initialLoading || current.refreshing || current.loadingMore || current.endReached) {
            return
        }
        val session = container.sessionManager.current ?: run {
            _state.value = current.copy(authExpired = true)
            return
        }
        val page = nextPage
        _state.value = current.copy(loadingMore = true, loadMoreError = null)
        loadJob = viewModelScope.launch {
            try {
                val result = container.api.fetchSearch(session, query, page)
                val latest = _state.value
                val merged = (latest.posts + result.posts)
                    .distinctBy { it.id.ifBlank { it.bid } }
                val added = merged.size - latest.posts.size
                nextPage = result.nextPage
                _state.value = latest.copy(
                    posts = merged,
                    loadingMore = false,
                    loadMoreError = null,
                    endReached = !result.hasMore || result.posts.isEmpty() || added == 0,
                    lastUpdated = System.currentTimeMillis()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    loadingMore = false,
                    loadMoreError = if (auth) null else errorMessage(error, "加载更多搜索结果失败"),
                    authExpired = auth
                )
            } finally {
                _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    private fun handleError(error: Exception) {
        val auth = error is AuthenticationException
        val message = if (auth) {
            (error as AuthenticationException).message
                ?: "登录状态已过期，请重新登录"
        } else {
            errorMessage(error, "搜索失败，请重试")
        }
        _state.value = _state.value.copy(
            authExpired = auth,
            error = message,
            loadMoreError = null,
            initialLoading = false,
            refreshing = false
        )
    }

    private fun errorMessage(error: Exception, fallback: String): String {
        return when (error) {
            is WeiboApiException -> error.message
            is IOException -> error.message ?: fallback
            else -> error.message ?: fallback
        }
    }

    companion object {
        fun factory(container: AppContainer, query: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SearchViewModel(container, query) as T
                }
            }
    }
}
