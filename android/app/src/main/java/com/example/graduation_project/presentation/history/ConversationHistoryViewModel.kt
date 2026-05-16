package com.example.graduation_project.presentation.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.graduation_project.data.local.AppDatabase
import com.example.graduation_project.data.local.entity.MessageEntity
import com.example.graduation_project.presentation.model.ConversationSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class HistoryListUiState(
    val summaries: List<ConversationSummary> = emptyList(),
    val isLoading: Boolean = true
)

class ConversationHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val messageDao = AppDatabase.getInstance(application).messageDao()

    private val _uiState = MutableStateFlow(HistoryListUiState())
    val uiState: StateFlow<HistoryListUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            messageDao.getAllConversationIds().collect { ids ->
                if (ids.isEmpty()) {
                    _uiState.value = HistoryListUiState(
                        summaries = mockSummaries(),
                        isLoading = false
                    )
                    return@collect
                }

                val summaries = ids.mapNotNull { id ->
                    buildSummary(id)
                }
                _uiState.value = HistoryListUiState(summaries = summaries, isLoading = false)
            }
        }
    }

    private suspend fun buildSummary(conversationId: String): ConversationSummary? {
        val messages = messageDao.getMessagesByConversationIdOnce(conversationId)
        if (messages.isEmpty()) return null

        val first = messages.first()
        val last = messages.last()
        val durationMs = last.timestamp - first.timestamp
        val durationMin = maxOf(1, TimeUnit.MILLISECONDS.toMinutes(durationMs).toInt())

        val preview = messages.firstOrNull { it.role == MessageEntity.ROLE_ASSISTANT }
            ?.content?.take(60) ?: messages.first().content.take(60)

        return ConversationSummary(
            conversationId = conversationId,
            date = formatDate(first.timestamp),
            timeRange = "${formatTime(first.timestamp)} ~ ${formatTime(last.timestamp)}",
            durationMin = durationMin,
            previewText = preview
        )
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN)
        return sdf.format(Date(timestamp))
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("a h:mm", Locale.KOREAN)
        return sdf.format(Date(timestamp))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return ConversationHistoryViewModel(application) as T
            }
        }
    }

    private fun mockSummaries(): List<ConversationSummary> {
        val now = System.currentTimeMillis()
        return listOf(
            ConversationSummary(
                conversationId = "mock-1",
                date = "2025년 5월 10일 (토)",
                timeRange = "오전 10:30 ~ 10:45",
                durationMin = 15,
                previewText = "안녕하세요! 오늘 하루는 어떠셨나요? 날씨가 좋았으면 기분도 좋으셨겠어요."
            ),
            ConversationSummary(
                conversationId = "mock-2",
                date = "2025년 5월 9일 (금)",
                timeRange = "오후 3:00 ~ 3:20",
                durationMin = 20,
                previewText = "산책을 다녀오셨군요! 어디로 산책을 가셨나요?"
            ),
            ConversationSummary(
                conversationId = "mock-3",
                date = "2025년 5월 8일 (목)",
                timeRange = "오전 9:15 ~ 9:30",
                durationMin = 15,
                previewText = "오늘 아침은 잘 주무셨나요? 수면이 중요하죠."
            )
        )
    }
}
