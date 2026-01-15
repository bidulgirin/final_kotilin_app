package com.final_pj.voice.feature.chatbot.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "chatbot_store")

class ConversationStore(private val context: Context) {

    private fun convKey(callId: Long) = stringPreferencesKey("conversation_id_$callId")

    // callId별 conversation_id 가져오기
    suspend fun getConversationId(callId: Long): String? {
        val prefs = context.dataStore.data.first()
        return prefs[convKey(callId)]
    }

    // callId별 conversation_id 저장
    suspend fun setConversationId(callId: Long, conversationId: String) {
        context.dataStore.edit { prefs ->
            prefs[convKey(callId)] = conversationId
        }
    }

    // (선택) callId 없이 쓰고 싶을 때 기본값
    suspend fun getConversationId(): String? = getConversationId(callId = -1L)

    suspend fun setConversationId(conversationId: String) = setConversationId(callId = -1L, conversationId = conversationId)
}
