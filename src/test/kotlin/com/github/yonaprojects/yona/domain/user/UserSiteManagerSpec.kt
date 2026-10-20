package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserSiteManagerSpec : DescribeSpec({
    describe("User.isSiteManager") {
        it("isSiteAdmin 플래그를 따르고 state와 무관하다") {
            User(loginId = "a", name = "a", isSiteAdmin = true, state = UserState.LOCKED).isSiteManager shouldBe true
            User(loginId = "b", name = "b", isSiteAdmin = false, state = UserState.ACTIVE).isSiteManager shouldBe false
        }
    }
})
