package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * yona-wiki P3-02 14라운드 — 실서버(H2)에 같은 프로젝트로 PR을 만드는 동시 요청 10개를 쏴 재현한
 * 실제 버그의 회귀 테스트. PullRequestServiceImpl.createPullRequest()는
 * findFirstByToProjectOrderByNumberDesc()로 최댓값을 조회해 +1하는 read-modify-write인데,
 * (이번 라운드 전까지는) pull_request(to_project_id, number) UNIQUE 제약조차 없어서 동시 요청이
 * 전부 같은 번호로 "성공"해버리는 조용한 데이터 손상이 났다(issue처럼 500으로라도 막히지 않음).
 *
 * 수정: PullRequest.kt에 그 UNIQUE 제약을 신설해 경쟁을 "명확한 제약 위반 실패"로 바꾸고,
 * PullRequestController.createPullRequest()가 그 실패를 잡아 전체를 재시도한다. 이 스펙은 그
 * 컨트롤러의 재시도 루프를 그대로 재현해(서비스 직접 호출 + DataIntegrityViolationException 재시도)
 * 재시도까지 포함한 전체 흐름이 실제로 경쟁을 해소하는지 검증한다.
 *
 * IssueNumberingConcurrencyIntegrationSpec과 동일한 이유로 클래스 레벨 @Transactional을 붙이지
 * 않는다 — 스레드마다 독립된 트랜잭션이 열려야 실제 경쟁이 재현된다.
 */
class PullRequestNumberingConcurrencyIntegrationSpec @Autowired constructor(
    private val pullRequestService: PullRequestService,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val repositoryService: RepositoryService
) : AbstractIntegrationTest() {

    private lateinit var contributor: User
    private lateinit var toProject: Project
    private lateinit var fromProject: Project

    private fun createPullRequestWithRetry(): PullRequest {
        var attempt = 0
        while (true) {
            try {
                return pullRequestService.createPullRequest(
                    title = "동시성 PR",
                    body = null,
                    fromProjectId = fromProject.id!!,
                    toProjectId = toProject.id!!,
                    fromBranch = "refs/heads/does-not-need-to-exist",
                    toBranch = "refs/heads/master",
                    contributor = contributor
                )
            } catch (e: DataIntegrityViolationException) {
                attempt++
                if (attempt >= 10) throw e
            }
        }
    }

    init {
        describe("동시에 같은 프로젝트로 PR을 여러 개 생성하면(컨트롤러의 재시도 루프 포함)") {
            it("모두 성공하고 번호가 중복 없이 채번되어야 한다") {
                val uniqueSuffix = UUID.randomUUID().toString().take(8)
                contributor = userRepository.save(User(loginId = "pr-concur-$uniqueSuffix", name = "동시성기여자", email = "pr-concur-$uniqueSuffix@yona.io"))
                toProject = projectRepository.save(Project(name = "pr-concur-to-$uniqueSuffix", owner = contributor.loginId, vcs = "GIT", projectScope = ProjectScope.PUBLIC))
                fromProject = projectRepository.save(Project(name = "pr-concur-from-$uniqueSuffix", owner = contributor.loginId, vcs = "GIT", projectScope = ProjectScope.PUBLIC))
                repositoryService.getRepository(toProject).create()
                repositoryService.getRepository(fromProject).create()

                val threadCount = 10
                val executor = Executors.newFixedThreadPool(threadCount)
                try {
                    val tasks = (1..threadCount).map { Callable { runCatching { createPullRequestWithRetry() } } }
                    val results = executor.invokeAll(tasks, 60, TimeUnit.SECONDS).map { it.get() }

                    val failures = results.mapNotNull { it.exceptionOrNull() }
                    if (failures.isNotEmpty()) {
                        println("동시 PR 생성 실패 목록: ${failures.map { it.message }}")
                    }
                    failures.size shouldBe 0

                    val savedPrs = pullRequestRepository.findByToProject(toProject)
                    savedPrs.size shouldBe threadCount
                    val numbers = savedPrs.map { it.number }
                    numbers.toSet().size shouldBe threadCount
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    override suspend fun afterSpec(spec: io.kotest.core.spec.Spec) {
        val prs = pullRequestRepository.findByToProject(toProject)
        prs.forEach { pr -> pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr).forEach { pullRequestEventRepository.delete(it) } }
        prs.forEach { pullRequestRepository.delete(it) }
        try { repositoryService.getRepository(toProject).delete() } catch (e: Exception) {}
        try { repositoryService.getRepository(fromProject).delete() } catch (e: Exception) {}
        projectRepository.delete(toProject)
        projectRepository.delete(fromProject)
        userRepository.delete(contributor)
    }
}
