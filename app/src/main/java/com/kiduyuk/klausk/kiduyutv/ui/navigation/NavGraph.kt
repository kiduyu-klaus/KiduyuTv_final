package com.kiduyuk.klausk.kiduyutv.ui.navigation

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kiduyuk.klausk.kiduyutv.ui.screens.company_network_list.tv.MediaListScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.search.tv.SearchScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.settings.tv.SettingsScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv.MovieDetailScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv.SeasonEpisodesScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv.StreamLinksScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.tv.TvShowDetailScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.cast.tv.CastDetailScreen
import com.kiduyuk.klausk.kiduyutv.data.model.CastMember
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv.HomeScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv.MoviesScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv.MyListScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.tv.TvShowsScreen
import com.kiduyuk.klausk.kiduyutv.viewmodel.SearchViewModelFactory
import com.kiduyuk.klausk.kiduyutv.viewmodel.SearchViewModel

/**
 * Main navigation graph for the application.
 * Defines all the screens and their navigation logic using Jetpack Compose Navigation.
 *
 * @param navController The [NavHostController] responsible for managing app navigation.
 */
@RequiresApi(Build.VERSION_CODES.O)
@androidx.media3.common.util.UnstableApi
@OptIn(UnstableApi::class)
@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // Home Screen: The main landing page with hero and mixed content.
        composable(Screen.Home.route) {
            Box(modifier = Modifier.fillMaxSize()) {
                HomeScreen(
                    onMovieClick = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId))
                    },
                    onTvShowClick = { tvId ->
                        navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                    },
                    onNavigate = { route ->
                        if (route != Screen.Home.route) {
                            navController.navigate(route)
                        }
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )

                // Overlay a non-intrusive banner at the bottom when on TV flavour
                /*if (BuildConfig.FLAVOR == "tv") {
                    TvBannerAdView(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(90.dp)
                            .background(Color(0xCC000000))
                    )
                }*/
            }
        }

        // Movies Screen: Dedicated screen for browsing movies.
        composable(Screen.Movies.route) {
            MoviesScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onNavigate = { route ->
                    if (route != Screen.Movies.route) {
                        navController.navigate(route)
                    }
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // TV Shows Screen: Dedicated screen for browsing TV shows.
        composable(Screen.TvShows.route) {
            TvShowsScreen(
                onTvShowClick = { tvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                },
                onNavigate = { route ->
                    if (route != Screen.TvShows.route) {
                        navController.navigate(route)
                    }
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // My List Screen: Screen displaying the user's saved movies and TV shows.
        composable(Screen.MyList.route) {
            MyListScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onTvShowClick = { tvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                },
                onNavigate = { route ->
                    if (route != Screen.MyList.route) {
                        navController.navigate(route)
                    }
                },
                onSearchClick = {
                    navController.navigate(Screen.Search.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onCompanyClick = { id, name ->
                    navController.navigate("media_list/company/$id/$name")
                },
                onNetworkClick = { id, name ->
                    navController.navigate("media_list/network/$id/$name")
                },
                onCastClick = { castMember ->
                    navController.navigate(
                        "cast_detail/${castMember.id}/${Uri.encode(castMember.name)}/${Uri.encode(castMember.character ?: "")}/${Uri.encode(castMember.profilePath ?: "")}/${Uri.encode(castMember.knownForDepartment ?: "")}"
                    )
                }
            )
        }

        // Search Screen: Screen for searching movies and TV shows.
        composable(Screen.Search.route) {
            val context = LocalContext.current
            val application = context.applicationContext as Application
            val searchViewModel: SearchViewModel = viewModel(
                factory = SearchViewModelFactory(application)
            )
            SearchScreen(
                viewModel = searchViewModel,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onTvShowClick = { tvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                }
            )
        }

        // Movie Detail Screen: Detailed information about a specific movie.
        composable(
            route = Screen.MovieDetail.route,
            arguments = listOf(navArgument("movieId") { type = NavType.IntType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: return@composable
            MovieDetailScreen(
                movieId = movieId,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { newMovieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(newMovieId))
                },
                onCompanyClick = { id, name ->
                    navController.navigate("media_list/company/$id/$name")
                },
                onPlayClick = { route ->
                    navController.navigate(route)
                },
                onCastClick = { castId, castName, character, profilePath, knownForDepartment ->
                    navController.navigate(
                        "cast_detail/$castId/${Uri.encode(castName)}/${Uri.encode(character ?: "")}/${Uri.encode(profilePath ?: "")}/${Uri.encode(knownForDepartment ?: "")}"
                    )
                }
            )
        }

        // TV Show Detail Screen: Detailed information about a specific TV show.
        composable(
            route = Screen.TvShowDetail.route,
            arguments = listOf(navArgument("tvId") { type = NavType.IntType })
        ) { backStackEntry ->
            val tvId = backStackEntry.arguments?.getInt("tvId") ?: return@composable
            TvShowDetailScreen(
                tvId = tvId,
                onBackClick = { navController.popBackStack() },
                onTvShowClick = { newTvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(newTvId))
                },
                onEpisodesClick = { id, name, totalSeasons ->
                    navController.navigate(Screen.SeasonEpisodes.createRoute(id, name, totalSeasons))
                },
                onNetworkClick = { id, name ->
                    navController.navigate("media_list/network/$id/$name")
                },
                onPlayClick = { route ->
                    navController.navigate(route)
                },
                onCastClick = { castId, castName, character, profilePath, knownForDepartment ->
                    navController.navigate(
                        "cast_detail/$castId/${Uri.encode(castName)}/${Uri.encode(character ?: "")}/${Uri.encode(profilePath ?: "")}/${Uri.encode(knownForDepartment ?: "")}"
                    )
                }
            )
        }

        // Stream Links Screen: Screen for selecting a streaming provider.
        composable(
            route = Screen.StreamLinks.route,
            arguments = listOf(
                navArgument("tmdbId") { type = NavType.IntType },
                navArgument("isTv") { type = NavType.BoolType },
                navArgument("season") { type = NavType.IntType; defaultValue = 0 },
                navArgument("episode") { type = NavType.IntType; defaultValue = 0 },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("overview") { type = NavType.StringType; defaultValue = "" },
                navArgument("posterPath") { type = NavType.StringType; defaultValue = "" },
                navArgument("backdropPath") { type = NavType.StringType; defaultValue = "" },
                navArgument("voteAverage") { type = NavType.FloatType; defaultValue = 0f },
                navArgument("releaseDate") { type = NavType.StringType; defaultValue = "" },
                navArgument("timestamp") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val tmdbId = backStackEntry.arguments?.getInt("tmdbId") ?: 0
            val isTv = backStackEntry.arguments?.getBoolean("isTv") ?: false
            val season = backStackEntry.arguments?.getInt("season")
            val episode = backStackEntry.arguments?.getInt("episode")
            val title = backStackEntry.arguments?.getString("title") ?: ""
            val overview = backStackEntry.arguments?.getString("overview") ?: ""
            val posterPath = backStackEntry.arguments?.getString("posterPath")
            val backdropPath = backStackEntry.arguments?.getString("backdropPath")
            val voteAverage = backStackEntry.arguments?.getFloat("voteAverage")?.toDouble() ?: 0.0
            val releaseDate = backStackEntry.arguments?.getString("releaseDate")
            val timestamp = backStackEntry.arguments?.getLong("timestamp") ?: 0L

            StreamLinksScreen(
                tmdbId = tmdbId,
                isTv = isTv,
                title = title,
                overview = overview,
                posterPath = posterPath,
                backdropPath = backdropPath,
                voteAverage = voteAverage,
                releaseDate = releaseDate,
                season = if (season == 0) null else season,
                episode = if (episode == 0) null else episode,
                timestamp = timestamp,
                onBackClick = { navController.popBackStack() }
            )
        }

        // Season Episodes Screen: Dedicated screen for viewing episodes of a TV show season.
        composable(
            route = Screen.SeasonEpisodes.route,
            arguments = listOf(
                navArgument("tvId") { type = NavType.IntType },
                navArgument("totalSeasons") { type = NavType.IntType },
                navArgument("tvShowName") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val tvId = backStackEntry.arguments?.getInt("tvId") ?: return@composable
            val totalSeasons = backStackEntry.arguments?.getInt("totalSeasons") ?: 1
            val tvShowName = backStackEntry.arguments?.getString("tvShowName") ?: ""
            SeasonEpisodesScreen(
                tvShowId = tvId,
                tvShowName = tvShowName,
                totalSeasons = totalSeasons,
                onPlayClick = { route ->
                    navController.navigate(route)
                }
            )
        }

        // Media List Screen: Generic screen for showing media by company or network.
        composable(
            route = "media_list/{type}/{id}/{name}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("id") { type = NavType.IntType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "company"
            val id = backStackEntry.arguments?.getInt("id") ?: 0
            val name = backStackEntry.arguments?.getString("name") ?: ""

            MediaListScreen(
                type = type,
                id = id,
                name = name,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onTvShowClick = { tvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                }
            )
        }

        // Settings Screen: Screen for app settings, information, and version details.
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Cast Detail Screen: Screen for displaying all movies and TV shows by a cast member.
        composable(
            route = Screen.CastDetail.route,
            arguments = listOf(
                navArgument("castId") { type = NavType.IntType },
                navArgument("castName") { type = NavType.StringType },
                navArgument("character") { type = NavType.StringType; defaultValue = "" },
                navArgument("profilePath") { type = NavType.StringType; defaultValue = "" },
                navArgument("knownForDepartment") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val castId = backStackEntry.arguments?.getInt("castId") ?: 0
            val castName = backStackEntry.arguments?.getString("castName") ?: ""
            val character = backStackEntry.arguments?.getString("character")
            val profilePath = backStackEntry.arguments?.getString("profilePath")
            val knownForDepartment = backStackEntry.arguments?.getString("knownForDepartment")

            val castMember = CastMember(
                id = castId,
                name = Uri.decode(castName),
                character = Uri.decode(character ?: ""),
                profilePath = if (profilePath.isNullOrBlank()) null else Uri.decode(profilePath),
                knownForDepartment = Uri.decode(knownForDepartment ?: ""),
                popularity = null,
                order = null
            )

            CastDetailScreen(
                castMember = castMember,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onTvShowClick = { tvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                }
            )
        }
    }
}
