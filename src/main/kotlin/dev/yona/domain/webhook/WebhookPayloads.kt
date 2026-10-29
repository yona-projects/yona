package dev.yona.domain.webhook

import dev.yona.security.SessionPermissions
import dev.yona.config.YonaProperties
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.Posting
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.PullRequestReviewAction
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.issue.Issue
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.repository.PostReceiveJobs
import dev.yona.security.AvatarService
import dev.yona.util.SiteUrls
import org.springframework.context.MessageSource
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 웹훅으로 보낼 본문(JSON)을 만든다. v1.6 `Webhook`의 `buildRequestBody…`, `build…JSON`(`app/models/Webhook.java:170-640`)에 해당한다.
 *
 * 본문의 글은 `[프로젝트] 보낸이 이벤트 설명 <주소|#번호: 제목>` 형식이다. 슬랙(`DETAIL_SLACK`)은 글 뒤에 첨부(본문, 항목, 이벤트별 색)를 더하고,
 * 구글 챗(`DETAIL_HANGOUT_CHAT`)은 같은 리소스의 알림을 한 스레드로 묶으려고 스레드 이름을 더한다. 문구는 서버 기본 언어로 만든다.
 */
@Component
class WebhookPayloads(
    private val properties: YonaProperties,
    private val messages: MessageSource,
    private val siteUrls: SiteUrls,
    private val avatars: AvatarService,
    private val permissions: SessionPermissions
) {

    private val mapper = JsonMapper.builder().build()
    private val locale: Locale get() = Locale.getDefault()

    /** 웹훅이 가리키는 서버 주소이다. 호스트 이름을 설정하지 않았으면 `localhost:9000`이다 (v1.6 `getBaseUrl`). */
    fun baseUrl(): String {
        val scheme = properties.scheme.ifEmpty { "http" }
        val host = if (properties.hostname.isNotEmpty()) properties.hostname + (properties.port?.takeIf { it != 80 }?.let { ":$it" } ?: "") else "localhost:9000"
        return "$scheme://$host"
    }

    private fun text(key: String, vararg args: Any?): String = messages.getMessage(key, args.map { it ?: "null" }.toTypedArray(), key, locale).orEmpty()

    /** ` <주소|문구>`이다. 슬랙 상세 형식은 문구의 `>`를 이스케이프한다. */
    private fun link(type: WebhookType?, url: String, message: String): String =
        " <${baseUrl()}$url|${if (type == WebhookType.DETAIL_SLACK) message.replace(">", "&gt;") else message}>"

    // --- 이슈 ---

    fun issueMessage(type: WebhookType?, eventType: EventType, sender: User, issue: Issue): String {
        val project = requireNotNull(issue.project)
        val builder = StringBuilder("[${project.name}] ${sender.name} ")
        when (eventType) {
            EventType.NEW_ISSUE -> builder.append(text("notification.type.new.issue"))
            EventType.ISSUE_STATE_CHANGED -> builder.append(text("notification.type.issue.state.changed"))
            EventType.ISSUE_ASSIGNEE_CHANGED -> builder.append(text("notification.type.issue.assignee.changed"))
            EventType.ISSUE_BODY_CHANGED -> builder.append(text("notification.type.issue.body.changed"))
            EventType.ISSUE_MILESTONE_CHANGED -> builder.append(text("notification.type.milestone.changed"))
            EventType.RESOURCE_DELETED -> builder.append(text("notification.type.issue.deleted"))
            else -> org.slf4j.LoggerFactory.getLogger(javaClass).warn("Unknown webhook event: {}", eventType)
        }
        builder.append(link(type, siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/${issue.number}"), "#${issue.number}: ${issue.title}"))
        return builder.toString()
    }

    fun issueMovedMessage(type: WebhookType?, sender: User, issue: Issue, previous: String): String {
        val project = requireNotNull(issue.project)
        return "[${project.name}] ${sender.name} " + text("notification.type.issue.moved", previous, project.name) +
            link(type, siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/${issue.number}"), "#${issue.number}: ${issue.title}")
    }

    /** 슬랙 상세 첨부: 이슈 본문과 마일스톤, 담당자, 상태 항목이다. */
    fun issueAttachments(issue: Issue, eventType: EventType): ArrayNode {
        val fields = mapper.createArrayNode()
        issue.milestone?.let { fields.add(titleValue(text("notification.type.milestone.changed"), it.title, true)) }
        fields.add(titleValue(text(""), issue.assignee?.user?.name, true))
        fields.add(titleValue(text("issue.state"), (issue.state ?: dev.yona.domain.enumeration.State.OPEN).name, true))
        return mapper.createArrayNode().add(attachment(issue.body, fields, eventType))
    }

    // --- 글, 댓글 ---

    fun postingMessage(type: WebhookType?, eventType: EventType, sender: User, posting: Posting): String {
        val project = requireNotNull(posting.project)
        val builder = StringBuilder("[${project.name}] ${sender.name} ")
        if (eventType == EventType.NEW_POSTING) builder.append(text("notification.type.new.posting")) else org.slf4j.LoggerFactory.getLogger(javaClass).warn("Unknown webhook event: {}", eventType)
        builder.append(link(type, siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/post/${posting.number}"), "#${posting.number}: ${posting.title}"))
        return builder.toString()
    }

    fun issueCommentMessage(type: WebhookType?, eventType: EventType, sender: User, comment: IssueComment): String {
        val issue = requireNotNull(comment.issue)
        val project = requireNotNull(issue.project)
        return commentMessage(type, eventType, sender, project, siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/${issue.number}#comment-${comment.id}"), issue.number, issue.title)
    }

    fun postingCommentMessage(type: WebhookType?, eventType: EventType, sender: User, comment: PostingComment): String {
        val posting = requireNotNull(comment.posting)
        val project = requireNotNull(posting.project)
        return commentMessage(type, eventType, sender, project, siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/post/${posting.number}#comment-${comment.id}"), posting.number, posting.title)
    }

    private fun commentMessage(type: WebhookType?, eventType: EventType, sender: User, project: Project, url: String, number: Long?, title: String?): String {
        val builder = StringBuilder("[${project.name}] ${sender.name} ")
        when (eventType) {
            EventType.NEW_COMMENT -> builder.append(text("notification.type.new.comment"))
            EventType.COMMENT_UPDATED -> builder.append(text("notification.type.comment.updated"))
            else -> Unit
        }
        return builder.append(link(type, url, "#$number: $title")).toString()
    }

    fun commentAttachments(contents: String?, eventType: EventType): ArrayNode = mapper.createArrayNode().add(attachment(contents, null, eventType))

    // --- 풀 리퀘스트 ---

    private fun pullRequestUrl(pullRequest: PullRequest): String {
        val project = requireNotNull(pullRequest.toProject)
        return siteUrls.path("${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/pullRequest/${pullRequest.number}")
    }

    fun pullRequestMessage(type: WebhookType?, eventType: EventType, sender: User, pullRequest: PullRequest): String {
        val project = requireNotNull(pullRequest.toProject)
        val builder = StringBuilder("[${project.name}] ${sender.name} ")
        when (eventType) {
            EventType.NEW_PULL_REQUEST -> builder.append(text("notification.type.new.pullrequest"))
            EventType.PULL_REQUEST_STATE_CHANGED -> builder.append(text("notification.type.pullrequest.state.changed"))
            EventType.PULL_REQUEST_MERGED -> builder.append(text("notification.type.pullrequest.merged"))
            EventType.PULL_REQUEST_COMMIT_CHANGED -> builder.append(text("notification.type.pullrequest.commit.changed"))
            EventType.PULL_REQUEST_ASSIGNEE_CHANGED -> builder.append(text("notification.type.pullrequest.assignee.changed"))
            EventType.PULL_REQUEST_REVIEW_REQUESTED -> builder.append(text("notification.type.pullrequest.review.requested"))
            EventType.PULL_REQUEST_REVIEW_SUBMITTED -> builder.append(text("notification.type.pullrequest.review.submitted"))
            EventType.PULL_REQUEST_REVIEW_DISMISSED -> builder.append(text("notification.type.pullrequest.review.dismissed"))
            else -> Unit
        }
        return builder.append(link(type, pullRequestUrl(pullRequest), "#${pullRequest.number}: ${pullRequest.title}")).toString()
    }

    fun pullRequestCommentMessage(sender: User, pullRequest: PullRequest, commentUrl: String): String {
        val project = requireNotNull(pullRequest.toProject)
        return "[${project.name}] ${sender.name} " + text("notification.type.new.simple.comment") + " <${baseUrl()}$commentUrl|#${pullRequest.number}: ${pullRequest.title}>"
    }

    /** 슬랙 상세 첨부: 풀 리퀘스트 본문과 보낸 사람, 보내는 브랜치, 받는 브랜치 항목이다. */
    fun pullRequestAttachments(pullRequest: PullRequest, eventType: EventType): ArrayNode {
        val fields = mapper.createArrayNode()
        fields.add(titleValue(text("pullRequest.sender"), pullRequest.contributor?.name, false))
        fields.add(titleValue(text("pullRequest.from"), pullRequest.fromBranch, true))
        fields.add(titleValue(text("pullRequest.to"), pullRequest.toBranch, true))
        return mapper.createArrayNode().add(attachment(pullRequest.body, fields, eventType))
    }

    // --- 푸시 ---

    fun pushMessage(project: Project, commitCount: Int, refNames: List<String>): String =
        text("notification.pushed.commits.to", project.name, commitCount, refNames.first())

    /** 지속적 통합 도구용 푸시 본문이다 (v1.6 `buildRequestBody(commits, refNames, sender)`). */
    fun pushJson(project: Project, commits: List<PostReceiveJobs.CommitDetail>, refNames: List<String>, sender: User): String {
        val body = mapper.createObjectNode()
        val refs = mapper.createArrayNode()
        refNames.forEach { refs.add(it) }
        body.set("ref", refs)
        val commitNodes = mapper.createArrayNode()
        commits.forEach { commitNodes.add(commitJson(project, it)) }
        body.set("commits", commitNodes)
        // 커밋이 없는 푸시(브랜치 삭제)는 v1.6 `ObjectNode.put("head_commit", null)`처럼 null이다
        body.set("head_commit", commitNodes.get(0) ?: mapper.nullNode())
        body.set("sender", senderJson(sender))
        body.set("pusher", mapper.createObjectNode().also { it.put("name", sender.name); it.put("email", sender.email) })
        body.set("repository", mapper.createObjectNode().also {
            it.put("id", project.id); it.put("name", project.name); it.put("owner", project.owner)
            it.put("html_url", siteUrls.path(dev.yona.util.UrlPaths.projectPath(project.owner, project.name))); it.put("overview", project.overview); it.put("private", project.isPrivate())
        })
        return mapper.writeValueAsString(body)
    }

    private fun commitJson(project: Project, commit: PostReceiveJobs.CommitDetail): ObjectNode {
        val node = mapper.createObjectNode()
        node.put("id", commit.id)
        node.put("message", commit.message)
        // v1.6의 시각 형식은 12시간제(`hh`)라 오후 시각이 구별되지 않는다 (R-45).
        node.put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ").format(commit.commitTime))
        node.put("url", "${baseUrl()}${siteUrls.path(dev.yona.util.UrlPaths.projectPath(project.owner, project.name))}/commit/${commit.id} ")
        node.set("author", mapper.createObjectNode().also { it.put("name", commit.authorName); it.put("email", commit.authorEmail) })
        node.set("committer", mapper.createObjectNode().also { it.put("name", commit.committerName); it.put("email", commit.committerEmail) })
        return node
    }

    private fun senderJson(sender: User): ObjectNode = mapper.createObjectNode().also {
        it.put("login", sender.loginId); it.put("id", sender.id); it.put("avatar_url", avatars.avatarUrl(sender)); it.put("type", "User")
        // v1.6 `sender.isSiteManager()`: 사이트 관리자 표에 있는 사용자인지이다
        it.put("site_admin", permissions.siteManager(sender.id))
    }

    // --- 공통 ---

    /** `{"text": …}`만 있는 본문이다. */
    fun textOnly(message: String): String = mapper.writeValueAsString(mapper.createObjectNode().also { it.put("text", message) })

    /** 글과 첨부가 있는 본문이다. */
    fun withAttachments(message: String, attachments: ArrayNode): String =
        mapper.writeValueAsString(mapper.createObjectNode().also { it.put("text", message); it.set("attachments", attachments) })

    /** 글과 스레드가 있는 본문이다. [threadId]가 없으면 스레드는 빈 객체이다. */
    fun withThread(message: String, threadId: String?): String =
        mapper.writeValueAsString(mapper.createObjectNode().also {
            it.put("text", message)
            it.set("thread", mapper.createObjectNode().also { thread -> if (threadId != null) thread.put("name", threadId) })
        })

    private fun titleValue(title: String, value: String?, short: Boolean): ObjectNode =
        mapper.createObjectNode().also { it.put("title", title); it.put("value", value); it.put("short", short) }

    private fun attachment(text: String?, fields: ArrayNode?, eventType: EventType): ObjectNode =
        mapper.createObjectNode().also {
            it.put("text", text)
            if (fields != null) it.set("fields", fields) else it.putNull("fields")
            it.put("color", properties.slack[eventType.name].orEmpty())
        }
}
