package com.final_pj.voice.feature.report.repository

import com.final_pj.voice.core.util.NumberNormalizer
import com.final_pj.voice.feature.report.network.api.VoicePhisingApi
import com.final_pj.voice.feature.report.network.dto.PhishingDao
import com.final_pj.voice.feature.report.network.dto.VoicePhisingCreateReq
import com.final_pj.voice.feature.report.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultVoicePhishingRepository(
    private val dao: PhishingDao,
    private val api: VoicePhisingApi,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : VoicePhishingRepository {

    override suspend fun lookupLocal(rawNumber: String): VoicePhishingLookup? = withContext(io) {
        if (rawNumber.isBlank()) return@withContext null
        val normalized = NumberNormalizer.normalize(rawNumber)
        val hit = dao.findByNormalized(normalized) ?: return@withContext null
        VoicePhishingLookup(
            number = hit.number,
            description = hit.description,
            reportCount = hit.reportCount
        )
    }

    override suspend fun submitReport(rawNumber: String, description: String?): Boolean = withContext(io) {
        if (rawNumber.isBlank()) return@withContext false
        val normalized = NumberNormalizer.normalize(rawNumber)
        val res = api.insertNumber(
            VoicePhisingCreateReq(
                number = normalized,
                description = description
            )
        )
        res.isSuccessful
    }

    override suspend fun syncSnapshot(): Result<Int> = withContext(io) {
        return@withContext runCatching {
            val res = api.getSnapshot()
            if (!res.isSuccessful) error("snapshot http ${res.code()}")

            val body = res.body() ?: emptyList()
            val entities = body.map { it.toEntity() }

            // 정책: 서버 스냅샷이 “정답”이면 clear 후 upsert가 더 안전
            dao.clearAll()
            dao.upsertAll(entities)

            entities.size
        }
    }
}