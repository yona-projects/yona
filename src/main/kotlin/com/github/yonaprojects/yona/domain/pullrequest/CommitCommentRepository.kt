package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CommitCommentRepository : JpaRepository<CommitComment, Long> {
    fun findByProjectAndCommitIdOrderByCreatedDateAsc(project: Project, commitId: String): List<CommitComment>

    // yona CommitComment.count(project, commitId, path) 대응 (그룹10 #154, code/view.html SVN 파일뷰의
    // 리비전 링크 옆 댓글 수 배지).
    fun countByProjectAndCommitIdAndPath(project: Project, commitId: String, path: String): Long
}
