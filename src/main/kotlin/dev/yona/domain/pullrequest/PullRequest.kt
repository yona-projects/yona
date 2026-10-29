package dev.yona.domain.pullrequest

import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.ColumnDefault
import dev.yona.domain.LONG_TEXT
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.*
import java.util.Date

/** 풀 리퀘스트이다. */
@Entity
@Table(
    name = "pull_request",
    indexes = [
        Index(name = "ix_pull_request_contributor_35", columnList = "contributor_id"),
        Index(name = "ix_pull_request_fromProject_34", columnList = "from_project_id"),
        Index(name = "ix_pull_request_number", columnList = "number"),
        Index(name = "ix_pull_request_receiver_36", columnList = "receiver_id"),
        Index(name = "ix_pull_request_toProject_33", columnList = "to_project_id")
    ]
)
class PullRequest(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var title: String? = null,
    @Column(length = LONG_TEXT)
    var body: String? = null,
    @ManyToOne @JoinColumn(name = "to_project_id")
    var toProject: Project? = null,
    @ManyToOne @JoinColumn(name = "from_project_id")
    var fromProject: Project? = null,
    @Column(name = "to_branch")
    var toBranch: String? = null,
    @Column(name = "from_branch")
    var fromBranch: String? = null,
    @ManyToOne @JoinColumn(name = "contributor_id")
    var contributor: User? = null,
    @ManyToOne @JoinColumn(name = "receiver_id")
    var receiver: User? = null,
    var created: Date? = null,
    var updated: Date? = null,
    var received: Date? = null,
    var state: State? = null,
    @Column(name = "is_conflict")
    @ColumnDefault("false")
    var isConflict: Boolean? = false,
    @Column(name = "is_merging")
    @ColumnDefault("false")
    var isMerging: Boolean? = false,
    @Column(name = "last_commit_id")
    var lastCommitId: String? = null,
    @Column(name = "merged_commit_id_from")
    var mergedCommitIdFrom: String? = null,
    @Column(name = "merged_commit_id_to")
    var mergedCommitIdTo: String? = null,
    var number: Long? = null,
    // v1.6 Ebean은 트랜잭션 밖에서도 컬렉션을 읽을 수 있다. 병합 커밋 메시지(Reviewed-by)가 트랜잭션 밖에서 리뷰어를 읽으므로 즉시 불러온다.
    @ManyToMany(fetch = jakarta.persistence.FetchType.EAGER) @JoinTable(
        name = "pull_request_reviewers",
        joinColumns = [JoinColumn(name = "pull_request_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
        indexes = [
            Index(name = "fk_pull_request_reviewers_n4user_02", columnList = "user_id")
        ]
    )
    var reviewers: MutableSet<User> = mutableSetOf(),
    /**
     * 담당자이다. v1.6에 없는 추가 항목이다(로드맵 X-14, `decisions.md` D-34). 연결 테이블 `pull_request_assignee`에 저장하며 서비스 규칙으로 지금은 1명까지만
     * 받는다([PullRequestAssigneeService.MAX_ASSIGNEES]).
     */
    @ManyToMany @BatchSize(size = 50) @JoinTable(
        name = "pull_request_assignee",
        joinColumns = [JoinColumn(name = "pull_request_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
        indexes = [Index(name = "ix_pull_request_assignee_user", columnList = "user_id")]
    )
    var assignees: MutableSet<User> = mutableSetOf(),
    /** 라벨이다. 받는 프로젝트의 이슈 라벨만 붙는다(D-34). 연결 테이블 `pull_request_issue_label`에 저장한다. */
    @ManyToMany @BatchSize(size = 50) @JoinTable(
        name = "pull_request_issue_label",
        joinColumns = [JoinColumn(name = "pull_request_id")],
        inverseJoinColumns = [JoinColumn(name = "issue_label_id")],
        indexes = [Index(name = "ix_pull_request_issue_label_label", columnList = "issue_label_id")]
    )
    var labels: MutableSet<IssueLabel> = mutableSetOf(),
    /** 리뷰 판정 이력이다(추가 개발 항목 X-12, `decisions.md` D-35). 읽는 용도이며 서비스가 저장하면서 함께 채운다. 정책은 리뷰어별 최신 판정만 쓴다. */
    @OneToMany(mappedBy = "pullRequest") @BatchSize(size = 50)
    var reviewHistory: MutableList<PullRequestReview> = mutableListOf()
)

/** 풀 리퀘스트에 포함된 커밋이다. */
@Entity
@Table(
    name = "pull_request_commit",
    indexes = [
        Index(name = "ix_pull_request_commit_pullRequest_37", columnList = "pull_request_id")
    ]
)
class PullRequestCommit(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "pull_request_id")
    var pullRequest: PullRequest? = null,
    @Column(name = "commit_id")
    var commitId: String? = null,
    @Column(name = "author_date")
    var authorDate: Date? = null,
    var created: Date? = null,
    @Column(name = "commit_message", length = LONG_TEXT)
    var commitMessage: String? = null,
    @Column(name = "commit_short_id")
    var commitShortId: String? = null,
    @Column(name = "author_email")
    var authorEmail: String? = null,
    @Column(length = 7)
    var state: PullRequestCommitState? = null
)

/** 풀 리퀘스트 이벤트 기록이다. */
@Entity
@Table(
    name = "pull_request_event",
    indexes = [
        Index(name = "ix_pull_request_event_pullRequest_38", columnList = "pull_request_id")
    ]
)
class PullRequestEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "sender_login_id")
    var senderLoginId: String? = null,
    @ManyToOne @JoinColumn(name = "pull_request_id")
    var pullRequest: PullRequest? = null,
    @Column(name = "event_type", length = 34)
    var eventType: EventType? = null,
    var created: Date? = null,
    @Column(name = "old_value", length = LONG_TEXT)
    var oldValue: String? = null,
    @Column(name = "new_value", length = LONG_TEXT)
    var newValue: String? = null
)
