package com.example.graduation_project.data.repository

import android.util.Log
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.ConversationApi
import com.example.graduation_project.data.api.ConversationStreamProtocol
import com.example.graduation_project.data.model.ConversationMessageResponse
import com.example.graduation_project.data.model.ConversationStartResponse
import com.example.graduation_project.data.model.HealthData
import com.example.graduation_project.data.model.MessageReply
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * sendMessageStreaming의 폴백 규칙 검증:
 * 스트리밍 엔드포인트가 없을 때(404/405)만 /message로 재전송하고, 그 외 실패는 재전송하지 않는다
 * (서버가 이미 그 턴을 처리했을 수 있어 재전송하면 대화 기록이 중복될 수 있음).
 */
class ConversationRepositoryStreamingTest {

    private val api = mockk<ConversationApi>()
    private lateinit var streamSupport: ConversationRepository.StreamSupport
    private lateinit var repository: ConversationRepository

    private val audioPart = MultipartBody.Part.createFormData(
        "audio", "recording.wav", ByteArray(4).toRequestBody("audio/wav".toMediaType())
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        streamSupport = ConversationRepository.StreamSupport()
        repository = ConversationRepository(api, streamSupport, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

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

    private val healthData = HealthData(steps = 1000)

    private fun text(value: String) =
        frame(ConversationStreamProtocol.TYPE_TEXT, """{"text":"$value"}""".toByteArray())

    private fun startStreamBody(): ByteArray =
        frame(ConversationStreamProtocol.TYPE_META, """{"userMessage":null}""".toByteArray()) +
            text("안녕하세요, 어르신!") +
            frame(ConversationStreamProtocol.TYPE_AUDIO, byteArrayOf(9, 8)) +
            frame(ConversationStreamProtocol.TYPE_END, ByteArray(0))

    private fun streamBody(): ByteArray =
        frame(ConversationStreamProtocol.TYPE_META, """{"userMessage":"안녕"}""".toByteArray()) +
            text("반가워요.") +
            frame(ConversationStreamProtocol.TYPE_AUDIO, byteArrayOf(1, 2, 3)) +
            text("오늘 어떠셨어요?") +
            frame(ConversationStreamProtocol.TYPE_AUDIO, byteArrayOf(4)) +
            frame(ConversationStreamProtocol.TYPE_END, ByteArray(0))

    private fun errorResponse(code: Int): Response<okhttp3.ResponseBody> =
        Response.error(code, """{"status":$code}""".toResponseBody("application/json".toMediaType()))

    @Test
    fun `스트리밍 성공 시 사용자 발화와 첫 조각을 돌려주고, 나머지 조각 텍스트는 오디오를 읽으며 받는다`() = runTest {
        coEvery { api.sendMessageStream(any()) } returns
            Response.success(streamBody().toResponseBody("application/x-echo-stream".toMediaType()))

        val result = repository.sendMessageStreaming(audioPart)

        val reply = (result as ApiResult.Success).data as MessageReply.Streaming
        assertEquals("안녕", reply.userMessage)
        assertEquals("반가워요.", reply.aiResponse)
        val moreText = mutableListOf<String>()
        reply.audio.onText = { moreText += it }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), reply.audio.readBytes())
        assertEquals(listOf("오늘 어떠셨어요?"), moreText)
        coVerify(exactly = 0) { api.sendMessage(any()) }
    }

    @Test
    fun `404면 기존 message로 폴백하고, 이후에는 스트리밍을 다시 시도하지 않는다`() = runTest {
        coEvery { api.sendMessageStream(any()) } returns errorResponse(404)
        coEvery { api.sendMessage(any()) } returns
            ConversationMessageResponse(userMessage = "안녕", aiResponse = "반가워요", audioData = "base64")

        val first = repository.sendMessageStreaming(audioPart)
        val second = repository.sendMessageStreaming(audioPart)

        assertEquals(MessageReply.Buffered("안녕", "반가워요", "base64"), (first as ApiResult.Success).data)
        assertTrue(second is ApiResult.Success)
        coVerify(exactly = 1) { api.sendMessageStream(any()) }
        coVerify(exactly = 2) { api.sendMessage(any()) }
        assertTrue(streamSupport.messageUnsupported)
    }

    @Test
    fun `405도 엔드포인트 없음으로 보고 폴백한다`() = runTest {
        coEvery { api.sendMessageStream(any()) } returns errorResponse(405)
        coEvery { api.sendMessage(any()) } returns ConversationMessageResponse(audioData = "base64")

        val result = repository.sendMessageStreaming(audioPart)

        assertTrue((result as ApiResult.Success).data is MessageReply.Buffered)
    }

    @Test
    fun `500이면 재전송하지 않고 서버 오류를 반환한다`() = runTest {
        coEvery { api.sendMessageStream(any()) } returns errorResponse(500)

        val result = repository.sendMessageStreaming(audioPart)

        val error = (result as ApiResult.Error).exception
        assertTrue(error is ApiException.ServerError)
        assertEquals(500, (error as ApiException.ServerError).code)
        coVerify(exactly = 0) { api.sendMessage(any()) }
        assertEquals(false, streamSupport.messageUnsupported)
    }

    @Test
    fun `네트워크 오류면 재전송하지 않고 NetworkError를 반환한다`() = runTest {
        coEvery { api.sendMessageStream(any()) } throws IOException("timeout")

        val result = repository.sendMessageStreaming(audioPart)

        assertTrue((result as ApiResult.Error).exception is ApiException.NetworkError)
        coVerify(exactly = 0) { api.sendMessage(any()) }
    }

    @Test
    fun `첫머리(META, 첫 TEXT)를 읽지 못하면(본문 손상) 재전송하지 않고 NetworkError를 반환한다`() = runTest {
        coEvery { api.sendMessageStream(any()) } returns
            Response.success(byteArrayOf(0x02, 0, 0).toResponseBody("application/x-echo-stream".toMediaType()))

        val result = repository.sendMessageStreaming(audioPart)

        assertTrue((result as ApiResult.Error).exception is ApiException.NetworkError)
        coVerify(exactly = 0) { api.sendMessage(any()) }
    }

    // ---------- /start-stream ----------

    @Test
    fun `대화 시작 스트리밍 성공 시 인사말과 오디오 스트림을 반환한다`() = runTest {
        coEvery { api.startConversationStream(any()) } returns
            Response.success(startStreamBody().toResponseBody("application/x-echo-stream".toMediaType()))

        val result = repository.startConversationStreaming(healthData, null)

        val reply = (result as ApiResult.Success).data as MessageReply.Streaming
        // 첫 인사에는 사용자 발화가 없다
        assertEquals(null, reply.userMessage)
        assertEquals("안녕하세요, 어르신!", reply.aiResponse)
        assertArrayEquals(byteArrayOf(9, 8), reply.audio.readBytes())
        coVerify(exactly = 0) { api.startConversation(any()) }
    }

    @Test
    fun `start-stream이 404면 기존 start로 폴백하고, 이후에는 스트리밍을 다시 시도하지 않는다`() = runTest {
        coEvery { api.startConversationStream(any()) } returns errorResponse(404)
        coEvery { api.startConversation(any()) } returns
            ConversationStartResponse(message = "안녕하세요, 어르신!", audioData = "base64")

        val first = repository.startConversationStreaming(healthData, null)
        val second = repository.startConversationStreaming(healthData, null)

        assertEquals(
            MessageReply.Buffered(null, "안녕하세요, 어르신!", "base64"),
            (first as ApiResult.Success).data
        )
        assertTrue(second is ApiResult.Success)
        coVerify(exactly = 1) { api.startConversationStream(any()) }
        coVerify(exactly = 2) { api.startConversation(any()) }
        assertTrue(streamSupport.startUnsupported)
    }

    @Test
    fun `start-stream 404는 message-stream 스트리밍까지 끄지 않는다`() = runTest {
        coEvery { api.startConversationStream(any()) } returns errorResponse(404)
        coEvery { api.startConversation(any()) } returns ConversationStartResponse(audioData = "base64")
        coEvery { api.sendMessageStream(any()) } returns
            Response.success(streamBody().toResponseBody("application/x-echo-stream".toMediaType()))

        repository.startConversationStreaming(healthData, null)
        val message = repository.sendMessageStreaming(audioPart)

        // 한쪽만 배포된 서버에서 멀쩡한 엔드포인트의 지연 개선까지 잃으면 안 된다
        assertTrue((message as ApiResult.Success).data is MessageReply.Streaming)
        assertEquals(false, streamSupport.messageUnsupported)
        coVerify(exactly = 0) { api.sendMessage(any()) }
    }

    @Test
    fun `start-stream이 500이면 재요청하지 않고 서버 오류를 반환한다`() = runTest {
        coEvery { api.startConversationStream(any()) } returns errorResponse(500)

        val result = repository.startConversationStreaming(healthData, null)

        val error = (result as ApiResult.Error).exception
        assertTrue(error is ApiException.ServerError)
        coVerify(exactly = 0) { api.startConversation(any()) }
        assertEquals(false, streamSupport.startUnsupported)
    }
}
