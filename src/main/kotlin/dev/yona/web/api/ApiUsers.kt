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
    private val messages: MessageSource
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /**
     * 사용자를 만들고 결과를 `{status, reason, user}`로 알린다. 허용하지 않는 메일 도메인은 `403`, 이미 있는 이메일이나 아이디는 `409`이다.
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
            signup.isEmailExist(email) || signup.isLoginIdUsed(loginId) -> {
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
     * 가져오는 글의 작성자를 이메일로 찾고 없으면 만든다. 만들 수 없으면(허용하지 않는 도메인 등) 요청한 사용자 [fallback]을 쓴다
     * (v1.6은 이때 익명 사용자가 작성자가 되었다).
     */
    fun findOrCreateByEmail(loginId: String, name: String, email: String, fallback: User): User {
        users.findByEmail(email)?.let { return it }
        create(loginId, name, email)
        return users.findByEmail(email) ?: fallback
    }
}
