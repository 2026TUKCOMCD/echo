package com.example.graduation_project.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.presentation.auth.EchoOutlinedTextField
import com.example.graduation_project.presentation.common.BirthdayInputRow
import com.example.graduation_project.presentation.common.EchoTimePickerContent
import com.example.graduation_project.presentation.common.buildBirthdayString
import com.example.graduation_project.presentation.common.parseBirthday
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.EchoBgMuted
import com.example.graduation_project.ui.theme.EchoBgPage
import com.example.graduation_project.ui.theme.EchoTextPrimary
import com.example.graduation_project.ui.theme.EchoTextSecondary
import com.example.graduation_project.ui.theme.EchoTextTertiary
import com.example.graduation_project.ui.theme.OutfitFontFamily

private val stepTitles = listOf(
    "생년월일", "거주 지역", "대화 시간",
    "보호자 이메일", "가족 관계", "직업", "취미", "선호 대화 주제", "음성 설정", "선호 수면 시간"
)

private val stepHints = listOf(
    "생일을 알려주세요 (필수)",
    "예) 서울 강남구 (필수)",
    "매일 같은 시간에 대화를 시작합니다 (필수)",
    "보호자 이메일을 입력해주세요",
    "예) 딸, 아들, 배우자",
    "예) 전직 교사, 은행원",
    "예) 정원 가꾸기, 독서",
    "예) 옛날 이야기, 건강",
    "음성 속도와 톤을 설정해주세요",
    "하루 평균 몇 시간 주무시나요?"
)

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) onOnboardingComplete()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EchoBgPage)
    ) {
        // 상단 진행률
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "초기 설정",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFontFamily,
                    color = EchoTextPrimary
                )
                Text(
                    text = "${uiState.currentStep + 1} / $ONBOARDING_STEPS",
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = EchoTextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { (uiState.currentStep + 1).toFloat() / ONBOARDING_STEPS },
                modifier = Modifier.fillMaxWidth(),
                color = EchoAccentGreen,
                trackColor = EchoBgMuted
            )
        }

        // 본문
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = stepTitles[uiState.currentStep],
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = EchoTextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stepHints[uiState.currentStep],
                fontSize = 15.sp,
                fontFamily = OutfitFontFamily,
                color = EchoTextSecondary
            )
            Spacer(Modifier.height(32.dp))

            StepContent(
                step = uiState.currentStep,
                uiState = uiState,
                onBirthdayChange = viewModel::updateBirthday,
                onFamilyInfoChange = viewModel::updateFamilyInfo,
                onGuardianEmailChange = viewModel::updateGuardianEmail,
                onLocationChange = viewModel::updateLocation,
                onOccupationChange = viewModel::updateOccupation,
                onHobbiesChange = viewModel::updateHobbies,
                onPreferredTopicsChange = viewModel::updatePreferredTopics,
                onVoiceSpeedChange = viewModel::updateVoiceSpeed,
                onVoiceToneChange = viewModel::updateVoiceTone,
                onConversationTimeChange = viewModel::updateConversationTime,
                onSleepHoursChange = viewModel::updatePreferredSleepHours
            )

            uiState.fieldError?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 14.sp,
                    fontFamily = OutfitFontFamily,
                    color = androidx.compose.ui.graphics.Color(0xFFE53935)
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        // 하단 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.currentStep > 0) {
                OutlinedButton(
                    onClick = viewModel::previous,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "이전",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OutfitFontFamily,
                        color = EchoAccentGreen
                    )
                }
            }
            Button(
                onClick = viewModel::next,
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .weight(if (uiState.currentStep > 0) 1f else Float.MAX_VALUE)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EchoAccentGreen,
                    contentColor = Color.White,
                    disabledContainerColor = EchoAccentGreen.copy(alpha = 0.6f)
                )
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (uiState.currentStep < ONBOARDING_STEPS - 1) "다음" else "완료",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OutfitFontFamily
                    )
                }
            }
        }

        SnackbarHost(snackbarHostState)
    }
}

@Composable
private fun StepContent(
    step: Int,
    uiState: OnboardingUiState,
    onBirthdayChange: (String) -> Unit,
    onFamilyInfoChange: (String) -> Unit,
    onGuardianEmailChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onOccupationChange: (String) -> Unit,
    onHobbiesChange: (String) -> Unit,
    onPreferredTopicsChange: (String) -> Unit,
    onVoiceSpeedChange: (Double) -> Unit,
    onVoiceToneChange: (String) -> Unit,
    onConversationTimeChange: (String) -> Unit,
    onSleepHoursChange: (String) -> Unit
) {
    when (step) {
        0 -> DatePickerStep(selected = uiState.birthday, onDateSelected = onBirthdayChange)
        1 -> EchoOutlinedTextField(
            value = uiState.location,
            onValueChange = onLocationChange,
            label = "거주 지역",
            isError = uiState.fieldError != null
        )
        2 -> TimePickerStep(selected = uiState.conversationTime, onTimeSelected = onConversationTimeChange)
        3 -> EchoOutlinedTextField(
            value = uiState.guardianEmail,
            onValueChange = onGuardianEmailChange,
            label = "보호자 이메일",
            keyboardType = KeyboardType.Email,
            isError = uiState.fieldError != null
        )
        4 -> EchoOutlinedTextField(value = uiState.familyInfo, onValueChange = onFamilyInfoChange, label = "가족 관계")
        5 -> EchoOutlinedTextField(value = uiState.occupation, onValueChange = onOccupationChange, label = "직업")
        6 -> EchoOutlinedTextField(value = uiState.hobbies, onValueChange = onHobbiesChange, label = "취미")
        7 -> EchoOutlinedTextField(value = uiState.preferredTopics, onValueChange = onPreferredTopicsChange, label = "선호 대화 주제")
        8 -> VoiceSettingsStep(
            voiceSpeed = uiState.voiceSpeed,
            voiceTone = uiState.voiceTone,
            onSpeedChange = onVoiceSpeedChange,
            onToneChange = onVoiceToneChange
        )
        9 -> EchoOutlinedTextField(
            value = uiState.preferredSleepHours,
            onValueChange = onSleepHoursChange,
            label = "수면 시간 (시간)",
            keyboardType = KeyboardType.Number,
            isError = uiState.fieldError != null
        )
    }
}

@Composable
private fun DatePickerStep(selected: String, onDateSelected: (String) -> Unit) {
    val initial = remember { parseBirthday(selected) }
    var year by remember { mutableStateOf(initial.first) }
    var month by remember { mutableStateOf(initial.second) }
    var day by remember { mutableStateOf(initial.third) }

    fun notifyChange(y: String, m: String, d: String) {
        val built = buildBirthdayString(y, m, d)
        if (built != null) onDateSelected(built)
        else if (y.isBlank() && m.isBlank() && d.isBlank()) onDateSelected("")
    }

    BirthdayInputRow(
        year = year, month = month, day = day,
        onYearChange = { year = it; notifyChange(it, month, day) },
        onMonthChange = { month = it; notifyChange(year, it, day) },
        onDayChange = { day = it; notifyChange(year, month, it) }
    )
}

@Composable
private fun TimePickerStep(selected: String, onTimeSelected: (String) -> Unit) {
    val initial = if (selected.isNotEmpty()) selected else "09:00"
    LaunchedEffect(Unit) {
        if (selected.isEmpty()) onTimeSelected("09:00")
    }
    EchoTimePickerContent(value = initial, onValueChange = onTimeSelected)
}

@Composable
private fun VoiceSettingsStep(
    voiceSpeed: Double,
    voiceTone: String,
    onSpeedChange: (Double) -> Unit,
    onToneChange: (String) -> Unit
) {
    Column {
        Text(
            text = "음성 속도",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OutfitFontFamily,
            color = EchoTextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${String.format("%.1f", voiceSpeed)}x  ${
                when {
                    voiceSpeed < 1.0 -> "느리게"
                    voiceSpeed > 1.0 -> "빠르게"
                    else -> "보통"
                }
            }",
            fontSize = 15.sp,
            fontFamily = OutfitFontFamily,
            color = EchoAccentGreen
        )
        androidx.compose.material3.Slider(
            value = voiceSpeed.toFloat(),
            onValueChange = { onSpeedChange(Math.round(it * 10.0) / 10.0) },
            valueRange = 0.8f..1.2f,
            steps = 3,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = EchoAccentGreen,
                activeTrackColor = EchoAccentGreen,
                inactiveTrackColor = EchoBgMuted
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("느리게", fontSize = 13.sp, fontFamily = OutfitFontFamily, color = EchoTextTertiary)
            Text("빠르게", fontSize = 13.sp, fontFamily = OutfitFontFamily, color = EchoTextTertiary)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "음성 톤",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = OutfitFontFamily,
            color = EchoTextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val tones = listOf("warm" to "따뜻하게", "calm" to "차분하게", "cheerful" to "밝게")
            tones.forEach { (value, label) ->
                FilterChip(
                    selected = voiceTone == value,
                    onClick = { onToneChange(value) },
                    label = {
                        Text(label, fontSize = 14.sp, fontFamily = OutfitFontFamily)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EchoAccentGreen,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}
