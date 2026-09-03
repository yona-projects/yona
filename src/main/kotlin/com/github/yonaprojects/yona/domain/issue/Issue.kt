package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.support.AbstractPosting
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "issue",
    uniqueConstraints = [UniqueConstraint(columnNames = ["project_id", "number"])]
)
class Issue(
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: State = State.OPEN,

    var dueDate: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id")
    var milestone: Milestone? = null,

    @ManyToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinColumn(name = "assignee_id")
    var assignee: Assignee? = null,

    // yona Issue.java:138 "public Issue parent" 대응. 한 부모 이슈가 여러 하위이슈(subtask)를 가질 수
    // 있어야 하는데(findByParentIssueId()가 List<Issue>를 반환) @OneToOne으로 매핑돼 있으면 Hibernate가
    // parent_id 컬럼에 DB 레벨 UNIQUE 제약을 걸어 같은 부모를 가리키는 두 번째 하위이슈 INSERT부터
    // DataIntegrityViolationException이 난다 — 그룹7 #134/#135/#136 렌더링 테스트(하위이슈 2건 이상)로
    // 실제로 재현된 버그. @ManyToOne으로 수정한다(그룹7 TASK-0256).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: Issue? = null,

    var weight: Int = 0,
    var isDraft: Boolean = false,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "issue_issue_label",
        joinColumns = [JoinColumn(name = "issue_id")],
        inverseJoinColumns = [JoinColumn(name = "issue_label_id")]
    )
    var labels: MutableSet<IssueLabel> = mutableSetOf(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "issue_voter",
        joinColumns = [JoinColumn(name = "issue_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    var voters: MutableSet<User> = mutableSetOf(),

    @OneToMany(mappedBy = "issue", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var sharers: MutableSet<IssueSharer> = mutableSetOf()
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
