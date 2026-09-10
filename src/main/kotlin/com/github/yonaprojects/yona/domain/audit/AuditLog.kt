package com.github.yonaprojects.yona.domain.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

// 관리자가 수행하는 보안 민감 조치(계정 상태 변경, 2FA 강제 비활성화, 브루트포스 잠금 강제
// 해제)를 "누가/언제/무엇을/누구에게/왜" 조회 가능하게 남긴다 — DB 상태 변경만으로는 사후
// 추적이 불가능했던 부분(SOC2 CC6/ISO 27001 A.8.15류 감사 추적 통제 대응).
@Entity
@Table(name = "audit_log")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var actorLoginId: String = "",

    @Column(nullable = false)
    var targetLoginId: String = "",

    @Column(nullable = false)
    var action: String = "",

    @Column(length = 1000)
    var reason: String? = null,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
)
