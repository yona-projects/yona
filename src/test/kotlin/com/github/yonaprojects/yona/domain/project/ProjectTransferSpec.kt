package com.github.yonaprojects.yona.domain.project

import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class ProjectTransferSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val sender = User(id = 1L, loginId = "sender")
            val project = Project(id = 2L, name = "p", owner = "owner")
            val transfer = ProjectTransfer(sender = sender, project = project)

            val requestedAt = Instant.parse("2026-08-25T00:00:00Z")
            transfer.id = 10L
            transfer.sender = sender
            transfer.destination = "new-owner"
            transfer.project = project
            transfer.requested = requestedAt
            transfer.confirmKey = "key-abc"
            transfer.accepted = true
            transfer.newProjectName = "new-name"

            transfer.id shouldBe 10L
            transfer.sender shouldBe sender
            transfer.destination shouldBe "new-owner"
            transfer.project shouldBe project
            transfer.requested shouldBe requestedAt
            transfer.confirmKey shouldBe "key-abc"
            transfer.accepted shouldBe true
            transfer.newProjectName shouldBe "new-name"
        }

        it("기본값만으로 생성하면 destination/confirmKey/newProjectName이 빈 문자열, accepted가 false여야 한다") {
            val sender = User(id = 1L, loginId = "sender")
            val project = Project(id = 2L, name = "p", owner = "owner")
            val transfer = ProjectTransfer(sender = sender, project = project)

            transfer.id shouldBe null
            transfer.destination shouldBe ""
            transfer.confirmKey shouldBe ""
            transfer.accepted shouldBe false
            transfer.newProjectName shouldBe ""
        }
    }
})
