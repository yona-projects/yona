package com.github.yonaprojects.yona.domain.vcs

import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.eclipse.jgit.lib.Constants

class GitBranchSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드를 읽을 수 있어야 한다") {
            val commit = mockk<Commit>()
            val user = User(id = 1L)

            val branch = GitBranch(name = "${Constants.R_HEADS}main", headCommit = commit, user = user)

            branch.name shouldBe "${Constants.R_HEADS}main"
            branch.headCommit shouldBe commit
            branch.user shouldBe user
            branch.shortName shouldBe "main"
        }

        it("user 없이도 생성 가능해야 한다") {
            val commit = mockk<Commit>()

            val branch = GitBranch(name = "${Constants.R_HEADS}feature", headCommit = commit)

            branch.user shouldBe null
            branch.shortName shouldBe "feature"
        }

        it("data class 자동생성 메서드가 정상 동작해야 한다") {
            val commit = mockk<Commit>()
            val a = GitBranch(name = "${Constants.R_HEADS}main", headCommit = commit)
            val b = GitBranch(name = "${Constants.R_HEADS}main", headCommit = commit)
            val c = a.copy(name = "${Constants.R_HEADS}other")

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
            (a == c) shouldBe false
            a.component1() shouldBe a.name
            a.component2() shouldBe commit
            a.component3() shouldBe null
            a.toString() shouldBe a.toString()
        }
    }
})
