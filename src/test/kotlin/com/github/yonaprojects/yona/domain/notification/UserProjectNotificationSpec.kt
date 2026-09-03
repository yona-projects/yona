package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserProjectNotificationSpec : DescribeSpec({
    val user = User(id = 1L, loginId = "gildong", name = "홍길동")
    val project = Project(id = 1L, name = "test-project", owner = "owner")

    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val notification = UserProjectNotification(
                user = user,
                project = project,
                notificationType = EventType.NEW_ISSUE
            )

            notification.id = 10L
            val otherUser = User(id = 2L, loginId = "other", name = "다른유저")
            val otherProject = Project(id = 2L, name = "other-project", owner = "owner")
            notification.user = otherUser
            notification.project = otherProject
            notification.notificationType = EventType.NEW_COMMENT
            notification.allowed = false

            notification.id shouldBe 10L
            notification.user shouldBe otherUser
            notification.project shouldBe otherProject
            notification.notificationType shouldBe EventType.NEW_COMMENT
            notification.allowed shouldBe false
        }

        it("allowed 기본값은 true여야 한다") {
            val notification = UserProjectNotification(
                user = user,
                project = project,
                notificationType = EventType.NEW_ISSUE
            )

            notification.allowed shouldBe true
        }
    }

    describe("toggle()") {
        it("allowed가 true이면 false로 뒤집어야 한다") {
            val notification = UserProjectNotification(
                user = user,
                project = project,
                notificationType = EventType.NEW_ISSUE,
                allowed = true
            )

            notification.toggle()

            notification.allowed shouldBe false
        }

        it("allowed가 false이면 true로 뒤집어야 한다") {
            val notification = UserProjectNotification(
                user = user,
                project = project,
                notificationType = EventType.NEW_ISSUE,
                allowed = false
            )

            notification.toggle()

            notification.allowed shouldBe true
        }
    }
})
