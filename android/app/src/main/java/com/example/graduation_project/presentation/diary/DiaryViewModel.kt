package com.example.graduation_project.presentation.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.AppDatabase
import com.example.graduation_project.data.local.dao.SessionRange
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.data.repository.DiaryRepository
import com.example.graduation_project.presentation.model.ConversationSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 일기 탭 목록 항목
 *
 * - DiaryCard: 일기 레코드가 있는 날 (하루 1개, FAILED 실패 기록 포함)
 * - SessionCard: 일기가 없는 날의 로컬 대화 세션 (일기 기능 이전의 과거 기록 등)
 */
sealed class DiaryDayItem {
    data class DiaryCard(val diary: DiaryEntity, val sessionCount: Int) : DiaryDayItem()
    data class SessionCard(val summary: ConversationSummary) : DiaryDayItem()
}

data class DiaryListUiState(
    val items: List<DiaryDayItem> = emptyList(),
    val isLoading: Boolean = true,
    val syncError: String? = null
)

/**
 * 일기 탭 ViewModel
 *
 * 서버 일기 캐시(Room)와 로컬 대화 세션을 날짜 기준으로 병합해
 * 하나의 목록으로 제공. 진입 시 서버 동기화를 시도하고,
 * 실패해도 캐시를 그대로 보여주며 에러를 배너로 노출
 */
class DiaryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val messageDao = database.messageDao()
    private val diaryDao = database.diaryDao()
    private val diaryRepository = DiaryRepository(diaryDao)

    private val syncError = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(DiaryListUiState())
    val uiState: StateFlow<DiaryListUiState> = _uiState.asStateFlow()

    init {
        refresh()
        observeItems()
    }

    /**
     * 서버에서 일기 동기화 (탭 진입 시 / 수동 재시도)
     */
    fun refresh() {
        viewModelScope.launch {
            when (val result = diaryRepository.refresh()) {
                is ApiResult.Success -> syncError.value = null
                is ApiResult.Error -> syncError.value = result.exception.message
            }
        }
    }

    private fun observeItems() {
        viewModelScope.launch {
            combine(
                diaryDao.observeAll(),
                messageDao.getSessionRanges(),
                syncError
            ) { diaries, ranges, error ->
                Triple(diaries, ranges, error)
            }.collect { (diaries, ranges, error) ->
                _uiState.value = DiaryListUiState(
                    items = buildItems(diaries, ranges),
                    isLoading = false,
                    syncError = error
                )
            }
        }
    }

    /**
     * 병합 규칙: 날짜 내림차순으로,
     * 일기가 있는 날 = 일기 카드 1개 / 없는 날 = 그날의 세션 카드 나열
     */
    private suspend fun buildItems(
        diaries: List<DiaryEntity>,
        ranges: List<SessionRange>
    ): List<DiaryDayItem> {
        val diariesByDate = diaries.associateBy { it.date }
        val sessionsByDate = ranges.groupBy { sessionDateKey(it.firstTimestamp) }
        val allDates = (diariesByDate.keys + sessionsByDate.keys).sortedDescending()

        val items = mutableListOf<DiaryDayItem>()
        for (date in allDates) {
            val diary = diariesByDate[date]
            if (diary != null) {
                items += DiaryDayItem.DiaryCard(
                    diary = diary,
                    sessionCount = sessionsByDate[date]?.size ?: 0
                )
            } else {
                sessionsByDate[date]?.forEach { range ->
                    buildSessionSummary(messageDao, range)?.let {
                        items += DiaryDayItem.SessionCard(it)
                    }
                }
            }
        }
        return items
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
