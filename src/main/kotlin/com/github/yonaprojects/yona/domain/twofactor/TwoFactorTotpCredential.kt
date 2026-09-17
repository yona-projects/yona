package com.github.yonaprojects.yona.domain.twofactor

import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant

// secret은 로그인 시 원문 비교가 필요해 해시가 아닌 암호화로 저장한다(TotpSecretEncryptor).
// enabled=false는 QR 발급 후 코드 검증 전 임시 상태 — 검증 없이 활성화하면 오탈자 시크릿으로
// 스스로 잠길 위험이 있어 막는다(TwoFactorServiceImpl.verifyAndActivateTotp 참고).
@Entity
@Table(name = "user_totp_credential")
class TwoFactorTotpCredential(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // ON DELETE CASCADE(DB 레벨)로 사용자 삭제 시 2FA 자격증명도 함께 삭제한다
    // (기존 ssh_key 등은 명시적 정리 순서 관례를 그대로 따름).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User? = null,

    @Column(name = "label", nullable = false)
    var label: String = "",

    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
    var encryptedSecret: String = "",

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = false,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "activated_at")
    var activatedAt: Instant? = null
)
