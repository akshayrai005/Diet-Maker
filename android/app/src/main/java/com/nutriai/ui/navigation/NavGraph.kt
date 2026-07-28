package com.nutriai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nutriai.ui.auth.ForgotPasswordScreen
import com.nutriai.ui.auth.LoginScreen
import com.nutriai.ui.auth.RegisterScreen
import com.nutriai.ui.home.HomeScreen
import com.nutriai.ui.home.SessionViewModel
import com.nutriai.ui.onboarding.OnboardingScreen
import com.nutriai.ui.splash.SplashScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
}

@Composable
fun AppRoot(startTab: Int = 0, sessionViewModel: SessionViewModel = hiltViewModel()) {
    val loggedIn by sessionViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val needsProfile by sessionViewModel.needsProfile.collectAsStateWithLifecycle()

    var splashDone by remember { mutableStateOf(false) }
    if (!splashDone) {
        SplashScreen(onFinished = { splashDone = true })
        return
    }

    // Wait until we know both login state and (if logged in) whether the profile is complete, so a
    // half-onboarded user is sent to finish setup instead of flashing an empty home.
    if (loggedIn == null || (loggedIn == true && needsProfile == null)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = when {
            loggedIn != true -> Routes.LOGIN
            needsProfile == true -> Routes.ONBOARDING
            else -> Routes.HOME
        },
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onNeedsProfile = {
                    // Logged in but no profile yet - set it up first, then land on the dashboard.
                    navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onGoToRegister = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT) },
            )
        }
        composable(Routes.FORGOT) {
            ForgotPasswordScreen(
                onDone = { navController.popBackStack(Routes.LOGIN, inclusive = false) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegistered = {
                    navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onGoToLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onLogout = {
                    navController.navigate(Routes.LOGIN) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onCompleteProfile = { navController.navigate(Routes.ONBOARDING) },
                initialTab = startTab,
            )
        }
    }
}
