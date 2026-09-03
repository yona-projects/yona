package com.github.yonaprojects.yona.web

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `yona label list/create/edit/delete`.
// 감사표는 원래 web/LabelController.kt(전역 라벨 자동완성 `/labels`, `/categories`)로 안내했지만,
// 실제 프로젝트 스코프 라벨 CRUD는 그 파일에 없다 - create/edit/delete/list 전부
// ProjectViewController.newLabel()/updateLabelForm()/deleteLabelForm()/getIssueLabelsForRestApi()
// (ISSUE_LABEL 기준 AccessControl 체크, `/{owner}/{projectName}/issue/label(s)/...`)에 있다.
// 이 컨트롤러는 그 메서드들에 위임하는 얇은 어댑터다(재검증 후 재분류 - 계획 문서 참고).
//
// Step8.7 1번(2026-09-01) — list()는 원래 ProjectController.getProjectLabels()(다른 엔티티인
// domain/project/Label)에 위임해 create/update/delete와 엔티티가 어긋나는 버그가 있었다.
// ProjectViewController.getIssueLabelsForRestApi()(IssueLabel 기준)로 교체해 통일했다.
class LabelRestApiControllerSpec : DescribeSpec({
    val projectViewController = mockk<ProjectViewController>()

    val controller = LabelRestApiController(projectViewController)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(projectViewController)
    }

    describe("GET /api/v1/projects/{owner}/{project}/labels") {
        it("ProjectViewController.getIssueLabelsForRestApi에 위임한다") {
            every { projectViewController.getIssueLabelsForRestApi("yona", "yona", any()) } returns ResponseEntity.ok(emptyList<Any>())

            mockMvc.perform(get("/api/v1/projects/yona/yona/labels"))
                .andExpect(status().isOk)

            verify(exactly = 1) { projectViewController.getIssueLabelsForRestApi("yona", "yona", any()) }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/labels") {
        it("ProjectViewController.newLabel에 위임한다") {
            every {
                projectViewController.newLabel("yona", "yona", "bug", "red", "type", false, any())
            } returns ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to 1L))

            mockMvc.perform(
                post("/api/v1/projects/yona/yona/labels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"bug","color":"red","category":"type"}""")
            ).andExpect(status().isCreated)

            verify(exactly = 1) { projectViewController.newLabel("yona", "yona", "bug", "red", "type", false, any()) }
        }
    }

    describe("PATCH /api/v1/projects/{owner}/{project}/labels/{id}") {
        it("ProjectViewController.updateLabelForm에 위임한다") {
            every {
                projectViewController.updateLabelForm("yona", "yona", 1L, "bug", "blue", 2L, any())
            } returns ResponseEntity.ok().build()

            mockMvc.perform(
                patch("/api/v1/projects/yona/yona/labels/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"bug","color":"blue","categoryId":2}""")
            ).andExpect(status().isOk)

            verify(exactly = 1) { projectViewController.updateLabelForm("yona", "yona", 1L, "bug", "blue", 2L, any()) }
        }
    }

    describe("DELETE /api/v1/projects/{owner}/{project}/labels/{id}") {
        it("ProjectViewController.deleteLabelForm에 위임한다") {
            every {
                projectViewController.deleteLabelForm("yona", "yona", 1L, "delete", any())
            } returns ResponseEntity.ok().build()

            mockMvc.perform(delete("/api/v1/projects/yona/yona/labels/1"))
                .andExpect(status().isOk)

            verify(exactly = 1) { projectViewController.deleteLabelForm("yona", "yona", 1L, "delete", any()) }
        }
    }
})
