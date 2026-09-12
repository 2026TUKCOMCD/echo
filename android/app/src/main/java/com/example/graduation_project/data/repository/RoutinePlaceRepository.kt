package com.example.graduation_project.data.repository

import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.RoutinePlaceApi
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.model.ConsentResponse
import com.example.graduation_project.data.model.ConsentUpdateRequest
import com.example.graduation_project.data.model.RoutinePlaceConfirmRequest
import com.example.graduation_project.data.model.RoutinePlaceResponse

class RoutinePlaceRepository(
    private val routinePlaceApi: RoutinePlaceApi = ApiClient.routinePlaceApi
) {

    suspend fun getConsent(): ApiResult<ConsentResponse> =
        safeApiCall { routinePlaceApi.getConsent() }

    suspend fun setConsent(consented: Boolean): ApiResult<ConsentResponse> =
        safeApiCall { routinePlaceApi.updateConsent(ConsentUpdateRequest(consented)) }

    suspend fun getCandidates(): ApiResult<List<RoutinePlaceResponse>> =
        safeApiCall { routinePlaceApi.getCandidates() }

    suspend fun getConfirmedPlaces(): ApiResult<List<RoutinePlaceResponse>> =
        safeApiCall { routinePlaceApi.getConfirmedPlaces() }

    suspend fun confirm(id: Long, category: String): ApiResult<RoutinePlaceResponse> =
        safeApiCall { routinePlaceApi.confirm(id, RoutinePlaceConfirmRequest(category)) }

    suspend fun updateCategory(id: Long, category: String): ApiResult<RoutinePlaceResponse> =
        safeApiCall { routinePlaceApi.updateCategory(id, RoutinePlaceConfirmRequest(category)) }

    suspend fun delete(id: Long): ApiResult<Unit> =
        safeApiCall { routinePlaceApi.delete(id) }
}
