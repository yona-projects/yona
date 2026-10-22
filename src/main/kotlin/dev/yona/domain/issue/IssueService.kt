package dev.yona.domain.issue

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.notification.MentionService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 이슈를 만들 때 폼에서 받는 값이다.
 *
 * @property title 제목
 * @property body 본문. 없을 수 있다
 * @property dueDate 마감일. 그날의 마지막 초로 맞춘다
 * @property draft 초안으로 저장할지
 * @property parentIssueId 부모 이슈의 번호(PK). 없으면 최상위 이슈
 * @property assigneeLoginId 담당자 로그인 ID. 없거나 없는 사용자이면 담당자 없음
 * @property milestoneId 마일스톤 번호. 없거나 -1이면 마일스톤 없음
 * @property labelIds 붙일 라벨 번호. 이슈가 속한 프로젝트의 라벨만 붙인다
 */
data class NewIssue(
    val title: String,
    val body: String?,
    val dueDate: Date?,
    val draft: Boolean,
    val parentIssueId: Long?,
    val assigneeLoginId: String?,
    val milestoneId: Long?,
    val labelIds: Set<Long>
)

/**
 * 이슈의 생성과 변경을 담당한다. v1.6 `IssueApp`의 이슈 처리와 `AbstractPosting.save`, `Issue`
 * (`app/controllers/IssueApp.java`, `app/models/AbstractPosting.java`)에 해당한다.
 */
@Service
class IssueService(
    private val issues: IssueRepository,
    private val projects: ProjectRepository,
    private val users: UserRepository,
    private val assignees: AssigneeRepository,
    private val milestones: MilestoneRepository,
    private val labels: IssueLabelRepository,
    private val titleHeads: TitleHeadService,
    private val mentions: MentionService,
    private val publisher: ApplicationEventPublisher,
    private val entityManager: EntityManager
) {

    /**
     * 이슈를 만든다. 번호는 프로젝트의 마지막 이슈 번호에 1을 더해 매기고, 제목 머리말과 본문 멘션을 기록한다.
     * 초안이 아니면 [IssueCreatedActivity]를 발행한다.
     */
    @Transactional
    fun create(project: Project, author: User, input: NewIssue): Issue {
        val now = Date()
        val issue = Issue(
            title = input.title, body = input.body, project = project, state = if (input.draft) State.DRAFT else State.OPEN,
            createdDate = now, updatedDate = now, numOfComments = 0, isDraft = input.draft, weight = 0,
            authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
            dueDate = MilestoneService.lastSecondOfDay(input.dueDate),
            parent = input.parentIssueId?.let { issues.findById(it).orElse(null) },
            assignee = findOrCreateAssignee(project, input.assigneeLoginId),
            milestone = input.milestoneId?.let { milestones.findById(it).orElse(null) },
            labels = projectLabels(project, input.labelIds)
        )
        issue.number = nextIssueNumber(project)
        issues.save(issue)
        titleHeads.save(project, issue.title)
        if (issue.body != null) mentions.update(ResourceType.ISSUE_POST, requireNotNull(issue.id).toString(), issue.body)
        if (!input.draft) publisher.publishEvent(IssueCreatedActivity(issue, author))
        return issue
    }

    /** 프로젝트의 마지막 이슈 번호를 하나 올리고 새 번호를 돌려준다 (v1.6 `Project.increaseLastIssueNumber`). */
    @Transactional
    fun nextIssueNumber(project: Project): Long {
        val id = requireNotNull(project.id)
        entityManager.createQuery("update Project p set p.lastIssueNumber = coalesce(p.lastIssueNumber, 0) + 1 where p.id = :id").setParameter("id", id).executeUpdate()
        entityManager.flush()
        val number = entityManager.createQuery("select p.lastIssueNumber from Project p where p.id = :id", java.lang.Long::class.java).setParameter("id", id).singleResult.toLong()
        projects.findById(id).ifPresent { entityManager.refresh(it) }
        return number
    }

    /** 담당자 로그인 ID로 프로젝트의 담당자 행을 찾아 쓰거나 만든다. 없는 사용자이면 `null`이다 (v1.6 `Assignee.add`). */
    fun findOrCreateAssignee(project: Project, loginId: String?): Assignee? {
        if (loginId.isNullOrEmpty()) return null
        val user = users.findByLoginIdIgnoreCase(loginId) ?: return null
        return assignees.findFirstByUserIdAndProjectId(requireNotNull(user.id), requireNotNull(project.id))
            ?: assignees.save(Assignee(user = user, project = project))
    }

    /** 이 프로젝트의 라벨만 골라 모은다. 다른 프로젝트의 라벨이나 없는 번호는 건너뛴다. */
    private fun projectLabels(project: Project, ids: Set<Long>): MutableSet<IssueLabel> =
        labels.findAllById(ids).filter { it.project?.id == project.id }.toMutableSet()
}
