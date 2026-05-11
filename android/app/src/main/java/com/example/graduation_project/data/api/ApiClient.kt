package com.example.graduation_project.data.api

import com.example.graduation_project.BuildConfig
import com.example.graduation_project.data.local.TokenStorage
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    private val authRetrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(
            OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val authApi: AuthApi by lazy { authRetrofit.create(AuthApi::class.java) }

    lateinit var conversationApi: ConversationApi
        private set
    lateinit var diaryApi: DiaryApi
        private set
    lateinit var userApi: UserApi
        private set

    fun init(tokenStorage: TokenStorage) {
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor(tokenStorage))
            .authenticator(TokenAuthenticator(tokenStorage, authApi))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val authenticatedRetrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(authenticatedClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        conversationApi = authenticatedRetrofit.create(ConversationApi::class.java)
        diaryApi = authenticatedRetrofit.create(DiaryApi::class.java)
        userApi = authenticatedRetrofit.create(UserApi::class.java)
    }
}
