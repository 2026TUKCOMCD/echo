package com.example.graduation_project.presentation.diary

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.AppDatabase
import com.example.graduation_project.data.local.dao.ConversationDiaryLinkDao
import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.local.dao.SessionRange
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.data.repository.DiaryRepository
import com.example.graduation_project.presentation.model.ConversationSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val KST_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
private val DATE_KEY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private data class CalendarSources(
    val month: YearMonth,
    val diaries: List<DiaryEntity>,
    val ranges: List<SessionRange>,
    val diaryDateByConversationId: Map<String, String>,
    val syncError: String?
)

/**
 * 일기 탭 캘린더의 날짜 셀 상태
 *
 * - Empty: 일기도 세션도 없는 날 (탭 비활성)
 * - HasSessions: 일기는 없고 그날의 대화 세션만 있는 날 (1개면 즉시 이동, 2개 이상이면 바텀시트)
 * - HasDiary: 일기 레코드가 있는 날 (하루 1개, FAILED 실패/갱신 실패 기록 포함)
 */
sealed class DayCellState {
    data object Empty : DayCellState()
    data class HasSessions(val sessions: List<ConversationSummary>) : DayCellState()
    data class HasDiary(val diary: DiaryEntity, val sessionCount: Int) : DayCellState()
}

data class DiaryCalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(KST_ZONE),
    val cellsByDate: Map<LocalDate, DayCellState> = emptyMap(),
    val isLoading: Boolean = true,
    val syncError: String? = null
)

/**
 * 일기 탭 ViewModel (캘린더)
 *
 * 서버 일기 캐시(Room)와 로컬 대화 세션을 날짜 기준으로 병합해
 * 현재 보고 있는 달의 날짜별 상태 맵으로 제공.
 * 달 진입/이동 시 서버 동기화를 시도하고, 실패해도 캐시를 그대로 보여주며 에러를 배너로 노출
 */
class DiaryViewModel(
    application: Application,
    private val messageDao: MessageDao = AppDatabase.getInstance(application).messageDao(),
    private val conversationDiaryLinkDao: ConversationDiaryLinkDao =
        AppDatabase.getInstance(application).conversationDiaryLinkDao(),
    private val diaryRepository: DiaryRepository = DiaryRepository(AppDatabase.getInstance(application).diaryDao()),
    private val currentUserId: Long = ApiClient.tokenStorage?.getCurrentUserId() ?: -1L
) : AndroidViewModel(application) {

    private val currentMonth = MutableStateFlow(YearMonth.now(KST_ZONE))
    private val syncError = MutableStateFlow<String?>(null)

    // 동기화 요청 번호 - 가장 마지막 요청의 결과만 배너에 반영 (last request wins)
    private var latestRefreshId = 0

    private val _uiState = MutableStateFlow(DiaryCalendarUiState())
    val uiState: StateFlow<DiaryCalendarUiState> = _uiState.asStateFlow()

    init {
        refreshCurrentMonth()
        observeCalendar()
    }

    /**
     * 현재 보고 있는 달을 서버와 재동기화 (달 진입 시 / 수동 재시도)
     */
    fun refresh() = refreshCurrentMonth()

    /**
     * 이전/다음 달로 이동 (delta: -1 = 이전 달, +1 = 다음 달)
     */
    fun changeMonth(delta: Int) {
        currentMonth.value = currentMonth.value.plusMonths(delta.toLong())
        // 이전 달의 오류는 새 달과 무관하므로 새 달 결과를 기다리지 않고 바로 지움
        syncError.value = null
        refreshCurrentMonth()
    }

    /**
     * 요청은 취소하지 않고 끝까지 진행해 받은 일기를 캐시에 저장하되,
     * 그 사이 달 이동·재시도로 더 새 요청이 생겼다면 오래된 결과는 배너에 반영하지 않음
     * (늦게 끝난 이전 요청이 지금 화면의 오류를 띄우거나 지우는 것을 방지)
     */
    private fun refreshCurrentMonth() {
        val month = currentMonth.value
        val refreshId = ++latestRefreshId
        viewModelScope.launch {
            val result = diaryRepository.refreshMonth(month)
            if (refreshId != latestRefreshId) return@launch
            syncError.value = when (result) {
                is ApiResult.Success -> null
                is ApiResult.Error -> {
                    // 원인(HTTP 코드 등)은 로그로만 남기고 화면에는 어르신이 할 수 있는 행동만 안내
                    Log.w(TAG, "일기 동기화 실패 ($month)", result.exception)
                    syncErrorMessage(result.exception)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeCalendar() {
        viewModelScope.launch {
            currentMonth
                .flatMapLatest { month ->
                    combine(
                        diaryRepository.observeMonth(month),
                        messageDao.getSessionRanges(currentUserId),
                        conversationDiaryLinkDao.observeAll(),
                        syncError
                    ) { diaries, ranges, links, error ->
                        CalendarSources(
                            month = month,
                            diaries = diaries,
                            ranges = ranges,
                            diaryDateByConversationId = links.associate { it.conversationId to it.diaryDate },
                            syncError = error
                        )
                    }
                }
                .collect { sources ->
                    _uiState.value = DiaryCalendarUiState(
                        currentMonth = sources.month,
                        cellsByDate = buildDayCellStates(sources),
                        isLoading = false,
                        syncError = sources.syncError
                    )
                }
        }
    }

    /**
     * 병합 규칙: 일기가 있는 날 = HasDiary / 없고 세션만 있는 날 = HasSessions / 둘 다 없으면 Empty
     * (세션의 날짜 버킷은 서버가 확정해 준 diaryDate를 우선하고, 없으면 세션 종료 시각 기준으로 폴백)
     */
    private suspend fun buildDayCellStates(sources: CalendarSources): Map<LocalDate, DayCellState> {
        val diariesByDate = sources.diaries.associateBy { it.date }
        val sessionsByDate = sources.ranges.groupBy {
            resolveDateKey(it, sources.diaryDateByConversationId[it.conversationId])
        }

        val cells = mutableMapOf<LocalDate, DayCellState>()
        for (day in 1..sources.month.lengthOfMonth()) {
            val date = sources.month.atDay(day)
            val dateKey = date.format(DATE_KEY_FORMATTER)
            val diary = diariesByDate[dateKey]
            val sessionsForDate = sessionsByDate[dateKey].orEmpty()

            cells[date] = when {
                diary != null -> DayCellState.HasDiary(diary, sessionsForDate.size)
                sessionsForDate.isNotEmpty() -> {
                    val summaries = sessionsForDate.mapNotNull { range ->
                        buildSessionSummary(messageDao, range, dateKey, currentUserId)
                    }
                    if (summaries.isEmpty()) DayCellState.Empty else DayCellState.HasSessions(summaries)
                }
                else -> DayCellState.Empty
            }
        }
        return cells
    }

    companion object {
        private const val TAG = "DiaryViewModel"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return DiaryViewModel(application) as T
            }
        }
    }
}

/**
 * 동기화 실패 배너 문구 - 어르신이 할 수 있는 행동 기준으로 두 가지만 구분
 * (공통 ApiException 문구는 "~습니다" 말투·HTTP 코드가 섞여 있어 이 화면에서만 바꿔 표시)
 */
internal fun syncErrorMessage(exception: ApiException): String = when (exception) {
    is ApiException.NetworkError -> "인터넷 연결을 확인해 주세요."
    else -> "잠시 후 다시 시도해 주세요."
}
