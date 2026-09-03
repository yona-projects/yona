package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.organization.Organization
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class FavoriteOrganizationSpec : DescribeSpec({
    // JPA(Hibernate)가 프록시/리플렉션 인스턴스화 전용으로 쓰는 무인자 생성자 — kotlin-jpa 컴파일러
    // 플러그인이 순수 바이트코드 레벨에만 추가한다. Kotlin 소스에서는 `user`/`organization`이
    // 기본값 없는 필수 파라미터로 선언돼 있어 `FavoriteOrganization()` 호출 자체가 컴파일되지
    // 않는다(직접 시도해 "No value passed for parameter" 컴파일 에러로 확인) — Hibernate가
    // 리플렉션으로만 호출하는 구조라 어떤 Kotlin 테스트로도 도달 불가능하다.
    describe("프로퍼티 접근자") {
        it("모든 필드의 getter/setter가 정상 동작해야 한다") {
            val user = User()
            val organization = Organization()
            val favorite = FavoriteOrganization(id = null, user = user, organization = organization, organizationName = "")

            favorite.id = 1L
            favorite.user = user
            favorite.organization = organization
            favorite.organizationName = "테스트조직"

            favorite.id shouldBe 1L
            favorite.user shouldBe user
            favorite.organization shouldBe organization
            favorite.organizationName shouldBe "테스트조직"
        }
    }

    // Organization.name은 Kotlin에서 non-null String(기본값 "")이라, 보조 생성자의
    // `organization.name ?: ""` 엘비스는 null 쪽이 구조적으로 도달 불가능하다(레거시 Java 이식 시
    // 남은 방어 코드로 추정). 정상 값으로 위임 동작만 검증한다.
    describe("보조 생성자(user, organization)") {
        it("organization.name을 organizationName으로 그대로 채워야 한다") {
            val user = User()
            val organization = Organization(name = "실제조직명")

            val favorite = FavoriteOrganization(user, organization)

            favorite.id shouldBe null
            favorite.user shouldBe user
            favorite.organization shouldBe organization
            favorite.organizationName shouldBe "실제조직명"
        }

        it("organization.name이 기본값(빈 문자열)이어도 organizationName이 빈 문자열이어야 한다") {
            val user = User()
            val organization = Organization()

            val favorite = FavoriteOrganization(user, organization)

            favorite.organizationName shouldBe ""
        }
    }
})
