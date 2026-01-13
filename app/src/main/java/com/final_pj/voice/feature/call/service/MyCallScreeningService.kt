package com.final_pj.voice.feature.call.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.provider.BlockedNumberContract
import com.final_pj.voice.feature.blocklist.service.BlocklistCache

/**
 * 수/발신 통화를 “시스템이 전화 앱으로 연결하기 직전”에 스크리닝해서
 * 차단/허용 여부를 결정하는 서비스.
 *
 * - 사용자가 "통화 스크리닝 앱"으로 이 앱을 선택(ROLE_CALL_SCREENING 등)해야 동작합니다. :contentReference[oaicite:3]{index=3}
 */
class MyCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // 전화번호 (tel:010..., tel:+8210... 같은 URI에서 실제 번호 부분만 추출)
        val number = callDetails.handle?.schemeSpecificPart

        // 수신/발신 구분
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING

        // 발신 통화는 차단하지 않고 그냥 통과 (원하면 발신도 필터링 가능)
        if (!isIncoming) {
            allowCall(callDetails)
            return
        }

        /**
         * 1) 앱 내부 차단 목록(예: 서버/로컬 DB/캐시 기반)
         * 2) 안드로이드 "시스템 차단 목록"(설정/전화앱에서 사용자가 직접 차단한 목록)
         *
         * 둘 중 하나라도 걸리면 차단(Reject) 처리
         */
        val blockedByApp = BlocklistCache.contains(number)
        val blockedBySystem = isBlockedByAndroidSystem(number)

        if (blockedByApp || blockedBySystem) {
            blockCall(callDetails)
        } else {
            allowCall(callDetails)
        }
    }

    /**
     * 안드로이드 시스템 차단 목록에 포함된 번호인지 확인.
     *
     * - API 24(N) 이상에서 BlockedNumberContract 사용 가능 :contentReference[oaicite:4]{index=4}
     * - 단, 기기/정책에 따라 "기본 다이얼러/특정 역할 앱만" 접근 가능한 경우가 있어
     *   SecurityException 방어 코드를 넣는 게 안전합니다. :contentReference[oaicite:5]{index=5}
     */
    private fun isBlockedByAndroidSystem(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        // 현재 사용자/환경에서 차단 기능 및 provider 접근이 가능한지 힌트 체크
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(this)) return false

        return try {
            // 시스템 차단 목록과 매칭되면 true
            BlockedNumberContract.isBlocked(this, number)
        } catch (se: SecurityException) {
            // 시스템 차단 목록 접근 권한이 없는 경우(기본 다이얼러가 아니라든지) 발생 가능
            false
        }
    }

    /**
     * 통화 허용 응답: 시스템이 정상적으로 벨 울리고, 로그/알림도 정상 동작
     */
    private fun allowCall(details: Call.Details) {
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(false) // 통화 허용
                .build()
        )
    }

    /**
     * 통화 차단 응답:
     * - disallowCall + rejectCall 로 "자동으로 끊기"처럼 동작
     * - skipCallLog / skipNotification으로 사용자 로그/알림에 안 남길 수도 있음(원하면 옵션 조절)
     */
    private fun blockCall(details: Call.Details) {
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(true)     // 통화 불허
                .setRejectCall(true)       // 실제로 거절(끊기)
                //.setSkipCallLog(true)      // 통화 기록 남기지 않음 // 확인용
                .setSkipNotification(true) // 알림 표시 안 함
                .build()
        )
    }
}
