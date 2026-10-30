package dev.yona.web.api

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.TagService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Git 태그 REST(로드맵 X-37c, `decisions.md` D-52)를 검증한다: 목록, 만들기(경량과 주석), 지우기. 읽기는 코드를 읽을 수 있어야 하고, 만들기는 코드를 고칠 수 있는
 * 사용자(푸시할 수 있는 구성원), 지우기는 웹의 태그 화면처럼 프로젝트를 지울 수 있는 사용자(프로젝트 관리자)이다. 저장소 디렉터리가 실행 사이에 남으므로 이름에 시각을 넣는다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalTagApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var tagService: TagService

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()
    private val stamp = System.nanoTime()

    private fun newUser(scopes: Collection<String>? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "tg$n-$stamp", name = "태그$n", email = "tg$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        tokens[saved.id!!] = "tt$n$stamp"
        if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
        else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, vcs: String = "GIT", withCommits: Boolean = true): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "tgo$n-$stamp", name = "tgp$n", vcs = vcs, projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") {
            repositories.createRepository(project)
            if (withCommits) GitTestSupport.pushCommits(repositories, project, TestCommit("첫 커밋", mapOf("a.txt" to "a\n".toByteArray())), TestCommit("둘째 커밋", mapOf("b.txt" to "b\n".toByteArray())))
        }
        return project
    }

    private fun roleOn(project: Project, user: User, role: RoleType) { projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get())) }

    private fun api(project: Project, path: String = "tags") = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 만들기와 목록 ---

    @Test
    fun `경량 태그와 주석 태그를 만들고 목록에 최근 순서로 나온다`() {
        val project = newProject(); val member = newUser(); roleOn(project, member, RoleType.MEMBER); val token = tokenOf(member)
        json(get(api(project)), token, null).andExpect(status().isOk).andExpect(jsonPath("$.tags.length()").value(0))
        json(post(api(project)), token, """{"name":"v0.1"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.name").value("v0.1")).andExpect(jsonPath("$.result.annotated").value(false)).andExpect(jsonPath("$.result.commit.id").isNotEmpty).andExpect(jsonPath("$.result.message").doesNotExist())
        json(post(api(project)), token, """{"name":"release/1.0","message":"릴리스 1.0\n\n설명","target":"master"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.annotated").value(true)).andExpect(jsonPath("$.result.message").value("릴리스 1.0\n\n설명")).andExpect(jsonPath("$.result.tagger.name").value(member.name))
        json(get(api(project)), token, null).andExpect(jsonPath("$.tags.length()").value(2)).andExpect(jsonPath("$.tags[0].name").value("release/1.0")).andExpect(jsonPath("$.tags[1].name").value("v0.1"))
        assertEquals(setOf("v0.1", "release/1.0"), tagService.tags(project).map { it.shortName }.toSet())
    }

    @Test
    fun `대상은 브랜치와 커밋과 태그로 주고 없으면 기본 브랜치의 끝이다`() {
        val project = newProject(); val member = newUser(); roleOn(project, member, RoleType.MEMBER); val token = tokenOf(member)
        val commits = repositories.openRepository(project).use { r -> org.eclipse.jgit.api.Git(r).log().call().map { it.name }.toList() }
        json(post(api(project)), token, """{"name":"from-commit","target":"${commits.last()}"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.commit.id").value(commits.last()))
        json(post(api(project)), token, """{"name":"from-short","target":"${commits.last().take(8)}"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.commit.id").value(commits.last()))
        json(post(api(project)), token, """{"name":"from-tag","target":"from-commit"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.commit.id").value(commits.last()))
        json(post(api(project)), token, """{"name":"from-head"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.commit.id").value(commits.first()))
        json(post(api(project)), token, """{"name":"from-branch","target":"refs/heads/master"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.commit.id").value(commits.first()))
    }

    @Test
    fun `만들기의 오류와 권한`() {
        val project = newProject(); val member = newUser(); roleOn(project, member, RoleType.MEMBER); val token = tokenOf(member)
        json(post(api(project)), token, """{"name":"dup"}""").andExpect(status().isCreated)
        json(post(api(project)), token, """{"name":"dup"}""").andExpect(status().isConflict)
        json(post(api(project)), token, """{"name":"bad name"}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"name":"a..b"}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"name":""}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"name":"x1","target":"no-such"}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"name":"x2","target":5}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, """{"name":"x3","message":5}""").andExpect(status().isBadRequest)
        json(post(api(project)), token, null).andExpect(status().isBadRequest)
        json(post(api(project)), tokenOf(newUser()), """{"name":"x4"}""").andExpect(status().isForbidden)
        json(post(api(project)), null, """{"name":"x5"}""").andExpect(status().isUnauthorized)
        val empty = newProject(withCommits = false); roleOn(empty, member, RoleType.MEMBER)
        json(post(api(empty)), token, """{"name":"v1"}""").andExpect(status().isBadRequest)
        val svn = newProject(vcs = "Subversion"); roleOn(svn, member, RoleType.MEMBER)
        json(post(api(svn)), token, """{"name":"v1"}""").andExpect(status().isBadRequest)
        json(get(api(svn)), token, null).andExpect(status().isBadRequest)
    }

    // --- 읽기 ---

    @Test
    fun `비공개 프로젝트의 태그는 구성원만 읽고 공개 프로젝트는 누구나 읽는다`() {
        val secret = newProject(ProjectScope.PRIVATE); val member = newUser(); roleOn(secret, member, RoleType.MEMBER); val outsider = newUser(); val publicProject = newProject()
        json(post(api(secret)), tokenOf(member), """{"name":"v1"}""").andExpect(status().isCreated)
        json(get(api(secret)), tokenOf(member), null).andExpect(status().isOk).andExpect(jsonPath("$.tags.length()").value(1))
        json(get(api(secret)), tokenOf(outsider), null).andExpect(status().isForbidden)
        json(get(api(publicProject)), tokenOf(outsider), null).andExpect(status().isOk)
        json(get(api(publicProject)), null, null).andExpect(status().isUnauthorized)
        json(get("/-_-api/v1/owners/${publicProject.owner}/projects/none-$stamp/tags"), tokenOf(outsider), null).andExpect(status().isForbidden)
    }

    // --- 지우기 ---

    @Test
    fun `태그는 프로젝트 관리자만 지우고 슬래시가 든 이름도 지우며 브랜치는 지우지 않는다`() {
        val project = newProject(); val manager = newUser(); val member = newUser(); roleOn(project, manager, RoleType.MANAGER); roleOn(project, member, RoleType.MEMBER)
        json(post(api(project)), tokenOf(member), """{"name":"release/2.0"}""").andExpect(status().isCreated)
        json(post(api(project)), tokenOf(member), """{"name":"v2"}""").andExpect(status().isCreated)
        json(delete(api(project, "tags/v2")), tokenOf(member), null).andExpect(status().isForbidden)
        json(delete(api(project, "tags/v2")), null, null).andExpect(status().isUnauthorized)
        json(delete(api(project, "tags/v2")), tokenOf(manager), null).andExpect(status().isNoContent)
        json(delete(api(project, "tags/release/2.0")), tokenOf(manager), null).andExpect(status().isNoContent)
        json(delete(api(project, "tags/v2")), tokenOf(manager), null).andExpect(status().isNotFound)
        json(delete(api(project, "tags/master")), tokenOf(manager), null).andExpect(status().isNotFound)
        assertTrue(tagService.tags(project).isEmpty())
        assertTrue(repositories.refNames(project).any { it == "refs/heads/master" })
    }

    // --- 범위 ---

    @Test
    fun `공개 프로젝트의 태그는 public_repo로 읽고 만들고 비공개는 repo가 필요하다`() {
        val publicProject = newProject(); val secret = newProject(ProjectScope.PRIVATE)
        val limited = newUser(listOf("public_repo")); roleOn(publicProject, limited, RoleType.MEMBER); roleOn(secret, limited, RoleType.MEMBER)
        val other = newUser(listOf("read:org")); roleOn(publicProject, other, RoleType.MEMBER)
        json(post(api(publicProject)), tokenOf(limited), """{"name":"ok"}""").andExpect(status().isCreated)
        json(get(api(secret)), tokenOf(limited), null).andExpect(status().isForbidden)
        json(get(api(publicProject)), tokenOf(other), null).andExpect(status().isForbidden)
    }
}
