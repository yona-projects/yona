package com.github.yonaprojects.yona.domain.sso

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface Saml2SsoSettingsRepository : JpaRepository<Saml2SsoSettings, Long>
