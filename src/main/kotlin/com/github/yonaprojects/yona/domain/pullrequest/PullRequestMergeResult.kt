package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.vcs.Commit
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.enumeration.State

// gitCommits는 원래 GitCommit(JGit RevCommit 전용 래퍼)로만 타이핑돼 있었는데, Mercurial PR
// 병합(HgCommit)도 이 결과 타입을 그대로 재사용해야 해서 두 래퍼의 공통 상위 타입인 Commit으로
// 넓혔다. GitCommit은 Commit이 정의한 추상 메서드 외에 별도로 노출하는 메서드가 없어(GitCommit.kt
// 참고) 이 타입 확장은 기존 Git 소비자(컨트롤러/템플릿/이 클래스 자신)의 동작을 전혀 바꾸지
// 않는다 — 순수 컴파일타임 일반화.
class PullRequestMergeResult(
    var gitCommits: List<Commit> = emptyList(),
    var newCommits: List<PullRequestCommit> = emptyList(),
    var pullRequest: PullRequest
) {
    fun hasDiffCommits(): Boolean {
        return gitCommits.isNotEmpty()
    }

    fun conflicts(): Boolean {
        return pullRequest.isConflict == true
    }

    fun setConflictStateOfPullRequest() {
        pullRequest.isConflict = true
    }

    fun setResolvedStateOfPullRequest() {
        pullRequest.isConflict = false
    }

    fun setMergedStateOfPullRequest(receiver: User) {
        pullRequest.isConflict = false
        pullRequest.state = State.MERGED
        pullRequest.receiver = receiver
    }
}
