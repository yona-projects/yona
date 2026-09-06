package com.github.yonaprojects.yona.domain.oauth2server

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

// yona-wiki P3-07(MCP 서버) Step6 — "Authorized OAuth Apps" 화면의 데이터 소스 서비스. 동의(consent)
// 레코드를 등록된 클라이언트 정보와 조합해 화면에 필요한 뷰로 변환하는 로직과, 취소(revoke) 시
// 동의 레코드뿐 아니라 이미 발급된 토큰(OAuth2Authorization)까지 함께 지워야 한다는 것을 검증한다.
class OAuthAuthorizedAppsServiceSpec : DescribeSpec({
    val consentRepository = mockk<OAuthAuthorizationConsentRepository>()
    val clientRepository = mockk<OAuthRegisteredClientRepository>()
    val authorizationRepository = mockk<OAuthAuthorizationRepository>()
    val service = OAuthAuthorizedAppsService(consentRepository, clientRepository, authorizationRepository)

    beforeTest {
        clearMocks(consentRepository, clientRepository, authorizationRepository)
    }

    describe("listAuthorizedApps") {
        it("동의 레코드를 등록된 클라이언트 정보와 조합해 뷰로 변환해야 한다") {
            val consent = OAuthAuthorizationConsent(
                id = "client-pk-1:testuser",
                registeredClientId = "client-pk-1",
                principalName = "testuser",
                authorities = "SCOPE_issues:read,SCOPE_issues:write"
            )
            val client = OAuthRegisteredClient(
                id = "client-pk-1",
                clientId = "public-client-id-1",
                clientName = "Claude Code",
                clientAuthenticationMethods = "none",
                authorizationGrantTypes = "authorization_code,refresh_token",
                scopes = McpOAuthScopes.ALL.joinToString(","),
                dynamicallyRegistered = true
            )
            every { consentRepository.findByPrincipalName("testuser") } returns listOf(consent)
            every { clientRepository.findById("client-pk-1") } returns Optional.of(client)

            val result = service.listAuthorizedApps("testuser")

            result.size shouldBe 1
            result[0].clientId shouldBe "public-client-id-1"
            result[0].clientName shouldBe "Claude Code"
            result[0].scopes shouldBe listOf("issues:read", "issues:write")
            result[0].dynamicallyRegistered shouldBe true
        }

        it("동의 레코드가 가리키는 클라이언트가 이미 삭제됐으면 그 항목을 건너뛰어야 한다") {
            val consent = OAuthAuthorizationConsent(
                id = "missing-client:testuser",
                registeredClientId = "missing-client",
                principalName = "testuser",
                authorities = "SCOPE_issues:read"
            )
            every { consentRepository.findByPrincipalName("testuser") } returns listOf(consent)
            every { clientRepository.findById("missing-client") } returns Optional.empty()

            service.listAuthorizedApps("testuser") shouldBe emptyList()
        }
    }

    describe("revoke") {
        it("클라이언트를 찾으면 동의 레코드와 토큰 레코드를 모두 지워야 한다") {
            val client = OAuthRegisteredClient(
                id = "client-pk-1",
                clientId = "public-client-id-1",
                clientName = "Claude Code",
                clientAuthenticationMethods = "none",
                authorizationGrantTypes = "authorization_code",
                scopes = McpOAuthScopes.ALL.joinToString(",")
            )
            every { clientRepository.findByClientId("public-client-id-1") } returns Optional.of(client)
            every { consentRepository.deleteByRegisteredClientIdAndPrincipalName("client-pk-1", "testuser") } just Runs
            every { authorizationRepository.deleteByRegisteredClientIdAndPrincipalName("client-pk-1", "testuser") } just Runs

            service.revoke("testuser", "public-client-id-1")

            verify(exactly = 1) { consentRepository.deleteByRegisteredClientIdAndPrincipalName("client-pk-1", "testuser") }
            verify(exactly = 1) { authorizationRepository.deleteByRegisteredClientIdAndPrincipalName("client-pk-1", "testuser") }
        }

        it("존재하지 않는 clientId면 아무것도 지우지 않아야 한다") {
            every { clientRepository.findByClientId("no-such-client") } returns Optional.empty()

            service.revoke("testuser", "no-such-client")

            verify(exactly = 0) { consentRepository.deleteByRegisteredClientIdAndPrincipalName(any(), any()) }
            verify(exactly = 0) { authorizationRepository.deleteByRegisteredClientIdAndPrincipalName(any(), any()) }
        }
    }
})
