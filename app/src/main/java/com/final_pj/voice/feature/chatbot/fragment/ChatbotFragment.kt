package com.final_pj.voice.feature.chatbot.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.final_pj.voice.R
import com.final_pj.voice.feature.chatbot.adapter.ChatAdapter
import com.final_pj.voice.feature.chatbot.model.ChatMessage
import com.google.android.material.chip.Chip
import com.google.android.material.appbar.MaterialToolbar

/*
* Todo
* 실제 챗봇 기능 붙히기 
* 기존 코드를 활용해서 채팅창 개발
* */
class ChatbotFragment : Fragment(R.layout.fragment_chatbot) {
    
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private val actionAnswerMap: LinkedHashMap<String, String> = linkedMapOf(
        "피해신고" to "경찰청(112) 또는 금융감독원(1332)에 즉시 신고하시길 바랍니다. 통화/문자 내역과 계좌정보를 함께 준비하면 도움이 됩니다.",
        "지급정지" to "해당 은행 고객센터로 즉시 연락해 '지급정지'를 요청하세요. 이미 이체했다면 가능한 빨리 조치하는 것이 중요합니다.",
        "개인정보노출등록" to "금융감독원 '개인정보 노출자 사고예방시스템' 등을 통해 노출 등록을 진행해 보세요. 추가 인증수단 변경도 권장합니다.",
        "악성앱 점검" to "출처가 불분명한 앱 설치 여부를 확인하고, 의심 앱은 삭제하세요. 필요 시 안전모드 부팅 후 삭제를 시도하세요.",
        "계좌/카드 조치" to "사용 중인 계좌 비밀번호 변경, 카드 분실신고/재발급을 진행하세요. 타 서비스(포털/메신저) 비밀번호도 함께 변경하는 것을 권장합니다.",
        "앱 사용방법" to "이 화면에서 조치 버튼을 누르면 안내가 채팅 형태로 누적됩니다. 통화 상세 화면에서 '조치 안내(챗봇)' 버튼으로 진입할 수 있습니다."
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.chatToolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvChat)
        adapter = ChatAdapter(messages)
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }

        addBot("조치사항을 선택해주세요.")

        val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupActions)
        renderActionChips(chipGroup) { selectedTitle ->
            addUser(selectedTitle)

            val answer = actionAnswerMap[selectedTitle]
                ?: "해당 항목에 대한 안내를 준비 중입니다."

            addBot(answer)

            rv.smoothScrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun renderActionChips(
        chipGroup: com.google.android.material.chip.ChipGroup,
        onClick: (String) -> Unit
    ) {
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
