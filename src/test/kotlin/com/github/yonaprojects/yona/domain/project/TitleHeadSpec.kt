package com.github.yonaprojects.yona.domain.project

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona models/TitleHead.java 대응 (P1-103). 순수 데이터 홀더(분기 없음) — Kotlin이 생성자
// 프로퍼티마다 자동 생성하는 getter/setter가 실제로 호출된 적 없어 LINE/METHOD가 비어 있었다.
class TitleHeadSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("기본값으로 생성되고 모든 필드를 읽고 쓸 수 있어야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val titleHead = TitleHead(project = project)

            titleHead.id shouldBe null
            titleHead.headKeyword shouldBe ""
            titleHead.frequency shouldBe 0

            val otherProject = Project(id = 2L, name = "other", owner = "owner2")
            titleHead.id = 10L
            titleHead.project = otherProject
            titleHead.headKeyword = "Bug"
            titleHead.frequency = 5

            titleHead.id shouldBe 10L
            titleHead.project shouldBe otherProject
            titleHead.headKeyword shouldBe "Bug"
            titleHead.frequency shouldBe 5
        }

        it("모든 필드를 채운 생성자로도 인스턴스화할 수 있어야 한다") {
            val project = Project(id = 3L, name = "proj2", owner = "owner3")
            val titleHead = TitleHead(id = 20L, project = project, headKeyword = "UI", frequency = 3)

            titleHead.id shouldBe 20L
            titleHead.project shouldBe project
            titleHead.headKeyword shouldBe "UI"
            titleHead.frequency shouldBe 3
        }
    }
})
