package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.user.OidcUserProvisioningService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step2 — `config/oauth2/CustomOAuth2UserService`(소셜 로그인,
 * link/merge 흐름)와는 완전히 분리된 별도 서비스. `.oauth2Login { it.userInfoEndpoint {
 * .userService(customOAuth2UserService).oidcUserService(enterpriseOidcUserService) } }`로 등록해
 * "openid" 스코프를 포함한 등록(엔터프라이즈 OIDC)만 이쪽으로 라우팅되도록 한다 — Spring Security의
 * OAuth2LoginConfigurer는 ClientRegistration의 scope에 "openid"가 있으면 OidcUserService 경로를,
 * 없으면(google/github는 scope=profile,email) userInfoEndpoint().userService() 경로를 탄다. 즉
 * registrationId로 분기할 필요 없이 두 서비스가 스코프 기준으로 자동 분리된다.
 *
 * `OidcUserService`를 상속하지 않고 `OAuth2UserService<OidcUserRequest, OidcUser>` 인터페이스만
 * 구현한다 — `oidcUserService()` 설정 메서드가 요구하는 타입은 이 인터페이스뿐이라 상속이 불필요할
 * 뿐 아니라, 상속했을 때 `delegate: OidcUserService` 생성자 파라미터가 이 빈 자신과 타입이
 * 겹쳐(자기 자신도 OidcUserService의 하위타입이 됨) Spring이 자기참조 순환으로 오인해 컨텍스트
 * 기동에 실패했다(실측 확인: BeanCurrentlyInCreationException).
 */
@Service
class EnterpriseOidcUserService(
    private val oidcUserProvisioningService: OidcUserProvisioningService,
    private val delegate: OidcUserService = OidcUserService()
) : OAuth2UserService<OidcUserRequest, OidcUser> {

    @Transactional
    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = delegate.loadUser(userRequest)
        val user = oidcUserProvisioningService.reconcile(oidcUser)
        val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))
        return YonaOidcUser(user, oidcUser.idToken, oidcUser.userInfo, authorities)
    }
}
