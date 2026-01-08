package com.final_pj.voice.feature.call

/*
* {
  "call_id":"1",
  "phishing_score":0.92,
  "should_alert":true
}
*
*
* */
data class FusionResponse(
    val call_id: String,
    val phishing_score: Double,
    val should_alert: Boolean
)

