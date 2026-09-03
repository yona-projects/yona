package com.github.yonaprojects.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EmailRepository : JpaRepository<Email, Long> {
    fun findByEmailAndValid(email: String, valid: Boolean): Email?
    fun existsByEmailAndValid(email: String, valid: Boolean): Boolean
    fun findByEmailAndValidFalse(email: String): List<Email>
}
