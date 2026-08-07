package com.example.graduation_project.presentation.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.graduation_project.ui.theme.EchoColorScheme
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.OutfitFontFamily
import java.time.LocalDate
import java.time.YearMonth

private val WEEKDAY_LABELS = listOf("일", "월", "화", "수", "목", "금", "토")

/**
 * 일기 탭 (기존 대화기록 탭 대체)
 *
 * 월간 캘린더로 날짜를 짚어 그날의 일기/대화 세션을 확인:
 * - 일기가 있는 날 → 탭 시 일기 상세로 이동
 * - 일기는 없고 세션이 1개인 날 → 탭 시 바로 대화 상세로 이동
 * - 세션이 2개 이상인 날 → 탭 시 바텀시트로 목록을 보여주고 선택 시 이동
 * - 아무 기록도 없는 날 → 탭 비활성
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onDiaryClick: (String) -> Unit,
    onConversationClick: (String) -> Unit,
    viewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = LocalEchoColors.current

    var sheetSessions by remember { mutableStateOf<List<ConversationSummary>?>(null) }
    val sheetState = rememberModalBottomSheetState()

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
        } else {
            MonthNavigationHeader(
                currentMonth = uiState.currentMonth,
                onPreviousMonth = { viewModel.changeMonth(-1) },
                onNextMonth = { viewModel.changeMonth(1) }
            )
            CalendarLegend()
            CalendarGrid(
                currentMonth = uiState.currentMonth,
                cellsByDate = uiState.cellsByDate,
                onDateSelected = { state ->
                    when (state) {
                        is DayCellState.HasDiary -> onDiaryClick(state.diary.date)
                        is DayCellState.HasSessions -> {
                            if (state.sessions.size == 1) {
                                onConversationClick(state.sessions.first().conversationId)
                            } else {
                                sheetSessions = state.sessions
                            }
                        }
                        DayCellState.Empty -> Unit
                    }
                }
            )
        }
    }

    sheetSessions?.let { sessions ->
        ModalBottomSheet(
            onDismissRequest = { sheetSessions = null },
            sheetState = sheetState
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                items(sessions) { summary ->
                    SessionCard(
                        summary = summary,
                        showDate = false,
                        onClick = {
                            sheetSessions = null
                            onConversationClick(summary.conversationId)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthNavigationHeader(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPreviousMonth, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "이전 달",
                tint = colors.textPrimary
            )
        }
        Text(
            text = "${currentMonth.year}년 ${currentMonth.monthValue}월",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = OutfitFontFamily,
            color = colors.textPrimary
        )
        IconButton(onClick = onNextMonth, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "다음 달",
                tint = colors.textPrimary
            )
        }
    }
}

@Composable
private fun CalendarLegend() {
    val colors = LocalEchoColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LegendItem(color = colors.accentGreen, label = "일기", colors = colors)
        LegendItem(color = colors.accentCoral, label = "대화만", colors = colors)
        LegendItem(color = colors.accentBlue, label = "갱신 실패", colors = colors)
        LegendItem(color = colors.accentRed, label = "생성 실패", colors = colors)
    }
}

@Composable
private fun LegendItem(color: Color, label: String, colors: EchoColorScheme) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = OutfitFontFamily,
            color = colors.textTertiary
        )
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: YearMonth,
    cellsByDate: Map<LocalDate, DayCellState>,
    onDateSelected: (DayCellState) -> Unit
) {
    val colors = LocalEchoColors.current
    val today = remember { LocalDate.now() }
    val weeks = remember(currentMonth) { buildCalendarWeeks(currentMonth) }

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontFamily = OutfitFontFamily,
                        color = colors.textTertiary
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (date != null) {
                            CalendarDayCell(
                                date = date,
                                isToday = date == today,
                                state = cellsByDate[date] ?: DayCellState.Empty,
                                onClick = onDateSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isToday: Boolean,
    state: DayCellState,
    onClick: (DayCellState) -> Unit
) {
    val colors = LocalEchoColors.current
    val isEnabled = state != DayCellState.Empty

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isToday) Modifier.background(colors.bgMuted) else Modifier
            )
            .clickable(enabled = isEnabled) { onClick(state) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 16.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                fontFamily = OutfitFontFamily,
                color = if (isEnabled) colors.textPrimary else colors.textTertiary
            )
            Spacer(Modifier.height(2.dp))
            val dotColor = dayCellDotColor(state, colors)
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

private fun dayCellDotColor(state: DayCellState, colors: EchoColorScheme): Color? = when (state) {
    is DayCellState.HasDiary -> {
        val hasStaleContent = state.diary.status == "FAILED" && state.diary.content != null
        val isTotalFailure = state.diary.status == "FAILED" && state.diary.content == null
        when {
            isTotalFailure -> colors.accentRed
            hasStaleContent -> colors.accentBlue
            else -> colors.accentGreen
        }
    }
    is DayCellState.HasSessions -> colors.accentCoral
    DayCellState.Empty -> null
}

/**
 * 지정한 달을 일요일 시작 7일 단위 주(week) 목록으로 변환
 * (이전/다음 달 여백은 null로 채워 클릭 비활성 처리)
 */
private fun buildCalendarWeeks(month: YearMonth): List<List<LocalDate?>> {
    val firstDay = month.atDay(1)
    val leadingEmpty = firstDay.dayOfWeek.value % 7 // MONDAY=1..SUNDAY=7 -> 일요일 시작 인덱스로 변환
    val totalDays = month.lengthOfMonth()

    val cells = mutableListOf<LocalDate?>()
    repeat(leadingEmpty) { cells.add(null) }
    for (day in 1..totalDays) cells.add(month.atDay(day))
    while (cells.size % 7 != 0) cells.add(null)

    return cells.chunked(7)
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
 * 대화 세션 카드 (캘린더에서 세션 2개 이상인 날의 바텀시트, 기존 대화기록 탭 카드 스타일 유지)
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
