package com.example.graduation_project.data.repository

import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.AuthApi
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.data.model.SignupRequest
import com.example.graduation_project.data.model.TokenResponse

class AuthRepository(
    private val tokenStorage: TokenStorage,
    private val authApi: AuthApi = ApiClient.authApi
) {

    suspend fun signup(loginId: String, password: String, name: String): ApiResult<TokenResponse> {
        val result = safeApiCall {
            authApi.signup(SignupRequest(loginId, password, name))
        }
        if (result is ApiResult.Success) {
            tokenStorage.saveTokens(result.data.accessToken, result.data.refreshToken)
        }
        return result
    }
}
