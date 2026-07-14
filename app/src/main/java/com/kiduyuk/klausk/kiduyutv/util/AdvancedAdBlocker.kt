package com.kiduyuk.klausk.kiduyutv.util

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Downloads, caches, and evaluates EasyList ad-server rules for WebView players.
 *
 * Only domain-anchored rules such as `||ads.example^` and their `@@` exceptions are parsed.
 * Cosmetic filters, path rules, and regular expressions are deliberately ignored so
 * [shouldBlock] remains inexpensive when WebView calls it on request worker threads.
 *
 * Disk and network work runs on [Dispatchers.IO]. A complete immutable [DomainRules] snapshot
 * is published through a volatile reference, allowing lock-free request checks while a refresh
 * is running. Download and parsing failures are fail-open: the player continues with either
 * cached rules or an empty rule set.
 */
object AdvancedAdBlocker {

    /**
     * Describes the active filter snapshot for player diagnostics.
     *
     * @property blockedDomainCount number of blocking domains currently installed.
     * @property source cache, network, memory, or unavailable source used for this result.
     * @property refreshRecommended true when a stale cache is active and should be refreshed.
     * @property error exception type when loading failed; detailed messages are not exposed.
     */
    data class InitializationResult(
        val blockedDomainCount: Int,
        val source: String,
        val refreshRecommended: Boolean,
        val error: String? = null
    )

    private const val TAG = "AdvancedAdBlocker"
    private const val FILTER_URL =
        "https://raw.githubusercontent.com/easylist/easylist/refs/heads/master/easylist/easylist_adservers.txt"
    private const val CACHE_DIRECTORY_NAME = "advanced_adblocker"
    private const val FILTER_FILE_NAME = "easylist_adservers.txt"
    private const val NETWORK_TIMEOUT_MS = 12_000
    private const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
    private const val MAX_FILTER_BYTES = 3 * 1024 * 1024
    private const val MINIMUM_EXPECTED_RULES = 1_000
    private const val MIN_DOMAIN_LENGTH = 3
    private const val MAX_DOMAIN_LENGTH = 253
    private val CACHE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(24)

    /**
     * Domains that are always blocked in every player WebView, independently of the EasyList
     * snapshot. Use this for known-bad hosts the upstream filter list either does not include
     * or risks dropping on a future refresh. The [matchesDomain] walker matches the exact host
     * and every parent suffix, so any subdomain of a listed entry is also blocked.
     */
    private val MANUAL_BLOCKED_DOMAINS: Set<String> = setOf(
        "unswung.gurlleviter.cyou",
        "princesseileen-idohyhm.work"
    )

    // The compatibility init() entry point owns application-lifetime work only.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Prevent concurrent player activities from downloading and replacing the same cache file.
    private val initializationMutex = Mutex()

    // WebView request threads read this without locking; refresh publishes a whole new snapshot.
    @Volatile
    private var rules = DomainRules.EMPTY

    @Volatile
    private var initialized = false

    /**
     * Starts asynchronous initialization for legacy application-level callers.
     *
     * New lifecycle-aware callers should use [initialize] so they can decide when the first page
     * is loaded. This method never performs disk or network work on the calling thread.
     */
    fun init(context: Context) {
        if (initialized) return
        val applicationContext = context.applicationContext
        applicationScope.launch {
            val result = initialize(applicationContext)
            Log.i(
                TAG,
                "Initialized source=${result.source} domains=${result.blockedDomainCount} " +
                    "error=${result.error.orEmpty()}"
            )
        }
    }

    /**
     * Installs cached rules when available, otherwise performs a bounded initial download.
     *
     * A stale cache is returned immediately with [InitializationResult.refreshRecommended] set.
     * The caller can start playback with that snapshot and invoke [refresh] in the background.
     */
    suspend fun initialize(context: Context): InitializationResult = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            // Reuse the process-wide immutable snapshot for additional player activities.
            if (initialized) {
                return@withLock InitializationResult(
                    blockedDomainCount = rules.blocked.size,
                    source = "memory",
                    refreshRecommended = false
                )
            }

            loadCachedRules(context)?.let { cachedRules ->
                rules = cachedRules
                initialized = true
                return@withLock InitializationResult(
                    blockedDomainCount = cachedRules.blocked.size,
                    source = "cache",
                    refreshRecommended = isCacheStale(context)
                )
            }

            // Fail open when no usable cache exists and the first download cannot complete.
            runCatching { downloadAndInstall(context) }
                .fold(
                    onSuccess = { downloadedRules ->
                        initialized = true
                        InitializationResult(
                            blockedDomainCount = downloadedRules.blocked.size,
                            source = "network",
                            refreshRecommended = false
                        )
                    },
                    onFailure = { error ->
                        rules = DomainRules.EMPTY
                        initialized = true
                        InitializationResult(
                            blockedDomainCount = 0,
                            source = "unavailable",
                            refreshRecommended = false,
                            error = error.javaClass.simpleName
                        )
                    }
                )
        }
    }

    /**
     * Downloads and atomically installs a fresh rule snapshot.
     *
     * The current snapshot remains active if download, validation, or persistence fails.
     */
    suspend fun refresh(context: Context): InitializationResult = withContext(Dispatchers.IO) {
        initializationMutex.withLock {
            runCatching { downloadAndInstall(context) }
                .fold(
                    onSuccess = { downloadedRules ->
                        initialized = true
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
    }

    /**
     * Returns true when [uri]'s host matches a blocking rule and no exception rule applies.
     *
     * This method performs no disk, network, regex, or logging work and is safe for WebView's
     * request threads.
     */
    fun shouldBlock(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false

        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false

        // Manually-pinned hosts are checked first so they remain blocked even when the EasyList
        // snapshot is empty (e.g. before the first download completes or after a failed refresh).
        if (matchesDomain(MANUAL_BLOCKED_DOMAINS, host)) return true

        // Read once so a concurrent refresh cannot mix two snapshots in one decision.
        val currentRules = rules
        if (matchesDomain(currentRules.allowed, host)) return false
        return matchesDomain(currentRules.blocked, host)
    }

    /** Compatibility overload for existing callers that provide a URL string. */
    fun shouldBlock(url: String): Boolean =
        url.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?.let(::shouldBlock)
            ?: false

    /**
     * Returns conservative element-hiding JavaScript retained for existing WebView integrations.
     *
     * Remote cosmetic rules are intentionally not parsed or injected because large selector lists
     * increase page style recalculation costs and can hide legitimate player controls.
     */
    fun getCss(): String = """
        (function() {
            try {
                var existingStyle = document.getElementById('kiduyu-adblock-css');
                if (existingStyle) return;
                var style = document.createElement('style');
                style.id = 'kiduyu-adblock-css';
                style.textContent = '.video-ads,.ytp-ad-module,[data-ad],[data-advertisement] { display:none !important; visibility:hidden !important; }';
                (document.head || document.documentElement).appendChild(style);
            } catch (ignored) {}
        })();
    """.trimIndent()

    /** Returns a small popup guard retained for callers of the previous blocker API. */
    fun getBlockingJavaScript(): String = """
        (function() {
            if (!window.__kiduyuPopupGuardInstalled) {
                window.__kiduyuPopupGuardInstalled = true;
                window.open = function() { return null; };
            }
        })();
    """.trimIndent()

    /** Returns whether cache/network initialization has completed in this app process. */
    fun isInitialized(): Boolean = initialized

    /** Reads and validates the cached EasyList snapshot, returning null for absent/corrupt data. */
    private fun loadCachedRules(context: Context): DomainRules? {
        val filterFile = getFilterFile(context)
        if (!filterFile.isFile) return null

        return runCatching {
            val parsed = parseRules(filterFile.readText(StandardCharsets.UTF_8))
            parsed.takeIf { it.blocked.size >= MINIMUM_EXPECTED_RULES }
        }.getOrNull()
    }

    /** Uses cache age only to schedule refresh; stale rules remain usable during startup. */
    private fun isCacheStale(context: Context): Boolean {
        val filterFile = getFilterFile(context)
        if (!filterFile.isFile) return true
        return System.currentTimeMillis() - filterFile.lastModified() >= CACHE_MAX_AGE_MS
    }

    /** Downloads, validates, persists, and then publishes one complete domain-rule snapshot. */
    private fun downloadAndInstall(context: Context): DomainRules {
        val content = downloadFilter()
        val parsed = parseRules(content)
        if (parsed.blocked.size < MINIMUM_EXPECTED_RULES) {
            throw IOException("Downloaded filter contains too few supported domain rules")
        }

        val filterFile = getFilterFile(context)
        filterFile.parentFile?.mkdirs()

        // A temporary file prevents interrupted writes from becoming the next startup cache.
        val temporaryFile = File(filterFile.parentFile, "${filterFile.name}.tmp")
        temporaryFile.writeText(content, StandardCharsets.UTF_8)
        if (!temporaryFile.renameTo(filterFile)) {
            // Some filesystems do not replace an existing target through renameTo.
            filterFile.writeText(content, StandardCharsets.UTF_8)
            temporaryFile.delete()
        }

        // Publish only after both validation and cache persistence succeed.
        rules = parsed
        return parsed
    }

    /** Downloads the filter with bounded connection time, read time, and memory use. */
    private fun downloadFilter(): String {
        val connection = (URL(FILTER_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "text/plain")
            setRequestProperty("User-Agent", "KiduyuTV-AdBlock")
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

    /** Parses only domain-anchored blocking and exception rules from the EasyList source. */
    private fun parseRules(content: String): DomainRules {
        val blocked = HashSet<String>(50_000)
        val allowed = HashSet<String>()

        content.lineSequence().forEach { sourceLine ->
            val line = sourceLine.trim()
            // Ignore comments, blank lines, and Adblock Plus metadata headers.
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) {
                return@forEach
            }

            val isException = line.startsWith("@@")
            val rule = if (isException) line.removePrefix("@@") else line
            // Path, cosmetic, wildcard, and regular-expression rules are outside this matcher.
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

    /** Rejects malformed or non-host values before they enter the request-time sets. */
    private fun isSupportedDomain(domain: String): Boolean =
        domain.length in MIN_DOMAIN_LENGTH..MAX_DOMAIN_LENGTH &&
            '.' in domain &&
            domain.all { character ->
                character.isLetterOrDigit() || character == '-' || character == '.'
            }

    /** Checks the exact host and each parent suffix with one hash lookup per host label. */
    private fun matchesDomain(domains: Set<String>, host: String): Boolean {
        var candidate = host
        while (true) {
            if (candidate in domains) return true
            val separator = candidate.indexOf('.')
            if (separator < 0) return false
            candidate = candidate.substring(separator + 1)
        }
    }

    /** Returns the application-private cache file used by all WebView player instances. */
    private fun getFilterFile(context: Context): File =
        File(File(context.cacheDir, CACHE_DIRECTORY_NAME), FILTER_FILE_NAME)

    /** Immutable snapshot shared by initialization coroutines and WebView request threads. */
    private data class DomainRules(
        val blocked: Set<String>,
        val allowed: Set<String>
    ) {
        companion object {
            val EMPTY = DomainRules(emptySet(), emptySet())
        }
    }

}
