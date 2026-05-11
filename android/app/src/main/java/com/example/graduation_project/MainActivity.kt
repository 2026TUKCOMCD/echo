package com.example.graduation_project

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/**
 * 앱의 메인 액티비티
 *
 * ## Jetpack Compose 구조
 * - ComponentActivity: Compose 전용 액티비티
 * - enableEdgeToEdge(): 시스템 바 영역까지 콘텐츠 확장
 * - setContent { }: Compose UI를 설정하는 진입점
 *
 * ## 화면 구성
 * - Graduation_projectTheme: Material 3 테마 적용
 * - AppNavHost: Navigation으로 화면 전환 관리
 *
 * ## 권한 및 GPS 수집
 * - UnifiedPermissionHandler에서 모든 권한 처리 및 GPS 수집 시작
 */
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
            OnboardingPlaceholder(
                onSkip = {
                    navController.navigate(Routes.CONVERSATION) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
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
private fun OnboardingPlaceholder(onSkip: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("온보딩 (T1.3에서 구현 예정)")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSkip) {
            Text("대화 시작 화면으로 이동")
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MainActivityPreview() {
    Graduation_projectTheme {
        AppNavHost()
    }
}
