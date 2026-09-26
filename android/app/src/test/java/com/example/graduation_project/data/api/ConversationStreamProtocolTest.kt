package com.example.graduation_project.data.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 서버 ConversationStreamWriter와 같은 프레임 규격을 앱이 올바르게 해석하는지 검증
 * ([type 1B][길이 4B big-endian][payload], META → (TEXT → AUDIO...)* → END)
 */
class ConversationStreamProtocolTest {

    companion object {
        fun frame(type: Int, payload: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(type)
            out.write(payload.size ushr 24)
            out.write(payload.size ushr 16)
            out.write(payload.size ushr 8)
            out.write(payload.size)
            out.write(payload)
            return out.toByteArray()
        }

        fun meta(json: String) = frame(ConversationStreamProtocol.TYPE_META, json.toByteArray(Charsets.UTF_8))
        fun text(value: String) =
            frame(ConversationStreamProtocol.TYPE_TEXT, """{"text":"$value"}""".toByteArray(Charsets.UTF_8))
        fun audio(vararg bytes: Byte) = frame(ConversationStreamProtocol.TYPE_AUDIO, bytes)
        fun end() = frame(ConversationStreamProtocol.TYPE_END, ByteArray(0))
    }

    @Test
    fun `META와 첫 TEXT를 읽고, 이어지는 AUDIO 프레임들을 이어 붙여 END에서 정상 종료한다`() {
        val body = meta("""{"userMessage":"오늘 산책했어요"}""") + text("산책하셨군요!") +
            audio(1, 2, 3) + audio(4, 5) + end()
        val input = ByteArrayInputStream(body)

        val start = ConversationStreamProtocol.readStart(input)
        val audioBytes = AudioFrameInputStream(input).readBytes()

        assertEquals("오늘 산책했어요", start.userMessage)
        assertEquals("산책하셨군요!", start.firstText)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), audioBytes)
    }

    @Test
    fun `오디오 중간의 TEXT 프레임은 onText로 넘기고 오디오는 끊김 없이 이어진다`() {
        val body = meta("{}") + text("첫 조각이에요.") + audio(1, 2) + text("나머지예요.") + audio(3) + end()
        val input = ByteArrayInputStream(body)
        ConversationStreamProtocol.readStart(input)
        val received = mutableListOf<String>()
        val audioStream = AudioFrameInputStream(input).apply { onText = { received += it } }

        val audioBytes = audioStream.readBytes()

        assertArrayEquals(byteArrayOf(1, 2, 3), audioBytes)
        assertEquals(listOf("나머지예요."), received)
    }

    @Test
    fun `리스너를 붙이기 전에 도착한 TEXT는 붙이는 즉시 순서대로 전달한다`() {
        val body = meta("{}") + text("첫") + text("둘") + text("셋") + audio(1) + end()
        val input = ByteArrayInputStream(body)
        ConversationStreamProtocol.readStart(input)
        val audioStream = AudioFrameInputStream(input)
        audioStream.readBytes()

        val received = mutableListOf<String>()
        audioStream.onText = { received += it }

        assertEquals(listOf("둘", "셋"), received)
    }

    @Test
    fun `첫 인사처럼 userMessage가 없어도 해석한다`() {
        val start = ConversationStreamProtocol.readStart(
            ByteArrayInputStream(meta("""{"userMessage":null}""") + text("안녕하세요!") + end())
        )

        assertNull(start.userMessage)
        assertEquals("안녕하세요!", start.firstText)
    }

    @Test
    fun `길이 0인 AUDIO 프레임은 건너뛴다`() {
        val input = ByteArrayInputStream(meta("{}") + text("네") + audio() + audio(9) + end())
        ConversationStreamProtocol.readStart(input)

        assertArrayEquals(byteArrayOf(9), AudioFrameInputStream(input).readBytes())
    }

    @Test
    fun `모르는 필드가 있어도 META와 TEXT를 해석한다 (서버 확장 대비)`() {
        val input = ByteArrayInputStream(
            meta("""{"userMessage":"네","extra":1}""") +
                frame(ConversationStreamProtocol.TYPE_TEXT, """{"text":"좋아요","seq":0}""".toByteArray()) + end()
        )

        val start = ConversationStreamProtocol.readStart(input)

        assertEquals("네", start.userMessage)
        assertEquals("좋아요", start.firstText)
    }

    @Test
    fun `END 없이 스트림이 끝나면 잘림으로 보고 IOException을 던진다`() {
        val input = ByteArrayInputStream(meta("{}") + text("네") + audio(1, 2))
        ConversationStreamProtocol.readStart(input)
        val audioStream = AudioFrameInputStream(input)

        assertThrows(IOException::class.java) { audioStream.readBytes() }
    }

    @Test
    fun `AUDIO 프레임 도중에 끊기면 IOException을 던진다`() {
        val truncatedFrame = audio(1, 2, 3, 4).copyOf(5 + 2)  // 헤더 + payload 절반
        val input = ByteArrayInputStream(meta("{}") + text("네") + truncatedFrame)
        ConversationStreamProtocol.readStart(input)

        assertThrows(IOException::class.java) { AudioFrameInputStream(input).readBytes() }
    }

    @Test
    fun `첫 프레임이 META가 아니면 IOException을 던진다`() {
        val input = ByteArrayInputStream(audio(1) + end())

        assertThrows(IOException::class.java) { ConversationStreamProtocol.readStart(input) }
    }

    @Test
    fun `META 다음이 TEXT가 아니면 IOException을 던진다`() {
        val input = ByteArrayInputStream(meta("{}") + audio(1) + end())

        assertThrows(IOException::class.java) { ConversationStreamProtocol.readStart(input) }
    }

    @Test
    fun `빈 본문이면 IOException을 던진다`() {
        assertThrows(IOException::class.java) {
            ConversationStreamProtocol.readStart(ByteArrayInputStream(ByteArray(0)))
        }
    }

    @Test
    fun `META JSON이 깨졌으면 IOException을 던진다`() {
        assertThrows(IOException::class.java) {
            ConversationStreamProtocol.readStart(ByteArrayInputStream(meta("{not json") + text("네") + end()))
        }
    }

    @Test
    fun `END 이후에는 계속 -1을 반환한다`() {
        val input = ByteArrayInputStream(meta("{}") + text("네") + audio(7) + end())
        ConversationStreamProtocol.readStart(input)
        val audioStream = AudioFrameInputStream(input)

        assertEquals(7, audioStream.read())
        assertEquals(-1, audioStream.read())
        assertEquals(-1, audioStream.read())
    }
}
