package dev.yona.web.api

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
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
    private val postingComments: PostingCommentRepository,
    private val attachmentJson: dev.yona.web.AttachmentJson,
    private val urls: RepositoryUrls,
    private val clock: Clock,
    private val nullUser: dev.yona.web.NullUserDisplay
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
        issue.dueDate?.let { node["dueDate"] = longDate(it) }
        node["refUrl"] = "${urls.origin(request)}${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/${issue.number}"
        val files = attachmentJson.list(ResourceType.ISSUE_POST, requireNotNull(issue.id))
        if (files.isNotEmpty()) node["attachments"] = files
        val plain = plainComments(requireNotNull(issue.id))
        if (plain.isNotEmpty()) node["comments"] = plain
        return node
    }

    /** 게시글 하나의 JSON이다. 이슈 항목(담당자, 상태, 라벨, 마일스톤, 마감일, `refUrl`)은 없다. */
    fun ofPosting(posting: Posting): Map<String, Any?> {
        val project = requireNotNull(posting.project)
        val node = linkedMapOf<String, Any?>(
            "number" to posting.number, "id" to posting.id, "title" to posting.title, "type" to ResourceType.BOARD_POST.name,
            "author" to author(posting.authorId?.let { users.findById(it).orElse(null) }),
            "createdAt" to iso(posting.createdDate), "updatedAt" to iso(posting.updatedDate), "body" to posting.body, "owner" to project.owner, "projectName" to project.name
        )
        val files = attachmentJson.list(ResourceType.BOARD_POST, requireNotNull(posting.id))
        if (files.isNotEmpty()) node["attachments"] = files
        val plain = plainComments(
            postingComments.findByPostingIdOrderByCreatedDateAscIdAsc(requireNotNull(posting.id)).map { Row(it.id, it.authorId, it.createdDate, it.contents, it.parentComment?.id, ResourceType.NONISSUE_COMMENT) }
        )
        if (plain.isNotEmpty()) node["comments"] = plain
        return node
    }

    /** 이슈 댓글과 게시글 댓글을 같은 모양으로 다루기 위한 값이다. */
    private data class Row(val id: Long?, val authorId: Long?, val created: Date?, val contents: String?, val parentId: Long?, val type: ResourceType)

    private fun plainComments(issueId: Long): List<Map<String, Any?>> =
        plainComments(comments.findByIssueIdOrderByCreatedDateAscIdAsc(issueId).map { Row(it.id, it.authorId, it.createdDate, it.contents, it.parentComment?.id, ResourceType.ISSUE_COMMENT) })

    /** 부모 댓글에 한 줄 댓글을 `childComments`로 묶은 댓글 목록이다. */
    private fun plainComments(all: List<Row>): List<Map<String, Any?>> {
        val nodes = all.filter { it.parentId == null }.associate { requireNotNull(it.id) to comment(it) }
        all.filter { it.parentId != null }.groupBy { requireNotNull(it.parentId) }.forEach { (parentId, children) ->
            nodes[parentId]?.set("childComments", children.map { comment(it) })
        }
        return nodes.toSortedMap().values.toList()
    }

    private fun comment(row: Row): LinkedHashMap<String, Any?> {
        val node = linkedMapOf<String, Any?>(
            "id" to row.id, "type" to row.type.name, "author" to author(row.authorId?.let { users.findById(it).orElse(null) }),
            "createdAt" to iso(row.created), "body" to row.contents
        )
        val files = attachmentJson.list(row.type, requireNotNull(row.id))
        if (files.isNotEmpty()) node["attachments"] = files
        return node
    }

    private fun author(user: User?): Map<String, Any?> = linkedMapOf("loginId" to user?.loginId.orEmpty(), "name" to (user?.name ?: nullUser.name()), "email" to user?.email.orEmpty())

    private fun person(user: User): Map<String, Any?> = linkedMapOf("loginId" to user.loginId, "name" to user.name, "email" to user.email)


    /** 외부 API가 쓰는 긴 날짜 형식 `yyyy-MM-dd a hh:mm:ss Z`(영어)이다 (v1.6 `getDateString`). */
    fun longDate(date: Date?): String? = date?.let { SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone(clock.zone) }.format(it) }

    /** `yyyy-MM-dd'T'HH:mm:ssZ` 형식이다 (v1.6 `JodaDateUtil.ISO_FORMAT`). 날짜가 없으면 빈 문자열이다 (`getDateString`은 `null`에서 `""`를 돌려준다). */
    fun iso(date: Date?): String = date?.let { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").apply { timeZone = TimeZone.getTimeZone(clock.zone) }.format(it) }.orEmpty()
}
