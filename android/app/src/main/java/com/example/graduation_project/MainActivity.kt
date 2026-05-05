package com.example.graduation_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.graduation_project.presentation.auth.SignupScreen
import com.example.graduation_project.presentation.conversation.ConversationScreen
import com.example.graduation_project.ui.theme.Graduation_projectTheme

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
    const val SIGNUP = "signup"
    const val ONBOARDING = "onboarding"
    const val CONVERSATION = "conversation"
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SIGNUP) {
        composable(Routes.SIGNUP) {
            SignupScreen(
                onSignupSuccess = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SIGNUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingPlaceholder()
        }
        composable(Routes.CONVERSATION) {
            ConversationScreen()
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
