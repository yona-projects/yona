package com.github.yonaprojects.yona.config.webauthn

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement
import org.springframework.security.web.webauthn.api.UserVerificationRequirement
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations
import org.springframework.security.web.webauthn.registration.HttpSessionPublicKeyCredentialCreationOptionsRepository
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository
import org.springframework.security.web.webauthn.authentication.HttpSessionPublicKeyCredentialRequestOptionsRepository
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository

// yona는 HttpSecurity DSL의 webauthn() 확장(패스워드리스/discoverable 로그인 전용으로 설계됨)을
// 쓰지 않고, WebAuthnRelyingPartyOperations를 직접 빈으로 노출해 "1차 비밀번호 인증 후 2단계
// 인증"이라는 우리 유스케이스에 맞게 등록/설정 화면과 로그인 2FA 화면 컨트롤러에서 building
// block으로만 쓴다(SecurityConfig.kt 상단 주석 — AuthenticationManagerBuilder 초기화 순서 함정과
// 무관하게, 이 빈들은 필터 체인 구성과 별개로 독립적으로 동작한다).
@Configuration
class WebauthnRelyingPartyConfig(
    @Value("\${yona.security.webauthn.relying-party-id}") private val relyingPartyId: String,
    @Value("\${yona.security.webauthn.relying-party-name}") private val relyingPartyName: String,
    @Value("\${yona.security.webauthn.allowed-origins}") private val allowedOriginsRaw: String
) {

    @Bean
    fun webAuthnRelyingPartyOperations(
        userEntities: PublicKeyCredentialUserEntityRepository,
        userCredentials: UserCredentialRepository
    ): WebAuthnRelyingPartyOperations {
        val rp = PublicKeyCredentialRpEntity.builder()
            .id(relyingPartyId)
            .name(relyingPartyName)
            .build()
        val allowedOrigins = allowedOriginsRaw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val operations = Webauthn4JRelyingPartyOperations(userEntities, userCredentials, rp, allowedOrigins)
        // 라이브러리 기본값(residentKey=REQUIRED)은 패스키/username-less 로그인을 겨냥한 설정이라
        // resident key 저장 공간이 부족한 구형 보안 키와는 호환되지 않는다 — 우리는 비밀번호
        // 1차 인증 뒤에 붙는 2단계 인증이라 discoverable credential이 필요 없으므로 PREFERRED로
        // 완화해 호환성을 넓힌다.
        operations.setCustomizeCreationOptions { builder ->
            builder.authenticatorSelection(
                AuthenticatorSelectionCriteria.builder()
                    .userVerification(UserVerificationRequirement.PREFERRED)
                    .residentKey(ResidentKeyRequirement.PREFERRED)
                    .build()
            )
        }
        return operations
    }

    // 브라우저 navigator.credentials 결과(JSON)를 PublicKeyCredential<...>로 역직렬화하고,
    // Bytes/Instant 등을 스펙이 기대하는 base64url 문자열로 직렬화하는 데 필요하다. 이 프로젝트는
    // Jackson 3(tools.jackson)을 쓰므로 Jackson2용(WebauthnJackson2Module)이 아니라 이 모듈을 쓴다.
    @Bean
    fun webauthnJacksonModule(): WebauthnJacksonModule = WebauthnJacksonModule()

    // 등록(challenge)과 로그인 2FA(challenge)가 서로 다른 세션 슬롯을 쓰도록 별도 빈으로 분리한다
    // (동시에 두 플로우가 겹쳐도 서로의 challenge를 덮어쓰지 않음).
    @Bean
    fun publicKeyCredentialCreationOptionsRepository(): PublicKeyCredentialCreationOptionsRepository =
        HttpSessionPublicKeyCredentialCreationOptionsRepository()

    @Bean
    fun publicKeyCredentialRequestOptionsRepository(): PublicKeyCredentialRequestOptionsRepository =
        HttpSessionPublicKeyCredentialRequestOptionsRepository()
}
