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
    private val issueCloser: PullRequestIssueCloser
) {

    /**
     * 요청을 받아들여 받는 브랜치에 병합한다. 먼저 병합 중 표시를 켜서 다른 요청을 막고, 리뷰어 수를 쓰는 프로젝트에서 리뷰어가 모자라면 아무것도 하지 않는다.
     * 충돌하면 병합하지 않고 표시만 끈다. 병합하면 상태를 `MERGED`로 바꾸고 병합 기준 커밋을 저장하며 상태 변경 알림과 타임라인을 남긴 뒤, 같은 브랜치와 관련된
     * 다른 요청의 병합 검사를 다시 시작한다. 병합한 뒤에는 제목, 본문, 커밋 메시지에서 `closes #번호`로 가리킨 이슈를 닫는다([PullRequestIssueCloser], X-11).
     *
     * v1.6처럼 병합 중 표시를 가장 먼저 켜고 저장한다. 그래서 리뷰어가 모자라 400을 돌려줄 때나 병합 중 예외가 날 때도 표시가 꺼지지 않고
     * 요청이 계속 병합 중으로 남는다([refactoring-backlog.md] R-76, 선생님 결정 2026-10-26). v1.6 `accept`의 병합은 트랜잭션 밖에서 돌고
     * 저장이 즉시 반영되므로 이 메서드에도 일부러 트랜잭션을 걸지 않는다.
     */
    fun accept(pullRequest: PullRequest, accepter: User): AcceptResult {
        val toProject = requireNotNull(pullRequest.toProject)
        pullRequest.isMerging = true
        pullRequests.save(pullRequest)
        if (toProject.isUsingReviewerCount == true && !pullRequest.reviewed) return AcceptResult.NOT_REVIEWED

        val result = merger.merge(pullRequest, PersonIdent(accepter.name, accepter.email))
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
        events.addState(accepter, pullRequest, State.MERGED)
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
