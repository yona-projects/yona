package com.github.yonaprojects.yona.domain.apitoken

import java.security.MessageDigest

// tokenHash는 원문 미저장 요건(P3-02 설계)에 따라 SHA-256 해시만 저장한다. bcrypt류 salted 해시가
// 아닌 결정적(deterministic) 해시를 쓰는 이유는 ApiTokenRepository.findByTokenHash()로 인덱스
// 조회가 가능해야 하기 때문 — GitHub PAT도 동일한 이유로 SHA-256을 쓴다(bcrypt는 매 호출마다
// salt가 달라져 "이 해시값과 일치하는 행"을 SQL WHERE로 직접 찾을 수 없다).
fun hashApiToken(rawToken: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
