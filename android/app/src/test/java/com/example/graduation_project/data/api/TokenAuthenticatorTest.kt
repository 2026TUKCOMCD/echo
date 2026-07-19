package com.example.graduation_project.data.api

import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.model.TokenResponse
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * TokenAuthenticator 단위 테스트
 *
 * 핵심 검증: refresh 실패 유형에 따라 토큰을 지울지/보존할지 구분한다.
 * - 서버가 refresh 토큰을 명시적으로 거부(401/403)한 경우에만 로그아웃(clear)
 * - 네트워크 오류(IOException)나 서버 오류(5xx)는 토큰 보존 (일시적 실패)
 */
class TokenAuthenticatorTest {

    private lateinit var tokenStorage: TokenStorage
    private lateinit var authApi: AuthApi
    private lateinit var authenticator: TokenAuthenticator

    private val oldAccessToken = "old-access-token"
    private val storedRefreshToken = "stored-refresh-token"

    @Before
    fun setUp() {
        tokenStorage = mockk(relaxed = true)
        authApi = mockk()
        authenticator = TokenAuthenticator(tokenStorage, authApi)

        // refresh 경로로 진입하도록: 저장된 access 토큰 == 요청에 쓰인 토큰
        every { tokenStorage.getAccessToken() } returns oldAccessToken
        every { tokenStorage.getRefreshToken() } returns storedRefreshToken
    }

    private fun unauthorizedResponse(): Response {
        val request = Request.Builder()
            .url("https://example.com/api/resource")
            .header("Authorization", "Bearer $oldAccessToken")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
    }

    private fun httpException(code: Int): HttpException {
        val errorBody = "".toResponseBody(null)
        return HttpException(retrofit2.Response.error<Any>(code, errorBody))
    }

    @Test
    fun `refresh 성공 시 새 토큰 저장하고 재요청에 새 토큰 부착`() {
        coEvery { authApi.refresh(any()) } returns TokenResponse(
            accessToken = "new-access-token",
            refreshToken = "new-refresh-token"
        )
        every { tokenStorage.saveTokens(any(), any()) } just Runs

        val result = authenticator.authenticate(null, unauthorizedResponse())

        assertEquals("Bearer new-access-token", result?.header("Authorization"))
        verify { tokenStorage.saveTokens("new-access-token", "new-refresh-token") }
        verify(exactly = 0) { tokenStorage.clear() }
    }

    @Test
    fun `refresh 토큰 만료(401) 시 토큰을 지운다`() {
        coEvery { authApi.refresh(any()) } throws httpException(401)

        val result = authenticator.authenticate(null, unauthorizedResponse())

        assertNull(result)
        verify(exactly = 1) { tokenStorage.clear() }
    }

    @Test
    fun `refresh 토큰 거부(403) 시 토큰을 지운다`() {
        coEvery { authApi.refresh(any()) } throws httpException(403)

        val result = authenticator.authenticate(null, unauthorizedResponse())

        assertNull(result)
        verify(exactly = 1) { tokenStorage.clear() }
    }

    @Test
    fun `네트워크 오류 시 토큰을 보존한다`() {
        coEvery { authApi.refresh(any()) } throws IOException("timeout")

        val result = authenticator.authenticate(null, unauthorizedResponse())

        assertNull(result)
        verify(exactly = 0) { tokenStorage.clear() }
    }

    @Test
    fun `서버 오류(500) 시 토큰을 보존한다`() {
        coEvery { authApi.refresh(any()) } throws httpException(500)

        val result = authenticator.authenticate(null, unauthorizedResponse())

        assertNull(result)
        verify(exactly = 0) { tokenStorage.clear() }
    }
}
