package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

// TASK-0418 — `yona project fork owner/proj`를 목적지 지정 없이(로그인 사용자 == 이미 그 프로젝트의
// owner) 실행하면 실서버에서 500 + DB 오염이 발생하던 문제. mockk 기반 ProjectServiceImplSpec은
// 파일시스템 하드링크/트랜잭션 커밋 여부를 실제로 검증할 수 없어(항상 즉시 반환하는 스텁), 실제
// JPA 트랜잭션 + 실제 파일시스템으로 "정말 DB에 중복 행이 안 남는지"를 이 통합테스트로 고정한다.
class ProjectForkSelfIntegrationSpec @Autowired constructor(
    private val projectService: ProjectService,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    init {
        // AbstractIntegrationTest는 같은 forked 테스트 JVM 안의 스펙끼리 H2 인메모리 DB를 공유한다
        // — 이 스펙이 만든 User/Project를 정리하지 않으면 뒤에 실행되는 무관한 스펙이 project를
        // deleteAll()할 때 잔여 행과 충돌할 여지가 있다(실제로 다른 신규 스펙에서 이 패턴의 FK
        // 연쇄 실패를 확인했다 — ApiTokenAccountLevelAndLegacyAuthorizationIntegrationSpec 참고).
        afterSpec {
            projectRepository.findByOwnerAndName("self-fork-it-owner", "self-fork-it-repo")
                .ifPresent { projectRepository.delete(it) }
            userRepository.findByLoginId("self-fork-it-owner").ifPresent { userRepository.delete(it) }
            repositoryService.getRepository(
                Project(owner = "self-fork-it-owner", name = "self-fork-it-repo", vcs = "GIT")
            ).delete()
        }

        describe("ProjectServiceImpl.forkProject 자기 자신에게 fork (실제 DB + 실제 파일시스템)") {
            it("목적지 미지정 fork 시 forker가 이미 owner이면 IllegalArgumentException으로 거절되고 DB에 중복 행이 남지 않아야 한다") {
                roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }

                val owner = userRepository.save(
                    User(loginId = "self-fork-it-owner", name = "셀프포크", email = "self-fork-it-owner@example.com")
                )
                val project = projectRepository.save(
                    Project(owner = owner.loginId, name = "self-fork-it-repo", vcs = "GIT")
                )
                // 실제 bare 저장소가 디스크에 있어야 cloneHardLinkedRepository까지 도달하는
                // 실서버 재현 조건과 동일해진다.
                repositoryService.getRepository(project).create()

                shouldThrow<IllegalArgumentException> {
                    projectService.forkProject(project.id!!, owner.id!!, "", "")
                }

                val matches = projectRepository.findAll().filter {
                    it.owner == owner.loginId && it.name == "self-fork-it-repo"
                }
                matches.size shouldBe 1

                // 연쇄 장애 재현 방지 확인: owner+name 조합으로 정확히 1건만 조회돼야
                // ApiTokenAuthenticationFilter.authenticateScoped의 findByOwnerAndName()이
                // IncorrectResultSizeDataAccessException 없이 정상 동작한다.
                projectRepository.findByOwnerAndName(owner.loginId, "self-fork-it-repo").isPresent shouldBe true
            }
        }
    }
}
