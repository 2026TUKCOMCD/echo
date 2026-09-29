package com.example.graduation_project.data.api

import android.util.Log
import com.example.graduation_project.data.model.MessageReply
import com.example.graduation_project.util.TurnLatencyTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 실시간 음성 메시지 한 턴 (`/api/conversations/message-live`, WebSocket). 서버 `LiveMessageHandler`와 같은 규격이다.
 *
 * ```
 * 앱 → 서버: 텍스트 {"type":"start",...} → 바이너리 PCM 조각... → 텍스트 {"type":"commit"}
 * 서버 → 앱: 바이너리 프레임(META → TEXT → AUDIO... → END, /message-stream과 같은 형식, 메시지 1개 = 프레임 1개)
 *           또는 텍스트 {"type":"error","code":...} 뒤 닫힘
 * ```
 * 말하는 동안 소리를 보내 두므로, 말 끝(commit) 뒤에는 서버가 남은 인식만 마치고 바로 응답을 시작한다.
 *
 * 생성하자마자 연결을 시작하고, 연결되기 전에 보낸 소리는 OkHttp가 순서대로 쌓아 뒀다가 보낸다.
 * 한 번 쓰고 버린다(턴마다 새 연결).
 */
class LiveMessageSession internal constructor(
    client: OkHttpClient,
    url: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readTimeoutMs: Long = READ_TIMEOUT_MS
) {

    /** commit 결과 */
    sealed class Outcome {
        /** 응답 스트림이 시작됨 - [MessageReply.Streaming]의 오디오 스트림은 호출자가 소비하고 닫아야 한다 */
        data class Reply(val reply: MessageReply.Streaming) : Outcome()

        /**
         * 서버가 이 턴을 처리하기 전에 실패함 - 모아 둔 WAV로 `/message-stream`에 다시 보내도 대화 기록이 중복되지 않는다.
         * @param unsupported 서버에 이 경로가 없음(404/405) - 앱이 살아 있는 동안 다시 시도하지 않는다
         */
        data class Fallback(val reason: String, val unsupported: Boolean = false) : Outcome()

        /** 서버가 이 턴을 받은 뒤 실패함 - 기록 중복을 막기 위해 다시 보내지 않고 오류로 안내한다 */
        data class Failed(val exception: ApiException) : Outcome()
    }

    private val lock = Any()
    private val incoming = MessageQueueInputStream(readTimeoutMs) { cancel() }

    // 아래 상태는 lock으로 보호 (OkHttp 리스너 스레드 ↔ 호출 스레드)
    private var opened = false
    private var committed = false
    private var errorCode: String? = null
    private var handshakeCode: Int? = null
    private var failure: Throwable? = null

    private val webSocket: WebSocket = client.newWebSocket(
        Request.Builder().url(url).build(),
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(lock) { opened = true }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                incoming.offer(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val error = runCatching { json.decodeFromString(ServerError.serializer(), text) }.getOrNull()
                if (error?.type == "error") {
                    Log.w(TAG, "서버가 실시간 메시지 오류를 알림 - code=${error.code}")
                    synchronized(lock) { errorCode = error.code }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
                incoming.finish()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                incoming.finish()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                synchronized(lock) {
                    failure = t
                    if (!opened) handshakeCode = response?.code
                }
                response?.close()
                incoming.fail(IOException("실시간 메시지 연결 실패", t))
            }
        }
    )

    init {
        webSocket.send(START_MESSAGE)
    }

    /** 16kHz 16-bit 모노 PCM 조각을 보낸다. 실패는 commit 결과로 알린다 */
    fun send(pcm: ByteArray) {
        if (pcm.isNotEmpty()) webSocket.send(pcm.toByteString())
    }

    /**
     * 말 끝을 알리고 응답 첫머리(META + 첫 TEXT)까지 기다린다.
     *
     * 폴백 판단 - "서버가 이 턴을 처리하기 시작했는가"만 본다:
     * - 연결이 한 번도 열리지 않았음(핸드셰이크 실패) → 서버가 아무것도 받지 않았으므로 폴백
     * - 서버가 UPSTREAM_UNAVAILABLE을 알림 → commit 전 실패라 처리하지 않았으므로 폴백
     * - commit이 전송 큐에 들어가지 못함(이미 닫힘) → 서버가 commit을 받지 못했으므로 폴백
     * - 그 외(연결된 뒤 commit을 보냈는데 실패) → 서버가 처리했을 수 있어 다시 보내지 않는다
     */
    suspend fun commit(): Outcome {
        val sent = webSocket.send(COMMIT_MESSAGE)
        synchronized(lock) { committed = sent }
        TurnLatencyTracker.onUploadEnd()

        return withContext(ioDispatcher) {
            try {
                val start = ConversationStreamProtocol.readStart(incoming)
                TurnLatencyTracker.onFirstFrame()
                Outcome.Reply(MessageReply.Streaming(start.userMessage, start.firstText, AudioFrameInputStream(incoming)))
            } catch (e: IOException) {
                cancel()
                classifyFailure(e)
            }
        }
    }

    private fun classifyFailure(e: IOException): Outcome = synchronized(lock) {
        val handshake = handshakeCode
        when {
            handshake == 404 || handshake == 405 ->
                Outcome.Fallback("서버에 실시간 메시지 경로가 없음 ($handshake)", unsupported = true)
            !opened -> Outcome.Fallback("실시간 메시지 연결 실패 (${handshake ?: failure?.javaClass?.simpleName})")
            errorCode == ERROR_UPSTREAM_UNAVAILABLE -> Outcome.Fallback("서버가 실시간 전사를 쓸 수 없음")
            !committed -> Outcome.Fallback("commit 전에 연결이 닫힘")
            errorCode == ERROR_BAD_REQUEST -> Outcome.Failed(ApiException.ClientError(400, cause = e))
            errorCode != null -> Outcome.Failed(ApiException.ServerError(500, "서버 오류가 발생했습니다 ($errorCode)", e))
            else -> Outcome.Failed(ApiException.NetworkError(cause = e))
        }
    }

    /** 턴을 버린다 (대화 종료, 녹음 중지 등). 여러 번 불러도 된다 */
    fun cancel() {
        webSocket.cancel()
        incoming.finish()
    }

    @Serializable
    private data class ServerError(val type: String? = null, val code: String? = null)

    companion object {
        private const val TAG = "LiveMessageSession"
        private const val NORMAL_CLOSURE = 1000
        private const val READ_TIMEOUT_MS = 30_000L
        const val SAMPLE_RATE = 16_000
        const val ERROR_UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE"
        const val ERROR_BAD_REQUEST = "BAD_REQUEST"

        private val json = Json { ignoreUnknownKeys = true }
        private const val START_MESSAGE =
            """{"type":"start","sampleRate":$SAMPLE_RATE,"encoding":"pcm16le","channels":1}"""
        private const val COMMIT_MESSAGE = """{"type":"commit"}"""
    }
}

/**
 * WebSocket 바이너리 메시지들을 이어 붙인 바이트 스트림 - 기존 프레임 파서([ConversationStreamProtocol],
 * [AudioFrameInputStream])를 그대로 쓰기 위한 다리. 닫히면 끝(-1), 연결 실패면 IOException.
 * 메시지가 [readTimeoutMs] 동안 오지 않으면 IOException (HTTP 읽기 타임아웃과 같은 역할).
 */
internal class MessageQueueInputStream(
    private val readTimeoutMs: Long,
    private val onClose: () -> Unit
) : InputStream() {

    private sealed class Item {
        class Data(val bytes: ByteArray) : Item()
        object End : Item()
        class Error(val cause: IOException) : Item()
    }

    private val queue = LinkedBlockingQueue<Item>()
    private var current: ByteArray? = null
    private var position = 0
    private var terminal: Item? = null

    fun offer(bytes: ByteArray) {
        if (bytes.isNotEmpty()) queue.offer(Item.Data(bytes))
    }

    fun finish() {
        queue.offer(Item.End)
    }

    fun fail(cause: IOException) {
        queue.offer(Item.Error(cause))
    }

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) == -1) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (true) {
            val chunk = current
            if (chunk != null && position < chunk.size) {
                val n = minOf(len, chunk.size - position)
                System.arraycopy(chunk, position, b, off, n)
                position += n
                return n
            }
            when (val end = terminal) {
                is Item.End -> return -1
                is Item.Error -> throw end.cause
                else -> Unit
            }
            val next = try {
                queue.poll(readTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("실시간 메시지 읽기가 중단됨", e)
            } ?: throw IOException("실시간 메시지 응답이 ${readTimeoutMs}ms 동안 오지 않음")
            when (next) {
                is Item.Data -> {
                    current = next.bytes
                    position = 0
                }
                else -> terminal = next
            }
        }
    }

    override fun close() {
        onClose()
    }
}
