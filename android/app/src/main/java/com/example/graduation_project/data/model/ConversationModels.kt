package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

// /api/conversations/start 응답 Model(DTO)
@Serializable
data class ConversationStartResponse(
    val message: String? = null,
    val audioData: String? = null,
    val timestamp: String? = null
)

// /api/conversations/message 응답 Model(DTO)
@Serializable
data class ConversationMessageResponse(
    val userMessage: String? = null,
    val aiResponse: String? = null,
    val audioData: String? = null,
    val timestamp: String? = null
)

// /message-stream, /start-stream 응답의 META 프레임(JSON) - 맨 앞에 1번. 첫 인사면 userMessage는 null
@Serializable
data class StreamMeta(
    val userMessage: String? = null
)

// 스트리밍 응답의 TEXT 프레임(JSON) - AI 응답 조각 텍스트. 그 조각의 오디오 바로 앞에 온다
@Serializable
data class StreamText(
    val text: String = ""
)

// /api/conversations/end 응답 Model(DTO)
@Serializable
data class ConversationEndResponse(
    val endedAt: String? = null,
    val diaryStatus: String? = null,   // SUCCESS | FAILED | SKIPPED (구서버는 null)
    val diaryId: Long? = null,
    val diaryError: String? = null,
    val diaryDate: String? = null      // "yyyy-MM-dd" (Asia/Seoul), SKIPPED/구서버는 null
)

// /api/conversations/tts-retry 응답 Model(DTO)
// aiResponse: 다시 합성한 AI 응답 전체 - 스트리밍이 끊겼을 때 말풍선을 전체 문장으로 채우는 용도
@Serializable
data class TtsRetryResponse(
    val aiResponse: String? = null,
    val audioData: String? = null
)

// /start 요청 body DTO — HealthData + 위치 데이터 묶음
@Serializable
data class ConversationStartRequest(
    val healthData: HealthData,
    val locationData: RawLocationData? = null
)

// 요청 Model(DTO) -> /start에 대한 DTO
@Serializable
data class HealthData(
    val sleepDurationMinutes: Int? = null,   // renamed: sleepDuration → sleepDurationMinutes
    val sleepStartTime: String? = null,      // "HH:mm" 형식, 서버 낮잠/야간 수면 분류용
    val wakeUpTime: String? = null,          // "HH:mm" 형식, 기상 시각
    val steps: Int? = null,
    val exerciseDistanceKm: Double? = null,  // renamed: exerciseDistance → exerciseDistanceKm
    val exerciseActivity: String? = null,
    val activityList: String? = null         // 오늘 운동 활동 목록 (쉼표 구분)
)
