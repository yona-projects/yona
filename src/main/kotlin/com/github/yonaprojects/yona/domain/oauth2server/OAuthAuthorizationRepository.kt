package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OAuthAuthorizationRepository : JpaRepository<OAuthAuthorization, String> {
    fun findByState(state: String): Optional<OAuthAuthorization>
    fun findByAuthorizationCodeValue(value: String): Optional<OAuthAuthorization>
    fun findByAccessTokenValue(value: String): Optional<OAuthAuthorization>
    fun findByRefreshTokenValue(value: String): Optional<OAuthAuthorization>

    // yona-wiki P3-07 Step2 — OAuth2AuthorizationService.findByToken(token, tokenType=null)에 대응
    // (JdbcOAuth2AuthorizationService의 UNKNOWN_TOKEN_TYPE_FILTER와 동일한 의미 — 4개 토큰 컬럼 중
    // 어디에 있는지 모를 때 전부 검색).
    fun findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValue(
        state: String,
        authorizationCodeValue: String,
        accessTokenValue: String,
        refreshTokenValue: String
    ): Optional<OAuthAuthorization>
}
