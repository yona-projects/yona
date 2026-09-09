package com.github.yonaprojects.yona.config.webauthn

import com.github.yonaprojects.yona.domain.twofactor.WebauthnCredential
import com.github.yonaprojects.yona.domain.twofactor.WebauthnCredentialRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.security.web.webauthn.api.AuthenticatorTransport
import org.springframework.security.web.webauthn.api.Bytes
import org.springframework.security.web.webauthn.api.CredentialRecord
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType
import org.springframework.security.web.webauthn.management.UserCredentialRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// Spring Security의 WebAuthnRelyingPartyOperations가 등록(registerCredential)/인증(authenticate)
// 세리모니 중 실제 서명 검증에 쓰는 CredentialRecord를 우리 JPA 엔티티(WebauthnCredential)로
// 왕복 저장하는 어댑터. attestationObject/attestationClientDataJSON까지 그대로 보존해야
// authenticate()가 매 로그인마다 공개키를 다시 뽑아 서명을 검증할 수 있다.
@Component
class WebauthnUserCredentialRepositoryAdapter(
    private val webauthnCredentialRepository: WebauthnCredentialRepository,
    private val userRepository: UserRepository
) : UserCredentialRepository {

    @Transactional
    override fun delete(credentialId: Bytes) {
        webauthnCredentialRepository.findByCredentialId(credentialId.toBase64UrlString())
            .ifPresent { webauthnCredentialRepository.delete(it) }
    }

    @Transactional
    override fun save(credentialRecord: CredentialRecord) {
        val userId = WebauthnUserHandle.decode(credentialRecord.userEntityUserId)
            ?: throw IllegalStateException("잘못된 WebAuthn 사용자 핸들입니다.")
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalStateException("WebAuthn 등록 대상 사용자를 찾을 수 없습니다.") }

        val credentialIdStr = credentialRecord.credentialId.toBase64UrlString()
        val entity = webauthnCredentialRepository.findByCredentialId(credentialIdStr).orElseGet {
            WebauthnCredential(credentialId = credentialIdStr, createdAt = credentialRecord.created ?: Instant.now())
        }
        entity.user = user
        entity.publicKeyCose = credentialRecord.publicKey.bytes
        entity.signatureCount = credentialRecord.signatureCount
        entity.transports = credentialRecord.transports.joinToString(",") { it.value }
        entity.backupEligible = credentialRecord.isBackupEligible
        entity.backupState = credentialRecord.isBackupState
        entity.uvInitialized = credentialRecord.isUvInitialized
        entity.attestationObject = credentialRecord.attestationObject!!.bytes
        entity.attestationClientDataJson = credentialRecord.attestationClientDataJSON!!.bytes
        entity.label = credentialRecord.label ?: entity.label
        entity.lastUsedAt = credentialRecord.lastUsed
        webauthnCredentialRepository.save(entity)
    }

    @Transactional(readOnly = true)
    override fun findByCredentialId(credentialId: Bytes): CredentialRecord? =
        webauthnCredentialRepository.findByCredentialId(credentialId.toBase64UrlString())
            .map { it.toCredentialRecord() }
            .orElse(null)

    @Transactional(readOnly = true)
    override fun findByUserId(userId: Bytes): List<CredentialRecord> {
        val id = WebauthnUserHandle.decode(userId) ?: return emptyList()
        return webauthnCredentialRepository.findByUserId(id).map { it.toCredentialRecord() }
    }
}

fun WebauthnCredential.toCredentialRecord(): CredentialRecord {
    val userId = user?.id ?: throw IllegalStateException("소유자가 없는 WebAuthn credential입니다: $credentialId")
    val transportSet = transports
        ?.split(",")
        ?.mapNotNull { raw -> runCatching { AuthenticatorTransport.valueOf(raw) }.getOrNull() }
        ?.toSet()
        ?: emptySet()

    return ImmutableCredentialRecord.builder()
        .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
        .credentialId(Bytes.fromBase64(credentialId))
        .userEntityUserId(WebauthnUserHandle.encode(userId))
        .publicKey(ImmutablePublicKeyCose(publicKeyCose))
        .signatureCount(signatureCount)
        .uvInitialized(uvInitialized)
        .transports(transportSet)
        .backupEligible(backupEligible)
        .backupState(backupState)
        .attestationObject(Bytes(attestationObject))
        .attestationClientDataJSON(Bytes(attestationClientDataJson))
        .created(createdAt)
        .apply { lastUsedAt?.let { lastUsed(it) } }
        .label(label)
        .build()
}
