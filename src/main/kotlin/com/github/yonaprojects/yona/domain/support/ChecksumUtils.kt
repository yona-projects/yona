package com.github.yonaprojects.yona.domain.support

import java.security.MessageDigest

// yona IssueApi.java:538-548 isModifiedByOthers() 대응 (P1-102). 이슈 본문/댓글 인라인 수정 시 [GL-controllers_api_IssueApi-029]
// 동시편집 충돌을 감지하는 데 쓰인다.
fun sha1Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

// 캐리지 리턴 문자 차이로 인한 오탐을 막기 위해 \r을 제거하고 trim한 뒤 SHA-1로 비교한다.
fun isModifiedByOthers(current: String, fromView: String): Boolean {
    val currentChecksum = sha1Hex(current.replace("\r", "").trim())
    val fromViewChecksum = sha1Hex(fromView.replace("\r", "").trim())
    return currentChecksum != fromViewChecksum
}

// yona IssueApi.java:501-509 isModifiedByOthers()의 legacy Open API 경로 전용 대응(P2-56/57).
// 위 isModifiedByOthers()는 yona 내부 API 계약("클라이언트가 저장 직전 화면 원문을 그대로
// 보낸다")에 맞춰 양쪽을 모두 해시해서 비교하지만, legacy Open API 클라이언트는 이미
// 클라이언트에서 계산한 SHA-1 해시값 자체를 `sha1` 필드로 보낸다(원문이 아님) — 서버 쪽 현재
// 값만 해시해서 그 해시 문자열과 그대로(재해시 없이) 비교해야 legacy 클라이언트와 호환된다.
fun isModifiedByOthersLegacyChecksum(current: String, clientChecksum: String): Boolean {
    return sha1Hex(current.replace("\r", "").trim()) != clientChecksum
}
