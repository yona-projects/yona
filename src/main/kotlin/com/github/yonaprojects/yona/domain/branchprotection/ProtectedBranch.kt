package com.github.yonaprojects.yona.domain.branchprotection

import com.github.yonaprojects.yona.domain.project.Project
import jakarta.persistence.*
import java.time.Instant

// yona-wiki P3-04(브랜치 보호) — legacy에는 대응 모델이 전혀 없는 신규 인프라(Webhook.kt와 동일하게
// 프로젝트 범위 설정 엔티티). 적용 지점은 두 곳: (1) 직접 push 차단
// (domain/vcs/GitPushHooks.kt의 BranchProtectionPreReceiveHook), (2) PR 병합 시 체크
// (PullRequestServiceImpl.merge()). AccessControl과는 별개 레이어(권한 확인 통과 후 추가 정책)다.
//
// require_approvals/require_signed_commits는 Step1 스파이크 결론(계획 문서 참고 — CommentThread에
// 승인 개념 자체가 없고, GPG 서명 검증 파이프라인은 P3-03 소관)에 따라 필드만 존재하고 실제 판정
// 로직 없이 항상 통과 처리된다(PullRequestServiceImpl.checkBranchProtectionForMerge() 참고).
@Entity
@Table(name = "protected_branch")
class ProtectedBranch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: Project? = null,

    // glob 패턴(예: "main", "release/*"). '*'만 와일드카드로 지원한다 — 그 이상의 표현력(문자
    // 클래스, '?' 등)은 이 계획의 DoD에 없어 과도한 설계로 보류한다.
    @Column(nullable = false, length = 250)
    var branchPattern: String = "",

    @Column(nullable = false)
    var requirePullRequest: Boolean = false,

    // 0 = 비활성. Step1 스파이크 결론에 따라 값과 무관하게 병합 체크에서 항상 통과 처리된다.
    @Column(nullable = false)
    var requireApprovals: Int = 0,

    // P3-03의 GPG 서명 검증 파이프라인이 완성되기 전까지 값과 무관하게 항상 통과 처리된다.
    @Column(nullable = false)
    var requireSignedCommits: Boolean = false,

    // 콤마로 구분된 loginId 목록. null/공백 = 제한 없음(전원 push 가능). 별도 조인 테이블 대신
    // Webhook.secret과 동일한 단순 컬럼 방식을 택했다 — 목록 크기가 작고(프로젝트당 브랜치 보호
    // 규칙 수 자체가 적음) 별도 엔티티를 둘 만큼의 복잡도가 없다.
    @Column(length = 1000)
    var restrictPushTo: String? = null,

    @Column(nullable = false)
    var disallowForcePush: Boolean = false,

    @Column(nullable = false)
    var disallowDelete: Boolean = false,

    // 프로젝트 매니저(RoleType.MANAGER)가 이 브랜치 보호 규칙 전체를 우회할 수 있는지. 기본값 true —
    // GitHub의 "Do not allow bypassing the above settings"가 기본 해제(=매니저는 우회 가능)인 것과
    // 동일한 기본값을 택했다.
    @Column(nullable = false)
    var adminsCanBypass: Boolean = true,

    @Column(nullable = false)
    var createdAt: Instant = Instant.now()
) {
    // branchPattern을 정규식으로 변환해 branchName(순수 브랜치 이름, "refs/heads/" 접두어 없이)과
    // 매칭한다. '*'만 임의 길이 와일드카드로 해석하고 그 외 모든 문자는 리터럴로 이스케이프한다
    // (정규식 인젝션 방지 — branchPattern은 사용자 입력이다).
    fun matches(branchName: String): Boolean = patternRegex().matches(branchName)

    private fun patternRegex(): Regex {
        val sb = StringBuilder()
        for (part in branchPattern.split("*")) {
            if (sb.isNotEmpty()) sb.append(".*")
            sb.append(Regex.escape(part))
        }
        return Regex("^$sb$")
    }

    // restrictPushTo("alice, bob ,, charlie")를 {"alice", "bob", "charlie"}로 파싱한다. null이거나
    // 공백만 있으면 빈 집합(=제한 없음)을 반환한다.
    fun restrictedLoginIds(): Set<String> =
        restrictPushTo
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
}
