# 인증 & 권한 검사 애노테이션

Yobi에서 애노테이션을 사용하여 프로젝트의 특정 리소스 접근 권한 및 익명 사용자 접근을 확인하는 방법을 설명한다.

## 인증 & 권한 애노테이션

Yobi에서 사용하는 인증 및 권한과 관련있는 애노테이션은 다음과 같다:

* `@IsOnlyGitAvailable`
* `@IsCreatable`
* `@IsAllowed`
* `@With(AnonymousCheckAction.class)`
* `@With(NullProjectCheckAction.class)`
* `@With(DefaultProjectCheckAction.class)`

Yobi에서 사용하는 인증 및 권한 검사 애노테이션은 다음과 같은 URL 패턴에만 적용할 수 있다.

`/loginId/projectName/**`

`conf/application.conf`에 설정한 application.context가 있을 경우 해당 컨텍스트 패스를 제거한 URL 패턴에 적용된다.

## @With(DefaultProjectCheckAction.class)

이 애노테이션은 URL 패턴에 해당하는 프로젝트가 있는지 그리고 해당 프로젝트를 볼 수 있는 권한이 있는지 확인한다.

URL에 해당하는 프로젝트가 없거나 현재 사용자가 읽기 권한이 없을 때 403 Forbidden으로 응답한다.

### 사용예

다음 코드는 URL 패턴에 해당하는 프로젝트가 있는지 확인한다.

```
@With(DefaultProjectCheckAction.class)
public static Result editIssue(String ownerName, String projectName, Long number) {
    // 코드 생략
}
```

## @IsOnlyGitAvailable

`@With(DefaultProjectCheckAction.class)`가 확인하는 작업에 추가로 GIT 저장소를 사용하는 프로젝트인지 확인한다.

URL에 해당하는 프로젝트가 없거나 현재 사용자가 읽기 권한이 없을 때 403 Forbidden으로 응답한다.
URL에 해당하는 프로젝트가 GIT 저장소를 사용하지 않는 프로젝트일 경우에는 응답으로 400 Bad Request를 반환한다.

컨트롤러 클래스에 이 애노테이션을 사용하면 해당 컨트롤러의 모든 메서드에 적용된다.

### 사용예

다음 코드는 PullRequestApp 컨트롤러의 모든 메서드에 @IsOnlyGitAvailable을 적용하여 이 컨트롤러에서 처리하는 모든 요청은 GIT 저장소를 가진 프로젝트에 대해서만 동작한다고 설정한 것이다.

```
@IsOnlyGitAvailable
public class PullRequestApp extends Controller {
    // 핸들러 메서드 생략
}
```

## @IsCreatable

`@With(DefaultProjectCheckAction.class)`가 확인하는 작업에 추가로 현재 사용자가 프로젝트에 특정 리소스 타입을 생성할 수 있는지 확인한다.

다음의 경우 403 Forbidden으로 응답한다.
* URL에 해당하는 프로젝트가 없다.
* URL에 해당하는 프로젝트에 현재 사용자가 읽기 권한이 없다.
* URL에 해당하는 프로젝트에 해당 리소스를 생성할 권한이 없다.

### 파라미터

* value: 생성 권한을 확인하려는 리소스 타입으로 `ResourceType`을 입력한다.

### 사용예

위 코드는 현재 로그인한 사용자가 URL 패턴에 해당하는 프로젝트에 BOARD_POST 타입의 리소스를 생성할 수 있는 권한이 있는지 확인한다.

```
@IsCreatable(ResourceType.BOARD_POST)
public static Result newPostForm(String userName, String projectName) {
    // 코드 생략
}
```

## @IsAllowed

`@With(DefaultProjectCheckAction.class)`가 확인하는 작업에 추가로 현재 사용자가 특정 리소스에 특정 권한이 가능한지 확인한다.

다음의 경우 403 Forbidden으로 응답한다.
* URL에 해당하는 프로젝트가 없다.
* URL에 해당하는 프로젝트에 현재 사용자가 읽기 권한이 없다.
* 현재 사용자에게 특정 리소스(`ResourceType`)에 특정 동작(`Operation`)을 허용하지 않는다.

### 파라미터

* value: 확인하려는 권한으로 `Operation`을 입력한다.
* resourceType: 권한을 확인하려는 리소스 타입으로 `ResourceType`을 입력한다. 기본값은 PROJECT.

### 주의

이 애노테이션에서 사용할 수 있는 `ResourceType`은 제한되어 있다. 즉, 모든 리소스 타입을 지원하진 않는다.

현재 지원하는 리소스 타입은 다음과 같다.

* PROJECT
* MILESTONE
* BOARD_POST
* ISSUE_POST
* ISSUE_LAVEL
* PULL_REQUEST
* COMMIT_COMMENT

이밖에 필요한 리소스 타입이 있다면 `Resource.getResourceObject` 메서드에 코드를 추가해야 한다.

### 사용예

다음 코드는 resourceType을 명시하지 않았기 때문에 기본값인 PROJECT가 적용되어 현재 사용자가 PROJECT 리소스 타입에 READ 권한이 있는지 확인한다.

```
@IsAllowed(Operation.READ)
public static Result issues(String ownerName, String projectName, String state, String format, int pageNum) throws WriteException, IOException {
    // 코드 생략
}
```

다음 코드는 현재 사용자가 BOARD_POST, 즉 게시물에 대한 UPDATE 권한이 있는지 확인한다.

```
@IsAllowed(value = Operation.UPDATE, resourceType = ResourceType.BOARD_POST)
public static Result editPostForm(String owner, String projectName, Long number) {
    // 코드 생략
}
```

## @With(AnonymousCheckAction.class)

이 애노테이션은 익명 사용자의 요청인지 확인한다.

현재 요청한 사용자가 익명 사용자일 경우에 alert 창을 보여주고 로그인 폼으로 이동시킨다.

### 사용예

다음 코드는 로그인한 사용자가 있는지 확인한다.

```
@With(AnonymousCheckAction.class)
public static Result userIssues(String state, String format, int pageNum) {
    // 코드 생략
}
```

## @With(NullProjectCheckAction.class)

이 애노테이션은 URL 패턴에 해당하는 프로젝트가 있는지 확인한다.

URL에 해당하는 프로젝트가 없을 때 403 Forbidden으로 응답한다.

### 사용예

다음 코드는 URL 패턴에 해당하는 프로젝트가 있는지 확인한다.

```
@With(NullProjectCheckAction.class)
public static Result enroll(String loginId, String projectName) {
    // 코드 생략
}
```
