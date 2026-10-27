package dev.yona.web.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/** `GET /:user/:project/go`(v1.6 `ProjectApp.go`)가 첫 화면을 고르는 규칙을 검증한다. */
@AutoConfigureMockMvc
@Transactional
class ProjectGoTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository

    private fun project(issue: Boolean, board: Boolean): Project {
        val project = projects.save(Project(owner = "goowner", name = "goproj", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = issue, pullRequest = true, review = true, milestone = true, board = board))
        return project
    }

    @Test
    fun `이슈 메뉴가 켜져 있으면 이슈 목록으로 넘겨 그린다`() {
        project(issue = true, board = true)
        mockMvc.perform(get("/goowner/goproj/go")).andExpect(forwardedUrl("/goowner/goproj/issues"))
    }

    @Test
    fun `이슈 메뉴가 꺼져 있고 게시판이 켜져 있으면 게시판으로 이동한다`() {
        project(issue = false, board = true)
        mockMvc.perform(get("/goowner/goproj/go")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/goowner/goproj/posts"))
    }

    @Test
    fun `둘 다 꺼져 있으면 프로젝트 홈으로 이동한다`() {
        project(issue = false, board = false)
        mockMvc.perform(get("/goowner/goproj/go")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/goowner/goproj"))
    }
}
