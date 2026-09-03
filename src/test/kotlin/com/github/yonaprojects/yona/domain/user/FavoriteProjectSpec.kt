package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class FavoriteProjectSpec : DescribeSpec({
    // JPA(Hibernate)가 프록시/리플렉션 인스턴스화 전용으로 쓰는 무인자 생성자 — kotlin-jpa 컴파일러
    // 플러그인이 순수 바이트코드 레벨에만 추가한다. Kotlin 소스에서는 `user`/`project`가 기본값
    // 없는 필수 파라미터로 선언돼 있어 `FavoriteProject()` 호출 자체가 컴파일되지 않는다(직접
    // 시도해 "No value passed for parameter" 컴파일 에러로 확인) — Hibernate가 리플렉션으로만
    // 호출하는 구조라 어떤 Kotlin 테스트로도 도달 불가능하다.
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User()
            val project = Project()
            val favorite = FavoriteProject(id = null, user = user, project = project, owner = "", projectName = "")

            favorite.id = 1L
            favorite.user = user
            favorite.project = project
            favorite.owner = "owner1"
            favorite.projectName = "project1"

            favorite.id shouldBe 1L
            favorite.user shouldBe user
            favorite.project shouldBe project
            favorite.owner shouldBe "owner1"
            favorite.projectName shouldBe "project1"
        }
    }

    // Project.owner는 String?(nullable)이라 `project.owner ?: ""` 양쪽 분기가 실제로 도달 가능하다.
    // 반면 Project.name은 non-null String(기본값 "")이라 `project.name ?: ""`의 null 쪽은 구조적으로
    // 도달 불가능하다(레거시 Java 이식 시 남은 방어 코드로 추정).
    describe("보조 생성자(user, project)") {
        it("project.owner/name이 모두 있으면 그대로 채워야 한다") {
            val user = User()
            val project = Project(owner = "실제소유자", name = "실제프로젝트명")

            val favorite = FavoriteProject(user, project)

            favorite.id shouldBe null
            favorite.user shouldBe user
            favorite.project shouldBe project
            favorite.owner shouldBe "실제소유자"
            favorite.projectName shouldBe "실제프로젝트명"
        }

        it("project.owner가 null이면 owner를 빈 문자열로 채워야 한다") {
            val user = User()
            val project = Project(owner = null, name = "실제프로젝트명")

            val favorite = FavoriteProject(user, project)

            favorite.owner shouldBe ""
            favorite.projectName shouldBe "실제프로젝트명"
        }
    }
})
