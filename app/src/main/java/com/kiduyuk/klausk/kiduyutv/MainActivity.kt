package com.kiduyuk.klausk.kiduyutv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.kiduyuk.klausk.kiduyutv.ui.navigation.NavGraph
import com.kiduyuk.klausk.kiduyutv.ui.navigation.Screen
import com.kiduyuk.klausk.kiduyutv.ui.theme.BackgroundDark
import com.kiduyuk.klausk.kiduyutv.ui.theme.KiduyuTvTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KiduyuTvTheme {
                val navController = rememberNavController()
                var currentRoute by remember { mutableStateOf(Screen.Home.route) }

                // Track navigation changes
                LaunchedEffect(navController) {
                    navController.currentBackStackEntryFlow.collect { entry ->
                        currentRoute = entry.destination.route ?: Screen.Home.route
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundDark)
                ) {
                    NavGraph(navController = navController)
                }
            }
        }
    }
}
