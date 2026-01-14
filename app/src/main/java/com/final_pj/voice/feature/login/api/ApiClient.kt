package com.final_pj.voice.feature.login.api

// ApiClient.kts
import com.final_pj.voice.core.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // 에뮬레이터 기준 로컬 백엔드
    // 실제 폰이면 PC의 LAN IP로 바꿔야 함(예: http://192.168.0.10:8000)
    private const val BASE_URL = Constants.BASE_URL

    val api: ApiService by lazy {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logger)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
