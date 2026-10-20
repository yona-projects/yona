package com.github.yonaprojects.yona.config.oauth2

import com.github.yonaprojects.yona.domain.user.EmailDomainValidator
import com.github.yonaprojects.yona.domain.user.LinkedAccount
import com.github.yonaprojects.yona.domain.user.LinkedAccountRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * yona의 models/LinkedAccount.java + UserCredential.java(다중 provider 연동) 대응.
 * yona의 UserCredential 계층은 play-authenticate 플러그인 산물이라 이식하지 않고,
 * User에 직접 LinkedAccount(providerKey+providerUserId)를 연결하는 단순한 구조로
 * 동일한 핵심 기능(여러 OAuth 제공자를 한 계정에 연결)을 구현했다.
 */
@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
    private val linkedAccountRepository: LinkedAccountRepository,
    private val accountMergeService: OAuth2AccountMergeService,
    private val delegate: DefaultOAuth2UserService = DefaultOAuth2UserService(),
    @Value("\${yona.signup.allowed-email-domains:}")
    private val allowedEmailDomains: String = ""
) : OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = delegate.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId
        val attributes = oAuth2User.attributes

        val userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, attributes)

        // yona YonaUserServicePlugin.link()/merge() 훅 대응 — 이미 로그인 중인 세션이 있으면(비로그인
        // 상태에서의 일반 로그인/자동가입과는 완전히 다른 분기다) 그 계정을 최우선으로 취급한다.
        val currentUser = currentlyLoggedInUser()

        // 1) 이미 이 provider+providerUserId로 연결된 계정이 있으면 그대로 사용 (가장 신뢰할 수 있는 매칭)
        val existingLink = linkedAccountRepository
            .findByProviderKeyAndProviderUserId(registrationId, userInfo.id)
            .orElse(null)

        val user = if (existingLink != null) {
            // yona YonaUserServicePlugin.merge(newUser, oldUser) 대응 — 로그인 중인 사용자와 이 provider가
            // 이미 연결된 사용자가 서로 다르면, yona가 Global.askMerge()를 null로 두어(확인 절차 없이)
            // 자동 병합을 수행하는 것과 동일하게 즉시 병합한다. 살아남는 쪽은 현재 로그인 세션(oldUser),
            // 이 provider에 이미 연결돼 있던 별도 계정(newUser)은 병합되어 잠긴다.
            if (currentUser != null && currentUser.id != existingLink.user.id) {
                accountMergeService.merge(keepUserId = currentUser.id!!, otherUserId = existingLink.user.id!!)
                currentUser
            } else {
                existingLink.user
            }
        } else if (currentUser != null) {
            // yona YonaUserServicePlugin.link(oldUser, newUser) 대응 — 로그인 중인 사용자가 처음 보는
            // provider로 인증하면 이메일/loginId 매칭을 거치지 않고 곧바로 현재 계정에 연결한다.
            linkedAccountRepository.save(
                LinkedAccount(user = currentUser, providerKey = registrationId, providerUserId = userInfo.id)
            )
            currentUser
        } else {
            // 2) 로그인하지 않은 상태에서 처음 보는 provider 계정이면 이메일/loginId로 기존 가입 사용자를
            //    찾아 "연결"하거나, 없으면 신규 가입 처리 (신규 가입만 이메일 도메인 allowlist 적용 -
            //    yona와 동일하게 이미 존재하는 계정의 로그인은 도메인 정책 변경 후에도 계속 허용한다)
            // v1.6 YonaUserServicePlugin.save()/setStatusLoggedIn()은 이메일(User.findByEmail)로만 기존 계정을 찾았다.
            // 제공자가 알려준 loginId가 로컬 계정과 같다는 이유만으로 연결하면 같은 이름의 소셜 계정으로 로컬 계정을
            // 탈취할 수 있으므로 loginId 일치는 연결 근거로 쓰지 않는다.
            // v1.6 UserApp.createLocalUserWithOAuth: 이메일을 알 수 없으면(null 또는 "null") 계정을 만들지 않고 로그인을 거부했다.
            // 빈 이메일로 findByEmail 하면 이메일 없는 다른 소셜 계정들이 서로 같은 로컬 계정에 연결된다.
            if (userInfo.email.isBlank() || userInfo.email.equals("null", ignoreCase = true)) {
                throw OAuth2AuthenticationException(
                    OAuth2Error("email_not_provided"),
                    "소셜 계정에서 이메일 정보를 가져올 수 없습니다. 이메일을 공개하거나 다른 방법으로 가입해 주세요."
                )
            }
            val resolvedUser = userRepository.findByEmail(userInfo.email).orElse(null)
                ?.also { ensureLoginAllowed(it) }
                ?: run {
                    if (!EmailDomainValidator.isAllowed(userInfo.email, allowedEmailDomains)) {
                        throw OAuth2AuthenticationException(
                            OAuth2Error("unacceptable_email_domain"),
                            "허용되지 않은 이메일 도메인입니다: ${userInfo.email}"
                        )
                    }
                    userRepository.save(
                        User(
                            name = com.github.yonaprojects.yona.domain.support.HtmlUtil.defaultSanitize(userInfo.name),
                            loginId = uniqueLoginId(userInfo.loginId),
                            email = userInfo.email,
                            state = UserState.ACTIVE,
                            createdDate = Instant.now()
                        )
                    )
                }

            linkedAccountRepository.save(
                LinkedAccount(user = resolvedUser, providerKey = registrationId, providerUserId = userInfo.id)
            )
            resolvedUser
        }

        // v1.6 setStatusLoggedIn(): ACTIVE가 아니면 user.locked 경고 후 OAuth 로그아웃 처리했다.
        ensureLoginAllowed(user)

        val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))

        return YonaOAuth2User(user, attributes, authorities)
    }

    private fun ensureLoginAllowed(user: User) {
        if (user.state == UserState.LOCKED || user.state == UserState.DELETED) {
            throw OAuth2AuthenticationException(OAuth2Error("user_locked"), "잠겼거나 탈퇴한 계정입니다: ${user.loginId}")
        }
    }

    // 제공자의 loginId가 이미 쓰이고 있으면(다른 사람의 로컬 계정) 숫자 접미사를 붙여 새 계정의 loginId를 정한다.
    private fun uniqueLoginId(base: String): String {
        if (userRepository.findByLoginId(base).isEmpty) return base
        var suffix = 1
        while (userRepository.findByLoginId("$base$suffix").isPresent) suffix++
        return "$base$suffix"
    }

    // OAuth2 콜백 처리는 이 요청 스레드의 SecurityContext에 이전 로그인 세션이 그대로 남아있는 시점에
    // 실행된다(Spring Security가 새 Authentication으로 교체하는 건 loadUser()가 반환한 뒤) — yona가
    // play-authenticate의 "현재 로그인한 AuthUser" 개념으로 link()/merge() 분기를 타는 것과 동일한 지점.
    private fun currentlyLoggedInUser(): User? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        if (!authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return null
        }
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }
}
