package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.enumeration.State
import jakarta.persistence.*
import java.time.Instant

// yona-wiki P3-02 14라운드 — Issue/Posting은 (project_id, number) UNIQUE 제약이 있는데 PullRequest만
// 없었다. 그래서 채번 경쟁(동시 PR 생성)이 나면 issue처럼 500으로라도 막히지 않고 서로 다른 PR이
// 같은 번호를 갖는 조용한 데이터 손상이 났다(실서버 재현: 동시 요청 10개가 전부 #2로 성공). 이
// 제약이 그 경쟁을 "명확한 제약 위반 실패"로 바꿔주고, PullRequestController.createPullRequest()가
// 그 실패를 잡아 전체를 재시도한다(실서버 재검증: 동시 요청 10개가 전부 #1~#10 고유 번호로 성공).
@Entity
@Table(
    name = "pull_request",
    uniqueConstraints = [UniqueConstraint(columnNames = ["to_project_id", "number"])]
)
class PullRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column(length = 1_000_000)
    var body: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_project_id", nullable = false)
    var toProject: Project,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_project_id", nullable = false)
    var fromProject: Project,

    @Column(nullable = false)
    var toBranch: String = "",

    @Column(nullable = false)
    var fromBranch: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contributor_id", nullable = false)
    var contributor: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    var receiver: User? = null,

    var created: Instant? = null,
    var updated: Instant? = null,
    var received: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: State = State.OPEN,

    var isConflict: Boolean? = false,
    var isMerging: Boolean? = false,

    var lastCommitId: String? = null,
    var mergedCommitIdFrom: String? = null,
    var mergedCommitIdTo: String? = null,

    var number: Long? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "pull_request_reviewers",
        joinColumns = [JoinColumn(name = "pull_request_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
        uniqueConstraints = [UniqueConstraint(columnNames = ["pull_request_id", "user_id"])]
    )
    var reviewers: MutableSet<User> = mutableSetOf(),

    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR에 라벨/담당자 개념 추가.
    // 레거시 Play `yona`의 PullRequest.java(app/models/PullRequest.java)에도 label/assignee
    // 필드가 전혀 없음을 확인했다(전수 grep 0건) — 이 두 필드는 포팅 누락 버그가 아니라 신규 기능
    // 확장이다. `Assignee`(domain/issue/Assignee.kt)는 (user, project)만 갖는 범용 엔티티라
    // Issue와 동일한 패턴(@ManyToOne cascade=ALL, FK 컬럼 assignee_id)으로 그대로 재사용한다.
    @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinColumn(name = "assignee_id")
    var assignee: Assignee? = null,

    // 라벨은 domain/project/Label(프로젝트 레벨 토픽 태그, project_label 조인테이블, Step8.5
    // 1라운드가 프로젝트 검색/분류용으로 도입)과 domain/issue/IssueLabel(카테고리에 종속된 진짜
    // GitHub 스타일 이슈 라벨, issue_issue_label 조인테이블, ProjectViewController.newLabel() 등
    // 기존 라벨 관리 CRUD가 실제로 다루는 엔티티)의 용도가 서로 다름을 코드로 확인했다 — 전자는
    // "프로젝트 자체"에 붙는 태그이고 후자가 "프로젝트 안의 개별 항목(이슈)"에 붙는 라벨이다. PR도
    // 개별 항목이므로 후자(IssueLabel)를 그대로 재사용하는 것이 개념적으로 맞고, 이미 프로젝트마다
    // 라벨 정의(이름/색상/카테고리)가 있으니 새 `PullRequestLabel` 엔티티를 만들 필요가 없다 —
    // Issue.labels와 동일한 패턴(신규 조인테이블 pull_request_issue_label)으로 재사용한다.
    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "pull_request_issue_label",
        joinColumns = [JoinColumn(name = "pull_request_id")],
        inverseJoinColumns = [JoinColumn(name = "issue_label_id")]
    )
    var labels: MutableSet<IssueLabel> = mutableSetOf()
)
