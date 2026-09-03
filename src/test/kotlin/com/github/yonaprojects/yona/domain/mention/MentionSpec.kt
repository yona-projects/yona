package com.github.yonaprojects.yona.domain.mention

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MentionSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User(id = 1L)
            val otherUser = User(id = 2L)
            val mention = Mention(user = user)

            mention.id = 10L
            mention.resourceType = ResourceType.ISSUE_POST
            mention.resourceId = "3"
            mention.user = otherUser

            mention.id shouldBe 10L
            mention.resourceType shouldBe ResourceType.ISSUE_POST
            mention.resourceId shouldBe "3"
            mention.user shouldBe otherUser
        }

        it("기본값만으로 생성하면 id는 null, resourceType/resourceId는 기본값이어야 한다") {
            val user = User(id = 1L)
            val mention = Mention(user = user)

            mention.id shouldBe null
            mention.resourceType shouldBe ResourceType.NOT_A_RESOURCE
            mention.resourceId shouldBe ""
            mention.user shouldBe user
        }
    }
})
