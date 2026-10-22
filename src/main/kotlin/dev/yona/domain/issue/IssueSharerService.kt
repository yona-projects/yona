package dev.yona.domain.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 이슈 공유자의 추가와 삭제이다. v1.6 `IssueApi.updateSharer`, `changeSharer*`, `addSharer`, `removeSharer`
 * (`app/controllers/api/IssueApi.java:1151-1260`)에 해당한다. 권한 확인은 호출하는 쪽이 한다.
 */
@Service
class IssueSharerService(
    private val sharers: IssueSharerRepository,
    private val users: UserRepository,
    private val projects: ProjectRepository,
    private val projectUsers: ProjectUserRepository,
    private val events: IssueEventService,
    private val publisher: ApplicationEventPublisher
) {

    /** 공유자 변경 결과이다. [name]은 사용자의 이름이나 프로젝트의 이름이다. */
    data class Change(val action: String, val name: String?)

    /**
     * 사용자 한 명을 공유자로 추가하거나 삭제한다. 사용자가 없으면 `null`이다.
     *
     * @param add `true`이면 추가, `false`이면 삭제
     */
    @Transactional
    fun changeByUser(issue: Issue, actor: User, loginId: String, add: Boolean, viewerLang: String?): Change? {
        val user = users.findByLoginIdIgnoreCase(loginId) ?: return null
        apply(issue, actor, user, add)
        return Change(if (add) "added" else "deleted", dev.yona.domain.user.UserDisplay.of(user, viewerLang))
    }

    /** 프로젝트의 구성원을 모두 공유자로 추가하거나 삭제한다. 프로젝트가 없으면 `null`이다. */
    @Transactional
    fun changeByProject(issue: Issue, actor: User, projectId: Long, add: Boolean): Change? {
        val project = projects.findById(projectId).orElse(null) ?: return null
        projectUsers.findMembersIncludingSiteManager(projectId).mapNotNull { it.user }.forEach { apply(issue, actor, it, add) }
        return Change(if (add) "added" else "deleted", project.name)
    }

    private fun apply(issue: Issue, actor: User, user: User, add: Boolean) {
        val issueId = requireNotNull(issue.id)
        val loginId = user.loginId.orEmpty()
        val existing = sharers.findFirstByIssueIdAndLoginId(issueId, loginId)
        if (add) {
            if (existing == null) sharers.save(IssueSharer(created = LocalDate.now(), loginId = loginId, user = user, issue = issue))
        } else if (existing != null) {
            sharers.delete(existing)
        }
        // v1.6은 이미 공유자이거나 아닌 경우에도 이벤트를 남기며, 중간 값을 합치지 않는다.
        val old = if (add) "" else loginId
        val new = if (add) loginId else ""
        events.record(issue, EventType.ISSUE_SHARER_CHANGED, old, new, actor.loginId, skipWaypoints = false)
        publisher.publishEvent(IssueChangedActivity(issue, EventType.ISSUE_SHARER_CHANGED, old, new, actor))
    }
}
