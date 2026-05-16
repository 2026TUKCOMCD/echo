package com.example.graduation_project.presentation.history

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.graduation_project.data.local.entity.MessageEntity
import com.example.graduation_project.presentation.conversation.components.MessageItem
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.ui.theme.LocalEchoColors
import com.example.graduation_project.ui.theme.OutfitFontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ViewModel

data class HistoryDetailUiState(
    val messages: List<MessageUiModel> = emptyList(),
    val date: String = "",
    val timeRange: String = "",
    val durationMin: Int = 0,
    val isLoading: Boolean = true
)

class ConversationHistoryDetailViewModel(
    application: Application,
    private val conversationId: String
) : AndroidViewModel(application) {

    private val messageDao = AppDatabase.getInstance(application).messageDao()

    private val _uiState = MutableStateFlow(HistoryDetailUiState())
    val uiState: StateFlow<HistoryDetailUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            messageDao.getMessagesByConversationId(conversationId).collect { entities ->
                val uiModels = entities.map {
                    MessageUiModel(
                        id = it.id,
                        text = it.content,
                        isFromUser = it.role == MessageEntity.ROLE_USER,
                        timestamp = it.timestamp
                    )
                }

                val first = entities.firstOrNull()
                val last = entities.lastOrNull()
                val durationMin = if (first != null && last != null) {
                    maxOf(1, TimeUnit.MILLISECONDS.toMinutes(last.timestamp - first.timestamp).toInt())
                } else 0

                _uiState.value = HistoryDetailUiState(
                    messages = uiModels,
                    date = first?.let { formatDate(it.timestamp) } ?: "",
                    timeRange = if (first != null && last != null)
                        "${formatTime(first.timestamp)} ~ ${formatTime(last.timestamp)}" else "",
                    durationMin = durationMin,
                    isLoading = false
                )
            }
        }
    }

    private fun formatDate(ts: Long) =
        SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN).format(Date(ts))

    private fun formatTime(ts: Long) =
        SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date(ts))

    class Factory(private val app: Application, private val conversationId: String) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConversationHistoryDetailViewModel(app, conversationId) as T
    }
}

// Screen

@Composable
fun ConversationHistoryDetailScreen(
    conversationId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: ConversationHistoryDetailViewModel = viewModel(
        factory = ConversationHistoryDetailViewModel.Factory(
            context.applicationContext as Application,
            conversationId
        )
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
            Column(modifier = Modifier.padding(start = 8.dp)) {
                if (uiState.date.isNotBlank()) {
                    Text(
                        text = uiState.date,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFontFamily,
                        color = colors.textPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = uiState.timeRange,
                            fontSize = 13.sp,
                            fontFamily = OutfitFontFamily,
                            color = colors.textTertiary
                        )
                        if (uiState.durationMin > 0) {
                            Text("·", fontSize = 13.sp, color = colors.textTertiary)
                            Text(
                                text = "${uiState.durationMin}분",
                                fontSize = 13.sp,
                                fontFamily = OutfitFontFamily,
                                color = colors.textTertiary
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accentGreen)
            }
        } else if (uiState.messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "메시지가 없습니다",
                    fontSize = 18.sp,
                    color = colors.textTertiary,
                    fontFamily = OutfitFontFamily
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.messages) { message ->
                    MessageItem(message = message)
                }
            }
        }
    }
}
