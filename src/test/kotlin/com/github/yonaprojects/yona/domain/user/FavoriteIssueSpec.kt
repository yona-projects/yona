package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// 순수 JPA 데이터 홀더(연관 즐겨찾기 이슈, 분기 없음). Kotlin이 생성자 프로퍼티마다 자동 생성하는
// getter/setter가 다른 스펙에서 필드 읽기로만 쓰여 METHOD 커버리지 갭이 생겼다 — 전체 프로퍼티를
// 읽고/쓰는 접근자 테스트로 해결한다.
class FavoriteIssueSpec : DescribeSpec({
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val project = Project(id = 1L, name = "proj", owner = "owner")
            val user = User(id = 10L, loginId = "gildong", name = "홍길동")
            val issue = Issue(id = 100L, title = "이슈", project = project, number = 1L)

            val favorite = FavoriteIssue(user = user, issue = issue)
            favorite.id = 1L

            val newUser = User(id = 20L, loginId = "other", name = "다른사람")
            val newIssue = Issue(id = 200L, title = "다른이슈", project = project, number = 2L)
            favorite.user = newUser
            favorite.issue = newIssue

            favorite.id shouldBe 1L
            favorite.user shouldBe newUser
            favorite.issue shouldBe newIssue
        }
    }
})
