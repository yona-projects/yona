package dev.yona.web.git

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
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
 * 저장소 디렉터리가 없는 프로젝트(만들다 만 프로젝트 등)를 복제하려 하면 잘린 응답이 아니라 404를 돌려주는지 검증한다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class GitHttpMissingRepositoryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository

    @Test
    fun `저장소가 없으면 광고 요청은 404이다`() {
        val p = projects.save(Project(owner = "gnorepo", name = "norepo", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        mockMvc.perform(get("/${p.owner}/${p.name}/info/refs").param("service", "git-upload-pack")).andExpect(status().isNotFound)
    }
}
