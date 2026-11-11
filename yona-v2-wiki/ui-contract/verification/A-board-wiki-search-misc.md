---
title: UI 계약 발견 사항 재현과 v1.6 대조 (담당 A: 게시판, 위키, 검색, 이전, 시작 안내, 감사)
type: ui-contract-verification
updated: 2026-11-11
---

# 담당 A 재현과 v1.6 대조 결과

- 대상 42건(board 11, migration 8, welcome 4, audit 5, search 6, wiki 8). 모두 v2 검증 앱(19410)에서 재현을 시도했고, v1.6은 공식 v1.16.0 릴리스를 직접 실행(19510)해 같은 요청과 화면 동작을 대조했다. 위키와 감사는 v1.6에 없는 기능이라 "해당 없음"이다.
- 재현 수단: Playwright(화면 스크립트 동작), 스크립트를 거치지 않는 직접 요청, 데이터 생성(계정 `orgadm`, `grpmem`, `authoru`, 글 수십 건, 조직 관리자와 그룹 구성원, 비공개 프로젝트 구성원 제거 등). 이전 화면은 GitHub 토큰 교환이 외부 서비스라 브라우저에서 HTML의 토큰 값을 주입하고 `api.github.com` 요청을 가짜 응답으로 가로채 재현했다(외부 접속 없음). 이전 화면과 시작 안내 화면은 설정 때문에 앱을 다시 띄웠다(v2: `--yona.github.allow-migration=true`, 빈 데이터 디렉터리. v1.6: `-Dgithub.allow.migration=true`, 기본 `application.secret`).

## 요약

| 판정 | 개수 | 항목 |
|---|---|---|
| v2 신규 결함 | 2 | board-F15, search-F4 |
| v1.6 결함 유지 | 26 | board-F4, F5, F6, F9, F10, F11, F12, F13, F14, F16, migration-F1, F2, F3, F4, F5, F6, F8, welcome-F1, F2, F3, F4, search-F2, F3, F5, F6, F7 |
| v2 추가 기능의 결함 | 7 | audit-F4, wiki-F1, F2, F3, F4, F5, F6 |
| 결함 아님 | 6 | audit-F1, F2, F3, F5, wiki-F7, F8 |
| 문서 문제 | 1 | migration-F7 (화면 코드의 머리 주석) |
| 재현 불가 | 0 | |

**v2 신규 결함 목록 (고치는 방법 포함)**

1. board-F15: 익명이 글 보기에서 `변경 이력`을 누르면 로그인 화면으로 가지 않고 콘솔 오류만 나며 화면이 그대로이다(v1.6은 로그인 화면으로 이동). 고침: `src/main/resources/templates/board/view.html:45`의 익명용 링크에서 `data-bs-toggle="modal"`을 뺀다(모달이 없는 링크이므로).
2. search-F4: 검색 결과 강조 스크립트가 `.title` 모든 요소의 HTML을 글자로 바꾸어, 조직 검색 화면에서 비구성원에게 보이는 `멤버 등록 요청하기` 링크(`enrollBtn`)가 사라진다(v1.6은 키워드가 그 마크업과 겹치지 않으면 보존). 고침: `src/main/resources/templates/search/partial-search.html:107`의 선택자 `.search-content-body, .title`을 결과 영역 안으로 한정(예: `.search-result-wrap .search-content-body, .search-result-wrap .title`)한다.

**v2 추가 기능의 결함 (v1.6에 없는 감사, 위키)**

- audit-F4: 검증 단추가 응답 상태를 보지 않는다(403 HTML이면 JSON 파싱 오류만 나고 화면 무반응, 필드 없는 500 JSON이면 `undefined번 기록`). 고침: `templates/audit/list.html:75-82`에서 `r.ok` 확인과 `.catch`로 오류 문구를 `.audit-verify-result`에 보인다.
- wiki-F1: 리비전이 하나일 때 `from`이 선택되지 않아 비교가 400. 고침: `templates/wiki/history.html:24`의 `th:checked="${stat.index == 1}"`를 리비전이 하나일 때도 선택되도록(`stat.index == 1 or stat.size == 1`) 하거나 하나뿐이면 비교 단추를 숨긴다.
- wiki-F2: 익명이 쓰기 경로를 열면 로그인으로 이동하지 않고 403(같은 프로젝트의 글쓰기는 303 로그인 이동). 고침: `WikiViewController.kt`의 `deny()`에서 익명이면 로그인 이동(303)을 돌려준다.
- wiki-F3: 비구성원의 `POST /wiki/_upload`가 JSON이 아닌 HTML 403. 고침: `WikiViewController.upload`의 `deny()` 결과를 JSON 요청이면 JSON 403으로 바꾼다.
- wiki-F4: 편집 화면에 이탈 경고가 없다(게시판은 있음). 고침: `templates/wiki/edit.html`에 글 작성과 같은 `beforeunload` 처리를 더한다.
- wiki-F5: 문서 `title`(`Wiki`, `New Page`, `Edit X`, `X - Revisions`, `X - Compare`)이 한국어 화면에서도 영어. 고침: `WikiViewController.kt:86`, `:110`, `:138`, `:343`의 상수를 `messages` 키로 바꾼다.
- wiki-F6: 복제 주소 복사 단추가 성공과 실패를 알리지 않는다. 고침: `templates/wiki/shell.html:56-61`에서 `writeText(...).then(...).catch(...)`로 알림을 보인다.

## 게시판 (board.md)

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| board-F4 | 재현됨 | 조직 `orgx`의 그룹 관리자 `orgadm`(프로젝트 직접 구성원 아님)과 그룹 구성원 `grpmem`으로 `orgx/alpha-one` 글쓰기 화면은 `notice` 체크박스가 있고(둘 다 true), 글 수정 화면은 없다(둘 다 false). 직접 구성원인 `plainuser`는 둘 다 있다 | 같음. v1.6 `org1/orgproj`에서 `orgadm`, `grpmem` 모두 글쓰기 true, 수정 false(실행). `BoardApp.java:165-166`(생성), `:324`(수정은 `ProjectUser.isAllowedToNotice`), `ProjectUser.java:153` | v1.6 결함 유지 | 장부에 올린다 |
| board-F5 | 재현됨 | `POST /admin/hglfs/posts` 제목 빈 값, 본문 `내가 쓴 본문 XYZ` -> 400, 본문에 `XYZ` 없음(false), 제목 `value=""`. `?readme=on`을 붙여도 같음. 수정 오류(`/orgx/alpha-one/post/1/edit`, 제목 빈 값)는 400에 저장된 제목 `v-F4 orgx/alpha-one`이 보이고 제출 본문 `QQQ`는 없음 | 같음. v1.6 `tester/proj1` 400에서 본문 `XYZ` 없음, 제목 `value=""`, 수정 오류는 저장된 제목 `공지입니다`. `create.scala.html:54`(`value="@titleMessage"`), `:77`(`preparedPostBody`는 오류 때 `""`, `BoardApp.java:219`), `edit.scala.html:28` | v1.6 결함 유지 | 장부에 올린다 |
| board-F6 | 재현됨 | 글 17건 이상 프로젝트(`admin/gitproj`)에서 `#pagination a`는 `다음 페이지` 한 개(href `...posts?&pageNum=2`, `pageNum` 속성 null). 클릭하면 `pageNum=2` 주소로 이동, 목록 `페이지글 2, 1`. `#pagination input` 1개(번호 입력) | 같음. v1.6 `tester/proj1`: 링크 하나 href `...?&pageNum=2`, `pageNum` 속성 null, 이동 확인(실행). `yobi.board.List.js:49`, `:57`, `:64-66`, `:98-99`(링크를 `update` 전에 잡음) | v1.6 결함 유지 | 장부에 올린다 |
| board-F9 | 재현됨 | 글 보기 `#watch-button` 클릭: `POST /watch?resource.type=board_post&resource.id=163` 200, `POST /unwatch?...` **303** (`Accept: */*`). 같은 요청을 `fetch`로 따라가면 `redirected: true`, 응답 HTML(55852자)에 `이제 이 글에 관한 알림을 받지 않습니다`가 들어 있고 버려진다. 새로 열면 `.alert` 없음 | 같음. v1.6도 unwatch가 303(`Accept: */*`), 새로 연 화면에 안내 없음(실행). `WatchApp.java:72-84`(JSON이 아니면 flash 후 `redirect`), `yobi.Common.js:209-250`(`dataType: null`) | v1.6 결함 유지 | 장부에 올린다 |
| board-F10 | 재현됨 | 게시판 메뉴가 꺼진 `admin/hglfs`, `admin/a-b`, `admin/gitproj`는 글 목록에 `글쓰기` 링크(`/postform`)가 있고 `N` 키맵은 없음. 메뉴가 켜진 `admin/secretproj`, `plainuser/ownerproj`는 `"N"` 키맵 있음. 익명도 `/admin/a-b/posts`에서 링크 보임 | 같음. v1.6 `tester/noboard`(board 미선택)에서 `postform` 링크 true, `"N":` false. 켜진 `proj1`은 둘 다 true. 익명 `proj1` 링크 true. `list.scala.html:48`(조건 없음), `:102-104`(`menuSetting.board`일 때만 N) | v1.6 결함 유지 | 장부에 올린다 |
| board-F11 | 재현됨 | `POST /orgx/alpha-one/post/1/label` 본문 `["5"]`(`admin/hglfs`의 라벨 5) -> 200 `{"id":"orgx","labels":1}`. 다른 프로젝트 라벨이 붙는다. (경로는 `/-_-api/v1/owners/.../postlabel/{n}`이 아니라 `/{owner}/{project}/post/{n}/label`로 v2에서 바뀌었다.) | 같음(더 느슨). v1.6 `POST /-_-api/v1/owners/org1/projects/orgproj/postlabel/1` 본문 `["33"]`(`tester/proj1`의 라벨) -> `third`(비구성원)도, **익명도 200** `{"id":"org1","labels":1}`(실행). `BoardApi.java:49-56`(`byId`만, 프로젝트 대조 없음, 권한 검사 없음). v2는 수정 권한을 요구(A-24)하되 프로젝트 대조는 없다 | v1.6 결함 유지 | 장부에 올린다 |
| board-F12 | 재현됨 | 관리자가 `admin/gitproj/post/1`에 단 댓글(id 1)을 `DELETE /admin/hglfs/post/1/comment/1/delete`(Ajax)로 요청 -> 204, `Location: /admin/gitproj/post/1`, 댓글이 사라짐. 주소의 프로젝트와 글 번호는 대조하지 않고 댓글의 실제 프로젝트로 권한 판단 | 같음. v1.6 `DELETE /org1/orgproj/post/1/comment/33/delete` -> 204 `Location: /tester/proj1/post/1`, 삭제됨(실행). `BoardApp.java:488-493` | v1.6 결함 유지 | 장부에 올린다 |
| board-F13 | 재현됨 | 비구성원 `authoru`가 `admin/gitproj/post/1` 보기: 수정 아이콘 1개, 삭제 아이콘 0개. 아이콘을 누르면 수정 폼이 열리고 보이는 단추는 `체크리스트 추가`, `취소`뿐이며 저장 단추 없음 | 같음. v1.6 `third` 계열 `authoru`로 `tester/proj1/post/1`: 수정 아이콘 3개, 삭제 0개, 열린 폼에 저장 단추 없음(실행). `partial_comments.scala.html:57`(`Operation.READ`), `:66`(`isAllowedUpdate`) | v1.6 결함 유지 | 장부에 올린다 |
| board-F14 | 재현됨(서술 일부 틀림) | `authoru`를 `admin/secretproj`(비공개) 구성원으로 넣고 글 작성(`/post/2`) 뒤 구성원에서 제거. 그 뒤: 글 보기 **403**, 목록 403, 삭제 **403**, 그러나 `editform` **200**, `POST .../edit` **303**(성공, 관리자가 `HACK title` 확인). 즉 "`IsAllowed`가 막는다"는 서술은 글 보기와 삭제에만 맞고 **수정은 막히지 않는다**. 공개 프로젝트의 비구성원 작성자는 자기 글을 수정(303)하고 삭제(303)한다 | 같음. v1.6 `tester/priv1`에서 같은 순서: 보기 403, 목록 403, 삭제 403, editform 200, 수정 303, 소유자가 `HACK` 확인(실행). `AccessControl.java:243-246`(`isAllowedIfAuthor`) | v1.6 결함 유지 (발견 사항의 괄호 서술 정정 필요) | 장부에 올린다. 위키 서술의 "단 IsAllowed는 ... 막힌다"를 "글 보기와 삭제만 막히고 수정 화면과 제출은 열린다"로 고친다 |
| board-F15 | 재현됨 | 글을 한 번 수정해 변경 이력이 있는 `admin/gitproj/post/1`을 익명으로 열면 `.posting-history a`의 href는 `/users/loginform?redirectUrl=/admin/gitproj/post/1`. 클릭하면 **이동하지 않고** 주소가 그대로 `/admin/gitproj/post/1`, 콘솔 오류 `Cannot read properties of undefined (reading 'backdrop')`. 발견 사항의 "이동만 한다(추측)"는 **틀림**(이동조차 안 된다) | 다름. v1.6은 같은 링크 클릭 시 `/users/loginform?redirectUrl=/tester/proj1/post/1`로 **이동**하고(콘솔에 jQuery 선택자 오류만), 로그인 화면에 도달(실행). `view.scala.html:70-74` | **v2 신규 결함** | 고친다: `templates/board/view.html:45`의 `data-bs-toggle="modal"` 제거 |
| board-F16 | 재현됨 | 글쓰기 화면의 `tabindex` 순서 `1,3,3,4`(제목 1, 편집기 3, 저장 3, 취소 4), 취소 링크 `javascript:history.back()` 존재 | 같음. v1.6 글쓰기 화면도 `1,3,3,4`, 같은 링크(실행). `create.scala.html:77`, `:111-112` | v1.6 결함 유지 | 장부에 올린다 |

## 이전 (misc.md migration)

GitHub 호출은 가짜 응답으로 대체했다. 토큰은 HTML의 `data-token`(v2) 또는 `setUserToken('')`(v1.6)을 바꾸어 주입했다.

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| migration-F1 | 재현됨 | 토큰 없이 `/migration`을 열면 GitHub로 이동 요청 `https://github.com/login/oauth/authorize?client_id=e7f9ad76a3a4ba19b2a5&scope=user,repo,admin:org&state=...`. 서버를 `--yona.github.client-id=testclient`로 띄워도 같은 고정 값 | 같음. v1.6 이동 URL도 `client_id=e7f9ad76a3a4ba19b2a5`(state 없음). `yona.Migration.js:21`, `:87` | v1.6 결함 유지 | 장부에 올린다 |
| migration-F2 | 재현됨 | 대상 선택 뒤 경고 배지가 `Admin에  유저가 없음`, `유저가 ... 그렇지 않을 경우  대신 사용자 아이디가 ...`(`{0}` 자리 빈 글자) | 같음. v1.6 `Admin에  유저가 없음` 등 같은 빈 자리(실행). `home.scala.html`의 `vm.CONFIG.DEFAULT_WORKER`, `yona.Migration.js:17` | v1.6 결함 유지 | 장부에 올린다 |
| migration-F3 | 재현됨 | 대상이 조직(`myorg/orgdest`, 가짜 응답 admin)일 때 `GET /orgs/myorg/memberships/ghme`, 로그 이슈 POST, `POST /repos/myorg/orgdest/milestones` 모두 `Authorization: token `(빈 토큰). 개인 대상(`ghme/dest1`)은 `token faketok` | 같음. v1.6도 조직 대상이면 위 세 요청이 `auth=token`(빈 값), 개인 대상은 사용자 토큰(실행). `yona.Migration.js:234`, `:426-428` | v1.6 결함 유지 | 장부에 올린다. 실제 GitHub의 응답(거부)은 외부 서비스라 확인하지 못했다 |
| migration-F4 | 재현됨 | 마일스톤, 이슈 옮기기를 누르면 `POST /repos/yona-projects/yona-migration-log/issues`가 먼저 나간다. 사용자 안내 문구 없음 | 같음. v1.6도 같은 POST(실행). `yona.Migration.js` `logToIssue` | v1.6 결함 유지 | 장부에 올린다 |
| migration-F5 | 재현됨 | `.source-destination`의 열이 `col-lg-4`(w423) + `col-lg-4`(w423) + `col-lg-6`(w635): 세 번째가 같은 줄에 못 들어가 y=854로 내려감(앞 둘은 y=234) | 같음. v1.6 `span4 + span4 + span6`, 세 번째가 y=762로 내려감(실행). `home.scala.html:31`, `:44`, `:75` | v1.6 결함 유지 | 장부에 올린다 |
| migration-F6 | 재현됨 | 이슈나 게시글 전송이 끝까지 가면 콘솔 로그 `Not Yet Support! :: delegateAttachmentsMigration`(가짜 응답 202로 확인) | 다름. v1.6은 같은 지점에서 `TypeError: Cannot read properties of undefined (reading 'then')`가 난다(실행). v2는 오류를 고치고 로그만 남김. `yona.Migration.js:123`, `:128`, `:685` | v1.6 결함 유지(v1.6은 더 나쁨, v2는 로그만) | 장부에 올린다 |
| migration-F7 | 재현됨 | `templates/layout/migration-layout.html`의 머리 주석(7행)은 jquery 1.9.0이라고 쓰나 실제 `:39`는 `jquery-3.7.1.js` | v1.6은 `migrationPageLayout.scala.html:33`에서 `jquery-1.9.0.js`를 실제로 불러 주석이 맞았다. v2는 라이브러리를 올리고 주석을 고치지 않았다 | 문서 문제(코드 주석) | 문서만 고친다: 주석을 3.7.1로 고친다 |
| migration-F8 | 재현됨 | 마일스톤 전송이 422로 실패하면 `#system-msg`에 JSON 덩어리 `[{"id":1,"title":...},{"resource":"x","code":"invalid"},...]`만 보인다. 서버 요청 `/migration/admin/secretproj/projects/secretproj`를 403으로 바꾸어도 화면에 오류 안내 없음(제목에 `admin/secretproj`만 선택되고 메시지는 `OK!`) | 같음. v1.6 422 때 같은 JSON 덩어리(실행). 서버 403 쪽은 v1.6 실행 확인을 하지 않았고 `yona.Migration.js`의 `error`가 콘솔만 기록하는 구조는 같다(소스 대조) | v1.6 결함 유지 | 장부에 올린다 |

## 시작 안내 (misc.md welcome)

v2는 빈 데이터 디렉터리로 새로 띄워, v1.6은 기본 `application.secret`으로 띄워 비밀 키 기본값 상태를 만들었다.

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| welcome-F1 | 재현됨 | `POST /`에 `loginId` 없음 -> 500, `password` 없음 -> 500 | 같음. v1.6도 둘 다 500(실행). `Global.java:298` 주변 | v1.6 결함 유지 | 장부에 올린다 |
| welcome-F2 | 재현됨 | 화면을 열면 `document.activeElement`가 `BODY`(초점 이동 없음). 입력란은 `loginId(ro), name, email, password, retypedPassword`로 `seed` 없음 | 같음. v1.6도 `BODY`, `seed` 입력 없음(실행). `secret.scala.html:144` | v1.6 결함 유지 | 장부에 올린다 |
| welcome-F3 | 재현됨 | 비밀번호 `p` 한 글자, 확인 `p`로 `POST /` -> 200 `Welcome!`(재시작 안내). 이후 `GET /`도 같은 안내 | 같음. v1.6도 `p`로 200(실행) | v1.6 결함 유지 | 장부에 올린다 |
| welcome-F4 | 재현됨 | `.secret-box` 너비 비율 0.50, 375px 뷰포트에서 178px | 같음(규칙 동일 `width:50%`). v1.6은 비율 0.55, 375px 뷰포트 기준 521px(v1.6에는 모바일 뷰포트 설정이 없어 데스크톱 폭으로 그려짐). 규칙은 같고 모바일에서 v2가 더 좁게 보인다 | v1.6 결함 유지 | 장부에 올린다 |

## 감사 (misc.md audit, v1.6에 없는 기능)

| 항목 | 재현 | 재현 방법과 관찰값 | v1.6 대조 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| audit-F1 | 재현됨 | `/sites/audit` 필터 폼의 입력은 `actor, action, outcome, target, ip, from, to`(`targetType`, `projectId` 없음). `?targetType=USER&actor=admin`을 주면 내려받기 링크가 `...export?actor=admin&targetType=USER&format=csv`, 쪽 이동이 `...&targetType=USER&projectId=5&page=1&size=1`로 이어 붙인다 | 해당 없음 | 결함 아님(고급 쿼리는 의도된 URL 전용 필터) | 문서만 고친다(필요하면) |
| audit-F2 | 재현됨 | `/user/editform/audit`에 필터 폼 없음. `?actor=nobody-xyz`를 주면 목록이 비어 있음(`audit-empty`), 필터 없이는 51줄. 범위 제한은 항상 적용 | 해당 없음 | 결함 아님(의도. 사용자 화면이 본인 범위에서 필터만 더 좁힌다) | 조치 없음 |
| audit-F3 | 재현됨 | 한국어 화면에서 링크 글자가 `최신`(`.audit-prev`), `이전`(`.audit-next`). 영어는 `Newer`/`Older` | 해당 없음 | 결함 아님(최신이 앞쪽이라 의미가 맞는 표현) | 조치 없음 |
| audit-F4 | 재현됨 | `검증` 요청을 403 HTML로 바꾸면 `.audit-verify-result`가 빈 글자, 페이지 오류 `Unexpected token '<' ... is not valid JSON`. 필드 없는 500 JSON이면 `감사 사슬이 undefined번 기록에서 어긋났습니다(undefined).`. 정상은 `감사 사슬이 온전합니다(기록 716건을 확인했습니다).`. 단추에 `data-url` 속성 없음 | 해당 없음 | v2 추가 기능의 결함 | 고친다(위 요약의 고침) |
| audit-F5 | 재현됨 | 사이트 화면의 `actor=admin` 기록에는 `project.created`, `org.member_added`, `site.*` 등이 있고, 같은 관리자의 사용자 화면(`size=200`)에는 `auth.login_success`, `auth.login_failure`, `auth.logout`, `token.created/updated/deleted`만 나온다 | 해당 없음 | 결함 아님(설명 문구 `audit.description.user`대로) | 조치 없음 |

## 검색 (search.md)

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| search-F2 | 재현됨 | 없는 조직 `/organizations/nonexistorg/search?...`, 빈 `keyword`, 알 수 없는 `searchType`, 프로젝트 범위의 빈 `keyword`, 알 수 없는 `searchType`, `searchType=project` 모두 400 | 같음. v1.6도 여섯 요청 모두 400(실행). `SearchApp.java`의 `badRequest` 분기 | v1.6 결함 유지 | 장부에 올린다 |
| search-F3 | 재현됨 | 일치 0건이면 `<h3 class="search-result-title">Found <strong>0</strong> result(s) in Issues`(한국어는 `0 건이 검색 되었습니다`), `<div class="empty-result"></div>`는 비어 있다. 제목에 키워드 없음 | 같음. v1.6도 `Found 0 result(s)`와 빈 `empty-result`(실행). `partial_search.scala.html:138-139` | v1.6 결함 유지 | 장부에 올린다 |
| search-F4 | 재현됨 | 비구성원 `authoru`로 `/organizations/orgx/search?keyword=alpha&searchType=auto`: 서버 HTML에는 `div.dropdown-menu.flat.right.title` 안에 `a.enrollBtn`(멤버 등록 요청하기)이 있으나, 스크립트 실행 뒤 그 요소의 안쪽 요소가 없고(링크 0개, 글자만 남음) 등록 요청을 보낼 수 없다 | 다름. v1.6은 같은 화면에서 링크가 1개 남는다(키워드 `orgproj`가 마크업과 겹치지 않으면 `html()`이 바뀌지 않음, 실행). `partial_search.scala.html:189` | **v2 신규 결함** | 고친다: `templates/search/partial-search.html:107`의 `.title` 선택자를 결과 영역 안으로 한정 |
| search-F5 | 재현됨 | 일치 0건이어도 탭 7개가 모두 링크이고(`이슈 0`, `사용자 0` ...) `게시판 0`을 누르면 `...search?searchType=post&keyword=zzzqqq`로 이동. 프로젝트 범위 `searchType=project`는 400(F2) | 같음. v1.6도 같은 탭 7개, 같은 이동 주소(실행) | v1.6 결함 유지 | 장부에 올린다 |
| search-F6 | 재현됨 | `searchType=auto&keyword=페이지글`(글 23건, 쪽 크기 20): 쪽 링크 href가 `...searchType=auto&pageNum=2`. 그 사이 이슈 `페이지글 이슈`를 만들면 같은 `pageNum=2` 주소가 `Found 1 result(s) in Issues`로 풀려 종류가 바뀐다 | 같음. v1.6도 링크 href에 `searchType=auto`(글 24건), 이슈를 만든 뒤 같은 주소가 `Found 1 result(s) in Issues`(실행) | v1.6 결함 유지 | 장부에 올린다 |
| search-F7 | 재현됨 | 사용자 결과 기본 아바타 `<img src="/assets/images/default-avatar-128.png"/>`에 `width`/`height` 없음 | 같음. v1.6도 `<img src="/assets/images/default-avatar-128.png">` 크기 속성 없음(실행). `partial_users.scala.html:32-36` | v1.6 결함 유지 | 장부에 올린다 |

## 위키 (wiki.md, v1.6에 없는 기능)

| 항목 | 재현 | 재현 방법과 관찰값 | v1.6 대조 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| wiki-F1 | 재현됨 | `Page One`(리비전 1개) 이력 화면: 행 1개, `to` 라디오 선택 1, `from` 선택 0. `Compare Revisions`를 누르면 `/_compare?to=<해시>`로 이동하고 400(본문 `two revisions` 안내). 직접 `?to=abc`도 400 | 해당 없음 | v2 추가 기능의 결함 | 고친다(위 요약) |
| wiki-F2 | 재현됨 | 익명, 비구성원 `authoru` 모두 `GET _new`, `GET _edit`, `POST /wiki`, `POST _edit`, `POST _delete` 403 HTML(익명 본문 `You are not authorized`). 같은 조직 프로젝트의 `/postform`은 익명에게 303 `/users/loginform?redirectUrl=...` | 해당 없음 | v2 추가 기능의 결함 | 고친다(위 요약) |
| wiki-F3 | 재현됨 | 익명과 비구성원의 `POST /orgx/alpha-one/wiki/_upload`(`Accept: application/json`) 모두 403 `text/html;charset=UTF-8`. 구성원은 201 JSON `{"name":"a.txt","path":"uploads/a.txt","markdown":...}` | 해당 없음 | v2 추가 기능의 결함 | 고친다(위 요약) |
| wiki-F4 | 재현됨 | 위키 편집 화면에서 글자를 입력한 뒤 다른 주소로 가도 `beforeunload` 대화상자가 안 뜬다(false). 게시판 글쓰기는 뜬다(true). 편집 화면에서 `localStorage`를 쓰는 스크립트는 있었다(임시 저장 여부는 확인하지 않음) | 해당 없음 | v2 추가 기능의 결함 | 고친다(위 요약) |
| wiki-F5 | 재현됨 | 한국어 화면에서 `document.title`이 `Page One - orgx/alpha-one`, 새 문서 `New Page - ...`, 편집 `Edit Page One - ...`, 이력 `Page One - Revisions - ...`, 홈 `Wiki - ...` | 해당 없음 | v2 추가 기능의 결함 | 고친다(위 요약) |
| wiki-F6 | 재현됨 | 복사 단추(`.wiki-clone-copy`)를 누르면 클립보드에 `http://admin@127.0.0.1:19410/orgx/alpha-one.wiki.git`이 들어가나 단추 글자(`복사`)와 마크업 길이(316)가 전후 같고 알림 요소 0개 | 해당 없음 | v2 추가 기능의 결함 | 고친다(위 요약) |
| wiki-F7 | 재현 안 됨(조건이 현재 없음) | 홈, 보기, 이력, 새 문서, 편집 5개 화면 모두 사이드바 조각이 한 번만 들어간다(`wiki-pages-filter` 요소 1개씩). 두 번 넣는 화면이 없어 잠재 조건을 만들 수 없다(코드를 바꾸지 않는 한) | 해당 없음 | 결함 아님(잠재 제약, 현재 영향 없음) | 조치 없음 |
| wiki-F8 | 재현됨(없음을 확인) | `GET /wiki/_uploads`, `.../_uploads/a.txt` 삭제 경로, `_attachments`가 모두 404(목록/삭제 경로 없음). 올린 파일은 `GET /wiki/_uploads/a.txt`로만 받는다 | 해당 없음 | 결함 아님(결정 A-315의 의도) | 조치 없음 |

## 범위 점검

목록의 42건 번호가 모두 위 표에 있다.

| 영역 | 번호 | 있음 |
|---|---|---|
| board | F4, F5, F6, F9, F10, F11, F12, F13, F14, F15, F16 | 11/11 |
| migration | F1, F2, F3, F4, F5, F6, F7, F8 | 8/8 |
| welcome | F1, F2, F3, F4 | 4/4 |
| audit | F1, F2, F3, F4, F5 | 5/5 |
| search | F2, F3, F4, F5, F6, F7 | 6/6 |
| wiki | F1, F2, F3, F4, F5, F6, F7, F8 | 8/8 |
