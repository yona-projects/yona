package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.site.SiteService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.time.Instant

class UserRepositorySpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val siteService: SiteService
) : AbstractIntegrationTest() {

    init {
        describe("UserRepository") {
            beforeEach {
                userRepository.deleteAll()
            }

            it("사용자를 정상적으로 저장하고 조회할 수 있어야 한다") {
                // Given
                val user = User(
                    name = "홍길동",
                    loginId = "gildong",
                    email = "gildong@example.com",
                    createdDate = Instant.now()
                )

                // When
                val savedUser = userRepository.save(user)

                // Then
                savedUser.id shouldNotBe null
                
                val foundUser = userRepository.findByLoginId("gildong").orElse(null)
                foundUser shouldNotBe null
                foundUser.name shouldBe "홍길동"
                foundUser.email shouldBe "gildong@example.com"
            }

            it("[Test-14-1] UserState가 SITE_ADMIN인 사용자도 searchUsers 검색 결과에 정상 노출되어야 한다") {
                val adminUser = User(
                    name = "임시관리자",
                    loginId = "tempadmin",
                    email = "tempadmin@yona.io",
                    state = UserState.SITE_ADMIN,
                    createdDate = Instant.now()
                )
                userRepository.save(adminUser)

                val result = userRepository.searchUsers("%임시%", PageRequest.of(0, 10))
                result.content.size shouldBe 1
                result.content[0].loginId shouldBe "tempadmin"
            }

            it("[Test-14-2] UserState가 SITE_ADMIN인 사용자가 아바타가 없는 경우 getNoAvatarUsers 목록에 정상 노출되어야 한다") {
                val adminUser = User(
                    name = "임시관리자",
                    loginId = "tempadmin",
                    email = "tempadmin@yona.io",
                    state = UserState.SITE_ADMIN,
                    createdDate = Instant.now()
                )
                userRepository.save(adminUser)

                val list = siteService.getNoAvatarUsers()
                val found = list.any { it["loginId"] == "tempadmin" }
                found shouldBe true
            }
        }
    }
}
