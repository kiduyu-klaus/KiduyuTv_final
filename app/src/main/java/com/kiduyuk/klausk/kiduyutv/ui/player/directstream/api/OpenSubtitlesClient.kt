package com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.kiduyuk.klausk.kiduyutv.BuildConfig
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.SubtitleItem
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject

data class OpenSubtitlesResult(
    val subtitleId: String,
    val fileId: Long,
    val language: String,
    val releaseName: String,
    val fileName: String?,
    val hearingImpaired: Boolean,
    val downloadCount: Int
) {
    val displayName: String
        get() = buildString {
            append(language.ifBlank { "Unknown language" })
            if (releaseName.isNotBlank()) append(" • ").append(releaseName)
            if (hearingImpaired) append(" • HI")
            fileName?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
            if (downloadCount > 0) append(" • ").append(downloadCount).append(" downloads")
        }
}

class OpenSubtitlesClient(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    val isConfigured: Boolean
        get() = BuildConfig.OPENSUBTITLES_API_KEY.isNotBlank()

    suspend fun search(
        tmdbId: Int,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        language: String = "en"
    ): List<OpenSubtitlesResult> = withContext(Dispatchers.IO) {
        require(isConfigured) { "OpenSubtitles API key is not configured" }
        require(tmdbId > 0) { "A valid TMDB ID is required" }

        val url = API_BASE.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/subtitles")
            .addQueryParameter("languages", language)
            .addQueryParameter("type", if (isTv) "episode" else "movie")
            .apply {
                if (isTv) {
                    addQueryParameter("parent_tmdb_id", tmdbId.toString())
                    season?.let { addQueryParameter("season_number", it.toString()) }
                    episode?.let { addQueryParameter("episode_number", it.toString()) }
                } else {
                    addQueryParameter("tmdb_id", tmdbId.toString())
                }
            }
            .build()

        execute(
            Request.Builder()
                .url(url)
                .get()
        ).use { response ->
            if (!response.isSuccessful) {
                throw apiError("Search", response.code, response.body?.string())
            }
            parseResults(response.body?.string().orEmpty())
                .also { results ->
                    Log.i(
                        TAG,
                        "Search complete tmdbId=$tmdbId type=${if (isTv) "episode" else "movie"} " +
                            "season=${season ?: "-"} episode=${episode ?: "-"} results=${results.size}"
                    )
                }
        }
    }

    suspend fun download(result: OpenSubtitlesResult): SubtitleItem = withContext(Dispatchers.IO) {
        require(isConfigured) { "OpenSubtitles API key is not configured" }

        val requestBody = JSONObject()
            .put("file_id", result.fileId)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val downloadUrl = execute(
            Request.Builder()
                .url("$API_BASE/api/v1/download")
                .post(requestBody)
        ).use { response ->
            if (!response.isSuccessful) {
                throw apiError("Download request", response.code, response.body?.string())
            }
            val json = JSONObject(response.body?.string().orEmpty())
            json.optString("link").trim().takeIf { it.isNotBlank() }
                ?: throw IOException("OpenSubtitles did not return a download link")
        }

        val bytes = execute(
            Request.Builder()
                .url(downloadUrl)
                .get()
        ).use { response ->
            if (!response.isSuccessful) {
                throw apiError("File download", response.code, response.body?.string())
            }
            response.body?.bytes() ?: throw IOException("OpenSubtitles returned an empty subtitle file")
        }

        val subtitle = saveSubtitle(bytes, result)
        Log.i(
            TAG,
            "Subtitle downloaded id=${result.subtitleId} fileId=${result.fileId} " +
                "language=${result.language} bytes=${subtitle.length()}"
        )
        SubtitleItem(
            url = subtitle.toUri().toString(),
            mimeType = mimeType(subtitle.name),
            language = result.language.takeIf { it.isNotBlank() },
            label = "OpenSubtitles • ${result.language.ifBlank { result.releaseName }}"
        )
    }

    private fun execute(builder: Request.Builder) = client.newCall(
        builder
            .header("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
    ).execute()

    private fun parseResults(body: String): List<OpenSubtitlesResult> {
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val attributes = item.optJSONObject("attributes") ?: continue
                val files = attributes.optJSONArray("files") ?: continue
                val file = (0 until files.length())
                    .mapNotNull { files.optJSONObject(it) }
                    .firstOrNull { it.optLong("file_id", 0L) > 0L }
                    ?: continue
                val fileName = file.optString("file_name").trim().takeIf { it.isNotBlank() }
                // OpenSubtitles file_name values are often release-style
                // names such as "Show.S01E01.720p.WEB-DL" and may contain
                // several dots without a real extension. Trust the file_id;
                // the downloaded bytes are validated and typed later.
                val feature = attributes.optJSONObject("feature_details")
                add(
                    OpenSubtitlesResult(
                        subtitleId = item.optString("id").trim(),
                        fileId = file.optLong("file_id"),
                        language = attributes.optString("language").trim(),
                        releaseName = attributes.optString("release").trim(),
                        fileName = fileName,
                        hearingImpaired = attributes.optBoolean("hearing_impaired", false),
                        downloadCount = attributes.optInt("download_count", 0)
                    )
                )
            }
        }.distinctBy { it.fileId }
    }

    private fun saveSubtitle(bytes: ByteArray, result: OpenSubtitlesResult): File {
        val directory = File(context.cacheDir, "opensubtitles").apply { mkdirs() }
        val safeId = result.fileId.toString()
        if (bytes.isZip()) {
            val selectedName = result.fileName?.substringAfterLast('/')
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !isSupportedSubtitle(entry.name)) continue
                    if (selectedName != null &&
                        !entry.name.substringAfterLast('/').equals(selectedName, ignoreCase = true)
                    ) continue
                    val extension = entry.name.substringAfterLast('.', "srt").lowercase()
                    val output = File(directory, "$safeId.$extension")
                    FileOutputStream(output).use { zip.copyTo(it) }
                    return output
                }
            }
            throw IOException("The OpenSubtitles archive contains no supported SRT or VTT file")
        }

        val extension = result.fileName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it == "srt" || it == "vtt" }
            ?: detectExtension(bytes)
        return File(directory, "$safeId.$extension").apply { writeBytes(bytes) }
    }

    private fun apiError(operation: String, code: Int, body: String?): IOException {
        val message = runCatching {
            JSONObject(body.orEmpty()).optString("message").takeIf { it.isNotBlank() }
                ?: JSONObject(body.orEmpty()).optJSONArray("errors")?.optJSONObject(0)?.optString("message")
        }.getOrNull().orEmpty()
        val hint = when (code) {
            401, 403 -> "Check the OpenSubtitles API key and User-Agent."
            429 -> "OpenSubtitles rate limit reached; try again later."
            else -> ""
        }
        return IOException(
            "$operation failed (HTTP $code)" +
                message.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty() +
                hint.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        )
    }

    private fun ByteArray.isZip(): Boolean =
        size >= 4 && this[0] == 0x50.toByte() && this[1] == 0x4b.toByte()

    private fun detectExtension(bytes: ByteArray): String =
        if (bytes.toString(Charsets.UTF_8).trimStart().startsWith("WEBVTT")) "vtt" else "srt"

    private fun isSupportedSubtitle(name: String): Boolean {
        val path = name.substringBefore('?').lowercase()
        return path.endsWith(".srt") || path.endsWith(".vtt")
    }

    private fun mimeType(name: String): String =
        if (name.lowercase().endsWith(".vtt")) "text/vtt" else "application/x-subrip"

    private companion object {
        const val TAG = "OpenSubtitles"
        const val API_BASE = "https://api.opensubtitles.com"
        const val USER_AGENT = "kiduyutv v1.0"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
