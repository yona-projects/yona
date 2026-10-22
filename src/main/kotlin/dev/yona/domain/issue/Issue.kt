package dev.yona.domain.issue

import dev.yona.domain.LONG_TEXT
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.State
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDate
import java.util.Date

/** 이슈이다. `(project_id, number)`는 유일하다. */
@Entity
@Table(
    name = "issue",
    indexes = [
        Index(name = "ix_issue_assignee_9", columnList = "assignee_id"),
        Index(name = "ix_issue_author_id_state", columnList = "author_id, state"),
        Index(name = "ix_issue_created_date", columnList = "created_date"),
        Index(name = "ix_issue_is_draft_1", columnList = "weight, is_draft, number, created_date"),
        Index(name = "ix_issue_is_draft_2", columnList = "is_draft, author_login_id, project_id"),
        Index(name = "ix_issue_milestone_8", columnList = "milestone_id"),
        Index(name = "ix_issue_parent_id", columnList = "parent_id"),
        Index(name = "ix_issue_project_7", columnList = "project_id"),
        Index(name = "ix_issue_weight", columnList = "weight")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_issue_1", columnNames = ["project_id", "number"])
    ]
)
class Issue(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var title: String? = null,
    @Column(length = LONG_TEXT)
    var body: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "updated_date")
    var updatedDate: Date? = null,
    @Column(name = "author_id")
    var authorId: Long? = null,
    @Column(name = "author_login_id")
    var authorLoginId: String? = null,
    @Column(name = "author_name")
    var authorName: String? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "project_id")
    var project: Project? = null,
    var number: Long? = null,
    @Column(name = "num_of_comments")
    var numOfComments: Int? = null,
    var state: State? = null,
    @Column(name = "due_date")
    var dueDate: Date? = null,
    @ManyToOne @JoinColumn(name = "milestone_id")
    var milestone: Milestone? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.SET_NULL) @JoinColumn(name = "assignee_id")
    var assignee: Assignee? = null,
    @Column(length = LONG_TEXT)
    var history: String? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.SET_NULL) @JoinColumn(name = "parent_id")
    var parent: Issue? = null,
    @JdbcTypeCode(SqlTypes.TINYINT)
    var weight: Int? = 0,
    @Column(name = "updated_by_author_id")
    var updatedByAuthorId: Long? = null,
    @Column(name = "is_draft")
    var isDraft: Boolean? = false,
    @ManyToMany @JoinTable(
        name = "issue_issue_label",
        joinColumns = [JoinColumn(name = "issue_id")],
        inverseJoinColumns = [JoinColumn(name = "issue_label_id")],
        indexes = [
            Index(name = "fk_issue_issue_label_issue_label_02", columnList = "issue_label_id")
        ]
    )
    var labels: MutableSet<IssueLabel> = mutableSetOf(),
    @ManyToMany @JoinTable(
        name = "issue_voter",
        joinColumns = [JoinColumn(name = "issue_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
        indexes = [
            Index(name = "ix_issue_voter_user_id", columnList = "user_id")
        ]
    )
    var voters: MutableSet<User> = mutableSetOf()
)

/** 이슈 담당자이다. 프로젝트별로 사용자를 담당자로 등록한다. */
@Entity
@Table(
    name = "assignee",
    indexes = [
        Index(name = "ix_assignee_project_2", columnList = "project_id"),
        Index(name = "ix_assignee_user_1", columnList = "user_id")
    ]
)
class Assignee(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null
)

/** 마일스톤이다. */
@Entity
@Table(
    name = "milestone",
    indexes = [
        Index(name = "ix_milestone_project_16", columnList = "project_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_milestone_1", columnNames = ["project_id", "title"])
    ]
)
class Milestone(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var title: String? = null,
    @Column(name = "due_date")
    var dueDate: Date? = null,
    @Column(length = LONG_TEXT)
    var contents: String? = null,
    var state: State? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null
)

/** 이슈 이벤트(상태, 담당자, 마일스톤 등의 변경 기록)이다. */
@Entity
@Table(
    name = "issue_event",
    indexes = [
        Index(name = "ix_issue_event_issue_11", columnList = "issue_id")
    ]
)
class IssueEvent(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var created: Date? = null,
    @Column(name = "sender_login_id")
    var senderLoginId: String? = null,
    @Column(name = "sender_email")
    var senderEmail: String? = null,
    @ManyToOne @JoinColumn(name = "issue_id")
    var issue: Issue? = null,
    @Column(name = "event_type", length = 34)
    var eventType: EventType? = null,
    @Column(name = "old_value", length = LONG_TEXT)
    var oldValue: String? = null,
    @Column(name = "new_value", length = LONG_TEXT)
    var newValue: String? = null
)

/** 이슈 공유 대상 사용자이다. */
@Entity
@Table(
    name = "issue_sharer",
    indexes = [
        Index(name = "ix_issue_sharer_issue_id", columnList = "issue_id"),
        Index(name = "ix_issue_sharer_login_id", columnList = "login_id"),
        Index(name = "ix_issue_sharer_user_id", columnList = "user_id")
    ]
)
class IssueSharer(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var created: LocalDate? = null,
    @Column(name = "login_id")
    var loginId: String? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "user_id")
    var user: User? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.CASCADE) @JoinColumn(name = "issue_id")
    var issue: Issue? = null
)

/** 프로젝트에 속하지 않는 전역 라벨(언어, 라이선스, OS 등)이다. `(category, name)`은 유일하다. */
@Entity
@Table(
    name = "label",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_label_1", columnNames = ["category", "name"])
    ]
)
class Label(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var category: String? = null,
    var name: String? = null
)

/** 이슈 라벨 분류이다. 배타적 분류는 한 이슈에 하나의 라벨만 허용한다. */
@Entity
@Table(
    name = "issue_label_category",
    indexes = [
        Index(name = "ix_issue_label_category_project_14", columnList = "project_id")
    ]
)
class IssueLabelCategory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    var name: String? = null,
    @Column(name = "is_exclusive")
    var isExclusive: Boolean? = false
)

/** 프로젝트의 이슈 라벨이다. */
@Entity
@Table(
    name = "issue_label",
    indexes = [
        Index(name = "ix_issue_label_category_12", columnList = "category_id"),
        Index(name = "ix_issue_label_project_13", columnList = "project_id")
    ]
)
class IssueLabel(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "category_id")
    var category: IssueLabelCategory? = null,
    var color: String? = null,
    var name: String? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null
)
