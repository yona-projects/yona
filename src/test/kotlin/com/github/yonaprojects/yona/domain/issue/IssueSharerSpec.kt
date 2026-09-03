package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

// 순수 JPA 데이터 홀더(분기 없음) — Kotlin 생성자 프로퍼티마다 자동 생성되는 getter/setter가
// 한 번도 호출된 적 없어 라인 미실행이 발생한 것으로 판단, 전체 프로퍼티 접근자를 검증한다.
class IssueSharerSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val issue = Issue(id = 100L, title = "이슈", project = project, number = 1L)
            val user = User(id = 10L, loginId = "gildong", name = "홍길동")
            val date = Instant.parse("2026-01-01T00:00:00Z")

            val sharer = IssueSharer(loginId = "gildong", user = user, issue = issue)
            sharer.id = 5L
            sharer.created = date
            sharer.loginId = "gildong2"
            sharer.user = user
            sharer.issue = issue

            sharer.id shouldBe 5L
            sharer.created shouldBe date
            sharer.loginId shouldBe "gildong2"
            sharer.user shouldBe user
            sharer.issue shouldBe issue
        }
    }
})
