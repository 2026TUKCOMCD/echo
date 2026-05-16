package com.example.graduation_project.data.repository

import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.WeatherApi
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.model.WeatherResponse

class WeatherRepository(
    private val weatherApi: WeatherApi = ApiClient.weatherApi
) {

    suspend fun getWeather(lat: Double, lon: Double): ApiResult<WeatherResponse> {
        return safeApiCall { weatherApi.getWeather(lat, lon) }
    }
}
