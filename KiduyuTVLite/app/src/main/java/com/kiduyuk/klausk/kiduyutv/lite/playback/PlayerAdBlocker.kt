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
 * Downloads, caches, and evaluates EasyList ad-server domain rules for the Lite player.
 *
 * This class intentionally implements only network-domain rules such as `||ads.example^`
 * and exception rules such as `@@||allowed.example^`. Cosmetic filters, regular-expression
 * rules, and element-hiding syntax are ignored because [shouldBlock] runs for every WebView
 * request and must remain inexpensive.
 *
 * Disk and network operations run on [Dispatchers.IO]. Once parsed, an immutable rule snapshot
 * is published through a volatile reference so WebView request threads can perform lock-free
 * lookups. Download and parsing failures are fail-open: the player continues with either cached
 * rules or an empty rule set.
 *
 * @param cacheDirectory application-private cache directory supplied by `PlayerActivity`.
 */
internal class PlayerAdBlocker(cacheDirectory: File) {

    /**
     * Summary returned to PlayerActivity for diagnostics and refresh scheduling.
     *
     * @property blockedDomainCount number of parsed blocking domains currently installed.
     * @property source whether rules came from cache, network, or an unavailable fallback.
     * @property refreshRecommended true when cached rules can be used immediately but are stale.
     * @property error exception type when an operation failed; messages are deliberately omitted.
     */
    data class InitializationResult(
        val blockedDomainCount: Int,
        val source: String,
        val refreshRecommended: Boolean,
        val error: String? = null
    )

    // shouldInterceptRequest may read this from a WebView worker thread.
    @Volatile
    private var rules = DomainRules.EMPTY

    // Keeping the list under cacheDir allows Android to reclaim it without affecting app data.
    private val filterDirectory = File(cacheDirectory, CACHE_DIRECTORY_NAME)
    private val filterFile = File(filterDirectory, FILTER_FILE_NAME)

    /**
     * Installs cached rules when possible, otherwise performs the first network download.
     *
     * A stale cache is still returned immediately. The caller can begin playback and invoke
     * [refresh] in a separate lifecycle coroutine.
     */
    suspend fun initialize(): InitializationResult = withContext(Dispatchers.IO) {
        loadCachedRules()?.let { cachedRules ->
            rules = cachedRules
            return@withContext InitializationResult(
                blockedDomainCount = cachedRules.blocked.size,
                source = "cache",
                refreshRecommended = isCacheStale()
            )
        }

        // Fail open when no usable cache exists and the first download cannot complete.
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

    /**
     * Replaces the active rule snapshot with a newly downloaded list.
     *
     * If refresh fails, the already-installed cache remains active.
     */
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

    /**
     * Returns true when [uri]'s host matches a blocking rule and no exception rule applies.
     *
     * The caller is responsible for exempting main-frame and provider-owned requests.
     */
    fun shouldBlock(uri: Uri): Boolean {
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        // Read once so a concurrent refresh cannot mix two rule snapshots in one decision.
        val currentRules = rules
        if (matchesDomain(currentRules.allowed, host)) return false
        return matchesDomain(currentRules.blocked, host)
    }

    /** Returns a validated cached snapshot, or null when the cache is absent/corrupt. */
    private fun loadCachedRules(): DomainRules? {
        if (!filterFile.isFile) return null
        return runCatching {
            val parsed = parseRules(filterFile.readText(StandardCharsets.UTF_8))
            parsed.takeIf { it.blocked.size >= MINIMUM_EXPECTED_RULES }
        }.getOrNull()
    }

    /** Cache age controls refresh only; stale rules remain safe to use during playback startup. */
    private fun isCacheStale(): Boolean {
        if (!filterFile.isFile) return true
        return System.currentTimeMillis() - filterFile.lastModified() >= CACHE_MAX_AGE_MS
    }

    /** Downloads, validates, persists, and atomically publishes a complete rule snapshot. */
    private fun downloadAndInstall(): DomainRules {
        val content = downloadFilter()
        val parsed = parseRules(content)
        if (parsed.blocked.size < MINIMUM_EXPECTED_RULES) {
            throw IOException("Downloaded filter contains too few supported domain rules")
        }

        filterDirectory.mkdirs()
        // Write a temporary file first to avoid treating a partial download as a valid cache.
        val temporaryFile = File(filterDirectory, "$FILTER_FILE_NAME.tmp")
        temporaryFile.writeText(content, StandardCharsets.UTF_8)
        if (!temporaryFile.renameTo(filterFile)) {
            // Windows/Android filesystems may not replace an existing target via renameTo.
            filterFile.writeText(content, StandardCharsets.UTF_8)
            temporaryFile.delete()
        }
        // Publish only after parsing and persistence have both succeeded.
        rules = parsed
        return parsed
    }

    /**
     * Downloads the plain-text filter with bounded time and memory use.
     *
     * Both the declared Content-Length and streamed byte count are checked because servers may
     * omit or misreport Content-Length.
     */
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

    /**
     * Parses the domain-anchored EasyList subset used by the ad-server list.
     *
     * For `||ads.example^$third-party`, parsing stops before `^`/options and stores only
     * `ads.example`. Lines beginning with `@@` are stored in the exception set.
     */
    private fun parseRules(content: String): DomainRules {
        val blocked = HashSet<String>(50_000)
        val allowed = HashSet<String>()

        content.lineSequence().forEach { sourceLine ->
            val line = sourceLine.trim()
            // Ignore blank lines, comments, and Adblock Plus metadata headers.
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) {
                return@forEach
            }

            val isException = line.startsWith("@@")
            val rule = if (isException) line.removePrefix("@@") else line
            // Path, cosmetic, and regular-expression rules are outside this domain matcher.
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

    /** Rejects malformed or non-host values before they reach the request-time sets. */
    private fun isSupportedDomain(domain: String): Boolean =
        domain.length in MIN_DOMAIN_LENGTH..MAX_DOMAIN_LENGTH &&
            '.' in domain &&
            domain.all { character ->
                character.isLetterOrDigit() || character == '-' || character == '.'
            }

    /**
     * Checks an exact host and each parent suffix using O(number of host labels) hash lookups.
     * For example, `img.ads.example.com` checks itself, then `ads.example.com`, `example.com`,
     * and finally `com`.
     */
    private fun matchesDomain(domains: Set<String>, host: String): Boolean {
        var candidate = host
        while (true) {
            if (candidate in domains) return true
            val separator = candidate.indexOf('.')
            if (separator < 0) return false
            candidate = candidate.substring(separator + 1)
        }
    }

    /** Immutable snapshot shared by the initialization coroutine and WebView request threads. */
    private data class DomainRules(
        val blocked: Set<String>,
        val allowed: Set<String>
    ) {
        companion object {
            val EMPTY = DomainRules(emptySet(), emptySet())
        }
    }

    companion object {
        // Runtime source is intentionally remote so the large list is not packaged in the APK.
        private const val FILTER_URL =
            "https://raw.githubusercontent.com/easylist/easylist/refs/heads/master/easylist/easylist_adservers.txt"
        private const val CACHE_DIRECTORY_NAME = "player_adblock"
        private const val FILTER_FILE_NAME = "easylist_adservers.txt"
        private const val NETWORK_TIMEOUT_MS = 12_000
        private const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
        // The current list is roughly 1 MB; 3 MB leaves headroom while bounding memory use.
        private const val MAX_FILTER_BYTES = 3 * 1024 * 1024
        // Reject HTML error pages or unexpectedly truncated lists before caching them.
        private const val MINIMUM_EXPECTED_RULES = 1_000
        private const val MIN_DOMAIN_LENGTH = 3
        private const val MAX_DOMAIN_LENGTH = 253
        private val CACHE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(24)
    }
}
