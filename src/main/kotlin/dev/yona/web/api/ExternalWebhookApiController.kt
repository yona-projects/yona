package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.Webhook
import dev.yona.domain.webhook.WebhookService
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody

/**
 * 프로젝트 웹훅 REST이다(로드맵 X-30b, `decisions.md` D-49). 웹 화면의 [dev.yona.web.project.WebhookController]에 있는 목록, 만들기, 삭제만 있고
 * 수정은 v1.6에 없어 만들지 않았다. 경로는 GitHub 자원 이름(`hooks`)을 v1.6 접두사 아래에 얹은 것이고 토큰 전용이다(X-5).
 *
 * 범위는 읽기 `read:repo_hook`, 만들기 `write:repo_hook`, 삭제 `admin:repo_hook`이고 `repo`가 셋을 포함한다(표는 `ApiScopeRules`). 웹 화면처럼 프로젝트를
 * 고칠 수 있는 사용자(프로젝트 관리자, 조직 관리자, 사이트 관리자)만 쓴다(D-36 4번). 비밀 값(`secret`)은 돌려주지 않고 있는지(`hasSecret`)만 알린다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalWebhookApiController(
    private val projectService: ProjectService,
    private val webhooks: WebhookService,
    private val requests: ApiRequests,
    private val accessControl: AccessControl,
    private val pullRequestJson: PullRequestJson
) {

    /** 프로젝트의 웹훅 목록이다. 만든 순서이다. 결과: `{"result": [{"id", "payloadUrl", "webhookType", "gitPush", "hasSecret", "createdAt"}]}`. */
    @GetMapping("$PROJECT/hooks")
    @Transactional(readOnly = true)
    fun list(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val project = authorized(owner, projectName, request) { return it }
        return ok(HttpStatus.OK, linkedMapOf("result" to webhooks.list(project).sortedBy { it.id }.map { node(it) }))
    }

    /**
     * 웹훅을 만든다. 본문 `{"payloadUrl", "secret", "webhookType", "gitPush"}` 중 `payloadUrl`만 필수이다(2000자까지). `secret`은 250자까지이고 없으면 빈 문자열,
     * `webhookType`은 `SIMPLE`(기본), `DETAIL_SLACK`, `DETAIL_HANGOUT_CHAT`, `JSON`, `gitPush`는 불리언이고 없으면 `JSON` 형식만 켠다(웹 화면이 `JSON`일 때
     * 켜 두는 것과 같다). 틀린 값은 400이다. 성공하면 201과 만든 웹훅이다.
     */
    @PostMapping("$PROJECT/hooks")
    @Transactional
    fun create(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val project = authorized(owner, projectName, request) { return it }
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val payloadUrl = json.get("payloadUrl")?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotEmpty() } ?: return requests.message(HttpStatus.BAD_REQUEST, "payloadUrl is required")
        if (payloadUrl.length > MAX_URL) return requests.message(HttpStatus.BAD_REQUEST, "payloadUrl must be at most $MAX_URL characters")
        val secret = json.get("secret")?.takeIf { !it.isNull }?.let { node -> if (node.isString) node.asString("") else return requests.message(HttpStatus.BAD_REQUEST, "secret must be a string") }
        if (secret != null && secret.length > MAX_SECRET) return requests.message(HttpStatus.BAD_REQUEST, "secret must be at most $MAX_SECRET characters")
        val type = json.get("webhookType")?.takeIf { !it.isNull }?.let { node -> WebhookType.entries.firstOrNull { it.name == node.asString("") } ?: return requests.message(HttpStatus.BAD_REQUEST, "webhookType must be one of ${WebhookType.entries.joinToString(", ")}") }
        val gitPush = json.get("gitPush")?.takeIf { !it.isNull }?.let { node -> if (node.isBoolean) node.asBoolean() else return requests.message(HttpStatus.BAD_REQUEST, "gitPush must be a boolean") }
            ?: ((type ?: WebhookType.SIMPLE) == WebhookType.JSON)
        val webhook = webhooks.create(project, payloadUrl, secret, gitPush, type)
        return ok(HttpStatus.CREATED, linkedMapOf("result" to node(webhook)))
    }

    /** 웹훅을 지운다. 이 프로젝트의 웹훅이 아니면 404이다. 성공하면 204이다. */
    @DeleteMapping("$PROJECT/hooks/{hookId}")
    @Transactional
    fun delete(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable hookId: Long, request: HttpServletRequest): Any {
        val project = authorized(owner, projectName, request) { return it }
        if (!webhooks.delete(project, hookId)) return ErrorViews.notFound()
        return ResponseEntity.noContent().build<Void>()
    }

    /** 토큰 사용자를 확인하고 프로젝트를 고칠 수 있는지 본다. 안 되면 [reject]로 응답을 돌려준다(없는 프로젝트는 404, 권한이 없으면 403, 토큰이 없으면 401). */
    private inline fun authorized(owner: String, projectName: String, request: HttpServletRequest, reject: (ResponseEntity<*>) -> Nothing): Project {
        val user = requests.tokenUser(request) ?: reject(requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request"))
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: reject(ResponseEntity.status(HttpStatus.NOT_FOUND).build<Void>())
        if (!accessControl.isAllowed(user, project.asResource(), Operation.UPDATE)) reject(requests.message(HttpStatus.FORBIDDEN, "Forbidden request"))
        return project
    }

    private fun node(webhook: Webhook): Map<String, Any?> = linkedMapOf(
        "id" to webhook.id, "payloadUrl" to webhook.payloadUrl, "webhookType" to (webhook.webhookType ?: WebhookType.SIMPLE).name, "gitPush" to (webhook.gitPush == true),
        "hasSecret" to !webhook.secret.isNullOrEmpty(), "createdAt" to pullRequestJson.iso(webhook.createdAt)
    )

    private fun ok(status: HttpStatus, body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
        const val MAX_URL = 2000
        const val MAX_SECRET = 250
    }
}
