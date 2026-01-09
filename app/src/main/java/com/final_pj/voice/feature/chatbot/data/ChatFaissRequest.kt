package com.final_pj.voice.feature.chatbot.data

import com.google.gson.annotations.SerializedName

data class ChatFaissRequest(
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("message") val message: String,
    @SerializedName("k") val k: Int = 5,
    @SerializedName("min_score") val minScore: Double = 0.7,
    @SerializedName("temperature") val temperature: Double = 0.2,
    @SerializedName("model") val model: String? = null,

    // 통화 컨텍스트(백엔드에 필드 추가 전제)
    @SerializedName("call_id") val callId: Long? = null,
    @SerializedName("summary_text") val summaryText: String? = null,
    @SerializedName("call_text") val callText: String? = null
)

data class ChatFaissResponse(
    @SerializedName("session_id") val sessionId: String,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("final_answer") val finalAnswer: String,
    @SerializedName("matched_cases") val matchedCases: List<MatchedCase>,
    @SerializedName("follow_up_questions") val followUpQuestions: List<String>,
    @SerializedName("disclaimer") val disclaimer: String
)


data class MatchedCase(
    val id: Int,
    val score: Double,
    val category: String,
    @SerializedName("user_query") val userQuery: String,
    val answer: String,
    val metadata: Map<String, Any>?
)

// --------------------------------------
// 백엔드에 GET /chat-faiss/sessions/{id}/messages를 추가한다는 전제
// --------------------------------------

data class SessionHistoryResponse(
    @SerializedName("session_id") val sessionId: String,
    val messages: List<SessionMessage>
)

data class SessionMessage(
    val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("created_at") val createdAt: String
)


