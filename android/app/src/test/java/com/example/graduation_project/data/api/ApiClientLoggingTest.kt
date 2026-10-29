package com.example.graduation_project.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 디버그 빌드 HTTP 로그에 개인정보가 남지 않는지 확인한다 (logcat은 adb·버그 리포트로 꺼낼 수 있음).
 * - 기본은 모든 요청을 헤더까지만 남긴다 (본문에 대화 전사·건강·위치·일기·개인정보가 있음)
 * - 응답 내용을 봐야 할 때만 개발자가 본문 로깅을 켠다 (-Pecho.logHttpBody=true). 켜도 인증 본문(비밀번호·토큰)은 남기지 않는다
 */
class ApiClientLoggingTest {

    private val server = MockWebServer()
    private val lines = mutableListOf<String>()
    private lateinit var client: OkHttpClient

    private val log get() = lines.joinToString("\n")

    @Before
    fun setUp() {
        server.start()
        useClient(logBody = false)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `로그인 요청의 비밀번호와 응답의 토큰은 로그에 남기지 않는다`() {
        server.enqueue(MockResponse().setBody("""{"accessToken":"access-secret","refreshToken":"refresh-secret"}"""))

        post("/api/auth/login", """{"loginId":"grandma","password":"pw-secret"}""")

        assertTrue("어떤 요청이었는지는 남김", log.contains("/api/auth/login"))
        assertFalse(log.contains("pw-secret"))
        assertFalse(log.contains("access-secret"))
        assertFalse(log.contains("refresh-secret"))
    }

    @Test
    fun `토큰 갱신 요청·응답의 토큰도 로그에 남기지 않는다`() {
        server.enqueue(MockResponse().setBody("""{"accessToken":"new-access","refreshToken":"new-refresh"}"""))

        post("/api/auth/refresh", """{"refreshToken":"old-refresh"}""")

        assertFalse(log.contains("old-refresh"))
        assertFalse(log.contains("new-access"))
        assertFalse(log.contains("new-refresh"))
    }

    @Test
    fun `그 밖의 요청도 기본은 헤더까지만 남기고 Authorization 헤더는 가린다`() {
        server.enqueue(MockResponse().setBody("""[{"date":"2026-10-15","content":"오늘 손주와 산책했다"}]"""))

        getDiaries()

        assertTrue("어떤 요청이었는지는 남김", log.contains("/api/diaries"))
        assertFalse("일기 본문은 남기지 않음", log.contains("손주와 산책"))
        assertFalse(log.contains("header-secret"))
    }

    @Test
    fun `본문 로깅을 켜면 그 밖의 요청은 본문까지 남긴다`() {
        useClient(logBody = true)
        server.enqueue(MockResponse().setBody("""[{"date":"2026-10-15","content":"오늘 손주와 산책했다"}]"""))

        getDiaries()

        assertTrue(log.contains("손주와 산책"))
        assertFalse(log.contains("header-secret"))
    }

    @Test
    fun `본문 로깅을 켜도 인증 요청·응답 본문은 남기지 않는다`() {
        useClient(logBody = true)
        server.enqueue(MockResponse().setBody("""{"accessToken":"access-secret","refreshToken":"refresh-secret"}"""))

        post("/api/auth/login", """{"loginId":"grandma","password":"pw-secret"}""")

        assertFalse(log.contains("pw-secret"))
        assertFalse(log.contains("access-secret"))
        assertFalse(log.contains("refresh-secret"))
    }

    @Test
    fun `스트리밍 요청은 로그를 남기지 않는다`() {
        useClient(logBody = true)
        server.enqueue(MockResponse().setBody("stream"))

        post(ConversationApi.MESSAGE_STREAM_PATH, "{}")

        assertTrue(lines.isEmpty())
    }

    private fun useClient(logBody: Boolean) {
        client = OkHttpClient.Builder()
            .addInterceptor(ApiClient.createLoggingInterceptor(logger = { lines += it }, debug = true, logBody = logBody))
            .build()
    }

    private fun getDiaries() {
        client.newCall(
            Request.Builder()
                .url(server.url("/api/diaries?days=30"))
                .header("Authorization", "Bearer header-secret")
                .build()
        ).execute().use { it.body?.string() }
    }

    private fun post(path: String, json: String) {
        client.newCall(
            Request.Builder()
                .url(server.url(path))
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
        ).execute().use { it.body?.string() }
    }
}
