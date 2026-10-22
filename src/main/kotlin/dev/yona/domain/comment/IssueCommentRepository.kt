package dev.yona.domain.comment

import org.springframework.data.jpa.repository.JpaRepository

/** 이슈 댓글 저장소이다. */
interface IssueCommentRepository : JpaRepository<IssueComment, Long>

/** 커밋 코멘트 저장소이다. */
interface CommitCommentRepository : JpaRepository<CommitComment, Long>
