package com.tukcomcd.echo.data.repository

import com.tukcomcd.echo.data.api.ApiClient
import com.tukcomcd.echo.data.api.ApiResult
import com.tukcomcd.echo.data.api.UserApi
import com.tukcomcd.echo.data.api.safeApiCall
import com.tukcomcd.echo.data.model.UserPreferences

class UserRepository(
    private val userApi: UserApi = ApiClient.userApi
) {

    suspend fun getPreferences(): ApiResult<UserPreferences> {
        return safeApiCall {
            userApi.getPreferences()
        }
    }

    suspend fun updatePreferences(preferences: UserPreferences): ApiResult<UserPreferences> {
        return safeApiCall {
            userApi.updatePreferences(preferences)
        }
    }
}
