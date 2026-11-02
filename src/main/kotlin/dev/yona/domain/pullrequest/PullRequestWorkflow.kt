package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.State
import dev.yona.domain.user.User
import org.eclipse.jgit.lib.PersonIdent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** 풀 리퀘스트를 받아들인 결과이다. */
enum class AcceptResult {
    /** 병합했다. */
    MERGED,

    /** 충돌해서 병합하지 못했다. 요청은 그대로이다. */
    CONFLICT,

    /** 프로젝트가 허용하지 않은 병합 방식이다(추가 개발 항목 X-28). 아무것도 바꾸지 않았다. */
    METHOD_NOT_ALLOWED,

    /** 갱신 제한이 켜진 규칙 묶음이 병합을 막는다(추가 개발 항목 X-34, A-259). 요청은 그대로이다. */
    UPDATES_RESTRICTED,

    /** 리뷰어가 모자란다. 요청은 그대로이다. */
    NOT_REVIEWED
}

/**
 * 풀 리퀘스트의 상태를 바꾸는 일(받아들이기, 닫기, 다시 열기)과 수정을 담당한다. v1.6 `PullRequestApp.accept`, `close`, `open`, `editPullRequest`와
 * `PullRequest.merge`, `close`, `reopen`, `updateWith`, `deleteFromBranch`, `restoreFromBranch`
 * (`app/controllers/PullRequestApp.java:384-560`, `app/models/PullRequest.java`)에 해당한다. 권한 확인은 호출하는 쪽이 한다.
 */
@Service
class PullRequestWorkflow(
    private val pullRequests: PullRequestRepository,
    private val merger: PullRequestMerger,
    private val processor: PullRequestMergeProcessor,
    private val runner: BackgroundJobRunner,
    private val events: PullRequestEventService,
    private val service: PullRequestService,
    private val branches: PullRequestBranchService,
    private val publisher: ApplicationEventPublisher,
    private val pushedBranches: dev.yona.domain.project.PushedBranchRepository,
    private val issueCloser: PullRequestIssueCloser,
    private val threads: dev.yona.domain.comment.CommentThreadRepository,
    private val metrics: dev.yona.observability.CollaborationMetrics,
    private val signatureCheck: PullRequestSignatureCheck
) {

    /**
     * 요청을 받아들여 받는 브랜치에 병합한다. [method]로 병합 방식을 고르고(추가 항목 X-28, `decisions.md` D-41) 비우면 프로젝트의 기본 방식이다. 먼저 병합 중 표시를 켜서 다른 요청을 막고, 리뷰어 수를 쓰는 프로젝트에서 리뷰어가 모자라면 아무것도 하지 않는다.
     * 충돌하면 병합하지 않고 표시만 끈다. 병합하면 상태를 `MERGED`로 바꾸고 병합 기준 커밋을 저장하며 상태 변경 알림과 타임라인을 남긴 뒤, 같은 브랜치와 관련된
     * 다른 요청의 병합 검사를 다시 시작한다. 병합한 뒤에는 제목, 본문, 커밋 메시지에서 `closes #번호`로 가리킨 이슈를 닫는다([PullRequestIssueCloser], X-11).
     *
     * v1.6처럼 병합 중 표시를 가장 먼저 켜고 저장한다. 그래서 리뷰어가 모자라 400을 돌려줄 때나 병합 중 예외가 날 때도 표시가 꺼지지 않고
     * 요청이 계속 병합 중으로 남는다([refactoring-backlog.md] R-76, 선생님 결정 2026-10-26). v1.6 `accept`의 병합은 트랜잭션 밖에서 돌고
     * 저장이 즉시 반영되므로 이 메서드에도 일부러 트랜잭션을 걸지 않는다.
     */
    fun accept(
        pullRequest: PullRequest, accepter: User, canBypass: (List<dev.yona.domain.project.Ruleset>) -> Boolean = { false }, method: MergeMethod? = null, commitTitle: String? = null, commitMessage: String? = null
    ): AcceptResult = acceptInternal(pullRequest, accepter, canBypass, method, commitTitle, commitMessage).also { metrics.merge(it.name) }

    private fun acceptInternal(
        pullRequest: PullRequest, accepter: User, canBypass: (List<dev.yona.domain.project.Ruleset>) -> Boolean, method: MergeMethod?, commitTitle: String?, commitMessage: String?
    ): AcceptResult {
        val toProject = requireNotNull(pullRequest.toProject)
        // 추가 항목(X-28, D-41): 방식을 정하지 않으면 프로젝트의 기본 방식이다. 허용하지 않은 방식은 아무것도 바꾸기 전에 거절한다
        // 받는 브랜치의 규칙이 선형 이력을 요구하면 병합 커밋은 빠진다(X-33, D-45)
        val chosen = method ?: pullRequest.defaultMergeMethodOrFirst()
        if (chosen !in pullRequest.allowedMergeMethods()) return AcceptResult.METHOD_NOT_ALLOWED
        // v1.6처럼 병합 중 표시를 먼저 저장한다. 조건에 막혀 돌아가는 경로에서도 표시가 남는 것은 v1.6의 결함을 같게 재현한 것이다(`refactoring-backlog.md` R-76, 선생님 결정 2026-10-26)
        pullRequest.isMerging = true
        pullRequests.save(pullRequest)
        // 추가 항목(X-12, D-35 11번): 프로젝트 관리자와 사이트 관리자는 리뷰어 수와 변경 요청에 막혀도 병합할 수 있다(`canBypass`)
        // 추가 항목(X-32, D-42): 받는 브랜치에 보호 규칙이 있을 때만 승인 수와 변경 요청으로 막는다
        // 추가 항목(X-34, D-46): 규칙 묶음이 켠 조건(승인 수, 변경 요청, 열린 대화)이 남아 있으면 막는다. 막고 있는 규칙 묶음을 모두 우회할 수 있는 사용자만 통과한다
        val openConversations = threads.countByPullRequestIdAndState(requireNotNull(pullRequest.id), dev.yona.domain.enumeration.ThreadState.OPEN).toInt()
        val blocking = pullRequest.blockingRulesets(openConversations, signatureCheck.unverifiedCount(pullRequest))
        if (blocking.isNotEmpty() && !canBypass(blocking)) return if (blocking.any { it.restrictUpdates == true }) AcceptResult.UPDATES_RESTRICTED else AcceptResult.NOT_REVIEWED

        val result = merger.merge(pullRequest, PersonIdent(accepter.name, accepter.email), chosen, commitTitle, commitMessage)
        pullRequest.isMerging = false
        if (result == null) {
            pullRequests.save(pullRequest)
            return AcceptResult.CONFLICT
        }

        pullRequest.mergedCommitIdFrom = result.attempt.leftParent.name
        pullRequest.mergedCommitIdTo = requireNotNull(result.mergeCommitId).name
        pullRequest.state = State.MERGED
        pullRequest.received = Date()
        pullRequest.receiver = accepter
        pullRequests.save(pullRequest)
        publisher.publishEvent(PullRequestChangedActivity(pullRequest, EventType.PULL_REQUEST_STATE_CHANGED, State.OPEN.state, State.MERGED.state, accepter))
        events.addState(accepter, pullRequest, State.MERGED, chosen)
        issueCloser.closeReferenced(pullRequest, accepter)
        recheckRelated(toProject.id!!, requireNotNull(pullRequest.toBranch), requireNotNull(accepter.id))
        return AcceptResult.MERGED
    }

    /** 요청을 닫는다. 받은 사람은 닫은 사람이다. 상태 변경 알림과 타임라인을 남긴다. */
    @Transactional
    fun close(pullRequest: PullRequest, actor: User) {
        val before = pullRequest.state ?: State.OPEN
        change(pullRequest, State.CLOSED, actor)
        record(pullRequest, before, State.CLOSED, actor)
    }

    /**
     * 닫힌 요청을 다시 연다. 병합됐거나 이미 열린 요청이면 `false`이다. 열고 나면 병합 검사를 다시 시작한다.
     */
    @Transactional
    fun reopen(pullRequest: PullRequest, actor: User): Boolean {
        if (pullRequest.merged || pullRequest.open) return false
        val before = pullRequest.state ?: State.CLOSED
        change(pullRequest, State.OPEN, actor)
        // v1.6 `PullRequest.reopen`은 `PushedBranch.removeByPullRequestFrom`을 불러 보내는 쪽의 최근 푸시 브랜치 기록을 지운다.
        pushedBranches.findFirstByProjectIdAndName(requireNotNull(pullRequest.fromProject?.id), requireNotNull(pullRequest.fromBranch))?.let { pushedBranches.delete(it) }
        record(pullRequest, before, State.OPEN, actor)
        val id = requireNotNull(pullRequest.id)
        val actorId = requireNotNull(actor.id)
        runner.run("merging of pull request $id", { processor.start(id) }, { processor.process(id, actorId, false) })
        return true
    }

    /**
     * 제목, 본문, 브랜치를 고친다. 이슈 언급 이벤트는 지우고 새 제목과 본문으로 다시 남긴다. 병합 검사는 다시 하지 않는다(v1.6 `updateWith`).
     */
    @Transactional
    fun update(pullRequest: PullRequest, title: String, body: String, fromBranch: String, toBranch: String) {
        service.deleteIssueEvents(pullRequest)
        pullRequest.toBranch = toBranch
        pullRequest.fromBranch = fromBranch
        pullRequest.title = title
        pullRequest.body = body
        pullRequests.save(pullRequest)
        service.addIssueEvents(pullRequest)
    }

    /** 병합된 요청의 보내는 브랜치를 지우고 마지막 커밋 번호를 요청에 남긴다. 지울 수 없으면 `lastCommitId`가 `null`이 된다(v1.6 그대로). */
    @Transactional
    fun deleteFromBranch(pullRequest: PullRequest) {
        pullRequest.lastCommitId = branches.delete(pullRequest)
        pullRequests.save(pullRequest)
    }

    /** 지워진 보내는 브랜치를 마지막 커밋에서 되살린다. */
    @Transactional
    fun restoreFromBranch(pullRequest: PullRequest) {
        branches.restore(pullRequest)
    }

    private fun change(pullRequest: PullRequest, state: State, actor: User) {
        pullRequest.state = state
        pullRequest.received = Date()
        pullRequest.receiver = actor
        pullRequests.save(pullRequest)
    }

    private fun record(pullRequest: PullRequest, before: State, after: State, actor: User) {
        publisher.publishEvent(PullRequestChangedActivity(pullRequest, EventType.PULL_REQUEST_STATE_CHANGED, before.state, after.state, actor))
        events.addFromNotification(pullRequest, actor, EventType.PULL_REQUEST_STATE_CHANGED, before.state, after.state)
    }

    /** 병합으로 받는 브랜치가 바뀌었으므로 그 브랜치와 관련된 다른 요청의 병합 검사를 다시 한다 (v1.6 `RelatedPullRequestMergingActor`). */
    private fun recheckRelated(projectId: Long, branch: String, accepterId: Long) {
        val ids = pullRequests.findRelated(projectId, branch).mapNotNull { it.id }
        if (ids.isEmpty()) return
        runner.run("merging of pull requests related to $branch", { ids.forEach { processor.start(it) } }, { ids.forEach { processor.process(it, accepterId, false) } })
    }
}
