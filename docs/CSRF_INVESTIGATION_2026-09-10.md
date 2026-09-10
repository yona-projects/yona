# CSRF 재활성화 조사 (2026-09-10)

`docs/LEGAL_COMPLIANCE_AUDIT_2026-09-10.md` 3번 항목("CSRF 보호가 애플리케이션 전역에서
비활성화됨")에 대한 상세 조사 기록이다. **이 라운드에서는 조사만 수행했고, 실제 코드
변경(SecurityConfig.kt, JS, 템플릿)은 하지 않았다** — 사용자가 조사 도중 명시적으로
"조사만 하고 구현은 하지 마라"고 지시했기 때문이다(아래 "최종 결정" 참고). 조사 자체는
철저히 수행했고, 실제 구현이 필요한 시점에 그대로 착수할 수 있을 만큼 구체적인 설계
근거를 이 문서에 남긴다.

## 요약

| 항목 | 결론 |
|---|---|
| 왜 껐는가 | 단일 "빅뱅" 재작성 커밋에서 사유 설명 없이 도입됨. 레거시 Play 앱도 애초에 CSRF 보호가 없었음(신규 회귀 아님, 기존 공백의 승계) |
| Thymeleaf 자동 주입 전제 | 결론(자동 주입됨)은 맞으나 조사 착수 시점의 근거(thymeleaf-extras-springsecurity6)는 틀렸음 — 실제 메커니즘은 Spring Security가 `@EnableWebSecurity` 시 자동 등록하는 `requestDataValueProcessor` 빈 + thymeleaf-spring6의 `th:action` 처리. 두 개의 실통합테스트로 검증 완료 |
| 폼 커버리지 | 템플릿의 `<form>` 99개 중 82개(83%)가 `th:action`이라 CSRF를 켜면 추가 조치 없이 보호됨. 나머지 5개(그 중 최소 1개는 실사용 중인 sitewide 로그인 모달) + JS로 동적 생성되는 폼(11개 파일)은 미보호 |
| JS AJAX 호출 | `$.ajax` 약 68곳(JS 파일 약 40곳 + 템플릿 인라인 스크립트 약 28곳), `fetch()` 9곳(템플릿 6개 파일) — 전부 개별 대응 필요 |
| API/Git/SVN/Hg | `/api/v1/**`·`/mcp/**`는 이미 별도 체인이라 영향 없음(추가 조치 불요). `/-_-api/v1/**`·레거시 `/api/**`·`/git,svn,hg/**`는 캐치올 체인을 그대로 타므로 CSRF 예외 처리가 반드시 필요(안 하면 yona-cli/git push/svn/hg 전부 깨짐) |
| 최종 결정 | **보류(이번 라운드에서 구현하지 않음 — 사용자 지시)**. 실제 코드 변경 없음 |

---

## 1. 왜 꺼져 있었는가

```
$ git log --all --oneline -S ".csrf {" -- src/main/kotlin/.../SecurityConfig.kt
8020ae92c Rewrite yona from Play 2.3/Java/Ebean to Spring Boot/Kotlin/JPA
```

`.csrf { csrf -> csrf.disable() }`는 이 파일이 생긴 최초 커밋(레거시 Play 앱 전체를
Spring Boot/Kotlin으로 통째로 재작성한 "빅뱅" 커밋) 한 곳에서만 등장한다 — 이후 한
번도 다시 논의되거나 수정된 적이 없다. 커밋 메시지에도 CSRF에 대한 언급이 전혀 없다.

로컬에 남아있는 레거시 Play 앱 원본(`/home/jiho/yona-convert/legacy-yona`)도 확인했다.
Play는 `conf/application.conf`의 `filters`에 `play.filters.csrf.CSRFFilter`를 명시적으로
추가해야만 CSRF 보호가 켜지는데, 이 레거시 저장소에는 그런 설정이 없다(`app/Global.java`,
`conf/*.conf` 어디에도 CSRF 필터 등록 없음). 즉 **레거시 Play 앱도 원래 CSRF 보호가
없었다** — Spring Boot 재작성판의 `csrf.disable()`은 새로 만든 취약점이 아니라 기존
레거시의 공백을 그대로 승계한 것이다. 다만 "레거시도 안 했으니 괜찮다"는 근거가 되지는
않는다 — 단지 "의도적으로 REST API라서 껐다"는 설계 근거가 존재하지 않는다는 뜻이고,
`docs/yona-wiki/plans/p3-02-cli-and-rest-api.md`/`p3-13-decoupled-spa-frontend.md` 등
이후 계획 문서들도 "CSRF가 꺼져 있다"는 사실만 기록할 뿐 그 이유나 재검토는 하지 않았다.

**결론**: 의도적 설계가 아니라 대규모 포팅 과정에서 사유 기록 없이 넘어간 공백이다.
지금 다시 켜는 것을 막을 "당시의 설계 의도"는 존재하지 않는다.

---

## 2. Thymeleaf 자동 주입 — 조사 착수 전 가정의 검증

### 2.1 착수 전 가정과 실제 차이

조사 착수 시 주어진 가정: "`thymeleaf-extras-springsecurity6`가 있으면 Spring Security
CSRF가 켜져 있을 때 Thymeleaf `<form method="post">` 태그에 자동으로 `_csrf` 히든
input이 주입된다."

**결론(자동 주입된다)은 맞지만, 그 근거로 지목된 라이브러리는 틀렸다.**
`thymeleaf-extras-springsecurity6-3.1.5.RELEASE.jar`의 클래스 전체를 확인한 결과
(`org/thymeleaf/extras/springsecurity6/**`), 이 라이브러리는 `sec:authorize`,
`sec:authentication`, ACL 관련 프로세서와 `SpringSecurityDialect`만 제공한다 —
CSRF 토큰 주입과 관련된 클래스는 단 하나도 없다.

### 2.2 실제 메커니즘

1. `spring-webmvc`의 `RequestContext`(정확히는 그 초기화 로직)는 애플리케이션
   컨텍스트에서 **이름이 정확히 `"requestDataValueProcessor"`인 빈**을 조회한다
   (타입 기반이 아니다 — `RequestContext.class` 바이트코드를 `javap -v`로 직접 확인,
   상수 풀에 `ldc "requestDataValueProcessor"` → `getBean(name, RequestDataValueProcessor.class)`
   패턴이 그대로 남아있음).
2. `spring-security-config`의 `WebMvcSecurityConfiguration`(소스 확인:
   `org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration`)이
   바로 이 이름으로 `CsrfRequestDataValueProcessor`를 자동 등록한다. 이 설정 클래스는
   `@EnableWebSecurity`를 쓰고 `DispatcherServlet`(스프링 MVC)이 클래스패스에 있으면
   `SpringWebMvcImportSelector`에 의해 **자동으로** 임포트된다 — 이 저장소는
   `SecurityConfig.kt`에 이미 `@EnableWebSecurity`가 있으므로 **추가 설정 없이 이미 이
   빈이 존재한다.**
3. `thymeleaf-spring6`의 `SpringActionTagProcessor`(정확히 `th:action` 속성 전용
   프로세서 — 일반 HTML `action="..."` 속성에는 관여하지 않음)가 렌더링 시 이 빈을
   찾아 `getExtraHiddenFields()`를 호출해 히든 input을 주입한다.

즉 "CSRF를 켜면 자동으로 폼에 토큰이 붙는다"는 결론 자체는 맞다 — 다만 그 이유는
`thymeleaf-extras-springsecurity6`가 아니라 **Spring Security 자신이 `@EnableWebSecurity`
사용 시 자동으로 깔아주는 `requestDataValueProcessor` 빈** 때문이고, **오직 `th:action`
속성을 쓴 폼에만 적용된다**(순수 HTML `action=` 속성은 대상이 아님)는 중요한 단서가
있다.

### 2.3 실제 검증(통합테스트)

프로덕션 `SecurityConfig.kt`는 전혀 건드리지 않고, 이 테스트에서만 적용되는 좁은
`securityMatcher`(`/csrf-investigation-*/**`) 체인을 `@TestConfiguration`으로 별도
추가해 실제 `springSecurityFilterChain`(MockMvc + `SecurityMockMvcConfigurers.springSecurity()`)
위에서 확인했다:

- `src/test/kotlin/.../config/CsrfThymeleafAutoInjectionThActionFormSpec.kt` — `th:action`
  폼(`src/test/resources/templates/csrf-investigation-form.html`) → **`_csrf` 히든
  input이 실제로 나타남**(추가 빈 등록 시도 시 `BeanDefinitionOverrideException`이 나면서
  "이미 등록돼 있다"는 사실 자체가 한 번 더 확인됨 — 최초 시도에서 직접 재현).
- `src/test/kotlin/.../config/CsrfThymeleafAutoInjectionPlainActionFormSpec.kt` — 순수
  `action=` 폼(`src/test/resources/templates/csrf-investigation-plain-form.html`,
  `site/layout.html`의 로그인 모달과 동일한 패턴) → **`_csrf`가 나타나지 않음**.

두 스펙 모두 `./gradlew test -Dyona.it.db=h2 --tests "...CsrfThymeleafAutoInjection*"`로
GREEN 확인. 이 두 파일은 순수 조사/검증용 테스트라 커밋에 포함해도 무방하다(프로덕션
코드 변경 없음).

### 2.4 템플릿 폼 커버리지 실측

```
$ grep -rn "<form" templates --include=*.html | wc -l          # 99
$ grep -rn "<form[^>]*th:action" templates --include=*.html | wc -l   # 82
```

**82/99(83%)는 `th:action`이라 CSRF를 켜는 것만으로 자동 보호된다.** 나머지 중 실제
서버 렌더링 폼으로 확인된 것:

- **`templates/site/layout.html:582`** — `<form action="/users/login" method="post">`.
  익명 사용자용 로그인 모달(`scripts` 프래그먼트 안, `sec:authorize="isAnonymous()"`)로
  **거의 모든 페이지에 실제로 렌더링되는 sitewide 폼**이다. `th:action`이 아니므로
  CSRF를 켜면 이 모달을 통한 로그인이 즉시 403으로 깨진다 — 재활성화 시 반드시
  `th:action="@{/users/login}"`로 고치거나 수동으로 히든 필드를 추가해야 하는 대상.
- `templates/common/commentUpdateForm.html:11`의 `action=`은 실제 코드가 아니라 legacy
  동작을 설명하는 주석 안 텍스트였다(오탐 — 실제 폼은 action 자체가 없고 fetch로 대체됨).
- `templates/code/compare.html:282`, `templates/code/diff.html:280`의
  `<form action="${postUrl}" method="post">`는 Thymeleaf가 렌더링하는 게 아니라 JS
  백틱 템플릿 문자열로 **런타임에 DOM에 삽입**하는 폼이다(아래 2.5 참고) — 애초에
  서버 렌더링 대상이 아니므로 `th:action`으로 못 바꾼다.

### 2.5 JS로 동적 생성되는 `<form>` — 별도 카테고리(신규 발견)

Thymeleaf가 전혀 관여하지 않는, JS가 런타임에 `$('<form...>')`로 만들어 바로
`.submit()`하거나 `.ajaxForm()`으로 전송하는 패턴이 상당수 있다:

- `$yobi.sendForm`(정의: `static/javascripts/yona-layout.js`, 미니파이된 번들) —
  사용처: `yobi.project.Home.js`, `yobi.site.MassMail.js`, `yobi.organization.Member.js`,
  `yobi.git.View.js`, `yobi.code.Diff.js`, `yobi.code.SvnDiff.js`, `yobi.board.View.js`,
  `yobi.project.Member.js`, `yobi.ui.Typeahead.js`, `common/yobi.Files.js`,
  `common/yobi.Common.js`(총 11개 파일). 내부에서 `.ajaxForm(...)`으로 전송하므로
  **jQuery Form Plugin이 결국 `$.ajax()`를 호출**하고, 3.3절의 `$.ajaxSetup` 인터셉터
  방식이 자동으로 커버할 수 있다.
- `common/yobi.Common.js`의 `sendForm`(같은 이름, 다른 구현) — 마찬가지로
  `.ajaxForm()` 사용, 동일 범주.
- `common/yobi.Files.js:167` — 파일 업로드 다이나믹 폼, `.ajaxForm()` 사용, 동일 범주.
- `service/yobi.site.MassMail.js:107` — `$yobi.sendForm` 콜백 안에서 **`mailto:`
  스킴으로 실제 `.submit()`** — 브라우저 자체 mailto 핸들러 호출이라 CSRF와 무관.
- `templates/code/compare.html:282`, `templates/code/diff.html:280` — 인라인 코드 리뷰
  댓글 폼. 실제 제출 방식은 해당 파일의 JS를 더 봐야 확정되지만(이번 조사에서는 폼
  생성부까지만 확인), `$.ajax`/`fetch`가 아니라면 히든 필드를 JS에서 직접 채워 넣어야
  하는 대상이다.

**결론**: `$.ajaxSetup`/`fetch` 패치만으로는 부족하고, `$yobi.sendForm` 계열(11개
파일)도 CSRF 토큰을 `htData['_csrf']`에 채우도록 별도 패치가 필요하다 — 원래 브리핑엔
없던, 이번 조사로 새로 발견한 작업 항목이다.

---

## 3. JS AJAX 호출 전수 조사

### 3.1 정적 JS 파일(`static/javascripts/{common,service}/**`, 벤더 `lib/` 제외)

`$.ajax(` 호출이 있는 파일 20개, 호출 지점 약 40곳:

`yobi.code.Nohead.js`, `yobi.user.View.js`, `yobi.project.ChangeVCS.js`,
`yobi.issue.LabelEditor.js`(4곳), `yobi.user.Setting.js`, `yobi.code.Browser.js`,
`yona.detectChange.js`, `yobi.project.Transfer.js`, `yobi.project.Global.js`(2곳),
`yona-lib.js`(3곳, 미니파이 번들), `yona.issue.Sharer.js`(3곳), `yobi.issue.View.js`(2곳),
`yobi.project.Home.js`(2곳), `yobi.organization.View.js`, `yobi.organization.Member.js`,
`yobi.project.Member.js`, `yona.issue.Assginee.js`(2곳), `yona-layout.js`(미니파이 번들,
`ajaxConfirm` 헬퍼 포함), `yobi.git.Write.js`, `yobi.user.SignUp.js`,
`yobi.organization.Global.js`, `common/yobi.Files.js`(2곳), `common/yona.ReceiverList.js`,
`common/yobi.LoginDialog.js`, `common/yobi.Common.js`, `common/yona.Tasklist.js`,
`common/yobi.Markdown.js`, `common/yona.CommentAttachmentsUpdate.js`(2곳).

`XMLHttpRequest`를 직접 쓰는 앱 코드는 없음(벤더 `lib/`의 jQuery/ace 등 내부 구현에만
존재 — 정상, jQuery 자체가 내부적으로 XHR을 씀).

### 3.2 템플릿 인라인 `<script>` 안의 `$.ajax`

13개 템플릿, 약 28곳: `board/edit.html`, `board/view.html`(3곳),
`organization/members.html`(4곳), `organization/delete.html`, `issue/view.html`(3곳),
`pullrequest/create.html`(2곳), `pullrequest/edit.html`(2곳), `pullrequest/view.html`(8곳 —
PUT/DELETE/POST 혼합), `project/setting.html`, `project/members.html`(4곳).

### 3.3 템플릿 인라인 `fetch()`

6개 파일, 9곳: `code/diff.html`(1, POST), `login_2fa.html`(2, WebAuthn 옵션/검증 POST),
`common/commentDeleteModal.html`(1, DELETE), `code/svnDiff.html`(1, POST 댓글),
`code/compare.html`(2, POST 댓글/스레드), `user/edit_security_webauthn_new.html`(2,
WebAuthn 옵션/등록 POST). 이 중 `login_2fa.html`/`edit_security_webauthn_new.html`은
이번 세션(P3-43 2FA) 산출물이라는 브리핑 언급이 정확했다.

### 3.4 재활성화 시 최소 침습 대응안(설계만, 미구현)

`$.ajax` 약 68곳(3.1+3.2)은 **개별 수정 없이** `site/layout.html`의 `scripts`
프래그먼트(거의 모든 페이지가 `th:replace="~{site/layout :: scripts}"`로 포함 —
`login_2fa.html`, `user/edit_security_webauthn_new.html` 포함 확인됨) 안에 아래와 같은
전역 인터셉터 하나만 추가하면 커버된다:

```js
jQuery.ajaxSetup({
  beforeSend: function(xhr, settings) {
    var method = (settings.type || 'GET').toUpperCase();
    if (!/^(GET|HEAD|OPTIONS|TRACE)$/.test(method)) {
      var token = /* 쿠키 또는 meta 태그에서 읽은 CSRF 토큰 */;
      if (token) xhr.setRequestHeader('X-CSRF-TOKEN', token);
    }
  }
});
```

`.ajaxForm()`(jQuery Form Plugin)도 내부적으로 `$.ajax()`를 호출하므로 이 인터셉터가
`$yobi.sendForm`/`yobi.Common.js sendForm`/`yobi.Files.js` 업로드까지 함께 커버한다
(2.5절 정정 — `.ajaxForm()` 경로는 실제로는 `$.ajax` 계열이라 커버되고, 순수
`.submit()`으로 끝나는 경로만 별도 처리가 필요).

`fetch()` 9곳은 jQuery 인터셉터의 대상이 아니므로 별도로 `window.fetch`를 감싸는
패치가 같은 위치에 필요하다. 토큰 저장소로 `CookieCsrfTokenRepository.withHttpOnlyFalse()`를
쓰면(Spring Security 공식 문서가 AJAX 혼합 앱에 권장하는 패턴) JS가 쿠키에서 직접 토큰을
읽을 수 있어 별도 meta 태그 배선이 필요 없다.

---

## 4. API/Git/SVN/Hg 인증 경로와의 상호작용

### 4.1 `/api/v1/**`, `/mcp/**` — 이미 안전 (조사 결과 추가 조치 불요)

`ResourceServerConfig.kt`를 확인한 결과, 이 두 경로는 **캐치올 체인(`SecurityConfig`,
`@Order(4)`)과 완전히 분리된 자체 `SecurityFilterChain` 빈**을 이미 갖고 있다
(`mcpResourceServerSecurityFilterChain` `@Order(2)`, `apiResourceServerSecurityFilterChain`
`@Order(3)`, 둘 다 `.securityMatcher(...)`로 좁게 매치). 그리고 **두 체인 모두 이미
독립적으로 `.csrf { it.disable() }`를 갖고 있다** — 캐치올 체인의 CSRF 설정을 바꿔도
이 두 경로에는 **전혀 영향이 없다**. 애초 브리핑이 우려했던 "PAT/OAuth2 클라이언트가
깨질 위험"은 이 두 경로에 대해서는 이미 구조적으로 해소돼 있었다.

### 4.2 `/-_-api/v1/**`, 레거시 `/api/**` — 캐치올 체인을 그대로 탐 (실제 위험)

`/-_-api/v1/**`(전권 PAT, 감사 문서가 우려한 "legacy Open API")를 매핑하는 컨트롤러
14개(`UserController`, `CommentController`, `BoardApiController`, `FavoriteController`,
`IssueApiController`, `IssueShareController`, `MilestoneApiController`,
`ProjectApiController`, `TranslationController`, `WatchController`,
`GlobalApiController`, `StatisticsController`, `ProjectController`,
`IssueRestApiController` 참고 위치)를 확인한 결과, **이 경로들을 위한 별도
`SecurityFilterChain`은 존재하지 않는다** — `/api/v1/**`처럼 격리돼 있지 않고
캐치올 체인(`SecurityConfig`, `@Order(4)`)을 그대로 탄다. 레거시 `/api/projects/{owner}`,
`/api/{owner}/{projectName}/labels` 같은 구 경로도 마찬가지다.

인증은 `ApiTokenAuthenticationFilter`가 `Authorization: token ...` 또는
`Yona-Token: ...` 헤더로 처리한다(세션 쿠키 아님, `ApiTokenAuthenticationFilter.kt`
확인). **캐치올 체인에 CSRF를 그냥 켜면, 이 경로들의 모든 POST/PATCH/DELETE 요청이
CSRF 토큰 부재로 403이 된다** — yona-cli를 포함해 이 네임스페이스를 쓰는 모든
헤드리스 클라이언트가 깨진다는 뜻이다. 재활성화 시 반드시 예외 처리가 필요한, 이번
조사에서 확인된 **가장 실질적인 위험**이다.

### 4.3 `/git/**`, `/svn/**`, `/hg/**` — 캐치올 체인 안에서 permitAll + 별도 인증 필터

`SecurityConfig.kt`의 `authorizeHttpRequests`에서 `/git/**`, `/svn/**`, `/hg/**`는
`permitAll()`이고, 실제 인증/인가는 `GitAuthorizationFilter`/`SvnAuthorizationFilter`/
`HgAuthorizationFilter`(전부 `addFilterAfter(..., BasicAuthenticationFilter::class.java)`로
배선)가 담당한다. `GitAuthorizationFilter.kt`를 직접 읽어 확인한 결과, 인증이 없으면
`WWW-Authenticate: Basic`으로 401을 내려보낸다 — 즉 **HTTP Basic 인증**(세션 쿠키가
아님)이 실제 인증 수단이다. Git smart HTTP push(POST `git-receive-pack`), SVN
commit(PROPPATCH/MKCOL 등 WebDAV 메서드 — 이미 `StrictHttpFirewall`을 넓혀야 했을
정도로 표준과 다름), Hg push 모두 이 필터 체인을 그대로 통과하므로, 캐치올 체인에
CSRF를 무조건 켜면 **git push/svn commit/hg push가 전부 깨진다.**

### 4.4 재활성화 시 필요한 예외 처리(설계만)

두 가지 방식을 검토했다(둘 다 미구현):

1. **경로 기반 제외**(단순, 구현 리스크 낮음): 캐치올 체인에
   `.csrf { csrf -> csrf.ignoringRequestMatchers("/-_-api/**", "/api/**", "/git/**", "/svn/**", "/hg/**") }`.
   장점: 한 파일, 몇 줄로 끝남. 단점: 새 비세션 API 경로가 추가될 때마다 이 목록을
   계속 갱신해야 함(누락 시 조용히 CSRF 예외 대상에서 빠짐 — 반대 방향 실패라 안전한
   편이긴 함).
2. **인증 방식 기반 제외**(더 견고, 구현 리스크 있음): `Authorization`/`Yona-Token`
   헤더가 있는 요청, 또는 `AnonymousAuthenticationToken`이 아니면서 세션 기반이 아닌
   인증(Basic/토큰)인 요청은 CSRF 검증에서 제외하는 커스텀
   `RequestMatcher`(Spring Security 공식 문서의 "CSRF와 stateless API" 권고 패턴).
   `/git,svn,hg/**`도 Basic 인증이라 이 매처 하나로 함께 커버될 가능성이 높지만,
   `DeployKeyAuthenticationProvider`(HTTPS Deploy Key 인증)가 정확히 어떤 헤더/방식을
   쓰는지는 이번 라운드에서 **끝까지 확인하지 못했다**(한계로 남김 — 5번 참고).

어느 쪽이든 `/-_-api/**`에 `/api/v1/**`처럼 **전용 `SecurityFilterChain`을 새로
만드는 것**(옵션 1의 변형, `ResourceServerConfig` 패턴을 그대로 재사용)이 가장
아키텍처적으로 일관된 선택으로 보인다 — 다만 이 경로가 폼 로그인 세션 쿠키로도 호출될
수 있는지(웹 UI 자체가 이 경로를 세션 인증으로 호출하는 경우가 있는지)는 이번 조사에서
전수 확인하지 못했다(한계).

---

## 5. 확인하지 못한 것(한계)

- **`DeployKeyAuthenticationProvider`의 정확한 인증 헤더/방식**: git HTTPS Deploy Key
  인증이 Basic 헤더를 쓰는지, 커스텀 헤더/파라미터를 쓰는지 소스까지 들어가 확인하지
  못했다 — "인증 방식 기반 제외"를 채택한다면 이 경로도 반드시 재확인 필요.
  경로 기반 제외(`/git/**` 전체)를 쓰면 이 문제 자체가 사라진다.
- **웹 UI 자신이 세션 쿠키로 `-_-api`/레거시 `/api/**`를 호출하는 사례가 있는지**:
  `CommentController.kt` 주석에 "공식 `-_-api/v1` 경로와 bare 경로가 공존"한다는
  언급이 있어, 웹 UI 자체가 (PAT가 아니라) 세션 쿠키로 이 경로들 중 일부를 호출할
  가능성을 배제하지 못했다. 만약 그렇다면 4.4의 경로 기반 제외가 "그 경로는 세션
  요청이든 토큰 요청이든 전부 CSRF 면제"가 되어, 그 경로에 한해 CSRF 보호 공백이
  남는다 — 전수 확인 필요.
- **`code/compare.html`/`code/diff.html`의 인라인 댓글 폼이 실제로 어떻게 제출되는지**:
  폼 생성부(JS 템플릿 문자열)까지만 확인했고, 제출 시점 로직(`$.ajax`인지 `fetch`인지
  진짜 `.submit()`인지)은 끝까지 추적하지 못했다.
- **`fetch()`/`$.ajax` 호출 개수는 grep 기반 정적 집계**다 — 동적으로 생성되는 URL
  문자열 안에 숨은 호출(예: `eval`, 문자열 조합으로 만든 메서드명)이 있다면 이 목록에
  빠졌을 수 있다.
- 이번 조사는 CSRF를 **실제로 켜서** 전체 6,474건 테스트 스위트를 돌려보는 실측
  회귀 검증은 하지 않았다(사용자 지시로 구현 자체를 하지 않았으므로 해당 없음) —
  즉 이 문서의 "82/99 폼은 안전하다"는 결론은 개별 검증된 두 스펙(2.3절) + 정적
  패턴 분석의 조합이지, 168개 템플릿 전체를 실제 CSRF-on 상태로 렌더링해 확인한
  것은 아니다.

---

## 6. 최종 결정

**보류(이번 라운드에서 구현하지 않음 — 사용자 지시).**

조사 자체는 "재활성화가 안전하게 가능한가"라는 질문에 대해 상당히 긍정적인 답을
찾았다 — 폼의 83%는 이미 무료로 보호되고, `/api/v1/**`·`/mcp/**`는 애초에 영향이
없으며, 나머지 위험(레거시 `-_-api`/`api`, git/svn/hg, 동적 폼, AJAX 헤더)도 전부
구체적인 대응 설계(3.4, 4.4절)를 세울 수 있는 수준까지 파악했다. 즉 "손댈 수 없을
만큼 위험하거나 범위가 크다"는 판단으로 보류한 것이 아니다 — 조사 도중 사용자가
"조사만 하고 구현은 하지 마라"고 명시적으로 지시해 그 지시를 그대로 따른 것이다.

### 향후 착수 시 필요한 작업(우선순위 순, 이번 라운드 미착수)

1. `/-_-api/v1/**`(+ 레거시 `/api/**`) 전용 `SecurityFilterChain` 신설(CSRF 제외) —
   4.4절 옵션 1의 변형. 착수 전 5번의 "웹 UI가 세션 쿠키로 이 경로를 호출하는 사례"
   여부부터 확인해야 함.
2. 캐치올 체인 `.csrf {}`를 기본값(활성화)으로 전환 + `/git/**`, `/svn/**`, `/hg/**`
   `ignoringRequestMatchers` 추가. `CookieCsrfTokenRepository.withHttpOnlyFalse()` 채택.
3. `site/layout.html:582` 로그인 모달을 `th:action`으로 수정(가장 저비용 고효과).
4. `site/layout.html :: scripts`에 전역 `$.ajaxSetup` beforeSend 인터셉터 +
   `window.fetch` 패치 추가(3.4절 스니펫 기반).
5. `$yobi.sendForm`/`yobi.Common.js sendForm`(11개 파일 사용처)에 CSRF 히든 필드 주입
   패치.
6. `code/compare.html`/`code/diff.html` 인라인 댓글 폼의 실제 제출 경로 확정 후 대응.
7. 전체 6,474건 테스트 회귀(`./gradlew test -Dyona.it.db=h2`) + 대표 실사용 검증
   (브라우저 폼 제출, curl CSRF 토큰 유무별 재현, yona-cli PAT 골든패스, git
   clone/push, svn/hg 실사용) — 원래 브리핑의 TDD/검증 절 그대로.

### 이번 라운드에서 실제로 만든 것(순수 조사/검증, 프로덕션 코드 아님)

- `src/test/kotlin/com/github/yonaprojects/yona/config/CsrfThymeleafAutoInjectionThActionFormSpec.kt`
- `src/test/kotlin/com/github/yonaprojects/yona/config/CsrfThymeleafAutoInjectionPlainActionFormSpec.kt`
- `src/test/resources/templates/csrf-investigation-form.html`
- `src/test/resources/templates/csrf-investigation-plain-form.html`

네 파일 모두 이 저장소의 실제 `SecurityConfig.kt`/기존 템플릿을 전혀 수정하지 않고,
테스트 전용 좁은 `securityMatcher` 체인과 테스트 전용 템플릿만 추가해 위 2.3절의
사실을 검증한다 — GREEN 확인됨(`./gradlew test -Dyona.it.db=h2 --tests
"com.github.yonaprojects.yona.config.CsrfThymeleafAutoInjection*"`).
