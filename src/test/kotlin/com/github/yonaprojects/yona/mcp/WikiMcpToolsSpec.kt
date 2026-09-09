package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.web.WikiRestApiController
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

// 위키 MCP 도구 — IssueMcpToolsSpec/PullRequestMcpToolsSpec과 동일한 접근: 위임 여부만
// 검증한다(업무 로직 자체는 WikiRestApiControllerIntegrationSpec에서 이미 실제 bare 저장소로
// 검증됨). 핵심 검증 포인트는 McpScopeGuard가 거부하면 WikiRestApiController가 절대 호출되지
// 않아야 한다는 것 — "모든 MCP 도구는 호출 전에 스코프를 검증한다"는 이 세션의 보안 불변식이
// 위키 도구에도 동일하게 적용됨을 고정한다.
class WikiMcpToolsSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val wikiRestApiController = mockk<WikiRestApiController>()
    val scopeGuard = mockk<McpScopeGuard>()
    val tools = WikiMcpTools(projectRepository, wikiRestApiController, scopeGuard)

    val project = Project(id = 1L, owner = "yona", name = "yona", projectScope = ProjectScope.PUBLIC)
    val auth = UsernamePasswordAuthenticationToken("tester", "password")

    beforeTest {
        clearMocks(projectRepository, wikiRestApiController, scopeGuard)
        SecurityContextHolder.getContext().authentication = auth
    }

    describe("프로젝트를 찾지 못하면") {
        it("스코프 검증조차 하지 않고 McpToolException을 던져야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "unknown") } returns Optional.empty()

            shouldThrow<McpToolException> { tools.list_wiki_pages("yona", "unknown", null) }

            verify(exactly = 0) { scopeGuard.require(any(), any(), any(), any()) }
        }
    }

    describe("list_wiki_pages") {
        it("WIKI:READ 스코프를 검증한 뒤 WikiRestApiController.listPages에 위임해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, project) } returns Unit
            every { wikiRestApiController.listPages("yona", "yona", null, auth) } returns
                ResponseEntity.ok(listOf(mapOf("title" to "Home")))

            val result = tools.list_wiki_pages("yona", "yona", null)

            (result as List<*>).size shouldBe 1
            verify(exactly = 1) { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, project) }
        }

        it("스코프가 없으면 WikiRestApiController를 호출하지 않고 예외를 전파해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.list_wiki_pages("yona", "yona", null) }

            verify(exactly = 0) { wikiRestApiController.listPages(any(), any(), any(), any()) }
        }
    }

    describe("get_wiki_page") {
        it("페이지가 없으면(404) 사람이 읽을 수 있는 McpToolException으로 변환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, project) } returns Unit
            every { wikiRestApiController.getPage("yona", "yona", "NoSuchPage", auth) } returns
                ResponseEntity.notFound().build()

            shouldThrow<McpToolException> { tools.get_wiki_page("yona", "yona", "NoSuchPage") }
        }
    }

    describe("create_wiki_page") {
        it("WIKI:WRITE 스코프를 검증한 뒤 WikiRestApiController.createPage에 위임해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, project) } returns Unit
            every {
                wikiRestApiController.createPage(
                    "yona", "yona",
                    WikiRestApiController.CreateWikiPageRequest(title = "Home", content = "# Hi", message = null),
                    auth
                )
            } returns ResponseEntity.status(HttpStatus.CREATED).body(mapOf("title" to "Home", "revision" to "abc"))

            tools.create_wiki_page("yona", "yona", "Home", "# Hi", null)

            verify(exactly = 1) { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, project) }
        }

        it("WRITE 스코프가 없으면 거부되고 WikiRestApiController를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.create_wiki_page("yona", "yona", "Home", null, null) }

            verify(exactly = 0) { wikiRestApiController.createPage(any(), any(), any(), any()) }
        }
    }

    describe("update_wiki_page") {
        it("newTitle을 주면 그대로 UpdateWikiPageRequest에 담아 위임해야 한다(이름변경)") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, project) } returns Unit
            every {
                wikiRestApiController.updatePage(
                    "yona", "yona", "OldTitle",
                    WikiRestApiController.UpdateWikiPageRequest(newTitle = "NewTitle", content = "본문", message = null),
                    auth
                )
            } returns ResponseEntity.ok(mapOf("title" to "NewTitle", "revision" to "def"))

            val result = tools.update_wiki_page("yona", "yona", "OldTitle", "NewTitle", "본문", null)

            (result as Map<*, *>)["title"] shouldBe "NewTitle"
        }
    }

    describe("delete_wiki_page") {
        it("WIKI:WRITE 스코프를 검증한 뒤 삭제하고 성공 메시지를 반환해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, project) } returns Unit
            every { wikiRestApiController.deletePage("yona", "yona", "Home", null, auth) } returns
                ResponseEntity.noContent().build()

            val result = tools.delete_wiki_page("yona", "yona", "Home", null)

            result shouldBe "위키 페이지 'Home'을(를) 삭제했습니다."
        }

        it("스코프가 없으면 삭제를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.delete_wiki_page("yona", "yona", "Home", null) }

            verify(exactly = 0) { wikiRestApiController.deletePage(any(), any(), any(), any(), any()) }
        }
    }

    describe("list_wiki_history / get_wiki_diff") {
        it("history는 WIKI:READ 스코프로 위임해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, project) } returns Unit
            every { wikiRestApiController.history("yona", "yona", "Home", 0, 20, auth) } returns
                ResponseEntity.ok(listOf(mapOf("shortMessage" to "Create Home")))

            val result = tools.list_wiki_history("yona", "yona", "Home", null, null)

            (result as List<*>).size shouldBe 1
        }

        it("diff는 WIKI:READ 스코프로 위임해야 한다") {
            every { projectRepository.findByOwnerAndNameOrPreviousPlace("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.WIKI, ApiTokenPermission.READ, project) } returns Unit
            every { wikiRestApiController.diff("yona", "yona", "abc123", "Home", auth) } returns
                ResponseEntity.ok(mapOf("patch" to "diff text"))

            val result = tools.get_wiki_diff("yona", "yona", "abc123", "Home")

            (result as Map<*, *>)["patch"] shouldBe "diff text"
        }
    }
})
