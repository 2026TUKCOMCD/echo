package com.example.graduation_project.data.api

import retrofit2.HttpException
import java.io.IOException

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: ApiException) : ApiResult<Nothing>()
}

sealed class ApiException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    data class NetworkError(
        override val message: String = "네트워크 연결을 확인해주세요",
        override val cause: Throwable? = null
    ) : ApiException(message, cause)

    data class ServerError(
        val code: Int,
        override val message: String = "서버 오류가 발생했습니다",
        override val cause: Throwable? = null
    ) : ApiException(message, cause)

    data class ClientError(
        val code: Int,
        override val message: String = "요청 오류가 발생했습니다",
        override val cause: Throwable? = null
    ) : ApiException(message, cause)

    data class UnknownError(
        override val message: String = "알 수 없는 오류가 발생했습니다",
        override val cause: Throwable? = null
    ) : ApiException(message, cause)
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(apiCall())
    } catch (e: IOException) {
        ApiResult.Error(ApiException.NetworkError(cause = e))
    } catch (e: HttpException) {
        val code = e.code()
        val error = when {
            code in 400..499 -> ApiException.ClientError(
                code = code,
                message = getClientErrorMessage(code),
                cause = e
            )
            code in 500..599 -> ApiException.ServerError(
                code = code,
                message = "서버 오류가 발생했습니다 ($code)",
                cause = e
            )
            else -> ApiException.UnknownError(cause = e)
        }
        ApiResult.Error(error)
    } catch (e: Exception) {
        ApiResult.Error(ApiException.UnknownError(cause = e))
    }
}

private fun getClientErrorMessage(code: Int): String = when (code) {
    400 -> "잘못된 요청입니다"
    401 -> "인증이 필요합니다"
    403 -> "접근 권한이 없습니다"
    404 -> "요청한 리소스를 찾을 수 없습니다"
    409 -> "요청 충돌이 발생했습니다"
    422 -> "처리할 수 없는 요청입니다"
    429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해주세요"
    else -> "요청 오류가 발생했습니다 ($code)"
}
