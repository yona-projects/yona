package com.github.yonaprojects.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserVerificationRepository : JpaRepository<UserVerification, Long> {
    fun findByUser(user: User): UserVerification?
    fun findByLoginIdAndVerificationCode(loginId: String, verificationCode: String): UserVerification?
}
