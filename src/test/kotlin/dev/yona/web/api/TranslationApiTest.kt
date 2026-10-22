package dev.yona.web.api

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.translation.TranslationClient
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post as postJson
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.RecentProject
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.FavoriteOrganizationRepository
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 번역 API(`POST /-_-api/v1/translation`)를 검증한다. 번역 서비스 호출은 [TranslationClient]를 대체해 확인한다.
 *
 * 원본: `app/controllers/api/IssueApi.java:977-1004`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.translation-api=http://translator.test/api"])
class TranslationApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @MockitoBean private lateinit var client: TranslationClient

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "tr$n", name = "tr$n", email = "tr$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    @Test
    fun `이슈를 번역해 마크다운으로 그려 돌려준다`() {
        val project = projects.save(Project(owner = "tro", name = "trp", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 1))
        issues.save(Issue(project = project, number = 1, title = "제목", body = "본문 & 끝", state = State.OPEN, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
        org.mockito.Mockito.doReturn("**Translated**").`when`(client).translateLines(org.mockito.ArgumentMatchers.anyList())
        mockMvc.perform(post("/-_-api/v1/translation").session(login(newUser())).contentType(MediaType.APPLICATION_JSON).content("""{"owner":"tro","projectName":"trp","type":"issue","number":1}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.translated").value(org.hamcrest.Matchers.containsString("<strong>Translated</strong>")))
        // 번역 서비스에는 `&`를 `%26`으로 바꾼 줄 목록이 간다(v1.6과 같다).
        org.mockito.Mockito.verify(client).translateLines(listOf("Title: 제목", "", "본문 %26 끝"))
    }

    @Test
    fun `없는 대상은 404이고 로그인하지 않으면 거절한다`() {
        val project = projects.save(Project(owner = "tro2", name = "trp2", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        mockMvc.perform(post("/-_-api/v1/translation").session(login(newUser())).contentType(MediaType.APPLICATION_JSON).content("""{"owner":"tro2","projectName":"trp2","type":"issue","number":9}"""))
            .andExpect(status().isNotFound)
        mockMvc.perform(post("/-_-api/v1/translation").contentType(MediaType.APPLICATION_JSON).content("""{"owner":"tro2","projectName":"trp2","type":"issue","number":9}""")).andExpect(status().is3xxRedirection)
        org.junit.jupiter.api.Assertions.assertNotNull(project.id)
    }
}
