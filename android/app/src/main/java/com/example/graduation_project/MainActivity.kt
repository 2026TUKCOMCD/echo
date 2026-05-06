package com.example.graduation_project

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.repository.AuthRepository
import com.example.graduation_project.presentation.auth.LoginScreen
import com.example.graduation_project.presentation.auth.SignupScreen
import com.example.graduation_project.presentation.conversation.ConversationScreen
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Graduation_projectTheme {
                AppNavHost()
            }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val ONBOARDING = "onboarding"
    const val CONVERSATION = "conversation"
}

@Composable
private fun AppNavHost() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val authRepository = remember { AuthRepository(tokenStorage = TokenStorage(application)) }
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    val startDestination = remember {
        if (authRepository.hasAccessToken()) Routes.CONVERSATION else Routes.LOGIN
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.CONVERSATION) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate(Routes.SIGNUP)
                }
            )
        }
        composable(Routes.SIGNUP) {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingPlaceholder()
        }
        composable(Routes.CONVERSATION) {
            ConversationScreen(
                onLogout = {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { authRepository.logout() }
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(Routes.CONVERSATION) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun OnboardingPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("온보딩 (T1.3에서 구현 예정)")
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainActivityPreview() {
    Graduation_projectTheme {
        AppNavHost()
    }
}
