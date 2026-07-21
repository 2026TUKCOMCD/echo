package com.example.graduation_project.presentation.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.presentation.model.ConversationSummary
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.OutfitFontFamily

/**
 * 일기 탭 (기존 대화기록 탭 대체)
 *
 * 날짜 내림차순 목록:
 * - 일기가 있는 날 = 일기 카드 1개 (탭 → 일기 상세)
 * - 일기가 없는 날 = 그날의 대화 세션 카드 (탭 → 대화 말풍선 상세)
 */
@Composable
fun DiaryScreen(
    onDiaryClick: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    viewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    val colors = LocalEchoColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPage)
    ) {
        Text(
            text = "일기",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFontFamily,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        )

        // 서버 동기화 실패를 조용히 삼키지 않고 배너로 노출 (캐시는 그대로 표시됨)
        uiState.syncError?.let { error ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable { viewModel.refresh() },
                shape = RoundedCornerShape(8.dp),
                color = colors.bgMuted
            ) {
                Text(
                    text = "일기 동기화 실패: $error (눌러서 다시 시도)",
                    fontSize = 14.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.accentRed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentGreen)
            }
        } else if (uiState.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "아직 일기가 없어요\n대화를 나누면 일기가 만들어져요",
                    fontSize = 18.sp,
                    color = colors.textTertiary,
                    fontFamily = OutfitFontFamily,
                    lineHeight = 26.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                items(uiState.items) { item ->
                    when (item) {
                        is DiaryDayItem.DiaryCard -> DiaryCard(
                            diary = item.diary,
                            sessionCount = item.sessionCount,
                            onClick = { onDiaryClick(item.diary.date) }
                        )
                        is DiaryDayItem.SessionCard -> SessionCard(
                            summary = item.summary,
                            onClick = { onConversationClick(item.summary.conversationId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryCard(
    diary: DiaryEntity,
    sessionCount: Int,
    onClick: () -> Unit
) {
    val colors = LocalEchoColors.current
    val hasStaleContent = diary.status == "FAILED" && diary.content != null
    val isTotalFailure = diary.status == "FAILED" && diary.content == null

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatKoreanDate(diary.date),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary
                )
                if (hasStaleContent) {
                    FailureBadge(text = "갱신 실패", backgroundColor = colors.accentBlue)
                } else if (isTotalFailure) {
                    FailureBadge()
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (diary.weather != null) {
                    Text(
                        text = diary.weather,
                        fontSize = 16.sp,
                        fontFamily = OutfitFontFamily,
                        color = colors.textTertiary
                    )
                    Text("·", fontSize = 16.sp, color = colors.textTertiary)
                }
                Text(
                    text = if (sessionCount > 0) "대화 ${sessionCount}회" else "일기",
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textTertiary
                )
            }
            Spacer(Modifier.height(8.dp))
            if (diary.content != null) {
                Text(
                    text = diary.content,
                    fontSize = 18.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textSecondary,
                    maxLines = 2,
                    lineHeight = 26.sp
                )
            }
            if (hasStaleContent) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "오늘 대화 내용이 아직 반영되지 않았어요.",
                    fontSize = 14.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.accentBlue,
                    maxLines = 1
                )
            } else if (isTotalFailure) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "일기를 만들지 못했어요. 잠시 후 다시 확인해 주세요.",
                    fontSize = 14.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.accentRed,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun FailureBadge(
    text: String = "일기 생성 실패",
    backgroundColor: Color? = null
) {
    val colors = LocalEchoColors.current
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = OutfitFontFamily,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor ?: colors.accentRed)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/**
 * 일기가 없는 날의 대화 세션 카드 (기존 대화기록 탭 카드 스타일 유지)
 */
@Composable
internal fun SessionCard(
    summary: ConversationSummary,
    onClick: () -> Unit,
    showDate: Boolean = true
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
            if (showDate) {
                Text(
                    text = summary.date,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = summary.timeRange,
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textTertiary
                )
                Text("·", fontSize = 16.sp, color = colors.textTertiary)
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
