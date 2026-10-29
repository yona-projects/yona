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
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.CommitBuilder
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트의 병합 방식(스쿼시, 리베이스)과 방식별 가능 여부를 실제 Git 저장소로 검증한다(로드맵 X-28, `decisions.md` D-41).
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-merge-methods"])
class PullRequestMergeMethodsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var merger: PullRequestMerger
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository

    private var sequence = 0

    private fun user(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mm$n", name = name ?: "병합자$n", email = "mm$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private val contributor by lazy { user("작성자") }

    private fun originAndFork(): Pair<Project, Project> {
        val n = ++sequence
        val origin = projects.save(Project(owner = "mmo$n", name = "mmp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        return origin to forks.fork(origin, "mmf$n", "mmp$n", ProjectScope.PUBLIC, user())
    }

    private fun pullRequest(origin: Project, fork: Project) = pullRequests.save(
        PullRequest(title = "요청 제목", body = "본문", toProject = origin, fromProject = fork, toBranch = "refs/heads/master", fromBranch = "refs/heads/master", contributor = contributor, created = Date(), updated = Date(), state = State.OPEN, number = 7)
    )

    private fun head(project: Project): String = repositories.openRepository(project).use { it.resolve("refs/heads/master").name }

    private fun commit(project: Project, id: String): RevCommit = repositories.openRepository(project).use { r -> RevWalk(r).use { it.parseCommit(r.resolve(id)) } }

    private fun text(project: Project, commit: RevCommit, path: String): String? = repositories.openRepository(project).use { r ->
        org.eclipse.jgit.treewalk.TreeWalk.forPath(r, path, commit.tree)?.let { String(r.open(it.getObjectId(0)).bytes) }
    }

    private val merger1 get() = PersonIdent("병합한 사람", "merger@example.com")

    // --- 스쿼시 ---

    @Test
    fun `스쿼시는 부모가 하나인 커밋 하나를 만들고 작성자는 풀 리퀘스트 작성자이다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b\n".toByteArray())), TestCommit("또 하나", mapOf("c.txt" to "c\n".toByteArray())))
        val before = head(origin)
        val pr = pullRequest(origin, fork)
        val update = merger.merge(pr, merger1, MergeMethod.SQUASH)
        assertNotNull(update)
        val squash = commit(origin, head(origin))
        assertEquals(update!!.mergeCommitId!!.name, squash.name)
        assertEquals(1, squash.parentCount); assertEquals(before, squash.getParent(0).name)
        assertEquals("b\n", text(origin, squash, "b.txt")); assertEquals("c\n", text(origin, squash, "c.txt"))
        assertEquals("작성자", squash.authorIdent.name); assertEquals(contributor.email, squash.authorIdent.emailAddress)
        assertEquals("병합한 사람", squash.committerIdent.name)
        assertTrue(squash.fullMessage.startsWith("요청 제목 (#7)\n"), squash.fullMessage)
        assertTrue(squash.fullMessage.contains("* 새 파일") && squash.fullMessage.contains("* 또 하나"), squash.fullMessage)
    }

    @Test
    fun `스쿼시의 제목과 본문을 직접 줄 수 있다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, fork, TestCommit("커밋", mapOf("b.txt" to "b\n".toByteArray())))
        merger.merge(pullRequest(origin, fork), merger1, MergeMethod.SQUASH, "내가 쓴 제목", "내가 쓴 본문")
        val message = commit(origin, head(origin)).fullMessage
        assertTrue(message.startsWith("내가 쓴 제목\n\n내가 쓴 본문"), message)
    }

    @Test
    fun `스쿼시도 충돌하면 아무것도 바꾸지 않는다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val before = head(origin)
        assertNull(merger.merge(pullRequest(origin, fork), merger1, MergeMethod.SQUASH))
        assertEquals(before, head(origin))
    }

    // --- 리베이스 ---

    @Test
    fun `리베이스는 원래 커밋을 받는 브랜치 위에 순서대로 다시 적용하고 작성자와 메시지를 지킨다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, origin, TestCommit("원본 새 파일", mapOf("o.txt" to "o\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, fork, TestCommit("포크 하나", mapOf("b.txt" to "b\n".toByteArray()), authorName = "원저자", authorEmail = "orig@example.com"), TestCommit("포크 둘", mapOf("c.txt" to "c\n".toByteArray())))
        val originHead = head(origin)
        val pr = pullRequest(origin, fork)
        assertNull(merger.rebaseBlocked(pr))
        val update = merger.merge(pr, merger1, MergeMethod.REBASE)
        assertNotNull(update)
        val tip = commit(origin, head(origin)); val first = commit(origin, tip.getParent(0).name)
        assertEquals("포크 둘", tip.shortMessage); assertEquals("포크 하나", first.shortMessage)
        assertEquals(originHead, first.getParent(0).name); assertEquals(1, tip.parentCount); assertEquals(1, first.parentCount)
        assertEquals("원저자", first.authorIdent.name); assertEquals("orig@example.com", first.authorIdent.emailAddress)
        assertEquals("병합한 사람", tip.committerIdent.name); assertEquals("병합한 사람", first.committerIdent.name)
        assertEquals("o\n", text(origin, tip, "o.txt")); assertEquals("b\n", text(origin, tip, "b.txt")); assertEquals("c\n", text(origin, tip, "c.txt"))
        assertEquals(tip.name, update!!.mergeCommitId!!.name, "병합 후 기준 커밋은 새 머리 커밋이다")
    }

    @Test
    fun `리베이스는 어느 커밋에서든 충돌하면 아무것도 바꾸지 않고 가능 여부는 충돌로 알린다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, fork, TestCommit("충돌 없는 커밋", mapOf("b.txt" to "b\n".toByteArray())), TestCommit("충돌하는 커밋", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val before = head(origin); val pr = pullRequest(origin, fork)
        assertEquals("conflict", merger.rebaseBlocked(pr))
        assertNull(merger.merge(pr, merger1, MergeMethod.REBASE))
        assertEquals(before, head(origin))
    }

    @Test
    fun `병합 커밋이 섞인 브랜치는 리베이스할 수 없다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, fork, TestCommit("하나", mapOf("b.txt" to "b\n".toByteArray())))
        // 포크 master의 머리 커밋을 한 부모로, 기반 커밋을 다른 부모로 하는 병합 커밋을 직접 만든다
        repositories.openRepository(fork).use { r ->
            val tip = RevWalk(r).use { it.parseCommit(r.resolve("refs/heads/master")) }
            val base = tip.getParent(0)
            val builder = CommitBuilder().apply { setTreeId(tip.tree.id); setParentIds(tip.id, base.id); author = merger1; setCommitter(merger1); message = "병합 커밋\n" }
            val id = r.newObjectInserter().use { ins -> ins.insert(builder).also { ins.flush() } }
            r.updateRef("refs/heads/master").apply { setNewObjectId(id); isForceUpdate = true }.update()
        }
        val pr = pullRequest(origin, fork)
        assertEquals("merge-commits", merger.rebaseBlocked(pr))
        assertNull(merger.merge(pr, merger1, MergeMethod.REBASE))
    }

    @Test
    fun `가능 여부를 확인해도 받는 저장소의 참조는 바뀌지 않는다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, fork, TestCommit("하나", mapOf("b.txt" to "b\n".toByteArray())))
        val before = head(origin)
        merger.rebaseBlocked(pullRequest(origin, fork))
        assertEquals(before, head(origin))
        repositories.openRepository(origin).use { assertEquals(emptyList(), it.refDatabase.getRefsByPrefix("refs/yobi/pull-check/")) }
    }

    @Test
    fun `병합 커밋 방식은 지금까지와 같고 제목과 본문을 직접 줄 수 있다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, fork, TestCommit("하나", mapOf("b.txt" to "b\n".toByteArray())))
        val update = merger.merge(pullRequest(origin, fork), merger1, MergeMethod.MERGE, "직접 쓴 제목", "직접 쓴 본문")
        val merge = commit(origin, update!!.mergeCommitId!!.name)
        assertEquals(2, merge.parentCount)
        assertTrue(merge.fullMessage.startsWith("직접 쓴 제목\n\n직접 쓴 본문"), merge.fullMessage)
        val plain = merger.merge(pullRequest(origin, fork).also { it.number = 8 }, merger1)
        assertTrue(plain == null || commit(origin, plain.mergeCommitId!!.name).fullMessage.startsWith("Merge branch"), "기본 제목은 v1.6과 같다")
        assertNotNull(Git(repositories.openRepository(origin)))
    }
}
