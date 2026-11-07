package com.openweibo.app.ui.followers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.WeiboApiException
import com.openweibo.app.data.WeiboUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

enum class FollowersMode {
    Followers,
    Following
}

data class FollowersUiState(
    val users: List<WeiboUser> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val total: Int = 0,
    val nextPage: Int = 1,
    val endReached: Boolean = false,
    val authExpired: Boolean = false,
    val started: Boolean = false
)

class FollowersViewModel(
    private val container: AppContainer,
    private val uid: String,
    private val mode: FollowersMode = FollowersMode.Followers
) : ViewModel() {

    private val listName: String
        get() = if (mode == FollowersMode.Following) "关注列表" else "粉丝列表"

    private val _state = MutableStateFlow(FollowersUiState())
    val state: StateFlow<FollowersUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start(refresh: Boolean = false) {
        if (_state.value.started && !refresh) return
        refresh()
    }

    fun refresh() {
        if (job?.isActive == true) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true)
            return
        }
        _state.value = _state.value.copy(
            loading = true,
            error = null,
            loadMoreError = null,
            authExpired = false
        )
        job = viewModelScope.launch {
            try {
                val page = if (mode == FollowersMode.Following) {
                    container.api.fetchFollowing(session, uid, 1)
                } else {
                    container.api.fetchFollowers(session, uid, 1)
                }
                _state.value = _state.value.copy(
                    users = page.users.distinctBy { it.id.ifBlank { it.screenName } },
                    loading = false,
                    loadingMore = false,
                    error = null,
                    loadMoreError = null,
                    total = if (page.total > 0) page.total else page.users.size,
                    nextPage = page.nextPage.coerceAtLeast(2),
                    endReached = !page.hasMore,
                    started = true
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    loading = false,
                    error = if (auth) null else errorMessage(error, "${listName}加载失败，请重试"),
                    authExpired = auth,
                    started = true
                )
            }
        }
    }

    fun loadMore() {
        if (job?.isActive == true) return
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        val session = container.sessionManager.current ?: run {
            _state.value = current.copy(authExpired = true)
            return
        }
        val page = current.nextPage
        _state.value = current.copy(loadingMore = true, loadMoreError = null)
        job = viewModelScope.launch {
            try {
                val result = if (mode == FollowersMode.Following) {
                    container.api.fetchFollowing(session, uid, page)
                } else {
                    container.api.fetchFollowers(session, uid, page)
                }
                val latest = _state.value
                val users = (latest.users + result.users)
                    .distinctBy { it.id.ifBlank { it.screenName } }
                _state.value = latest.copy(
                    users = users,
                    loadingMore = false,
                    loadMoreError = null,
                    total = if (result.total > 0) result.total else latest.total,
                    nextPage = result.nextPage.coerceAtLeast(page + 1),
                    endReached = !result.hasMore || result.users.isEmpty()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    loadingMore = false,
                    loadMoreError = if (auth) null else errorMessage(error, "加载更多${listName}失败"),
                    authExpired = auth
                )
            }
        }
    }

    private fun errorMessage(error: Exception, fallback: String): String {
        return when (error) {
            is WeiboApiException -> error.message
            is IOException -> error.message ?: fallback
            else -> error.message ?: fallback
        }
    }

    companion object {
        fun factory(
            container: AppContainer,
            uid: String,
            mode: FollowersMode = FollowersMode.Followers
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FollowersViewModel(container, uid, mode) as T
                }
            }
    }
}
