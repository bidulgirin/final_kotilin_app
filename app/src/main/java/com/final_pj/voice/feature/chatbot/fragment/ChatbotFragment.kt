package com.final_pj.voice.feature.chatbot.fragment

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.final_pj.voice.R
import com.final_pj.voice.feature.chatbot.adapter.ChatAdapter
import com.final_pj.voice.feature.chatbot.data.ConversationStore
import com.final_pj.voice.feature.chatbot.model.ChatMessage
import com.final_pj.voice.feature.chatbot.network.RetrofitProvider
import com.final_pj.voice.feature.chatbot.repository.ChatRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
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
    private val keywords by lazy { arguments?.getStringArrayList("KEYWORDS") }

    data class PredefinedQA(
        val id: Int,
        val category: String,
        val userQuery: String,
        val answer: String,
        val metadata: Map<String, String> = emptyMap()
    )

    // ✅ JSON 내용을 Kotlin 리스트로 그대로 옮김 (Chip = category)
    private val predefinedQAs = listOf(
        PredefinedQA(
            id = 1,
            category = "기관사칭(검찰/경찰)",
            userQuery = "검찰청에서 내가 범죄에 연루됐다고 연락 왔어요. 내 통장이 대포통장이래요!",
            answer = "100% 보이스피싱입니다. 검찰은 절대 전화로 수사 협조를 구하거나 범죄 연루 사실을 통보하지 않습니다. 즉시 전화를 끊으세요. 확인이 필요하다면 검찰청 대표번호(1301)로 직접 전화하십시오.",
            metadata = mapOf("action" to "즉시종료", "contact" to "1301", "urgency" to "high")
        ),
        PredefinedQA(
            id = 2,
            category = "기관사칭(금감원)",
            userQuery = "금감원 직원이 자산 보호를 위해서 돈을 현금으로 찾아서 자신들에게 맡기래요",
            answer = "절대 응하지 마세요. 금융감독원이나 은행 직원은 어떤 경우에도 고객에게 현금을 직접 전달받거나 특정 계좌로 이체를 요구하지 않습니다. 전화를 끊고 해당 은행 공식 고객센터로 확인하세요.",
            metadata = mapOf("action" to "현금전달금지", "contact" to "1332", "urgency" to "high")
        ),
        PredefinedQA(
            id = 3,
            category = "긴급대응(송금직후)",
            userQuery = "방금 사기꾼한테 돈을 보냈어요! 어떡하죠?",
            answer = "지금 즉시 '지급정지'를 신청해야 합니다! 송금한 은행의 고객센터나 경찰청(112)에 전화하여 상대방 계좌를 묶어달라고 요청하세요. 1분 1초가 중요합니다.",
            metadata = mapOf("action" to "지급정지", "urgency" to "critical")
        ),
        PredefinedQA(
            id = 4,
            category = "지인사칭(가족)",
            userQuery = "가족이 폰 액정이 깨졌다고 링크를 보냈는데 눌러도 되나요?",
            answer = "절대 누르지 마세요. 전형적인 '메신저 피싱' 수법입니다. 자녀의 원래 번호로 직접 통화를 시도해 사실을 확인하세요. 자녀가 전화를 안 받는다면 절대 링크를 클릭하거나 돈을 보내지 마세요.",
            metadata = mapOf("action" to "클릭금지", "target" to "가족", "urgency" to "high")
        ),
        PredefinedQA(
            id = 5,
            category = "대출사기",
            userQuery = "정부지원 저금리 대출 문자가 왔는데, 기존 대출부터 갚아야 승인이 난대요.",
            answer = "사기입니다. 대출 승인을 위해 기존 대출금을 특정 계좌로 입금하라고 하거나, 수수료를 미리 요구하는 것은 100% 대출사기입니다. 금융사는 문자 메시지로 대출을 권유하지 않습니다.",
            metadata = mapOf("action" to "입금금지", "type" to "대환대출", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 6,
            category = "개인정보유출",
            userQuery = "모르는 사람한테 주민등록번호랑 계좌 비밀번호를 알려줬어.",
            answer = "즉시 다음 조치를 취하세요. 1. 엠세이퍼(M-Safer)에 접속해 내 명의의 폰 개통을 차단하세요. 2. 어카운트인포에서 내 모든 계좌의 출금을 한꺼번에 정지하세요. 3. 은행에 연락해 보안카드를 재발급받으세요.",
            metadata = mapOf("action" to "정보차단", "url" to "msafer.or.kr", "urgency" to "critical")
        ),
        PredefinedQA(
            id = 7,
            category = "악성앱설치",
            userQuery = "문자에 있는 링크 눌렀더니 이상한 앱이 깔리고 폰이 안 꺼져요.",
            answer = "폰이 해킹되었습니다. 즉시 '비행기 모드'를 켜서 통신을 차단하거나, 강제로 전원을 끄세요. 다른 안전한 폰을 이용해 은행 계좌를 정지시키고 인근 서비스 센터를 방문해 폰을 초기화해야 합니다.",
            metadata = mapOf("action" to "통신차단", "urgency" to "critical")
        ),
        PredefinedQA(
            id = 8,
            category = "협박형(미납)",
            userQuery = "전기세가 미납돼서 곧 단전된다고 입금하라는 문자가 왔어요.",
            answer = "가짜 공공기관 문자입니다. 한전이나 수도공사는 개인 계좌로 입금을 요구하지 않습니다. 해당 기관 공식 앱이나 홈페이지에서 미납 여부를 직접 확인하세요.",
            metadata = mapOf("action" to "확인후결제", "type" to "공과금", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 9,
            category = "구인사칭(대포통장)",
            userQuery = "알바 지원했는데, 월급 입금용이라며 체크카드를 택배로 보내달래요.",
            answer = "절대 보내지 마세요. 체크카드나 비밀번호를 공유하는 행위는 본인이 모르는 사이에 '보이스피싱 인출책'으로 연루되어 처벌받을 수 있는 매우 위험한 행동입니다.",
            metadata = mapOf("action" to "카드전달금지", "risk" to "형사처벌", "urgency" to "high")
        ),
        PredefinedQA(
            id = 10,
            category = "해외결제스미싱",
            userQuery = "[국제발신] 990,000원 결제완료라는 문자가 왔는데요 저는 구매한적이 없어요.",
            answer = "문자에 적힌 고객센터 번호로 전화하지 마세요! 가짜 번호입니다. 전화를 거는 순간 사기꾼에게 연결됩니다. 실제 카드사 공식 번호로 전화해 결제 내역을 확인하세요.",
            metadata = mapOf("action" to "가짜번호주의", "type" to "스미싱", "urgency" to "high")
        ),
        PredefinedQA(
            id = 11,
            category = "최신스미싱(부고)",
            userQuery = "모르는 번호로 친구 아버님 부고 문자가 왔는데 링크가 있어요.",
            answer = "전형적인 '스미싱'입니다. 링크를 누르는 순간 악성 앱이 설치되어 연락처와 금융정보가 빠져나갑니다. 아는 지인 이름이라도 반드시 직접 전화를 걸어 확인하고 링크는 클릭하지 마세요.",
            metadata = mapOf("action" to "링크클릭금지", "type" to "부고스미싱", "urgency" to "high")
        ),
        PredefinedQA(
            id = 12,
            category = "공공기관사칭(과태료)",
            userQuery = "쓰레기 무단투기로 과태료가 부과됐다고 정부24 링크가 왔어요.",
            answer = "정부 기관은 카카오톡이나 일반 문자로 과태료 납부 링크를 보내지 않습니다. 링크를 누르지 말고 '정부24' 공식 홈페이지나 앱에 직접 접속하여 미납 내역을 확인하세요.",
            metadata = mapOf("action" to "공식앱확인", "type" to "행안부사칭", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 13,
            category = "신종수법(AI)",
            userQuery = "아들 목소리가 들리는데 사고가 났다고 돈을 보내래요. 목소리가 똑같아요.",
            answer = "최근 AI 기술을 이용한 '목소리 복제' 사기가 많습니다. 목소리가 똑같더라도 일단 전화를 끊고 아들의 주변 지인이나 학교에 연락해 소재를 파악하세요. 당황하게 만들어 송금을 유도하는 것이 특징입니다.",
            metadata = mapOf("action" to "본인확인필수", "type" to "AI딥페이크", "urgency" to "critical")
        ),
        PredefinedQA(
            id = 14,
            category = "택배사칭",
            userQuery = "[CJ대한통운] 주소지 불명으로 물품 미배송. 주소 수정 부탁드립니다. 라는 문자가 왔어요",
            answer = "택배사를 사칭한 스미싱입니다. 링크를 누르면 개인정보 입력 창이 나오거나 악성 앱이 설치됩니다. 택배 상태는 해당 쇼핑몰이나 택배사 공식 앱에서 직접 조회하세요.",
            metadata = mapOf("action" to "송장번호조회", "type" to "택배스미싱", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 15,
            category = "금융지원사칭",
            userQuery = "소상공인 저금리 대출 지원 대상이라며 상담원 연결을 하라는 문자가 왔어요.",
            answer = "정부 지원 대출은 문자로 개별 광고를 하지 않습니다. '상담원 연결' 유도 시 개인정보를 요구하므로 해당 문자를 즉시 차단하세요.",
            metadata = mapOf("action" to "문자차단", "type" to "소상공인사기", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 16,
            category = "카드사칭(부정결제)",
            userQuery = "내가 쓰지 않은 카드 결제 문자가 와서 고객센터로 전화했더니 경찰에 신고해준대요.",
            answer = "위험합니다! 가짜 고객센터입니다. 범죄자가 경찰을 사칭하며 다시 전화해 올 것입니다. 전화를 끊고 카드사 뒷면의 공식 번호로 직접 전화해 확인하세요.",
            metadata = mapOf("action" to "공식번호연락", "type" to "피싱연결", "urgency" to "high")
        ),
        PredefinedQA(
            id = 17,
            category = "우체국사칭",
            userQuery = "우체국인데 택배가 반송되었다고 상담원 연결 1번을 누르라고 해요.",
            answer = "우체국은 상담원 연결을 유도하는 ARS 전화를 돌리지 않습니다. 1번을 누르면 사기꾼에게 연결되어 금융정보를 요구받게 되니 즉시 끊으세요.",
            metadata = mapOf("action" to "ARS종료", "type" to "우체국사칭", "urgency" to "high")
        ),
        PredefinedQA(
            id = 18,
            category = "2차사기주의",
            userQuery = "보이스피싱 피해금을 되찾아주겠다는 변호사 광고를 봤는데 믿어도 되나요?",
            answer = "피해 구제를 미끼로 착수금이나 수수료를 요구하는 2차 사기일 가능성이 높습니다. 환급은 금감원과 은행을 통해서만 공식적으로 진행됩니다.",
            metadata = mapOf("action" to "공식기관확인", "contact" to "1332", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 19,
            category = "일괄지급정지",
            userQuery = "모르고 통장 계좌번호랑 비밀번호를 알려줬는데 다 정지하고 싶어요.",
            answer = "즉시 '어카운트인포' 앱의 '내 계좌 한눈에' 서비스를 통해 모든 계좌에 대한 '일괄 지급정지'를 신청하세요. 이후 은행에 방문해 보안 매체를 교체하십시오.",
            metadata = mapOf("action" to "일괄지급정지", "url" to "payinfo.or.kr", "urgency" to "critical")
        ),
        PredefinedQA(
            id = 20,
            category = "해외직구사칭",
            userQuery = "해외 직구 물품 통관을 위해 관세 미납금을 입금하라는 문자가 왔어요.",
            answer = "관세청은 개인 계좌로 세금을 입금받지 않습니다. '모바일 지로' 앱이나 관세청 홈페이지(UNIPASS)에서 본인 인증 후 미납 세금을 확인하세요.",
            metadata = mapOf("action" to "공식앱납부", "type" to "관세사칭", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 21,
            category = "금전요구(경찰사칭)",
            userQuery = "경찰관이라면서 범죄 합의금이 필요하니 합의용 계좌로 돈을 보내래요.",
            answer = "사기입니다. 수사기관은 어떤 경우에도 전화로 돈을 요구하거나 합의금을 중개하지 않습니다. 전화를 끊고 112로 신고하세요.",
            metadata = mapOf("action" to "입금거부", "contact" to "112", "urgency" to "high")
        ),
        PredefinedQA(
            id = 22,
            category = "원격제어앱",
            userQuery = "은행 상담원이 문제 해결을 위해 원격 제어 앱을 깔라고 해요.",
            answer = "절대 설치하지 마세요. 원격 제어 앱(TeamViewer 등)을 설치하게 한 뒤 뱅킹 앱에 접속해 돈을 가로채는 전형적 수법입니다. 이미 설치했다면 즉시 삭제하고 전원을 끄세요.",
            metadata = mapOf("action" to "앱설치거부", "urgency" to "critical")
        ),
        PredefinedQA(
            id = 23,
            category = "허위정보조회",
            userQuery = "검찰청 사이트에 접속해서 제 사건 번호를 조회해보라고 링크를 줬어요.",
            answer = "가짜 피싱 사이트입니다. 실제와 매우 유사하게 만들어져 있습니다. 포털 사이트에서 '검찰청'을 직접 검색해 공식 홈페이지로 이동하거나 1301로 전화해 확인하세요.",
            metadata = mapOf("action" to "공식홈페이지접속", "type" to "파밍", "urgency" to "high")
        ),
        PredefinedQA(
            id = 24,
            category = "계좌이체유도",
            userQuery = "기존 대출을 상환해야 신규 대출이 된다며 직원이 직접 받으러 온대요.",
            answer = "사기입니다. 금융회사 직원이 고객을 직접 만나 현금을 수거하는 대출 상환 방식은 없습니다. 만남 장소에 나가지 마시고 즉시 전화를 끊으세요.",
            metadata = mapOf("action" to "대면금지", "urgency" to "high")
        ),
        PredefinedQA(
            id = 25,
            category = "신고방법",
            userQuery = "보이스피싱 전화를 받았는데 어디에 신고하면 되나요?",
            answer = "피해 발생 전이라면 '경찰청(112)'이나 '금감원(1332)'에 신고하세요. 스팸 문자는 KISA 불법스팸대응센터(118)로 신고하시면 됩니다.",
            metadata = mapOf("action" to "신고접수", "contact" to "112, 1332, 118", "urgency" to "low")
        ),
        PredefinedQA(
            id = 26,
            category = "명의도용확인",
            userQuery = "내 이름으로 핸드폰이 여러 대 개통됐는지 확인하고 싶어요.",
            answer = "한국정보통신진흥협회의 '엠세이퍼(M-Safer)' 사이트에서 내 명의로 개설된 통신 서비스 현황을 실시간으로 확인할 수 있습니다.",
            metadata = mapOf("action" to "M-Safer접속", "url" to "msafer.or.kr", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 27,
            category = "어플리케이션(탐지)",
            userQuery = "내 폰에 악성 앱이 있는지 어떻게 검사하나요?",
            answer = "경찰청에서 배포한 '시티즌코난' 또는 신뢰할 수 있는 백신 앱을 설치하여 정밀 검사를 실행하세요. 출처 불명의 앱이 발견되면 즉시 삭제해야 합니다.",
            metadata = mapOf("action" to "시티즌코난설치", "urgency" to "high")
        ),
        PredefinedQA(
            id = 28,
            category = "자녀사칭(편의점)",
            userQuery = "조카가 편의점에서 기프트카드를 사서 핀번호를 찍어 보내달라고 해요.",
            answer = "메신저 피싱의 대표적 수법입니다. 추적이 어려운 기프트카드나 문화상품권을 요구하는 것은 100% 사기입니다. 반드시 조카와 직접 통화하여 확인하세요.",
            metadata = mapOf("action" to "핀번호전달금지", "urgency" to "high")
        ),
        PredefinedQA(
            id = 29,
            category = "국제발신전화",
            userQuery = "006으로 시작하는 번호로 계속 전화가 오는데 받아도 되나요?",
            answer = "해외 발신 전화입니다. 아는 번호가 아니라면 받지 않는 것이 상책입니다. 최근 국제 전화를 국내 번호(010)로 바꿔주는 변작기를 사용하는 경우도 많으니 주의하세요.",
            metadata = mapOf("action" to "수신거부", "type" to "국제전화", "urgency" to "medium")
        ),
        PredefinedQA(
            id = 30,
            category = "피해금환급",
            userQuery = "이미 사기를 당했는데 돈을 돌려받을 수 있을까요?",
            answer = "지급정지가 성공하여 계좌에 잔액이 남아있다면 '피해구제 신청'을 통해 환급받을 수 있습니다. 송금한 은행이나 경찰에 사고 확인서를 발급받아 제출하세요.",
            metadata = mapOf("action" to "피해구제신청", "contact" to "1332", "urgency" to "medium")
        )
    )

    // category -> QA (순서 유지)
    private val categoryMap: LinkedHashMap<String, PredefinedQA> by lazy {
        LinkedHashMap<String, PredefinedQA>().apply {
            predefinedQAs.forEach { put(it.category, it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("ChatbotFragment", "callId=$callId summaryLen=${summaryTextArg.length} textLen=${callTextArg.length}")

        val toolbar = view.findViewById<MaterialToolbar>(R.id.chatToolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val rv = view.findViewById<RecyclerView>(R.id.rvChat)
        adapter = ChatAdapter(messages)
        rv.adapter = adapter
        rv.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }

        lifecycleScope.launch {
            restoreHistoryIfExists(rv)

            if (adapter.itemCount == 0) {
                showInitialGuide()
                rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
            }
        }

        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupActions)
        if (keywords != null) {
            renderKeywordChips(chipGroup, keywords!!) { selectedKeyword ->
                onKeywordChipSelected(selectedKeyword, rv)
            }
        } else {
            renderCategoryChips(chipGroup) { selectedCategory ->
                onCategoryChipSelected(selectedCategory, rv)
            }
        }

        val et = view.findViewById<TextInputEditText>(R.id.etUserInput)
        val btnSend = view.findViewById<MaterialButton>(R.id.btnSend)

        btnSend.setOnClickListener {
            val text = et.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) return@setOnClickListener
            et.setText("")
            onUserSend(text, rv, btnSend)
        }
    }


    /** callId 기준으로 conversationId를 가져와서 서버에서 히스토리 복원 */
    private suspend fun restoreHistoryIfExists(rv: RecyclerView) {
        try {
            val cid = store.getConversationId(callId)
            Log.d("ChatbotFragment", "restore: callId=$callId cid=$cid")

            if (cid.isNullOrBlank()) return

            val history = repository.getHistory(cid, limit = 200)
            val uiItems = history.messages.map { m ->
                ChatMessage(isUser = (m.role == "user"), text = m.content, isLoading = false)
            }

            adapter.setItems(uiItems)
            rv.scrollToPosition((adapter.itemCount - 1).coerceAtLeast(0))
        } catch (e: Exception) {
            Log.e("ChatbotFragment", "history load failed: ${e.message}", e)
        }
    }

    /** 초기 안내 */
    private fun showInitialGuide() {
        addBot("원하시는 항목(칩)을 누르거나, 직접 질문을 입력해 주세요.")
        if (summaryTextArg.isNotBlank() || callTextArg.isNotBlank()) {
            addBot("통화 요약/대화 내용을 참고해서 안내드릴게요.")
        }
    }

    // category 칩 클릭 → user_query / answer 자동 출력
    private fun onCategoryChipSelected(selectedCategory: String, rv: androidx.recyclerview.widget.RecyclerView) {
        val qa = categoryMap[selectedCategory]
        if (qa == null) {
            addUser(selectedCategory)
            addBot("해당 항목에 대한 안내를 준비 중입니다.")
            rv.smoothScrollToPosition(adapter.itemCount - 1)
            return
        }

        // 챗봇처럼 보이게: 유저는 user_query를 말한 것으로 처리
        addUser(qa.userQuery)
        addBot(qa.answer)
        rv.smoothScrollToPosition(adapter.itemCount - 1)

        // 칩은 고정 답변 → LLM 호출 없이 log만 저장
        lifecycleScope.launch {
            try {
                val cid = store.getConversationId(callId)

                // user_query를 유저 발화로 저장
                val newId1 = repository.log(cid, role = "user", content = qa.userQuery)
                store.setConversationId(callId, newId1)

                // answer를 assistant로 저장
                val newId2 = repository.log(newId1, role = "assistant", content = qa.answer)
                store.setConversationId(callId, newId2)

                // (선택) category 자체도 남기고 싶다면 아래처럼 한 줄 추가 가능
                // repository.log(newId2, role = "system", content = "[chip_category] ${qa.category}")
            } catch (e: Exception) {
                Log.e("ChatbotFragment", "chip log failed: ${e.message}", e)
            }
        }
    }

    private fun onKeywordChipSelected(selectedKeyword: String, rv: RecyclerView) {
        // 키워드를 사용자가 입력한 것처럼 처리
        onUserSend(selectedKeyword, rv, view!!.findViewById(R.id.btnSend))
    }

    private fun onUserSend(
        userText: String,
        rv: RecyclerView,
        btnSend: MaterialButton
    ) {
        addUser(userText)
        rv.smoothScrollToPosition(adapter.itemCount - 1)

        btnSend.isEnabled = false

        // ✅ 로딩 말풍선 추가
        val loadingId = adapter.addLoading()
        rv.smoothScrollToPosition(adapter.itemCount - 1)

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

                store.setConversationId(callId, res.sessionId)
                Log.d("ChatbotFragment", "send: cid(after)=${res.sessionId}")

                // ✅ 로딩을 봇 답변으로 교체
                adapter.replaceById(
                    loadingId,
                    ChatMessage(isUser = false, text = res.finalAnswer, isLoading = false)
                )
                rv.smoothScrollToPosition(adapter.itemCount - 1)

            } catch (e: Exception) {
                adapter.replaceById(
                    loadingId,
                    ChatMessage(
                        isUser = false,
                        text = "네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.\n(${e.localizedMessage ?: "unknown error"})",
                        isLoading = false
                    )
                )
                Log.e("ChatbotFragment", "send failed: ${e.message}", e)
            } finally {
                btnSend.isEnabled = true
            }
        }
    }

    private fun renderKeywordChips(chipGroup: ChipGroup, keywords: List<String>, onClick: (String) -> Unit) {
        chipGroup.removeAllViews()
        for (keyword in keywords) {
            val chip = Chip(requireContext()).apply {
                text = keyword
                isCheckable = false
                isClickable = true
                setOnClickListener { onClick(keyword) }
            }
            chipGroup.addView(chip)
        }
    }

    private fun renderCategoryChips(chipGroup: ChipGroup, onClick: (String) -> Unit) {
        chipGroup.removeAllViews()
        // categoryMap은 네 코드 그대로 사용
        for (category in categoryMap.keys) {
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = false
                isClickable = true
                setOnClickListener { onClick(category) }
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
