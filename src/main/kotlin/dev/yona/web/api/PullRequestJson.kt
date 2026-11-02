package dev.yona.web.api

import dev.yona.domain.comment.CodeCommentThread
import dev.yona.domain.comment.CommentThread
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestCommit
import dev.yona.domain.pullrequest.PullRequestEvent
import dev.yona.domain.pullrequest.PullRequestReview
import dev.yona.domain.pullrequest.countedApprovals
import dev.yona.domain.pullrequest.merged
import dev.yona.domain.pullrequest.open
import dev.yona.domain.pullrequest.allowedMergeMethods
import dev.yona.domain.pullrequest.requiredReviewerCount
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.diff.FileDiff
import dev.yona.util.SiteUrls
import dev.yona.util.UrlPaths
import org.eclipse.jgit.diff.DiffEntry
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

/**
 * 외부용 REST API가 풀 리퀘스트를 JSON으로 보여 주는 형식이다(로드맵 X-15, `decisions.md` D-39). 이슈 API(`IssueJson`)와 같은 카멜 규칙의 Yona 자체 모양이며
 * 사용자는 `{loginId, name}`, 프로젝트는 `{owner, name}`, 시각은 ISO 형식(`yyyy-MM-dd'T'HH:mm:ssZ`), 값이 없는 시각과 사용자는 `null`이다.
 */
@Component
class PullRequestJson(
    private val users: UserRepository, private val siteUrls: SiteUrls, private val clock: java.time.Clock, private val events: dev.yona.domain.pullrequest.PullRequestEventRepository
) {

    /** 목록과 상세가 함께 쓰는 풀 리퀘스트 한 건이다. 상세는 담당자, 라벨, 리뷰 요청, 승인 현황을 더 담는다. */
    fun of(pullRequest: PullRequest): Map<String, Any?> {
        val to = requireNotNull(pullRequest.toProject)
        val from = requireNotNull(pullRequest.fromProject)
        return linkedMapOf(
            "number" to pullRequest.number, "id" to pullRequest.id, "title" to pullRequest.title, "body" to pullRequest.body,
            "state" to (pullRequest.state?.state ?: "open"), "author" to user(pullRequest.contributor), "receiver" to user(pullRequest.receiver),
            "fromProject" to project(from), "toProject" to project(to), "fromBranch" to pullRequest.fromBranch, "toBranch" to pullRequest.toBranch,
            "createdAt" to iso(pullRequest.created), "updatedAt" to iso(pullRequest.updated ?: pullRequest.created), "mergedAt" to if (pullRequest.merged) iso(pullRequest.received) else null,
            "isConflict" to (pullRequest.isConflict == true), "isMerging" to (pullRequest.isMerging == true),
            "mergeable" to when {
                pullRequest.isMerging == true -> "unknown"
                pullRequest.isConflict == true -> "conflicting"
                pullRequest.mergedCommitIdFrom == null || pullRequest.mergedCommitIdTo == null -> "unknown"
                else -> "clean"
            },
            "url" to siteUrls.path("${UrlPaths.projectPath(to.owner, to.name)}/pullRequest/${pullRequest.number}"),
            "assignees" to pullRequest.assignees.sortedBy { it.id }.map { user(it) },
            "labels" to pullRequest.labels.sortedBy { it.id }.map { linkedMapOf("id" to it.id, "labelName" to it.name, "labelColor" to it.color, "category" to it.category?.name) },
            "requestedReviewers" to pullRequest.reviewRequests.sortedBy { it.id }.map { user(it) },
            "approvals" to linkedMapOf("count" to pullRequest.countedApprovals, "required" to pullRequest.requiredReviewerCount, "approvers" to pullRequest.reviewers.sortedBy { it.id }.map { user(it) }),
            "mergedCommitIdFrom" to pullRequest.mergedCommitIdFrom, "mergedCommitIdTo" to pullRequest.mergedCommitIdTo,
            "mergeMethod" to mergeMethodOf(pullRequest), "mergeMethods" to mergeMethods(pullRequest, to)
        )
    }

    /**
     * 병합된 풀 리퀘스트가 어떤 방식으로 병합됐는지이다(추가 개발 항목 X-28). 병합되지 않았으면 `null`이다. 병합 이벤트의 이전 값에 `squash`나 `rebase`가
     * 있으면 그 방식이고, 없으면(v1.6 이벤트 포함) 병합 커밋이다.
     */
    private fun mergeMethodOf(pullRequest: PullRequest): String? {
        if (!pullRequest.merged) return null
        val event = events.findByPullRequestIdOrderByCreatedAscIdAsc(requireNotNull(pullRequest.id))
            .lastOrNull { it.eventType == dev.yona.domain.enumeration.EventType.PULL_REQUEST_STATE_CHANGED && it.newValue == "merged" }
        return dev.yona.domain.pullrequest.MergeMethod.of(event?.oldValue)?.code ?: dev.yona.domain.pullrequest.MergeMethod.MERGE.code
    }

    /**
     * 방식마다 프로젝트가 허용했는지(`allowed`), 지금 병합할 수 있는지(`available`), 할 수 없는 이유(`reason`)이다. 병합 커밋과 스쿼시는 충돌 여부를 따르고
     * 리베이스는 병합 검사가 저장한 `rebaseBlocked`(`conflict`, `merge-commits`)를 따른다. 충돌 이유는 `conflict`이다.
     */
    private fun mergeMethods(pullRequest: PullRequest, to: Project): Map<String, Any?> {
        val allowed = pullRequest.allowedMergeMethods()
        val conflict = pullRequest.isConflict == true
        return dev.yona.domain.pullrequest.MergeMethod.entries.associate { method ->
            val reason = when {
                !pullRequest.open -> "not-open"
                method == dev.yona.domain.pullrequest.MergeMethod.REBASE -> pullRequest.rebaseBlocked ?: if (conflict) "conflict" else null
                conflict -> "conflict"
                else -> null
            }
            method.code to linkedMapOf("allowed" to (method in allowed), "available" to (reason == null), "reason" to reason)
        }
    }

    fun commit(commit: PullRequestCommit, verification: dev.yona.domain.gpgkey.SignatureVerification? = null): Map<String, Any?> = linkedMapOf<String, Any?>(
        "commitId" to commit.commitId, "shortId" to commit.commitShortId, "message" to commit.commitMessage, "authorEmail" to commit.authorEmail,
        "authoredAt" to iso(commit.authorDate), "state" to commit.state?.name?.lowercase()
    ).also { node -> verification?.let { node["verification"] = verification(it) } }

    /** 서명 판정의 JSON이다(X-4, D-65, SSH 서명은 D-68). 서명한 사람과 키 ID(SSH 서명은 키 지문)는 알 때만 담는다. */
    fun verification(result: dev.yona.domain.gpgkey.SignatureVerification): Map<String, Any?> =
        linkedMapOf<String, Any?>("verified" to result.verified, "reason" to result.reason.code, "format" to result.format).also { node ->
            result.signerLogin?.let { node["signer"] = it }
            result.keyId?.let { node["keyId"] = it }
        }

    /** 변경 파일 한 개이다. `patch`는 헝크를 이은 통합 diff이고 너무 크거나 이진 파일이면 `null`(`truncated`)이다. */
    fun file(diff: FileDiff): Map<String, Any?> {
        val binary = diff.isBinaryA || diff.isBinaryB
        val textA = diff.a?.takeUnless { binary }
        val textB = diff.b?.takeUnless { binary }
        // 추가하거나 지운 파일은 바뀐 곳 목록이 없어 전체 줄이 추가나 삭제이다
        val added = diff.changeType == DiffEntry.ChangeType.ADD
        val removed = diff.changeType == DiffEntry.ChangeType.DELETE
        val edits = diff.editList
        val additions = if (added) textB?.size() ?: 0 else edits?.sumOf { it.endB - it.beginB } ?: 0
        val deletions = if (removed) textA?.size() ?: 0 else edits?.sumOf { it.endA - it.beginA } ?: 0
        val hunks = if (binary || added || removed || edits == null) null else diff.hunks()
        val tooLarge = hunks is dev.yona.repository.diff.SizeExceededHunks
        val patch = when {
            binary || tooLarge -> null
            added && textB != null -> "@@ -0,0 +1,${textB.size()} @@\n" + (0 until textB.size()).joinToString("\n") { "+" + textB.getString(it) }
            removed && textA != null -> "@@ -1,${textA.size()} +0,0 @@\n" + (0 until textA.size()).joinToString("\n") { "-" + textA.getString(it) }
            hunks == null -> null
            else -> hunks.joinToString("\n") { hunk ->
                val header = "@@ -${hunk.beginA + 1},${hunk.endA - hunk.beginA} +${hunk.beginB + 1},${hunk.endB - hunk.beginB} @@"
                header + "\n" + hunk.lines.joinToString("\n") { line ->
                    when (line.kind) {
                        dev.yona.repository.diff.DiffLineType.ADD -> "+" + line.content
                        dev.yona.repository.diff.DiffLineType.REMOVE -> "-" + line.content
                        else -> " " + line.content
                    }
                }
            }
        }
        val status = when (diff.changeType) {
            DiffEntry.ChangeType.ADD -> "added"
            DiffEntry.ChangeType.DELETE -> "removed"
            DiffEntry.ChangeType.RENAME -> "renamed"
            DiffEntry.ChangeType.COPY -> "copied"
            else -> "modified"
        }
        val path = (if (diff.changeType == DiffEntry.ChangeType.DELETE) diff.pathA else diff.pathB) ?: diff.pathA
        return linkedMapOf(
            "path" to path, "oldPath" to if (diff.changeType == DiffEntry.ChangeType.RENAME || diff.changeType == DiffEntry.ChangeType.COPY) diff.pathA else null,
            "status" to status, "additions" to additions, "deletions" to deletions, "binary" to binary, "patch" to patch, "truncated" to tooLarge
        )
    }

    fun event(event: PullRequestEvent): Map<String, Any?> = linkedMapOf(
        "id" to event.id, "type" to event.eventType?.name, "createdAt" to iso(event.created),
        "sender" to event.senderLoginId?.let { users.findByLoginId(it) }?.let { user(it) }, "oldValue" to event.oldValue, "newValue" to event.newValue
    )

    fun review(review: PullRequestReview): Map<String, Any?> = linkedMapOf(
        "id" to review.id, "verdict" to review.verdict?.name, "body" to review.contents, "createdAt" to iso(review.createdDate),
        "author" to (review.authorId?.let { users.findById(it).orElse(null) }?.let { user(it) } ?: linkedMapOf("loginId" to review.authorLoginId, "name" to review.authorLoginId)),
        "dismissed" to (review.dismissedDate != null), "dismissedAt" to review.dismissedDate?.let { iso(it) },
        "dismissedBy" to review.dismissedByLoginId, "dismissedReason" to review.dismissedReason
    )

    /** 리뷰 댓글 스레드이다. 줄 범위 스레드는 위치를, 파일(변경 전체) 스레드는 커밋만 담는다. */
    fun thread(thread: CommentThread, comments: List<ReviewComment>): Map<String, Any?> {
        val node = linkedMapOf<String, Any?>("id" to thread.id, "state" to (thread.state?.name?.lowercase() ?: "open"), "createdAt" to iso(thread.createdDate), "pending" to thread.isPending)
        when (thread) {
            is CodeCommentThread -> {
                node["commitId"] = thread.commitId; node["prevCommitId"] = thread.prevCommitId; node["path"] = thread.path
                node["startSide"] = thread.startSide?.name; node["startLine"] = thread.startLine; node["startColumn"] = thread.startColumn
                node["endSide"] = thread.endSide?.name; node["endLine"] = thread.endLine; node["endColumn"] = thread.endColumn
            }
            is NonRangedCodeCommentThread -> { node["commitId"] = thread.commitId; node["prevCommitId"] = thread.prevCommitId; node["path"] = thread.path }
            else -> Unit
        }
        node["comments"] = comments.sortedWith(compareBy({ it.createdDate }, { it.id })).map { comment(it) }
        return node
    }

    fun comment(comment: ReviewComment): Map<String, Any?> = linkedMapOf(
        "id" to comment.id, "body" to comment.contents, "createdAt" to iso(comment.createdDate), "editedAt" to comment.editedDate?.let { iso(it) }, "pending" to comment.isPending,
        "author" to (comment.authorId?.let { users.findById(it).orElse(null) }?.let { user(it) } ?: linkedMapOf("loginId" to comment.authorLoginId, "name" to comment.authorName))
    )

    fun user(user: User?): Map<String, Any?>? = user?.let { linkedMapOf("loginId" to it.loginId, "name" to it.name) }

    private fun project(project: Project): Map<String, Any?> = linkedMapOf("owner" to project.owner, "name" to project.name)

    /** `yyyy-MM-dd'T'HH:mm:ssZ` 형식이다. 값이 없으면 `null`이다. */
    fun iso(date: Date?): String? = date?.let { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").apply { timeZone = TimeZone.getTimeZone(clock.zone) }.format(it) }
}
