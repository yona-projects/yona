package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.domain.user.User

// yona-wiki P3-02 Step6.6 — "토큰 발급/관리 웹 UI 설계" 대응. 지금까지 ApiTokenRepository는
// findByTokenHash() 하나뿐이라 사용자가 ApiToken을 발급/조회/폐기할 방법이 전혀 없었다(테스트
// 코드로 DB에 직접 넣는 것 외엔 발급 경로 없음 — 계획 문서 "Fine-grained PAT 완전성 갭 분석" 1번).
interface ApiTokenService {
    fun listByOwner(owner: User): List<ApiToken>

    fun issue(
        owner: User,
        name: String,
        allRepositories: Boolean,
        scopedProjectIds: List<Long>,
        scopePermissions: Map<ApiTokenScopeGroup, ApiTokenPermission>,
        expiresInDays: Long
    ): IssuedApiToken

    // owner가 아닌 다른 사용자의 토큰을 폐기하려는 시도는 조용히 무시한다(존재 여부를 노출하지
    // 않기 위해 별도 예외를 던지지 않음 — 컨트롤러가 owner 소유 여부와 무관하게 항상
    // "/user/editform/tokens"로 리다이렉트하는 것과 짝을 이룬다).
    fun revoke(owner: User, tokenId: Long)
}

// 발급 직후에만 원문 토큰 값을 볼 수 있다(해시만 저장하므로 이후 재조회 불가 — GitHub PAT과 동일한
// "지금 한 번만 표시됩니다" UX). 화면단이 이 값을 렌더링한 뒤 버리면 된다.
data class IssuedApiToken(val apiToken: ApiToken, val rawToken: String)
