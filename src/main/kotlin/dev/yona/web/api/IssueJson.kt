package dev.yona.web.api

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.web.project.RepositoryUrls
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 외부용 REST API가 이슈를 JSON으로 보여 주는 형식이다. v1.6 `ProjectApi.getResult`와 `composePlainCommentsJson`
 * (`app/controllers/api/ProjectApi.java:277-440`)에 해당한다.
 */
@Component
class IssueJson(
    private val users: UserRepository,
    private val comments: IssueCommentRepository,
    private val attachments: AttachmentRepository,
    private val urls: RepositoryUrls,
    private val clock: Clock
) {

    /** 이슈 하나의 JSON이다. 값이 없는 항목(담당자, 라벨, 마일스톤, 마감일, 첨부, 댓글)은 키를 뺀다. */
    fun of(issue: Issue, request: HttpServletRequest): Map<String, Any?> {
        val project = requireNotNull(issue.project)
        val node = linkedMapOf<String, Any?>(
            "number" to issue.number, "id" to issue.id, "title" to issue.title, "type" to ResourceType.ISSUE_POST.name,
            "author" to author(issue.authorId?.let { users.findById(it).orElse(null) }),
            "createdAt" to iso(issue.createdDate), "updatedAt" to iso(issue.updatedDate), "body" to issue.body, "owner" to project.owner, "projectName" to project.name
        )
        issue.assignee?.user?.let { node["assignees"] = listOf(person(it)) }
        node["state"] = issue.state?.name
        if (issue.labels.isNotEmpty()) {
            node["labels"] = issue.labels.sortedBy { it.id }.map { linkedMapOf("labelName" to it.name, "labelColor" to it.color, "category" to it.category?.name) }
        }
        issue.milestone?.let { node["milestoneId"] = it.id; node["milestoneTitle"] = it.title }
        issue.dueDate?.let { node["dueDate"] = SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone(clock.zone) }.format(it) }
        node["refUrl"] = "${urls.origin(request)}/${project.owner}/${project.name}/issue/${issue.number}"
        val files = attachments.findByContainerTypeAndContainerIdOrderById(ResourceType.ISSUE_POST, requireNotNull(issue.id))
        if (files.isNotEmpty()) node["attachments"] = files.map { attachment(it) }
        val plain = plainComments(requireNotNull(issue.id))
        if (plain.isNotEmpty()) node["comments"] = plain
        return node
    }

    /** 부모 댓글에 한 줄 댓글을 `childComments`로 묶은 댓글 목록이다. */
    private fun plainComments(issueId: Long): List<Map<String, Any?>> {
        val all = comments.findByIssueIdOrderByCreatedDateAscIdAsc(issueId)
        val nodes = all.filter { it.parentComment == null }.associate { requireNotNull(it.id) to comment(it) }
        all.filter { it.parentComment != null }.groupBy { requireNotNull(it.parentComment?.id) }.forEach { (parentId, children) ->
            nodes[parentId]?.set("childComments", children.map { comment(it) })
        }
        return nodes.toSortedMap().values.toList()
    }

    private fun comment(comment: IssueComment): LinkedHashMap<String, Any?> {
        val node = linkedMapOf<String, Any?>(
            "id" to comment.id, "type" to ResourceType.ISSUE_COMMENT.name, "author" to author(comment.authorId?.let { users.findById(it).orElse(null) }),
            "createdAt" to iso(comment.createdDate), "body" to comment.contents
        )
        val files = attachments.findByContainerTypeAndContainerIdOrderById(ResourceType.ISSUE_COMMENT, requireNotNull(comment.id))
        if (files.isNotEmpty()) node["attachments"] = files.map { attachment(it) }
        return node
    }

    private fun author(user: User?): Map<String, Any?> = linkedMapOf("loginId" to user?.loginId.orEmpty(), "name" to user?.name, "email" to user?.email.orEmpty())

    private fun person(user: User): Map<String, Any?> = linkedMapOf("loginId" to user.loginId, "name" to user.name, "email" to user.email)

    private fun attachment(a: Attachment): Map<String, Any?> = linkedMapOf(
        "id" to a.id, "name" to a.name, "hash" to a.hash, "containerType" to a.containerType?.name, "mimeType" to a.mimeType, "size" to a.size,
        "containerId" to a.containerId.toString(), "createdDate" to a.createdDate?.time, "ownerLoginId" to a.ownerLoginId
    )

    /** `yyyy-MM-dd'T'HH:mm:ssZ` 형식이다 (v1.6 `JodaDateUtil.ISO_FORMAT`). */
    fun iso(date: Date?): String? = date?.let { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").apply { timeZone = TimeZone.getTimeZone(clock.zone) }.format(it) }
}
