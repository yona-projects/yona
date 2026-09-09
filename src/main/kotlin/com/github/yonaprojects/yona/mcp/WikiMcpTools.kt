package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.web.WikiRestApiController
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 프로젝트 위키 읽기/쓰기 MCP 도구. IssueMcpTools/PullRequestMcpTools와 완전히 동일한
 * 원칙: 신규 비즈니스 로직 없이 기존 WikiRestApiController(이미 AccessControl/멤버십 검사를
 * 갖춘 REST 컨트롤러)에 위임만 한다 — 이 클래스가 새로 하는 일은 (1) owner/project 이름으로
 * 프로젝트를 찾는 것과 (2) McpScopeGuard로 OAuth/PAT 스코프(WIKI 그룹)를 도구 호출 "전"에
 * 검증하는 것 두 가지뿐이다.
 */
@Component
class WikiMcpTools(
    private val projectRepository: ProjectRepository,
    private val wikiRestApiController: WikiRestApiController,
    private val scopeGuard: McpScopeGuard
) {
    private fun currentAuth() = SecurityContextHolder.getContext().authentication

    private fun findProject(owner: String, project: String): Project =
        projectRepository.findByOwnerAndNameOrPreviousPlace(owner, project).orElse(null)
            ?: throw McpToolException("저장소 $owner/$project 를 찾을 수 없습니다.")

    @Tool(description = "프로젝트 위키 페이지 목록을 조회합니다(query를 주면 제목 부분일치 검색).")
    fun list_wiki_pages(
        @ToolParam(description = "저장소 소유자(로그인 ID 또는 조직명)") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "제목 부분일치 검색어, 생략 시 전체 목록", required = false) query: String?
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, found)
        return wikiRestApiController.listPages(owner, project, query, currentAuth()).unwrapForMcp()
    }

    @Tool(description = "위키 페이지 하나를 제목(슬래시로 하위 경로 표현 가능, 예: \"Guides/Setup\")으로 조회합니다.")
    fun get_wiki_page(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "페이지 제목(슬래시로 하위 경로 표현 가능)") title: String
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, found)
        return wikiRestApiController.getPage(owner, project, title, currentAuth())
            .unwrapForMcp("위키 페이지를 찾을 수 없습니다: $title")
    }

    @Tool(description = "새 위키 페이지를 만듭니다.")
    fun create_wiki_page(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "페이지 제목(슬래시로 하위 경로 표현 가능)") title: String,
        @ToolParam(description = "페이지 본문(마크다운)", required = false) content: String?,
        @ToolParam(description = "커밋 메시지, 생략하면 \"Create <title>\"", required = false) message: String?
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, found)
        val request = WikiRestApiController.CreateWikiPageRequest(title = title, content = content, message = message)
        return wikiRestApiController.createPage(owner, project, request, currentAuth()).unwrapForMcp()
    }

    @Tool(description = "위키 페이지를 수정합니다(newTitle을 주면 이름변경까지 한 커밋으로 반영).")
    fun update_wiki_page(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "수정할 페이지의 현재 제목") title: String,
        @ToolParam(description = "새 제목(이름변경), 생략하면 제목 유지", required = false) newTitle: String?,
        @ToolParam(description = "새 본문(마크다운)", required = false) content: String?,
        @ToolParam(description = "커밋 메시지, 생략하면 자동 생성", required = false) message: String?
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, found)
        val request = WikiRestApiController.UpdateWikiPageRequest(newTitle = newTitle, content = content, message = message)
        return wikiRestApiController.updatePage(owner, project, title, request, currentAuth())
            .unwrapForMcp("위키 페이지를 찾을 수 없습니다: $title")
    }

    @Tool(description = "위키 페이지를 삭제합니다.")
    fun delete_wiki_page(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "삭제할 페이지 제목") title: String,
        @ToolParam(description = "커밋 메시지, 생략하면 \"Delete <title>\"", required = false) message: String?
    ): String {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, found)
        wikiRestApiController.deletePage(owner, project, title, message, currentAuth())
            .requireSuccessForMcp("위키 페이지를 찾을 수 없습니다: $title")
        return "위키 페이지 '$title'을(를) 삭제했습니다."
    }

    @Tool(description = "위키 페이지의 리비전(커밋) 목록을 최신순으로 조회합니다.")
    fun list_wiki_history(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "페이지 제목") title: String,
        @ToolParam(description = "0부터 시작하는 페이지 번호, 생략 시 0", required = false) pageNum: Int?,
        @ToolParam(description = "페이지당 리비전 개수, 생략 시 20", required = false) pageSize: Int?
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, found)
        return wikiRestApiController.history(owner, project, title, pageNum ?: 0, pageSize ?: 20, currentAuth()).unwrapForMcp()
    }

    @Tool(description = "지정한 리비전(커밋)이 그 위키 페이지에 반영한 변경 내용을 unified diff로 조회합니다.")
    fun get_wiki_diff(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "커밋 id") commitId: String,
        @ToolParam(description = "페이지 제목") title: String
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, found)
        return wikiRestApiController.diff(owner, project, commitId, title, currentAuth()).unwrapForMcp()
    }
}
