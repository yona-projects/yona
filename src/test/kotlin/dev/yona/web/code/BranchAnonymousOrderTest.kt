package dev.yona.web.code

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 익명 접근을 막은 사이트에서 익명이 브랜치 화면을 열 때의 응답 순서를 검증한다. v1.6 `BranchApp`은 `@IsOnlyGitAvailable`을 `@AnonymousCheck`보다
 * 먼저 선언하므로 프로젝트 검사(`AbstractProjectCheckAction`)가 바깥이다. 읽기 권한이 없는 익명은 로그인 폼 이동(303)이 아니라 403이다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.allows-anonymous-access=false"])
class BranchAnonymousOrderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService

    @Test
    fun `익명이 브랜치 화면을 열면 로그인 폼이 아니라 프로젝트 검사의 403이다`() {
        val project = projects.save(Project(owner = "bro", name = "brp", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        mockMvc.perform(get("/bro/brp/branches")).andExpect(status().isForbidden)
    }
}
