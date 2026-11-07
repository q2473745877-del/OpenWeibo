package com.openweibo.app.ui.feed

import androidx.compose.runtime.staticCompositionLocalOf

fun interface LongTextLoader {
    suspend fun load(statusId: String): String
}

val LocalLongTextLoader = staticCompositionLocalOf<LongTextLoader?> { null }
