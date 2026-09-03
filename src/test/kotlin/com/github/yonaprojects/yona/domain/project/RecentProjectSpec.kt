package com.github.yonaprojects.yona.domain.project

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

// 순수 JPA 엔티티(연관 없는 단순 데이터 홀더, 분기 없음). Kotlin이 생성자 프로퍼티마다 자동 생성하는
// getter/setter가 다른 스펙에서 필드 읽기로만 쓰여 METHOD 커버리지 갭이 생겼다 — 전체 프로퍼티를
// 읽고/쓰는 접근자 테스트로 해결한다.
class RecentProjectSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val visitedAt = Instant.parse("2026-08-25T00:00:00Z")
            val recentProject = RecentProject(
                id = null,
                userId = 1L,
                owner = "owner",
                projectId = 2L,
                projectName = "project",
                visitedDate = visitedAt
            )

            recentProject.id shouldBe null
            recentProject.userId shouldBe 1L
            recentProject.owner shouldBe "owner"
            recentProject.projectId shouldBe 2L
            recentProject.projectName shouldBe "project"
            recentProject.visitedDate shouldBe visitedAt

            val newVisitedAt = Instant.parse("2026-08-26T00:00:00Z")
            recentProject.id = 10L
            recentProject.userId = 20L
            recentProject.owner = "new-owner"
            recentProject.projectId = 30L
            recentProject.projectName = "new-project"
            recentProject.visitedDate = newVisitedAt

            recentProject.id shouldBe 10L
            recentProject.userId shouldBe 20L
            recentProject.owner shouldBe "new-owner"
            recentProject.projectId shouldBe 30L
            recentProject.projectName shouldBe "new-project"
            recentProject.visitedDate shouldBe newVisitedAt
        }

        it("기본값만으로도 생성할 수 있어야 한다") {
            val recentProject = RecentProject()

            recentProject.id shouldBe null
            recentProject.userId shouldBe 0L
            recentProject.owner shouldBe ""
            recentProject.projectId shouldBe 0L
            recentProject.projectName shouldBe ""
        }
    }
})
