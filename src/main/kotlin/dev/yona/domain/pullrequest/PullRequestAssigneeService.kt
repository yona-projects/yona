package dev.yona.domain.pullrequest

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.user.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 담당자를 바꾼 결과이다. */
enum class AssignResult { CHANGED, UNCHANGED, NOT_CANDIDATE }

/**
 * 풀 리퀘스트의 담당자를 지정하고 해제한다. v1.6에 없는 추가 항목이다(로드맵 X-14, `decisions.md` D-34). 권한 확인(풀 리퀘스트 수정 권한)은 호출하는 쪽이 한다.
 *
 * 담당자는 연결 테이블 `pull_request_assignee`에 저장하며 지금은 [MAX_ASSIGNEES]명까지만 받는다. 후보는 v1.6 이슈 담당자 후보 규칙을 그대로 쓴다
 * ([IssueListService.assignableUsers]). 바뀌면 타임라인에 기록하고 알림과 웹훅을 내보낸다(`PULL_REQUEST_ASSIGNEE_CHANGED`).
 * 같은 사람을 다시 지정하거나 이미 비어 있는데 비우면 아무 일도 하지 않는다.
 */
@Service
class PullRequestAssigneeService(
    private val pullRequests: PullRequestRepository,
    private val events: PullRequestEventService,
    private val publisher: ApplicationEventPublisher,
    private val listService: IssueListService,
    private val accessControl: AccessControl
) {

    companion object {
        /** 한 풀 리퀘스트에 지정할 수 있는 담당자 수이다. 키울 때는 이 값과 화면의 선택을 다중 선택으로 고친다(D-34). */
        const val MAX_ASSIGNEES = 1
    }

    /**
     * 담당자를 [assignee]로 바꾼다. `null`이면 해제한다. 후보가 아니면 [AssignResult.NOT_CANDIDATE]이고 아무것도 바꾸지 않는다.
     * 한도가 1명이므로 기존 담당자는 새 담당자로 대체되고, 이벤트의 이전 값은 기존 담당자이다.
     */
    @Transactional
    fun set(pullRequest: PullRequest, assignee: User?, actor: User): AssignResult {
        val project = requireNotNull(pullRequest.toProject)
        if (assignee != null && candidates(pullRequest, actor).none { it.id == assignee.id }) return AssignResult.NOT_CANDIDATE
        val old = pullRequest.assignees.firstOrNull()
        if (old?.id == assignee?.id) return AssignResult.UNCHANGED
        pullRequest.assignees.clear()
        if (assignee != null) pullRequest.assignees.add(assignee)
        pullRequests.save(pullRequest)
        events.addAssignee(actor, pullRequest, old?.loginId, assignee?.loginId)
        publisher.publishEvent(PullRequestChangedActivity(pullRequest, EventType.PULL_REQUEST_ASSIGNEE_CHANGED, old?.loginId, assignee?.loginId, actor))
        return AssignResult.CHANGED
    }

    /** [viewer]가 이 풀 리퀘스트에 담당자로 고를 수 있는 사람들이다(받는 프로젝트의 이슈 담당자 후보). */
    @Transactional(readOnly = true)
    fun candidates(pullRequest: PullRequest, viewer: User?): List<User> = candidates(requireNotNull(pullRequest.toProject), viewer)

    /** 아직 만들기 전인 풀 리퀘스트의 받는 프로젝트 [project]에서 [viewer]가 담당자로 고를 수 있는 사람들이다(생성 폼). */
    @Transactional(readOnly = true)
    fun candidates(project: dev.yona.domain.project.Project, viewer: User?): List<User> = listService.assignableUsers(project, viewer, accessControl.isSiteManager(viewer))
}
