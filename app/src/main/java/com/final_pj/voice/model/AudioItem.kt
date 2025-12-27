// 오디오 데이터 모델 구조
package com.final_pj.voice.model

import android.net.Uri

data class AudioItem(
    val id: Long = -1L,
    val title: String,
    val displayName: String,
    val duration: Long = 0L,
    val path: String = "",
    val uri: Uri
)