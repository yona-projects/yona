package com.github.yonaprojects.yona.config.webauthn

import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse as SpringAssertionResponse
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse as SpringAttestationResponse
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria as SpringAuthenticatorSelectionCriteria
import org.springframework.security.web.webauthn.api.AuthenticatorTransport as SpringAuthenticatorTransport
import org.springframework.security.web.webauthn.api.Bytes as SpringBytes
import org.springframework.security.web.webauthn.api.ImmutableAuthenticationExtensionsClientOutputs
import org.springframework.security.web.webauthn.api.PublicKeyCredential as SpringPublicKeyCredential
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions as SpringCreationOptions
import org.springframework.security.web.webauthn.api.PublicKeyCredentialDescriptor as SpringDescriptor
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions as SpringRequestOptions
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType as SpringCredentialType
import com.webauthn4j.data.AuthenticatorSelectionCriteria as W4jAuthenticatorSelectionCriteria
import com.webauthn4j.data.AuthenticatorTransport as W4jAuthenticatorTransport
import com.webauthn4j.data.PublicKeyCredential as W4jPublicKeyCredential
import com.webauthn4j.data.PublicKeyCredentialCreationOptions as W4jCreationOptions
import com.webauthn4j.data.PublicKeyCredentialDescriptor as W4jDescriptor
import com.webauthn4j.data.PublicKeyCredentialParameters as W4jParameters
import com.webauthn4j.data.PublicKeyCredentialRequestOptions as W4jRequestOptions
import com.webauthn4j.data.PublicKeyCredentialRpEntity as W4jRpEntity
import com.webauthn4j.data.PublicKeyCredentialType as W4jCredentialType
import com.webauthn4j.data.PublicKeyCredentialUserEntity as W4jUserEntity
import com.webauthn4j.data.ResidentKeyRequirement as W4jResidentKeyRequirement
import com.webauthn4j.data.UserVerificationRequirement as W4jUserVerificationRequirement
import com.webauthn4j.data.AttestationConveyancePreference as W4jAttestationConveyancePreference
import com.webauthn4j.data.AuthenticatorAssertionResponse as W4jAssertionResponse
import com.webauthn4j.data.AuthenticatorAttestationResponse as W4jAttestationResponse
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.data.extension.client.AuthenticationExtensionClientOutput
import com.webauthn4j.data.extension.client.AuthenticationExtensionsClientOutputs as W4jExtensionOutputs
import com.webauthn4j.data.extension.client.RegistrationExtensionClientOutput

// spring-security-webauthn(등록/인증 building block으로 직접 쓰는 Webauthn4JRelyingPartyOperations)과
// webauthn4j-test(가상 인증기)는 같은 사양(WebAuthn L2/L3)을 각자 독립된 자바 모델로 표현한다 — 실제
// 브라우저 대신 가상 인증기로 통합테스트를 태우려면 두 모델을 필드 단위로 왕복 변환해야 한다.
// (프로덕션 코드에서는 필요 없음 — 실제 브라우저는 이미 spring-security-webauthn이 기대하는
// JSON 형태로 응답을 보내온다.)
object Webauthn4jModelConverter {

    fun toWebauthn4j(options: SpringCreationOptions): W4jCreationOptions {
        val rp = W4jRpEntity(options.rp.id, options.rp.name)
        val user = W4jUserEntity(options.user.id.bytes, options.user.name, options.user.displayName ?: options.user.name)
        val challenge = DefaultChallenge(options.challenge.bytes)
        val pubKeyCredParams = options.pubKeyCredParams.map {
            W4jParameters(W4jCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.create(it.alg.value))
        }
        val selection = options.authenticatorSelection?.let { toWebauthn4j(it) }
        val attestation = W4jAttestationConveyancePreference.create(options.attestation?.value ?: "none")
        return W4jCreationOptions(rp, user, challenge, pubKeyCredParams, options.timeout?.toMillis(), emptyList(), selection, attestation, null)
    }

    private fun toWebauthn4j(selection: SpringAuthenticatorSelectionCriteria): W4jAuthenticatorSelectionCriteria {
        val residentKey = W4jResidentKeyRequirement.create(selection.residentKey?.value ?: "preferred")
        val userVerification = W4jUserVerificationRequirement.create(selection.userVerification?.value ?: "preferred")
        return W4jAuthenticatorSelectionCriteria(null, residentKey, userVerification)
    }

    fun toWebauthn4j(options: SpringRequestOptions): W4jRequestOptions {
        val challenge = DefaultChallenge(options.challenge.bytes)
        val allowCredentials = options.allowCredentials.map { toWebauthn4j(it) }
        val userVerification = W4jUserVerificationRequirement.create(options.userVerification?.value ?: "preferred")
        return W4jRequestOptions(challenge, options.timeout?.toMillis(), options.rpId!!, allowCredentials, userVerification, null)
    }

    private fun toWebauthn4j(descriptor: SpringDescriptor): W4jDescriptor {
        val transports = descriptor.transports?.map { W4jAuthenticatorTransport.create(it.value) }?.toSet() ?: emptySet()
        return W4jDescriptor(W4jCredentialType.PUBLIC_KEY, descriptor.id!!.bytes, transports)
    }

    // PublicKeyCredentialBuilder<R>의 setter들이 Kotlin에서 raw 타입(제네릭 소거)으로 보여 build()
    // 반환 타입을 좁게 추론하지 못한다 — 우리가 넘긴 response 값의 실제 런타임 타입은 항상
    // 맞으므로(방금 그 타입으로 만들어 넣었으므로) unchecked cast가 안전하다.
    @Suppress("UNCHECKED_CAST")
    fun toSpringAttestation(credential: W4jPublicKeyCredential<W4jAttestationResponse, RegistrationExtensionClientOutput>): SpringPublicKeyCredential<SpringAttestationResponse> {
        val rawId = SpringBytes(credential.rawId)
        val w4jResponse = credential.response!!
        val response = SpringAttestationResponse.builder()
            .attestationObject(SpringBytes(w4jResponse.attestationObject))
            .clientDataJSON(SpringBytes(w4jResponse.clientDataJSON))
            .transports(w4jResponse.transports?.map { SpringAuthenticatorTransport.valueOf(it.value) } ?: emptyList())
            .build()
        val built = SpringPublicKeyCredential.builder<SpringAttestationResponse>()
            .id(rawId.toBase64UrlString())
            .type(SpringCredentialType.PUBLIC_KEY)
            .rawId(rawId)
            .response(response)
            .clientExtensionResults(emptyExtensionOutputs())
            .build()
        return built as SpringPublicKeyCredential<SpringAttestationResponse>
    }

    @Suppress("UNCHECKED_CAST")
    fun toSpringAssertion(credential: W4jPublicKeyCredential<W4jAssertionResponse, AuthenticationExtensionClientOutput>): SpringPublicKeyCredential<SpringAssertionResponse> {
        val rawId = SpringBytes(credential.rawId)
        val w4jResponse = credential.response!!
        val response = SpringAssertionResponse.builder()
            .authenticatorData(SpringBytes(w4jResponse.authenticatorData))
            .signature(SpringBytes(w4jResponse.signature))
            .userHandle(w4jResponse.userHandle?.let { SpringBytes(it) })
            .clientDataJSON(SpringBytes(w4jResponse.clientDataJSON))
            .build()
        val built = SpringPublicKeyCredential.builder<SpringAssertionResponse>()
            .id(rawId.toBase64UrlString())
            .type(SpringCredentialType.PUBLIC_KEY)
            .rawId(rawId)
            .response(response)
            .clientExtensionResults(emptyExtensionOutputs())
            .build()
        return built as SpringPublicKeyCredential<SpringAssertionResponse>
    }

    private fun emptyExtensionOutputs() = ImmutableAuthenticationExtensionsClientOutputs(emptyList())
}
