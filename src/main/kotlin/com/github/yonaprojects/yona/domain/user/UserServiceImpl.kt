package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.mail.MailService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserServiceImpl(
    private val userRepository: UserRepository,
    private val emailRepository: EmailRepository,
    private val userVerificationRepository: UserVerificationRepository,
    private val mailService: MailService
) : UserService {

    override fun findByLoginId(loginId: String): User? {
        return userRepository.findByLoginId(loginId).orElse(null)
    }

    override fun findByEmail(email: String): User? {
        // 메인 이메일로 검색 시도
        val mainUser = userRepository.findByEmail(email).orElse(null)
        if (mainUser != null) {
            return mainUser
        }
        // 보조 이메일(인증된 것)로 검색 시도
        val subEmail = emailRepository.findByEmailAndValid(email, true)
        return subEmail?.user
    }

    @Transactional
    override fun createUser(user: User): User {
        user.createdDate = Instant.now()
        user.name = user.name.trim()
        return userRepository.save(user)
    }

    override fun isLoginIdExist(loginId: String): Boolean {
        return userRepository.findByLoginId(loginId).isPresent
    }

    override fun isEmailExist(email: String): Boolean {
        if (userRepository.findByEmail(email).isPresent) {
            return true
        }
        return emailRepository.existsByEmailAndValid(email, true)
    }

    @Transactional
    override fun addEmail(userId: Long, newEmail: String): Email {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }
        
        if (isEmailExist(newEmail) || user.has(newEmail)) {
            throw IllegalArgumentException("이미 등록되었거나 등록 대기 중인 이메일입니다.")
        }

        val email = Email(
            user = user,
            email = newEmail,
            valid = false
        )
        user.addEmail(email)
        return emailRepository.save(email)
    }

    @Transactional
    override fun deleteEmail(userId: Long, emailId: Long) {
        val email = emailRepository.findById(emailId).orElseThrow { IllegalArgumentException("이메일을 찾을 수 없습니다.") }
        if (email.user.id != userId) {
            throw IllegalArgumentException("삭제 권한이 없습니다.")
        }
        emailRepository.delete(email)
    }

    @Transactional
    override fun sendValidationEmail(userId: Long, emailId: Long, serverUrl: String) {
        val email = emailRepository.findById(emailId).orElseThrow { IllegalArgumentException("이메일을 찾을 수 없습니다.") }
        if (email.user.id != userId) {
            throw IllegalArgumentException("메일을 보낼 권한이 없습니다.")
        }

        val token = (1..50).map { ('0'..'9').random() }.joinToString("")
        email.token = token
        emailRepository.save(email)

        val confirmUrl = "$serverUrl/user/emails/$emailId/confirm?token=$token"
        val htmlContent = """
            <h3>[Yona] 이메일 인증 요청</h3>
            <p>아래 링크를 클릭하여 이메일 주소를 인증해 주세요:</p>
            <p><a href="$confirmUrl">$confirmUrl</a></p>
        """.trimIndent()

        mailService.sendHtmlMail(email.email, email.user.name, "[Yona] 보조 이메일 주소 인증", htmlContent)
    }

    @Transactional
    override fun confirmEmail(emailId: Long, token: String): Boolean {
        val email = emailRepository.findById(emailId).orElse(null) ?: return false
        if (email.validate(token)) {
            emailRepository.save(email)
            // 다른 가입대기 무효 메일 정리
            val invalidEmails = emailRepository.findByEmailAndValidFalse(email.email)
            emailRepository.deleteAll(invalidEmails)
            return true
        }
        return false
    }

    @Transactional
    override fun setAsMainEmail(userId: Long, emailId: Long) {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다.") }
        val email = emailRepository.findById(emailId).orElseThrow { IllegalArgumentException("이메일을 찾을 수 없습니다.") }
        
        if (email.user.id != userId) {
            throw IllegalArgumentException("변경 권한이 없습니다.")
        }

        val oldMainEmail = user.email
        user.email = email.email
        user.removeEmail(email)
        userRepository.save(user)

        // 기존 메인을 서브로 보관
        val newSubEmail = Email(
            user = user,
            email = oldMainEmail,
            valid = true
        )
        user.addEmail(newSubEmail)
        emailRepository.save(newSubEmail)
        
        // 기존 서브 메일 엔티티는 삭제
        emailRepository.delete(email)
    }

    @Transactional
    override fun verifyUser(loginId: String, verificationCode: String): Boolean {
        val verification = userVerificationRepository.findByLoginIdAndVerificationCode(loginId, verificationCode) 
            ?: return false

        if (verification.isValidDate()) {
            val user = verification.user
            user.state = UserState.ACTIVE
            userRepository.save(user)
            userVerificationRepository.delete(verification)
            return true
        } else {
            userVerificationRepository.delete(verification)
            return false
        }
    }

    @Transactional
    override fun createVerification(user: User): UserVerification {
        val existing = userVerificationRepository.findByUser(user)
        if (existing != null) {
            userVerificationRepository.delete(existing)
        }

        val verification = UserVerification(
            user = user,
            loginId = user.loginId,
            verificationCode = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis()
        )
        return userVerificationRepository.save(verification)
    }

    @Transactional
    override fun sendVerificationEmail(user: User, serverUrl: String) {
        val verification = createVerification(user)
        val verifyUrl = "$serverUrl/user/verify?loginId=${user.loginId}&code=${verification.verificationCode}"
        val htmlContent = """
            <h3>[Yona] 회원가입 이메일 인증</h3>
            <p>아래 가입 인증 링크를 클릭하여 계정을 활성화해 주세요:</p>
            <p><a href="$verifyUrl">$verifyUrl</a></p>
        """.trimIndent()

        mailService.sendHtmlMail(user.email, user.name, "[Yona] 회원가입 계정 활성화 인증", htmlContent)
    }
}
