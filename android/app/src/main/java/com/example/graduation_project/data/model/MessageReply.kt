package com.example.graduation_project.data.model

import java.io.InputStream

/**
 * 음성 메시지 전송 결과. 서버가 스트리밍을 지원하는지에 따라 오디오 전달 방식이 다르다.
 */
sealed class MessageReply {
    abstract val userMessage: String?
    abstract val aiResponse: String?

    /** 기존 `/message` 응답 (Base64 오디오) - 서버에 스트리밍 엔드포인트가 없을 때의 폴백 */
    data class Buffered(
        override val userMessage: String?,
        override val aiResponse: String?,
        val audioData: String?
    ) : MessageReply()

    /**
     * `/message-stream` 응답 - [audio]는 생성되는 대로 도착하는 mp3 바이트.
     * 받은 쪽이 소유권을 가지며 반드시 close() 해야 한다 (HTTP 연결 반환).
     */
    class Streaming(
        override val userMessage: String?,
        override val aiResponse: String?,
        val audio: InputStream
    ) : MessageReply()
}
