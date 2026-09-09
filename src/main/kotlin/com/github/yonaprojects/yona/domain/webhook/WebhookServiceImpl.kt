package com.github.yonaprojects.yona.domain.webhook

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.notification.NotificationUrlResolver
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.CommitComment
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.HgCommit as YonaHgCommitAuthorParser
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
@Transactional
class WebhookServiceImpl(
    private val webhookRepository: WebhookRepository,
    private val webhookThreadRepository: WebhookThreadRepository,
    // yona Webhook.java getBaseUrl()(스킴+호스트) 대응. NotificationUrlResolver가
    // 이미 동일 목적으로 쓰는 설정값을 그대로 재사용한다.
    @Value("\${yona.base-url:}")
    private val baseUrl: String,
    // yona Webhook.java buildRequestMessage()(리소스 링크) 대응 — 이슈/게시글/PR/댓글
    // URL 계산 로직을 새로 만들지 않고, 알림메일 경로에서 이미 쓰는 것과 동일한 리졸버를 재사용한다.
    private val notificationUrlResolver: NotificationUrlResolver,
    // yona Webhook.java sendRequest(payload, webhookId, resource) 대응 — Hangout Chat
    // 응답의 thread.name을 저장하는 쓰기 경로. 비동기 HTTP 콜백 스레드에서도 트랜잭션이 걸리도록 별도
    // Spring 빈으로 분리했다(자세한 이유는 WebhookThreadRecorder 주석 참고).
    private val webhookThreadRecorder: WebhookThreadRecorder,
    private val meterRegistry: MeterRegistry
) : WebhookService {

    @Transactional(readOnly = true)
    override fun findByProject(projectId: Long): List<Webhook> {
        return webhookRepository.findByProjectId(projectId)
    }

    override fun createWebhook(
        project: Project,
        payloadUrl: String,
        secret: String?,
        gitPush: Boolean,
        webhookType: WebhookType
    ): Webhook {
        val webhook = Webhook(
            project = project,
            payloadUrl = payloadUrl,
            secret = secret,
            gitPush = gitPush,
            webhookType = webhookType,
            createdAt = Instant.now()
        )
        return webhookRepository.save(webhook)
    }

    override fun deleteWebhook(id: Long) {
        val webhook = webhookRepository.findById(id).orElse(null)
        if (webhook != null) {
            webhookRepository.delete(webhook)
        }
    }

    override fun sendWebhook(
        project: Project,
        eventType: EventType,
        sender: User,
        resource: Any
    ) {
        val webhooks = webhookRepository.findByProjectId(project.id ?: return)
        if (webhooks.isEmpty()) return

        for (webhook in webhooks) {
            if (!shouldDeliverToWebhook(webhook, eventType)) {
                continue
            }

            val payload = buildPayload(webhook, eventType, sender, resource)
            sendRequestAsync(webhook, resource, payload)
        }
    }

    /**
     * gitPush 플래그는 "push(NEW_COMMIT) 이벤트를 보낼지"만 결정한다.
     * 이슈/게시글/댓글/PR 등 push가 아닌 이벤트는 이 플래그와 무관하게 항상 전송된다.
     * 단, JSON 포맷 웹훅은 gitPush 설정과 무관하게 push 이벤트를 항상 받는다(yona 원본 동작).
     */
    internal fun shouldDeliverToWebhook(
        webhook: Webhook,
        eventType: EventType
    ): Boolean {
        if (eventType != EventType.NEW_COMMIT) {
            return true
        }
        return webhook.gitPush || webhook.webhookType == WebhookType.JSON
    }

    internal fun buildPayload(
        webhook: Webhook,
        eventType: EventType,
        sender: User,
        resource: Any
    ): String {
        val objectMapper = ObjectMapper()
        val textMessage = buildTextMessage(webhook, eventType, sender, resource)

        return when (webhook.webhookType) {
            WebhookType.DETAIL_SLACK -> {
                val root = objectMapper.createObjectNode()
                root.put("text", textMessage)

                // yona Webhook.java Posting 오버로드 대응 — 다른 리소스 타입과
                // 달리 Posting(게시글) 오버로드에는 DETAIL_SLACK 전용 분기 자체가 없어, SLACK
                // 웹훅이어도 attachments 없이 텍스트만 보낸다(buildTextPropertyOnlyJSON로 귀결).
                if (resource !is Posting) {
                    val attachments = objectMapper.createArrayNode()
                    attachments.add(buildAttachmentJSON(objectMapper, resource))
                    root.set("attachments", attachments)
                }

                objectMapper.writeValueAsString(root)
            }
            WebhookType.DETAIL_HANGOUT_CHAT -> {
                val root = objectMapper.createObjectNode()
                root.put("text", textMessage)

                // 스레드 지원 — 부모 리소스 기준 키로 조회
                val (resType, resId) = threadKeyOf(resource)
                if (resType != ResourceType.NOT_A_RESOURCE && resId.isNotBlank()) {
                    val webhookThread = webhookThreadRepository.findByWebhookIdAndResourceTypeAndResourceId(
                        webhook.id ?: 0L,
                        resType,
                        resId
                    )
                    if (webhookThread != null) {
                        val threadNode = objectMapper.createObjectNode()
                        threadNode.put("name", webhookThread.threadId)
                        root.set("thread", threadNode)
                    }
                }
                objectMapper.writeValueAsString(root)
            }
            WebhookType.JSON -> {
                if (resource is PushedCommits) {
                    buildPushPayload(webhook, sender, resource)
                } else if (resource is PushedHgCommits) {
                    buildPushPayloadForHg(webhook, sender, resource)
                } else {
                    // Raw JSON 포맷
                    val root = objectMapper.createObjectNode()
                    root.put("event", eventType.name)
                    root.put("sender", sender.name ?: "")
                    root.put("project", webhook.project?.name ?: "")
                    root.put("resourceId", getResourceId(resource))
                    root.put("resourceType", getResourceType(resource).name)
                    objectMapper.writeValueAsString(root)
                }
            }
            else -> {
                // WebhookType.SIMPLE
                val root = objectMapper.createObjectNode()
                root.put("text", textMessage)
                objectMapper.writeValueAsString(root)
            }
        }
    }

    // yona Webhook.java의 push용 buildRequestBody(commits, refNames, sender) 대응.
    // 커밋 목록이 빠진 채 event/sender/project만 담겨있던 단순 JSON 대신, GitHub 웹훅과 유사한
    // ref/commits/head_commit/sender/pusher/repository 구조로 구성한다.
    // yona Webhook.java getBaseUrl() 대응 — RouteUtil.getUrl(project)(상대경로)에 붙는
    // 스킴+호스트. NotificationUrlResolver가 쓰는 것과 동일한 yona.base-url 설정을 재사용한다.
    private fun projectUrl(project: Project?): String =
        project?.let { "$baseUrl/${it.owner}/${it.name}" } ?: baseUrl

    // yona Webhook.java buildJSONFromCommit()의
    // new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ") 대응 — 문자열 포맷까지 그대로 재현한다.
    private val commitTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'hh:mm:ssZ")

    // yona Webhook.java buildIssueDetails() / buildJsonWithPullReqtuestDetails() /
    // buildCommentDetails() / buildAttachmentJSON() 대응 — DETAIL_SLACK
    // attachment의 fields를 리소스 타입별로 yona와 동일하게 구성한다. Issue는 마일스톤(있을 때만)+담당자+상태,
    // PullRequest는 보낸사람+보낸브랜치+받는브랜치(yona 원본은 완전히 미지원이었음), 그 외(댓글 등)는
    // yona도 fields 없이 본문만 담는다. yona는 color를 `Play.application().configuration().getString(
    // "slack." + eventType, "")`로 조회하나 yona에는 이 설정 자체가 없어(기본값도 항상 "") 빈 문자열로 고정.
    private fun buildAttachmentJSON(objectMapper: ObjectMapper, resource: Any): ObjectNode {
        val fields = objectMapper.createArrayNode()
        val text: String

        when (resource) {
            is Issue -> {
                text = resource.body ?: ""
                resource.milestone?.let {
                    fields.add(buildTitleValueJSON(objectMapper, "마일 스톤 변경", it.title, true))
                }
                fields.add(buildTitleValueJSON(objectMapper, "", resource.assignee?.user?.name ?: "", true))
                fields.add(buildTitleValueJSON(objectMapper, "상태", resource.state.toString(), true))
            }
            // Posting은 위 buildPayload()의 DETAIL_SLACK 분기에서 이 함수 자체를 호출하지 않으므로
            // 이 when에는 도달하지 않는다 — legacy에도 Posting용 buildXxxDetails()가 없다.
            is PullRequest -> {
                text = resource.body ?: ""
                fields.add(buildTitleValueJSON(objectMapper, "보낸 사람", resource.contributor.name, false))
                fields.add(buildTitleValueJSON(objectMapper, "코드 보내는 곳", resource.fromBranch, true))
                fields.add(buildTitleValueJSON(objectMapper, "코드 받을 곳", resource.toBranch, true))
            }
            is IssueComment -> text = resource.contents
            is PostingComment -> text = resource.contents
            // yona Webhook.java:476-478 — 리뷰 댓글(Pull Request Comment) 이벤트의 DETAIL_SLACK
            // attachment는 댓글 자신이 아니라 buildJsonWithPullReqtuestDetails(eventPullRequest, ...)를
            // 그대로 재사용해 부모 풀 리퀘스트의 본문+필드(보낸사람/보낸브랜치/받는브랜치)를 담는다.
            is ReviewComment -> {
                val pullRequest = resource.thread?.pullRequest
                text = pullRequest?.body ?: ""
                if (pullRequest != null) {
                    fields.add(buildTitleValueJSON(objectMapper, "보낸 사람", pullRequest.contributor.name, false))
                    fields.add(buildTitleValueJSON(objectMapper, "코드 보내는 곳", pullRequest.fromBranch, true))
                    fields.add(buildTitleValueJSON(objectMapper, "코드 받을 곳", pullRequest.toBranch, true))
                }
            }
            // CommitComment는 yona Webhook.java에 대응하는 오버로드 자체가 없는 yona 전용 리소스라
            // else 분기(본문/필드 없음)로 떨어지도록 그대로 둔다(레거시에 없는 동작 추가 금지).
            else -> text = ""
        }

        val attachmentNode = objectMapper.createObjectNode()
        attachmentNode.put("text", text)
        attachmentNode.set("fields", fields)
        attachmentNode.put("color", "")
        return attachmentNode
    }

    private fun buildTitleValueJSON(
        objectMapper: ObjectMapper,
        title: String,
        value: String,
        shorten: Boolean
    ): ObjectNode {
        val titleJSON = objectMapper.createObjectNode()
        titleJSON.put("title", title)
        titleJSON.put("value", value)
        titleJSON.put("short", shorten)
        return titleJSON
    }

    private fun buildPushPayload(webhook: Webhook, sender: User, pushed: PushedCommits): String {
        val objectMapper = ObjectMapper()
        val root = objectMapper.createObjectNode()
        val project = webhook.project

        val refNodes = objectMapper.createArrayNode()
        pushed.refNames.forEach { refNodes.add(it) }
        root.set("ref", refNodes)

        val commitNodes = objectMapper.createArrayNode()
        for (commit in pushed.commits) {
            val commitNode = objectMapper.createObjectNode()
            commitNode.put("id", commit.name)
            commitNode.put("message", commit.fullMessage)
            val authorInstant = commit.authorIdent?.`when`?.toInstant()
            commitNode.put(
                "timestamp",
                authorInstant?.atZone(ZoneId.systemDefault())?.format(commitTimestampFormatter) ?: ""
            )
            commitNode.put("url", "${projectUrl(project)}/commit/${commit.name}")

            val authorNode = objectMapper.createObjectNode()
            authorNode.put("name", commit.authorIdent?.name ?: "")
            authorNode.put("email", commit.authorIdent?.emailAddress ?: "")
            commitNode.set("author", authorNode)

            val committerNode = objectMapper.createObjectNode()
            committerNode.put("name", commit.committerIdent?.name ?: "")
            committerNode.put("email", commit.committerIdent?.emailAddress ?: "")
            commitNode.set("committer", committerNode)

            commitNodes.add(commitNode)
        }
        root.set("commits", commitNodes)
        if (commitNodes.size() > 0) {
            root.set("head_commit", commitNodes.get(0))
        }

        val senderNode = objectMapper.createObjectNode()
        senderNode.put("login", sender.loginId)
        senderNode.put("id", sender.id ?: 0L)
        senderNode.put("avatar_url", sender.avatarUrl)
        senderNode.put("type", "User")
        // yona Webhook.java buildSenderJSON()의 site_admin 대응.
        senderNode.put("site_admin", sender.isSiteManager)
        root.set("sender", senderNode)

        val pusherNode = objectMapper.createObjectNode()
        pusherNode.put("name", sender.name)
        pusherNode.put("email", sender.email ?: "")
        root.set("pusher", pusherNode)

        val repositoryNode = objectMapper.createObjectNode()
        repositoryNode.put("id", project?.id ?: 0L)
        repositoryNode.put("name", project?.name ?: "")
        repositoryNode.put("owner", project?.owner ?: "")
        repositoryNode.put("html_url", projectUrl(project))
        // yona Webhook.java buildRepositoryJSON()의 overview(프로젝트 설명) 대응.
        repositoryNode.put("overview", project?.overview ?: "")
        repositoryNode.put("private", project?.projectScope != ProjectScope.PUBLIC)
        root.set("repository", repositoryNode)

        return objectMapper.writeValueAsString(root)
    }

    // buildPushPayload(PushedCommits)의 Mercurial 대응. 정확히 동일한 필드
    // 구조(ref/commits/head_commit/sender/pusher/repository)를 hg4j의 HgCommit 값 객체로 채운다 —
    // Mercurial은 저자/커미터 구분이 없으므로(HgCommit.kt 주석 참고) author와 committer는 항상
    // 동일한 값이다.
    private fun buildPushPayloadForHg(webhook: Webhook, sender: User, pushed: PushedHgCommits): String {
        val objectMapper = ObjectMapper()
        val root = objectMapper.createObjectNode()
        val project = webhook.project

        val refNodes = objectMapper.createArrayNode()
        pushed.refNames.forEach { refNodes.add(it) }
        root.set("ref", refNodes)

        val commitNodes = objectMapper.createArrayNode()
        for (commit in pushed.commits) {
            val commitNode = objectMapper.createObjectNode()
            val hex = commit.nodeId.toHex()
            commitNode.put("id", hex)
            commitNode.put("message", commit.message ?: "")
            val authorInstant = Instant.ofEpochSecond(commit.timestamp)
            commitNode.put(
                "timestamp",
                authorInstant.atZone(ZoneId.systemDefault()).format(commitTimestampFormatter)
            )
            commitNode.put("url", "${projectUrl(project)}/commit/$hex")

            val authorEmail = YonaHgCommitAuthorParser.parseAuthorEmail(commit.author) ?: ""
            val authorName = if (authorEmail.isNotEmpty()) commit.author.substringBefore("<").trim() else commit.author

            val authorNode = objectMapper.createObjectNode()
            authorNode.put("name", authorName)
            authorNode.put("email", authorEmail)
            commitNode.set("author", authorNode)

            // Mercurial 커밋에는 저자/커미터 구분이 없다 — 동일한 값을 그대로 다시 채운다.
            val committerNode = objectMapper.createObjectNode()
            committerNode.put("name", authorName)
            committerNode.put("email", authorEmail)
            commitNode.set("committer", committerNode)

            commitNodes.add(commitNode)
        }
        root.set("commits", commitNodes)
        if (commitNodes.size() > 0) {
            root.set("head_commit", commitNodes.get(0))
        }

        val senderNode = objectMapper.createObjectNode()
        senderNode.put("login", sender.loginId)
        senderNode.put("id", sender.id ?: 0L)
        senderNode.put("avatar_url", sender.avatarUrl)
        senderNode.put("type", "User")
        senderNode.put("site_admin", sender.isSiteManager)
        root.set("sender", senderNode)

        val pusherNode = objectMapper.createObjectNode()
        pusherNode.put("name", sender.name)
        pusherNode.put("email", sender.email ?: "")
        root.set("pusher", pusherNode)

        val repositoryNode = objectMapper.createObjectNode()
        repositoryNode.put("id", project?.id ?: 0L)
        repositoryNode.put("name", project?.name ?: "")
        repositoryNode.put("owner", project?.owner ?: "")
        repositoryNode.put("html_url", projectUrl(project))
        repositoryNode.put("overview", project?.overview ?: "")
        repositoryNode.put("private", project?.projectScope != ProjectScope.PUBLIC)
        root.set("repository", repositoryNode)

        return objectMapper.writeValueAsString(root)
    }

    private fun buildTextMessage(
        webhook: Webhook,
        eventType: EventType,
        sender: User,
        resource: Any
    ): String {
        val projectName = webhook.project?.name ?: ""
        val actionMessage = when (eventType) {
            EventType.NEW_ISSUE -> "새 이슈를 등록했습니다"
            EventType.ISSUE_STATE_CHANGED -> "이슈 상태를 변경했습니다"
            EventType.NEW_POSTING -> "새 게시글을 작성했습니다"
            EventType.NEW_COMMENT -> "새 댓글을 등록했습니다"
            EventType.NEW_REVIEW_COMMENT -> "새 리뷰 댓글을 등록했습니다"
            EventType.NEW_PULL_REQUEST -> "새 풀 리퀘스트를 생성했습니다"
            EventType.PULL_REQUEST_MERGED -> "풀 리퀘스트를 병합했습니다"
            EventType.PULL_REQUEST_STATE_CHANGED -> "풀 리퀘스트 상태를 변경했습니다"
            EventType.PULL_REQUEST_COMMIT_CHANGED -> "풀 리퀘스트에 커밋을 추가했습니다"
            EventType.PULL_REQUEST_REVIEW_STATE_CHANGED -> "풀 리퀘스트 리뷰 상태를 변경했습니다"
            EventType.NEW_COMMIT -> "커밋을 푸시했습니다"
            else -> "이벤트를 트리거했습니다"
        }

        // PushedCommits는 yona도 buildRequestMessage()(리소스 링크)를 쓰지 않는 별도 경로
        // (Webhook.java:668 buildRequestBody(commits, refNames, sender, title))라 링크 없이 그대로 유지.
        if (resource is PushedCommits) {
            val resourceInfo = "${resource.commits.size}개의 커밋을 ${resource.refNames.firstOrNull() ?: ""} 브랜치로 푸시했습니다"
            return "[$projectName] ${sender.name}님이 $actionMessage. $resourceInfo"
        }
        if (resource is PushedHgCommits) {
            val resourceInfo = "${resource.commits.size}개의 커밋을 ${resource.refNames.firstOrNull() ?: ""} 브랜치로 푸시했습니다"
            return "[$projectName] ${sender.name}님이 $actionMessage. $resourceInfo"
        }

        return "[$projectName] ${sender.name}님이 $actionMessage.${buildResourceLink(webhook, resource)}"
    }

    private fun buildResourceLink(webhook: Webhook, resource: Any): String {
        val linkText = when (resource) {
            is Issue -> "#${resource.number}: ${resource.title}"
            is Posting -> "#${resource.number}: ${resource.title}"
            is IssueComment ->
                "#${resource.issue.number}: ${resource.issue.title}"
            is PostingComment ->
                "#${resource.posting.number}: ${resource.posting.title}"
            // yona Webhook.java:493-499 buildRequestBody(PullRequest, ReviewComment) — 링크 텍스트는
            // 리뷰 댓글 자신이 아니라 부모 풀 리퀘스트의 "#번호: 제목". 부모를 못 찾으면(비정상 상태)
            // yona에 대응 분기가 없으므로 링크를 만들지 않는다.
            is ReviewComment ->
                resource.thread?.pullRequest?.let { "#${it.number}: ${it.title}" }
            // CommitComment는 yona Webhook.java에 대응하는 오버로드 자체가 없는 yona 전용 리소스라
            // 링크를 만들지 않는다(레거시에 없는 동작 추가 금지).
            is PullRequest -> "#${resource.number}: ${resource.title}"
            else -> null
        } ?: return ""

        val url = notificationUrlResolver.getUrl(getResourceType(resource), getResourceId(resource)) ?: return ""

        val escapedText = if (webhook.webhookType == WebhookType.DETAIL_SLACK) {
            linkText.replace(">", "&gt;")
        } else {
            linkText
        }
        return " <$url|$escapedText>"
    }

    private fun getResourceType(resource: Any): ResourceType {
        return when (resource) {
            is Issue -> ResourceType.ISSUE_POST
            is Posting -> ResourceType.BOARD_POST
            is IssueComment -> ResourceType.ISSUE_COMMENT
            is PostingComment -> ResourceType.NONISSUE_COMMENT
            is ReviewComment -> ResourceType.REVIEW_COMMENT
            is CommitComment -> ResourceType.COMMIT_COMMENT
            is PushedCommits -> ResourceType.COMMIT
            is PushedHgCommits -> ResourceType.COMMIT
            is PullRequest -> ResourceType.PULL_REQUEST
            else -> ResourceType.NOT_A_RESOURCE
        }
    }

    private fun getResourceId(resource: Any): String {
        return when (resource) {
            is Issue -> resource.id?.toString() ?: ""
            is Posting -> resource.id?.toString() ?: ""
            is IssueComment -> resource.id?.toString() ?: ""
            is PostingComment -> resource.id?.toString() ?: ""
            is ReviewComment -> resource.id?.toString() ?: ""
            is CommitComment -> resource.id?.toString() ?: ""
            is PushedCommits -> resource.commits.firstOrNull()?.name ?: ""
            is PushedHgCommits -> resource.commits.firstOrNull()?.nodeId?.toHex() ?: ""
            is PullRequest -> resource.id?.toString() ?: ""
            else -> ""
        }
    }

    private fun sendRequestAsync(webhook: Webhook, resource: Any, payload: String) {
        val sample = Timer.start(meterRegistry)
        try {
            val httpClient = HttpClient.newBuilder().build()
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(webhook.payloadUrl))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Yobi-Hookshot")
                .POST(HttpRequest.BodyPublishers.ofString(payload))

            if (!webhook.secret.isNullOrBlank()) {
                requestBuilder.header("Authorization", "token ${webhook.secret}")
            }

            val request = requestBuilder.build()
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept { response ->
                    sample.stop(meterRegistry.timer("yona.webhook.duration"))
                    val statusCode = response.statusCode()
                    meterRegistry.counter("yona.webhook.sent", "status", statusCode.toString()).increment()
                    if (statusCode < 200 || statusCode >= 300) {
                        println("[Webhook] HTTP 전송 실패: $statusCode - ${response.body()}")
                    } else {
                        recordHangoutThreadIfNeeded(webhook, resource, response.body())
                    }
                }
                .exceptionally { ex ->
                    sample.stop(meterRegistry.timer("yona.webhook.duration"))
                    meterRegistry.counter("yona.webhook.sent", "status", "connection_error").increment()
                    println("[Webhook] 전송 중 예외 발생: ${ex.message}")
                    null
                }
        } catch (e: Exception) {
            meterRegistry.counter("yona.webhook.sent", "status", "client_error").increment()
            println("[Webhook] HTTP 클라이언트 생성 중 오류: ${e.message}")
        }
    }

    // yona Webhook.java:643-648 — 응답 본문의 thread.name을 파싱해 WebhookThread로 저장한다.
    // DETAIL_HANGOUT_CHAT이 아닌 웹훅이거나 응답에 thread.name이 없으면(threadId가 빈 문자열이면
    // WebhookThreadRecorder가 스스로 걸러낸다) 아무 일도 하지 않는다.
    internal fun recordHangoutThreadIfNeeded(webhook: Webhook, resource: Any, responseBody: String) {
        if (webhook.webhookType != WebhookType.DETAIL_HANGOUT_CHAT) return
        val webhookId = webhook.id ?: return

        val threadId = try {
            ObjectMapper().readTree(responseBody).path("thread").path("name").asText()
        } catch (e: Exception) {
            return
        }

        val (resType, resId) = threadKeyOf(resource)
        webhookThreadRecorder.recordThreadIfAbsent(webhookId, resType, resId, threadId)
    }

    // yona Webhook.java eventComment.getParent().asResource() / eventPullRequest.asResource()
    // 대응 — Hangout Chat 스레드 키는 댓글 자신이 아니라 부모 리소스(이슈/게시글/PR) 기준으로
    // 계산해야 같은 이슈/게시글/PR에 달리는 댓글들이 하나의 대화 스레드로 묶인다. CommitComment는 yona에
    // 대응 이벤트 자체가 없어 부모 매핑 규칙이 없으므로 자기 자신의 키를 그대로 쓴다
    // (레거시에 없는 동작을 새로 추가하지 않는다).
    private fun threadKeyOf(resource: Any): Pair<ResourceType, String> {
        return when (resource) {
            is IssueComment ->
                ResourceType.ISSUE_POST to (resource.issue.id?.toString() ?: "")
            is PostingComment ->
                ResourceType.BOARD_POST to (resource.posting.id?.toString() ?: "")
            is ReviewComment ->
                resource.thread?.pullRequest?.let { ResourceType.PULL_REQUEST to (it.id?.toString() ?: "") }
                    ?: (getResourceType(resource) to getResourceId(resource))
            else -> getResourceType(resource) to getResourceId(resource)
        }
    }
}
