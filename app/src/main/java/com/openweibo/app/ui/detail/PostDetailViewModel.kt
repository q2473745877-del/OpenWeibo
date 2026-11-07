package com.openweibo.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.CommentCursor
import com.openweibo.app.data.WeiboApiException
import com.openweibo.app.data.WeiboComment
import com.openweibo.app.data.WeiboRepost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

enum class DetailTab { Comments, Reposts }

data class CommentListState(
    val items: List<WeiboComment> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val total: Int = 0,
    val cursor: CommentCursor? = null,
    val endReached: Boolean = false,
    val started: Boolean = false
)

data class RepostListState(
    val items: List<WeiboRepost> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val total: Int = 0,
    val nextPage: Int = 1,
    val endReached: Boolean = false,
    val started: Boolean = false
)

data class DetailUiState(
    val tab: DetailTab = DetailTab.Comments,
    val comments: CommentListState = CommentListState(),
    val reposts: RepostListState = RepostListState(),
    val postingComment: Boolean = false,
    val postingRepost: Boolean = false,
    val feedback: String? = null,
    val feedbackIsError: Boolean = false,
    val authExpired: Boolean = false,
    val commentResetToken: Int = 0
)

class PostDetailViewModel(
    private val container: AppContainer,
    private val statusId: String
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private var commentsJob: Job? = null
    private var repostsJob: Job? = null

    fun start(initialTab: DetailTab) {
        _state.value = _state.value.copy(tab = initialTab, authExpired = false)
        when (initialTab) {
            DetailTab.Comments -> if (!_state.value.comments.started) {
                loadComments(refresh = false)
            }

            DetailTab.Reposts -> if (!_state.value.reposts.started) {
                loadReposts(refresh = false)
            }
        }
    }

    fun selectTab(tab: DetailTab) {
        if (_state.value.tab == tab) return
        _state.value = _state.value.copy(tab = tab)
        when (tab) {
            DetailTab.Comments -> if (!_state.value.comments.started) {
                loadComments(refresh = false)
            }

            DetailTab.Reposts -> if (!_state.value.reposts.started) {
                loadReposts(refresh = false)
            }
        }
    }

    fun refresh() {
        when (_state.value.tab) {
            DetailTab.Comments -> loadComments(refresh = true)
            DetailTab.Reposts -> loadReposts(refresh = true)
        }
    }

    fun loadMore() {
        when (_state.value.tab) {
            DetailTab.Comments -> loadMoreComments()
            DetailTab.Reposts -> loadMoreReposts()
        }
    }

    fun submitComment(text: String) {
        val content = text.trim()
        if (content.isBlank() || _state.value.postingComment) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true)
            return
        }
        _state.value = _state.value.copy(postingComment = true, feedback = null)
        viewModelScope.launch {
            try {
                val comment = container.api.postComment(session, statusId, content)
                val current = _state.value
                val comments = current.comments
                val newItems = if (comment != null) {
                    (listOf(comment) + comments.items)
                        .distinctBy { it.id }
                } else {
                    comments.items
                }
                val newTotal = when {
                    comments.total > 0 -> comments.total + 1
                    comment != null -> maxOf(newItems.size, 1)
                    else -> comments.total
                }
                _state.value = current.copy(
                    postingComment = false,
                    comments = comments.copy(
                        items = newItems,
                        total = newTotal,
                        started = true
                    ),
                    commentResetToken = current.commentResetToken + 1,
                    feedback = "评论已发布",
                    feedbackIsError = false
                )
                if (comment == null) {
                    loadComments(refresh = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    postingComment = false,
                    authExpired = auth,
                    feedback = errorMessage(error, "评论发送失败，请重试"),
                    feedbackIsError = true
                )
            }
        }
    }

    fun submitRepost(text: String) {
        if (_state.value.postingRepost) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true)
            return
        }
        _state.value = _state.value.copy(postingRepost = true, feedback = null)
        viewModelScope.launch {
            try {
                container.api.repostStatus(session, statusId, text)
                _state.value = _state.value.copy(
                    postingRepost = false,
                    tab = DetailTab.Reposts,
                    feedback = "已转发这条微博",
                    feedbackIsError = false
                )
                loadReposts(refresh = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    postingRepost = false,
                    authExpired = auth,
                    feedback = errorMessage(error, "转发失败，请重试"),
                    feedbackIsError = true
                )
            }
        }
    }

    fun consumeFeedback() {
        _state.value = _state.value.copy(feedback = null)
    }

    private fun loadComments(refresh: Boolean) {
        if (commentsJob?.isActive == true) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true)
            return
        }
        val current = _state.value.comments
        _state.value = _state.value.copy(
            comments = current.copy(
                loading = true,
                error = null,
                loadMoreError = null
            ),
            authExpired = false
        )
        commentsJob = viewModelScope.launch {
            try {
                val page = container.api.fetchComments(session, statusId, null)
                val latest = _state.value.comments
                val items = page.comments.distinctBy { it.id }
                _state.value = _state.value.copy(
                    comments = latest.copy(
                        items = items,
                        loading = false,
                        error = null,
                        loadMoreError = null,
                        total = if (page.total > 0) page.total else items.size,
                        cursor = page.nextCursor,
                        endReached = !page.hasMore,
                        started = true
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                val latest = _state.value.comments
                _state.value = _state.value.copy(
                    comments = latest.copy(
                        loading = false,
                        error = if (auth) null else errorMessage(error, "评论加载失败，请重试"),
                        started = true
                    ),
                    authExpired = auth
                )
            }
        }
    }

    private fun loadMoreComments() {
        if (commentsJob?.isActive == true) return
        val current = _state.value.comments
        if (current.loading || current.loadingMore || current.endReached) return
        val cursor = current.cursor ?: return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true)
            return
        }
        _state.value = _state.value.copy(
            comments = current.copy(loadingMore = true, loadMoreError = null)
        )
        commentsJob = viewModelScope.launch {
            try {
                val page = container.api.fetchComments(session, statusId, cursor)
                val latest = _state.value.comments
                val items = (latest.items + page.comments).distinctBy { it.id }
                val sameCursor = page.nextCursor?.maxId == cursor.maxId
                val added = items.size - latest.items.size
                _state.value = _state.value.copy(
                    comments = latest.copy(
                        items = items,
                        loadingMore = false,
                        loadMoreError = null,
                        total = if (page.total > 0) page.total else latest.total,
                        cursor = page.nextCursor,
                        endReached = !page.hasMore || page.comments.isEmpty() ||
                            sameCursor || added == 0,
                        started = true
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    comments = _state.value.comments.copy(
                        loadingMore = false,
                        loadMoreError = if (auth) null else errorMessage(error, "加载更多评论失败")
                    ),
                    authExpired = auth
                )
            }
        }
    }

    private fun loadReposts(refresh: Boolean) {
        if (repostsJob?.isActive == true) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true)
            return
        }
        val current = _state.value.reposts
        val page = if (refresh) 1 else current.nextPage.coerceAtLeast(1)
        _state.value = _state.value.copy(
            reposts = current.copy(
                loading = true,
                loadingMore = false,
                error = null,
                loadMoreError = null
            ),
            authExpired = false
        )
        repostsJob = viewModelScope.launch {
            try {
                val result = container.api.fetchReposts(session, statusId, page)
                val latest = _state.value.reposts
                val items = if (page == 1) {
                    result.reposts.distinctBy { it.id }
                } else {
                    (latest.items + result.reposts).distinctBy { it.id }
                }
                _state.value = _state.value.copy(
                    reposts = latest.copy(
                        items = items,
                        loading = false,
                        loadingMore = false,
                        error = null,
                        loadMoreError = null,
                        total = if (result.total > 0) result.total else latest.total,
                        nextPage = page + 1,
                        endReached = !result.hasMore || result.reposts.isEmpty(),
                        started = true
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                val latest = _state.value.reposts
                _state.value = _state.value.copy(
                    reposts = latest.copy(
                        loading = false,
                        loadingMore = false,
                        error = if (auth) null else errorMessage(error, "转发列表加载失败，请重试"),
                        started = true
                    ),
                    authExpired = auth
                )
            }
        }
    }

    private fun loadMoreReposts() {
        if (repostsJob?.isActive == true) return
        val current = _state.value.reposts
        if (current.loading || current.loadingMore || current.endReached) return
        val session = container.sessionManager.current ?: run {
            _state.value = _state.value.copy(authExpired = true)
            return
        }
        val page = current.nextPage
        _state.value = _state.value.copy(
            reposts = current.copy(loadingMore = true, loadMoreError = null)
        )
        repostsJob = viewModelScope.launch {
            try {
                val result = container.api.fetchReposts(session, statusId, page)
                val latest = _state.value.reposts
                val items = (latest.items + result.reposts).distinctBy { it.id }
                val added = items.size - latest.items.size
                _state.value = _state.value.copy(
                    reposts = latest.copy(
                        items = items,
                        loadingMore = false,
                        loadMoreError = null,
                        total = if (result.total > 0) result.total else latest.total,
                        nextPage = page + 1,
                        endReached = !result.hasMore || result.reposts.isEmpty() || added == 0,
                        started = true
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    reposts = _state.value.reposts.copy(
                        loadingMore = false,
                        loadMoreError = if (auth) null else errorMessage(error, "加载更多转发失败")
                    ),
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
        fun factory(container: AppContainer, statusId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PostDetailViewModel(container, statusId) as T
                }
            }
    }
}
