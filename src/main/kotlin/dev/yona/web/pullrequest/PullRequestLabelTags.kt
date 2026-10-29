package dev.yona.web.pullrequest

import dev.yona.domain.pullrequest.PullRequest
import dev.yona.web.issue.IssueLabelTag

/**
 * 풀 리퀘스트에 붙은 라벨을 목록과 보기에서 칩으로 보일 값으로 만든다. 분류 이름, 라벨 이름 순이다(v1.6 이슈 목록 `partial_list`의 정렬과 같다).
 * 추가 항목이다(로드맵 X-14, `decisions.md` D-34).
 */
fun labelTagsOf(pullRequest: PullRequest): List<IssueLabelTag> =
    pullRequest.labels.sortedWith(compareBy({ it.category?.name }, { it.name })).map {
        IssueLabelTag(requireNotNull(it.id), it.category?.id, it.category?.name.orEmpty(), it.category?.isExclusive == true, it.name.orEmpty(), it.color.orEmpty())
    }
