package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.user.Saml2UserProvisioningService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication

// yona-wiki P3-06 Step3 — 실제 OpenSAML 어서션 파싱(XML/서명 검증)은 Spring Security의 기본
// responseAuthenticationConverter(delegate)에 위임하고, 그 결과(Saml2Authentication)를 받아 JIT
// 프로비저닝하는 부분만 순수 유닛테스트로 검증한다. delegate를 직접 호출하는 convert()가 아니라
// buildAuthentication()을 직접 테스트해 OpenSAML의 ResponseToken(실제 SAML Response XML 필요)을
// 만들 필요가 없게 했다.
class EnterpriseSaml2ResponseAuthenticationConverterSpec : DescribeSpec({
    val provisioningService = mockk<Saml2UserProvisioningService>()

    describe("EnterpriseSaml2ResponseAuthenticationConverter.buildAuthentication") {
        it("기본 컨버터가 만든 Saml2Authentication의 principal로 JIT 프로비저닝하고 로컬 User가 담긴 principal로 교체해야 한다") {
            val converter = EnterpriseSaml2ResponseAuthenticationConverter(
                provisioningService, "email", "displayName"
            )
            val originalPrincipal = DefaultSaml2AuthenticatedPrincipal(
                "gildong@example.com",
                mapOf("email" to listOf("gildong@example.com"), "displayName" to listOf("홍길동"))
            )
            val defaultAuthentication = Saml2Authentication(originalPrincipal, "raw-saml-response", emptyList())

            val localUser = User(
                id = 11L, loginId = "gildong", name = "홍길동", email = "gildong@example.com",
                state = UserState.ACTIVE
            )
            every {
                provisioningService.reconcile(originalPrincipal, "email", "displayName")
            } returns localUser

            val result = converter.buildAuthentication(defaultAuthentication)

            val principal = result.principal as YonaSaml2AuthenticatedPrincipal
            principal.user.id shouldBe 11L
            result.name shouldBe "gildong"
            result.authorities.map { it.authority } shouldBe listOf("ROLE_ACTIVE")
            result.saml2Response shouldBe "raw-saml-response"
        }
    }
})
