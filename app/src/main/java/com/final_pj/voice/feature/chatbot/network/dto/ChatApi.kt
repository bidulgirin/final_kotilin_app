package com.final_pj.voice.feature.chatbot.network.dto

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {

    @POST("/api/v1/chat/send")
    suspend fun send(@Body body: SendChatRequest): SendChatResponse

    @POST("/api/v1/chat/log")
    suspend fun log(@Body body: LogMessageRequest): LogMessageResponse

    @GET("/api/v1/chat/{conversationId}")
    suspend fun getHistory(
        @Path("conversationId") conversationId: String,
        @Query("limit") limit: Int = 200
    ): ConversationDto
}