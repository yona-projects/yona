package com.github.yonaprojects.yona.domain.support

import java.security.MessageDigest

// yona IssueApi.java isModifiedByOthers() 대응. 이슈 본문/댓글 인라인 수정 시
// 동시편집 충돌을 감지하는 데 쓰인다.
fun sha1Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

// 캐리지 리턴 문자 차이로 인한 오탐을 막기 위해 \r을 제거하고 trim한 뒤 SHA-1로 비교한다.
// legacy Open API(`-_-api/v1`)의 이슈/게시글 본문·댓글 수정 4종도 이 함수를 그대로 재사용한다 —
// legacy 클라이언트(`yona.Tasklist.js`)는 `original` 필드에 미리 계산한 해시가 아니라 원문
// 자체를 보내므로, 클라이언트 해시값과 한쪽만 비교하는 별도 함수는 필요 없다(과거 그런 함수를
// legacy 경로에 잘못 연결해 매 저장 요청이 409로 거부되던 버그가 있었다 — 제거하고 통일함).
fun isModifiedByOthers(current: String, fromView: String): Boolean {
    val currentChecksum = sha1Hex(current.replace("\r", "").trim())
    val fromViewChecksum = sha1Hex(fromView.replace("\r", "").trim())
    return currentChecksum != fromViewChecksum
}
