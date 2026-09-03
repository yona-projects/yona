package com.github.yonaprojects.yona.domain.mail

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface OriginalEmailRepository : JpaRepository<OriginalEmail, Long> {
    fun existsByMessageId(messageId: String): Boolean
    fun findByMessageId(messageId: String): Optional<OriginalEmail>
}
