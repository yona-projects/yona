package com.github.yonaprojects.yona.domain.enumeration

enum class WebhookType(val value: Int) {
    SIMPLE(0),
    DETAIL_SLACK(1),
    DETAIL_HANGOUT_CHAT(2),
    JSON(3)
}
