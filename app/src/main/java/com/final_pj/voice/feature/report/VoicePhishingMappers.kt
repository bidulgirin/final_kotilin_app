package com.final_pj.voice.feature.report

import com.final_pj.voice.core.util.NumberNormalizer
import com.final_pj.voice.feature.report.entity.PhishingNumberEntity
import com.final_pj.voice.feature.report.network.dto.VoicePhisingSnapshotItemRes


fun VoicePhisingSnapshotItemRes.toEntity(nowMs: Long = System.currentTimeMillis()): PhishingNumberEntity {
    val normalized = NumberNormalizer.normalize(this.number)
    return PhishingNumberEntity(
        id = this.id,
        number = this.number,
        numberNormalized = normalized,
        description = this.description,
        reportCount = this.reportCount,
        updatedAt = nowMs
    )
}