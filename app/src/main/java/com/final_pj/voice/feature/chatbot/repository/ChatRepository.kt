package com.final_pj.voice.feature.chatbot.repository

import com.final_pj.voice.feature.chatbot.network.dto.ChatApi
import com.final_pj.voice.feature.chatbot.network.dto.*

class ChatRepository(private val api: ChatApi) {

    suspend fun send(
        conversationId: String?,
        userText: String,
        callId: Long? = null,
        summaryText: String? = null,
        callText: String? = null
    ): SendChatResponse {
        return api.send(
            SendChatRequest(
                conversationId = conversationId,
                userText = userText,
                callId = callId,
                summaryText = summaryText,
                callText = callText
            )
        )
    }

    suspend fun log(conversationId: String?, role: String, content: String): String {
        val res = api.log(LogMessageRequest(conversationId, role, content))
        return res.conversationId
    }

    // 대화 히스토리 조회
    suspend fun getHistory(conversationId: String, limit: Int = 200): ConversationDto {
        return api.getHistory(conversationId = conversationId, limit = limit)
    }
}