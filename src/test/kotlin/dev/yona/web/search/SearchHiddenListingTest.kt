package dev.yona.web.search

import org.springframework.boot.test.context.SpringBootTest
import dev.yona.support.selectFirst
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingComment
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.comment.ReviewComment
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 사이트가 프로젝트 목록을 숨기면(`yona.hide-project-listing`) 이슈 검색도 공개 프로젝트의 글을 구성원에게만 보인다(R-50-4).
 * v1.6은 프로젝트 검색만 목록 숨김을 따르고 이슈와 글 검색은 공개 프로젝트의 것을 그대로 찾았다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.hide-project-listing=true"])
@AutoConfigureMockMvc
@Transactional
class SearchHiddenListingTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "sh$n", name = "sh$n", email = "sh$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun titles(session: MockHttpSession?): List<String> {
        val html = mockMvc.perform(get("/search").apply { session?.let { session(it) } }.param("searchType", "issue").param("keyword", "hiddenneedle")).andExpect(status().isOk).andReturn().response.contentAsString
        return Jsoup.parse(html).select("ul.search-list-wrap li.search-list-item a.title").map { it.text().trim() }
    }

    @Test
    fun `목록 숨김이면 공개 프로젝트의 이슈도 구성원에게만 검색된다 (R-50-4)`() {
        val owner = newUser(); val member = newUser(); val outsider = newUser()
        val p = projects.save(Project(owner = owner.loginId, name = "hiddenpub", vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MEMBER.id).get()))
        issues.save(Issue(project = p, number = 1, title = "hiddenneedle", body = "x", state = State.OPEN, authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
        assertEquals(emptyList(), titles(null))
        assertEquals(emptyList(), titles(login(outsider)))
        assertEquals(listOf("hiddenneedle"), titles(login(member)))
    }

    @Test
    fun `목록 숨김이면 조직 검색은 그 조직의 구성원이나 관리자 어느 쪽이든 쓸 수 있다 (R-50-7, v1_6은 두 역할을 모두 가져야 했다)`() {
        val org = organizations.save(Organization(name = "hiddenorg", descr = "d", created = Date()))
        val member = newUser(); val admin = newUser(); val outsider = newUser()
        organizationUsers.save(OrganizationUser(user = member, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get()))
        organizationUsers.save(OrganizationUser(user = admin, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        fun status(session: MockHttpSession?) = mockMvc.perform(get("/organizations/hiddenorg/search").apply { session?.let { session(it) } }.param("searchType", "issue").param("keyword", "x")).andReturn().response.status
        assertEquals(200, status(login(member)), "일반 구성원")
        assertEquals(200, status(login(admin)), "조직 관리자")
        assertEquals(400, status(login(outsider)), "조직에 속하지 않은 사용자")
        assertEquals(400, status(null), "익명")
    }
}
