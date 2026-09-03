package com.github.yonaprojects.yona.domain.watch

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UnwatchSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            val unwatch = Unwatch(user = user, resourceType = ResourceType.ISSUE_POST, resourceId = "1")

            unwatch.id = 10L
            unwatch.user = user
            unwatch.resourceType = ResourceType.BOARD_POST
            unwatch.resourceId = "2"

            unwatch.id shouldBe 10L
            unwatch.user shouldBe user
            unwatch.resourceType shouldBe ResourceType.BOARD_POST
            unwatch.resourceId shouldBe "2"
        }
    }
})
