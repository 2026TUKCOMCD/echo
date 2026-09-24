package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.StreamMeta
import com.example.graduation_project.data.model.StreamText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * `/api/conversations/message-stream`, `/start-stream` 응답 본문(프레임 스트림) 파서.
 * 서버 `ConversationStreamWriter`와 같은 규격이다 (docs/API.md 2-1절).
 *
 * ```
 * 프레임 = [1바이트 type][4바이트 big-endian payload 길이][payload]
 *   META(0x01):  UTF-8 JSON {"userMessage"}  - 맨 앞에 정확히 1개
 *   TEXT(0x03):  UTF-8 JSON {"text"}         - AI 응답 조각 텍스트. 그 조각의 AUDIO들 바로 앞에 온다
 *   AUDIO(0x02): mp3 바이트 조각              - 0개 이상
 *   END(0x00):   길이 0                       - 정상 종료 표시
 *
 * 예) META → TEXT(첫 조각) → AUDIO... → TEXT(나머지) → AUDIO... → END
 * ```
 * 서버는 AI 응답을 생성하는 대로 조각 단위로 TTS하므로, 첫 소리가 나갈 때는 전체 텍스트를 모른다.
 * END 없이 스트림이 끝나면 잘린 것이다.
 */
object ConversationStreamProtocol {

    const val TYPE_END = 0x00
    const val TYPE_META = 0x01
    const val TYPE_AUDIO = 0x02
    const val TYPE_TEXT = 0x03

    private const val HEADER_BYTES = 5

    // 비정상 길이 값으로 거대한 배열을 할당하지 않도록 하는 방어선 (실제 META/TEXT는 수백 바이트)
    private const val MAX_JSON_BYTES = 64 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    /** 스트림 첫머리 - 사용자 발화(STT 결과)와 AI 응답 첫 조각 */
    data class Start(val userMessage: String?, val firstText: String)

    /**
     * 스트림 맨 앞의 META와 첫 TEXT 프레임을 읽는다. 호출 후 [input]은 첫 조각의 AUDIO 프레임 직전에 위치한다.
     * 서버는 첫 조각의 TTS가 준비된 뒤에야 응답을 시작하므로 두 프레임은 곧바로 이어서 온다.
     * @throws IOException 프레임 순서가 다르거나, 잘렸거나, JSON이 깨진 경우
     */
    fun readStart(input: InputStream): Start {
        val meta = readJsonFrame(input, TYPE_META, "META", StreamMeta.serializer())
        val first = readJsonFrame(input, TYPE_TEXT, "TEXT", StreamText.serializer())
        return Start(meta.userMessage, first.text)
    }

    private fun <T> readJsonFrame(input: InputStream, type: Int, name: String, serializer: KSerializer<T>): T {
        val header = readHeader(input) ?: throw IOException("스트림이 $name 전에 끝났습니다")
        if (header.type != type) {
            throw IOException("$name 프레임이 와야 할 자리에 type=${header.type} 프레임이 왔습니다")
        }
        return decodeJson(readJsonPayload(input, header, name), name, serializer)
    }

    /** TEXT 프레임 payload를 읽어 텍스트를 꺼낸다 (헤더는 이미 읽은 상태) */
    internal fun readText(input: InputStream, header: FrameHeader): String =
        decodeJson(readJsonPayload(input, header, "TEXT"), "TEXT", StreamText.serializer()).text

    private fun readJsonPayload(input: InputStream, header: FrameHeader, name: String): ByteArray {
        if (header.length !in 0..MAX_JSON_BYTES) {
            throw IOException("$name 길이가 비정상입니다 (${header.length})")
        }
        return readFully(input, header.length)
    }

    private fun <T> decodeJson(payload: ByteArray, name: String, serializer: KSerializer<T>): T {
        return try {
            json.decodeFromString(serializer, payload.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw IOException("$name JSON을 해석하지 못했습니다", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("$name JSON을 해석하지 못했습니다", e)
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
 * 첫 조각 이후의 AUDIO 프레임들을 이어 붙인 mp3 바이트 스트림.
 * - 중간에 TEXT 프레임(다음 조각 텍스트)을 만나면 [onText]로 넘기고 오디오 읽기를 계속한다.
 * - END 프레임을 만나면 -1(정상 종료)을 반환한다.
 * - END 없이 원본 스트림이 끝나면 [IOException]을 던진다 → 재생 측이 "잘림"으로 처리해 tts-retry로 폴백.
 * close()하면 원본(HTTP 응답 본문)도 닫힌다.
 *
 * 읽기는 재생기의 버퍼 채우기 스레드에서 일어나므로 [onText]도 그 스레드에서 호출된다.
 * 재생기가 오디오를 미리 읽어 두기 때문에 텍스트가 해당 소리보다 조금 먼저 도착할 수 있다.
 */
class AudioFrameInputStream(private val source: InputStream) : InputStream() {

    private var remainingInFrame = 0
    private var ended = false
    private val pendingTexts = mutableListOf<String>()
    private var textListener: ((String) -> Unit)? = null

    /**
     * 다음 조각 텍스트를 받을 리스너. 설정 전에 도착한 텍스트는 설정하는 즉시 순서대로 전달한다.
     */
    var onText: ((String) -> Unit)?
        get() = synchronized(pendingTexts) { textListener }
        set(listener) {
            val backlog = synchronized(pendingTexts) {
                textListener = listener
                if (listener == null) emptyList() else pendingTexts.toList().also { pendingTexts.clear() }
            }
            backlog.forEach { listener?.invoke(it) }
        }

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
                ConversationStreamProtocol.TYPE_TEXT -> deliverText(ConversationStreamProtocol.readText(source, header))
                else -> throw IOException("알 수 없는 프레임입니다 (type=${header.type})")
            }
        }

        val n = source.read(b, off, minOf(len, remainingInFrame))
        if (n == -1) throw IOException("음성 스트림이 프레임 도중 끝났습니다 (잘림)")
        remainingInFrame -= n
        return n
    }

    private fun deliverText(text: String) {
        val listener = synchronized(pendingTexts) {
            textListener ?: run {
                pendingTexts.add(text)
                null
            }
        }
        listener?.invoke(text)
    }

    override fun close() {
        source.close()
    }
}
