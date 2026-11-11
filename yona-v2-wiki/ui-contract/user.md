---
title: 사용자 영역 화면 계약 1 - 사용자 페이지, 프로필 설정, 이메일, 알림, 내 파일
type: ui-contract
sources:
  - src/main/resources/templates/user/view.html
  - src/main/resources/templates/user/partial-issues.html
  - src/main/resources/templates/user/partial-projectlist.html
  - src/main/resources/templates/user/partial-pull-requests.html
  - src/main/resources/templates/user/files.html
  - src/main/resources/templates/user/edit.html
  - src/main/resources/templates/user/edit-password.html
  - src/main/resources/templates/user/edit-emails.html
  - src/main/resources/templates/user/edit-notifications.html
  - src/main/resources/templates/user/edit-audit.html
  - src/main/resources/templates/user/partial-edit-tabmenu.html
  - src/main/kotlin/dev/yona/web/user/UserPageController.kt
  - src/main/kotlin/dev/yona/web/user/UserPageAssembler.kt
  - src/main/kotlin/dev/yona/web/user/UserFilesController.kt
  - src/main/kotlin/dev/yona/web/user/UserLeaveController.kt
  - src/main/kotlin/dev/yona/web/user/AccountController.kt
  - src/main/kotlin/dev/yona/web/user/UserMenuController.kt
  - src/main/kotlin/dev/yona/web/audit/AuditControllers.kt
  - src/main/kotlin/dev/yona/domain/user/AccountService.kt
  - src/main/resources/static/assets/javascripts/esm/service/user.View.js
  - src/main/resources/static/assets/javascripts/esm/service/user.Setting.js
  - src/main/resources/static/assets/javascripts/esm/Request.js
updated: 2026-11-11
---

# 사용자 영역 화면 계약 1: 사용자 페이지, 프로필 설정, 이메일, 알림, 내 파일

로그인, 가입, 2단계 인증, 키, 토큰은 [user-security.md](user-security.md), OAuth와 기기 승인은 [oauth.md](oauth.md). 이미 있는 글 `web/user-page.md`, `web/account.md`, `web/user-menu.md`, `web/my-issues.md`는 다시 쓰지 않는다. 줄임말 `T/`, `K/`, `J/`, `M/`, `TT/`는 user-security.md와 같다(`T/`=`src/main/resources/templates/`, `K/`=`src/main/kotlin/dev/yona/`, `J/`=`src/main/resources/static/assets/javascripts/`, `M/`=`src/main/resources/`, `TT/`=`src/test/kotlin/dev/yona/`).

## 1. 영역 요약

| 템플릿 | URL | 컨트롤러 | 용도 |
|---|---|---|---|
| `user/view.html` | `GET /{user}?daysAgo=&selected=` | `K/web/user/UserPageController.kt:38-62` | 사용자 페이지(프로필 + 최근 이슈, 풀 리퀘스트, 프로젝트) |
| `user/partial-issues.html` | (조각 `issueRow(row)`) | `K/web/user/UserPageAssembler.kt:97-116` | 이슈 한 줄 |
| `user/partial-pull-requests.html` | (조각 `pullRequestRow(pull)`) | `UserPageAssembler.kt:118-136` | 풀 리퀘스트 한 줄 |
| `user/partial-projectlist.html` | (조각 `projectRow(project)`) | `UserPageAssembler.kt:138-152` | 프로젝트 한 줄 |
| `user/files.html` | `GET /user/files?filter=&pageNum=` | `K/web/user/UserFilesController.kt:40-53` | 내 첨부 파일 |
| `user/edit.html` | `GET /user/editform`, `GET,POST /user/editform/profile`, 오류 시 `POST /user/edit`와 `POST /user/resetPassword` 400 | `K/web/user/AccountController.kt:83, :103, :119, :131` | 프로필 설정(이름, 이메일, 아바타) |
| `user/edit-password.html` | `GET,POST /user/editform/password` | `AccountController.kt:103-110` | 비밀번호 변경 |
| `user/edit-emails.html` | `GET,POST /user/editform/emails` | `AccountController.kt:103-111` | 보조 이메일 |
| `user/edit-notifications.html` | `GET,POST /user/editform/notifications` | `AccountController.kt:103-114, :238-246` | 프로젝트별 알림 종류 켜고 끄기 |
| `user/edit-audit.html` | `GET /user/editform/audit` | `K/web/audit/AuditControllers.kt:114-123` | 내 보안 기록 |
| `user/partial-edit-tabmenu.html` | (조각 `tabmenu(tabId)`) | 설정 화면 전부가 포함 | 설정 탭 줄 |

사용자 메뉴(`/user/usermenuTabContentList`, `/user/sidebar`)와 내 이슈(`/user/issues`)는 이 영역 템플릿이 아니라 `index/usermenu-tab-content-list.html`, `layout/layout-framed.html`, 이슈 영역 템플릿이 그리므로 6절에서 컨트롤러 규칙만 요약한다.

## 2. 사용자 페이지 `user/view.html`

### 2.1 개요

`GET /{user}`(`UserPageController.kt:38`, 클래스에 `@AnonymousCheck`, 곧 사이트가 익명 접근을 막았을 때만 로그인 요구). 처리 순서:

1. `{user}`가 조직 이름(대소문자 무시)이면 `303`으로 조직 페이지로 이동(`:47`).
2. 사용자를 로그인 ID(대소문자 무시)로 찾고 없으면 `404`(`user.notExists.name`, `:50`).
3. `daysAgo`를 정한다(`:65-76`): 파라미터가 `0`(기본값)이면 쿠키 `daysAgo`(정수), 쿠키가 없거나 정수가 아니면 14로 저장하고 14 사용. 음수는 1. 그 밖에는 값을 쿠키에 저장(`Path=/`, HttpOnly, 세션 쿠키).
4. 모델: `profile`(`UserProfileView`), `activity`(`UserActivity`), `daysAgo`, `selected`(기본 `issues`), `showStream`(`viewer?.isGuest != true`), `openIssueCount`, `closedIssueCount`.

`properties.hideProjectListing`이고 비로그인이면 `activity`가 빈 목록 세 개이다(`UserPageAssembler.kt:75`).

### 2.2 데이터 규칙(서버)

- 프로젝트: 이 사용자가 멤버인 프로젝트 중 보는 사람이 `READ` 가능한 것만, 최근 푸시 내림차순(푸시가 없으면 뒤, 이름순)(`UserPageAssembler.kt:83-84, :159-168`).
- 이슈: 최근 `daysAgo`일(오늘 0시 기준 달력 N일 전 0시부터, `:82`) 안에 이 사용자가 만들거나 관련된 이슈 중 읽을 수 있는 것. 보는 사람이 작성자가 아닌 초안은 뺀다(`:86-87`, R-48-1).
- 풀 리퀘스트: 같은 기간, 이 사용자가 기여자인 것 중 읽을 수 있는 것(`:88`).
- 프로필: 이메일은 `properties.showUserEmail`일 때만, 아바타 256px, `since`는 `MMM dd, yyyy`(US 로캘), `ownPage`는 보는 사람의 로그인 ID와 같을 때, `locked`는 상태 `LOCKED`만(삭제된 사용자에는 붙지 않음), `siteManager`는 사이트 관리자 여부(`:59-67`).

### 2.3 레이아웃 계약

- 머리(`site-breadcrumb-inner h3`)에 사용자 이름(`T/user/view.html:13-17`). `layout/site-layout`의 제목은 로그인 ID, 메뉴 `'USER'`.
- 본문 `section.user-box`는 왼쪽 `user-info-box`(아바타 배경 이미지 `.whoami-wrap`, 게스트면 `OUR GUEST` 표시, 영문 이름, `@로그인ID`, 이메일, 본인 페이지면 프로필 수정 단추, `SITE ADMIN` 배지, `BLOCKED` 배지, 가입일)와 오른쪽 `user-stream-box`로 구성된다(`:20-132`). 폭 고정 값은 템플릿에 없고 CSS 담당이다.
- `showStream`이 거짓(게스트 사용자가 보는 경우)이면 `user-stream-box` 안이 비어 탭과 목록이 모두 사라진다(`:45, :130`). 여기서 "게스트"는 **보는 사람**이다. 페이지 주인의 게스트 표시는 `profile.guest`로 따로 나온다.
- 탭 3개: 이슈(`#issues`), 풀 리퀘스트(`#pullRequests`), 프로젝트(`#projects`). `selected` 값이 일치하는 탭이 `active`이고, 각 탭 제목 옆에 건수 배지(비어 있지 않을 때만)(`:50-65`). 이슈 탭 안에는 하위 탭 열림(`#openIssues`, 기본 활성), 닫힘(`#closedIssues`)과 각 건수 배지(`openIssueCount`, `closedIssueCount`, 0이어도 보임)가 있다(`:71-85`).
- 빈 상태: 이슈 열림/닫힘 0건이면 `userinfo.daysAgo.prefix` + `issue.is.empty`, 풀 리퀘스트 0건이면 `...prefix` + `pullRequest.is.empty`, 프로젝트 0건이면 `project.is.empty`(`:88-90, :98-100, :110-112, :120-122`). 이슈는 `row.open`/`row.closed`로 나뉘고 초안(`DRAFT`)은 어느 쪽에도 들어가지 않는다(상태 `OPEN`과 `CLOSED`만 구분, `UserPageAssembler.kt:106`).
- 반응형: 이슈 행의 작성자와 담당자 칸은 `hide-in-mobile`(`@media (max-width: 720px)`에서 `display:none`, `app/assets/stylesheets/scss/_responsive.scss:1,302`), 대신 meta 칸에 `show-in-mobile` 담당자가 보인다(`T/user/partial-issues.html:64-77`). 두 칼럼 모드 체크박스도 `hide-in-mobile`.

### 2.4 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 프로필 수정 단추 | 링크 | `profile.ownPage` | 이동 | `GET /user/editform` | 프로필 설정 | 로그인 | `T/user/view.html:31-33` |
| 며칠 전 입력(`#daysAgoBtn`) | number(1~99) | `showStream` | Enter(`keypress`)에서만 동작: 주소를 `?daysAgo=값&selected=탭&groups=...`로 바꿔 이동 | `GET /{user}?daysAgo=&selected=` | 쿠키 `daysAgo` 갱신 | `/{user}`와 같음 | `view.html:47`, `user.View.js:107-113` |
| 탭 링크 3개와 하위 탭 2개 | 링크 | `showStream` | Bootstrap 탭 전환(`data-bs-toggle=tab`), 서버 요청 없음 | 없음 | | | `view.html:50-85` |
| 두 칼럼 모드 체크박스(`#two-column-mode`) | 체크박스 | `showStream`, PC 폭 | 켜면 목록의 제목 링크가 오른쪽 오프캔버스(`#pageslide`)로 열림 | 없음(`localStorage.useTwoColumnMode`) | | | `T/common/two-column-mode-checkbox-area.html`, `J/esm/service/twoColumnMode.js:66-105` |
| 하위 이슈 보기 체크박스(`#toggle-show-subtasks`) | 체크박스 | 이슈 탭 | 하위 이슈 보임 토글 | 없음(`localStorage.showSubtasksAlways`) | | | `T/common/show-subtasks-checkbox.html`, `J/esm/service/showSubtask.js:11-23` |
| 이슈 행(`li.post-item`) | 행 | | 클릭하면 그 행의 `.child-issue-list`가 보인다(제목 링크 클릭은 전파를 막아 영향 없음) | 없음 | | | `user.View.js:42-49` |
| 이슈 제목 링크, 프로젝트 링크, 작성자, 담당자, 라벨, 마일스톤, 댓글 수, 추천 수, 상위 이슈 링크 | 링크 | 데이터에 따라 | 이동 | `GET /{owner}/{project}/issue/{n}`, `/{owner}/{project}`, `/{loginId}`, `/{owner}/{project}/issues?state=&labelIds=`, `/{owner}/{project}/milestone/{id}` 등 | 각 화면 | 각 화면의 권한 | `partial-issues.html:15-90` |
| 풀 리퀘스트 행 링크 | 링크 | 데이터에 따라 | 이동 | `GET /{owner}/{project}/pullRequest/{n}`, `...#comments` 등 | | | `partial-pull-requests.html:13-42` |
| 프로젝트 행의 `watch`/`unwatch` 단추(`.watchBtn`) | 링크(`a`) | `project.showWatchButton`(= 보는 사람이 프로젝트 소유자가 아님, **비로그인 포함**)이고, 구독 중이면 `unwatch`, 구독 중이 아니고 `publicScope`이면 `watch` | 클릭하면 기본 이동을 막고 Ajax `POST`, 성공하면 페이지 새로고침, 실패하면 `$yona.notify(error.failedTo(...))` | `POST /{owner}/{project}/watch` 또는 `/unwatch` | 성공 `200`(본문 없음), 프로젝트 없음 `404` | `@IsAllowed(READ)`(`K/web/project/WatchProjectController.kt:47-65`). 비로그인은 `requireNotNull`이 실패한다(추측: 접근 검사 체인이 먼저 막지 못하면 500) | `partial-projectlist.html:35-46`, `user.View.js:64-83` |
| 프로젝트 "탈퇴"(`a.leaveProject`) | 링크 | `project.canLeave`(= 페이지 주인이 프로젝트 소유자가 아니고, 페이지 주인과 보는 사람이 같음) | `confirm(userinfo.leaveProject.confirm, 프로젝트명)` 뒤, 숨은 `<form method=post>`을 만들어 `data-leave-url`로 제출 | `POST /info/leave/{owner}/{project}` | 성공 `303 /{loginId}?daysAgo=14&selected=projects`, 프로젝트 없음 `404`, 권한 없음 `403` | `Operation.LEAVE`: 프로젝트 멤버이고 소유자가 아닐 때(사이트 관리자는 `isAllowed`가 항상 참이라 통과하지만 `removeMember`가 의미 있는지는 별개) | `partial-projectlist.html:48-50`, `user.View.js:88-102`, `K/web/user/UserLeaveController.kt:35-49`, `K/access/AccessControl.kt:154-157` |
| 프로젝트 이름, 로고, 원본 프로젝트, 소유자 링크 | 링크 | 데이터에 따라 | 이동 | `GET /{owner}/{project}`, `/{owner}` | | | `partial-projectlist.html:13-28` |

### 2.5 스크립트와 저장소

모듈 `twoColumnMode.js`, `showSubtask.js`, `user.View.js`(`$yona.loadModule("user.View", {sTabSelected: selected})`, `view.html:135-144`). 읽고 쓰는 값: `localStorage.useTwoColumnMode`, `localStorage.showSubtasksAlways`, 쿠키 `daysAgo`(서버가 쓴다). 폴링과 지연 로딩 없음. 며칠 전 입력의 이동 주소는 **초기 `selected` 값만** 사용한다. 탭을 눌러도 `htVar.sTabSelected`는 바뀌지 않으므로(`_rememberCurrentTab`의 인자가 호출되는 곳이 없다, `user.View.js:56-58, :118-123`) Enter를 누르면 지금 보고 있는 탭이 아니라 처음 탭으로 돌아간다(`user-F4`). 위치를 바꾸는 `history.replaceState`도 같은 값을 쓴다.

### 2.6 단축키

이 화면 범위의 단축키 없음. 두 칼럼 모드가 켜져 있으면 제목 링크 클릭이 오프캔버스를 열고 `history.pushState`로 주소를 바꾼다(`twoColumnMode.js:112-123`). 전역 키는 `common` 담당.

### 2.7 팝업, 툴팁, 오류

`data-bs-toggle="tooltip"` 툴팁(프로젝트명, 작성자, 마일스톤, 마감일), 확인창 `confirm`(탈퇴). 서버 오류: 없는 사용자 `404`, 읽기 불가 프로젝트는 목록에서 빠진다(오류 아님).

### 2.8 권한 행렬

| 기능 | 비로그인 | 로그인 | 본인 페이지 | 사이트 관리자 | 서버 규칙 |
|---|---|---|---|---|---|
| 페이지 열람 | 익명 접근 허용 사이트면 가능 | 가능 | | 가능 | `@AnonymousCheck`(기본 `requiresLogin=false`) |
| 활동 목록 | 읽을 수 있는 프로젝트의 것만(`hideProjectListing`이면 빈 목록) | 같음 | 같음 | 모두 읽을 수 있음 | `accessControl.isAllowed(viewer, project, READ)` |
| 프로필 수정 단추 | 안 보임 | 안 보임 | 보임 | 안 보임 | 서버는 `/user/editform`이 본인 것만 다룬다 |
| 탈퇴 단추 | 안 보임 | 안 보임 | 보임(소유자가 아닌 프로젝트만) | 안 보임 | 서버는 `LEAVE`로 따로 검사 |
| 구독 단추 | 보임(비로그인 포함) | 보임 | 소유 프로젝트는 숨김 | 같음 | 서버는 `READ`만 검사 |

시험: `TT/web/user/UserPageTest.kt`, `UserLeaveTest.kt`.

## 3. 설정 탭 줄 `user/partial-edit-tabmenu.html`

`tabmenu(tabId)`: `ul.nav.nav-tabs.mt20.mb-3`에 링크 11개, 현재 탭에 `active`.

| 탭 | 링크 | `tabId` |
|---|---|---|
| 프로필 수정 | `/user/editform` | `profile` |
| 비밀번호 변경 | `/user/editform/password` | `password` |
| 알림 | `/user/editform/notifications` | `notifications` |
| 이메일 | `/user/editform/emails` | `emails` |
| 토큰 | `/user/editform/token` | `token` |
| GPG | `/user/editform/gpg` | `gpg` |
| SSH | `/user/editform/ssh` | `ssh` |
| 2단계 인증 | `/user/editform/two-factor` | `two-factor` |
| OAuth 앱 | `/user/editform/oauth-apps` | `oauth-apps` |
| 연결된 계정 | `/user/editform/linked-accounts` | `linked-accounts` |
| 보안 기록 | `/user/editform/audit` | `audit` |

탭이 보이는 조건은 없다(항상 11개, `T/user/partial-edit-tabmenu.html:7-40`). 경로 라우팅: `K/web/user/AccountController.kt:103-117`의 `@RequestMapping("/user/editform/{tabId}")`는 `password`, `emails`, `token`, `profile`, `notifications`만 처리하고 그 밖의 `tabId`는 `IllegalArgumentException("Invalid tabId")`로 `500` 화면이 된다. `gpg`, `ssh`, `two-factor`, `oauth-apps`, `linked-accounts`, `audit`은 더 구체적인 매핑이 우선해 각자의 컨트롤러가 받는다(`user-F5`). 모든 설정 컨트롤러는 `@AnonymousCheck(requiresLogin=true, displaysFlashMessage=true)`로 비로그인을 `303 /users/loginform?redirectUrl=<경로>`+flash `user.login.alert`로 보낸다(`K/web/access/AnonymousCheckStage.kt:24-33`).

## 4. 프로필 설정 `user/edit.html`

### 4.1 개요

`GET /user/editform`(`AccountController.kt:83-85`)과 `GET,POST /user/editform/profile`. 모델 `title`(로그인 ID), `tabId=profile`, `account`(`AccountView`: loginId, name(HTML 정리본), rawName, email, avatarUrl80, avatarUrl256).

### 4.2 레이아웃

머리 `userinfo.accountSetting`, 탭 줄, 왼쪽 기본 정보 폼 `#frmBasic`(float-start), 오른쪽 `#frmAvatar`(왼쪽 여백 50px + 왼쪽 테두리 `1px solid #ddd`, 아바타 128px 고정 `max-width:none`, 업로드 진행 막대 `.upload-progress.avatar`), 아래 `reset-user-visited-list`, 자르기 모달 `#avatarCropWrap`(정적 백드롭, 이미지 최대 폭 500px)(`T/user/edit.html:22-79`). 스크립트 `lib/cropperjs/cropper.js`(Cropper.js 2)와 `user.Setting.js`.

### 4.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 로그인 ID | 읽기 전용 입력 | | 표시 | | | | `edit.html:26` |
| 이름 `name` | 입력 | | | `POST /user/edit` 필드 `name` | | | `:30` |
| 이메일 `email` | `type=email` | | | 필드 `email` | | | `:34` |
| 프로필 수정 단추 | submit | | 제출 | `POST /user/edit` 필드 `name`, `email` | 성공 `303 /{loginId}?daysAgo=14&selected=projects`. 실패 `400`으로 이 화면에 flash 경고. 비로그인 `303` 로그인 | 본인(세션 사용자)만 수정, 대상 id 파라미터 없음 | `:37`, `AccountController.kt:119-129` |
| 아바타 파일(`#avatarFile`) | 파일 입력 | | 선택하면 업로드 → 자르기 모달 | `POST /files`(파일 업로더, `T/common/scripts.html:106`) | 응답 `{id,url,mimeType,...}` | 로그인 | `:54`, `user.Setting.js:108-155` |
| 자르기 모달 "저장"(`.btnSubmitCrop`) | 단추 | 모달 열림 | 선택 영역을 128x128 PNG로 만들어 업로드, 끝나면 `#frmAvatar`에 `avatarId` 숨은 입력을 더해 제출 | `POST /files`, 그다음 `POST /user/edit` 필드 `name`(raw), `email`, `avatarId` | 성공 `303 /{loginId}?daysAgo=14&selected=projects` | | `:77`, `user.Setting.js:282-301` |
| 모달 "취소" | 단추 | | `data-bs-dismiss=modal`, 모달이 닫히면 자르기 상자 제거 | 없음 | | | `:76`, `user.Setting.js:120` |
| 방문 프로젝트 목록 초기화 | submit | | 제출(확인창 없음) | `POST /user/resetVisitedList` | `303 /user/editform`+flash 정보 `userinfo.reset.visited.project.list.done` | 컨트롤러에 `@AnonymousCheck`가 없고 `currentUser`가 없으면 아무것도 지우지 않고 같은 `303`(그 뒤 설정 화면이 로그인으로 보냄) | `:62-64`, `AccountController.kt:146-151` |

서버 검사(`K/domain/user/AccountService.kt:46-74`): 이메일이 비었거나 형식이 틀리면 `user.wrongEmail.alert`. 현재 이메일과 다르고 이미 쓰는 이메일(주 이메일 또는 확인된 보조 이메일)이면 `user.email.duplicate`. `avatarId`가 있으면 이미지 MIME이어야 하고(아니면 아바타를 무시하고 진행), 크기 1,024,000바이트 초과면 `user.avatar.fileSizeAlert`, 업로더가 본인이 아니면 `user.avatar.uploadError`. 이름은 `HtmlSanitizer.defaultSanitize`를 두 번 적용해 저장한다(`:59`). 같은 이메일의 미확인 보조 이메일 행은 삭제한다. 이름과 이메일이 바뀌면 감사 `account.profile_changed`에 전후 값을 남긴다. 아바타 크기 제한은 클라이언트 업로드 단계에는 없어 제출 후 `400`으로 나타난다.

### 4.4 스크립트

`user.Setting.js`(여러 설정 탭이 공통으로 로드). 이 화면에서 쓰는 부분: `yona.Files.attach/getUploader(".avatar-frm")`, 이미지가 아니면 `$yona.alert(user.avatar.onlyImage)`, 업로드 실패는 `$yona.alert(user.avatar.uploadError + (상태))`, 올린 파일의 MIME이 `image/`가 아니면 업로드 파일을 지운다(`:143-155`). 자르기: 최소 한 변 32px, 비율 1:1, 처음 선택은 왼쪽 위 128x128(`:29-34, :194-243`). 진행 막대는 100%가 되면 1초 뒤 숨긴다. 단축키 없음.

### 4.5 권한

모든 처리가 세션 사용자 한 명만 대상으로 한다. 사이트 관리자도 다른 사용자의 프로필을 이 화면으로 바꿀 수 없다. 시험 `TT/web/user/ProfileEditTest.kt`.

## 5. 비밀번호 변경 `user/edit-password.html`

`GET,POST /user/editform/password`. 폼 `#frmPassword`(`POST /user/resetPassword`): 숨은 `loginId`(서버가 읽지 않음), `oldPassword`, `password`, `retypedPassword`(입력 폭 `w-25`). 그 아래 "비밀번호 재설정 메일" 링크(`GET /lostPassword`, 소셜 로그인으로 자동 로그인된 사용자용 안내 `site.resetPasswordEmail.desc`).

서버(`AccountController.kt:131-144`): `oldPassword`나 `password`가 null이면 `400 error.badrequest`. 현재 비밀번호가 틀리면 **프로필 탭을 `400`으로** 다시 그리고 flash 경고 `user.wrongPassword.alert`(비밀번호 탭이 아니다. v1.6 `UserApp.java:596-598`과 같다). 맞으면 새 비밀번호(argon2id)로 저장, 감사 `account.password_changed`(`via=settings`), **현재 세션만** 끊고 `303 /users/loginform`+flash 경고 `user.loginWithNewPassword`. 새 비밀번호 길이와 일치는 서버가 보지 않는다([user-security.md](user-security.md) `usersec-F2`).

스크립트(`user.Setting.js:345-386`): 세 입력란에서 포커스가 나갈 때 `yona.ui.FormValidator`(필수, 새 비밀번호 4자 이상, 확인 일치)로 검증해 오른쪽 팝오버로 오류를 보인다. 오류가 없으면 팝오버를 모두 지운다. 제출 단추는 서버 `POST`. 단축키 없음. 시험 `TT/web/user/ProfileEditTest.kt`(추측: 비밀번호 변경 시험 포함 여부는 확인하지 않음).

## 6. 이메일 `user/edit-emails.html`

`GET,POST /user/editform/emails`(모델 `subEmails`: id, email, valid, avatarUrl(40px)). 위쪽 폼 `POST /user/email` 필드 `email`(`user.email.new` 자리표시자)와 "추가" 단추. 아래 표 첫 행은 주 이메일(`account.email`, `emails.main.email` 라벨, 단추 없음), 이어서 보조 이메일 행마다 단추 2개.

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 이메일 추가 | submit | 항상 | 제출 | `POST /user/email` 필드 `email` | `303 /user/editform`+flash(경고만): 비었으면 `error.required`, 형식 오류 `error.email`, 중복이거나 이미 내 보조 이메일이면 `user.email.duplicate`. 성공하면 flash 없이 `303`(미확인 행으로 저장, 감사 `account.email_added`). 로그인하지 않았으면 `403` 오류 화면 | 로그인 | `edit-emails.html:22-25`, `AccountController.kt:155-173`, `AccountService.kt:110-118` |
| 삭제 | 단추 `data-request-method=delete` `data-request-uri=/user/email/delete/{id}` | 행마다 | `esm/Request.js`가 `fetch DELETE`(헤더 `X-Requested-With`, CSRF 헤더 포함, 확인창 없음) | `DELETE /user/email/delete/{emailId}` | 성공(2xx, 303을 따라간 `GET /user/editform`이 200)이면 페이지 새로고침. 실패는 사용자에게 알리지 않는다(`Request.js:26`, `:85-87`) | 본인 이메일, 또는 사이트 관리자(`AccessControl.isAllowed`가 사이트 관리자에게 항상 참). 없으면 `403`+404 화면(`forbiddenNotFound`) | `:51`, `AccountController.kt:175-181, :221-229` |
| 주 이메일로 | 단추 `data-request-method=put` `th:href=/user/email/setAsMain/{id}` | `mail.valid` | 주소를 `href`에서 읽어 `PUT` | `PUT /user/email/setAsMain/{emailId}` | 주 이메일과 보조 이메일을 맞바꾸고(옛 주 이메일은 확인됨 상태의 보조로) 새로고침. 감사 `account.email_main_changed` | 위와 같음 | `:53`, `AccountController.kt:183-191`, `AccountService.kt:145-153` |
| 확인 메일 보내기 | 단추 `data-request-method=post` `th:href=/user/email/sendValidationEmail/{id}` | `!mail.valid` | `POST` | `POST /user/email/sendValidationEmail/{emailId}` | 50자리 숫자 토큰을 만들고 메일 이벤트 발행, flash 경고 `확인 메일을 전송했습니다.`(**한국어 고정 문자열**, 메시지 키 아님) 뒤 새로고침 | 위와 같음 | `:55-57`, `AccountController.kt:193-200`, `AccountService.kt:127-131` |
| 메일 안의 확인 링크 | 링크(메일) | | 열기 | `GET /user/email/confirm/{emailId}/{token}` | 토큰이 맞으면 확인됨으로 바꾸고(같은 주소의 다른 미확인 행 삭제), **그 이메일의 사용자로 로그인**(2단계 사용자는 코드 화면 경유) 후 `303 /user/editform`. 틀리면 `403` 404 화면 | 링크를 가진 누구나(로그인 불필요) | `AccountController.kt:202-216`, `AccountService.kt:134-142` |

`data-request-*` 단추는 `Request.js`가 문서 로딩 시 처리기를 달고 커서를 `pointer`로 바꾼다. Enter 키도 같은 동작이다(`:107-115, :137-138`). 발견 사항 `user-F6`: 확인 링크(토큰만 알면 로그인되는 경로)는 v1.6 동작이다. 시험 `TT/web/user/EmailSettingsTest.kt`.

## 7. 알림 설정 `user/edit-notifications.html`

`GET,POST /user/editform/notifications`(`AccountController.kt:238-246`). 모델 `notificationProjects`: 내가 구독(`watch`) 중인 프로젝트마다 `id`, `owner`, `name`, `rows`(알림 종류 `EventType`을 `order` 순으로 `type`, `descrKey`, `enabled`). 왼쪽 프로젝트 목록(`#notification-projects`, 첫 항목 `active`, `col-lg-3`)과 오른쪽 탭 내용(프로젝트별 표, 행마다 스위치). 구독 프로젝트가 없으면 목록이 비어 있고 안내 문구는 없다(`T/user/edit-notifications.html:23-41`).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 프로젝트 탭 링크 | 링크 `href=#<projectId>` | 프로젝트마다 | Bootstrap 탭 전환. 처음 로딩 때 `location.hash`가 있으면 그 탭을 보인다(`$yona.showTab`) | 없음 | | | `:25`, `user.Setting.js:424-426` |
| 알림 스위치(`.notiUpdate`) | 체크박스 `data-href=/noti/toggle/{projectId}/{type}` | 행마다 | 바꾸면 `POST`, 성공하면 상태 유지, 실패하면 되돌리고 `$yona.alert(error.failedTo(userinfo.changeNotifications, 상태))` | `POST /noti/toggle/{projectId}/{notificationType}` | `200`(본문 없음). 알 수 없는 종류 `400`, 프로젝트 없음 `404`, 읽기 권한 없음 `403`, 구독하지 않았으면 `400 text/plain`(`error.notfound.watch`) | 로그인(비로그인은 `403`), 프로젝트 `READ` | `:35`, `user.Setting.js:405-422`, `K/web/project/WatchProjectController.kt:73-90` |

스위치는 `data-toggle="switch"`로 표시한다. 켜짐과 꺼짐 라벨 `On`/`Off`는 영어 고정(`data-on-label`, `data-off-label`). 시험 `TT/web/project/` 아래 알림 시험(해당 영역 담당 밖, 미확인).

## 8. 보안 기록 `user/edit-audit.html`

`GET /user/editform/audit`(`K/web/audit/AuditControllers.kt:114-123`, 로그인 필요). 머리 `userinfo.audit`, 설명 `audit.description.user`, 그 아래 `audit/list :: auditList(false, false, false)`를 쓴다. 곧 **필터 폼, 내려받기, 무결성 검증 단추가 모두 꺼져 있다**. 표(열: 시각, 행위자, 동작, 결과, 대상, IP, 상세)와, 행이 없으면 `audit.empty`, 이전/다음(`audit.pager.prev`/`next`) 링크만 있다. 쪽 크기는 `size` 파라미터(1 이상 `MAX_PAGE_SIZE` 이하, 기본 50, `K/domain/audit/AuditQueryService.kt:117`), 쪽 번호 `page`(1 이상). 데이터 범위는 `AuditScope.User(내 id, 내 로그인 ID)`로 본인 기록만. 같은 조각의 다른 모드는 `audit/` 담당. 단축키, 스크립트 없음(`verifiable`이 거짓이라 인라인 스크립트도 출력되지 않는다).

## 9. 내 파일 `user/files.html`

### 9.1 개요

`GET /user/files?pageNum=&filter=`(`UserFilesController.kt:40-53`). 컨트롤러에 `@AnonymousCheck`(로그인 강제 아님). 로그인하지 않았으면 로그인 ID `anonymous`의 파일을 조회한다(`:45, :66`)(`user-F7`). `pageNum`이 숫자가 아니거나 1 미만이면 1쪽(R-48-6), 쪽 크기 50(`PAGE_SIZE`). 모델 `rows`(`FileRow`: id, name, mimeType, size, createdDate, location), `totalPages`, `menuTab=TAB_MY_FILES`, 제목 `User Files`(`siteLayout(#{user.files}, 'SITE_HOME', ...)`).

### 9.2 레이아웃

`common/my-series-menu-tab :: mySeriesMenuTab(menuTab)`(탭 줄: 알림 `/notifications`, 내 이슈 `/user/issues`, 내 파일 `/user/files`, 현재 탭 `active`). `TAB_MY_FILES`에는 "기본 로그인 페이지로 설정" 단추가 나오지 않는다. 그 아래 검색 폼, 파일 표(Bootstrap 12칸 그리드: 미리보기 1, 파일명 4, 크기 1, 다운로드 1, 날짜 2, 위치 3, 머리글 `Preview`, `Filename`, `Size`, `Download`, `Date`, `Location`은 영어 고정), `#pagination` 영역(`T/user/files.html:14-44`). 행이 없을 때의 안내 문구는 없고 머리글만 보인다. 행에는 마우스 올림 시 `hover` 클래스가 붙는다(`:48-54`).

### 9.3 동작 요소

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 검색 입력/단추 | 폼 `GET` | 항상 | 제출 | `GET /user/files?filter=<값>` | 첨부 이름 필터 | 컨트롤러는 현재 사용자 소유만 조회 | `files.html:17-22` |
| 미리보기 링크 | 링크 `target=_blank` | 항상(이미지면 `<img>`) | 새 창 | `GET /files/{id}` | 파일 보기 | 파일 영역 규칙 | `:33` |
| 파일명 링크 | 링크 `target=_blank` | 항상 | 새 창, 확장자로 아이콘 선택(`lib/filetype.js`) | `GET /files/{id}` | | | `:34, :55-63` |
| 다운로드 단추 | 링크+버튼 | 항상 | 이동 | `GET /files/{id}?action=download` | | | `:36` |
| 위치 링크 | 링크 `target=_blank` | `row.location != null` | 새 창 | 파일이 붙은 리소스 주소(`ResourceUrls.urlOf`) | | | `:38`, `UserFilesController.kt:58` |
| 쪽 나누기 | `yona.Pagination.update($("#pagination"), totalPages)` | 항상 | 번호 이동 시 쿼리 `pageNum`을 바꿔 이동 | `GET /user/files?pageNum=n` | | | `files.html:69`, `J/esm/Pagination.js:80-89, :133` |

검색어 입력란의 `value=""`가 고정이라 검색한 뒤에도 입력란이 비어 보이고, 쪽 이동 때 `filter`가 유지되는지는 `Pagination.js`가 현재 쿼리를 이어 붙이는 방식에 달려 있다(`user-F8`). `.user-file-search > button`에 `submit` 핸들러(콘솔 로그)가 있지만 그 요소는 `button`의 `submit` 이벤트를 받지 않으므로 동작하지 않는 죽은 코드이다(`:65-67`). 날짜는 `yyyy-MM-dd h:mm a`(`helper.dateString`), 크기는 `humanReadableByteCount(size, true)`.

단축키, 팝업, 모달 없음. 시험 `TT/web/user/UserFilesTest.kt`.

## 10. 사용자 메뉴와 내 이슈 컨트롤러 요약 (템플릿은 타 영역)

- `GET /user/usermenuTabContentList`(`K/web/user/UserMenuController.kt:27-30`): `index/usermenu-tab-content-list`를 그린다. `@AnonymousCheck`가 없고 `currentUserProvider.get().user`가 null이어도 조립기가 처리한다. 모델 `menu`(`UserMenuModel`: loginId, 내 프로젝트, 조직, 즐겨찾기, 최근 방문, 구독, 내가 만든, 참여, 최근 이슈). 자세한 화면 계약은 `web/user-menu.md`.
- `GET /user/sidebar?path=&hash=`(`:32-47`): `layout/layout-framed`. `hash` 파라미터가 **없으면** `NoSuchElementException`으로 500(빈 값 `hash=`은 정상, v1.6 `getQueryString("hash").get` 재현, R-123). `path`가 없으면 사용자 설정의 로그인 기본 화면(없으면 `/notifications`)을 iframe 경로로 쓴다.
- `GET /user/issues`(`K/web/issue/MyIssuesController.kt:46`)는 이슈 영역 담당이다.

## 11. 발견 사항

| 번호 | 내용 | 근거 |
|---|---|---|
| user-F1 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `GET /user/editform/{tabId}`가 `GET`과 `POST` 둘 다 받는다. 화면은 `GET`만 부르지만 `POST`로도 열린다(v1.6 라우트 재현으로 보이나 v1.6 대조는 하지 않음) | `AccountController.kt:103` |
| user-F2 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `POST /user/resetVisitedList`에 로그인 강제 어노테이션이 없다. 비로그인이면 아무것도 하지 않고 `303 /user/editform`+flash를 돌려주며 이 `303`을 따라가면 다시 로그인으로 간다 | `AccountController.kt:146-151` |
| user-F3 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 이메일 확인 안내 flash가 한국어 고정 문자열 `확인 메일을 전송했습니다.`이다(다른 언어 설정에서도 한국어). `messageText`는 경고 키만 번역한다 | `AccountController.kt:199` |
| user-F4 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 사용자 페이지의 며칠 전 입력 Enter 이동이 탭을 눌러도 갱신되지 않는 `sTabSelected`(처음 값)를 쓴다 | `J/esm/service/user.View.js:38, :118-123, :134` |
| user-F5 **[브라우저로 확인됨 2026-11-11, R-162]** | 알 수 없는 설정 탭 id(`/user/editform/foo`)는 예외가 되어 500 화면이다(404가 아님). 11개 탭 중 5개만 이 메서드가 처리한다 | `AccountController.kt:108-116` |
| user-F6 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 이메일 확인 링크는 토큰(숫자 50자리)만으로 해당 사용자로 로그인한다. 2단계 인증 사용자는 코드 화면을 거친다(추가 항목) | `AccountController.kt:202-216` |
| user-F7 **[검증: v2 신규 결함, 수정됨 A-460]** | `/user/files`는 로그인하지 않으면 로그인 ID가 `anonymous`인 사용자의 파일을 보여 준다(익명 접근이 허용된 사이트일 때). 이 이름의 실제 사용자가 있으면 그의 파일이 노출된다 | `UserFilesController.kt:45, :66` |
| user-F8 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `files.html`의 검색 입력란이 `value=""` 고정이라 검색어가 화면에 유지되지 않고, 죽은 `submit` 핸들러(콘솔 로그 `searche!`)가 남아 있다 | `T/user/files.html:19, :65-67` |
| user-F9 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 사용자 페이지의 구독 단추가 비로그인에도 보인다(`showWatchButton = viewer?.loginId != project.owner`). 서버는 `READ` 검사만 하고 비로그인 구독은 `requireNotNull(...user)`에 걸린다(접근 검사 체인이 앞서 막는지는 확인하지 못했다) | `UserPageAssembler.kt:149`, `WatchProjectController.kt:47-54` |
| user-F10 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 이슈 탭의 열림/닫힘 분류가 `OPEN`, `CLOSED`뿐이라 초안(`DRAFT`)은 건수 배지에도, 어느 목록에도 나타나지 않는다. 작성자 본인의 초안만 `activity.issues`에 들어오지만 표시되지 않는다 | `UserPageAssembler.kt:86-87, :106`, `T/user/view.html:92, :102` |
| user-F11 **[검증: 결함 아님]** | 위키와 코드 불일치는 발견하지 못했다. `web/user-page.md`, `web/account.md`의 내용과 이 글이 어긋나는 곳은 확인한 범위에서 없다(두 글을 전부 대조하지는 않았음) | |

## 12. 범위 점검

| 템플릿 | 다룬 절 |
|---|---|
| `user/view.html` | 2 |
| `user/partial-issues.html` | 2.3, 2.4 |
| `user/partial-pull-requests.html` | 2.4 |
| `user/partial-projectlist.html` | 2.4 |
| `user/files.html` | 9 |
| `user/edit.html` | 4 |
| `user/edit-password.html` | 5 |
| `user/edit-emails.html` | 6 |
| `user/edit-notifications.html` | 7 |
| `user/edit-audit.html` | 8 |
| `user/partial-edit-tabmenu.html` | 3 |
| 나머지 `user/` 22개 | [user-security.md](user-security.md) 또는 [oauth.md](oauth.md) |
