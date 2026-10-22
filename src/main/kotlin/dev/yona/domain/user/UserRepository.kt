package dev.yona.domain.user

import org.springframework.data.jpa.repository.JpaRepository

/**
 * 사용자 저장소이다. 조회 규칙은 v1.6 `User.findByLoginKey`와 같다.
 */
interface UserRepository : JpaRepository<User, Long> {

    /** 로그인 ID로 조회한다. v1.6의 `ieq`처럼 대소문자를 구별하지 않는다. */
    fun findByLoginIdIgnoreCase(loginId: String): User?

    /** 대표 이메일(`n4user.email`)로 정확히 일치하는 사용자를 조회한다. */
    fun findByEmail(email: String): User?

    /** 로그인 ID로 정확히 일치하는 사용자를 조회한다 (v1.6 `User.findByLoginId`는 `eq`). */
    fun findByLoginId(loginId: String): User?

    /** 개인 API 토큰(`n4user.token`)으로 사용자를 조회한다 (v1.6 `User.findByUserToken`). */
    fun findByToken(token: String): User?

    /**
     * 로그인 ID 또는 이메일로 사용자를 찾는다. 로그인 ID가 우선이고, 없을 때만 이메일로 찾는다
     * (v1.6 `User.findByLoginKey`).
     */
    fun findByLoginKey(loginIdOrEmail: String): User? =
        findByLoginIdIgnoreCase(loginIdOrEmail) ?: findByEmail(loginIdOrEmail)
}
