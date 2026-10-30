package dev.yona.web.api

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 검색 REST(로드맵 X-30c, `decisions.md` D-49)를 검증한다. 웹 검색(`SearchController`)과 같은 서비스와 가시성 규칙을 쓰고 결과를 JSON으로 돌려준다.
 * 전체와 조직 범위는 `repo` 범위가 필요하고(비공개 항목도 찾을 수 있어서), 프로젝트 범위는 `repo`나 공개 프로젝트의 `public_repo`이다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalSearchApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()
    private val word = "zq${System.nanoTime()}"

    private fun newUser(token: String? = null, scopes: Collection<String>? = null, name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "se$n", name = name ?: "se$n", email = "se$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        token?.let {
            tokens[saved.id!!] = "st$n$it"
            if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
            else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        }
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, org: Organization? = null, overview: String? = null): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = org?.name ?: "seo$n", name = "sep$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0, organization = org, overview = overview, createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun member(project: Project, scopes: Collection<String>? = null): String {
        val user = newUser("a", scopes); projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return tokenOf(user)
    }

    /** 검색 결과의 작성자는 실제 사용자로 풀리므로(없으면 "존재하지 않는 사용자") 한 명을 만들어 둔다. */
    private val author: User by lazy { newUser(name = "작성자") }

    private fun issue(project: Project, title: String, number: Long = (project.lastIssueNumber ?: 0) + 1, body: String = "본문"): Issue {
        project.lastIssueNumber = number; projects.save(project)
        return issues.save(Issue(project = project, number = number, title = title, body = body, state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, createdDate = Date()))
    }

    private fun posting(project: Project, title: String): Posting {
        val number = (project.lastPostingNumber ?: 0) + 1; project.lastPostingNumber = number; projects.save(project)
        return postings.save(Posting(project = project, number = number, title = title, body = "본문", authorLoginId = "se-author", authorName = "작성자", numOfComments = 0, notice = false, createdDate = Date(), updatedDate = Date()))
    }

    private fun json(path: String, token: String?) =
        mockMvc.perform(get(path).apply { token?.let { header("Authorization", "token $it") } }.accept(MediaType.APPLICATION_JSON))

    // --- 전체 ---

    @Test
    fun `전체 검색은 종류별 결과 수와 항목을 돌려주고 auto는 결과가 있는 첫 종류를 고른다`() {
        val project = newProject(overview = "프로젝트 $word 설명"); val token = member(project)
        issue(project, "이슈 $word 하나"); posting(project, "글 $word"); milestones.save(Milestone(project = project, title = "마일스톤 $word", contents = "", state = State.OPEN))
        newUser(name = "사용자 $word")
        json("/-_-api/v1/search?keyword=$word&searchType=issue", token).andExpect(status().isOk)
            .andExpect(jsonPath("$.searchType").value("issue")).andExpect(jsonPath("$.counts.issues").value(1)).andExpect(jsonPath("$.counts.posts").value(1)).andExpect(jsonPath("$.counts.milestones").value(1))
            .andExpect(jsonPath("$.counts.users").value(1)).andExpect(jsonPath("$.counts.projects").value(1)).andExpect(jsonPath("$.counts.issueComments").value(0))
            .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("이슈 $word 하나")).andExpect(jsonPath("$.items[0].number").value(1)).andExpect(jsonPath("$.items[0].url").value("/${project.owner}/${project.name}/issue/1"))
            .andExpect(jsonPath("$.items[0].project").value("${project.owner}/${project.name}")).andExpect(jsonPath("$.items[0].author.name").value("작성자"))
        json("/-_-api/v1/search?keyword=$word", token).andExpect(jsonPath("$.searchType").value("issue"))
        json("/-_-api/v1/search?keyword=$word&searchType=post", token).andExpect(jsonPath("$.items[0].title").value("글 $word"))
        json("/-_-api/v1/search?keyword=$word&searchType=milestone", token).andExpect(jsonPath("$.items[0].title").value("마일스톤 $word"))
        json("/-_-api/v1/search?keyword=$word&searchType=user", token).andExpect(jsonPath("$.items[0].name").value("사용자 $word")).andExpect(jsonPath("$.items[0].loginId").exists())
        json("/-_-api/v1/search?keyword=$word&searchType=project", token).andExpect(jsonPath("$.items[0].name").value(project.name)).andExpect(jsonPath("$.items[0].owner").value(project.owner))
        json("/-_-api/v1/search?keyword=nothing-$word", token).andExpect(status().isOk).andExpect(jsonPath("$.searchType").value("issue")).andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `비공개 프로젝트의 항목은 구성원에게만 보인다`() {
        val secret = newProject(ProjectScope.PRIVATE); val token = member(secret); val outsider = tokenOf(newUser("x"))
        issue(secret, "비밀 $word")
        json("/-_-api/v1/search?keyword=$word&searchType=issue", token).andExpect(jsonPath("$.counts.issues").value(1)).andExpect(jsonPath("$.items.length()").value(1))
        json("/-_-api/v1/search?keyword=$word&searchType=issue", outsider).andExpect(jsonPath("$.counts.issues").value(0)).andExpect(jsonPath("$.items.length()").value(0))
    }

    @Test
    fun `쪽을 나누어 돌려주고 틀린 요청은 거절한다`() {
        val project = newProject(); val token = member(project)
        (1..21).forEach { issue(project, "쪽 $word $it") }
        json("/-_-api/v1/search?keyword=$word&searchType=issue", token).andExpect(jsonPath("$.items.length()").value(20)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.page").value(1))
        json("/-_-api/v1/search?keyword=$word&searchType=issue&pageNum=2", token).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.page").value(2))
        json("/-_-api/v1/search?searchType=issue", token).andExpect(status().isBadRequest)
        json("/-_-api/v1/search?keyword=&searchType=issue", token).andExpect(status().isBadRequest)
        json("/-_-api/v1/search?keyword=$word&searchType=fax", token).andExpect(status().isBadRequest)
        json("/-_-api/v1/search?keyword=$word&searchType=not%20available", token).andExpect(status().isBadRequest)
        json("/-_-api/v1/search?keyword=$word&pageNum=0", token).andExpect(status().isBadRequest)
        json("/-_-api/v1/search?keyword=$word&pageNum=x", token).andExpect(status().isBadRequest)
        json("/-_-api/v1/search?keyword=$word", null).andExpect(status().isUnauthorized)
    }

    // --- 조직 ---

    @Test
    fun `조직 검색은 그 조직의 항목만 찾고 없는 조직은 404이다`() {
        val org = organizations.save(Organization(name = "seorg-${System.nanoTime()}", descr = "", created = Date())); val other = organizations.save(Organization(name = "seother-${System.nanoTime()}", descr = "", created = Date()))
        val inside = newProject(org = org); val outside = newProject(org = other); val token = member(inside)
        issue(inside, "안 $word"); issue(outside, "밖 $word")
        json("/-_-api/v1/orgs/${org.name}/search?keyword=$word&searchType=issue", token).andExpect(status().isOk).andExpect(jsonPath("$.counts.issues").value(1)).andExpect(jsonPath("$.items[0].title").value("안 $word"))
        json("/-_-api/v1/orgs/${other.name}/search?keyword=$word&searchType=issue", token).andExpect(jsonPath("$.items[0].title").value("밖 $word"))
        json("/-_-api/v1/orgs/zzz-none/search?keyword=$word", token).andExpect(status().isNotFound)
        json("/-_-api/v1/orgs/${org.name}/search?keyword=$word&searchType=project", token).andExpect(status().isOk)
        organizationUsers.save(OrganizationUser(user = newUser(), organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get()))
    }

    // --- 프로젝트 ---

    @Test
    fun `프로젝트 검색은 그 프로젝트의 항목만 찾고 프로젝트 종류는 받지 않으며 읽을 수 없으면 거절한다`() {
        val project = newProject(); val other = newProject(); val token = member(project); val secret = newProject(ProjectScope.PRIVATE)
        issue(project, "여기 $word"); issue(other, "저기 $word"); issue(secret, "비밀 $word")
        val base = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/search"
        json("$base?keyword=$word&searchType=issue", token).andExpect(status().isOk).andExpect(jsonPath("$.counts.issues").value(1)).andExpect(jsonPath("$.counts.projects").value(0)).andExpect(jsonPath("$.items[0].title").value("여기 $word"))
        json("$base?keyword=$word&searchType=project", token).andExpect(status().isBadRequest)
        json("$base?keyword=$word", token).andExpect(jsonPath("$.searchType").value("issue"))
        json("/-_-api/v1/owners/${secret.owner}/projects/${secret.name}/search?keyword=$word&searchType=issue", token).andExpect(status().isForbidden)
        json("/-_-api/v1/owners/${secret.owner}/projects/none/search?keyword=$word", token).andExpect(status().isNotFound)
        json("$base?keyword=$word", null).andExpect(status().isUnauthorized)
    }

    // --- 범위 ---

    @Test
    fun `전체와 조직 검색은 repo 범위가 필요하고 프로젝트 검색은 공개 프로젝트에 public_repo도 허용한다`() {
        val project = newProject(); val secret = newProject(ProjectScope.PRIVATE); val org = organizations.save(Organization(name = "sescope-${System.nanoTime()}", descr = "", created = Date()))
        val publicRepo = member(project, listOf("public_repo")); val repo = member(project, listOf("repo")); val readOrg = member(project, listOf("read:org"))
        val secretPublicRepo = member(secret, listOf("public_repo"))
        json("/-_-api/v1/search?keyword=$word", publicRepo).andExpect(status().isForbidden)
        json("/-_-api/v1/search?keyword=$word", readOrg).andExpect(status().isForbidden)
        json("/-_-api/v1/search?keyword=$word", repo).andExpect(status().isOk)
        json("/-_-api/v1/orgs/${org.name}/search?keyword=$word", publicRepo).andExpect(status().isForbidden)
        json("/-_-api/v1/orgs/${org.name}/search?keyword=$word", repo).andExpect(status().isOk)
        json("/-_-api/v1/owners/${project.owner}/projects/${project.name}/search?keyword=$word", publicRepo).andExpect(status().isOk)
        json("/-_-api/v1/owners/${secret.owner}/projects/${secret.name}/search?keyword=$word", secretPublicRepo).andExpect(status().isForbidden)
    }
}
