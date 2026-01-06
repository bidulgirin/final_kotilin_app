package com.final_pj.voice.feature.chatbot.network.dto

import com.google.gson.annotations.SerializedName

data class SendChatRequest(
    @SerializedName("conversation_id")
    val conversationId: String?,

    @SerializedName("user_text")
    val userText: String,

    @SerializedName("call_id")
    val callId: Long? = null,

    @SerializedName("summary_text")
    val summaryText: String? = null,

    @SerializedName("call_text")
    val callText: String? = null
)

data class SendChatResponse(
    @SerializedName("conversation_id")
    val conversationId: String,

    @SerializedName("assistant_text")
    val assistantText: String
)

data class LogMessageRequest(
    @SerializedName("conversation_id")
    val conversationId: String?,

    @SerializedName("role")
    val role: String,

    @SerializedName("content")
    val content: String
)

data class LogMessageResponse(
    @SerializedName("conversation_id")
    val conversationId: String
)

// ---------- 대화 조회 DTO들 ----------
data class MessageDto(
    @SerializedName("id")
    val id: Long,

    @SerializedName("role")
    val role: String, // "user" | "assistant"

    @SerializedName("content")
    val content: String,

    // 서버는 datetime ISO 문자열이므로 String으로 받고 필요하면 파싱
    @SerializedName("created_at")
    val createdAt: String
)

data class ConversationDto(
    @SerializedName("conversation_id")
    val conversationId: String,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("messages")
    val messages: List<MessageDto>
)
