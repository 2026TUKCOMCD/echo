package com.example.graduation_project.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.ui.theme.EchoAccentGreen
import com.example.graduation_project.ui.theme.EchoAccentRed
import com.example.graduation_project.ui.theme.EchoBgCard
import com.example.graduation_project.ui.theme.EchoBgMuted
import com.example.graduation_project.ui.theme.EchoBgPage
import com.example.graduation_project.ui.theme.EchoBorderSubtle
import com.example.graduation_project.ui.theme.EchoTextPrimary
import com.example.graduation_project.ui.theme.EchoTextSecondary
import com.example.graduation_project.ui.theme.EchoTextTertiary
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import com.example.graduation_project.ui.theme.OutfitFontFamily

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingsScreenContent(
        uiState = uiState,
        onSpeedChange = viewModel::onSpeedChange,
        onLogout = onLogout
    )
}

@Composable
private fun SettingsScreenContent(
    uiState: SettingsUiState,
    onSpeedChange: (Float) -> Unit,
    onLogout: () -> Unit
) {
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
            color = EchoBgCard,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = EchoBgMuted
                ) {
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

        Spacer(Modifier.height(20.dp))

        // 음성 속도 섹션
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = EchoBgCard,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "음성 속도",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = OutfitFontFamily,
                    color = EchoTextPrimary
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "현재 속도",
                        fontSize = 15.sp,
                        fontFamily = OutfitFontFamily,
                        color = EchoTextSecondary
                    )
                    Text(
                        text = "${String.format("%.1f", uiState.voiceSpeed)}x  ${
                            when {
                                uiState.voiceSpeed < 1.0f -> "느리게"
                                uiState.voiceSpeed > 1.0f -> "빠르게"
                                else -> "보통"
                            }
                        }",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = OutfitFontFamily,
                        color = EchoAccentGreen
                    )
                }

                Spacer(Modifier.height(8.dp))

                Slider(
                    value = uiState.voiceSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.8f..1.2f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = EchoAccentGreen,
                        activeTrackColor = EchoAccentGreen,
                        inactiveTrackColor = EchoBgMuted
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "느리게",
                        fontSize = 13.sp,
                        fontFamily = OutfitFontFamily,
                        color = EchoTextTertiary
                    )
                    Text(
                        text = "빠르게",
                        fontSize = 13.sp,
                        fontFamily = OutfitFontFamily,
                        color = EchoTextTertiary
                    )
                }

                if (uiState.isSaved) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "저장되었습니다",
                        fontSize = 14.sp,
                        fontFamily = OutfitFontFamily,
                        color = EchoAccentGreen
                    )
                }
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
            colors = ButtonDefaults.buttonColors(
                containerColor = EchoAccentRed,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "로그아웃",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OutfitFontFamily
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    Graduation_projectTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(
                userName = "김순자",
                age = 72,
                voiceSpeed = 1.0f,
                isLoading = false
            ),
            onSpeedChange = {},
            onLogout = {}
        )
    }
}
