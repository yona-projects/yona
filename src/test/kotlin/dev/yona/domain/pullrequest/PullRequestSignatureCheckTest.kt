package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.gpgkey.GpgKeyService
import dev.yona.domain.gpgkey.SignedGitObjects
import dev.yona.domain.gpgkey.TestGpgKey
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ruleset
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 풀 리퀘스트의 Verified가 아닌 커밋 수 계산을 검증한다(X-4 7단계, `decisions.md` D-65). 서명 커밋을 요구하는 규칙 묶음이 없으면 저장소를 읽지 않고 0이다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PullRequestSignatureCheckTest : AbstractIntegrationTest() {

    @Autowired private lateinit var check: PullRequestSignatureCheck
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var commitRepository: PullRequestCommitRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var keyService: GpgKeyService
    @Autowired private lateinit var rulesetRepository: dev.yona.domain.project.RulesetRepository

    private var sequence = 0

    private fun project(signed: Boolean): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "psc$n", name = "psp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(p)
        if (signed) rulesetRepository.save(ruleset(p, "master", signedCommits = true, approvals = 0, noChangesRequested = false))
        return p
    }

    private fun pullRequest(p: Project, vararg commitIds: String): PullRequest {
        val pr = pullRequests.save(PullRequest(toProject = p, fromProject = p, toBranch = "master", fromBranch = "feature", state = State.OPEN, number = 1L, created = Date()))
        commitIds.forEach { commitRepository.save(PullRequestCommit(pullRequest = pr, commitId = it, state = PullRequestCommitState.CURRENT, created = Date())) }
        return pr
    }

    private fun user(email: String) = users.save(User(loginId = "ps${++sequence}", name = "ps$sequence", email = email, state = UserState.ACTIVE))

    @Test
    fun `서명 커밋을 요구하지 않으면 서명이 없어도 0이다`() {
        val p = project(signed = false)
        val c = SignedGitObjects.commit(repositories, p, null, "a@example.com")
        assertEquals(0, check.unverifiedCount(pullRequest(p, c)))
    }

    @Test
    fun `서명 커밋을 요구하면 Verified가 아닌 커밋만 센다`() {
        val p = project(signed = true)
        val owner = user("alice@example.com")
        val key = TestGpgKey.generate(userId = "Alice <alice@example.com>").also { keyService.add(owner, "k", it.publicArmored) }
        val good = SignedGitObjects.commit(repositories, p, key, "alice@example.com", "검증됨")
        val plain = SignedGitObjects.commit(repositories, p, null, "alice@example.com", "서명 없음", parent = good)
        val tampered = SignedGitObjects.commit(repositories, p, key, "alice@example.com", "변조", parent = plain, tamper = true)
        assertEquals(0, check.unverifiedCount(pullRequest(p, good)))
        val mixed = pullRequest(p, good, plain, tampered).also { it.number = 2L }
        assertEquals(2, check.unverifiedCount(mixed))
    }

    @Test
    fun `저장소에서 읽을 수 없는 커밋은 Verified가 아니다`() {
        val p = project(signed = true)
        SignedGitObjects.commit(repositories, p, null, "a@example.com")
        assertEquals(1, check.unverifiedCount(pullRequest(p, "0".repeat(40))))
    }
}
