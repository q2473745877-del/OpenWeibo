package com.openweibo.app

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.openweibo.app.data.ImageSaver
import com.openweibo.app.data.PostChange
import com.openweibo.app.data.SessionManager
import com.openweibo.app.data.SessionStore
import com.openweibo.app.data.SettingsStore
import com.openweibo.app.data.VideoSaver
import com.openweibo.app.data.WeiboApi
import kotlinx.coroutines.flow.MutableSharedFlow

class AppContainer(context: Context) {
    val appContext = context.applicationContext
    val sessionManager = SessionManager(SessionStore(appContext))
    val settingsStore = SettingsStore(appContext)
    val postChanges = MutableSharedFlow<PostChange>(extraBufferCapacity = 16)
    val api = WeiboApi(
        contentResolver = appContext.contentResolver,
        desktopCookieProvider = {
            android.webkit.CookieManager.getInstance().getCookie("https://weibo.com/").orEmpty()
        }
    )
    val imageSaver = ImageSaver(appContext)
    val videoSaver = VideoSaver(appContext)
}

class OpenWeiboApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
