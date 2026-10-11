package com.example.graduation_project.data.api

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 실시간 음성 메시지 턴의 규격과 폴백 판단 검증 (가짜 WebSocket 서버).
 * 핵심: 서버가 이 턴을 처리하기 전 실패만 폴백(Fallback), 처리한 뒤 실패는 재전송하지 않음(Failed).
 */
class LiveMessageSessionTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()
    private val received: MutableList<Any> = Collections.synchronizedList(mutableListOf())

    /** 서버 쪽에서 본 연결 종료 - 정상 닫기면 "closing:<code>", 끊김이면 "failure:<예외 이름>" */
    private val serverSawEnd = CompletableFuture<String>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        unmockkStatic(Log::class)
    }

    private fun frame(type: Int, payload: ByteArray): ByteString {
        val out = ByteArrayOutputStream()
        out.write(type)
        out.write(payload.size ushr 24)
        out.write(payload.size ushr 16)
        out.write(payload.size ushr 8)
        out.write(payload.size)
        out.write(payload)
        return out.toByteArray().toByteString()
    }

    private val audio = byteArrayOf(9, 8, 7, 6)

    /** commit을 받으면 onCommit을 실행하는 가짜 서버 */
    private fun serve(onOpen: (WebSocket) -> Unit = {}, onCommit: (WebSocket) -> Unit) {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) = onOpen(webSocket)

            override fun onMessage(webSocket: WebSocket, text: String) {
                received.add(text)
                if (text.contains("\"commit\"")) onCommit(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                received.add(bytes)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                serverSawEnd.complete("closing:$code")
                webSocket.close(code, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                serverSawEnd.complete("failure:${t.javaClass.simpleName}")
            }
        }))
    }

    private fun replyNormally(webSocket: WebSocket, withEnd: Boolean = true, serverCloses: Boolean = true) {
        webSocket.send(frame(ConversationStreamProtocol.TYPE_META, """{"userMessage":"안녕하세요"}""".toByteArray()))
        webSocket.send(frame(ConversationStreamProtocol.TYPE_TEXT, """{"text":"반가워요"}""".toByteArray()))
        webSocket.send(frame(ConversationStreamProtocol.TYPE_AUDIO, audio))
        if (withEnd) webSocket.send(frame(ConversationStreamProtocol.TYPE_END, ByteArray(0)))
        if (serverCloses) webSocket.close(if (withEnd) 1000 else 1011, null)
    }

    private fun session(readTimeoutMs: Long = 5_000) = LiveMessageSession(
        client, server.url("/api/conversations/message-live").toString(), Dispatchers.IO, readTimeoutMs
    )

    @Test
    fun `정상 - start와 소리를 보낸 뒤 commit하면 응답 첫머리와 오디오를 받는다`() = runBlocking {
        serve { replyNormally(it) }
        val session = session()
        session.send(byteArrayOf(1, 2))

        val outcome = session.commit()

        assertTrue(outcome is LiveMessageSession.Outcome.Reply)
        val reply = (outcome as LiveMessageSession.Outcome.Reply).reply
        assertEquals("안녕하세요", reply.userMessage)
        assertEquals("반가워요", reply.aiResponse)
        assertArrayEquals(audio, reply.audio.readBytes())

        // 서버가 받은 순서: start → 소리 → commit
        assertTrue((received[0] as String).contains("\"sampleRate\":16000"))
        assertEquals(byteArrayOf(1, 2).toByteString(), received[1])
        assertTrue((received[2] as String).contains("commit"))
    }

    @Test
    fun `서버에 경로가 없으면(404) 폴백하고 다시 시도하지 않도록 알린다`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val outcome = session().commit()

        assertEquals(LiveMessageSession.Outcome.Fallback::class, outcome::class)
        assertTrue((outcome as LiveMessageSession.Outcome.Fallback).unsupported)
    }

    @Test
    fun `서버가 commit 전 실시간 전사 실패(UPSTREAM_UNAVAILABLE)를 알리면 폴백한다`() = runBlocking {
        serve(onOpen = {
            it.send("""{"type":"error","code":"UPSTREAM_UNAVAILABLE","message":"x"}""")
            it.close(1011, null)
        }) {}

        val outcome = session().commit()

        assertTrue(outcome is LiveMessageSession.Outcome.Fallback)
        assertTrue(!(outcome as LiveMessageSession.Outcome.Fallback).unsupported)
    }

    @Test
    fun `commit 뒤 서버가 STT_FAILED를 알리면 재전송하지 않고 서버 오류로 끝낸다`() = runBlocking {
        serve {
            it.send("""{"type":"error","code":"STT_FAILED","message":"x"}""")
            it.close(1011, null)
        }

        val outcome = session().commit()

        assertTrue(outcome is LiveMessageSession.Outcome.Failed)
        assertTrue((outcome as LiveMessageSession.Outcome.Failed).exception is ApiException.ServerError)
    }

    @Test
    fun `commit 뒤 오류 알림 없이 끊기면 재전송하지 않고 네트워크 오류로 끝낸다`() = runBlocking {
        serve { it.cancel() }

        val outcome = session().commit()

        assertTrue(outcome is LiveMessageSession.Outcome.Failed)
        assertTrue((outcome as LiveMessageSession.Outcome.Failed).exception is ApiException.NetworkError)
    }

    @Test
    fun `첫 소리 뒤 END 없이 끝나면 오디오 읽기가 잘림으로 실패한다 - tts-retry 폴백 대상`() = runBlocking {
        serve { replyNormally(it, withEnd = false) }

        val outcome = session().commit() as LiveMessageSession.Outcome.Reply

        try {
            outcome.reply.audio.readBytes()
            fail("잘린 스트림은 IOException이어야 함")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun `응답을 다 읽고 닫으면 끊지 않고 정상 종료(1000)한다 - 서버에 EOFException이 남지 않음`() = runBlocking {
        // 서버가 END 뒤 닫기 전에 앱이 먼저 닫는 경우(실제로 EOFException 로그가 나던 순서)
        serve { replyNormally(it, serverCloses = false) }

        val reply = (session().commit() as LiveMessageSession.Outcome.Reply).reply
        reply.audio.use { assertArrayEquals(audio, it.readBytes()) }

        assertEquals("closing:1000", serverSawEnd.get(5, TimeUnit.SECONDS))
    }

    @Test
    fun `응답 읽기가 실패한 뒤 닫으면 기다리지 않고 연결을 끊는다`() = runBlocking {
        // 첫머리만 보내고 멈춘 서버 - 오디오 읽기가 타임아웃난다
        serve {
            it.send(frame(ConversationStreamProtocol.TYPE_META, """{"userMessage":"안녕하세요"}""".toByteArray()))
            it.send(frame(ConversationStreamProtocol.TYPE_TEXT, """{"text":"반가워요"}""".toByteArray()))
        }

        val reply = (session(readTimeoutMs = 300).commit() as LiveMessageSession.Outcome.Reply).reply
        try {
            reply.audio.use { it.readBytes() }
            fail("응답이 멈추면 IOException이어야 함")
        } catch (expected: IOException) {
        }

        assertTrue(serverSawEnd.get(5, TimeUnit.SECONDS).startsWith("failure:"))
    }
}
