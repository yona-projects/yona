package dev.yona.web.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Label
import dev.yona.domain.issue.LabelRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 트랜잭션 없이 이미 저장된 데이터를 화면으로 그리는 경로를 검증한다.
 *
 * 다른 화면 테스트는 테스트 메서드 전체가 하나의 트랜잭션이라 지연 로딩이 항상 성공한다. 운영에서는
 * `open-in-view=false`라서 템플릿이 트랜잭션 밖에서 지연 로딩 연관(라벨, 구성원 등)을 건드리면
 * `LazyInitializationException`이 난다. 이 테스트는 그런 실수를 잡는다. 데이터는 커밋한 뒤 직접 지운다.
 */
@AutoConfigureMockMvc
class CommittedDataRenderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var labels: LabelRepository

    @Test
    fun `커밋된 데이터로 목록과 프로젝트 화면을 그린다`() {
        val tx = TransactionTemplate(transactionManager)
        lateinit var created: Triple<Long, Long, Long>
        tx.executeWithoutResult {
            val label = labels.save(Label(category = "OS", name = "CommittedLabel"))
            val original = projects.save(Project(owner = "cdo", name = "cd-original", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
            val fork = projects.save(
                Project(
                    owner = "cdo", name = "cd-fork", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date(),
                    originalProject = original
                ).apply { labels.add(label) }
            )
            menuSettings.save(ProjectMenuSetting(project = fork, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
            val member = users.save(User(loginId = "cdmember", name = "Member", email = "cdmember@example.com", state = UserState.ACTIVE))
            projectUsers.save(ProjectUser(user = member, project = fork, role = roles.findById(RoleType.MEMBER.id).get()))
            created = Triple(original.id!!, fork.id!!, label.id!!)
        }
        try {
            val list = Jsoup.parse(
                mockMvc.perform(get("/projects?filter=cd-")).andExpect(status().isOk).andReturn().response.contentAsString
            )
            val fork = list.select("ul.all-projects > li.project").first { it.selectFirst(".header a.black")?.text() == "cd-fork" }
            assertEquals("CommittedLabel", fork.selectFirst("a.project-label")?.text())
            assertEquals("cdo / cd-original", fork.selectFirst(".origin-title")?.text())
            assertEquals(1, fork.select(".members li").size)

            val page = Jsoup.parse(
                mockMvc.perform(get("/__test/project-page/${created.second}")).andExpect(status().isOk).andReturn().response.contentAsString
            )
            assertEquals("cdo / cd-original", page.selectFirst(".project-origin-name")?.text()?.trim())
            assertNotNull(page.selectFirst(".project-menu-outer"))
        } finally {
            tx.executeWithoutResult {
                projectUsers.deleteAll(projectUsers.findAll().filter { it.project?.id == created.second })
                menuSettings.deleteAll(menuSettings.findAll().filter { it.project?.id == created.second })
                projects.deleteById(created.second)
                projects.deleteById(created.first)
                labels.deleteById(created.third)
                users.deleteAll(users.findAll().filter { it.loginId == "cdmember" })
            }
        }
    }
}
