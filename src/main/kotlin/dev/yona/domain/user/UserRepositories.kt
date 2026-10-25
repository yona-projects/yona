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

/** 소셜 로그인 자격 증명 저장소이다. */
interface UserCredentialRepository : JpaRepository<UserCredential, Long> {

    /** 사용자의 자격 증명을 조회한다 (v1.6 `UserCredential.findByUserId`). */
    fun findByUserId(userId: Long): List<UserCredential>

    /** 제공자와 제공자 쪽 사용자 ID로 연결된 활성 자격 증명을 찾는다 (v1.6 `UserCredential.findByAuthUserIdentity`). */
    @org.springframework.data.jpa.repository.Query(
        "select c from LinkedAccount a join a.userCredential c where c.active = true and a.providerKey = :providerKey and a.providerUserId = :providerUserId order by c.id"
    )
    fun findActiveByProvider(providerKey: String, providerUserId: String): List<UserCredential>
}

/** 소셜 로그인 제공자별 연결 계정 저장소이다. */
interface LinkedAccountRepository : JpaRepository<LinkedAccount, Long> {

    /** 자격 증명에 연결된 계정을 조회한다. */
    fun findByUserCredentialId(userCredentialId: Long): List<LinkedAccount>
}
