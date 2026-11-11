---
title: UI 계약 - 이전, 시작 안내, 감사 로그 (migration, welcome, audit)
type: ui-contract
sources:
  - src/main/resources/templates/migration/home.html
  - src/main/resources/templates/layout/migration-layout.html
  - src/main/resources/templates/welcome/secret.html
  - src/main/resources/templates/welcome/restart.html
  - src/main/resources/templates/audit/list.html
  - src/main/kotlin/dev/yona/web/migration/MigrationController.kt
  - src/main/kotlin/dev/yona/domain/migration/MigrationService.kt
  - src/main/kotlin/dev/yona/domain/migration/GithubTokenClient.kt
  - src/main/kotlin/dev/yona/web/welcome/SiteSecretInterceptor.kt
  - src/main/kotlin/dev/yona/web/welcome/FakeController.kt
  - src/main/kotlin/dev/yona/security/SiteSecret.kt
  - src/main/kotlin/dev/yona/web/audit/AuditControllers.kt
  - src/main/kotlin/dev/yona/web/audit/AuditViewSupport.kt
  - src/main/resources/static/assets/javascripts/esm/service/Migration.js
updated: 2026-11-11
---

# UI 계약: 이전(migration), 시작 안내(welcome), 감사 로그(audit)

기존 설명: [web/migration.md](../web/migration.md), [web/welcome.md](../web/welcome.md), [architecture/audit-log.md](../architecture/audit-log.md). 공통 규칙(CSRF 토큰 싣기, 303 이동, 접근 검사 순서)은 [board.md](board.md#공통-규칙)를 본다.

## 영역 요약

| 템플릿 | URL (메서드) | 컨트롤러 | 용도 |
|---|---|---|---|
| `migration/home.html` | `/migration` (GET) | `MigrationController.home` (`web/migration/MigrationController.kt:39-61`) | Yona 프로젝트를 GitHub로 옮기는 화면(Vue 3 앱) |
| `welcome/secret.html` | 비밀 키를 정하기 전의 모든 요청(POST `/`가 제출) | `SiteSecretInterceptor.pendingScreen` (`web/welcome/SiteSecretInterceptor.kt:59-73`), `FakeController.fake` (`web/welcome/FakeController.kt:16`) | 최초 기동: 기본 관리자 계정과 비밀 키 정하기 |
| `welcome/restart.html` | 같은 인터셉터 | 같음 | 비밀 키를 정한 뒤 재시작 안내 |
| `audit/list.html` | (조각 `auditList(exportable, verifiable, showFilter)`) `/sites/audit`, `/organizations/{name}/audit`, `/user/editform/audit` | `web/audit/AuditControllers.kt`의 `SiteAuditController` `:59`, `OrganizationAuditController` `:94`, `UserAuditController` `:116` | 감사 로그 표, 필터, 내려받기, 무결성 검증 |

템플릿 없는 이전 경로(JSON, 이전 화면의 스크립트가 부른다, 모두 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`): `GET /migration/projects` `:63`, `/migration/{owner}/projects/{projectName}` `:68`, `.../labels` `:73`, `.../issuelabel` `:78`, `.../milestones` `:83`, `.../issues` `:88`, `.../posts` `:94`.

---

# 이전 (`migration/home.html`)

### 1. 화면 개요

- `home`의 쿼리: `code`(GitHub OAuth 돌려받는 코드), `state`. 설정 `yona.github.allow-migration`(기본 꺼짐, `YonaProperties.kt:320-326`)이 꺼져 있으면 `ErrorViews.forbidden("error.forbidden.or.not.allowed")` 403이다. 내보내기 JSON 경로는 이 설정과 무관하게 열려 있다(시험 `MigrationExportTest` "화면 설정이 꺼져 있으면 화면은 403이지만 JSON 주소는 열려 있다").
- 접근: 메서드에 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`가 있어 익명은 로그인 폼 303(+`user.login.alert`).
- 세션 `state`(R-58-2): `code`가 있으면 세션에 둔 `migration.oauth.state`와 `state`가 같아야 한다(다르거나 세션에 없으면 403). 맞으면 한 번 쓰고 지운다. 이어 `code`를 `GithubTokenClient.exchange`로 토큰으로 바꾼다(공백 `code`는 없는 것과 같다). 모든 응답에서 새 `state`(UUID 둘을 이은 64자)를 세션에 저장하고 화면에 넘긴다.
- 모델: `oauthState`, `title`(`Migration`), `token`(없으면 빈 글자), `loginId`, `email`. 문서 제목은 `site.siteName`이다(`home.html:13`).
- 틀: 전용 `layout/migration-layout`(푸터 없음, `#main` 안에 관리자 띠, 업데이트 알림, 상단 바, 본문, `common/scripts`). `<head>`에서 jQuery 3.7.1, `yona.Csrf.js`, `yona.Common.js`, `vue.global.prod.js`, ES 모듈 `esm/service/Migration.js`를 불러온다(`migration-layout.html`).
- 화면은 `#migration-app`을 Vue 3(옵션 API, 화면 안에서 템플릿 컴파일)이 그린다. 서버 값은 `data-oauth-state`, `data-token`, `data-login-id`, `data-email`로 넘긴다(`home.html:15`). `v-cloak`은 `[v-cloak]{display:none}`.

### 2. 레이아웃 계약

- 본문 전체가 `v-if="token"`인 `.header-pannel` 하나이다. **토큰이 없으면 본문이 비어 있다**(스크립트가 곧바로 GitHub 인증 화면으로 이동시킨다).
- 위에서 아래: 메시지 상자 `#system-msg`(진행 메시지와 오류 JSON), 원본 → 대상 제목 줄(선택 전에는 경고색 안내 `migration.selectSource`, `migration.selectDestination`), 가운데 3열 `.row.source-destination`: 원본 목록 `col-lg-4`, 대상 목록 `col-lg-4`, 상태 `col-lg-6`(`home.html:40-152`). 목록 폭 합이 14/12를 넘는 레이아웃이다(`col-lg-4 + col-lg-4 + col-lg-6`, 줄이 넘어가 세 번째가 다음 줄로 내려간다. 추측: 의도는 v1.6과 같은 구성).
- 상태 열: 진행 막대(`importResult.count / expectedImportCount`, 100% 미만이면 `bg-danger`, 100%면 `bg-success`), 표(마일스톤, 이슈, 게시글 행과 주의 행), 담당자 매핑 표(원본 프로젝트 담당자 목록이 있고 대상을 골랐을 때만, `v-show="destination.projectName"`).
- 이미 대상에 데이터가 있으면 해당 행 칸에 `alert-bg` 배경과 `import-warning` 컴포넌트가 나온다.
- 반응형: `col-lg-*`만 쓴다(992px 미만에서 세 열이 쌓임). `hide-in-mobile`은 없다.

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| (자동) 인증 이동 | 스크립트 | `token`이 비었거나 2자 미만 | 페이지를 열자마자 `window.location`을 GitHub로 바꿈 | `GET https://github.com/login/oauth/authorize?client_id=<CONFIG.CLIENT_ID>&scope=user,repo,admin:org&state=<oauthState>` | 돌아오면 `GET /migration?code=&state=` | 로그인 | `Migration.js:setUserToken`, `redirect` |
| 원본 검색 `input[name=target-filter]` (첫째) | 입력란 | 토큰 있음 | `full_name`에 대소문자 무시 포함 글자로 거름 | 없음 | 소유자가 바뀌는 첫 항목에 소유자 이름 | | `home.html:43`, `Migration.js:itemsOf` |
| 원본 항목 `.project-list` | 행(`v-on:click`) | | 선택 표시(`selected`)하고 원본 요약을 읽는다 | `GET /migration/{owner}/projects/{projectName}` | 요약 JSON(`milestoneCount`, `issueCount`, `postCount`, `assignees`...)을 `source`에 | 프로젝트 관리자, 조직 관리자, 사이트 관리자(서버) | `home.html:45` |
| 원본 이름 링크 | 링크 | | 새 탭으로 `https://yona.dev/{full_name}` | | 고정 서버 주소(R-58-3: 시험은 "사라진 옛 서버가 아니라 yona 사이트"로 적는다) | | `home.html:47` |
| 대상 검색 (둘째) | 입력란 | | 같음 | 없음 | | | `home.html:55` |
| 대상 항목 `.project-list` | 행 | | 대상을 정하고 GitHub에서 마일스톤, 이슈, 게시글 현황을 읽는다(2초마다 반복) | `GET api.github.com/repos/{owner}/{name}/milestones?state=all`, `.../issues?state=all`, `.../issues?state=all&labels=게시글`. 조직이면 `GET .../orgs/{org}/memberships/{me}` | 대상에 데이터가 있으면 경고 | 사용자 토큰 | `Migration.js:setDestination`, `checkIfDestinationHaveDataAlready` |
| 경고 배지 | 글자 | 대상이 조직이고 내가 admin이 아님 등 | `migration.noWorkerInAdmin`, `workerNeeded`, `bothNotAdmin`, `userUsedInstead`, `notAdmin`, `cannotNonAdmin` | | 문구의 `{0}` 자리에 `{{CONFIG.DEFAULT_WORKER}}`가 들어가는데 이 값은 정의되어 있지 않다([migration-F2](#발견-사항)) | | `home.html:63-79` |
| 담당자 입력 `.assignee input` | 입력란 | 원본에 담당자가 있고 대상이 정해짐 | 입력이 멈추면(300ms) 대상 저장소의 협업자인지 확인. 확인되면 체크 아이콘(`yobicon-check-circle`), 아니면 `yobicon-delete-circle-alt`. 확인된 값은 `localStorage[원본 로그인 ID]`에 저장, 아니면 삭제 | `GET api.github.com/repos/{owner}/{name}/collaborators/{login}` (204면 확인) | 이후 이슈 담당자 매핑에 쓰임 | | `home.html:147`, `Migration.js:userExistAtDestinationProject` |
| 마일스톤 옮기기 `button.btn-danger` | 단추 | 대상이 정해짐(`v-if="destination.projectName"`). 비활성: 원본 마일스톤 수 0 또는 작업 중 | 확인창 `migration.js.confirmStart`(`confirm`) 후 작업 시작 | `GET /migration/{owner}/projects/{name}/milestones?withWikiCommit=true`, 마일스톤마다 `POST api.github.com/repos/{owner}/{name}/milestones` | 성공 때마다 진행 막대 +1. 실패는 `#system-msg` 오류 JSON에 쌓임 | | `home.html:100`, `Migration.js:importMilestones`, `migrationService.importMilestones` |
| 이슈 옮기기 | 단추 | 같음, 비활성: 이슈 수 0 또는 작업 중 | 확인창 후 라벨 정보를 먼저 읽고 이슈를 보낸다 | `GET .../labels`, `GET .../issuelabel`, `GET .../issues?withWikiCommit=true`, 이슈마다 0.5초 간격으로 `POST api.github.com/repos/{owner}/{name}/import/issues`(`Accept: application/vnd.github.golden-comet-preview+json`) | 202가 아니면 오류 목록에 쌓임 | | `home.html:111`, `Migration.js:importIssues` |
| 게시글 옮기기 | 단추 | 같음, 비활성: 게시글 수 0 또는 작업 중 | 안내 문구(`showMoreDesc`) 토글 후 확인창, 게시글을 이슈(`게시글` 라벨)로 보낸다 | `GET .../posts?withWikiCommit=true`, `POST .../import/issues` | | | `home.html:123`, `Migration.js:onImportPosts` |
| (자동) 작업 기록 | 스크립트 | 옮기기 시작 때 | 로그 저장소에 이슈를 올린다 | `POST api.github.com/repos/yona-projects/yona-migration-log/issues`(`Authorization: token <WORKER.TOKEN>`) | 응답은 무시. 사용자 정의 값(원본, 대상, 종류, 개수)이 외부 저장소에 남는다 | | `Migration.js:logToIssue` |

### 4. 스크립트 동작

- 모든 GitHub 호출은 **브라우저가 직접** 한다(서버는 중계하지 않는다). `Authorization: token <사용자 토큰>`을 `fetch`로 보내며 `yona.Csrf.js`는 같은 출처 요청에만 토큰을 싣는다.
- 토큰이 있으면 `mounted`에서 내 GitHub 저장소 목록(`GET api.github.com/user/repos?per_page=100&page=N`, 100쪽까지, 응답 `Link` 헤더의 `rel="next"`로 판단)과 내 계정(`GET api.github.com/user`)을 읽는다. 원본 목록은 `created`에서 `GET /migration/projects`.
- 나가기 경고: `window.onload`에서 `beforeunload`를 걸어 토큰이 있으면 `migration.js.leavePage` 문구를 보인다.
- 대상을 고른 뒤 로딩 막대 종료 때마다 2초 간격 `setInterval`로 대상의 기존 데이터를 다시 읽는다(폴링).
- 로컬 저장소: `localStorage[원본 로그인 ID] = 대상 로그인 ID`(담당자 매핑). 저장소를 못 쓰면 무시.
- 작업 토큰(`WORKER.TOKEN`): 대상 소유자가 개인이면 사용자 토큰, 조직이면 빈 글자로 시작하고 내가 그 조직의 admin이 아니면 사용자 토큰으로 바꾼다(`setDestination`, `isMigrationWorkerAdmin`). 조직 admin이면 빈 글자인 채라 GitHub 호출이 `token ` 헤더로 나간다([migration-F3](#발견-사항)).
- 시험용 제한 `hardCodedDestination.owner`는 빈 글자이다(제한 없음).

### 5. 단축키, 6. 팝업

단축키 없음(키맵 조각 없음). 팝업은 브라우저 `confirm`(`migration.js.confirmStart`), `alert`(시험용 제한 위반 때만) 뿐이다.

### 7. 요청과 응답 (서버 쪽)

| 요청 | 응답 |
|---|---|
| `GET /migration` | 200 HTML. 설정 꺼짐 403. 잘못된 `state` 403. 익명은 로그인 폼 303 |
| `GET /migration/projects` | 200 JSON: 내가 `ORG_ADMIN`인 조직의 모든 프로젝트 + 내가 구성원이고 설정을 바꿀 수 있는 프로젝트, 소유자와 이름순. 항목 `{owner, projectName, private, members, full_name}` (`MigrationService.sourceProjects`, `:79-92`) |
| `GET /migration/{owner}/projects/{name}[/labels\|/issuelabel\|/milestones\|/issues\|/posts]` | 200 JSON. 없는 프로젝트 404, 내보낼 권한 없음 403 (`MigrationController.export`, `:103-107`). `/issues`, `/posts`는 쿼리 `withWikiCommit`이 비어 있지 않고 `true`로 끝나면 첨부 링크를 위키 커밋 경로로 바꾼다(`:101`) |

### 8. 권한 행렬

| 역할 | `/migration` 화면 | JSON 내보내기 |
|---|---|---|
| 비로그인 | 로그인 폼 | 로그인 폼 |
| 로그인 일반, 프로젝트 구성원, 소유자 | 설정이 켜져 있으면 열림 | 목록에는 없고 직접 요청하면 403 |
| 프로젝트 관리자 | 열림 | 그 프로젝트 허용 |
| 조직 관리자(`ORG_ADMIN`) | 열림 | 그 조직의 모든 프로젝트 허용 |
| 사이트 관리자 | 열림 | 모든 프로젝트 허용(목록에는 자신이 구성원인 프로젝트만) |

근거: `MigrationService.canExport`(`:69-73`)와 `isAllowedToSettings`(`:63-67`), 결정 D-23. 화면은 권한이 없는 프로젝트를 목록에서 걸러 낸다. 서버도 같은 기준으로 막는다.

### 9. 경계 값

GitHub 저장소 목록 100개씩 100쪽까지, 이슈 전송 0.5초 간격(분당 요청 제한 회피), 진행 막대 종료 대기 최대 20회 x 500ms. 이전 시험: `MigrationPageTest`, `MigrationExportTest`.

### 발견 사항

| 번호 | 내용 | 근거 |
|---|---|---|
| migration-F1 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 서버 설정 `yona.github.client-id`/`client-secret`로 코드를 토큰으로 바꾸지만(`GithubTokenClient`), 인증 시작 주소의 `client_id`는 `Migration.js`의 상수 `CONFIG.CLIENT_ID = "e7f9ad76a3a4ba19b2a5"`로 고정이다. 사이트가 자기 GitHub OAuth 앱을 설정해도 화면은 이 고정 클라이언트 ID로 인증을 시작한다(v1.6 `yona.Migration.js`에도 같은 상수가 있다) | `Migration.js:22`, `GithubTokenClient.kt:26-31`, `YonaProperties.kt:327` |
| migration-F2 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 템플릿이 `{{CONFIG.DEFAULT_WORKER}}`를 쓰지만 `CONFIG`에는 `DEFAULT_WORKER`가 없다. 경고 문구의 `{0}` 자리가 빈 글자로 나온다(v1.6의 `CONFIG`에도 없다: `public/javascripts/service/yona.Migration.js:17`, 확인) | `home.html:64-74`, `Migration.js:18-24` |
| migration-F3 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 대상이 조직이고 내가 그 조직의 admin이면 `WORKER.TOKEN`이 빈 글자인 채로 GitHub에 `Authorization: token `이 전송되고(작업 계정 개념의 잔재), 마일스톤, 이슈 전송이 실패하거나 작업 기록 이슈가 올라가지 않는다. v1.6도 같은 구조(`yona.Migration.js:426-428`, `:234`). 실제 GitHub 응답은 확인하지 못했다(추측) | `Migration.js:setDestination`, `isMigrationWorkerAdmin` |
| migration-F4 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 작업 시작 때마다 외부 저장소 `yona-projects/yona-migration-log`에 원본과 대상 이름, 개수가 이슈로 올라가려 한다(작업 토큰이 맞을 때). 사용자에게 알리는 문구가 없다 | `Migration.js:logToIssue`, `CONFIG.LOGGING_PROJECT_FULLNAME` |
| migration-F5 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 템플릿의 열 합계(`col-lg-4`+`col-lg-4`+`col-lg-6` = 14)가 12를 넘는다. 상태 열은 다음 줄로 내려간다 | `home.html:41`, `:53`, `:83` |
| migration-F6 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `delegateAttachmentsMigration`은 호출만 되고 "Not Yet Support" 로그만 남긴다 | `Migration.js:delegateAttachmentsMigration` |
| migration-F7 **[검증: 문서 문제]** | `migration-layout.html`의 설명 주석은 "jquery 1.9.0을 head에서 불러온다"고 적지만 실제로는 `jquery-3.7.1.js`이다 | `layout/migration-layout.html` 머리 주석, `:38-40` |
| migration-F8 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 이슈, 마일스톤 전송 실패는 사용자에게 `#system-msg`의 JSON 덩어리로만 보인다(별도 오류 문구 없음). 서버 JSON 요청이 403, 404이어도 화면은 아무 안내가 없다(`http()`가 거부하고 호출 쪽 `error`는 콘솔만 기록) | `Migration.js:error`, `home.html:22` |

---

# 시작 안내 (`welcome/secret.html`, `welcome/restart.html`)

### 1. 화면 개요

- 사이트 비밀 키가 기본값이면(`SiteSecret.invalid`) `SiteSecretInterceptor`가 **컨트롤러 핸들러로 가는 모든 요청**을 가로챈다. 정적 자원은 컨트롤러가 아니므로 영향이 없다. 인터셉터는 접근 검사 체인 **뒤**에 등록되어(`config/WebConfig.kt:68-73`) 익명이 열 수 없는 주소는 검사가 먼저 막는다(시험 "접근 검사가 붙은 라우트는 검사가 먼저이고 통과해야 비밀 키 화면이 나온다"). 라우트가 없는 GET도 `GlobalErrorHandler`가 같은 화면을 준다(v1.6의 포괄 라우트).
- 응답: 재시작이 필요하면 어떤 요청이든 `welcome/restart` 200. 비밀 키가 기본값이면 GET 등은 `welcome/secret` 200, POST는 검증한다.
- `secret.html` 모델: `siteName`, `loginId`(고정 `admin`), `errors`(항목별 메시지 키 목록), `name`, `email`(제출한 값을 그대로 돌려줌). `restart.html` 모델: `siteName`.
- 두 화면은 사이트 틀을 쓰지 않는 독립 문서이다(`bootstrap.css`, `yobi.css`, `yobicon/style.css`, `yona.Common.js`, secret은 `yona.Csrf.js`, jQuery 3.7.1).

### 2. 레이아웃 계약

중앙 정렬 `.secret-wrap`: 주황색 로고 상자(`.logo`, 123x55, 링크 `/`, 글자는 사이트 이름), `h3` 환영 문구, 경고 상자(`.alert-warning.secret-box`, 너비 50%)와 폼 `.signup-form-wrap.frm-wrap`. 아래 푸터 `Powered by <사이트 이름>`. 반응형 규칙은 없다(`.secret-box`가 50% 폭 고정이라 좁은 화면에서도 50%).

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 로고 링크 | 링크 | 항상 | `/`로 | `GET /` | 비밀 키가 기본값이면 같은 화면 | `secret.html:42`, `restart.html:41` |
| `loginId` | 입력란 | 항상 | `readonly`, 값 `admin` | 필드 `loginId` | | `secret.html:59` |
| 이름 `name` | 입력란 | 항상 | | 필드 `name` | 비어 있으면 오류 `error.required` | `secret.html:66` |
| 이메일 `email` | 입력란(`type=text`) | 항상 | | 필드 `email` | 오류: 비어 있음 `validation.invalidEmail`, 형식 `user.wrongEmail.alert`, 다른 사용자가 쓰는 이메일 `user.email.duplicate`(기본 관리자 자신의 이메일은 중복이 아님) | `secret.html:73` |
| 비밀번호 `password` | 비밀번호 | 항상 | | 필드 `password` | 비어 있으면 `user.wrongPassword.alert` | `secret.html:80` |
| 비밀번호 확인 `retypedPassword` | 비밀번호 | 항상 | | 필드 `retypedPassword` | 다르면 `user.confirmPassword.alert` | `secret.html:87` |
| 오류 배지 `.badge.bg-danger` | 글자 | 해당 항목에 오류 | 항목 라벨 옆에 메시지 | | | `secret.html:56`, `:63`, `:70`, `:77`, `:84` |
| 제출 `app.welcome.submit` | 제출 | 항상 | `POST /`(urlencoded) | `POST /` | 아래 7절 | `secret.html:52`, `:91` |

### 4. 스크립트 동작

`secret.html:104-108`이 `$("input[name=seed]").focus()`를 호출하지만 `seed` 입력란은 이 화면에 없다(아무 일도 하지 않는다. 이름 입력란에 초점을 주지 않는다). `yona.Csrf.js`가 제출 직전 `_csrf` 숨은 입력을 더한다.

### 5~6. 단축키, 팝업

없음.

### 7. 요청과 응답

| 요청 | 응답 | 근거 |
|---|---|---|
| `POST /` 검증 통과 | `SiteSecret.configure`가 새 비밀 키(130비트 난수, 32진수)를 `<데이터 디렉터리>/config/secret.properties`에 쓰고 기본 관리자 `admin`의 이름, 이메일, 비밀번호를 바꾼 뒤 `welcome/restart` 200 | `SiteSecretInterceptor.kt:66-72`, `SiteSecret.kt` |
| 검증 실패 | 400으로 `welcome/secret`을 다시 그림. 항목마다 오류 키(같은 키는 한 번만) | `:64-65`, `:76-104` |
| 설정 파일에 쓰지 못함 | 500으로 같은 폼, `password`에 `app.secret.writeFailed` 오류. 계정은 바뀌지 않는다 | `:68-71` |
| `loginId` 또는 `password` 항목이 아예 없음 | 널 참조로 500(v1.6과 같게) | `:81`, `:95` |
| 비밀 키를 정한 뒤 `POST /` | `FakeController`가 400(본문 없음) | `FakeController.kt:16-17` |
| 재시작이 필요한 상태의 모든 요청 | `welcome/restart` 200 | `:61` |

`loginId` 검사: 비면 `error.required`, 형식 `user.wrongloginId.alert`, 예약어 `validation.reservedWord`, `admin`이 아니면 `user.wrongloginId.alert`(`:84-90`).

### 8. 권한

인증이 없다(접근 검사가 막는 주소를 제외한 모든 사람). 이 상태는 사이트 최초 기동 때만 존재한다.

### 9. 시험

`SiteSecretTest`(기본이면 어떤 주소든 계정 설정 화면, 포괄 GET, 접근 검사 우선, 정적 자원 통과, ID 오류, 널 참조 500, 비밀번호, 이름, 이메일, 입력 보존, 설정 저장, 재시작 안내, 쓰기 실패, POST 루트 400, 비밀 키 형식).

### 발견 사항

| 번호 | 내용 | 근거 |
|---|---|---|
| welcome-F1 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 제출 처리에서 `loginId`와 `password` 항목이 없으면 500(NullPointerException)이다. 화면의 폼은 항상 보내지만 직접 요청은 500을 받는다(v1.6과 같다고 시험이 고정) | `SiteSecretInterceptor.kt:81`, `:95` |
| welcome-F2 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `$("input[name=seed]").focus()`는 대상이 없는 죽은 코드이다(v1.6 잔재). 화면이 열릴 때 초점이 이동하지 않는다 | `secret.html:104-108` |
| welcome-F3 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 비밀번호에 길이 규칙이 없다(`user.wrongPassword.alert`는 빈 값일 때만). 사이트 비밀번호 정책 설정이 있는지는 이 화면에서 쓰지 않는다 | `SiteSecretInterceptor.kt:93-96` |
| welcome-F4 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `.secret-box { width: 50% }` 고정이라 좁은 화면에서 안내 글이 좁다(반응형 규칙 없음) | `secret.html:26`, `restart.html:25` |

---

# 감사 로그 (`audit/list.html`)

세 화면이 하나의 조각을 쓴다. 조각 매개변수 `(exportable, verifiable, showFilter)`.

| 화면 | 템플릿 호출 | 매개변수 | 컨트롤러 | 접근 |
|---|---|---|---|---|
| 사이트 `/sites/audit` | `site/audit.html:33` | `(true, true, true)` | `SiteAuditController.list` (`AuditControllers.kt:59-63`) | `@SiteManagerOnly` + `@AnonymousCheck`. 사이트 관리자가 아니면 `error.auth.unauthorized.waringMessage` 403(익명도 로그인 폼이 아니라 403) |
| 조직 `/organizations/{name}/audit` | `organization/audit.html:17` | `(true, false, true)` | `OrganizationAuditController.list` (`:94-101`) | 없는 조직 404(`organization.member.unknownOrganization`), 조직 `UPDATE` 권한(조직 관리자, 사이트 관리자)이 없으면 403 |
| 사용자 `/user/editform/audit` | `user/edit-audit.html:20` | `(false, false, false)` | `UserAuditController.list` (`:116-123`) | `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`: 익명은 로그인 폼. 본인 것만 |

### 1. 화면 개요

모델(`AuditViewSupport.model`, `:59-81`): `base`, `rows`, `page`, `pages`, `total`, `size`, `prevUrl`, `nextUrl`, `filter`(맵), `outcomes`, `exportCsv`, `exportJsonl`, `exportJson`. 사이트 화면은 추가로 `streams`(외부 스트리밍 상태 표, 이 조각 밖이다).
쿼리: `page`(1부터, 기본 1, 1 미만이면 1), `size`(기본 50, 1~200으로 자름, `AuditQueryService.kt:117-118`), 필터 `actor`, `action`(접두사), `outcome`(`SUCCESS|FAILURE|DENIED`, 대소문자 무시, 모르는 값은 무시), `targetType`, `target`, `ip`, `projectId`, `from`, `to`(UTC `yyyy-MM-dd`, `to`는 그날 끝까지, 틀린 형식은 무시)(`AuditViewSupport.kt:31-40`).
사용자 범위는 내 ID가 행위자이거나 대상인 기록 가운데 `auth.`, `token.`, `account.` 로 시작하는 조치만이다(`AuditQueryService.kt:103-108`, `:122`).

### 2. 레이아웃 계약

위에서 아래: 필터 폼(`showFilter`일 때만, 12열 그리드 `row g-2`: 행위자 3, 조치 3, 결과 2, 대상 4, IP 3, 시작일 3, 종료일 3, 단추 3), 합계 줄(`audit.pager.info`: `기록 N건, P/T쪽`)과 내려받기 링크와 `무결성 검증` 단추, 검증 결과 줄, 표(`table-sm`, 7열: 시각, 행위자, 조치, 결과, 대상, IP, 상세), 이전/다음 링크. 열 폭 규칙은 `col-lg-*`(992px 미만에서 세로로 쌓임)이고 `hide-in-mobile` 같은 규칙은 없다. 표 `text-nowrap` 열: 시각, 조치, IP.
- 빈 상태: 표에 한 줄 `audit.empty`(`colspan=7`)(`list.html:62`). 이전/다음 `nav`는 둘 중 하나라도 있을 때만.
- 결과 배지 색: `SUCCESS` 초록(`text-bg-success`), `DENIED` 빨강, 그 밖(`FAILURE`) 노랑. 상세는 120자까지 보이고 넘으면 `…`, 전체는 `title`(마우스 올림)에 있다(`AuditViewSupport.rowOf`, `:47-53`).

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 근거 |
|---|---|---|---|---|---|---|
| 필터 입력(`actor`, `action`, `target`, `ip`, `from`, `to`, `outcome`) | 입력, 선택, 날짜 | `showFilter` | 적용 단추나 Enter로 제출 | `GET {base}?actor=&action=&outcome=&target=&ip=&from=&to=`(빈 값도 폼이 보내고 서버는 빈 값을 필터에서 뺀다) | 1쪽부터 다시 그림 | `list.html:9-26` |
| `적용` | 제출 | `showFilter` | | 위와 같음 | | `list.html:23` |
| `초기화` | 링크 | `showFilter` | 필터를 모두 지운 주소로 | `GET {base}` | | `list.html:24` |
| CSV, JSON 줄, JSON | 링크 | `exportable` | 현재 필터(`targetType`, `projectId` 포함)를 이어 받아 내려받기 | `GET {base}/export?<필터>&format=csv\|jsonl\|json` | 첨부 파일 응답(`Content-Disposition: attachment; filename=audit-{UTC 날짜}.{확장자}`). 이 내려받기도 `audit.exported` 기록으로 남는다. `limit`(기본 10000, 최대 100000)은 링크에 없고 직접 쿼리로만 준다(`AuditControllers.kt:34-44`, `AuditExporter.kt:49-50`). 알 수 없는 `format`은 400 | `list.html:30-34` |
| `무결성 검증` `.audit-verify` | 단추 | `verifiable`(사이트 화면만) | 해시 사슬을 처음부터 다시 계산 | `GET {현재 경로}/verify` (`fetch`, `Accept: application/json`) | JSON `{ok, checked, brokenId, reason}`을 받아 `.audit-verify-result`에 `audit.verify.ok`(초록) 또는 `audit.verify.broken`(빨강) 문구를 보임. 오류 처리는 없다 | `list.html:35`, `:69-84`, `AuditControllers.kt:76-78` |
| `최신`(`.audit-prev`), `이전`(`.audit-next`) | 링크 | `prevUrl`(`page > 1`), `nextUrl`(`page < pages`) | 쪽 이동. 필터와 `size`를 유지 | `GET {base}?<필터>&page=N&size=S` | 이름이 반대로 보인다: `audit.pager.prev`="최신", `next`="이전"(최신이 앞쪽이므로) | `list.html:65-68` |

### 4. 스크립트 동작

`list.html:69-84`의 인라인 즉시 실행 함수 하나(`verifiable`일 때만 출력). `fetch(..., {credentials:"same-origin"})`로 GET만 하므로 CSRF 토큰은 필요 없다. 메시지는 `data-ok`, `data-broken` 속성에 `{0}`, `{1}` 자리표시자를 남겨 두고(`#{audit.verify.ok('{0}')}`) 응답 값으로 `String.replace`한다. 로컬 저장소, 폴링 없음.

### 5~6. 단축키, 팝업

없음.

### 7. 요청과 응답

| 요청 | 응답 |
|---|---|
| `GET /sites/audit`, `/organizations/{n}/audit`, `/user/editform/audit` | 200 HTML. 권한 없음 403, 없는 조직 404, 사용자 화면의 익명은 로그인 폼 303 |
| `GET .../export` | 200 첨부 파일(스트림). `format`이 `csv`, `jsonl`, `json`이 아니면 400. 조직 내려받기는 `OrganizationAuditController.export`(`:103-109`), 사이트는 `SiteAuditController.export`(`:71-73`). 사용자 범위에는 내려받기 경로가 없다 |
| `GET /sites/audit/verify` | 200 JSON, 사이트 관리자만 |

### 8. 권한 행렬

| 역할 | 사이트 화면 | 조직 화면 | 사용자 화면 |
|---|---|---|---|
| 비로그인 | 403 | 조직 `UPDATE` 불가라 403 | 로그인 폼 |
| 일반 로그인 | 403 | 403 | 본인 기록만 |
| 조직 구성원(관리자 아님) | 403 | 403 | 본인 기록만 |
| 조직 관리자 | 403 | 조직과 그 조직 소유 프로젝트의 기록 | 본인 기록만 |
| 사이트 관리자 | 전체 기록, 내려받기, 검증 | 허용 | 본인 기록만 |

### 9. 시험

`AuditQueryTest`, `AuditOrganizationSiteTest`, `AuditAccountTest`, `AuditApiTest`, `AuditStreamViewTest`, `AuditCoverageTest` 등(`src/test/kotlin/dev/yona/domain/audit/`, `.../audit/`). 템플릿의 화면 조각을 직접 단언하는 시험이 어느 파일인지는 `AuditOrganizationSiteTest`, `AuditAccountTest`에서 일부 보이나 이 조사에서 줄 단위로 대조하지 않았다.

### 발견 사항

| 번호 | 내용 | 근거 |
|---|---|---|
| audit-F1 **[검증: 결함 아님]** | 필터 폼에는 `targetType`과 `projectId` 입력이 없지만 서버는 쿼리로 받고(`filterOf`), 쪽 이동과 내려받기 링크는 이를 이어 붙인다. 화면에서는 직접 URL로만 쓸 수 있다 | `AuditViewSupport.kt:34-35`, `:84` |
| audit-F2 **[검증: 결함 아님]** | 사용자 화면은 필터 폼을 그리지 않지만(`showFilter=false`) 쿼리의 필터(예: `?actor=`)는 서버가 적용한다. 범위 제한(본인, `auth.`/`token.`/`account.`)은 항상 먼저 걸린다 | `AuditControllers.kt:122`, `AuditQueryService.kt:103-108` |
| audit-F3 **[검증: 결함 아님]** | 이전/다음 링크 글자가 "최신"(`audit.pager.prev`)과 "이전"(`audit.pager.next`)이다. `prev`가 앞쪽(최신)이다. 영어 기본 메시지에서도 같은 의미(`Newer`/`Older`)이다 | `messages_ko_KR.properties:1424-1425`, `list.html:66-67` |
| audit-F4 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 검증 단추의 `fetch`는 응답 상태를 확인하지 않고 `r.json()`만 호출하므로 403이나 500 때는 화면에 아무 일도 일어나지 않는다. 단추는 `data-url`이 있으면 그 값을 쓰나 템플릿이 설정하지 않아 항상 `{현재 경로}/verify`이다 | `list.html:75-82` |
| audit-F5 **[검증: 결함 아님]** | 사용자 범위에서 내가 행위자인 모든 기록 가운데 `auth.`, `token.`, `account.`이 아닌 조치(프로젝트 변경 등)는 보이지 않는다(설명 문구 `audit.description.user`와 일치) | `AuditQueryService.kt:103-108` |

## 범위 점검

| 템플릿 | 다룬 절 |
|---|---|
| `migration/home.html` | 이전(1~9) |
| `welcome/secret.html` | 시작 안내(1~9) |
| `welcome/restart.html` | 시작 안내(1, 2, 3 로고, 7) |
| `audit/list.html` | 감사 로그(1~9) |

조사한 템플릿 4개 / 담당 4개(`migration` 1, `welcome` 2, `audit` 1). 함께 읽은 틀: `layout/migration-layout.html`. 따라간 코드: `MigrationController`, `MigrationService`, `GithubTokenClient`, `Migration.js`(전체), `SiteSecretInterceptor`, `FakeController`, `SiteSecret`, `WebConfig`(인터셉터 순서), `AuditControllers`, `AuditViewSupport`, `AuditQueryService`(범위), `AccessChainInterceptor`, `SiteManagerOnlyStage`.
