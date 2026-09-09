package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.WebhookType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.webhook.Webhook
import com.github.yonaprojects.yona.domain.webhook.WebhookService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

// `@IsAllowed(Operation.UPDATE)`(resourceType 기본값 PROJECT)로 웹훅(secret 포함) 조회/생성/삭제를
// 매니저·조직관리자로 제한한다. resourceType 기본값 PROJECT는 `Resource.getResourceObject()`가
// project 자신을 `GlobalResource`로 반환해 `isGlobalResourceAllowed()`의 PROJECT 케이스(매니저
// 또는 조직관리자만)를 타는 것이지, `isProjectResourceAllowed()`의 일반 멤버 규칙이 아니다.
@Controller
class WebhookController(
    private val webhookService: WebhookService,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkWebhookPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.UPDATE)
    }

    @GetMapping("/projects/{owner}/{projectName}/webhooks")
    fun webhooks(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
        if (!checkWebhookPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        val webhooks = webhookService.findByProject(project.id ?: 0L)
        model.addAttribute("project", project)
        model.addAttribute("webhooks", webhooks)
        return "project/setting_webhook"
    }

    @PostMapping("/projects/{owner}/{projectName}/webhooks")
    fun newWebhook(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @RequestParam("payloadUrl") payloadUrl: String,
        @RequestParam(value = "secret", required = false) secret: String?,
        @RequestParam(value = "gitPush", defaultValue = "false") gitPush: Boolean,
        @RequestParam("webhookType") webhookTypeStr: String,
        authentication: Authentication?
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found")

        val user = getLoginUser(authentication)
        if (!checkWebhookPermission(project, user)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        // 이 사전 검증이 없으면 DB 컬럼 길이 제약(payloadUrl<=2000, secret<=250) 위반으로 처리되지
        // 않은 500이 노출될 수 있다.
        if (payloadUrl.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload URL은 필수 입력 항목입니다.")
        }
        if (payloadUrl.length > 2000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "입력한 Payload URL이 너무 깁니다. (최대 2000자)")
        }
        if (!secret.isNullOrEmpty() && secret.length > 250) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "입력한 Authorization Token이 너무 깁니다. (최대 250자)")
        }

        val webhookType = try {
            WebhookType.valueOf(webhookTypeStr)
        } catch (e: Exception) {
            WebhookType.SIMPLE
        }

        webhookService.createWebhook(
            project = project,
            payloadUrl = payloadUrl,
            secret = secret,
            gitPush = gitPush,
            webhookType = webhookType
        )

        return "redirect:/projects/$owner/$projectName/webhooks"
    }

    @DeleteMapping("/projects/{owner}/{projectName}/webhooks/{id}")
    @ResponseBody
    fun deleteWebhook(
        @PathVariable("owner") owner: String,
        @PathVariable("projectName") projectName: String,
        @PathVariable("id") id: Long,
        authentication: Authentication?
    ): ResponseEntity<Unit> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val user = getLoginUser(authentication)
        if (!checkWebhookPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        webhookService.deleteWebhook(id)
        return ResponseEntity.ok().build()
    }


    // `web/WebhookRestApiController.kt`의 JSON API가 위임하는 대상 — 기존 `webhooks()`는 Thymeleaf
    // 뷰 이름을 반환해 JSON 클라이언트가 파싱할 수 없다. 동일한 조회+권한 체크 로직을 재사용하되
    // JSON으로 직렬화 가능한 형태로 반환한다. secret은 `setting_webhook.html`에서도 매니저에게
    // 그대로 노출되므로(비어있으면 "NONE") API 응답에서도 동일한 노출 수준을 유지한다.
    fun listWebhooksJson(
        owner: String,
        projectName: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkWebhookPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val webhooks = webhookService.findByProject(project.id ?: 0L)
        return ResponseEntity.ok(webhooks.map { toWebhookNode(it) })
    }

    private fun toWebhookNode(webhook: Webhook): Map<String, Any?> {
        return mapOf(
            "id" to webhook.id,
            "payloadUrl" to webhook.payloadUrl,
            "secret" to webhook.secret,
            "gitPush" to webhook.gitPush,
            "webhookType" to webhook.webhookType.name,
            "createdAt" to webhook.createdAt.toString()
        )
    }
}
