package com.final_pj.voice.bus
import kotlinx.coroutines.flow.MutableSharedFlow

object CallEventBus {
    val callEnded = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1
    )
}