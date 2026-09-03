package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.vcs.SvnRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.net.URI
import java.nio.file.Files

// SvnAuthorizationFilterSpec은 인가만 검증했고, 실제 DAVServlet 서빙 자체는 검증된 적이 없었다
// (사용자 지시로 신설). SvnController가 진짜 로컬 SVN 저장소를 대상으로 WebDAV 프로토콜을 실제로
// 서빙하는지(legacy SvnApp.java의 DAVServlet 위임과 동일한 결과) end-to-end로 검증한다.
//
// PROPFIND를 주 검증 수단으로 쓰는 이유: OPTIONS는 MockMvc standaloneSetup()의 dispatchOptions
// 기본값(false)에 좌우되는 테스트 인프라 특성이 섞여 판정이 흐려진다(운영환경 자체의 실제 동작은
// SvnControllerOptionsIntegrationSpec에서 실제 Spring Boot 컨텍스트로 별도 검증). PROPFIND는 서블릿
// 스펙에 아예 없는 메서드라 HttpServlet 기본 처리로 절대 응답될 수 없고, 207+multistatus XML이
// 돌아온다는 것 자체가 이 매핑이 진짜로 DAVServlet까지 도달했다는 확실한 증거가 된다.
class SvnControllerSpec : DescribeSpec({

    fun newTempBaseDir(): String = Files.createTempDirectory("yona-svnctrl-test").toFile().absolutePath

    fun buildController(baseDir: String) = SvnController(baseDir, MockServletContext())

    fun buildMockMvc(controller: SvnController) = MockMvcBuilders.standaloneSetup(controller).build()

    describe("경로 형식 검증 (legacy SvnApp.service():94-96 대응)") {
        // legacy conf/routes의 "/svn/*path" catch-all(Play 와일드카드)과 달리 이전 yona 매핑
        // "/svn/{ownerName}/{projectName}/**"은 두 세그먼트가 다 있어야만 이 핸들러에 도달했다 —
        // 짧은 경로는 Spring MVC 자체가 (컨트롤러에 도달하지도 못한 채) 404로 처리해버려 legacy의
        // 403과 달랐다(TASK-0264에서 발견해 매핑을 "/svn/**"로 넓혀 수정).
        it("owner/project 세그먼트가 모두 없는 요청은 legacy와 동일하게 403을 반환해야 한다") {
            val controller = buildController(newTempBaseDir())
            val mockMvc = buildMockMvc(controller)

            mockMvc.perform(MockMvcRequestBuilders.get("/svn/onlyowner"))
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }

        it("/svn/만 요청해도(세그먼트 전무) 핸들러에 도달해 403을 반환해야 한다") {
            val controller = buildController(newTempBaseDir())
            val mockMvc = buildMockMvc(controller)

            mockMvc.perform(MockMvcRequestBuilders.get("/svn/"))
                .andExpect(MockMvcResultMatchers.status().isForbidden)
        }
    }

    describe("실제 로컬 SVN 저장소에 대한 WebDAV 서빙(DAVServlet 배선)") {
        it("PROPFIND 요청에 207 Multi-Status와 실제 WebDAV XML 본문으로 응답해야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("gildong2", "myproject2", baseDir) { null }
            repo.create()

            val controller = buildController(baseDir)
            val mockMvc = buildMockMvc(controller)

            val result = mockMvc.perform(
                MockMvcRequestBuilders.request(HttpMethod.valueOf("PROPFIND"), URI.create("/svn/gildong2/myproject2"))
                    .header("Depth", "0")
            ).andReturn()

            result.response.status shouldBe 207
            result.response.contentAsString.contains("multistatus") shouldBe true
        }

        // 실제 운영 파이프라인에서는 SvnAuthorizationFilter가 이 요청보다 먼저 실행되어 DB에 없는
        // 프로젝트는 이미 404로 걸러진다(SvnAuthorizationFilterSpec). 이 테스트는 그 필터를 우회한
        // SvnController 단독 호출 시나리오 — legacy SvnApp.startDavService()도 DAVServlet이 던지는
        // 예외를 잡아 500으로 응답하므로(TASK-0264에서 발견해 동일하게 try/catch+로깅 추가), 스택
        // 트레이스가 그대로 노출되는 게 아니라 "제어된" 500이어야 한다는 게 검증 대상이다.
        it("물리 저장소 자체가 없으면 legacy와 동일하게 (제어된) 500을 반환해야 한다") {
            val baseDir = newTempBaseDir()

            val controller = buildController(baseDir)
            val mockMvc = buildMockMvc(controller)

            val result = mockMvc.perform(
                MockMvcRequestBuilders.request(HttpMethod.valueOf("PROPFIND"), URI.create("/svn/nouser/noproject"))
                    .header("Depth", "0")
            ).andReturn()

            result.response.status shouldBe 500
        }

        // catch 블록의 `if (!response.isCommitted)` — DAVServlet이 예외를 던지기 전에 이미
        // 응답을 커밋해버린 경우엔 sendError()를 또 호출하면 IllegalStateException이 나므로
        // 건너뛰어야 한다. 실제 DAVServlet으로 이 순서를 자연스럽게 재현하기 어려워, 응답 객체를
        // mock으로 대체해 isCommitted=true를 강제하고 controller.service()를 MockMvc 없이 직접
        // 호출해서 검증한다.
        it("예외 발생 시점에 응답이 이미 커밋되어 있으면 sendError를 다시 호출하지 않아야 한다") {
            val baseDir = newTempBaseDir()
            val controller = buildController(baseDir)

            val request = MockHttpServletRequest("PROPFIND", "/svn/nouser/noproject")
            request.addHeader("Depth", "0")
            val response = mockk<HttpServletResponse>(relaxed = true)
            every { response.isCommitted } returns true

            controller.service(request, response)

            verify(exactly = 0) { response.sendError(any()) }
        }

        it("owner별로 DAVServlet 인스턴스를 캐시하면서도 서로 다른 owner의 저장소를 모두 정상 서빙해야 한다") {
            val baseDir = newTempBaseDir()
            val repoA = SvnRepository("ownerA", "projA", baseDir) { null }
            repoA.create()
            val repoB = SvnRepository("ownerB", "projB", baseDir) { null }
            repoB.create()

            val controller = buildController(baseDir)
            val mockMvc = buildMockMvc(controller)

            val resultA = mockMvc.perform(
                MockMvcRequestBuilders.request(HttpMethod.valueOf("PROPFIND"), URI.create("/svn/ownerA/projA"))
                    .header("Depth", "0")
            ).andReturn()
            val resultB = mockMvc.perform(
                MockMvcRequestBuilders.request(HttpMethod.valueOf("PROPFIND"), URI.create("/svn/ownerB/projB"))
                    .header("Depth", "0")
            ).andReturn()

            resultA.response.status shouldBe 207
            resultB.response.status shouldBe 207
            // 캐시된 DAVServlet 인스턴스가 owner를 혼동하지 않고 각자 자기 저장소만 서빙해야 한다.
            resultA.response.contentAsString.contains("projA") shouldBe true
            resultB.response.contentAsString.contains("projB") shouldBe true
        }

        it("커밋된 파일이 있는 저장소를 PROPFIND하면 그 파일명이 응답 XML에 포함되어야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("gildong3", "myproject3", baseDir) { null }
            repo.create()

            // 저수준 커밋 에디터로 실제 파일 하나를 커밋해둔다(SvnRepositorySpec의 commitFile과 동일 기법).
            val svnURL = org.tmatesoft.svn.core.SVNURL.fromFile(repo.getDirectory())
            val svnRepository = org.tmatesoft.svn.core.io.SVNRepositoryFactory.create(svnURL)
            try {
                val editor = svnRepository.getCommitEditor("초기 커밋", null)
                editor.openRoot(-1)
                editor.addFile("hello.txt", null, -1)
                editor.applyTextDelta("hello.txt", null)
                val deltaGenerator = org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator()
                val checksum = deltaGenerator.sendDelta(
                    "hello.txt",
                    java.io.ByteArrayInputStream("hello".toByteArray()),
                    editor,
                    true
                )
                editor.closeFile("hello.txt", checksum)
                editor.closeDir()
                editor.closeEdit()
            } finally {
                svnRepository.closeSession()
            }

            val controller = buildController(baseDir)
            val mockMvc = buildMockMvc(controller)

            val result = mockMvc.perform(
                MockMvcRequestBuilders.request(HttpMethod.valueOf("PROPFIND"), URI.create("/svn/gildong3/myproject3"))
                    .header("Depth", "1")
            ).andReturn()

            result.response.status shouldBe 207
            result.response.contentAsString.contains("hello.txt") shouldBe true
        }

        // serviceOptions()는 OPTIONS 요청을 service()로 그대로 위임하기만 한다 — 직접 호출로
        // 위임 자체를 검증한다(MockMvc의 dispatchOptions 기본값이 false라 실제 라우팅으로는
        // 도달시키기 어렵다는 점은 클래스 상단 주석 참고).
        it("serviceOptions()는 service()로 그대로 위임해야 한다") {
            val baseDir = newTempBaseDir()
            val repo = SvnRepository("gildong4", "myproject4", baseDir) { null }
            repo.create()
            val controller = buildController(baseDir)

            val request = MockHttpServletRequest("OPTIONS", "/svn/gildong4/myproject4")
            request.addHeader("Depth", "0")
            val response = org.springframework.mock.web.MockHttpServletResponse()

            controller.serviceOptions(request, response)

            // OPTIONS는 PROPFIND가 아니므로 207(Multi-Status)이 아니라 200으로 응답한다 —
            // service()로 실제 위임됐다는 사실 자체가 검증 대상이다(오류 없이 정상 완료).
            response.status shouldBe 200
        }
    }
})
