package dev.yona.web.git

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.wiki.WikiService
import dev.yona.repository.RepositoryService
import dev.yona.repository.WikiRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.TestDatabase
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 위키 저장소의 git clone과 push를 검증한다(로드맵 X-17 W7, `decisions.md` D-60). 주소는 Forgejo 방식으로 `/<소유자>/<이름>.wiki.git`이고 코드 저장소처럼 `.git` 없는
 * `/<소유자>/<이름>.wiki`도 받는다. 복제는 프로젝트 읽기 권한, 푸시는 구성원이다(`WIKI_PAGE`의 `UPDATE`). 프로젝트의 VCS가 Subversion이어도 위키는 git이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0", "yona.git-default-branch=master"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-wiki-git-http"])
class WikiGitHttpTest {

    companion object {
        private val counter = AtomicInteger()

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            TestDatabase.current.register(registry)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.datasource.hikari.maximum-pool-size") { "3" }
            registry.add("spring.datasource.hikari.minimum-idle") { "0" }
        }
    }

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var wikis: WikiRepositories
    @Autowired private lateinit var wiki: WikiService
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var apiTokenService: dev.yona.domain.apitoken.ApiTokenService
    @Autowired private lateinit var tokenFailures: dev.yona.security.TokenFailureLimiter

    @TempDir lateinit var work: File

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var project: Project
    private val created = mutableListOf<User>()

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))).also { created += it }
    }

    private fun project(scope: ProjectScope, vcs: String = "GIT"): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "wgit$n", vcs = vcs, projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true, wiki = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        if (vcs == "GIT") repositories.createRepository(p)
        return p
    }

    @BeforeEach
    fun setUp() {
        member = user("wm"); outsider = user("wo")
        project = project(ProjectScope.PUBLIC)
    }

    @AfterEach
    fun tearDown() {
        apiTokenRepository.deleteAll(); tokenCache.clear()
        tokenFailures.clear()
        projects.findAll().filter { it.owner == member.loginId }.forEach { wikis.delete(it.owner!!, it.name!!); deletion.delete(it) }
        users.deleteAll(created)
    }

    private fun wikiUrl(p: Project = project, suffix: String = ".wiki.git") = "http://localhost:$port/${p.owner}/${p.name}$suffix"

    /** 사용자마다 한 번 만든 개인용 API 토큰이다. git HTTP는 토큰만 받는다(D-70). */
    private val tokenCache = mutableMapOf<Long, String>()

    private fun tokenOf(u: User): String = tokenCache.getOrPut(requireNotNull(u.id)) { (apiTokenService.create(u, "git", setOf("repo"), null) as dev.yona.domain.apitoken.TokenCreation.Created).value }

    private fun credentials(u: User, password: String? = null) = UsernamePasswordCredentialsProvider(u.loginId, password ?: tokenOf(u))

    /** 작업 디렉터리에 마크다운 파일 하나를 커밋해 위키 저장소로 밀어 넣는다. */
    private fun push(p: Project, user: User?, file: String = "Home.md", text: String = "hello", suffix: String = ".wiki.git", ref: String = "refs/heads/master", password: String? = null) {
        val dir = File(work, "push-${counter.incrementAndGet()}")
        Git.init().setDirectory(dir).setInitialBranch("master").call().use { git ->
            File(dir, file).apply { parentFile.mkdirs() }.writeText(text)
            git.add().addFilepattern(file).call()
            git.commit().setMessage("first").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            val command = git.push().setRemote(wikiUrl(p, suffix)).setRefSpecs(RefSpec("refs/heads/master:$ref"))
            user?.let { command.setCredentialsProvider(credentials(it, password)) }
            command.call().forEach { result ->
                result.remoteUpdates.forEach { update ->
                    check(update.status.name == "OK" || update.status.name == "UP_TO_DATE") { "푸시 실패: ${update.status} ${update.message}" }
                }
            }
        }
    }

    private fun clone(p: Project, user: User? = null, suffix: String = ".wiki.git"): File {
        val dir = File(work, "clone-${counter.incrementAndGet()}")
        val command = Git.cloneRepository().setURI(wikiUrl(p, suffix)).setDirectory(dir)
        user?.let { command.setCredentialsProvider(credentials(it)) }
        command.call().close()
        return dir
    }

    // --- 푸시 ---

    @Test
    fun `구성원이 첫 푸시를 하면 위키 저장소가 만들어지고 위키 서비스가 그 페이지를 읽는다`() {
        assertFalse(wikis.exists(project.owner!!, project.name!!))
        push(project, member, text = "# 푸시한 홈")
        assertTrue(wikis.exists(project.owner!!, project.name!!))
        assertEquals("# 푸시한 홈", wiki.page(project, "Home")?.content)
    }

    @Test
    fun `점 git 없이 점 wiki 로 끝나는 주소도 받는다`() {
        push(project, member, suffix = ".wiki")
        assertEquals("hello", File(clone(project, suffix = ".wiki"), "Home.md").readText())
    }

    @Test
    fun `구성원이 아니거나 익명이거나 비밀번호가 틀리면 푸시할 수 없다`() {
        assertFailsWith<TransportException> { push(project, null) }
        assertFailsWith<TransportException> { push(project, member, password = "wrong") }
        val error = assertFailsWith<TransportException> { push(project, outsider) }
        assertTrue(error.message!!.contains("not permitted") || error.message!!.contains("403"), error.message)
        assertFalse(wikis.exists(project.owner!!, project.name!!))
    }

    @Test
    fun `위키 푸시는 코드 저장소를 건드리지 않는다`() {
        push(project, member)
        repositories.openRepository(project).use { assertNull(it.resolve("refs/heads/master")) }
    }

    @Test
    fun `기본 브랜치는 지울 수 없다`() {
        push(project, member)
        val dir = clone(project, member)
        val statuses = Git.open(dir).use { git ->
            git.push().setCredentialsProvider(credentials(member)).setRefSpecs(RefSpec(":refs/heads/master")).call().flatMap { it.remoteUpdates }.map { it.status.name }
        }
        assertTrue(statuses.none { it == "OK" }, statuses.toString())
        assertEquals("hello", wiki.page(project, "Home")?.content)
    }

    // --- 복제 ---

    @Test
    fun `서비스로 만든 페이지를 공개 프로젝트는 익명으로 복제한다`() {
        wiki.create(project, member, "Home", "# 홈", null)
        wiki.create(project, member, "Install Guide", "설치", null)
        val dir = clone(project)
        assertEquals("# 홈", File(dir, "Home.md").readText())
        assertEquals("설치", File(dir, "Install-Guide.md").readText())
    }

    @Test
    fun `복제한 위키에서 고쳐 푸시하면 위키 페이지에 반영된다`() {
        wiki.create(project, member, "Home", "v1", null)
        val dir = clone(project, member)
        File(dir, "Home.md").writeText("v2")
        Git.open(dir).use { git ->
            git.add().addFilepattern("Home.md").call()
            git.commit().setMessage("edit").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            git.push().setCredentialsProvider(credentials(member)).call()
        }
        assertEquals("v2", wiki.page(project, "Home")?.content)
        assertEquals(listOf("edit", "Create Home"), wiki.history(project, "Home", 0, 10).map { it.shortMessage })
    }

    @Test
    fun `위키가 아직 없으면 빈 저장소로 복제된다`() {
        val dir = clone(project)
        assertTrue(dir.isDirectory)
        assertEquals(emptyList(), dir.listFiles { f -> f.name.endsWith(".md") }?.toList().orEmpty())
        assertFalse(wikis.exists(project.owner!!, project.name!!))
    }

    @Test
    fun `비공개 프로젝트의 위키는 익명으로 복제할 수 없고 구성원은 복제한다`() {
        val secret = project(ProjectScope.PRIVATE)
        wiki.create(secret, member, "Home", "비밀", null)
        assertFailsWith<TransportException> { clone(secret) }
        assertFailsWith<TransportException> { clone(secret, outsider) }
        assertEquals("비밀", File(clone(secret, member), "Home.md").readText())
    }

    @Test
    fun `Subversion 프로젝트도 위키는 git으로 복제하고 푸시한다`() {
        val svn = project(ProjectScope.PUBLIC, vcs = "Subversion")
        push(svn, member, text = "svn 위키")
        assertEquals("svn 위키", File(clone(svn), "Home.md").readText())
    }

    @Test
    fun `없는 프로젝트의 위키는 찾을 수 없다`() {
        // JGit은 404를 "Invalid remote"(InvalidRemoteException)로 알린다
        assertFailsWith<org.eclipse.jgit.api.errors.InvalidRemoteException> {
            Git.cloneRepository().setURI("http://localhost:$port/${member.loginId}/nothing.wiki.git").setDirectory(File(work, "none")).call().close()
        }
    }

    @Test
    fun `위키를 끈 프로젝트는 복제와 푸시가 404이고 켜면 내용이 돌아온다 (D-61)`() {
        wiki.create(project, member, "Home", "보존", null)
        fun setWiki(on: Boolean) { menuSettings.save(menuSettings.findFirstByProjectId(project.id!!)!!.also { it.wiki = on }) }
        setWiki(false)
        assertFailsWith<org.eclipse.jgit.api.errors.InvalidRemoteException> { clone(project) }
        assertFailsWith<org.eclipse.jgit.api.errors.InvalidRemoteException> { clone(project, member) }
        assertFailsWith<Exception> { push(project, member, file = "x.md") }
        setWiki(true)
        assertEquals("보존", File(clone(project), "Home.md").readText())
    }

    @Test
    fun `push로 올라온 하위 폴더와 다른 파일은 위키 목록에서 숨긴다`() {
        push(project, member, file = "docs/Guide.md", text = "g")
        assertEquals(emptyList(), wiki.pages(project).map { it.name })
    }
}
