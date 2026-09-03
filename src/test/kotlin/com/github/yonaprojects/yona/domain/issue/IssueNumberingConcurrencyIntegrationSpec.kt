package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * yona-wiki P3-02 14라운드 — 실서버(H2)에 동시 요청 20개로 이슈를 만들어 재현한 실제 버그의
 * 회귀 테스트. IssueServiceImpl.createIssue()가 project.lastIssueNumber를 잠금 없이
 * read-modify-write 했을 때는 두 트랜잭션이 같은 번호를 읽어 issue(project_id, number) UNIQUE
 * 제약을 위반하며 대부분(3/20 성공, 17건 500)이 실패했다.
 *
 * 이 스펙은 클래스 레벨 @Transactional을 의도적으로 붙이지 않는다 — 붙이면 테스트 메서드 전체가
 * 하나의 트랜잭션/커넥션으로 묶여 서비스 계층의 실제 동시성(별도 트랜잭션 간 경쟁)을 재현할 수
 * 없기 때문이다(IssueServiceImpl은 자체적으로 @Transactional이라 스레드마다 독립된 트랜잭션이
 * 열린다). 대신 afterSpec에서 직접 정리한다.
 */
class IssueNumberingConcurrencyIntegrationSpec @Autowired constructor(
    private val issueService: IssueService,
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {

    private lateinit var author: User
    private lateinit var project: Project

    init {
        describe("동시에 같은 프로젝트에 이슈를 여러 개 생성하면") {
            it("모두 성공하고 번호가 중복 없이 채번되어야 한다") {
                author = userRepository.save(User(loginId = "concur-author", name = "동시성작성자", email = "concur-author@yona.io"))
                project = projectRepository.save(Project(name = "concur-project", owner = author.loginId))

                val threadCount = 20
                val executor = Executors.newFixedThreadPool(threadCount)
                try {
                    val tasks = (1..threadCount).map { i ->
                        Callable {
                            runCatching {
                                issueService.createIssue(
                                    issue = Issue(title = "동시성 이슈 $i", body = "본문", project = project),
                                    author = author,
                                    isDraft = true
                                )
                            }
                        }
                    }
                    val results = executor.invokeAll(tasks, 60, TimeUnit.SECONDS).map { it.get() }

                    val failures = results.mapNotNull { it.exceptionOrNull() }
                    withClueOnFailure(failures) {
                        failures.size shouldBe 0
                    }

                    val savedIssues = issueRepository.findByProject(project)
                    savedIssues.size shouldBe threadCount
                    val numbers = savedIssues.map { it.number }
                    numbers.toSet().size shouldBe threadCount
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    override suspend fun afterSpec(spec: io.kotest.core.spec.Spec) {
        issueRepository.findByProject(project).forEach { issueRepository.delete(it) }
        projectRepository.delete(project)
        userRepository.delete(author)
    }

    // 실패 목록을 그대로 shouldBe 0의 클루로 붙여, 실패 시 어떤 예외들이 있었는지 바로 보이게 한다.
    private fun withClueOnFailure(failures: List<Throwable>, block: () -> Unit) {
        if (failures.isNotEmpty()) {
            println("동시 이슈 생성 실패 목록: ${failures.map { it.message }}")
        }
        block()
    }
}
