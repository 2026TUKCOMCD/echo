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

    /** 완전 철회 - 확정된 장소를 포함해 저장된 모든 관련 데이터를 즉시 삭제 */
    suspend fun withdrawConsent(): ApiResult<ConsentResponse> =
        safeApiCall { routinePlaceApi.withdrawConsent() }

    suspend fun getCandidates(): ApiResult<List<RoutinePlaceResponse>> =
        safeApiCall { routinePlaceApi.getCandidates() }

    suspend fun getConfirmedPlaces(): ApiResult<List<RoutinePlaceResponse>> =
        safeApiCall { routinePlaceApi.getConfirmedPlaces() }

    suspend fun confirm(id: Long, category: String): ApiResult<RoutinePlaceResponse> =
        safeApiCall { routinePlaceApi.confirm(id, RoutinePlaceConfirmRequest(category)) }

    suspend fun update(
        id: Long,
        category: String,
        routineDays: List<String>? = null,
        routineTimeRangeStart: String? = null,
        routineTimeRangeEnd: String? = null
    ): ApiResult<RoutinePlaceResponse> =
        safeApiCall {
            routinePlaceApi.update(id, RoutinePlaceConfirmRequest(category, routineDays, routineTimeRangeStart, routineTimeRangeEnd))
        }

    suspend fun delete(id: Long): ApiResult<Unit> =
        safeApiCall { routinePlaceApi.delete(id) }
}
