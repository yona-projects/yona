package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.organization.Organization
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

import org.springframework.transaction.annotation.Transactional

@Transactional
class ProjectRepositorySpec @Autowired constructor(
    private val projectRepository: ProjectRepository,
    private val organizationRepository: OrganizationRepository
) : AbstractIntegrationTest() {

    init {
        describe("ProjectRepository") {
            beforeEach {
                projectRepository.deleteAll()
                organizationRepository.deleteAll()
            }

            it("프로젝트를 정상적으로 저장하고 조회할 수 있어야 한다") {
                // Given
                val organization = organizationRepository.save(
                    Organization(name = "test-org", created = Instant.now(), descr = "테스트 조직")
                )

                val project = Project(
                    name = "test-project",
                    owner = "test-user",
                    overview = "테스트 프로젝트입니다.",
                    createdDate = Instant.now(),
                    organization = organization,
                    projectScope = ProjectScope.PUBLIC
                )

                // When
                val savedProject = projectRepository.save(project)

                // Then
                savedProject.id shouldNotBe null
                
                val foundProject = projectRepository.findByOwnerAndName("test-user", "test-project").orElse(null)
                foundProject shouldNotBe null
                foundProject.name shouldBe "test-project"
                foundProject.organization?.name shouldBe "test-org"
                foundProject.projectScope shouldBe ProjectScope.PUBLIC
            }

            it("특정 소유자의 모든 프로젝트를 조회할 수 있어야 한다") {
                // Given
                projectRepository.save(
                    Project(name = "project-1", owner = "test-user", createdDate = Instant.now())
                )
                projectRepository.save(
                    Project(name = "project-2", owner = "test-user", createdDate = Instant.now())
                )
                projectRepository.save(
                    Project(name = "project-3", owner = "other-user", createdDate = Instant.now())
                )

                // When
                val projects = projectRepository.findByOwner("test-user")

                // Then
                projects.size shouldBe 2
                projects.map { it.name } shouldBe listOf("project-1", "project-2")
            }

            // yona Project.findByPreviousPlaceOf()/findByOwnerAndProjectName()의 폴백 대응 (P1-76).
            it("현재 owner/name으로 조회되면 예전 위치는 확인하지 않고 그대로 반환해야 한다") {
                projectRepository.save(
                    Project(
                        name = "current-name", owner = "current-owner", createdDate = Instant.now(),
                        previousOwnerLoginId = "old-owner", previousName = "old-name",
                        previousNameChangedTime = Instant.now()
                    )
                )

                val found = projectRepository.findByOwnerAndNameOrPreviousPlace("current-owner", "current-name").orElse(null)

                found shouldNotBe null
                found.name shouldBe "current-name"
            }

            it("현재 owner/name으로 못 찾으면 예전 위치(previousOwnerLoginId/previousName)로 폴백해야 한다") {
                projectRepository.save(
                    Project(
                        name = "new-name", owner = "new-owner", createdDate = Instant.now(),
                        previousOwnerLoginId = "old-owner", previousName = "old-name",
                        previousNameChangedTime = Instant.now()
                    )
                )

                val found = projectRepository.findByOwnerAndNameOrPreviousPlace("old-owner", "old-name").orElse(null)

                found shouldNotBe null
                found.name shouldBe "new-name"
                found.owner shouldBe "new-owner"
            }

            it("예전 위치로도 못 찾으면 빈 결과를 반환해야 한다") {
                val found = projectRepository.findByOwnerAndNameOrPreviousPlace("nobody", "nothing")

                found.isPresent shouldBe false
            }

            it("같은 예전 위치로 여러 번 이전됐으면 가장 최근 변경 건을 우선해야 한다") {
                projectRepository.save(
                    Project(
                        name = "stale-current-name", owner = "stale-current-owner", createdDate = Instant.now(),
                        previousOwnerLoginId = "old-owner", previousName = "old-name",
                        previousNameChangedTime = Instant.now().minusSeconds(3600)
                    )
                )
                projectRepository.save(
                    Project(
                        name = "fresh-current-name", owner = "fresh-current-owner", createdDate = Instant.now(),
                        previousOwnerLoginId = "old-owner", previousName = "old-name",
                        previousNameChangedTime = Instant.now()
                    )
                )

                val found = projectRepository.findByOwnerAndNameOrPreviousPlace("old-owner", "old-name").orElse(null)

                found shouldNotBe null
                found.name shouldBe "fresh-current-name"
            }
        }
    }
}
