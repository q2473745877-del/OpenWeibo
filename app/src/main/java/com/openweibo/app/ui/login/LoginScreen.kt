package com.openweibo.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openweibo.app.AppContainer
import com.openweibo.app.data.AuthenticationException
import com.openweibo.app.data.Session
import com.openweibo.app.ui.components.BrandMark
import com.openweibo.app.ui.components.Hairline
import com.openweibo.app.ui.components.IconAction
import com.openweibo.app.ui.components.MonoLabel
import com.openweibo.app.ui.components.PrimaryButton
import com.openweibo.app.ui.theme.AppTheme
import com.openweibo.app.ui.theme.GeistMono
import com.openweibo.app.ui.theme.monoStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOGIN_URL =
    "https://passport.weibo.com/sso/signin?entry=wapsso&source=wapssowb&url=https%3A%2F%2Fm.weibo.cn"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    container: AppContainer,
    onAuthenticated: (Session) -> Unit,
    onSnackbar: (String) -> Unit
) {
    val colors = AppTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cookieManager = remember { CookieManager.getInstance() }

    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var pageTitle by remember { mutableStateOf("正在打开微博登录") }
    var validating by remember { mutableStateOf(false) }
    var autoCheckToken by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun verifyLogin(showError: Boolean) {
        if (validating) return
        val cookies = cookieManager.getCookie("https://m.weibo.cn/")
            ?: cookieManager.getCookie("https://m.weibo.cn")
            ?: ""
        if (cookies.isBlank()) {
            if (showError) errorMessage = "还没有检测到登录信息，请先在下方完成微博登录。"
            return
        }
        val desktopCookies = cookieManager.getCookie("https://weibo.com/")
            ?: cookieManager.getCookie("https://weibo.com")
            ?: ""
        validating = true
        errorMessage = null
        scope.launch {
            try {
                cookieManager.flush()
                val session = container.api.validateLogin(cookies, desktopCookies)
                onAuthenticated(session)
            } catch (error: Exception) {
                if (showError) {
                    errorMessage = when (error) {
                        is AuthenticationException -> "未检测到有效登录，请完成登录后再试。"
                        else -> error.message ?: "登录校验失败，请重试。"
                    }
                }
            } finally {
                validating = false
            }
        }
    }

    LaunchedEffect(autoCheckToken) {
        if (autoCheckToken <= 0) return@LaunchedEffect
        delay(650)
        verifyLogin(showError = false)
    }

    DisposableEffect(Unit) {
        cookieManager.setAcceptCookie(true)
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
    ) {
        val wide = maxWidth >= 820.dp
        Column(Modifier.fillMaxSize()) {
            LoginTopBar()
            Hairline()
            if (wide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    LoginManifesto(
                        modifier = Modifier
                            .width(330.dp)
                            .fillMaxHeight()
                    )
                    LoginWebCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        progress = progress,
                        pageTitle = pageTitle,
                        validating = validating,
                        errorMessage = errorMessage,
                        onReload = { webView?.reload() },
                        onManualLogin = { verifyLogin(showError = true) },
                        webViewContent = {
                            LoginWebView(
                                container = container,
                                cookieManager = cookieManager,
                                onProgress = { progress = it },
                                onTitle = { pageTitle = it },
                                onWebView = { webView = it },
                                onArrivedAtWeibo = { autoCheckToken++ }
                            )
                        }
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactIntro()
                    LoginWebCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        progress = progress,
                        pageTitle = pageTitle,
                        validating = validating,
                        errorMessage = errorMessage,
                        compact = true,
                        onReload = { webView?.reload() },
                        onManualLogin = { verifyLogin(showError = true) },
                        webViewContent = {
                            LoginWebView(
                                container = container,
                                cookieManager = cookieManager,
                                onProgress = { progress = it },
                                onTitle = { pageTitle = it },
                                onWebView = { webView = it },
                                onArrivedAtWeibo = { autoCheckToken++ }
                            )
                        }
                    )
                    Text(
                        text = "凭证直接提交给微博，本应用不会读取或保存你的密码。",
                        color = colors.textFaint,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        if (validating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.54f)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceRaised)
                        .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        color = colors.text,
                        strokeWidth = 1.7.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "正在验证微博会话…",
                        color = colors.text,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginTopBar() {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.chrome)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "OpenWeibo",
                color = colors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            MonoLabel("WINDOWS EDITION / UNOFFICIAL", size = 8.5)
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surfaceAlt)
                .border(1.dp, colors.borderSoft, CircleShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(colors.success)
            )
            Spacer(Modifier.width(6.dp))
            MonoLabel("LOCAL SESSION", color = colors.textDim, size = 8.5)
        }
    }
}

@Composable
private fun LoginManifesto(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.chrome)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WindowDot(Color(0xFF5B5B61))
            Spacer(Modifier.width(6.dp))
            WindowDot(Color(0xFF5B5B61))
            Spacer(Modifier.width(6.dp))
            WindowDot(colors.success)
            Spacer(Modifier.weight(1f))
            MonoLabel("session://weibo", size = 9)
        }
        Spacer(Modifier.height(30.dp))
        Text(
            text = "把微博，\n还给自己。",
            color = colors.text,
            fontSize = 31.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.9).sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "一个按发布时间排序、没有算法噪音的第三方微博客户端。",
            color = colors.textDim,
            fontSize = 13.5.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(30.dp))
        Hairline()
        Spacer(Modifier.height(18.dp))
        ManifestLine("01", "关注时间线严格按时间排列")
        ManifestLine("02", "实时热搜，支持七个分类")
        ManifestLine("03", "单图原图保存，一键保存正文全部原图")
        ManifestLine("04", "应用内视频播放与 GIF 动图")
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceAlt)
                .border(1.dp, colors.borderSoft, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = colors.textFaint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "登录在微博官方页面完成。会话 Cookie 使用 Android Keystore 加密，仅保存在本机。",
                color = colors.textFaint,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun ManifestLine(number: String, text: String) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            fontFamily = GeistMono,
            fontSize = 9.5.sp,
            color = colors.textFaint
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = colors.textDim,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun CompactIntro() {
    val colors = AppTheme.colors
    Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)) {
        Text(
            text = "登录微博",
            color = colors.text,
            fontSize = 27.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.7).sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "按时间排列的关注时间线 · 实时热搜 · 原图保存",
            color = colors.textFaint,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun WindowDot(color: Color) {
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun LoginWebCard(
    progress: Int,
    pageTitle: String,
    validating: Boolean,
    errorMessage: String?,
    onReload: () -> Unit,
    onManualLogin: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    webViewContent: @Composable () -> Unit
) {
    val colors = AppTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(15.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceAlt)
                .padding(start = 14.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (progress in 1..99) colors.highlight else colors.success)
            )
            Spacer(Modifier.width(8.dp))
            MonoLabel(
                text = pageTitle.take(44).ifBlank { "weibo.com / sign in" },
                color = colors.textDim,
                size = 9
            )
            Spacer(Modifier.weight(1f))
            IconAction(
                icon = Icons.Outlined.Refresh,
                contentDescription = "重新加载登录页",
                onClick = onReload,
                tint = colors.textFaint,
                iconSize = 16.dp
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.borderSoft)
        ) {
            if (progress in 1..99) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress / 100f)
                        .height(1.dp)
                        .background(colors.textDim)
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            webViewContent()
        }
        Hairline()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = colors.danger,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!compact) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = colors.textFaint,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "由微博官方页面处理登录",
                            color = colors.textFaint,
                            fontSize = 10.5.sp
                        )
                    }
                }
                PrimaryButton(
                    label = if (validating) "正在验证…" else "我已登录，继续",
                    onClick = onManualLogin,
                    enabled = !validating,
                    loading = validating,
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 150.dp)
                )
                if (!compact) Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    container: AppContainer,
    cookieManager: CookieManager,
    onProgress: (Int) -> Unit,
    onTitle: (String) -> Unit,
    onWebView: (WebView) -> Unit,
    onArrivedAtWeibo: () -> Unit
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    mediaPlaybackRequiresUserGesture = true
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onProgress(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        if (!title.isNullOrBlank()) onTitle(title)
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onProgress(5)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onProgress(100)
                        val host = url?.let { android.net.Uri.parse(it).host }.orEmpty()
                        if (host == "m.weibo.cn" || host.endsWith(".weibo.cn") && !host.contains("passport")) {
                            onArrivedAtWeibo()
                        }
                    }
                }
                onWebView(this)
                loadUrl(LOGIN_URL)
            }
        }
    )
}
