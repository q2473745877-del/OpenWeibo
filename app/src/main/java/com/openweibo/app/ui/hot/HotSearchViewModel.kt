package com.openweibo.app.ui.hot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.HotCategory
import com.openweibo.app.data.HotItem
import com.openweibo.app.data.WeiboApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class HotUiState(
    val category: HotCategory = HotCategory.Realtime,
    val items: List<HotItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val authExpired: Boolean = false,
    val updatedAt: Long? = null
)

class HotSearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HotUiState())
    val state: StateFlow<HotUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        load(initial = true, force = false)
    }

    fun selectCategory(category: HotCategory) {
        if (_state.value.category == category) return
        job?.cancel()
        _state.value = _state.value.copy(
            category = category,
            items = emptyList(),
            loading = true,
            refreshing = false,
            error = null,
            updatedAt = null
        )
        load(initial = true, force = true)
    }

    fun refresh() {
        load(initial = false, force = true)
    }

    private fun load(initial: Boolean, force: Boolean) {
        if (job?.isActive == true && !force) return
        val session = container.sessionManager.current
        if (session == null) {
            _state.value = _state.value.copy(
                loading = false,
                refreshing = false,
                authExpired = true,
                error = "登录状态已失效，请重新登录"
            )
            return
        }
        if (job?.isActive == true) job?.cancel()
        val current = _state.value
        _state.value = current.copy(
            loading = current.items.isEmpty(),
            refreshing = current.items.isNotEmpty(),
            error = null,
            authExpired = false
        )
        val category = current.category
        job = viewModelScope.launch {
            try {
                val page = container.api.fetchHotSearch(session.cookies, category)
                _state.value = _state.value.copy(
                    category = category,
                    items = page.items,
                    loading = false,
                    refreshing = false,
                    error = null,
                    authExpired = false,
                    updatedAt = page.updatedAt
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val auth = error is AuthenticationException
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    authExpired = auth,
                    error = when {
                        auth -> "登录状态已失效，请重新登录"
                        error is WeiboApiException -> error.message
                        error is IOException -> error.message ?: "网络连接失败，请检查网络后重试"
                        else -> error.message ?: "热搜加载失败，请重试"
                    }
                )
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HotSearchViewModel(container) as T
                }
            }
    }
}
