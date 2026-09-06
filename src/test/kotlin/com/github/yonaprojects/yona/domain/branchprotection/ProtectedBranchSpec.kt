package com.github.yonaprojects.yona.domain.branchprotection

import com.github.yonaprojects.yona.domain.project.Project
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona-wiki P3-04(브랜치 보호) Step 2 — ProtectedBranch 모델 + branch_pattern glob 매칭.
// 실물 git ref 매칭은 짧은 브랜치 이름(예: "main", "release/1.0")을 대상으로 하므로
// matches()는 "refs/heads/" 접두어가 없는 순수 브랜치 이름을 받는다(호출부인
// BranchProtectionPreReceiveHook/PullRequestServiceImpl 양쪽 모두 이미 접두어를 벗겨서 넘긴다).
class ProtectedBranchSpec : DescribeSpec({

    describe("matches() — branch_pattern glob 매칭") {
        it("패턴에 와일드카드가 없으면 완전히 동일한 브랜치 이름만 매칭돼야 한다") {
            val rule = ProtectedBranch(branchPattern = "main")

            rule.matches("main") shouldBe true
            rule.matches("mainline") shouldBe false
            rule.matches("MAIN") shouldBe false
        }

        it("'*'는 임의의 문자열(빈 문자열 포함)에 매칭돼야 한다") {
            val rule = ProtectedBranch(branchPattern = "release/*")

            rule.matches("release/1.0") shouldBe true
            rule.matches("release/") shouldBe true
            rule.matches("release/1.0/hotfix") shouldBe true
            rule.matches("hotfix/1.0") shouldBe false
            rule.matches("release") shouldBe false
        }

        it("패턴 중간에 특수문자가 있어도 리터럴로 취급돼야 한다(정규식 인젝션 방지)") {
            val rule = ProtectedBranch(branchPattern = "release.v1(final)")

            rule.matches("release.v1(final)") shouldBe true
            rule.matches("releaseXv1(final)") shouldBe false
        }
    }

    describe("restrictedLoginIds() — restrict_push_to 파싱") {
        it("null이면 빈 집합(=제한 없음)을 반환해야 한다") {
            val rule = ProtectedBranch(branchPattern = "main", restrictPushTo = null)

            rule.restrictedLoginIds() shouldBe emptySet()
        }

        it("콤마로 구분된 loginId 목록을 앞뒤 공백 제거 후 집합으로 반환해야 한다") {
            val rule = ProtectedBranch(branchPattern = "main", restrictPushTo = "alice, bob ,, charlie")

            rule.restrictedLoginIds() shouldBe setOf("alice", "bob", "charlie")
        }
    }

    describe("프로퍼티 접근자") {
        it("기본값과 생성자 인자가 모두 정상 동작해야 한다") {
            val rule = ProtectedBranch()

            rule.id shouldBe null
            rule.project shouldBe null
            rule.branchPattern shouldBe ""
            rule.requirePullRequest shouldBe false
            rule.requireApprovals shouldBe 0
            rule.requireSignedCommits shouldBe false
            rule.restrictPushTo shouldBe null
            rule.disallowForcePush shouldBe false
            rule.disallowDelete shouldBe false
            rule.adminsCanBypass shouldBe true

            val project = Project(id = 1L, name = "p", owner = "owner")
            rule.id = 10L
            rule.project = project
            rule.branchPattern = "release/*"
            rule.requirePullRequest = true
            rule.requireApprovals = 2
            rule.requireSignedCommits = true
            rule.restrictPushTo = "alice"
            rule.disallowForcePush = true
            rule.disallowDelete = true
            rule.adminsCanBypass = false

            rule.id shouldBe 10L
            rule.project shouldBe project
            rule.branchPattern shouldBe "release/*"
            rule.requirePullRequest shouldBe true
            rule.requireApprovals shouldBe 2
            rule.requireSignedCommits shouldBe true
            rule.restrictPushTo shouldBe "alice"
            rule.disallowForcePush shouldBe true
            rule.disallowDelete shouldBe true
            rule.adminsCanBypass shouldBe false
        }
    }
})
