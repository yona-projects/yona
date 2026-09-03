package com.github.yonaprojects.yona.domain.apitoken

// GitHub Fine-grained PAT의 "No access/Read/Write" 3단계 대응. ordinal 순서가 곧 권한 크기 비교
// 기준이라(NONE < READ < WRITE) ApiTokenAuthorizer가 ordinal 비교만으로 "충분한 권한인지"를 판정한다.
enum class ApiTokenPermission {
    NONE,
    READ,
    WRITE
}
