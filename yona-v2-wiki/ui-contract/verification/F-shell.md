---
title: 공통 틀과 전역 스크립트 발견 사항 재현과 v1.6 대조 (담당 F)
type: ui-contract-verification
updated: 2026-11-11
---

# 공통 틀과 전역 스크립트 발견 사항 재현과 v1.6 대조 (담당 F)

v2 검증 앱(포트 19415)과 공식 v1.16.0(포트 19515)을 함께 띄워 Playwright로 같은 이벤트와 같은 요청을 일으켜 비교했다. v1.6 실행 결과가 1순위 근거이고 소스(`yona-v1.6-run`)는 보조 근거이다. 스크립트는 `verification` 작업 폴더 `work/F/scripts/`에 있다.

## 요약

- 항목 수: 22
- 판정별: v1.6 결함 유지 17, v2 신규 결함 1, 문서 문제 1, 결함 아님 3, v2 추가 기능의 결함 0, 재현 불가 0
- **v2 신규 결함**: shell-F16 (컨텍스트 경로 배포에서 `@{}` 없이 쓴 `/...` 링크가 어긋난다. v1.6은 역방향 라우트라 컨텍스트 경로를 반영했다)
- 문서 문제: shell-F14 (대체 경로 시도 횟수 서술이 다르다)
- 결함 아님: shell-F18, shell-F22, shell-F24
- v1.6 결함 유지: shell-F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F15, F17, F20, F23, F25, F26, F27
- 시험 환경 메모: v1.6 사이트 관리자 `admin`의 비밀번호는 이 스냅샷에서 `admin1234`였다(키트 문서의 `admin`이 아님). 항목 확인용으로 v2는 `--yona.github.allow-migration=true`, `--yona.max-file-size=1024`, `--server.servlet.context-path=/yona`를, v1.6은 `-Dgithub.allow.migration=true`, `-Dapplication.maxFileSize=1024`, `-Dapplication.context=/yona`를 잠시 켰다가 모두 원래대로 돌려 다시 띄운 뒤 껐다. 또 v1.6 `tester/proj1`의 메뉴(게시판, 마일스톤, 풀 리퀘스트)를 껐고 이슈 하나를 만들었다(사본 DB라 무관).

## 판정표

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| shell-F4 | 재현됨 | `admin/hglfs/issue/1`에서 `yona.ShortcutKey.getHandlers()` 키는 `SHIFT+CTRL+ENTER`로 등록되어 있다. 댓글 입력란에서 Control+Shift+Enter를 누르면 이벤트가 만든 키 문자열은 `CTRL+SHIFT+ENTER`이고(`CTRL+SHIFT+ENTER` 이름으로 감시기를 달아 확인) 등록된 `SHIFT+CTRL+ENTER`와 달라 처리기가 불리지 않는다. 브라우저 정렬 결과 `['CTRL','SHIFT','ENTER'].sort(v=>-1*indexOf(v))` = `SHIFT+CTRL+ENTER`로 확인했다. 원인은 인자 하나짜리 비교 함수(`ShortcutKey.js:227-235`)이다 | 같음(실행). `tester/proj1/issue/1`의 등록 키가 `SHIFT+CTRL+ENTER`이고 Control+Shift+Enter에 처리기 호출 0회. 소스 `yobi.ShortcutKey.js:171` 같은 비교 함수, `yobi.issue.View.js:505` | v1.6 결함 유지 | 장부에 올린다(R-147 계열). 고치려면 `ShortcutKey.js`의 `_normalizeKeyString`을 `sort((a,b)=>idx(a)-idx(b))`로 하고 `CTRL+ALT+SHIFT` 순서로 맞춘다 |
| shell-F5 | 재현됨 | 메뉴를 끈 `admin/hglfs`(게시판, 마일스톤, 풀 리퀘스트 꺼짐)의 이슈 목록 안내에는 `H B I C M P Q N ...`가 나오지만 처리기는 `H C I Q ...`뿐(`B`, `M`, `P` 없음). 일반 사용자 `plainuser`로 `hglfs/issue/1`을 열면 안내에 `E`가 있으나 처리기에 `E` 없음. 안내가 있는 화면은 이슈 목록과 보기, 게시판 목록과 보기 4곳(`grep keymap`)뿐 | 같음(실행). `tester/proj1`에서 게시판, 마일스톤, 풀 리퀘스트를 끄자 안내 `H B I C M P Q ...`, 처리기 `N CTRL+ENTER U A H C I`. 구성원이 아닌 `third`는 안내에 `E`, 처리기에 `E` 없음. 소스 `keymap.scala.html:23-48`, `projectMenu.scala.html:131-151`. 차이 하나: v1.6은 소유자에게도 안내에 `Q`가 있으나 처리기에 `Q`가 없다(`projectMenu.scala.html:148-151`의 `loginId == "manager"` 조건, 실행으로 확인). v2는 `Q`가 동작한다(`menu.html` `settingShortcutEnabled`) | v1.6 결함 유지 | 장부에 올린다. 안내를 처리기 등록 조건과 같은 조건으로 그린다 |
| shell-F6 | 재현됨 | `hglfs/issues?state=closed&pageNum=2`에서 `.pin` 클릭: 이동 주소 `/admin/hglfs/issues`(쿼리 소실), 저장값 `shallWeOpenLeftNavigation=true`. 저장값이 켜진 채 같은 주소를 직접 열면 이동 사슬 `issues?state=closed&pageNum=2` → `/user/sidebar?path=/admin/hglfs/issues&hash=` → `/admin/hglfs/issues`, 상위 주소는 쿼리 없는 주소, 프레임 2개 | 같음(실행). `tester/proj1/issues?state=closed&pageNum=2`가 같은 사슬로 쿼리를 잃는다. 소스 `layout.scala.html:47,102` | v1.6 결함 유지 | 장부에 올린다. `layout.html:50,106`에서 `pathname + search`를 넘기고 서버가 `path`를 쿼리째 받게 한다 |
| shell-F7 | 재현됨 | 정상: 홈에서 `f` 키로 `#mySidenav` 폭 0 → 360, `Shift+F`는 안 열림, 아바타 단추 클릭으로 360. `/user/usermenuTabContentList`를 500으로 막으면 `f` 키와 단추 클릭 모두 폭 0 유지, 콘솔 `Usermenu loading failed: [object Object]`. (`ㄹ` 키는 Playwright가 키 이름을 몰라 못 눌렀고 코드 `isShortcutKeyPressed`로만 확인) | 같음(실행). 정상은 `f` 후 폭 362, `Shift+F` 안 열림. 실패 시 폭 0 유지, 같은 콘솔 로그. 소스 `yona.Usermenu.js:24`, `:210-211`(`which===102`) | v1.6 결함 유지 | 장부에 올린다. `iniNaviUserMenu()`를 `.always`나 로드 실패 경로에서도 부르고 `keypress`를 `keydown`으로 바꾼다 |
| shell-F8 | 재현됨 | 핀 없는 `hglfs/issues`에서 패널 프로젝트 `hglfs`(`data-location=/admin/hglfs/go`)를 클릭하면 새 페이지(탭) `/admin/hglfs/go`가 열리고 현재 창 주소는 그대로이다 | 같음(실행). `tester/proj1/go`가 새 탭으로 열리고 현재 창은 그대로. 소스 `yona.Usermenu.js:127-139` | v1.6 결함 유지 | 조치 없음(의도된 동작으로 본다) 또는 장부에 올린다 |
| shell-F9 | 재현됨 | 설정 `yona.github.allow-migration=true`로 `/migration`을 열고 GitHub 이동 토큰 처리만 응답 변조(토큰 두 글자 주입, 외부 접속 없음)해 화면을 유지했다. 사용자 패널 내용은 `Loading...` 그대로이고 단추를 누르면 폭 420. 구글 글꼴 3종 요청이 나간다. 이 화면의 jQuery는 3.7.1이고 `Usermenu.js` 스크립트는 없다 | 같음(실행). 폭 422, 내용 `Loading...`, 구글 글꼴 3종, `yona.Usermenu.js` 없음. jQuery는 1.9.0이다. 소스 `migrationPageLayout.scala.html`에 `Usermenu` 없음 | v1.6 결함 유지 | 장부에 올린다. 한 가지 사실 정정: 머리 주석의 "jquery 1.9.0"은 v1.6 값을 옮겨 적은 것으로 v2 실제 값(3.7.1)과 다르다 |
| shell-F10 | 재현됨 | 한국어 로케일에서 홈의 `#notification-more` 글자 `More`, `.pin` 툴팁 `Sidebar`. `/notifications`의 `#setDefaultLoginPage` 요청이 500일 때 알림창 `set Default page failed: [object Object]` | 같음(실행). 같은 `More`, `Sidebar`, 같은 알림 `set Default page failed: [object Object]`. 소스 `partial_notifications.scala.html:96`, `notifications.scala.html:128-132`, `my_list.scala.html:30-34`, `layout_framed.scala.html:70`, `yona.Usermenu.js:117`, `yona-lib.js`와 `yona.temporarySaveHandler.js`의 `Draft saved`. 소개 문구, 푸터, 도움말 본문은 하나씩 열어 보지 않고 소스 대조만 했다 | v1.6 결함 유지 | 장부에 올린다 |
| shell-F11 | 재현됨 | 모든 일반 화면에서 `query` id가 2개(패널의 `my-project-list`, `my-recent-issue-list`). `admin/hglfs/issues`와 `/admin`에서 `two-column-mode-checkbox`도 2개. 이슈 보기에서 `comments`, `timeline`, `comment-N`, `upload-N`, `comment-body-N`, `button-clear-temporary`(4), `yb-header-*`(4), `tplAttachedFile`이 중복 | 같음(실행). 같은 목록(`queryx2`, `two-column-mode-checkboxx2`, 이슈 보기의 `comment-N`, `upload-N`, `commentsx2` 등 동일). 이전 서술의 "v1.6 템플릿 미확인"이 해소됨: v1.6도 같다 | v1.6 결함 유지 | 장부에 올린다 |
| shell-F12 | 재현됨 | 설정 `application.maxFileSize`를 1024로 낮춘 상태(화면 스크립트에 `maxFileSize": "1024"`)에서 `POST /files`로 5MB 파일 업로드: 201, `size":"5242880"` | 같음(실행). 같은 설정 1024에서 5MB 업로드가 201. 소스 `scripts.scala.html:105` | v1.6 결함 유지 | 장부에 올린다(서버에서도 크기를 검사하려면 `AttachmentController.upload`에 한도 비교 추가) |
| shell-F13 | 재현됨 | 비로그인(`GET /` 뒤 CSRF 쿠키만 얻음) `POST /markdown/admin/hglfs` body `{"body":"# hi #1 **b**"}`: 200 `text/html;charset=UTF-8`, 이슈 링크 `#1.커밋 링크 확인 수정`으로 변환. 비공개 `admin/secretproj`는 200이지만 `#1`을 그대로 둔다(제목 노출 없음) | 같음(실행) 단 v1.6은 비공개 `tester/priv1`에서도 `#1.비공개 이슈`로 변환해 제목이 비로그인에 노출된다(v2는 R-16으로 막혔다). 응답 형식은 `text/plain; charset=utf-8`. 소스 `MarkdownApp.java:26-35` 검사 없음 | v1.6 결함 유지(접근 검사 없음). v2는 제목 노출은 막았다 | 장부에 올린다 |
| shell-F14 | 재현됨(일부) | `$yona.loadModule('nonexistent.Module')`: `javascripts/service/yona.nonexistent.Module.js`를 **1번** 요청해 404, 재시도 없음, `[Yobi] fail to load module` 로그 없음(스크립트 `onload`가 404에서는 불리지 않아 서술의 "3번 시도하고 실패 로그"는 틀림). `service/` 폴더가 비어 있음(`ls`), `template/yona.CodeTemplate.js`는 `/assets/javascripts/template/yona.CodeTemplate.js`로 200 서빙되고 어디서도 참조하지 않음 | 다름(실행). v1.6은 `service/yobi.<이름>.js`가 실제 있어(`/assets/javascripts/service/yobi.board.List.js` 200) 대체 경로가 살아 있었다. `yobi.CodeTemplate.js`도 v1.6에 있고 참조하는 곳이 없다. 소스 `yobi.Common.js:77-93` | 문서 문제 | 문서만 고친다(시도 횟수와 로그 서술 정정). 죽은 파일 정리는 선생님 확인 |
| shell-F15 | 재현됨 | 로그인 ID `admin`에서 `.admin-logged-in-affix`가 보이고(`관리자로 로그인 하였습니다!`) `plainuser`는 안 보인다. 코드가 ID 문자열 비교(`LayoutModelAdvice.kt:76`)임을 확인. `admin` ID를 가진 비관리자는 `admin` 계정이 이미 기본 관리자라 만들 수 없어 그 부분은 코드로만 확인 | 같음(실행). `admin`에서 띠 보임, `tester`는 안 보임. 소스 `UserApp.java:1364-1366`(`SITEADMIN_DEFAULT_LOGINID.equals(session loginId)`) | v1.6 결함 유지 | 조치 없음(v1.6 동작, 이름만 오해하기 쉬움) |
| shell-F16 | 재현됨 | 서버를 `--server.servlet.context-path=/yona`로 띄워 관찰: 홈 알림 `More` 클릭이 `http://127.0.0.1:19415/notification?from=20&limit=20`(컨텍스트 경로 없이)로 가서 404. 첨부 링크 `/files/14`가 404. 사이드바, 이슈 보기, 도움말 목차에서 `/admin` 형태 링크가 `/yona` 없이 그려진다. 그 밖에 `/assets/images/default-avatar-128.png`, `project_default_logo.png`, `ico-like-small.png`, `labels.css`도 같은 이유로 404(서술에 없는 추가 위치). 서술 정정: `partial-notifications.html:28,34`는 이미 `@{'/' + ...}`로 감싸져 있어 해당하지 않는다 | 실행 불가, 소스 대조. v1.6을 `-Dapplication.context=/yona`로 띄우면 홈 요청이 404가 되어 화면을 열 수 없었다. 소스는 모두 역방향 라우트라 컨텍스트 경로를 반영한다: `partial_notifications.scala.html:115`의 `@routes.NotificationApp.notifications(from + size, size)`, `sidebar.scala.html:27`의 `@routes.UserApp.userInfo(...)`, `attachmentFile.scala.html:13`의 `@routes.AttachmentApp.getFile(...)` | v2 신규 결함(컨텍스트 경로 배포에서만 나타난다) | 고친다. `partial-notifications.html:61`을 `[[@{/notification}]] + "?from=..."`로, `sidebar.html:11`, `child-comments.html:18`, `help/toc.html:73,76,89`, `attachment-file.html:10`의 `/...`를 `@{...}`로 감싼다. 정적 이미지 404도 함께 확인해 같은 방식으로 고친다 |
| shell-F17 | 재현됨 | 존재하지 않는 `/nonexistent-zzz`(404): 상단 링크 `프로젝트 목록`(`/projects`), `도움말`, `개발팀에게 문의하기 -> https://github.com/nforge/yobi/issues?state=open`이 고정 | 같음(실행). 같은 링크 목록. 소스 `notfound_default.scala.html:29,35`(`routes.ProjectApp.projects()` 고정, 피드백 주소 고정), 일반 상단 바는 `navbar.scala.html:16,52`(`application.feedback.url`). `site.feedbackUrl`과 `hideProjectListing`을 바꾼 사이트의 차이는 설정을 바꿔 보지 않았고 소스로만 대조(같음) | v1.6 결함 유지 | 장부에 올린다 |
| shell-F18 | 재현 안 됨 | `/sites/userList`, `?state=LOCKED`, `/userList/`, `projectList?filter=a`, `issueList?state=closed`, `mail`, `update`, `oauth-apps`, `audit?page=1`, `lfs`, `sso`에서 활성 메뉴가 항상 하나 켜진다. 이 레이아웃을 쓰는 화면에 하위 경로(상세, 새로 만들기)가 없다(`/sites/oauth-apps/new`는 404) | 같음(실행). 같은 주소들에서 동일한 활성 표시. 서술 정정: v1.6도 `call.toString().equals(requestHeader.path)`로 **정확 일치**였다(`siteMngLayout.scala.html:23-30`), 라우트 비교가 아니다 | 결함 아님 | 조치 없음. 위키 서술을 정정한다(v1.6도 정확 일치, 하위 경로 화면이 없어 영향 없음) |
| shell-F20 | 재현됨 | 패널의 즐겨찾기 별 단추의 `projectId`를 `abc`로 바꿔 클릭: 서버 400 `Wrong project id`, 페이지 오류 `Unexpected token 'W', "Wrong project id" is not valid JSON`, 경고창 안 뜸 | 같음(실행). 같은 조작에서 v1.6은 500(HTML 본문)이고 오류 `Unexpected token '<' ... is not valid JSON`, 경고창 안 뜸(서버가 던지는 모양은 다르나 결과는 같다). 소스 `yona.Usermenu.js:117,135`, `UserApi.java:62-64` | v1.6 결함 유지 | 장부에 올린다. `Usermenu.js`에서 `JSON.parse`를 try로 감싸고 실패하면 본문 글자를 그대로 보인다 |
| shell-F22 | 재현됨 | 핀을 켠 상태에서 iframe 안 화면이 이동하면 상위 주소가 `/admin/hglfs/issues`, 상위 제목이 `hglfs - 이슈 - admin/hglfs`로 바뀐다(`replaceState`와 `document.title` 호출 확인). 응답 머리글 `X-Frame-Options: SAMEORIGIN` | 같음(실행). 같은 동작으로 상위 주소와 제목이 바뀐다. 다른 점: v1.6 응답에는 `X-Frame-Options` 머리글이 없다(v2의 보안 강화). 소스 `layout.scala.html:41-42` | 결함 아님 | 조치 없음(서술이 맞는 설계 설명) |
| shell-F23 | 재현됨 | 비로그인(CSRF 쿠키만)으로 `POST /files` 멀티파트: 항목 없음 403, `authorLoginId=plainuser` 항목 포함 201(`id 149`), 같은 값을 쿼리 문자열로만 주면 403 | 같음(실행). 항목 없음 403, `authorLoginId=other` 항목 201, 쿼리만이면 403. 소스 `AttachmentApp.java:44-59,61-75` | v1.6 결함 유지(익명이 임의 사용자 이름으로 올릴 수 있는 점은 보안상 주의) | 장부에 올린다 |
| shell-F24 | 재현 안 됨(영향 없음 확인) | 이슈 보기 HTML에 `Comment.init({'sContainer' : '#comments'})`로 출력된다. 호출부 6곳이 모두 `#id` 고정 값이다. 따옴표가 든 값은 `&#39;`로 바뀌어 선택자 의미가 달라지지만 호출부에 없다 | 같음(실행). 같은 출력. 소스 `commentDeleteModal.scala.html:38`(이스케이프 없이 `@commentsContainer`) | 결함 아님 | 조치 없음 |
| shell-F25 | 재현됨 | 이슈 목록(닫힌 이슈 상태, `#check-all` 있음)에서 입력란 밖 Control+A: `#check-all` 체크됨(1), 입력란 안 Control+A: 체크 안 됨(0, `bFormInput` 검사). Control+Enter는 입력란 밖에서 이동 0회, 입력란 안(검색창)에서 폼 제출로 `search?...keyword=zz`로 이동 | 같음(실행). 같은 동작(`CTRL+A` 입력란 밖 1, 안 0, `CTRL+ENTER` 입력란 안만 제출). 소스 `yobi.issue.MassUpdate.js:100-106`, `scripts.scala.html` | v1.6 결함 유지 | 장부에 올린다 |
| shell-F26 | 재현됨(일부) | 이슈 본문에 `[mail](mailto:a@b.c) [tel](tel:+8210000) [js](javascript:alert(1)) [ext](https://example.com/x) [rel](/foo) [same](동일호스트/admin) [bare](example.org/y) [anchor](#top)`를 올려 보면 `mailto:`와 `https://...`와 `example.org/y`에 `target=_blank`, `/foo`, 동일 출처 절대 주소, `#top`은 없음. `tel:`과 `javascript:` 링크는 마크다운 처리가 `#`으로 바꿔 버려(`target` 없음) 이 규칙에 닿지 않는다. 따라서 실제 영향은 `mailto:` | 같음(실행). 같은 링크 8개 결과가 완전히 같다(`mailto:` `_blank`, `tel`과 `js`는 `#`, 동일 출처는 없음). 소스 `scripts.scala.html:158-167` 같은 코드 | v1.6 결함 유지 | 장부에 올린다(`mailto:`, `tel:`을 제외하려면 `^[^./#]` 대신 `^https?:`와 상대 주소 판정으로 바꾼다) |
| shell-F27 | 재현됨 | 이슈 목록에서 `$yona.alert('a<br><img src=x onerror="window.__a=1"><b id=ab>...')`와 `$yona.notify(...)`: 두 호출 모두 `onerror`가 실행되어 `window.__a`, `window.__n`이 1, `<b>`, `<i>` 요소가 DOM에 생김. `$yona.escapeHtml` 존재 | 같음(실행). `$yobi.alert/notify`가 같은 결과(둘 다 1, 요소 생성). 다른 점: v1.6에는 `escapeHtml`이 없다. 소스 `yobi.Dialog.js`, `yobi.Toast.js` | v1.6 결함 유지 | 장부에 올린다(호출부가 사용자 값을 넘기는 곳은 `escapeHtml`을 쓰는지 점검) |

## 범위 점검

| 목록 번호 | 결과 파일에 있음 | 판정 |
|---|---|---|
| shell-F23 | 있음 | v1.6 결함 유지 |
| shell-F24 | 있음 | 결함 아님 |
| shell-F25 | 있음 | v1.6 결함 유지 |
| shell-F26 | 있음 | v1.6 결함 유지 |
| shell-F27 | 있음 | v1.6 결함 유지 |
| shell-F4 | 있음 | v1.6 결함 유지 |
| shell-F5 | 있음 | v1.6 결함 유지 |
| shell-F6 | 있음 | v1.6 결함 유지 |
| shell-F7 | 있음 | v1.6 결함 유지 |
| shell-F8 | 있음 | v1.6 결함 유지 |
| shell-F9 | 있음 | v1.6 결함 유지 |
| shell-F10 | 있음 | v1.6 결함 유지 |
| shell-F11 | 있음 | v1.6 결함 유지 |
| shell-F12 | 있음 | v1.6 결함 유지 |
| shell-F13 | 있음 | v1.6 결함 유지 |
| shell-F14 | 있음 | 문서 문제 |
| shell-F15 | 있음 | v1.6 결함 유지 |
| shell-F16 | 있음 | v2 신규 결함 |
| shell-F17 | 있음 | v1.6 결함 유지 |
| shell-F18 | 있음 | 결함 아님 |
| shell-F20 | 있음 | v1.6 결함 유지 |
| shell-F22 | 있음 | 결함 아님 |

## 덧붙이는 관찰

- `GET /user/sidebar`를 `path` 없이 부르면 v2와 v1.6 모두 500이다(두 서버 같음, 이번 항목 밖).
- v2를 컨텍스트 경로 `/yona`로 띄우면 shell-F16 외에도 `/yona/help/toc`가 403, `/yona/user/sidebar?path=...`가 500으로 나왔다. 컨텍스트 경로 배포 지원 범위(R-104)를 별도로 점검할 필요가 있다.
- 프로젝트 `Q` 단축키는 v1.6에서 사실상 죽어 있었고(`projectMenu.scala.html:148-151`) v2에서 동작하게 되었다(v2 쪽 추가 동작, 결함 아님).
