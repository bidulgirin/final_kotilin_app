package com.final_pj.voice.feature.blocklist

import com.final_pj.voice.core.NumberNormalizer

class BlocklistRepository(private val dao: BlockedNumberDao) {

    suspend fun loadAllToSet(): Set<String> =
        dao.getAllNormalized().toSet()

    suspend fun add(raw: String): Boolean {
        val normalized = NumberNormalizer.normalize(raw)
        if (normalized.isBlank()) return false
        val id = dao.insert(
            BlockedNumberEntity(rawNumber = raw, normalizedNumber = normalized)
        )
        return id != -1L
    }

    suspend fun remove(rawOrNormalized: String): Boolean {
        val normalized = NumberNormalizer.normalize(rawOrNormalized)
        if (normalized.isBlank()) return false
        return dao.deleteByNormalized(normalized) > 0
    }
}