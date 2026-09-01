package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager

import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.SeekBar
import android.widget.Toast

import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.kiduyuk.klausk.kiduyutv.data.model.SkipSegment
import com.kiduyuk.klausk.kiduyutv.data.model.SkipSegmentQuality
import com.kiduyuk.klausk.kiduyutv.data.model.SkipSegmentType
import com.kiduyuk.klausk.kiduyutv.data.model.SkipSegmentsResponse
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.model.Episode
import com.kiduyuk.klausk.kiduyutv.data.model.SeasonDetail
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.databinding.ActivityDirectStreamBinding
import com.kiduyuk.klausk.kiduyutv.ui.player.cloudflareBypass.CloudflareBypassActivity
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.SubtitleItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.SubdlSubtitleClient
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.SubdlSubtitleResult
import com.kiduyuk.klausk.kiduyutv.ui.player.webviewsniffer.SniffedSubtitle
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.PlayerEngine
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamCatalog
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamProviderChoice
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamResolver
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamSelectionDialog
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamValidator
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.TrackSelectionDialog
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import com.kiduyuk.klausk.kiduyutv.util.QuitDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.json.JSONArray

/**
 * TV-first native player for streams returned by the kiduyuTv_providers
 * (TMDB-Embed-API) server. Replaces the previous WebView-based
 * implementation: no JS injection, no ad blocker, no provider host
 * allowlist.
 *
 * Flow:
 *   1. Read the title metadata from Intent extras (type, tmdbId,
 *      season/episode, provider).
 *   2. Call [ProvidersApi.streams] via [StreamResolver] to fetch every
 *      available stream.
 *   3. Pick the highest-ranked stream (or fall back to the first) and
 *      hand it to [PlayerEngine.play].
 *   4. Map D-pad keys to native player actions: left/right ramp-seek,
 *      center play/pause, back to finish.
 */
class DirectStreamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDirectStreamBinding
    private lateinit var engine: PlayerEngine
    private lateinit var resolver: StreamResolver

    private var streamJob: Job? = null
    private var trackDialog: TrackSelectionDialog? = null
    private var streamDialog: StreamSelectionDialog? = null
    private var subtitleDialog: AlertDialog? = null
    private var noStreamsDialog: AlertDialog? = null
    private var quitDialog: QuitDialog? = null
    private var cloudflareDialog: AlertDialog? = null
    private var cloudflareProbeJob: Job? = null
    private var subtitleJob: Job? = null
    private var availableStreams: List<StreamItem> = emptyList()
    private var activeStream: StreamItem? = null
    private var activeSubtitles: List<SubtitleItem> = emptyList()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var controlsLockedVisible = false
    private var userSeeking = false
    private var remoteSeekInProgress = false
    private var resizeModeIndex = 0
    private var muted = false
    private var currentMediaType = TYPE_MOVIE
    private var currentTmdbId = 0
    private var currentSeason: Int? = null
    private var currentEpisode: Int? = null
    private var currentTitle: String = ""
    private var currentOverview: String? = null
    private var currentPosterPath: String? = null
    private var currentBackdropPath: String? = null
    private var currentVoteAverage: Double = 0.0
    private var currentReleaseDate: String? = null
    private var currentProvider: StreamProviderChoice = StreamCatalog.default
    private var currentImdbId: String? = null
    private var skipData: SkipSegmentsResponse? = null
    private var shownSkipType: SkipSegmentType? = null
    // Track which content the skipData corresponds to so we don't clear
    // the UI when a transient fetch failure happens while switching
    // streams for the same title.
    private var skipLoadedForImdbId: String? = null
    private var skipLoadedForTmdbId: Int? = null
    // Remember which segment we've already auto-skipped to avoid repeats
    private var autoSkippedSegmentStartMs: Long? = null
    private lateinit var settingsManager: com.kiduyuk.klausk.kiduyutv.util.SettingsManager
    private val repository = TmdbRepository()
    private var pendingStartPositionMs = 0L
    private var pendingReadySeekPositionMs = 0L
    private var handlingPlaybackError = false
    private var watchHistoryReady = false
    private val controlsClock = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val controlsTime = SimpleDateFormat("h:mm a", Locale.getDefault())

    /**
     * Stream that the most recent 403 dialog was offered for. Held so the
     * [cloudflareBypassLauncher] callback knows which stream to retry after
     * the CloudflareBypassActivity reports success.
     */
    private var pendingCloudflareStream: StreamItem? = null

    /**
     * Position (in ms) the user was at when the 403 was detected. The retry
     * after a successful Cloudflare bypass resumes from the same point.
     */
    private var pendingCloudflareResumeMs: Long = 0L
    private var lastLoadSignature: String? = null
    private var lastStreamPlaybackKey: String? = null
    private var lastFocusChainSignature: String? = null
    // Cached runtime (in milliseconds) for the currently loaded TV episode,
    // populated from TMDB's `tv/{tv_id}/season/{season}/episode/{episode}`
    // endpoint. Used only to keep the seek-bar UI informative while the
    // underlying player is still preparing; SkipDB requests use player.duration.
    private var cachedEpisodeDurationMs: Long? = null
    private var episodeDurationFetchJob: Job? = null
    private var skipSegmentsFetchJob: Job? = null
    private var autoSkipCountdownJob: Job? = null
    private var currentAutoSkipSegment: SkipSegment? = null

    // --- Episodes side panel ------------------------------------------
    private lateinit var episodeAdapter: EpisodeAdapter
    private var isEpisodesPanelOpen = false
    private var episodeFetchJob: Job? = null
    private var cachedSeasonDetail: SeasonDetail? = null
    private var cachedSeasonFor: Int? = null   // which season is in the cache

    /**
     * Receives the result from [CloudflareBypassActivity]. When the user
     * returns with `RESULT_OK` we re-issue playback of the stream that was
     * blocked; the new `cf_clearance` cookie is automatically picked up by
     * [PlayerEngine] via [CloudflareBypassActivity.loadCookies].
     */
    private val cloudflareBypassLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isFinishing || isDestroyed) return@registerForActivityResult
        val stream = pendingCloudflareStream
        pendingCloudflareStream = null
        val resumeMs = pendingCloudflareResumeMs
        pendingCloudflareResumeMs = 0L
        if (stream == null) {
            Log.w(TAG, "CloudflareBypass returned but no pending stream to retry")
            return@registerForActivityResult
        }
        if (result.resultCode != RESULT_OK) {
            Log.i(
                TAG,
                "CloudflareBypass cancelled by user; keeping stream selection open"
            )
            showStatus(
                getString(R.string.playback_failed_try_another_stream),
                retry = true
            )
            return@registerForActivityResult
        }
        val savedCookies = result.data?.getStringExtra(CloudflareBypassActivity.EXTRA_COOKIES)
        val domain = result.data?.getStringExtra(CloudflareBypassActivity.EXTRA_DOMAIN)
        Log.i(
            TAG,
            "CloudflareBypass solved; cookies=${savedCookies?.length ?: 0} chars " +
                "domain=$domain stream=${stream.provider} ${stream.quality} " +
                "url=${stream.url}"
        )
        Toast.makeText(
            this,
            R.string.cloudflare_bypass_retried,
            Toast.LENGTH_SHORT
        ).show()
        // The user should return to the player with the fresh cookie jar.
        // Recreate the activity so all player components rebind using the
        // newly saved Cloudflare cookies instead of continuing with stale
        // state from the prior session.
        activeStream = stream
        recreate()
    }

    private val watchProgressTick = object : Runnable {
        override fun run() {
            persistWatchProgress()
            uiHandler.postDelayed(this, WATCH_PROGRESS_INTERVAL_MS)
        }
    }

    private val skipOverlayTick = object : Runnable {
        override fun run() {
            updateSkipButton()
            uiHandler.postDelayed(this, 250L)
        }
    }

    private val progressTick = object : Runnable {
        override fun run() {
            if (::engine.isInitialized) {
                val duration = engine.player.duration.takeIf { it > 0 } ?: 0L
                val currentPosition = engine.player.currentPosition.coerceAtLeast(0L)
                binding.seekBar.setDurationMs(duration)
                if (!userSeeking) {
                    binding.seekBar.setPositionMs(currentPosition)
                    binding.tvCurrentTime.text = formatPlaybackTime(currentPosition)
                }
                binding.tvTotalTime.text = formatPlaybackTime(duration)
                binding.seekBar.secondaryProgress =
                    if (duration > 0) {
                        ((engine.player.bufferedPosition.coerceAtMost(duration) * 1000L) / duration).toInt()
                    } else {
                        0
                    }
                binding.btnPlayPause.setIconResource(
                    if (engine.player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
            val now = Date()
            binding.tvDate.text = controlsClock.format(now)
            binding.tvTime.text = controlsTime.format(now)
            uiHandler.postDelayed(this, 1_000)
        }
    }

    // D-pad left/right ramp seeking: 30s on press, repeating every 600ms
    // and ramping up to 60s after 5 seconds of holding.
    private var skipDirection = 0
    private var skipHoldStart = 0L
    private val skipRampTick = object : Runnable {
        override fun run() {
            if (skipDirection == 0) return
            val held = System.currentTimeMillis() - skipHoldStart
            val progress = (held.toFloat() / SKIP_RAMP_DURATION_MS).coerceIn(0f, 1f)
            val seconds = (SKIP_SEC_MIN +
                (SKIP_SEC_MAX - SKIP_SEC_MIN) * progress).toInt()
            val deltaMs = (if (skipDirection < 0) -seconds else seconds) * 1000L
            engine.seekBy(deltaMs)
            uiHandler.postDelayed(this, SKIP_REPEAT_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = com.kiduyuk.klausk.kiduyutv.util.SettingsManager(this)
        Log.i(TAG, "Player activity created")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityDirectStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Glide.with(this)
            .load(normalizeArtworkUrl(intent.getStringExtra(EXTRA_BACKDROP_URL)))
            .into(binding.loadingBackdrop)
        showLoadingArtwork()

        currentMediaType = intent.getStringExtra(EXTRA_TYPE)
            ?: if (intent.getBooleanExtra(EXTRA_IS_TV, false)) TYPE_SERIES else TYPE_MOVIE
        currentTmdbId = intent.getIntExtra(EXTRA_TMDB_ID, 0)
        currentSeason = intent.getIntExtra(EXTRA_SEASON, -1).takeIf { it > 0 }
        currentEpisode = intent.getIntExtra(EXTRA_EPISODE, -1).takeIf { it > 0 }
        currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        currentOverview = intent.getStringExtra(EXTRA_OVERVIEW)
        currentPosterPath = intent.getStringExtra(EXTRA_POSTER_PATH)
        currentBackdropPath = intent.getStringExtra(EXTRA_BACKDROP_URL)
        currentVoteAverage = intent.getDoubleExtra(EXTRA_VOTE_AVERAGE, 0.0)
        currentReleaseDate = intent.getStringExtra(EXTRA_RELEASE_DATE)
        currentProvider = StreamCatalog.resolve(intent.getStringExtra(EXTRA_PROVIDER))
        currentImdbId = intent.getStringExtra(EXTRA_IMDB_ID)?.takeIf { it.isNotBlank() }
        updatePlayerTitle()

        Log.i(
            PROVIDER_TAG,
            "Player opened type=$currentMediaType tmdbId=$currentTmdbId " +
                "season=${currentSeason ?: "-"} episode=${currentEpisode ?: "-"} " +
                "provider=${currentProvider.displayName} key=${currentProvider.key.ifEmpty { "<aggregate>" }}"
        )

        if (currentTmdbId <= 0) {
            Toast.makeText(this, R.string.playback_link_unavailable, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        resolver = StreamResolver()
        engine = PlayerEngine(this).apply {
            onError = { code -> handlePlaybackError(code) }
            onPlaybackStateChanged = { state ->
                when (state) {
                    Player.STATE_BUFFERING -> {
                        showLoadingArtwork()
                        showStatus(getString(R.string.buffering), retry = false)
                    }
                    Player.STATE_READY -> {
                        binding.playerStatus.visibility = View.GONE
                        loadSkipSegments()
                        applyPendingReadySeek()
                        startWatchProgressUpdates()
                    }
                    Player.STATE_ENDED -> {
                        stopWatchProgressUpdates()
                        binding.playerStatus.visibility = View.GONE
                        if (currentMediaType == TYPE_SERIES && currentEpisode != null) {
                            Log.i(
                                TAG,
                                "Episode playback ended; loading the next episode automatically"
                            )
                            loadAdjacentEpisode(1)
                        } else {
                            persistWatchProgress()
                        }
                    }
                    // Preserve "Loading streams" and retry messages while
                    // Media3 is idle; IDLE does not mean the request failed.
                    Player.STATE_IDLE -> Unit
                }
            }
            onIsPlayingChanged = { isPlaying ->
                if (isPlaying) hideLoadingArtwork()
            }
            onTracksChanged = { tracks ->
                updateTracksButton(tracks)
                trackDialog?.updateCurrentTracks(tracks)
            }
        }
        binding.playerView.player = engine.player
        binding.playerView.subtitleView?.apply {
            visibility = View.VISIBLE
            setApplyEmbeddedStyles(true)
            setApplyEmbeddedFontSizes(true)
        }
        // The Media3 default settings cog is left in place: in Media3 1.4.1
        // the PlayerView has no public setShowSettingsButton (it was added
        // in a later release). Our custom Tracks button (btnPlayerTracks) is
        // in a different visual position — top-right of the activity
        // chrome vs top-right of the in-player control bar — so the two
        // don't overlap. The subtitle button is hidden via the layout's
        // app:show_subtitle_button="false" because we don't render
        // subtitle tracks via the Media3 overlay.
        binding.btnPlayerBack.setOnClickListener { showExitConfirmationDialog() }
        binding.btnSkipSegment.setOnClickListener { onSkipClicked() }
        binding.btnPlayerTracks.setOnClickListener { showTrackDialog() }
        binding.btnPlayerStreams.setOnClickListener { showStreamDialog() }
        binding.btnPlayerSubtitles.setOnClickListener { searchSubdlSubtitles() }
        binding.playerView.setOnClickListener { showControls() }
        binding.overlayControls.setOnClickListener { showControls() }
        binding.btnRewind.setOnClickListener { engine.seekBy(-30_000L); showControls() }
        binding.btnForward.setOnClickListener { engine.seekBy(30_000L); showControls() }
        binding.btnPlayPause.setOnClickListener {
            if (engine.player.isPlaying) engine.pause() else engine.resume()
            showControls()
        }
        binding.btnFill.setOnClickListener {
            resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
            applyResizeMode()
            showControls()
        }
        binding.btnVolume.setOnClickListener {
            muted = !muted
            engine.player.volume = if (muted) 0f else 1f
            binding.btnVolume.setIconResource(if (muted) R.drawable.ic_vol_mute else R.drawable.ic_vol)
            showControls()
        }
        binding.btnPreviousEpisode.setOnClickListener { loadAdjacentEpisode(-1) }
        binding.btnNextEpisode.setOnClickListener { loadAdjacentEpisode(1) }
        updateEpisodeButtons()

        // ── Episodes side panel ────────────────────────────────────────────
        episodeAdapter = EpisodeAdapter { episode -> onEpisodeSelected(episode) }
        binding.rvEpisodes.layoutManager = LinearLayoutManager(this)
        binding.rvEpisodes.adapter = episodeAdapter
        episodeAdapter.setCurrentlyPlaying(currentEpisode)

        binding.btnEpisodes.setOnClickListener { toggleEpisodesPanel() }
        binding.btnCloseEpisodesPanel.setOnClickListener { closeEpisodesPanel() }
        binding.episodesPanelScrim.setOnClickListener { closeEpisodesPanel() }

        // Pre-position the panel off-screen; openEpisodesPanel() will animate it in.
        binding.episodesPanelContainer.translationX = -binding.episodesPanelContainer.width.toFloat()
        binding.episodesPanelContainer.visibility = View.GONE

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = engine.player.duration
                if (duration > 0L) {
                    binding.tvCurrentTime.text =
                        formatPlaybackTime((duration * progress) / 1000L)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                binding.seekBar.setDurationMs(engine.player.duration)
                userSeeking = true
                controlsLockedVisible = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = engine.player.duration
                if (duration > 0L) {
                    engine.player.seekTo(binding.seekBar.getPositionMsFromProgress())
                }
                userSeeking = false
                controlsLockedVisible = false
                showControls()
            }
        })
        binding.seekBar.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> handleSeekBarRemoteKey(event, -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> handleSeekBarRemoteKey(event, 1)
                else -> false
            }
        }
        binding.seekBar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) completeRemoteSeek()
        }
        updateBottomFocusChain()
        showControls()
        uiHandler.post(progressTick)
        uiHandler.post(skipOverlayTick)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEpisodesPanelOpen) {
                    closeEpisodesPanel()
                    return
                }
                showExitConfirmationDialog()
            }
        })

        // Start the TMDB episode-runtime lookup for series content so the
        // canonical duration is ready by the time `loadSkipSegments` runs
        // (or as soon as the player reports its first duration).
        fetchEpisodeDurationFromTmdb()

        checkAndAddToWatchHistory()
    }

    private fun playSniffedStream(url: String) {
        val headers = linkedMapOf<String, String>()
        intent.getStringExtra(EXTRA_SNIFFED_HEADERS)?.let { encoded ->
            runCatching {
                val json = JSONObject(encoded)
                json.keys().forEach { key ->
                    json.optString(key).takeIf { it.isNotBlank() }?.let { headers[key] = it }
                }
            }.onFailure { Log.w(TAG, "Could not parse sniffed request headers", it) }
        }
        intent.getStringExtra(EXTRA_SNIFFED_COOKIE)
            ?.takeIf { it.isNotBlank() && headers.keys.none { key -> key.equals("Cookie", true) } }
            ?.let { headers["Cookie"] = it }

        val stream = StreamItem(
            name = "Web Sniffer",
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Captured WebView stream" },
            url = url,
            quality = "Auto",
            provider = "WebSniffer",
            type = intent.getStringExtra(EXTRA_SNIFFED_TYPE).orEmpty(),
            mimeType = intent.getStringExtra(EXTRA_SNIFFED_MIME_TYPE).orEmpty(),
            headers = headers
        )
        availableStreams = listOf(stream)
        activeStream = stream
        showStatus(getString(R.string.buffering), retry = false)
        activeSubtitles = parseSniffedSubtitles()
        // A non-zero seek while Media3 is still resolving a sniffed video's
        // external subtitle timelines can trigger ERROR_CODE_FAILED_RUNTIME_CHECK.
        // Prepare the merged source first, then restore progress at STATE_READY.
        pendingReadySeekPositionMs = consumePendingStartPosition()
        engine.play(stream, 0L, activeSubtitles)
    }

    private fun parseSniffedSubtitles(): List<SubtitleItem> {
        val encoded = intent.getStringExtra(EXTRA_SNIFFED_SUBTITLES) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    val mimeType = item.optString("mimeType")
                    if (url.isBlank() || mimeType.isBlank()) continue
                    val headers = linkedMapOf<String, String>()
                    item.optJSONObject("headers")?.let { json ->
                        json.keys().forEach { key ->
                            json.optString(key).takeIf { it.isNotBlank() }?.let { headers[key] = it }
                        }
                    }
                    item.optString("cookie")
                        .takeIf {
                            it.isNotBlank() &&
                                headers.keys.none { key -> key.equals("Cookie", ignoreCase = true) }
                        }
                        ?.let { headers["Cookie"] = it }
                    add(
                        SubtitleItem(
                            url = url,
                            mimeType = mimeType,
                            label = "Subtitle ${index + 1}",
                            headers = headers
                        )
                    )
                }
            }
        }.onFailure {
            Log.w(TAG, "Could not parse sniffed subtitles", it)
        }.getOrDefault(emptyList())
    }

    private fun applyResizeMode() {
        val mode = resizeModes[resizeModeIndex]
        binding.playerView.resizeMode = mode.resizeMode
        // Some decoders update the SurfaceView dimensions independently
        // after reporting a new video size. Explicitly relayout both levels
        // so the selected mode also takes effect for those streams.
        binding.playerView.requestLayout()
        binding.playerView.videoSurfaceView?.apply {
            requestLayout()
            invalidate()
        }
        Toast.makeText(this, mode.label, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Player resize mode changed to ${getString(mode.label)}")
    }

    private fun formatPlaybackTime(positionMs: Long): String {
        val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun loadAdjacentEpisode(delta: Int) {
        if (currentMediaType != TYPE_SERIES) return
        val episode = currentEpisode ?: return
        val nextEpisode = episode + delta
        if (nextEpisode < 1) return

        currentEpisode = nextEpisode
        pendingStartPositionMs = 0L
        // Clear the previously cached TMDB runtime so the next fetch
        // populates the duration for the new episode.
        cachedEpisodeDurationMs = null
        episodeDurationFetchJob?.cancel()
        currentTitle = currentTitle.substringBefore(" • ")
        updatePlayerTitle()
        updateEpisodeButtons()
        if (::episodeAdapter.isInitialized) {
            episodeAdapter.setCurrentlyPlaying(nextEpisode)
        }
        trackDialog?.takeIf { it.isShowing }?.dismiss()
        streamDialog?.takeIf { it.isShowing }?.dismiss()
        engine.pause()
        showLoadingArtwork()
        Log.i(
            PROVIDER_TAG,
            "Loading adjacent episode season=$currentSeason episode=$nextEpisode delta=$delta"
        )
        resetWatchProgressForCurrentEpisode()
        // Kick off the TMDB episode-runtime lookup for the new episode.
        fetchEpisodeDurationFromTmdb()
        loadCurrentMedia()
        showControls()
    }

    private fun updateEpisodeButtons() {
        val isSeries = currentMediaType == TYPE_SERIES && currentEpisode != null
        binding.btnNextEpisode.visibility = if (isSeries) View.VISIBLE else View.GONE
        binding.btnPreviousEpisode.visibility =
            if (isSeries && (currentEpisode ?: 1) > 1) View.VISIBLE else View.GONE
        binding.btnEpisodes.visibility = if (isSeries) View.VISIBLE else View.GONE
        updateBottomFocusChain()
    }

    private fun updatePlayerTitle() {
        val hasEpisodeNumber = Regex("""(?i)\bS\d+\s*E\d+\b""").containsMatchIn(currentTitle)
        binding.tvPlayerTitle.text = if (
            currentMediaType == TYPE_SERIES &&
            currentSeason != null &&
            currentEpisode != null &&
            !hasEpisodeNumber
        ) {
            "$currentTitle • S${currentSeason} E${currentEpisode}"
        } else {
            currentTitle
        }
    }

    private fun mediaLoadSignature(
        type: String,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        provider: StreamProviderChoice
    ): String = "$type|$tmdbId|${season ?: 0}|${episode ?: 0}|${provider.key}"

    /**
     * Kicks off an asynchronous lookup of the currently loaded TV episode's
     * runtime from TMDB's `tv/{tv_id}/season/{season}/episode/{episode}`
     * endpoint. The result (in milliseconds) is stored in
     * [cachedEpisodeDurationMs] for the seek-bar UI while the underlying
     * player is preparing. SkipDB requests wait for the player duration
     * instead. No-op for non-series content.
     */
    private fun fetchEpisodeDurationFromTmdb() {
        if (currentMediaType != TYPE_SERIES) return
        val tvId = currentTmdbId.takeIf { it > 0 } ?: return
        val season = currentSeason ?: return
        val episode = currentEpisode ?: return
        // Skip if we already have a positive cached value for the same episode.
        if (cachedEpisodeDurationMs != null && cachedEpisodeDurationMs!! > 0L) return
        episodeDurationFetchJob?.cancel()
        episodeDurationFetchJob = lifecycleScope.launch {
            val minutes = repository.getEpisodeRuntimeMinutes(tvId, season, episode)
            val ms = minutes?.takeIf { it > 0 }?.toLong()?.times(60_000L)
            if (ms != null) {
                cachedEpisodeDurationMs = ms
                Log.i(
                    TAG,
                    "TMDB episode runtime resolved: tvId=$tvId S${season}E${episode} " +
                        "${minutes}min -> ${ms}ms"
                )
                // Push the resolved duration into the seek bar so the UI shows
                // a meaningful total length even before the player reports it.
                if (::engine.isInitialized) {
                    val playerDuration = engine.player.duration.takeIf { it > 0L }
                    if (playerDuration == null) {
                        binding.seekBar.setDurationMs(ms)
                        binding.tvTotalTime.text = formatPlaybackTime(ms)
                    }
                }
            } else {
                Log.w(
                    TAG,
                    "TMDB did not return a runtime for tvId=$tvId S${season}E${episode}"
                )
            }
        }
    }

    /**
     * Suspends until Media3 reports a positive duration for the current item.
     * A duration of zero or `TIME_UNSET` is not valid for SkipDB matching.
     */
    private suspend fun awaitPlayerDuration(): Long {
        while (true) {
            val durationMs = engine.player.duration.takeIf { it > 0L }
            if (durationMs != null) return durationMs
            delay(SKIP_DURATION_POLL_INTERVAL_MS)
        }
    }

    private fun loadSkipSegments() {
        skipSegmentsFetchJob?.cancel()

        // Capture the media identity before waiting so a delayed response can
        // never be applied to a different episode or title.
        val requestImdb = currentImdbId
        val requestTmdb = currentTmdbId
        val requestMediaType = currentMediaType
        val requestSeason = currentSeason
        val requestEpisode = currentEpisode

        skipSegmentsFetchJob = lifecycleScope.launch {
            // Do not send SkipDB a null/zero duration. The player may report
            // STATE_READY before its timeline duration is populated.
            val durationMs = awaitPlayerDuration()

            if (
                requestImdb != currentImdbId ||
                requestTmdb != currentTmdbId ||
                requestMediaType != currentMediaType ||
                requestSeason != currentSeason ||
                requestEpisode != currentEpisode
            ) {
                return@launch
            }

            val result: SkipSegmentsResponse? = when {
                !requestImdb.isNullOrBlank() -> {
                    repository.fetchSkipSegments(
                        imdbId = requestImdb,
                        season = if (requestMediaType == TYPE_SERIES) requestSeason else null,
                        episode = if (requestMediaType == TYPE_SERIES) requestEpisode else null,
                        streamDurationMs = durationMs
                    )
                }
                requestMediaType == TYPE_SERIES && requestTmdb > 0 -> {
                    repository.fetchSkipSegmentsByTmdb(
                        tvId = requestTmdb,
                        season = requestSeason,
                        episode = requestEpisode,
                        streamDurationMs = durationMs
                    )
                }
                else -> null
            }

            // Ignore a response that completed after the activity moved to a
            // different media item.
            if (
                requestImdb != currentImdbId ||
                requestTmdb != currentTmdbId ||
                requestMediaType != currentMediaType ||
                requestSeason != currentSeason ||
                requestEpisode != currentEpisode
            ) {
                return@launch
            }

            // If we successfully fetched segments, record which content
            // they belong to and update the UI. If the fetch failed but
            // the previously-loaded segments belong to the same content,
            // keep them rather than clearing the UI (avoids blink when
            // switching streams). Only clear when we truly don't have
            // segments for this content.
            if (result != null) {
                skipData = result
                skipLoadedForImdbId = requestImdb
                skipLoadedForTmdbId = requestTmdb
                // Draw every valid intro/recap/outro/preview interval once
                // the SkipDB response arrives. The custom SeekBar keeps these
                // colors visible while playback continues.
                binding.seekBar.setSegments(result.segments)
            } else {
                val sameContent = (skipLoadedForImdbId != null && skipLoadedForImdbId == requestImdb) ||
                    (skipLoadedForTmdbId != null && skipLoadedForTmdbId == requestTmdb && requestImdb.isNullOrBlank())
                if (!sameContent) {
                    skipData = null
                    skipLoadedForImdbId = null
                    skipLoadedForTmdbId = null
                    binding.seekBar.clearHighlights()
                    hideSkipButton()
                }
                // else: keep existing skipData for the same content
            }
        }
    }

    private fun updateSkipButton() {
        val data = skipData ?: run {
            binding.seekBar.clearHighlights()
            return
        }
        val duration = engine.player.duration
        if (duration > 0L) binding.seekBar.setDurationMs(duration)
        val positionMs = engine.player.currentPosition.coerceAtLeast(0L)
        val candidates = listOf(
            SkipSegmentType.RECAP to data.segments.recap,
            SkipSegmentType.INTRO to data.segments.intro,
            SkipSegmentType.OUTRO to data.segments.outro,
            SkipSegmentType.PREVIEW to data.segments.preview
        ).mapNotNull { (type, segment) ->
            val resolved = segment ?: return@mapNotNull null
            if (!SkipSegmentQuality.isUsable(resolved)) return@mapNotNull null
            type to resolved
        }

        val active = candidates.firstOrNull { (_, segment) -> isSkipActive(segment, positionMs) }

        if (active == null) {
            cancelAutoSkipCountdown()
            if (shownSkipType != null) hideSkipButton()
            return
        }

        // Auto-skip uses only the snackbar countdown; the manual skip button
        // remains hidden so the player UI presents one clear action.
        if (settingsManager.isAutoSkipSegmentsEnabled()) {
            hideSkipButton()
            val (type, segment) = active
            if (autoSkippedSegmentStartMs != segment.startMs) {
                if (engine.player.isPlaying) {
                    startAutoSkipCountdown(type, segment)
                } else {
                    cancelAutoSkipCountdown()
                }
            } else {
                cancelAutoSkipCountdown()
            }
            return
        }

        cancelAutoSkipCountdown()
        // Reset auto-skip tracker when auto-skip is disabled.
        autoSkippedSegmentStartMs = null

        val (type, segment) = active
        if (shownSkipType == type) {
            binding.btnSkipSegment.isEnabled = true
            binding.btnSkipSegment.alpha = 1f
            return
        }
        showSkipButton(type, segment)
        binding.btnSkipSegment.isEnabled = true
        binding.btnSkipSegment.alpha = 1f
    }


    private fun isSkipActive(segment: SkipSegment?, positionMs: Long): Boolean {
        if (segment == null) return false
        val startMs = segment.startMs
        val endMs = segment.endMs ?: (startMs + 5 * 60_000L)
        return positionMs in (startMs - 2_000L)..endMs
    }

    private fun showSkipButton(type: SkipSegmentType, segment: SkipSegment) {
        shownSkipType = type
        val label = when (type) {
            SkipSegmentType.INTRO -> getString(R.string.skip_intro)
            SkipSegmentType.RECAP -> getString(R.string.skip_recap)
            SkipSegmentType.OUTRO -> getString(R.string.skip_outro)
            SkipSegmentType.PREVIEW -> getString(R.string.skip_outro)
        }
        val durationSec = ((segment.endMs ?: segment.startMs) - segment.startMs).coerceAtLeast(0L) / 1000L
        binding.btnSkipSegment.text = if (durationSec > 0L) "$label • ${durationSec}s" else label
        binding.btnSkipSegment.isEnabled = true
        binding.btnSkipSegment.alpha = 1f
        binding.skipOverlayContainer.visibility = View.VISIBLE
        binding.btnSkipSegment.requestFocus()
    }

    private fun hideSkipButton() {
        if (shownSkipType == null && binding.skipOverlayContainer.visibility != View.VISIBLE) return
        shownSkipType = null
        binding.btnSkipSegment.isEnabled = false
        binding.btnSkipSegment.alpha = 0f
        binding.skipOverlayContainer.visibility = View.GONE
    }

    private fun startAutoSkipCountdown(type: SkipSegmentType, segment: SkipSegment) {
        if (autoSkipCountdownJob?.isActive == true && currentAutoSkipSegment == segment) return

        cancelAutoSkipCountdown()
        currentAutoSkipSegment = segment

        autoSkipCountdownJob = lifecycleScope.launch {
            val label = when (type) {
                SkipSegmentType.INTRO -> "Intro"
                SkipSegmentType.RECAP -> "Recap"
                SkipSegmentType.OUTRO -> "Outro"
                SkipSegmentType.PREVIEW -> "Preview"
            }

            binding.tvAutoSkipCountdown.visibility = View.VISIBLE
            for (i in 5 downTo 1) {
                binding.tvAutoSkipCountdown.text = "Skipping $label in $i..."
                delay(1000L)
            }

            // Perform skip
            val segmentEnd = segment.endMs ?: (segment.startMs + 5 * 60_000L)
            autoSkippedSegmentStartMs = segment.startMs
            engine.player.seekTo(segmentEnd)

            binding.tvAutoSkipCountdown.visibility = View.GONE
            autoSkipCountdownJob = null
            currentAutoSkipSegment = null
            hideSkipButton()
        }
    }

    private fun cancelAutoSkipCountdown() {
        autoSkipCountdownJob?.cancel()
        autoSkipCountdownJob = null
        currentAutoSkipSegment = null
        binding.tvAutoSkipCountdown.visibility = View.GONE
    }

    private fun onSkipClicked() {
        val type = shownSkipType ?: return
        val segment = skipData?.segments?.get(type) ?: return
        val targetMs = segment.endMs ?: (segment.startMs + 5 * 60_000L)
        cancelAutoSkipCountdown()
        engine.player.seekTo(targetMs)
        hideSkipButton()
    }

    private fun loadCurrentMedia() {
        val signature = mediaLoadSignature(
            type = currentMediaType,
            tmdbId = currentTmdbId,
            season = currentSeason,
            episode = currentEpisode,
            provider = currentProvider
        )
        if (lastLoadSignature == signature && streamJob?.isActive == true) return
        lastLoadSignature = signature
        loadAndPlay(
            currentMediaType,
            currentTmdbId,
            currentSeason,
            currentEpisode,
            currentProvider
        )
    }

    /**
     * Show the custom tracks button only when the manifest exposes at
     * least one track the user can switch to. HLS playlists with a single
     * video track and a single audio track will leave the button hidden.
     */
    private fun updateTracksButton(tracks: Tracks) {
        val hasVideoChoices = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.length > 1 }
        val hasAudioChoices = tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 1 }
        // A single subtitle track is still a real choice because the dialog
        // also provides an explicit Off row.
        val hasSubtitleChoices = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
        val anyChoice = hasVideoChoices || hasAudioChoices || hasSubtitleChoices
        binding.btnPlayerTracks.visibility = if (anyChoice) View.VISIBLE else View.GONE
        updateBottomFocusChain()
        Log.i(
            TAG,
            "Tracks button visibility=$anyChoice " +
                "(video=$hasVideoChoices audio=$hasAudioChoices subtitle=$hasSubtitleChoices)"
        )
    }

    private fun showTrackDialog() {
        // Don't stack two dialogs.
        if (trackDialog?.isShowing == true) return
        val tracks = engine.currentTracks()
        if (tracks.groups.isEmpty()) {
            Toast.makeText(this, R.string.track_none_available, Toast.LENGTH_SHORT).show()
            return
        }
        trackDialog = TrackSelectionDialog(
            context = this,
            tracks = tracks,
            initialParameters = engine.currentTrackSelectionParameters(),
            onApply = { params -> engine.applyTrackSelectionParameters(params) }
        )
        trackDialog?.setOnDismissListener { trackDialog = null }
        trackDialog?.show()
    }

    private fun showStreamDialog() {
        if (streamDialog?.isShowing == true || availableStreams.size < 2) return
        streamDialog = StreamSelectionDialog(
            context = this,
            streams = availableStreams,
            activeUrl = activeStream?.url,
            onStreamSelected = ::switchStream
        )
        streamDialog?.setOnDismissListener { streamDialog = null }
        streamDialog?.show()
    }

    private fun searchSubdlSubtitles() {
        if (subtitleJob?.isActive == true || subtitleDialog?.isShowing == true) return
        val client = SubdlSubtitleClient(applicationContext)
        if (!client.isConfigured) {
            Toast.makeText(this, R.string.subdl_key_missing, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, R.string.subdl_searching, Toast.LENGTH_SHORT).show()
        subtitleJob = lifecycleScope.launch {
            runCatching {
                client.search(
                    tmdbId = currentTmdbId,
                    isTv = currentMediaType == TYPE_SERIES,
                    season = currentSeason,
                    episode = currentEpisode
                )
            }.onSuccess { results ->
                Log.i(TAG, "SubDL search returned ${results.size} selectable subtitles")
                if (results.isEmpty()) {
                    Toast.makeText(
                        this@DirectStreamActivity,
                        R.string.subdl_no_results,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    showSubdlResults(results, client)
                }
            }.onFailure { error ->
                Log.e(TAG, "SubDL subtitle search failed", error)
                Toast.makeText(
                    this@DirectStreamActivity,
                    getString(
                        R.string.subdl_search_failed,
                        error.message ?: error.javaClass.simpleName
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showSubdlResults(
        results: List<SubdlSubtitleResult>,
        client: SubdlSubtitleClient
    ) {
        val labels = results.map { it.displayName }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.subdl_choose)
            .setIcon(R.drawable.ic_closed_caption)
            .setSingleChoiceItems(labels, -1) { chooser, index ->
                chooser.dismiss()
                downloadAndLoadSubtitle(results[index], client)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        subtitleDialog = dialog
        dialog.setOnDismissListener { subtitleDialog = null }
        dialog.show()
    }

    private fun downloadAndLoadSubtitle(
        result: SubdlSubtitleResult,
        client: SubdlSubtitleClient
    ) {
        subtitleJob?.cancel()
        Toast.makeText(this, R.string.subdl_downloading, Toast.LENGTH_SHORT).show()
        subtitleJob = lifecycleScope.launch {
            runCatching { client.download(result) }
                .onSuccess { subtitle ->
                    loadExternalSubtitle(subtitle)
                    Toast.makeText(
                        this@DirectStreamActivity,
                        getString(R.string.subdl_loaded, result.language.ifBlank { "SubDL" }),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure { error ->
                    Log.e(TAG, "SubDL subtitle download failed", error)
                    Toast.makeText(
                        this@DirectStreamActivity,
                        getString(
                            R.string.subdl_download_failed,
                            error.message ?: error.javaClass.simpleName
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun loadExternalSubtitle(subtitle: SubtitleItem) {
        val stream = activeStream ?: run {
            Toast.makeText(this, R.string.playback_link_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val positionMs = engine.player.currentPosition.coerceAtLeast(0L)
        activeSubtitles = listOf(subtitle) + activeSubtitles.filterNot {
            it.label?.startsWith("SubDL", ignoreCase = true) == true
        }
        Log.i(
            TAG,
            "Loading external subtitle label=${subtitle.label.orEmpty()} " +
                "language=${subtitle.language.orEmpty()} mimeType=${subtitle.mimeType}"
        )
        pendingReadySeekPositionMs = positionMs
        showStatus(getString(R.string.buffering), retry = false)
        engine.play(stream, 0L, activeSubtitles)
    }

    private fun switchStream(stream: StreamItem) {
        if (stream.url == activeStream?.url) return
        val positionMs = engine.player.currentPosition.coerceAtLeast(0L)
        activeStream = stream
        Log.i(
            PROVIDER_TAG,
            "Switching stream provider=${stream.provider.ifBlank { "?" }} " +
                "quality=${stream.quality} positionMs=$positionMs"
        )
        startStreamPlayback(stream, positionMs)
    }

    private fun handlePlaybackError(code: String) {
        if (handlingPlaybackError || isFinishing || isDestroyed) return
        handlingPlaybackError = true
        Log.e(
            TAG,
            "Playback failed code=$code; keeping DirectStreamActivity open for stream selection"
        )
        stopWatchProgressUpdates()
        engine.pause()
        hideLoadingArtwork()
        binding.playerStatus.visibility = View.GONE
        showControls()
        // The ExoPlayer error name doesn't carry the underlying HTTP
        // status code, but Cloudflare-style 403 challenges are by far the
        // most common reason playback fails after a successful stream
        // load. Probe the active stream and, if it's actually a 403, swap
        // the generic "Playback failed" toast for the bypass dialog so
        // the user can solve the challenge instead of picking a
        // (probably identical) alternative source.
        val active = activeStream
        if (active != null) {
            cloudflareProbeJob?.cancel()
            cloudflareProbeJob = lifecycleScope.launch {
                Log.w(TAG, "Playback error for active stream; url=${active.url} provider=${active.provider} quality=${active.quality}")
                val statusCode = withContext(Dispatchers.IO) {
                    StreamValidator.probeStatus(active)
                }
                if (isFinishing || isDestroyed) return@launch
                if (
                    statusCode == 403 &&
                    cloudflareDialog?.isShowing != true &&
                    CloudflareBypassActivity.loadCookies(
                        applicationContext,
                        runCatching { Uri.parse(active.url).host.orEmpty() }
                            .getOrNull().orEmpty()
                    ).isNullOrBlank()
                ) {
                    Log.w(
                        TAG,
                        "Playback error mapped to HTTP 403; offering Cloudflare bypass"
                    )
                    val resumeMs = engine.player.currentPosition.coerceAtLeast(0L)
                    showCloudflareBypassDialog(active, resumeMs)
                    return@launch
                }
                if (binding.btnPlayerStreams.visibility == View.VISIBLE) {
                    binding.btnPlayerStreams.requestFocus()
                }
                Toast.makeText(
                    this@DirectStreamActivity,
                    R.string.playback_failed_try_another_stream,
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        if (binding.btnPlayerStreams.visibility == View.VISIBLE) {
            binding.btnPlayerStreams.requestFocus()
        }
        Toast.makeText(
            this,
            R.string.playback_failed_try_another_stream,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun loadAndPlay(
        type: String,
        tmdbId: Int,
        season: Int?,
        episode: Int?,
        provider: StreamProviderChoice
    ) {
        streamJob?.cancel()
        skipSegmentsFetchJob?.cancel()
        noStreamsDialog?.takeIf { it.isShowing }?.dismiss()
        noStreamsDialog = null
        availableStreams = emptyList()
        activeStream = null
        activeSubtitles = emptyList()
        pendingReadySeekPositionMs = 0L
        handlingPlaybackError = false
        binding.btnPlayerStreams.visibility = View.GONE
        updateBottomFocusChain()
        showStatus("Fetching enabled providers", retry = false)
        streamJob = lifecycleScope.launch {
            val result = runCatching {
                resolver.load(
                    type = type,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode,
                    provider = provider
                ) { index, total, providerName ->
                    withContext(Dispatchers.Main) {
                        showStatus(
                            "Provider $index/$total enabled providers: $providerName\nfetching streams",
                            retry = false
                        )
                    }
                }
            }
            result.onSuccess { items ->
                Log.i(PROVIDER_TAG, "loadAndPlay received ${items.size} streams for provider=${provider.displayName}")
                if (items.isEmpty()) {
                    Log.w(PROVIDER_TAG, "Empty stream list for provider=${provider.displayName}")
                    showStatus(getString(R.string.streams_empty), retry = false)
                    showNoStreamsDialog()
                } else {
                    availableStreams = items
                    binding.btnPlayerStreams.visibility =
                        if (items.size > 1) View.VISIBLE else View.GONE
                    updateBottomFocusChain()
                    binding.playerStatus.visibility = View.GONE
                    playBest(items)
                    validateStreamsInBackground(items)
                }
            }.onFailure { error ->
                Log.w(TAG, "Stream fetch failed: ${error.message}")
                Log.w(PROVIDER_TAG, "Stream fetch failed for provider=${provider.displayName}: ${error.message}")
                showStatus(getString(R.string.streams_failed), retry = false)
                showNoStreamsDialog()
            }
        }
    }

    private fun showNoStreamsDialog() {
        if (isFinishing || isDestroyed || noStreamsDialog?.isShowing == true) return

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.no_streams_dialog_title)
            .setMessage(R.string.no_streams_dialog_message)
            .setCancelable(false)
            .setPositiveButton(R.string.no_streams_retry) { currentDialog, _ ->
                currentDialog.dismiss()
                loadCurrentMedia()
            }
            .setNegativeButton(R.string.no_streams_exit) { currentDialog, _ ->
                currentDialog.dismiss()
                finish()
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
        }
        dialog.setOnDismissListener {
            if (noStreamsDialog === dialog) noStreamsDialog = null
        }
        noStreamsDialog = dialog
        dialog.show()
    }

    /**
     * Probe every loaded [StreamItem] in the background to confirm the
     * upstream CDN is reachable. Each result is written back to the item
     * (`isValid`, `isChecking`, `httpStatusCode`) and the open
     * [StreamSelectionDialog] — if any — is refreshed so the "stream ok"
     * badge can render.
     *
     * If the *currently playing* stream turns out to be a 403 (typically
     * because the user picked a stream that was already 403'ing at fetch
     * time, and the pre-flight 403 check raced the actual playback), the
     * user is offered the Cloudflare bypass via [offerCloudflareBypass].
     */
    private fun validateStreamsInBackground(items: List<StreamItem>) {
        lifecycleScope.launch {
            val validated = StreamValidator.validateAll(items)
            withContext(Dispatchers.Main) {
                availableStreams = validated
                streamDialog?.updateStreams(validated)
                val okCount = validated.count { it.isValid }
                val blockedCount = validated.count { it.httpStatusCode == 403 }
                Log.i(
                    PROVIDER_TAG,
                    "stream ok: $okCount/${validated.size} candidates validated " +
                        "(cloudflare-blocked=$blockedCount)"
                )
                val active = activeStream
                if (
                    active != null &&
                    !handlingPlaybackError &&
                    !engine.player.isPlaying &&
                    cloudflareDialog?.isShowing != true &&
                    active.httpStatusCode == 403 &&
                    CloudflareBypassActivity.loadCookies(
                        applicationContext,
                        runCatching { Uri.parse(active.url).host.orEmpty() }
                            .getOrNull().orEmpty()
                    ).isNullOrBlank()
                ) {
                    Log.w(
                        TAG,
                        "Background validation flagged active stream as 403; " +
                            "offering Cloudflare bypass"
                    )
                    val resumeMs = engine.player.currentPosition.coerceAtLeast(0L)
                    showCloudflareBypassDialog(active, resumeMs)
                }
            }
        }
    }

    /**
     * Automatically picks the best stream up to 1080p. Higher-bandwidth
     * 1440p/2160p streams remain available in the Streams dialog so the
     * viewer can opt into them explicitly.
     *
     * Before calling [startStreamPlayback] the chosen stream is probed
     * synchronously. A 403 response (typically a Cloudflare "Verify you
     * are human" challenge) is offered to the user as a Cloudflare bypass
     * flow instead of just failing playback.
     */
    private fun playBest(items: List<StreamItem>) {
        val automaticCandidates = items.filterNot {
            (qualityResolution(it.quality) ?: 0) >= 1440
        }
        val chosen = automaticCandidates
            .ifEmpty { items }
            .maxByOrNull { qualityRank(it.quality) }
            ?: items.first()
        activeStream = chosen
        val scheme = chosen.url.substringBefore(':').uppercase()
        Log.i(
            PROVIDER_TAG,
            "playBest picked provider=${chosen.provider.ifBlank { "?" }} " +
                "quality=${chosen.quality} scheme=$scheme " +
                "excludedHighResolution=${items.size - automaticCandidates.size} url=${chosen.url}"
        )
        val resumeMs = consumePendingStartPosition()
        // DahmerMovies playback is served through p.111477.xyz. When no
        // cf_clearance cookie has been captured yet, open the site's root
        // page first so Cloudflare can complete its browser challenge. The
        // resulting cookie is persisted by CloudflareBypassActivity and is
        // attached by PlayerEngine when this stream is retried.
        if (needsDahmerMoviesClearance(chosen)) {
            Log.w(
                TAG,
                "DahmerMovies stream detected without cf_clearance; " +
                    "opening $DAHMER_CLEARANCE_URL"
            )
            pendingCloudflareStream = chosen
            pendingCloudflareResumeMs = resumeMs
            launchCloudflareBypass(chosen, DAHMER_CLEARANCE_URL)
            return
        }
        // Proactive Cloudflare bypass for the oogachaka CDN: if the stream
        // comes from serve.oogachakacdn.store and we don't already have a
        // saved cf_clearance cookie, open the bypass activity immediately
        // instead of waiting for ExoPlayer to surface a generic 403 error.
        if (needsOogachakaBypass(chosen)) {
            Log.w(
                TAG,
                "Oogachaka CDN stream detected without saved cookies; " +
                    "launching CloudflareBypassActivity directly"
            )
            pendingCloudflareStream = chosen
            pendingCloudflareResumeMs = resumeMs
            launchCloudflareBypass(chosen)
            return
        }
        // Pre-flight 403 detection. If the manifest URL is gated by
        // Cloudflare, give the user the option to open the bypass screen
        // *before* ExoPlayer emits a generic "Playback failed" toast.
        if (needsCloudflareBypass(chosen)) {
            offerCloudflareBypass(chosen, resumeMs)
        } else {
            startStreamPlayback(chosen, resumeMs)
        }
    }

    /**
     * Detects streams hosted on the oogachaka CDN
     * (https://serve.oogachakacdn.store) that do not have a saved
     * `cf_clearance` cookie yet. The oogachaka CDN reliably responds with
     * a 403 challenge to fresh clients, so we short-circuit the
     * probe-and-prompt flow and launch the bypass activity directly.
     */
    private fun needsOogachakaBypass(stream: StreamItem): Boolean {
        if (stream.url.isBlank()) return false
        if (!stream.url.startsWith(OOGACHAKA_STREAM_PREFIX)) return false
        val host = runCatching { Uri.parse(stream.url).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        val saved = CloudflareBypassActivity.loadCookies(applicationContext, host)
        if (!saved.isNullOrBlank()) {
            Log.i(
                TAG,
                "Oogachaka bypass skipped for $host — saved Cloudflare " +
                    "cookie present (${saved.length} chars)"
            )
            return false
        }
        return true
    }

    /**
     * Returns `true` when [stream] belongs to DahmerMovies and no usable
     * `cf_clearance` cookie is available for p.111477.xyz. Cookies supplied
     * directly with the stream response are checked before the persisted
     * Cloudflare cookie store.
     */
    private fun needsDahmerMoviesClearance(stream: StreamItem): Boolean {
        val url = stream.url.trim()
        if (url.isBlank()) return false
        // Immediate prefix check: if the upstream stream URL starts with
        // the Dahmer host root, treat it as a Dahmer stream so the
        // Cloudflare bypass flow can be launched before playback.
        if (url.startsWith(DAHMER_CLEARANCE_URL, ignoreCase = true)) {
            Log.d(TAG, "needsDahmerMoviesClearance: direct prefix match for url=$url")
            return true
        }
        val host = runCatching { Uri.parse(url).host?.lowercase()?.trim() }.getOrNull().orEmpty()
        // Consider it a Dahmer stream when either:
        // - the host equals the known Dahmer host (p.111477.xyz),
        // - the URL starts with the configured clearance URL (covers scheme+prefix),
        // - or the provider name equals the Dahmer provider token.
        val isDahmerStream = host.equals(DAHMER_CLEARANCE_HOST, ignoreCase = true) ||
            url.startsWith(DAHMER_CLEARANCE_URL, ignoreCase = true) ||
            stream.provider.equals(DAHMER_PROVIDER, ignoreCase = true)
        Log.d(TAG, "needsDahmerMoviesClearance: url=$url host=$host provider=${stream.provider} isDahmer=$isDahmerStream")
        if (!isDahmerStream) return false

        val streamCookie = stream.headers.entries
            .firstOrNull { it.key.equals("Cookie", ignoreCase = true) }
            ?.value
        if (containsCfClearance(streamCookie)) return false

        val saved = CloudflareBypassActivity.loadCookies(
            applicationContext,
            DAHMER_CLEARANCE_HOST
        )
        if (containsCfClearance(saved)) {
            Log.i(
                TAG,
                "DahmerMovies clearance already saved for $DAHMER_CLEARANCE_HOST"
            )
            return false
        }
        return true
    }

    private fun containsCfClearance(cookies: String?): Boolean = cookies
        ?.split(';')
        ?.any { entry ->
            entry.substringBefore('=').trim()
                .equals(CloudflareBypassActivity.CF_CLEARANCE_COOKIE, ignoreCase = true) &&
                entry.substringAfter('=', missingDelimiterValue = "").trim().isNotEmpty()
        } == true

    /**
     * Quick check for a Cloudflare-style 403 on [stream]. Returns `true`
     * when the upstream replied with HTTP 403 (a "Verify you are human"
     * challenge) and the host does not already have a saved `cf_clearance`
     * cookie. When a saved cookie is present, the player will use it
     * automatically and the user does not need to be re-prompted.
     */
    private fun needsCloudflareBypass(stream: StreamItem): Boolean {
        if (stream.url.isBlank()) return false
        val host = runCatching { Uri.parse(stream.url).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return false
        // Skip the probe if we already have a saved cf_clearance for the
        // host — the bypass has been done before and the cookie should
        // already let the request through.
        val saved = CloudflareBypassActivity.loadCookies(applicationContext, host)
        if (!saved.isNullOrBlank()) {
            Log.i(
                TAG,
                "playBest skipping 403 probe for $host — saved Cloudflare " +
                    "cookie present (${saved.length} chars)"
            )
            return false
        }
        // Use the existing in-memory probe result if validateStreamsInBackground
        // has already classified this stream.
        stream.httpStatusCode?.let { code ->
            if (code == 403) return true
        }
        return false
    }

    private fun startStreamPlayback(stream: StreamItem, startPositionMs: Long = 0L) {
        val streamKey = "${stream.url}|${stream.provider}|${startPositionMs}"
        if (lastStreamPlaybackKey == streamKey && engine.player.currentMediaItem != null) return
        lastStreamPlaybackKey = streamKey
        skipSegmentsFetchJob?.cancel()

        // This also covers stream switching, subtitle reloads and sniffed
        // playback paths that do not pass through playBest().
        if (!engine.player.isPlaying && needsDahmerMoviesClearance(stream)) {
            Log.w(
                TAG,
                "startStreamPlayback intercepted DahmerMovies without " +
                    "cf_clearance; opening $DAHMER_CLEARANCE_URL"
            )
            pendingCloudflareStream = stream
            pendingCloudflareResumeMs = startPositionMs
            showStatus(getString(R.string.cloudflare_blocked_checking), retry = false)
            showLoadingArtwork()
            launchCloudflareBypass(stream, DAHMER_CLEARANCE_URL)
            return
        }
        // Pre-load guard: the oogachaka CDN reliably 403s fresh clients, so
        // route every load to the bypass activity before handing the stream
        // to ExoPlayer. Catches streams that bypass the playBest() check
        // (e.g. switchStream, loadExternalSubtitle, sniffed playback).
        if (!engine.player.isPlaying && needsOogachakaBypass(stream)) {
            Log.w(
                TAG,
                "startStreamPlayback intercepted oogachaka stream without " +
                    "saved cookies; launching CloudflareBypassActivity"
            )
            pendingCloudflareStream = stream
            pendingCloudflareResumeMs = startPositionMs
            showStatus(getString(R.string.cloudflare_blocked_checking), retry = false)
            showLoadingArtwork()
            launchCloudflareBypass(stream)
            return
        }
        handlingPlaybackError = false
        stopWatchProgressUpdates()
        showLoadingArtwork()
        showStatus(getString(R.string.buffering), retry = false)
        // A replacement MediaSource has its own buffer. Clear the old
        // source's buffered marker so the seek bar reflects the new source
        // as Media3 fills it.
        binding.seekBar.secondaryProgress = 0
        engine.play(stream, startPositionMs, activeSubtitles)
    }

    /**
     * Run a synchronous 403 probe on [stream] and, if the upstream is gated
     * by Cloudflare, display the bypass dialog. A non-403 failure falls
     * through to plain playback so the user still gets a chance to retry.
     */
    private fun offerCloudflareBypass(stream: StreamItem, resumeMs: Long) {
        if (isFinishing || isDestroyed || engine.player.isPlaying) return
        showStatus(getString(R.string.cloudflare_blocked_checking), retry = false)
        showLoadingArtwork()
        cloudflareProbeJob?.cancel()
        cloudflareProbeJob = lifecycleScope.launch {
            val code = withContext(Dispatchers.IO) {
                StreamValidator.probeStatus(stream)
            }
            if (isFinishing || isDestroyed || engine.player.isPlaying) return@launch
            Log.i(
                TAG,
                "Cloudflare probe for ${stream.provider} ${stream.quality} " +
                    "url=${stream.url} -> HTTP $code"
            )
            if (code == 403) {
                showCloudflareBypassDialog(stream, resumeMs)
            } else {
                // 2xx, 4xx-other, 5xx, or null (network error): play
                // normally so the user gets a normal failure UI.
                startStreamPlayback(stream, resumeMs)
            }
        }
    }

    /**
     * Show an [AlertDialog] asking the user whether to open the
     * [CloudflareBypassActivity] to solve a 403 challenge. On accept we
     * stash [stream] / [resumeMs] into pending fields so the
     * [cloudflareBypassLauncher] callback can resume playback when the
     * bypass completes. On cancel we fall through to plain playback so
     * the user can pick a different stream from the dialog.
     */
    private fun showCloudflareBypassDialog(stream: StreamItem, resumeMs: Long) {
        if (isFinishing || isDestroyed || engine.player.isPlaying) return
        cloudflareDialog?.takeIf { it.isShowing }?.dismiss()
        engine.pause()
        hideLoadingArtwork()
        showStatus(getString(R.string.cloudflare_blocked_message), retry = false)
        pendingCloudflareStream = stream
        pendingCloudflareResumeMs = resumeMs
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.cloudflare_blocked_title)
            .setMessage(R.string.cloudflare_blocked_message)
            .setCancelable(true)
            .setPositiveButton(R.string.cloudflare_blocked_solve) { dialog, _ ->
                dialog.dismiss()
                launchCloudflareBypass(stream)
            }
            .setNegativeButton(R.string.cloudflare_blocked_skip) { dialog, _ ->
                dialog.dismiss()
                pendingCloudflareStream = null
                pendingCloudflareResumeMs = 0L
                // Move to the next best stream instead of starting this one
                // (which would just 403 again). Fall back to the existing
                // "playback failed" UI so the user can pick a different
                // source from the Streams dialog.
                skipToNextAvailableStream()
            }
            .create()
        dialog.setOnDismissListener {
            if (cloudflareDialog === dialog) cloudflareDialog = null
        }
        cloudflareDialog = dialog
        dialog.show()
    }

    /**
     * Launches [CloudflareBypassActivity] in front of the user. The
     * activity loads the gated URL in a WebView and, on success, writes
     * the `cf_clearance` cookie to SharedPreferences and finishes with
     * `RESULT_OK`. Our [cloudflareBypassLauncher] picks that result up and
     * retries [stream].
     */
    private fun resolveCloudflareBypassHost(
        stream: StreamItem,
        fallbackUrl: String = stream.url
    ): String? {
        val candidateUrl = stream.headers.entries
            .firstOrNull { (key, _) ->
                key.equals("Referer", ignoreCase = true) ||
                    key.equals("Referrer", ignoreCase = true) ||
                    key.equals("Origin", ignoreCase = true)
            }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.startsWith("http", ignoreCase = true) }
            ?: fallbackUrl.trim()

        return runCatching { Uri.parse(candidateUrl).host.orEmpty() }
            .getOrDefault("")
            .takeIf { it.isNotBlank() }
    }

    private fun launchCloudflareBypass(
        stream: StreamItem,
        verificationUrl: String = stream.url
    ) {
        // Retain explicit verification targets (for example, the DahmerMovies
        // clearance page), while reducing the final input to the host only.
        val bypassHost = when {
            verificationUrl.isBlank() -> resolveCloudflareBypassHost(stream, stream.url)
            verificationUrl.equals(stream.url, ignoreCase = true) ->
                resolveCloudflareBypassHost(stream, verificationUrl)
            else -> runCatching { Uri.parse(verificationUrl).host.orEmpty() }
                .getOrDefault("")
                .takeIf { it.isNotBlank() }
        }
        if (bypassHost == null) {
            Log.e(TAG, "Could not resolve Cloudflare verification host for ${stream.url}")
            return
        }

        val intent = Intent(this, CloudflareBypassActivity::class.java).apply {
            putExtra(CloudflareBypassActivity.EXTRA_HOST, bypassHost)
            putExtra(
                CloudflareBypassActivity.EXTRA_TITLE,
                if (stream.provider.isNotBlank()) {
                    "Verifying ${stream.provider}"
                } else {
                    "Verifying stream"
                }
            )
        }
        Log.i(
            TAG,
            "Launching CloudflareBypassActivity for ${stream.provider} " +
                "${stream.quality} verificationHost=$bypassHost"
        )
        runCatching {
            cloudflareBypassLauncher.launch(intent)
        }.onFailure { error ->
            Log.e(TAG, "Failed to launch CloudflareBypassActivity", error)
            Toast.makeText(
                this,
                R.string.cloudflare_bypass_open_failed,
                Toast.LENGTH_LONG
            ).show()
            pendingCloudflareStream = null
            pendingCloudflareResumeMs = 0L
        }
    }

    /**
     * Skip the gated stream and try the next one in [availableStreams].
     * Used when the user dismisses the 403 dialog with "Skip". Falls back
     * to the "playback failed" UI when no alternative is available.
     */
    private fun skipToNextAvailableStream() {
        val candidates = availableStreams
            .filter { it.url != activeStream?.url }
            .sortedByDescending { qualityRank(it.quality) }
        val next = candidates.firstOrNull()
        if (next == null) {
            showStatus(
                getString(R.string.playback_failed_try_another_stream),
                retry = true
            )
            return
        }
        Log.i(
            PROVIDER_TAG,
            "Skipping Cloudflare-gated stream; trying next best " +
                "provider=${next.provider} quality=${next.quality}"
        )
        activeStream = next
        startStreamPlayback(next, consumePendingStartPosition())
    }

    private fun consumePendingStartPosition(): Long {
        val position = pendingStartPositionMs.coerceAtLeast(0L)
        pendingStartPositionMs = 0L
        return position
    }

    private fun applyPendingReadySeek() {
        val requestedPosition = pendingReadySeekPositionMs
        if (requestedPosition <= 0L) return
        pendingReadySeekPositionMs = 0L
        val duration = engine.player.duration
        val target = if (duration > 0L) {
            requestedPosition.coerceAtMost((duration - 1_000L).coerceAtLeast(0L))
        } else {
            requestedPosition
        }
        engine.player.seekTo(target)
    }

    private fun qualityRank(quality: String): Int {
        return qualityResolution(quality) ?: 0
    }

    private fun qualityResolution(quality: String): Int? {
        val normalized = quality.lowercase()
        if (normalized.contains("4k")) return 2160
        if (normalized.contains("2k")) return 1440
        return Regex("""(\d{3,4})\s*p""")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun showStatus(message: String, retry: Boolean) {
        if (::engine.isInitialized && engine.player.isPlaying) {
            binding.playerStatus.setOnClickListener(null)
            binding.playerStatus.visibility = View.GONE
            return
        }
        binding.playerStatus.text = message
        binding.playerStatus.visibility = View.VISIBLE
        if (retry) {
            binding.playerStatus.setOnClickListener { loadCurrentMedia() }
        } else {
            binding.playerStatus.setOnClickListener(null)
        }
    }

    private fun showLoadingArtwork() {
        binding.loadingArtwork.animate().cancel()
        binding.loadingArtwork.alpha = 1f
        binding.loadingArtwork.visibility = View.VISIBLE
    }

    private fun hideLoadingArtwork() {
        binding.loadingArtwork.animate()
            .alpha(0f)
            .setDuration(250L)
            .withEndAction {
                binding.loadingArtwork.visibility = View.GONE
                binding.loadingArtwork.alpha = 1f
            }
            .start()
    }

    // ── Episodes side panel helpers ────────────────────────────────────────

    private fun toggleEpisodesPanel() {
        if (isEpisodesPanelOpen) closeEpisodesPanel() else openEpisodesPanel()
    }

    private fun openEpisodesPanel() {
        if (isEpisodesPanelOpen) return
        if (currentMediaType != TYPE_SERIES) return
        isEpisodesPanelOpen = true

        // Keep the player playing — the user requested "without exiting the player".
        showControls()
        binding.episodesPanelContainer.visibility = View.VISIBLE
        binding.episodesPanelScrim.visibility = View.VISIBLE
        binding.episodesPanelScrim.alpha = 0f
        binding.episodesPanelScrim.animate().alpha(1f).setDuration(180L).start()

        val panelWidth = binding.episodesPanelContainer.width
            .takeIf { it > 0 } ?: (360 * resources.displayMetrics.density).toInt()
        binding.episodesPanelContainer.translationX = -panelWidth.toFloat()
        ObjectAnimator.ofFloat(binding.episodesPanelContainer, "translationX", 0f)
            .apply {
                duration = 220L
                interpolator = AccelerateDecelerateInterpolator()
            }
            .start()

        // Move focus into the panel for D-pad users.
        binding.rvEpisodes.post {
            val target = binding.rvEpisodes.layoutManager
                ?.findViewByPosition(currentEpisode?.minus(1)?.coerceAtLeast(0) ?: 0)
            (target ?: binding.rvEpisodes).requestFocus()
        }

        loadEpisodesForCurrentSeason()
    }

    private fun closeEpisodesPanel() {
        if (!isEpisodesPanelOpen) return
        isEpisodesPanelOpen = false
        val panelWidth = binding.episodesPanelContainer.width
            .takeIf { it > 0 } ?: (360 * resources.displayMetrics.density).toInt()
        ObjectAnimator.ofFloat(binding.episodesPanelContainer, "translationX", -panelWidth.toFloat())
            .apply {
                duration = 200L
                interpolator = AccelerateDecelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.episodesPanelContainer.visibility = View.GONE
                    }
                })
            }
            .start()
        binding.episodesPanelScrim.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction { binding.episodesPanelScrim.visibility = View.GONE }
            .start()

        // Return focus to the button that opened the panel.
        binding.btnEpisodes.requestFocus()
    }

    private fun loadEpisodesForCurrentSeason() {
        val season = currentSeason ?: return
        val tmdbId = currentTmdbId.takeIf { it > 0 } ?: return

        if (cachedSeasonFor == season && cachedSeasonDetail != null) {
            bindEpisodes(cachedSeasonDetail!!)
            return
        }

        if (episodeFetchJob?.isActive == true && cachedSeasonFor == season) return

        binding.tvEpisodesPanelStatus.visibility = View.VISIBLE
        binding.tvEpisodesPanelStatus.text = getString(R.string.episode_panel_loading)

        episodeFetchJob?.cancel()
        episodeFetchJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.getSeasonDetail(tmdbId, season)
            }
            result
                .onSuccess { detail ->
                    cachedSeasonDetail = detail
                    cachedSeasonFor = season
                    bindEpisodes(detail)
                }
                .onFailure { e ->
                    Log.w(TAG, "Failed to load season $season: ${e.message}")
                    binding.tvEpisodesPanelStatus.text =
                        getString(R.string.episode_panel_error)
                }
        }
    }

    private fun bindEpisodes(detail: SeasonDetail) {
        val episodes = detail.episodes.sortedBy { it.episodeNumber }
        if (binding.tvEpisodesPanelHeader.text.toString() == buildString {
                append(detail.name)
                append(" — ")
                append(episodes.size)
                append(if (episodes.size == 1) " episode" else " episodes")
            } && episodeAdapter.currentList == episodes) {
            return
        }

        binding.tvEpisodesPanelStatus.visibility = View.GONE
        binding.tvEpisodesPanelHeader.text = buildString {
            append(detail.name)
            append(" — ")
            append(episodes.size)
            append(if (episodes.size == 1) " episode" else " episodes")
        }
        episodeAdapter.setCurrentlyPlaying(currentEpisode)
        episodeAdapter.submitList(episodes)
    }

    private fun onEpisodeSelected(episode: Episode) {
        if (currentMediaType != TYPE_SERIES) return
        if (episode.seasonNumber == currentSeason && episode.episodeNumber == currentEpisode) {
            // Tapped the row that is already playing — just close the panel.
            closeEpisodesPanel()
            return
        }
        playEpisode(episode)
    }

    private fun playEpisode(episode: Episode) {
        // Persist the user's progress for the episode they're leaving so the
        // new instance of the activity can resume at the right place if the
        // user comes back.
        resetWatchProgressForCurrentEpisode()

        val newIntent = DirectStreamActivity.createIntent(
            context = this,
            tmdbId = currentTmdbId,
            isTv = true,
            season = episode.seasonNumber,
            episode = episode.episodeNumber,
            title = currentTitle.substringBefore(" • "),
            posterPath = currentPosterPath,
            backdropPath = currentBackdropPath,
            overview = currentOverview,
            voteAverage = currentVoteAverage,
            releaseDate = currentReleaseDate
        )
        // Preserve the provider choice and any sniffed-stream extras so the
        // new activity picks the same source as the old one.
        intent.getStringExtra(EXTRA_PROVIDER)?.let { newIntent.putExtra(EXTRA_PROVIDER, it) }
        intent.getStringExtra(EXTRA_SNIFFED_URL)?.let { newIntent.putExtra(EXTRA_SNIFFED_URL, it) }
        intent.getStringExtra(EXTRA_SNIFFED_TYPE)?.let { newIntent.putExtra(EXTRA_SNIFFED_TYPE, it) }
        intent.getStringExtra(EXTRA_SNIFFED_MIME_TYPE)?.let { newIntent.putExtra(EXTRA_SNIFFED_MIME_TYPE, it) }
        intent.getStringExtra(EXTRA_SNIFFED_HEADERS)?.let { newIntent.putExtra(EXTRA_SNIFFED_HEADERS, it) }
        intent.getStringExtra(EXTRA_SNIFFED_COOKIE)?.let { newIntent.putExtra(EXTRA_SNIFFED_COOKIE, it) }
        intent.getStringExtra(EXTRA_SNIFFED_SUBTITLES)?.let { newIntent.putExtra(EXTRA_SNIFFED_SUBTITLES, it) }

        // Cancel any in-flight panel fetch and dismiss the panel cleanly
        // *before* finishing so the user does not see the panel "snap shut"
        // after the new activity is already on top.
        episodeFetchJob?.cancel()
        isEpisodesPanelOpen = false
        binding.episodesPanelContainer.visibility = View.GONE
        binding.episodesPanelScrim.visibility = View.GONE

        DirectStreamLauncher.launch(this, newIntent)
        finish()
    }

    private fun showExitConfirmationDialog() {
        if (quitDialog?.isShowing == true) return
        quitDialog = QuitDialog(
            context = this,
            title = "Stop Playback?",
            message = "Are you sure you want to stop playback and exit?",
            positiveButtonText = "Stop",
            negativeButtonText = "Continue",
            lottieAnimRes = R.raw.exit,
            onNo = { quitDialog = null },
            onYes = {
                quitDialog = null
                finish()
            }
        ).also { dialog ->
            dialog.setOnDismissListener { quitDialog = null }
            dialog.show()
        }
    }

    private val hideControlsRunnable = Runnable {
        if (
            !controlsLockedVisible &&
            trackDialog?.isShowing != true &&
            streamDialog?.isShowing != true &&
            subtitleDialog?.isShowing != true
        ) {
            binding.overlayControls.visibility = View.GONE
        }
    }

    private fun showControls() {
        if (binding.overlayControls.visibility == View.VISIBLE) {
            uiHandler.removeCallbacks(hideControlsRunnable)
            uiHandler.postDelayed(hideControlsRunnable, 4_000)
            return
        }
        uiHandler.removeCallbacks(hideControlsRunnable)
        binding.overlayControls.visibility = View.VISIBLE
        binding.btnPlayPause.post { binding.btnPlayPause.requestFocus() }
        uiHandler.postDelayed(hideControlsRunnable, 4_000)
    }

    private fun updateBottomFocusChain() {
        val controls = listOf(
            binding.btnPreviousEpisode,
            binding.btnFill,
            binding.btnPlayerSubtitles,
            binding.btnPlayerTracks,
            binding.btnPlayerStreams,
            binding.btnVolume,
            binding.btnNextEpisode,
            binding.btnEpisodes
        ).filter { it.visibility == View.VISIBLE }

        val signature = buildString {
            controls.forEachIndexed { index, control ->
                append(control.id)
                append(':')
                append(control.visibility)
                append('|')
                append(index)
                append(';')
            }
            append(binding.btnPlayPause.id)
        }
        if (signature == lastFocusChainSignature) return
        lastFocusChainSignature = signature

        controls.forEachIndexed { index, control ->
            control.nextFocusLeftId = controls[(index - 1 + controls.size) % controls.size].id
            control.nextFocusRightId = controls[(index + 1) % controls.size].id
            control.nextFocusUpId = binding.btnPlayPause.id
        }
        binding.btnPlayPause.nextFocusDownId = binding.seekBar.id
        binding.seekBar.nextFocusUpId = binding.btnPlayPause.id
        binding.seekBar.nextFocusDownId = controls.firstOrNull()?.id ?: View.NO_ID
    }

    private fun handleSeekBarRemoteKey(event: KeyEvent, direction: Int): Boolean {
        val duration = engine.player.duration
        if (duration <= 0L) return true

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                binding.seekBar.setDurationMs(duration)
                remoteSeekInProgress = true
                userSeeking = true
                controlsLockedVisible = true

                val nextProgress = (binding.seekBar.progress +
                    (direction * REMOTE_SEEK_PROGRESS_STEP)).coerceIn(0, binding.seekBar.max)
                binding.seekBar.progress = nextProgress
                binding.tvCurrentTime.text =
                    formatPlaybackTime(binding.seekBar.getPositionMsFromProgress())
                true
            }
            KeyEvent.ACTION_UP -> {
                completeRemoteSeek()
                true
            }
            else -> true
        }
    }

    private fun completeRemoteSeek() {
        if (!remoteSeekInProgress) return
        remoteSeekInProgress = false
        val duration = engine.player.duration
        if (duration > 0L) {
            engine.player.seekTo(binding.seekBar.getPositionMsFromProgress())
        }
        userSeeking = false
        controlsLockedVisible = false
        showControls()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Track dialog swallows its own back key; defer to the dialog first.
        if (trackDialog?.isShowing == true) {
            return super.dispatchKeyEvent(event)
        }
        if (streamDialog?.isShowing == true) {
            return super.dispatchKeyEvent(event)
        }
        if (subtitleDialog?.isShowing == true) {
            return super.dispatchKeyEvent(event)
        }
        showControls()
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    engine.seekBy(-SEEK_STEP_MS)
                }
                return true
            }

            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    engine.seekBy(SEEK_STEP_MS)
                }
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    engine.resume()
                }
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    engine.pause()
                }
                return true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (engine.player.isPlaying) engine.pause() else engine.resume()
                }
                return true
            }

            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    if (isEpisodesPanelOpen) {
                        closeEpisodesPanel()
                    } else {
                        showExitConfirmationDialog()
                    }
                    return true
                }
                return true
            }

//            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
//                if (event.action == KeyEvent.ACTION_UP) {
//                    if (engine.player.isPlaying) engine.pause() else engine.resume()
//                    return true
//                }
//            }

            // KeyEvent.KEYCODE_DPAD_LEFT -> {
            //     return handleSkip(event.action, -1)
            // }

            // KeyEvent.KEYCODE_DPAD_RIGHT -> {
            //     return handleSkip(event.action, +1)
            // }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleSkip(action: Int, dir: Int): Boolean = when (action) {
        KeyEvent.ACTION_DOWN -> {
            if (skipDirection != dir) {
                stopSkipRamp()
                skipDirection = dir
                skipHoldStart = System.currentTimeMillis()
                engine.seekBy(dir * SKIP_SEC_MIN * 1000L)
                uiHandler.postDelayed(skipRampTick, SKIP_REPEAT_MS)
            }
            true
        }
        KeyEvent.ACTION_UP -> {
            stopSkipRamp()
            true
        }
        else -> false
    }

    private fun stopSkipRamp() {
        skipDirection = 0
        uiHandler.removeCallbacks(skipRampTick)
    }

    private data class ResumeHistory(
        val positionMs: Long,
        val durationMs: Long = 0L,
        val season: Int? = null,
        val episode: Int? = null,
        val updatedAt: Long = 0L,
        val source: String
    )

    private fun checkAndAddToWatchHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val isTv = currentMediaType == TYPE_SERIES
                val localHistory = repository.getWatchHistoryItem(
                    this@DirectStreamActivity,
                    currentTmdbId,
                    isTv
                )
                val firebaseHistory = withTimeoutOrNull(FIREBASE_HISTORY_TIMEOUT_MS) {
                    FirebaseManager.getWatchHistoryOnce()
                }
                val remoteHistory = extractFirebaseResumeHistory(firebaseHistory, isTv)
                val localResume = localHistory?.let {
                    ResumeHistory(
                        positionMs = it.playbackPosition.coerceAtLeast(0L),
                        season = it.seasonNumber,
                        episode = it.episodeNumber,
                        updatedAt = it.lastWatched,
                        source = "local"
                    )
                }
                val selectedResume = listOfNotNull(localResume, remoteHistory)
                    .filter { historyMatchesCurrentMedia(it, isTv) }
                    .maxWithOrNull(
                        compareBy<ResumeHistory> { it.updatedAt }
                            .thenBy { it.positionMs }
                    )
                val resumePosition = selectedResume?.positionMs?.coerceAtLeast(0L) ?: 0L

                pendingStartPositionMs = resumePosition
                Log.i(
                    TAG,
                    "[WatchHistory] Resume resolved from ${selectedResume?.source ?: "new history"} " +
                        "at ${resumePosition}ms"
                )

                if (localHistory == null) {
                    DatabaseManager.addToWatchHistoryAsync(
                        id = currentTmdbId,
                        mediaType = if (isTv) "tv" else "movie",
                        title = currentTitle,
                        overview = currentOverview,
                        posterPath = currentPosterPath,
                        backdropPath = currentBackdropPath,
                        voteAverage = currentVoteAverage,
                        releaseDate = currentReleaseDate,
                        seasonNumber = currentSeason.takeIf { isTv },
                        episodeNumber = currentEpisode.takeIf { isTv },
                        playbackPosition = resumePosition
                    )
                } else {
                    val mediaType = if (isTv) "tv" else "movie"
                    DatabaseManager.watchHistoryDao().updatePlaybackPosition(
                        currentTmdbId,
                        mediaType,
                        resumePosition
                    )
                    if (isTv) {
                        DatabaseManager.watchHistoryDao().updateEpisodeInfo(
                            currentTmdbId,
                            mediaType,
                            currentSeason ?: 1,
                            currentEpisode ?: 1
                        )
                    }
                }
                syncWatchHistory(
                    position = resumePosition,
                    duration = selectedResume?.durationMs ?: 0L
                )
            } catch (error: Exception) {
                Log.e(TAG, "[WatchHistory] Could not initialize playback progress", error)
            }

            withContext(Dispatchers.Main) {
                watchHistoryReady = true
                val sniffedUrl = intent.getStringExtra(EXTRA_SNIFFED_URL)
                if (sniffedUrl.isNullOrBlank()) {
                    loadCurrentMedia()
                } else {
                    playSniffedStream(sniffedUrl)
                }
            }
        }
    }

    private fun extractFirebaseResumeHistory(
        watchHistory: Map<String, Any>?,
        isTv: Boolean
    ): ResumeHistory? {
        val mediaCollection = watchHistory
            ?.get(if (isTv) "tv" else "movies") as? Map<*, *>
            ?: return null
        val media = mediaCollection.entries
            .firstOrNull { it.key.toString() == currentTmdbId.toString() }
            ?.value as? Map<*, *>
            ?: return null

        return ResumeHistory(
            positionMs = media.longValue("playbackPosition").coerceAtLeast(0L),
            durationMs = media.longValue("duration").coerceAtLeast(0L),
            season = media.intValueOrNull("seasonNumber"),
            episode = media.intValueOrNull("episodeNumber"),
            updatedAt = media.longValue("updatedAt"),
            source = "firebase"
        )
    }

    private fun historyMatchesCurrentMedia(history: ResumeHistory, isTv: Boolean): Boolean {
        return !isTv ||
            (history.season == currentSeason && history.episode == currentEpisode)
    }

    private fun Map<*, *>.longValue(key: String): Long {
        return when (val value = this[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun Map<*, *>.intValueOrNull(key: String): Int? {
        return when (val value = this[key]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun startWatchProgressUpdates() {
        uiHandler.removeCallbacks(watchProgressTick)
        uiHandler.postDelayed(watchProgressTick, WATCH_PROGRESS_INTERVAL_MS)
    }

    private fun stopWatchProgressUpdates() {
        uiHandler.removeCallbacks(watchProgressTick)
    }

    private fun persistWatchProgress() {
        if (
            !watchHistoryReady ||
            !::engine.isInitialized ||
            engine.player.currentMediaItem == null ||
            currentTmdbId <= 0
        ) return

        val isTv = currentMediaType == TYPE_SERIES
        val mediaType = if (isTv) "tv" else "movie"
        val position = engine.player.currentPosition.coerceAtLeast(0L)
        val duration = engine.player.duration.takeIf { it > 0 } ?: 0L
        val season = currentSeason
        val episode = currentEpisode

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                DatabaseManager.watchHistoryDao().updatePlaybackPosition(
                    currentTmdbId,
                    mediaType,
                    position
                )
                if (isTv) {
                    DatabaseManager.watchHistoryDao().updateEpisodeInfo(
                        currentTmdbId,
                        mediaType,
                        season ?: 1,
                        episode ?: 1
                    )
                }
                syncWatchHistory(position, duration, season, episode)
            } catch (error: Exception) {
                Log.e(TAG, "[WatchHistory] Could not persist playback progress", error)
            }
        }
    }

    private fun resetWatchProgressForCurrentEpisode() {
        if (currentMediaType != TYPE_SERIES) return
        val season = currentSeason ?: 1
        val episode = currentEpisode ?: 1
        lifecycleScope.launch(Dispatchers.IO) {
            DatabaseManager.watchHistoryDao().updatePlaybackPosition(currentTmdbId, "tv", 0L)
            DatabaseManager.watchHistoryDao().updateEpisodeInfo(
                currentTmdbId,
                "tv",
                season,
                episode
            )
            syncWatchHistory(0L, 0L, season, episode)
        }
    }

    private suspend fun syncWatchHistory(
        position: Long,
        duration: Long,
        season: Int? = currentSeason,
        episode: Int? = currentEpisode
    ) {
        val isTv = currentMediaType == TYPE_SERIES
        FirebaseManager.syncWatchHistory(
            tmdbId = currentTmdbId,
            isTv = isTv,
            seasonNumber = season.takeIf { isTv },
            episodeNumber = episode.takeIf { isTv },
            playbackPosition = position,
            duration = duration,
            title = currentTitle,
            overview = currentOverview,
            posterPath = currentPosterPath,
            backdropPath = currentBackdropPath,
            voteAverage = currentVoteAverage,
            releaseDate = currentReleaseDate
        ).await()
    }

    override fun onStart() {
        super.onStart()
        if (::engine.isInitialized) engine.resume()
        if (
            watchHistoryReady &&
            ::engine.isInitialized &&
            engine.player.playbackState == Player.STATE_READY
        ) {
            startWatchProgressUpdates()
        }
    }

    override fun onStop() {
        stopWatchProgressUpdates()
        if (::engine.isInitialized) {
            persistWatchProgress()
            engine.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        cancelAutoSkipCountdown()
        streamJob?.cancel()
        subtitleJob?.cancel()
        cloudflareProbeJob?.cancel()
        cloudflareProbeJob = null
        episodeFetchJob?.cancel()
        episodeFetchJob = null
        episodeDurationFetchJob?.cancel()
        episodeDurationFetchJob = null
        skipSegmentsFetchJob?.cancel()
        skipSegmentsFetchJob = null
        trackDialog?.takeIf { it.isShowing }?.dismiss()
        trackDialog = null
        streamDialog?.takeIf { it.isShowing }?.dismiss()
        streamDialog = null
        subtitleDialog?.takeIf { it.isShowing }?.dismiss()
        subtitleDialog = null
        noStreamsDialog?.takeIf { it.isShowing }?.dismiss()
        noStreamsDialog = null
        quitDialog?.takeIf { it.isShowing }?.dismiss()
        quitDialog = null
        cloudflareDialog?.takeIf { it.isShowing }?.dismiss()
        cloudflareDialog = null
        pendingCloudflareStream = null
        pendingCloudflareResumeMs = 0L
        uiHandler.removeCallbacksAndMessages(null)
        if (::engine.isInitialized) engine.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KiduyuLitePlayer"
        private const val PROVIDER_TAG = "KiduyuLiteProvider"
        private const val SKIP_DURATION_POLL_INTERVAL_MS = 100L

        const val EXTRA_TYPE = "MEDIA_TYPE"
        const val EXTRA_IS_TV = "IS_TV"
        const val EXTRA_TMDB_ID = "TMDB_ID"
        const val EXTRA_IMDB_ID = "IMDB_ID"
        const val EXTRA_SEASON = "SEASON_NUMBER"
        const val EXTRA_EPISODE = "EPISODE_NUMBER"
        const val EXTRA_PROVIDER = "PROVIDER"
        const val EXTRA_BACKDROP_URL = "BACKDROP_PATH"
        const val EXTRA_TITLE = "TITLE"
        const val EXTRA_POSTER_PATH = "POSTER_PATH"
        const val EXTRA_OVERVIEW = "OVERVIEW"
        const val EXTRA_VOTE_AVERAGE = "VOTE_AVERAGE"
        const val EXTRA_RELEASE_DATE = "RELEASE_DATE"
        const val EXTRA_SNIFFED_URL = "SNIFFED_STREAM_URL"
        const val EXTRA_SNIFFED_HEADERS = "SNIFFED_STREAM_HEADERS"
        const val EXTRA_SNIFFED_COOKIE = "SNIFFED_STREAM_COOKIE"
        const val EXTRA_SNIFFED_TYPE = "SNIFFED_STREAM_TYPE"
        const val EXTRA_SNIFFED_MIME_TYPE = "SNIFFED_STREAM_MIME_TYPE"
        const val EXTRA_SNIFFED_SUBTITLES = "SNIFFED_SUBTITLES"

        fun createIntent(
            context: Context,
            tmdbId: Int,
            isTv: Boolean,
            season: Int? = null,
            episode: Int? = null,
            imdbId: String? = null,
            title: String = "",
            posterPath: String? = null,
            backdropPath: String? = null,
            overview: String? = null,
            voteAverage: Double = 0.0,
            releaseDate: String? = null
        ): Intent = Intent(context, DirectStreamActivity::class.java).apply {
            putExtra(EXTRA_TMDB_ID, tmdbId)
            putExtra(EXTRA_IMDB_ID, imdbId)
            putExtra(EXTRA_TYPE, if (isTv) TYPE_SERIES else TYPE_MOVIE)
            putExtra(EXTRA_IS_TV, isTv)
            putExtra(EXTRA_SEASON, season ?: 0)
            putExtra(EXTRA_EPISODE, episode ?: 0)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_POSTER_PATH, posterPath)
            putExtra(EXTRA_BACKDROP_URL, backdropPath)
            putExtra(EXTRA_OVERVIEW, overview)
            putExtra(EXTRA_VOTE_AVERAGE, voteAverage)
            putExtra(EXTRA_RELEASE_DATE, releaseDate)
        }

        fun createSniffedIntent(
            context: Context,
            tmdbId: Int,
            isTv: Boolean,
            season: Int?,
            episode: Int?,
            title: String,
            posterPath: String?,
            backdropPath: String?,
            overview: String?,
            voteAverage: Double,
            releaseDate: String?,
            streamUrl: String,
            headers: Map<String, String>,
            cookie: String?,
            type: String,
            mimeType: String,
            subtitles: List<SniffedSubtitle>
        ): Intent = createIntent(
            context = context,
            tmdbId = tmdbId,
            isTv = isTv,
            season = season,
            episode = episode,
            title = title,
            posterPath = posterPath,
            backdropPath = backdropPath,
            overview = overview,
            voteAverage = voteAverage,
            releaseDate = releaseDate
        ).apply {
            putExtra(EXTRA_SNIFFED_URL, streamUrl)
            putExtra(EXTRA_SNIFFED_HEADERS, JSONObject(headers).toString())
            putExtra(EXTRA_SNIFFED_COOKIE, cookie)
            putExtra(EXTRA_SNIFFED_TYPE, type)
            putExtra(EXTRA_SNIFFED_MIME_TYPE, mimeType)
            putExtra(
                EXTRA_SNIFFED_SUBTITLES,
                JSONArray().apply {
                    subtitles.forEach { subtitle ->
                        put(
                            JSONObject()
                                .put("url", subtitle.url)
                                .put("mimeType", subtitle.mimeType)
                                .put("headers", JSONObject(subtitle.headers))
                                .put("cookie", subtitle.cookie.orEmpty())
                        )
                    }
                }.toString()
            )
        }

        const val TYPE_MOVIE  = "movie"
        const val TYPE_SERIES = "series"

        private const val OOGACHAKA_STREAM_PREFIX = "https://serve.oogachakacdn.store"
        private const val DAHMER_PROVIDER = "DahmerMovies"
        private const val DAHMER_CLEARANCE_HOST = "p.111477.xyz"
        private const val DAHMER_CLEARANCE_URL = "https://p.111477.xyz/"

        private const val SKIP_SEC_MIN = 30
        private const val WATCH_PROGRESS_INTERVAL_MS = 15_000L
        private const val FIREBASE_HISTORY_TIMEOUT_MS = 8_000L
        private const val SKIP_SEC_MAX = 60
        private const val SEEK_STEP_MS = 30_000L
        // One D-pad press moves 1% of the media duration; repeat presses support fast seeking.
        private const val REMOTE_SEEK_PROGRESS_STEP = 10
        private const val SKIP_RAMP_DURATION_MS = 5_000L
        private const val SKIP_REPEAT_MS = 600L
    }

    private fun normalizeArtworkUrl(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> "https://image.tmdb.org/t/p/original/${path.trimStart('/')}"
    }

    private data class ResizeModeOption(
        val resizeMode: Int,
        val label: Int
    )

    private val resizeModes = listOf(
        ResizeModeOption(AspectRatioFrameLayout.RESIZE_MODE_FIT, R.string.player_fit),
        ResizeModeOption(AspectRatioFrameLayout.RESIZE_MODE_FILL, R.string.player_fill),
        ResizeModeOption(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, R.string.player_zoom),
        ResizeModeOption(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, R.string.player_fixed_width),
        ResizeModeOption(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, R.string.player_fixed_height)
    )
}
