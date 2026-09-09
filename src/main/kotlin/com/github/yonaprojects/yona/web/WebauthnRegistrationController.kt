package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.twofactor.TwoFactorService
import com.github.yonaprojects.yona.domain.twofactor.WebauthnCredentialRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.web.webauthn.api.AuthenticatorAttestationResponse
import org.springframework.security.web.webauthn.api.PublicKeyCredential
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest
import org.springframework.security.web.webauthn.management.PublicKeyCredentialCreationOptionsRequest
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 계정 설정 화면에서 브라우저 navigator.credentials.create() 결과를 받아 실제로 검증/저장하는
// JSON API. 로그인 시점 2FA WebAuthn 검증(TwoFactorLoginController)과는 별도 경로 —
// 등록은 이미 완전히 로그인된 사용자(authentication.authorities에 ROLE_PRE_2FA가 없는 상태)만
// 접근 가능해야 하므로 별도 컨트롤러로 분리했다.
@RestController
@RequestMapping("/user/editform/security/webauthn")
class WebauthnRegistrationController(
    private val userRepository: UserRepository,
    private val twoFactorService: TwoFactorService,
    private val webAuthnRelyingPartyOperations: WebAuthnRelyingPartyOperations,
    private val creationOptionsRepository: PublicKeyCredentialCreationOptionsRepository,
    private val webauthnCredentialRepository: WebauthnCredentialRepository
) {

    @PostMapping("/options")
    fun options(
        authentication: Authentication?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Any> {
        if (authentication == null) return ResponseEntity.status(401).build()
        val options = webAuthnRelyingPartyOperations.createPublicKeyCredentialCreationOptions(
            PublicKeyCredentialCreationOptionsRequest { authentication }
        )
        creationOptionsRepository.save(request, response, options)
        return ResponseEntity.ok(options)
    }

    data class RegisterRequest(val label: String, val credential: PublicKeyCredential<AuthenticatorAttestationResponse>)

    @PostMapping
    fun register(
        @RequestBody body: RegisterRequest,
        authentication: Authentication?,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        val user = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(401).build()
        val options = creationOptionsRepository.load(request)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "등록 세션이 만료되었습니다. 다시 시도해주세요."))

        return try {
            val label = body.label.trim().ifBlank { "보안 키" }
            val record = webAuthnRelyingPartyOperations.registerCredential(
                ImmutableRelyingPartyRegistrationRequest(options, RelyingPartyPublicKey(body.credential, label))
            )
            val savedEntity = webauthnCredentialRepository.findByCredentialId(record.credentialId.toBase64UrlString())
                .orElseThrow { IllegalStateException("등록된 credential을 찾을 수 없습니다.") }
            val freshBackupCodes = twoFactorService.completeWebauthnRegistration(user, savedEntity)
            ResponseEntity.ok(mapOf("status" to "success", "freshBackupCodes" to freshBackupCodes))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to "WebAuthn 등록에 실패했습니다: ${e.message}"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("error" to "WebAuthn 등록에 실패했습니다."))
        }
    }
}
