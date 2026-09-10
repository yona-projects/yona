package com.github.yonaprojects.yona.domain.user

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64

// 저장된 비밀번호 해시를 문자열 형태만으로 두 포맷 중 하나로
// 구분한다 — Argon2 인코딩 결과는 항상 "$argon2id$..."로 시작하므로 별도 DB 컬럼/버전 필드
// 없이 "$"로 시작하는지만으로 판별 가능하다. 레거시 포맷(SHA-256, salt 선행, 1024회 반복,
// Base64, prefix 없음)은 기존 가입자의 저장된 해시를 검증하기 위해서만 남겨두고, 새 해시는
// 항상 Argon2id로 생성한다 — 이 파일이 비밀번호 해싱/검증 로직의 유일한 진실 공급원이며, 예전에
// 아홉 곳에 중복돼 있던 동일한 SHA-256 구현을 대체한다.
@Component
class PasswordEncodingService {
    private val argon2Encoder: PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    fun encode(rawPassword: String): String = argon2Encoder.encode(rawPassword)!!

    fun matches(rawPassword: String, storedHash: String?, storedSalt: String?): Boolean {
        if (storedHash.isNullOrEmpty()) return false
        return if (isArgon2Hash(storedHash)) {
            argon2Encoder.matches(rawPassword, storedHash)
        } else {
            // 레거시 호출부 전부가 `user.passwordSalt ?: ""` 관례를 썼다 — salt 컬럼이 없는(빈)
            // 계정도 빈 문자열 salt로 해시된 것으로 취급해 동일하게 검증한다.
            legacyHash(rawPassword, storedSalt ?: "") == storedHash
        }
    }

    fun needsUpgrade(storedHash: String?): Boolean = !storedHash.isNullOrEmpty() && !isArgon2Hash(storedHash)

    private fun isArgon2Hash(storedHash: String): Boolean = storedHash.startsWith("$")

    companion object {
        fun legacyHash(password: String, salt: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.reset()
            digest.update(salt.toByteArray(Charsets.UTF_8))
            var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
            for (i in 1 until 1024) {
                digest.reset()
                hashed = digest.digest(hashed)
            }
            return Base64.getEncoder().encodeToString(hashed)
        }
    }
}
