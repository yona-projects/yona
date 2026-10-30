package dev.yona.domain.project

import org.hibernate.annotations.ColumnDefault
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.issue.Label
import dev.yona.domain.user.Role
import dev.yona.domain.user.User
import jakarta.persistence.*
import org.hibernate.annotations.Fetch
import org.hibernate.annotations.FetchMode
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.Date

/** 프로젝트이다. 포크한 프로젝트는 `originalProject`로 원본을 가리킨다. */
@Entity
@Table(
    name = "project",
    indexes = [
        Index(name = "ix_project_organization_24", columnList = "organization_id"),
        Index(name = "ix_project_originalProject_23", columnList = "original_project_id")
    ]
)
class Project(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null,
    var overview: String? = null,
    var vcs: String? = null,
    var siteurl: String? = null,
    var owner: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "last_issue_number")
    var lastIssueNumber: Long? = 0,
    @Column(name = "last_posting_number")
    var lastPostingNumber: Long? = 0,
    @ManyToOne @JoinColumn(name = "original_project_id")
    var originalProject: Project? = null,
    @Column(name = "last_pushed_date")
    var lastPushedDate: Date? = null,
    @Column(name = "default_reviewer_count")
    var defaultReviewerCount: Int? = 1,
    @Column(name = "is_using_reviewer_count")
    @ColumnDefault("false")
    var isUsingReviewerCount: Boolean? = false,
    /**
     * 새 커밋이 올라오면 오래된 승인을 해제할지이다(추가 개발 항목 X-12, `decisions.md` D-35 5번). v1.6은 항상 비웠으므로 기본은 켬이다.
     * `null`(열이 없던 옛 행)도 켬으로 본다.
     */
    @Column(name = "is_dismissing_stale_approvals")
    @ColumnDefault("true")
    var isDismissingStaleApprovals: Boolean? = true,
    /**
     * 허용하는 풀 리퀘스트 병합 방식이다(추가 개발 항목 X-28, `decisions.md` D-41). 새 프로젝트는 세 가지 모두 허용하고, 이 열이 없던 옛 행은 DB 기본값으로
     * 병합 커밋만 허용한다(v1.6과 같은 동작). `null`도 같게 본다.
     */
    @Column(name = "allow_merge_commit")
    @ColumnDefault("true")
    var allowMergeCommit: Boolean? = true,
    @Column(name = "allow_squash_merge")
    @ColumnDefault("false")
    var allowSquashMerge: Boolean? = true,
    @Column(name = "allow_rebase_merge")
    @ColumnDefault("false")
    var allowRebaseMerge: Boolean? = true,
    /** 병합 화면이 처음 고르는 방식의 이름이다(`merge`, `squash`, `rebase`). 허용한 방식 중 하나여야 한다. */
    @Column(name = "default_merge_method", length = 10)
    @ColumnDefault("'merge'")
    var defaultMergeMethod: String? = "merge",
    @ManyToOne @JoinColumn(name = "organization_id")
    var organization: Organization? = null,
    @Column(name = "project_scope", length = 9)
    var projectScope: ProjectScope? = null,
    @Column(name = "previous_owner_login_id")
    var previousOwnerLoginId: String? = null,
    @Column(name = "previous_name")
    var previousName: String? = null,
    @Column(name = "previous_name_changed_time")
    var previousNameChangedTime: Long? = null,
    @Column(name = "is_code_accessible_member_only")
    @ColumnDefault("false")
    var isCodeAccessibleMemberOnly: Boolean? = false,
    @ManyToMany @JoinTable(
        name = "project_label",
        joinColumns = [JoinColumn(name = "project_id")],
        inverseJoinColumns = [JoinColumn(name = "label_id")],
        indexes = [
            Index(name = "fk_project_label_label_02", columnList = "label_id")
        ]
    )
    var labels: MutableSet<Label> = mutableSetOf()
) {

    /**
     * 이 프로젝트의 브랜치 보호 규칙이다(추가 개발 항목 X-32, `decisions.md` D-42). 트랜잭션 밖(풀 리퀘스트 병합은 트랜잭션을 걸지 않는다)에서도 읽도록 즉시 불러온다.
     */
    @OneToMany(mappedBy = "project", fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    var branchProtectionRules: MutableList<BranchProtectionRule> = mutableListOf()

    /**
     * [branch]로 가는 풀 리퀘스트와 푸시에 적용할 보호 규칙이다(추가 개발 항목 X-32, X-33, `decisions.md` D-42, D-45). 이름이 정확히 같은 규칙이 먼저이고,
     * 없으면 `*` 패턴 중 패턴이 가장 긴 규칙이다. 맞는 규칙이 없으면 `null`이고 아무것도 제한하지 않는다(GitHub 기본 동작).
     * v1.6의 프로젝트 전체 리뷰어 설정(`is_using_reviewer_count`, `default_reviewer_count`)은 더는 보지 않는다.
     */
    fun protectionFor(branch: String?): BranchProtectionRule? {
        val matching = branchProtectionRules.filter { it.matches(branch) }
        return matching.firstOrNull { it.isExact } ?: matching.maxByOrNull { it.pattern?.length ?: 0 }
    }

    /** 공개 프로젝트이면 `true`이다. */
    fun isPublic(): Boolean = projectScope == ProjectScope.PUBLIC

    /** 보호 프로젝트(같은 조직 구성원에게만 공개)이면 `true`이다. */
    fun isProtected(): Boolean = projectScope == ProjectScope.PROTECTED

    /**
     * 이 프로젝트가 허용하는 병합 방식이다(`decisions.md` D-41). 병합 커밋은 `null`이어도 허용으로, 스쿼시와 리베이스는 `null`이면 불허로 본다(옛 행은 v1.6처럼 병합 커밋만).
     */
    fun allowedMergeMethods(): Set<dev.yona.domain.pullrequest.MergeMethod> = buildSet {
        if (allowMergeCommit != false) add(dev.yona.domain.pullrequest.MergeMethod.MERGE)
        if (allowSquashMerge == true) add(dev.yona.domain.pullrequest.MergeMethod.SQUASH)
        if (allowRebaseMerge == true) add(dev.yona.domain.pullrequest.MergeMethod.REBASE)
    }

    /** 병합 화면이 처음 고르는 방식이다. 설정한 기본 방식이 허용되지 않으면 허용한 방식 중 병합 커밋, 스쿼시, 리베이스 순서로 첫 번째이다. */
    fun defaultMergeMethodOrFirst(): dev.yona.domain.pullrequest.MergeMethod {
        val allowed = allowedMergeMethods()
        return dev.yona.domain.pullrequest.MergeMethod.of(defaultMergeMethod)?.takeIf { it in allowed } ?: allowed.firstOrNull() ?: dev.yona.domain.pullrequest.MergeMethod.MERGE
    }

    /** 비공개 프로젝트이면 `true`이다. */
    fun isPrivate(): Boolean = projectScope == ProjectScope.PRIVATE

    /** 조직에 속한 프로젝트이면 `true`이다. */
    fun hasGroup(): Boolean = organization != null

    /** 로그인 ID가 소유자 이름과 같으면(대소문자 무시) 소유자이다 (v1.6 `Project.isOwner`). */
    fun isOwner(user: User): Boolean = owner?.lowercase() == user.loginId?.lowercase()

    /**
     * 이전 위치가 없거나 마지막 이름 변경이 24시간(실제로는 25시간 이상)을 넘었으면 지금의 위치를 이전 위치로 기록한다
     * (v1.6 `recordRenameOrTransferHistoryIfLastChangePassed24HoursFrom`). 24시간 안에 다시 바꾸면 처음의 이전 위치를 유지한다.
     * 이름이나 소유자를 바꾸기 **전에** 부른다.
     */
    fun recordPreviousPlaceIfLastChangePassed24Hours(now: Long = System.currentTimeMillis()) {
        val changedAt = previousNameChangedTime
        // v1.6 `hasPassed24hoursFrom`: 경과 시간(시간 단위 내림)이 24보다 클 때이다.
        if (changedAt == null || (now - changedAt) / MILLIS_PER_HOUR > 24) {
            previousNameChangedTime = now
            previousName = name
            previousOwnerLoginId = owner
        }
    }

    /** 저장소 종류를 바꿀 때 가는 종류이다. Git이면 Subversion, 아니면 Git이다 (v1.6 `nextVCS`). */
    fun nextVcs(): String = if (vcs == "GIT") "Subversion" else "GIT"

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000L
    }
}

/** 조직이다. */
@Entity
@Table(name = "organization")
class Organization(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String? = null,
    var created: Date? = null,
    var descr: String? = null
)

/** 조직 구성원과 그 역할이다. */
@Entity
@Table(
    name = "organization_user",
    indexes = [
        Index(name = "ix_organization_user_organization_19", columnList = "organization_id"),
        Index(name = "ix_organization_user_role_20", columnList = "role_id"),
        Index(name = "ix_organization_user_user_18", columnList = "user_id")
    ]
)
class OrganizationUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @JoinColumn(name = "organization_id")
    var organization: Organization? = null,
    @ManyToOne @JoinColumn(name = "role_id")
    var role: Role? = null
)

/** 프로젝트 구성원과 그 역할이다. */
@Entity
@Table(
    name = "project_user",
    indexes = [
        Index(name = "ix_project_user_project_29", columnList = "project_id"),
        Index(name = "ix_project_user_role_30", columnList = "role_id"),
        Index(name = "ix_project_user_user_28", columnList = "user_id")
    ]
)
class ProjectUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @ManyToOne @JoinColumn(name = "role_id")
    var role: Role? = null
)

/** 프로젝트 메뉴 표시 설정이다. */
@Entity
@Table(
    name = "project_menu_setting",
    indexes = [
        Index(name = "ix_project_menu_setting_project_25", columnList = "project_id")
    ]
)
class ProjectMenuSetting(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @ColumnDefault("false")
    var code: Boolean? = false,
    @ColumnDefault("false")
    var issue: Boolean? = false,
    @Column(name = "pull_request")
    @ColumnDefault("false")
    var pullRequest: Boolean? = false,
    @ColumnDefault("false")
    var review: Boolean? = false,
    @ColumnDefault("false")
    var milestone: Boolean? = false,
    @ColumnDefault("false")
    var board: Boolean? = false
)

/** 프로젝트 이전 요청이다. */
@Entity
@Table(
    name = "project_transfer",
    indexes = [
        Index(name = "ix_project_transfer_project_27", columnList = "project_id"),
        Index(name = "ix_project_transfer_sender_26", columnList = "sender_id")
    ]
)
class ProjectTransfer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "sender_id")
    var sender: User? = null,
    var destination: String? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    var requested: Date? = null,
    @Column(name = "confirm_key")
    var confirmKey: String? = null,
    @ColumnDefault("false")
    var accepted: Boolean? = false,
    @Column(name = "new_project_name")
    var newProjectName: String? = null
)

/** 최근 푸시된 브랜치이다. */
@Entity
@Table(
    name = "project_pushed_branch",
    indexes = [
        Index(name = "ix_project_pushed_branch_project_39", columnList = "project_id")
    ]
)
class PushedBranch(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "pushed_date")
    var pushedDate: Date? = null,
    var name: String? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null
)

/** 사용자가 최근 방문한 프로젝트이다. `(user_id, project_id)`는 유일하다. */
@Entity
@Table(
    name = "recent_project",
    indexes = [
        Index(name = "fk_recent_project_project_2", columnList = "project_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_recent_project_1", columnNames = ["user_id", "project_id"])
    ]
)
class RecentProject(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "user_id")
    var userId: Long? = null,
    var owner: String? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "project_id", foreignKey = ForeignKey(foreignKeyDefinition = "foreign key (project_id) references project (id) on update cascade"))
    var project: Project? = null,
    @Column(name = "project_name")
    var projectName: String? = null
)

/** 이슈 제목 머리말 후보와 사용 빈도이다. */
@Entity
@Table(
    name = "title_head",
    indexes = [
        Index(name = "ix_title_head_head_keyword", columnList = "head_keyword"),
        Index(name = "ix_title_head_project_id", columnList = "project_id")
    ]
)
class TitleHead(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(name = "head_keyword")
    var headKeyword: String? = null,
    var frequency: Int? = null
)

/** 프로젝트 웹훅이다. */
@Entity
@Table(
    name = "webhook",
    indexes = [
        Index(name = "ix_webhook_git_push_only", columnList = "git_push"),
        Index(name = "ix_webhook_project_47", columnList = "project_id"),
        Index(name = "ix_webhook_webhook_type", columnList = "webhook_type")
    ]
)
class Webhook(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @Column(name = "payload_url", length = 2000)
    var payloadUrl: String? = null,
    @Column(length = 250)
    var secret: String? = null,
    @Column(name = "created_at")
    var createdAt: Date? = null,
    @Column(name = "git_push")
    var gitPush: Boolean? = null,
    @Column(name = "webhook_type")
    @ColumnDefault("1")
    var webhookType: WebhookType? = WebhookType.DETAIL_SLACK
)

/** 웹훅 알림을 하나의 스레드로 묶기 위한 식별자이다. */
@Entity
@Table(
    name = "webhook_thread",
    indexes = [
        Index(name = "ix_webhook_thread_resource_2", columnList = "resource_type, resource_id"),
        Index(name = "ix_webhook_thread_webhook_1", columnList = "webhook_id")
    ]
)
class WebhookThread(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "webhook_id")
    var webhook: Webhook? = null,
    @Column(name = "resource_type", length = 20)
    var resourceType: dev.yona.domain.enumeration.ResourceType? = null,
    @Column(name = "resource_id")
    var resourceId: String? = null,
    @Column(name = "thread_id", length = 2000)
    var threadId: String? = null,
    @Column(name = "created_at")
    var createdAt: Date? = null
)

/**
 * v1.6 스키마에는 남아 있지만 v1.6 코드에는 대응하는 엔티티가 없는 테이블이다.
 * 방문 기록은 `recent_project`로 대체되었다. 스키마를 v1.6과 같게 유지하기 위해 매핑만 둔다.
 */
@Entity
@Table(
    name = "recently_visited_projects",
    indexes = [
        Index(name = "ix_recently_visited_projects_user_40", columnList = "user_id")
    ]
)
class RecentlyVisitedProjects(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null
)

/** [RecentlyVisitedProjects]와 같은 이유로 유지하는 방문 기록 테이블이다. */
@Entity
@Table(
    name = "project_visitation",
    indexes = [
        Index(name = "ix_project_visitation_project_31", columnList = "project_id"),
        Index(name = "ix_project_visitation_recentlyVisitedProjects_32", columnList = "recently_visited_projects_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_project_visitation_1", columnNames = ["project_id", "recently_visited_projects_id"])
    ]
)
class ProjectVisitation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "project_id", foreignKey = ForeignKey(foreignKeyDefinition = "foreign key (project_id) references project (id) on update cascade"))
    var project: Project? = null,
    @ManyToOne @JoinColumn(name = "recently_visited_projects_id")
    var recentlyVisitedProjects: RecentlyVisitedProjects? = null,
    var visited: Date? = null
)
