package com.kiduyuk.klausk.kiduyutv.ui.navigation

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.*
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.*
import com.kiduyuk.klausk.kiduyutv.ui.screens.search.MobileSearchScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.settings.MobileSettingsScreen

@androidx.media3.common.util.UnstableApi
@OptIn(UnstableApi::class)
@Composable
fun MobileNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            MobileHomeScreen(
                navController = navController,
                onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                onTvShowClick = { tvShowId -> navController.navigate(Screen.TvShowDetail.createRoute(tvShowId)) },
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable(Screen.Movies.route) {
            MobileMoviesScreen(
                navController = navController,
                onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) }
            )
        }

        composable(Screen.TvShows.route) {
            MobileTvShowsScreen(
                navController = navController,
                onTvShowClick = { tvShowId -> navController.navigate(Screen.TvShowDetail.createRoute(tvShowId)) }
            )
        }

        composable(Screen.Search.route) {
            MobileSearchScreen(
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                onTvShowClick = { tvShowId -> navController.navigate(Screen.TvShowDetail.createRoute(tvShowId)) }
            )
        }

        composable(Screen.Settings.route) {
            MobileSettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // See All Screen
        composable(
            route = "see_all/{category}",
            arguments = listOf(navArgument("category") { type = NavType.StringType })
        ) { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category") ?: ""
            SeeAllScreen(
                category = category,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                onTvShowClick = { tvShowId -> navController.navigate(Screen.TvShowDetail.createRoute(tvShowId)) }
            )
        }

        // Genres Screen
        composable(
            route = "genres/{mediaType}",
            arguments = listOf(navArgument("mediaType") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            GenresScreen(
                mediaType = mediaType,
                onBackClick = { navController.popBackStack() },
                onGenreClick = { genreId, genreName ->
                    navController.navigate("genre_content/$mediaType/$genreId/$genreName")
                }
            )
        }

        // Genre Content Screen (See All for a specific genre)
        composable(
            route = "genre_content/{mediaType}/{genreId}/{genreName}",
            arguments = listOf(
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("genreId") { type = NavType.IntType },
                navArgument("genreName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mediaType = backStackEntry.arguments?.getString("mediaType") ?: "movie"
            val genreId = backStackEntry.arguments?.getInt("genreId") ?: 0
            val genreName = backStackEntry.arguments?.getString("genreName") ?: ""

            GenreContentScreen(
                mediaType = mediaType,
                genreId = genreId,
                genreName = genreName,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                onTvShowClick = { tvShowId -> navController.navigate(Screen.TvShowDetail.createRoute(tvShowId)) }
            )
        }

        // Movie Detail Screen
        composable(
            route = Screen.MovieDetail.route,
            arguments = listOf(navArgument("movieId") { type = NavType.IntType })
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getInt("movieId") ?: return@composable
            MobileMovieDetailScreen(
                movieId = movieId,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { newMovieId -> navController.navigate(Screen.MovieDetail.createRoute(newMovieId)) },
                onPlayClick = { route ->
                    navController.navigate(route)
                },
                onCastClick = { castId, castName, character, profilePath, knownForDepartment ->
                    navController.navigate(
                        "cast_detail/$castId/${Uri.encode(castName)}/${Uri.encode(character ?: "")}/${Uri.encode(profilePath ?: "")}/${Uri.encode(knownForDepartment ?: "")}"
                    )
                },
                onNavigateToCastDetail = { route -> navController.navigate(route) }
            )
        }

        // TV Show Detail Screen
        composable(
            route = Screen.TvShowDetail.route,
            arguments = listOf(navArgument("tvId") { type = NavType.IntType })
        ) { backStackEntry ->
            val tvId = backStackEntry.arguments?.getInt("tvId") ?: return@composable
            MobileTvShowDetailScreen(
                tvId = tvId,
                onBackClick = { navController.popBackStack() },
                onTvShowClick = { newTvId -> navController.navigate(Screen.TvShowDetail.createRoute(newTvId)) },
                onEpisodesClick = { id, name, totalSeasons ->
                    navController.navigate(Screen.SeasonEpisodes.createRoute(id, name, totalSeasons))
                },
                onPlayClick = { route ->
                    navController.navigate(route)
                },
                onCastClick = { castId, castName, character, profilePath, knownForDepartment ->
                    navController.navigate(
                        "cast_detail/$castId/${android.net.Uri.encode(castName)}/${android.net.Uri.encode(character ?: "")}/${android.net.Uri.encode(profilePath ?: "")}/${android.net.Uri.encode(knownForDepartment ?: "")}"
                    )
                },
                onNavigateToCastDetail = { route -> navController.navigate(route) }
            )
        }

        // Season Episodes Screen
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
            com.kiduyuk.klausk.kiduyutv.ui.screens.detail.SeasonEpisodesScreen(
                tvShowId = tvId,
                tvShowName = tvShowName,
                totalSeasons = totalSeasons,
                onBackClick = { navController.popBackStack() },
                onPlayClick = { route ->
                    navController.navigate(route)
                }
            )
        }

        // Media List Screen (for company/network browse)
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
            com.kiduyuk.klausk.kiduyutv.ui.screens.company_network_list.MediaListScreen(
                type = type,
                id = id,
                name = name,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                onTvShowClick = { tvId -> navController.navigate(Screen.TvShowDetail.createRoute(tvId)) }
            )
        }

        // Cast Detail Screen
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

            val castMember = com.kiduyuk.klausk.kiduyutv.data.model.CastMember(
                id = castId,
                name = android.net.Uri.decode(castName),
                character = android.net.Uri.decode(character ?: ""),
                profilePath = if (profilePath.isNullOrBlank()) null else android.net.Uri.decode(profilePath),
                knownForDepartment = android.net.Uri.decode(knownForDepartment ?: ""),
                popularity = null,
                order = null
            )

            com.kiduyuk.klausk.kiduyutv.ui.screens.cast.CastDetailScreen(
                castMember = castMember,
                onBackClick = { navController.popBackStack() },
                onMovieClick = { movieId -> navController.navigate(Screen.MovieDetail.createRoute(movieId)) },
                onTvShowClick = { tvId -> navController.navigate(Screen.TvShowDetail.createRoute(tvId)) }
            )
        }

        // Stream Links Screen
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

            com.kiduyuk.klausk.kiduyutv.ui.screens.detail.StreamLinksScreen(
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
    }
}
