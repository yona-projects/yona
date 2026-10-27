package dev.yona.web.svn

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
import dev.yona.repository.RepositoryService
import dev.yona.repository.svn.SvnRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.tmatesoft.svn.core.SVNErrorCode
import org.tmatesoft.svn.core.SVNException
import org.tmatesoft.svn.core.SVNNodeKind
import org.tmatesoft.svn.core.SVNURL
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory
import org.tmatesoft.svn.core.io.SVNRepository
import org.tmatesoft.svn.core.io.SVNRepositoryFactory
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 실제 Subversion 클라이언트(SVNKit)로 WebDAV 서비스(`/svn/소유자/프로젝트`)를 검증한다.
 * 원본: `app/controllers/SvnApp.java`, `RepositoryService.createDavServlet`, `utils.PlayServletRequest`, `PlayServletResponse`.
 * 서버가 따로 뜨므로 데이터는 커밋하고, 테스트가 끝나면 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-svn-dav"])
class SvnDavTest {

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
    @Autowired private lateinit var svn: SvnRepositories
    @Autowired private lateinit var deletion: ProjectDeletionService

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var publicProject: Project
    private lateinit var privateProject: Project
    private val createdUsers = mutableListOf<User>()
    private val createdProjects = mutableListOf<Project>()

    init {
        DAVRepositoryFactory.setup()
    }

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))).also { createdUsers += it }
    }

    private fun project(owner: User, scope: ProjectScope, vcs: String = "Subversion"): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = owner.loginId, name = "dav$n", vcs = vcs, projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = owner, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p.also { createdProjects += it }
    }

    @BeforeEach
    fun setUp() {
        member = user("davm")
        outsider = user("davo")
        publicProject = project(member, ProjectScope.PUBLIC)
        privateProject = project(member, ProjectScope.PRIVATE)
    }

    @AfterEach
    fun tearDown() {
        createdProjects.forEach { runCatching { deletion.delete(it) } }
        createdProjects.clear()
        users.deleteAll(createdUsers)
        createdUsers.clear()
    }

    private fun url(p: Project) = "http://localhost:$port/svn/${p.owner}/${p.name}"

    private fun client(p: Project, user: User? = null, password: String = "1234"): SVNRepository =
        SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url(p))).also {
            if (user != null) it.authenticationManager = BasicAuthenticationManager.newInstance(user.loginId, password.toCharArray())
        }

    /** HTTP로 파일 하나를 올리는 커밋을 만든다. */
    private fun commitOverHttp(repository: SVNRepository, path: String, content: String, message: String = "http commit"): Long {
        val editor = repository.getCommitEditor(message, null)
        editor.openRoot(-1)
        editor.addFile(path, null, -1)
        editor.applyTextDelta(path, null)
        val checksum = SVNDeltaGenerator().sendDelta(path, ByteArrayInputStream(content.toByteArray()), editor, true)
        editor.closeFile(path, checksum)
        editor.closeDir()
        return editor.closeEdit().newRevision
    }

    private fun raw(method: String, path: String, auth: User? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).method(method, HttpRequest.BodyPublishers.noBody())
        if (auth != null) builder.header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("${auth.loginId}:1234".toByteArray()))
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    // --- 읽기 ---

    @Test
    fun `공개 프로젝트는 익명으로 읽을 수 있다`() {
        val repository = client(publicProject)
        assertEquals(0L, repository.latestRevision)
        assertEquals(SVNNodeKind.DIR, repository.checkPath("", -1))
    }

    @Test
    fun `OPTIONS는 DAV 헤더로 응답한다`() {
        val response = raw("OPTIONS", "/svn/${publicProject.owner}/${publicProject.name}")
        assertEquals(200, response.statusCode())
        assertTrue(response.headers().firstValue("DAV").isPresent, response.headers().toString())
    }

    @Test
    fun `응답의 href는 맨 앞에 슬래시가 있는 절대 경로이다`() {
        // svn 명령줄 클라이언트는 맨 앞 슬래시가 빠진 href를 요청 주소로 쓰지 못한다.
        val request = HttpRequest.newBuilder(URI.create(url(publicProject)))
            .method("OPTIONS", HttpRequest.BodyPublishers.ofString("<?xml version=\"1.0\" encoding=\"utf-8\"?><D:options xmlns:D=\"DAV:\"><D:activity-collection-set/></D:options>"))
            .header("Content-Type", "text/xml").build()
        val body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
        assertTrue(body.contains("<D:href>/svn/${publicProject.owner}/${publicProject.name}/!svn/act/</D:href>"), body)
    }

    // --- 쓰기 ---

    @Test
    fun `구성원은 HTTP로 커밋하고 파일을 읽을 수 있다`() {
        val repository = client(publicProject, member)
        assertEquals(1L, commitOverHttp(repository, "hello.txt", "안녕 DAV\n"))
        assertEquals(1L, svn.latestRevision(publicProject.owner!!, publicProject.name!!))
        val out = ByteArrayOutputStream()
        client(publicProject).getFile("hello.txt", -1, null, out)
        assertEquals("안녕 DAV\n", out.toString(Charsets.UTF_8))
        val log = client(publicProject).log(arrayOf("/"), null, 1, 1, false, false).single() as org.tmatesoft.svn.core.SVNLogEntry
        assertEquals(member.loginId, log.author)
        assertEquals("http commit", log.message)
    }

    @Test
    fun `공백과 한글이 든 경로도 조회하고 읽을 수 있다 (v1_6 svnkit-dav 1_8은 pathInfo를 디코딩한다)`() {
        val repository = client(publicProject, member)
        assertEquals(1L, commitOverHttp(repository, "한글 a.txt", "내용\n"))
        val reader = client(publicProject)
        assertEquals(SVNNodeKind.FILE, reader.checkPath("한글 a.txt", -1))
        val out = ByteArrayOutputStream()
        reader.getFile("한글 a.txt", -1, null, out)
        assertEquals("내용\n", out.toString(Charsets.UTF_8))
        val names = mutableListOf<org.tmatesoft.svn.core.SVNDirEntry>()
        reader.getDir("", -1, null, 0, names)
        assertEquals(listOf("한글 a.txt"), names.map { it.name })
    }

    @Test
    fun `자식 항목의 PROPFIND 응답 href도 퍼센트 인코딩되어 있다`() {
        commitOverHttp(client(publicProject, member), "한글 b.txt", "x\n")
        val request = HttpRequest.newBuilder(URI.create(url(publicProject) + "/!svn/bc/1/"))
            .method("PROPFIND", HttpRequest.BodyPublishers.noBody()).header("Depth", "1").build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(207, response.statusCode(), response.body())
        assertTrue(response.body().contains("<D:href>/svn/${publicProject.owner}/${publicProject.name}/!svn/bc/1/%ED%95%9C%EA%B8%80%20b.txt</D:href>"), response.body())
    }

    @Test
    fun `한글 프로젝트 이름의 저장소에도 HTTP로 커밋하고 읽을 수 있다`() {
        val p = project(member, ProjectScope.PUBLIC)
        val old = p.name!!
        val renamed = projects.save(p.also { it.name = "한글 $old" })
        repositories.createRepository(renamed)
        val repository = client(renamed, member)
        assertEquals(1L, commitOverHttp(repository, "hello.txt", "안녕\n"))
        val out = ByteArrayOutputStream()
        client(renamed).getFile("hello.txt", -1, null, out)
        assertEquals("안녕\n", out.toString(Charsets.UTF_8))
    }

    @Test
    fun `한글 프로젝트 이름의 PROPFIND 응답 href는 퍼센트 인코딩되어 있다`() {
        val p = project(member, ProjectScope.PUBLIC)
        val old = p.name!!
        val renamed = projects.save(p.also { it.name = "한글 $old" })
        repositories.createRepository(renamed)
        val request = HttpRequest.newBuilder(URI.create(url(renamed).replace(renamed.name!!, java.net.URLEncoder.encode(renamed.name, Charsets.UTF_8).replace("+", "%20"))))
            .method("PROPFIND", HttpRequest.BodyPublishers.noBody()).header("Depth", "0").build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(207, response.statusCode(), response.body())
        // 프로젝트 이름 구간의 인코딩은 v1.6 svnkit-dav 1.8.15가 정한다. 여기서는 오류 없이 207로 응답하고 한글이 그대로 나가지 않음만 확인한다
        assertTrue(!response.body().contains("한글"), response.body())
    }

    @Test
    fun `익명이나 구성원이 아닌 사용자는 쓸 수 없다`() {
        val anonymous = assertFailsWith<SVNException> { commitOverHttp(client(publicProject), "a.txt", "x") }
        assertEquals(SVNErrorCode.RA_NOT_AUTHORIZED, anonymous.errorMessage.errorCode)
        val stranger = assertFailsWith<SVNException> { commitOverHttp(client(publicProject, outsider), "a.txt", "x") }
        assertTrue(stranger.errorMessage.errorCode in setOf(SVNErrorCode.RA_DAV_FORBIDDEN, SVNErrorCode.RA_NOT_AUTHORIZED, SVNErrorCode.RA_DAV_REQUEST_FAILED), stranger.errorMessage.toString())
        assertEquals(0L, svn.latestRevision(publicProject.owner!!, publicProject.name!!))
    }

    @Test
    fun `틀린 비밀번호는 인증 실패이다`() {
        val failure = assertFailsWith<SVNException> { client(publicProject, member, "wrong").also { commitOverHttp(it, "a.txt", "x") } }
        assertEquals(SVNErrorCode.RA_NOT_AUTHORIZED, failure.errorMessage.errorCode)
    }

    // --- 비공개 ---

    @Test
    fun `비공개 프로젝트는 익명에게 인증을 요구하고 구성원은 읽는다`() {
        val response = raw("PROPFIND", "/svn/${privateProject.owner}/${privateProject.name}")
        assertEquals(401, response.statusCode())
        assertEquals("Basic realm=\"Yobi\"", response.headers().firstValue("WWW-Authenticate").get())
        assertEquals("Invalid username or password", response.body())
        assertFailsWith<SVNException> { client(privateProject).latestRevision }
        assertEquals(0L, client(privateProject, member).latestRevision)
    }

    @Test
    fun `인증은 했지만 권한이 없으면 403이다`() {
        val response = raw("PROPFIND", "/svn/${privateProject.owner}/${privateProject.name}", outsider)
        assertEquals(403, response.statusCode())
        assertEquals("You have no permission to access this repository.", response.body())
    }

    // --- 주소 ---

    @Test
    fun `프로젝트가 없거나 Git 프로젝트이면 404이고 주소가 짧으면 403이다`() {
        val git = project(member, ProjectScope.PUBLIC, "GIT")
        assertEquals(404, raw("GET", "/svn/${member.loginId}/none").statusCode())
        assertEquals(404, raw("GET", "/svn/${git.owner}/${git.name}").statusCode())
        assertEquals(403, raw("GET", "/svn/${member.loginId}").statusCode())
        // v1.6 SvnApp은 기본 액션 안에서 응답하므로 Cache-Control: no-cache가 붙고, 주소가 짧아 라우트가 없는 403에는 붙지 않는다
        assertEquals("no-cache", raw("GET", "/svn/${member.loginId}/none").headers().firstValue("Cache-Control").orElse(null))
        assertEquals(null, raw("GET", "/svn/${member.loginId}").headers().firstValue("Cache-Control").orElse(null))
    }

    @Test
    fun `올바른 Basic 자격이면 404와 403 응답에도 세션 쿠키가 심기고 자격이 없으면 심기지 않는다`() {
        // v1.6은 BasicAuthAction이 가장 바깥이라 이후의 403, 404 응답에도 세션이 실린다
        fun cookie(response: HttpResponse<String>) = response.headers().allValues("Set-Cookie").firstOrNull { it.startsWith("JSESSIONID=") }
        val git = project(member, ProjectScope.PUBLIC, "GIT")
        assertEquals(404, raw("GET", "/svn/${member.loginId}/none", member).statusCode())
        assertTrue(cookie(raw("GET", "/svn/${member.loginId}/none", member)) != null)
        assertTrue(cookie(raw("GET", "/svn/${git.owner}/${git.name}", member)) != null)
        assertTrue(cookie(raw("GET", "/svn/${member.loginId}", member)) != null)
        assertTrue(cookie(raw("GET", "/svn/${member.loginId}/none")) == null)
    }
}
