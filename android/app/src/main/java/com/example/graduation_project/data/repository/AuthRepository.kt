package com.example.graduation_project.data.repository

import android.app.Application
import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.AuthApi
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.local.AppDatabase
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.local.dao.ConversationDiaryLinkDao
import com.example.graduation_project.data.local.dao.DiaryDao
import com.example.graduation_project.data.local.dao.LocationPointDao
import com.example.graduation_project.data.model.LoginRequest
import com.example.graduation_project.data.model.RefreshRequest
import com.example.graduation_project.data.model.SignupRequest
import com.example.graduation_project.data.model.TokenResponse

class AuthRepository(
    private val tokenStorage: TokenStorage,
    application: Application,
    private val authApi: AuthApi = ApiClient.authApi,
    private val diaryDao: DiaryDao = AppDatabase.getInstance(application).diaryDao(),
    private val conversationDiaryLinkDao: ConversationDiaryLinkDao =
        AppDatabase.getInstance(application).conversationDiaryLinkDao(),
    private val locationPointDao: LocationPointDao = AppDatabase.getInstance(application).locationPointDao()
) {

    suspend fun signup(loginId: String, password: String, name: String): ApiResult<TokenResponse> {
        val result = safeApiCall {
            authApi.signup(SignupRequest(loginId, password, name))
        }
        if (result is ApiResult.Success) {
            clearOtherAccountCache()
            tokenStorage.saveTokens(result.data.accessToken, result.data.refreshToken)
        }
        return result
    }

    suspend fun login(loginId: String, password: String): ApiResult<TokenResponse> {
        val result = safeApiCall {
            authApi.login(LoginRequest(loginId, password))
        }
        if (result is ApiResult.Success) {
            clearOtherAccountCache()
            tokenStorage.saveTokens(result.data.accessToken, result.data.refreshToken)
        }
        return result
    }

    suspend fun logout(): ApiResult<Unit> {
        val accessToken = tokenStorage.getAccessToken()
        val refreshToken = tokenStorage.getRefreshToken()
        val result = if (accessToken != null && refreshToken != null) {
            safeApiCall {
                authApi.logout("Bearer $accessToken", RefreshRequest(refreshToken))
            }
        } else {
            ApiResult.Success(Unit)
        }
        tokenStorage.clear()
        clearOtherAccountCache()
        return result
    }

    fun hasAccessToken(): Boolean = tokenStorage.getAccessToken() != null

    /**
     * 계정 전환 시 다른 계정의 일기/위치 캐시가 남아 노출되지 않도록 정리.
     * messages는 여기서 지우지 않음 - userId로 격리되어 있고 서버에 원본이 없는
     * 유일한 사본이라 계정별로 보존됨 (MessageDao 참조)
     */
    private suspend fun clearOtherAccountCache() {
        diaryDao.deleteAll()
        conversationDiaryLinkDao.deleteAll()
        locationPointDao.deleteAll()
    }
}
