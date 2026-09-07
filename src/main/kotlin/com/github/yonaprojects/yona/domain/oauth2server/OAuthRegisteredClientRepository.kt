package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OAuthRegisteredClientRepository : JpaRepository<OAuthRegisteredClient, String> {
    fun findByClientId(clientId: String): Optional<OAuthRegisteredClient>

    // yona-wiki P3-17 — 사용자 셀프서비스 OAuth 앱 등록 화면(내가 등록한 앱 목록)에서 사용.
    fun findByOwnerId(ownerId: Long): List<OAuthRegisteredClient>
}
