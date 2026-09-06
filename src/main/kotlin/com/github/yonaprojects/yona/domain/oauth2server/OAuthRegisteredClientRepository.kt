package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OAuthRegisteredClientRepository : JpaRepository<OAuthRegisteredClient, String> {
    fun findByClientId(clientId: String): Optional<OAuthRegisteredClient>
}
