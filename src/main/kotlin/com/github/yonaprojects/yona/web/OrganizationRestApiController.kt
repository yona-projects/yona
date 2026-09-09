package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// `yona org list/view`. web/OrganizationViewController.kt(orgList()/organizationHome())는
// Thymeleaf 뷰(`organization/list`, `organization/view`)를 렌더링하는 세션 기반 컨트롤러라 위임
// 대상으로 쓸 수 없어(응답이 뷰 이름 String), 동일한 권한 로직(게스트 차단, HIDE_PROJECT_LISTING,
// AccessControl.getVisibleProjects)만 재사용해 JSON 응답으로 노출하는 신규 얇은 컨트롤러를 뒀다.
//
// 조직은 "저장소"가 아니라 여러 저장소를 묶는 상위 개념이라 `/api/v1/projects/{owner}/{project}/
// {resource}` 저장소 단위 스코프 모델과 자연스럽게 맞지 않는다 —
// ApiTokenAuthenticationFilter.parseAccountLevelTarget()가 이미 ADMINISTRATION 그룹에 매핑돼
// 있는 ResourceType.ORGANIZATION으로 계정 수준(project=null) 스코프 판정한다.
@RestController
@RequestMapping("/api/v1/organizations")
class OrganizationRestApiController(
    private val organizationRepository: OrganizationRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl,
    // yona OrganizationApp.java의 HIDE_PROJECT_LISTING 대응 - OrganizationViewController/
    // SearchController와 동일한 프로퍼티를 재사용한다.
    @Value("\${yona.application.hide-project-listing:false}")
    private val hideProjectListing: Boolean = false
) {

    private fun getLoginUser(authentication: Authentication?) =
        authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "") filter: String,
        @RequestParam(defaultValue = "0") page: Int,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val user = getLoginUser(authentication)
        // yona OrganizationApp의 @GuestProhibit 대응.
        if (user?.isGuest == true) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }
        if (hideProjectListing) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Any>()
        }

        val pageable = PageRequest.of(page, 30, Sort.by("id").descending())
        val orgPage = organizationRepository.findByNameContainingIgnoreCaseOrDescrContainingIgnoreCase(filter, filter, pageable)
        return ResponseEntity.ok(orgPage.map { toOrgSummaryNode(it) })
    }

    @GetMapping("/{name}")
    fun get(
        @PathVariable name: String,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        val org = organizationRepository.findByName(name).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        val visibleProjects = accessControl.getVisibleProjects(org, user)

        return ResponseEntity.ok(
            mapOf(
                "id" to org.id,
                "name" to org.name,
                "descr" to org.descr,
                "projects" to visibleProjects.map { mapOf("owner" to it.owner, "name" to it.name) }
            )
        )
    }

    private fun toOrgSummaryNode(org: Organization): Map<String, Any?> = mapOf(
        "id" to org.id,
        "name" to org.name,
        "descr" to org.descr
    )
}
