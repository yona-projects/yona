package com.github.yonaprojects.yona.domain.gpgkey

// yona-wiki P3-03 Step9 — 커밋 목록/상세 화면의 Verified 배지가 쓰는 3가지 상태. GitHub의
// "Verified"/"Unverified"/무배지 3분류와 동일한 개념이다.
enum class GpgVerificationStatus {
    // 커밋에 gpgsig 헤더가 아예 없음(가장 흔한 기본 상태) — 배지를 표시하지 않는다.
    UNSIGNED,

    // 서명은 있지만 등록된 GPG 키로 암호학적 검증에 실패했거나(위조/변조), 서명 키를 찾았지만
    // author 이메일이 그 키의 인증된 UID 이메일과 일치하지 않음 — "Unverified" 배지.
    UNVERIFIED,

    // 등록된 GPG 키로 서명이 실제로 암호학적으로 검증됐고, author 이메일도 그 키의 UID와
    // 일치함 — "Verified" 배지.
    VERIFIED
}
