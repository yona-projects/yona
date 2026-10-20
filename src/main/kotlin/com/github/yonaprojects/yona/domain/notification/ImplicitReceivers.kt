package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository

// yona NotificationEvent.getMandatoryReceivers(Issue/Posting)의 "암묵 수신자" — 감시(Watch) 여부와 무관하게
// 글과 관계가 있다는 이유만으로 알림을 받는 사용자(작성자, 담당자, 공유 대상). 호출부가 baseWatchers로 넘기면
// WatchService.findActualWatchers()가 프로젝트/리소스 감시자와 합친 뒤 읽기 권한·뮤트를 적용한다.
internal fun issueImplicitReceivers(issue: Issue, userRepository: UserRepository): MutableSet<User> {
    val users = mutableSetOf<User>()
    issue.authorId?.let { userRepository.findById(it).orElse(null) }?.let { users.add(it) }
    issue.assignee?.user?.let { users.add(it) }
    issue.sharers.forEach { users.add(it.user) }
    return users
}

internal fun postingImplicitReceivers(posting: Posting, userRepository: UserRepository): MutableSet<User> {
    val users = mutableSetOf<User>()
    posting.authorId?.let { userRepository.findById(it).orElse(null) }?.let { users.add(it) }
    return users
}

// yona PullRequest.getWatchers() 대응 — PR 기여자와 이 PR의 리뷰 댓글을 쓴 모든 사용자(감시 여부와 무관).
internal fun pullRequestImplicitReceivers(
    pullRequest: com.github.yonaprojects.yona.domain.pullrequest.PullRequest,
    userRepository: UserRepository
): MutableSet<User> {
    val users = mutableSetOf(pullRequest.contributor)
    pullRequest.commentThreads
        .flatMap { it.reviewComments }
        .mapNotNull { it.author?.id }
        .distinct()
        .forEach { id -> userRepository.findById(id).ifPresent { users.add(it) } }
    return users
}
