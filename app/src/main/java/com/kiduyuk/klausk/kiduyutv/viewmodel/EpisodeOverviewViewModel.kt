package com.kiduyuk.klausk.kiduyutv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.data.model.CrewMember
import com.kiduyuk.klausk.kiduyutv.data.model.Episode
import com.kiduyuk.klausk.kiduyutv.data.model.TvShowDetail
import com.kiduyuk.klausk.kiduyutv.data.repository.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EpisodeOverviewUiState(
    val isLoading: Boolean = true,
    val episode: Episode? = null,
    val tvShow: TvShowDetail? = null,
    val cast: List<CastMember> = emptyList(),
    val writingAndProductionCrew: List<CrewMember> = emptyList(),
    val error: String? = null
)

class EpisodeOverviewViewModel : ViewModel() {
    private val repository = TmdbRepository()
    private val _uiState = MutableStateFlow(EpisodeOverviewUiState())
    val uiState: StateFlow<EpisodeOverviewUiState> = _uiState.asStateFlow()

    fun loadEpisode(tvId: Int, seasonNumber: Int, episodeNumber: Int) {
        viewModelScope.launch {
            _uiState.value = EpisodeOverviewUiState(isLoading = true)
            try {
                val episodeDeferred = async {
                    repository.getEpisodeDetails(tvId, seasonNumber, episodeNumber)
                }
                val showDeferred = async { repository.getTvShowDetail(tvId) }
                val episode = episodeDeferred.await().getOrElse { throw it }
                val tvShow = showDeferred.await().getOrNull()
                val crewJobs = listOf(
                    "Screenplay", "Writer", "Story", "Director", "Series Director",
                    "Co-Director", "Producer", "Executive Producer", "Co-Producer",
                    "Line Producer", "Production Manager", "Production Supervisor"
                )
                val crew = episode.crew
                    .filter { member -> crewJobs.any { it.equals(member.job, ignoreCase = true) } }
                    .distinctBy { it.id to it.job }
                    .take(10)
                val cast = episode.guestStars
                    .sortedBy { it.order ?: Int.MAX_VALUE }
                    .take(20)
                _uiState.value = EpisodeOverviewUiState(
                    isLoading = false,
                    episode = episode,
                    tvShow = tvShow,
                    cast = cast,
                    writingAndProductionCrew = crew
                )
            } catch (error: Exception) {
                _uiState.value = EpisodeOverviewUiState(
                    isLoading = false,
                    error = error.message ?: "Failed to load episode details"
                )
            }
        }
    }
}
