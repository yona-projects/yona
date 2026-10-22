package dev.yona.domain.board

import org.springframework.data.jpa.repository.JpaRepository

/** 게시글 저장소이다. */
interface PostingRepository : JpaRepository<Posting, Long> {

    /** 프로젝트의 게시글 개수이다 (v1.6 `Posting.countPostings`). */
    fun countByProjectId(projectId: Long): Long
}
