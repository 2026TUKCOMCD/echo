package com.example.graduation_project.data.repository

import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.UserApi
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.model.UserPreferences

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
