package com.final_pj.voice.feature.chatbot.util

/**
 * 요약 텍스트 기반으로 LinkedHashMap(버튼 노출 순서 유지) 생성
 *
 * 지원 구분자: ":", "|", "-", "—"
 * 예) "피해신고: 112 또는 1332로 ..."
 */
object ActionMapBuilder {

    fun buildFromSummary(
        summaryText: String,
        fallbackKeysInOrder: List<String>,
        defaultMessage: String = "해당 항목에 대한 안내를 준비 중입니다."
    ): LinkedHashMap<String, String> {

        val parsed = LinkedHashMap<String, String>()

        val lines = summaryText
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (line in lines) {
            val (k, v) = splitKeyValue(line) ?: continue
            parsed[k] = v
        }

        // fallbackKeysInOrder 순서대로 채워넣기(없는 키는 기본 문구)
        val result = LinkedHashMap<String, String>()
        for (key in fallbackKeysInOrder) {
            result[key] = parsed[key] ?: defaultMessage
        }

        // 요약 텍스트에 추가로 들어온 키(정의되지 않은 키)도 뒤에 붙이고 싶으면 아래 주석 해제
        // for ((k, v) in parsed) if (!result.containsKey(k)) result[k] = v

        return result
    }

    private fun splitKeyValue(line: String): Pair<String, String>? {
        val separators = listOf(":", "|", "—", "-", "–")
        val idx = separators
            .map { sep -> line.indexOf(sep).takeIf { it > 0 } ?: Int.MAX_VALUE }
            .minOrNull() ?: Int.MAX_VALUE

        if (idx == Int.MAX_VALUE) return null

        val key = line.substring(0, idx).trim()
        val value = line.substring(idx + 1).trim()
        if (key.isBlank() || value.isBlank()) return null
        return key to value
    }
}
