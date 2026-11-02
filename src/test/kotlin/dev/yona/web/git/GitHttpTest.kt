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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0", "yona.git-default-branch=master"])
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
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var apiTokenService: dev.yona.domain.apitoken.ApiTokenService
    @Autowired private lateinit var tokenFailures: dev.yona.security.TokenFailureLimiter
    @Autowired private lateinit var recentVisits: dev.yona.domain.project.RecentVisitService
    @Autowired private lateinit var recentProjects: dev.yona.domain.project.RecentProjectRepository
    @Autowired private lateinit var auditLogs: dev.yona.domain.audit.AuditLogRepository
    @Autowired private lateinit var meters: io.micrometer.core.instrument.MeterRegistry
    @Autowired private lateinit var history: dev.yona.repository.CommitHistoryService
    @Autowired private lateinit var browser: dev.yona.repository.CodeBrowserService
    @Autowired private lateinit var branchService: dev.yona.repository.BranchService
    @Autowired private lateinit var tagService: dev.yona.repository.TagService
    @Autowired private lateinit var diffService: dev.yona.repository.diff.DiffService
    @Autowired private lateinit var gpgKeyService: dev.yona.domain.gpgkey.GpgKeyService
    @Autowired private lateinit var gpgKeyRepository: dev.yona.domain.gpgkey.GpgKeyRepository

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
        // 토큰은 사용자를 참조하므로 먼저 지운다. 같은 주소(127.0.0.1)의 실패 기록은 시험끼리 영향을 주지 않게 비운다(X-5)
        apiTokenRepository.deleteAll(); tokenCache.clear()
        // GPG 키도 사용자를 참조한다(하위 키 행이 주 키 행을 가리키므로 하위 키부터 지운다, X-4)
        gpgKeyRepository.findAll().filter { it.parent != null }.forEach { gpgKeyRepository.delete(it) }
        gpgKeyRepository.flush(); gpgKeyRepository.deleteAll()
        auditLogs.deleteAllInBatch()
        tokenFailures.clear()
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
        users.deleteAll(created)
    }

    private fun get(url: String): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    private fun url(p: Project = project) = "http://localhost:$port/${p.owner}/${p.name}"

    /** 사용자마다 한 번 만든 개인용 API 토큰이다. git HTTP는 토큰만 받는다(D-70). */
    private val tokenCache = mutableMapOf<Long, String>()

    private fun tokenOf(u: User): String = tokenCache.getOrPut(requireNotNull(u.id)) { (apiTokenService.create(u, "git", setOf("repo"), null) as dev.yona.domain.apitoken.TokenCreation.Created).value }

    /** [password]를 주지 않으면 사용자의 토큰이다. 틀린 값을 시험할 때만 [password]를 준다. */
    private fun credentials(u: User, password: String? = null) = UsernamePasswordCredentialsProvider(u.loginId, password ?: tokenOf(u))

    /** 작업 디렉터리를 새로 만들어 파일 하나를 커밋하고 [target] 브랜치로 밀어 넣는다. */
    private fun push(p: Project, user: User?, branch: String = "master", password: String? = null, file: String = "a.txt", ref: String = "refs/heads/$branch") {
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
        fun auth(name: String) = "Basic " + java.util.Base64.getEncoder().encodeToString("$name:${tokenOf(member)}".toByteArray())
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

    private fun gitEvents(action: String) = auditLogs.findAll().filter { it.action == action && it.projectId == project.id }

    @Test
    fun `푸시하면 git push 감사 기록에 행위자와 ref가 남는다 (X-9 B3)`() {
        push(project, member, branch = "feature")
        val event = gitEvents("git.push").single()
        assertEquals(member.loginId, event.actorLogin)
        assertEquals("${project.owner}/${project.name}", event.targetLabel)
        assertTrue(event.details!!.contains("refs/heads/feature") && event.details!!.contains("\"type\":\"CREATE\""), event.details)
    }

    @Test
    fun `브랜치와 태그를 지우면 ref deleted 기록이 남고 종류를 구분한다 (X-9 B3)`() {
        push(project, member, branch = "feature"); push(project, member, branch = "t1", ref = "refs/tags/t1")
        val dir = File(work, "del-${counter.incrementAndGet()}")
        Git.init().setDirectory(dir).call().use { git ->
            for (ref in listOf("refs/heads/feature", "refs/tags/t1")) {
                git.push().setRemote(url()).setRefSpecs(RefSpec(":$ref")).setCredentialsProvider(credentials(member)).call()
            }
        }
        val kinds = gitEvents("git.ref_deleted").map { it.details!! }
        assertEquals(2, kinds.size)
        assertTrue(kinds.any { it.contains("\"kind\":\"branch\"") && it.contains("refs/heads/feature") })
        assertTrue(kinds.any { it.contains("\"kind\":\"tag\"") && it.contains("refs/tags/t1") })
    }

    @Test
    fun `강제 푸시는 force push 기록이 따로 남는다 (X-9 B3)`() {
        push(project, member, branch = "feature")
        val dir = File(work, "force-${counter.incrementAndGet()}")
        Git.init().setDirectory(dir).setInitialBranch("feature").call().use { git ->
            File(dir, "b.txt").writeText("other")
            git.add().addFilepattern("b.txt").call()
            git.commit().setMessage("diverged").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            git.push().setRemote(url()).setForce(true).setRefSpecs(RefSpec("refs/heads/feature:refs/heads/feature")).setCredentialsProvider(credentials(member)).call()
        }
        assertEquals(1, gitEvents("git.force_push").size)
        assertEquals(2, gitEvents("git.push").size)
    }

    @Test
    fun `예약된 ref로 거부된 푸시는 거부 기록으로 남는다 (X-9 B3)`() {
        assertFailsWith<IllegalStateException> { push(project, member, ref = "refs/yobi/test") }
        val event = gitEvents("git.push_rejected").single()
        assertEquals(dev.yona.domain.audit.AuditOutcome.DENIED, event.outcome)
        assertTrue(event.details!!.contains("refs/yobi/test") && event.details!!.contains("reserved"), event.details)
        assertTrue(gitEvents("git.push").isEmpty())
    }

    @Test
    fun `복제는 감사 기록을 남기지 않는다 (X-9 B3)`() {
        push(project, member)
        val before = auditLogs.findAll().filter { it.action.startsWith("git.") }.size
        clone(project, member)
        assertEquals(before, auditLogs.findAll().filter { it.action.startsWith("git.") }.size)
    }

    private fun timerCount(name: String, vararg tags: String) = meters.find(name).tags(*tags).timer()?.count() ?: 0L
    private fun counterValue(name: String, vararg tags: String) = meters.find(name).tags(*tags).counters().sumOf { it.count() }

    @Test
    fun `푸시와 복제의 요청 시간과 전송량과 푸시 수를 지표로 남긴다 (X-10 C5c)`() {
        val receive = timerCount("yona.vcs.request", "vcs", "git", "service", "receive_pack", "target", "code", "result", "ok")
        val upload = timerCount("yona.vcs.request", "vcs", "git", "service", "upload_pack", "target", "code", "result", "ok")
        val refs = timerCount("yona.vcs.request", "vcs", "git", "service", "info_refs", "target", "code", "result", "ok")
        val bytesIn = counterValue("yona.vcs.transfer", "vcs", "git", "direction", "in")
        val bytesOut = counterValue("yona.vcs.transfer", "vcs", "git", "direction", "out")
        val pushes = counterValue("yona.vcs.pushes", "vcs", "git", "kind", "branch", "result", "ok")
        push(project, member)
        clone(project, member)
        assertEquals(receive + 1, timerCount("yona.vcs.request", "vcs", "git", "service", "receive_pack", "target", "code", "result", "ok"))
        assertEquals(upload + 1, timerCount("yona.vcs.request", "vcs", "git", "service", "upload_pack", "target", "code", "result", "ok"))
        assertTrue(timerCount("yona.vcs.request", "vcs", "git", "service", "info_refs", "target", "code", "result", "ok") >= refs + 2)
        assertTrue(counterValue("yona.vcs.transfer", "vcs", "git", "direction", "in") > bytesIn, "받은 바이트가 늘지 않았다")
        assertTrue(counterValue("yona.vcs.transfer", "vcs", "git", "direction", "out") > bytesOut, "보낸 바이트가 늘지 않았다")
        assertEquals(pushes + 1, counterValue("yona.vcs.pushes", "vcs", "git", "kind", "branch", "result", "ok"))
    }

    @Test
    fun `이력과 코드 보기와 diff와 브랜치와 태그 작업의 시간을 지표로 남긴다 (X-10 C5c)`() {
        push(project, member)
        val ops = listOf("history", "commit", "browse", "raw_file", "diff", "branches", "tags")
        val before = ops.associateWith { timerCount("yona.vcs.operation.duration", "vcs", "git", "op", it, "result", "success") }
        val head = history.history(project, 0, 5, null, null)!!.first()
        history.commit(project, head.id!!)
        browser.metaData(project, null, "")
        browser.rawFile(project, "HEAD", "a.txt")
        diffService.commitDiff(project, head.id!!)
        branchService.branches(project)
        tagService.tags(project)
        ops.forEach { assertTrue(timerCount("yona.vcs.operation.duration", "vcs", "git", "op", it, "result", "success") > before.getValue(it), it) }
    }

    @Test
    fun `푸시 뒤 훅의 시간을 지표로 남긴다 (X-10 C5c)`() {
        val before = listOf("last_pushed", "recent_branch", "post_receive_jobs").associateWith { timerCount("yona.vcs.hook.duration", "hook", it, "result", "success") }
        push(project, member)
        before.forEach { (hook, count) -> assertEquals(count + 1, timerCount("yona.vcs.hook.duration", "hook", hook, "result", "success"), hook) }
    }

    @Test
    fun `거부된 푸시는 이유와 함께 센다 (X-10 C5c)`() {
        val before = counterValue("yona.vcs.pushes", "vcs", "git", "result", "rejected", "reason", "reserved")
        assertFailsWith<IllegalStateException> { push(project, member, ref = "refs/yobi/test") }
        assertEquals(before + 1, counterValue("yona.vcs.pushes", "vcs", "git", "result", "rejected", "reason", "reserved"))
    }

    @Test
    fun `인증 없이 비공개 저장소를 요청하면 denied로 센다 (X-10 C5c)`() {
        val secret = project(ProjectScope.PRIVATE)
        val before = timerCount("yona.vcs.request", "vcs", "git", "service", "info_refs", "target", "code", "result", "denied")
        get("${url(secret)}/info/refs?service=git-upload-pack")
        assertTrue(timerCount("yona.vcs.request", "vcs", "git", "service", "info_refs", "target", "code", "result", "denied") > before)
    }

    @Test
    fun `지표 태그에 프로젝트와 사용자 이름이 들어가지 않는다 (X-10 C5c)`() {
        push(project, member); clone(project, member)
        val leaked = meters.meters.filter { m -> m.id.name.startsWith("yona.vcs") }.flatMap { m -> m.id.tags.map { it.value } }.filter { it.contains(project.name!!) || it.contains(member.loginId!!) }
        assertEquals(emptyList(), leaked)
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

    private fun basic(u: User, password: String? = null) = "Basic " + java.util.Base64.getEncoder().encodeToString("${u.loginId}:${password ?: tokenOf(u)}".toByteArray())

    private fun advertise(p: Project, authorization: String? = null, cookie: String? = null): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("${url(p)}/info/refs?service=git-upload-pack")).GET()
                .apply { authorization?.let { header("Authorization", it) }; cookie?.let { header("Cookie", it) } }.build(),
            HttpResponse.BodyHandlers.ofByteArray()
        )

    @Test
    fun `Basic 인증은 세션 쿠키를 심지 않아 쿠키만으로는 읽을 수 없다 (D-70, v1_6은 비밀번호 인증으로 세션을 만들었다)`() {
        val secret = project(ProjectScope.PRIVATE)
        val first = advertise(secret, basic(member))
        assertEquals(200, first.statusCode())
        assertTrue(first.headers().allValues("Set-Cookie").none { it.startsWith("JSESSIONID=") }, first.headers().toString())
        assertEquals(401, advertise(secret).statusCode())
    }

    @Test
    fun `비밀번호를 보내면 401과 토큰 안내이고 웹 세션을 만들지 않는다 (D-70)`() {
        val secret = project(ProjectScope.PRIVATE)
        val response = advertise(secret, basic(member, "1234"))
        assertEquals(401, response.statusCode())
        assertTrue(String(response.body()).contains("personal access token"), String(response.body()))
        assertTrue(response.headers().allValues("Set-Cookie").none { it.startsWith("JSESSIONID=") })
    }

    @Test
    fun `틀린 토큰이어도 웹 로그인 세션 쿠키 사용자로 되돌아간다 (v1_6 UserApp_currentUser)`() {
        val secret = project(ProjectScope.PRIVATE)
        val login = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port/users/login")).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("loginIdOrEmail=${member.loginId}&password=1234")).build(), HttpResponse.BodyHandlers.ofString()
        )
        val cookie = login.headers().allValues("Set-Cookie").first { it.startsWith("JSESSIONID=") }.substringBefore(';')
        assertEquals(401, advertise(secret, basic(member, "yona_pat_" + "x".repeat(40))).statusCode())
        assertEquals(200, advertise(secret, basic(member, "yona_pat_" + "x".repeat(40)), cookie).statusCode())
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
    fun `Basic 인증은 404와 403 응답에도 세션 쿠키를 심지 않는다 (D-70)`() {
        val noProject = raw("/${member.loginId}/nothing/info/refs?service=git-upload-pack", headers = arrayOf("Authorization" to basic(member)))
        assertEquals(404, noProject.statusCode()); assertTrue(sessionCookie(noProject) == null)
        val noService = raw("/${project.owner}/${project.name}/info/refs", headers = arrayOf("Authorization" to basic(member)))
        assertEquals(403, noService.statusCode()); assertTrue(String(noService.body()).contains("Unsupported service: getanyfile")); assertTrue(sessionCookie(noService) == null)
        // 형식이 틀리면(콜론 없음) 400이다
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
    fun `RPC 본문이 102400바이트를 넘어도 413이 아니다 (공식 v1_16_0은 130MB 푸시도 받는다)`() {
        // Play는 git 요청 본문을 원시 본문으로 받아 한도가 사실상 없다. 폼 본문의 한도(102400)와 다르다
        val big = ByteArray(300_000) { 'x'.code.toByte() }
        val response = raw("/${project.owner}/${project.name}/git-upload-pack", "POST", big, "Content-Type" to "application/x-git-upload-pack-request")
        assertTrue(response.statusCode() != 413, response.statusCode().toString())
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

    // --- 브랜치 보호 푸시 검사 (X-33 2단계, D-45) ---

    @Autowired private lateinit var protectionRules: dev.yona.domain.project.RulesetRepository

    private fun plainMember(): User = user("gp").also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }

    private fun protect(pattern: String = "master", configure: dev.yona.domain.project.Ruleset.() -> Unit = {}) {
        protectionRules.save(dev.yona.domain.project.Ruleset(project = project, name = pattern, pattern = pattern, createdDate = java.util.Date()).apply(configure))
    }

    /** 복제해서 커밋을 하나 더 얹어([force]이면 이력을 다시 써서) [branch]로 밀어 넣고 원격 갱신 결과를 돌려준다. */
    private fun pushMore(user: User, branch: String = "master", force: Boolean = false, delete: Boolean = false): org.eclipse.jgit.transport.RemoteRefUpdate {
        val dir = clone(project, user)
        Git.open(dir).use { git ->
            val spec = if (delete) RefSpec(":refs/heads/$branch") else {
                if (git.repository.branch != branch) git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/$branch").call()
                File(dir, "more-${counter.incrementAndGet()}.txt").writeText("more")
                git.add().addFilepattern(".").call()
                if (force) git.commit().setAmend(true).setMessage("rewritten").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
                else git.commit().setMessage("more").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
                RefSpec("refs/heads/$branch:refs/heads/$branch")
            }
            return git.push().setRemote(url(project)).setCredentialsProvider(credentials(user)).setForce(force).setRefSpecs(spec).call().flatMap { it.remoteUpdates }.single()
        }
    }

    /**
     * 복제해서 커밋을 하나 더 얹되 [key]로 서명해([key]가 `null`이면 서명 없이) [branch]로 밀어 넣는다. 커밋터 이메일은 [committerEmail]이다.
     * 서명은 실제 `gpg` 없이 BouncyCastle로 만든다(X-4).
     */
    private fun pushSigned(user: User, key: dev.yona.domain.gpgkey.TestGpgKey?, committerEmail: String, branch: String = "master", startPoint: String? = "origin/master"): org.eclipse.jgit.transport.RemoteRefUpdate {
        val dir = clone(project, user)
        Git.open(dir).use { git ->
            val repository = git.repository
            val inserter = repository.newObjectInserter()
            val head = repository.resolve(startPoint ?: "HEAD")
            val tree = org.eclipse.jgit.lib.TreeFormatter().apply { append("signed-${counter.incrementAndGet()}.txt", org.eclipse.jgit.lib.FileMode.REGULAR_FILE, inserter.insert(org.eclipse.jgit.lib.Constants.OBJ_BLOB, "x".toByteArray())) }
            val ident = org.eclipse.jgit.lib.PersonIdent("Signer", committerEmail)
            val builder = org.eclipse.jgit.lib.CommitBuilder().apply { setTreeId(inserter.insert(tree)); author = ident; committer = ident; message = "signed push"; head?.let { setParentId(it) } }
            if (key != null) builder.setGpgSignature(org.eclipse.jgit.lib.GpgSignature(key.sign(builder.build()).toByteArray()))
            val id = inserter.insert(builder)
            inserter.flush()
            repository.updateRef("refs/heads/$branch").apply { setNewObjectId(id); setForceUpdate(true) }.update()
            return git.push().setRemote(url(project)).setCredentialsProvider(credentials(user)).setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch")).call().flatMap { it.remoteUpdates }.single()
        }
    }

    /** [owner]에게 이메일 [email]을 사용자 ID로 가진 새 GPG 키를 등록하고 돌려준다. [owner]의 대표 이메일을 [email]로 맞춘다. */
    private fun registeredKey(owner: User, email: String): dev.yona.domain.gpgkey.TestGpgKey {
        owner.email = email; users.save(owner)
        return dev.yona.domain.gpgkey.TestGpgKey.generate(userId = "Signer <$email>").also { gpgKeyService.add(owner, "k", it.publicArmored) }
    }

    @Test
    fun `서명 커밋을 요구하는 규칙은 서명 없는 커밋과 등록되지 않은 키의 커밋을 막는다 (X-4)`() {
        push(project, member); protect { requireSignedCommits = true; blockForcePushes = false }
        val plain = plainMember()
        assertRejected(pushSigned(plain, null, plain.email!!), "protected branch", "signatures", "unsigned")
        val stranger = dev.yona.domain.gpgkey.TestGpgKey.generate(userId = "S <${plain.email}>")
        assertRejected(pushSigned(plain, stranger, plain.email!!), "protected branch", "unknown_key")
    }

    @Test
    fun `서명 커밋을 요구하는 규칙은 등록한 키로 서명한 Verified 커밋을 받는다 (X-4)`() {
        push(project, member); protect { requireSignedCommits = true }
        val plain = plainMember()
        val key = registeredKey(plain, "signer-${counter.incrementAndGet()}@example.com")
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushSigned(plain, key, plain.email!!).status)
    }

    @Test
    fun `커밋터 이메일이 키와 다르면 서명이 맞아도 막는다 (X-4)`() {
        push(project, member); protect { requireSignedCommits = true }
        val plain = plainMember()
        val key = registeredKey(plain, "signer-${counter.incrementAndGet()}@example.com")
        assertRejected(pushSigned(plain, key, "someone-else@example.com"), "protected branch", "bad_email")
    }

    @Test
    fun `바이패스를 허용한 규칙 묶음은 프로젝트 관리자가 서명 없이 푸시해도 우회한다 (X-4)`() {
        push(project, member); protect { requireSignedCommits = true; bypassAdmins = true }
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushSigned(member, null, member.email!!).status)
        protectionRules.findAll().filter { it.project?.id == project.id }.forEach { it.bypassAdmins = false; protectionRules.save(it) }
        assertRejected(pushSigned(member, null, member.email!!), "protected branch", "signatures")
    }

    @Test
    fun `규칙 이전의 서명 없는 이력은 다시 검사하지 않고 브랜치를 새로 만들 때도 새 커밋만 본다 (X-4)`() {
        push(project, member); protect("**") { requireSignedCommits = true }
        val plain = plainMember()
        val key = registeredKey(plain, "signer-${counter.incrementAndGet()}@example.com")
        // 이미 있는 서명 없는 master 위에 서명한 커밋 하나를 올린다: 새 커밋만 검사하므로 통과한다
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushSigned(plain, key, plain.email!!).status)
        // 새 브랜치를 master에서 갈라 서명 없는 커밋을 올리면 막고 서명한 커밋이면 받는다
        assertRejected(pushSigned(plain, null, plain.email!!, branch = "feature", startPoint = "origin/master"), "signatures")
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushSigned(plain, key, plain.email!!, branch = "feature2", startPoint = "origin/master").status)
    }

    @Test
    fun `서명 커밋 규칙은 대상이 아닌 브랜치와 삭제와 태그에는 영향이 없다 (X-4)`() {
        push(project, member); push(project, member, branch = "feature")
        protect("master") { requireSignedCommits = true }
        val plain = plainMember()
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushSigned(plain, null, plain.email!!, branch = "feature", startPoint = "origin/feature").status)
        push(project, plain, branch = "v1", ref = "refs/tags/v1")
    }

    private fun assertRejected(update: org.eclipse.jgit.transport.RemoteRefUpdate, vararg words: String) {
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON, update.status, update.message)
        words.forEach { assertTrue(update.message.orEmpty().contains(it), update.message) }
    }

    @Test
    fun `풀 리퀘스트를 요구하는 규칙은 일반 구성원의 직접 푸시를 막는다`() {
        push(project, member); protect { requirePullRequest = true; blockForcePushes = false }
        val plain = plainMember()
        assertRejected(pushMore(plain), "protected branch", "pull request")
    }

    @Test
    fun `프로젝트 관리자는 바이패스를 허용한 규칙 묶음을 우회하고 허용하지 않으면 막힌다`() {
        push(project, member); protect { requirePullRequest = true; blockForcePushes = false; bypassAdmins = true }
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushMore(member).status)
        protectionRules.findAll().filter { it.project?.id == project.id }.forEach { it.bypassAdmins = false; protectionRules.save(it) }
        assertRejected(pushMore(member), "protected branch")
    }

    @Test
    fun `규칙이 있는 브랜치의 강제 푸시는 막고 허용하면 받는다`() {
        push(project, member); protect()
        val plain = plainMember()
        assertRejected(pushMore(plain, force = true), "protected branch", "force")
        protectionRules.findAll().filter { it.project?.id == project.id }.forEach { it.blockForcePushes = false; protectionRules.save(it) }
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushMore(plain, force = true).status)
    }

    @Test
    fun `규칙이 있는 브랜치의 삭제는 막고 허용하면 지운다`() {
        push(project, member, branch = "master"); push(project, member, branch = "release")
        protect("release")
        val plain = plainMember()
        assertRejected(pushMore(plain, branch = "release", delete = true), "protected branch", "delet")
        protectionRules.findAll().filter { it.project?.id == project.id }.forEach { it.restrictDeletions = false; protectionRules.save(it) }
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushMore(plain, branch = "release", delete = true).status)
    }

    @Test
    fun `규칙이 없는 브랜치와 태그는 영향이 없다`() {
        push(project, member, branch = "master"); push(project, member, branch = "feature")
        protect("master") { requirePullRequest = true }
        val plain = plainMember()
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushMore(plain, branch = "feature").status)
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushMore(plain, branch = "feature", force = true).status)
        protect("**") { requirePullRequest = true }
        push(project, plain, branch = "v1", ref = "refs/tags/v1")
    }

    @Test
    fun `갱신 제한은 일반 구성원의 직접 푸시를 막고 바이패스를 허용한 룰셋은 프로젝트 관리자가 우회한다`() {
        push(project, member); protect { restrictUpdates = true; blockForcePushes = false; restrictDeletions = false }
        val plain = plainMember()
        assertRejected(pushMore(plain), "protected branch", "updates")
        assertRejected(pushMore(member), "protected branch")
        protectionRules.findAll().filter { it.project?.id == project.id }.forEach { it.bypassAdmins = true; protectionRules.save(it) }
        assertEquals(org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK, pushMore(member).status)
        assertRejected(pushMore(plain), "protected branch")
    }

    @Test
    fun `갱신 제한은 브랜치를 새로 만드는 푸시는 막지 않는다`() {
        push(project, member); protect("master") { restrictUpdates = true }
        push(project, plainMember(), branch = "feature")
    }
}
