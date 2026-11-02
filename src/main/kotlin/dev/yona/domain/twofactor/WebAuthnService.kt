package dev.yona.domain.twofactor

import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.CredentialRepository
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RegisteredCredential
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.ByteArray
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.RelyingPartyIdentity
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.UserVerificationRequirement
import dev.yona.config.YonaProperties
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.audit.auditUser
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import java.util.Optional

/**
 * WebAuthn이 쓰는 이 사이트의 신원이다. 브라우저가 보는 주소에서 정한다: [rpId]는 호스트 이름이고 [origin]은 `스킴://호스트[:포트]`이다. 자격 증명은 이 두 값에 묶이므로
 * 주소가 바뀌면(도메인 변경) 이미 등록한 보안 키는 쓸 수 없다.
 */
data class WebAuthnContext(val rpId: String, val origin: String)

/**
 * 시작한 요청이다. [server]는 서버가 세션에 두었다가 검증할 때 돌려주는 원문이고, [browser]는 브라우저의 `navigator.credentials`에 넘길 JSON이다. 둘은 모양이 다르다.
 */
data class WebAuthnChallenge(val server: String, val browser: String)

/** 보안 키 등록 결과이다. */
sealed interface WebAuthnRegistration {
    /** 등록했다. 2단계 인증을 처음 켠 것이면 [backupCodes]에 백업 코드를 한 번 담는다. */
    data class Registered(val backupCodes: List<String>?) : WebAuthnRegistration

    /** 검증에 실패했다(도전 값, 출처, 서명, 중복 등). */
    data object Rejected : WebAuthnRegistration
}

/**
 * 보안 키(WebAuthn, 패스키)를 2단계 인증의 한 수단으로 쓴다(추가 개발 항목 X-2, `decisions.md` D-69). 비밀번호를 대신하는 로그인이 아니라 **두 번째 단계로만** 쓴다.
 * 검증은 Yubico `java-webauthn-server`가 한다. 서버는 도전 값과 요청을 보관하지 않으므로(호출하는 쪽이 세션에 둔다) 이 서비스는 상태가 없다.
 * 서명 횟수가 줄어든 응답은 복제된 키를 의심해 거부한다. 사용자 식별자(user handle)는 사이트 비밀 키로 만든 값이라 사용자 번호가 밖으로 나가지 않는다.
 */
@Service
class WebAuthnService(
    private val credentials: TwoFactorCredentialRepository,
    private val users: UserRepository,
    private val twoFactor: TwoFactorService,
    private val crypto: TwoFactorCrypto,
    private val properties: YonaProperties,
    private val audit: AuditRecorder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 사용자에게 보안 키가 있으면 `true`이다. */
    @Transactional(readOnly = true)
    fun hasSecurityKey(user: User): Boolean = credentials.existsByUserIdAndTypeAndConfirmedTrue(requireNotNull(user.id), TwoFactorCredential.WEBAUTHN)

    /** 보안 키 등록 요청을 만든다. 호출하는 쪽이 [WebAuthnChallenge.server]를 세션에 두었다가 [finishRegistration]에 돌려줘야 한다. */
    @Transactional(readOnly = true)
    fun startRegistration(user: User, context: WebAuthnContext): WebAuthnChallenge {
        val identity = UserIdentity.builder().name(user.loginId.orEmpty()).displayName(user.name.orEmpty().ifBlank { user.loginId.orEmpty() }).id(handleOf(user)).build()
        val options = relyingParty(context).startRegistration(
            StartRegistrationOptions.builder().user(identity)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder().residentKey(ResidentKeyRequirement.DISCOURAGED).userVerification(UserVerificationRequirement.DISCOURAGED).build())
                .build()
        )
        return WebAuthnChallenge(options.toJson(), options.toCredentialsCreateJson())
    }

    /** 브라우저가 돌려준 등록 응답 [responseJson]을 [requestJson]에 대해 검증하고 저장한다. 실패는 사유를 로그에만 남기고 [WebAuthnRegistration.Rejected]이다. */
    @Transactional
    fun finishRegistration(user: User, name: String, requestJson: String, responseJson: String, context: WebAuthnContext, now: Instant = Instant.now()): WebAuthnRegistration {
        try {
            val request = PublicKeyCredentialCreationOptions.fromJson(requestJson)
            val result = relyingParty(context).finishRegistration(
                FinishRegistrationOptions.builder().request(request).response(PublicKeyCredential.parseRegistrationResponseJson(responseJson)).build()
            )
            val credentialId = result.keyId.id.base64Url
            if (credentials.findByCredentialId(credentialId).isNotEmpty()) return WebAuthnRegistration.Rejected
            val first = !twoFactor.isEnabled(user)
            credentials.save(
                TwoFactorCredential(
                    user = user, type = TwoFactorCredential.WEBAUTHN, name = name.trim().ifBlank { "Security key" }.take(100), confirmed = true,
                    credentialId = credentialId, publicKey = result.publicKeyCose.base64Url, signCount = result.signatureCount, createdDate = Date.from(now)
                )
            )
            val codes = if (first) twoFactor.ensureBackupCodes(user) else null
            audit.record(auditUser("two_factor.enabled", user, mapOf("method" to "webauthn")))
            return WebAuthnRegistration.Registered(codes)
        } catch (e: Exception) {
            log.info("보안 키 등록을 거부했다: {}", e.toString())
            return WebAuthnRegistration.Rejected
        }
    }

    /** 인증 요청을 만든다. 보안 키가 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun startAssertionOrNull(user: User, context: WebAuthnContext): WebAuthnChallenge? =
        if (!hasSecurityKey(user)) null else startAssertion(user, context)

    /** 인증 요청을 만든다. 호출하는 쪽이 [WebAuthnChallenge.server]를 세션에 두었다가 [finishAssertion]에 돌려줘야 한다. */
    @Transactional(readOnly = true)
    fun startAssertion(user: User, context: WebAuthnContext): WebAuthnChallenge {
        val request = relyingParty(context).startAssertion(StartAssertionOptions.builder().username(user.loginId.orEmpty()).userVerification(UserVerificationRequirement.DISCOURAGED).build())
        return WebAuthnChallenge(request.toJson(), request.toCredentialsGetJson())
    }

    /**
     * 인증 응답을 [requestJson]에 대해 검증한다. 맞고 **그 사용자의 키**이면 서명 횟수를 올리고 `true`이다. 서명 횟수가 줄었거나 도전 값, 출처, 서명이 맞지 않으면 `false`이다.
     */
    @Transactional
    fun finishAssertion(user: User, requestJson: String, responseJson: String, context: WebAuthnContext, now: Instant = Instant.now()): Boolean {
        try {
            val result = relyingParty(context).finishAssertion(
                FinishAssertionOptions.builder().request(AssertionRequest.fromJson(requestJson)).response(PublicKeyCredential.parseAssertionResponseJson(responseJson)).build()
            )
            if (!result.isSuccess || !result.isSignatureCounterValid) return false
            val stored = credentials.findByCredentialId(result.credential.credentialId.base64Url).firstOrNull { it.user?.id == user.id } ?: return false
            return credentials.updateSignCount(requireNotNull(stored.id), result.signatureCount, Date.from(now)) == 1
        } catch (e: Exception) {
            log.info("보안 키 인증을 거부했다: {}", e.message)
            return false
        }
    }

    private fun relyingParty(context: WebAuthnContext): RelyingParty = RelyingParty.builder()
        .identity(RelyingPartyIdentity.builder().id(context.rpId).name(properties.siteName).build())
        .credentialRepository(Repository())
        .origins(setOf(context.origin))
        .allowOriginPort(false)
        .allowOriginSubdomain(false)
        .build()

    /** 사용자 식별자이다. 사이트 비밀 키로 만든 32바이트라 사용자 번호를 담지 않는다. */
    private fun handleOf(user: User): ByteArray = ByteArray(crypto.userHandle(requireNotNull(user.id)))

    /** Yubico 라이브러리가 저장된 자격 증명을 읽는 길이다. 사용자 이름은 로그인 ID이다. */
    private inner class Repository : CredentialRepository {

        override fun getCredentialIdsForUsername(username: String): MutableSet<PublicKeyCredentialDescriptor> {
            val user = users.findByLoginId(username) ?: return mutableSetOf()
            return credentials.findByUserIdOrderById(requireNotNull(user.id)).filter { it.confirmed == true && it.type == TwoFactorCredential.WEBAUTHN }
                .map { PublicKeyCredentialDescriptor.builder().id(ByteArray.fromBase64Url(it.credentialId.orEmpty())).build() }.toMutableSet()
        }

        override fun getUserHandleForUsername(username: String): Optional<ByteArray> =
            Optional.ofNullable(users.findByLoginId(username)?.let { handleOf(it) })

        override fun getUsernameForUserHandle(userHandle: ByteArray): Optional<String> = Optional.empty()

        override fun lookup(credentialId: ByteArray, userHandle: ByteArray): Optional<RegisteredCredential> =
            Optional.ofNullable(credentials.findByCredentialId(credentialId.base64Url).firstOrNull { it.confirmed == true && it.user?.let { u -> handleOf(u) } == userHandle }?.let { registered(it, userHandle) })

        override fun lookupAll(credentialId: ByteArray): MutableSet<RegisteredCredential> =
            credentials.findByCredentialId(credentialId.base64Url).filter { it.confirmed == true }.mapNotNull { c -> c.user?.let { registered(c, handleOf(it)) } }.toMutableSet()

        private fun registered(c: TwoFactorCredential, handle: ByteArray) = RegisteredCredential.builder()
            .credentialId(ByteArray.fromBase64Url(c.credentialId.orEmpty())).userHandle(handle).publicKeyCose(ByteArray.fromBase64Url(c.publicKey.orEmpty())).signatureCount(c.signCount ?: 0).build()
    }
}
