package com.bookorbit.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bookorbit.core.auth.SessionState
import com.bookorbit.feature.auth.LoginScreen
import com.bookorbit.feature.auth.ServerSetupScreen
import com.bookorbit.ui.LocalImageUrls

private object AuthRoutes {
    const val SERVER_SETUP = "server-setup"
    const val LOGIN = "login"
}

/**
 * Root composable. Gates on [SessionState]: while loading shows a spinner; signed-in shows the main
 * shell; otherwise runs the unauthenticated server-setup -> login flow. Login success flips the
 * session to SignedIn, which recomposes here and swaps in [MainScreen].
 */
@Composable
fun BookOrbitApp() {
    val rootVm: RootViewModel = hiltViewModel()
    val state by rootVm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        SessionState.Loading -> LoadingScreen()
        is SessionState.SignedIn -> CompositionLocalProvider(LocalImageUrls provides rootVm.imageUrls) {
            AuthenticatedApp(user = s.user, onSignOut = rootVm::signOut)
        }
        else -> AuthFlow(startAtServerSetup = s is SessionState.NeedsServer)
    }
}

@Composable
private fun AuthFlow(startAtServerSetup: Boolean) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = if (startAtServerSetup) AuthRoutes.SERVER_SETUP else AuthRoutes.LOGIN,
    ) {
        composable(AuthRoutes.SERVER_SETUP) {
            ServerSetupScreen(
                onConnected = {
                    navController.navigate(AuthRoutes.LOGIN) {
                        popUpTo(AuthRoutes.SERVER_SETUP) { inclusive = true }
                    }
                },
            )
        }
        composable(AuthRoutes.LOGIN) {
            LoginScreen(onChangeServer = { navController.navigate(AuthRoutes.SERVER_SETUP) })
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
