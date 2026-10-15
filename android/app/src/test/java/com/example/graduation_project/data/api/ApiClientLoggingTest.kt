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
 * 디버그 빌드 HTTP 로그에 비밀번호·토큰이 남지 않는지 확인한다 (logcat은 adb·버그 리포트로 꺼낼 수 있음).
 * 인증 외 요청은 디버깅을 위해 지금처럼 본문까지 남긴다.
 */
class ApiClientLoggingTest {

    private val server = MockWebServer()
    private val lines = mutableListOf<String>()
    private lateinit var client: OkHttpClient

    private val log get() = lines.joinToString("\n")

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder()
            .addInterceptor(ApiClient.createLoggingInterceptor(logger = { lines += it }, debug = true))
            .build()
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
    fun `그 밖의 요청은 본문까지 남기되 Authorization 헤더는 가린다`() {
        server.enqueue(MockResponse().setBody("""[{"date":"2026-10-15"}]"""))

        client.newCall(
            Request.Builder()
                .url(server.url("/api/diaries?days=30"))
                .header("Authorization", "Bearer header-secret")
                .build()
        ).execute().use { it.body?.string() }

        assertTrue(log.contains("2026-10-15"))
        assertFalse(log.contains("header-secret"))
    }

    @Test
    fun `스트리밍 요청은 로그를 남기지 않는다`() {
        server.enqueue(MockResponse().setBody("stream"))

        post(ConversationApi.MESSAGE_STREAM_PATH, "{}")

        assertTrue(lines.isEmpty())
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
