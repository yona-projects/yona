package com.github.yonaprojects.yona.domain.twofactor

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

// 백업 코드는 원문 비교가 필요 없는 1회용 값이라 비밀번호와 달리 salt 없는 단방향 해시로도
// 충분하다(코드 자체가 40비트 랜덤이라 레인보우테이블 공격 실익이 없다) — 다만 대소문자/혼동
// 문자(0/O, 1/l)를 배제한 알파벳으로 사람이 옮겨 적기 쉽게 만든다.
@Component
class BackupCodeGenerator {
    private val random = SecureRandom()
    private val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun generate(count: Int = 8): List<String> = (1..count).map { generateOne() }

    fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.getEncoder().encodeToString(digest.digest(normalize(code).toByteArray(Charsets.UTF_8)))
    }

    fun matches(code: String, hash: String): Boolean = hash(code) == hash

    private fun generateOne(): String {
        val raw = (1..10).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        return "${raw.substring(0, 5)}-${raw.substring(5)}"
    }

    private fun normalize(code: String): String = code.trim().uppercase()
}
