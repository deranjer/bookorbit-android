package com.bookorbit.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
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
import com.bookorbit.feature.reader.pdf.PdfReaderScreen

/**
 * App-level navigation host for the signed-in area. Holds full-screen destinations that sit OUTSIDE
 * the bottom-bar shell: book detail, the reader, and the audiobook player. The bottom-bar tabs are a
 * nested host inside [MainShell].
 *
 * Full-screen destinations pop themselves via [dropUnlessResumed]: while a popped screen is still
 * fading out it keeps receiving taps, and a second back (e.g. tapping the shell's menu button, which
 * sits where the player's minimize button was) would otherwise pop [AppRoutes.MAIN] too, leaving an
 * empty back stack and a blank screen.
 */
@Composable
fun AuthenticatedApp(user: AuthUser, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    // Shared player VM at the app-nav scope so "Listen" can start playback before navigating.
    val playerVm: PlayerViewModel = hiltViewModel()
    // Reopen the last audiobook (paused) so the mini-player survives the app being closed. The
    // manager makes this a once-per-process no-op, so recompositions and recreations are harmless.
    LaunchedEffect(Unit) { playerVm.restoreLastBook() }

    NavHost(navController = navController, startDestination = AppRoutes.MAIN) {
        composable(AppRoutes.MAIN) {
            // Book detail lives INSIDE the shell (so the bottom bar + mini-player persist); only the
            // immersive reader/player are full-screen app-level destinations.
            MainShell(
                user = user,
                onSignOut = onSignOut,
                onOpenReader = { id -> navController.navigate(AppRoutes.reader(id)) },
                onOpenPdf = { id -> navController.navigate(AppRoutes.pdf(id)) },
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
            ReaderScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(
            route = AppRoutes.PDF,
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) {
            PdfReaderScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable(AppRoutes.PLAYER) {
            PlayerScreen(onBack = dropUnlessResumed { navController.popBackStack() })
        }
    }
}

object AppRoutes {
    const val MAIN = "main"
    const val READER = "reader/{id}"
    const val PDF = "pdf/{id}"
    const val PLAYER = "player"
    fun reader(id: Int) = "reader/$id"
    fun pdf(id: Int) = "pdf/$id"
}
