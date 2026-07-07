package com.example.graduation_project.data.api

import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException

class TokenAuthenticator(
    private val tokenStorage: TokenStorage,
    private val authApi: AuthApi
) : Authenticator {

    companion object {
        private val mutex = Mutex()
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") == null) return null

        val newAccessToken = runBlocking {
            mutex.withLock {
                val currentToken = tokenStorage.getAccessToken()
                val requestToken = response.request.header("Authorization")
                    ?.removePrefix("Bearer ")
                // 다른 코루틴이 이미 갱신했으면 새 토큰 바로 사용
                if (currentToken != null && currentToken != requestToken) {
                    return@withLock currentToken
                }

                val refreshToken = tokenStorage.getRefreshToken() ?: return@withLock null
                try {
                    val tokenResponse = authApi.refresh(RefreshRequest(refreshToken))
                    tokenStorage.saveTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
                    tokenResponse.accessToken
                } catch (e: HttpException) {
                    // 서버가 refresh 토큰을 명시적으로 거부한 경우(만료/무효)에만 로그아웃 처리.
                    // 그 외 서버 오류(5xx 등)는 토큰을 보존해 이후 재시도가 가능하도록 함.
                    if (e.code() == 401 || e.code() == 403) {
                        tokenStorage.clear()
                    }
                    null
                } catch (e: Exception) {
                    // 네트워크 오류/타임아웃 등 일시적 실패 - 토큰을 지우지 않아
                    // 연결 복구 후 정상 로그인 상태를 유지한다.
                    null
                }
            }
        }

        return if (newAccessToken != null) {
            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        } else {
            null
        }
    }
}
