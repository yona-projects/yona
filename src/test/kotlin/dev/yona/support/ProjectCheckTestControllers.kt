package dev.yona.support

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.web.access.CodeAccessCheck
import dev.yona.web.access.DefaultProjectCheck
import dev.yona.web.access.GuestProhibit
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsCreatable
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.access.NullProjectCheck
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 프로젝트 검사 애너테이션의 동작을 확인하기 위한 테스트 전용 엔드포인트이다. 테스트 소스에만 있다.
 * 경로의 위치가 중요하다: 리소스 번호는 v1.6 `Resource.getResourceObject`가 읽는 위치(0부터 센 경로 구간)에 있다.
 */
@RestController
class ProjectCheckTestControllers {

    @IsAllowed(Operation.READ)
    @GetMapping("/{owner}/{project}/__t/project-read")
    fun projectRead(): String = "ok"

    @IsAllowed(Operation.UPDATE)
    @GetMapping("/{owner}/{project}/__t/project-update")
    fun projectUpdate(): String = "ok"

    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.ISSUE_POST)
    @GetMapping("/{owner}/{project}/issue/{number}/__t")
    fun issueUpdate(): String = "ok"

    @IsAllowed(Operation.READ, resourceType = ResourceType.BOARD_POST)
    @GetMapping("/{owner}/{project}/post/{number}/__t")
    fun postRead(): String = "ok"

    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.MILESTONE)
    @GetMapping("/{owner}/{project}/milestone/{id}/__t")
    fun milestoneUpdate(): String = "ok"

    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.ISSUE_LABEL)
    @GetMapping("/{owner}/{project}/issue/label/{id}/__t")
    fun labelUpdate(): String = "ok"

    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.ISSUE_LABEL_CATEGORY)
    @GetMapping("/{owner}/{project}/issue/label/category/{id}/__t")
    fun categoryUpdate(): String = "ok"

    @IsAllowed(Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    @GetMapping("/{owner}/{project}/pullRequest/{number}/__t")
    fun pullRequestRead(): String = "ok"

    @IsAllowed(Operation.READ, resourceType = ResourceType.WEBHOOK)
    @GetMapping("/{owner}/{project}/__t/unsupported-type")
    fun unsupportedType(): String = "ok"

    @IsCreatable(ResourceType.ISSUE_POST)
    @GetMapping("/{owner}/{project}/__t/creatable")
    fun creatable(): String = "ok"

    @IsOnlyGitAvailable
    @GetMapping("/{owner}/{project}/__t/git-only")
    fun gitOnly(): String = "ok"

    @DefaultProjectCheck
    @GetMapping("/{owner}/{project}/__t/default")
    fun defaultCheck(): String = "ok"

    @NullProjectCheck
    @GetMapping("/{owner}/{project}/__t/null-check")
    fun nullCheck(): String = "ok"

    @CodeAccessCheck
    @GetMapping("/{owner}/{project}/__t/code")
    fun code(): String = "ok"

    @DefaultProjectCheck
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{project}/__t/api")
    fun api(): String = "ok"

    @GuestProhibit
    @GetMapping("/__t/guest-prohibit")
    fun guestProhibit(): String = "ok"

    @GuestProhibit(displaysFlashMessage = false)
    @GetMapping("/__t/guest-prohibit-silent")
    fun guestProhibitSilent(): String = "ok"
}

/** 시험용 외부 API 경로에 범위를 요구하지 않게 한다(대응표에 없는 `/-_-api` 경로는 기본 거부이다). */
@org.springframework.stereotype.Component
class TestApiScopeRules : dev.yona.security.ApiScopeRuleProvider {
    override fun rules() = mapOf(("GET" to "/-_-api/v1/owners/{owner}/projects/{project}/__t/api") to dev.yona.security.ApiRequirement.None)
}
