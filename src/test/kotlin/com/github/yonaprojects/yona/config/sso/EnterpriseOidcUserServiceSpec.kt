package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.user.OidcUserProvisioningService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import java.time.Instant

// yona-wiki P3-06 Step2 — 소셜 로그인(CustomOAuth2UserService)의 링크/병합 흐름을 타지 않고, LDAP과
// 동일하게 곧바로 로컬 계정으로 이어지는지 검증. delegate(OidcUserService)를 주입 가능하게 해
// 실제 IdP userinfo 엔드포인트 호출 없이 단위테스트한다(CustomOAuth2UserServiceSpec과 동일한 패턴).
class EnterpriseOidcUserServiceSpec : DescribeSpec({
    val provisioningService = mockk<OidcUserProvisioningService>()
    val delegate = mockk<OidcUserService>()
    val service = EnterpriseOidcUserService(provisioningService, delegate)

    describe("EnterpriseOidcUserService.loadUser") {
        it("OIDC 로그인 성공 시 JIT 프로비저닝된 로컬 User를 principal에 담고 loginId를 name으로 반환해야 한다") {
            val userRequest = mockk<OidcUserRequest>()
            val idToken = OidcIdToken(
                "token-value", Instant.now(), Instant.now().plusSeconds(3600),
                mapOf("sub" to "abc123", "email" to "gildong@example.com", "name" to "홍길동")
            )
            val delegateResult = DefaultOidcUser(emptyList(), idToken)
            every { delegate.loadUser(userRequest) } returns delegateResult

            val localUser = User(
                id = 10L, loginId = "gildong", name = "홍길동", email = "gildong@example.com",
                state = UserState.ACTIVE
            )
            every { provisioningService.reconcile(delegateResult) } returns localUser

            val result = service.loadUser(userRequest) as YonaOidcUser

            result.user.id shouldBe 10L
            result.name shouldBe "gildong"
            result.authorities.map { it.authority } shouldBe listOf("ROLE_ACTIVE")
        }
    }
})
