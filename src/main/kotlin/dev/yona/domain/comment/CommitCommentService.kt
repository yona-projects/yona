package dev.yona.domain.comment

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.Side
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** Subversion 프로젝트의 커밋에 코멘트를 달았음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterNewSVNCommitComment`를 부르던 지점에서 발행한다. */
data class CommitCommentedActivity(val comment: CommitComment, val author: User)

/**
 * Subversion 프로젝트의 커밋 코멘트를 만든다. v1.6 `CodeHistoryApp.newSVNComment`(`app/controllers/CodeHistoryApp.java:160-190`)에 해당한다.
 * Git 프로젝트의 코드 리뷰 댓글은 [ReviewCommentService]가 맡는다.
 */
@Service
class CommitCommentService(
    private val commitComments: CommitCommentRepository,
    private val attachments: AttachmentService,
    private val publisher: ApplicationEventPublisher
) {

    /**
     * 커밋 코멘트를 만든다. 작성자의 임시 첨부를 코멘트로 옮기고 새 코멘트 알림을 내보낸다.
     *
     * @param path 코멘트를 단 파일 경로. 파일 전체에 달았으면 `null`
     * @param line 코멘트를 단 줄. 없으면 `null`
     * @param side 코멘트를 단 쪽(변경 전 `A`, 변경 후 `B`). 없으면 `null`
     */
    @Transactional
    fun add(project: Project, commitId: String, author: User, contents: String, path: String?, line: Int?, side: Side?): CommitComment {
        val comment = commitComments.save(
            CommitComment(
                project = project, commitId = commitId, contents = contents, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId,
                authorName = author.name, path = path, line = line, side = side
            )
        )
        attachments.moveAll(ResourceType.USER, requireNotNull(author.id), ResourceType.COMMIT_COMMENT, requireNotNull(comment.id))
        publisher.publishEvent(CommitCommentedActivity(comment, author))
        return comment
    }
}
