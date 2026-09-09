package com.github.yonaprojects.yona.domain.deploykey

import com.github.yonaprojects.yona.domain.project.Project
import jakarta.persistence.*
import java.time.Instant

// GitHub "Deploy keys"(SSH, read-only 체크박스) 화면과 동일한 저장소
// 스코프 자격증명. 설계 초안은 필드를 (repository_id, public_key, fingerprint, read_only,
// added_date)로 적었지만, 구현 과정에서 다음 결정을 추가했다:
// - GitHub/GitLab 모두 SSH 공개키와 불투명 토큰을 같은 엔티티로 합치지 않는다는 조사 결과를
//   존중하되, "저장소 범위로 스코프된 자격증명" 하나의 관리 화면에서 SSH 프로토콜 경로와
//   HTTPS AuthenticationProvider 양쪽에 다 쓸 수 있어야 한다는 요구가
//   있어, 이 엔티티에 "SSH 공개키" 필드와 "HTTPS Basic 인증용 불투명 시크릿 해시" 필드를
//   같이 둔다 — 실제 인증 메커니즘(비대칭키 서명 검증 vs 시크릿 비교)은 여전히 별개이고, 단지
//   같은 project_id/read_only 스코프 레코드를 두 경로가 공유할 뿐이다.
// - publicKey는 등록 시점에 항상 요구한다(GitHub Deploy Key 화면과 동일). httpsToken은 등록할
//   때마다 항상 자동 발급한다(한 번만 표시) — SSH 프로토콜과 HTTPS 양쪽에서
//   즉시 쓸 수 있게 하기 위함.
// - fingerprint는 SshKey(사용자 전역 키)와 전역적으로 유일해야 한다 — 같은 공개키를
//   서로 다른 프로젝트의 Deploy Key로, 혹은 다른 사용자의 SshKey로 중복 등록하면 계정/저장소
//   사칭에 악용될 수 있다는 보안 검토를 반영한 결정.
@Entity
@Table(name = "deploy_key")
class DeployKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    // EAGER: authenticate()가 반환한 직후(트랜잭션 밖, 예: SSH/HTTPS 인증 컨텍스트)에도
    // principal.deployKey.project를 안전하게 읽어야 하므로 LAZY 프록시로 인한
    // LazyInitializationException을 피한다.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project? = null,

    @Column(name = "title", nullable = false)
    var title: String = "",

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    var publicKey: String = "",

    @Column(name = "fingerprint", nullable = false, unique = true, length = 128)
    var fingerprint: String = "",

    @Column(name = "https_token_hash", unique = true, length = 128)
    var httpsTokenHash: String? = null,

    // GitHub Deploy Key 기본값(read-only)과 동일 — 명시적으로 "쓰기 허용"을 체크해야 push 가능.
    @Column(name = "read_only", nullable = false)
    var readOnly: Boolean = true,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
)
