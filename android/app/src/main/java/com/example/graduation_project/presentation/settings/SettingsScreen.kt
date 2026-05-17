package com.example.graduation_project.presentation.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 설정에서 돌아왔을 때 권한 상태 새로고침
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    // 마이크 권한 상태 표시 (필수 권한)
                    MicrophonePermissionRow(
                        isGranted = PermissionChecker.hasMicrophonePermission(context),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
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
                    PreferenceRow(
                        label = "선호 수면 시간",
                        value = uiState.preferredSleepHours?.let { "${it}시간" },
                        enabled = enabled
                    ) { editingField = "sleepHours" }
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PermissionStatusRow(
                        label = "건강 데이터 권한",
                        isGranted = uiState.hasHealthConnectPermission,
                        grantedText = "허용됨",
                        deniedText = "허용 필요"
                    )
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
                    // 위치 수집 상태 토글
                    LocationCollectionToggleRow(
                        isRunning = uiState.isLocationCollectionRunning,
                        hasPermission = uiState.hasBackgroundLocationPermission,
                        onToggle = { viewModel.toggleLocationCollection() }
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow(
                        label = "수집 시작 시간",
                        value = uiState.locationCollectionStartTime,
                        enabled = enabled
                    ) { editingField = "locationStartTime" }
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
                    // 대화 시간 알림 토글
                    NotificationToggleRow(
                        label = "대화 시간 알림",
                        description = "설정한 대화 시간에 알림",
                        enabled = uiState.alarmEnabled,
                        hasNotificationPermission = uiState.hasNotificationPermission,
                        onToggle = { viewModel.setAlarmEnabled(it) }
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    // 아침 인사 알림 토글
                    NotificationToggleRow(
                        label = "아침 인사 알림",
                        description = "위치 수집 시작 시 알림",
                        enabled = uiState.morningGreetingEnabled,
                        hasNotificationPermission = uiState.hasNotificationPermission,
                        onToggle = { viewModel.setMorningGreetingEnabled(it) }
                    )
                    HorizontalDivider(color = colors.borderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
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
                    // 앱 권한 설정
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
 * 마이크 권한 상태 표시 (필수 권한 - 대화 기능에 필수)
 */
@Composable
private fun MicrophonePermissionRow(
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary)
    }
}

/**
 * 알림 토글 Row (알림 권한 없으면 비활성화)
 */
@Composable
private fun NotificationToggleRow(
    label: String,
    description: String,
    enabled: Boolean,
    hasNotificationPermission: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    !hasNotificationPermission -> "알림 권한 필요"
                    enabled -> description
                    else -> "알림 꺼짐"
                },
                fontSize = 17.sp,
                fontFamily = OutfitFontFamily,
                color = when {
                    !hasNotificationPermission -> colors.accentRed
                    enabled -> colors.textPrimary
                    else -> colors.textTertiary
                }
            )
        }
        Switch(
            checked = enabled && hasNotificationPermission,
            onCheckedChange = { onToggle(it) },
            enabled = hasNotificationPermission,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.bgMuted
            )
        )
    }
}

@Composable
private fun LocationCollectionToggleRow(
    isRunning: Boolean,
    hasPermission: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("위치 수집 상태", fontSize = 15.sp, fontFamily = OutfitFontFamily, color = colors.textSecondary)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isRunning) colors.accentGreen else colors.textTertiary,
                            shape = androidx.compose.foundation.shape.CircleShape
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
        Switch(
            checked = isRunning,
            onCheckedChange = { onToggle() },
            enabled = hasPermission,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.bgMuted
            )
        )
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
