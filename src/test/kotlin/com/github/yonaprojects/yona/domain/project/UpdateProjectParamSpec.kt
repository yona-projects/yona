package com.github.yonaprojects.yona.domain.project

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// UpdateProjectParam은 순수 데이터 홀더(data class)라 분기가 없다. 다른 스펙(예:
// ProjectServiceImplSpec)에서 필드 값을 읽는 용도로는 이미 두루 쓰이고 있지만, data class가
// 자동 생성하는 equals()/hashCode()/copy()/toString()/componentN()은 직접 호출된 적이 없어
// LINE 커버리지가 비어 있었다.
class UpdateProjectParamSpec : DescribeSpec({
    fun baseParam(name: String? = "new-name") = UpdateProjectParam(
        name = name,
        overview = "설명",
        projectScope = ProjectScope.PRIVATE,
        isCodeAccessibleMemberOnly = false,
        isUsingReviewerCount = false,
        defaultReviewerCount = 1,
        defaultBranch = null,
        isCodeEnabled = true,
        isIssueEnabled = true,
        isPullRequestEnabled = true,
        isReviewEnabled = true,
        isMilestoneEnabled = true,
        isBoardEnabled = true,
        isWikiEnabled = true
    )

    describe("UpdateProjectParam") {
        it("생성자 프로퍼티를 그대로 읽을 수 있어야 한다") {
            val param = baseParam()

            param.name shouldBe "new-name"
            param.overview shouldBe "설명"
            param.projectScope shouldBe ProjectScope.PRIVATE
            param.isCodeAccessibleMemberOnly shouldBe false
            param.isUsingReviewerCount shouldBe false
            param.defaultReviewerCount shouldBe 1
            param.defaultBranch shouldBe null
            param.isCodeEnabled shouldBe true
            param.isIssueEnabled shouldBe true
            param.isPullRequestEnabled shouldBe true
            param.isReviewEnabled shouldBe true
            param.isMilestoneEnabled shouldBe true
            param.isBoardEnabled shouldBe true
            param.isWikiEnabled shouldBe true
        }

        it("name의 기본값은 null이어야 한다") {
            val param = UpdateProjectParam(
                overview = "설명",
                projectScope = ProjectScope.PUBLIC,
                isCodeAccessibleMemberOnly = false,
                isUsingReviewerCount = false,
                defaultReviewerCount = 0,
                defaultBranch = null,
                isCodeEnabled = true,
                isIssueEnabled = true,
                isPullRequestEnabled = true,
                isReviewEnabled = true,
                isMilestoneEnabled = true,
                isBoardEnabled = true,
                isWikiEnabled = true
            )

            param.name shouldBe null
        }

        it("equals()/hashCode()는 모든 필드가 같으면 동등해야 한다") {
            val a = baseParam()
            val b = baseParam()

            (a == b) shouldBe true
            a.hashCode() shouldBe b.hashCode()
        }

        it("equals()는 필드 하나만 달라도 false여야 한다") {
            val a = baseParam(name = "new-name")
            val b = baseParam(name = "other-name")

            (a == b) shouldBe false
        }

        it("copy()로 일부 필드만 바꿔 새 인스턴스를 만들 수 있어야 한다") {
            val original = baseParam()

            val copied = original.copy(overview = "새 설명")

            copied.overview shouldBe "새 설명"
            copied.name shouldBe original.name
        }

        it("toString()은 주요 필드 값을 포함해야 한다") {
            val param = baseParam()

            param.toString().contains("new-name") shouldBe true
        }

        it("componentN()으로 구조 분해할 수 있어야 한다") {
            val param = baseParam()

            val (name, overview) = param

            name shouldBe "new-name"
            overview shouldBe "설명"
        }
    }
})
