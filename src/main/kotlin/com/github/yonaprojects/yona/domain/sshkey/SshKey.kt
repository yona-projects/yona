package com.github.yonaprojects.yona.domain.sshkey

import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

// yona-wiki P3-03 Step3 — GitHub "Settings > SSH and GPG keys" 화면과 동일한 사용자 전역 SSH
// 공개키. DeployKey(저장소 스코프)와는 별개 엔티티로 유지하기로 이미 확정된 설계(계획 문서 "제외"
// 항목)를 따른다. fingerprint는 DeployKey.fingerprint와 전역적으로 유일해야 한다(계정/저장소
// 사칭 방지 — SshKeyServiceImpl.create() 참고).
@Entity
@Table(name = "ssh_key")
class SshKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // EAGER: authenticate()가 반환한 직후(트랜잭션 밖, 예: SSH 세션 컨텍스트)에도 principal.user를
    // 안전하게 읽어야 하므로 LAZY 프록시로 인한 LazyInitializationException을 피한다.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Column(name = "title", nullable = false)
    var title: String = "",

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    var publicKey: String = "",

    @Column(name = "fingerprint", nullable = false, unique = true, length = 128)
    var fingerprint: String = "",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
)
