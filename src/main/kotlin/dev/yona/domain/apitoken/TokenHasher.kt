package dev.yona.domain.apitoken

import java.security.MessageDigest

/** 토큰 값의 해시를 만든다. 새 토큰은 임의 값이라 소금 없이 SHA-256 16진수(64자)로 저장한다. */
object TokenHasher {
    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
