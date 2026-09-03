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
            val resolvedUser = userRepository.findByEmail(userInfo.email).orElse(null)
                ?: userRepository.findByLoginId(userInfo.loginId).orElse(null)
                ?: run {
                    if (!EmailDomainValidator.isAllowed(userInfo.email, allowedEmailDomains)) {
                        throw OAuth2AuthenticationException(
                            OAuth2Error("unacceptable_email_domain"),
                            "허용되지 않은 이메일 도메인입니다: ${userInfo.email}"
                        )
                    }
                    userRepository.save(
                        User(
                            name = userInfo.name,
                            loginId = userInfo.loginId,
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

        val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))

        return YonaOAuth2User(user, attributes, authorities)
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
