package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LdapUserSpec : DescribeSpec({
    describe("LdapUser") {
        it("부서가 null이면 displayName만 반환해야 한다") {
            val user = LdapUser(
                displayName = "홍길동",
                email = "hong@test.com",
                loginId = "hong"
            )
            user.fullDisplayName shouldBe "홍길동"
        }

        it("부서가 빈 문자열이면 displayName만 반환해야 한다") {
            val user = LdapUser(
                displayName = "홍길동",
                email = "hong@test.com",
                loginId = "hong",
                department = "   "
            )
            user.fullDisplayName shouldBe "홍길동"
        }

        it("부서가 존재하면 displayName [부서명] 형태로 반환해야 한다") {
            val user = LdapUser(
                displayName = "홍길동",
                email = "hong@test.com",
                loginId = "hong",
                department = "개발팀"
            )
            user.fullDisplayName shouldBe "홍길동 [개발팀]"
        }

        it("모든 필드를 설정할 수 있어야 한다") {
            val user = LdapUser(
                displayName = "홍길동",
                email = "hong@test.com",
                loginId = "hong",
                department = "개발팀",
                englishName = "GilDong Hong",
                isGuestUser = true
            )
            user.displayName shouldBe "홍길동"
            user.email shouldBe "hong@test.com"
            user.loginId shouldBe "hong"
            user.department shouldBe "개발팀"
            user.englishName shouldBe "GilDong Hong"
            user.isGuestUser shouldBe true
        }
    }
})
