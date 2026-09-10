package com.github.yonaprojects.yona.domain.device

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

// 폼 로그인으로 완전히 로그인에 성공한 브라우저를 식별하는 쿠키 기반 기기 인식 기록.
// 쿠키 값 자체가 아니라 해시만 저장한다(BackupCode와 동일한 이유 — 토큰이 고엔트로피 랜덤값이라
// 원문 비교가 필요 없다).
@Entity
@Table(name = "user_known_device")
class UserKnownDevice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User? = null,

    @Column(name = "device_token_hash", nullable = false, length = 128)
    var deviceTokenHash: String = "",

    @Column(name = "label", length = 255)
    var label: String? = null,

    @Column(name = "first_seen_at", nullable = false)
    var firstSeenAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now()
)
