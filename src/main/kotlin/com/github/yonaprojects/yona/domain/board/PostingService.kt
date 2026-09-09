package com.github.yonaprojects.yona.domain.board

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PostingService {
    fun getPostings(projectId: Long, pageable: Pageable): Page<Posting>
    fun getNotices(projectId: Long): List<Posting>
    fun getPosting(projectId: Long, number: Long): Posting?
    // yona BoardApi.createPostingNode()의 "number 필드가 있으면
    // saveWithNumber(number), 없으면 save()" 대응 — explicitNumber가 주어지면
    // project.lastPostingNumber 카운터를 건드리지 않고 그 번호를 그대로 쓴다.
    fun createPosting(projectId: Long, posting: Posting, authorId: Long, explicitNumber: Long? = null): Posting
    fun updatePosting(
        projectId: Long,
        number: Long,
        title: String,
        body: String,
        notice: Boolean,
        readme: Boolean,
        authorId: Long,
        sendNotificationMail: Boolean = false
    ): Posting
    fun deletePosting(projectId: Long, number: Long, authorId: Long)


    // yona Project.delete()의 posting 삭제 루프(posting.delete(), 알림 미발행) 대응.
    // deletePosting()과 달리 RESOURCE_DELETED 알림을 발행하지 않는다 — 프로젝트 자체가 삭제되는
    // 상황에서 게시글 개수만큼 알림이 나가는 것을 막기 위함(legacy도 Project.delete()에서
    // AbstractPostingApp의 알림 발행 경로를 타지 않고 모델의 delete()를 직접 호출한다).
    fun deletePostingCascade(posting: Posting)
}
