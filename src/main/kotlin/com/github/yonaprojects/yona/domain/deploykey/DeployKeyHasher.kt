package com.github.yonaprojects.yona.domain.deploykey

import java.security.MessageDigest

// ApiTokenHasher.hashApiToken()과 동일한 이유(SHA-256 결정적 해시로 WHERE 조회 가능해야 함)로
// 동일한 방식을 그대로 재사용한다.
fun hashDeployKeyToken(rawToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
