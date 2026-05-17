package com.example.graduation_project.presentation.home

import android.app.Application
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.R
import com.example.graduation_project.data.model.WeatherResponse
import com.example.graduation_project.presentation.conversation.components.AnimatedWebpImage
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import com.example.graduation_project.ui.theme.OutfitFontFamily

@Composable
fun HomeScreen(
    onStartConversation: () -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        userName = uiState.userName,
        conversationTime = uiState.conversationTime,
        date = uiState.date,
        weather = uiState.weather,
        onStartConversation = onStartConversation
    )
}

@Composable
private fun HomeScreenContent(
    userName: String,
    conversationTime: String? = null,
    date: String = "",
    weather: WeatherResponse? = null,
    onStartConversation: () -> Unit
) {
    val colors = LocalEchoColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (date.isNotBlank()) {
                Text(
                    text = date,
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textSecondary
                )
            }

            if (weather != null) {
                if (date.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "·",
                        fontSize = 14.sp,
                        color = colors.textTertiary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = getWeatherIcon(weather.description),
                    contentDescription = weather.description,
                    modifier = Modifier.size(16.dp),
                    tint = colors.textTertiary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${weather.temperature}°C",
                    fontSize = 14.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textTertiary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedWebpImage(
            resId = R.raw.echo_05_greeting,
            modifier = Modifier.size(180.dp)
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = if (userName.isNotBlank()) "안녕하세요, ${userName}님" else "안녕하세요",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = OutfitFontFamily,
            color = colors.textPrimary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (!conversationTime.isNullOrBlank()) {
                "${formatConversationTime(conversationTime)}에 만나요"
            } else {
                "함께 이야기 나눠요"
            },
            fontSize = 18.sp,
            fontFamily = OutfitFontFamily,
            color = colors.textSecondary
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onStartConversation,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentGreen,
                contentColor = Color.White
            )
        ) {
            Text(
                text = "대화 시작",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = OutfitFontFamily
            )
        }
    }
}

private fun formatConversationTime(time: String): String {
    return try {
        val (h, m) = time.split(":").map { it.toInt() }
        val amPm = if (h < 12) "오전" else "오후"
        val displayHour = if (h % 12 == 0) 12 else h % 12
        "$amPm ${displayHour}시 ${m.toString().padStart(2, '0')}분"
    } catch (e: Exception) { time }
}

private fun getWeatherIcon(description: String) = when {
    description.contains("맑") -> Icons.Filled.WbSunny
    description.contains("흐") -> Icons.Filled.WbCloudy
    description.contains("구름") -> Icons.Filled.Cloud
    description.contains("비") || description.contains("소나기") -> Icons.Filled.WaterDrop
    description.contains("눈") -> Icons.Filled.Cloud
    description.contains("천둥") || description.contains("번개") -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.WbSunny
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    Graduation_projectTheme {
        HomeScreenContent(
            userName = "김순자",
            conversationTime = "21:00",
            date = "5월 16일 금요일",
            weather = WeatherResponse(description = "맑음", temperature = 22),
            onStartConversation = {}
        )
    }
}
