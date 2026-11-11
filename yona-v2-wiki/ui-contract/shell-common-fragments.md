---
title: 화면 공통부 계약 - 공통 조각(common/)과 사용처 표
type: ui-contract
sources:
  - src/main/resources/templates/common/*.html
  - src/main/resources/templates/help/markdown.html
  - src/main/resources/static/assets/javascripts/esm/Attachments.js
  - src/main/resources/static/assets/javascripts/esm/Files.js
  - src/main/resources/static/assets/javascripts/esm/Comment.js
  - src/main/resources/static/assets/javascripts/esm/CommentForm.js
  - src/main/resources/static/assets/javascripts/esm/SubComment.js
  - src/main/resources/static/assets/javascripts/esm/Markdown.js
  - src/main/resources/static/assets/javascripts/esm/LoginDialog.js
  - src/main/kotlin/dev/yona/web/AttachmentController.kt
  - src/main/kotlin/dev/yona/web/MarkdownController.kt
updated: 2026-11-11
---

# 공통 조각 계약 (`common/`)

[`shell.md`](shell.md)에서 다루지 않은 `common/` 조각과 **다른 영역의 사용처**를 정리한다. 상단 바, 사용자 메뉴, 사이드바, 푸터, `scripts`, `importmap`은 `shell.md`와 [`shell-scripts.md`](shell-scripts.md)에 있다. 발견 사항 번호(`shell-F#`)는 `shell.md` 17절의 번호이다.

대부분의 조각은 **표지 마크업만 그리고 동작은 쓰는 화면의 스크립트가 붙인다.** 업로더는 `yona.Files.getUploader(...)` + `new yona.Attachments(...)`를 각 화면 모듈(`issue.Write.js:96-108`, `board.Write.js:95`, `milestone.Write.js:119`, `git.Write.js:278`, `issue.View.js:287` 등)이 부른다.

## 1. 조각별 계약

### 1.1 `common/editor` (마크다운 편집기)

`editor(editorName, textValue, additionalAttr, editorMode, viaEmail)`(`editor.html:15`).

| 인자 | 의미 |
|---|---|
| `editorName` | `이름-식별자`면 앞이 textarea `name`, 뒤가 영역 id 접미사. 식별자가 없으면 무작위 영문 8자(`TemplateHelper.randomAlphabetic`). 예: `contents-123`(수정 댓글) |
| `textValue` | 처음 내용(textarea 본문) |
| `additionalAttr` | `tabindex=N`, `style=...`, `data-is-user-has-typed=true` 셋 중 하나를 접두어로 읽는다(그 밖은 무시) |
| `editorMode` | `data-editor-mode`와 미리보기 영역 클래스. 값: `content-body`, `comment-body`, `update-comment-body`, `code-review-body` |
| `viaEmail` | 미리보기 영역 `data-via-email` |

구성(`editor.html:19-74`): `div[data-toggle=markdown-editor]` > 탭 `ul.nav-tabs`(편집 `#edit-<id>`, 미리보기 `#preview-<id>`, `.add-task-list-button`, `#button-clear-temporary`, `.editor-notice-label`) > `.tab-content` 안에 마크다운 도움말(`help/markdown`), 편집 패널(`markdown-toolbar[for=editor-<name>-<id>]`의 `md-header, md-bold, md-italic, md-quote, md-code, md-link, md-unordered-list, md-ordered-list, md-mention, md-ref`와 `textarea#editor-<name>-<id>.editorSeries.content.comment.form-control[markdown=true]`), 미리보기 패널(`.markdown-preview.markdown-wrap`), `.notification-receiver`(`.notification-receiver-list`).

| 요소 | 동작 | 요청 | 근거 |
|---|---|---|---|
| 미리보기 탭 클릭 | `Markdown.js`가 textarea 값을 서버로 보내 HTML을 받아 미리보기에 넣고 `min-height`를 textarea 높이로 맞춘다. `sMarkdownRendererUrl`이 없으면(`common/markdown`에 프로젝트가 null) **요청하지 않아 미리보기가 빈 채** 남는다 | `POST /markdown/{owner}/{project}` JSON `{body, breaks}` | `Markdown.js:74-100,132-147` |
| `Tab`, `Shift+Tab`(textarea) | 4칸 들여쓰기와 내어쓰기(`KeyControl.js`), 브라우저 되돌리기 기록에 한 번으로 남는다 | 없음 | `Markdown.js:148-155`, `KeyControl.js` |
| `.add-task-list-button` | 커서 위치에 `- [ ] Todo A/B/C` 세 줄 삽입(전역 `scripts.html:170-189`) | 없음 | |
| `#button-clear-temporary` | 임시 저장 초안 지우기(화면 모듈이 처리, 이 조각은 단추만) | 없음 | 확인: 이 담당 파일에는 처리기가 없다(화면 스크립트) |
| `markdown-toolbar` 단추 | GitHub `markdown-toolbar-element`가 선택 범위에 문법을 넣는다 | 없음 | `scripts.html:57`, 시험 `EditorToolbarTest.kt` |
| `@`, `#`, `:` 입력 | `yona.Mention`이 `text-expander`로 감싸 후보를 연다(`@` 사용자, `#` 이슈, `:` 이모지) | `GET /{owner}/{project}/mentionList?query=&mentionType=user\|issue`(`@IsAllowed(READ)`, `MentionListController.kt:43-70`) | `Mention.js` |
| 입력 5초 뒤 | 초안을 `localStorage`에 저장하고 `.editor-notice-label`에 `Draft saved`(영문 고정) | 없음 | `temporarySaveHandler.js` |

### 1.2 `common/file-uploader`, `common/upload-form`

`fileUploader(resourceType, resourceId)`는 `uploadForm(type, id, 'upload')` + 템플릿 둘(`script#tplAttachedFile`(mustache `li.attached-file`), `script#tplDropFilesHere`)이다(`file-uploader.html:9-27`). `uploadForm(resourceType, resourceId, formId)`는 `div.upload-wrap.content-footer[data-resource-type][data-resource-id][id=formId]`(`upload-form.html:9-27`)에 드롭 안내, `input[type=file][name=filePath][multiple]`, `ul.attached-files`, 안내문(`common.attach.attachIfYouSave`)이 있다.

| 요소 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|
| 파일 선택, 끌어놓기, 붙여넣기(textarea) | 파일마다 임시 항목(`id=<submitId>`, 투명도 0.2)을 목록에 넣고 진행률을 그린다. 붙여넣은 `image.png`는 `<submitId>.png`로 이름을 바꾼다. textarea에 `<!--_<id>_-->` 임시 표지를 넣었다가 성공 때 `[이름](/files/<id>)`나 `![이름](...)`로 바꾼다 | `POST /files`(multipart `filePath`, 헤더 `X-Requested-With`, CSRF) | 201 새로 저장, 200 같은 내용이 이미 있음. JSON `{id, mimeType, name, url, size}`(Accept가 JSON이 아니면 `text/html`). 파일 없음 400 | **로그인 필요**: 올린 사람을 못 정하면 403(본문 없음) | `Files.js:100-160`, `AttachmentController.kt:110-137` |
| 크기 초과 | 요청하지 않고 `error.toolargefile`을 알림 | 없음 | | 한도는 화면만(F12) | `Files.js:102-107` |
| 업로드 성공 | 숨은 `input[name=temporaryUploadFiles]`(업로더 컨테이너 안에 prepend)에 id를 쉼표로 이어 넣는다. 폼 제출 때 서버가 이 임시 파일을 글에 붙인다 | 없음 | | | `Attachments.js:56-63,280-300` |
| 목록 항목 클릭 | `.btn-delete`면 삭제, 아니면 링크를 textarea 커서 위치에 삽입. 동영상 MIME(`video/mp4`, `ogg`, `webm`)이면 `<video class="video-js">` 조각을 넣는다 | | | | `Attachments.js:362-374` |
| `.btn-delete`(×) | 삭제 후 항목과 textarea의 링크, 임시 id를 지운다. 실패하면 `common.attach.error.delete` 알림 | `POST /files/{id}` multipart `_method=delete` | 200 평문. 멀티파트인데 `_method`가 `delete`가 아니면 400, 없는 파일 404 | `Operation.DELETE` 권한 없으면 **403**(본문 없음) | `Attachments.js:376-397`, `Files.js:224-236`, `AttachmentController.kt:167-185` |
| 수정 화면의 기존 첨부 | `resourceType`과 `resourceId`가 있으면 목록 조회 | `GET /files?containerType=&containerId=` | `{attachments:[...], tempFiles:[...]}` JSON. 권한 없으면 403 | 읽기 권한 | `Files.js:246-258`, `AttachmentController.kt:153-165` |

`resourceType` 값: `ISSUE_POST`, `BOARD_POST`, `MILESTONE`, `PULL_REQUEST`, `COMMIT_COMMENT`, `REVIEW_COMMENT`, `ISSUE_COMMENT`, `NONISSUE_COMMENT`(2절 표). 동시에 한 컨테이너나 textarea에는 업로더가 하나만 붙는다(`isYobiUploader`).

### 1.3 `common/comment-form`

`commentForm(canCreate, resourceType, action)`(`comment-form.html:9-41`).

- `canCreate`가 참: `form#comment-form[method=post][enctype=multipart/form-data]`: `editor('contents', '', '', 'comment-body', false)`, 로그인했으면 `fileUploader(resourceType, null)`, 단추 `#dynamic-comment-btn.hidden`(이슈 댓글 화면이 "댓글과 닫기" 같은 글자를 채움, 화면 스크립트)과 `button[type=submit]`("댓글 쓰기", `button.comment.new`). `esm/CommentForm.js`를 모듈로 불러온다.
- 거짓: 비활성 `textarea`와 회색 단추 상자 `div.write-comment-box[data-login=required][title=error.auth.unauthorized.comment]`. 익명이면 클릭에 로그인 대화상자가 열린다(`LoginDialog`는 익명에게만 로드). 로그인했지만 권한이 없는 사용자는 클릭해도 아무 일도 없다.

`CommentForm.js` 동작: 제출 때 임시 저장 초안을 지우고, 내용이 공백뿐이면 `post.comment.empty` 알림(3초)과 포커스, 이미 제출 중이면 무시, 아니면 `NProgress.start()` 후 300ms 뒤 `form.submit()`. 제출 중 `Esc`는 진행 막대를 걷고 다시 제출할 수 있게 한다. 입력이 있고 제출 중이 아니면 `beforeunload`가 `common.comment.beforeunload.confirm`을 돌려준다(이 처리기가 `ShortcutKey`의 `beforeunload`와 겹쳐 단축키를 죽인다, F3). `CTRL+ENTER`는 전역 처리(`shell-scripts.md` 5절)가 `$(form).submit()`으로 제출한다(이 경로는 위 300ms 지연과 `onsubmit` 표지를 거치지 않고 jQuery `submit` 처리기를 탄다). 서버 요청은 `action`(이슈 `/issue/N/comments`, 게시글 `/post/N/comment`, 커밋 `commentAction` 등)으로의 POST이다. 근거 `CommentForm.js:35-110`.

### 1.4 `common/comment-update-form`, `common/attachment-file`

`commentUpdateForm(comment, action, resourceType, isAllowedUpdate)`: `div#comment-editform-<id>.comment-update-form > form[action=<action>/<id>][POST multipart]`(`comment-update-form.html:9-47`). 숨은 `id`, `editor('contents-<id>', 본문, '', 'update-comment-body', false)`, 드롭 안내, `label[for=upload-<id>]` + `input#upload-<id>[name=filePath][multiple]`, 메일 보내기 체크박스(`comment.own`일 때만, `name=notificationMail value=yes checked`, 호버 팝오버), 취소 단추(`button.ybtn-cancel[data-comment-id]`), 저장 단추(`isAllowedUpdate`일 때만), 숨은 `temporaryUploadFiles`, 기존 첨부 `.attachment-files`(`attachment-file` 조각 반복), 중복 id `div#upload-<id>[data-resourceType][data-resourceId]`(F11).

`attachmentFile(file)`(`attachment-file.html:9-16`): `div.attached-file.attached-file-marker[data-name][data-href=/files/<id>][data-mime]`, 크기는 `helper.binaryPrefix`, `button.btn-delete[data-id]`(×).

| 요소 | 동작 | 요청 | 근거 |
|---|---|---|---|
| 취소 `.ybtn-cancel`, `[data-toggle=comment-edit]` | `Comment.js`가 수정 폼과 본문(`#comment-body-<id>`)을 토글하고 `.add-a-comment`를 숨기고 `autosize.update` | 없음 | `Comment.js:42-50,58-66` |
| 기존 첨부 × | `CommentAttachmentsUpdate.js`가 링크 문자열을 textarea에서 지우고 `temporaryUploadFiles`에서 id를 빼고 삭제 요청 후 항목 제거. 실패는 콘솔 로그만 | `POST /files/{id}`(바디 없음, `_method` 없음. 멀티파트가 아니라 서버가 허용) | `CommentAttachmentsUpdate.js` |
| `.file-upload__input` 변경 | 선택한 파일을 올려 커서 위치에 링크 삽입 | `POST /files` | 같은 파일 |
| 저장 | 일반 폼 제출 | `POST {action}/{id}` | 권한은 담당 화면(이슈, 게시글) 컨트롤러. 이 담당에서는 확인하지 않음 |

### 1.5 `common/child-comments`, `child-comment-form`, `comment-delete-modal`

`childComments(comment, baseUrl, number, commentsPath)`(`child-comments.html:10-29`): `.add-a-comment.float-end`("한 줄 댓글" 자리표시 문구), `.child-comments > .one-line-comment`(`comment.children` 반복: 본문 HTML `th:utext`, 숨은 `.subcomment-author`(작성자 링크 `'/' + authorLoginId`, 시각 링크 `#comment-<id>`, `child.canDelete`이면 `a[data-toggle=comment-delete][data-request-uri=<base>/<경로>/<번호>/comment/<id>/delete]`)), `comment.canReply`이면 `.child-comment-input-form > childCommentForm(action, parentCommentId)`. 등록 주소: 이슈 `…/issue/N/comments`, 게시글 `…/post/N/comment`(단수).

`childCommentForm`(`child-comment-form.html:9-21`): `form[POST multipart]` 숨은 `parentCommentId`, `textarea.editorSeries[name=contents][markdown=true][rows=1]`(자리표시 끝에 `(CTRL|⌘ + ENTER)`), `button[type=submit]` 글자 `OK`(영문 고정), `.notification-receiver`.

`commentDeleteModal(commentsContainer)`(`comment-delete-modal.html:9-29`): `div#comment-delete-modal.modal.fade`(제목 `common.comment.delete`, 확인 문구, `#comment-delete-confirm` "예"(`button.yes`), "아니오", 닫기 ×)와 `esm/Comment.js` 모듈, 인라인 `yona.Comment.init({sContainer: <escapeXml(commentsContainer)>})`. 이 인라인 `$(function(){ yona.Comment.init })`은 모듈 스크립트가 먼저 평가된 뒤 도는 순서에 기댄다.

| 요소 | 동작 | 요청 | 근거 |
|---|---|---|---|
| `[data-toggle=comment-delete]` 클릭(컨테이너 위임) | 주소(`data-request-uri`)를 기억하고 모달을 연다 | 없음 | `Comment.js:52-56` |
| "예" `#comment-delete-confirm` | 기억한 주소로 삭제 | `DELETE <주소>`(`yona.Request.send('delete', url)`) | `Comment.js:36-41` |
| 삭제 성공 | 2xx면 화면 새로 고침(204+`Location`이면 그곳) | 응답 상태만 본다 | `Request.js` |
| 삭제 실패 | **화면에 아무 알림 없음**(v1.6 그대로) | | `Request.js` |
| `.add-a-comment` 클릭 | `.child-comment-input-form` 토글, textarea에 초점. `CTRL|⌘+ENTER`(keydown)는 폼 제출, `Esc`(keyup)는 모든 한 줄 입력 폼을 숨기고 `.add-a-comment`를 보인다 | 제출 때 `POST` 위 주소 | `SubComment.js` |
| `.comment` 마우스 오버/아웃 | 입력 중이 아니면 `.add-a-comment`, `.new-issue-by`, `.share-link`를 300ms 페이드로 보이고 숨긴다 | 없음 | `SubComment.js` |
| `.subcomment-author` | 로드 때 마지막 `<p>` 끝(없으면 `.contents` 끝)으로 옮겨 붙이고 원래 자리는 지운다 | 없음 | `SubComment.js` |

서버 권한(삭제, 대댓글 작성)은 담당 화면 컨트롤러이고 이 담당에서는 확인하지 않았다(`canDelete`, `canReply`는 서버가 계산해 넘긴 값).

### 1.6 `common/analytics` (`gtag(layout)`)

`yona.send-yona-usage`와 `yona.analytics.measurement-id`가 모두 있을 때만 렌더링(`AnalyticsSettings.kt` `clientEnabled`, `analytics.html:7`). 설치된 모든 레이아웃이 아니라 `layout`(`'normal'`)과 `layout-framed`(`'left_sidebar'`)만 포함한다. `www.googletagmanager.com/gtag/js`를 불러오고 `send_page_view:false`, 광고 신호 끔. **실제 주소, 제목, 리퍼러는 보내지 않고** `maskPath`로 "화면 종류"(`/:owner/:project/issue` 등, 예약된 첫 구간 목록은 `analytics.html:11-14`)만 `page_location`(`https://yona.local<마스킹>`)으로 넣는다. 이벤트: `page_view`(`layout`, `in_frame`, `two_column`, 2단 보기 토글이 있는 화면이면 `screen`), 핀 최상위 창이면 `feature_use{left_sidebar_open}`, `#two-column-mode` 체크 변경 시 `two_column_on|off`, 2단 보기가 켜진 채 `.title`/`.twoColumeModeTarget` 클릭 시 `two_column_open`. 시험 `AnalyticsTagTest.kt`, `AnalyticsTagOffTest.kt`. 마이그레이션 레이아웃에는 없다.

### 1.7 `common/login-dialog`

익명일 때만 `common/scripts`가 포함한다(`scripts.html:37-39`). `div#loginDialog.modal`의 `form.login-form-wrap[action=/users/login][data-two-factor-action=/users/login/2fa]`:

- `.two-factor-step`(처음 숨김): 코드 입력 `name=code`(`autocomplete=one-time-code`), `rememberDevice` 체크, 제출, `.security-key-step`의 `.security-key-use`(`data-options-uri=/users/login/2fa/webauthn/options`, `data-submit-uri=/users/login/2fa/webauthn`).
- `.login-step`: `loginIdOrEmail`, `password`(SSO 전용 모드(`ssoLogin.only`)면 둘 다 없음), 오류 `.error > .error-message`, 로그인 단추, SSO 단추들(`a.sso-login-btn[href=<provider.path>?returnTo=<requestUri>]`, 전용 모드면 `a.sso-local-link`("관리자 로그인", `/users/loginform?local=true`)), `rememberMe`(기본 체크), 링크 "비밀번호 재설정"(`/lostPassword`)과 "멤버 가입"(`/users/signupform`).
- 로드: jQuery UI 1.14.2(흔들기 효과), `esm/webauthn.js`, `esm/LoginDialog.js`.

| 사건 | 동작 | 요청 | 응답 처리 | 근거 |
|---|---|---|---|---|
| `[data-login=required]` 클릭(문서 위임) | 모달을 열고 입력을 비우고 첫 단계로 돌리고 아이디에 초점. **경로가 `/users/…`이면 처리기를 달지 않는다**(`LoginDialog.js:35-36`) | 없음 | | `LoginDialog.js:34,62-80` |
| 제출(비밀번호 단계) | ajax JSON | `POST /users/login` `{loginIdOrEmail, password, rememberMe}` | `200`이고 `{twoFactor:true, securityKey}`면 코드 단계, 아니면 화면 새로 고침. 실패는 아래 | `LoginDialog.js:132-170`, `PasswordLoginHandlers.kt:72-110` |
| 제출(코드 단계) | | `POST /users/login/2fa` `{code, rememberDevice}` | 성공이면 새로 고침 | `TwoFactorLoginController.kt:57` |
| 보안 키 | `options`를 받아 `navigator.credentials.get`, 결과를 제출 | `POST /users/login/2fa/webauthn/options`, `POST /users/login/2fa/webauthn` | 성공 새로 고침, 실패 `Messages(message \|\| 'user.twoFactor.invalid')` | `LoginDialog.js:92-114` |
| 실패 | 서버 JSON `message`(`user.login.invalid`, `user.locked`, `user.deleted`, `sso.error.ssoOnly`)를 `Messages()`로 번역해 오류칸에 보이고 대화상자를 흔든다. 본문이 JSON이 아니면 상태별 문구(4xx `user.login.failed.client`, 5xx `.server`, 그 밖 `user.login.failed`) | | 네트워크 문구 분기는 닿지 않는다(F2) | `LoginDialog.js:132-185` |

서버 상태: 자격 오류 403 `user.login.invalid`, 잠김 403 `user.locked`, 삭제 404 `user.deleted`, SSO 전용 403 `sso.error.ssoOnly`, 입력 누락 400 `user.login.required`(`PasswordLoginHandlers.kt:129-160`, `LoginController.kt:31-40`). 2단계 인증을 켠 사용자의 성공 응답 `200 {"twoFactor":true,"securityKey":bool}`, 아니면 `text/plain` `{}`. 로그인 처리 자체의 계약은 사용자 영역 문서를 본다.

### 1.8 `common/markdown`과 `help/markdown`

`markdown(project)`(`common/markdown.html:6-28`): 하이라이트 CSS와 `highlight.pack.js`(11.12.0), `marked.js`(4.3.0), `marked-yona.js`를 불러오고 문서 준비 때 `hljs.configure({languages: []})`, `yona.Markdown.init({sMarkdownRendererUrl: '/markdown/<owner>/<name>'})`(프로젝트가 있을 때만), `hljs.highlightAll()`. 모든 마크다운을 보이는 화면과 편집기 화면이 포함한다(2절). `yona.Markdown.init`은 `[markdown]` 속성이 있는 요소를 훑어 textarea는 편집기로, 나머지는 보기(`marked` + `$yona.xssClean`)로 만든다(`Markdown.js:60-70,112-117,160-170`). `help/markdown`은 `shell.md` 10.3절.

### 1.9 `common/select2`, `common/calendar`

`select2`: `lib/select2/select2.js`(4.1.0), `esm/ui/Select2.js`, 언어 파일(ko, ja, ru만, 그 밖은 영어)과 기본 언어 지정, mustache 템플릿 5개(`tplSelect2FormatUser`, `…Milestone`, `…Projects`, `…ProjectsWithoutAvatar`, `…FormatIssues`)(`select2.html:5-47`). `data-toggle="select2"` 선택 상자를 `data-format`(projects, issues, user, milestone, issuelabel, branch)에 따라 꾸민다(`Select2.js` 머리 주석). `calendar`: vanilla-calendar-pro 3.4.0과 `esm/ui/Calendar.js`(`data-toggle=calendar` 입력란 자동 초기화, 값 `YYYY-MM-DD`)(`calendar.html:9-12`). 마감일 검증은 `esm/dates.js`.

### 1.10 작은 조각

| 조각 | 마크업과 동작 | 근거 |
|---|---|---|
| `comment-count(linkUrl, countNumber, showColorAlways)` | `a.comments-count[href=linkUrl]`(`showColorAlways`면 `comments-count-color`) 안 아이콘 `yobicon-comment2`와 개수 | `comment-count.html:9-14` |
| `vote-count(linkUrl, ...)` | `a.vote-count`(`vote-color`) 아이콘 `yobicon-hearts` | `vote-count.html:9-14` |
| `sharer-count(countNumber, ...)` | `href` 없는 `a`, 툴팁 `issue.sharer`, 아이콘 `yobicon-friends` | `sharer-count.html:9-14` |
| `tasklist-bar` | `.tasklist > .task-title`("Tasks"+`.done-counter`) + `.task-progress > .bar.red`. 채우기는 `Tasklist.js`(`[ ]`, `[x]` 개수, 체크 상자 클릭 시 PATCH `{content, original}`을 폼 `action`으로) | `tasklist-bar.html`, `Tasklist.js` |
| `partial-history(historyHtml)` | `div#-yona-posting-history.modal`(제목 `change.history`, 본문 `th:utext`, 확인). 열기는 쓰는 화면의 단추 | `partial-history.html:9-22` |
| `review-form(resourceType, action, pendingCount)` | `div#review-form.review-form > form[POST multipart]`: 작성자 아바타(`hide-in-mobile`), 닫기 `button[data-toggle=close]`, `editor('contents','','','code-review-body')`, 로그인했으면 `uploadForm(type, null, null)`, 오른쪽에 `pendingCount != null`이면 `name=pending value=true` 단추(`pendingCount>0`이면 `pullRequest.review.pending.add`, 아니면 `.start`)와 제출(`pending.single`, 없으면 `button.comment.new`) | `review-form.html:10-39` |
| `my-series-menu-tab(viewType)` | `ul.nav.nav-tabs`: 알림, 내 이슈, 내 파일(활성은 `viewType` 일치). 알림과 내 이슈일 때만 `#setDefaultLoginPage`(`shell.md` 8.2절) | `my-series-menu-tab.html:9-18` |
| `show-subtasks-checkbox` | `div#two-column-mode-checkbox.show-subtasks`(호버 팝오버) 안 `input#toggle-show-subtasks`. `showSubtask.js`가 `localStorage.showSubtasksAlways`를 읽고 쓰고 `.child-issue-list`를 보이고 숨긴다 | `show-subtasks-checkbox.html`, `showSubtask.js` |
| `two-column-mode-checkbox-area` | `div#two-column-mode-checkbox.two-column-icon.hide-in-mobile` 안 `input#two-column-mode`. `twoColumnMode.js`가 `localStorage.useTwoColumnMode`로 기억하고, 켜지면 `.title, .twoColumeModeTarget` 클릭을 가로채 **오른쪽 오프캔버스 `#pageslide` 안 iframe**에 그 주소를 연다(같은 링크 다시 클릭으로 닫힘, `Esc`나 바깥 클릭으로는 안 닫힘) | `two-column-mode-checkbox-area.html`, `twoColumnMode.js:10-90` |
| `notification-mail` | 웹 화면이 아니라 알림 메일 본문 틀. `NotificationMailSender.kt:172`가 `templates.process`로 렌더링. `@{}`와 `#request` 금지 | `notification-mail.html:8-17` |

## 2. 사용처 표 (다른 영역이 어떻게 쓰는가)

조각 호출 인자 값은 해당 줄에서 가져왔다. 한 조각을 쓰는 영역 이름은 템플릿 디렉터리(board 게시판, code 코드, issue 이슈, milestone 마일스톤, pullrequest 코드 주고받기, project 프로젝트, wiki 위키, organization 그룹, user 사용자, site 사이트 관리, search 검색, reviewthread 리뷰)이다.

### 2.1 편집 관련

| 조각 | 사용처(파일:줄)와 인자 |
|---|---|
| `editor` | `issue/create.html:41`(`'body', 본문, 'tabindex=2', 'content-body'`), `issue/edit.html:49`, `board/create.html:37`(`tabindex=3`), `board/edit.html:29`, `milestone/create.html:36`, `milestone/edit.html:36`(`'contents'`), `wiki/edit.html:23`(`'content'`), `pullrequest/create.html:63`(`''`), `pullrequest/edit.html:65`(`data-is-user-has-typed=true`), `code/partial-comment-thread.html:106`(`style=height:100px, code-review-body`), 그리고 `comment-form`, `comment-update-form`, `review-form` 안 |
| `file-uploader` | `issue/create:46`(`ISSUE_POST, null`), `issue/edit:54`(`ISSUE_POST, issueId`), `board/create:43`(`BOARD_POST`), `board/edit:35`, `milestone/create:41`, `milestone/edit:41`(`MILESTONE`), `pullrequest/create:65`, `pullrequest/edit:67`(`PULL_REQUEST`), `comment-form` 안 |
| `upload-form` | `file-uploader` 안(id `upload`), `review-form` 안, `code/partial-comment-thread.html:108`(`f.resourceType, null, null`) |
| `comment-form` | `issue/view.html:152`(`issue.canVoteOrComment, 'ISSUE_COMMENT', /issue/N/comments`), `board/view.html:93`(`post.canComment, 'NONISSUE_COMMENT', /post/N/comment`), `code/diff.html:64`, `code/svn-diff.html:82`(`'COMMIT_COMMENT', commentAction`), `pullrequest/changes.html:102`(`commentable, 'REVIEW_COMMENT'`) |
| `comment-update-form` | `issue/partial-comment.html:71`(`'ISSUE_COMMENT', c.canUpdate`), `board/partial-comments.html:44`(`'NONISSUE_COMMENT'`) |
| `child-comments` | `issue/partial-comment.html`, `board/partial-comments.html` |
| `comment-delete-modal` | `issue/view:287`(`'#comments'`), `board/view:154`(`'#comments'`), `pullrequest/view:162`(`'#comments'`), `pullrequest/changes:152`(`'#changes'`), `code/diff:111`, `code/svn-diff:98`(`'#code-browse-wrap'`) |
| `review-form` | `code/diff.html:68`(`'COMMIT_COMMENT', commentAction, null`), `pullrequest/changes.html:106`(`'REVIEW_COMMENT', commentAction, pendingCount`) |
| `markdown` | `project/home:144`, `project/partial-readme:25`, `issue/create:95`, `issue/edit:131`, `issue/view:286`, `board/create:75`, `board/edit:66`, `board/view:153`, `code/diff:110`, `code/svn-diff:97`, `code/view:142`, `milestone/create:77`, `milestone/edit:77`, `milestone/view:91`, `pullrequest/create:66`, `pullrequest/edit:68`, `pullrequest/changes:151`, `pullrequest/view:161`, `wiki/edit:79`, `wiki/view:32`(인자는 프로젝트) |
| `tasklist-bar` | `issue/view:75`, `issue/partial-comment:74`, `board/view:61`, `board/partial-comments:47` |
| `partial-history` | `issue/view:63`(`issue.historyHtml`), `board/view:49`(`post.historyHtml`) |
| `attachment-file` | `comment-update-form` 안 |

### 2.2 선택, 달력, 개수, 탭

| 조각 | 사용처 |
|---|---|
| `select2` | `board/list:95`, `board/view:250`, `code/diff:112`, `code/history:99`, `code/view:151`, `issue/create:96`, `issue/edit:132`, `issue/list:21`, `issue/view:288`, `organization/board-list:94`, `organization/issue-list:157`, `project/create:185`, `project/importing:214`, `project/issue-labels:123`, `project/setting:182`, `pullrequest/create:105`, `pullrequest/edit:95`, `pullrequest/list:22`, `pullrequest/view:184,189`(조건 두 곳에서 두 번 포함될 수 있다) |
| `calendar` | `issue/create:97`, `issue/edit:133`, `issue/list:22`, `issue/view:289` |
| `comment-count` | `board/partial-list`, `issue/partial-list`, `issue/my-partial-list`, `issue/partial-subtasks`, `organization/board-list`, `reviewthread/list`, `user/partial-issues` |
| `vote-count` | `issue/partial-list`, `issue/my-partial-list`, `issue/partial-subtasks`, `user/partial-issues` |
| `sharer-count` | `issue/partial-list` |
| `my-series-menu-tab` | `index/notifications:53`(`menuTab`=`TAB_NOTI`), `issue/my-list:15`(`TAB_MY_ISSUES`), `user/files:16`(`TAB_MY_FILES`) |
| `show-subtasks-checkbox` | `issue/partial-list-wrap:31`, `issue/my-partial-search:81`, `user/view:84` |
| `two-column-mode-checkbox-area` | `board/list`, `issue/partial-list-wrap:30`, `issue/my-partial-search:80`, `organization/board-list`, `pullrequest/partial-search`, `user/view:66` |
| `help/keymap` | `issue/partial-list-wrap:44,72`(`issueList`), `issue/view:261`(`issueDetail`), `board/list:63`(`boardList`), `board/view:128`(`boardDetail`) |
| `calendar`/`select2`가 없는 화면 | 위 목록 밖의 화면은 `data-toggle=select2`나 `calendar`를 써도 동작하지 않는다(확인: 조각을 불러야 스크립트가 로드됨) |

### 2.3 레이아웃 사용처 요약

| 레이아웃 | 쓰는 영역(파일 수) |
|---|---|
| `project-layout` | project 16, code 11, pullrequest 7, wiki 5, board 4, issue 4, milestone 4, error 3, reviewthread 1, search 1 |
| `organization-layout` | organization 4, error 1, search 1 |
| `site-layout` | user 28, organization 10, error 4, project 3, issue 1, site 1, oauth2 1, help 1, index 1, search 1 |
| `site-mng-layout` | site 14 |
| `layout/layout` 직접 | `error/notfound-default` 1 |
| `migration-layout` | migration 1 |
| `layout-framed` | `UserMenuController.sidebar` |
| 레이아웃 없음 | `welcome/secret`, `welcome/restart` |

## 3. 관련 발견 사항

`shell.md` 17절의 F2(로그인 대화상자 네트워크 오류), F3(`beforeunload`와 단축키), F11(중복 id), F12(업로드 한도), F13(`/markdown` 접근 검사 없음), F16(컨텍스트 경로 없는 절대 경로, `attachment-file.html:10` 포함)이 이 파일의 조각과 관련이 있다. 이 파일에서 추가로 확인한 사실은 다음 둘이다.

| 번호 | 내용 | 근거 |
|---|---|---|
| shell-F23 **[검증: v1.6 결함 유지, 수정됨 A-460]** | 업로드 `POST /files`는 로그인하지 않으면 403이지만 `authorEmail`, `authorLoginId` 폼 항목이 있으면 그 사용자로 올린 것으로 처리된다(쿼리 문자열은 제외). 화면은 이 항목을 보내지 않으며 외부 연동용이다 | `AttachmentController.kt:140-150` |
| shell-F24 **[검증: 결함 아님]** | `comment-delete-modal`은 `Comment.init`의 컨테이너 선택자를 `escapeXml`한 문자열로 박는다. 선택자에 따옴표가 있어도 JS 문자열이 깨지지 않지만 호출부는 모두 `#id` 고정 값이다(실제 영향 없음) | `comment-delete-modal.html:26` |
