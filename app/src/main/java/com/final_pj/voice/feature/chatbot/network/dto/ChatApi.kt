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
    // ============= faiss 사용 안하는 버전 ===================================
    @POST("/api/v1/chat/send")
    suspend fun send(@Body body: SendChatRequest): SendChatResponse
    // ============= faiss 사용 안하는 버전 ===================================


    @POST("/api/v1/chat/log")
    suspend fun log(@Body body: LogMessageRequest): LogMessageResponse

    // 대화 했던거 다시봐야하니까 넣음
    @GET("/api/v1/chat/{conversationId}")
    suspend fun getHistory(
        @Path("conversationId") conversationId: String,
        @Query("limit") limit: Int = 200
    ): ConversationDto
    
    // =================== 이건 faiss 기반 챗봇 ===================
    @POST("/api/v1/chat-faiss/chat")
    suspend fun chat(@Body req: ChatFaissRequest): ChatFaissResponse

    // 세션별로 (callid 기준 sessionid 생성됨!!!!)
    @GET("/api/v1/chat-faiss/sessions/{sessionId}/messages")
    suspend fun getSessionMessages(
        @Path("sessionId") sessionId: String,
        @Query("limit") limit: Int = 200 // 최대 200개만 불러와라~
    ): SessionHistoryResponse

}