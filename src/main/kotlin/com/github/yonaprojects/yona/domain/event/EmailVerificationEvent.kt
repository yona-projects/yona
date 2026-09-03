package com.github.yonaprojects.yona.domain.event

data class EmailVerificationEvent(
    val email: String,
    val userName: String,
    val confirmUrl: String
)
