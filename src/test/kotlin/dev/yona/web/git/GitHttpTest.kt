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
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * 실제 Git 클라이언트(JGit)로 smart-HTTP 서비스(`info/refs`, `git-upload-pack`, `git-receive-pack`)를 검증한다.
 *
 * 원본: `app/controllers/GitApp.java`, `playRepository/RepositoryService.gitAdvertise`, `gitRpc`, `playRepository/hooks 패키지`.
 * 서버가 따로 뜨므로 데이터는 커밋하고, 테스트가 끝나면 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-git-http"])
class GitHttpTest {

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
    @Autowired private lateinit var pushedBranches: PushedBranchRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var recentVisits: dev.yona.domain.project.RecentVisitService
    @Autowired private lateinit var recentProjects: dev.yona.domain.project.RecentProjectRepository

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

    private fun project(scope: ProjectScope, memberOnly: Boolean = false): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "git$n", vcs = "GIT", projectScope = scope, isCodeAccessibleMemberOnly = memberOnly))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p
    }

    @BeforeEach
    fun setUp() {
        member = user("gm"); outsider = user("go")
        project = project(ProjectScope.PUBLIC)
    }

    @AfterEach
    fun tearDown() {
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
        users.deleteAll(created)
    }

    private fun get(url: String): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    private fun url(p: Project = project) = "http://localhost:$port/${p.owner}/${p.name}"

    private fun credentials(u: User, password: String = "1234") = UsernamePasswordCredentialsProvider(u.loginId, password)

    /** 작업 디렉터리를 새로 만들어 파일 하나를 커밋하고 [target] 브랜치로 밀어 넣는다. */
    private fun push(p: Project, user: User?, branch: String = "master", password: String = "1234", file: String = "a.txt", ref: String = "refs/heads/$branch") {
        val dir = File(work, "push-${counter.incrementAndGet()}")
        Git.init().setDirectory(dir).setInitialBranch(branch).call().use { git ->
            File(dir, file).writeText("hello")
            git.add().addFilepattern(file).call()
            git.commit().setMessage("first").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            val command = git.push().setRemote(url(p)).setRefSpecs(RefSpec("refs/heads/$branch:$ref"))
            user?.let { command.setCredentialsProvider(credentials(it, password)) }
            command.call().forEach { result ->
                result.remoteUpdates.forEach { update ->
                    check(update.status.name == "OK" || update.status.name == "UP_TO_DATE") { "푸시 실패: ${update.status} ${update.message}" }
                }
            }
        }
    }

    private fun clone(p: Project, user: User? = null): File {
        val dir = File(work, "clone-${counter.incrementAndGet()}")
        val command = Git.cloneRepository().setURI(url(p)).setDirectory(dir)
        user?.let { command.setCredentialsProvider(credentials(it)) }
        command.call().close()
        return dir
    }

    @Test
    fun `푸시는 마지막 방문 프로젝트를 갱신해 다시 열면 최근 방문 맨 앞이 된다 (v1_6 user_visits)`() {
        val other = project(ProjectScope.PUBLIC)
        recentVisits.visits(member, other)
        push(project, member)
        // git 요청이 visits로 마지막 방문 번호를 바꾸므로, 웹으로 other를 다시 열면 직전과 다르다고 보고 맨 앞에 다시 기록한다
        recentVisits.visits(member, other)
        assertEquals(other.id, recentProjects.findByUserIdOrderByIdDesc(member.id!!).first().project?.id)
    }

    @Test
    fun `Basic 인증은 로그인 ID로만 찾고 이메일 사용자 이름은 받지 않는다 (v1_6 authenticateWithPlainPassword)`() {
        val secret = project(ProjectScope.PRIVATE)
        fun auth(name: String) = "Basic " + java.util.Base64.getEncoder().encodeToString("$name:1234".toByteArray())
        val byLogin = raw("/${secret.owner}/${secret.name}/info/refs?service=git-upload-pack", headers = arrayOf("Authorization" to auth(member.loginId!!)))
        assertEquals(200, byLogin.statusCode())
        val byEmail = raw("/${secret.owner}/${secret.name}/info/refs?service=git-upload-pack", headers = arrayOf("Authorization" to auth(member.email!!)))
        assertEquals(401, byEmail.statusCode())
    }

    @Test
    fun `구성원이 푸시하고 공개 프로젝트는 익명으로 복제한다`() {
        push(project, member)
        val dir = clone(project)
        assertEquals("hello", File(dir, "a.txt").readText())
    }

    @Test
    fun `푸시하면 마지막 푸시 시각과 최근 푸시한 브랜치가 남는다`() {
        push(project, member, branch = "feature")
        val saved = projects.findById(project.id!!).get()
        assertNotNull(saved.lastPushedDate)
        assertEquals(listOf("refs/heads/feature"), pushedBranches.findAll().filter { it.project?.id == project.id }.map { it.name })
    }

    @Test
    fun `익명이나 잘못된 비밀번호로는 푸시할 수 없다`() {
        assertFailsWith<TransportException> { push(project, null) }
        assertFailsWith<TransportException> { push(project, member, password = "wrong") }
    }

    @Test
    fun `프로젝트 구성원이 아닌 사용자는 푸시할 수 없다`() {
        val error = assertFailsWith<TransportException> { push(project, outsider) }
        assertTrue(error.message!!.contains("not permitted") || error.message!!.contains("403"), error.message)
    }

    @Test
    fun `예약된 ref에는 푸시할 수 없다`() {
        val error = assertFailsWith<IllegalStateException> { push(project, member, ref = "refs/yobi/test") }
        assertTrue(error.message!!.contains("reserved"), error.message)
    }

    @Test
    fun `비공개 프로젝트는 익명으로 복제할 수 없고 구성원은 복제한다`() {
        val secret = project(ProjectScope.PRIVATE)
        push(secret, member)
        assertFailsWith<TransportException> { clone(secret) }
        assertFailsWith<TransportException> { clone(secret, outsider) }
        assertEquals("hello", File(clone(secret, member), "a.txt").readText())
    }

    @Test
    fun `코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 복제할 수 없다`() {
        val closed = project(ProjectScope.PUBLIC, memberOnly = true)
        push(closed, member)
        assertFailsWith<TransportException> { clone(closed) }
        assertFailsWith<TransportException> { clone(closed, outsider) }
        assertEquals("hello", File(clone(closed, member), "a.txt").readText())
    }

    @Test
    fun `지원하지 않는 서비스와 서비스를 정하지 않은 요청은 거부한다`() {
        assertEquals(403, get("${url()}/info/refs?service=git-foo").statusCode())
        val none = get("${url()}/info/refs")
        assertEquals(403, none.statusCode())
        assertTrue(String(none.body()).contains("getanyfile"))
    }

    @Test
    fun `없는 프로젝트는 찾을 수 없다`() {
        assertEquals(404, get("http://localhost:$port/${member.loginId}/nothing/info/refs?service=git-upload-pack").statusCode())
    }

    @Test
    fun `광고 응답은 서비스별 콘텐츠 유형을 쓴다`() {
        val response = get("${url()}/info/refs?service=git-upload-pack")
        assertEquals(200, response.statusCode())
        assertEquals("application/x-git-upload-pack-advertisement", response.headers().firstValue("Content-Type").get())
        assertTrue(String(response.body()).startsWith("001e# service=git-upload-pack\n"))
    }

    private fun basic(u: User, password: String = "1234") = "Basic " + java.util.Base64.getEncoder().encodeToString("${u.loginId}:$password".toByteArray())

    private fun advertise(p: Project, authorization: String? = null, cookie: String? = null): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("${url(p)}/info/refs?service=git-upload-pack")).GET()
                .apply { authorization?.let { header("Authorization", it) }; cookie?.let { header("Cookie", it) } }.build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )

    @Test
    fun `Basic 인증에 성공하면 세션 쿠키를 심고 그 쿠키로 다시 인증된다 (v1_6 BasicAuthAction)`() {
        val secret = project(ProjectScope.PRIVATE)
        val first = advertise(secret, basic(member))
        assertEquals(200, first.statusCode())
        val cookie = first.headers().allValues("Set-Cookie").firstOrNull { it.startsWith("JSESSIONID=") }?.substringBefore(';')
        assertTrue(cookie != null, first.headers().toString())
        // 쿠키만 보내도 세션 사용자로 읽을 수 있고, 쿠키가 없으면 401이다
        assertEquals(200, advertise(secret, cookie = cookie).statusCode())
        assertEquals(401, advertise(secret).statusCode())
    }

    @Test
    fun `틀린 Basic 자격이어도 세션 쿠키 사용자로 되돌아간다 (v1_6 UserApp_currentUser)`() {
        val secret = project(ProjectScope.PRIVATE)
        val cookie = advertise(secret, basic(member)).headers().allValues("Set-Cookie").first { it.startsWith("JSESSIONID=") }.substringBefore(';')
        assertEquals(401, advertise(secret, basic(member, "wrong")).statusCode())
        assertEquals(200, advertise(secret, basic(member, "wrong"), cookie).statusCode())
    }

    private fun raw(path: String, method: String = "GET", body: ByteArray? = null, vararg headers: Pair<String, String>): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
                .method(method, if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofByteArray(body))
                .apply { headers.forEach { (k, v) -> header(k, v) } }.build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )

    @Test
    fun `형식이 틀린 자격의 400은 Cache-Control이 없고 방식 이름은 보지 않는다 (v1_6 BasicAuthAction은 기본 액션 밖이다)`() {
        val malformed = raw("/${project.owner}/${project.name}/info/refs?service=git-upload-pack", headers = arrayOf("Authorization" to "Bearer abc"))
        assertEquals(400, malformed.statusCode()); assertTrue(malformed.headers().firstValue("Cache-Control").isEmpty)
        // 방식 이름이 소문자여도 앞 여섯 글자를 건너뛰어 같은 자격으로 읽는다
        val lower = raw("/${project.owner}/${project.name}/info/refs?service=git-receive-pack", headers = arrayOf("Authorization" to basic(member).replaceFirst("Basic", "basic")))
        assertEquals(200, lower.statusCode())
    }

    private fun sessionCookie(response: HttpResponse<ByteArray>) = response.headers().allValues("Set-Cookie").firstOrNull { it.startsWith("JSESSIONID=") }

    @Test
    fun `Basic 인증은 404와 403 응답에도 세션 쿠키를 심는다 (v1_6 BasicAuthAction이 가장 바깥)`() {
        val noProject = raw("/${member.loginId}/nothing/info/refs?service=git-upload-pack", headers = arrayOf("Authorization" to basic(member)))
        assertEquals(404, noProject.statusCode()); assertTrue(sessionCookie(noProject) != null)
        val noService = raw("/${project.owner}/${project.name}/info/refs", headers = arrayOf("Authorization" to basic(member)))
        assertEquals(403, noService.statusCode()); assertTrue(String(noService.body()).contains("Unsupported service: getanyfile")); assertTrue(sessionCookie(noService) != null)
        // 자격이 틀리면 쿠키가 없고, 형식이 틀리면(콜론 없음) 400이다
        val wrong = raw("/${member.loginId}/nothing/info/refs?service=git-upload-pack", headers = arrayOf("Authorization" to basic(member, "wrong")))
        assertEquals(404, wrong.statusCode()); assertTrue(sessionCookie(wrong) == null)
        val malformed = raw("/${project.owner}/${project.name}/info/refs?service=git-upload-pack", headers = arrayOf("Authorization" to "Basic bm9jb2xvbg=="))
        assertEquals(400, malformed.statusCode()); assertTrue(sessionCookie(malformed) == null)
    }

    @Test
    fun `저장소 디렉터리가 없어도 info refs는 빈 저장소와 같은 광고를 200으로 준다`() {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "nodir$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        // v1.6 GitApp은 저장소 존재를 확인하지 않고 디렉터리를 만들지도 않는다
        val upload = raw("/${p.owner}/${p.name}/info/refs?service=git-upload-pack")
        assertEquals(200, upload.statusCode())
        assertEquals("application/x-git-upload-pack-advertisement", upload.headers().firstValue("Content-Type").get())
        val text = String(upload.body())
        assertTrue(text.startsWith("001e# service=git-upload-pack\n0000"), text); assertTrue(text.contains("capabilities^{}"), text); assertTrue(text.endsWith("0000"), text)
        val receive = raw("/${p.owner}/${p.name}/info/refs?service=git-receive-pack", headers = arrayOf("Authorization" to basic(member)))
        assertEquals(200, receive.statusCode()); assertTrue(String(receive.body()).contains("capabilities^{}"))
        assertTrue(!java.io.File(repositories.openRepositoryForHttp(p).use { it.directory.path }).exists())
    }

    @Test
    fun `RPC 중 IOException은 밖으로 던지지 않고 200으로 끝난다`() {
        // v1.6은 응답 200을 먼저 보내고 uploadPack failed를 로그로만 남긴다. 없는 객체를 want하면 ERR 패킷이 흐른다.
        val want = "0032want ${"1".repeat(40)}\n00000009done\n".toByteArray()
        val response = raw("/${project.owner}/${project.name}/git-upload-pack", "POST", want, "Content-Type" to "application/x-git-upload-pack-request")
        assertEquals(200, response.statusCode())
        assertTrue(String(response.body()).contains("ERR"), String(response.body()))
    }

    @Test
    fun `RPC 본문이 102400바이트를 넘으면 413이고 그 이하는 처리한다 (v1_6 isMaxSizeExceeded)`() {
        // v1.6 GitApp.serviceRpc: Play의 기본 본문 한도(parsers.text.maxLength 102400)를 넘으면 처리하지 않고 413이다
        val big = ByteArray(102_401) { 'x'.code.toByte() }
        val over = raw("/${project.owner}/${project.name}/git-upload-pack", "POST", big, "Content-Type" to "application/x-git-upload-pack-request")
        assertEquals(413, over.statusCode())
        val edge = raw("/${project.owner}/${project.name}/git-upload-pack", "POST", ByteArray(102_400) { 'x'.code.toByte() }, "Content-Type" to "application/x-git-upload-pack-request")
        assertTrue(edge.statusCode() != 413, edge.statusCode().toString())
    }

    @Test
    fun `권한 없음 메시지는 요청 언어로 쓰고 문자 집합을 못 쓰는 클라이언트의 메시지는 JVM 기본 로케일로 쓴다`() {
        val secret = project(ProjectScope.PRIVATE)
        val auth = "Authorization" to basic(outsider)
        val ko = raw("/${secret.owner}/${secret.name}/info/refs?service=git-upload-pack", headers = arrayOf(auth, "User-Agent" to "git/2.30.0", "Accept-Language" to "ko-KR"))
        assertEquals(403, ko.statusCode()); assertTrue(String(ko.body()).contains("권한이 없습니다"), String(ko.body()))
        val en = raw("/${secret.owner}/${secret.name}/info/refs?service=git-upload-pack", headers = arrayOf(auth, "User-Agent" to "git/2.30.0"))
        assertTrue(String(en.body()).contains("has no permission"), String(en.body()))
        val old = raw("/${secret.owner}/${secret.name}/info/refs?service=git-upload-pack", headers = arrayOf(auth, "User-Agent" to "git/2.0.0", "Accept-Language" to "ko-KR"))
        assertEquals("text/plain", old.headers().firstValue("Content-Type").get())
    }
}
