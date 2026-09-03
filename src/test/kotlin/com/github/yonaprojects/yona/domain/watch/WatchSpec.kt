package com.github.yonaprojects.yona.domain.watch

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class WatchSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L, loginId = "gildong", name = "홍길동")
            val watch = Watch(user = user, resourceType = ResourceType.ISSUE_POST, resourceId = "1")

            watch.id = 10L
            watch.user = user
            watch.resourceType = ResourceType.BOARD_POST
            watch.resourceId = "2"

            watch.id shouldBe 10L
            watch.user shouldBe user
            watch.resourceType shouldBe ResourceType.BOARD_POST
            watch.resourceId shouldBe "2"
        }
    }
})
