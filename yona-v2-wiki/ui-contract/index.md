---
title: 화면 동작 계약(v2 UI 계약) 안내
type: ui-contract
sources: [src/main/resources/templates/**, src/main/resources/static/assets/javascripts/**, src/main/kotlin/dev/yona/web/**, src/main/kotlin/dev/yona/security/**]
updated: 2026-11-11
---

# 화면 동작 계약(v2 UI 계약)

`ui-improvements` 브랜치의 **템플릿 230개 전부**를 기준으로, 화면의 모든 단추, 링크, 폼, 단축키, 레이아웃 규칙, 팝업과 모달을 스크립트, 컨트롤러, 권한까지 따라가 정리한 문서이다. 새 UI는 이 계약대로 동작하면 기존 기능과 같아야 한다. 조사는 담당 영역 여덟 개로 나누어 했고, 각 파일 끝의 "범위 점검" 표에 템플릿별로 어느 절에서 다루었는지 적었다(230개 모두 언급됨을 확인했다).

**`next`와의 관계(2026-11-11)**: 이 조사로 찾아 고친 소스 변경(A-457~A-465)은 `next`에도 같은 내용으로 들어 있고 두 브랜치의 `src`는 같다(2026-11-11 확인). 이 `ui-contract/`와 재현 기록은 `ui-improvements`에만 있다. 아래 표에서 "수정됨"이라고 한 항목은 두 브랜치 모두에 적용되어 있다.

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

## 수정으로 달라진 화면 계약 (2026-11-11, A-462, A-464, A-465)

계약 조사 뒤 "v1.6 결함 유지"로 판정된 항목을 고치고 선생님 결정 28건을 구현하면서 아래 규칙이 **새 UI가 지켜야 할 계약**이 되었다. 항목별 근거는 각 영역 파일의 `[검증: 수정함(...)]` 표시와 `verification/v16-fixes-W1~W4.md`에 있다.

**주소와 스크립트 규칙**
- **컨텍스트 경로**: 서버를 하위 경로(`yona.application-context`, 예 `https://서버/yona/`)에 올려도 모든 내부 주소는 접두어를 가져야 한다. 템플릿은 `@{...}`나 `@{|/${값}|}`를 쓰고, 서버가 만드는 주소(아바타, 로고, 이벤트 줄 HTML)는 `SiteUrls.path`를 쓴다. 정적 스크립트는 전역 `window.yonaContextPath`를 앞에 붙인다. 인라인 `background-image:url(...)`도 같다. 시험 `ContextPathLinksTest`가 `/yona` 경로로 32개 화면을 그려 접두어 없는 주소를 찾는다. 사용자가 본문에 직접 쓴 `/assets/...` 같은 주소는 대상이 아니다.
- **알림 문구**: `$yona.alert`와 `$yona.notify`는 넘긴 글을 HTML로 해석한다. 사용자가 정한 값(라벨, 프로젝트, 사용자 이름, 서버 응답 본문)은 `$yona.escapeHtml`로 감싸서 넘긴다. 고정 문구는 `Messages(키)`를 쓰고 영어 문구를 박지 않는다.
- **중복 제출**: 만들기 폼은 `data-submit-once`를 달면 제출 뒤 제출 단추가 잠긴다(`esm/ui/SubmitOnce.js`). 토큰 만들기와 OAuth 앱 폼에 적용했다.
- **상태를 바꾸는 요청**: 이슈 다음 상태는 화면이 POST로 보낸다(`POST .../issue/{n}/nextstate`, GET도 받는다).

**기능 규칙이 바뀐 화면**
- **프로젝트 메뉴를 끈 기능**: 이슈나 마일스톤 메뉴를 끄면 그 주소(`issues`, `issue/*`, `issueform`, `milestones`, `milestone/*`, `newMilestoneForm`)는 404이다. 라벨 관리(`issue/label...`)는 프로젝트 설정이 쓰므로 열려 있다. 게시판 목록은 메뉴가 꺼져 있으면 글쓰기 단추가 없다.
- **로그인 화면**: 같은 아이디로 5번 틀리면 15분 동안 거절한다(폼은 `user.login.tooMany` 안내, JSON은 429). 삭제된 계정은 없는 계정과 같은 `user.login.invalid`이다.
- **가입, 비밀번호 변경, 재설정, 최초 관리자 설정**: 새 비밀번호는 8자 이상 128자 이하이고 흔한 비밀번호, 같은 글자 반복, 아이디나 이메일과 같은 값은 거절한다(400, 토스트로 안내). 화면의 길이 검사도 8자이다. 가입 아이디는 한글을 허용하고, 구분 기호(`_`, `.`)를 연달아 쓰는 이름은 만들 수 없다.
- **기기 인증**: 코드를 5번 틀리면 15분 잠긴다(429). 승인 화면에 요청한 기기의 주소와 시각을 보인다.
- **OAuth 동의 폼**: 제출에 CSRF 토큰이 필요하다.
- **사이트 관리**: 자기 자신과 기본 admin은 삭제와 잠금을 할 수 없다(삭제 403, 잠금은 안내와 함께 거절). 보안 설정 화면은 저장 단추 옆에 어느 영역이 저장되는지 안내한다.
- **이슈 일괄 변경**: 상태는 OPEN, CLOSED만, 같은 배타 분류 라벨 둘은 400이다.
- **코드 주고받기**: 병합된 요청의 보낸 사람은 자기 포크의 구성원이면 보낸 브랜치를 지우고 되살릴 수 있다. 변경 탭의 리뷰 카드 댓글 수는 커밋 화면과 같이 둘 이상일 때 전체 수이다.
- **이전(Migration) 화면**: 값이 빈 작업 계정 경고는 없고 조직 관리자 경고만 있다. 서버가 요청을 거절하면(403, 404) 상태와 주소를 시스템 메시지 영역에 보인다. 시작 확인 창에 기록 이슈가 올라간다는 안내가 있다.
- **도움말과 단축키**: 도움말의 프로젝트 키 안내는 메뉴 설정과 코드 접근 권한을 따른다. `CTRL+SHIFT+ENTER`가 동작한다. 사용자 메뉴 목록을 읽지 못해도 사이드바 단추가 동작한다.

**유지한 것**: 웹훅 비밀 값 평문 표시(project-F21)와 죽은 코드 6건은 그대로이다(코드 4, 라벨 분류 조회 경로 등은 "쓰지 않음" 주석만 있다). 도움말 본문(FAQ, 마크다운 안내)은 한국어만 있다.

## 교차 계약 요약

여러 영역에 공통인 규칙이다. 근거와 줄 번호는 각 파일에 있다.

- **접근 검사 체인**: 요청은 `AccessChainInterceptor`가 어노테이션(`ProjectCheckAnnotations`, `@SiteManagerOnly` 등)을 단계로 풀어 순서대로 검사한다. 관리자 판정은 `SessionPermissions.siteManager`이다(`admin-role.md`, `site.md`).
- **CSRF**: `POST /oauth2/authorize`(OAuth 동의 폼 제출)는 이제 토큰이 필요하다(A-464). 읽기 요청과 토큰 전용 API 경로(`/-_-api`, `/mcp`, `/svn`, git, lfs, 사내 SSH 인증)와 `Authorization` 또는 `Yona-Token` 헤더 요청은 검사하지 않는다. 그 밖의 쓰기 요청은 쿠키 `XSRF-TOKEN`과 같은 값을 헤더 `X-XSRF-TOKEN`이나 인자 `_csrf`로 보내야 하고, 없거나 틀리면 `Accept`와 상관없이 `403`과 Spring 기본 오류 JSON(`{"timestamp","status","error","path"}`)이다(`YonaCsrfMatcher`, 2026-11-11 브라우저로 확인). 화면의 `yona.Csrf.js`가 `fetch`와 XHR에 헤더를 자동으로 붙이므로 스크립트를 거치는 요청은 신경 쓰지 않아도 된다(`shell-scripts.md`).
- **응답 형태**: 화면 요청은 HTML, 스크립트 요청은 JSON이며, 오류는 영역마다 다르다(상태 코드와 본문 모양은 영역별 "요청과 응답" 절). `HX-Request`가 오면 목록 영역만 돌려주는 곳이 있다(D-96, D-98).
- **전역 단축키**: `ShortcutKey`가 `window`의 `keydown`에 하나의 표로 등록하며 같은 키는 나중에 등록한 쪽이 이긴다. 본문 다음에 전역 스크립트가 로드되므로 **전역이 화면 키를 덮어쓴다**(코드 이력의 `A`, R-147). 표는 `shell-scripts.md`와 [ui-shortcuts.md](../architecture/ui-shortcuts.md)에 있다.
- **브라우저 저장소 키**: `shallWeOpenLeftNavigation`(핀 사이드바) 등 `localStorage` 키 목록은 `shell-scripts.md`에 있다.
- **인증**: 쿠키 `JSESSIONID`, `yona.token`(30일, HMAC 서명), `yona_device`, `XSRF-TOKEN`, `daysAgo`. 같은 아이디로 비밀번호를 5번 틀리면 15분 잠금(429 `user.login.tooMany`), 2단계 코드와 기기 코드는 사용자별 5회 실패에 15분 잠금, 토큰 추측은 주소별 5회 실패에 15분 차단이다. 잠금 횟수는 DB `attempt_record`에 저장해 재시작으로 풀리지 않는다(`user-security.md` 2절, A-464). 새 비밀번호는 8자 이상 128자 이하이고 흔한 비밀번호와 아이디, 이메일과 같은 값은 거절한다(A-465).
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

### 서버 권한 후보를 데이터를 넣어 재현한 결과 (A-458)

검증용 앱에 비공개 프로젝트 `admin/secretproj`(이슈 1, 댓글, 글 1, 브랜치 `topsecret-feature`와 파일 `secret.txt`가 든 커밋, 비공개 PR)를 만들고, 그 프로젝트의 구성원이 아닌 `plainuser`로 요청했다(기준선: 이슈 보기와 글 보기는 둘 다 403). 요청은 화면 스크립트를 거치지 않고 올바른 CSRF 토큰으로 직접 보냈다.

| 발견 사항 | 재현 결과 | v1.6 |
|---|---|---|
| pullrequest-F1 | **재현됨, 심각.** `newPullRequestForm`이 비공개 프로젝트의 브랜치 이름을 돌려준다. `newPullRequest/mergeResult`가 커밋 메시지를 돌려준다(`LAUNCH-CODE` 노출). 비구성원이 보내는 쪽을 비공개 프로젝트로 해 자기 프로젝트에 PR을 만들면 서버가 비공개 저장소를 가져와 **자기 저장소의 `refs/yona/pull/N/head`에 복사**한다. 그 커밋의 `secret.txt` 내용(`TOPSECRET-FILE-CONTENT`)을 자기 저장소에서 읽을 수 있다 | 같음(`PullRequestApp`이 URL 프로젝트의 `@IsCreatable(FORK)`만 본다) |
| pullrequest-F2 | **재현됨.** 비구성원이 비공개 PR 경로로 직접 댓글을 쓰면 403이지만, 자기 프로젝트 경로(`/plainuser/ownerproj/pullRequest/{DB번호}/comments`)에 비공개 PR의 DB 번호를 넣으면 303으로 등록되고 그 댓글이 비공개 PR의 변경 탭에 나타난다 | 같은 구조(`PullRequestApp.java:607`이 번호로만 찾는다) |
| issue-F5 | **재현됨.** `detectChange`가 비구성원에게 마지막 댓글 작성자 이름, 댓글 수, 본문 SHA-1, 수정 시각을 준다. `commentNotiReceivers`가 알림 수신자(관리자 로그인 ID와 이름)를 준다. `findSharer`는 열려 있으나(`[]`) 이 데이터에서는 내용이 없었다 | `commentNotiReceivers`는 코드 주석에 v1.6도 검사 없음. `detectChange`는 대조하지 않음 |
| issue-F6 | **재현됨.** 비구성원이 읽을 수 있는 공개 프로젝트 경로(`/admin/hglfs/issue/1/comment/110/vote`)로 비공개 이슈의 댓글 110에 추천이 들어가고(관리자 화면에 추천 표시 0에서 1) `unvote`로 되돌린다 | 같음(`VoteApp.java:93`, 번호로만 찾음) |
| issue-F4 | **재현됨.** 수정 권한이 없는 비구성원이 담당자를 바꾸는 요청을 보내면 200 `{"issue": 경로}`만 오고 바뀌지 않는다 | 같음(코드 주석) |
| board-F8 | **재현됨.** 비구성원이 제목을 빈 값으로 글 수정 요청을 보내면 400 응답에 저장된 글 제목과 본문(`SECRET-POST`)이 담긴다 | 같은 순서(`BoardApp.java:341-345`) |
| board-F2 | **재현됨.** 비구성원이 `notice=on`으로 공개 프로젝트에 글을 쓰면 공지가 된다(공지 표시 2, 공지 없는 글 0) | 같음(공식 v1.16.0 동작, 시험이 고정) |
| usersec-F5 | **재현됨.** 2단계 인증을 모두 강제하면 `/user/editform/two-factor/security-key`가 `303 /user/editform/two-factor`로 되돌려졌다. v1.6에 없는 추가 기능(강제)의 결함이다 | v1.6에 없음 |

**모두 고쳤다.** usersec-F5(v1.6에 없는 2단계 강제의 결함)는 A-458에서, 나머지 일곱 건과 pullrequest-F3(코드 접근을 구성원으로 제한한 프로젝트의 풀 리퀘스트 개요와 변경 화면, R-159)은 선생님 지시로 서버 권한 검사를 더해 A-459에서 고쳤다. v1.6과 달라진 부분은 `architecture/differences-from-v16.md`의 "서버 권한 검사 보강" 표에 있다. 같은 부류로 `MentionListController`가 풀 리퀘스트 번호로 다른 프로젝트의 참여자와 커밋 작성자를 후보로 내던 것도 함께 고쳤다. 정상 사용자의 화면과 계약은 바뀌지 않고 권한 없는 요청의 응답만 403 또는 404가 된다. 새 UI의 정보 구조를 짤 때 서버가 화면의 숨김에 기대지 않는다는 전제를 쓸 수 있다.

그 밖에 코드 읽기로만 근거를 둔 항목(issue-F5의 `findSharer` 내용, board-F8과 위키의 어긋남 등)은 각 파일에 "추측" 또는 "코드 읽기"로 표시되어 있다.

## 재현과 v1.6 대조의 결과 (2026-11-11)

조사 담당이 코드 읽기로 적은 발견 사항 221건을 모두 v2와 공식 v1.16.0(v1.6)에 같은 요청과 같은 화면 동작을 보내 확인했다. 요약은 [verification/index.md](verification/index.md)이고 영역별 결과는 같은 폴더에 있다. 각 발견 사항의 행에 `[검증: 판정, 수정 상태]`가 붙어 있다.

- v1.6 결함 유지 139건(지침대로 고치지 않음), 결함 아님 32건, v2 추가 기능의 결함 28건, 문서 문제 11건, v2 신규 결함 10건, 재현 불가 1건.
- v2 쪽 결함(신규 10건과 추가 기능 28건) 가운데 선택 대기 8건과 조치 없음 1건(`organization-F13`)을 뺀 나머지는 모두 고쳤고(A-460), 선택 대기 8건의 권장안은 `decisions.md` A-461에 있다.
- `next`에도 같은 소스 변경이 들어 있다. 이 계약 문서와 검증 결과는 `ui-improvements`에만 있다.

## 이 문서가 확정한 것

- 붉은 "관리자로 로그인" 띠는 관리자 권한이 아니라 로그인 ID가 `admin`일 때 뜬다(`LayoutModelAdvice.kt:76`, v1.6도 같음). 관리자가 다른 사용자로 로그인하는 기능은 v2와 v1.6 모두에 없다. [ui-ia-gitlab.md](../architecture/ui-ia-gitlab.md)의 해당 서술은 이 문서에 맞춰 고친다.
- 사이트 관리자만 가진 권한과 일반 규칙을 우회하는 지점의 전체 목록은 [admin-role.md](admin-role.md)에 있다.
