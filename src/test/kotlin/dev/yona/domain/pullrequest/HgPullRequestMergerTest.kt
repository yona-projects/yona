package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mercurial 풀 리퀘스트 병합(X-7 8b, 8c, `decisions.md` D-87)을 실제 `hg` 저장소로 검증한다: 비교(가져올 커밋, 충돌 판정), 숨은 참조(secret
 * 체인지셋과 예약 이름표), 병합 커밋, 병합한 뒤 공개. [PullRequestMergerTest]의 Git 시나리오와 같은 모양이다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-pull-request"])
class HgPullRequestMergerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var merger: PullRequestMerger
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "hm$n-$stamp", name = "병합자$n", email = "hm$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun dir(p: Project) = hgRepositories.directoryOf(p.owner!!, p.name!!)
    private fun hg(p: Project, vararg args: String) = HgTestSupport.hg(dir(p), *args)
    private fun node(p: Project, rev: String) = hg(p, "log", "-r", rev, "--template", "{node}")
    private fun phase(p: Project, node: String) = hg(p, "log", "-r", node, "--template", "{phase}").trim()

    /** 첫 커밋 하나와 이름표 `main`이 있는 원본과 그 포크를 만든다. */
    private fun originAndFork(): Pair<Project, Project> {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        val origin = projects.save(Project(owner = "hmo$n-$stamp", name = "hmp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        HgTestSupport.commit(dir(origin), "기반", mapOf("a.txt" to "line1\nline2\nline3\n"))
        HgTestSupport.bookmark(dir(origin), "main")
        return origin to forks.fork(origin, "hmf$n-$stamp", "hmp$n", ProjectScope.PUBLIC, user())
    }

    /** [project]에서 이름표 [bookmark]를 활성으로 두고 커밋을 더한다(이름표가 따라 움직인다). */
    private fun commit(project: Project, message: String, files: Map<String, String?>, bookmark: String = "main", date: String = "2026-11-02 10:00 +0900"): String {
        HgTestSupport.hg(dir(project), "update", "-C", bookmark)
        return HgTestSupport.commit(dir(project), message, files, date)
    }

    private fun pullRequest(origin: Project, fork: Project, fromBranch: String = "refs/heads/main"): PullRequest = pullRequests.save(
        PullRequest(
            title = "요청", body = "본문", toProject = origin, fromProject = fork, toBranch = "refs/heads/main", fromBranch = fromBranch, contributor = user(),
            created = Date(), updated = Date(), state = State.OPEN, number = 1
        )
    )

    @Test
    fun `보내는 브랜치에만 있는 커밋을 모으고 충돌하지 않으며 받는 저장소의 브랜치는 그대로이다`() {
        val (origin, fork) = originAndFork()
        commit(fork, "새 파일", mapOf("b.txt" to "b\n")); commit(fork, "또 하나", mapOf("c.txt" to "c\n"), date = "2026-11-03 10:00 +0900")
        val mainBefore = node(origin, "main")
        val attempt = merger.attempt(pullRequest(origin, fork))
        assertFalse(attempt.conflict)
        assertEquals(listOf("또 하나", "새 파일"), attempt.commits.map { it.shortMessage })
        assertEquals(mainBefore, attempt.leftParent); assertEquals(node(fork, "main"), attempt.rightParent)
        assertEquals(mainBefore, node(origin, "main"))
        assertEquals(listOf("refs/heads/main"), repositories.refNames(origin))
        assertTrue(HgTestSupport.verifies(dir(origin)))
    }

    @Test
    fun `같은 줄을 서로 다르게 고쳤으면 충돌이다`() {
        val (origin, fork) = originAndFork()
        commit(origin, "원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n"))
        commit(fork, "포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n"))
        val attempt = merger.attempt(pullRequest(origin, fork))
        assertTrue(attempt.conflict)
        assertEquals(listOf("포크 수정"), attempt.commits.map { it.shortMessage })
    }

    @Test
    fun `서로 다른 파일을 고쳤으면 충돌이 아니고 같은 파일의 다른 줄도 합쳐진다`() {
        val (origin, fork) = originAndFork()
        commit(origin, "원본 수정", mapOf("a.txt" to "ORIGIN\nline2\nline3\n"))
        commit(fork, "포크 수정", mapOf("a.txt" to "line1\nline2\nFORK\n", "b.txt" to "b\n"))
        assertFalse(merger.attempt(pullRequest(origin, fork)).conflict)
    }

    @Test
    fun `새 커밋이 없으면 가져올 커밋이 비어 있다`() {
        val (origin, fork) = originAndFork()
        assertEquals(emptyList(), merger.attempt(pullRequest(origin, fork)).commits)
    }

    @Test
    fun `업데이트는 머리와 병합 이름표를 만들고 병합 커밋은 부모가 둘이며 숨어 있다`() {
        val (origin, fork) = originAndFork()
        commit(fork, "새 파일", mapOf("b.txt" to "b\n"))
        commit(origin, "원본 파일", mapOf("o.txt" to "o\n"))
        val pr = pullRequest(origin, fork)
        val update = merger.update(pr)
        val merged = assertNotNull(update.mergeCommitId)

        assertEquals("yobi/pull/${pr.id}/head", merger.headRef(pr)); assertEquals("yobi/pull/${pr.id}/merged", merger.mergedRef(pr))
        val bookmarks = hg(origin, "bookmarks", "--template", "{bookmark}={node}\\n")
        assertTrue("${merger.headRef(pr)}=${update.attempt.rightParent}" in bookmarks, bookmarks)
        assertTrue("${merger.mergedRef(pr)}=$merged" in bookmarks, bookmarks)
        assertEquals(listOf(update.attempt.leftParent, update.attempt.rightParent), hg(origin, "log", "-r", merged, "--template", "{p1node} {p2node}").split(" "))
        assertTrue(hg(origin, "log", "-r", merged, "--template", "{desc}").startsWith("Merge branch 'main' of ${fork.owner}/${fork.name} into 'main'\n\nfrom pull-request 1\n\n* main:\n  새 파일"), "Mercurial은 커밋 메시지 끝의 줄바꿈을 지운다")
        assertEquals("secret", phase(origin, merged))
        // 받는 브랜치는 병합 전까지 그대로이고, 숨은 것은 목록에 나오지 않는다
        assertEquals(update.attempt.leftParent, node(origin, "main"))
        assertEquals(listOf("refs/heads/main"), repositories.refNames(origin))
        assertTrue(HgTestSupport.verifies(dir(origin)))
    }

    @Test
    fun `같은 부모로 다시 업데이트하면 같은 병합 커밋을 다시 쓴다`() {
        val (origin, fork) = originAndFork()
        commit(fork, "새 파일", mapOf("b.txt" to "b\n"))
        val pr = pullRequest(origin, fork)
        val first = merger.update(pr).mergeCommitId
        assertEquals(first, merger.update(pr).mergeCommitId)
    }

    @Test
    fun `충돌하면 업데이트는 병합 커밋을 만들지 않는다`() {
        val (origin, fork) = originAndFork()
        commit(origin, "원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n"))
        commit(fork, "포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n"))
        val update = merger.update(pullRequest(origin, fork))
        assertTrue(update.attempt.conflict); assertNull(update.mergeCommitId)
        assertEquals("", hg(origin, "bookmarks", "--template", "{bookmark}\\n").lines().filter { it.contains("merged") }.joinToString())
    }

    @Test
    fun `병합하면 받는 이름표가 병합 커밋으로 옮겨지고 공개로 올라 pull로 복제하는 사용자에게 보인다`() {
        val (origin, fork) = originAndFork()
        commit(fork, "새 파일", mapOf("b.txt" to "b\n"))
        val pr = pullRequest(origin, fork)
        val sender = MergePerson("받는 사람", "receiver@example.com")
        val result = merger.merge(pr, sender)!!
        val merged = assertNotNull(result.mergeCommitId)

        assertEquals(merged, node(origin, "main"))
        assertEquals(listOf(result.attempt.leftParent, result.attempt.rightParent), hg(origin, "log", "-r", merged, "--template", "{p1node} {p2node}").split(" "))
        assertEquals("받는 사람 <receiver@example.com>", hg(origin, "log", "-r", merged, "--template", "{author}"))
        assertEquals("public", phase(origin, merged)); assertEquals("public", phase(origin, result.attempt.rightParent))
        val cloned = File(dir(origin).parentFile, "clone-${System.nanoTime()}")
        HgTestSupport.hg(dir(origin).absoluteFile.parentFile, "clone", "--pull", dir(origin).absolutePath, cloned.absolutePath)
        assertTrue(merged in HgTestSupport.hg(cloned, "log", "--template", "{node}\\n"))
        assertTrue(HgTestSupport.verifies(dir(origin)))
    }

    @Test
    fun `충돌하면 병합하지 않고 null이며 받는 이름표는 그대로이다`() {
        val (origin, fork) = originAndFork()
        commit(origin, "원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n"))
        commit(fork, "포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n"))
        val before = node(origin, "main")
        assertNull(merger.merge(pullRequest(origin, fork), MergePerson("n", "e@example.com")))
        assertEquals(before, node(origin, "main"))
    }

    @Test
    fun `받는 브랜치가 이미 보내는 브랜치를 포함해도 Git처럼 병합 커밋을 만든다`() {
        val (origin, fork) = originAndFork()
        commit(origin, "원본이 앞선다", mapOf("o.txt" to "o\n"))
        // 포크는 원본의 옛 지점에서 멈춰 있다: 보내는 브랜치(포크의 main)는 받는 브랜치의 조상이다
        val pr = pullRequest(origin, fork)
        val attempt = merger.attempt(pr)
        assertFalse(attempt.conflict); assertEquals(emptyList(), attempt.commits)
    }

    @Test
    fun `병합 메시지의 기본 제목과 본문은 Git과 같은 글이다`() {
        val (origin, fork) = originAndFork()
        val pr = pullRequest(origin, fork)
        // Git과 같은 규칙: 받는 브랜치가 master가 아니면 "into"가 붙는다(Mercurial의 기본 이름표는 main이다)
        assertEquals("Merge branch 'main' of ${fork.owner}/${fork.name} into 'main'" to "from pull-request 1\n\n* main:\n  a\n  b\n", merger.defaults(pr, MergeMethod.MERGE, listOf("a", "b")))
    }

    // --- 스쿼시와 리베이스(8e, hg4j 1.1.2) ---

    private fun commitBy(project: Project, message: String, files: Map<String, String?>, user: String, date: String = "2026-11-02 10:00 +0900"): String {
        HgTestSupport.hg(dir(project), "update", "-C", "main")
        return HgTestSupport.commit(dir(project), message, files, date, user)
    }

    private fun parents(p: Project, rev: String) = hg(p, "log", "-r", rev, "--template", "{p1node} {p2node}").split(" ").filter { it.isNotBlank() && !it.all { c -> c == '0' } }

    @Test
    fun `스쿼시는 병합 결과 트리를 받는 머리 하나만 부모로 갖는 커밋 하나로 만들고 작성자는 요청자이다`() {
        val (origin, fork) = originAndFork()
        commit(origin, "원본 파일", mapOf("o.txt" to "o\n"))
        commit(fork, "새 파일", mapOf("b.txt" to "b\n")); commit(fork, "또 하나", mapOf("c.txt" to "c\n"), date = "2026-11-03 10:00 +0900")
        val pr = pullRequest(origin, fork)
        val result = merger.merge(pr, MergePerson("받는 사람", "receiver@example.com"), MergeMethod.SQUASH)!!
        val squash = assertNotNull(result.mergeCommitId)

        assertEquals(squash, node(origin, "main"))
        assertEquals(listOf(result.attempt.leftParent), parents(origin, squash))
        assertEquals("public", phase(origin, squash))
        // 병합 결과 트리: 원본과 포크의 파일이 모두 있다
        assertEquals(setOf("a.txt", "o.txt", "b.txt", "c.txt"), hg(origin, "files", "-r", squash, "-T", "{path}\\n").lines().filter { it.isNotBlank() }.toSet())
        assertEquals("${pr.contributor!!.name} <${pr.contributor!!.email}>", hg(origin, "log", "-r", squash, "--template", "{author}"))
        assertTrue(hg(origin, "log", "-r", squash, "--template", "{desc}").startsWith("요청 (#1)\n\n* 새 파일\n* 또 하나"), hg(origin, "log", "-r", squash, "--template", "{desc}"))
        assertTrue(HgTestSupport.verifies(dir(origin)))
    }

    @Test
    fun `스쿼시는 제목과 본문을 직접 정할 수 있다`() {
        val (origin, fork) = originAndFork()
        commit(fork, "새 파일", mapOf("b.txt" to "b\n"))
        val result = merger.merge(pullRequest(origin, fork), MergePerson("n", "e@example.com"), MergeMethod.SQUASH, "내 제목", "내 본문")!!
        assertTrue(hg(origin, "log", "-r", result.mergeCommitId!!, "--template", "{desc}").startsWith("내 제목\n\n내 본문"))
    }

    @Test
    fun `리베이스는 원래 커밋을 받는 머리 위에 순서대로 다시 적용하고 작성자와 메시지는 그대로이다`() {
        val (origin, fork) = originAndFork()
        val tip = commit(origin, "원본 파일", mapOf("o.txt" to "o\n"))
        val first = commitBy(fork, "새 파일", mapOf("b.txt" to "b\n"), "포크 작성자 <fork@example.com>")
        val second = commitBy(fork, "또 하나", mapOf("c.txt" to "c\n"), "포크 작성자 <fork@example.com>", "2026-11-03 10:00 +0900")
        val result = merger.merge(pullRequest(origin, fork), MergePerson("받는 사람", "receiver@example.com"), MergeMethod.REBASE)!!
        val newTip = assertNotNull(result.mergeCommitId)

        assertEquals(newTip, node(origin, "main"))
        // 선형 이력: 새 머리 ← 다시 적용한 첫 커밋 ← 받는 쪽의 옛 머리. 원래 체인지셋과는 다른 노드이다
        val newFirst = parents(origin, newTip).single()
        assertEquals(listOf(tip), parents(origin, newFirst))
        assertNotEquals(first, newFirst); assertNotEquals(second, newTip)
        assertEquals(listOf("또 하나", "새 파일"), listOf(newTip, newFirst).map { hg(origin, "log", "-r", it, "--template", "{desc|firstline}") })
        assertEquals(listOf("포크 작성자 <fork@example.com>", "포크 작성자 <fork@example.com>"), listOf(newTip, newFirst).map { hg(origin, "log", "-r", it, "--template", "{author}") })
        assertEquals("public", phase(origin, newTip))
        assertTrue(HgTestSupport.verifies(dir(origin)))
    }

    @Test
    fun `리베이스를 할 수 있으면 막는 이유가 없고 충돌하면 conflict이며 병합 커밋이 섞였으면 merge-commits이다`() {
        val (origin, fork) = originAndFork()
        commit(fork, "새 파일", mapOf("b.txt" to "b\n"))
        assertNull(merger.rebaseBlocked(pullRequest(origin, fork)))

        val (origin2, fork2) = originAndFork()
        commit(origin2, "원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n"))
        commit(fork2, "포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n"))
        assertEquals("conflict", merger.rebaseBlocked(pullRequest(origin2, fork2)))

        val (origin3, fork3) = originAndFork()
        // 포크에서 갈라진 두 줄기를 합친 병합 커밋을 만든다
        val base = node(fork3, "main")
        commit(fork3, "줄기 하나", mapOf("x.txt" to "x\n"))
        HgTestSupport.hg(dir(fork3), "update", "-C", base)
        HgTestSupport.commit(dir(fork3), "줄기 둘", mapOf("y.txt" to "y\n"), "2026-11-03 10:00 +0900")
        HgTestSupport.hg(dir(fork3), "merge", "-r", "main"); HgTestSupport.hg(dir(fork3), "commit", "-m", "병합", "-d", "2026-11-04 10:00 +0900")
        HgTestSupport.hg(dir(fork3), "bookmark", "-f", "-r", ".", "main")
        assertEquals("merge-commits", merger.rebaseBlocked(pullRequest(origin3, fork3)))
        assertNull(merger.merge(pullRequest(origin3, fork3), MergePerson("n", "e@example.com"), MergeMethod.REBASE))
    }

    @Test
    fun `스쿼시와 리베이스도 충돌하면 null이고 받는 이름표는 그대로이다`() {
        for (method in listOf(MergeMethod.SQUASH, MergeMethod.REBASE)) {
            val (origin, fork) = originAndFork()
            commit(origin, "원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n"))
            commit(fork, "포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n"))
            val before = node(origin, "main")
            assertNull(merger.merge(pullRequest(origin, fork), MergePerson("n", "e@example.com"), method), method.name)
            assertEquals(before, node(origin, "main"))
        }
    }
}
