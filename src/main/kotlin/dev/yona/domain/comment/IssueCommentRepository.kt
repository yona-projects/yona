package dev.yona.domain.comment

import org.springframework.data.jpa.repository.JpaRepository

/** 이슈 댓글 저장소이다. */
interface IssueCommentRepository : JpaRepository<IssueComment, Long> {

    /** 이슈의 댓글을 작성 시각 순으로 조회한다 (v1.6 `Issue.getComments`, `Comment.comparator`). */
    fun findByIssueIdOrderByCreatedDateAscIdAsc(issueId: Long): List<IssueComment>

    /** 이슈의 댓글 수이다. */
    fun countByIssueId(issueId: Long): Long

    /** 댓글에 달린 한 줄 댓글을 조회한다 (v1.6 `IssueComment.getChildComments`). */
    fun findByParentCommentIdOrderByIdAsc(parentCommentId: Long): List<IssueComment>
}

/** 커밋 코멘트 저장소이다. */
interface CommitCommentRepository : JpaRepository<CommitComment, Long> {

    /** 프로젝트의 커밋에 달린 커밋 코멘트 수이다 (v1.6 `CommitComment.count(project, commitId, null)`). */
    fun countByProjectIdAndCommitId(projectId: Long, commitId: String): Int

    /** 프로젝트의 커밋 한 건에 달린 코멘트를 작성 시각 순으로 조회한다. */
    fun findByProjectIdAndCommitIdOrderByCreatedDateAscIdAsc(projectId: Long, commitId: String): List<CommitComment>

    /** 프로젝트의 커밋 한 건에서 [path] 파일에 달린 커밋 코멘트 수이다 (v1.6 `CommitComment.count(project, commitId, path)`). */
    fun countByProjectIdAndCommitIdAndPath(projectId: Long, commitId: String, path: String): Int
}
