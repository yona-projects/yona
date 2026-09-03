# 인증 & 권한 검사 방식

legacy Yona의 `docs/ko/technical/validation-with-annotation.md`는 Play 프레임워크의 커스텀
애노테이션(`@IsAllowed`, `@IsCreatable`, `@IsOnlyGitAvailable`, `@With(...)`)으로 권한을
검사하는 방법을 설명했다. **이 애노테이션 기반 방식은 yona에 그대로 옮겨지지 않았다** —
코드 전체를 검색해도 `@IsAllowed`/`@IsCreatable`/`@IsOnlyGitAvailable`/커스텀
`@With(...)`에 해당하는 애노테이션이 하나도 없다. 대신 각 컨트롤러 메서드 안에서
`AccessControl`의 메서드를 **직접 호출**해서 검사한다.

이 문서는 legacy 방식을 그대로 번역하는 대신, yona의 실제 방식을 설명한다.

## 검사 규칙 자체는 동일하다

**권한 규칙 자체(누가 무엇을 할 수 있는가)는 legacy와 동일하게 유지됐다** —
[access-control.md](access-control.md) 참고. 바뀐 것은 "그 규칙을 어디에, 어떤 코드로
적용하는가"뿐이다.

## legacy 애노테이션 → yona의 직접 호출

각 컨트롤러 메서드 맨 앞에서 조건을 확인하고, 실패하면 바로 `403 Forbidden` 등을 반환한다.

```kotlin
// legacy: @IsAllowed(Operation.READ)
if (!accessControl.isAllowedToReadProject(loginUser, project)) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
}

// legacy: @IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.BOARD_POST)
val isAllowedUpdate = accessControl.isAllowedToUpdatePosting(loginUser, project, posting.authorLoginId)

// legacy: @IsCreatable(ResourceType.BOARD_POST)
// → 보통 accessControl.isAllowedTo*Creatable류 헬퍼 또는 isAllowedToReadProject +
//   추가 조건 조합으로 구현 (컨트롤러마다 정확한 헬퍼 이름은 다르다)
```

각 리소스 타입(`ResourceType.PROJECT`, `MILESTONE`, `BOARD_POST`, `ISSUE_POST`, `ISSUE_LABEL`,
`PULL_REQUEST`, `COMMIT_COMMENT`, `REVIEW_COMMENT` 등)과 동작(`Operation.READ`/`UPDATE`/
`DELETE` 등)에 대응하는 `AccessControl.isAllowedTo*` 계열 메서드가 컨트롤러별로 호출된다 —
정확한 메서드 이름과 시그니처는 `config/security/AccessControl.kt`를 직접 확인하는 게 가장
정확하다.

## 왜 이렇게 바뀌었나

Play의 `@With(ActionClass)` 애노테이션은 컨트롤러 메서드를 감싸는 액션 컴포지션이다. Spring
MVC에서 비슷한 효과를 내려면 `HandlerInterceptor`나 AOP, 커스텀 애노테이션 + `@PreAuthorize`
SpEL 등을 조합할 수 있지만, yona는 그런 간접적인 방식 대신 **컨트롤러 코드 안에서 직접
`AccessControl`을 호출하는 방식**을 택했다 — 리소스 인스턴스별 세밀한 조건(예: "이슈
작성자거나 프로젝트 관리자")을 다루기에는 애노테이션/SpEL보다 명시적 코드가 더 읽기 쉽고
디버깅하기 쉽다는 판단이다. 다만 이건 이 프로젝트가 실제로 택한 방식에 대한 설명이지, 두
접근 중 어느 쪽이 일반적으로 더 낫다는 뜻은 아니다.

## 참고

legacy 애노테이션의 정확한 동작(URL 패턴 제약, 403/400 구분 등)이 궁금하면 legacy 저장소의
`app/utils/*.java`, `app/actions/*.java`를 참고하되, 이는 이제 순수 역사적 참고 자료다 —
yona의 실제 검사 로직은 각 컨트롤러 메서드 안의 `AccessControl` 호출을 직접 읽는 것이 가장
정확하다.
