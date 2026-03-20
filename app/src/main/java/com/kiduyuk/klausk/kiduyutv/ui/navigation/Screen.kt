package com.kiduyuk.klausk.kiduyutv.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Movies : Screen("movies")
    object TvShows : Screen("tv_shows")
    object MyList : Screen("my_list")
    object MovieDetail : Screen("movie/{movieId}") {
        fun createRoute(movieId: Int) = "movie/$movieId"
    }
    object TvShowDetail : Screen("tv/{tvId}") {
        fun createRoute(tvId: Int) = "tv/$tvId"
    }
    object SeasonDetail : Screen("tv/{tvId}/season/{seasonNumber}") {
        fun createRoute(tvId: Int, seasonNumber: Int) = "tv/$tvId/season/$seasonNumber"
    }
}
