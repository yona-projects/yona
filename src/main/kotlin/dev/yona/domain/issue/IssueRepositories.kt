package dev.yona.domain.issue

import org.springframework.data.jpa.repository.JpaRepository

/** 이슈 담당자 저장소이다. */
interface AssigneeRepository : JpaRepository<Assignee, Long>

/** 이슈 공유자 저장소이다. */
interface IssueSharerRepository : JpaRepository<IssueSharer, Long> {

    /** 사용자가 이슈의 공유자인지 확인한다 (v1.6 `Issue.findSharerByUserId`). */
    fun existsByIssueIdAndUserId(issueId: Long, userId: Long): Boolean
}
