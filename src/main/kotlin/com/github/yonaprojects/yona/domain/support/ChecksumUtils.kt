package com.github.yonaprojects.yona.domain.support

import java.security.MessageDigest

// yona IssueApi.java isModifiedByOthers() 대응. 이슈 본문/댓글 인라인 수정 시
// 동시편집 충돌을 감지하는 데 쓰인다.
fun sha1Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

// 캐리지 리턴 문자 차이로 인한 오탐을 막기 위해 \r을 제거하고 trim한 뒤 SHA-1로 비교한다.
// legacy Open API(`-_-api/v1`)의 이슈/게시글 본문·댓글 수정 4종도 전부 이 함수를 그대로
// 재사용한다 — v1.6 원본(`IssueApi.java`/`BoardApi.java`)의 `isModifiedByOthers(current,
// original)`도 클라이언트가 보낸 "원문"(`original` 필드, 미리 계산한 해시가 아니다)과 서버
// 현재 값을 "양쪽 다" 해시해서 비교하는 동일한 로직이다(원본 소스로 확인). 한때 "legacy
// 클라이언트는 이미 계산한 SHA-1 해시를 보낸다"는 검증 없는 가정으로 별도의
// `isModifiedByOthersLegacyChecksum(current, clientChecksum)`(한쪽만 해시)을 만들어 legacy
// 경로 4곳에 잘못 연결해뒀었다 — 실제 legacy 클라이언트(`yona.Tasklist.js`)가 원문을 `original`
// 필드로 보내는 것과 맞지 않아, 그 함수를 쓰던 동안은 실제 legacy 클라이언트의 모든 저장
// 요청이 사실상 항상 409로 거부됐을 것이다. 지금은 제거하고 이 함수 하나로 통일했다.
fun isModifiedByOthers(current: String, fromView: String): Boolean {
    val currentChecksum = sha1Hex(current.replace("\r", "").trim())
    val fromViewChecksum = sha1Hex(fromView.replace("\r", "").trim())
    return currentChecksum != fromViewChecksum
}
