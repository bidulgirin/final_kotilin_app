package com.final_pj.voice.feature.chatbot.model

data class ChatMessage(
    val isUser: Boolean,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)