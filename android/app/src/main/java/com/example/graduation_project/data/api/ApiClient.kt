package com.example.graduation_project.data.api

import com.example.graduation_project.BuildConfig
import com.example.graduation_project.data.local.TokenStorage
import com.example.graduation_project.util.TurnLatencyTracker
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
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

    private val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    // BODY 레벨 로깅은 응답 본문을 끝까지 읽어 버퍼링한 뒤에 넘겨주므로 스트리밍 응답을 무력화한다
    // (디버그 빌드에서 첫 소리가 전체 합성 완료 후에야 나옴). 스트리밍 요청은 로깅을 건너뛴다.
    // 스트리밍 엔드포인트를 추가하면 반드시 이 목록에도 넣어야 한다.
    private val streamingPaths = setOf(
        ConversationApi.MESSAGE_STREAM_PATH,
        ConversationApi.START_STREAM_PATH
    )

    private val loggingInterceptor = Interceptor { chain ->
        if (chain.request().url.encodedPath in streamingPaths) {
            chain.proceed(chain.request())
        } else {
            httpLoggingInterceptor.intercept(chain)
        }
    }

    // 지연 측정: 스트리밍 요청의 업로드(요청 본문 전송) 완료 시점을 기록한다.
    // 소켓 버퍼 기준이라 실제 전송 완료보다 이르다 (TurnLatencyTracker.onUploadEnd 참고)
    private val streamLatencyListener = object : EventListener() {
        override fun requestBodyEnd(call: Call, byteCount: Long) {
            if (call.request().url.encodedPath in streamingPaths) {
                TurnLatencyTracker.onUploadEnd()
            }
        }
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
    lateinit var weatherApi: WeatherApi
        private set
    lateinit var routinePlaceApi: RoutinePlaceApi
        private set
    var tokenStorage: TokenStorage? = null
        private set

    fun init(tokenStorage: TokenStorage) {
        this.tokenStorage = tokenStorage
        val authenticatedClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(AuthInterceptor(tokenStorage))
            .authenticator(TokenAuthenticator(tokenStorage, authApi))
            .eventListener(streamLatencyListener)
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
        weatherApi = authenticatedRetrofit.create(WeatherApi::class.java)
        routinePlaceApi = authenticatedRetrofit.create(RoutinePlaceApi::class.java)
    }
}
