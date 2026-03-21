package com.kiduyuk.klausk.kiduyutv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.MovieDetailScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.SeasonEpisodesScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.detail.TvShowDetailScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.HomeScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.MoviesScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.MyListScreen
import com.kiduyuk.klausk.kiduyutv.ui.screens.home.TvShowsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onTvShowClick = { tvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                }
            )
        }

        composable(Screen.Movies.route) {
            MoviesScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                }
            )
        }

        composable(Screen.TvShows.route) {
            TvShowsScreen(
                onTvShowClick = { tvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                }
            )
        }

        composable(Screen.MyList.route) {
            MyListScreen(
                onMovieClick = { movieId ->
                    navController.navigate(Screen.MovieDetail.createRoute(movieId))
                },
                onTvShowClick = { tvId ->
                    navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                }
            )
        }

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
                }
            )
        }

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
                }
            )
        }

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
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

