package com.github.yonaprojects.yona.domain.oauth2server

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// yona-wiki P3-14 1라운드 — ApiTokenAuthorizer(PAT)와 동일한 ordinal 비교 규칙을
// OAuth 스코프 문자열 축에도 적용하는지 검증.
class OAuthScopeAuthorizerSpec : DescribeSpec({

    describe("isAuthorized") {
        it("필요한 그룹:write 스코프가 있으면 WRITE 요구를 통과해야 한다") {
            OAuthScopeAuthorizer.isAuthorized(
                grantedScopes = setOf("issues:write"),
                resourceTypes = listOf(ResourceType.ISSUE_POST),
                requiredPermission = ApiTokenPermission.WRITE
            ) shouldBe true
        }

        it("group:write는 group:read 요구도 만족해야 한다(상위 권한이 하위 권한을 포함)") {
            OAuthScopeAuthorizer.isAuthorized(
                grantedScopes = setOf("issues:write"),
                resourceTypes = listOf(ResourceType.ISSUE_POST),
                requiredPermission = ApiTokenPermission.READ
            ) shouldBe true
        }

        it("group:read만 있으면 WRITE 요구는 거부돼야 한다") {
            OAuthScopeAuthorizer.isAuthorized(
                grantedScopes = setOf("issues:read"),
                resourceTypes = listOf(ResourceType.ISSUE_POST),
                requiredPermission = ApiTokenPermission.WRITE
            ) shouldBe false
        }

        it("해당 그룹 스코프가 전혀 없으면 거부돼야 한다") {
            OAuthScopeAuthorizer.isAuthorized(
                grantedScopes = setOf("code:write"),
                resourceTypes = listOf(ResourceType.ISSUE_POST),
                requiredPermission = ApiTokenPermission.READ
            ) shouldBe false
        }

        it("resourceType이 null이면(metadata 스코프) 유효한 스코프 집합이 있는 것만으로 통과해야 한다") {
            OAuthScopeAuthorizer.isAuthorized(
                grantedScopes = emptySet(),
                resourceTypes = listOf(null),
                requiredPermission = ApiTokenPermission.READ
            ) shouldBe true
        }

        it("여러 resourceType이 있으면 전부(AND) 만족해야 한다") {
            OAuthScopeAuthorizer.isAuthorized(
                grantedScopes = setOf("issues:read"),
                resourceTypes = listOf(ResourceType.ISSUE_POST, ResourceType.PULL_REQUEST),
                requiredPermission = ApiTokenPermission.READ
            ) shouldBe false
        }
    }
})
