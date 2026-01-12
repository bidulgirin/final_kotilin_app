package com.final_pj.voice.feature.chatbot.repository

import com.final_pj.voice.feature.chatbot.data.ChatFaissRequest
import com.final_pj.voice.feature.chatbot.data.ChatFaissResponse
import com.final_pj.voice.feature.chatbot.data.SessionHistoryResponse
import com.final_pj.voice.feature.chatbot.network.dto.ChatApi
import com.final_pj.voice.feature.chatbot.network.dto.*

class ChatRepository(private val api: ChatApi) {

    suspend fun send(
        conversationId: String?, // = sessionId
        userText: String,
        callId: Long? = null,
        summaryText: String? = null,
        callText: String? = null
    ): ChatFaissResponse {
        return api.chat(
            ChatFaissRequest(
                sessionId = conversationId,
                message = userText,
                k = 5,
                minScore = 0.4,
                temperature = 0.2,
                model = null, // 필요 시 지정
                callId = callId,
                summaryText = summaryText,
                callText = callText
            )
        )
    }

    suspend fun getHistory(conversationId: String, limit: Int = 200): SessionHistoryResponse {
        return api.getSessionMessages(sessionId = conversationId, limit = limit)
    }

    suspend fun log(conversationId: String?, role: String, content: String): String {
        val res = api.log(LogMessageRequest(conversationId, role, content))
        return res.conversationId
    }

    // 대화 히스토리 조회
//    suspend fun getHistory(conversationId: String, limit: Int = 200): ConversationDto {
//        return api.getHistory(conversationId = conversationId, limit = limit)
//    }
}

