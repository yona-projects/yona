package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PushedBranchSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val pushedBranch = PushedBranch()
            val project = Project(id = 1L, name = "p", owner = "owner")
            val pushedDate = Instant.parse("2026-08-25T00:00:00Z")

            pushedBranch.id = 10L
            pushedBranch.name = "feature/foo"
            pushedBranch.pushedDate = pushedDate
            pushedBranch.project = project

            pushedBranch.id shouldBe 10L
            pushedBranch.name shouldBe "feature/foo"
            pushedBranch.pushedDate shouldBe pushedDate
            pushedBranch.project shouldBe project
        }

        it("기본값만으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val pushedBranch = PushedBranch()

            pushedBranch.id shouldBe null
            pushedBranch.name shouldBe ""
            pushedBranch.pushedDate shouldBe null
            pushedBranch.project shouldBe null
        }
    }
})
