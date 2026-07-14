package com.kiduyuk.klausk.kiduyutv.lite.playback

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * EasyList domain blocker used only by the Lite player WebView.
 */
internal class PlayerAdBlocker(cacheDirectory: File) {

    data class InitializationResult(
        val blockedDomainCount: Int,
        val source: String,
        val refreshRecommended: Boolean,
        val error: String? = null
    )

    @Volatile
    private var rules = DomainRules.EMPTY

    private val filterDirectory = File(cacheDirectory, CACHE_DIRECTORY_NAME)
    private val filterFile = File(filterDirectory, FILTER_FILE_NAME)

    suspend fun initialize(): InitializationResult = withContext(Dispatchers.IO) {
        loadCachedRules()?.let { cachedRules ->
            rules = cachedRules
            return@withContext InitializationResult(
                blockedDomainCount = cachedRules.blocked.size,
                source = "cache",
                refreshRecommended = isCacheStale()
            )
        }

        runCatching { downloadAndInstall() }
            .fold(
                onSuccess = { downloadedRules ->
                    InitializationResult(
                        blockedDomainCount = downloadedRules.blocked.size,
                        source = "network",
                        refreshRecommended = false
                    )
                },
                onFailure = { error ->
                    InitializationResult(
                        blockedDomainCount = 0,
                        source = "unavailable",
                        refreshRecommended = false,
                        error = error.javaClass.simpleName
                    )
                }
            )
    }

    suspend fun refresh(): InitializationResult = withContext(Dispatchers.IO) {
        runCatching { downloadAndInstall() }
            .fold(
                onSuccess = { downloadedRules ->
                    InitializationResult(
                        blockedDomainCount = downloadedRules.blocked.size,
                        source = "network-refresh",
                        refreshRecommended = false
                    )
                },
                onFailure = { error ->
                    InitializationResult(
                        blockedDomainCount = rules.blocked.size,
                        source = "cache-refresh-failed",
                        refreshRecommended = false,
                        error = error.javaClass.simpleName
                    )
                }
            )
    }

    fun shouldBlock(uri: Uri): Boolean {
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        val currentRules = rules
        if (matchesDomain(currentRules.allowed, host)) return false
        return matchesDomain(currentRules.blocked, host)
    }

    private fun loadCachedRules(): DomainRules? {
        if (!filterFile.isFile) return null
        return runCatching {
            val parsed = parseRules(filterFile.readText(StandardCharsets.UTF_8))
            parsed.takeIf { it.blocked.size >= MINIMUM_EXPECTED_RULES }
        }.getOrNull()
    }

    private fun isCacheStale(): Boolean {
        if (!filterFile.isFile) return true
        return System.currentTimeMillis() - filterFile.lastModified() >= CACHE_MAX_AGE_MS
    }

    private fun downloadAndInstall(): DomainRules {
        val content = downloadFilter()
        val parsed = parseRules(content)
        if (parsed.blocked.size < MINIMUM_EXPECTED_RULES) {
            throw IOException("Downloaded filter contains too few supported domain rules")
        }

        filterDirectory.mkdirs()
        val temporaryFile = File(filterDirectory, "$FILTER_FILE_NAME.tmp")
        temporaryFile.writeText(content, StandardCharsets.UTF_8)
        if (!temporaryFile.renameTo(filterFile)) {
            filterFile.writeText(content, StandardCharsets.UTF_8)
            temporaryFile.delete()
        }
        rules = parsed
        return parsed
    }

    private fun downloadFilter(): String {
        val connection = (URL(FILTER_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("User-Agent", "KiduyuTVLite-AdBlock")
        }

        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Filter download failed with HTTP $status")
            }
            if (connection.contentLengthLong > MAX_FILTER_BYTES) {
                throw IOException("Filter download exceeds the size limit")
            }

            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                var totalBytes = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    totalBytes += read
                    if (totalBytes > MAX_FILTER_BYTES) {
                        throw IOException("Filter download exceeds the size limit")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRules(content: String): DomainRules {
        val blocked = HashSet<String>(50_000)
        val allowed = HashSet<String>()

        content.lineSequence().forEach { sourceLine ->
            val line = sourceLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) {
                return@forEach
            }

            val isException = line.startsWith("@@")
            val rule = if (isException) line.removePrefix("@@") else line
            if (!rule.startsWith("||")) return@forEach

            val domain = rule
                .removePrefix("||")
                .takeWhile { character ->
                    character != '^' &&
                        character != '$' &&
                        character != '/' &&
                        character != '*' &&
                        character != '|'
                }
                .trim()
                .trim('.')
                .lowercase()

            if (!isSupportedDomain(domain)) return@forEach
            if (isException) allowed.add(domain) else blocked.add(domain)
        }

        return DomainRules(blocked = blocked, allowed = allowed)
    }

    private fun isSupportedDomain(domain: String): Boolean =
        domain.length in MIN_DOMAIN_LENGTH..MAX_DOMAIN_LENGTH &&
            '.' in domain &&
            domain.all { character ->
                character.isLetterOrDigit() || character == '-' || character == '.'
            }

    private fun matchesDomain(domains: Set<String>, host: String): Boolean {
        var candidate = host
        while (true) {
            if (candidate in domains) return true
            val separator = candidate.indexOf('.')
            if (separator < 0) return false
            candidate = candidate.substring(separator + 1)
        }
    }

    private data class DomainRules(
        val blocked: Set<String>,
        val allowed: Set<String>
    ) {
        companion object {
            val EMPTY = DomainRules(emptySet(), emptySet())
        }
    }

    companion object {
        private const val FILTER_URL =
            "https://raw.githubusercontent.com/easylist/easylist/refs/heads/master/easylist/easylist_adservers.txt"
        private const val CACHE_DIRECTORY_NAME = "player_adblock"
        private const val FILTER_FILE_NAME = "easylist_adservers.txt"
        private const val NETWORK_TIMEOUT_MS = 12_000
        private const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
        private const val MAX_FILTER_BYTES = 3 * 1024 * 1024
        private const val MINIMUM_EXPECTED_RULES = 1_000
        private const val MIN_DOMAIN_LENGTH = 3
        private const val MAX_DOMAIN_LENGTH = 253
        private val CACHE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(24)
    }
}
