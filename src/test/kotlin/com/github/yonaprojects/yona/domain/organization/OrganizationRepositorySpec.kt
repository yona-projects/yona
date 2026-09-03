package com.github.yonaprojects.yona.domain.organization

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

class OrganizationRepositorySpec @Autowired constructor(
    private val organizationRepository: OrganizationRepository
) : AbstractIntegrationTest() {

    init {
        describe("OrganizationRepository") {
            beforeEach {
                organizationRepository.deleteAll()
            }

            it("조직을 정상적으로 저장하고 조회할 수 있어야 한다") {
                // Given
                val organization = Organization(
                    name = "yona-team",
                    created = Instant.now(),
                    descr = "Yona 공식 개발 팀"
                )

                // When
                val savedOrg = organizationRepository.save(organization)

                // Then
                savedOrg.id shouldNotBe null
                
                val foundOrg = organizationRepository.findByName("yona-team").orElse(null)
                foundOrg shouldNotBe null
                foundOrg.name shouldBe "yona-team"
                foundOrg.descr shouldBe "Yona 공식 개발 팀"
            }
        }
    }
}
