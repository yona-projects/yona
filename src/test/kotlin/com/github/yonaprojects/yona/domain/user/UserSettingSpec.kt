package com.github.yonaprojects.yona.domain.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserSettingSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L)
            val setting = UserSetting(user = user)

            val newUser = User(id = 2L)

            setting.id = 10L
            setting.user = newUser
            setting.loginDefaultPage = "/dashboard"

            setting.id shouldBe 10L
            setting.user shouldBe newUser
            setting.loginDefaultPage shouldBe "/dashboard"
        }

        it("기본값만으로 생성하면 id/loginDefaultPage가 null이어야 한다") {
            val user = User(id = 1L)
            val setting = UserSetting(user = user)

            setting.id shouldBe null
            setting.user shouldBe user
            setting.loginDefaultPage shouldBe null
        }
    }
})
