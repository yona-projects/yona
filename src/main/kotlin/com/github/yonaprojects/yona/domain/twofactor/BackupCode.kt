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

// 1회용 로그인 복구 수단 8개(계정당). 원문 비교가 필요 없으므로(재발급 시 전량 무효화되고
// 사용 즉시 소모) TOTP secret과 달리 단방향 해시로 저장한다. usedAt이 채워지면 재사용 불가.
@Entity
@Table(name = "user_backup_code")
class BackupCode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // ON DELETE CASCADE — TwoFactorTotpCredential.kt 주석 참고.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User? = null,

    @Column(name = "code_hash", nullable = false, length = 128)
    var codeHash: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "used_at")
    var usedAt: Instant? = null
)
