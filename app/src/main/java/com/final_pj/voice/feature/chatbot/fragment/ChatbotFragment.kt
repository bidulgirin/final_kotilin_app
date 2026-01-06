package com.final_pj.voice.feature.chatbot.fragment

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.final_pj.voice.R
import com.final_pj.voice.feature.chatbot.adapter.ChatAdapter
import com.final_pj.voice.feature.chatbot.data.ConversationStore
import com.final_pj.voice.feature.chatbot.model.ChatMessage
import com.final_pj.voice.feature.chatbot.network.RetrofitProvider
import com.final_pj.voice.feature.chatbot.repository.ChatRepository
import com.final_pj.voice.feature.chatbot.util.ActionMapBuilder
import com.google.ai.client.generativeai.type.content
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ChatbotFragment : Fragment(R.layout.fragment_chatbot) {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private val repository by lazy { ChatRepository(RetrofitProvider.api) }
    private val store by lazy { ConversationStore(requireContext()) }

    // DetailFragment에서 넘어온 맥락
    private val callId by lazy { arguments?.getLong("CALL_ID", -1L) ?: -1L }
    private val summaryTextArg by lazy { arguments?.getString("SUMMARY_TEXT").orEmpty() }
    private val callTextArg by lazy { arguments?.getString("CALL_TEXT").orEmpty() }

    // Chip 노출 순서를 고정
    private val actionKeysInOrder = listOf(
        "피해신고",
        "지급정지",
        "개인정보노출등록",
        "악성앱 점검",
        "계좌/카드 조치",
        "앱 사용방법"
    )

    private lateinit var actionAnswerMap: LinkedHashMap<String, String>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("ChatbotFragment", "callId=$callId summaryLen=${summaryTextArg.length} textLen=${callTextArg.length}")

        val toolbar = view.findViewById<MaterialToolbar>(R.id.chatToolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvChat)
        adapter = ChatAdapter(messages)
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }

        // summary로 chip 답변맵 생성
        actionAnswerMap = ActionMapBuilder.buildFromSummary(
            summaryText = summaryTextArg,
            fallbackKeysInOrder = actionKeysInOrder
        )

        // ✅ 1) 진입 시: callId → conversationId 로 복원 시도
        lifecycleScope.launch {
            restoreHistoryIfExists(rv)

            // ✅ 2) 복원된 메시지가 하나도 없을 때만 초기 안내 메시지 출력
            if (adapter.itemCount == 0) {
                showInitialGuide()
                rv.scrollToPosition(adapter.itemCount - 1)
            }
        }

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupActions)
        renderActionChips(chipGroup) { selectedTitle ->
            onChipSelected(selectedTitle, rv)
        }

        val et = view.findViewById<TextInputEditText>(R.id.etUserInput)
        val btnSend = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSend)

        btnSend.setOnClickListener {
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@setOnClickListener
            et.setText("")
            onUserSend(text, rv, btnSend)
        }
    }

    /** ✅ callId 기준으로 conversationId를 가져와서 서버에서 히스토리 복원 */
    private suspend fun restoreHistoryIfExists(rv: androidx.recyclerview.widget.RecyclerView) {
        try {
            val cid = store.getConversationId(callId)
            Log.d("ChatbotFragment", "restore: callId=$callId cid=$cid")

            if (cid.isNullOrBlank()) return

            val history = repository.getHistory(cid, limit = 200)
            val uiItems = history.messages.map { m ->
                ChatMessage(isUser = (m.role == "user"), text = m.content)
            }

            adapter.setItems(uiItems)
            rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
        } catch (e: Exception) {
            Log.e("ChatbotFragment", "history load failed: ${e.message}", e)
        }
    }

    /** ✅ 초기 안내는 여기로 모음(중복 방지) */
    private fun showInitialGuide() {
        addBot("조치사항을 선택하거나, 궁금한 점을 입력해주세요.")
        if (summaryTextArg.isNotBlank() || callTextArg.isNotBlank()) {
            addBot("통화 요약/대화 내용을 참고해서 안내드릴게요.")
        }
    }

    private fun onChipSelected(selectedTitle: String, rv: androidx.recyclerview.widget.RecyclerView) {
        val answer = actionAnswerMap[selectedTitle] ?: "해당 항목에 대한 안내를 준비 중입니다."

        // UI 누적
        addUser(selectedTitle)
        addBot(answer)
        rv.smoothScrollToPosition(adapter.itemCount - 1)

        // 칩은 고정 답변 → send(LLM) 불필요 / log로만 저장
        lifecycleScope.launch {
            try {
                val cid = store.getConversationId(callId)
                val newId1 = repository.log(cid, role = "user", content = selectedTitle)
                store.setConversationId(callId, newId1)

                val newId2 = repository.log(newId1, role = "assistant", content = answer)
                store.setConversationId(callId, newId2)
            } catch (e: Exception) {
                Log.e("ChatbotFragment", "chip log failed: ${e.message}", e)
            }
        }
    }

    private fun onUserSend(
        userText: String,
        rv: androidx.recyclerview.widget.RecyclerView,
        btnSend: com.google.android.material.button.MaterialButton
    ) {
        addUser(userText)
        rv.smoothScrollToPosition(adapter.itemCount - 1)

        btnSend.isEnabled = false

        lifecycleScope.launch {
            val summaryToSend = summaryTextArg.trim().takeIf { it.isNotBlank() }
            val textToSend = callTextArg.trim().takeIf { it.isNotBlank() }
            val callIdToSend = if (callId > 0) callId else null

            try {
                val cid = store.getConversationId(callId)
                Log.d("ChatbotFragment", "send: callId=$callId cid(before)=$cid callIdToSend=$callIdToSend")

                val res = repository.send(
                    conversationId = cid,
                    userText = userText,
                    callId = callIdToSend,
                    summaryText = summaryToSend,
                    callText = textToSend
                )

                store.setConversationId(callId, res.conversationId)
                Log.d("ChatbotFragment", "send: cid(after)=${res.conversationId}")

                addBot(res.assistantText)
                rv.smoothScrollToPosition(adapter.itemCount - 1)
            } catch (e: Exception) {
                addBot("네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.\n(${e.localizedMessage ?: "unknown error"})")
                Log.e("ChatbotFragment", "send failed: ${e.message}", e)
            } finally {
                btnSend.isEnabled = true
            }
        }
    }

    private fun renderActionChips(chipGroup: ChipGroup, onClick: (String) -> Unit) {
        chipGroup.removeAllViews()
        for (title in actionAnswerMap.keys) {
            val chip = Chip(requireContext()).apply {
                text = title
                isCheckable = false
                isClickable = true
                setOnClickListener { onClick(title) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun addBot(text: String) {
        adapter.add(ChatMessage(isUser = false, text = text))
    }

    private fun addUser(text: String) {
        adapter.add(ChatMessage(isUser = true, text = text))
    }
}
