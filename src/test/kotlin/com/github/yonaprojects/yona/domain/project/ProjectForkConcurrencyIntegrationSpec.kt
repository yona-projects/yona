package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * yona-wiki P3-02 14라운드 — 같은 프로젝트를 동시에 두 번 fork하면(더블클릭 등) 실서버(H2)에서
 * 500 Internal Server Error가 나던 실제 버그의 회귀 테스트.
 *
 * ProjectServiceImpl.forkProject()는 목적지가 이미 존재하는지 findByOwnerAndName()로 사전
 * 체크한 뒤에야 물리 bare 저장소를 하드링크로 복제하는데, 그 사이 시간차(TOCTOU)가 있다. 동시에
 * 같은 목적지로 fork 요청 여러 개가 들어오면 전부 사전 체크를 통과한 뒤 같은 물리 경로에
 * 하드링크를 시도해, 나중 요청들이 FileAlreadyExistsException(IOException)으로 실패한다 — 이
 * 예외가 잡히지 않아 500으로 그대로 노출됐다(실서버 동시요청 3개로 재현: 1건 성공/2건 500).
 *
 * IssueNumberingConcurrencyIntegrationSpec과 동일한 이유로 클래스 레벨 @Transactional을 붙이지
 * 않는다 — 스레드마다 독립된 트랜잭션이 열려야 실제 경쟁이 재현된다.
 */
class ProjectForkConcurrencyIntegrationSpec @Autowired constructor(
    private val projectService: ProjectService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService
) : AbstractIntegrationTest() {

    private lateinit var originalOwner: User
    private lateinit var forker: User
    private lateinit var original: Project

    init {
        describe("동시에 같은 프로젝트를 여러 번 fork하면") {
            it("정확히 하나만 성공하고 나머지는 IllegalArgumentException(이미 존재)으로 깔끔하게 실패해야 한다") {
                roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }

                val uniqueSuffix = UUID.randomUUID().toString().take(8)
                originalOwner = userRepository.save(
                    User(loginId = "fork-concur-owner-$uniqueSuffix", name = "원본소유자", email = "fork-concur-owner-$uniqueSuffix@yona.io")
                )
                forker = userRepository.save(
                    User(loginId = "fork-concur-forker-$uniqueSuffix", name = "동시성포커", email = "fork-concur-forker-$uniqueSuffix@yona.io")
                )
                original = projectRepository.save(
                    Project(owner = originalOwner.loginId, name = "fork-concur-repo-$uniqueSuffix", vcs = "GIT")
                )
                // 실제 bare 저장소가 디스크에 있어야 cloneHardLinkedRepository까지 도달하는
                // 실서버 재현 조건과 동일해진다.
                repositoryService.getRepository(original).create()

                val threadCount = 5
                val executor = Executors.newFixedThreadPool(threadCount)
                try {
                    val tasks = (1..threadCount).map {
                        Callable { runCatching { projectService.forkProject(original.id!!, forker.id!!, "", "") } }
                    }
                    val results = executor.invokeAll(tasks, 60, TimeUnit.SECONDS).map { it.get() }

                    val successes = results.filter { it.isSuccess }
                    val failures = results.mapNotNull { it.exceptionOrNull() }

                    successes.size shouldBe 1
                    failures.size shouldBe (threadCount - 1)
                    // 회귀 확인의 핵심: 실패가 전부 "이미 존재합니다" IllegalArgumentException이어야
                    // 한다 — FileAlreadyExistsException 등 원시 예외가 그대로 새면 안 된다.
                    failures.forEach { e ->
                        (e is IllegalArgumentException) shouldBe true
                        (e.message?.contains("이미 존재합니다") == true) shouldBe true
                    }

                    val forkedRows = projectRepository.findAll()
                        .filter { it.owner == forker.loginId && it.name == original.name }
                    forkedRows.size shouldBe 1
                } finally {
                    executor.shutdownNow()
                }
            }
        }
    }

    override suspend fun afterSpec(spec: io.kotest.core.spec.Spec) {
        projectRepository.findByOwnerAndName(forker.loginId, original.name)
            .ifPresent { fork ->
                projectUserRepository.findByProjectId(fork.id!!).forEach { projectUserRepository.delete(it) }
                try { repositoryService.getRepository(fork).delete() } catch (e: Exception) {}
                projectRepository.delete(fork)
            }
        try { repositoryService.getRepository(original).delete() } catch (e: Exception) {}
        projectRepository.delete(original)
        userRepository.delete(forker)
        userRepository.delete(originalOwner)
    }
}
