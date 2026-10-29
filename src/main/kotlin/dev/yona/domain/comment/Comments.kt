package dev.yona.domain.comment

import dev.yona.domain.LONG_TEXT
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.issue.Issue
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import jakarta.persistence.*
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.Date

/** 이슈 댓글이다. */
@Entity
@Table(
    name = "issue_comment",
    indexes = [
        Index(name = "ix_issue_comment_author_id", columnList = "author_id"),
        Index(name = "ix_issue_comment_issue_10", columnList = "issue_id"),
        Index(name = "ix_issue_comment_project_id", columnList = "project_id"),
        Index(name = "ix_issue_comment_parent_id", columnList = "parent_comment_id")
    ]
)
class IssueComment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(length = LONG_TEXT)
    var contents: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "author_id")
    var authorId: Long? = null,
    @Column(name = "author_login_id")
    var authorLoginId: String? = null,
    @Column(name = "author_name")
    var authorName: String? = null,
    @ManyToOne @JoinColumn(name = "issue_id")
    var issue: Issue? = null,
    @Column(name = "project_id", nullable = false)
    var projectId: Long = 0,
    @ManyToOne @OnDelete(action = OnDeleteAction.SET_NULL) @JoinColumn(name = "parent_comment_id")
    var parentComment: IssueComment? = null,
    @ManyToMany @JoinTable(
        name = "issue_comment_voter",
        joinColumns = [JoinColumn(name = "issue_comment_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
        indexes = [
            Index(name = "ix_issue_comment_voter_user_id", columnList = "user_id")
        ]
    )
    var voters: MutableSet<User> = mutableSetOf()
)

/**
 * 코드 리뷰 스레드이다. 하나의 테이블에 세 종류를 저장하며 `dtype` 컬럼으로 구분한다.
 *
 * - `ranged`: 코드 범위에 달린 스레드([CodeCommentThread])
 * - `non_ranged`: 파일 단위 스레드([NonRangedCodeCommentThread])
 * - `simple`: 풀 리퀘스트 전체에 달린 일반 스레드([SimpleCommentThread])
 */
@Entity
@Table(
    name = "comment_thread",
    indexes = [
        Index(name = "ix_comment_thread_project_4", columnList = "project_id"),
        Index(name = "ix_comment_thread_pullRequest_3", columnList = "pull_request_id")
    ]
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "dtype", length = 10)
@org.hibernate.annotations.SQLRestriction("pending_date is null")
abstract class CommentThread(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(name = "author_id")
    var authorId: Long? = null,
    @Column(name = "author_login_id")
    var authorLoginId: String? = null,
    @Column(name = "author_name")
    var authorName: String? = null,
    @Column(length = 6)
    var state: ThreadState? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @ManyToOne @JoinColumn(name = "pull_request_id")
    var pullRequest: PullRequest? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    @OneToMany(mappedBy = "thread")
    var reviewComments: MutableList<ReviewComment> = mutableListOf(),
    @ManyToMany @JoinTable(
        name = "comment_thread_n4user",
        joinColumns = [JoinColumn(name = "comment_thread_id")],
        inverseJoinColumns = [JoinColumn(name = "n4user_id")],
        indexes = [
            Index(name = "fk_comment_thread_n4user_n4user_02", columnList = "n4user_id")
        ]
    )
    var codeAuthors: MutableSet<User> = mutableSetOf(),
    /**
     * 임시 저장 리뷰(추가 개발 항목 X-26, `decisions.md` D-38)에서 아직 제출하지 않은 새 스레드이면 임시로 저장한 시각이다. 공개된 스레드는 `null`이다.
     * 엔티티에 항상 적용되는 조건(`@SQLRestriction`) 때문에 값이 있는 행은 모든 읽기에서 보이지 않는다(임시 댓글은 `PendingReviewReader`만 읽는다).
     * v1.6 파일에는 없는 컬럼이다. 불리언 대신 시각을 쓰는 것은 숨김 조건을 `is null`로 쓰면 모든 DB에서 같은 SQL이기 때문이다(불리언 상수는 DB마다 다르다).
     */
    @Column(name = "pending_date")
    var pendingDate: Date? = null
) {
    /** 임시 저장 중(아직 제출하지 않음)이면 `true`이다. */
    val isPending: Boolean @Transient get() = pendingDate != null
}

/** 코드 범위에 달린 리뷰 스레드이다. */
@Entity
@DiscriminatorValue("ranged")
class CodeCommentThread(
    @Column(name = "prev_commit_id")
    var prevCommitId: String? = null,
    @Column(name = "commit_id")
    var commitId: String? = null,
    var path: String? = null,
    @Column(name = "start_side", length = 1)
    var startSide: Side? = null,
    @Column(name = "start_line")
    var startLine: Int? = null,
    @Column(name = "start_column")
    var startColumn: Int? = null,
    @Column(name = "end_side", length = 1)
    var endSide: Side? = null,
    @Column(name = "end_line")
    var endLine: Int? = null,
    @Column(name = "end_column")
    var endColumn: Int? = null
) : CommentThread()

/** 파일 단위로 달린 리뷰 스레드이다. */
@Entity
@DiscriminatorValue("non_ranged")
class NonRangedCodeCommentThread(
    @Column(name = "prev_commit_id")
    var prevCommitId: String? = null,
    @Column(name = "commit_id")
    var commitId: String? = null,
    var path: String? = null
) : CommentThread()

/** 풀 리퀘스트 전체에 달린 일반 스레드이다. */
@Entity
@DiscriminatorValue("simple")
class SimpleCommentThread : CommentThread()

/** 리뷰 스레드에 달린 댓글이다. */
@org.hibernate.annotations.SQLRestriction("pending_date is null")
@Entity
@Table(
    name = "review_comment",
    indexes = [
        Index(name = "ix_review_comment_thread_41", columnList = "thread_id")
    ]
)
class ReviewComment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(length = LONG_TEXT)
    var contents: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "author_id")
    var authorId: Long? = null,
    @Column(name = "author_login_id")
    var authorLoginId: String? = null,
    @Column(name = "author_name")
    var authorName: String? = null,
    @ManyToOne @JoinColumn(name = "thread_id")
    var thread: CommentThread? = null,
    /** 임시 저장 리뷰에서 아직 제출하지 않은 댓글이면 임시로 저장한 시각이다. 공개 스레드에 단 임시 답글도 포함하며 모든 읽기에서 보이지 않는다(D-38). 공개된 댓글은 `null`이다. */
    @Column(name = "pending_date")
    var pendingDate: Date? = null
) {
    /** 임시 저장 중(아직 제출하지 않음)이면 `true`이다. */
    val isPending: Boolean @Transient get() = pendingDate != null
}

/** 커밋의 특정 줄에 단 댓글이다. */
@Entity
@Table(
    name = "commit_comment",
    indexes = [
        Index(name = "ix_commit_comment_project_5", columnList = "project_id")
    ]
)
class CommitComment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    var path: String? = null,
    var line: Int? = null,
    @Column(length = 1)
    var side: Side? = null,
    @Column(length = LONG_TEXT)
    var contents: String? = null,
    @Column(name = "created_date")
    var createdDate: Date? = null,
    @Column(name = "author_id")
    var authorId: Long? = null,
    @Column(name = "author_login_id")
    var authorLoginId: String? = null,
    @Column(name = "author_name")
    var authorName: String? = null,
    @Column(name = "commit_id")
    var commitId: String? = null
)
