package com.final_pj.voice.feature.chatbot.network.dto

import com.final_pj.voice.feature.chatbot.data.ChatFaissRequest
import com.final_pj.voice.feature.chatbot.data.ChatFaissResponse
import com.final_pj.voice.feature.chatbot.data.SessionHistoryResponse
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
    
    // 이건 faiss 기반 챗봇 위에껀 openai 만 사용한거
    @POST("/chat-faiss/chat")
    suspend fun chat(@Body req: ChatFaissRequest): ChatFaissResponse

    @GET("/chat-faiss/sessions/{sessionId}/messages")
    suspend fun getSessionMessages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int = 200
    ): SessionHistoryResponse

}