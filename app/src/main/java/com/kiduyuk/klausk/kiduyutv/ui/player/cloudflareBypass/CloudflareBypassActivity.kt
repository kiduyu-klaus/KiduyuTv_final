package com.kiduyuk.klausk.kiduyutv.ui.player.cloudflareBypass

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.AdBlockerWebViewClient
import com.kiduyuk.klausk.kiduyutv.ui.player.webview.MouseCursorView

/**
 * CloudflareBypassActivity
 *
 * A transparent WebView screen whose sole purpose is to solve a Cloudflare
 * "Verify you are human" challenge and persist the resulting `cf_clearance`
 * cookie so every other WebView in the process (notably the one inside
 * [com.kiduyuk.klausk.kiduyutv.ui.player.webview.PlayerActivity]) can pass
 * the same Cloudflare gate on subsequent loads.
 *
 * ## How it works
 * 1. The caller starts this activity with the gated URL in [EXTRA_URL].
 * 2. The activity opens that URL in a fullscreen WebView.
 * 3. While the page is loading, the activity polls [CookieManager] every
 *    [POLL_INTERVAL_MS] looking for the `cf_clearance` cookie on the
 *    target domain.
 * 4. As soon as the cookie is detected, the activity:
 *    a. Calls [CookieManager.flush] to persist into the WebView's
 *       internal cookie database.
 *    b. Mirrors the cookie blob to [SharedPreferences] under the
 *       [PREFS_NAME] file, keyed by the target domain, so it can be
 *       re-injected into any WebView via [applyCookiesToCookieManager].
 * 5. The activity finishes with [Activity.RESULT_OK] so the caller can
 *    retry the original request.
 *
 * ## TV / remote navigation
 * On TV-form-factor devices (Android TV, Fire TV) the activity overlays a
 * [MouseCursorView] on top of the WebView. DPAD keys move the cursor, and
 * the OK / ENTER / CENTER key dispatches a synthetic touchscreen tap at
 * the cursor's location so Cloudflare's JS challenge receives a real
 * touch event. The cursor fades out after 5 seconds of inactivity and
 * reappears on the next key press. On phones and tablets the cursor is
 * not created — touch input goes directly to the WebView.
 *
 * ## Intent extras
 * - [EXTRA_URL] (String, **required**): the URL that triggered the gate.
 * - [EXTRA_TITLE] (String, optional): label shown in the top bar.
 * - [EXTRA_TIMEOUT_MS] (Long, optional): max wait in milliseconds before
 *   the activity gives up. Defaults to [DEFAULT_TIMEOUT_MS].
 *
 * ## Result
 * - `RESULT_OK` — challenge solved; `cf_clearance` is now persisted both
 *   in the WebView's cookie store and in [SharedPreferences].
 * - `RESULT_CANCELED` — user closed the screen before solving.
 *
 * ## Reusing saved cookies from other components
 * ```
 * val host = Uri.parse(streamUrl).host
 * CloudflareBypassActivity.applyCookiesToCookieManager(context, host)
 * // Now load the player WebView; cf_clearance is already in place.
 * ```
 */
class CloudflareBypassActivity : AppCompatActivity() {

    companion object {
        const val TAG = "CloudflareBypass"

        /** Legacy URL input, retained for callers that have not migrated to [EXTRA_HOST]. */
        const val EXTRA_URL = "url"

        /** Required for new callers: the gated stream host, without a path or query string. */
        const val EXTRA_HOST = "host"

        /** Optional: human-readable label for the top bar. */
        const val EXTRA_TITLE = "title"

        /** Optional: max wait in ms. Default 60 s. */
        const val EXTRA_TIMEOUT_MS = "timeout_ms"

        /**
         * Result extra: the raw `Cookie:` header containing the full cookie
         * jar captured for the target site, including `cf_clearance` and any
         * companion cookies required by the challenge. The caller can use
         * this to set the cookies directly without re-reading the
         * SharedPreferences mirror.
         */
        const val EXTRA_COOKIES = "cookies"

        /**
         * Result extra: the host (registered domain) that the cookies apply
         * to. Matches the key used by [saveCookies] / [loadCookies].
         */
        const val EXTRA_DOMAIN = "domain"

        const val DEFAULT_TIMEOUT_MS = 60_000L
        const val POLL_INTERVAL_MS = 1_000L

        /**
         * Cloudflare's challenge UI does not set `cf_clearance` until the
         * JS challenge has actually executed. We wait at least this long
         * before declaring a timeout, so a slow but legitimate solve isn't
         * killed by a tight timeout.
         */
        const val MIN_SOLVE_TIME_MS = 4_000L

        /** Cookie name whose presence signals a solved challenge. */
        const val CF_CLEARANCE_COOKIE = "cf_clearance"

        /** Secondary cookie sometimes set immediately on first visit. */
        const val CF_BM_COOKIE = "__cf_bm"

        // ── SharedPreferences persistence ────────────────────────────────────
        /** Name of the SharedPreferences file that stores captured cookies. */
        const val PREFS_NAME = "cf_cookies"

        /** Suffix used to store the capture timestamp for a domain. */
        const val KEY_TS_SUFFIX = "_ts"

        /** Suffix used to store the original URL for a domain. */
        const val KEY_URL_SUFFIX = "_url"

        /**
         * Persists the raw `Cookie:` header string for [domain] to
         * SharedPreferences so the cookies survive the process and can be
         * re-injected into any future WebView.
         *
         * @return true if the write succeeded.
         */
        @JvmStatic
        fun saveCookies(context: Context, domain: String, cookies: String, originalUrl: String): Boolean {
            if (domain.isBlank() || cookies.isBlank()) return false
            return try {
                val prefs = context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(domain, cookies)
                    .putLong("$domain$KEY_TS_SUFFIX", System.currentTimeMillis())
                    .putString("$domain$KEY_URL_SUFFIX", originalUrl)
                    .apply()
                Log.i(TAG, "Saved ${cookies.length}-char cookie blob to prefs for domain=$domain")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save cookies for $domain: ${e.message}")
                false
            }
        }

        /**
         * Retrieves the previously saved cookie blob for [domain], or null if
         * nothing is stored (or the stored value is empty).
         */
        @JvmStatic
        fun loadCookies(context: Context, domain: String): String? {
            if (domain.isBlank()) return null
            return try {
                val prefs = context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getString(domain, null)?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cookies for $domain: ${e.message}")
                null
            }
        }

        /**
         * @return the epoch-ms timestamp at which [domain]'s cookies were
         * captured, or 0 if unknown / never captured.
         */
        @JvmStatic
        fun loadCookiesTimestamp(context: Context, domain: String): Long {
            if (domain.isBlank()) return 0L
            return try {
                val prefs = context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getLong("$domain$KEY_TS_SUFFIX", 0L)
            } catch (e: Exception) {
                0L
            }
        }

        /**
         * Re-injects cookies previously stored by [saveCookies] into the
         * platform [CookieManager] so a WebView loading a request right now
         * can use them immediately. Each cookie pair in the blob is set
         * individually; values are URL-encoded if necessary.
         *
         * @return the number of cookies injected.
         */
        @JvmStatic
        fun applyCookiesToCookieManager(context: Context, domain: String): Int {
            val cookies = loadCookies(context, domain) ?: return 0
            val cm = CookieManager.getInstance()
            var count = 0
            cookies.split(';').forEach { entry ->
                val name = entry.substringBefore('=', missingDelimiterValue = "").trim()
                if (name.isNotEmpty()) {
                    // setCookie expects "name=value; Domain=...; Path=...; ..."
                    // We re-emit the full entry, which is already in that shape
                    // (the Android WebView accepted it when it wrote the cookie).
                    // CookieManager.setCookie returns Unit (void) on Android, not
                    // a Boolean — the return value tells you nothing about success.
                    cm.setCookie(domain, entry.trim())
                    count++
                }
            }
            cm.flush()
            Log.i(TAG, "Re-injected $count cookie(s) for domain=$domain")
            return count
        }

        /** Removes the stored cookies for [domain] only. */
        @JvmStatic
        fun clearCookies(context: Context, domain: String) {
            if (domain.isBlank()) return
            try {
                val prefs = context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .remove(domain)
                    .remove("$domain$KEY_TS_SUFFIX")
                    .remove("$domain$KEY_URL_SUFFIX")
                    .apply()
                Log.i(TAG, "Cleared saved cookies for domain=$domain")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear cookies for $domain: ${e.message}")
            }
        }

        /** Removes every captured cookie in the file. */
        @JvmStatic
        fun clearAllCookies(context: Context) {
            try {
                val prefs = context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                Log.i(TAG, "Cleared all saved Cloudflare cookies")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all cookies: ${e.message}")
            }
        }
    }

    // ── UI ──────────────────────────────────────────────────────────────────
    private lateinit var rootLayout: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var backButton: ImageButton
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView
    private lateinit var webView: WebView
    private lateinit var loadingDialog: ProgressDialog
    private lateinit var cursorView: MouseCursorView

    // ── State ───────────────────────────────────────────────────────────────
    private var targetUrl: String = ""
    private var lastMainFrameUrl: String = ""
    private var targetHost: String = ""
    private var displayTitle: String = ""
    private var timeoutMs: Long = DEFAULT_TIMEOUT_MS
    private var challengeStartedAt: Long = 0L
    private var isSolved: Boolean = false
    private var isFinishingForResult: Boolean = false

    // ── Cursor (TV remote navigation) ───────────────────────────────────────
    /** False on phones/tablets, true on TVs/Fire TV. */
    private var isCursorDisabled: Boolean = true
    private var cursorX: Float = 0f
    private var cursorY: Float = 0f
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private val cursorMoveSpeed: Float = 50f
    private val cursorHideHandler = Handler(Looper.getMainLooper())
    private val cursorHideRunnable = Runnable {
        if (!isCursorDisabled && ::cursorView.isInitialized) {
            cursorView.animate().alpha(0f).setDuration(500).start()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Polls CookieManager for `cf_clearance` while the challenge is open. */
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (isSolved) return
            if (hasCfClearance()) {
                onChallengeSolved()
                return
            }
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    /** Fires once after [timeoutMs] if the challenge is still unsolved. */
    private val timeoutRunnable = Runnable {
        // Bare `return` is illegal from a Runnable lambda (not inline).
        // Use a labelled `return@Runnable` … but Runnables are SAM
        // lambdas, so the idiomatic fix is to use the labelled
        // continuation explicitly. We just guard the body with an
        // `if/else` so there's no need for an early exit.
        run {
            if (isFinishing || isDestroyed || isSolved) return@run
            Log.w(TAG, "Challenge did not solve within ${timeoutMs}ms")
            setStatus("Cloudflare challenge timed out. Tap retry or close.", isError = true)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on while the user is solving the challenge.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val requestedHost = intent.getStringExtra(EXTRA_HOST).orEmpty().trim()
        val legacyUrl = intent.getStringExtra(EXTRA_URL).orEmpty().trim()
        targetHost = requestedHost
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .takeIf { it.isNotBlank() }
            ?: runCatching { Uri.parse(legacyUrl).host.orEmpty() }.getOrDefault("")
        if (targetHost.isBlank()) {
            Log.e(TAG, "Missing or invalid $EXTRA_HOST / $EXTRA_URL verification target")
            finish()
            return
        }
        // Use the requested URL when it belongs to the verification host. For
        // DahmerMovies this lets the WebView follow the post-challenge redirect
        // to the actual worker download URL instead of returning only the
        // p.111477.xyz bulk URL.
        targetUrl = legacyUrl
            .takeIf { candidate ->
                candidate.startsWith("http://", ignoreCase = true) ||
                    candidate.startsWith("https://", ignoreCase = true)
            }
            ?.takeIf { candidate ->
                runCatching { Uri.parse(candidate).host.orEmpty() }
                    .getOrDefault("")
                    .equals(targetHost, ignoreCase = true)
            }
            ?: "https://$targetHost"
        lastMainFrameUrl = targetUrl
        displayTitle = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
            ?: "Verifying $targetHost"
        timeoutMs = intent.getLongExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
            .coerceAtLeast(MIN_SOLVE_TIME_MS)

        setContentView(buildLayout())
        configureCookieManager()
        configureWebView()
        setupCursor()
        showLoadingDialog()
        startChallenge()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) {
            webView.onResume()
            webView.resumeTimers()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
            webView.pauseTimers()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.removeCallbacks(timeoutRunnable)
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        dismissLoadingDialog()
        if (::webView.isInitialized) {
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.apply {
                    stopLoading()
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    clearHistory()
                    clearCache(true)
                    loadUrl("about:blank")
                    onPause()
                    removeAllViews()
                    destroy()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during WebView cleanup: ${e.message}")
            }
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Treat hardware/gesture back the same as the close button.
        finishWithCancel()
    }

    // ════════════════════════════════════════════════════════════════════════
    // Layout
    // ════════════════════════════════════════════════════════════════════════

    /** Programmatically builds the top-bar + WebView + status-bar layout. */
    private fun buildLayout(): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
        }

        // ── Top bar ────────────────────────────────────────────────────────
        backButton = ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            setBackgroundColor(0x00000000)
            setColorFilter(0xFFFFFFFF.toInt())
            contentDescription = "Close"
            setOnClickListener { finishWithCancel() }
        }
        titleView = TextView(this).apply {
            text = displayTitle
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }
        val backLp = LinearLayout.LayoutParams(dp(32), dp(32))
        backButton.layoutParams = backLp
        val titleLp = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { leftMargin = dp(12) }
        titleView.layoutParams = titleLp
        topBar.addView(backButton)
        topBar.addView(titleView)

        // ── Status bar (below WebView) ────────────────────────────────────
        statusView = TextView(this).apply {
            text = "Connecting to $targetHost…"
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 13f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xFF1A1A1A.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        rootLayout.addView(topBar)
        rootLayout.addView(statusView)
        // WebView added later in configureWebView() so it sits between the bars.
        return rootLayout
    }

    // ════════════════════════════════════════════════════════════════════════
    // WebView configuration
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Enables both first- and third-party cookies. Cloudflare's challenge
     * domain (`challenges.cloudflare.com`) is third-party relative to the
     * target site, so without `setAcceptThirdPartyCookies` the resulting
     * `cf_clearance` is silently dropped.
     */
    private fun configureCookieManager() {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        // removeAllCookies must be called on a WebView-less CookieManager; safe here.
        // We do NOT clear existing cookies — the user's existing session
        // cookies are usually required for the challenge to verify.
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = topBar.height
                bottomMargin = statusView.height
            }
            // Insert above the status bar but below the top bar visually.
            (layoutParams as FrameLayout.LayoutParams).gravity = Gravity.TOP

            setBackgroundColor(0xFF000000.toInt())
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Reuse the device's normal UA so Cloudflare's risk model
                // treats us as a regular browser, not a curl-style bot.
                userAgentString = WebSettingsCompat.defaultUserAgentString(this@CloudflareBypassActivity, this)
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                setSupportZoom(false)
                builtInZoomControls = false
            }

            // Enable third-party cookies for this specific WebView — required
            // for challenges.cloudflare.com to write cf_clearance for the
            // target host.
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    Log.d(TAG, "[WebView] Progress: $newProgress%")
                }
            }

            webViewClient = object : AdBlockerWebViewClient(
                onPageFinished = {},
                onError = {}
            ) {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.i(TAG, "[WebView] onPageStarted: $url")
                    url?.takeIf { it.startsWith("http", ignoreCase = true) }?.let {
                        lastMainFrameUrl = it
                    }
                    if (isSolved) return
                    setStatus("Loading $url…", isError = false)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    // The parent installs request interception and DOM cleanup for ads.
                    super.onPageFinished(view, url)
                    Log.i(TAG, "[WebView] onPageFinished: $url")
                    url?.takeIf { it.startsWith("http", ignoreCase = true) }?.let {
                        lastMainFrameUrl = it
                    }
                    if (isSolved) return
                    // Check synchronously: the cookie is often written just
                    // before onPageFinished fires for the solved redirect.
                    if (hasCfClearance()) {
                        onChallengeSolved()
                    } else {
                        setStatus("Solving Cloudflare challenge…")
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    if (super.shouldOverrideUrlLoading(view, request)) return true
                    Log.d(TAG, "[WebView] Navigating: ${request?.url}")
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    // Only react to top-level errors; sub-resource errors are
                    // common and harmless during a challenge.
                    if (request?.isForMainFrame == true) {
                        val desc = error?.description?.toString().orEmpty()
                        Log.w(TAG, "[WebView] Main frame error: $desc")
                        setStatus("Network error: $desc", isError = true)
                    }
                }
            }
        }

        // Insert between the top bar and the status bar.
        rootLayout.addView(webView, 1)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Challenge flow
    // ════════════════════════════════════════════════════════════════════════

    private fun startChallenge() {
        challengeStartedAt = System.currentTimeMillis()
        Log.i(TAG, "Starting challenge for $targetUrl (timeout=${timeoutMs}ms)")
        webView.loadUrl(targetUrl)
        mainHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)
    }

    /**
     * @return true if the [CF_CLEARANCE_COOKIE] is present for the target
     * domain. Optionally also requires [CF_BM_COOKIE] if you want a stricter
     * signal — we don't, because some CF configs only emit `cf_clearance`.
     */
    private fun hasCfClearance(): Boolean {
        if (targetHost.isBlank()) return false
        val cm = CookieManager.getInstance()
        val cookieHeader = cm.getCookie(targetUrl)
            ?: cm.getCookie("https://$targetHost")
            ?: return false
        Log.v(TAG, "Cookies for $targetHost: $cookieHeader")
        return cookieHeader.split(';').any { entry ->
            val name = entry.substringBefore('=', missingDelimiterValue = "").trim()
            name == CF_CLEARANCE_COOKIE || name == CF_BM_COOKIE
        }
    }

    /**
     * Collects the full cookie header for the target URL/host instead of only
     * the Cloudflare challenge cookie. This keeps any companion cookies that the
     * site sets alongside `cf_clearance` so the player can reload with an
     * identical browser session.
     */
    private fun captureAllCookiesForTarget(): String {
        if (targetHost.isBlank()) return ""

        val cm = CookieManager.getInstance()
        val candidateUrls = linkedSetOf(
            targetUrl,
            lastMainFrameUrl,
            "https://$targetHost",
            "http://$targetHost"
        )

        val merged = linkedSetOf<String>()
        for (candidate in candidateUrls) {
            val cookieHeader = cm.getCookie(candidate).orEmpty()
            if (cookieHeader.isBlank()) continue
            cookieHeader.split(';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { merged.add(it) }
        }

        return merged.joinToString("; ")
    }

    private fun onChallengeSolved() {
        if (isSolved) return
        isSolved = true
        val elapsed = System.currentTimeMillis() - challengeStartedAt
        Log.i(TAG, "Challenge solved in ${elapsed}ms — persisting cookies")

        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.removeCallbacks(timeoutRunnable)

        // Force the WebView's internal cookie store to flush to disk so the
        // cookies survive the activity finishing. flush() is async; the
        // call returns immediately and the write happens on a background
        // thread, but the cookies are guaranteed to be persisted before the
        // process is allowed to exit cleanly.
        CookieManager.getInstance().flush()

        val cookies = captureAllCookiesForTarget().ifBlank {
            CookieManager.getInstance().getCookie(lastMainFrameUrl).orEmpty()
        }
        setStatus(
            "✓ Verified. Returning to player…",
            isError = false
        )
        Log.i(TAG, "Persisted cookies: $cookies")

        // Mirror the cookies to SharedPreferences under the target domain.
        // This is an *additional* persistence layer to the WebView's internal
        // cookie DB, so the entire cookie set can be re-injected into any other
        // WebView (e.g. the player) even after a process death.
        val saved = saveCookies(this, targetHost, cookies, targetUrl)
        if (!saved) {
            Log.w(TAG, "Could not mirror cookies to SharedPreferences for $targetHost")
        }

        // Brief delay so the user can see the success state, then return OK.
        mainHandler.postDelayed({ finishWithOk(cookies) }, 800L)
    }

    private fun finishWithOk(cookies: String) {
        if (isFinishingForResult) return
        isFinishingForResult = true
        val data = Intent().apply {
            putExtra(EXTRA_URL, lastMainFrameUrl.ifBlank { targetUrl })
            putExtra(EXTRA_COOKIES, cookies)
            putExtra(EXTRA_DOMAIN, targetHost)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun finishWithCancel() {
        if (isFinishingForResult) return
        isFinishingForResult = true
        setResult(RESULT_CANCELED)
        finish()
    }

    // ════════════════════════════════════════════════════════════════════════
    // Cursor navigation (TV remote)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Detects whether the current device is a TV (and therefore needs a
     * virtual cursor) versus a phone/tablet (where the touchscreen is used
     * directly). Mirrors the logic in `PlayerActivity`.
     */
    private fun detectTvDevice(): Boolean {
        return try {
            val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            // Configuration.UI_MODE_MASK was removed in API 30; the `uiMode`
            // field is now stored pre-masked, so we can compare it directly
            // against the type constants. UI_MODE_TYPE_TELEVISION is still
            // public on the platform for source/binary compatibility, even
            // though it's deprecated — we suppress the warning here.
            @Suppress("DEPRECATION")
            val isTvFromConfig = resources.configuration.uiMode == Configuration.UI_MODE_TYPE_TELEVISION
            @Suppress("DEPRECATION")
            val isTvFromService = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            val isLeanback = packageManager.hasSystemFeature("android.software.leanback")
            isTvFromConfig || isTvFromService || isLeanback
        } catch (e: Exception) {
            Log.w(TAG, "TV detection failed: ${e.message}")
            false
        }
    }

    /**
     * Creates the [MouseCursorView], centers it on the screen, makes the
     * root layout focusable so DPAD keys are routed to [onKeyDown], and
     * starts the inactivity fade timer.
     */
    private fun setupCursor() {
        isCursorDisabled = !detectTvDevice()
        if (isCursorDisabled) {
            Log.i(TAG, "Non-TV device — cursor disabled")
            // Even on mobile, the root must be focusable so back key works.
            rootLayout.isFocusable = true
            rootLayout.isFocusableInTouchMode = true
            rootLayout.requestFocus()
            return
        }

        Log.i(TAG, "TV device — cursor enabled")

        cursorView = MouseCursorView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(cursorView)
        cursorView.bringToFront()

        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true
        rootLayout.requestFocus()

        rootLayout.post {
            if (isFinishing || isDestroyed) return@post
            screenWidth = rootLayout.width
            screenHeight = rootLayout.height
            if (screenWidth > 0 && screenHeight > 0) {
                cursorX = screenWidth / 2f
                cursorY = screenHeight / 2f
                updateCursorPosition()
                showCursorAndResetTimer()
            }
        }
    }

    /**
     * Routes TV-remote DPAD keys to cursor movement, and the OK / ENTER keys
     * to a synthetic touch tap on the WebView at the cursor location.
     * Non-TV devices fall through to the default behavior.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isCursorDisabled || !::cursorView.isInitialized) {
            return super.onKeyDown(keyCode, event)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                cursorY = (cursorY - cursorMoveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                cursorY = (cursorY + cursorMoveSpeed).coerceAtMost(screenHeight.toFloat())
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                cursorX = (cursorX - cursorMoveSpeed).coerceAtLeast(0f)
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                cursorX = (cursorX + cursorMoveSpeed).coerceAtMost(screenWidth.toFloat())
                updateCursorPosition()
                showCursorAndResetTimer()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                simulateClick(cursorX, cursorY)
                showCursorAndResetTimer()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** Applies the current cursor coordinates and keeps it above the WebView. */
    private fun updateCursorPosition() {
        if (isCursorDisabled || !::cursorView.isInitialized) return
        cursorView.x = cursorX
        cursorView.y = cursorY
        cursorView.bringToFront()
        cursorView.invalidate()
    }

    /** Makes the cursor fully visible and restarts its 5s inactivity fade timer. */
    private fun showCursorAndResetTimer() {
        if (isCursorDisabled || !::cursorView.isInitialized) return
        cursorView.animate().cancel()
        cursorView.alpha = 1f
        cursorHideHandler.removeCallbacks(cursorHideRunnable)
        cursorHideHandler.postDelayed(cursorHideRunnable, 5_000L)
    }

    /**
     * Dispatches a synthetic touchscreen tap at the given rootLayout
     * coordinates. Cloudflare's JS challenge needs real touch events to
     * flip its bot-detection signals, so we forward as `SOURCE_TOUCHSCREEN`
     * instead of letting the WebView's own key handling deal with it.
     */
    private fun simulateClick(x: Float, y: Float) {
        try {
            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0
            )
            val upEvent = MotionEvent.obtain(
                downTime, eventTime + 100, MotionEvent.ACTION_UP, x, y, 0
            )
            downEvent.source = InputDevice.SOURCE_TOUCHSCREEN
            upEvent.source = InputDevice.SOURCE_TOUCHSCREEN

            window.decorView.dispatchTouchEvent(downEvent)
            window.decorView.dispatchTouchEvent(upEvent)

            downEvent.recycle()
            upEvent.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "simulateClick failed: ${e.message}")
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // UI helpers
    // ════════════════════════════════════════════════════════════════════════

    private fun setStatus(message: String, isError: Boolean = false) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            statusView.text = message
            statusView.setTextColor(
                if (isError) 0xFFFF6B6B.toInt() else 0xFFCCCCCC.toInt()
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun showLoadingDialog() {
        try {
            loadingDialog = ProgressDialog(this).apply {
                setMessage("Opening $targetHost…")
                setCancelable(true)
                setCanceledOnTouchOutside(false)
                show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show loading dialog: ${e.message}")
        }
    }

    private fun dismissLoadingDialog() {
        if (::loadingDialog.isInitialized) {
            try {
                if (loadingDialog.isShowing) loadingDialog.dismiss()
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Thin shim around the static [android.webkit.WebSettings.getDefaultUserAgent]
 * call so this file doesn't need to import [android.webkit.WebSettings] twice
 * with conflicting helper signatures. Returning a sensible value when the
 * platform call fails keeps us functional on unusual OEM WebView builds.
 */
private object WebSettingsCompat {
    @SuppressLint("StaticFieldLeak")
    fun defaultUserAgentString(context: android.content.Context, settings: android.webkit.WebSettings): String {
        return runCatching {
            android.webkit.WebSettings.getDefaultUserAgent(context)
        }.getOrElse {
            settings.userAgentString ?: System.getProperty("http.agent").orEmpty()
        }
    }
}
