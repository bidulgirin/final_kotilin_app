package com.final_pj.voice.feature.report.network

import com.final_pj.voice.core.Constants
import com.final_pj.voice.feature.report.network.api.VoicePhisingApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // 에뮬레이터에서 로컬 서버 접근 시: http://10.0.2.2:8000/
    // 실제 서버면: https://your-domain.com/
    private const val BASE_URL = Constants.BASE_URL
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    val voicePhisingApi: VoicePhisingApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VoicePhisingApi::class.java)
    }
}
