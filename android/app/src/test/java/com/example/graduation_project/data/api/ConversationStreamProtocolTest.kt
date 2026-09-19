package com.example.graduation_project.data.api

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 서버 ConversationStreamWriter와 같은 프레임 규격을 앱이 올바르게 해석하는지 검증
 * ([type 1B][길이 4B big-endian][payload], META → AUDIO... → END)
 */
class ConversationStreamProtocolTest {

    private fun frame(type: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(type)
        out.write(payload.size ushr 24)
        out.write(payload.size ushr 16)
        out.write(payload.size ushr 8)
        out.write(payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    private fun meta(json: String) = frame(ConversationStreamProtocol.TYPE_META, json.toByteArray(Charsets.UTF_8))
    private fun audio(vararg bytes: Byte) = frame(ConversationStreamProtocol.TYPE_AUDIO, bytes)
    private fun end() = frame(ConversationStreamProtocol.TYPE_END, ByteArray(0))

    @Test
    fun `META를 읽고 이어지는 AUDIO 프레임들을 이어 붙여 END에서 정상 종료한다`() {
        val body = meta("""{"userMessage":"오늘 산책했어요","aiResponse":"산책하셨군요!"}""") +
            audio(1, 2, 3) + audio(4, 5) + end()
        val input = ByteArrayInputStream(body)

        val parsed = ConversationStreamProtocol.readMeta(input)
        val audioBytes = AudioFrameInputStream(input).readBytes()

        assertEquals("오늘 산책했어요", parsed.userMessage)
        assertEquals("산책하셨군요!", parsed.aiResponse)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), audioBytes)
    }

    @Test
    fun `길이 0인 AUDIO 프레임은 건너뛴다`() {
        val body = meta("{}") + audio() + audio(9) + end()
        val input = ByteArrayInputStream(body)
        ConversationStreamProtocol.readMeta(input)

        assertArrayEquals(byteArrayOf(9), AudioFrameInputStream(input).readBytes())
    }

    @Test
    fun `모르는 필드가 있어도 META를 해석한다 (서버 확장 대비)`() {
        val input = ByteArrayInputStream(meta("""{"aiResponse":"네","extra":1}""") + end())

        assertEquals("네", ConversationStreamProtocol.readMeta(input).aiResponse)
    }

    @Test
    fun `END 없이 스트림이 끝나면 잘림으로 보고 IOException을 던진다`() {
        val input = ByteArrayInputStream(meta("{}") + audio(1, 2))
        ConversationStreamProtocol.readMeta(input)
        val audioStream = AudioFrameInputStream(input)

        assertThrows(IOException::class.java) { audioStream.readBytes() }
    }

    @Test
    fun `AUDIO 프레임 도중에 끊기면 IOException을 던진다`() {
        val truncatedFrame = audio(1, 2, 3, 4).copyOf(5 + 2)  // 헤더 + payload 절반
        val input = ByteArrayInputStream(meta("{}") + truncatedFrame)
        ConversationStreamProtocol.readMeta(input)

        assertThrows(IOException::class.java) { AudioFrameInputStream(input).readBytes() }
    }

    @Test
    fun `첫 프레임이 META가 아니면 IOException을 던진다`() {
        val input = ByteArrayInputStream(audio(1) + end())

        assertThrows(IOException::class.java) { ConversationStreamProtocol.readMeta(input) }
    }

    @Test
    fun `빈 본문이면 IOException을 던진다`() {
        assertThrows(IOException::class.java) {
            ConversationStreamProtocol.readMeta(ByteArrayInputStream(ByteArray(0)))
        }
    }

    @Test
    fun `META JSON이 깨졌으면 IOException을 던진다`() {
        assertThrows(IOException::class.java) {
            ConversationStreamProtocol.readMeta(ByteArrayInputStream(meta("{not json") + end()))
        }
    }

    @Test
    fun `END 이후에는 계속 -1을 반환한다`() {
        val input = ByteArrayInputStream(meta("{}") + audio(7) + end())
        ConversationStreamProtocol.readMeta(input)
        val audioStream = AudioFrameInputStream(input)

        assertEquals(7, audioStream.read())
        assertEquals(-1, audioStream.read())
        assertEquals(-1, audioStream.read())
    }
}
