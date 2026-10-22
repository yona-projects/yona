# v1.6 결함 재현 목록

v1.6을 그대로 따른다는 원칙에 따라 결함도 같게 재현하고, 개선 후보로 여기에 모은다. 개선은 선생님의 승인을 받은 뒤에만 한다.

| 번호 | 위치 | 결함 | v2의 처리 | 근거 |
|---|---|---|---|---|
| R-01 | 로그인 | 솔트나 패스워드가 비어 있는 계정으로 로그인하면 예외가 발생해 HTTP 500이 된다 | 같은 예외(`IllegalArgumentException`)를 던진다 | `UserApp.java:1027-1032`, `LocalAuthenticatorTest` |
| R-02 | 전역 | CSRF 보호가 없다. 폼과 JS가 토큰을 보내지 않는다 | CSRF를 끈다 | `SecurityConfig` |
| R-03 | 로그아웃 | Referer 헤더가 없으면 `redirect(null)`로 오류가 난다 | 홈(`/`)으로 이동한다. v1.6의 오류까지 재현하지는 않는다. **선생님 승인(2026-10-22)** | `UserApp.java:154-159` |
| R-04 | API 토큰 | 토큰으로 식별된 사용자는 계정 상태(LOCKED)를 확인하지 않아 잠긴 계정도 API를 쓸 수 있다 | 같게 재현한다 | `User.java:306-316` |
| R-05 | remember-me | 쿠키 값이 `로그인ID:해시된 패스워드`라서 쿠키가 유출되면 패스워드 없이 로그인된다. 패스워드를 바꿔도 해시가 바뀌므로 쿠키는 무효가 된다 | 같게 재현한다 | `UserApp.java:1227-1230` |
| R-06 | 로그인 | `redirectUrl`을 검증하지 않아 `//example.com` 같은 값으로 외부 주소로 이동시킬 수 있다(열린 리다이렉트) | 같게 재현한다 | `UserApp.java:244-252` |
| R-07 | 기본 로그인 페이지 | 로그인하지 않은 사용자가 `POST /-_-api/v1/user/defultLoginPage`를 호출하면 사용자 ID가 없어 오류(HTTP 500)가 난다 | 같게 재현한다(`IllegalArgumentException`) | `UserApp.java` `setDefaultLoginPage` |
| R-08 | AccessControl | 컨테이너가 프로젝트가 아닌 전역 리소스(예: USER)이면 `isResourceCreatable`이 `container.getProject()`의 예외로 실패한다 | 같게 재현한다(`UnsupportedOperationException`) | `AccessControl.java:99-102` |
| R-09 | AccessControl | 프로젝트 매니저 판정이 "코드(CODE)는 삭제할 수 없다" 규칙보다 먼저 평가되어 매니저는 코드도 삭제할 수 있다 | 같게 재현한다 | `AccessControl.java:244-248`, `:289-293` |
| R-10 | AccessControl | 존재하지 않는 이슈를 ISSUE_POST 리소스로 검사하면 담당자 조회에서 NullPointerException이 난다 | 같게 재현한다(`NoSuchElementException`) | `AccessControl.java:403-405` |
