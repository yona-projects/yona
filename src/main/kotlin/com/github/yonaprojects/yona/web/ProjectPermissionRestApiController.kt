package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.ProjectRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// `yona admin permission list`용 신규
// JSON REST API(`/api/v1/projects/{owner}/{project}/permissions`). 기존
// `web/ProjectMemberController.kt`(`/api/projects/{projectId}(숫자 ID)/...`)엔 멤버 추가/역할변경/
// 삭제는 있지만 "현재 멤버+역할 목록" 자체를 내려주는 엔드포인트가 없었다(
// `assignableUsers`는 배정 후보 목록이지 이미 배정된 권한 매트릭스가 아니다) — 신규
// `ProjectMemberController.listMembers()`에 위임하는 얇은 어댑터로, owner/project 이름만 숫자
// projectId로 바꿔 넘긴다(`ProjectRestApiController`의 settings/fork 어댑터와 동일 패턴).
//
// ApiTokenAuthenticationFilter의 resourceSegmentToResourceType에 "permissions" ->
// ResourceType.PROJECT_SETTING(ADMINISTRATION 그룹)을 신규 매핑했다 — 위임 대상
// `ProjectMemberController.isProjectManager()`가 요구하는 권한 수준(프로젝트 매니저)과
// ADMINISTRATION 그룹의 성격이 일치한다.
@RestController
@RequestMapping("/api/v1/projects/{owner}/{project}/permissions")
class ProjectPermissionRestApiController(
    private val projectRepository: ProjectRepository,
    private val projectMemberController: ProjectMemberController
) {

    @GetMapping
    fun list(
        @PathVariable owner: String,
        @PathVariable project: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val found = projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return projectMemberController.listMembers(found.id!!, authentication)
    }
}
