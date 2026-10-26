package dev.yona.support

import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.CodeAccessCheck
import dev.yona.web.access.DefaultProjectCheck
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.access.SiteManagerOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 최상위 부모 클래스의 어노테이션이 먼저(바깥) 실행되는지 보려는 테스트용 부모이다. */
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
open class AccessChainParent

/**
 * 접근 검사 어노테이션의 실행 순서를 확인하기 위한 테스트 전용 엔드포인트이다 (`AccessChainInterceptor`).
 * v1.6 Play 2.3 규칙: 메서드 어노테이션이 바깥, 클래스 어노테이션이 안쪽, 부모 클래스가 자식 클래스보다 바깥이다.
 */
@RestController
class AccessChainTestControllers : AccessChainParent() {

    /** 메서드의 프로젝트 검사가 클래스(부모)의 `@AnonymousCheck(requiresLogin = true)`보다 바깥이다 (`EnrollProjectApp`과 같다). */
    @DefaultProjectCheck
    @GetMapping("/{owner}/{project}/__chain/project-then-anonymous")
    fun projectThenAnonymous(): String = "ok"

    /** `SiteManagerOnly`가 클래스(부모)의 `@AnonymousCheck`보다 바깥이다 (`SiteApp`과 같다). */
    @SiteManagerOnly
    @GetMapping("/__chain/site-manager")
    fun siteManager(): String = "ok"

    /** 메서드의 `@AnonymousCheck`가 맨 바깥이고, 걸리지 않으면 클래스의 것이 이어서 실행된다. */
    @AnonymousCheck(requiresLogin = false, displaysFlashMessage = false)
    @GetMapping("/__chain/open-then-required")
    fun openThenRequired(): String = "ok"

    /** 같은 요소에 단 두 어노테이션의 읽는 순서가 소스 순서와 같은지 보는 가드용이다. */
    @DefaultProjectCheck
    @CodeAccessCheck
    @GetMapping("/{owner}/{project}/__chain/order-ab")
    fun orderAb(): String = "ok"

    @CodeAccessCheck
    @DefaultProjectCheck
    @GetMapping("/{owner}/{project}/__chain/order-ba")
    fun orderBa(): String = "ok"
}

/** 클래스에 Git 전용 검사가 있고 메서드에 코드 접근 검사가 있는 컨트롤러이다. */
@RestController
@IsOnlyGitAvailable
class AccessChainGitControllers {

    /** 메서드의 `CodeAccess`가 클래스의 `OnlyGit`보다 바깥이다 (`BranchApp.branches`와 같다). */
    @CodeAccessCheck
    @GetMapping("/{owner}/{project}/__chain/code-then-git")
    fun codeThenGit(): String = "ok"
}
