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

// 계정당 여러 개(기기별) 등록 가능 — ssh_key와 동일하게 User와 분리된 1:N 테이블. secret은
// 로그인 시 원문 비교가 필요한 대칭 비밀이라 단방향 해시가 아니라 암호화로 저장한다
// (TotpSecretEncryptor). enabled=false인 행은 "등록 화면에서 QR을 발급했지만 아직 6자리 코드로
// 검증하지 않은" 임시 상태 — 검증 없이 바로 활성화하면 오탈자 시크릿으로 스스로 잠길 위험이 있어
// 배제한다(TwoFactorServiceImpl.verifyAndActivateTotp 참고).
@Entity
@Table(name = "user_totp_credential")
class TwoFactorTotpCredential(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // ON DELETE CASCADE(DB 레벨) — 이 프로젝트 다른 통합테스트들이 "자식 테이블 명시적으로
    // 먼저 지우고 userRepository.deleteAll()" 관례 대신, 사용자가 지워지면 2FA 자격증명도
    // 함께 사라지는 게 자연스러운 관계라 여기서는 DB 제약으로 보장한다(ssh_key 등 기존
    // 테이블은 명시적 정리 순서 관례를 그대로 따름 — 새 관례를 섞지 않기 위해 그쪽은 손대지 않음).
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
