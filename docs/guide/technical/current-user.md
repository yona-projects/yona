# 현재 사용자 식별 방식

legacy Yona의 `docs/ko/technical/current-user.md`는 Play Framework 고유의 세션/토큰/컨텍스트
메커니즘을 설명하는 문서였다 — PLAY_SESSION 쿠키 포맷, Shiro `Sha256Hash`, Play의
`Http.Context.args` 등. **이 메커니즘 자체는 yona에 그대로 옮겨지지 않았다** — 웹 프레임워크가
Play에서 Spring Security로 바뀌면서 인증/세션 처리 방식 자체가 근본적으로 다르기 때문이다.
그래서 이 문서는 legacy 원문을 그대로 번역하는 대신, **yona의 실제 방식**을 새로 설명한다
(`config/SecurityConfig.kt` 기준).

## 세션 (로그인 유지 기본값)

- Spring Security의 표준 세션 메커니즘을 그대로 쓴다 — 로그인하면 `JSESSIONID` 쿠키가 발급되고,
  서버 쪽 `HttpSession`에 `SecurityContext`(인증된 `Authentication` 객체)가 저장된다.
- legacy처럼 자체적으로 쿠키 값을 인코딩·서명하는 로직이 없다 — Spring Security/서블릿 컨테이너가
  전담한다.
- 로그아웃하면 세션이 무효화된다.

## Remember-Me (로그인 유지 옵션)

- `SecurityConfig.kt`가 Spring Security의 표준 `rememberMe()`를 구성한다
  (`rememberMeParameter("rememberMe")`, 고유 `key`).
- 별도의 persistent-token 저장소를 설정하지 않았으므로 Spring Security 기본값인
  `TokenBasedRememberMeServices`(해시 기반, DB에 별도 토큰을 저장하지 않는 방식)가 쓰인다 —
  legacy의 `yobi.token` 쿠키(SHA-256 + salt + 1024회 반복 해시, DB에 저장된 salt 필요)와는
  해시 알고리즘도, DB 저장 여부도 다르다.
- 유효기간도 legacy(30일)와 다르다 — Spring Security 기본값은 **14일**이고, yona는 이 값을
  별도로 늘려 설정하지 않았다.

## 현재 사용자 조회

컨트롤러에서 현재 로그인 사용자를 얻는 방법도 legacy의 `UserApp.currentUser()`처럼 세션→토큰→
컨텍스트 순으로 수동 폴백하는 로직이 아니라, Spring Security의 표준 방식을 쓴다 —
`SecurityContextHolder.getContext().authentication` 또는 컨트롤러 메서드 파라미터로 받는
`Authentication`/`Principal`. 익명 사용자(비로그인)는 Spring Security의 `AnonymousAuthenticationToken`으로
표현된다.

## 참고

legacy의 상세한 쿠키 포맷·해시 계산 방식이 궁금하다면 legacy 저장소의
`app/utils/*`, `conf/application.conf`의 `application.secret`을 참고하되, **이 정보는 이제
순수 역사적 참고 자료**다 — 현재 yona 세션은 위 방식으로 완전히 대체되었다.
