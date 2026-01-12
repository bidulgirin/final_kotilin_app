package com.final_pj.voice.core.util

object NumberNormalizer {
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        // 숫자만 남기기
        val digits = input.filter { it.isDigit() }

        // 한국 번호 기준 간단 처리 예시:
        // +82로 들어오는 케이스를 0으로 시작하게 변환하는 정도
        return when {
            digits.startsWith("82") -> "0" + digits.drop(2)
            else -> digits
        }
    }
}