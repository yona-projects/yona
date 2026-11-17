package dev.yona.web

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ApiTokenSupport
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 요청 하나가 DB에 보내는 질의 수의 상한을 검증한다(`decisions.md` A-628). 질의 한 번이 약 1ms인 DB(Cubrid)에서는 질의 수가 곧 지연이어서,
 * 같은 행을 요청 안에서 되풀이해 읽거나(프로젝트와 규칙을 여덟 번씩) 행마다 따로 읽는(N+1) 질의를 막는다.
 *
 * 두 가지를 본다. (1) 결과 행이 늘어도 질의 수가 늘지 않는다(N+1이 없다). (2) 질의 수가 상한 이하이다. 상한은 지금 구현의 값에 여유를 조금 둔 것이라
 * 코드를 바꾸다 질의가 늘면 여기서 알려 준다. 트랜잭션을 걸지 않는다(운영과 같게 요청이 서로 다른 세션을 쓰는 상태를 재현하려는 것이다).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["spring.jpa.properties.hibernate.generate_statistics=true", "spring.jpa.properties.hibernate.session_factory.statement_inspector=dev.yona.web.QueryCountTest\$Recorder"])
class QueryCountTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var environment: org.springframework.core.env.Environment
    @Autowired private lateinit var emf: EntityManagerFactory
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var apiTokens: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var deletion: ProjectDeletionService

    private var sequence = 0
    private val createdProjects = mutableListOf<Project>()
    private val createdUsers = mutableListOf<User>()

    @AfterEach
    fun cleanUp() {
        apiTokens.deleteAll()
        createdProjects.forEach { runCatching { deletion.delete(projects.findById(it.id!!).get()) } }
        createdUsers.forEach { runCatching { users.deleteById(it.id!!) } }
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "qc$n", name = "qc$n", email = "qc$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
            .also { createdUsers += it }
    }

    /** 프로젝트와 이슈 [count]개를 만든다. 이슈마다 작성자(서로 다른 사용자 둘을 번갈아)와 라벨 둘이 있고 제목에 `검색어`가 들어 있다. */
    private fun projectWithIssues(count: Int): Pair<Project, User> {
        val n = ++sequence
        val owner = newUser(); val other = newUser()
        val project = projects.save(Project(owner = "qco$n", name = "qcp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC)).also { createdProjects += it }
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        val category = categories.save(IssueLabelCategory(project = project, name = "분류$n"))
        val tags = (1..2).map { labels.save(IssueLabel(project = project, category = category, name = "라벨$it", color = "#fff")) }
        for (i in 1..count) {
            val author = if (i % 2 == 0) owner else other
            issues.save(
                Issue(
                    project = project, number = i.toLong(), title = "검색어 이슈 $i", body = "본문 $i", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                    labels = tags.toMutableSet(), numOfComments = 0, createdDate = Date(1_700_000_000_000L + i), updatedDate = Date(1_700_000_000_000L + i)
                )
            )
        }
        return project to owner
    }

    private var lastBody = ""

    private fun statements(): Long = emf.unwrap(SessionFactory::class.java).statistics.prepareStatementCount

    /** 요청을 한 번 보내 예열한 뒤 한 번 더 보내 그때 나간 질의 수를 센다. */
    private fun queries(path: String, token: String? = null, params: Map<String, String> = emptyMap()): Long {
        fun call() = mockMvc.perform(get(path).apply { token?.let { header("Authorization", "token $it") }; params.forEach { (k, v) -> param(k, v) } }).andExpect(status().isOk).andReturn().response.contentAsString
        lastBody = call()
        val before = statements(); val mark = Recorder.log.size
        call()
        dump(mark)
        return statements() - before
    }

    /** 나간 질의를 호출한 앱 코드 위치와 함께 기록한다. 상한을 넘으면 어디서 나갔는지 보려는 진단용이다. */
    class Recorder : org.hibernate.resource.jdbc.spi.StatementInspector {
        override fun inspect(sql: String): String {
            val callers = Throwable().stackTrace.filter { it.className.startsWith("dev.yona.") && !it.className.contains("$$") && !it.className.contains("QueryCountTest") }
                .take(2).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            log.add(sql.replace(Regex("\\s+"), " ").take(70) to callers)
            return sql
        }
        companion object { val log = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>() }
    }

    /** 직전 요청이 보낸 질의를 호출 위치별로 묶은 설명이다. 단언이 실패하면 메시지에 붙여 어디서 나갔는지 보여 준다. */
    private var lastDump = ""

    private fun dump(from: Int) {
        lastDump = Recorder.log.drop(from).groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            .joinToString("\n") { "  ${it.value}회 ${it.key.first} <- ${it.key.second}" }
    }

    private fun token(user: User): String = "qc-token-${user.id}".also { ApiTokenSupport.grant(apiTokens, user, it) }

    @Test
    fun `Cubrid로 돌릴 때 접속 주소에 준비된 문장 캐시 옵션이 실제로 들어 있다`() {
        org.junit.jupiter.api.Assumptions.assumeTrue(dev.yona.support.TestDatabase.current == dev.yona.support.TestDatabase.CUBRID, "Cubrid로 돌릴 때만 본다")
        val url = environment.getProperty("spring.datasource.url").orEmpty()
        assertTrue("usePreparedStmtCache=true" in url, "A-629의 옵션이 접속 주소에 없다(시험의 동적 속성이 우선해 덮었을 수 있다): $url")
    }

    @Test
    fun `이슈 목록 화면은 이슈가 늘어도 질의가 늘지 않고 상한 이하이다`() {
        val (small, _) = projectWithIssues(3); val (large, _) = projectWithIssues(12)
        val few = queries("/${small.owner}/${small.name}/issues"); val many = queries("/${large.owner}/${large.name}/issues")
        println("QUERYCOUNT 이슈 목록 3건=$few 12건=$many")
        assertEquals(few, many, "이슈마다 따로 읽는 질의(N+1)가 있다: 3건 $few, 12건 $many")
        assertTrue(many <= LIST_BUDGET, "이슈 목록 화면의 질의가 $many 개이다(상한 $LIST_BUDGET)\n$lastDump")
    }

    @Test
    fun `이슈 상세 화면의 질의는 상한 이하이다`() {
        val (project, _) = projectWithIssues(5)
        val count = queries("/${project.owner}/${project.name}/issue/3")
        println("QUERYCOUNT 이슈 상세=$count")
        assertTrue(count <= DETAIL_BUDGET, "이슈 상세 화면의 질의가 $count 개이다(상한 $DETAIL_BUDGET)\n$lastDump")
    }

    @Test
    fun `이슈 한 건 조회 API의 질의는 상한 이하이다`() {
        val (project, owner) = projectWithIssues(5)
        val count = queries("/-_-api/v1/owners/${project.owner}/projects/${project.name}/issues/3", token(owner))
        println("QUERYCOUNT 이슈 단건 API=$count")
        assertTrue(count <= API_ISSUE_BUDGET, "이슈 단건 조회의 질의가 $count 개이다(상한 $API_ISSUE_BUDGET)\n$lastDump")
    }

    @Test
    fun `검색 API는 결과 행이 늘어도 질의가 늘지 않고 상한 이하이다`() {
        val (small, smallOwner) = projectWithIssues(3); val (large, largeOwner) = projectWithIssues(12)
        fun search(p: Project, u: User) = queries("/-_-api/v1/owners/${p.owner}/projects/${p.name}/search", token(u), mapOf("keyword" to "검색어", "searchType" to "issue"))
        val few = search(small, smallOwner); val many = search(large, largeOwner)
        println("QUERYCOUNT 검색 3건=$few 12건=$many 응답=${lastBody.take(160)}")
        assertEquals(few, many, "결과 행마다 따로 읽는 질의(N+1)가 있다: 3건 $few, 12건 $many")
        assertTrue(many <= SEARCH_BUDGET, "검색의 질의가 $many 개이다(상한 $SEARCH_BUDGET)\n$lastDump")
    }

    private companion object {
        // 개선 전(A-628): 이슈 목록 57(12건), 상세 38, 단건 API 18, 검색 31(12건). 아래는 개선 뒤의 값에 1개의 여유를 둔 것이다
        const val LIST_BUDGET = 38
        const val DETAIL_BUDGET = 34
        const val API_ISSUE_BUDGET = 19
        const val SEARCH_BUDGET = 22
    }
}
