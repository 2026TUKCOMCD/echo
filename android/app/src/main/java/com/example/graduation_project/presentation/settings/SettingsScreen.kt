package com.example.graduation_project.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.presentation.common.BirthdayInputRow
import com.example.graduation_project.presentation.common.EchoTimePickerContent
import com.example.graduation_project.presentation.common.buildBirthdayString
import com.example.graduation_project.presentation.common.parseBirthday
import com.example.graduation_project.presentation.health.openHealthConnectSettings
import com.example.graduation_project.data.local.DisplaySettingsStorage
import com.example.graduation_project.presentation.permission.PermissionChecker
import com.example.graduation_project.presentation.permission.SamsungBatterySettingsDialog
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.OutfitFontFamily

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    displayViewModel: DisplaySettingsViewModel = viewModel(factory = DisplaySettingsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val displaySettings by displayViewModel.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingField by remember { mutableStateOf<String?>(null) }
    var showSamsungBatteryDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 설정에서 돌아왔을 때 권한 상태 새로고침
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
                viewModel.refreshBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 배터리 최적화 해제 요청 처리
    LaunchedEffect(uiState.shouldRequestBatteryOptimization) {
        if (uiState.shouldRequestBatteryOptimization) {
            val powerManager = context.getSystemService(PowerManager::class.java)
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
            viewModel.dismissBatteryOptimizationRequest()
        }
    }

    // 삼성 기기 배터리 설정 안내 다이얼로그 (ViewModel에서 삼성 기기 여부 판단)
    LaunchedEffect(uiState.shouldShowSamsungBatteryDialog) {
        if (uiState.shouldShowSamsungBatteryDialog) {
            showSamsungBatteryDialog = true
            viewModel.dismissSamsungBatteryDialog()
        }
    }

    if (showSamsungBatteryDialog) {
        SamsungBatterySettingsDialog(
            onDismiss = { showSamsungBatteryDialog = false },
            onOpenSettings = {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        )
    }

    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSavedMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    val colors = LocalEchoColors.current
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgPage)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "설정",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )

            // 프로필 카드
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgCard
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = colors.bgMuted) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (uiState.userName.isNotBlank()) uiState.userName else "사용자",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = OutfitFontFamily,
                            color = colors.textPrimary
                        )
                        if (uiState.age != null) {
                            Text(
                                text = "${uiState.age}세",
                                fontSize = 15.sp,
                                fontFamily = OutfitFontFamily,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            val enabled = !uiState.isSaving

            Spacer(Modifier.height(20.dp))

            // ===== 화면 설정 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.Settings,
                        title = "화면 설정"
                    )
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text("글씨 크기", fontSize = 14.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "소" to DisplaySettingsStorage.SCALE_SMALL,
                                "중" to DisplaySettingsStorage.SCALE_MEDIUM,
                                "대" to DisplaySettingsStorage.SCALE_LARGE
                            ).forEach { (label, scale) ->
                                FilterChip(
                                    selected = displaySettings.fontScale == scale,
                                    onClick = { displayViewModel.setFontScale(scale) },
                                    label = { Text(label, fontSize = 14.sp, fontFamily = OutfitFontFamily) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.accentGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("색상 테마", fontSize = 14.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !displaySettings.isHighContrast,
                                onClick = { displayViewModel.setHighContrast(false) },
                                label = { Text("기본", fontSize = 14.sp, fontFamily = OutfitFontFamily) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = displaySettings.isHighContrast,
                                onClick = { displayViewModel.setHighContrast(true) },
                                label = { Text("높은 대비", fontSize = 14.sp, fontFamily = OutfitFontFamily) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 대화 설정 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        title = "대화 설정"
                    )
                    // 마이크 권한 상태 표시 (필수 권한) - 최상단
                    MicrophonePermissionStatusRow(
                        isGranted = PermissionChecker.hasMicrophonePermission(context)
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow("대화 시간", uiState.conversationTime, enabled = enabled) { editingField = "conversationTime" }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow(
                        label = "음성 설정",
                        value = "${String.format("%.1f", uiState.voiceSpeed)}x · ${
                            when (uiState.voiceTone) {
                                "calm" -> "차분하게"
                                "cheerful" -> "밝게"
                                else -> "따뜻하게"
                            }
                        }",
                        enabled = enabled
                    ) { editingField = "voiceSettings" }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow("선호 대화 주제", uiState.preferredTopics, enabled = enabled) { editingField = "preferredTopics" }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 내 정보 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.Person,
                        title = "내 정보",
                        iconTint = colors.accentBlue
                    )
                    PreferenceRow("생년월일", uiState.birthday, enabled = enabled) { editingField = "birthday" }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow("거주 지역", uiState.location, enabled = enabled) { editingField = "location" }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow("직업", uiState.occupation, enabled = enabled) { editingField = "occupation" }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow("취미", uiState.hobbies, enabled = enabled) { editingField = "hobbies" }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 보호자 연결 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.People,
                        title = "보호자 연결",
                        iconTint = Color(0xFFFF9800)
                    )
                    PreferenceRow(
                        label = "보호자 이메일",
                        value = uiState.guardianEmail,
                        showWarning = uiState.guardianEmail.isNullOrBlank(),
                        enabled = enabled
                    ) { editingField = "guardianEmail" }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow("가족 관계", uiState.familyInfo, enabled = enabled) { editingField = "familyInfo" }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 건강 정보 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.Favorite,
                        title = "건강 정보",
                        iconTint = colors.accentRed
                    )
                    // 건강 데이터 권한 - 최상단
                    PermissionStatusRow(
                        label = "건강 데이터 권한",
                        isGranted = uiState.hasHealthConnectPermission,
                        grantedText = "허용됨",
                        deniedText = "허용 필요"
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow(
                        label = "선호 수면 시간",
                        value = uiState.preferredSleepHours?.let { "${it}시간" },
                        enabled = enabled
                    ) { editingField = "sleepHours" }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 위치 수집 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.LocationOn,
                        title = "위치 수집",
                        iconTint = colors.accentBlue
                    )
                    PermissionStatusRow(
                        label = "위치 권한",
                        isGranted = uiState.hasBackgroundLocationPermission,
                        grantedText = "항상 허용됨",
                        deniedText = "허용 필요"
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    // 배터리 최적화 상태
                    PermissionStatusRow(
                        label = "배터리 최적화",
                        isGranted = uiState.isBatteryOptimizationDisabled,
                        grantedText = "제한 없음",
                        deniedText = "제한됨 (터치하여 해제)",
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    // 위치 수집 상태 표시 (토글 없이 상태만)
                    LocationCollectionStatusRow(
                        isRunning = uiState.isLocationCollectionRunning
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow(
                        label = "수집 시작 시간",
                        value = uiState.locationCollectionStartTime,
                        enabled = enabled
                    ) { editingField = "locationStartTime" }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    // 위치 데이터 확인 (디버그)
                    NavigationRow(
                        label = "수집된 위치 데이터",
                        description = "오늘 수집된 GPS 포인트 확인",
                        onClick = { viewModel.loadLocationDebugData() }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 알람 및 권한 설정 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = colors.bgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.Notifications,
                        title = "알람 및 권한 설정",
                        iconTint = colors.textSecondary
                    )
                    // 알림 권한 상태 및 설정
                    PermissionStatusRow(
                        label = "알림 권한",
                        isGranted = uiState.hasNotificationPermission,
                        grantedText = "허용됨",
                        deniedText = "허용 필요 (터치하여 설정)",
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            } else {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            }
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    // 앱 권한 설정 - 권한 페이지로 직접 이동
                    NavigationRow(
                        label = "앱 권한 설정",
                        description = "모든 권한을 한 곳에서 관리",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // 로그아웃 버튼
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentRed, contentColor = Color.White)
            ) {
                Text("로그아웃", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFontFamily)
            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // 다이얼로그
    fun dismiss() { editingField = null }

    when (editingField) {
        "birthday" -> DatePickerFieldDialog(
            current = uiState.birthday,
            onSelected = { viewModel.updateBirthday(it.ifBlank { null }); dismiss() },
            onDismiss = { dismiss() }
        )
        "familyInfo" -> TextFieldDialog(
            title = "가족 관계",
            hint = "예) 딸, 아들, 배우자",
            initialValue = uiState.familyInfo ?: "",
            onConfirm = { viewModel.updateFamilyInfo(it.ifBlank { null }); dismiss() },
            onDismiss = { dismiss() }
        )
        "guardianEmail" -> TextFieldDialog(
            title = "보호자 이메일",
            hint = "유효한 이메일 주소를 입력해주세요",
            initialValue = uiState.guardianEmail ?: "",
            keyboardType = KeyboardType.Email,
            validate = { v ->
                when {
                    v.isBlank() -> "보호자 이메일을 입력해주세요"
                    !EMAIL_REGEX.matches(v) -> "올바른 이메일 형식을 입력해주세요"
                    else -> null
                }
            },
            onConfirm = { viewModel.updateGuardianEmail(it.ifBlank { null }); dismiss() },
            onDismiss = { dismiss() }
        )
        "location" -> TextFieldDialog(
            title = "거주 지역",
            hint = "예) 서울 강남구",
            initialValue = uiState.location ?: "",
            onConfirm = { viewModel.updateLocation(it.ifBlank { null }); dismiss() },
            onDismiss = { dismiss() }
        )
        "occupation" -> TextFieldDialog(
            title = "직업",
            hint = "예) 전직 교사, 은행원",
            initialValue = uiState.occupation ?: "",
            onConfirm = { viewModel.updateOccupation(it.ifBlank { null }); dismiss() },
            onDismiss = { dismiss() }
        )
        "hobbies" -> TextFieldDialog(
            title = "취미",
            hint = "예) 정원 가꾸기, 독서",
            initialValue = uiState.hobbies ?: "",
            onConfirm = { viewModel.updateHobbies(it.ifBlank { null }); dismiss() },
            onDismiss = { dismiss() }
        )
        "preferredTopics" -> TextFieldDialog(
            title = "선호 대화 주제",
            hint = "예) 옛날 이야기, 건강",
            initialValue = uiState.preferredTopics ?: "",
            onConfirm = { viewModel.updatePreferredTopics(it.ifBlank { null }); dismiss() },
            onDismiss = { dismiss() }
        )
        "voiceSettings" -> VoiceSettingsDialog(
            initialSpeed = uiState.voiceSpeed,
            initialTone = uiState.voiceTone,
            onConfirm = { speed, tone -> viewModel.updateVoiceSettings(speed, tone); dismiss() },
            onDismiss = { dismiss() }
        )
        "conversationTime" -> TimePickerFieldDialog(
            current = uiState.conversationTime,
            onSelected = { viewModel.updateConversationTime(it); dismiss() },
            onDismiss = { dismiss() }
        )
        "sleepHours" -> SleepHoursDialog(
            initialValue = uiState.preferredSleepHours,
            onConfirm = { viewModel.updatePreferredSleepHours(it); dismiss() },
            onDismiss = { dismiss() }
        )
        "locationStartTime" -> LocationStartTimeDialog(
            current = uiState.locationCollectionStartTime,
            onSelected = { viewModel.setLocationCollectionStartTime(it); dismiss() },
            onDismiss = { dismiss() }
        )
    }

    // 위치 데이터 디버그 다이얼로그
    uiState.locationDebugData?.let { debugData ->
        LocationDebugDialog(
            debugData = debugData,
            isServiceRunning = uiState.isLocationCollectionRunning,
            onDismiss = { viewModel.dismissLocationDebugData() },
            onRefresh = { viewModel.loadLocationDebugData() }
        )
    }
}

@Composable
private fun PreferenceRow(
    label: String,
    value: String?,
    showWarning: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (!value.isNullOrBlank()) value else "미설정",
                fontSize = 17.sp,
                fontFamily = OutfitFontFamily,
                color = if (!value.isNullOrBlank()) colors.textPrimary else colors.textTertiary
            )
        }
        if (showWarning) {
            Icon(Icons.Outlined.Warning, contentDescription = "필수 항목 미설정", tint = colors.accentRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    isGranted: Boolean,
    grantedText: String,
    deniedText: String,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (isGranted) colors.accentBlue else colors.accentRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isGranted) grantedText else deniedText,
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = if (isGranted) colors.accentBlue else colors.accentRed
                )
            }
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
        }
    }
}

/**
 * 마이크 권한 상태 표시 (필수 권한 - 표시만, 클릭 불가)
 */
@Composable
private fun MicrophonePermissionStatusRow(
    isGranted: Boolean
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("마이크 권한", fontSize = 15.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "(필수)",
                    fontSize = 12.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.accentRed
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.Check else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = if (isGranted) colors.accentBlue else colors.accentRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isGranted) "허용됨" else "허용 필요 - 대화 기능 사용 불가",
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = if (isGranted) colors.accentBlue else colors.accentRed
                )
            }
        }
    }
}

/**
 * 위치 수집 상태 표시 (토글 없이 상태만 표시)
 */
@Composable
private fun LocationCollectionStatusRow(
    isRunning: Boolean
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "위치 수집 상태",
                fontSize = 15.sp,
                fontFamily = OutfitFontFamily,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isRunning) colors.accentGreen else colors.textTertiary,
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "수집 중" else "중지됨",
                    fontSize = 17.sp,
                    fontFamily = OutfitFontFamily,
                    color = if (isRunning) colors.accentGreen else colors.textTertiary
                )
            }
        }
    }
}

@Composable
private fun TextFieldDialog(
    title: String,
    hint: String = "",
    initialValue: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    validate: (String) -> String? = { null },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalEchoColors.current
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgCard,
        title = { Text(title, fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = colors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text(hint.ifBlank { title }, fontFamily = OutfitFontFamily, color = colors.textTertiary, fontSize = 14.sp) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, fontFamily = OutfitFontFamily, fontSize = 13.sp) } },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.bgMuted,
                        unfocusedContainerColor = colors.bgMuted,
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.borderSubtle,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = validate(value)
                if (e != null) { error = e } else { onConfirm(value) }
            }) {
                Text("확인", fontFamily = OutfitFontFamily, color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun VoiceSettingsDialog(
    initialSpeed: Double,
    initialTone: String,
    onConfirm: (Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var speed by remember { mutableStateOf(initialSpeed) }
    var tone by remember { mutableStateOf(initialTone) }

    val colors = LocalEchoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgCard,
        title = { Text("음성 설정", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = colors.textPrimary) },
        text = {
            Column {
                Text("음성 속도", fontSize = 15.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${String.format("%.1f", speed)}x  ${when { speed < 1.0 -> "느리게"; speed > 1.0 -> "빠르게"; else -> "보통" }}",
                    fontSize = 15.sp, fontFamily = OutfitFontFamily, color = colors.accentGreen
                )
                Slider(
                    value = speed.toFloat(),
                    onValueChange = { speed = Math.round(it * 10.0) / 10.0 },
                    valueRange = 0.8f..1.2f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = colors.accentGreen, activeTrackColor = colors.accentGreen, inactiveTrackColor = colors.bgMuted)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("느리게", fontSize = 12.sp, fontFamily = OutfitFontFamily, color = colors.textTertiary)
                    Text("빠르게", fontSize = 12.sp, fontFamily = OutfitFontFamily, color = colors.textTertiary)
                }
                Spacer(Modifier.height(20.dp))
                Text("음성 톤", fontSize = 15.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("warm" to "따뜻하게", "calm" to "차분하게", "cheerful" to "밝게").forEach { (value, label) ->
                        FilterChip(
                            selected = tone == value,
                            onClick = { tone = value },
                            label = { Text(label, fontSize = 13.sp, fontFamily = OutfitFontFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(speed, tone) }) {
                Text("확인", fontFamily = OutfitFontFamily, color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun SleepHoursDialog(
    initialValue: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val colors = LocalEchoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgCard,
        title = { Text("선호 수면 시간", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = colors.textPrimary) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = { Text("시간 (1~24)", fontFamily = OutfitFontFamily, color = colors.textTertiary, fontSize = 14.sp) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it, fontFamily = OutfitFontFamily, fontSize = 13.sp) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.bgMuted,
                    unfocusedContainerColor = colors.bgMuted,
                    focusedBorderColor = colors.accentGreen,
                    unfocusedBorderColor = colors.borderSubtle,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (value.isBlank()) {
                    onConfirm(null)
                } else {
                    val h = value.toIntOrNull()
                    if (h == null || h !in 1..24) error = "1~24 사이의 숫자를 입력해주세요"
                    else onConfirm(h)
                }
            }) {
                Text("확인", fontFamily = OutfitFontFamily, color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun DatePickerFieldDialog(
    current: String?,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = remember { parseBirthday(current ?: "") }
    var year by remember { mutableStateOf(initial.first) }
    var month by remember { mutableStateOf(initial.second) }
    var day by remember { mutableStateOf(initial.third) }
    var error by remember { mutableStateOf<String?>(null) }

    val colors = LocalEchoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgCard,
        title = {
            Text("생년월일", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        },
        text = {
            Column {
                BirthdayInputRow(
                    year = year, month = month, day = day,
                    onYearChange = { year = it; error = null },
                    onMonthChange = { month = it; error = null },
                    onDayChange = { day = it; error = null },
                    isError = error != null
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 13.sp, fontFamily = OutfitFontFamily, color = colors.accentRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    year.isBlank() && month.isBlank() && day.isBlank() -> onSelected("")
                    buildBirthdayString(year, month, day) != null -> onSelected(buildBirthdayString(year, month, day)!!)
                    else -> error = "올바른 날짜를 입력해주세요"
                }
            }) {
                Text("확인", fontFamily = OutfitFontFamily, color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun TimePickerFieldDialog(
    current: String?,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(if (!current.isNullOrBlank()) current else "21:00") }

    val colors = LocalEchoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgCard,
        title = {
            Text("대화 시간", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        },
        text = {
            EchoTimePickerContent(value = value, onValueChange = { value = it })
        },
        confirmButton = {
            TextButton(onClick = { onSelected(value) }) {
                Text("확인", fontFamily = OutfitFontFamily, color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun LocationStartTimeDialog(
    current: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(current.ifBlank { "06:00" }) }

    val colors = LocalEchoColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgCard,
        title = {
            Text("위치 수집 시작 시간", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
        },
        text = {
            Column {
                Text(
                    text = "매일 이 시간에 위치 수집을 시작합니다.\n대화를 시작하면 수집이 종료됩니다.",
                    fontSize = 14.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                EchoTimePickerContent(value = value, onValueChange = { value = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(value) }) {
                Text("확인", fontFamily = OutfitFontFamily, color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun CardHeader(
    icon: ImageVector,
    title: String,
    iconTint: Color? = null
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgMuted.copy(alpha = 0.5f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint ?: colors.accentGreen,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = OutfitFontFamily,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun NavigationRow(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, fontFamily = OutfitFontFamily, color = colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                fontFamily = OutfitFontFamily,
                color = colors.textSecondary
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
    }
}

/**
 * 위치 데이터 디버그 다이얼로그
 */
@Composable
private fun LocationDebugDialog(
    debugData: LocationDebugData,
    isServiceRunning: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = LocalEchoColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgCard,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "위치 데이터",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // 요약 정보
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bgMuted
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("오늘 수집", fontSize = 14.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
                            Text("${debugData.todayCount}개", fontSize = 14.sp, fontFamily = OutfitFontFamily, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("마지막 수집", fontSize = 14.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
                            Text(debugData.lastCollectionTime ?: "-", fontSize = 14.sp, fontFamily = OutfitFontFamily, color = colors.textPrimary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("서비스 상태", fontSize = 14.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (isServiceRunning) colors.accentGreen else colors.textTertiary,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isServiceRunning) "수집 중" else "중지됨",
                                    fontSize = 14.sp,
                                    fontFamily = OutfitFontFamily,
                                    color = if (isServiceRunning) colors.accentGreen else colors.textTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 상태 정보 (디버그)
                debugData.statusInfo?.let { status ->
                    LocationStatusSection(status = status)
                    Spacer(Modifier.height(12.dp))
                }

                // 포인트 목록
                if (debugData.points.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "수집된 데이터가 없습니다",
                            fontSize = 14.sp,
                            fontFamily = OutfitFontFamily,
                            color = colors.textTertiary
                        )
                    }
                } else {
                    Text(
                        "수집 포인트",
                        fontSize = 12.sp,
                        fontFamily = OutfitFontFamily,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        debugData.points.forEach { point ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = colors.bgMuted.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "#${point.id}",
                                        fontSize = 12.sp,
                                        fontFamily = OutfitFontFamily,
                                        color = colors.textTertiary,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    Text(
                                        "${String.format("%.4f", point.latitude)}, ${String.format("%.4f", point.longitude)}",
                                        fontSize = 13.sp,
                                        fontFamily = OutfitFontFamily,
                                        color = colors.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        point.time,
                                        fontSize = 12.sp,
                                        fontFamily = OutfitFontFamily,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh) {
                    Text("새로고침", fontFamily = OutfitFontFamily, color = colors.accentBlue)
                }
                TextButton(onClick = onDismiss) {
                    Text("닫기", fontFamily = OutfitFontFamily, color = colors.textSecondary)
                }
            }
        }
    )
}

/**
 * 위치 수집 상태 정보 섹션 (디버그용)
 */
@Composable
private fun LocationStatusSection(status: LocationStatusInfo) {
    val colors = LocalEchoColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.bgMuted
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "시스템 상태",
                fontSize = 12.sp,
                fontFamily = OutfitFontFamily,
                color = colors.textSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 위치 서비스 상태
            StatusRow(
                label = "위치 서비스",
                isOk = status.isLocationEnabled,
                okText = "켜짐",
                errorText = "꺼짐 ⚠️"
            )
            StatusRow(
                label = "GPS",
                isOk = status.isGpsEnabled,
                okText = "켜짐",
                errorText = "꺼짐"
            )
            StatusRow(
                label = "네트워크 위치",
                isOk = status.isNetworkEnabled,
                okText = "켜짐",
                errorText = "꺼짐"
            )

            Spacer(Modifier.height(8.dp))

            // 권한 상태
            StatusRow(
                label = "정밀 위치 권한",
                isOk = status.hasFineLocation,
                okText = "허용됨",
                errorText = "거부됨 ⚠️"
            )
            StatusRow(
                label = "백그라운드 권한",
                isOk = status.hasBackgroundLocation,
                okText = "허용됨",
                errorText = "거부됨 ⚠️"
            )

            Spacer(Modifier.height(8.dp))

            // 기타 상태
            StatusRow(
                label = "배터리 최적화",
                isOk = status.isBatteryOptimizationIgnored,
                okText = "제외됨",
                errorText = "활성화됨 ⚠️"
            )
            StatusRow(
                label = "비행기 모드",
                isOk = !status.isAirplaneModeOn,
                okText = "꺼짐",
                errorText = "켜짐 ⚠️"
            )

            // 삼성 기기 힌트
            if (status.isSamsungDevice && status.isBatteryOptimizationIgnored && !status.isServiceRunning) {
                Spacer(Modifier.height(8.dp))
                val warningColor = Color(0xFFFF9800) // Orange/Amber
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = warningColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        "💡 삼성 기기: 설정 → 배터리 → 백그라운드 사용 제한에서 이 앱을 제외해주세요",
                        fontSize = 11.sp,
                        fontFamily = OutfitFontFamily,
                        color = warningColor,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 상태 표시 행
 */
@Composable
private fun StatusRow(
    label: String,
    isOk: Boolean,
    okText: String,
    errorText: String
) {
    val colors = LocalEchoColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontFamily = OutfitFontFamily,
            color = colors.textSecondary
        )
        Text(
            if (isOk) okText else errorText,
            fontSize = 13.sp,
            fontFamily = OutfitFontFamily,
            color = if (isOk) colors.accentGreen else colors.accentRed,
            fontWeight = if (!isOk) FontWeight.Medium else FontWeight.Normal
        )
    }
}
