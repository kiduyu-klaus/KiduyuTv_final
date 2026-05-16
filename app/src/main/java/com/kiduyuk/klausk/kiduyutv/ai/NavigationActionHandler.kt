package com.kiduyuk.klausk.kiduyutv.ai

import androidx.navigation.NavHostController
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionCommand
import com.kiduyuk.klausk.kiduyutv.ai.model.ActionType
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen

/**
 * Handles navigation actions triggered from the AI chat.
 */
class NavigationActionHandler(
    private val navController: NavHostController
) {

    /**
     * Processes an action command and navigates accordingly.
     *
     * @param action The action command to process
     * @return true if navigation was successful, false otherwise
     */
    fun handleAction(action: ActionCommand): Boolean {
        return when (action.type) {
            ActionType.NAVIGATE_TO_MOVIE -> {
                val movieId = action.data["id"] as? Int ?: return false
                navController.navigate(Screen.MovieDetail.createRoute(movieId))
                true
            }
            ActionType.NAVIGATE_TO_TV_SHOW -> {
                val tvId = action.data["id"] as? Int ?: return false
                navController.navigate(Screen.TvShowDetail.createRoute(tvId))
                true
            }
            ActionType.NAVIGATE_TO_CAST -> {
                val castId = action.data["id"] as? Int ?: return false
                val name = action.data["name"] as? String ?: ""
                val character = action.data["character"] as? String
                val profilePath = action.data["profilePath"] as? String
                val knownForDepartment = action.data["knownForDepartment"] as? String
                navController.navigate(
                    Screen.MobileCastDetail.createRoute(
                        castId = castId,
                        castName = name,
                        character = character,
                        profilePath = profilePath,
                        knownForDepartment = knownForDepartment
                    )
                )
                true
            }
            ActionType.NAVIGATE_TO_GENRE -> {
                val genreId = action.data["id"] as? Int ?: return false
                val mediaType = action.data["type"] as? String ?: "movie"
                navController.navigate("genre_content/$mediaType/$genreId/Recommended")
                true
            }
            ActionType.SEARCH_MOVIES -> {
                val query = action.data["query"] as? String ?: return false
                navController.navigate(Screen.Search.route)
                true
            }
            ActionType.SEARCH_TV_SHOWS -> {
                val query = action.data["query"] as? String ?: return false
                navController.navigate(Screen.Search.route)
                true
            }
            ActionType.SHOW_RECOMMENDATIONS -> {
                navController.navigate(Screen.Home.route)
                true
            }
            ActionType.OPEN_SETTINGS -> {
                navController.navigate(Screen.Settings.route)
                true
            }
        }
    }
}