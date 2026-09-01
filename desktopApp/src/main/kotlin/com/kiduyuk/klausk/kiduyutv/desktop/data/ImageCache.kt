package com.kiduyuk.klausk.kiduyutv.desktop.data

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed class ImageLoadState {
    data object Loading : ImageLoadState()
    data class Success(val path: String) : ImageLoadState()
    data class Error(val message: String) : ImageLoadState()
}

class ImageCache(private val scope: CoroutineScope) {
    private val cacheDir = File(
        System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
        "KiduyuTV/cache/images"
    ).also { it.mkdirs() }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val loadingStates = mutableMapOf<String, MutableState<ImageLoadState>>()
    private val urlToFile = mutableMapOf<String, String>()

    fun loadImage(url: String?): State<ImageLoadState> {
        if (url.isNullOrBlank()) {
            return mutableStateOf<ImageLoadState>(ImageLoadState.Error("No URL provided"))
        }

        val tmdbImageUrl = if (url.startsWith("/")) {
            "https://image.tmdb.org/t/p/w500$url"
        } else if (url.startsWith("http")) {
            url
        } else {
            return mutableStateOf<ImageLoadState>(ImageLoadState.Error("Invalid URL"))
        }

        return loadingStates.getOrPut(tmdbImageUrl) { 
            mutableStateOf<ImageLoadState>(ImageLoadState.Loading).also { state ->
                scope.launch {
                    try {
                        val cachedFile = urlToFile[tmdbImageUrl] ?: run {
                            val file = File(cacheDir, tmdbImageUrl.hashCode().toString() + ".jpg")
                            if (!file.exists()) {
                                downloadImage(tmdbImageUrl, file)
                            }
                            file.absolutePath.also { urlToFile[tmdbImageUrl] = it }
                        }

                        if (File(cachedFile).exists()) {
                            state.value = ImageLoadState.Success(cachedFile)
                        } else {
                            state.value = ImageLoadState.Error("File not found")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        state.value = ImageLoadState.Error(e.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    private suspend fun downloadImage(url: String, destination: File) {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                destination.parentFile?.mkdirs()
                destination.outputStream().use { out ->
                    response.body?.byteStream()?.copyTo(out)
                }
            }
        }
    }

    fun clearCache() {
        try {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
            urlToFile.clear()
            loadingStates.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCachedFile(url: String?): File? {
        if (url.isNullOrBlank()) return null
        val tmdbImageUrl = if (url.startsWith("/")) {
            "https://image.tmdb.org/t/p/w500$url"
        } else {
            url
        }
        val path = urlToFile[tmdbImageUrl] ?: return null
        return File(path).takeIf { it.exists() }
    }
}
