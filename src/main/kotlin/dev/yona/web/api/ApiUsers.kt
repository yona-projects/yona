package dev.yona.web.api

import dev.yona.domain.user.SignupForm
import dev.yona.domain.user.SignupService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

/**
 * 외부 REST API가 사용자를 만들거나 찾는 도우미이다. v1.6 `UserApi.createUserNode`와 `IssueApi.findAuthor`에 해당한다.
 */
@Component
class ApiUsers(
    private val signup: SignupService,
    private val users: UserRepository,
    private val messages: MessageSource,
    private val lookup: dev.yona.domain.user.UserLookup
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /**
     * 사용자를 만들고 결과를 `{status, reason, user}`로 알린다. 허용하지 않는 메일 도메인은 `403`, 이미 있는 이메일이나 사용자 아이디는 `409`이다. 조직 이름과 같은 아이디는 막지 않는다(v1.6 `UserApi.newUser`는 이메일만 보며, 아이디만 겹치면 DB 유일 제약으로 `500`이 나서 v2는 `409`로 알린다).
     * 비밀번호는 무작위이다. 가입 정책(관리자 승인, 메일 인증)은 가입 화면과 같다.
     */
    fun create(loginId: String, name: String, email: String): Map<String, Any?> {
        val given = linkedMapOf("loginId" to loginId, "name" to name, "email" to email)
        return when {
            !signup.isAllowedEmailDomain(email) -> {
                val message = messages.getMessage("user.unacceptable.email.domain", null, LocaleContextHolder.getLocale())
                log.warn("Rejected: {} with {} ({})", name, email, message)
                linkedMapOf("status" to 403, "reason" to "Forbidden", "message" to message, "user" to given)
            }
            signup.isEmailExist(email) || users.existsByLoginIdIgnoreCase(loginId) -> {
                log.warn("Rejected: {} with {} (Already exists!)", name, email)
                linkedMapOf("status" to 409, "reason" to "Conflict", "message" to "Already exists!", "user" to given)
            }
            else -> {
                val user = signup.create(SignupForm(loginId, name, email, Base64.getEncoder().encodeToString(ByteArray(32).also(random::nextBytes))))
                linkedMapOf("status" to 201, "reason" to "Created", "user" to linkedMapOf("id" to user.id, "loginId" to user.loginId, "name" to user.name, "email" to user.email))
            }
        }
    }

    /**
     * 가져오는 글의 작성자를 이메일로 찾는다 (v1.6 `IssueApi.findAuthor`, `User.findByEmail`). 대표 이메일, 보조 이메일, 대소문자를 무시한 대표 이메일
     * 순으로 찾고, 없으면 `NullUser`처럼 익명 사용자(번호 -1, 로그인 아이디 빈 문자열)를 돌려준다. v1.6은 `findByEmail`이 절대 `null`을 주지 않아
     * 사용자를 만드는 분기(`createUserNode`)에 닿지 못하므로 새 사용자를 만들지 않는다. 익명 사용자는 저장되지 않고 글의 작성자 열에만 -1이 기록된다.
     */
    fun findAuthor(email: String): User = lookup.findByEmail(email) ?: anonymous(email)

    /** 이메일로 사용자를 찾는다. 없으면 `null`이다 (v1.6 담당자와 구성원 조회는 익명 사용자를 건너뛴다). */
    fun lookup(email: String): User? = lookup.findByEmail(email)

    /** v1.6 `NullUser`에 해당하는 익명 사용자이다. 저장하지 않는 값 객체로만 쓴다. */
    fun anonymous(email: String): User =
        User(id = -1L, name = messages.getMessage("user.notExists.name", null, LocaleContextHolder.getLocale()), loginId = "", email = email)
}
