package com.github.yonaprojects.yona.domain.user

/**
 * yona의 models/NotificationMail.java isAllowedEmailDomains() 대응.
 * 콤마로 구분된 허용 도메인 목록이 설정돼 있으면 그 안에 속한 이메일만
 * 가입/자동생성을 허용한다. 설정이 비어있으면 전부 허용(기본값).
 */
object EmailDomainValidator {
    fun isAllowed(email: String, allowedDomainsConfig: String): Boolean {
        if (allowedDomainsConfig.isBlank()) {
            return true
        }
        val allowedDomains = allowedDomainsConfig.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        val atIndex = email.lastIndexOf('@')
        if (atIndex < 0 || atIndex == email.length - 1) {
            return false
        }
        val domain = email.substring(atIndex + 1).lowercase()

        return allowedDomains.contains(domain)
    }
}
