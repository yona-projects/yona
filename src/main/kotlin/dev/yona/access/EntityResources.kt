package dev.yona.access

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.CommitComment
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.Milestone
import dev.yona.domain.project.Organization
import dev.yona.domain.project.ProjectTransfer
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User

/*
 * 엔티티를 권한 검사의 대상([Resource])으로 바꾸는 함수 모음이다. v1.6의 `asResource()`에 해당한다.
 * 리소스 번호는 DB 기본키이다. 새 엔티티의 화면을 옮길 때 이 파일에 함수를 추가한다.
 */

/** 이슈 (`ISSUE_POST`). 작성자 권한이 있다. */
fun Issue.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.ISSUE_POST, project, authorId = authorId)

/** 이슈의 상태 (`ISSUE_STATE`). 권한은 이슈를 따른다 (v1.6 `Issue.stateAsResource`). */
fun Issue.stateAsResource(): ProjectResource = fieldAsResource(ResourceType.ISSUE_STATE)

/** 이슈의 마일스톤 (`ISSUE_MILESTONE`). 권한은 이슈를 따른다 (v1.6 `Issue.milestoneAsResource`). */
fun Issue.milestoneAsResource(): ProjectResource = fieldAsResource(ResourceType.ISSUE_MILESTONE)

/** 이슈의 담당자 (`ISSUE_ASSIGNEE`). 권한은 이슈를 따른다 (v1.6 `Issue.assigneeAsResource`). */
fun Issue.assigneeAsResource(): ProjectResource = fieldAsResource(ResourceType.ISSUE_ASSIGNEE)

private fun Issue.fieldAsResource(type: ResourceType): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), type, project, container = asResource())

/** 게시글 (`BOARD_POST`). 작성자 권한이 있다. */
fun Posting.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.BOARD_POST, project, authorId = authorId)

/** 이슈 댓글 (`ISSUE_COMMENT`). 컨테이너는 이슈이고 작성자 권한이 있다 (v1.6 `IssueComment.asResource`). */
fun IssueComment.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.ISSUE_COMMENT, issue?.project, container = issue?.asResource(), authorId = authorId)

/** 게시글 댓글 (`NONISSUE_COMMENT`). 컨테이너는 게시글이고 작성자 권한이 있다 (v1.6 `PostingComment.asResource`). */
fun PostingComment.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.NONISSUE_COMMENT, posting?.project, container = posting?.asResource(), authorId = authorId)

/** 마일스톤 (`MILESTONE`). */
fun Milestone.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.MILESTONE, project)

/** 이슈 라벨 (`ISSUE_LABEL`). */
fun IssueLabel.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.ISSUE_LABEL, project)

/** 이슈 라벨 분류 (`ISSUE_LABEL_CATEGORY`). */
fun IssueLabelCategory.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.ISSUE_LABEL_CATEGORY, project)

/** 풀 리퀘스트 (`PULL_REQUEST`). 받는 프로젝트에 속하고 기여자가 작성자이다. */
fun PullRequest.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.PULL_REQUEST, toProject, authorId = contributor?.id)

/**
 * 커밋 코멘트 (`COMMIT_COMMENT`). 작성자 권한이 있다.
 * v1.6은 컨테이너로 커밋 리소스를 돌려주지만, 커밋은 Git 저장소 서비스를 옮길 때 추가한다.
 */
fun CommitComment.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.COMMIT_COMMENT, project, authorId = authorId)

/** 코드 리뷰 스레드 (`COMMENT_THREAD`). 프로젝트에 속하고 스레드를 연 사람이 작성자이다 (v1.6 `CommentThread.asResource`). */
fun CommentThread.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.COMMENT_THREAD, project, authorId = authorId)

/** 코드 리뷰 댓글 (`REVIEW_COMMENT`). 스레드의 프로젝트에 속하고 스레드가 컨테이너이다 (v1.6 `ReviewComment.asResource`). */
fun ReviewComment.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.REVIEW_COMMENT, thread?.project, container = thread?.asResource(), authorId = authorId)

/** 사용자 (`USER`). 임시 첨부의 컨테이너로 쓴다 (v1.6 `User.asResource`). */
fun User.asResource(): GlobalResource = GlobalResource(requireNotNull(id).toString(), ResourceType.USER)

/** 사용자 아바타 (`USER_AVATAR`). 아바타 첨부의 컨테이너로 쓴다 (v1.6 `User.avatarAsResource`). */
fun User.avatarAsResource(): GlobalResource = GlobalResource(requireNotNull(id).toString(), ResourceType.USER_AVATAR)

/** 조직 (`ORGANIZATION`) (v1.6 `Organization.asResource`). */
fun Organization.asResource(): GlobalResource = GlobalResource(requireNotNull(id).toString(), ResourceType.ORGANIZATION)

/** 프로젝트 이전 요청 (`PROJECT_TRANSFER`). 받는 쪽만 수락할 수 있다 (v1.6 `ProjectTransfer.asResource`). */
fun ProjectTransfer.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.PROJECT_TRANSFER, project)
