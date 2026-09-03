package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.project.ProjectScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ImportFormSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val form = ImportForm()

            form.url = "https://github.com/example/repo.git"
            form.owner = "owner"
            form.name = "repo"
            form.overview = "설명"
            form.projectScope = ProjectScope.PRIVATE
            form.vcs = "SVN"
            form.authId = "id"
            form.authPw = "pw"
            form.code = false
            form.issue = false
            form.pullRequest = false
            form.review = false
            form.milestone = false
            form.board = false
            form.repoAuth = "legacy-auth"

            form.url shouldBe "https://github.com/example/repo.git"
            form.owner shouldBe "owner"
            form.name shouldBe "repo"
            form.overview shouldBe "설명"
            form.projectScope shouldBe ProjectScope.PRIVATE
            form.vcs shouldBe "SVN"
            form.authId shouldBe "id"
            form.authPw shouldBe "pw"
            form.code shouldBe false
            form.issue shouldBe false
            form.pullRequest shouldBe false
            form.review shouldBe false
            form.milestone shouldBe false
            form.board shouldBe false
            form.repoAuth shouldBe "legacy-auth"
        }

        it("기본값으로 생성하면 각 필드가 기본값을 가져야 한다") {
            val form = ImportForm()

            form.url shouldBe ""
            form.owner shouldBe ""
            form.name shouldBe ""
            form.overview shouldBe ""
            form.projectScope shouldBe ProjectScope.PUBLIC
            form.vcs shouldBe "GIT"
            form.authId shouldBe null
            form.authPw shouldBe null
            form.code shouldBe true
            form.issue shouldBe true
            form.pullRequest shouldBe true
            form.review shouldBe true
            form.milestone shouldBe true
            form.board shouldBe true
            form.repoAuth shouldBe null
        }
    }
})
