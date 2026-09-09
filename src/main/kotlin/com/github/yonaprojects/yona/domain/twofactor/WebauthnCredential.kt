package com.github.yonaprojects.yona.domain.twofactor

import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant

// Spring Security의 WebAuthnRelyingPartyOperations(WebAuthn4J 기반)가 요구하는
// CredentialRecord(config/webauthn/WebauthnUserCredentialRepositoryAdapter.kt의
// toCredentialRecord() 참고)를 왕복 저장하는 데 필요한 필드만 그대로 옮겨 담는다 —
// attestationObject/attestationClientDataJSON은
// 인증(assertion) 검증 시 서명 대상 재구성에 실제로 쓰이므로(라이브러리 내부 authenticate()가
// 저장된 attestationObject에서 공개키를 다시 뽑아 서명을 검증) 저장이 필수다. 계정당 여러 개
// (보안 키/기기별) 등록 가능 — ssh_key와 동일한 1:N 설계.
@Entity
@Table(name = "user_webauthn_credential")
class WebauthnCredential(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // ON DELETE CASCADE — TwoFactorTotpCredential.kt 주석 참고.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User? = null,

    // Bytes.toBase64UrlString() — 다른 계정과 전역적으로 겹칠 수 없는 인증기 고유 ID.
    @Column(name = "credential_id", nullable = false, unique = true, length = 512)
    var credentialId: String = "",

    @Lob
    @Column(name = "public_key_cose", nullable = false)
    var publicKeyCose: ByteArray = ByteArray(0),

    // 리플레이 공격 탐지용 카운터 — 인증 성공마다 갱신되며, 저장된 값보다 작거나 같은 값이
    // 오면 WebAuthn4J가 복제된 인증기로 간주해 거부한다.
    @Column(name = "signature_count", nullable = false)
    var signatureCount: Long = 0,

    // AuthenticatorTransport.getValue() 콤마 구분 저장(usb,nfc,ble,internal,hybrid,smart-card).
    @Column(name = "transports", length = 255)
    var transports: String? = null,

    @Column(name = "backup_eligible", nullable = false)
    var backupEligible: Boolean = false,

    @Column(name = "backup_state", nullable = false)
    var backupState: Boolean = false,

    @Column(name = "uv_initialized", nullable = false)
    var uvInitialized: Boolean = false,

    @Lob
    @Column(name = "attestation_object", nullable = false)
    var attestationObject: ByteArray = ByteArray(0),

    @Lob
    @Column(name = "attestation_client_data_json", nullable = false)
    var attestationClientDataJson: ByteArray = ByteArray(0),

    // 사용자가 등록 시 붙이는 별명(예: "업무용 노트북", "YubiKey 5C"). GitHub 관례와 동일.
    @Column(name = "label", nullable = false)
    var label: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
)
