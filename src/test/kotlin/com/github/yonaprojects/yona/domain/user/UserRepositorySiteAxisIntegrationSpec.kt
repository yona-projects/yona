package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest

// v1.6 User.findUsers: SITE_ADMIN 탭은 SiteAdmin 집합, GUEST 탭은 isGuest, 나머지는 state. 검색 대상은 잠금·삭제가 아닌 모든 사용자다.
class UserRepositorySiteAxisIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository
) : AbstractIntegrationTest() {
    init {
        describe("사이트 관리자·게스트·잠금 축 조회") {
            val admin = userRepository.save(User(loginId = "ax-admin", name = "ax관리자", email = "ax-admin@yona.io", isSiteAdmin = true))
            val guest = userRepository.save(User(loginId = "ax-guest", name = "ax게스트", email = "ax-guest@yona.io", isGuest = true))
            val normal = userRepository.save(User(loginId = "ax-normal", name = "ax일반", email = "ax-normal@yona.io"))
            val locked = userRepository.save(User(loginId = "ax-locked", name = "ax잠금", email = "ax-locked@yona.io", state = UserState.LOCKED))
            val page = PageRequest.of(0, 50)

            fun tab(state: UserState) = userRepository.findUsersForAdmin(state, "%ax-%", page).content.map { it.loginId }

            it("관리자 탭은 isSiteAdmin, 게스트 탭은 isGuest, 활성 탭은 잠기지 않은 모든 사용자, 잠금 탭은 LOCKED만 보여 준다") {
                tab(UserState.SITE_ADMIN) shouldBe listOf("ax-admin")
                tab(UserState.GUEST) shouldBe listOf("ax-guest")
                tab(UserState.ACTIVE).sorted() shouldBe listOf("ax-admin", "ax-guest", "ax-normal")
                tab(UserState.LOCKED) shouldBe listOf("ax-locked")
                userRepository.countUsersForAdmin(UserState.SITE_ADMIN, "%ax-%") shouldBe 1
            }

            it("멘션·담당자 검색은 관리자와 게스트를 포함하고 잠긴 사용자는 제외한다") {
                val found = userRepository.searchUsers("%ax-%", page).content.map { it.loginId }
                found shouldContain "ax-admin"
                found shouldContain "ax-guest"
                found shouldContain "ax-normal"
                found shouldNotContain "ax-locked"
                userRepository.countSearchUsers("%ax-%") shouldBe 3
            }
        }
    }
}
