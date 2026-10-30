package dev.yona.domain.board

import dev.yona.domain.issue.IssueHistory
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.TitleHeadService
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.notification.MentionService
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 게시글 폼에서 받은 값이다.
 *
 * @property notice 공지로 올릴지
 * @property readme README 글로 올릴지
 */
data class PostForm(val title: String, val body: String, val notice: Boolean, val readme: Boolean)

/**
 * 게시글의 작성, 수정, 삭제를 담당한다. v1.6 `BoardApp.newPost`, `editPost`, `deletePost`와 `AbstractPostingApp.editPosting`,
 * `AbstractPosting.save/update/delete`에 해당한다. 권한 확인은 호출하는 쪽이 한다.
 */
@Service
class PostingService(
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val titleHeads: TitleHeadService,
    private val mentions: MentionService,
    private val attachments: AttachmentService,
    private val issueService: IssueService,
    private val publisher: ApplicationEventPublisher,
    private val entityManager: EntityManager
) {

    /**
     * 프로젝트의 마지막 게시글 번호를 하나 올리고 새 번호를 돌려준다 (v1.6 `Project.increaseLastPostingNumber`).
     * 카운터가 게시글 수보다 작으면 가장 큰 게시글 번호로 먼저 바로잡는다 (v1.6 `isLastPostingNumberCorrect`, `getLastPostingNumber`).
     */
    @Transactional
    fun nextNumber(project: Project): Long {
        val id = requireNotNull(project.id)
        entityManager.createQuery(
            "update Project p set p.lastPostingNumber = coalesce((select max(g.number) from Posting g where g.project.id = p.id), 0) " +
                "where p.id = :id and coalesce(p.lastPostingNumber, 0) < (select count(g2) from Posting g2 where g2.project.id = p.id)"
        ).setParameter("id", id).executeUpdate()
        entityManager.createQuery("update Project p set p.lastPostingNumber = coalesce(p.lastPostingNumber, 0) + 1 where p.id = :id").setParameter("id", id).executeUpdate()
        entityManager.flush()
        val number = entityManager.createQuery("select p.lastPostingNumber from Project p where p.id = :id", java.lang.Long::class.java).setParameter("id", id).singleResult.toLong()
        entityManager.find(Project::class.java, id)?.let { entityManager.refresh(it) }
        return number
    }

    /** 게시글을 저장한다. 번호를 받고 제목 머리말 개수와 멘션을 맞추며 [PostingCreatedActivity]를 발행한다. */
    @Transactional
    fun create(project: Project, author: User, form: PostForm): Posting {
        val now = Date()
        val posting = postings.save(
            Posting(
                project = project, number = nextNumber(project), title = form.title, body = form.body, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                notice = form.notice, readme = form.readme, numOfComments = 0, createdDate = now, updatedDate = now
            )
        )
        titleHeads.save(project, form.title)
        mentions.update(ResourceType.BOARD_POST, requireNotNull(posting.id).toString(), form.body)
        publisher.publishEvent(PostingCreatedActivity(posting, author))
        return posting
    }

    /**
     * 게시글을 고친다. 본문이 바뀌었으면 변경 이력을 앞에 쌓고, 제목 머리말 개수와 멘션을 새 값에 맞춘다.
     * [notifyMail]이 켜져 있거나 [editor]가 작성자가 아니면 [PostingUpdatedActivity]를 발행한다. `readme` 표시는 폼 값을 따른다.
     */
    @Transactional
    fun update(posting: Posting, editor: User, form: PostForm, notifyMail: Boolean): Posting {
        val oldBody = posting.body.orEmpty()
        val oldTitle = posting.title
        val now = Date()
        if (oldBody != form.body) posting.history = IssueHistory.entry(oldBody, form.body, editor, now) + posting.history.orEmpty()
        posting.title = form.title
        posting.body = form.body
        posting.notice = form.notice
        posting.readme = form.readme
        posting.updatedDate = now
        posting.updatedByAuthorId = editor.id
        if (notifyMail || posting.authorLoginId != editor.loginId) publisher.publishEvent(PostingUpdatedActivity(posting, oldBody, editor))
        posting.numOfComments = postingComments.countByPostingId(requireNotNull(posting.id)).toInt()
        postings.save(posting)
        mentions.update(ResourceType.BOARD_POST, posting.id.toString(), form.body)
        titleHeads.save(requireNotNull(posting.project), form.title)
        titleHeads.delete(requireNotNull(posting.project), oldTitle)
        return posting
    }

    /**
     * 글의 라벨을 [newLabels]로 바꾼다 (v1.6 `BoardApi.updatePostLabel`). v1.6은 `save()`로 저장해 제목 머리말 개수가 한 번 더 늘고
     * 멘션 기록이 다시 맞춰진다([refactoring-backlog.md] R-29). 같은 결과를 내도록 재현한다.
     */
    @Transactional
    fun replaceLabels(posting: Posting, newLabels: Set<dev.yona.domain.issue.IssueLabel>) {
        posting.labels.clear()
        posting.labels.addAll(newLabels)
        posting.numOfComments = postingComments.countByPostingId(requireNotNull(posting.id)).toInt()
        postings.save(posting)
        titleHeads.save(requireNotNull(posting.project), posting.title)
        mentions.update(ResourceType.BOARD_POST, posting.id.toString(), posting.body.orEmpty())
    }

    /**
     * 글의 라벨을 [newLabels]로 바꾸기만 한다(외부 REST, X-29b). 웹 화면이 쓰는 [replaceLabels]는 v1.6처럼 `save()`로 제목 머리말 개수를 한 번 더 늘리는데
     * ([refactoring-backlog.md] R-29), 새 REST가 그 결함을 되풀이할 이유가 없어 라벨만 바꾼다.
     */
    @Transactional
    fun setLabels(posting: Posting, newLabels: Set<dev.yona.domain.issue.IssueLabel>) {
        posting.labels.clear()
        posting.labels.addAll(newLabels)
        postings.save(posting)
    }

    /**
     * README 글의 표시를 푼다 (v1.6 `ProjectApp.changeVCS`의 `posting.readme = false; posting.save()`). 글을 `save()`로 저장하므로 댓글 수를 다시 세고,
     * 제목 머리말 개수를 늘리고, 멘션 기록을 맞춘다.
     */
    @Transactional
    fun unmarkReadmeOnVcsChange(posting: Posting) {
        posting.readme = false
        posting.numOfComments = postingComments.countByPostingId(requireNotNull(posting.id)).toInt()
        postings.save(posting)
        titleHeads.save(requireNotNull(posting.project), posting.title)
        mentions.update(ResourceType.BOARD_POST, posting.id.toString(), posting.body.orEmpty())
    }

    /** 본문만 바꾼다(체크리스트 갱신). 이력과 이벤트를 남기지 않고 수정 시각도 바꾸지 않는다. 댓글 수를 다시 세고 멘션을 맞춘다. */
    @Transactional
    fun replaceBody(posting: Posting, body: String) {
        posting.body = body
        posting.numOfComments = postingComments.countByPostingId(requireNotNull(posting.id)).toInt()
        postings.save(posting)
        mentions.update(ResourceType.BOARD_POST, posting.id.toString(), body)
    }

    /** README로 표시된 글 중 [number]가 아닌 글의 표시를 푼다 (v1.6 `BoardApp.unmarkAnotherReadmePostingIfExists`). */
    @Transactional
    fun unmarkOtherReadme(project: Project, number: Long) {
        postings.findFirstByProjectIdAndReadmeTrue(requireNotNull(project.id))?.takeIf { it.number != number }?.let {
            it.readme = false
            postings.save(it)
        }
    }

    /**
     * 게시글을 지운다. 댓글(첨부와 알림 이벤트 포함), 제목 머리말 개수, 글의 첨부와 알림 이벤트를 지운다. [PostingDeletedActivity]를 발행한다.
     * v1.6처럼 멘션 기록은 지우지 않는다([refactoring-backlog.md] R-24).
     */
    @Transactional
    fun delete(posting: Posting, actor: User) {
        val id = requireNotNull(posting.id)
        val project = requireNotNull(posting.project)
        publisher.publishEvent(PostingDeletedActivity(requireNotNull(project.id), posting.number ?: 0, posting.title, posting.body, actor))
        val comments = postingComments.findByPostingIdOrderByCreatedDateAscIdAsc(id)
        comments.forEach { it.parentComment = null }
        entityManager.flush()
        comments.forEach { comment ->
            val commentId = requireNotNull(comment.id)
            attachments.deleteAll(ResourceType.NONISSUE_COMMENT, commentId)
            issueService.deleteResourceRelations(ResourceType.NONISSUE_COMMENT, commentId.toString())
            entityManager.remove(comment)
        }
        titleHeads.delete(project, posting.title)
        attachments.deleteAll(ResourceType.BOARD_POST, id)
        issueService.deleteResourceRelations(ResourceType.BOARD_POST, id.toString())
        entityManager.flush()
        posting.labels.clear()
        postings.delete(posting)
        postings.flush()
    }
}
