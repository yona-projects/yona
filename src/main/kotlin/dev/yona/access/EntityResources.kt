package dev.yona.access

import dev.yona.domain.board.Posting
import dev.yona.domain.comment.CommitComment
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.Milestone
import dev.yona.domain.pullrequest.PullRequest

/*
 * 엔티티를 권한 검사의 대상([Resource])으로 바꾸는 함수 모음이다. v1.6의 `asResource()`에 해당한다.
 * 리소스 번호는 DB 기본키이다. 새 엔티티의 화면을 옮길 때 이 파일에 함수를 추가한다.
 */

/** 이슈 (`ISSUE_POST`). 작성자 권한이 있다. */
fun Issue.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.ISSUE_POST, project, authorId = authorId)

/** 게시글 (`BOARD_POST`). 작성자 권한이 있다. */
fun Posting.asResource(): ProjectResource =
    ProjectResource(requireNotNull(id).toString(), ResourceType.BOARD_POST, project, authorId = authorId)

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
