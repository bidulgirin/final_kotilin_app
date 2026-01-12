package com.final_pj.voice.bus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object CallEventBus {

    // 공용 스코프 (이벤트 발행용)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 1. 통화 시작 이벤트
    val _callStarted = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val callStarted = _callStarted.asSharedFlow()

    // 2. 통화 종료 이벤트
    private val _callEnded = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val callEnded = _callEnded.asSharedFlow()

    // 3. STT 결과 데이터 이벤트 (새로 추가)
    private val _sttResult = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val sttResult = _sttResult.asSharedFlow()

    // --- 발행 함수들 (기존 방식 유지) ---

    fun notifyCallStarted() {
        scope.launch { _callStarted.emit(Unit) }
    }

    fun notifyCallEnded() {
        scope.launch { _callEnded.emit(Unit) }
    }

    /**
     * STT 결과 전달을 위한 함수
     * @param text 인식된 문자열
     */
    fun postSttResult(text: String) {
        scope.launch { _sttResult.emit(text) }
    }
}