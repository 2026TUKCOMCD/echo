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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.repository.AuthRepository
import com.example.graduation_project.presentation.auth.LoginScreen
import com.example.graduation_project.presentation.auth.SignupScreen
import com.example.graduation_project.presentation.common.EchoTab
import com.example.graduation_project.presentation.common.EchoTabBar
import com.example.graduation_project.presentation.conversation.ConversationScreen
import com.example.graduation_project.presentation.history.ConversationHistoryDetailScreen
import com.example.graduation_project.presentation.history.ConversationHistoryScreen
import com.example.graduation_project.presentation.home.HomeScreen
import com.example.graduation_project.presentation.settings.SettingsScreen
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

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
    const val HISTORY_DETAIL = "history_detail/{conversationId}"

    fun historyDetail(conversationId: String): String {
        val encoded = URLEncoder.encode(conversationId, "UTF-8")
        return "history_detail/$encoded"
    }
}

// 탭바가 표시되는 최상위 루트 목록
private val tabRoutes = EchoTab.entries.map { it.route }.toSet()

@Composable
private fun AppNavHost() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val authRepository = remember { AuthRepository(tokenStorage = TokenStorage(application)) }
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    val startDestination = remember {
        if (authRepository.hasAccessToken()) EchoTab.HOME.route else Routes.LOGIN
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTabBar = currentRoute in tabRoutes

    Scaffold(
        bottomBar = {
            if (showTabBar) {
                EchoTabBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(EchoTab.HOME.route) {
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
                            popUpTo(Routes.SIGNUP) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.ONBOARDING) {
                OnboardingPlaceholder(
                    onSkip = {
                        navController.navigate(EchoTab.HOME.route) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            // 홈 탭
            composable(EchoTab.HOME.route) {
                HomeScreen(
                    onStartConversation = {
                        navController.navigate(Routes.CONVERSATION)
                    }
                )
            }

            // 대화기록 탭
            composable(EchoTab.HISTORY.route) {
                ConversationHistoryScreen(
                    onConversationClick = { conversationId ->
                        navController.navigate(Routes.historyDetail(conversationId))
                    }
                )
            }

            // 대화기록 상세
            composable(Routes.HISTORY_DETAIL) { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("conversationId") ?: ""
                val conversationId = URLDecoder.decode(encoded, "UTF-8")
                ConversationHistoryDetailScreen(
                    conversationId = conversationId,
                    onBack = { navController.popBackStack() }
                )
            }

            // 설정 탭
            composable(EchoTab.SETTINGS.route) {
                SettingsScreen(
                    onLogout = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { authRepository.logout() }
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // 대화 화면 (전체화면, 탭바 없음)
            composable(Routes.CONVERSATION) {
                ConversationScreen(
                    onLogout = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { authRepository.logout() }
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
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
            Text("시작하기")
        }
    }
}
