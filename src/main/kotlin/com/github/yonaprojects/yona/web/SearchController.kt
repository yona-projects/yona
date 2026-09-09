package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.SearchType
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.support.SearchService
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class SearchController(
    private val searchService: SearchService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository,
    private val organizationUserRepository: OrganizationUserRepository,
    // legacy Application.HIDE_PROJECT_LISTING 대응.
    @Value("\${yona.application.hide-project-listing:false}")
    private val hideProjectListing: Boolean = false
) {

    @GetMapping("/search")
    fun searchInAll(
        @RequestParam(value = "keyword", required = false, defaultValue = "") keyword: String,
        @RequestParam(value = "searchType", required = false, defaultValue = "auto") searchTypeVal: String,
        @RequestParam(value = "pageNum", required = false, defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        if (keyword.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요.")
        }

        val searchType = SearchType.getValue(searchTypeVal)
        // legacy SearchApp.searchInAll()의 "SearchType.NA면 badRequest" 가드 대응.
        if (searchType == SearchType.NA) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val pageable = PageRequest.of(pageNum - 1, 20)

        val searchResult = searchService.searchInAll(keyword, searchType, loginUser, pageable)

        model.addAttribute("keyword", keyword)
        model.addAttribute("searchResult", searchResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("org", null)
        model.addAttribute("project", null)

        return "search/list"
    }

    @GetMapping(value = ["/org/{organizationName}/search", "/organizations/{organizationName}/search"])
    fun searchInAGroup(
        @PathVariable organizationName: String,
        @RequestParam(value = "keyword", required = false, defaultValue = "") keyword: String,
        @RequestParam(value = "searchType", required = false, defaultValue = "auto") searchTypeVal: String,
        @RequestParam(value = "pageNum", required = false, defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        if (keyword.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요.")
        }

        val organization = organizationRepository.findByName(organizationName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "조직을 찾을 수 없습니다.")
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

        // legacy SearchApp 대응. HIDE_PROJECT_LISTING이 켜져 있으면 이 조직의 ORG_MEMBER이면서
        // 동시에 ORG_ADMIN인 사용자만 그룹 검색이 허용된다(legacy 원문 그대로 — 두 역할이 DB상
        // 상호 배타적이라 사실상 항상 거부되는 legacy 자체의 동작을 그대로 재현한 것).
        if (hideProjectListing) {
            val orgUser = loginUser?.id?.let {
                organizationUserRepository.findByOrganizationIdAndUserId(organization.id!!, it).orElse(null)
            }
            val isMember = orgUser?.role?.id == RoleType.ORG_MEMBER.roleType
            val isAdmin = orgUser?.role?.id == RoleType.ORG_ADMIN.roleType
            if (!isMember || !isAdmin) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST)
            }
        }

        val searchType = SearchType.getValue(searchTypeVal)
        // legacy SearchApp.searchInAGroup()의 "SearchType.NA면 badRequest" 가드 대응. legacy 조건의
        // 나머지 절(organization == null)은 yona에서는 위에서 이미 orElseThrow{404}로 먼저
        // 처리되므로 여기서 다시 확인할 필요가 없다.
        if (searchType == SearchType.NA) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        }
        val pageable = PageRequest.of(pageNum - 1, 20)

        val searchResult = searchService.searchInAGroup(keyword, searchType, loginUser, organization, pageable)

        model.addAttribute("keyword", keyword)
        model.addAttribute("searchResult", searchResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("org", organization)
        model.addAttribute("project", null)

        return "search/list"
    }

    @GetMapping("/{owner}/{projectName}/search")
    fun searchInAProject(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(value = "keyword", required = false, defaultValue = "") keyword: String,
        @RequestParam(value = "searchType", required = false, defaultValue = "auto") searchTypeVal: String,
        @RequestParam(value = "pageNum", required = false, defaultValue = "1") pageNum: Int,
        authentication: Authentication?,
        model: Model
    ): String {
        if (keyword.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요.")
        }

        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")
        }

        val searchType = SearchType.getValue(searchTypeVal)
        // legacy SearchApp.searchInAProject()의 "SearchType.NA 또는 PROJECT면 badRequest" 가드
        // 대응 — 프로젝트 범위 검색에서 "프로젝트를 찾는다"는 검색 타입 자체가 무의미하다.
        if (searchType == SearchType.NA || searchType == SearchType.PROJECT) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        }

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        val pageable = PageRequest.of(pageNum - 1, 20)

        val searchResult = searchService.searchInAProject(keyword, searchType, loginUser, project, pageable)

        model.addAttribute("keyword", keyword)
        model.addAttribute("searchResult", searchResult)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("org", null)
        model.addAttribute("project", project)

        return "search/list"
    }
}
