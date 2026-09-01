package com.kiduyuk.klausk.kiduyutv.desktop.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale

data class DesktopUpdateInfo(
    val version: String,
    val releaseUrl: String,
    val exeUrl: String?,
    val msiUrl: String?,
    val exeName: String?,
    val msiName: String?
)

class DesktopUpdater(
    private val currentVersion: String = System.getProperty("kiduyutv.version", "1.1.71")
) {
    suspend fun checkForUpdate(): DesktopUpdateInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "KiduyuTV-Desktop")
            .build()

        DesktopHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub update check failed (HTTP ${response.code})")
            }
            val release = response.body?.string()?.let(JsonParser::parseString)?.asJsonObject
                ?: throw IOException("GitHub returned an empty release response")
            val latestVersion = release.get("tag_name")?.asString
                ?.removePrefix("v")
                ?.trim()
                ?: throw IOException("Latest release has no version tag")

            if (compareVersions(latestVersion, currentVersion) <= 0) return@withContext null

            var exeUrl: String? = null
            var msiUrl: String? = null
            var exeName: String? = null
            var msiName: String? = null
            release.getAsJsonArray("assets")?.forEach { element ->
                val asset = element.asJsonObject
                val name = asset.get("name")?.asString.orEmpty()
                val url = asset.get("browser_download_url")?.asString
                when {
                    exeUrl == null && url != null && name.endsWith(".exe", true) -> {
                        exeUrl = url
                        exeName = name
                    }
                    msiUrl == null && url != null && name.endsWith(".msi", true) -> {
                        msiUrl = url
                        msiName = name
                    }
                }
            }

            DesktopUpdateInfo(
                version = latestVersion,
                releaseUrl = release.get("html_url")?.asString.orEmpty(),
                exeUrl = exeUrl,
                msiUrl = msiUrl,
                exeName = exeName,
                msiName = msiName
            )
        }
    }

    suspend fun downloadAndInstall(
        update: DesktopUpdateInfo,
        preferMsi: Boolean = false,
        onProgress: suspend (Int) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        if (!isWindows()) throw IOException("Windows installer updates are only supported on Windows")
        val url = if (preferMsi) update.msiUrl ?: update.exeUrl else update.exeUrl ?: update.msiUrl
            ?: throw IOException("No Windows installer was found in the latest release")
        val fileName = if (preferMsi) update.msiName ?: update.exeName else update.exeName ?: update.msiName
            ?: "KiduyuTV-update.${if (url.endsWith(".msi", true)) "msi" else "exe"}"
        val destination = File(System.getProperty("java.io.tmpdir"), fileName)

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "KiduyuTV-Desktop")
            .build()
        DesktopHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Update download was empty")
            val total = body.contentLength()
            var downloaded = 0L
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) {
                            onProgress(((downloaded * 100L) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
        }
        onProgress(100)
        launchInstaller(destination)
    }

    private fun launchInstaller(file: File) {
        if (file.extension.equals("msi", true)) {
            ProcessBuilder("msiexec.exe", "/i", file.absolutePath).start()
        } else {
            ProcessBuilder(file.absolutePath).start()
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")

    private fun compareVersions(left: String, right: String): Int {
        val a = versionParts(left)
        val b = versionParts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val result = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    private fun versionParts(value: String): List<Int> =
        Regex("\\d+").findAll(value).map { it.value.toIntOrNull() ?: 0 }.toList()

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/kiduyu-klaus/KiduyuTv_final/releases/latest"
    }
}
