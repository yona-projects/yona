package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.vcs.Commit
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "pull_request_commit")
class PullRequestCommit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    var pullRequest: PullRequest,

    @Column(nullable = false)
    var commitId: String = "",

    var authorDate: Instant? = null,

    var created: Instant? = null,

    @Column(length = 1_000_000)
    var commitMessage: String = "",

    @Column(nullable = false)
    var commitShortId: String = "",

    var authorEmail: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var state: State = State.CURRENT
) {
    enum class State {
        PRIOR, CURRENT
    }

    fun getCommitShortMessage(): String {
        if (commitMessage.isEmpty()) {
            return ""
        }
        if (!commitMessage.contains("\n")) {
            return commitMessage
        }
        val segments = commitMessage.split("\n")
        return if (segments.isNotEmpty()) segments[0] else ""
    }

    companion object {
        // yona-wiki P3-27 — 원래 GitCommit 전용이었으나, Mercurial PR 병합도 이 바인딩을 재사용해야
        // 해서 공통 상위 타입 Commit으로 넓혔다(bindPullRequestCommit이 실제로 쓰는 멤버는 전부
        // Commit 추상 클래스가 정의한 것들이라 Git 쪽 동작은 그대로다).
        fun bindPullRequestCommit(commit: Commit, pullRequest: PullRequest): PullRequestCommit {
            return PullRequestCommit(
                commitId = commit.getId(),
                commitShortId = commit.getShortId(),
                commitMessage = commit.getMessage() ?: "",
                authorEmail = commit.getAuthorEmail(),
                authorDate = commit.getAuthorDate()?.toInstant(),
                created = Instant.now(),
                state = State.CURRENT,
                pullRequest = pullRequest
            )
        }
    }
}
