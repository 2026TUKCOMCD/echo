package com.example.graduation_project.data.model

import com.example.graduation_project.data.api.AudioFrameInputStream

/**
 * 음성 메시지 전송 결과. 서버가 스트리밍을 지원하는지에 따라 오디오 전달 방식이 다르다.
 */
sealed class MessageReply {
    abstract val userMessage: String?

    /** 지금까지 알고 있는 AI 응답 텍스트 (스트리밍이면 첫 조각) */
    abstract val aiResponse: String?

    /** 기존 `/message` 응답 (Base64 오디오) - 서버에 스트리밍 엔드포인트가 없을 때의 폴백 */
    data class Buffered(
        override val userMessage: String?,
        override val aiResponse: String?,
        val audioData: String?
    ) : MessageReply()

    /**
     * `/message-stream` 응답 - AI 응답이 조각 단위로 도착한다.
     * [aiResponse]는 첫 조각이고, 이후 조각의 텍스트는 [audio]를 읽는 동안 [AudioFrameInputStream.onText]로 온다.
     * [audio]는 받은 쪽이 소유권을 가지며 반드시 close() 해야 한다 (HTTP 연결 반환).
     */
    class Streaming(
        override val userMessage: String?,
        override val aiResponse: String,
        val audio: AudioFrameInputStream
    ) : MessageReply()
}
