package com.openweibo.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.EditablePost
import com.openweibo.app.data.HotItem
import com.openweibo.app.data.PostChange
import com.openweibo.app.data.SaveResult
import com.openweibo.app.data.Session
import com.openweibo.app.data.ThemeMode
import com.openweibo.app.data.VideoPreview
import com.openweibo.app.data.VideoStream
import com.openweibo.app.data.WeiboImage
import com.openweibo.app.data.WeiboPost
import com.openweibo.app.data.WeiboUser
import com.openweibo.app.ui.account.AccountScreen
import com.openweibo.app.ui.components.AppSnackbar
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.chat.ChatThreadScreen
import com.openweibo.app.ui.chat.ChatThreadViewModel
import com.openweibo.app.ui.message.MessageScreen
import com.openweibo.app.ui.message.MessageViewModel
import com.openweibo.app.ui.compose.ComposeScreen
import com.openweibo.app.ui.compose.ComposeViewModel
import com.openweibo.app.ui.detail.DetailTab
import com.openweibo.app.ui.detail.PostDetailScreen
import com.openweibo.app.ui.detail.PostDetailViewModel
import com.openweibo.app.ui.feed.DeletePostDialog
import com.openweibo.app.ui.feed.FeedScreen
import com.openweibo.app.ui.feed.FeedViewModel
import com.openweibo.app.ui.feed.LocalLongTextLoader
import com.openweibo.app.ui.feed.LongTextLoader
import com.openweibo.app.ui.feed.PostManageDialog
import com.openweibo.app.ui.followers.FollowersMode
import com.openweibo.app.ui.followers.FollowersScreen
import com.openweibo.app.ui.followers.FollowersViewModel
import com.openweibo.app.ui.hot.HotSearchScreen
import com.openweibo.app.ui.hot.HotSearchViewModel
import com.openweibo.app.ui.login.LoginScreen
import com.openweibo.app.ui.profile.ProfileScreen
import com.openweibo.app.ui.profile.ProfileViewModel
import com.openweibo.app.ui.search.SearchScreen
import com.openweibo.app.ui.search.SearchViewModel
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.OpenWeiboTheme
import com.openweibo.app.ui.theme.monoStyle
import com.openweibo.app.ui.video.VideoPlayerScreen
import com.openweibo.app.ui.viewer.ImageViewer
import com.openweibo.app.ui.viewer.ShareAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class ViewerState(
    val postId: String,
    val images: List<WeiboImage>,
    val index: Int,
    val title: String
)

private data class VideoTarget(
    val video: VideoPreview,
    val title: String,
    val cookies: String
)

private data class VideoSaveRequest(
    val stream: VideoStream,
    val title: String,
    val cookies: String
)

private sealed interface OverlayTarget {
    data class Detail(val post: WeiboPost, val tab: DetailTab) : OverlayTarget
    data class Profile(val user: WeiboUser) : OverlayTarget
    data class Search(val query: String) : OverlayTarget
    data object Followers : OverlayTarget
    data object Following : OverlayTarget
    data object Compose : OverlayTarget
    data class Edit(val post: WeiboPost, val editable: EditablePost) : OverlayTarget
}

private data class OverlayEntry(
    val id: Long,
    val target: OverlayTarget
)

private enum class MainTab(val label: String, val icon: ImageVector) {
    Timeline("时间线", Icons.Outlined.DynamicFeed),
    Hot("热搜", Icons.Outlined.Whatshot),
    Chat("消息", Icons.Outlined.MailOutline),
    Account("我的", Icons.Outlined.PersonOutline)
}

@Composable
fun OpenWeiboRoot(container: AppContainer) {
    val themeMode by container.settingsStore.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
    }

    OpenWeiboTheme(darkTheme = darkTheme) {
        val colors = AppTheme.colors
        val session by container.sessionManager.session.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        var viewer by remember { mutableStateOf<ViewerState?>(null) }
        var videoTarget by remember { mutableStateOf<VideoTarget?>(null) }
        val overlays = remember { mutableStateListOf<OverlayEntry>() }
        var overlaySequence by remember { mutableLongStateOf(0L) }
        var saving by remember { mutableStateOf(false) }
        var saveDone by remember { mutableIntStateOf(0) }
        var saveTotal by remember { mutableIntStateOf(0) }
        var pendingSave by remember { mutableStateOf<Pair<String, List<WeiboImage>>?>(null) }
        var videoSaving by remember { mutableStateOf(false) }
        var videoSaveProgress by remember { mutableFloatStateOf(-1f) }
        var pendingVideoSave by remember { mutableStateOf<VideoSaveRequest?>(null) }
        var feedRefreshToken by remember { mutableIntStateOf(0) }
        var manageTarget by remember { mutableStateOf<WeiboPost?>(null) }
        var deleteTarget by remember { mutableStateOf<WeiboPost?>(null) }
        var deleting by remember { mutableStateOf(false) }
        var preparingEdit by remember { mutableStateOf(false) }
        var sharing by remember { mutableStateOf(false) }
        var pendingMessageUser by remember { mutableStateOf<WeiboUser?>(null) }

        fun notify(message: String, long: Boolean = false) {
            if (message.isBlank()) return
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short
                )
            }
        }

        fun closeOverlays() {
            viewer = null
            videoTarget = null
            overlays.clear()
            manageTarget = null
            deleteTarget = null
        }

        fun pushOverlay(target: OverlayTarget) {
            overlaySequence += 1L
            overlays.add(OverlayEntry(overlaySequence, target))
        }

        fun popOverlay(entryId: Long) {
            val index = overlays.indexOfLast { it.id == entryId }
            if (index >= 0) overlays.removeAt(index)
        }

        fun startSave(postId: String, images: List<WeiboImage>) {
            if (images.isEmpty()) return
            if (saving) {
                notify("正在保存上一批原图，请稍候")
                return
            }
            saving = true
            saveDone = 0
            saveTotal = images.distinctBy { it.originalUrl }.size
            scope.launch {
                val result = try {
                    container.imageSaver.saveImages(images, postId) { done, total ->
                        saveDone = done
                        saveTotal = total
                    }
                } catch (error: Exception) {
                    SaveResult(
                        saved = 0,
                        failed = saveTotal,
                        firstError = error.message ?: "保存失败"
                    )
                }
                saving = false
                saveDone = 0
                saveTotal = 0
                notify(saveMessage(result), long = result.failed > 0)
            }
        }

        fun startVideoSave(request: VideoSaveRequest) {
            if (videoSaving) {
                notify("正在下载视频，请稍候")
                return
            }
            videoSaving = true
            videoSaveProgress = -1f
            scope.launch {
                try {
                    val fileName = container.videoSaver.saveVideo(
                        stream = request.stream,
                        title = request.title,
                        cookies = request.cookies
                    ) { downloaded, total ->
                        videoSaveProgress = if (total > 0L) {
                            (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                    }
                    notify("视频已保存到 Movies/OpenWeibo：$fileName", long = true)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    notify("视频保存失败：${error.message ?: "请重试"}", long = true)
                } finally {
                    videoSaving = false
                    videoSaveProgress = -1f
                }
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            val pendingImages = pendingSave
            val pendingVideo = pendingVideoSave
            pendingSave = null
            pendingVideoSave = null
            when {
                granted && pendingImages != null -> startSave(pendingImages.first, pendingImages.second)
                granted && pendingVideo != null -> startVideoSave(pendingVideo)
                !granted && pendingImages != null -> notify("没有存储权限，无法保存原图")
                !granted && pendingVideo != null -> notify("没有存储权限，无法保存视频")
            }
        }

        fun performSave(postId: String, images: List<WeiboImage>) {
            if (images.isEmpty()) return
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                !container.imageSaver.hasStoragePermission()
            ) {
                pendingSave = postId to images
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
            startSave(postId, images)
        }

        fun performVideoSave(title: String, cookies: String, stream: VideoStream) {
            if (stream.isHls) {
                notify("HLS 流暂不支持直接下载，请切换到 MP4 清晰度")
                return
            }
            if (videoSaving) {
                notify("正在下载视频，请稍候")
                return
            }
            val request = VideoSaveRequest(stream = stream, title = title, cookies = cookies)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                !container.videoSaver.hasStoragePermission()
            ) {
                pendingVideoSave = request
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
            startVideoSave(request)
        }

        fun openViewer(
            postId: String,
            images: List<WeiboImage>,
            index: Int,
            title: String
        ) {
            if (images.isEmpty()) return
            viewer = ViewerState(
                postId = postId,
                images = images,
                index = index.coerceIn(0, images.lastIndex),
                title = title
            )
        }

        fun performShare(image: WeiboImage, action: ShareAction) {
            if (sharing) {
                notify("正在准备分享内容，请稍候")
                return
            }
            sharing = true
            scope.launch {
                try {
                    val extension = if (image.isGif) "gif" else "jpg"
                    val fileName = "OpenWeibo_share_${System.currentTimeMillis()}.$extension"
                    val file = container.mediaSharer.downloadForShare(image.originalUrl, fileName)
                    val caption = "来自 OpenWeibo 分享"
                    when (action) {
                        ShareAction.System -> {
                            container.mediaSharer.shareToSystem(file, caption)
                        }

                        is ShareAction.ToTarget -> {
                            val ok = container.mediaSharer.shareTo(action.target, file, caption)
                            if (!ok) {
                                notify("未检测到${action.target.label}，可改用「更多分享」")
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    notify("分享失败：${error.message ?: "请重试"}", long = true)
                } finally {
                    sharing = false
                }
            }
        }

        fun openVideo(video: VideoPreview, title: String) {
            videoTarget = VideoTarget(
                video = video,
                title = title,
                cookies = session?.cookies.orEmpty()
            )
        }

        fun openPost(post: WeiboPost, tab: DetailTab = DetailTab.Comments) {
            if (post.id.isBlank() && post.bid.isBlank()) {
                notify("这条微博暂时无法打开")
                return
            }
            pushOverlay(OverlayTarget.Detail(post, tab))
        }

        fun openProfile(user: WeiboUser) {            if (user.id.isBlank()) {
                notify("无法打开该用户主页")
                return
            }
            val top = overlays.lastOrNull()?.target
            if (top is OverlayTarget.Profile && top.user.id == user.id) return
            pushOverlay(OverlayTarget.Profile(user))
        }

        /**
         * Notification rows only carry a uid, so resolve a minimal profile first;
         * the profile screen reloads the full user info on its own.
         */
        fun openUserByUid(uid: String) {
            if (uid.isBlank()) {
                notify("无法打开该用户主页")
                return
            }
            val activeSession = session ?: return
            scope.launch {
                try {
                    val user = container.api.fetchUser(activeSession, uid)
                    if (user == null) {
                        notify("无法打开该用户主页")
                    } else {
                        openProfile(user)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    notify(error.message ?: "无法打开该用户主页")
                }
            }
        }

        fun requestEdit(post: WeiboPost) {
            manageTarget = null
            if (preparingEdit) {
                notify("正在读取上一条微博的编辑内容，请稍候")
                return
            }
            if (post.video != null || post.retweet != null) {
                notify("含视频或转发的微博暂不支持编辑")
                return
            }
            if (post.id.isBlank()) {
                notify("这条微博暂时无法编辑")
                return
            }
            val activeSession = session ?: return
            preparingEdit = true
            notify("正在读取编辑内容…")
            scope.launch {
                try {
                    val editable = container.api.loadEditablePost(activeSession, post.id)
                    val sourcePicIds = post.images
                        .map { it.pid.trim() }
                        .filter { it.isNotEmpty() }
                    val picIds = editable.picIds.ifEmpty { sourcePicIds }
                    if (post.images.isNotEmpty() && picIds.isEmpty()) {
                        notify("无法读取原图信息，这条微博暂不支持编辑", long = true)
                        return@launch
                    }
                    pushOverlay(
                        OverlayTarget.Edit(post, editable.copy(picIds = picIds))
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: AuthenticationException) {
                    closeOverlays()
                    manageTarget = null
                    deleteTarget = null
                    container.sessionManager.clear()
                    notify(error.message ?: "登录状态已过期，请重新登录", long = true)
                } catch (error: Exception) {
                    notify(error.message ?: "无法编辑这条微博", long = true)
                } finally {
                    preparingEdit = false
                }
            }
        }

        fun requestDelete(post: WeiboPost) {
            manageTarget = null
            if (deleting) return
            deleteTarget = post
        }

        fun performDelete() {
            val target = deleteTarget ?: return
            if (deleting) return
            val activeSession = session ?: return
            deleting = true
            scope.launch {
                try {
                    container.api.deleteStatus(activeSession, target.id)
                    container.postChanges.emit(PostChange.Deleted(target.id))
                    overlays.removeAll { entry ->
                        val detail = entry.target as? OverlayTarget.Detail
                        detail?.post?.id == target.id
                    }
                    deleteTarget = null
                    notify("微博已删除", long = true)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: AuthenticationException) {
                    deleteTarget = null
                    closeOverlays()
                    container.sessionManager.clear()
                    notify(error.message ?: "登录状态已过期，请重新登录", long = true)
                } catch (error: Exception) {
                    deleteTarget = null
                    notify("删除失败：${error.message ?: "请重试"}", long = true)
                } finally {
                    deleting = false
                }
            }
        }

        fun applyEdit(source: WeiboPost, content: String) {
            val text = content.trim().ifBlank {
                if (source.images.isNotEmpty()) "分享图片" else "分享微博"
            }
            val updated = source.copy(text = text, isLongText = false)
            overlays.indices.forEach { index ->
                val target = overlays[index].target
                if (target is OverlayTarget.Detail && target.post.id == source.id) {
                    overlays[index] = overlays[index].copy(
                        target = target.copy(post = updated)
                    )
                }
            }
            scope.launch {
                container.postChanges.emit(PostChange.Edited(updated))
            }
            notify("微博已更新", long = true)
        }

        val loggedIn = session != null
        val paletteOverlayOpen = overlays.isNotEmpty()
        val topOverlay = overlays.lastOrNull()?.target
        val snackbarBottom = when {
            viewer != null || videoTarget != null -> 6.dp
            topOverlay is OverlayTarget.Detail -> 76.dp
            loggedIn && !paletteOverlayOpen -> 62.dp
            else -> 6.dp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
        ) {
            val currentSession = session
            if (currentSession == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                ) {
                    LoginScreen(
                        container = container,
                        onAuthenticated = { authenticated ->
                            closeOverlays()
                            container.sessionManager.set(authenticated)
                            notify("已登录：${authenticated.screenName}")
                        },
                        onSnackbar = { notify(it) }
                    )
                }
            } else {
                CompositionLocalProvider(
                    LocalLongTextLoader provides LongTextLoader { statusId ->
                        val activeSession = container.sessionManager.current
                            ?: throw AuthenticationException("登录状态已过期，请重新登录")
                        container.api.fetchLongText(activeSession, statusId)
                    }
                ) {
                val sessionKey = "${currentSession.uid}-${currentSession.cookies.hashCode()}"
                MainScaffold(
                    container = container,
                    session = currentSession,
                    themeMode = themeMode,
                    onThemeModeChange = { container.settingsStore.setThemeMode(it) },
                    overlayOpen = paletteOverlayOpen,
                    viewerOpen = viewer != null || videoTarget != null,
                    onOpenImages = ::openViewer,
                    onSaveImages = ::performSave,
                    onOpenComments = { post -> openPost(post, DetailTab.Comments) },
                    onOpenReposts = { post -> openPost(post, DetailTab.Reposts) },
                    onOpenVideo = ::openVideo,
                    onOpenUser = ::openProfile,
                    onOpenHotItem = { item ->
                        pushOverlay(OverlayTarget.Search(item.title))
                    },
                    onOpenFollowers = { pushOverlay(OverlayTarget.Followers) },
                    onOpenFollowing = { pushOverlay(OverlayTarget.Following) },
                    feedRefreshToken = feedRefreshToken,
                    onCompose = { pushOverlay(OverlayTarget.Compose) },
                    onManagePost = { manageTarget = it },
                    onSnackbar = { notify(it) },
                    onLoginExpired = {
                        closeOverlays()
                        container.sessionManager.clear()
                        notify("登录已过期，请重新登录")
                    },
                    onLogout = {
                        closeOverlays()
                        container.sessionManager.clear()
                        notify("已退出登录，本机会话已清除")
                    },
                    pendingMessageTarget = pendingMessageUser,
                    onPendingMessageConsumed = { pendingMessageUser = null },
                    onOpenUserByUid = ::openUserByUid
                )

                overlays.forEach { entry ->
                    key(entry.id) {
                        when (val target = entry.target) {
                            is OverlayTarget.Search -> {
                                val searchViewModel: SearchViewModel = viewModel(
                                    key = "search-$sessionKey-${target.query}",
                                    factory = SearchViewModel.factory(container, target.query)
                                )
                                SearchScreen(
                                    query = target.query,
                                    viewModel = searchViewModel,
                                    onClose = { popOverlay(entry.id) },
                                    onOpenImages = ::openViewer,
                                    onSaveImages = ::performSave,
                                    onOpenComments = { post ->
                                        openPost(post, DetailTab.Comments)
                                    },
                                    onOpenReposts = { post ->
                                        openPost(post, DetailTab.Reposts)
                                    },
                                    onOpenVideo = ::openVideo,
                                    onOpenUser = ::openProfile,
                                    currentUid = currentSession.uid,
                                    onManagePost = { manageTarget = it },
                                    onSnackbar = { notify(it) },
                                    onLoginExpired = {
                                        closeOverlays()
                                        container.sessionManager.clear()
                                        notify("登录已过期，请重新登录")
                                    }
                                )
                            }

                            is OverlayTarget.Detail -> {
                                val detailViewModel: PostDetailViewModel = viewModel(
                                    key = "detail-$sessionKey-${target.post.id}",
                                    factory = PostDetailViewModel.factory(
                                        container,
                                        target.post.id
                                    )
                                )
                                PostDetailScreen(
                                    post = target.post,
                                    viewModel = detailViewModel,
                                    initialTab = target.tab,
                                    onClose = { popOverlay(entry.id) },
                                    onOpenImages = ::openViewer,
                                    onSaveImages = ::performSave,
                                    onOpenVideo = ::openVideo,
                                    onOpenUser = ::openProfile,
                                    onOpenPost = { post ->
                                        openPost(post, DetailTab.Comments)
                                    },
                                    currentUid = currentSession.uid,
                                    onManagePost = { manageTarget = it },
                                    onSnackbar = { notify(it) },
                                    onLoginExpired = {
                                        closeOverlays()
                                        container.sessionManager.clear()
                                        notify("登录已过期，请重新登录")
                                    }
                                )
                            }

                            is OverlayTarget.Profile -> {
                                val profileViewModel: ProfileViewModel = viewModel(
                                    key = "profile-$sessionKey-${target.user.id}",
                                    factory = ProfileViewModel.factory(
                                        container,
                                        target.user.id,
                                        target.user
                                    )
                                )
                                ProfileScreen(
                                    initialUser = target.user,
                                    viewModel = profileViewModel,
                                    onClose = { popOverlay(entry.id) },
                                    onOpenImages = ::openViewer,
                                    onSaveImages = ::performSave,
                                    onOpenComments = { post ->
                                        openPost(post, DetailTab.Comments)
                                    },
                                    onOpenReposts = { post ->
                                        openPost(post, DetailTab.Reposts)
                                    },
                                    onOpenVideo = ::openVideo,
                                    onOpenUser = ::openProfile,
                                    currentUid = currentSession.uid,
                                    onManagePost = { manageTarget = it },
                                    onSendMessage = { user ->
                                        closeOverlays()
                                        pendingMessageUser = user
                                    },
                                    onSnackbar = { notify(it) },
                                    onLoginExpired = {
                                        closeOverlays()
                                        container.sessionManager.clear()
                                        notify("登录已过期，请重新登录")
                                    }
                                )
                            }

                            OverlayTarget.Followers -> {
                                val followersViewModel: FollowersViewModel = viewModel(
                                    key = "followers-$sessionKey",
                                    factory = FollowersViewModel.factory(
                                        container,
                                        currentSession.uid
                                    )
                                )
                                FollowersScreen(
                                    session = currentSession,
                                    viewModel = followersViewModel,
                                    onClose = { popOverlay(entry.id) },
                                    onOpenUser = ::openProfile,
                                    onLoginExpired = {
                                        closeOverlays()
                                        container.sessionManager.clear()
                                        notify("登录已过期，请重新登录")
                                    }
                                )
                            }

                            OverlayTarget.Following -> {
                                val followingViewModel: FollowersViewModel = viewModel(
                                    key = "following-$sessionKey",
                                    factory = FollowersViewModel.factory(
                                        container,
                                        currentSession.uid,
                                        FollowersMode.Following
                                    )
                                )
                                FollowersScreen(
                                    session = currentSession,
                                    viewModel = followingViewModel,
                                    onClose = { popOverlay(entry.id) },
                                    onOpenUser = ::openProfile,
                                    onLoginExpired = {
                                        closeOverlays()
                                        container.sessionManager.clear()
                                        notify("登录已过期，请重新登录")
                                    },
                                    mode = FollowersMode.Following
                                )
                            }

                            OverlayTarget.Compose -> {
                                val composeViewModel: ComposeViewModel = viewModel(
                                    key = "compose-$sessionKey-${entry.id}",
                                    factory = ComposeViewModel.factory(container)
                                )
                                ComposeScreen(
                                    viewModel = composeViewModel,
                                    session = currentSession,
                                    onClose = { popOverlay(entry.id) },
                                    onPublished = {
                                        popOverlay(entry.id)
                                        feedRefreshToken += 1
                                        notify("发布成功，正在刷新时间线", long = true)
                                    },
                                    onLoginExpired = {
                                        closeOverlays()
                                        container.sessionManager.clear()
                                        notify("登录已过期，请重新登录")
                                    }
                                )
                            }

                            is OverlayTarget.Edit -> {
                                val composeViewModel: ComposeViewModel = viewModel(
                                    key = "edit-$sessionKey-${entry.id}",
                                    factory = ComposeViewModel.factory(container, target.editable)
                                )
                                ComposeScreen(
                                    viewModel = composeViewModel,
                                    session = currentSession,
                                    onClose = { popOverlay(entry.id) },
                                    onPublished = {},
                                    onLoginExpired = {
                                        closeOverlays()
                                        container.sessionManager.clear()
                                        notify("登录已过期，请重新登录")
                                    },
                                    onEdited = { content ->
                                        popOverlay(entry.id)
                                        applyEdit(target.post, content)
                                    }
                                )
                            }
                        }
                    }
                }

                if (viewer != null) {
                    val target = viewer!!
                    ImageViewer(
                        images = target.images,
                        initialIndex = target.index,
                        title = target.title,
                        onSaveCurrent = { image ->
                            performSave(target.postId, listOf(image))
                        },
                        onSaveAll = { images -> performSave(target.postId, images) },
                        onShare = { image, action -> performShare(image, action) },
                        availableShareTargets = container.mediaSharer.availableTargets(),
                        onClose = { viewer = null }
                    )
                }

                if (videoTarget != null) {
                    val target = videoTarget!!
                    VideoPlayerScreen(
                        video = target.video,
                        title = target.title,
                        cookies = target.cookies,
                        downloading = videoSaving,
                        downloadProgress = videoSaveProgress,
                        onDownload = { stream ->
                            performVideoSave(target.title, target.cookies, stream)
                        },
                        onSnackbar = { notify(it) },
                        onClose = { videoTarget = null }
                    )
                }
                }
            }

            manageTarget?.let { post ->
                PostManageDialog(
                    post = post,
                    onEdit = { requestEdit(post) },
                    onDelete = { requestDelete(post) },
                    onDismiss = { manageTarget = null }
                )
            }

            deleteTarget?.let { post ->
                DeletePostDialog(
                    post = post,
                    deleting = deleting,
                    onDismiss = { if (!deleting) deleteTarget = null },
                    onConfirm = { performDelete() }
                )
            }

            AnimatedVisibility(
                visible = saving,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 82.dp)
                    .navigationBarsPadding()
            ) {
                SavingIndicator(done = saveDone, total = saveTotal)
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = snackbarBottom)
                    .navigationBarsPadding()
            ) { data ->
                AppSnackbar(data)
            }
        }
    }
}

@Composable
private fun MainScaffold(
    container: AppContainer,
    session: Session,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    overlayOpen: Boolean,
    viewerOpen: Boolean,
    onOpenImages: (String, List<WeiboImage>, Int, String) -> Unit,
    onSaveImages: (String, List<WeiboImage>) -> Unit,
    onOpenComments: (WeiboPost) -> Unit,
    onOpenReposts: (WeiboPost) -> Unit,
    onOpenVideo: (VideoPreview, String) -> Unit,
    onOpenUser: (WeiboUser) -> Unit,
    onOpenHotItem: (HotItem) -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    feedRefreshToken: Int,
    onCompose: () -> Unit,
    onManagePost: (WeiboPost) -> Unit,
    onSnackbar: (String) -> Unit,
    onLoginExpired: () -> Unit,
    onLogout: () -> Unit,
    pendingMessageTarget: WeiboUser?,
    onPendingMessageConsumed: () -> Unit,
    onOpenUserByUid: (String) -> Unit
) {
    val colors = AppTheme.colors
    var tab by rememberSaveable { mutableStateOf(MainTab.Timeline) }
    val sessionKey = session.uid + "-" + session.cookies.hashCode()
    val feedViewModel: FeedViewModel = viewModel(
        key = "feed-$sessionKey",
        factory = FeedViewModel.factory(container)
    )
    val hotViewModel: HotSearchViewModel = viewModel(
        key = "hot-$sessionKey",
        factory = HotSearchViewModel.factory(container)
    )
    val messageViewModel: MessageViewModel = viewModel(
        key = "message-$sessionKey",
        factory = MessageViewModel.factory(container)
    )
    var chatThread by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    // Opening a thread from a user profile: switch to the message tab first.
    LaunchedEffect(pendingMessageTarget) {
        val target = pendingMessageTarget ?: return@LaunchedEffect
        chatThread = Triple(target.id, target.screenName, target.avatarUrl)
        tab = MainTab.Chat
        onPendingMessageConsumed()
    }

    BackHandler(enabled = (tab != MainTab.Timeline || chatThread != null) && !overlayOpen && !viewerOpen) {
        if (tab == MainTab.Chat && chatThread != null) {
            chatThread = null
        } else {
            chatThread = null
            tab = MainTab.Timeline
        }
    }

    LaunchedEffect(feedRefreshToken) {
        if (feedRefreshToken > 0) feedViewModel.refresh()
    }

    LaunchedEffect(tab, session.uid) {
        if (tab != MainTab.Account) return@LaunchedEffect
        try {
            val refreshed = container.api.refreshSessionCounts(session)
            if (refreshed != session) container.sessionManager.set(refreshed)
        } catch (error: CancellationException) {
            throw error
        } catch (_: AuthenticationException) {
            onLoginExpired()
        } catch (_: Exception) {
            // Keep the cached counts when the lightweight refresh fails.
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = colors.bg,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        bottomBar = {
            MainBottomBar(
                selected = tab,
                onSelect = { tab = it },
                onTimelineDoubleTap = { feedViewModel.refresh() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                MainTab.Timeline -> FeedScreen(
                    viewModel = feedViewModel,
                    onOpenImages = onOpenImages,
                    onSaveImages = onSaveImages,
                    onOpenComments = onOpenComments,
                    onOpenReposts = onOpenReposts,
                    onOpenVideo = onOpenVideo,
                    onOpenUser = onOpenUser,
                    currentUid = session.uid,
                    onManagePost = onManagePost,
                    onSnackbar = onSnackbar,
                    onLoginExpired = onLoginExpired,
                    onCompose = onCompose
                )

                MainTab.Hot -> HotSearchScreen(
                    viewModel = hotViewModel,
                    onOpenItem = onOpenHotItem,
                    onSnackbar = onSnackbar,
                    onLoginExpired = onLoginExpired
                )

                MainTab.Chat -> {
                    val peer = chatThread
                    if (peer == null) {
                        MessageScreen(
                            viewModel = messageViewModel,
                            onOpenThread = { uid, name, avatar ->
                                chatThread = Triple(uid, name, avatar)
                            },
                            onOpenNotificationPost = { item ->
                                item.post?.let { post ->
                                    onOpenComments(post)
                                }
                            },
                            onOpenUserByUid = onOpenUserByUid,
                            onLoginExpired = onLoginExpired
                        )
                    } else {
                        key(peer.first) {
                            val threadViewModel: ChatThreadViewModel = viewModel(
                                key = "chat-${peer.first}-$sessionKey",
                                factory = ChatThreadViewModel.factory(
                                    container,
                                    peer.first,
                                    peer.second,
                                    peer.third
                                )
                            )
                            ChatThreadScreen(
                                viewModel = threadViewModel,
                                onBack = { chatThread = null },
                                onSnackbar = onSnackbar,
                                onLoginExpired = onLoginExpired
                            )
                        }
                    }
                }

                MainTab.Account -> AccountScreen(
                    session = session,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenProfile = { onOpenUser(session.toWeiboUser()) },
                    onOpenFollowers = onOpenFollowers,
                    onOpenFollowing = onOpenFollowing,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun MainBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onTimelineDoubleTap: () -> Unit
) {
    val colors = AppTheme.colors
    var lastTimelineTapAt by remember { mutableLongStateOf(0L) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chrome)
    ) {
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable {
                            if (tab == MainTab.Timeline) {
                                val now = SystemClock.elapsedRealtime()
                                if (lastTimelineTapAt > 0L && now - lastTimelineTapAt <= 350L) {
                                    lastTimelineTapAt = 0L
                                    onTimelineDoubleTap()
                                } else {
                                    lastTimelineTapAt = now
                                }
                            } else {
                                lastTimelineTapAt = 0L
                            }
                            onSelect(tab)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (active) colors.text else colors.textFaint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = if (active) colors.text else colors.textFaint,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.success else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingIndicator(done: Int, total: Int) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceRaised)
            .border(1.dp, colors.border, CircleShape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            color = colors.textDim,
            strokeWidth = 1.4.dp
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "SAVING ORIGINALS",
            style = monoStyle(8.5, letterSpacing = 0.7),
            color = colors.textDim
        )
        if (total > 0) {
            Spacer(Modifier.size(9.dp))
            Box(
                modifier = Modifier
                    .size(width = 1.dp, height = 10.dp)
                    .background(colors.border)
            )
            Spacer(Modifier.size(9.dp))
            Text(
                text = "$done/$total",
                style = monoStyle(8.5, letterSpacing = 0.5),
                color = colors.textFaint
            )
        }
    }
}

private fun saveMessage(result: SaveResult): String {
    return when {
        result.saved > 0 && result.failed == 0 ->
            "已保存 ${result.saved} 张原图到系统相册"

        result.saved > 0 ->
            "已保存 ${result.saved} 张，${result.failed} 张失败" +
                (result.firstError?.let { "：$it" } ?: "")

        else ->
            "原图保存失败" + (result.firstError?.let { "：$it" } ?: "")
    }
}

private fun Session.toWeiboUser(): WeiboUser = WeiboUser(
    id = uid,
    screenName = screenName,
    avatarUrl = avatarUrl,
    verified = verified,
    followersCount = followersCount,
    followingCount = followingCount,
    statusesCount = statusesCount,
    description = description
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
