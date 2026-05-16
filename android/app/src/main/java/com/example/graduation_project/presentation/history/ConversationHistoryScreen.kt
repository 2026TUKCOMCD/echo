package com.example.graduation_project.presentation.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.presentation.model.ConversationSummary
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.Graduation_projectTheme
import com.example.graduation_project.ui.theme.OutfitFontFamily

@Composable
fun ConversationHistoryScreen(
    onConversationClick: (String) -> Unit,
    viewModel: ConversationHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val colors = LocalEchoColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPage)
    ) {
        Text(
            text = "대화 기록",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFontFamily,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        )

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentGreen)
            }
        } else if (uiState.summaries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "아직 대화 기록이 없어요",
                    fontSize = 18.sp,
                    color = colors.textTertiary,
                    fontFamily = OutfitFontFamily
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 24.dp, vertical = 8.dp
                )
            ) {
                items(uiState.summaries) { summary ->
                    ConversationCard(
                        summary = summary,
                        onClick = { onConversationClick(summary.conversationId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    summary: ConversationSummary,
    onClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = colors.bgCard,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = summary.date,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = summary.timeRange,
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textTertiary
                )
                Text(
                    text = "·",
                    fontSize = 16.sp,
                    color = colors.textTertiary
                )
                Text(
                    text = "${summary.durationMin}분",
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textTertiary
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = summary.previewText,
                fontSize = 18.sp,
                fontFamily = OutfitFontFamily,
                color = colors.textSecondary,
                maxLines = 2,
                lineHeight = 26.sp
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HistoryScreenPreview() {
    Graduation_projectTheme {
        val colors = LocalEchoColors.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bgPage)
        ) {
            Text(
                text = "대화 기록",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 24.dp, vertical = 8.dp
                )
            ) {
                items(
                    listOf(
                        ConversationSummary("1", "2025년 5월 10일 (토)", "오전 10:30 ~ 10:45", 15, "안녕하세요! 오늘 하루는 어떠셨나요?"),
                        ConversationSummary("2", "2025년 5월 9일 (금)", "오후 3:00 ~ 3:20", 20, "산책을 다녀오셨군요!")
                    )
                ) { summary ->
                    ConversationCard(summary = summary, onClick = {})
                }
            }
        }
    }
}
