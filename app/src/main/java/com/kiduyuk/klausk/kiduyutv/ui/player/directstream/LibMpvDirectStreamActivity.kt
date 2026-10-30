package com.kiduyuk.klausk.kiduyutv.ui.player.directstream

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kiduyuk.klausk.kiduyutv.data.local.database.DatabaseManager
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import com.kiduyuk.klausk.kiduyutv.R
import com.kiduyuk.klausk.kiduyutv.activity.mainactivity.MainActivity
import com.kiduyuk.klausk.kiduyutv.databinding.ActivityDirectStreamBinding
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.StreamItem
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.model.SubtitleItem
import com.kiduyuk.klausk.kiduyutv.ui.player.cloudflareBypass.CloudflareBypassActivity
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.HttpCookieStore
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.OpenSubtitlesClient
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.api.SubdlSubtitleClient
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.LibMpvSession
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.MegaPlaybackToken
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamCatalog
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamResolver
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamSelectionDialog
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.StreamValidator
import com.kiduyuk.klausk.kiduyutv.ui.player.directstream.playback.VidsrcPlaybackToken
import com.kiduyuk.klausk.kiduyutv.util.SettingsManager
import com.kiduyuk.klausk.kiduyutv.util.AuthManager
import com.kiduyuk.klausk.kiduyutv.util.FirebaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

/** TV-friendly direct-stream player backed exclusively by libmpv. */
class LibMpvDirectStreamActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var binding: ActivityDirectStreamBinding
    private lateinit var session: LibMpvSession
    private val resolver = StreamResolver()
    private val repository = TmdbRepository()
    private lateinit var surfaceView: SurfaceView
    private var surfaceReady = false
    private var sessionReady = false
    private var pendingStream: StreamItem? = null
    private var streams: List<StreamItem> = emptyList()
    private var activeStream: StreamItem? = null
    private var playbackGeneration = 0
    private var streamDialog: StreamSelectionDialog? = null
    private var trackDialog: AlertDialog? = null
    private var resolverJob: Job? = null
    private var validationJob: Job? = null
    private var subtitleJob: Job? = null
    private var subtitleDialog: AlertDialog? = null
    private var wasPlaying = false
    private var muted = false
    private var aspectIndex = 0
    private var durationMs = 0L
    private var seeking = false
    private var remoteSeekInProgress = false
    private var resumePositionMs = 0L
    private var lastShownPlaybackError: String? = null
    private var lastLoggedSnapshotTrace: String? = null
    private var watchHistoryReady = false
    private var lastProgressPersistAt = 0L
    private var progressWriteJob: Job? = null
    private val aspectModes = listOf("auto", "16:9", "4:3", "21:9", "1:1")
    private val subtitleHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityDirectStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.playerView.visibility = View.GONE
        surfaceView = SurfaceView(this).apply {
            setBackgroundColor(Color.BLACK)
            holder.addCallback(this@LibMpvDirectStreamActivity)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val playerIndex = binding.root.indexOfChild(binding.playerView).coerceAtLeast(0)
        binding.root.addView(surfaceView, playerIndex, binding.playerView.layoutParams)
        session = LibMpvSession(this)
        Glide.with(this)
            .load(normalizeArtworkUrl(intent.getStringExtra(DirectStreamActivity.EXTRA_BACKDROP_URL)))
            .into(binding.loadingBackdrop)
        binding.loadingArtwork.visibility = View.VISIBLE
        Log.i(TAG, "onCreate: libmpv activity created; surface=${surfaceView.hashCode()} " +
            "tmdbId=${intent.getIntExtra(DirectStreamActivity.EXTRA_TMDB_ID, 0)}")
        startInitialRoute()
        wireControls()
        lifecycleScope.launch {
            session.snapshot.collect { snapshot ->
                val snapshotTrace = "loading=${snapshot.loading} buffering=${snapshot.buffering} " +
                    "paused=${snapshot.paused} positionMs=${snapshot.positionMs} " +
                    "durationMs=${snapshot.durationMs} tracks=${snapshot.tracks.size} " +
                    "error=${snapshot.error ?: "<none>"}"
                if (snapshotTrace != lastLoggedSnapshotTrace) {
                    lastLoggedSnapshotTrace = snapshotTrace
                    Log.i(TAG, "libmpv snapshot: $snapshotTrace")
                }
                durationMs = snapshot.durationMs.coerceAtLeast(0L)
                if (activeStream != null && durationMs > 0L && System.currentTimeMillis() - lastProgressPersistAt >= 30_000L) {
                    lastProgressPersistAt = System.currentTimeMillis()
                    persistWatchProgress(snapshot.positionMs)
                }
                binding.tvCurrentTime.text = clock(snapshot.positionMs)
                binding.tvTotalTime.text = clock(durationMs)
                if (!seeking) binding.seekBar.progress = if (durationMs > 0L) ((snapshot.positionMs * 1000L) / durationMs).toInt().coerceIn(0, 1000) else 0
                binding.btnPlayPause.setIconResource(if (snapshot.paused) R.drawable.ic_play else R.drawable.ic_pause)
                binding.bufferingOverlay.visibility = if (snapshot.buffering && activeStream != null) View.VISIBLE else View.GONE
                binding.loadingArtwork.visibility = if (activeStream == null || snapshot.loading || snapshot.buffering) View.VISIBLE else View.GONE
                val err = snapshot.error
                if (!err.isNullOrBlank()) showPlaybackError(err) else lastShownPlaybackError = null
                val hasChoices = snapshot.tracks.size > 1 || snapshot.tracks.any { it.type.equals("sub", true) || it.type.equals("subtitle", true) }
                binding.btnPlayerTracks.visibility = if (hasChoices) View.VISIBLE else View.GONE
                updateBottomFocusChain()
            }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
        enterImmersive()
    }

    /**
     * Confirm the legacy all-provider mode before resolving any providers.
     * Sniffed streams are already resolved and should start immediately.
     */
    private fun startInitialRoute() {
        val sniffedUrl = intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_URL)
        if (sniffedUrl.isNullOrBlank() && DirectStreamProviderWarning.showIfNeeded(
                activity = this,
                intent = intent,
                onContinue = ::initializeRoute
            )
        ) {
            return
        }
        initializeRoute()
    }

    private fun initializeRoute() {
        val type = intent.getStringExtra(DirectStreamActivity.EXTRA_TYPE)
            ?: if (intent.getBooleanExtra(DirectStreamActivity.EXTRA_IS_TV, false)) DirectStreamActivity.TYPE_SERIES else DirectStreamActivity.TYPE_MOVIE
        val tmdbId = intent.getIntExtra(DirectStreamActivity.EXTRA_TMDB_ID, 0)
        val season = intent.getIntExtra(DirectStreamActivity.EXTRA_SEASON, -1).takeIf { it > 0 }
        val episode = intent.getIntExtra(DirectStreamActivity.EXTRA_EPISODE, -1).takeIf { it > 0 }
        val title = intent.getStringExtra(DirectStreamActivity.EXTRA_TITLE).orEmpty()
        binding.tvPlayerTitle.text = title.ifBlank { getString(R.string.app_name) }
        val captured = parseSniffedStream()
        if (tmdbId <= 0 && captured == null) {
            showError(getString(R.string.playback_link_unavailable))
            return
        }
        resolverJob?.cancel()
        resolverJob = lifecycleScope.launch {
            showLoading()
            showStatus("Checking providers backend health", retry = false)
            if (!watchHistoryReady) {
                val explicitResume = intent.getLongExtra(DirectStreamActivity.EXTRA_RESUME_POSITION_MS, -1L)
                    .takeIf { it >= 0L } ?: 0L
                resumePositionMs = runCatching {
                    withContext(Dispatchers.IO) { initializeWatchHistory(tmdbId, type, season, episode) }
                }.getOrElse { explicitResume }
                watchHistoryReady = true
            }
            if (captured != null) {
                streams = listOf(captured)
                Log.i(TAG, "initializeRoute: using sniffed stream ${streamSummary(captured)}")
                beginPlayback(captured)
                validateStreamsInBackground(streams)
                return@launch
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val settings = SettingsManager(this@LibMpvDirectStreamActivity)
                    val requestedProvider = intent.getStringExtra(DirectStreamActivity.EXTRA_PROVIDER)
                    val configuredProvider = settings.getDefaultDirectStreamProvider()
                    val providerSelection = if (!DirectStreamProviderWarning.isAllProviders(configuredProvider)) {
                        configuredProvider
                    } else {
                        requestedProvider
                    }
                    resolver.load(
                        type = type,
                        tmdbId = tmdbId,
                        season = season,
                        episode = episode,
                        provider = StreamCatalog.resolve(providerSelection),
                        endpoint = settings.getDirectStreamEndpoint(),
                        onHealthCheckStart = {
                            withContext(Dispatchers.Main) {
                                showStatus("Checking providers backend health", retry = false)
                            }
                        },
                        onHealthCheckPassed = {
                            withContext(Dispatchers.Main) {
                                showStatus("Backend health check passed", retry = false)
                            }
                        },
                        onProviderDiscoveryStart = {
                            withContext(Dispatchers.Main) {
                                showStatus("Fetching providers", retry = false)
                            }
                        },
                        onProviderStreamsStart = { totalProviders ->
                            withContext(Dispatchers.Main) {
                                showProviderFetchProgress()
                                showStreamFetchCount()
                                updateProviderFetchProgress(0, totalProviders)
                                showStatus(
                                    "Fetching streams from $totalProviders providers",
                                    retry = false
                                )
                            }
                        },
                        onProviderProgress = { index, total, providerName ->
                            withContext(Dispatchers.Main) {
                                updateProviderFetchProgress(index, total)
                                showStatus(
                                    "Fetching streams · provider $index/$total completed: $providerName",
                                    retry = false
                                )
                            }
                        },
                        onProviderRetry = { index, total, providerName ->
                            withContext(Dispatchers.Main) {
                                showStatus(
                                    "Provider $index/$total enabled providers: $providerName\nretrying streams",
                                    retry = false
                                )
                            }
                        },
                        onStreamProgress = { fetchedCount ->
                            withContext(Dispatchers.Main) {
                                updateStreamFetchCount(fetchedCount)
                            }
                        }
                    )
                }
            }.onSuccess { result ->
                hideProviderFetchProgress()
                hideStreamFetchCount()
                streams = result
                Log.i(TAG, "initializeRoute: resolver returned ${result.size} streams")
                result.forEachIndexed { index, stream ->
                    Log.i(TAG, "initializeRoute: stream[$index] ${streamSummary(stream)}")
                }
                result.firstOrNull()?.let(::beginPlayback)
                    ?: showError(getString(R.string.no_streams_dialog_message))
                validateStreamsInBackground(result)
            }.onFailure { error ->
                hideProviderFetchProgress()
                hideStreamFetchCount()
                Log.i(TAG, "initializeRoute: stream resolution failed: ${error.message}", error)
                showError(error.message ?: getString(R.string.streams_failed))
            }
        }
    }

    private suspend fun initializeWatchHistory(
        tmdbId: Int,
        type: String,
        season: Int?,
        episode: Int?
    ): Long {
        val isTv = type == DirectStreamActivity.TYPE_SERIES
        val mediaType = if (isTv) "tv" else "movie"
        val explicitPosition = intent.getLongExtra(DirectStreamActivity.EXTRA_RESUME_POSITION_MS, -1L)
            .takeIf { it >= 0L }
        if (tmdbId <= 0) return explicitPosition ?: 0L

        val local = repository.getWatchHistoryItem(this, tmdbId, isTv)
        val remote = if (AuthManager.isSignedIn.value) {
            withTimeoutOrNull(5_000L) { FirebaseManager.getWatchHistoryOnce() }
        } else null
        val collection = remote?.get(if (isTv) "tv" else "movies") as? Map<*, *>
        val remoteItem = collection?.entries?.firstOrNull { it.key.toString() == tmdbId.toString() }?.value as? Map<*, *>
        val remoteSeason = remoteItem?.historyInt("seasonNumber")
        val remoteEpisode = remoteItem?.historyInt("episodeNumber")
        val localMatches = !isTv || (local != null && local.seasonNumber == season && local.episodeNumber == episode)
        val remoteMatches = !isTv || (remoteSeason == season && remoteEpisode == episode)
        val localPosition = local?.takeIf { localMatches }?.playbackPosition?.coerceAtLeast(0L)
        val remotePosition = remoteItem?.takeIf { remoteMatches }?.historyLong("playbackPosition")?.coerceAtLeast(0L)
        val localUpdated = local?.lastWatched ?: 0L
        val remoteUpdated = remoteItem?.historyLong("updatedAt") ?: 0L
        val resumePosition = explicitPosition ?: when {
            localPosition == null -> remotePosition ?: 0L
            remotePosition == null -> localPosition
            remoteUpdated > localUpdated -> remotePosition
            else -> localPosition
        }

        val title = intent.getStringExtra(DirectStreamActivity.EXTRA_TITLE).orEmpty()
        val overview = intent.getStringExtra(DirectStreamActivity.EXTRA_OVERVIEW).orEmpty()
        val poster = intent.getStringExtra(DirectStreamActivity.EXTRA_POSTER_PATH)
        val backdrop = intent.getStringExtra(DirectStreamActivity.EXTRA_BACKDROP_URL)
        val vote = intent.getDoubleExtra(DirectStreamActivity.EXTRA_VOTE_AVERAGE, 0.0)
        val releaseDate = intent.getStringExtra(DirectStreamActivity.EXTRA_RELEASE_DATE)
        if (local == null) {
            DatabaseManager.addToWatchHistoryAsync(
                id = tmdbId,
                mediaType = mediaType,
                title = title,
                overview = overview,
                posterPath = poster,
                backdropPath = backdrop,
                voteAverage = vote,
                releaseDate = releaseDate,
                seasonNumber = season.takeIf { isTv },
                episodeNumber = episode.takeIf { isTv },
                playbackPosition = resumePosition
            )
        } else {
            DatabaseManager.watchHistoryDao().updatePlaybackPosition(tmdbId, mediaType, resumePosition)
            if (isTv) DatabaseManager.watchHistoryDao().updateEpisodeInfo(tmdbId, mediaType, season ?: 1, episode ?: 1)
        }
        if (AuthManager.isSignedIn.value) {
            runCatching {
                withTimeoutOrNull(5_000L) {
                    FirebaseManager.syncWatchHistory(
                        tmdbId = tmdbId,
                        isTv = isTv,
                        seasonNumber = season.takeIf { isTv },
                        episodeNumber = episode.takeIf { isTv },
                        playbackPosition = resumePosition,
                        duration = remoteItem?.historyLong("duration") ?: 0L,
                        title = title,
                        overview = overview,
                        posterPath = poster,
                        backdropPath = backdrop,
                        voteAverage = vote,
                        releaseDate = releaseDate
                    ).await()
                }
            }
        }
        return resumePosition
    }

    private fun Map<*, *>.historyLong(key: String): Long = when (val value = this[key]) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    private fun Map<*, *>.historyInt(key: String): Int? = when (val value = this[key]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    private fun persistWatchProgress(positionOverrideMs: Long? = null) {
        if (!watchHistoryReady || activeStream == null) return
        val tmdbId = intent.getIntExtra(DirectStreamActivity.EXTRA_TMDB_ID, 0)
        if (tmdbId <= 0) return
        val isTv = intent.getStringExtra(DirectStreamActivity.EXTRA_TYPE) == DirectStreamActivity.TYPE_SERIES ||
            intent.getBooleanExtra(DirectStreamActivity.EXTRA_IS_TV, false)
        val mediaType = if (isTv) "tv" else "movie"
        val position = (positionOverrideMs ?: session.snapshot.value.positionMs).coerceAtLeast(0L)
        val duration = session.snapshot.value.durationMs.coerceAtLeast(0L)
        val season = intent.getIntExtra(DirectStreamActivity.EXTRA_SEASON, 1).coerceAtLeast(1)
        val episode = intent.getIntExtra(DirectStreamActivity.EXTRA_EPISODE, 1).coerceAtLeast(1)
        val title = intent.getStringExtra(DirectStreamActivity.EXTRA_TITLE).orEmpty()
        val overview = intent.getStringExtra(DirectStreamActivity.EXTRA_OVERVIEW).orEmpty()
        val poster = intent.getStringExtra(DirectStreamActivity.EXTRA_POSTER_PATH)
        val backdrop = intent.getStringExtra(DirectStreamActivity.EXTRA_BACKDROP_URL)
        val vote = intent.getDoubleExtra(DirectStreamActivity.EXTRA_VOTE_AVERAGE, 0.0)
        val releaseDate = intent.getStringExtra(DirectStreamActivity.EXTRA_RELEASE_DATE)
        DatabaseManager.updatePlaybackPosition(tmdbId, mediaType, position)
        if (isTv) DatabaseManager.updateEpisodeInfo(tmdbId, mediaType, season, episode)
        if (!AuthManager.isSignedIn.value) return
        val previousWrite = progressWriteJob
        progressWriteJob = lifecycleScope.launch(Dispatchers.IO) {
            previousWrite?.join()
            runCatching {
                withTimeoutOrNull(5_000L) {
                    FirebaseManager.syncWatchHistory(
                        tmdbId = tmdbId,
                        isTv = isTv,
                        seasonNumber = season.takeIf { isTv },
                        episodeNumber = episode.takeIf { isTv },
                        playbackPosition = position,
                        duration = duration,
                        title = title,
                        overview = overview,
                        posterPath = poster,
                        backdropPath = backdrop,
                        voteAverage = vote,
                        releaseDate = releaseDate
                    ).await()
                }
            }
        }
    }

    private fun parseSniffedStream(): StreamItem? {
        val url = intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_URL)?.takeIf(String::isNotBlank) ?: return null
        val headers = linkedMapOf<String, String>()
        intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_HEADERS)?.let { raw ->
            runCatching { JSONObject(raw) }.getOrNull()?.let { json -> json.keys().forEach { headers[it] = json.optString(it) } }
        }
        intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_COOKIE)?.takeIf(String::isNotBlank)?.let { headers.putIfAbsent("Cookie", it) }
        return StreamItem(title = "Captured stream", name = "Captured stream", url = url,
            quality = "Auto", provider = "Web Sniffer", type = intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_TYPE).orEmpty(),
            mimeType = intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_MIME_TYPE).orEmpty(), headers = headers,
            subtitles = parseSniffedSubtitles())
    }

    private fun parseSniffedSubtitles(): List<SubtitleItem> {
        val encoded = intent.getStringExtra(DirectStreamActivity.EXTRA_SNIFFED_SUBTITLES) ?: return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    val mime = item.optString("mimeType")
                    if (url.isBlank() || mime.isBlank()) continue
                    val headers = linkedMapOf<String, String>()
                    item.optJSONObject("headers")?.let { obj -> obj.keys().forEach { key -> obj.optString(key).takeIf(String::isNotBlank)?.let { headers[key] = it } } }
                    item.optString("cookie").takeIf(String::isNotBlank)?.let { cookie -> if (headers.keys.none { it.equals("Cookie", true) }) headers["Cookie"] = cookie }
                    add(SubtitleItem(url, mime, item.optString("language").takeUnless { it == "null" }, item.optString("label").takeUnless { it == "null" }, headers))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun wireControls() {
        binding.btnPlayerBack.setOnClickListener { finish() }
        binding.btnPlayerSettings.setOnClickListener { startActivity(MainActivity.createSettingsIntent(this)) }
        binding.btnPlayPause.setOnClickListener { session.togglePause() }
        binding.btnRewind.setOnClickListener { session.seekBy(-30_000L) }
        binding.btnForward.setOnClickListener { session.seekBy(30_000L) }
        binding.btnFill.setOnClickListener { aspectIndex = (aspectIndex + 1) % aspectModes.size; session.setAspectMode(aspectModes[aspectIndex]) }
        binding.btnVolume.setOnClickListener { muted = !muted; session.setMuted(muted); binding.btnVolume.setIconResource(if (muted) R.drawable.ic_vol_mute else R.drawable.ic_vol) }
        binding.btnReloadStreams.setOnClickListener { reloadStreams() }
        binding.btnPlayerStreams.setOnClickListener { showStreams() }
        binding.btnPlayerTracks.setOnClickListener { showTracks() }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar?) { seeking = true }
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && durationMs > 0L) binding.tvCurrentTime.text = clock(durationMs * progress / 1000L)
            }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                if (durationMs > 0L) session.seekTo(durationMs * (bar?.progress ?: 0) / 1000L)
                seeking = false
            }
        })
        binding.btnPlayerSubtitles.setOnClickListener { searchExternalSubtitles() }
        binding.btnEpisodes.setOnClickListener { showEpisodeJump() }
        binding.btnPreviousEpisode.setOnClickListener { changeEpisode(-1) }
        binding.btnNextEpisode.setOnClickListener { changeEpisode(1) }
        binding.overlayControls.setOnClickListener { binding.btnPlayPause.requestFocus() }
        binding.root.isFocusableInTouchMode = true
        binding.seekBar.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> handleSeekBarRemoteKey(event, -1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> handleSeekBarRemoteKey(event, 1)
                else -> false
            }
        }
        val isSeries = intent.getStringExtra(DirectStreamActivity.EXTRA_TYPE) == DirectStreamActivity.TYPE_SERIES || intent.getBooleanExtra(DirectStreamActivity.EXTRA_IS_TV, false)
        if (isSeries) {
            binding.btnEpisodes.visibility = View.VISIBLE
            binding.btnPreviousEpisode.visibility = View.VISIBLE
            binding.btnNextEpisode.visibility = View.VISIBLE
        }
        updateBottomFocusChain()
        binding.btnPlayPause.post { binding.btnPlayPause.requestFocus() }
    }

    private fun beginPlayback(stream: StreamItem) {
        playbackGeneration++
        val position = if (activeStream == null) resumePositionMs else currentPosition()
        Log.i(
            TAG,
            "beginPlayback: generation=$playbackGeneration positionMs=$position " +
                "surfaceReady=$surfaceReady sessionReady=$sessionReady ${streamSummary(stream)}"
        )
        activeStream = stream
        pendingStream = stream
        binding.playerStatus.visibility = View.GONE
        binding.tvPlayerTitle.text = listOf(intent.getStringExtra(DirectStreamActivity.EXTRA_TITLE).orEmpty(), stream.name.ifBlank { stream.title })
            .filter(String::isNotBlank).joinToString(" · ")
        binding.loadingArtwork.visibility = View.VISIBLE
        binding.btnPlayerStreams.visibility = if (streams.size > 1) View.VISIBLE else View.GONE
        updateBottomFocusChain()
        if (surfaceReady && sessionReady) loadPending(position)
        else Log.i(TAG, "beginPlayback: waiting for surface/session initialization")
    }

    /**
     * Validate streams independently of playback so the picker can replace its
     * initial Unverified badges with real probe results without delaying startup.
     */
    private fun validateStreamsInBackground(items: List<StreamItem>) {
        if (items.isEmpty()) return
        validationJob?.cancel()
        Log.i(TAG, "validateStreamsInBackground: starting validation for ${items.size} streams")
        validationJob = lifecycleScope.launch {
            runCatching { StreamValidator.validateAll(items) }
                .onSuccess { validated ->
                    if (isFinishing || isDestroyed) return@onSuccess
                    streams = validated.toList()
                    streamDialog?.updateStreams(
                        streams,
                        activeIndex = streams.indexOfFirst { it === activeStream }.takeIf { it >= 0 }
                    )
                    val valid = validated.count { it.isValid && !it.isFailed }
                    val checking = validated.count { it.isChecking }
                    val failed = validated.count { it.isFailed }
                    Log.i(
                        TAG,
                        "validateStreamsInBackground: completed total=${validated.size} " +
                            "valid=$valid failed=$failed checking=$checking " +
                            "statuses=${validated.joinToString { streamStatusSummary(it) }}"
                    )
                }
                .onFailure { error ->
                    Log.i(TAG, "validateStreamsInBackground: validation failed: ${error.message}", error)
                }
        }
    }

    private fun loadPending(position: Long = currentPosition(), generation: Int = playbackGeneration) {
        val stream = pendingStream ?: run {
            Log.i(TAG, "loadPending: no pending stream; generation=$generation")
            return
        }
        pendingStream = null
        val requestHeaders = playbackHeaders(stream)
        val refreshMega = MegaPlaybackToken.needsRefresh(stream)
        val refreshVidsrc = VidsrcPlaybackToken.needsRefresh(stream)
        Log.i(
            TAG,
            "loadPending: generation=$generation positionMs=$position refreshMega=$refreshMega " +
                "refreshVidsrc=$refreshVidsrc headers=${requestHeaders.keys} ${streamSummary(stream)}"
        )
        if (!refreshMega && !refreshVidsrc) {
            loadResolvedStream(stream, requestHeaders, position, generation)
            return
        }
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    when {
                        refreshMega -> MegaPlaybackToken.refresh(stream, requestHeaders)
                        refreshVidsrc -> VidsrcPlaybackToken.refresh(stream, requestHeaders)
                        else -> stream
                    }
                }
            }.onSuccess { refreshed ->
                Log.i(TAG, "loadPending: token refresh succeeded generation=$generation ${streamSummary(refreshed)}")
                if (generation == playbackGeneration) {
                    loadResolvedStream(refreshed, playbackHeaders(refreshed), position, generation)
                } else {
                    Log.i(TAG, "loadPending: ignoring refreshed stream from stale generation=$generation")
                }
            }.onFailure { error ->
                Log.i(TAG, "loadPending: token refresh failed generation=$generation: ${error.message}", error)
                if (generation == playbackGeneration) {
                    showPlaybackError(
                        if (refreshVidsrc) "Could not prepare Vidsrc playback: ${error.message ?: "retry for a fresh token"}"
                        else "Could not prepare Mega playback: ${error.message ?: "retry for a fresh token"}"
                    )
                }
            }
        }
    }

    private fun loadResolvedStream(
        stream: StreamItem,
        headers: Map<String, String>,
        position: Long,
        generation: Int = playbackGeneration
    ) {
        lifecycleScope.launch {
            val playbackUrl = normalizePlaybackUrl(stream)
            Log.i(
                TAG,
                "loadResolvedStream: calling session.load generation=$generation " +
                    "positionMs=$position headers=${headers.keys} url=${safeUrl(playbackUrl)} ${streamSummary(stream)}"
            )
            runCatching {
                session.load(playbackUrl, headers, startPositionMs = position.coerceAtLeast(0L))
            }.onSuccess {
                Log.i(TAG, "loadResolvedStream: session.load accepted generation=$generation")
                // A previous pause state can survive a libmpv loadfile command.
                // Explicitly resume so choosing a stream always starts playback.
                session.setPaused(false)
                Log.i(TAG, "loadResolvedStream: requested playback resume generation=$generation")
            }.onFailure { error ->
                Log.i(TAG, "loadResolvedStream: session.load failed generation=$generation: ${error.message}", error)
                if (generation == playbackGeneration) showPlaybackError(error.message ?: "libmpv could not load stream")
                return@launch
            }
            if (stream.subtitles.isEmpty()) return@launch
            val (subtitlePairs, failedSubtitles) = withContext(Dispatchers.IO) {
                prepareProviderSubtitles(stream.subtitles)
            }
            if (generation != playbackGeneration || isFinishing || isDestroyed) return@launch
            if (failedSubtitles > 0) {
                Toast.makeText(
                    this@LibMpvDirectStreamActivity,
                    "${failedSubtitles} provider subtitle track(s) could not be loaded",
                    Toast.LENGTH_LONG
                ).show()
            }
            if (subtitlePairs.isEmpty()) return@launch
            val loaded = withTimeoutOrNull(60_000L) {
                session.snapshot.first { !it.loading || it.error != null }
            } ?: return@launch
            if (loaded.error != null || generation != playbackGeneration || isFinishing || isDestroyed) return@launch
            subtitlePairs.forEach { (url, label) -> session.addSubtitle(url, label) }
        }
    }

    /** Copy external subtitle tracks into a private local cache so libmpv does not
     * need to reuse the media URL's headers for a subtitle host with different auth. */
    private fun prepareProviderSubtitles(subtitles: List<SubtitleItem>): Pair<List<Pair<String, String>>, Int> {
        if (subtitles.isEmpty()) return emptyList<Pair<String, String>>() to 0
        val cache = File(cacheDir, "libmpv-subtitles").apply { mkdirs() }
        var failures = 0
        val prepared = subtitles.mapNotNull { subtitle ->
            var outputFile: File? = null
            runCatching {
                val uri = Uri.parse(subtitle.url)
                val extension = subtitleExtension(uri, subtitle.mimeType)
                val keyMaterial = subtitle.url + "\n" + subtitle.headers.toSortedMap().entries.joinToString("\n") { "${it.key}:${it.value}" }
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(keyMaterial.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val destination = File(cache, "$digest.$extension")
                outputFile = destination
                if (!destination.isFile || destination.length() == 0L) {
                    val temporary = File.createTempFile("subtitle-", ".$extension", cache)
                    try {
                        val input: InputStream? = when (uri.scheme?.lowercase(Locale.ROOT)) {
                            "http", "https" -> {
                                val request = Request.Builder().url(subtitle.url).apply {
                                    subtitle.headers.forEach { (name, value) ->
                                        require(name.isNotBlank() && !name.contains('\r') && !name.contains('\n')) { "Invalid subtitle header name" }
                                        require(!value.contains('\r') && !value.contains('\n')) { "Invalid subtitle header value" }
                                        header(name, value)
                                    }
                                }.build()
                                subtitleHttpClient.newCall(request).execute().use { response ->
                                    check(response.isSuccessful) { "Subtitle request returned HTTP ${response.code}" }
                                    val body = checkNotNull(response.body) { "Subtitle response was empty" }
                                    check(body.contentLength() <= MAX_PROVIDER_SUBTITLE_BYTES) { "Subtitle file is too large" }
                                    copySubtitle(body.byteStream(), temporary)
                                }
                                null
                            }
                            "content" -> contentResolver.openInputStream(uri)
                                ?: error("Could not open subtitle content")
                            "file" -> uri.path?.let(::File)?.let(::FileInputStream)
                                ?: error("Could not open subtitle file")
                            else -> error("Unsupported subtitle URL scheme")
                        }
                        if (input != null) copySubtitle(input, temporary)
                        check(temporary.isFile && temporary.length() > 0L) { "Subtitle file is empty" }
                        if (!temporary.renameTo(destination)) {
                            temporary.copyTo(destination, overwrite = true)
                        }
                    } finally {
                        temporary.delete()
                    }
                }
                check(destination.isFile && destination.length() > 0L) { "Subtitle file is empty" }
                destination.toURI().toString() to (subtitle.label ?: subtitle.language ?: "Subtitle")
            }.onFailure {
                failures++
                outputFile?.delete()
            }.getOrNull()
        }
        return prepared to failures
    }

    private fun copySubtitle(input: InputStream, destination: File) {
        input.use { source ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MAX_PROVIDER_SUBTITLE_BYTES) { "Subtitle file is too large" }
                    output.write(buffer, 0, count)
                }
            }
        }
    }

    private fun subtitleExtension(uri: Uri, mimeType: String): String {
        val pathExtension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        if (pathExtension in setOf("srt", "vtt", "ass", "ssa", "sub", "sup")) return pathExtension
        return when {
            mimeType.contains("vtt", ignoreCase = true) -> "vtt"
            mimeType.contains("ass", ignoreCase = true) -> "ass"
            mimeType.contains("ssa", ignoreCase = true) -> "ssa"
            mimeType.contains("subrip", ignoreCase = true) || mimeType.contains("srt", ignoreCase = true) -> "srt"
            else -> "srt"
        }
    }

    private fun reloadStreams(startFromBeginning: Boolean = false) {
        Log.i(TAG, "reloadStreams: startFromBeginning=$startFromBeginning currentGeneration=$playbackGeneration")
        validationJob?.cancel()
        playbackGeneration++
        if (parseSniffedStream() != null) { Toast.makeText(this, R.string.no_streams_dialog_message, Toast.LENGTH_SHORT).show(); return }
        val resume = if (startFromBeginning) 0L else currentPosition()
        persistWatchProgress(resume)
        resumePositionMs = resume
        activeStream = null
        pendingStream = null
        initializeRoute()
    }

    private fun playbackHeaders(stream: StreamItem): Map<String, String> {
        if (stream.provider.equals("Cinejoy", ignoreCase = true) ||
            stream.provider.equals("CineJoyTV", ignoreCase = true)
        ) {
            // Nebula rejects cookies and provider-specific headers; keep only
            // the browser identity and Cinejoy playlist Referer.
            return stream.headers.filterKeys { key ->
                key.equals("User-Agent", ignoreCase = true) ||
                    key.equals("Referer", ignoreCase = true)
            }
        }
        val result = linkedMapOf<String, String>()
        if (stream.provider.equals("DahmerMovies", ignoreCase = true)) {
            result["User-Agent"] = REAL_BROWSER_USER_AGENT
            result["Accept"] = "*/*"
            result["Referer"] = DAHMER_MOVIES_REFERER
        }
        stream.headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                val old = result.keys.firstOrNull { it.equals(name, ignoreCase = true) }
                if (old != null) result.remove(old)
                result[name] = value
            }
        }
        val userAgentEntry = result.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
        if (userAgentEntry == null) result["User-Agent"] = REAL_BROWSER_USER_AGENT
        else if (isObviouslyFakeUserAgent(userAgentEntry.value)) {
            result.remove(userAgentEntry.key)
            result["User-Agent"] = REAL_BROWSER_USER_AGENT
        }

        if (Uri.parse(stream.url).host?.equals("bcdn.hakunaymatata.com", ignoreCase = true) == true) {
            result.keys.filter { it.equals("User-Agent", ignoreCase = true) }.forEach(result::remove)
            result["User-Agent"] = HAKUNA_MATATA_USER_AGENT
        }
        if (result.keys.none { it.equals("Cookie", ignoreCase = true) }) {
            val host = runCatching { Uri.parse(stream.url).host }.getOrNull()
            val cloudflareCookie = host?.let { CloudflareBypassActivity.loadCookies(applicationContext, it) }
            val cookie = cloudflareCookie?.takeIf(String::isNotBlank)
                ?: HttpCookieStore.cookieHeader(stream.url)?.takeIf(String::isNotBlank)
            if (cookie != null) result["Cookie"] = cookie
        }
        return result
    }

    private fun isObviouslyFakeUserAgent(value: String): Boolean {
        val chrome = Regex("""Chrome/(\d+)""").find(value)?.groupValues?.get(1)?.toIntOrNull()
        val firefox = Regex("""Firefox/(\d+)""").find(value)?.groupValues?.get(1)?.toIntOrNull()
        val safari = Regex("""Version/(\d+)\.\d+""").find(value)?.groupValues?.get(1)?.toIntOrNull()
        return (chrome != null && chrome > 140) || (firefox != null && firefox > 140) || (safari != null && safari > 25)
    }

    private fun normalizePlaybackUrl(stream: StreamItem): String {
        val rawPrefix = "https://p.111477.xyz/bulk?u=https://a.111477.xyz"
        val encodedPrefix = "https://p.111477.xyz/bulk?u=https%3A%2F%2Fa.111477.xyz"
        return if (stream.provider.equals("DahmerMovies", ignoreCase = true) &&
            stream.url.startsWith(rawPrefix, ignoreCase = true)
        ) encodedPrefix + stream.url.substring(rawPrefix.length) else stream.url
    }

    private fun showStreams() {
        if (streams.isEmpty()) {
            Log.i(TAG, "showStreams: no streams available")
            return
        }
        val activeIndex = streams.indexOfFirst { it === activeStream }.takeIf { it >= 0 }
        Log.i(TAG, "showStreams: opening dialog total=${streams.size} activeIndex=$activeIndex")
        streams.forEachIndexed { index, stream ->
            Log.i(TAG, "showStreams: stream[$index] ${streamStatusSummary(stream)}")
        }
        streamDialog = StreamSelectionDialog(this, streams, activeIndex) { selected ->
            val selectedIndex = streams.indexOfFirst { it === selected }.takeIf { it >= 0 }
            Log.i(
                TAG,
                "showStreams: selected stream index=$selectedIndex " +
                    "dialogShowing=${streamDialog?.isShowing == true} ${streamStatusSummary(selected)}"
            )
            beginPlayback(selected)
        }
        streamDialog?.show()
        Log.i(TAG, "showStreams: dialog shown=${streamDialog?.isShowing == true}")
    }

    private fun showTracks(subtitlesOnly: Boolean = false) {
        val tracks = session.snapshot.value.tracks.filter { track ->
            val subtitle = track.type.equals("sub", true) || track.type.equals("subtitle", true) || track.type.equals("text", true)
            if (subtitlesOnly) subtitle else true
        }
        if (tracks.isEmpty()) { Toast.makeText(this, R.string.track_none_available, Toast.LENGTH_SHORT).show(); return }
        val labels = tracks.map { track -> "${if (track.selected) "✓ " else ""}${track.label.ifBlank { track.language.orEmpty().ifBlank { track.type } }}${if (track.external) " (external)" else ""}" }.toTypedArray()
        trackDialog = MaterialAlertDialogBuilder(this).setTitle(if (subtitlesOnly) R.string.subdl_subtitles_cd else R.string.track_settings_cd)
            .setItems(labels) { _, which -> tracks.getOrNull(which)?.let { session.selectTrack(it.id, it.type) } }.create().also { it.show() }
    }

    private fun searchExternalSubtitles() {
        if (subtitleJob?.isActive == true || subtitleDialog?.isShowing == true) return
        val subdl = SubdlSubtitleClient(applicationContext)
        val openSubtitles = OpenSubtitlesClient(applicationContext)
        if (!subdl.isConfigured && !openSubtitles.isConfigured) {
            Toast.makeText(this, R.string.subtitle_provider_key_missing, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.subtitle_searching, Toast.LENGTH_SHORT).show()
        subtitleJob = lifecycleScope.launch {
            val tmdbId = intent.getIntExtra(DirectStreamActivity.EXTRA_TMDB_ID, 0)
            val isTv = intent.getStringExtra(DirectStreamActivity.EXTRA_TYPE) == DirectStreamActivity.TYPE_SERIES ||
                intent.getBooleanExtra(DirectStreamActivity.EXTRA_IS_TV, false)
            val season = intent.getIntExtra(DirectStreamActivity.EXTRA_SEASON, 0).takeIf { it > 0 }
            val episode = intent.getIntExtra(DirectStreamActivity.EXTRA_EPISODE, 0).takeIf { it > 0 }
            val choices = mutableListOf<SubtitleChoice>()
            val failures = mutableListOf<Throwable>()
            if (subdl.isConfigured) {
                runCatching { subdl.search(tmdbId, isTv, season, episode) }
                    .onSuccess { results -> choices += results.map { result ->
                        SubtitleChoice("SubDL • ${result.displayName}") { subdl.download(result) }
                    } }
                    .onFailure(failures::add)
            }
            if (openSubtitles.isConfigured) {
                runCatching { openSubtitles.search(tmdbId, isTv, season, episode) }
                    .onSuccess { results -> choices += results.map { result ->
                        SubtitleChoice("OpenSubtitles • ${result.displayName}") { openSubtitles.download(result) }
                    } }
                    .onFailure(failures::add)
            }
            if (choices.isEmpty()) {
                val message = failures.firstOrNull()?.let {
                    getString(R.string.subtitle_search_failed, it.message ?: it.javaClass.simpleName)
                } ?: getString(R.string.subtitle_no_results)
                Toast.makeText(this@LibMpvDirectStreamActivity, message, Toast.LENGTH_LONG).show()
            } else {
                val labels = choices.map { it.label }.toTypedArray()
                subtitleDialog = MaterialAlertDialogBuilder(this@LibMpvDirectStreamActivity)
                    .setTitle(R.string.subtitle_choose)
                    .setIcon(R.drawable.ic_closed_caption)
                    .setSingleChoiceItems(labels, -1) { dialog, index ->
                        dialog.dismiss()
                        downloadSubtitle(choices[index])
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create().also { dialog ->
                        dialog.setOnDismissListener { subtitleDialog = null }
                        dialog.show()
                    }
            }
        }
    }

    private fun downloadSubtitle(choice: SubtitleChoice) {
        subtitleJob?.cancel()
        Toast.makeText(this, R.string.subtitle_downloading, Toast.LENGTH_SHORT).show()
        subtitleJob = lifecycleScope.launch {
            runCatching { choice.download() }
                .onSuccess { subtitle ->
                    session.addSubtitle(subtitle.url, subtitle.label ?: subtitle.language ?: choice.label)
                    Toast.makeText(this@LibMpvDirectStreamActivity,
                        getString(R.string.subtitle_loaded, subtitle.language ?: "subtitle"), Toast.LENGTH_SHORT).show()
                }
                .onFailure { error ->
                    Toast.makeText(this@LibMpvDirectStreamActivity,
                        getString(R.string.subtitle_download_failed, error.message ?: error.javaClass.simpleName), Toast.LENGTH_LONG).show()
                }
        }
    }

    private data class SubtitleChoice(val label: String, val download: suspend () -> SubtitleItem)

    /** Constants shared by subtitle handling, remote seeking, and media controls. */
    companion object {
        private const val TAG = "LibMpvDirectStream"
        private const val MAX_PROVIDER_SUBTITLE_BYTES = 20L * 1024L * 1024L
        private const val REAL_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        private const val HAKUNA_MATATA_USER_AGENT = "ExoPlayer/1.6.1"
        private const val DAHMER_MOVIES_REFERER = "https://a.111477.xyz/"
        /** Number of SeekBar progress units moved by one remote D-pad step. */
        private const val REMOTE_SEEK_PROGRESS_STEP = 10
        /** Duration, in milliseconds, used by media rewind and fast-forward keys. */
        private const val SEEK_STEP_MS = 30_000L
    }

    private fun showEpisodeJump() {
        val currentSeason = intent.getIntExtra(DirectStreamActivity.EXTRA_SEASON, 1).coerceAtLeast(1)
        val currentEpisode = intent.getIntExtra(DirectStreamActivity.EXTRA_EPISODE, 1).coerceAtLeast(1)
        val input = android.widget.EditText(this).apply { setText(currentEpisode.toString()); inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        MaterialAlertDialogBuilder(this).setTitle("Go to episode (season $currentSeason)").setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val nextEpisode = input.text.toString().toIntOrNull()?.takeIf { it > 0 } ?: return@setPositiveButton
                intent.putExtra(DirectStreamActivity.EXTRA_EPISODE, nextEpisode)
                reloadStreams(startFromBeginning = true)
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun changeEpisode(delta: Int) {
        val episode = intent.getIntExtra(DirectStreamActivity.EXTRA_EPISODE, 1).coerceAtLeast(1) + delta
        if (episode > 0) { intent.putExtra(DirectStreamActivity.EXTRA_EPISODE, episode); reloadStreams(startFromBeginning = true) }
    }

    private fun showStatus(message: String, retry: Boolean) {
        binding.playerStatus.text = message
        binding.playerStatus.visibility = View.VISIBLE
        binding.playerStatus.setOnClickListener {
            if (retry) initializeRoute()
        }
    }

    private fun showProviderFetchProgress() {
        binding.providerFetchProgress.reset()
        binding.providerFetchProgress.alpha = 1f
        binding.providerFetchProgress.visibility = View.VISIBLE
        binding.providerFetchProgress.setProviderProgress(completed = 0, total = 0)
    }

    private fun updateProviderFetchProgress(completed: Int, total: Int) {
        if (binding.providerFetchProgress.visibility == View.VISIBLE) {
            binding.providerFetchProgress.setProviderProgress(completed, total)
        }
    }

    private fun showStreamFetchCount() {
        binding.streamFetchCount.text = getString(R.string.stream_fetch_count, 0)
        binding.streamFetchCount.alpha = 1f
        binding.streamFetchCount.visibility = View.VISIBLE
    }

    private fun updateStreamFetchCount(fetchedCount: Int) {
        if (binding.streamFetchCount.visibility == View.VISIBLE) {
            binding.streamFetchCount.text = getString(
                R.string.stream_fetch_count,
                fetchedCount.coerceAtLeast(0)
            )
        }
    }

    private fun hideStreamFetchCount() {
        binding.streamFetchCount.animate().cancel()
        binding.streamFetchCount.visibility = View.GONE
        binding.streamFetchCount.alpha = 1f
    }

    private fun hideProviderFetchProgress() {
        binding.providerFetchProgress.animate().cancel()
        binding.providerFetchProgress.visibility = View.GONE
        binding.providerFetchProgress.alpha = 1f
    }

    private fun showLoading() {
        binding.loadingArtwork.visibility = View.VISIBLE
        binding.bufferingOverlay.visibility = View.GONE
    }

    private fun updateBottomFocusChain() {
        val controls = listOf(
            binding.btnPreviousEpisode,
            binding.btnFill,
            binding.btnPlayerSubtitles,
            binding.btnPlayerTracks,
            binding.btnPlayerStreams,
            binding.btnReloadStreams,
            binding.btnVolume,
            binding.btnNextEpisode,
            binding.btnEpisodes
        ).filter { it.visibility == View.VISIBLE && it.isFocusable }

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
        if (durationMs <= 0L) return true
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                remoteSeekInProgress = true
                seeking = true
                val nextProgress = (
                    binding.seekBar.progress + direction * REMOTE_SEEK_PROGRESS_STEP
                ).coerceIn(0, binding.seekBar.max)
                binding.seekBar.progress = nextProgress
                binding.tvCurrentTime.text = clock(durationMs * nextProgress / 1000L)
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
        session.seekTo(durationMs * binding.seekBar.progress / 1000L)
        seeking = false
    }

    private fun showError(message: String) {
        binding.loadingArtwork.visibility = View.VISIBLE
        showStatus(message, retry = true)
    }
    private fun showPlaybackError(message: String) {
        if (message == lastShownPlaybackError) return
        lastShownPlaybackError = message
        binding.loadingArtwork.visibility = View.VISIBLE
        showStatus(message, retry = true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        Log.i(TAG, "surfaceCreated: valid=${holder.surface?.isValid} sessionReady=$sessionReady")
        lifecycleScope.launch {
            runCatching { session.initialize(holder.surface) }
                .onSuccess {
                    sessionReady = true
                    Log.i(TAG, "surfaceCreated: libmpv session initialized")
                    session.setSurfaceSize(surfaceView.width, surfaceView.height)
                    pendingStream?.let { loadPending(if (activeStream == null) resumePositionMs else currentPosition()) }
                }
                .onFailure {
                    Log.i(TAG, "surfaceCreated: libmpv initialization failed: ${it.message}", it)
                    showError(it.message ?: "Could not initialize libmpv")
                }
        }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        session.setSurfaceSize(width, height)
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        Log.i(TAG, "surfaceDestroyed: detaching libmpv surface")
        session.detachSurface()
    }

    private fun currentPosition(): Long = session.snapshot.value.positionMs.coerceAtLeast(0L)

    private fun normalizeArtworkUrl(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        else -> "https://image.tmdb.org/t/p/original/${path.trimStart('/')}"
    }

    private fun clock(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000L
        return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    }
    private fun enterImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply { hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enterImmersive() }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::session.isInitialized) return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) session.togglePause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) session.setPaused(false)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) session.setPaused(true)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) session.seekBy(-SEEK_STEP_MS)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) session.seekBy(SEEK_STEP_MS)
                return true
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) finish()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onPause() {
        persistWatchProgress()
        wasPlaying = !session.snapshot.value.paused
        if (wasPlaying) session.setPaused(true)
        super.onPause()
    }
    override fun onResume() { super.onResume(); if (::session.isInitialized && wasPlaying) { wasPlaying = false; session.setPaused(false) } }
    override fun onDestroy() {
        Log.i(TAG, "onDestroy: generation=$playbackGeneration active=${activeStream?.let(::streamStatusSummary)}")
        resolverJob?.cancel()
        validationJob?.cancel()
        subtitleJob?.cancel()
        streamDialog?.dismiss()
        trackDialog?.dismiss()
        subtitleDialog?.dismiss()
        surfaceView.holder.removeCallback(this)
        if (::session.isInitialized) session.release()
        super.onDestroy()
    }

    /** Compact diagnostic representation that avoids logging signed URL query tokens. */
    private fun streamSummary(stream: StreamItem): String =
        "provider=${stream.provider.ifBlank { "?" }} name=${stream.name.ifBlank { "?" }} " +
            "quality=${stream.quality.ifBlank { "?" }} language=${stream.language.ifBlank { "?" }} " +
            "url=${safeUrl(stream.url)}"

    /** Include validation state and HTTP status in picker/playback diagnostics. */
    private fun streamStatusSummary(stream: StreamItem): String =
        "${streamSummary(stream)} valid=${stream.isValid} checking=${stream.isChecking} " +
            "failed=${stream.isFailed} http=${stream.httpStatusCode ?: "?"}"

    /** Redact signed query strings while retaining enough URL detail for Logcat diagnosis. */
    private fun safeUrl(url: String): String = runCatching {
        val uri = Uri.parse(url)
        buildString {
            append(uri.scheme.orEmpty())
            append("://")
            append(uri.host.orEmpty())
            append(uri.path.orEmpty())
            if (!uri.query.isNullOrBlank()) append("?[query]")
        }
    }.getOrDefault("<invalid-url>")

}
