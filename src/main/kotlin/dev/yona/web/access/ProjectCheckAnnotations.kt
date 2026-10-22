package dev.yona.web.access

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType

/**
 * 컨트롤러 메서드(또는 클래스)가 프로젝트의 리소스에 대한 권한을 요구함을 나타낸다.
 * v1.6 `controllers.annotation.IsAllowed`이다.
 *
 * 프로젝트를 먼저 찾고(없거나 읽을 수 없으면 거부), URL에서 대상 리소스를 찾아 [value] 연산이 허용되는지 검사한다.
 * 리소스는 경로 구간의 위치로 식별한다(`ProjectResourceLocator`). 그래서 [resourceType]에 따라 붙일 수 있는 경로가 정해져 있다.
 *
 * @property value 요구하는 연산
 * @property resourceType 대상 리소스 종류. 기본값은 프로젝트 자체이다
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class IsAllowed(val value: Operation, val resourceType: ResourceType = ResourceType.PROJECT)

/**
 * 프로젝트 안에 [value] 종류의 리소스를 만들 권한을 요구한다. v1.6 `controllers.annotation.IsCreatable`이다.
 * 리소스를 식별할 필요가 없어 API 경로에서도 쓴다.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class IsCreatable(val value: ResourceType)

/** Git 프로젝트에서만 동작하는 기능에 붙인다. 다른 VCS이면 400을 응답한다. v1.6 `IsOnlyGitAvailable`이다. */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class IsOnlyGitAvailable

/**
 * 프로젝트가 있고 읽을 수 있는지만 검사한다. 나머지 권한은 컨트롤러 안에서 직접 판단하는 경우에 쓴다.
 * v1.6 `@With(DefaultProjectCheckAction.class)`이다.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class DefaultProjectCheck

/**
 * 프로젝트가 있는지만 검사하고 읽기 권한은 보지 않는다. 컨트롤러가 직접 권한을 검사하는 곳에 쓴다.
 * v1.6 `@With(NullProjectCheckAction.class)`이다.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class NullProjectCheck

/**
 * 코드 열람을 구성원으로 제한한 프로젝트(`is_code_accessible_member_only`)에서 비구성원을 막는다.
 * 프로젝트 존재와 읽기 검사를 먼저 한다. v1.6 `@With(CodeAccessCheckAction.class)`이다.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class CodeAccessCheck

/**
 * 게스트 계정의 접근을 막고 홈으로 보낸다. v1.6 `controllers.annotation.GuestProhibit`이다.
 *
 * @property displaysFlashMessage 홈에서 `error.forbidden.or.not.allowed` 안내를 보여 준다
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class GuestProhibit(val displaysFlashMessage: Boolean = true)
