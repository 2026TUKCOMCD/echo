package com.example.graduation_project.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.presentation.conversation.components.AiCharacterImage
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import com.example.graduation_project.ui.theme.OutfitFontFamily

@Composable
fun HomeScreen(
    onStartConversation: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        userName = uiState.userName,
        conversationTime = uiState.conversationTime,
        onStartConversation = onStartConversation
    )
}

@Composable
private fun HomeScreenContent(
    userName: String,
    conversationTime: String? = null,
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
        AiCharacterImage(size = 180.dp)

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
            text = "함께 이야기 나눠요",
            fontSize = 18.sp,
            fontFamily = OutfitFontFamily,
            color = colors.textSecondary
        )

        if (!conversationTime.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "오늘 대화 시간: ${formatConversationTime(conversationTime)}",
                fontSize = 16.sp,
                fontFamily = OutfitFontFamily,
                color = colors.textTertiary
            )
        }

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
        "$amPm ${displayHour}:${m.toString().padStart(2, '0')}"
    } catch (e: Exception) { time }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    Graduation_projectTheme {
        HomeScreenContent(userName = "김순자", conversationTime = "09:00", onStartConversation = {})
    }
}
