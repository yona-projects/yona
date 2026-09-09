package com.github.yonaprojects.yona.config.webauthn

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.twofactor.WebauthnCredentialRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.data.client.Origin
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor
import com.webauthn4j.test.client.ClientPlatform
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse
import org.springframework.security.web.webauthn.api.AuthenticatorTransport
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.ImmutableAuthenticationExtensionsClientOutputs
import org.springframework.security.web.webauthn.api.PublicKeyCredential
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest
import org.springframework.security.web.webauthn.management.PublicKeyCredentialCreationOptionsRequest
import org.springframework.security.web.webauthn.management.PublicKeyCredentialRequestOptionsRequest
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations

// 브라우저 없이는 실제 navigator.credentials.create()/get() 세리모니를 curl로 재현할 수 없다 —
// 대신 WebAuthn4J의 가상 인증기(webauthn4j-test, ClientPlatform)로 실제 attestation/assertion
// object를 프로그래밍적으로 만들어, Spring Security의 Webauthn4JRelyingPartyOperations가 실제로
// 서명을 검증하고 우리 JPA 어댑터(WebauthnUserCredentialRepositoryAdapter)에 왕복 저장하는
// 전체 경로를 실제 Spring 컨텍스트 위에서 태운다. 두 라이브러리의 모델 객체가 서로 달라
// (webauthn4j core vs spring-security-webauthn api) 필드 단위로 직접 변환한다.
class WebauthnRelyingPartyIntegrationSpec @Autowired constructor(
    private val webAuthnRelyingPartyOperations: WebAuthnRelyingPartyOperations,
    private val userRepository: UserRepository,
    private val webauthnCredentialRepository: WebauthnCredentialRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private val objectConverter = ObjectConverter()
    private val origin = Origin.create("http://localhost:8080")

    private fun authenticationFor(user: User): Authentication =
        TestingAuthenticationToken(user.loginId, null, emptyList()).apply { isAuthenticated = true }

    init {
        describe("WebAuthn 등록/인증 세리모니(실제 서명 검증)") {
            it("가상 인증기로 등록한 자격증명이 DB에 저장되고, 그 자격증명으로 실제 로그인 인증(서명 검증)에 성공한다") {
                val user = userRepository.save(
                    User(loginId = "webauthn-user-${System.nanoTime()}", name = "웹인증사용자", email = "webauthn@example.com")
                )
                val authentication = authenticationFor(user)
                val authenticator = NoneAttestationAuthenticator()
                val clientPlatform = ClientPlatform(origin, WebAuthnAuthenticatorAdaptor(authenticator))

                // 1) 등록: 서버가 발급한 creation options를 가상 인증기가 그대로 소비해 attestation을 만든다.
                val creationOptions = webAuthnRelyingPartyOperations.createPublicKeyCredentialCreationOptions(
                    PublicKeyCredentialCreationOptionsRequest { authentication }
                )
                val w4jCreationOptions = Webauthn4jModelConverter.toWebauthn4j(creationOptions)
                val w4jAttestationCredential = clientPlatform.create(w4jCreationOptions)
                val springAttestationCredential = Webauthn4jModelConverter.toSpringAttestation(w4jAttestationCredential)

                val record = webAuthnRelyingPartyOperations.registerCredential(
                    ImmutableRelyingPartyRegistrationRequest(
                        creationOptions,
                        RelyingPartyPublicKey(springAttestationCredential, "테스트 보안키")
                    )
                )

                record.label shouldBe "테스트 보안키"
                val savedRows = webauthnCredentialRepository.findByUserId(user.id!!)
                savedRows.size shouldBe 1
                savedRows[0].publicKeyCose.isNotEmpty() shouldBe true
                savedRows[0].label shouldBe "테스트 보안키"

                // 2) 인증(로그인 2FA): 방금 등록한 자격증명으로만 challenge를 발급하고, 가상
                // 인증기가 실제 개인키로 서명한 assertion을 서버가 검증하는 전체 경로를 태운다.
                val requestOptions = webAuthnRelyingPartyOperations.createCredentialRequestOptions(
                    PublicKeyCredentialRequestOptionsRequest { authentication }
                )
                requestOptions.allowCredentials.size shouldBe 1

                val w4jRequestOptions = Webauthn4jModelConverter.toWebauthn4j(requestOptions)
                val w4jAssertionCredential = clientPlatform.get(w4jRequestOptions)
                val springAssertionCredential = Webauthn4jModelConverter.toSpringAssertion(w4jAssertionCredential)

                val resolvedUser = webAuthnRelyingPartyOperations.authenticate(
                    RelyingPartyAuthenticationRequest(requestOptions, springAssertionCredential)
                )

                resolvedUser.name shouldBe user.loginId
            }

            it("서명이 조작된 assertion은 검증에 실패해야 한다(리플레이/위조 방지가 실제로 동작하는지 확인)") {
                val user = userRepository.save(
                    User(loginId = "webauthn-tamper-${System.nanoTime()}", name = "위조테스트", email = "webauthn-tamper@example.com")
                )
                val authentication = authenticationFor(user)
                val authenticator = NoneAttestationAuthenticator()
                val clientPlatform = ClientPlatform(origin, WebAuthnAuthenticatorAdaptor(authenticator))

                val creationOptions = webAuthnRelyingPartyOperations.createPublicKeyCredentialCreationOptions(
                    PublicKeyCredentialCreationOptionsRequest { authentication }
                )
                val w4jCreationOptions = Webauthn4jModelConverter.toWebauthn4j(creationOptions)
                val w4jAttestationCredential = clientPlatform.create(w4jCreationOptions)
                webAuthnRelyingPartyOperations.registerCredential(
                    ImmutableRelyingPartyRegistrationRequest(
                        creationOptions,
                        RelyingPartyPublicKey(Webauthn4jModelConverter.toSpringAttestation(w4jAttestationCredential), "위조테스트키")
                    )
                )

                val requestOptions = webAuthnRelyingPartyOperations.createCredentialRequestOptions(
                    PublicKeyCredentialRequestOptionsRequest { authentication }
                )
                val w4jRequestOptions = Webauthn4jModelConverter.toWebauthn4j(requestOptions)
                val w4jAssertionCredential = clientPlatform.get(w4jRequestOptions)
                val tamperedResponse = w4jAssertionCredential.response!!
                val tamperedSignature = tamperedResponse.signature.copyOf().also { it[0] = (it[0] + 1).toByte() }
                @Suppress("UNCHECKED_CAST")
                val tamperedSpringCredential = PublicKeyCredential.builder<AuthenticatorAssertionResponse>()
                    .id(Bytes(w4jAssertionCredential.rawId).toBase64UrlString())
                    .type(PublicKeyCredentialType.PUBLIC_KEY)
                    .rawId(Bytes(w4jAssertionCredential.rawId))
                    .response(
                        AuthenticatorAssertionResponse.builder()
                            .authenticatorData(Bytes(tamperedResponse.authenticatorData))
                            .signature(Bytes(tamperedSignature))
                            .userHandle(tamperedResponse.userHandle?.let { Bytes(it) })
                            .clientDataJSON(Bytes(tamperedResponse.clientDataJSON))
                            .build()
                    )
                    .clientExtensionResults(ImmutableAuthenticationExtensionsClientOutputs(emptyList()))
                    .build() as PublicKeyCredential<AuthenticatorAssertionResponse>

                shouldThrow<Exception> {
                    webAuthnRelyingPartyOperations.authenticate(
                        RelyingPartyAuthenticationRequest(requestOptions, tamperedSpringCredential)
                    )
                }
            }
        }
    }
}
