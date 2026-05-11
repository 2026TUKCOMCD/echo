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
