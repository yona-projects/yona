package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CommentThreadRepository : JpaRepository<CommentThread, Long> {
    fun findByCommitIdOrderByCreatedDateDesc(commitId: String): List<CommentThread>
    fun findByProjectAndCommitIdOrderByCreatedDateDesc(project: Project, commitId: String): List<CommentThread>
    fun findByCommitIdAndStateOrderByCreatedDateDesc(commitId: String, state: CommentThread.ThreadState): List<CommentThread>
    fun findByPullRequest(pullRequest: PullRequest): List<CommentThread>
    fun findByProjectAndCommitIdAndPullRequestIsNullOrderByCreatedDateDesc(project: Project, commitId: String): List<CommentThread>

    // yona Project.deleteCommentThreads()(this.commentThreads 전체) 대응 (P0-19).
    fun findByProject(project: Project): List<CommentThread>

    // yona CommentThread.countOnCommit(project, commitId, path) 대응 (그룹10 #154, code/view.html 파일뷰의
    // 리비전 링크 옆 댓글 수 배지). codeRange는 CodeCommentThread(서브클래스)에만 있는 필드라 Spring Data의
    // 파생 쿼리(프로퍼티 경로)로는 베이스 타입 CommentThread에서 곧바로 참조할 수 없어 TREAT를 쓰는
    // 명시적 JPQL로 작성한다.
    @Query("""
        SELECT COUNT(ct) FROM CommentThread ct
        WHERE ct.project = :project AND ct.commitId = :commitId
          AND TYPE(ct) = CodeCommentThread
          AND TREAT(ct AS CodeCommentThread).codeRange.path = :codeRangePath
    """)
    fun countByProjectAndCommitIdAndCodeRangePath(
        @Param("project") project: Project,
        @Param("commitId") commitId: String,
        @Param("codeRangePath") codeRangePath: String
    ): Long
}

