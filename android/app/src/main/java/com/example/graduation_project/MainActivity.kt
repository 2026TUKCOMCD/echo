package com.example.graduation_project

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.graduation_project.presentation.diary.DiaryDetailScreen
import com.example.graduation_project.presentation.diary.DiaryScreen
import com.example.graduation_project.presentation.history.ConversationHistoryDetailScreen
import com.example.graduation_project.presentation.home.HomeScreen
import com.example.graduation_project.presentation.onboarding.OnboardingScreen
import com.example.graduation_project.presentation.settings.SettingsScreen
import com.example.graduation_project.presentation.settings.DisplaySettingsViewModel
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.location.LocationScheduler
import com.example.graduation_project.data.location.MorningAlarmReceiver
import com.example.graduation_project.presentation.permission.SamsungBatterySettingsDialog
import com.example.graduation_project.util.DeviceUtil
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import com.example.graduation_project.presentation.permission.LocationCollectionConfirmDialog
import com.example.graduation_project.presentation.permission.LocationPermissionGuideDialog
import com.example.graduation_project.util.CrashReporter

class MainActivity : ComponentActivity() {

    private val displayViewModel: DisplaySettingsViewModel by viewModels { DisplaySettingsViewModel.Factory }

    // 권한 다이얼로그 표시 여부 (알림에서 앱 열었을 때)
    private var shouldShowPermissionDialog = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 알림에서 권한 다이얼로그 표시 요청 확인
        handlePermissionDialogIntent(intent)

        // 알림 딥링크 처리 (회전 등 재생성 시 재실행 방지 위해 콜드 스타트에만 적용)
        val navigateTo = if (savedInstanceState == null) intent.getStringExtra("navigate_to") else null

        setContent {
            val displaySettings by displayViewModel.settings.collectAsState()
            Graduation_projectTheme(displaySettings = displaySettings) {
                AppNavHost(
                    navigateTo = navigateTo,
                    displayViewModel = displayViewModel,
                    shouldShowPermissionDialog = shouldShowPermissionDialog.value,
                    onPermissionDialogHandled = { shouldShowPermissionDialog.value = false }
                )

                // 디버그 빌드: 이전 실행에서 크래시가 있었으면 스택트레이스 표시 (ADB 없는 기기 디버깅용)
                if (BuildConfig.DEBUG) {
                    DebugCrashDialog()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePermissionDialogIntent(intent)
    }

    private fun handlePermissionDialogIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(MorningAlarmReceiver.EXTRA_SHOW_PERMISSION_DIALOG, false) == true) {
            shouldShowPermissionDialog.value = true
        }
    }
}

/**
 * 디버그 빌드 전용: 이전 실행의 크래시 스택트레이스를 다이얼로그로 표시
 */
@Composable
private fun DebugCrashDialog() {
    val context = LocalContext.current
    var crashText by remember { mutableStateOf(CrashReporter.readLastCrash(context)) }

    crashText?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                CrashReporter.clear(context)
                crashText = null
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    CrashReporter.clear(context)
                    crashText = null
                }) {
                    androidx.compose.material3.Text("닫기")
                }
            },
            title = { androidx.compose.material3.Text("이전 실행 크래시 (디버그)") },
            text = {
                androidx.compose.material3.Text(
                    text = text.take(4000),
                    fontSize = 11.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        )
    }
}

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val ONBOARDING = "onboarding"
    const val CHECKING = "checking"
    const val CONVERSATION = "conversation"
    const val HISTORY_DETAIL = "history_detail/{conversationId}"
    const val DIARY_DETAIL = "diary_detail/{date}"

    fun historyDetail(conversationId: String): String {
        val encoded = URLEncoder.encode(conversationId, "UTF-8")
        return "history_detail/$encoded"
    }

    // date는 "yyyy-MM-dd" 형식이라 URL 인코딩 불필요
    fun diaryDetail(date: String): String = "diary_detail/$date"
}

// 탭바가 표시되는 최상위 루트 목록
private val tabRoutes = EchoTab.entries.map { it.route }.toSet()

@Composable
private fun AppNavHost(
    navigateTo: String? = null,
    displayViewModel: DisplaySettingsViewModel,
    shouldShowPermissionDialog: Boolean = false,
    onPermissionDialogHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val authRepository = remember { AuthRepository(tokenStorage = TokenStorage(application), application = application) }
    val userRepository = remember { UserRepository() }
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberNavController()

    // 로그아웃 공통 처리: 사용자별 알람/위치 수집 정리 후 토큰 삭제
    // (미정리 시 로그아웃 후에도 대화 알람이 계속 울리고 위치가 계속 수집됨)
    val performLogout: suspend () -> Unit = {
        ConversationAlarmScheduler.cancelAndClear(context)
        LocationScheduler.disableLocationCollection(context)
        withContext(Dispatchers.IO) { authRepository.logout() }
    }

    // 권한 다이얼로그 상태
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showSamsungBatteryDialog by remember { mutableStateOf(false) }
    var permissionStep by remember { mutableStateOf(0) } // 0: 대기, 1: 위치, 2: 백그라운드, 3: 배터리, 4: 삼성

    // 위치 권한 요청 런처
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineGranted) {
            // 다음 단계: 백그라운드 위치 권한
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionStep = 2
            } else {
                // Android 9 이하는 배터리 최적화로
                permissionStep = 3
            }
        } else {
            // 거부됨 - 설정 안내
            showGuideDialog = true
            permissionStep = 0
        }
    }

    // 백그라운드 위치 권한 요청 런처
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 다음 단계: 배터리 최적화
            permissionStep = 3
        } else {
            // 거부됨 - 설정 안내
            showGuideDialog = true
            permissionStep = 0
        }
    }

    // 권한 단계별 처리
    LaunchedEffect(permissionStep) {
        when (permissionStep) {
            1 -> {
                // 위치 권한 요청
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            2 -> {
                // 백그라운드 위치 권한 요청
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
            3 -> {
                // 배터리 최적화 해제 요청
                val powerManager = context.getSystemService(PowerManager::class.java)
                if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
                // 삼성 기기인 경우 추가 설정 안내
                if (DeviceUtil.isSamsungDevice()) {
                    permissionStep = 4
                } else {
                    // 완료 - 위치 수집 시작 시도
                    LocationScheduler.enableLocationCollection(context)
                    permissionStep = 0
                }
            }
            4 -> {
                // 삼성 기기 추가 배터리 설정 안내
                showSamsungBatteryDialog = true
            }
        }
    }

    // 알림에서 앱 열었을 때 확인 다이얼로그 표시
    LaunchedEffect(shouldShowPermissionDialog) {
        if (shouldShowPermissionDialog) {
            val result = LocationScheduler.checkPrerequisites(context)
            if (result != LocationScheduler.PrerequisiteResult.ALL_SATISFIED) {
                showConfirmDialog = true
            }
            onPermissionDialogHandled()
        }
    }

    // 위치 수집 허용 확인 다이얼로그
    if (showConfirmDialog) {
        LocationCollectionConfirmDialog(
            onAllow = {
                showConfirmDialog = false
                // 순차적 권한 요청 시작
                permissionStep = 1
            },
            onDeny = {
                showConfirmDialog = false
                showGuideDialog = true
            }
        )
    }

    // 설정 안내 다이얼로그
    if (showGuideDialog) {
        LocationPermissionGuideDialog(
            onDismiss = { showGuideDialog = false }
        )
    }

    // 삼성 기기 배터리 설정 안내 다이얼로그
    if (showSamsungBatteryDialog) {
        SamsungBatterySettingsDialog(
            onDismiss = {
                showSamsungBatteryDialog = false
                permissionStep = 0
                // 위치 수집 시작 시도
                LocationScheduler.enableLocationCollection(context)
            },
            onOpenSettings = {
                // 삼성 배터리 설정 화면으로 이동
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // 실패 시 일반 설정 화면
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        )
    }

    // EncryptedSharedPreferences 초기화는 Android Keystore를 사용하므로
    // 메인 스레드에서 동기 호출 시 ANR/크래시 발생. IO 스레드에서 비동기 처리.
    var startDestination by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (startDestination == null) {
            val hasToken = withContext(Dispatchers.IO) { authRepository.hasAccessToken() }
            startDestination = if (hasToken) Routes.CHECKING else Routes.LOGIN
        }
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

    // 탭 화면(홈/일기/설정)이면서 더 이상 뒤로 갈 화면이 없을 때만 "한번 더 눌러 종료" 처리.
    // 그 외 화면(일기/설정에서 홈으로 돌아가는 경우 포함)은 기본 popBackStack 동작을 그대로 둔다.
    var lastBackPressTime by remember { mutableStateOf(0L) }
    BackHandler(enabled = currentRoute in tabRoutes && navController.previousBackStackEntry == null) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < 2000L) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(context, "뒤로가기를 한번 더 누르면 종료됩니다", Toast.LENGTH_SHORT).show()
        }
    }

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
                            // 탭 전환 시 그래프의 실제 startDestination(CHECKING/LOGIN)은
                            // 로그인 확인 후 inclusive popUpTo로 이미 백스택에서 제거된 상태라
                            // findStartDestination()을 앵커로 쓰면 아무것도 못 지워 탭 이력이
                            // 무한히 쌓인다. 항상 존재하는 HOME 탭을 앵커로 고정해야 한다.
                            popUpTo(EchoTab.HOME.route) {
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
                val checkingContext = LocalContext.current
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EchoAccentGreen)
                }
                LaunchedEffect(Unit) {
                    val destination = when (val result = userRepository.getOnboardingStatus()) {
                        is ApiResult.Success -> if (result.data.completed) EchoTab.HOME.route else Routes.ONBOARDING
                        is ApiResult.Error -> {
                            // 조용히 로그인 화면으로 돌아가면 사용자가 원인을 알 수 없으므로 실패 사유 표시
                            Toast.makeText(
                                checkingContext,
                                "로그인 상태 확인 실패: ${result.exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            Routes.LOGIN
                        }
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.CHECKING) { inclusive = true }
                    }
                }
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        // 온보딩 상태 확인 동안 로그인 화면이 멈춘 것처럼 보이지 않도록
                        // 즉시 CHECKING(전체 화면 스피너)으로 전환하고, 확인/분기는 CHECKING에서 처리
                        navController.navigate(Routes.CHECKING) {
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

            // 일기 탭 (일기 + 일기 없는 날의 대화 기록)
            composable(EchoTab.HISTORY.route) {
                DiaryScreen(
                    onDiaryClick = { date ->
                        navController.navigate(Routes.diaryDetail(date))
                    },
                    onConversationClick = { conversationId ->
                        navController.navigate(Routes.historyDetail(conversationId))
                    }
                )
            }

            // 일기 상세 (일기 본문 + 그날의 대화 세션 목록)
            composable(Routes.DIARY_DETAIL) { backStackEntry ->
                val date = backStackEntry.arguments?.getString("date") ?: ""
                DiaryDetailScreen(
                    date = date,
                    onSessionClick = { conversationId ->
                        navController.navigate(Routes.historyDetail(conversationId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 대화 상세 (말풍선)
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
                            performLogout()
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
                            performLogout()
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

