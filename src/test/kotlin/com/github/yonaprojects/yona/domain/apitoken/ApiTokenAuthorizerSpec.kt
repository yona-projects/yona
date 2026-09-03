package com.github.yonaprojects.yona.domain.apitoken

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.User
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-02 Step2 — ApiTokenAuthorizer 판정 로직 단위 테스트. DB/Spring 컨텍스트 없이 순수
// 객체 조합만으로 검증 가능하다(ApiTokenAuthenticationFilterSpec과 동일하게 DescribeSpec 단독 사용).
class ApiTokenAuthorizerSpec : DescribeSpec({

    fun tokenWithScope(
        group: ApiTokenScopeGroup?,
        permission: ApiTokenPermission,
        allRepositories: Boolean = true,
        scopedProjects: MutableSet<Project> = mutableSetOf(),
        expiresAt: Instant? = Instant.now().plus(1, ChronoUnit.DAYS)
    ): ApiToken {
        val token = ApiToken(
            owner = User(id = 1L, loginId = "owner", name = "소유자", email = "owner@example.com"),
            tokenHash = "irrelevant-hash",
            allRepositories = allRepositories,
            scopedProjects = scopedProjects,
            expiresAt = expiresAt
        )
        if (group != null) {
            token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = group, permission = permission))
        }
        return token
    }

    describe("ApiTokenAuthorizer.isAuthorized") {
        it("ISSUE_POST write 요청은 issues 그룹 write 권한이 없는 토큰이면 거부되어야 한다") {
            val token = tokenWithScope(ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ)

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.ISSUE_POST,
                project = null,
                requiredPermission = ApiTokenPermission.WRITE
            )

            allowed shouldBe false
        }

        it("issues 그룹 write 권한이 있으면 ISSUE_POST write 요청이 허용되어야 한다") {
            val token = tokenWithScope(ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.ISSUE_POST,
                project = null,
                requiredPermission = ApiTokenPermission.WRITE
            )

            allowed shouldBe true
        }

        it("아무 스코프도 없으면 read 요청조차 거부되어야 한다") {
            val token = tokenWithScope(null, ApiTokenPermission.NONE)

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.ISSUE_POST,
                project = null,
                requiredPermission = ApiTokenPermission.READ
            )

            allowed shouldBe false
        }

        it("write 권한을 가진 토큰은 read 요청도 허용되어야 한다(상위 권한 포함)") {
            val token = tokenWithScope(ApiTokenScopeGroup.CODE, ApiTokenPermission.WRITE)

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.CODE,
                project = null,
                requiredPermission = ApiTokenPermission.READ
            )

            allowed shouldBe true
        }

        it("만료된 토큰은 스코프가 맞아도 거부되어야 한다") {
            val token = tokenWithScope(
                ApiTokenScopeGroup.ISSUES,
                ApiTokenPermission.WRITE,
                expiresAt = Instant.now().minus(1, ChronoUnit.DAYS)
            )

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.ISSUE_POST,
                project = null,
                requiredPermission = ApiTokenPermission.WRITE
            )

            allowed shouldBe false
        }

        it("NOT_A_RESOURCE는 그룹이 없어 항상 거부되어야 한다") {
            val token = tokenWithScope(ApiTokenScopeGroup.ADMINISTRATION, ApiTokenPermission.WRITE)

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.NOT_A_RESOURCE,
                project = null,
                requiredPermission = ApiTokenPermission.READ
            )

            allowed shouldBe false
        }

        it("전체 저장소(allRepositories=true) 토큰이면 어느 프로젝트든 허용되어야 한다") {
            val token = tokenWithScope(ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, allRepositories = true)
            val project = Project(id = 42L, owner = "someone", name = "some-repo")

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.ISSUE_POST,
                project = project,
                requiredPermission = ApiTokenPermission.WRITE
            )

            allowed shouldBe true
        }

        it("선택 저장소 토큰(allRepositories=false)은 스코프에 없는 프로젝트를 거부해야 한다") {
            val scopedProject = Project(id = 1L, owner = "owner", name = "allowed-repo")
            val token = tokenWithScope(
                ApiTokenScopeGroup.ISSUES,
                ApiTokenPermission.WRITE,
                allRepositories = false,
                scopedProjects = mutableSetOf(scopedProject)
            )
            val otherProject = Project(id = 2L, owner = "owner", name = "other-repo")

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.ISSUE_POST,
                project = otherProject,
                requiredPermission = ApiTokenPermission.WRITE
            )

            allowed shouldBe false
        }

        it("선택 저장소 토큰(allRepositories=false)은 스코프에 포함된 프로젝트를 허용해야 한다") {
            val scopedProject = Project(id = 1L, owner = "owner", name = "allowed-repo")
            val token = tokenWithScope(
                ApiTokenScopeGroup.ISSUES,
                ApiTokenPermission.WRITE,
                allRepositories = false,
                scopedProjects = mutableSetOf(scopedProject)
            )

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = ResourceType.ISSUE_POST,
                project = scopedProject,
                requiredPermission = ApiTokenPermission.WRITE
            )

            allowed shouldBe true
        }

        // yona-wiki P3-02 Step6.5 — "metadata" 스코프(resourceType == null)는 GitHub의 "Metadata:
        // Read-only" 자동 부여와 동일한 개념이라 그룹/권한 매트릭스를 전혀 보지 않고 repo scope +
        // 만료 여부만으로 판정해야 한다.
        it("resourceType이 null(metadata)이면 그룹 권한이 하나도 없어도 repo scope만 있으면 허용되어야 한다") {
            val token = tokenWithScope(group = null, permission = ApiTokenPermission.NONE, allRepositories = true)

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = null,
                project = Project(id = 1L, owner = "owner", name = "some-repo"),
                requiredPermission = ApiTokenPermission.READ
            )

            allowed shouldBe true
        }

        it("resourceType이 null(metadata)이어도 repo scope 자체가 없으면(선택 저장소 밖) 거부되어야 한다") {
            val scopedProject = Project(id = 1L, owner = "owner", name = "allowed-repo")
            val token = tokenWithScope(
                group = null,
                permission = ApiTokenPermission.NONE,
                allRepositories = false,
                scopedProjects = mutableSetOf(scopedProject)
            )
            val otherProject = Project(id = 2L, owner = "owner", name = "other-repo")

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = null,
                project = otherProject,
                requiredPermission = ApiTokenPermission.READ
            )

            allowed shouldBe false
        }

        it("resourceType이 null(metadata)이어도 만료된 토큰은 거부되어야 한다") {
            val token = tokenWithScope(
                group = null,
                permission = ApiTokenPermission.NONE,
                allRepositories = true,
                expiresAt = Instant.now().minus(1, ChronoUnit.DAYS)
            )

            val allowed = ApiTokenAuthorizer.isAuthorized(
                token = token,
                resourceType = null,
                project = Project(id = 1L, owner = "owner", name = "some-repo"),
                requiredPermission = ApiTokenPermission.READ
            )

            allowed shouldBe false
        }
    }
})
