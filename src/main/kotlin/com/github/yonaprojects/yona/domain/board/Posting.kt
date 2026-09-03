package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.support.AbstractPosting
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "posting",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "number"])]
)
class Posting(
    id: Long? = null,
    title: String = "",
    body: String? = null,
    history: String? = null,
    createdDate: Instant? = null,
    updatedDate: Instant? = null,
    authorId: Long? = null,
    authorLoginId: String? = null,
    authorName: String? = null,
    updatedByAuthorId: Long? = null,
    updatedByAuthorLoginId: String? = null,
    updatedByAuthorName: String? = null,
    project: Project,
    number: Long? = null,
    numOfComments: Int = 0,

    var notice: Boolean = false,
    var readme: Boolean = false,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Posting? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "posting_issue_label",
        joinColumns = [JoinColumn(name = "posting_id")],
        inverseJoinColumns = [JoinColumn(name = "issue_label_id")]
    )
    var labels: MutableSet<IssueLabel> = mutableSetOf()
) : AbstractPosting(
    id = id,
    title = title,
    body = body,
    history = history,
    createdDate = createdDate,
    updatedDate = updatedDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    updatedByAuthorId = updatedByAuthorId,
    updatedByAuthorLoginId = updatedByAuthorLoginId,
    updatedByAuthorName = updatedByAuthorName,
    project = project,
    number = number,
    numOfComments = numOfComments
)
