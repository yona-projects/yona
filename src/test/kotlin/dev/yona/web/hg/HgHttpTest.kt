package dev.yona.web.hg

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
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.HgTestSupport
import dev.yona.support.TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 실제 Mercurial 클라이언트(`hg`)로 HTTP 서빙(`?cmd=` 와이어 프로토콜)을 검증한다: 복제, 가져오기, 푸시와 접근 권한(X-7 6단계).
 * 서버가 따로 뜨므로 데이터는 커밋하고, 테스트가 끝나면 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-http"])
class HgHttpTest {

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
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var apiTokenService: dev.yona.domain.apitoken.ApiTokenService
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var tokenFailures: dev.yona.security.TokenFailureLimiter
    @Autowired private lateinit var gpgKeyService: dev.yona.domain.gpgkey.GpgKeyService
    @Autowired private lateinit var gpgKeyRepository: dev.yona.domain.gpgkey.GpgKeyRepository
    @Autowired private lateinit var auditLogs: dev.yona.domain.audit.AuditLogRepository
    @Autowired private lateinit var protectionRules: dev.yona.domain.project.RulesetRepository
    @Autowired private lateinit var pushedBranches: dev.yona.domain.project.PushedBranchRepository
    @Autowired private lateinit var siteSettings: dev.yona.domain.site.SiteSettingService

    @TempDir lateinit var work: File

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var project: Project
    private val created = mutableListOf<User>()
    private val tokens = mutableMapOf<Long, String>()

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))).also { created += it }
    }

    private fun tokenOf(u: User): String = tokens.getOrPut(requireNotNull(u.id)) { (apiTokenService.create(u, "hg", setOf("repo"), null) as dev.yona.domain.apitoken.TokenCreation.Created).value }

    private fun project(scope: ProjectScope, vcs: String = "Mercurial"): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "hg$n", vcs = vcs, projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p
    }

    private fun serverDir(p: Project) = hgRepositories.directoryOf(p.owner!!, p.name!!)

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        member = user("hm"); outsider = user("ho")
        project = project(ProjectScope.PUBLIC)
        HgTestSupport.commit(serverDir(project), "서버의 첫 커밋", mapOf("a.txt" to "hello\n"))
    }

    @AfterEach
    fun tearDown() {
        siteSettings.setLfsEnabled(true)
        // 토큰은 사용자를 참조하므로 먼저 지운다
        gpgKeyRepository.findAll().filter { it.parent != null }.forEach { gpgKeyRepository.delete(it) }; gpgKeyRepository.flush(); gpgKeyRepository.deleteAll()
        apiTokenRepository.deleteAll(); tokens.clear(); tokenFailures.clear(); auditLogs.deleteAllInBatch()
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
        users.deleteAll(created)
    }

    private fun url(p: Project = project, user: User? = null) = "http://" + (user?.let { "${it.loginId}:${tokenOf(it)}@" } ?: "") + "localhost:$port/${p.owner}/${p.name}"

    /** `hg`를 [dir]에서 실행하고 종료 코드와 출력을 돌려준다. 묻는 일은 모두 꺼서 인증이 필요하면 실패한다. */
    private fun hg(dir: File, vararg args: String): Pair<Int, String> {
        val builder = ProcessBuilder(listOf("hg", "--config", "ui.interactive=off") + args.toList()).directory(dir).redirectErrorStream(true)
        builder.environment()["HGPLAIN"] = "1"; builder.environment()["HGUSER"] = "Tester <tester@example.com>"
        builder.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
        val process = builder.start()
        val out = String(process.inputStream.readBytes())
        return process.waitFor() to out
    }

    private fun clone(p: Project, user: User? = null): Pair<File, Pair<Int, String>> {
        val dir = File(work, "clone-${counter.incrementAndGet()}")
        return dir to hg(work, "clone", url(p, user), dir.path)
    }

    private fun raw(path: String): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    @Test
    fun `공개 프로젝트는 익명으로 복제한다`() {
        val (dir, result) = clone(project)
        assertEquals(0, result.first, result.second)
        assertEquals("hello\n", File(dir, "a.txt").readText())
    }

    @Test
    fun `서버에 새 커밋이 생기면 pull로 가져온다`() {
        val (dir, _) = clone(project)
        HgTestSupport.commit(serverDir(project), "둘째 커밋", mapOf("b.txt" to "b\n"), "2026-11-02 10:00 +0900")
        val pulled = hg(dir, "pull", "-u")
        assertEquals(0, pulled.first, pulled.second)
        assertEquals("b\n", File(dir, "b.txt").readText())
    }

    @Test
    fun `비공개 프로젝트는 익명이 복제하지 못하고 토큰을 가진 구성원은 한다`() {
        val closed = project(ProjectScope.PRIVATE)
        HgTestSupport.commit(serverDir(closed), "비공개 커밋", mapOf("secret.txt" to "s\n"))
        val anonymous = clone(closed)
        assertFalse(anonymous.second.first == 0, "익명 복제가 성공했다: ${anonymous.second.second}")
        assertTrue(anonymous.second.second.contains("authorization", ignoreCase = true) || anonymous.second.second.contains("401"), anonymous.second.second)
        val (dir, result) = clone(closed, member)
        assertEquals(0, result.first, result.second)
        assertEquals("s\n", File(dir, "secret.txt").readText())
    }

    @Test
    fun `구성원은 푸시하고 서버 저장소에 체인지셋이 들어온다`() {
        val (dir, _) = clone(project, member)
        File(dir, "c.txt").writeText("from client\n")
        assertEquals(0, hg(dir, "add", "c.txt").first)
        assertEquals(0, hg(dir, "commit", "-m", "클라이언트 커밋", "-d", "2026-11-03 10:00 +0900").first)
        val pushed = hg(dir, "push", url(project, member))
        assertEquals(0, pushed.first, pushed.second)
        val log = HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n")
        assertTrue("클라이언트 커밋" in log, log)
        assertTrue(HgTestSupport.verifies(serverDir(project)))
    }

    @Test
    fun `익명이나 잘못된 비밀번호와 구성원이 아닌 사용자는 푸시하지 못한다`() {
        val (dir, _) = clone(project)
        File(dir, "d.txt").writeText("x\n"); hg(dir, "add", "d.txt"); hg(dir, "commit", "-m", "막힐 커밋")
        assertFalse(hg(dir, "push", url(project)).first == 0, "익명 푸시가 성공했다")
        assertFalse(hg(dir, "push", "http://${member.loginId}:wrong@localhost:$port/${project.owner}/${project.name}").first == 0, "잘못된 비밀번호 푸시가 성공했다")
        assertFalse(hg(dir, "push", url(project, outsider)).first == 0, "구성원이 아닌 사용자의 푸시가 성공했다")
        val log = HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n")
        assertFalse("막힐 커밋" in log, log)
    }

    @Test
    fun `Mercurial 프로젝트가 아니면 와이어 프로토콜로 답하지 않고 모르는 명령은 거절한다`() {
        // Git 프로젝트의 `?cmd=`는 v1.6처럼 쿼리를 무시하는 프로젝트 홈이다(Mercurial 응답이 아니다)
        val git = project(ProjectScope.PUBLIC, vcs = "GIT")
        assertFalse("unbundle" in String(raw("/${git.owner}/${git.name}?cmd=capabilities").body()))
        assertEquals(400, raw("/${project.owner}/${project.name}?cmd=nosuchcommand").statusCode())
        // 없는 프로젝트는 필터가 건드리지 않고 앱의 보통 처리(익명이면 접근 거절)로 넘어간다
        assertTrue(raw("/${member.loginId}/nosuchproject?cmd=capabilities").statusCode() in listOf(403, 404))
    }

    @Test
    fun `기능 목록은 와이어 프로토콜로 답한다`() {
        val body = String(raw("/${project.owner}/${project.name}?cmd=capabilities").body())
        assertTrue("batch" in body && "unbundle" in body, body)
    }

    // --- 푸시 전 검사와 푸시 뒤 처리(X-7 6b, 6c) ---

    private fun protect(pattern: String, configure: dev.yona.domain.project.Ruleset.() -> Unit = {}) {
        protectionRules.save(dev.yona.domain.project.Ruleset(project = project, name = pattern, pattern = pattern, createdDate = java.util.Date()).apply(configure))
    }

    /** 서버에 이름표 [bookmark]를 만들고 복제해 커밋 하나를 얹은 작업 복사본을 돌려준다. */
    private fun cloneWithBookmark(bookmark: String): File {
        HgTestSupport.hg(serverDir(project), "bookmark", bookmark)
        val (dir, result) = clone(project, member)
        assertEquals(0, result.first, result.second)
        // 이름표를 활성화해야 다음 커밋과 함께 움직인다
        assertEquals(0, hg(dir, "update", bookmark).first)
        File(dir, "more-${counter.incrementAndGet()}.txt").writeText("more\n")
        hg(dir, "add", "."); assertEquals(0, hg(dir, "commit", "-m", "더한 커밋", "-d", "2026-11-04 10:00 +0900").first)
        return dir
    }

    private fun serverBookmarks() = HgTestSupport.hg(serverDir(project), "bookmarks", "--template", "{bookmark} {node}\\n")

    @Test
    fun `풀 리퀘스트를 요구하는 이름표로는 직접 푸시하지 못하고 체인지셋도 남지 않는다`() {
        protect("main") { requirePullRequest = true }
        val dir = cloneWithBookmark("main")
        val before = serverBookmarks()
        val pushed = hg(dir, "push", "-B", "main", url(project, member))
        assertFalse(pushed.first == 0, "보호된 이름표로 푸시가 성공했다: ${pushed.second}")
        // 거절 사유가 클라이언트 화면에 보인다(hg4j `HgHookAbortException`)
        assertTrue("protected branch" in pushed.second && "pull request" in pushed.second, pushed.second)
        assertEquals(before, serverBookmarks())
        assertFalse("더한 커밋" in HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n"))
        assertTrue(HgTestSupport.verifies(serverDir(project)))
    }

    @Test
    fun `보호하지 않는 이름표로는 푸시하고 보호 규칙이 있어도 다른 이름표는 막지 않는다`() {
        protect("main") { requirePullRequest = true }
        val dir = cloneWithBookmark("feature")
        val pushed = hg(dir, "push", "-B", "feature", url(project, member))
        assertEquals(0, pushed.first, pushed.second)
        assertTrue("더한 커밋" in HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n"))
    }

    @Test
    fun `보호 규칙이 있는 이름표라도 일반 갱신은 강제 갱신으로 보지 않고 강제 푸시만 막는다`() {
        // 규칙 기본값은 삭제와 강제 푸시를 막는다. 새 체인지셋을 얹는 갱신(빨리감기)은 강제가 아니다
        protect("main")
        val dir = cloneWithBookmark("main")
        val pushed = hg(dir, "push", "-B", "main", url(project, member))
        assertEquals(0, pushed.first, pushed.second)
        assertTrue("더한 커밋" in HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n"))
        assertTrue(HgTestSupport.verifies(serverDir(project)))
    }

    @Test
    fun `후손이 아닌 곳으로 이름표를 옮기는 진짜 강제 푸시는 막힌다`() {
        protect("main")
        HgTestSupport.hg(serverDir(project), "bookmark", "main")
        // 서버의 main 위에 커밋을 하나 더 올려 둔다. 클라이언트는 그 앞(첫 커밋)에서 갈라진 커밋으로 main을 옮긴다
        HgTestSupport.commit(serverDir(project), "서버가 앞선다", mapOf("s.txt" to "s\n"), "2026-11-02 10:00 +0900")
        HgTestSupport.hg(serverDir(project), "bookmark", "-f", "-r", "tip", "main")
        val serverMain = HgTestSupport.hg(serverDir(project), "log", "-r", "main", "--template", "{node}")
        val (dir, cloned) = clone(project, member)
        assertEquals(0, cloned.first, cloned.second)
        hg(dir, "update", "-r", "0")
        File(dir, "d.txt").writeText("diverged\n"); hg(dir, "add", "d.txt")
        assertEquals(0, hg(dir, "commit", "-m", "갈라진 커밋", "-d", "2026-11-04 10:00 +0900").first)
        hg(dir, "bookmark", "-f", "main")
        val pushed = hg(dir, "push", "-f", "-B", "main", url(project, member))
        assertFalse(pushed.first == 0, "강제 푸시가 성공했다: ${pushed.second}")
        assertEquals(serverMain, HgTestSupport.hg(serverDir(project), "log", "-r", "main", "--template", "{node}"))
        assertFalse("갈라진 커밋" in HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n"))
    }

    @Test
    fun `이름표 삭제는 삭제를 막는 규칙에 걸린다`() {
        protect("main") { restrictDeletions = true }
        HgTestSupport.hg(serverDir(project), "bookmark", "main")
        val (dir, _) = clone(project, member)
        hg(dir, "bookmark", "-d", "main")
        val pushed = hg(dir, "push", "-B", "main", url(project, member))
        assertFalse(pushed.first == 0, "삭제가 허용됐다: ${pushed.second}")
        assertTrue("main" in serverBookmarks())
    }

    @Test
    fun `푸시하면 마지막 푸시 시각과 최근 푸시한 브랜치가 남는다`() {
        val dir = cloneWithBookmark("feature")
        assertEquals(0, hg(dir, "push", "-B", "feature", url(project, member)).first)
        val saved = projects.findById(project.id!!).get()
        assertTrue(saved.lastPushedDate != null)
        assertTrue(pushedBranches.findAll().filter { it.project?.id == project.id }.any { it.name == "refs/heads/feature" })
    }

    @Test
    fun `이름표 없이 체인지셋만 푸시해도 푸시 뒤 처리가 돈다`() {
        val (dir, _) = clone(project, member)
        File(dir, "plain.txt").writeText("p\n"); hg(dir, "add", "plain.txt"); hg(dir, "commit", "-m", "이름표 없는 커밋")
        assertEquals(0, hg(dir, "push", url(project, member)).first)
        assertTrue(projects.findById(project.id!!).get().lastPushedDate != null)
        assertTrue(pushedBranches.findAll().filter { it.project?.id == project.id }.any { it.name == "refs/heads/default" })
    }

    @Test
    fun `푸시와 거부는 hg 이름으로 감사 기록에 남는다`() {
        val dir = cloneWithBookmark("feature")
        assertEquals(0, hg(dir, "push", "-B", "feature", url(project, member)).first)
        protect("main") { requirePullRequest = true }
        val blocked = cloneWithBookmark("main")
        hg(blocked, "push", "-B", "main", url(project, member))
        val actions = auditLogs.findAll().map { it.action }
        assertTrue("hg.push" in actions, actions.toString())
        assertTrue("hg.push_rejected" in actions, actions.toString())
    }

    // --- LFS(X-7 9a, D-90): hg4j 내장 처리 ---

    private val lfsConfig = arrayOf("--config", "extensions.lfs=", "--config", "lfs.track=size('>100')")

    private fun lfsUrl(user: User? = null) = url(project, user) + "/.git/info/lfs"

    private fun lfsObjects(): List<File> = File(serverDir(project), ".hg/store/lfs/objects").walkTopDown().filter { it.isFile }.toList()

    @Test
    fun `LFS 파일을 푸시하면 저장소 안 LFS 저장소에 본문이 들어가고 복제하면 본문을 되받는다`() {
        val (dir, cloned) = clone(project, member)
        assertEquals(0, cloned.first, cloned.second)
        // 본문이 실행마다 달라야 한다: 같은 컴퓨터의 hg 사용자 캐시에 같은 본문이 있으면 서버가 이미 있는 객체로 본다(운영에서는 서로 다른 컴퓨터이다)
        val body = "큰 파일 본문 ${System.nanoTime()} ".repeat(30)
        File(dir, "big.bin").writeText(body)
        assertEquals(0, hg(dir, "--config", "extensions.lfs=", "--config", "lfs.track=size('>100')", "add", "big.bin").first)
        val committed = hg(dir, "--config", "extensions.lfs=", "--config", "lfs.track=size('>100')", "commit", "-m", "LFS 파일", "-d", "2026-11-03 10:00 +0900")
        assertEquals(0, committed.first, committed.second)
        val pushed = hg(dir, *lfsConfig, "--config", "lfs.url=${lfsUrl(member)}", "push", url(project, member))
        assertEquals(0, pushed.first, pushed.second)
        assertEquals(1, lfsObjects().size, "저장소 안 LFS 저장소에 본문이 한 개 있어야 한다. 푸시 출력=${pushed.second} 서버 lfs 관련 파일=" + File(serverDir(project), ".hg").walkTopDown().filter { it.isFile && it.path.contains("lfs") }.map { it.path.substringAfter(".hg/") }.toList() + " 서버 requires=" + File(serverDir(project), ".hg/requires").readText().replace("\n", ","))
        // 새로 복제해 본문을 되받는다(공개 프로젝트의 읽기는 익명이어도 된다)
        val fresh = File(work, "fresh-${counter.incrementAndGet()}")
        val got = hg(work, *lfsConfig, "--config", "lfs.url=${lfsUrl()}", "clone", url(project), fresh.path)
        assertEquals(0, got.first, got.second)
        assertEquals(body, File(fresh, "big.bin").readText())
    }

    @Test
    fun `구성원이 아니거나 익명이면 LFS 본문을 올리지 못한다`() {
        val (dir, _) = clone(project, member)
        File(dir, "big.bin").writeText("큰 파일 본문 ${System.nanoTime()} ".repeat(30))
        hg(dir, "--config", "extensions.lfs=", "--config", "lfs.track=size('>100')", "add", "big.bin")
        hg(dir, "--config", "extensions.lfs=", "--config", "lfs.track=size('>100')", "commit", "-m", "LFS 파일", "-d", "2026-11-03 10:00 +0900")
        val denied = hg(dir, *lfsConfig, "--config", "lfs.url=${lfsUrl(outsider)}", "push", url(project, outsider))
        assertFalse(denied.first == 0, "구성원이 아닌 사용자의 푸시가 성공했다: ${denied.second}")
        assertEquals(0, lfsObjects().size)
    }

    @Test
    fun `LFS 경로는 저장소 기준 경로 밖에서 처리하지 않는다`() {
        // 기준 경로 아래의 batch는 hg4j가 답하고(405나 400이어도 404는 아니다), 다른 프로젝트 이름으로는 404이다
        val ok = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:$port/${project.owner}/${project.name}/.git/info/lfs/objects/batch")).POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/vnd.git-lfs+json").build(), HttpResponse.BodyHandlers.ofString())
        assertTrue(ok.statusCode() != 404, "batch가 처리되지 않았다: ${ok.statusCode()} ${ok.body()}")
    }

    @Test
    fun `사이트의 LFS를 끄면 Mercurial LFS 경로도 원본의 lfs serve false처럼 처리하지 않는다`() {
        val batch = URI.create("http://localhost:$port/${project.owner}/${project.name}/.git/info/lfs/objects/batch")
        val objects = URI.create("http://localhost:$port/${project.owner}/${project.name}/.hg/lfs/objects/" + "a".repeat(64))
        fun post() = HttpClient.newHttpClient().send(HttpRequest.newBuilder(batch).POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/vnd.git-lfs+json").build(), HttpResponse.BodyHandlers.ofString())
        fun get() = HttpClient.newHttpClient().send(HttpRequest.newBuilder(objects).GET().build(), HttpResponse.BodyHandlers.ofString())
        assertTrue(post().statusCode() != 404, "켜 두면 batch를 처리한다")
        siteSettings.setLfsEnabled(false)
        assertEquals(404, post().statusCode())
        assertEquals(404, get().statusCode())
        // 와이어 프로토콜(복제와 푸시)은 사이트 LFS 설정과 무관하다
        assertEquals(0, clone(project).second.first)
        siteSettings.setLfsEnabled(true)
        assertTrue(post().statusCode() != 404, "다시 켜면 처리한다")
    }

    // --- 서명 커밋 필수 규칙(X-7 9c, D-88 3번) ---

    /** 일반 구성원(관리자 우회가 없다)과 그 이메일로 서명하는 키를 만든다. */
    private fun signer(): Pair<User, dev.yona.domain.gpgkey.TestGpgKey> {
        val plain = user("hs")
        projectUsers.save(ProjectUser(user = plain, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        val key = dev.yona.domain.gpgkey.TestGpgKey.generate(userId = "Signer <${plain.email}>")
        gpgKeyService.add(plain, "k", key.publicArmored)
        return plain to key
    }

    /** `.hgsigs`의 한 줄이다: `<노드> 0 <서명 base64>`. */
    private fun sigLine(key: dev.yona.domain.gpgkey.TestGpgKey, node: String): String {
        val binary = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(java.io.ByteArrayInputStream(key.sign("$node\n".toByteArray(Charsets.US_ASCII)).toByteArray())).readAllBytes()
        return "$node 0 ${java.util.Base64.getEncoder().encodeToString(binary)}"
    }

    @Test
    fun `서명을 요구하는 이름표로 서명 없는 커밋은 푸시하지 못한다`() {
        protect("main") { requireSignedCommits = true }
        HgTestSupport.hg(serverDir(project), "bookmark", "main")
        val (plain, _) = signer()
        val (dir, cloned) = clone(project, plain)
        assertEquals(0, cloned.first, cloned.second)
        hg(dir, "update", "main")
        File(dir, "u.txt").writeText("unsigned\n"); hg(dir, "add", "u.txt")
        assertEquals(0, hg(dir, "commit", "-m", "서명 없는 커밋", "-u", "Signer <${plain.email}>", "-d", "2026-11-04 10:00 +0900").first)
        val pushed = hg(dir, "push", "-B", "main", url(project, plain))
        assertFalse(pushed.first == 0, "서명 없는 푸시가 성공했다: ${pushed.second}")
        assertFalse("서명 없는 커밋" in HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n"))
    }

    @Test
    fun `서명한 커밋과 서명을 담은 hgsigs 커밋을 함께 올리면 허용한다`() {
        protect("main") { requireSignedCommits = true }
        HgTestSupport.hg(serverDir(project), "bookmark", "main")
        val (plain, key) = signer()
        val (dir, cloned) = clone(project, plain)
        assertEquals(0, cloned.first, cloned.second)
        hg(dir, "update", "main")
        File(dir, "s.txt").writeText("signed\n"); hg(dir, "add", "s.txt")
        assertEquals(0, hg(dir, "commit", "-m", "서명할 커밋", "-u", "Signer <${plain.email}>", "-d", "2026-11-04 10:00 +0900").first)
        val node = hg(dir, "log", "-r", ".", "--template", "{node}").second
        // 서명은 다음 커밋의 .hgsigs에 담는다. 이 커밋은 서명 파일만 바꾸므로 서명을 요구하지 않는다
        File(dir, ".hgsigs").writeText(sigLine(key, node) + "\n"); hg(dir, "add", ".hgsigs")
        assertEquals(0, hg(dir, "commit", "-m", "서명 추가", "-u", "Signer <${plain.email}>", "-d", "2026-11-05 10:00 +0900").first)
        // 서버 저장소의 첫 커밋(일반 푸시 이전부터 있던 것)은 새 커밋이 아니므로 검사하지 않는다
        val pushed = hg(dir, "push", "-B", "main", url(project, plain))
        assertEquals(0, pushed.first, pushed.second)
        assertTrue("서명할 커밋" in HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n"))
    }

    @Test
    fun `서명을 요구해도 프로젝트 관리자나 다른 이름표나 규칙이 없으면 막지 않는다`() {
        protect("main") { requireSignedCommits = true }
        val dir = cloneWithBookmark("feature")
        assertEquals(0, hg(dir, "push", "-B", "feature", url(project, member)).first)
    }
}
