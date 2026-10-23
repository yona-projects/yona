package dev.yona.domain.board

import org.hibernate.annotations.ColumnDefault
import dev.yona.domain.LONG_TEXT
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.project.Project
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.Date

/** 게시판 글이다. `(project_id, number)`는 유일하다. */
@Entity
@Table(
    name = "posting",
    indexes = [
        Index(name = "ix_posting_parent_id", columnList = "parent_id"),
        Index(name = "ix_posting_project_21", columnList = "project_id")
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uq_posting_1", columnNames = ["project_id", "number"])
    ]
)
class Posting(
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
    @ManyToOne @JoinColumn(name = "project_id")
    var project: Project? = null,
    var number: Long? = null,
    @Column(name = "num_of_comments")
    var numOfComments: Int? = null,
    @ColumnDefault("false")
    var notice: Boolean? = false,
    @ColumnDefault("false")
    var readme: Boolean? = false,
    @Column(length = LONG_TEXT)
    var history: String? = null,
    @ManyToOne @OnDelete(action = OnDeleteAction.SET_NULL) @JoinColumn(name = "parent_id")
    var parent: Posting? = null,
    @Column(name = "updated_by_author_id")
    var updatedByAuthorId: Long? = null,
    @ManyToMany @JoinTable(
        name = "posting_issue_label",
        joinColumns = [JoinColumn(name = "posting_id")],
        inverseJoinColumns = [JoinColumn(name = "issue_label_id")],
        indexes = [
            Index(name = "fk_posting_issue_label_issue_label_02", columnList = "issue_label_id")
        ]
    )
    var labels: MutableSet<IssueLabel> = mutableSetOf()
)

/** 게시판 글의 댓글이다. */
@Entity
@Table(
    name = "posting_comment",
    indexes = [
        Index(name = "ix_posting_comment_author_id", columnList = "author_id"),
        Index(name = "ix_posting_comment_posting_22", columnList = "posting_id"),
        Index(name = "ix_posting_comment_project_id", columnList = "project_id"),
        Index(name = "ix_posting_comment_parent_id", columnList = "parent_comment_id")
    ]
)
class PostingComment(
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
    @ManyToOne @JoinColumn(name = "posting_id")
    var posting: Posting? = null,
    @Column(name = "project_id", nullable = false)
    var projectId: Long = 0,
    @ManyToOne @OnDelete(action = OnDeleteAction.SET_NULL) @JoinColumn(name = "parent_comment_id")
    var parentComment: PostingComment? = null
)
