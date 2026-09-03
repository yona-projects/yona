package com.github.yonaprojects.yona.web

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// yona-wiki P3-02 Step8.6 항목1(2026-09-01, 우선순위 1위) — `yona admin webhook list`용 신규
// JSON REST API(`/api/v1/projects/{owner}/{project}/webhooks`). 기존 `web/WebhookController.kt`는
// 세션/폼 기반 레거시 MVC 컨트롤러라 목록 조회(GET)가 Thymeleaf HTML(`project/setting_webhook`)만
// 반환해(4라운드 완료 로그 참고) CLI가 파싱할 구조화된 데이터가 없었다 — 이 컨트롤러는 그
// `WebhookController`가 이미 갖고 있는 프로젝트 조회 + 권한 체크(Operation.UPDATE) 로직을 그대로
// 재사용하는 `listWebhooksJson()`에 위임하는 얇은 어댑터다(Step4~6과 동일 패턴, 신규 서비스 로직 없음).
//
// ApiTokenAuthenticationFilter의 resourceSegmentToResourceType엔 "webhooks" ->
// ResourceType.WEBHOOK(WEBHOOKS 그룹)이 Step1~3부터 이미 매핑돼 있어 필터 변경이 필요 없다.
@RestController
@RequestMapping("/api/v1/projects/{owner}/{project}/webhooks")
class WebhookRestApiController(
    private val webhookController: WebhookController
) {

    @GetMapping
    fun list(
        @PathVariable owner: String,
        @PathVariable project: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        return webhookController.listWebhooksJson(owner, project, authentication)
    }
}
