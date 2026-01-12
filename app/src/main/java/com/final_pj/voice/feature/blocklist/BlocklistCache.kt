package com.final_pj.voice.feature.blocklist

import com.final_pj.voice.core.util.NumberNormalizer
import java.util.concurrent.ConcurrentHashMap

object BlocklistCache {
    // thread-safe set
    private val set = ConcurrentHashMap.newKeySet<String>()

    fun contains(number: String?): Boolean {
        val normalized = NumberNormalizer.normalize(number)
        return normalized.isNotBlank() && set.contains(normalized)
    }

    fun replaceAll(newSet: Set<String>) {
        set.clear()
        set.addAll(newSet)
    }

    fun add(raw: String) {
        val n = NumberNormalizer.normalize(raw)
        if (n.isNotBlank()) set.add(n)
    }

    fun remove(raw: String) {
        val n = NumberNormalizer.normalize(raw)
        if (n.isNotBlank()) set.remove(n)
    }
}