package com.github.yonaprojects.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface LinkedAccountRepository : JpaRepository<LinkedAccount, Long> {
    fun findByProviderKeyAndProviderUserId(providerKey: String, providerUserId: String): Optional<LinkedAccount>
    fun findByUser(user: User): List<LinkedAccount>
}
