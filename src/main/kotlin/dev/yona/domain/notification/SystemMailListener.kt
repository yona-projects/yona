package dev.yona.domain.notification

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 요청 즉시 보내는 시스템 메일의 요청 이벤트를 받아 [SystemMails]에 넘긴다. 요청한 사용자의 언어로 문구를 만들어야 하므로
 * 요청 스레드에서 동기로 받고, 실제 발송만 [MailDispatcher]가 비동기로 처리한다.
 */
@Component
class SystemMailListener(private val mails: SystemMails) {

    @EventListener
    fun onPasswordReset(event: PasswordResetRequestedEvent) = mails.passwordReset(event.user, event.token)

    @EventListener
    fun onUserVerification(event: UserVerificationRequestedEvent) = mails.signup(event.user, event.verification)

    @EventListener
    fun onEmailValidation(event: EmailValidationRequestedEvent) = mails.emailValidation(event.email, event.confirmPath)

    @EventListener
    fun onProjectTransfer(event: ProjectTransferRequestedEvent) = mails.projectTransfer(event.transfer)
}
