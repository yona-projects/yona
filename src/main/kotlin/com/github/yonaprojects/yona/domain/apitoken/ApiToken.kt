package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import jakarta.persistence.*
import java.time.Instant

// GitHub Fine-grained PAT 대응. legacy User.token(전권 단일 토큰, 기존
// UserRepository.findByToken)을 대체하는 저장소 범위 + 리소스별 권한 스코프 토큰.
// repo scope는 "전체 저장소(allRepositories) vs 선택 저장소 목록(scopedProjects)" 2가지 모드를
// User.enrolledProjects와 동일한 ManyToMany 조인테이블 패턴으로 구현했다 — 선택 저장소 목록은
// 토큰 1개당 여러 프로젝트, 프로젝트 1개당 여러 토큰이 걸릴 수 있는 전형적인 다대다 관계라
// 별도 조인 엔티티가 필요 없다(반면 resource scope는 그룹별 permission 값이 함께 필요해
// ApiTokenScope로 분리 — 조인테이블만으로는 permission 컬럼을 못 붙인다).
@Entity
@Table(name = "api_token")
class ApiToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User? = null,

    // GitHub Fine-grained PAT은 토큰마다 이름을 강제한다(여러 토큰을
    // 발급/관리하는 화면에서 구분할 유일한 사용자용 식별자). 기존 Step1~3 테스트가 positional이 아닌
    // named argument로만 ApiToken(...)을 생성하므로 어느 위치에 넣어도 안전하지만, owner/tokenHash
    // 바로 다음에 둬 "토큰을 식별하는 정보"끼리 묶었다.
    @Column(name = "name", nullable = false)
    var name: String = "",

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    var tokenHash: String = "",

    // GitHub의 "All repositories" 옵션 대응. true면 scopedProjects는 무시되고 소유자의 모든
    // 저장소에 대해 resource scope만으로 판정한다.
    @Column(name = "all_repositories", nullable = false)
    var allRepositories: Boolean = false,

    // cascade를 두지 않는다 — scopedProjects는 항상 이미 존재하는 Project를 참조만 하고(토큰이
    // 새 프로젝트를 만들 일은 없다), CascadeType.PERSIST를 뒀더니 이미 저장된(그래서 detached인)
    // Project를 담아 토큰을 저장할 때 Hibernate가 "detached entity passed to persist"로 거부하는
    // 문제가 실제로 있었다(선택 저장소 스코프 토큰 발급이라는 핵심 시나리오에서 발생).
    @ManyToMany
    @JoinTable(
        name = "api_token_project",
        joinColumns = [JoinColumn(name = "api_token_id")],
        inverseJoinColumns = [JoinColumn(name = "project_id")]
    )
    var scopedProjects: MutableSet<Project> = mutableSetOf(),

    @OneToMany(mappedBy = "apiToken", cascade = [CascadeType.ALL], orphanRemoval = true)
    var scopes: MutableList<ApiTokenScope> = mutableListOf(),

    // 설계 결정 — 무기한 토큰 발급 자체를 금지한다. 타입을 Instant?로 열어둔 이유는
    // "null이면 저장 거부"를 테스트가 직접 null을 넘겨 검증할 수 있게 하기 위함이고, 실제 거부는
    // 이 @Column(nullable = false) DB 제약(NOT NULL 위반)이 담당한다 — 이 리포지토리의 다른
    // 엔티티(Webhook.project 등)도 같은 방식(Kotlin nullable 타입 + JPA nullable=false)을 쓴다.
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
