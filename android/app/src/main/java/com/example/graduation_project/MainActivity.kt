package com.example.graduation_project

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.repository.AuthRepository
import com.example.graduation_project.data.repository.UserRepository
import com.example.graduation_project.presentation.auth.LoginScreen
import com.example.graduation_project.presentation.auth.SignupScreen
import com.example.graduation_project.presentation.common.EchoTab
import com.example.graduation_project.presentation.common.EchoTabBar
import com.example.graduation_project.presentation.conversation.ConversationScreen
import com.example.graduation_project.presentation.history.ConversationHistoryDetailScreen
import com.example.graduation_project.presentation.history.ConversationHistoryScreen
import com.example.graduation_project.presentation.home.HomeScreen
import com.example.graduation_project.presentation.onboarding.OnboardingScreen
import com.example.graduation_project.presentation.settings.SettingsScreen
import com.example.graduation_project.presentation.settings.DisplaySettingsViewModel
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {

    private val displayViewModel: DisplaySettingsViewModel by viewModels { DisplaySettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 알림 딥링크 처리
        val navigateTo = intent.getStringExtra("navigate_to")

        setContent {
            val displaySettings by displayViewModel.settings.collectAsState()
            Graduation_projectTheme(displaySettings = displaySettings) {
                AppNavHost(navigateTo = navigateTo, displayViewModel = displayViewModel)
            }
        }
    }
}

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val ONBOARDING = "onboarding"
    const val CHECKING = "checking"
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
private fun AppNavHost(navigateTo: String? = null, displayViewModel: DisplaySettingsViewModel) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val authRepository = remember { AuthRepository(tokenStorage = TokenStorage(application)) }
    val userRepository = remember { UserRepository() }
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    // EncryptedSharedPreferences 초기화는 Android Keystore를 사용하므로
    // 메인 스레드에서 동기 호출 시 ANR/크래시 발생. IO 스레드에서 비동기 처리.
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val hasToken = withContext(Dispatchers.IO) { authRepository.hasAccessToken() }
        startDestination = if (hasToken) Routes.CHECKING else Routes.LOGIN
    }

    // 알림에서 홈 화면으로 이동 요청 시 처리
    LaunchedEffect(navigateTo, startDestination) {
        if (navigateTo == "home" && startDestination != null) {
            // 로그인 상태 확인 후 홈으로 이동
            val hasToken = withContext(Dispatchers.IO) { authRepository.hasAccessToken() }
            if (hasToken) {
                navController.navigate(EchoTab.HOME.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTabBar = currentRoute in tabRoutes

    if (startDestination == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EchoAccentGreen)
        }
        return
    }

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
            startDestination = startDestination!!,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Routes.CHECKING) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EchoAccentGreen)
                }
                LaunchedEffect(Unit) {
                    val destination = when (val result = userRepository.getOnboardingStatus()) {
                        is ApiResult.Success -> if (result.data.completed) EchoTab.HOME.route else Routes.ONBOARDING
                        is ApiResult.Error -> Routes.LOGIN
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.CHECKING) { inclusive = true }
                    }
                }
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        coroutineScope.launch {
                            val completed = when (val result = userRepository.getOnboardingStatus()) {
                                is ApiResult.Success -> result.data.completed
                                is ApiResult.Error -> false
                            }
                            val destination = if (completed) EchoTab.HOME.route else Routes.ONBOARDING
                            navController.navigate(destination) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
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
                OnboardingScreen(
                    onOnboardingComplete = {
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
                    displayViewModel = displayViewModel,
                    onLogout = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { authRepository.logout() }
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    displayViewModel = displayViewModel
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

