package dev.yona.domain.twofactor

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 2단계 인증 백업 코드이다(추가 개발 항목 X-2, `decisions.md` D-69). 기기를 잃어버렸을 때 쓰는 한 번용 코드로, 만들 때 열 개를 한 번 보여 주고 서버에는 **해시만** 둔다
 * (사이트 비밀 키로 만든 키의 HMAC-SHA256). 코드는 열 글자(다섯 글자씩 하이픈으로 나눈다)이고 헷갈리는 글자(0, 1, i, l, o)는 쓰지 않는다. 입력은 대소문자, 하이픈, 공백을 가리지 않는다.
 */
class BackupCodes(siteSecret: String) {

    private val key = SecretKeySpec(Hkdf.derive(siteSecret, "yona.two-factor.backup"), "HmacSHA256")
    private val random = SecureRandom()

    /** 새 코드 [COUNT]개를 만든다. 서로 다르다. */
    fun generate(): List<String> {
        val codes = LinkedHashSet<String>()
        while (codes.size < COUNT) codes += (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("").let { "${it.take(5)}-${it.drop(5)}" }
        return codes.toList()
    }

    /** 입력을 정리(소문자, 하이픈과 공백 제거)한 글이 코드 모양이면 `true`이다. 6자리 TOTP 코드와 구별하는 데 쓴다. */
    fun looksLikeCode(input: String): Boolean = normalize(input).let { it.length == LENGTH && it.all { c -> c in ALPHABET } }

    /** 저장할 해시(소문자 16진수 64자)이다. 입력은 [looksLikeCode]처럼 정리한 뒤 해시한다. */
    fun hash(input: String): String =
        Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(normalize(input).toByteArray()).joinToString("") { "%02x".format(it) }

    private fun normalize(input: String) = input.lowercase().filterNot { it == '-' || it.isWhitespace() }

    companion object {
        const val COUNT = 10
        private const val LENGTH = 10
        private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    }
}
