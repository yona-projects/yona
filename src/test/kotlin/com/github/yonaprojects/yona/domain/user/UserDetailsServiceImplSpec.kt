package com.github.yonaprojects.yona.domain.user

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.util.Optional

// yona models/User.java:563-569 isSiteManager() 대응 (P1-119). SiteAdmin 테이블 소속 여부만으로
// 판단하는 yona와 달리, loginId=="admin"이면 state와 무관하게 ROLE_SITE_ADMIN/ROLE_ADMIN을 부여하는
// 분기는 yona에 없는 yona 자체 버그(권한 상승 소지)였다.
class UserDetailsServiceImplSpec : DescribeSpec({
    val userRepository = mockk<UserRepository>()
    val service = UserDetailsServiceImpl(userRepository)

    describe("UserDetailsServiceImpl.loadUserByUsername") {
        it("loginId가 admin이어도 state가 SITE_ADMIN이 아니면 ROLE_SITE_ADMIN/ROLE_ADMIN을 부여하지 않아야 한다") {
            val user = User(id = 1L, loginId = "admin", name = "관리자아님", email = "admin@example.com", state = UserState.ACTIVE)
            every { userRepository.findByLoginId("admin") } returns Optional.of(user)

            val details = service.loadUserByUsername("admin")

            val authorityNames = details.authorities.map { it.authority }
            authorityNames.contains("ROLE_SITE_ADMIN") shouldBe false
            authorityNames.contains("ROLE_ADMIN") shouldBe false
        }

        it("state가 SITE_ADMIN이면 loginId와 무관하게 ROLE_SITE_ADMIN/ROLE_ADMIN을 부여해야 한다") {
            val user = User(id = 2L, loginId = "someone", name = "누군가", email = "someone@example.com", state = UserState.SITE_ADMIN)
            every { userRepository.findByLoginId("someone") } returns Optional.of(user)

            val details = service.loadUserByUsername("someone")

            val authorityNames = details.authorities.map { it.authority }
            authorityNames.contains("ROLE_SITE_ADMIN") shouldBe true
            authorityNames.contains("ROLE_ADMIN") shouldBe true
        }

        it("사용자를 찾을 수 없으면 UsernameNotFoundException을 던져야 한다") {
            every { userRepository.findByLoginId("unknown") } returns Optional.empty()
            
            shouldThrow<UsernameNotFoundException> {
                service.loadUserByUsername("unknown")
            }
        }

        it("password, passwordSalt가 non-null이면 그대로 반환해야 한다") {
            val user = User(id = 3L, loginId = "haspw", name = "비번있음", email = "haspw@example.com", state = UserState.ACTIVE)
            user.password = "hashed-pw"
            user.passwordSalt = "salt-value"
            every { userRepository.findByLoginId("haspw") } returns Optional.of(user)

            val details = service.loadUserByUsername("haspw") as YonaUserDetails

            details.password shouldBe "hashed-pw"
            details.passwordSalt shouldBe "salt-value"
        }

        it("id, password, passwordSalt가 null일 때 기본값을 처리해야 한다") {
            val user = User(id = null, loginId = "nullfields", name = "null", email = "null@example.com", state = UserState.ACTIVE)
            user.password = null
            user.passwordSalt = null
            every { userRepository.findByLoginId("nullfields") } returns Optional.of(user)

            val details = service.loadUserByUsername("nullfields") as YonaUserDetails

            details.id shouldBe 0L
            details.password shouldBe ""
            details.passwordSalt shouldBe ""
        }
    }
})
