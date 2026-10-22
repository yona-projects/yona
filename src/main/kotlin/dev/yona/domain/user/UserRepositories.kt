package dev.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository

/** 사용자의 보조 이메일 저장소이다. */
interface EmailRepository : JpaRepository<Email, Long> {

    /** 이메일 주소와 인증 여부가 같은 행이 있는지 확인한다 (v1.6 `Email.exists`). */
    fun existsByEmailIgnoreCaseAndValid(email: String, valid: Boolean): Boolean

    /** 이메일 주소와 인증 여부가 같은 행을 조회한다. */
    fun findByEmailIgnoreCaseAndValid(email: String, valid: Boolean): List<Email>

    /** 사용자의 보조 이메일을 조회한다. */
    fun findByUserIdOrderById(userId: Long): List<Email>
}

/** 가입 인증 정보 저장소이다. */
interface UserVerificationRepository : JpaRepository<UserVerification, Long> {

    /** 로그인 ID와 인증 코드가 모두 맞는 인증 정보를 조회한다 (v1.6 `UserVerification.findbyLoginIdAndVerificationCode`). */
    fun findFirstByLoginIdAndVerificationCode(loginId: String, verificationCode: String): UserVerification?

    /** 사용자의 인증 정보를 조회한다 (v1.6 `UserVerification.findbyUser`). */
    fun findFirstByUserId(userId: Long): UserVerification?
}
