package dev.yona.domain.social

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.LinkedAccount
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.SignupForm
import dev.yona.domain.user.SignupService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserCredential
import dev.yona.domain.user.UserCredentialRepository
import dev.yona.domain.user.UserLookup
import dev.yona.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64

/**
 * 소셜 로그인 제공자가 알려 준 사용자이다. v1.6 play-authenticate의 `AuthUser`(`getProvider`, `getId`)와
 * `EmailIdentity`, `NameIdentity`를 합친 값이다.
 *
 * @property provider 제공자 키 (`github`, `google`)
 * @property providerUserId 제공자 쪽 사용자 ID
 * @property email 제공자가 알려 준 이메일. 알 수 없으면 `null`
 * @property name 제공자가 알려 준 이름
 * @property expires 소셜 로그인 세션이 끝나는 시각(UNIX 시간, 밀리초). 끝이 없으면 -1 (v1.6 `AuthUser.expires()`)
 */
data class SocialIdentity(val provider: String, val providerUserId: String, val email: String?, val name: String?, val expires: Long = -1)

/**
 * 소셜 로그인 처리의 결과이다.
 *
 * @property user 로그인시킬 사용자. 없으면 로그인시키지 않는다
 * @property flash 다음 화면에 한 번만 보일 안내(키: `warning`, `info`, `error`, 값: 메시지 키)
 * @property forceLogout 소셜 로그인 직후 곧바로 로그아웃으로 보낸다 (v1.6 `forceOAuthLogout`)
 */
data class SocialLoginResult(val user: User? = null, val flash: Map<String, String> = emptyMap(), val forceLogout: Boolean = false)

/**
 * 소셜 계정을 사용자(`User`)와 자격 증명(`UserCredential`)에 연결한다. v1.6 `service.YonaUserServicePlugin`
 * (`app/service/YonaUserServicePlugin.java`)과 `UserApp.createLocalUserWithOAuth`, `linkWithExistedOrCreateLocalUser`에 해당한다.
 *
 * 세션과 remember-me 쿠키는 다루지 않는다. 결과의 [SocialLoginResult.user]를 받아 호출한 쪽이 로그인시킨다.
 */
@Service
@Transactional
class SocialAccountService(
    private val credentials: UserCredentialRepository,
    private val linkedAccounts: LinkedAccountRepository,
    private val users: UserRepository,
    private val lookup: UserLookup,
    private val signup: SignupService,
    private val properties: YonaProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /** 이미 연결된 활성 자격 증명이 있으면 `true`이다 (v1.6 `UserCredential.existsByAuthUserIdentity`). */
    @Transactional(readOnly = true)
    fun isLinked(identity: SocialIdentity): Boolean = find(identity) != null

    private fun find(identity: SocialIdentity): UserCredential? =
        credentials.findActiveByProvider(identity.provider, identity.providerUserId).firstOrNull()

    /**
     * 소셜 로그인을 마치고 로그인시킬 사용자를 정한다. 이미 연결된 신원이면 연결된 사용자로(v1.6 `getLocalIdentity`),
     * 처음 보는 신원이면 가입시켜서(v1.6 `save`) 처리한다.
     */
    fun signIn(identity: SocialIdentity): SocialLoginResult {
        val credential = find(identity) ?: return signUp(identity)
        val flash = linkedMapOf<String, String>()
        var forceLogout = false
        var user = loginUserOf(credential, identity, flash)
        if (user == null) forceLogout = true
        if (properties.social.nameSync && credential.name != identity.name) {
            val synced = syncName(credential, identity)
            if (synced == null) {
                flash["warning"] = "user.locked"
                forceLogout = true
                user = null
            } else if (user != null) {
                user = synced
            }
        }
        if (user == null && !flash.containsKey("warning")) flash["warning"] = "user.locked"
        return SocialLoginResult(user, flash, forceLogout)
    }

    /** v1.6 `setStatusLoggedIn`: 소셜 이메일, 자격 증명 이메일, 연결된 사용자 순으로 로컬 사용자를 찾아 로그인 대상을 정한다. */
    private fun loginUserOf(credential: UserCredential, identity: SocialIdentity, flash: MutableMap<String, String>): User? {
        var local = lookup.findByEmail(identity.email)
        if (local == null) {
            local = lookup.findByEmail(credential.email) ?: credential.user?.id?.let { users.findById(it).orElse(null) }
        }
        val willLogin = if (local == null && credential.loginId == null) {
            createLocalUser(credential, flash)
        } else {
            if (local != null && credential.loginId == null) {
                credential.loginId = local.loginId
                credential.user = local
                credentials.save(credential)
            }
            local
        }
        return willLogin?.takeIf { it.state == UserState.ACTIVE }
    }

    /** v1.6 `updateLocalUserName`: 자격 증명과 활성 사용자의 이름을 소셜 계정의 이름으로 고친다. 사용자를 고치지 못하면 `null`이다. */
    private fun syncName(credential: UserCredential, identity: SocialIdentity): User? {
        credential.name = identity.name
        credentials.save(credential)
        val local = lookup.findByEmail(identity.email)
        if (local == null || local.state != UserState.ACTIVE) return null
        local.name = identity.name
        return users.save(local)
    }

    /** v1.6 `YonaUserServicePlugin.save`: 자격 증명을 만들고, 같은 이메일의 사용자가 없으면 가입시킨다. */
    private fun signUp(identity: SocialIdentity): SocialLoginResult {
        val credential = credentials.save(UserCredential(active = true, email = identity.email, emailValidated = true, name = identity.name))
        linkedAccounts.save(LinkedAccount(userCredential = credential, providerKey = identity.provider, providerUserId = identity.providerUserId))
        val flash = linkedMapOf<String, String>()
        val existing = lookup.findByEmail(credential.email)
        val user = existing ?: createLocalUser(credential, flash)
        val forceLogout = existing == null && user == null
        return SocialLoginResult(user?.takeIf { it.state == UserState.ACTIVE }, flash, forceLogout)
    }

    /**
     * 소셜 자격 증명으로 사용자를 만든다(v1.6 `createLocalUserWithOAuth`). 이메일을 모르거나 허용된 도메인이 아니면
     * 자격 증명을 지우고 `null`을 돌려주며, 안내를 [flash]에 남긴다. 이메일은 소셜 제공자가 확인한 것으로 보고 활성 상태로 만든다.
     */
    private fun createLocalUser(credential: UserCredential, flash: MutableMap<String, String>): User? {
        val email = credential.email
        if (email == null || email.equals("null", ignoreCase = true)) {
            flash["error"] = "app.warn.cannot.access.email.information"
            log.error("Cannot confirm email address of {}: {}", credential.id, credential.name)
            discard(credential)
            return null
        }
        if (!signup.isAllowedEmailDomain(email)) {
            flash["info"] = "user.unacceptable.email.domain"
            log.warn("Signup rejected: {} with {}", credential.name, email)
            discard(credential)
            return null
        }
        val created = signup.create(SignupForm(signup.generateLoginId(email.substringBefore('@')), credential.name, email, randomPassword()))
        created.state = UserState.ACTIVE
        users.save(created)
        credential.loginId = created.loginId
        credential.user = created
        credentials.save(credential)
        return created
    }

    private fun discard(credential: UserCredential) {
        linkedAccounts.deleteAll(linkedAccounts.findByUserCredentialId(requireNotNull(credential.id)))
        credentials.delete(credential)
        credentials.flush()
    }

    private fun randomPassword(): String = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })

    /** 로그인한 소셜 신원([old])의 자격 증명에 새 신원([new])을 연결한다 (v1.6 `UserCredential.addLinkedAccount`). */
    fun link(old: SocialIdentity, new: SocialIdentity) {
        val credential = find(old) ?: return
        linkedAccounts.save(LinkedAccount(userCredential = credential, providerKey = new.provider, providerUserId = new.providerUserId))
    }

    /**
     * 소셜 세션이 있는 채로 로그인 화면에 왔을 때 사용자를 이어받는다 (v1.6 `UserApp.linkWithExistedOrCreateLocalUser`).
     * 연결된 사용자가 없으면 만들지만 이번에는 로그인시키지 않는다(v1.6은 세션을 만들지 않는다). 잠긴 사용자도 로그인시키지 않는다.
     */
    fun resume(identity: SocialIdentity): SocialLoginResult {
        val credential = find(identity) ?: return SocialLoginResult()
        val user = if (credential.loginId == null) lookup.findByEmail(credential.email) else users.findByLoginId(credential.loginId!!)
        if (user == null) {
            val flash = linkedMapOf<String, String>()
            createLocalUser(credential, flash)
            return SocialLoginResult(null, flash, forceLogout = false)
        }
        if (credential.loginId == null) {
            credential.loginId = user.loginId
            credential.user = user
            credentials.save(credential)
        }
        return SocialLoginResult(user.takeIf { it.state != UserState.LOCKED })
    }
}
