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
                } catch (e: Exception) {
                    tokenStorage.clear()
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
