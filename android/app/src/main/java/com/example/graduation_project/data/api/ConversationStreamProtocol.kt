package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.StreamMeta
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * `/api/conversations/message-stream` 응답 본문(프레임 스트림) 파서.
 * 서버 `ConversationStreamWriter`와 같은 규격이다 (docs/API.md 2-1절).
 *
 * ```
 * 프레임 = [1바이트 type][4바이트 big-endian payload 길이][payload]
 *   META(0x01):  UTF-8 JSON {"userMessage", "aiResponse"}  - 맨 앞에 정확히 1개
 *   AUDIO(0x02): mp3 바이트 조각                            - 0개 이상
 *   END(0x00):   길이 0                                     - 정상 종료 표시
 * ```
 * END 없이 스트림이 끝나면 잘린 것이다.
 */
object ConversationStreamProtocol {

    const val TYPE_END = 0x00
    const val TYPE_META = 0x01
    const val TYPE_AUDIO = 0x02

    private const val HEADER_BYTES = 5

    // 비정상 길이 값으로 거대한 배열을 할당하지 않도록 하는 방어선 (실제 META는 수백 바이트)
    private const val MAX_META_BYTES = 64 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 스트림 맨 앞의 META 프레임을 읽는다. 호출 후 [input]은 첫 AUDIO 프레임 직전에 위치한다.
     * @throws IOException 첫 프레임이 META가 아니거나, 잘렸거나, JSON이 깨진 경우
     */
    fun readMeta(input: InputStream): StreamMeta {
        val header = readHeader(input) ?: throw IOException("스트림이 비어 있습니다 (META 없음)")
        if (header.type != TYPE_META) {
            throw IOException("첫 프레임이 META가 아닙니다 (type=${header.type})")
        }
        if (header.length !in 0..MAX_META_BYTES) {
            throw IOException("META 길이가 비정상입니다 (${header.length})")
        }
        val payload = readFully(input, header.length)
        return try {
            json.decodeFromString(StreamMeta.serializer(), payload.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw IOException("META JSON을 해석하지 못했습니다", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("META JSON을 해석하지 못했습니다", e)
        }
    }

    internal data class FrameHeader(val type: Int, val length: Int)

    /** 프레임 헤더를 읽는다. 헤더 시작 전에 스트림이 끝났으면 null. */
    internal fun readHeader(input: InputStream): FrameHeader? {
        val first = input.read()
        if (first == -1) return null
        val rest = readFully(input, HEADER_BYTES - 1)
        val length = ((rest[0].toInt() and 0xFF) shl 24) or
            ((rest[1].toInt() and 0xFF) shl 16) or
            ((rest[2].toInt() and 0xFF) shl 8) or
            (rest[3].toInt() and 0xFF)
        return FrameHeader(first, length)
    }

    private fun readFully(input: InputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n == -1) throw EOFException("프레임 도중 스트림이 끝났습니다")
            read += n
        }
        return buffer
    }
}

/**
 * META 이후의 AUDIO 프레임들을 이어 붙인 mp3 바이트 스트림.
 * - END 프레임을 만나면 -1(정상 종료)을 반환한다.
 * - END 없이 원본 스트림이 끝나면 [IOException]을 던진다 → 재생 측이 "잘림"으로 처리해 tts-retry로 폴백.
 * close()하면 원본(HTTP 응답 본문)도 닫힌다.
 */
class AudioFrameInputStream(private val source: InputStream) : InputStream() {

    private var remainingInFrame = 0
    private var ended = false

    override fun read(): Int {
        val single = ByteArray(1)
        val n = read(single, 0, 1)
        return if (n == -1) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (ended) return -1

        while (remainingInFrame == 0) {
            val header = ConversationStreamProtocol.readHeader(source)
                ?: throw IOException("음성 스트림이 END 없이 끝났습니다 (잘림)")
            when (header.type) {
                ConversationStreamProtocol.TYPE_END -> {
                    ended = true
                    return -1
                }
                ConversationStreamProtocol.TYPE_AUDIO -> {
                    if (header.length < 0) throw IOException("AUDIO 길이가 비정상입니다 (${header.length})")
                    remainingInFrame = header.length
                }
                else -> throw IOException("알 수 없는 프레임입니다 (type=${header.type})")
            }
        }

        val n = source.read(b, off, minOf(len, remainingInFrame))
        if (n == -1) throw IOException("음성 스트림이 프레임 도중 끝났습니다 (잘림)")
        remainingInFrame -= n
        return n
    }

    override fun close() {
        source.close()
    }
}
