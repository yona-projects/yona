package com.github.yonaprojects.yona.domain.mail

internal const val DEFAULT_MAIL_FROM = "no-reply@yona.io"

// yona Config.getEmailFromSmtp() 대응. 우선순위: yona.mail.from -> spring.mail.username(이메일 형식일 때) -> 기본값.
// SMTP 서버가 인증 계정과 다른 From을 거부하는 환경에서 발송이 되도록 한다.
fun resolveMailFromAddress(configuredFrom: String, smtpUsername: String): String {
    val configured = configuredFrom.trim()
    if (configured.isNotEmpty()) return configured
    val username = smtpUsername.trim()
    if (username.contains("@")) return username
    return DEFAULT_MAIL_FROM
}
