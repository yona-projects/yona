package com.github.yonaprojects.yona.domain.twofactor

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.stereotype.Component

// TOTP secret은 로그인 시 원문(base32)이 그대로 필요한 대칭 비밀이라 비밀번호처럼 단방향
// 해시로 저장할 수 없다 — Spring Security Crypto의 TextEncryptor(AES, salt로 IV 파생)로
// 암호화해 저장하고 검증 시점에만 복호화한다. application.yml의 yona.security.totp.*
// 참고(운영 배포 시 반드시 환경변수로 덮어써야 하는 개발용 기본값).
@Component
class TotpSecretEncryptor(
    @Value("\${yona.security.totp.encryption-password}") password: String,
    @Value("\${yona.security.totp.encryption-salt}") salt: String
) {
    private val delegate = Encryptors.text(password, salt)

    fun encrypt(plainSecret: String): String = delegate.encrypt(plainSecret)
    fun decrypt(cipherText: String): String = delegate.decrypt(cipherText)
}
