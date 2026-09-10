package com.github.yonaprojects.yona.domain.twofactor

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

// 이 저장소에는 prod/dev 같은 환경 프로파일 개념이 없고(profile은 DB 엔진 선택 용도), 표준
// 로컬 검증 워크플로와 전체 테스트 스위트가 전부 env var 없이 이 placeholder 기본값으로
// 컨텍스트를 띄운다 — 그래서 hard fail 대신, 무시하기 어려운 ERROR 로그로 알리는 방식을
// 택했다. 배포 환경에서 이 로그를 감시하는 것은 운영팀 책임이다.
@Component
class TotpEncryptionKeyGuard(
    @Value("\${yona.security.totp.encryption-password}") private val password: String,
    @Value("\${yona.security.totp.encryption-salt}") private val salt: String
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (isUsingDefaultEncryptionKey(password, salt)) {
            logger.error(WARNING_MESSAGE)
        }
    }

    companion object {
        const val DEFAULT_PASSWORD = "yona-totp-dev-only-password"
        const val DEFAULT_SALT = "596f6e6132303236"

        private val logger = LoggerFactory.getLogger(TotpEncryptionKeyGuard::class.java)

        private val WARNING_MESSAGE = """
            |
            |================================================================
            |[SECURITY WARNING] TOTP encryption key is still the committed
            |placeholder default. If this is PRODUCTION, every user's TOTP
            |secret is encrypted with a key anyone can read from source
            |control, and 2FA provides no real protection.
            |Set YONA_TOTP_ENCRYPTION_PASSWORD and YONA_TOTP_ENCRYPTION_SALT
            |to values unique to this deployment.
            |================================================================
        """.trimMargin()

        fun isUsingDefaultEncryptionKey(password: String, salt: String): Boolean =
            password == DEFAULT_PASSWORD && salt == DEFAULT_SALT
    }
}
