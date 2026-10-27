package dev.yona.domain.user

import dev.yona.util.LegacyText
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.UserVerificationRequestedEvent
import dev.yona.domain.project.OrganizationRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.util.HtmlSanitizer
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.UUID

/**
 * 가입 신청 폼의 입력값이다. v1.6은 요청을 `User` 전체에 묶어 `state`, `isGuest`, `token` 같은 항목도 정할 수 있었다.
 * v2는 폼이 보내는 항목만 받는다([decisions.md] A-24).
 */
data class SignupForm(val loginId: String?, val name: String?, val email: String?, val password: String?)

/**
 * 가입과 이메일 인증이다. v1.6 `UserApp.newUser`, `validate`, `createNewUser`, `verifyUser`,
 * `models.UserVerification`에 해당한다.
 */
@Service
@Transactional
class SignupService(
    private val users: UserRepository,
    private val emails: EmailRepository,
    private val verifications: UserVerificationRepository,
    private val organizations: OrganizationRepository,
    private val properties: YonaProperties,
    private val events: ApplicationEventPublisher
) {

    /** 가입 입력이 올바르지 않으면 `true`이다. v1.6처럼 어느 항목이 틀렸는지는 화면에 알리지 않는다. */
    @Transactional(readOnly = true)
    fun hasErrors(form: SignupForm): Boolean {
        val loginId = form.loginId
        // 입력 묶기 단계: 로그인 ID는 필수이고 형식과 예약어를 지켜야 하며, 이메일은 있으면 형식이 맞아야 한다.
        if (loginId.isNullOrEmpty() || !LoginIdRules.isValidFormat(loginId) || LoginIdRules.isReserved(loginId)) return true
        val email = form.email
        if (!email.isNullOrEmpty() && !isValidEmailFormat(email)) return true

        // 폼 검증 단계
        if (LegacyText.trim(loginId).isEmpty() || loginId.contains(" ")) return true
        if (LegacyText.trim(form.password.orEmpty()).isEmpty()) return true
        if (users.existsByLoginIdIgnoreCase(loginId) || organizations.existsByNameIgnoreCase(loginId)) return true
        if (email != null && isEmailExist(email)) return true
        return false
    }

    /** 이메일을 이미 쓰고 있으면 `true`이다. 사용자의 대표 이메일이거나 인증을 마친 보조 이메일이 대상이다 (v1.6 `User.isEmailExist`). */
    @Transactional(readOnly = true)
    fun isEmailExist(email: String): Boolean = users.existsByEmailIgnoreCase(email) || emails.existsByEmailIgnoreCaseAndValid(email, true)

    /** 로그인 ID나 조직 이름으로 이미 쓰고 있으면 `true`이다 (v1.6 `UserApp.isUsed`). */
    @Transactional(readOnly = true)
    fun isLoginIdUsed(name: String): Boolean = users.existsByLoginIdIgnoreCase(name) || organizations.existsByNameIgnoreCase(name)

    /** 허용된 이메일 도메인이면 `true`이다. 설정이 비어 있으면 모두 허용한다 (v1.6 `NotificationMail.isAllowedEmailDomains`). */
    fun isAllowedEmailDomain(email: String?): Boolean {
        val configured = properties.allowedSendingMailDomains
        if (configured.isBlank()) return true
        val domain = email?.substringAfterLast('@', "")?.takeIf { it.isNotEmpty() } ?: return false
        return configured.split(",").map { it.lowercase().trim() }.contains(domain.lowercase())
    }

    /**
     * 이메일의 앞부분 같은 후보로 쓸 수 있는 로그인 ID를 만든다 (v1.6 `generateLoginId`). 겹치면 `-yona`를 붙이고, 또 겹치면 숫자를
     * 이어 붙인다. v1.6 `newLoginIdWithoutDup`은 번호를 바꾸는 것이 아니라 후보 뒤에 이어 붙인다(`dup2`, `dup23`, `dup234`).
     */
    @Transactional(readOnly = true)
    fun generateLoginId(candidate: String): String {
        if (users.findByLoginId(candidate) == null) return candidate
        if (users.findByLoginId("$candidate-yona") == null) return "$candidate-yona"
        var suggestion = candidate
        var number = 2
        while (true) {
            suggestion = "$suggestion$number"
            if (users.findByLoginId(suggestion) == null) return suggestion
            number++
        }
    }

    /** 관리자 승인제이면 `true`이다 (`signup.require.admin.confirm`). */
    fun requiresAdminConfirm(): Boolean = properties.signup.required()

    /** 이메일 인증을 쓰면 `true`이다 (`application.use.email.verification`). */
    fun usesEmailVerification(): Boolean = properties.useEmailVerification

    /**
     * 사용자를 만든다. 승인제이거나 이메일 인증을 쓰면 잠긴 상태로, 아니면 활성 상태로 만든다. 같은 주소의 인증 전 보조 이메일은 지우고,
     * 이메일 인증을 쓰면 인증 정보를 만들어 인증 메일 요청을 알린다.
     */
    fun create(form: SignupForm): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(
            User(
                loginId = form.loginId, name = HtmlSanitizer.defaultSanitize(form.name), email = form.email,
                passwordSalt = salt, password = LegacyPasswordHasher.hash(requireNotNull(form.password), salt),
                state = if (requiresAdminConfirm() || usesEmailVerification()) UserState.LOCKED else UserState.ACTIVE,
                createdDate = Date()
            )
        )
        form.email?.let { address -> emails.findByEmailIgnoreCaseAndValid(address, false).forEach { emails.delete(it) } }
        if (usesEmailVerification()) {
            val verification = newVerification(user)
            events.publishEvent(UserVerificationRequestedEvent(user, verification))
        }
        return user
    }

    /** 인증 정보를 만든다. 코드는 무작위 UUID이다 (v1.6 `UserVerification.newVerification`). */
    fun newVerification(user: User): UserVerification = verifications.save(
        UserVerification(user = user, loginId = user.loginId, verificationCode = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis())
    )

    /**
     * 인증 링크를 처리한다. 코드가 맞고 만든 지 하루가 지나지 않았으면 사용자를 활성화하고 인증 정보를 지운다.
     * 하루가 지난 인증 정보는 지운다.
     *
     * @return 인증된 로그인 ID. 올바르지 않으면 `null`
     */
    fun verify(loginId: String, code: String): String? {
        val verification = verifications.findFirstByLoginIdAndVerificationCode(loginId, code) ?: return null
        val timestamp = verification.timestamp ?: return null
        if (timestamp + ONE_DAY_MILLIS <= System.currentTimeMillis()) {
            verifications.delete(verification)
            return null
        }
        val user = users.findByLoginId(loginId) ?: return null
        user.state = UserState.ACTIVE
        users.save(user)
        verifications.delete(verification)
        return loginId
    }

    companion object {
        private const val ONE_DAY_MILLIS = 60L * 60 * 24 * 1000

        /** 이메일 형식이 Play `@Constraints.Email`과 같으면 `true`이다. */
        fun isValidEmailFormat(email: String): Boolean = EMAIL_PATTERN.matches(email)

        /** Play `@Constraints.Email`와 같은 정규식이다. */
        private val EMAIL_PATTERN = Regex("\\b[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*\\b")
    }
}
