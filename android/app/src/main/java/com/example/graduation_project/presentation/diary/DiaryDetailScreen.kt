package com.example.graduation_project.presentation.diary

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.graduation_project.data.local.AppDatabase
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.presentation.model.ConversationSummary
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.OutfitFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// ViewModel

data class DiaryDetailUiState(
    val diary: DiaryEntity? = null,
    val sessions: List<ConversationSummary> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * 일기 상세 ViewModel
 *
 * 캐시된 일기(diaries)와 그날의 로컬 대화 세션(messages)을 함께 로드
 * 모두 로컬 조회이므로 오프라인에서도 동작
 */
class DiaryDetailViewModel(
    application: Application,
    private val date: String   // "yyyy-MM-dd"
) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val messageDao = database.messageDao()
    private val diaryDao = database.diaryDao()
    private val conversationDiaryLinkDao = database.conversationDiaryLinkDao()

    private val _uiState = MutableStateFlow(DiaryDetailUiState())
    val uiState: StateFlow<DiaryDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            combine(
                messageDao.getSessionRanges(),
                conversationDiaryLinkDao.observeAll()
            ) { ranges, links -> ranges to links.associate { it.conversationId to it.diaryDate } }
                .collect { (ranges, linkedDates) ->
                    // DiaryViewModel의 버킷 기준과 동일하게: 서버 diaryDate가 있으면 우선 신뢰,
                    // 없으면 lastTimestamp 휴리스틱으로 폴백
                    val sessions = ranges
                        .filter { resolveDateKey(it, linkedDates[it.conversationId]) == date }
                        .mapNotNull { buildSessionSummary(messageDao, it, date) }
                    _uiState.value = DiaryDetailUiState(
                        diary = diaryDao.getByDate(date),
                        sessions = sessions,
                        isLoading = false
                    )
                }
        }
    }

    class Factory(private val app: Application, private val date: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DiaryDetailViewModel(app, date) as T
    }
}

// Screen

/**
 * 일기 상세 화면
 *
 * 상단: 일기 본문 (실패 시 실패 사유 카드)
 * 하단: "그날의 대화" 세션 카드 목록 → 탭 시 기존 말풍선 화면으로 이동
 */
@Composable
fun DiaryDetailScreen(
    date: String,
    onSessionClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: DiaryDetailViewModel = viewModel(
        factory = DiaryDetailViewModel.Factory(context.applicationContext as Application, date)
    )
    val uiState by vm.uiState.collectAsState()

    val colors = LocalEchoColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPage)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로 가기",
                    tint = colors.textPrimary
                )
            }
            Text(
                text = formatKoreanDate(date),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = OutfitFontFamily,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentGreen)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
            ) {
                item {
                    DiaryContentCard(diary = uiState.diary)
                }

                if (uiState.sessions.isNotEmpty()) {
                    item {
                        Text(
                            text = "그날의 대화",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(uiState.sessions) { session ->
                        SessionCard(
                            summary = session,
                            onClick = { onSessionClick(session.conversationId) },
                            showDate = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryContentCard(diary: DiaryEntity?) {
    val colors = LocalEchoColors.current
    val isFailed = diary?.status == "FAILED"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(12.dp)),
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
                    text = diary?.title ?: "오늘의 일기",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFontFamily,
                    color = colors.textPrimary
                )
                if (isFailed) {
                    FailureBadge()
                }
            }
            if (diary?.weather != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "날씨: ${diary.weather}",
                    fontSize = 16.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textTertiary
                )
            }
            Spacer(Modifier.height(12.dp))
            when {
                diary?.content != null -> Text(
                    text = diary.content,
                    fontSize = 18.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textSecondary,
                    lineHeight = 28.sp
                )
                else -> Text(
                    text = "이날의 일기가 없어요",
                    fontSize = 18.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.textTertiary
                )
            }
            // 디버깅 단계: 실패 사유를 상세 화면에 그대로 노출
            if (isFailed && diary?.failureReason != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "실패 사유: ${diary.failureReason}",
                    fontSize = 14.sp,
                    fontFamily = OutfitFontFamily,
                    color = colors.accentRed,
                    lineHeight = 20.sp
                )
            }
        }
    }
}
