package dev.yona.domain.pullrequest

import org.springframework.data.jpa.repository.JpaRepository

/** 풀 리퀘스트에 포함된 커밋 저장소이다. */
interface PullRequestCommitRepository : JpaRepository<PullRequestCommit, Long>
