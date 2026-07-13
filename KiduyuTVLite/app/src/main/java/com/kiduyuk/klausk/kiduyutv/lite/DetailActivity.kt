package com.kiduyuk.klausk.kiduyutv.lite

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.kiduyuk.klausk.kiduyutv.lite.api.TmdbApi
import com.kiduyuk.klausk.kiduyutv.lite.databinding.ActivityDetailBinding
import com.kiduyuk.klausk.kiduyutv.lite.model.Episode
import com.kiduyuk.klausk.kiduyutv.lite.model.Season
import com.kiduyuk.klausk.kiduyutv.lite.playback.LitePlaybackUrlBuilder
import com.kiduyuk.klausk.kiduyutv.lite.playback.LiteStreamProvider
import com.kiduyuk.klausk.kiduyutv.lite.playback.LiteStreamProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private var mediaId = 0
    private var mediaType = "movie"
    private var seasons: List<Season> = emptyList()
    private var seasonIndex = 0
    private var selectedEpisode = 1
    private var selectedProvider: LiteStreamProvider = LiteStreamProviders.default
    private var seasonJob: Job? = null
    private var episodeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaId = intent.getIntExtra(HomeActivity.EXTRA_ID, 0)
        mediaType = intent.getStringExtra(HomeActivity.EXTRA_MEDIA_TYPE) ?: "movie"
        selectedProvider = LiteStreamProviders.resolve(
            savedInstanceState?.getString(STATE_PROVIDER_NAME)
        )
        if (mediaId <= 0 || mediaType !in setOf("movie", "tv")) {
            Toast.makeText(this, R.string.empty_titles, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        setupProviderChooser()
        bindMetadata()
        if (mediaType == "movie") showMovieUi() else showTvUi()
    }

    override fun onDestroy() {
        seasonJob?.cancel()
        episodeJob?.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PROVIDER_NAME, selectedProvider.name)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun bindMetadata() {
        binding.detailTitle.text = intent.getStringExtra(HomeActivity.EXTRA_TITLE).orEmpty()
        binding.detailOverview.text = intent.getStringExtra(HomeActivity.EXTRA_OVERVIEW).orEmpty()
        binding.detailYear.text = intent.getStringExtra(HomeActivity.EXTRA_YEAR).orEmpty()

        val rating = intent.getDoubleExtra(HomeActivity.EXTRA_RATING, 0.0)
        binding.detailRating.text = getString(R.string.rating_format, rating)
        binding.detailTypeBadge.text = getString(
            if (mediaType == "movie") R.string.movie_badge else R.string.tv_series_badge
        )
        binding.detailTypeBadge.setBackgroundColor(
            getColor(if (mediaType == "movie") R.color.kiduyu_red else R.color.kiduyu_red_dark)
        )

        binding.detailPoster.load(intent.getStringExtra(HomeActivity.EXTRA_POSTER_URL)) {
            placeholder(R.drawable.placeholder)
            error(R.drawable.placeholder)
            crossfade(true)
        }
        binding.detailBackdrop.load(intent.getStringExtra(HomeActivity.EXTRA_BACKDROP_URL)) {
            crossfade(true)
        }
    }

    private fun showMovieUi() {
        binding.btnPlay.visibility = View.VISIBLE
        binding.btnPlay.setOnClickListener { launchPlayer() }
        binding.btnPlay.requestFocus()
    }

    private fun showTvUi() {
        binding.seasonRow.visibility = View.VISIBLE
        binding.labelEpisodes.visibility = View.VISIBLE
        binding.detailStatus.visibility = View.VISIBLE

        binding.btnSeasonPrev.setOnClickListener { stepSeason(-1) }
        binding.btnSeasonNext.setOnClickListener { stepSeason(1) }
        listOf(binding.btnSeasonPrev, binding.btnSeasonNext).forEach { button ->
            button.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.1f else 1f)
                    .scaleY(if (hasFocus) 1.1f else 1f)
                    .setDuration(120)
                    .start()
            }
        }

        binding.btnProvider.requestFocus()
        loadSeasons()
    }

    private fun setupProviderChooser() {
        updateProviderButton()
        binding.btnProvider.setOnClickListener { showProviderDialog() }
        binding.btnProvider.setOnFocusChangeListener { view, hasFocus ->
            view.animate()
                .scaleX(if (hasFocus) 1.06f else 1f)
                .scaleY(if (hasFocus) 1.06f else 1f)
                .setDuration(120)
                .start()
        }
    }

    private fun showProviderDialog() {
        val providers = LiteStreamProviders.all
        val selectedIndex = providers.indexOfFirst { it.name == selectedProvider.name }
            .coerceAtLeast(0)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.choose_provider)
            .setSingleChoiceItems(
                providers.map { it.name }.toTypedArray(),
                selectedIndex
            ) { activeDialog, index ->
                selectedProvider = providers[index]
                updateProviderButton()
                activeDialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnDismissListener { binding.btnProvider.requestFocus() }
        dialog.show()
    }

    private fun updateProviderButton() {
        binding.btnProvider.text = getString(
            R.string.provider_button_format,
            selectedProvider.name
        )
    }

    private fun loadSeasons() {
        seasonJob?.cancel()
        showStatus(getString(R.string.loading_titles), retryEnabled = false) {}

        seasonJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { TmdbApi.tvSeasons(mediaId) }
            }

            result.onSuccess { fetched ->
                seasons = fetched
                if (seasons.isEmpty()) {
                    showStatus(
                        getString(R.string.empty_titles),
                        retryEnabled = true,
                        action = ::loadSeasons
                    )
                } else {
                    seasonIndex = seasonIndex.coerceIn(seasons.indices)
                    updateSeasonDisplay()
                    loadEpisodes()
                }
            }.onFailure {
                showStatus(
                    getString(R.string.load_failed),
                    retryEnabled = true,
                    action = ::loadSeasons
                )
            }
        }
    }

    private fun stepSeason(delta: Int) {
        if (seasons.isEmpty()) return
        val nextIndex = (seasonIndex + delta).coerceIn(seasons.indices)
        if (nextIndex == seasonIndex) return
        seasonIndex = nextIndex
        selectedEpisode = 1
        updateSeasonDisplay()
        loadEpisodes()
    }

    private fun updateSeasonDisplay() {
        val season = seasons[seasonIndex]
        binding.tvSeasonCurrent.text = season.name
        binding.tvSeasonCount.text = getString(
            R.string.season_count_format,
            seasonIndex + 1,
            seasons.size
        )

        val hasPrevious = seasonIndex > 0
        val hasNext = seasonIndex < seasons.lastIndex
        binding.btnSeasonPrev.isEnabled = hasPrevious
        binding.btnSeasonPrev.alpha = if (hasPrevious) 1f else 0.3f
        binding.btnSeasonNext.isEnabled = hasNext
        binding.btnSeasonNext.alpha = if (hasNext) 1f else 0.3f
    }

    private fun loadEpisodes() {
        val season = seasons.getOrNull(seasonIndex) ?: return
        episodeJob?.cancel()
        binding.rvEpisodes.visibility = View.GONE
        showStatus(getString(R.string.loading_titles), retryEnabled = false) {}

        episodeJob = lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    TmdbApi.tvEpisodes(mediaId, season.seasonNumber)
                }
            }

            result.onSuccess { episodes ->
                if (episodes.isEmpty()) {
                    showStatus(
                        getString(R.string.empty_titles),
                        retryEnabled = true,
                        action = ::loadEpisodes
                    )
                } else {
                    bindEpisodes(episodes)
                }
            }.onFailure {
                showStatus(
                    getString(R.string.load_failed),
                    retryEnabled = true,
                    action = ::loadEpisodes
                )
            }
        }
    }

    private fun bindEpisodes(episodes: List<Episode>) {
        binding.detailStatus.visibility = View.GONE
        binding.rvEpisodes.visibility = View.VISIBLE
        binding.rvEpisodes.layoutManager = LinearLayoutManager(this)
        binding.rvEpisodes.adapter = EpisodeAdapter(episodes) { episode ->
            selectedEpisode = episode.episodeNumber
            launchPlayer()
        }
        binding.rvEpisodes.post {
            binding.rvEpisodes.findViewHolderForAdapterPosition(0)
                ?.itemView
                ?.requestFocus()
                ?: binding.btnSeasonNext.requestFocus()
        }
    }

    private fun showStatus(message: String, retryEnabled: Boolean, action: () -> Unit) {
        binding.detailStatus.text = message
        binding.detailStatus.visibility = View.VISIBLE
        binding.detailStatus.isClickable = retryEnabled
        binding.detailStatus.isFocusable = retryEnabled
        binding.detailStatus.setOnClickListener { if (retryEnabled) action() }
        if (retryEnabled) binding.detailStatus.requestFocus()
    }

    private fun launchPlayer() {
        val url = if (mediaType == "movie") {
            LitePlaybackUrlBuilder.movie(mediaId, selectedProvider.name)
        } else {
            val season = seasons.getOrNull(seasonIndex)?.seasonNumber ?: return
            LitePlaybackUrlBuilder.episode(
                mediaId,
                season,
                selectedEpisode,
                selectedProvider.name
            )
        } ?: run {
            Toast.makeText(this, R.string.playback_link_unavailable, Toast.LENGTH_LONG).show()
            return
        }

        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_URL, url)
        )
    }

    private inner class EpisodeAdapter(
        private val items: List<Episode>,
        private val onPick: (Episode) -> Unit
    ) : RecyclerView.Adapter<EpisodeAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.epNumber)
            val title: TextView = view.findViewById(R.id.epTitle)
            val overview: TextView = view.findViewById(R.id.epOverview)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_episode, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val episode = items[position]
            holder.number.text = episode.episodeNumber.toString()
            holder.title.text = episode.name
            holder.overview.text = episode.overview.ifBlank {
                getString(R.string.no_description)
            }
            holder.itemView.setOnClickListener { onPick(episode) }
            holder.itemView.setOnKeyListener { _, keyCode, event ->
                if (
                    event.action == KeyEvent.ACTION_UP &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    onPick(episode)
                    true
                } else {
                    false
                }
            }
            holder.itemView.setOnFocusChangeListener { view, hasFocus ->
                view.animate()
                    .scaleX(if (hasFocus) 1.02f else 1f)
                    .scaleY(if (hasFocus) 1.02f else 1f)
                    .setDuration(120)
                    .start()
            }
        }
    }

    companion object {
        private const val STATE_PROVIDER_NAME = "provider_name"
    }
}
