package dev.yona.domain.board

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.TitleHeadService
import dev.yona.domain.notification.MentionService
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 외부 API가 게시글을 만들 때 받는 값이다.
 *
 * @property number 지정하면 그 번호로 만든다(프로젝트의 마지막 번호는 올리지 않는다). 없거나 0 이하이면 다음 번호이다
 */
data class ExternalPosting(val title: String, val body: String, val createdDate: Date?, val updatedDate: Date?, val number: Long?)

/**
 * 외부 REST API가 쓰는 게시판 도메인 동작이다. v1.6 `BoardApi.createPostingNode`, `newPostingComment`(`app/controllers/api/BoardApi.java:70-190`)에 해당한다.
 * 권한 확인은 호출하는 쪽이 한다. 가져오기용이라 알림 활동은 발행하지 않는다.
 */
@Service
class ExternalPostingService(
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val postingService: PostingService,
    private val titleHeads: TitleHeadService,
    private val mentions: MentionService
) {

    /**
     * 게시글을 만든다. 번호를 지정하지 않으면 다음 번호를 받고 제목 머리말 개수를 늘리며, 지정하면 그 번호로 저장하고 머리말은 건드리지 않는다
     * (v1.6 `saveWithNumber`). 시각을 주지 않으면 비운 채 저장한다(v1.6 `BoardApi`가 생성자의 기본값을 `null`로 덮어쓴다).
     */
    @Transactional
    fun create(project: Project, author: User, spec: ExternalPosting): Posting {
        val numbered = spec.number != null && spec.number > 0
        val posting = postings.save(
            Posting(
                project = project, number = if (numbered) spec.number else postingService.nextNumber(project), title = spec.title, body = spec.body,
                authorId = author.id, authorLoginId = author.loginId, authorName = author.name, notice = false, readme = false, numOfComments = 0,
                createdDate = spec.createdDate, updatedDate = spec.updatedDate
            )
        )
        if (!numbered) titleHeads.save(project, spec.title)
        mentions.update(ResourceType.BOARD_POST, requireNotNull(posting.id).toString(), spec.body)
        return posting
    }

    /** 게시글에 댓글을 단다. 시각을 주지 않으면 비운 채 저장한다(v1.6 `BoardApi`가 기본값을 `null`로 덮어쓴다). 글의 댓글 수를 맞춘다(v1.6 `PostingComment.save`). */
    @Transactional
    fun addComment(posting: Posting, author: User, contents: String, createdDate: Date?): PostingComment {
        val comment = postingComments.save(
            PostingComment(
                contents = contents, createdDate = createdDate, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                posting = posting, projectId = requireNotNull(posting.project?.id)
            )
        )
        mentions.update(ResourceType.NONISSUE_COMMENT, requireNotNull(comment.id).toString(), contents)
        posting.numOfComments = postingComments.countByPostingId(requireNotNull(posting.id)).toInt()
        postings.save(posting)
        return comment
    }
}
