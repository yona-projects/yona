package dev.yona.domain.pullrequest

import dev.yona.domain.comment.CodeCommentThread
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mercurial 풀 리퀘스트의 보내는 브랜치 지우기와 되살리기, 코멘트 스레드의 어긋남 판정(X-7 8d)을 실제 `hg` 저장소로 검증한다. Git과 같은 서비스
 * ([PullRequestBranchService], [PullRequestChangesService])가 종류와 무관한 계약으로 저장소를 읽는지 확인한다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-pull-request"])
class HgPullRequestServicesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var branches: PullRequestBranchService
    @Autowired private lateinit var changes: PullRequestChangesService
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
        return users.save(User(loginId = "hs$n-$stamp", name = "서비스$n", email = "hs$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun dir(p: Project) = hgRepositories.directoryOf(p.owner!!, p.name!!)

    /** 기준 커밋과 이름표 `main`이 있는 원본과 그 포크를 만든다. */
    private fun originAndFork(): Pair<Project, Project> {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        val origin = projects.save(Project(owner = "hso$n-$stamp", name = "hsp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        HgTestSupport.commit(dir(origin), "기반", mapOf("a.txt" to "line1\nline2\n"))
        HgTestSupport.bookmark(dir(origin), "main")
        return origin to forks.fork(origin, "hsf$n-$stamp", "hsp$n", ProjectScope.PUBLIC, user())
    }

    /** 포크에서 [bookmark] 이름표로 커밋을 더하고 그 노드를 돌려준다. */
    private fun forkCommit(fork: Project, bookmark: String, message: String, files: Map<String, String?>): String {
        fun hg(vararg args: String) = HgTestSupport.hg(dir(fork), *args)
        if (bookmark !in hg("bookmarks", "--template", "{bookmark}\n").lines()) hg("bookmark", "-r", "main", bookmark)
        hg("update", "-C", bookmark)
        return HgTestSupport.commit(dir(fork), message, files, "2026-11-02 10:00 +0900")
    }

    private fun pullRequest(origin: Project, fork: Project, from: String): PullRequest = pullRequests.save(
        PullRequest(
            title = "요청", body = "본문", toProject = origin, fromProject = fork, toBranch = "refs/heads/main", fromBranch = "refs/heads/$from", contributor = user(),
            created = Date(), updated = Date(), state = State.OPEN, number = 1
        )
    )

    /** 포크의 [node]까지를 받는 저장소로 가져와 공개한다(병합된 것과 같은 상태). */
    private fun merged(origin: Project, fork: Project, node: String) {
        HgTestSupport.hg(dir(origin), "pull", "-r", node, dir(fork).absolutePath)
        HgTestSupport.hg(dir(origin), "phase", "--public", "-r", node)
    }

    @Test
    fun `보내는 브랜치는 병합돼 받는 저장소가 머리 커밋을 가지면 지울 수 있다`() {
        val (origin, fork) = originAndFork()
        val head = forkCommit(fork, "feature", "기능", mapOf("f.txt" to "f\n"))
        val pr = pullRequest(origin, fork, "feature")
        assertFalse(branches.canDelete(pr), "받는 저장소에 아직 없는 커밋이 있는 브랜치는 지울 수 없다")
        merged(origin, fork, head)
        assertTrue(branches.canDelete(pr))
        assertEquals(head, branches.delete(pr))
        assertFalse("refs/heads/feature" in repositories.refNames(fork))
        assertTrue(HgTestSupport.verifies(dir(fork)))
    }

    @Test
    fun `기본 이름표나 없는 브랜치는 지울 수 없다`() {
        val (origin, fork) = originAndFork()
        val head = forkCommit(fork, "main", "기능", mapOf("f.txt" to "f\n"))
        merged(origin, fork, head)
        assertFalse(branches.canDelete(pullRequest(origin, fork, "main")), "기본 브랜치(HEAD)는 지울 수 없다")
        assertFalse(branches.canDelete(pullRequest(origin, fork, "ghost")))
        assertNull(branches.delete(pullRequest(origin, fork, "ghost")))
    }

    @Test
    fun `지운 브랜치는 지울 때 남긴 커밋에서 같은 이름으로 되살린다`() {
        val (origin, fork) = originAndFork()
        val head = forkCommit(fork, "feature", "기능", mapOf("f.txt" to "f\n"))
        val pr = pullRequest(origin, fork, "feature")
        merged(origin, fork, head)
        assertFalse(branches.canRestore(pr))
        pr.lastCommitId = branches.delete(pr)
        assertTrue(branches.canRestore(pr))
        branches.restore(pr)
        assertTrue("refs/heads/feature" in repositories.refNames(fork))
        assertEquals(head, HgTestSupport.hg(dir(fork), "log", "-r", "feature", "--template", "{node}"))
        assertFalse(branches.canRestore(pr))
    }

    @Test
    fun `코멘트 스레드는 파일이 바뀌지 않았으면 최신이고 바뀌었으면 어긋난 것이다`() {
        val (origin, fork) = originAndFork()
        val base = HgTestSupport.hg(dir(origin), "log", "-r", "main", "--template", "{node}")
        val first = forkCommit(fork, "feature", "다른 파일", mapOf("b.txt" to "b\n"))
        val second = forkCommit(fork, "feature", "같은 파일 수정", mapOf("a.txt" to "line1\nCHANGED\n"))
        merged(origin, fork, second)
        val pr = pullRequest(origin, fork, "feature").also { it.mergedCommitIdFrom = base; it.mergedCommitIdTo = second }
        fun thread(path: String, prev: String, commit: String) = CodeCommentThread(prevCommitId = prev, commitId = commit, path = path, startLine = 1).also { it.pullRequest = pr }
        // 기준 쌍(base, second)에 단 스레드는 그대로 최신이다. a.txt는 base와 first 사이에서는 같지만 first와 second 사이에서 바뀌었다
        assertFalse(changes.isOutdated(thread("a.txt", base, second)))
        assertFalse(changes.isOutdated(thread("b.txt", first, second)))
        assertTrue(changes.isOutdated(thread("a.txt", first, second)))
        // 없는 커밋이 가리키면 어긋난 것으로 본다
        assertTrue(changes.isOutdated(thread("a.txt", base, "0".repeat(39) + "1")))
    }
}
