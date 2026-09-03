package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.core.authority.SimpleGrantedAuthority

class YonaUserDetailsSpec : DescribeSpec({
    describe("YonaUserDetails") {
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))

        it("생성자 프로퍼티와 UserDetails 위임 메서드가 모두 정상 동작해야 한다") {
            val details = YonaUserDetails(
                id = 1L,
                loginId = "gildong",
                passwordVal = "hashed-password",
                passwordSalt = "salt",
                authoritiesVal = authorities,
                state = UserState.ACTIVE
            )

            details.id shouldBe 1L
            details.loginId shouldBe "gildong"
            details.passwordSalt shouldBe "salt"
            details.authorities shouldBe authorities
            details.password shouldBe "hashed-password"
            details.username shouldBe "gildong"
            details.isAccountNonExpired shouldBe true
            details.isCredentialsNonExpired shouldBe true
        }

        it("state가 기본값(ACTIVE)이면 isAccountNonLocked/isEnabled 모두 true여야 한다") {
            val details = YonaUserDetails(2L, "user2", "pw", "salt2", authorities)

            details.isAccountNonLocked shouldBe true
            details.isEnabled shouldBe true
        }

        it("state가 LOCKED면 isAccountNonLocked는 false, isEnabled는 true여야 한다") {
            val details = YonaUserDetails(3L, "user3", "pw", "salt3", authorities, UserState.LOCKED)

            details.isAccountNonLocked shouldBe false
            details.isEnabled shouldBe true
        }

        it("state가 DELETED면 isEnabled는 false, isAccountNonLocked는 true여야 한다") {
            val details = YonaUserDetails(4L, "user4", "pw", "salt4", authorities, UserState.DELETED)

            details.isEnabled shouldBe false
            details.isAccountNonLocked shouldBe true
        }
    }
})
