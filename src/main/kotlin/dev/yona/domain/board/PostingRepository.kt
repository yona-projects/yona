package dev.yona.domain.board

import org.springframework.data.jpa.repository.JpaRepository

/** 게시글 저장소이다. */
interface PostingRepository : JpaRepository<Posting, Long> {

    /** 프로젝트 안의 번호로 게시글을 조회한다 (v1.6 `Posting.findByNumber`). */
    fun findByProjectIdAndNumber(projectId: Long, number: Long): Posting?

    /** 프로젝트의 게시글 개수이다 (v1.6 `Posting.countPostings`). */
    fun countByProjectId(projectId: Long): Long

    /** 프로젝트의 README 게시글이다. 코드 메뉴를 끈 프로젝트가 홈에 보여 준다 (v1.6 `Posting.findREADMEPosting`). */
    fun findFirstByProjectIdAndReadmeTrue(projectId: Long): Posting?
}
