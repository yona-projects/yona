package dev.yona.domain.user

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.notification.EmailValidationRequestedEvent
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.util.HtmlSanitizer
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64

/**
 * 계정 설정이다. v1.6 `UserApp`의 `editUserInfo`, `resetUserPassword`, `resetUserVisitedList`, 토큰, 보조 이메일 관리에 해당한다.
 *
 * 거부 이유는 사용자에게 보여 줄 메시지 키(`String`)로 돌려주고, 성공이면 `null`이다.
 */
@Service
@Transactional
class AccountService(
    private val users: UserRepository,
    private val emails: EmailRepository,
    private val attachmentRepository: AttachmentRepository,
    private val attachments: AttachmentService,
    private val recentProjects: RecentProjectRepository,
    private val signup: SignupService,
    private val events: ApplicationEventPublisher
) {

    private val random = SecureRandom()

    // --- 프로필 ---

    /**
     * 이름과 이메일, 아바타를 저장한다. 이메일이 비었으면 `user.wrongEmail.alert`, 바꾸는 이메일이 이미 쓰이고 있으면
     * `user.email.duplicate`로 거부한다. 이메일 형식은 검사하지 않는다(v1.6).
     *
     * 아바타는 [avatarId]의 첨부가 **본인이 올린 임시 첨부**이고 이미지일 때만 바꾼다. 이전 아바타는 지운다.
     * 첨부가 1MB를 넘어도 v1.6처럼 거부하지 않고 옮긴다([refactoring-backlog.md] R-21).
     */
    fun updateProfile(user: User, name: String?, email: String?, avatarId: String?): String? {
        if (email.isNullOrEmpty() || !SignupService.isValidEmailFormat(email)) return "user.wrongEmail.alert"
        if (user.email != email && signup.isEmailExist(email)) return "user.email.duplicate"

        user.email = email
        user.name = HtmlSanitizer.defaultSanitize(HtmlSanitizer.defaultSanitize(name))

        avatarId?.toLongOrNull()?.let { id ->
            val attachment = attachmentRepository.findById(id).orElse(null)
            if (attachment != null && attachment.containerType == ResourceType.USER && attachment.containerId == user.id &&
                attachment.mimeType?.substringBefore('/')?.lowercase() == "image"
            ) {
                attachments.deleteAll(ResourceType.USER_AVATAR, requireNotNull(user.id))
                attachments.moveTo(attachment, ResourceType.USER_AVATAR, requireNotNull(user.id))
            }
        }

        emails.findByEmailIgnoreCaseAndValid(email, false).forEach { emails.delete(it) }
        users.save(user)
        return null
    }

    // --- 비밀번호 ---

    /** 현재 비밀번호가 맞으면 `true`이다. */
    @Transactional(readOnly = true)
    fun isValidPassword(user: User, password: String): Boolean =
        user.password == LegacyPasswordHasher.hash(password, requireNotNull(user.passwordSalt))

    /** 비밀번호를 바꾼다. 소금은 그대로 쓴다. */
    fun resetPassword(user: User, newPassword: String) {
        user.password = LegacyPasswordHasher.hash(newPassword, requireNotNull(user.passwordSalt))
        users.save(user)
    }

    // --- 토큰 ---

    /** 사용자 토큰이 없으면 만든다. [recreate]이면 먼저 지우고 새로 만든다. 토큰은 무작위 32바이트의 Base64(44자)이다. */
    fun token(user: User, recreate: Boolean): String {
        if (recreate) user.token = null
        if (user.token.isNullOrEmpty()) {
            user.token = Base64.getEncoder().encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))
            users.save(user)
        }
        return requireNotNull(user.token)
    }

    // --- 방문 기록 ---

    /** 최근 방문한 프로젝트 기록을 모두 지운다. */
    fun resetVisitedProjects(user: User) {
        user.id?.let { recentProjects.deleteByUserId(it) }
    }

    // --- 보조 이메일 ---

    /** 사용자의 보조 이메일이다. 입력 순서(번호 순)이다. */
    @Transactional(readOnly = true)
    fun subEmails(user: User): List<Email> = emails.findByUserIdOrderById(requireNotNull(user.id))

    /**
     * 보조 이메일을 인증 전 상태로 추가한다. 형식이 틀리면 `error.email`, 비었으면 `error.required`, 이미 쓰이고 있거나
     * 본인의 보조 이메일이면 `user.email.duplicate`이다.
     */
    fun addEmail(user: User, address: String?): String? {
        if (address.isNullOrEmpty()) return "error.required"
        if (!SignupService.isValidEmailFormat(address)) return "error.email"
        val mine = emails.findByUserIdOrderById(requireNotNull(user.id)).any { it.email == address }
        if (signup.isEmailExist(address) || mine) return "user.email.duplicate"
        emails.save(Email(user = user, email = address, valid = false))
        return null
    }

    /** 보조 이메일을 지운다. */
    fun deleteEmail(email: Email) {
        emails.delete(email)
    }

    /** 확인용 숫자 50자 토큰을 만들어 저장하고 확인 메일 요청을 알린다. */
    fun requestValidationEmail(email: Email) {
        email.token = (1..VALIDATION_TOKEN_LENGTH).map { ('0' + random.nextInt(DIGITS)) }.joinToString("")
        emails.save(email)
        events.publishEvent(EmailValidationRequestedEvent(email, "/user/email/confirm/${email.id}/${email.token}"))
    }

    /** 토큰이 맞으면 보조 이메일을 인증 상태로 바꾸고 같은 주소의 다른 인증 전 행을 지운다. 틀리면 `false`이다. */
    fun confirmEmail(email: Email, token: String): Boolean {
        if (email.token != token) return false
        email.valid = true
        emails.save(email)
        emails.flush()
        emails.findByEmailIgnoreCaseAndValid(requireNotNull(email.email), false).forEach { emails.delete(it) }
        return true
    }

    /** 보조 이메일을 대표 이메일로 바꾼다. 이전 대표 이메일은 인증된 보조 이메일이 된다. 인증 여부는 보지 않는다. */
    fun setAsMainEmail(user: User, email: Email) {
        val oldMain = user.email
        user.email = email.email
        emails.delete(email)
        emails.flush()
        users.save(user)
        emails.save(Email(user = user, email = oldMain, valid = true))
    }

    private companion object {
        const val TOKEN_BYTES = 32
        const val VALIDATION_TOKEN_LENGTH = 50
        const val DIGITS = 10
    }
}
