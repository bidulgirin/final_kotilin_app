// 오디오 데이터 모델 구조
package com.final_pj.voice.model

import android.net.Uri

data class AudioItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val duration: Long,
    val uri: Uri
)