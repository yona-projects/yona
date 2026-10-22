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
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.revwalk.RevWalk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 병합기(`PullRequestMerger`)를 실제 Git 저장소로 검증한다.
 *
 * 원본: `app/models/PullRequest.java`의 `Merger`, `attemptMerge`, `updateMerge`, `merge`, `makeMergeCommitMessage`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PullRequestMergerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var merger: PullRequestMerger
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mg$n", name = "병합자$n", email = "mg$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    /** 첫 커밋 하나가 있는 원본과 그 포크를 만든다. */
    private fun originAndFork(): Pair<Project, Project> {
        val n = ++sequence
        val origin = projects.save(Project(owner = "mgo$n", name = "mgp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        return origin to forks.fork(origin, "mgf$n", "mgp$n", ProjectScope.PUBLIC, user())
    }

    private fun pullRequest(origin: Project, fork: Project, fromBranch: String = "refs/heads/master"): PullRequest = pullRequests.save(
        PullRequest(
            title = "요청", body = "본문", toProject = origin, fromProject = fork, toBranch = "refs/heads/master", fromBranch = fromBranch, contributor = user(),
            created = Date(), updated = Date(), state = State.OPEN, number = 1
        )
    )

    @Test
    fun `보내는 브랜치에만 있는 커밋을 모으고 충돌하지 않는다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b\n".toByteArray())), TestCommit("또 하나", mapOf("c.txt" to "c\n".toByteArray())))
        val attempt = merger.attempt(pullRequest(origin, fork))
        assertFalse(attempt.conflict)
        assertEquals(listOf("또 하나", "새 파일"), attempt.commits.map { it.shortMessage })
        repositories.openRepository(origin).use { assertEquals(emptyList(), it.refDatabase.getRefsByPrefix("refs/yobi/pull-check/")) }
    }

    @Test
    fun `같은 줄을 서로 다르게 고쳤으면 충돌이다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val attempt = merger.attempt(pullRequest(origin, fork))
        assertTrue(attempt.conflict)
        assertEquals(listOf("포크 수정"), attempt.commits.map { it.shortMessage })
    }

    @Test
    fun `새 커밋이 없으면 가져올 커밋이 비어 있다`() {
        val (origin, fork) = originAndFork()
        assertEquals(emptyList(), merger.attempt(pullRequest(origin, fork)).commits)
    }

    @Test
    fun `업데이트는 머리와 병합 참조를 만들고 병합 커밋은 부모가 둘이다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b\n".toByteArray())))
        val pr = pullRequest(origin, fork)
        val update = merger.update(pr)
        val merged = assertNotNull(update.mergeCommitId)
        repositories.openRepository(origin).use { repository ->
            assertEquals(update.attempt.rightParent, repository.resolve(merger.headRef(pr)))
            assertEquals(merged, repository.resolve(merger.mergedRef(pr)))
            RevWalk(repository).use { walk ->
                val commit = walk.parseCommit(merged)
                assertEquals(2, commit.parentCount)
                assertEquals(update.attempt.leftParent, commit.getParent(0).id); assertEquals(update.attempt.rightParent, commit.getParent(1).id)
                assertEquals("Yona", commit.authorIdent.name)
                assertTrue(commit.fullMessage.startsWith("Merge branch 'master' of ${fork.owner}/${fork.name}\n\nfrom pull-request 1\n\n* master:\n  새 파일\n"), commit.fullMessage)
            }
            assertNotEquals(merged, repository.resolve("refs/heads/master"))
        }
    }

    @Test
    fun `충돌하면 병합 참조를 만들지 않는다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val pr = pullRequest(origin, fork)
        val update = merger.update(pr)
        assertTrue(update.attempt.conflict); assertNull(update.mergeCommitId)
        repositories.openRepository(origin).use { assertNull(it.resolve(merger.mergedRef(pr))) }
    }

    @Test
    fun `받아들이면 받는 브랜치가 병합 커밋을 가리키고 작성자는 보낸 사람이다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, fork, TestCommit("새 파일", mapOf("b.txt" to "b\n".toByteArray())))
        val pr = pullRequest(origin, fork)
        pr.reviewers.add(user())
        val sender = PersonIdent("받는 사람", "receiver@example.com")
        val result = merger.merge(pr, sender)!!
        repositories.openRepository(origin).use { repository ->
            assertEquals(result.mergeCommitId, repository.resolve("refs/heads/master"))
            RevWalk(repository).use { walk ->
                val commit = walk.parseCommit(result.mergeCommitId)
                assertEquals("받는 사람", commit.authorIdent.name); assertEquals("receiver@example.com", commit.committerIdent.emailAddress)
                assertTrue(commit.fullMessage.contains("Reviewed-by: 병합자"), commit.fullMessage)
            }
            Git(repository).use { assertEquals(3, it.log().call().count()) }
        }
    }

    @Test
    fun `충돌하면 받아들여도 받는 브랜치는 그대로이다`() {
        val (origin, fork) = originAndFork()
        GitTestSupport.appendCommits(repositories, origin, TestCommit("원본 수정", mapOf("a.txt" to "line1\nORIGIN\nline3\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, fork, TestCommit("포크 수정", mapOf("a.txt" to "line1\nFORK\nline3\n".toByteArray())))
        val before = repositories.openRepository(origin).use { it.resolve("refs/heads/master") }
        assertNull(merger.merge(pullRequest(origin, fork), PersonIdent("n", "e@example.com")))
        assertEquals(before, repositories.openRepository(origin).use { it.resolve("refs/heads/master") })
    }

    @Test
    fun `받는 브랜치가 master가 아니면 메시지에 into가 붙고 같은 프로젝트면 of가 붙지 않는다`() {
        val (origin, _) = originAndFork()
        val pr = PullRequest(toProject = origin, fromProject = origin, toBranch = "refs/heads/dev", fromBranch = "refs/heads/topic", number = 7)
        assertEquals("Merge branch 'topic' into 'dev'\n\nfrom pull-request 7\n\n* topic:\n\n", merger.message(pr, emptyList()))
    }
}
