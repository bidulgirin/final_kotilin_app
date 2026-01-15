package com.final_pj.voice.feature.chatbot.network

import com.final_pj.voice.core.Constants
import com.final_pj.voice.feature.chatbot.network.dto.ChatApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object RetrofitProvider {

    val BASE_URL = Constants.BASE_URL

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // 연결 시간도 약간 늘림
        .readTimeout(120, TimeUnit.SECONDS)   // 읽기 시간 대폭 늘림 (2분)
        .writeTimeout(30, TimeUnit.SECONDS)  // 쓰기 시간도 약간 늘림
        .callTimeout(130, TimeUnit.SECONDS)    // 전체 호출 시간 대폭 늘림
        .build()

    val api: ChatApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
    }
}
