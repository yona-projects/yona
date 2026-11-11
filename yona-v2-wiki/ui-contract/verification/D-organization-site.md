---
title: UI 계약 발견 사항 재현 검증 D (그룹 화면과 사이트 관리)
type: ui-contract-verification
updated: 2026-11-11
---

# 검증 D: 그룹(조직) 화면과 사이트 관리

대상은 `organization.md`, `organization-lists.md`, `organization-oauth.md`, `site.md`, `site-settings.md`의 발견 사항 42건이다. v2(포트 19413)와 공식 v1.16.0 실행본(포트 19513)에 같은 요청과 같은 화면 조작을 해서 비교했다. v1.6 소스는 보조 근거로만 썼다. 스크립트는 `work/D/scripts/`에 있다.

## 요약

| 판정 | 개수 | 항목 |
|---|---|---|
| v2 신규 결함 | 4 | organization-F8, site-F3, site-F6, site-F8 |
| v1.6 결함 유지 | 19 | organization-F2, F3, F4, F5, F6, F7, F9, F10, F12, F16, F17, F18, F19 / site-F4, F5, F7, F9, F10, F13 |
| v2 추가 기능의 결함 | 5 | organization-F13, F21 / site-F16, F17, F19 |
| 결함 아님 | 9 | organization-F11, F15, F20, F22, F23 / site-F12, F14, F18, F20 |
| 문서 문제 | 4 | organization-F14, F24 / site-F1, F11 |
| 재현 불가 | 1 | site-F15 |
| 합계 | 42 | |

### v2 신규 결함 (고칠 곳)

- **site-F8 (심각도 높음)**: 메일 화면 `GET /sites/mail?errorMessage=${7*7}`가 템플릿 전처리로 표현식을 실행해 화면에 `49`가 나온다. `T(java.lang.Math)...`는 500이다. v1.6은 메시지 키를 번역해 HTML로 넣을 뿐 표현식은 실행하지 않았다. 고치기: `SiteMailController.mailView`가 `errorMessage`를 쓰기 전에 알려진 메시지 키 형식(`^[A-Za-z0-9._]+$`)이고 실제로 있는 키일 때만 통과시키고, `site/mail.html:16-19`의 `#{__${errorMessage}__}` 전처리를 `#{${errorMessage}}`(전처리 없음)로 바꾼다. 쿼리 값은 컨트롤러 안에서 모델 값으로만 쓰고 `th:utext`도 `th:text`로 바꾼다. 같은 전처리(`__${title}__`)를 쓰는 다른 화면도 값이 서버 상수인지 확인한다.
- **organization-F8**: 사이트 관리자가 조직의 일반 구성원이고 관리자가 정확히 1명이면 그 사람의 탈퇴 단추가 숨고 `DELETE .../member/leave`도 403(`atLeastOneAdmin`)이다. v1.6은 같은 상황에서 단추가 보이고 탈퇴가 200이었다. 고치기: `OrganizationManagementService.leave`(OrganizationManagementService.kt:157)와 `OrganizationViewContext.leaveBlocked`(OrganizationViewContext.kt:92)가 `isAllowed(LEAVE)` 대신 "본인이 `ORG_ADMIN` 역할이고 관리자 수가 1명"일 때만 막도록 조건을 본인 역할 기준으로 바꾼다.
- **site-F3**: 보호 규칙(자기 자신, 기본 관리자, 마지막 관리자의 권한 회수 금지, v2가 더한 것)에 걸리면 서버가 403만 주고 화면은 아무 반응이 없다. 안내는 flash에 남아 화면을 다시 열 때 나타난다. 고치기: `user-list.html`의 권한 회수 단추를 누르는 `yona.Request`(Request.js:76-95)가 403에서 계정 삭제 모달처럼 `location.reload()`하거나 403 본문의 안내를 바로 보인다.
- **site-F6**: 새 버전 알림을 끄면 v2는 DB 속성에 저장해 서버를 다시 시작해도 꺼져 있고(v1.6은 다시 시작하면 켜졌다), 다시 켜는 화면이 없다. 고치기: 업데이트 화면(`site/update.html`)에 알림 켜기 단추와 `POST /sites/watchUpdate`(UpdateCheckService에 `watch()` 추가)를 더하거나 끄기를 세션 한정으로 되돌린다. `SiteMaintenanceController` KDoc의 "서버를 다시 시작하면 켜진다"는 고친다.

### v2 추가 기능의 결함 (고칠 곳)

- **organization-F21**: 조직 OAuth 앱 수정 양식에서 오류로 다시 그릴 때 기밀(confidential) 앱의 유형 라디오가 "공개"로 보인다(발견 사항은 "기존 앱의 값으로 되살린다"고 적어 틀렸다). 고치기: `OrganizationOAuthAppsController.update`의 `ClientEdit.Rejected` 분기(OrganizationOAuthAppsController.kt:97)에서 `Form.of(request).copy(confidential = client.confidential == true)`를 넘긴다. 사용자 설정용 `OAuthAppsSettingsController`에도 같은 코드가 있으면 같이 고친다.
- **organization-F13**: 조직 설정 탭 중 OAuth 앱과 앱 정책은 비로그인에게 로그인 폼(303)을, 설정, 구성원, 삭제, 감사는 403 화면을 준다. 고치기: 두 컨트롤러의 `requiresLogin`을 다른 설정 탭과 같게 맞추거나 반대로 모두 로그인 폼으로 보낸다(가벼움).
- **site-F16**: LDAP와 OIDC "연결 확인" 단추는 입력란이 없는 별도 폼이라 요청 본문에 `_csrf`뿐이고 저장된 설정만 확인한다. 고친 값은 확인되지 않고 화면을 다시 그리면 편집값이 사라진다. 고치기: 확인 단추를 저장 폼 안으로 옮겨 `formaction`으로 보내거나 `SiteSsoController.checkLdap/checkOidc`가 폼 값을 읽어 저장하지 않은 값으로 확인하게 한다.
- **site-F17**: SAML 2 화면에 "인증되지 않은 이메일 허용" 체크박스가 없는데 `saveSaml2`는 `allowUnverifiedEmail`을 읽어 저장하므로 화면에서 저장할 때마다 꺼진다. OIDC 화면에는 `#oidc-allow-unverified`가 있다. 고치기: `sso.html`의 SAML 폼에 체크박스를 더하거나 서버가 값이 오지 않았을 때 기존 값을 유지한다.
- **site-F19**: 보안 화면의 폼 3개(`/sites/security`, `/sites/security/oauth`, `/sites/security/mcp`)가 서로 독립이다. 설계 한계이며 경미하다. 고치기: 한 폼으로 합치거나 영역마다 저장 안내를 분명히 한다.

## 검증 환경과 메모

- v2: `./start-app.sh D 19413`, v1.6: `./start-v16.sh D 19513 data2`. v1.6 `data2`의 관리자 비밀번호는 `admin1234`이다(키트 안내의 `admin`과 다르다). v1.6의 `POST /organizations/:name/setting`, `/:o/:p/posts`, `/:o/:p/issues/latest`는 multipart만 받고(urlencoded는 500) 글에는 `issueTemplate=false`가 필요하다. `/sites/toggleGuestMode` 같은 v1.6 라우트의 `loginId`는 쿼리 문자열로 받는다. 이 차이를 알고 요청을 맞춰 비교했다.
- 시험용으로 두 서버에 같은 사용자(`guestu`(게스트), `한글유저`, `mem1`, `mem2`, `adm2`~`adm4`), 조직(`orgy`, `한글조직`, `Casey`), 프로젝트와 글과 이슈를 만들었다. 이 과정에서 두 서버의 `admin` 계정을 삭제 시험(site-F4)에 썼으므로 이 사본에서는 로그인이 안 된다(사본 DB이다).
- 결과: organization-F5와 site 쪽 `input pattern`은 Chromium 153의 `v` 플래그 때문에 `[a-zA-Z0-9-]` 같은 패턴이 문법 오류가 되어 무시된다. 그 영향이 항목에 적혀 있다.

## 항목별 결과

### 조직 (organization.md)

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| organization-F2 | 재현됨 | `/organizations/orgy/members`에서 mem1(관리자)의 역할을 "그룹 구성원"으로 바꾸는 요청을 Playwright로 가로채 500을 주고, 별도로 요청을 abort했다. 두 경우 모두 안내(알림, 다이얼로그)가 없고 라벨이 **"그룹 구성원"으로 바뀐 채** 남았다. 새로고침하면 실제 값은 "그룹 관리자". 정상 변경은 204이고 라벨만 바뀐다. 사이트 관리자가 아닌 거부 경로는 `200 {"location"}`이라 이동한다(POST `member/1/edit`에 마지막 관리자 강등 시 이 경로를 확인) | **같음(실행)**. 같은 시나리오에서 v1.6도 500과 abort 모두 안내 없이 라벨이 "그룹 구성원"으로 바뀌고 새로고침하면 "그룹 관리자". 소스 `public/javascripts/service/yobi.organization.Member.js:228-238`이 `fOnError` 없이 `sendForm`을 부른다 | v1.6 결함 유지 (발견 사항보다 나쁘게, 실패했는데 라벨이 틀린 값으로 바뀐다) | 장부에 올린다 |
| organization-F3 | 재현됨 | `GET /users?query=a`에 `Referer: .../members`이면 200 JSON, `Referer: .../members?x=1`이면 406, Referer 없음 406. (`#z`도 406이지만 실제 브라우저는 Referer에 해시를 싣지 않으므로 의미 없는 시험이다.) 구성원 화면 주소 뒤에 쿼리가 붙으면 자동 완성이 죽는다 | **같음(실행)**. 주소만 다르다(v1.6은 `/-_-api/v1/users`, 소스 `conf/routes:77`). `Referer` 끝이 `members`일 때만 200, `members?x=1`은 406. `UserApp.java:88-89` | v1.6 결함 유지 | 장부에 올린다 |
| organization-F4 | 재현됨 | `POST /organizations/orgy/setting`(name 빈 값, `a b`) 둘 다 500. 이미 있는 이름(`admin`)은 400 | **같음(실행)**. multipart로 보냈을 때 빈 값과 `a b`가 500, `admin`은 400. 소스 `OrganizationApp.java:371`의 `organizationForm.get()`이 오류 있는 폼에서 예외, `Organization.java:43-46`의 제약 | v1.6 결함 유지 | 장부에 올린다 |
| organization-F5 | 일부 재현됨 | 서버는 `a..b`를 받는다(`POST setting` 303으로 이름이 `a..b`로 바뀜). 구성원 추가 서버 경로는 `한글유저`를 받아 목록에 나온다(303). **클라이언트 쪽은 달랐다**: 구성원 추가 입력의 `pattern="^[a-zA-Z0-9-]+(...)"`가 Chromium 153에서 `v` 플래그 문법 오류라 **무시**되어 `한글유저`, `a b!` 모두 `checkValidity()`가 true. 즉 "한글 ID를 직접 입력으로 추가할 수 없다"는 서술은 지금 브라우저에서는 틀렸다(옛 브라우저에서만 맞다). 설정과 만들기의 `rxOrgName`(a..b 거부)은 소스대로다 | **같음(실행)**. 같은 마크업(`members.scala.html:42`), v1.6도 `한글` 입력이 valid true, 서버가 `a..b`를 받고 303, `한글유저` 추가 성공. `User.java:66` 정규식, `yobi.organization.Setting.js:45` | v1.6 결함 유지 (서술의 "한글 입력 불가"는 현 브라우저에서 재현 안 됨, 정규식 세 곳 불일치만 사실) | 장부에 올린다 |
| organization-F6 | 재현됨 | 비구성원(plainuser)으로 `/organizations/orgy` 헤더의 `ul.project-util`의 자식 요소가 `BUTTON`, `DIV`(올바른 `LI`가 아님) | **같음(실행)**. 비구성원(third)에서 `ul.project-util > BUTTON, DIV` 같음. `header.scala.html:45-62` | v1.6 결함 유지 | 장부에 올린다 |
| organization-F7 | 재현됨 | 게스트 `guestu`로 `GET /organizations/new` 200, `POST /organizations/new` 303 `/` | **같음(실행)**. 게스트로 폼 GET 200, POST 303 `/`. `OrganizationApp.java:81-92`(`@GuestProhibit`는 POST에만) | v1.6 결함 유지 | 장부에 올린다 |
| organization-F8 | 재현됨 | 사이트 관리자 admin을 조직 `orgy`의 일반 구성원으로 강등(mem1이 유일한 관리자)한 뒤 `/organizations/orgy`에서 `#groupLeaveBtn` 0개. `DELETE .../member/leave` 403 `{"errorMsg":"organization.member.atLeastOneAdmin"}`. 관리자가 1명인 조직(Casey)의 유일한 관리자 admin도 403 | **다름(실행)**. 같은 구성에서 v1.6은 `#groupLeaveBtn` 1개, `DELETE leave` 200 `{"location":"/organizations/api"}`. Casey(유일한 관리자 admin)도 단추 1개, 탈퇴 200. 소스 `OrganizationApp.java:304-307`(`!isAllowed(LEAVE)`일 때만 검사, 사이트 관리자는 `AccessControl.java:56`에서 항상 허용이라 검사를 건너뜀) | v2 신규 결함 (v2가 C157 뒤집힘을 고치면서 사이트 관리자인 일반 구성원이 탈퇴하지 못하게 됨) | 고친다 (위 요약의 organization-F8 항목) |
| organization-F9 | 재현됨 | `/organizations/ORGY`(대문자)의 `.project-menu-gruop li.active`가 비어 있다(`orgy`는 "홈", `issues`는 "이슈"). 한글 조직(`/organizations/한글조직`)도 빈 값. `Casey`/`CASEY`도 같다 | **같음(실행)**. 대문자 `API`와 한글 조직 모두 활성 탭 없음, 소문자는 있음. `menu.scala.html:25-28` | v1.6 결함 유지 | 장부에 올린다 |
| organization-F10 | 재현됨 | v2 `/orgs`에서 고정 문구 `created`가 번역 없이 출력(ko 로케일). 조직 홈의 `by`, `at`도 영문 그대로(Casey/orgx 프로젝트 줄). `Group Project` title과 머리글 `group`은 소스와 DOM(`.project-author`가 `grouporgy`)으로 확인 | **같음(실행)**. v1.6 `/orgs`에도 `created`, 홈에 `by`, `at`. `list.scala.html:67,79`, `view.scala.html:100,109`, `header.scala.html:41` | v1.6 결함 유지 | 장부에 올린다 |
| organization-F11 | 재현됨 | 로고 업로드: `.jpeg`, `.gif`, `.bmp` 303 허용, 5,120,010바이트와 5,242,890바이트 400, `.txt` 400. 한도는 5,120,000바이트(`1024*1000*5`)라 화면의 "5MB" 표기와 같은 뜻이다. 안내의 `bmp, jpg, gif, png`는 `jpeg`를 빼지만 서버는 받는다 | **같음(실행)**. 같은 입력에서 같은 결과(303, 303, 303, 400, 400, 400). `LogoUtil.java:26`, `setting.scala.html:41-42` | 결함 아님 (값은 맞고 상수가 두 곳에 있는 것은 v1.6과 같다) | 조치 없음 |
| organization-F12 | 재현됨(제한적) | `/users` 응답의 `info`에 이름이 이스케이프 없이 들어간다. 가입 시 `HtmlSanitizer`가 `<script>`와 속성(`onerror`)을 지우므로 스크립트 실행은 안 된다(`xs<img src=x onerror=...>`는 `xs`로 저장). 그러나 `q<<b>i</b>`는 `q&lt;<b>i</b>`로 저장되어 자동 완성 목록에 **굵은 글씨로 렌더링**됐다(`window.__pwn` 정의 안 됨) | **같음(실행)**. 같은 이름이 v1.6 자동 완성 목록에도 `<b>`로 렌더링. `UserApp.java:88-` 이후 JSON 조립, `Typeahead` 렌더 | v1.6 결함 유지 (영향은 쓰기 쪽 정화로 제한됨) | 장부에 올린다 |
| organization-F13 | 재현됨 | 비로그인으로 `/organizations/orgy/{settingform,members,deleteForm,audit}` 모두 403 화면, `/oauth-apps`, `/oauth-policy`, `/oauth-apps/new`는 303 `/users/loginform?redirectUrl=...` | **다름(해당 기능 없음)**. v1.6은 `settingform`, `members`, `deleteForm`이 403으로 v2와 같고 `audit`, `oauth-apps`는 404(화면 자체가 없음). OAuth 탭과 감사 탭은 v2 추가 기능 | v2 추가 기능의 결함 (경미) | 고친다 (위 요약) |
| organization-F14 | 재현됨 | `yona-v2-wiki/web/organization.md:36`("하위 메뉴는 설정, 구성원, 삭제"), `:63-65`("아직 없는 부분"), `:53`(탈퇴 규칙을 v1.6의 뒤집힌 조건이라고 서술)을 코드와 대조. 실제 메뉴는 6개(`partial-settingmenu.html`), 목록 화면은 있고, 탈퇴는 v2가 고친 규칙이다(위 organization-F8 관찰값). `web/organization.md`의 설명은 낡았다 | v1.6 실행에서 같은 탈퇴 동작(v1.6 사이트 관리자와 마지막 관리자가 탈퇴 가능)이 wiki 서술 "관리자는 한 명뿐이어도 탈퇴할 수 있고"와 일치함을 확인했다. v2 쪽 서술만 낡았다 | 문서 문제 | 문서만 고친다 |
| organization-F15 | 재현됨 | 비구성원 plainuser로 조직 홈: `.member-info` 0개, 본문에 `mem1` 없음. 관리자는 2개 상자와 `mem1` 보임 | **같음(실행)**. 비구성원 third는 0개, 관리자 2개. `view.scala.html:141-145`의 같은 조건 | 결함 아님 (의도된 화면 제한, 서술은 설계 설명) | 조치 없음 |
| organization-F16 | 재현됨 | 설정 화면에서 저장(`#save`) 후 `/organizations/orgy/settingform`으로 303, 화면에 `.alert`, `.noty`, `.notify` 없음 | **같음(실행)**. 저장 후 같은 화면, 안내 없음. `OrganizationApp.java:390`(`redirect`에 flash 없음) | v1.6 결함 유지 | 장부에 올린다 |

### 조직 목록 (organization-lists.md)

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| organization-F17 | 재현됨 | 글 3개가 있는 `orgx`에서 `/organizations/orgx/boards?filter=post&orderBy=createdDate&orderDir=asc&projectNames=alpha-one`: 정렬 링크 3개가 `.../boards?orderBy=updatedDate&orderDir=desc` 등으로 `filter`, `projectNames[]`를 버린다. `input[name=pageNum]` 0개, `#pagination a` 0개 | **같음(실행)**. 글 있는 `orgy`에서 정렬 링크가 같은 모양, 폼에 `pageNum` 없음, `#pagination a` 0개 | v1.6 결함 유지 | 장부에 올린다 |
| organization-F18 | 재현됨 | 이슈 4개와 라벨 `lab2`가 붙은 이슈로: 라벨 배지 `href`가 `/orgx/alpha-one/issues?state=open&labelIds=7`(프로젝트 목록). 상태 탭은 "열림 4", "닫힘 0"뿐(전체 없음). 프로젝트 `alpha-one`을 고른 뒤 "작성한 이슈"를 누르면 요청 주소가 `projectNames[]=alpha-one&...&authorId=1`로 유지, 폼에 `[data-search=projectNames]` 입력 없고 `authorId`, `assigneeId`, `mentionId`만 있음 | **같음(실행)**. 배지 `/orgy/oproj/issues?state=open&labelIds=33`, 탭은 열림과 닫힘만, 빠른 필터 뒤에도 `projectNames[]=oproj` 유지, 같은 입력 3개 | v1.6 결함 유지 | 장부에 올린다 |
| organization-F19 | 일부 재현됨 | `/organizations/orgx/pullrequests?pageNum=2&filter=zz`에서 "닫힘" 탭을 누르면 전체 화면 이동으로 `/organizations/orgx/closedPullrequests?filter=zz`(`pageNum` 사라짐). 폼에 숨은 `pageNum` 없음. `<div id="pagination">`을 `<ul>` 안에 두는 줄은 소스에 있고(`pullrequest-list.html:85`), **PR 줄이 있는 화면은 만들지 못했다**(저장소에 브랜치와 포크가 있는 PR 데이터를 만들어 보았으나 시간 제한 안에 PR 생성에 이르지 못했다). 빈 목록에서는 `ul #pagination`이 없다 | **같음(실행)** 탭 전환 동작과 전체 이동(`.../closedPullrequests?filter=zz`)이 같다. 마크업은 소스 대조: `group_pullrequest_list_partial.scala.html:81`의 `<div id="pagination">`이 `<ul class="post-list-wrap">`(:13, 닫기 :88) 안에 있다(실행 확인은 PR 데이터 부재로 못 함) | v1.6 결함 유지 | 장부에 올린다 |

### 조직 OAuth (organization-oauth.md, 모두 v2 추가 기능)

v1.6에는 조직 OAuth 앱과 앱 정책 화면이 없다(`/organizations/:name/oauth-apps`가 404). v1.6 대조는 "해당 없음"이다.

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| organization-F20 | 재현됨 | Casey에서 기밀 앱 만들기: `POST /organizations/Casey/oauth-apps` 200(리다이렉트 아님), 본문에 `yona_cs_...` 비밀 값, `Cache-Control: no-cache, no-store, must-revalidate`. `POST .../1/secret`을 두 번 보내면 값이 `yona_cs_NR3i...`, `yona_cs_mO5I...`로 매번 바뀐다. `GET .../oauth-apps/1`에는 비밀 값 없음 | 해당 없음 | 결함 아님 (의도된 한 번 보이기) | 조치 없음 |
| organization-F21 | 재현됨(서술 일부 틀림) | 수정 양식의 유형 라디오는 `public:disabled`, `confidential:disabled:checked`. `type` 없이 수정 303 후 앱은 계속 confidential이고 이름만 바뀜. `type=public`을 보내도 confidential 유지(맞음). **그러나 오류로 다시 그릴 때(이름 빈 값, 400) 라디오가 `public checked disabled`로 나온다.** 발견 사항의 "기존 앱의 값으로 되살린다"는 틀렸다 | 해당 없음 | v2 추가 기능의 결함 | 고친다 (위 요약) |
| organization-F22 | 재현됨 | 한글 조직 `한글조직`의 `POST .../oauth-policy`(restricted true) 303 `Location: /organizations/%ED%95%9C%EA%B8%80%EC%A1%B0%EC%A7%81/oauth-policy`(인코딩됨). 화면에서 저장 단추 후에도 같은 주소. 결정(`/apps/1`)은 그 앱이 후보가 아니라 404(승인한 구성원이 없음) | 해당 없음 | 결함 아님 (실제로 인코딩되어 동작, 코드 스타일 차이뿐) | 조치 없음 |
| organization-F23 | 재현됨 | 사이트 보안에서 `userApps`를 끄면(`POST /sites/security/oauth` 303) Casey의 앱 목록에서 "새 앱" 링크 0개, `GET .../oauth-apps/new` 403, `POST .../oauth-apps` 403. 이미 있는 앱의 `GET .../1` 200, 수정 303, 이전 303(한글조직으로) 모두 허용. 시험 뒤 설정을 되돌렸다 | 해당 없음 | 결함 아님 (화면과 서버가 같다) | 조치 없음 |
| organization-F24 | 재현됨(서술 일부 틀림) | 앱 상세의 이전 대상에 현재 소유자 `Casey`가 빠지고 `user|admin (내 계정)`, 관리자인 다른 조직들이 나옴. 삭제는 앱 목록의 `form method=post action=.../delete`이고 **`onsubmit="return confirm(...)"`가 붙어 있다**(발견 사항의 "JS 없음"은 부정확). `POST .../1/delete` 303. 위키 `oauth2-server.md:50`에는 요청 계약 표가 없다(목록 줄만) | 해당 없음 | 문서 문제 | 문서만 고친다 |

### 사이트 관리 (site.md)

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| site-F1 | 재현됨 | 비로그인으로 `/sites/userList`, `/sites/update`, `/sites/mail` 모두 403 화면(로그인 폼 아님) | **같음(실행)**. v1.6도 세 주소 모두 403 | 문서 문제 (코드가 맞고 `web/site-admin.md`의 "익명은 로그인 폼으로 간다"가 틀림. v1.6도 403이다) | 문서만 고친다 |
| site-F3 | 재현됨 | 두 번째 사이트 관리자 `adm2`로 로그인해 자기 행의 "사이트 어드민 권한 회수"를 누르면 응답 403, 다이얼로그나 알림 없음. 화면을 새로 열면 그때 "자기 자신, 기본 admin, 마지막 사이트 관리자의 관리자 권한은 해제할 수 없습니다."가 보이고, 한 번 더 열면 사라진다 | **다름(실행)**. v1.6은 보호가 없어 같은 요청이 303이고 `adm2`가 즉시 관리자 권한을 잃어 이후 `/sites/userList`가 403(`SiteApp.java:185-` `toggleSiteAdminRole`) | v2 신규 결함 (v2가 더한 보호의 안내가 늦게 나옴) | 고친다 (위 요약) |
| site-F4 | 재현됨 | (1) `adm3`이 자기 자신 삭제 `DELETE /sites/user/delete110` 303, 목록 `DELETED`에 `adm3`. (2) `adm4`가 자기 자신 잠금 `POST /sites/toggleAccountLock?loginId=adm4` 200, `LOCKED`에 `adm4`. (3) 기본 관리자 `admin`(id 1) 삭제: `adm2`가 요청하면 처음에는 403(admin이 프로젝트의 유일한 매니저). admin이 매니저인 프로젝트 13개를 모두 지운 뒤 `DELETE /sites/user/delete1` 303, 이후 admin의 `/sites`가 403. | **같음(실행)**. 같은 세 시험이 v1.6에서 같은 결과(자기 삭제 303, 자기 잠금 200, 기본 관리자 id 1은 매니저 프로젝트 8개를 지운 뒤 303이고 로그인 실패). `SiteApp.java:164-182`(자기, 기본 관리자 검사 없음, 프로젝트 유일 매니저만 검사) | v1.6 결함 유지 | 장부에 올린다 |
| site-F5 | 재현됨 | 시험 프로젝트를 만들어 삭제 확인창을 연 뒤, 다른 요청으로 먼저 지우고 "예"를 누르면 `DELETE` 404, 모달이 계속 보이고 알림 없음, 콘솔에 `/sites/project/delete/106`(`console.log`)와 리소스 로드 실패만 | **같음(실행)**. 같은 조작에서 v1.6은 500, 모달이 계속 보이고 알림 없음, 콘솔에 같은 `console.log`. `projectList.scala.html:90` | v1.6 결함 유지 | 장부에 올린다 |
| site-F6 | 재현됨 | `POST /sites/unwatchUpdate` 200 후 모든 화면의 "새 버전" 배너가 사라진다. **앱을 다시 시작한 뒤에도 배너가 꺼져 있다**(영속). 업데이트 화면에 다시 켜는 조작이 없다 | **다름(실행)**. v1.6은 같은 조작 후 다시 시작하면 배너가 다시 나온다(메모리 `YobiUpdate.isWatched`, `SiteApp.java:316-320`). 컨트롤러 KDoc의 "다시 시작하면 켜진다"는 v1.6 이야기이고 v2 코드는 DB에 저장한다 | v2 신규 결함 (재시작 뒤에도 꺼지는 새 동작에 되돌리는 화면이 없음. KDoc 주석도 틀림) | 고친다 (위 요약) |
| site-F7 | 재현됨 | `/sites/update` 응답 시간이 487~621 ms(요청 안에서 `ls-remote`로 원격 저장소를 읽고 "Yona 2.0.0-rc1 is available"을 표시). 코드는 `check`가 꺼지면 백그라운드를 시작하지 않는다(UpdateCheckScheduler.kt:33-36) | **같음(실행)**. v1.6 `SiteApp.java:326-340`도 요청 안에서 `refreshVersionToUpdate()`(타임아웃 없음)를 부르고, 이 시험의 v1.6을 `application.update.check.use=false`로 띄웠는데도 배너 "2.0.0-rc1 is available"이 나와 **백그라운드 확인은 `check`와 관계없이 돈다**는 위키 서술이 v1.6에서는 사실(R-55-1은 v2의 의도적 변경). 그래서 요청 안 읽기는 v1.6도 같고(v2는 10초 타임아웃이 더해짐), 백그라운드가 `check`를 따르는 것은 v2 변경이다 | v1.6 결함 유지 (요청 안 원격 읽기). 위키 서술은 v1.6 기준 | 장부에 올린다 |
| site-F8 | 재현됨 | `GET /sites/mail?errorMessage=${7*7}` 응답에 `<p>49</p>`(표현식 실행). `?errorMessage=title.siteSetting`은 번역 문구, `?errorMessage=<b>x</b>`는 `<b>x</b>`가 그대로 렌더링. `?errorMessage=${T(java.lang.Math).abs(-5)*11}`은 500 | **다름(실행)**. v1.6은 같은 `errorMessage` 값을 키로 번역해 `Html()`로 넣을 뿐(`<b>x</b>` 그대로 렌더, 키 번역 됨) 표현식 `${7*7}`는 실행하지 않아 `49`가 나오지 않고 응답에 `errorMessage`도 없다. `mail.scala.html:30`(`@Html(Messages(errorMessage))`), `SiteApp.java:105-118` | v2 신규 결함 (서버 쪽 템플릿 표현식 주입, 사이트 관리자만 열 수 있어도 심각) | 고친다 (위 요약) |
| site-F9 | 재현됨 | `/sites/massmail`에서 "프로젝트별"을 고르고 선택 없이 "메일 쓰기": `POST /sites/mailList` 200, 제출된 폼 주소가 `mailto:`(수신자 없음). 없는 프로젝트 이름: 400. 주소 길이 한도는 사용자 수가 적어 확인 못 함(추측 부분은 재현 안 됨) | **같음(실행)**. 같은 조작이 `mailto:`를 만들고, 없는 프로젝트는 **500**이다(v2는 400으로 개선). `yobi.site.MassMail.js:98-114` | v1.6 결함 유지 | 장부에 올린다 |
| site-F10 | 재현됨 | `POST /sites/mail`(from, to, subject, body)이 200으로 화면을 다시 그린다(303 아님), 이 사본은 메일 서버가 없어 "Failed to send mail... Mailer has not been configured" 안내. 실제 재전송은 메일 서버가 없어 확인 못 함(재그림 방식은 확인) | **같음(실행)**. v1.6도 200으로 다시 그리고 "Mail has been sent."(서버가 없어도 성공 표시). `SiteApp.java:84-103`(`writeMail` 직접 호출, PRG 아님) | v1.6 결함 유지 | 장부에 올린다 |
| site-F11 | 재현됨 | 위키 `web/site-admin.md`와 실제 비교: 프로젝트 목록 `pageNum=0`과 `1`이 같은 첫 쪽(1부터), `pageNum=2`는 빈 쪽(`:44`는 "0부터"라 틀림). 프로젝트 삭제 뒤 저장소 디렉터리가 사라짐(`data2/repo/git/admin/delpv2.git` 없음, `:45`는 "지우지 않는다"). 내보내기 파일 이름 `yona-data-202611111023.json`(`:74`는 `yobi-data-`). 메일 실패 안내 표시(위 site-F10). 관리자 권한 자기 보호 있음(위 site-F3, `:34`는 "없음"). 사이드바 13개(`:18`은 8개) | v1.6 실행으로 v1.6 서술은 사실임을 확인: `pageNum=0`이 첫 쪽이고 `1`은 빈 쪽, 삭제 뒤 `repo/git/admin/delpv16.git` 남음, 파일 이름 `yobi-data-...json`, 사이드바 8개. 코드가 맞다 | 문서 문제 | 문서만 고친다 |
| site-F12 | 재현됨 | v2 사이드바: 사용자, 게시글, 이슈, 프로젝트, 메일 보내기, 대량 메일, 업데이트, 진단, 감사, 보안, OAuth 앱, Git LFS, SSO(13개) | **실행으로 확인**. v1.6 사이드바는 8개(사용자, 게시글, 이슈, 프로젝트, 메일, 대량 메일, 업데이트, 진단). `/sites/security`, `/sites/lfs`, `/sites/sso`, `/sites/oauth-apps`는 v1.6에서 모두 403 | 결함 아님 (의도된 추가 화면, 서술이 정확) | 조치 없음 |
| site-F13 | 재현됨 | `/sites/data`의 `input[type=submit]`에 `value`가 없어 `.value`가 빈 문자열, 브라우저 기본 글자로 그려진다. 가져오기 폼 `action="/sites/import" enctype=multipart/form-data` | **같음(실행)**. 똑같은 `<input type="submit">` | v1.6 결함 유지 | 장부에 올린다 |

### 사이트 설정 (site-settings.md, 모두 v2 추가 화면)

v1.6에는 `/sites/security`, `/sites/lfs`, `/sites/sso`, `/sites/oauth-apps`가 없다(실행 시 403). v1.6 대조는 "해당 없음"이다.

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| site-F14 | 재현됨 | `POST /sites/security/oauth` 303 `/sites/security`, `POST /sites/security/mcp` 303 `/sites/security`(관리자 세션으로 실제 값 저장 후 되돌림). 코드 위치가 `SiteOAuthAppsController.kt:53-66`임은 소스로 확인 | 해당 없음 | 결함 아님 (설계 서술, 주소로 부르면 됨) | 조치 없음 |
| site-F15 | 재현 불가 | `POST /sites/lfs`: `maxObjectMb=7&quotaMb=-1`은 400이고 저장값이 **그대로**(검증이 저장보다 먼저라 입력 오류로는 일부만 저장되지 않음), `maxObjectMb=7&quotaMb=3`은 303이고 둘 다 반영. 도중 실패(트랜잭션)는 DB 오류를 만들 수단이 없어 만들지 못했고, 화면 값이 MiB로 나눠 떨어지지 않는 저장값은 화면으로 저장할 방법이 없어(입력이 항상 MiB 정수) 내림 표시를 재현하지 못했다. 세 번 시도: 정상 저장, 검증 실패, 저장값 재조회 | 해당 없음 | 재현 불가 (입력으로는 부분 저장을 만들 수 없고 DB 장애가 필요함) | 조치 없음 (원하면 `SiteLfsController.save`에 `@Transactional`을 붙인다) |
| site-F16 | 재현됨 | LDAP 탭에서 `127.0.0.1:1`을 저장하고, 입력란을 `10.255.255.1:999`로 고친 뒤 저장하지 않고 "연결 확인"을 누르면 요청이 `POST /sites/sso/ldap/check`이고 **본문은 `_csrf`뿐**이다. 리다이렉트 뒤 호스트 입력란은 저장된 `127.0.0.1`로 돌아가 편집값이 사라진다(OIDC도 같은 구조의 입력 없는 폼, 소스 `sso.html:152-153`) | 해당 없음 | v2 추가 기능의 결함 | 고친다 (위 요약) |
| site-F17 | 일부 재현됨 | SAML 2 탭의 체크박스는 `enabled, autoCreate, linkByEmail, nameSync, initiateEnabled, signRequests`뿐으로 `allowUnverifiedEmail`이 없다(OIDC 탭에는 `#oidc-allow-unverified`가 있음). `allowUnverifiedEmail=true`를 직접 보낸 저장은 303으로 받아들여졌다. 저장된 값을 읽어 볼 화면과 감사 로그가 이 값을 싣지 않아 "저장 때마다 꺼진다"는 마지막 부분은 코드(`SiteSsoController.kt:117`의 `flag("allowUnverifiedEmail")`)로만 확인했다 | 해당 없음 | v2 추가 기능의 결함 | 고친다 (위 요약) |
| site-F18 | 재현됨 | 보안 화면의 OIDC 제공자 구역은 읽기 전용(입력란 없음)이고 꺼져 있으면 "꺼짐"과 키 생성 방법 안내(`site.security.oidc.howto`, `openssl genpkey ...`)만 있다 | 해당 없음 | 결함 아님 (의도된 설정 파일 방식, 안내가 있다) | 조치 없음 |
| site-F19 | 재현됨 | `/sites/security`에 폼이 3개(`/sites/security`, `/sites/security/oauth`, `/sites/security/mcp`)이고 서로 입력이 겹치지 않는다. 저장은 영역마다 303 뒤 화면을 새로 그린다 | 해당 없음 | v2 추가 기능의 결함 (경미, 설계 한계) | 장부에 올린다 |
| site-F20 | 재현됨 | `site.oauthApps.registration.USER`, `.CIMD` 키만 있고 `.MANUAL`은 없다(`messages.properties:1705-1706`). `user.role.site_admin` 키는 어디에도 없다. `/sites/oauth-apps`는 앱이 있을 때 `Users`로 정상 번역되어 원시 키가 화면에 나오지 않는다 | 해당 없음 | 결함 아님 (정보성 메모) | 조치 없음 |

## 범위 점검

| 목록 번호 | 결과 파일의 판정 |
|---|---|
| organization-F17 | v1.6 결함 유지 |
| organization-F18 | v1.6 결함 유지 |
| organization-F19 | v1.6 결함 유지 |
| organization-F20 | 결함 아님 |
| organization-F21 | v2 추가 기능의 결함 |
| organization-F22 | 결함 아님 |
| organization-F23 | 결함 아님 |
| organization-F24 | 문서 문제 |
| organization-F2 | v1.6 결함 유지 |
| organization-F3 | v1.6 결함 유지 |
| organization-F4 | v1.6 결함 유지 |
| organization-F5 | v1.6 결함 유지 |
| organization-F6 | v1.6 결함 유지 |
| organization-F7 | v1.6 결함 유지 |
| organization-F8 | v2 신규 결함 |
| organization-F9 | v1.6 결함 유지 |
| organization-F10 | v1.6 결함 유지 |
| organization-F11 | 결함 아님 |
| organization-F12 | v1.6 결함 유지 |
| organization-F13 | v2 추가 기능의 결함 |
| organization-F14 | 문서 문제 |
| organization-F15 | 결함 아님 |
| organization-F16 | v1.6 결함 유지 |
| site-F14 | 결함 아님 |
| site-F15 | 재현 불가 |
| site-F16 | v2 추가 기능의 결함 |
| site-F17 | v2 추가 기능의 결함 |
| site-F18 | 결함 아님 |
| site-F19 | v2 추가 기능의 결함 |
| site-F20 | 결함 아님 |
| site-F1 | 문서 문제 |
| site-F3 | v2 신규 결함 |
| site-F4 | v1.6 결함 유지 |
| site-F5 | v1.6 결함 유지 |
| site-F6 | v2 신규 결함 |
| site-F7 | v1.6 결함 유지 |
| site-F8 | v2 신규 결함 |
| site-F9 | v1.6 결함 유지 |
| site-F10 | v1.6 결함 유지 |
| site-F11 | 문서 문제 |
| site-F12 | 결함 아님 |
| site-F13 | v1.6 결함 유지 |
