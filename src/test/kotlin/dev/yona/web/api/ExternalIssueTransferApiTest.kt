package dev.yona.web.api

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이슈 옮기기 REST(로드맵 X-37d, `decisions.md` D-52)를 검증한다. 웹의 이슈 수정(`targetProjectId`)과 같은 서비스(`IssueService.update`)를 쓴다: 이슈는 대상 프로젝트의
 * 다음 번호를 받고, 마일스톤은 지워지며, 라벨은 옮기는 사람이 대상 프로젝트의 구성원이면 같은 분류와 이름으로 옮겨지고, 이동 이벤트가 남는다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalIssueTransferApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var issueEvents: IssueEventRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "tr$n", name = "tr$n", email = "tr$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "trt$n"; dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "tro$n", name = "trp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun roleOn(project: Project, user: User, role: RoleType = RoleType.MEMBER) { projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get())) }

    private fun issue(project: Project, author: User, number: Long = 1): Issue {
        project.lastIssueNumber = maxOf(project.lastIssueNumber ?: 0, number); projects.save(project)
        return issues.save(Issue(project = project, number = number, title = "옮길 이슈", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, createdDate = Date()))
    }

    private fun api(project: Project, path: String) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun transfer(project: Project, number: Long, token: String?, body: String?) =
        mockMvc.perform(post(api(project, "issues/$number/transfer")).apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun target(project: Project) = """{"targetOwner":"${project.owner}","targetProject":"${project.name}"}"""

    @Test
    fun `이슈를 다른 프로젝트로 옮기면 새 번호를 받고 마일스톤은 지워지고 라벨은 옮겨지며 이동 이벤트가 남는다`() {
        val from = newProject(); val to = newProject(); val user = newUser(); roleOn(from, user); roleOn(to, user)
        val category = categories.save(IssueLabelCategory(project = from, name = "종류", isExclusive = false)); val label = labels.save(IssueLabel(project = from, category = category, name = "bug", color = "#f00"))
        val milestone = milestones.save(Milestone(project = from, title = "1.0", contents = "", state = State.OPEN))
        val i = issue(from, user); i.labels.add(label); i.milestone = milestone; issues.save(i)
        comments.save(IssueComment(contents = "댓글", createdDate = Date(), authorId = user.id, authorLoginId = user.loginId, authorName = user.name, issue = i, projectId = from.id!!))
        issue(to, user, 5)
        transfer(from, 1, tokenOf(user), target(to)).andExpect(status().isOk)
            .andExpect(jsonPath("$.result.owner").value(to.owner)).andExpect(jsonPath("$.result.projectName").value(to.name)).andExpect(jsonPath("$.result.number").value(6))
            .andExpect(jsonPath("$.result.milestoneId").doesNotExist()).andExpect(jsonPath("$.result.labels[0].labelName").value("bug")).andExpect(jsonPath("$.result.comments[0].body").value("댓글"))
        entityManager.flush(); entityManager.clear()
        assertNull(issues.findByProjectIdAndNumber(from.id!!, 1))
        val moved = assertNotNull(issues.findByProjectIdAndNumber(to.id!!, 6))
        assertEquals(to.id, moved.project?.id); assertNull(moved.milestone)
        assertTrue(moved.labels.any { it.name == "bug" && it.project?.id == to.id })
        assertTrue(issueEvents.findByIssueIdOrderByCreatedAscIdAsc(moved.id!!).any { it.eventType == EventType.ISSUE_MOVED && it.newValue == "${to.owner}/${to.name}" })
    }

    @Test
    fun `옮기기의 오류와 권한`() {
        val from = newProject(); val to = newProject(); val secret = newProject(ProjectScope.PRIVATE); val user = newUser(); roleOn(from, user); roleOn(to, user)
        issue(from, user)
        transfer(from, 1, tokenOf(user), target(from)).andExpect(status().isBadRequest)
        transfer(from, 1, tokenOf(user), """{"targetOwner":"${to.owner}","targetProject":"none-$sequence"}""").andExpect(status().isNotFound)
        transfer(from, 1, tokenOf(user), """{"targetOwner":"${to.owner}"}""").andExpect(status().isBadRequest)
        transfer(from, 1, tokenOf(user), """{}""").andExpect(status().isBadRequest)
        transfer(from, 1, tokenOf(user), null).andExpect(status().isBadRequest)
        transfer(from, 1, tokenOf(user), target(secret)).andExpect(status().isForbidden)
        transfer(from, 9, tokenOf(user), target(to)).andExpect(status().isNotFound)
        transfer(from, 1, tokenOf(newUser()), target(to)).andExpect(status().isForbidden)
        transfer(from, 1, null, target(to)).andExpect(status().isUnauthorized)
        entityManager.flush(); entityManager.clear()
        assertNotNull(issues.findByProjectIdAndNumber(from.id!!, 1))
    }

    @Test
    fun `대상 프로젝트의 구성원이 아니어도 만들 수 있는 공개 프로젝트로 옮기면 라벨은 지워진다`() {
        val from = newProject(); val to = newProject(); val user = newUser(); roleOn(from, user)
        val category = categories.save(IssueLabelCategory(project = from, name = "종류", isExclusive = false)); val label = labels.save(IssueLabel(project = from, category = category, name = "bug", color = "#f00"))
        val i = issue(from, user); i.labels.add(label); issues.save(i)
        transfer(from, 1, tokenOf(user), target(to)).andExpect(status().isOk).andExpect(jsonPath("$.result.labels").doesNotExist())
    }
}
