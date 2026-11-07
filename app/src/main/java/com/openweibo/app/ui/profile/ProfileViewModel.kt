package com.openweibo.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.PostChange
import com.openweibo.app.data.WeiboApiException
import com.openweibo.app.data.WeiboPost
import com.openweibo.app.data.WeiboUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException

data class ProfileUiState(
    val user: WeiboUser? = null,
    val posts: List<WeiboPost> = emptyList(),
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val authExpired: Boolean = false,
    val endReached: Boolean = false,
    val lastUpdated: Long? = null,
    val isSelf: Boolean = false,
    val followBusy: Boolean = false,
    val followMessage: String? = null
)

class ProfileViewModel(
    private val container: AppContainer,
    private val uid: String,
    initialUser: WeiboUser
) : ViewModel() {

    private val viewerUid = normalizeUid(container.sessionManager.current?.uid)
    private val _state = MutableStateFlow(
        ProfileUiState(
            user = initialUser,
            isSelf = viewerUid.isNotEmpty() && normalizeUid(initialUser.id) == viewerUid
        )
    )
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var nextPage = 1
    private var nextSinceId: String? = null
    private var sinceMode = false
    private var job: Job? = null
    private var started = false

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
        if (started && !_state.value.authExpired && _state.value.error == null) return
        started = true
        refresh()
    }

    fun refresh() {
        if (job?.isActive == true) return
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
        job = viewModelScope.launch {
            try {
                val page = container.api.fetchProfile(session, uid, 1)
                val current = _state.value
                _state.value = current.copy(
                    user = page.user ?: current.user,
                    posts = page.posts,
                    initialLoading = false,
                    refreshing = false,
                    error = null,
                    loadMoreError = null,
                    authExpired = false,
                    endReached = !page.hasMore || page.posts.isEmpty(),
                    lastUpdated = System.currentTimeMillis()
                )
                nextPage = page.nextPage.coerceAtLeast(2)
                nextSinceId = page.nextSinceId
                sinceMode = !page.nextSinceId.isNullOrBlank()
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
        if (job?.isActive == true) return
        val current = _state.value
        if (current.initialLoading || current.refreshing || current.loadingMore || current.endReached) {
            return
        }
        val session = container.sessionManager.current ?: run {
            _state.value = current.copy(authExpired = true)
            return
        }
        if (sinceMode && nextSinceId.isNullOrBlank()) {
            _state.value = current.copy(endReached = true)
            return
        }
        val page = nextPage
        val sinceId = if (sinceMode) nextSinceId else null
        _state.value = current.copy(loadingMore = true, loadMoreError = null)
        job = viewModelScope.launch {
            try {
                val result = container.api.fetchProfile(session, uid, page, sinceId)
                val latest = _state.value
                val merged = (latest.posts + result.posts)
                    .distinctBy { it.id.ifBlank { it.bid } }
                val added = merged.size - latest.posts.size
                nextPage = result.nextPage.coerceAtLeast(page + 1)
                nextSinceId = result.nextSinceId
                sinceMode = sinceMode || !result.nextSinceId.isNullOrBlank()
                val noMoreSince = sinceMode && result.nextSinceId.isNullOrBlank()
                _state.value = latest.copy(
                    user = result.user ?: latest.user,
                    posts = merged,
                    loadingMore = false,
                    loadMoreError = null,
                    endReached = noMoreSince || !result.hasMore ||
                        result.posts.isEmpty() || added == 0,
                    lastUpdated = System.currentTimeMillis()
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    loadingMore = false,
                    loadMoreError = if (auth) null else errorMessage(error, "加载更多微博失败"),
                    authExpired = auth
                )
            } finally {
                _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    fun toggleFollow() {
        val current = _state.value
        val user = current.user ?: return
        if (current.isSelf || current.followBusy || user.id.isBlank()) return
        val session = container.sessionManager.current ?: run {
            _state.value = current.copy(authExpired = true)
            return
        }
        val targetFollowing = !user.following
        _state.value = current.copy(
            followBusy = true,
            followMessage = null,
            authExpired = false
        )
        viewModelScope.launch {
            try {
                container.api.setFollowing(session, user.id, targetFollowing)
                val latest = _state.value
                val latestUser = latest.user
                val updatedUser = latestUser?.copy(
                    following = targetFollowing,
                    followersCount = (
                        latestUser.followersCount + if (targetFollowing) 1L else -1L
                    ).coerceAtLeast(0L)
                )
                _state.value = latest.copy(
                    user = updatedUser,
                    followBusy = false,
                    followMessage = if (targetFollowing) {
                        "已关注 @${latestUser?.screenName.orEmpty()}"
                    } else {
                        "已取消关注 @${latestUser?.screenName.orEmpty()}"
                    }
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    followBusy = false,
                    followMessage = if (auth) {
                        null
                    } else {
                        errorMessage(
                            error,
                            if (targetFollowing) "关注失败，请重试" else "取消关注失败，请重试"
                        )
                    },
                    authExpired = auth
                )
            }
        }
    }

    fun consumeFollowMessage() {
        if (_state.value.followMessage != null) {
            _state.value = _state.value.copy(followMessage = null)
        }
    }

    private fun handleError(error: Exception) {
        val auth = error is AuthenticationException
        val message = if (auth) {
            (error as AuthenticationException).message ?: "登录状态已过期，请重新登录"
        } else {
            errorMessage(error, "主页加载失败，请重试")
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

    private fun normalizeUid(value: String?): String {
        val clean = value?.trim().orEmpty()
        if (clean.isEmpty()) return ""
        return clean.trimStart('0').ifBlank { "0" }
    }

    companion object {
        fun factory(
            container: AppContainer,
            uid: String,
            initialUser: WeiboUser
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfileViewModel(container, uid, initialUser) as T
            }
        }
    }
}
