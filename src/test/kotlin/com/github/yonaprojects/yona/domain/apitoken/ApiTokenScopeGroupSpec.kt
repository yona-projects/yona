package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona-wiki P3-02 Step2 — ResourceType(33종) -> ApiTokenScopeGroup 매핑 정확성 검증. 매핑 테이블
// 자체가 곧 스코프 판정의 정책이라(어느 그룹에 넣느냐가 곧 어떤 권한을 요구하느냐를 결정) 값 하나
// 하나를 명시적으로 고정해둔다 — 나중에 실수로 그룹이 바뀌면 이 테스트가 바로 잡아낸다.
class ApiTokenScopeGroupSpec : DescribeSpec({

    describe("ResourceType.toApiTokenScopeGroup") {
        it("이슈 관련 리소스는 ISSUES 그룹이어야 한다") {
            listOf(
                ResourceType.ISSUE_POST,
                ResourceType.ISSUE_ASSIGNEE,
                ResourceType.ISSUE_STATE,
                ResourceType.ISSUE_CATEGORY,
                ResourceType.ISSUE_MILESTONE,
                ResourceType.ISSUE_LABEL,
                ResourceType.ISSUE_COMMENT,
                ResourceType.ISSUE_LABEL_CATEGORY,
                ResourceType.MILESTONE
            ).forEach { it.toApiTokenScopeGroup() shouldBe ApiTokenScopeGroup.ISSUES }
        }

        it("PR/코드리뷰 관련 리소스는 PULL_REQUESTS 그룹이어야 한다") {
            listOf(
                ResourceType.PULL_REQUEST,
                ResourceType.COMMIT_COMMENT,
                ResourceType.COMMENT_THREAD,
                ResourceType.REVIEW_COMMENT
            ).forEach { it.toApiTokenScopeGroup() shouldBe ApiTokenScopeGroup.PULL_REQUESTS }
        }

        it("코드/커밋/포크 관련 리소스는 CODE 그룹이어야 한다") {
            listOf(
                ResourceType.CODE,
                ResourceType.COMMIT,
                ResourceType.FORK
            ).forEach { it.toApiTokenScopeGroup() shouldBe ApiTokenScopeGroup.CODE }
        }

        it("게시판 관련 리소스는 BOARD 그룹이어야 한다") {
            listOf(
                ResourceType.BOARD_POST,
                ResourceType.BOARD_CATEGORY,
                ResourceType.BOARD_NOTICE,
                ResourceType.NONISSUE_COMMENT
            ).forEach { it.toApiTokenScopeGroup() shouldBe ApiTokenScopeGroup.BOARD }
        }

        it("WIKI_PAGE는 WIKI 그룹이어야 한다") {
            ResourceType.WIKI_PAGE.toApiTokenScopeGroup() shouldBe ApiTokenScopeGroup.WIKI
        }

        it("WEBHOOK은 WEBHOOKS 그룹이어야 한다") {
            ResourceType.WEBHOOK.toApiTokenScopeGroup() shouldBe ApiTokenScopeGroup.WEBHOOKS
        }

        it("프로젝트/사이트 설정·라벨·조직·첨부파일 관련 리소스는 ADMINISTRATION 그룹이어야 한다") {
            listOf(
                ResourceType.PROJECT_SETTING,
                ResourceType.SITE_SETTING,
                ResourceType.PROJECT,
                ResourceType.PROJECT_LABELS,
                ResourceType.LABEL,
                ResourceType.PROJECT_TRANSFER,
                ResourceType.ORGANIZATION,
                ResourceType.ATTACHMENT
            ).forEach { it.toApiTokenScopeGroup() shouldBe ApiTokenScopeGroup.ADMINISTRATION }
        }

        it("사용자/아바타 관련 리소스는 USERS 그룹이어야 한다") {
            listOf(
                ResourceType.USER,
                ResourceType.USER_AVATAR
            ).forEach { it.toApiTokenScopeGroup() shouldBe ApiTokenScopeGroup.USERS }
        }

        it("NOT_A_RESOURCE는 어느 그룹에도 속하지 않아야 한다(null)") {
            ResourceType.NOT_A_RESOURCE.toApiTokenScopeGroup() shouldBe null
        }

        it("NOT_A_RESOURCE를 제외한 모든 ResourceType이 그룹에 매핑되어야 한다") {
            val unmapped = ResourceType.values()
                .filter { it != ResourceType.NOT_A_RESOURCE }
                .filter { it.toApiTokenScopeGroup() == null }

            unmapped shouldBe emptyList()
        }
    }
})
