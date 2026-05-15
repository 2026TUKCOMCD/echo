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
import com.example.graduation_project.data.model.UserPreferences
import com.example.graduation_project.data.model.VoiceSettings
import com.example.graduation_project.presentation.common.BirthdayInputRow
import com.example.graduation_project.presentation.common.EchoTimePickerContent
import com.example.graduation_project.presentation.common.buildBirthdayString
import com.example.graduation_project.presentation.common.parseBirthday
import com.example.graduation_project.presentation.health.openHealthConnectSettings
import com.example.graduation_project.ui.theme.EchoAccentBlue
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.EchoAccentRed
import com.example.graduation_project.ui.theme.EchoBgCard
import com.example.graduation_project.ui.theme.EchoBgMuted
import com.example.graduation_project.ui.theme.EchoBgPage
import com.example.graduation_project.ui.theme.EchoBorderSubtle
import com.example.graduation_project.ui.theme.EchoTextPrimary
import com.example.graduation_project.ui.theme.EchoTextSecondary
import com.example.graduation_project.ui.theme.EchoTextTertiary
import com.example.graduation_project.ui.theme.OutfitFontFamily

private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

private fun buildPreferences(
    state: SettingsUiState,
    birthday: String? = state.birthday,
    familyInfo: String? = state.familyInfo,
    guardianEmail: String? = state.guardianEmail,
    location: String? = state.location,
    occupation: String? = state.occupation,
    hobbies: String? = state.hobbies,
    preferredTopics: String? = state.preferredTopics,
    voiceSpeed: Double = state.voiceSpeed,
    voiceTone: String = state.voiceTone,
    conversationTime: String? = state.conversationTime,
    preferredSleepHours: Int? = state.preferredSleepHours
): UserPreferences = UserPreferences(
    birthday = birthday,
    familyInfo = familyInfo,
    guardianEmail = guardianEmail,
    location = location,
    occupation = occupation,
    hobbies = hobbies,
    preferredTopics = preferredTopics,
    voiceSettings = VoiceSettings(voiceSpeed = voiceSpeed, voiceTone = voiceTone),
    conversationTime = conversationTime,
    preferredSleepHours = preferredSleepHours
)

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
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

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EchoBgPage)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "설정",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = EchoTextPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )

            // 프로필 카드
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = EchoBgCard
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = EchoBgMuted) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = EchoTextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (uiState.userName.isNotBlank()) uiState.userName else "사용자",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = OutfitFontFamily,
                            color = EchoTextPrimary
                        )
                        if (uiState.age != null) {
                            Text(
                                text = "${uiState.age}세",
                                fontSize = 15.sp,
                                fontFamily = OutfitFontFamily,
                                color = EchoTextSecondary
                            )
                        }
                    }
                }
            }

            val enabled = !uiState.isSaving

            Spacer(Modifier.height(20.dp))

            // ===== 대화 설정 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = EchoBgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        title = "대화 설정"
                    )
                    PreferenceRow("대화 시간", uiState.conversationTime, enabled = enabled) { editingField = "conversationTime" }
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    AlarmToggleRow(
                        enabled = uiState.alarmEnabled,
                        onToggle = { viewModel.setAlarmEnabled(it) }
                    )
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
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
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
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
                color = EchoBgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.Person,
                        title = "내 정보",
                        iconTint = EchoAccentBlue
                    )
                    PreferenceRow("생년월일", uiState.birthday, enabled = enabled) { editingField = "birthday" }
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow("거주 지역", uiState.location, enabled = enabled) { editingField = "location" }
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow("직업", uiState.occupation, enabled = enabled) { editingField = "occupation" }
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
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
                color = EchoBgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.People,
                        title = "보호자 연결",
                        iconTint = Color(0xFFFF9800) // Orange
                    )
                    PreferenceRow(
                        label = "보호자 이메일",
                        value = uiState.guardianEmail,
                        showWarning = uiState.guardianEmail.isNullOrBlank(),
                        enabled = enabled
                    ) { editingField = "guardianEmail" }
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
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
                color = EchoBgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.Favorite,
                        title = "건강 정보",
                        iconTint = EchoAccentRed
                    )
                    PreferenceRow(
                        label = "선호 수면 시간",
                        value = uiState.preferredSleepHours?.let { "${it}시간" },
                        enabled = enabled
                    ) { editingField = "sleepHours" }
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PermissionStatusRow(
                        label = "건강 데이터 권한",
                        isGranted = uiState.hasHealthConnectPermission,
                        grantedText = "허용됨",
                        deniedText = "탭하여 설정",
                        onClick = { openHealthConnectSettings(context) }
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
                color = EchoBgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.LocationOn,
                        title = "위치 수집",
                        iconTint = EchoAccentBlue
                    )
                    PermissionStatusRow(
                        label = "위치 권한",
                        isGranted = uiState.hasBackgroundLocationPermission,
                        grantedText = "항상 허용됨",
                        deniedText = "탭하여 설정",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
                    PreferenceRow(
                        label = "수집 시작 시간",
                        value = uiState.locationCollectionStartTime,
                        enabled = enabled
                    ) { editingField = "locationStartTime" }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ===== 앱 설정 섹션 =====
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(16.dp),
                color = EchoBgCard
            ) {
                Column {
                    CardHeader(
                        icon = Icons.Outlined.Settings,
                        title = "앱 설정",
                        iconTint = EchoTextSecondary
                    )
                    // 앱 알림 설정
                    NavigationRow(
                        label = "앱 알림 설정",
                        description = "시스템 알림 설정으로 이동",
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
                    HorizontalDivider(color = EchoBorderSubtle, modifier = Modifier.padding(horizontal = 20.dp))
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
                colors = ButtonDefaults.buttonColors(containerColor = EchoAccentRed, contentColor = Color.White)
            ) {
                Text("로그아웃", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = OutfitFontFamily)
            }

            Spacer(Modifier.height(24.dp))
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // 다이얼로그
    val save: (UserPreferences) -> Unit = {
        viewModel.savePreferences(it)
        editingField = null
    }

    when (editingField) {
        "birthday" -> DatePickerFieldDialog(
            current = uiState.birthday,
            onSelected = { save(buildPreferences(uiState, birthday = it)) },
            onDismiss = { editingField = null }
        )
        "familyInfo" -> TextFieldDialog(
            title = "가족 관계",
            hint = "예) 딸, 아들, 배우자",
            initialValue = uiState.familyInfo ?: "",
            onConfirm = { save(buildPreferences(uiState, familyInfo = it.ifBlank { null })) },
            onDismiss = { editingField = null }
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
            onConfirm = { save(buildPreferences(uiState, guardianEmail = it.ifBlank { null })) },
            onDismiss = { editingField = null }
        )
        "location" -> TextFieldDialog(
            title = "거주 지역",
            hint = "예) 서울 강남구",
            initialValue = uiState.location ?: "",
            onConfirm = { save(buildPreferences(uiState, location = it.ifBlank { null })) },
            onDismiss = { editingField = null }
        )
        "occupation" -> TextFieldDialog(
            title = "직업",
            hint = "예) 전직 교사, 은행원",
            initialValue = uiState.occupation ?: "",
            onConfirm = { save(buildPreferences(uiState, occupation = it.ifBlank { null })) },
            onDismiss = { editingField = null }
        )
        "hobbies" -> TextFieldDialog(
            title = "취미",
            hint = "예) 정원 가꾸기, 독서",
            initialValue = uiState.hobbies ?: "",
            onConfirm = { save(buildPreferences(uiState, hobbies = it.ifBlank { null })) },
            onDismiss = { editingField = null }
        )
        "preferredTopics" -> TextFieldDialog(
            title = "선호 대화 주제",
            hint = "예) 옛날 이야기, 건강",
            initialValue = uiState.preferredTopics ?: "",
            onConfirm = { save(buildPreferences(uiState, preferredTopics = it.ifBlank { null })) },
            onDismiss = { editingField = null }
        )
        "voiceSettings" -> VoiceSettingsDialog(
            initialSpeed = uiState.voiceSpeed,
            initialTone = uiState.voiceTone,
            onConfirm = { speed, tone -> save(buildPreferences(uiState, voiceSpeed = speed, voiceTone = tone)) },
            onDismiss = { editingField = null }
        )
        "conversationTime" -> TimePickerFieldDialog(
            current = uiState.conversationTime,
            onSelected = { save(buildPreferences(uiState, conversationTime = it)) },
            onDismiss = { editingField = null }
        )
        "sleepHours" -> SleepHoursDialog(
            initialValue = uiState.preferredSleepHours,
            onConfirm = { save(buildPreferences(uiState, preferredSleepHours = it)) },
            onDismiss = { editingField = null }
        )
        "locationStartTime" -> LocationStartTimeDialog(
            current = uiState.locationCollectionStartTime,
            onSelected = {
                viewModel.setLocationCollectionStartTime(it)
                editingField = null
            },
            onDismiss = { editingField = null }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontFamily = OutfitFontFamily, color = EchoTextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (!value.isNullOrBlank()) value else "미설정",
                fontSize = 16.sp,
                fontFamily = OutfitFontFamily,
                color = if (!value.isNullOrBlank()) EchoTextPrimary else EchoTextTertiary
            )
        }
        if (showWarning) {
            Icon(Icons.Outlined.Warning, contentDescription = "필수 항목 미설정", tint = EchoAccentRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = EchoTextTertiary)
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    isGranted: Boolean,
    grantedText: String,
    deniedText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, fontFamily = OutfitFontFamily, color = EchoTextSecondary)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (isGranted) EchoAccentBlue else EchoAccentRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isGranted) grantedText else deniedText,
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = if (isGranted) EchoAccentBlue else EchoAccentRed
                )
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = EchoTextTertiary)
    }
}

@Composable
private fun AlarmToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("대화 시간 알림", fontSize = 14.sp, fontFamily = OutfitFontFamily, color = EchoTextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (enabled) "매일 알림을 받습니다" else "알림 꺼짐",
                fontSize = 16.sp,
                fontFamily = OutfitFontFamily,
                color = if (enabled) EchoTextPrimary else EchoTextTertiary
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EchoAccentGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = EchoBgMuted
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
    var value by remember { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoBgCard,
        title = { Text(title, fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = EchoTextPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text(hint.ifBlank { title }, fontFamily = OutfitFontFamily, color = EchoTextTertiary, fontSize = 14.sp) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, fontFamily = OutfitFontFamily, fontSize = 13.sp) } },
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = EchoBgMuted,
                        unfocusedContainerColor = EchoBgMuted,
                        focusedBorderColor = EchoAccentGreen,
                        unfocusedBorderColor = EchoBorderSubtle,
                        focusedTextColor = EchoTextPrimary,
                        unfocusedTextColor = EchoTextPrimary
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
                Text("확인", fontFamily = OutfitFontFamily, color = EchoAccentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = EchoTextSecondary)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoBgCard,
        title = { Text("음성 설정", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = EchoTextPrimary) },
        text = {
            Column {
                Text("음성 속도", fontSize = 15.sp, fontFamily = OutfitFontFamily, color = EchoTextSecondary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${String.format("%.1f", speed)}x  ${when { speed < 1.0 -> "느리게"; speed > 1.0 -> "빠르게"; else -> "보통" }}",
                    fontSize = 15.sp, fontFamily = OutfitFontFamily, color = EchoAccentGreen
                )
                Slider(
                    value = speed.toFloat(),
                    onValueChange = { speed = Math.round(it * 10.0) / 10.0 },
                    valueRange = 0.8f..1.2f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = EchoAccentGreen, activeTrackColor = EchoAccentGreen, inactiveTrackColor = EchoBgMuted)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("느리게", fontSize = 12.sp, fontFamily = OutfitFontFamily, color = EchoTextTertiary)
                    Text("빠르게", fontSize = 12.sp, fontFamily = OutfitFontFamily, color = EchoTextTertiary)
                }
                Spacer(Modifier.height(20.dp))
                Text("음성 톤", fontSize = 15.sp, fontFamily = OutfitFontFamily, color = EchoTextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("warm" to "따뜻하게", "calm" to "차분하게", "cheerful" to "밝게").forEach { (value, label) ->
                        FilterChip(
                            selected = tone == value,
                            onClick = { tone = value },
                            label = { Text(label, fontSize = 13.sp, fontFamily = OutfitFontFamily) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EchoAccentGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(speed, tone) }) {
                Text("확인", fontFamily = OutfitFontFamily, color = EchoAccentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = EchoTextSecondary)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoBgCard,
        title = { Text("선호 수면 시간", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = EchoTextPrimary) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = { Text("시간 (1~24)", fontFamily = OutfitFontFamily, color = EchoTextTertiary, fontSize = 14.sp) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it, fontFamily = OutfitFontFamily, fontSize = 13.sp) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = EchoBgMuted,
                    unfocusedContainerColor = EchoBgMuted,
                    focusedBorderColor = EchoAccentGreen,
                    unfocusedBorderColor = EchoBorderSubtle,
                    focusedTextColor = EchoTextPrimary,
                    unfocusedTextColor = EchoTextPrimary
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
                Text("확인", fontFamily = OutfitFontFamily, color = EchoAccentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = EchoTextSecondary)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoBgCard,
        title = {
            Text("생년월일", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = EchoTextPrimary)
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
                    Text(it, fontSize = 13.sp, fontFamily = OutfitFontFamily, color = EchoAccentRed)
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
                Text("확인", fontFamily = OutfitFontFamily, color = EchoAccentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = EchoTextSecondary)
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
    var value by remember { mutableStateOf(if (!current.isNullOrBlank()) current else "09:00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoBgCard,
        title = {
            Text("대화 시간", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = EchoTextPrimary)
        },
        text = {
            EchoTimePickerContent(value = value, onValueChange = { value = it })
        },
        confirmButton = {
            TextButton(onClick = { onSelected(value) }) {
                Text("확인", fontFamily = OutfitFontFamily, color = EchoAccentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = EchoTextSecondary)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EchoBgCard,
        title = {
            Text("위치 수집 시작 시간", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, color = EchoTextPrimary)
        },
        text = {
            Column {
                Text(
                    text = "매일 이 시간에 위치 수집을 시작합니다.\n대화를 시작하면 수집이 종료됩니다.",
                    fontSize = 14.sp,
                    fontFamily = OutfitFontFamily,
                    color = EchoTextSecondary,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
                EchoTimePickerContent(value = value, onValueChange = { value = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(value) }) {
                Text("확인", fontFamily = OutfitFontFamily, color = EchoAccentGreen, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", fontFamily = OutfitFontFamily, color = EchoTextSecondary)
            }
        }
    )
}

@Composable
private fun CardHeader(
    icon: ImageVector,
    title: String,
    iconTint: Color = EchoAccentGreen
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EchoBgMuted.copy(alpha = 0.5f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = OutfitFontFamily,
            color = EchoTextPrimary
        )
    }
}

@Composable
private fun NavigationRow(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, fontFamily = OutfitFontFamily, color = EchoTextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                fontFamily = OutfitFontFamily,
                color = EchoTextSecondary
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = EchoTextTertiary)
    }
}
