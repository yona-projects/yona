---
title: 화면 동작 계약(v2 UI 계약) 안내
type: ui-contract
sources: [src/main/resources/templates/**, src/main/resources/static/assets/javascripts/**, src/main/kotlin/dev/yona/web/**, src/main/kotlin/dev/yona/security/**]
updated: 2026-11-11
---

# 화면 동작 계약(v2 UI 계약)

`ui-improvements` 브랜치의 **템플릿 230개 전부**를 기준으로, 화면의 모든 단추, 링크, 폼, 단축키, 레이아웃 규칙, 팝업과 모달을 스크립트, 컨트롤러, 권한까지 따라가 정리한 문서이다. 새 UI는 이 계약대로 동작하면 기존 기능과 같아야 한다. 조사는 담당 영역 여덟 개로 나누어 했고, 각 파일 끝의 "범위 점검" 표에 템플릿별로 어느 절에서 다루었는지 적었다(230개 모두 언급됨을 확인했다).

읽는 법: 각 파일은 영역 요약, 화면별 절(개요, 레이아웃, 동작 요소 표, 스크립트, 단축키, 팝업, 요청과 응답, 권한, 빈 상태와 경계 값), 발견 사항, 범위 점검 순서이다. 동작 요소 표의 "조건"은 화면에 보이는 조건이고 "권한"은 서버가 실제로 막는 규칙이며, 둘이 다르면 따로 적었다. 줄 번호는 조사 시점(2026-11-11)의 `ui-improvements` 기준이다.

## 영역별 파일

| 영역 | 파일 | 다루는 템플릿 |
|---|---|---|
| 공통 틀과 전역 스크립트 | [shell.md](shell.md), [shell-common-fragments.md](shell-common-fragments.md), [shell-scripts.md](shell-scripts.md) | `layout/`, `common/`, `index/`, `error/`, `help/`(57개). 전역 단축키, 저장소 키, htmx와 CSRF 설정 포함 |
| 프로젝트 | [project.md](project.md), [project-settings.md](project-settings.md), [milestone.md](milestone.md) | `project/`, `milestone/`(31개) |
| 이슈 | [issue.md](issue.md), [issue-list.md](issue-list.md), [issue-view.md](issue-view.md), [issue-form.md](issue-form.md), [issue-my.md](issue-my.md) | `issue/`, `reviewthread/`(23개) |
| 코드 주고받기와 코드 | [pullrequest.md](pullrequest.md), [code.md](code.md), [code-diff-script.md](code-diff-script.md), [code-vcs.md](code-vcs.md) | `pullrequest/`, `code/`(28개) |
| 그룹과 사이트 관리 | [organization.md](organization.md), [organization-lists.md](organization-lists.md), [organization-oauth.md](organization-oauth.md), [site.md](site.md), [site-settings.md](site-settings.md) | `organization/`, `site/`(32개) |
| 게시판, 위키, 검색 등 | [board.md](board.md), [wiki.md](wiki.md), [search.md](search.md), [misc.md](misc.md) | `board/`, `wiki/`, `search/`, `migration/`, `welcome/`, `audit/`(25개) |
| 사용자와 인증 | [user.md](user.md), [user-security.md](user-security.md), [oauth.md](oauth.md) | `user/`, `oauth/`, `oauth2/`(35개) |
| 사이트 관리자 역할(횡단) | [admin-role.md](admin-role.md) | 관리자 전용 요소, 우회 규칙, 설정 영향, 전체 템플릿 대상 |

## 교차 계약 요약

여러 영역에 공통인 규칙이다. 근거와 줄 번호는 각 파일에 있다.

- **접근 검사 체인**: 요청은 `AccessChainInterceptor`가 어노테이션(`ProjectCheckAnnotations`, `@SiteManagerOnly` 등)을 단계로 풀어 순서대로 검사한다. 관리자 판정은 `SessionPermissions.siteManager`이다(`admin-role.md`, `site.md`).
- **CSRF**: 읽기 요청과 토큰 전용 API 경로(`/-_-api`, `/mcp`, `/svn`, git, lfs, 사내 SSH 인증)와 `Authorization` 또는 `Yona-Token` 헤더 요청은 검사하지 않는다. 그 밖의 쓰기 요청은 쿠키 `XSRF-TOKEN`과 같은 값을 헤더 `X-XSRF-TOKEN`이나 인자 `_csrf`로 보내야 하고, 없거나 틀리면 `Accept`와 상관없이 `403`과 Spring 기본 오류 JSON(`{"timestamp","status","error","path"}`)이다(`YonaCsrfMatcher`, 2026-11-11 브라우저로 확인). 화면의 `yona.Csrf.js`가 `fetch`와 XHR에 헤더를 자동으로 붙이므로 스크립트를 거치는 요청은 신경 쓰지 않아도 된다(`shell-scripts.md`).
- **응답 형태**: 화면 요청은 HTML, 스크립트 요청은 JSON이며, 오류는 영역마다 다르다(상태 코드와 본문 모양은 영역별 "요청과 응답" 절). `HX-Request`가 오면 목록 영역만 돌려주는 곳이 있다(D-96, D-98).
- **전역 단축키**: `ShortcutKey`가 `window`의 `keydown`에 하나의 표로 등록하며 같은 키는 나중에 등록한 쪽이 이긴다. 본문 다음에 전역 스크립트가 로드되므로 **전역이 화면 키를 덮어쓴다**(코드 이력의 `A`, R-147). 표는 `shell-scripts.md`와 [ui-shortcuts.md](../architecture/ui-shortcuts.md)에 있다.
- **브라우저 저장소 키**: `shallWeOpenLeftNavigation`(핀 사이드바) 등 `localStorage` 키 목록은 `shell-scripts.md`에 있다.
- **인증**: 쿠키 `JSESSIONID`, `yona.token`(30일, HMAC 서명), `yona_device`, `XSRF-TOKEN`, `daysAgo`. 비밀번호 로그인에는 횟수 제한이 없고, 2단계 코드는 사용자별 5회 실패에 15분 잠금, 토큰 추측은 주소별 5회 실패에 15분 차단이다(`user-security.md` 2절).
- **사이트 설정**: DB 설정은 요청마다 읽어 즉시 반영되고, 파일 설정(`yona.*`, OIDC 서명 키, 메일 서버, 업데이트 확인)은 재시작이 필요하다(`site.md`, `admin-role.md`).

## 조사 결과의 확인 상태 (2026-11-11)

조사 담당은 앱을 실행하지 못해 일부 항목을 코드 읽기만으로 "추측"이라 표시했다. 총괄이 검증용 앱과 Playwright로 그 가운데 영향이 큰 항목을 직접 확인했다. 모든 발견 사항은 각 파일의 "발견 사항" 절에 있고, 아래 표의 항목은 해당 행에 확인 결과를 표시했다.

### v2에서 새로 생긴 결함을 확인하고 고친 것 (A-457)

| 발견 사항 | 확인 | 조치 |
|---|---|---|
| project-F15, board-F3, code-F1 | select2 4.x의 `change`에는 `val`이 없어 프로젝트 생성의 Subversion 경고가 오류로 동작하지 않았고(`TypeError`), 이력과 코드 보기의 브랜치 이동, 글 보기의 라벨 변경도 같은 원인 | 다섯 곳을 `event.target.value`, `$(this).val()`로 고쳤다. `Select2EventValueTest`가 소스에 같은 패턴이 남지 않게 지킨다. 브라우저로 프로젝트 생성, 이력 브랜치 이동, 글 라벨 변경(요청 본문 `["5"]`, 200)을 확인했다 |
| project-F2, milestone-F2, organization-F1 | `data-toggle="item-search"` 입력란이 동작하지 않았다(`$.fn.search` 없음). v1.6의 `jquery.search.js`를 A-410 정리에서 지웠다 | `esm/ui/ItemSearch.js`로 같은 동작을 복원했다(`ItemSearchModuleTest`). 그룹 홈에서 `alpha` 입력 시 해당 프로젝트만 남고 지우면 전체가 돌아옴을 확인했다 |
| board-F7(와 이슈 작성, 수정의 같은 문구) | 제목이 255자를 넘으면 `Maximum length is {0}`가 그대로 나옴 | 네 템플릿이 인자 255를 넘긴다(`IssueCreateTest`). v1.6은 Play가 255를 채웠다 |
| site-F2 | `/sites/userList?pageNum=0`이 500(음수 쪽 번호). 없는 사용자 조작이 이 주소로 되돌려 보낸다. 내 이슈(`/user/issues?pageNum=0`)도 같았다 | 둘 다 0 아래로 내리지 않는다(`SitePageNumZeroTest`). 글 목록과 이슈 목록은 이미 `pageNum<1`이면 400으로 포팅되어 있어 그대로 둔다 |
| shell-F2 | 네트워크 오류일 때 `yona.http`의 실패 객체가 `readyState` 4라 로그인 대화상자와 프로젝트 전역의 "네트워크 오류" 분기가 닿지 않았다(오프라인 브라우저로 확인) | `http.js`가 응답이 없으면 jqXHR처럼 0을 준다(`http.test.mjs`) |

### 브라우저로 확인했고 v1.6도 같아 그대로 둔 것 (장부 R-148 이후)

| 발견 사항 | 확인 | v1.6 |
|---|---|---|
| pullrequest-F4 | 병합된 코드 주고받기에서 폴링이 한 번 돈 뒤 `복구`가 POST가 아니라 링크 이동(GET)이 된다. 폴링 전의 `삭제`는 정상 | 같은 구조(`sStateHTML` 초기값 `""`, 요소마다 연결) |
| shell-F3 | `beforeunload`가 발생하면(이탈 확인을 취소해도) 그 화면의 단축키가 모두 죽는다 | 같음 |
| shell-F21 | 내 이슈 빠른 검색의 "즐겨찾기" 라벨을 누르면 `POST /user/favoriteIssues/undefined`가 400 | 같은 마크업 |
| board-F1, shell-F1 | 키 `title.boardDetail`이 번들에 없다 | v1.6 `conf/messages`에도 없음 |
| 코드 이력 `A` | 2페이지에서 `A`가 이전 페이지 대신 내 이슈로 간다 | 같은 스크립트 순서(R-147) |

### 조사 담당의 판단이 틀려 정정한 것

- **issue-F1**: "수정 폼의 상태 드롭다운이 동작하지 않는다"는 틀렸다. `common/scripts.html:77-82`가 `.btn-group[data-name]`을 전부 `yona.ui.Dropdown`으로 초기화한다. 브라우저에서 "닫힘"을 누르면 `state=CLOSED`가 실린다.

### 코드 읽기로만 근거를 둔 항목

아래는 환경을 더 만들어야 하거나 사용자 설정이 있어야 재현되는 항목이라 코드 읽기 근거로 남겼고, 각 파일에 "추측" 또는 "코드 읽기"로 표시되어 있다. 새 UI를 만들 때 이 항목에 기대기 전에 먼저 재현하기를 권한다.

- 2단계 인증 강제 설정에서 "보안 키 추가" 화면이 목록으로 되돌려진다(usersec-F5, 강제 설정과 2단계 미사용 사용자가 필요).
- 서버 권한 검사 누락(issue-F5, F6, pullrequest-F1, F2, board-F8 등)은 코드 읽기로 확인한 것이며 시험이 없다.
- 위키가 코드와 어긋난 곳(usersec-F9, site-F1, project-F4 등)은 각 파일의 "기존 위키 정합" 항목을 본다.

## 이 문서가 확정한 것

- 붉은 "관리자로 로그인" 띠는 관리자 권한이 아니라 로그인 ID가 `admin`일 때 뜬다(`LayoutModelAdvice.kt:76`, v1.6도 같음). 관리자가 다른 사용자로 로그인하는 기능은 v2와 v1.6 모두에 없다. [ui-ia-gitlab.md](../architecture/ui-ia-gitlab.md)의 해당 서술은 이 문서에 맞춰 고친다.
- 사이트 관리자만 가진 권한과 일반 규칙을 우회하는 지점의 전체 목록은 [admin-role.md](admin-role.md)에 있다.
