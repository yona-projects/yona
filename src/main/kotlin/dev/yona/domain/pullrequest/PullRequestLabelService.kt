package dev.yona.domain.pullrequest

import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 라벨을 바꾼 결과이다. */
enum class LabelResult { CHANGED, UNCHANGED, WRONG_PROJECT }

/**
 * 풀 리퀘스트에 이슈 라벨을 붙이고 뗀다. v1.6에 없는 추가 항목이다(로드맵 X-14, `decisions.md` D-34). 권한 확인(풀 리퀘스트 수정 권한)은 호출하는 쪽이 한다.
 *
 * 붙일 수 있는 것은 받는 프로젝트(`toProject`)의 이슈 라벨뿐이다. 한 분류만 고르는 분류(`exclusive`)는 같은 분류의 기존 라벨이 빠지고 새 라벨이 붙는다.
 * 변경은 타임라인에만 기록하고 알림도 웹훅도 만들지 않는다(v1.6 이슈 라벨 변경은 `receivers = null`이고 웹훅도 없다).
 */
@Service
class PullRequestLabelService(
    private val pullRequests: PullRequestRepository,
    private val events: PullRequestEventService
) {

    /** [label]을 붙인다. 다른 프로젝트의 라벨이면 [LabelResult.WRONG_PROJECT], 이미 붙어 있으면 [LabelResult.UNCHANGED]이다. */
    @Transactional
    fun add(pullRequest: PullRequest, label: IssueLabel, actor: User): LabelResult {
        if (label.project?.id != pullRequest.toProject?.id) return LabelResult.WRONG_PROJECT
        if (pullRequest.labels.any { it.id == label.id }) return LabelResult.UNCHANGED
        val category = label.category
        if (category?.isExclusive == true) {
            pullRequest.labels.filter { it.category?.id == category.id }.forEach {
                pullRequest.labels.remove(it)
                events.addLabel(actor, pullRequest, describe(it), null)
            }
        }
        pullRequest.labels.add(label)
        pullRequests.save(pullRequest)
        events.addLabel(actor, pullRequest, null, describe(label))
        return LabelResult.CHANGED
    }

    /** [label]을 뗀다. 붙어 있지 않으면 [LabelResult.UNCHANGED]이다. */
    @Transactional
    fun remove(pullRequest: PullRequest, label: IssueLabel, actor: User): LabelResult {
        if (!pullRequest.labels.removeIf { it.id == label.id }) return LabelResult.UNCHANGED
        pullRequests.save(pullRequest)
        events.addLabel(actor, pullRequest, describe(label), null)
        return LabelResult.CHANGED
    }

    /**
     * 붙일 라벨 [attach]와 뗄 라벨 [detach]를 한꺼번에 바꾼다(이슈 화면의 일괄 변경이 `attachingLabelIds`, `detachingLabelIds`를 보내는 것과 같다).
     * 붙일 라벨이 하나라도 다른 프로젝트의 것이면 [LabelResult.WRONG_PROJECT]이고 아무것도 바꾸지 않는다. 같은 라벨이 양쪽에 있으면 먼저 떼고 붙인다.
     * 한 분류만 고르는 분류에서 둘을 붙이면 뒤의 것만 남는다.
     */
    @Transactional
    fun change(pullRequest: PullRequest, attach: Collection<IssueLabel>, detach: Collection<IssueLabel>, actor: User): LabelResult {
        if (attach.any { it.project?.id != pullRequest.toProject?.id }) return LabelResult.WRONG_PROJECT
        var changed = false
        detach.forEach { if (remove(pullRequest, it, actor) == LabelResult.CHANGED) changed = true }
        attach.forEach { if (add(pullRequest, it, actor) == LabelResult.CHANGED) changed = true }
        return if (changed) LabelResult.CHANGED else LabelResult.UNCHANGED
    }

    /** 타임라인 이벤트의 값이다. 이슈 라벨 이벤트와 같은 `분류 - 이름 색` 형태이다. */
    private fun describe(label: IssueLabel) = "${label.category?.name} - ${label.name} ${label.color}"
}
