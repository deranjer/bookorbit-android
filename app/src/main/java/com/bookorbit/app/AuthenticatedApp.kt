package com.bookorbit.app

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bookorbit.core.model.AuthUser
import com.bookorbit.feature.main.MainShell
import com.bookorbit.feature.player.PlayerScreen
import com.bookorbit.feature.player.PlayerViewModel
import com.bookorbit.feature.reader.ReaderScreen

/**
 * App-level navigation host for the signed-in area. Holds full-screen destinations that sit OUTSIDE
 * the bottom-bar shell: book detail, the reader, and the audiobook player. The bottom-bar tabs are a
 * nested host inside [MainShell].
 */
@Composable
fun AuthenticatedApp(user: AuthUser, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    // Shared player VM at the app-nav scope so "Listen" can start playback before navigating.
    val playerVm: PlayerViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = AppRoutes.MAIN) {
        composable(AppRoutes.MAIN) {
            // Book detail lives INSIDE the shell (so the bottom bar + mini-player persist); only the
            // immersive reader/player are full-screen app-level destinations.
            MainShell(
                user = user,
                onSignOut = onSignOut,
                onOpenReader = { id -> navController.navigate(AppRoutes.reader(id)) },
                onListen = { id ->
                    playerVm.loadAndPlay(id)
                    navController.navigate(AppRoutes.PLAYER)
                },
                onOpenPlayer = { navController.navigate(AppRoutes.PLAYER) },
            )
        }
        composable(
            route = AppRoutes.READER,
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) {
            ReaderScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.PLAYER) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
    }
}

object AppRoutes {
    const val MAIN = "main"
    const val READER = "reader/{id}"
    const val PLAYER = "player"
    fun reader(id: Int) = "reader/$id"
}
