package com.example.graduation_project.presentation.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.api.ApiClient
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
import java.time.Clock
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
    val syncError: String?,
    val today: LocalDate
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
    val syncError: String? = null,
    val today: LocalDate = LocalDate.now(KST_ZONE)   // 캘린더 "오늘" 표시 (기기 시간대와 무관하게 KST)
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
    private val currentUserId: Long = ApiClient.tokenStorage?.getCurrentUserId() ?: -1L,
    private val clock: Clock = Clock.systemUTC()
) : AndroidViewModel(application) {

    private val currentMonth = MutableStateFlow(YearMonth.from(todayInKst()))
    private val today = MutableStateFlow(todayInKst())
    private val syncError = MutableStateFlow<String?>(null)

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
     * "오늘"을 다시 계산 - 화면이 다시 보일 때(ON_RESUME) 호출
     * (앱을 백그라운드에 뒀다가 다음 날 열어도 어제가 오늘로 남지 않도록)
     *
     * 날짜가 바뀌며 달도 넘어갔다면, 어제가 속한 달을 보고 있던 경우에만 오늘의 달로 따라감
     * (사용자가 직접 다른 달로 옮겨 둔 경우는 그대로 둠)
     */
    fun refreshToday() {
        val previous = today.value
        val now = todayInKst()
        if (now == previous) return
        today.value = now

        val nowMonth = YearMonth.from(now)
        if (currentMonth.value == YearMonth.from(previous) && currentMonth.value != nowMonth) {
            currentMonth.value = nowMonth
            refreshCurrentMonth()
        }
    }

    // 서버·날짜 버킷과 같은 기준(Asia/Seoul)으로 계산 - 기기 시간대는 쓰지 않음
    private fun todayInKst(): LocalDate = clock.instant().atZone(KST_ZONE).toLocalDate()

    /**
     * 이전/다음 달로 이동 (delta: -1 = 이전 달, +1 = 다음 달)
     */
    fun changeMonth(delta: Int) {
        currentMonth.value = currentMonth.value.plusMonths(delta.toLong())
        refreshCurrentMonth()
    }

    private fun refreshCurrentMonth() {
        val month = currentMonth.value
        viewModelScope.launch {
            when (val result = diaryRepository.refreshMonth(month)) {
                is ApiResult.Success -> syncError.value = null
                is ApiResult.Error -> syncError.value = result.exception.message
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
                        syncError,
                        today
                    ) { diaries, ranges, links, error, today ->
                        CalendarSources(
                            month = month,
                            diaries = diaries,
                            ranges = ranges,
                            diaryDateByConversationId = links.associate { it.conversationId to it.diaryDate },
                            syncError = error,
                            today = today
                        )
                    }
                }
                .collect { sources ->
                    _uiState.value = DiaryCalendarUiState(
                        currentMonth = sources.month,
                        cellsByDate = buildDayCellStates(sources),
                        isLoading = false,
                        syncError = sources.syncError,
                        today = sources.today
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
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return DiaryViewModel(application) as T
            }
        }
    }
}
