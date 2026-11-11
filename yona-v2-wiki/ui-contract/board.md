---
title: UI 계약 - 게시판 (board)
type: ui-contract
sources:
  - src/main/resources/templates/board/list.html
  - src/main/resources/templates/board/partial-list.html
  - src/main/resources/templates/board/view.html
  - src/main/resources/templates/board/partial-comments.html
  - src/main/resources/templates/help/keymap.html
  - src/main/resources/static/assets/javascripts/esm/Request.js
  - src/main/kotlin/dev/yona/web/access/ProjectCheckStage.kt
  - src/main/kotlin/dev/yona/web/access/AnonymousCheckStage.kt
  - src/main/resources/templates/board/create.html
  - src/main/resources/templates/board/edit.html
  - src/main/kotlin/dev/yona/web/board/BoardListController.kt
  - src/main/kotlin/dev/yona/web/board/BoardViewController.kt
  - src/main/kotlin/dev/yona/web/board/BoardWriteController.kt
  - src/main/kotlin/dev/yona/web/board/BoardCommentController.kt
  - src/main/kotlin/dev/yona/web/board/BoardViewAssembler.kt
  - src/main/kotlin/dev/yona/web/board/OrganizationBoardController.kt
  - src/main/kotlin/dev/yona/web/api/BoardApiController.kt
  - src/main/kotlin/dev/yona/domain/board/PostingSearchService.kt
  - src/main/kotlin/dev/yona/access/AccessControl.kt
  - src/main/resources/static/assets/javascripts/esm/service/board.List.js
  - src/main/resources/static/assets/javascripts/esm/service/board.View.js
  - src/main/resources/static/assets/javascripts/esm/service/board.Write.js
updated: 2026-11-11
---

# UI 계약: 게시판 (`board/`)

기존 설명은 [web/board.md](../web/board.md), 접근 검사 체인은 [subsystems/access-control.md](../subsystems/access-control.md), 오류 화면은 [web/error-pages.md](../web/error-pages.md)에 있다. 여기에는 템플릿 기준으로 새로 확인한 동작 계약과 어긋난 점만 적는다. 줄 번호는 `ui-improvements` 브랜치 기준이다.

## 영역 요약

| 템플릿 | URL (메서드) | 컨트롤러 | 용도 |
|---|---|---|---|
| `board/list.html` | `/{user}/{project}/posts` (GET) | `BoardListController.posts` (`web/board/BoardListController.kt:40`) | 프로젝트 게시글 목록, 공지, 검색, 라벨 거르기, 정렬 |
| `board/partial-list.html` | (조각 `item(post, base)`) | 목록 화면과 조직 게시판(`organization/board-list`)이 쓴다 | 목록의 글 한 줄 |
| `board/view.html` | `/{user}/{project}/post/{number}` (GET) | `BoardViewController.post` (`web/board/BoardViewController.kt:47`) | 글 보기, 댓글, 지켜보기, 라벨 |
| `board/partial-comments.html` | (조각 `commentList`) | `view.html`이 쓴다 | 댓글 목록 |
| `board/create.html` | `/{user}/{project}/postform` (GET), `/{user}/{project}/posts` (POST, 검증 실패 때 400으로 다시 그림) | `BoardWriteController.newPostForm` (`web/board/BoardWriteController.kt:71`), `newPost` (`:89`) | 글 작성, README, 이슈 템플릿, 저장소 파일 온라인 커밋 |
| `board/edit.html` | `/{user}/{project}/post/{number}/editform` (GET), `/{user}/{project}/post/{number}/edit` (POST, 검증 실패 때 400으로 다시 그림) | `BoardWriteController.editPostForm` (`:126`), `editPost` (`:139`) | 글 수정, 수정 권한이 없으면 원본 보기 |

템플릿이 없는 같은 영역의 서버 경로(화면이 부른다):

| 경로 (메서드) | 컨트롤러 | 부르는 곳 |
|---|---|---|
| `/{user}/{project}/post/{number}/comment` 와 `/comment/{commentId}` (POST) | `BoardCommentController.newComment` (`web/board/BoardCommentController.kt:49`) | 댓글 입력 폼, 댓글 수정 폼, 한 줄 댓글 폼 |
| `/{user}/{project}/post/{number}/comment/{commentId}/delete` (DELETE) | `BoardCommentController.deleteComment` (`:79`) | 댓글 삭제 확인창의 "예", 한 줄 댓글의 x |
| `/{user}/{project}/post/{number}/delete` (DELETE) | `BoardWriteController.deletePost` (`web/board/BoardWriteController.kt:180`) | 글 삭제 확인창의 "예" |
| `/{owner}/{project}/post/{number}/label` (POST, JSON) | `BoardApiController.updatePostLabel` (`web/api/BoardApiController.kt:55`) | 글 보기 오른쪽 라벨 선택기 |
| `/{owner}/{project}/post/{number}/content` (PATCH, JSON) | `BoardApiController.updatePostingContent` (`:73`) | 본문 체크리스트(`Tasklist.js`) |
| `/{owner}/{project}/post/{number}/comment/{commentId}` (PATCH, JSON) | `BoardApiController.updatePostingComment` (`:86`) | 댓글 체크리스트 |
| `/watch`, `/unwatch` (POST) | `web/WatchController.kt:40`, `:50` | 지켜보기 단추 |
| `/translation` (POST, JSON) | `web/api/TranslationApiController.kt:47` | 번역 단추 |
| `/{user}/{project}/mentionList` (GET) | `web/project/MentionListController.kt:43` | `yona.Mention` 자동 완성 |
| `/{owner}/{project}/titleHeads` (GET) | `web/api/IssueFormApiController.kt:43` | 글 작성의 제목 머리말 자동 완성 |
| `/{user}/{project}/issue/labels.css` (GET) | `web/issue/IssueLabelController.kt:174` | 라벨 색 스타일 시트 |

## 공통 규칙

### 접근 검사 순서

모든 컨트롤러 메서드는 클래스에 `@AnonymousCheck`가 있고 `AccessChainInterceptor`가 "메서드에 붙은 것이 먼저, 클래스에 붙은 것이 나중" 순서로 실행한다(`web/access/AccessChainInterceptor.kt:29-38`, `:47-50`). 이 영역에서의 결과는 다음과 같다.

| 메서드 | 어노테이션(실행 순서) | 익명이 요청하면 |
|---|---|---|
| `posts`, `post` | `@IsAllowed(READ ...)` 다음 `@AnonymousCheck` | 사이트가 익명을 허용하고 프로젝트가 공개이면 통과. 아니면 로그인 화면이 403으로 나온다(`ProjectCheckStage.kt:130-136`, `:148-157`). 이슈와 마일스톤 메뉴를 끈 프로젝트의 주소만 404로 막는 `checkMenuEnabled`(`:97-111`)는 게시판 주소(`posts`, `post`)에는 적용되지 않는다. 게시판 메뉴를 꺼도 주소로는 계속 열린다 |
| `newPostForm` | `@AnonymousCheck(requiresLogin, flash)` 다음 `@IsCreatable(BOARD_POST)` | 로그인 폼으로 303(`/users/loginform?redirectUrl=<요청 경로>`, GET이고 질의 문자열이 있으면 `%3F`와 인코딩한 질의 문자열이 뒤에 붙는다)이고 `user.login.alert` 안내가 붙는다(`AnonymousCheckStage.kt:25-36`) |
| `newPost` | `@IsCreatable(BOARD_POST)` 다음 `@AnonymousCheck` | 익명은 만들 권한이 없어 `error.forbidden` 403 화면(`ProjectCheckStage.kt:191-195`). 로그인 이동이 아니다 |
| `editPostForm`, `editPost` | `@NullProjectCheck` 다음 `@AnonymousCheck`. 글 읽기 검사는 컨트롤러 안에서 한다(`editPost`는 입력 검사보다 먼저, `BoardWriteController.kt:134`, `:151`) | 프로젝트가 없으면 404(로그인한 사용자는 403 `error.forbidden.or.notfound`). 글을 읽을 수 없으면 403 `error.forbidden` |
| `deletePost` | `@IsAllowed(DELETE, BOARD_POST)` 다음 `@AnonymousCheck` | 403 |
| `newComment` | `@IsAllowed(READ, BOARD_POST)`, `@NullProjectCheck`, `@AnonymousCheck` | 읽을 수 있으면 컨트롤러가 `viewer == null` 또는 댓글 만들기 권한 없음을 403으로 막는다(`BoardCommentController.kt:63-65`) |
| `deleteComment` | `@NullProjectCheck` 다음 `@AnonymousCheck` | 댓글이 없거나 주소의 소유자, 프로젝트, 글 번호에 속한 댓글이 아니면 404(`BoardCommentController.kt:86-89`). 그다음 컨트롤러가 `DELETE` 권한을 보고 403(`:91`) |

### 요청 종류와 응답

- 화면에서 나가는 `POST`/`DELETE`/`PATCH`는 `yona.Csrf.js`(`common/scripts.html:41`)가 쿠키 `XSRF-TOKEN`을 폼에는 숨은 `_csrf`, `fetch`/XHR에는 `X-XSRF-TOKEN` 헤더로 싣는다(`static/assets/javascripts/common/yona.Csrf.js`). 서버는 `yona.csrf-protection`(기본 `true`)일 때 검사한다(`config/SecurityConfig.kt:60-65`, `security/CsrfSupport.kt`). 토큰이 없거나 틀리면 스프링 보안이 거부한다(응답 모양은 이 영역 코드가 아니라 `shell`/`common` 담당).
- 성공한 폼 제출은 모두 `RedirectSupport.see`로 **303**이다(`web/RedirectSupport.kt:111-124`). 이동 위치는 아래 화면별 표에 적었다. 삭제(`DELETE`)는 `X-Requested-With: XMLHttpRequest`(`Request.js`가 단다)이면 **204 + `Location`**, 아니면 303이다(`BoardWriteController.kt:193-194`, `BoardCommentController.kt:94-95`). `Request.js`는 204와 `Location`이 있으면 그곳으로 이동하고 아니면 새로 고친다(`esm/Request.js:85-123`). 실패(2xx가 아닌 응답, 네트워크 오류)는 요소에 `request:error` 이벤트를 보내고, 취소(`preventDefault`)되지 않으면 `$yona.alert`로 알린다. 글은 403이면 `error.forbidden`, 404이면 `error.notfound`, 그 밖에는 `error.internalServerError` 메시지이다(`_alertFailure`, `Request.js:73-80`). 이 영역의 삭제 단추(글 삭제 "예", 댓글 삭제 "예")는 이벤트를 취소하지 않으므로 이 알림이 뜬다.
- 오류 화면: 404 `ErrorViews.notFound`, 403 `ErrorViews.forbidden`, 400 `ErrorViews.badRequest`, 413 `ErrorViews.requestTextEntityTooLarge`(`web/error/ErrorViews.kt`). 프로젝트 화면 틀을 쓰는 것은 `ErrorViews.*(messageKey, project, ...)` 변형이다.
- 반응형: 폭 720px 이하(`@media all and (max-width: 720px)`, `app/assets/stylesheets/scss/_responsive.scss:1`)에서 `.hide-in-mobile`은 `display:none !important`(`:302`), `.show-in-mobile`은 `display:block !important`(`:290`), `.span-right-pane`은 숨김(`:265`), `.span-left-pane`은 폭 100%(`:261`)이다. `col-lg-*`는 Bootstrap `lg`(992px) 기준이다.

## 화면 1: 글 목록 (`board/list.html`)

### 1. 화면 개요

- 컨트롤러: `BoardListController.posts` (`web/board/BoardListController.kt:40-72`). `@Transactional(readOnly)`.
- 쿼리 값: `pageNum`(1부터, 숫자가 아니면 400 `ErrorViews.badRequest()`), `orderBy`(없거나 공백이면 기본 `id`, `id`는 `createdDate`로 본다, `PostingSearchCondition.kt:25`), `orderDir`(기본 `desc`, `asc` 아니면 모두 내림차순), `filter`(기본 빈 글자), `labelIds`(여러 개, 숫자가 아니면 400).
- 모델: `title`(`menu.board`), `projectView`, `cond`, `orderBy`, `items`, `notices`, `totalRows`, `totalPageCount`, `sortLinks`, `hasLabels`, `labelGroups`, `selectedLabelIds`.
- 틀: `layout/project-layout`(`'BOARD'`), `project/menu :: projectMenu(..., 'BOARD', 'main-menu-only')`(`list.html:13`), `board/partial-list :: item`, `issue/partial-select-label`, `common/two-column-mode-checkbox-area`, `help/keymap`, `common/select2`.
- 문서 제목: `프로젝트이름 - 게시판`(`list.html:11`).

### 2. 레이아웃 계약

| 영역 | 내용 | 근거 |
|---|---|---|
| 머리 줄 `.search-wrap.underline` | 왼쪽 `float-start` 검색 폼(`#option_form`), 오른쪽 `float-end` 글쓰기 단추(게시판 메뉴가 켜져 있을 때만, `th:if="${projectView.menuSetting.board}"`) | `list.html:17-33` |
| 정렬 막대 `.filter-wrap.board` | `totalRows > 1`일 때만 보인다. 링크 3개(`sortLinks`) | `list.html:40-45` |
| 공지 `ul.post-list-wrap.notice-wrap` | 공지가 있을 때만. 쪽, 검색어, 라벨과 무관하게 항상 모두 보인다 | `list.html:47-49`, `PostingSearchService.kt:90-92` |
| 본문 `ul.post-list-wrap` | 공지가 아닌 글 15개씩 | `list.html:51-53`, `BoardListController.kt:93-94` |
| 쪽 막대 `#pagination` | `pagination.js`가 아니라 `esm/Pagination.js`가 채운다. 쪽 수가 0이면 아무것도 그리지 않는다 | `list.html:59-61`, `Pagination.js:123-125` |
| 키맵 | `help/keymap('boardList')`: "단축키" 단추와 모달 | `list.html:63` |

- 빈 상태: `totalRows == 0`이고 공지도 없으면 `.error-wrap`에 `post.is.empty`(등록된 게시물이 없습니다)만 보이고 정렬 막대, 목록은 그리지 않는다(`list.html:35-38`). 공지만 있고 일반 글이 0이면 목록 상태이고 공지만 보인다.
- 글 한 줄(`partial-list.html`)의 모바일 규칙: 작성자 아바타는 `hide-in-mobile`(`partial-list.html:11`, `:14`).
- 줄 호버 배경색(`#fafafa`/`#fff`)은 `board.List.js`가 `.post-list-wrap > .post-item`(공지 제외)에 직접 칠한다(`board.List.js:30-36`).

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 검색어 입력 `input[name=filter]` | 입력란 | 항상 | Enter(폼 제출) | `GET {base}/posts?orderBy=&orderDir=&filter=&labelIds=` | 같은 화면을 다시 그림 | 프로젝트 읽기 | `list.html:18-24` |
| 돋보기 단추 `.search-btn` | 제출 단추 | 항상 | 폼 제출 | 위와 같음 | 위와 같음 | 같음 | `list.html:23` |
| 숨은 `orderBy`, `orderDir` | 숨은 입력 | 항상 | 검색해도 정렬 유지 | 위와 같음 | `pageNum`은 폼에 없어 검색하면 1쪽부터 | | `list.html:19-20` |
| 라벨 선택기 `select#labelIds[name=labelIds][multiple]` | select2 다중 선택 | `hasLabels`(프로젝트에 라벨이 하나라도 있음) | 선택이 바뀌면 `#option_form`을 제출 | `GET ...?labelIds=1&labelIds=2` | 선택한 라벨 중 **하나라도** 붙은 글(`PostingSearchService.kt:50-53`) | 같음 | `list.html:25-27`, `:81-83` |
| 라벨 옆 `[편집]` 링크 | 링크 | `view.manager`(프로젝트 관리자) | 새 탭으로 라벨 편집 화면 | `GET {base}/issue/labelsform` | | 서버: 라벨 편집 화면이 자체 검사 | `issue/partial-select-label.html` |
| 2단 보기 체크박스 `#two-column-mode` | 체크박스 | 항상(`hide-in-mobile`) | 켜면 글 제목 클릭이 오른쪽 오프캔버스 iframe으로 열림 | 없음(`localStorage.useTwoColumnMode`) | 아래 4절 | | `list.html:28`, `common/two-column-mode-checkbox-area.html` |
| 글쓰기 `a.ybtn-success` | 링크 | `projectView.menuSetting.board`(익명, 쓰기 권한 없는 사용자에게도 보인다. 게시판 메뉴가 꺼진 프로젝트에서는 단추를 감싼 `.float-end`가 없다) | 작성 화면으로 이동 | `GET {base}/postform` | 익명은 로그인 폼, 권한 없으면 403 | `@IsCreatable(BOARD_POST)` | `list.html:30-31` |
| 정렬 링크 3개 `.filter` | 링크 | `totalRows > 1` | 정렬 기준 변경 | `GET {listUrl}?filter=..&labelIds=..&orderBy=<updatedDate\|createdDate\|numOfComments>&orderDir=<asc\|desc>` | 현재 기준을 다시 누르면 방향이 뒤집힌다(현재가 내림차순이면 `asc`, 아니면 `desc`). 기준이 아닌 항목은 `desc`로 시작 | 읽기 | `list.html:42-43`, `BoardListController.kt:77-92` |
| 글 제목 링크 `a.title` | 링크 | 줄마다 | 글 보기로 이동(2단 보기 켜짐이면 오프캔버스) | `GET {base}/post/{n}` | | 읽기 | `partial-list.html:22` |
| 작성자 아바타, 작성자 이름 | 링크 | `authorLoginId != null`(아바타), 이름이 비어 있지 않으면 이름 | 사용자 페이지 | `GET /{loginId}` | 작성자가 없으면 로그인 ID `NullUser` 값, 이름 `issue.noAuthor` 문구 | | `partial-list.html:11-16`, `:25-26` |
| 댓글 수 | 링크 | `commentCount > 0` | 글의 `#comments` 앵커로 | `GET {base}/post/{n}#comments` | | | `partial-list.html:30-32` |
| 제목 머리말 `.title-prefix` | 글자(`titleHeadersHtml`) | 제목에 머리말이 있을 때 | 클릭하면 그 글자를 검색어로 넣고 폼 제출. 마우스를 올리면 같은 글자를 가진 머리말이 모두 `.title-prefix-hover` | `GET ...?filter=<글자>` | | | `board.List.js:11-17`, `list.html:84-92` |
| 줄의 라벨 배지 `a.list-label[data-label-id][data-category-id]` | 링크(`href="#"`) | 글에 라벨이 있을 때 | 클릭하면 라벨 선택기에 그 라벨을 더하고 `change` 발생 → 폼 제출 | 위의 라벨 거르기 | | | `partial-list.html:33`, `board.List.js:49-70` |
| 쪽 이동(이전, 번호 입력, 다음) | `esm/Pagination.js`가 그림 | `totalPageCount > 0` | 이전, 다음 링크는 현재 URL에서 `pageNum`만 바꾼 주소. 번호 입력란에서 Enter이면 그 쪽으로 이동 | `GET <현재 URL>&pageNum=N` | 1쪽에서 이전, 마지막 쪽에서 다음은 링크 없이 흐린 글자 | | `Pagination.js:122-159`, `:210-224` |
| 단축키 단추 | 링크 | 항상 | 키맵 모달 | 없음 | | | `help/keymap.html:12` |

### 4. 스크립트 동작

- 불러오는 것: `esm/service/twoColumnMode.js`, `esm/service/board.List.js`(모듈), `common/select2`(select2 4.1.0 + `esm/ui/Select2.js`).
- `$yona.loadModule("board.List", {elPagination, nTotalPages})`가 `Pagination.update`를 호출하고 라벨 배지 클릭, 호버 배경, `.title-prefix` 클릭, `_initTwoColumnMode`를 연결한다(`board.List.js:25-37`).
- `board.List.js`의 `_onClickPage`(`#pagination a` 클릭 때 `input[name=pageNum]`에 값을 넣고 폼 제출)는 쓰이지 않는다. 폼에 `pageNum` 입력이 없고, `welPages`를 `Pagination.update`가 링크를 만들기 **전에** 잡아 빈 집합이기 때문이다(`board.List.js:78`, `:114-116`). 실제 쪽 이동은 링크의 `href`이다([board-F6](#발견-사항)).
- 2단 보기(`twoColumnMode.js`): `localStorage.useTwoColumnMode`(`'true'`/`'false'`)를 읽고 쓴다. 켜 있으면 `.title`, `.twoColumeModeTarget` 클릭을 가로채 `#pageslide`(Bootstrap 오프캔버스, 배경 없음, `keyboard:false`, 스크롤 허용)에 iframe으로 주소를 연다. 같은 링크를 다시 누르면 닫는다. 다른 링크는 iframe을 지우고 300ms 뒤 새 주소를 읽는다. 열려 있는 동안 `.left-menu`와 `.user-info-box`를 숨기고 `history.pushState/replaceState`로 주소 표시줄을 바꾼다. 체크박스에는 호버 팝오버(`common.two.column.mode.desc`)가 있다(`twoColumnMode.js:19-161`).
- 라벨 선택 변경 → 폼 제출(`list.html:81-83`).

### 5. 단축키

| 키 | 동작 | 등록 위치와 조건 |
|---|---|---|
| `N` | 글쓰기(`{base}/postform`)로 이동 | `list.html:75-79`. `projectView.menuSetting.board`가 켜져 있을 때만 등록. 글쓰기 단추도 같은 조건이다 |
| `←`, `→` | 이전 쪽, 다음 쪽 | `Pagination.js:311-316`이 링크를 만들 때마다 `ShortcutKey.setKeymapLink({LEFT/RIGHT: href})` 호출. 갈 쪽이 없으면 href가 빈 글자라 핸들러를 떼어 낸다(`ShortcutKey.js:265-271`) |

- 입력란, 텍스트 영역에 초점이 있으면 `setKeymapLink` 핸들러는 무시한다(`ShortcutKey.js:257-261`, `aFormTags = INPUT, TEXTAREA`, `:42`). `select`는 포함되지 않는다.
- 같은 키가 겹치면 핸들러 표에 마지막으로 넣은 것이 이긴다(`ShortcutKey.js:136-139`). 전역 키(`H B I C M P Q A U F` 등)는 `common` 담당이다.

### 6. 팝업, 모달

- 키맵 모달 `#helpKeys`(Bootstrap 모달). 제목은 `title.boardList`, 내용은 프로젝트 이동 키(`H`는 항상, `B`는 게시판 메뉴, `I`는 이슈 메뉴, `C`는 코드 메뉴가 켜져 있고 코드를 볼 수 있을 때, `M`은 마일스톤 메뉴, `P`는 풀 리퀘스트 메뉴와 VCS 지원과 코드 열람 가능일 때, `Q`는 프로젝트 관리자일 때만 줄이 있다), `N`(글쓰기. 이 줄은 게시판 메뉴 설정과 무관하게 항상 있다), `←`/`→`, 사이트 키(`A`, `U`, `F`, `ALT+S`), `CTRL+ENTER`. 닫는 방법은 "확인" 단추, Esc, 바깥 클릭(Bootstrap 기본)이다(`help/keymap.html`).

### 7. 요청과 응답

| 요청 | 응답 |
|---|---|
| `GET {base}/posts` | 200 HTML. `pageNum`이나 `labelIds`가 숫자가 아니면 400, 없는 프로젝트 404, 읽을 수 없으면 403(익명은 로그인 화면 403) |

### 8. 권한 행렬

| 역할 | 목록 보임 | 서버 허용 |
|---|---|---|
| 비로그인 | 사이트가 익명을 허용하고 프로젝트가 공개일 때 | 같음. 아니면 로그인 화면(403) |
| 로그인 비멤버 | 공개 프로젝트만 | 같음(비공개는 403) |
| 프로젝트 멤버, 관리자, 사이트 관리자 | 항상 | 같음 |

정의: `AccessControl.isProjectResourceAllowed`의 `READ`(`access/AccessControl.kt:216-219`). 게스트 계정은 공개 프로젝트도 읽지 못한다(`user?.isGuest != true`).

### 9. 빈 상태, 경계 값

- 쪽당 15개(`BoardListController.kt:93-94`), 공지는 쪽과 무관하게 전부.
- 정렬 가능 열은 `number`, `title`, `createdDate`, `updatedDate`, `numOfComments`(`PostingSearchService.kt:98`). 그 밖의 값은 `createdDate`. 같은 값이면 `id desc`로 이어서 정렬.
- 검색은 제목과 본문에서 대소문자 무시 부분 일치이고 `%`, `_`는 글자 그대로이다(`LikeEscape`, `PostingSearchService.kt:46-49`). 공지는 `filter`, `labelIds` 영향을 받지 않는다.
- 시험: `BoardListTest`(기본 정렬, 공지 분리, 정렬, 검색, 라벨, 15개 쪽, 빈 목록, 정렬 링크, 쓰기 단추, 403/404).

## 화면 2: 글 보기 (`board/view.html`, `board/partial-comments.html`)

### 1. 화면 개요

- 컨트롤러: `BoardViewController.post` (`web/board/BoardViewController.kt:47-87`), `@IsAllowed(READ, BOARD_POST)`. 경로 변수 `number`는 `[0-9]+`.
- 같은 URL에 `Accept: application/json`(문자열 포함 검사)이면 JSON을 돌려준다: `{title, type, author, authorName, created_at, body, attachments, comments[{id,type,authorId,authorName,created_at,body,attachments?}]}`(`:55-70`). 템플릿은 쓰지 않는다.
- HTML일 때 부작용: 로그인한 사용자의 최근 방문 프로젝트, 최근 본 글, 읽음 표시를 기록한다(`:72-74`, 시험 `BoardViewTest` "최근 본 글에 남는다").
- 모델: `title`(`글제목 |:| 본문 앞 200자`), `projectView`, `post(PostDetail)`, `listUrl`. README 글(`post.readme` + VCS가 README 글 지원)이면 본문은 저장소 README를 읽는다(`:53`, `BoardViewAssembler.kt:55`).
- `listUrl`: `Referer`가 이 프로젝트의 게시판 목록이면 그 주소(검색 조건, 쪽 포함), 아니면 `{base}/posts`(`:89-92`).
- 틀: `layout/project-layout('BOARD')`, `projectMenu`, `common/tasklist-bar`, `common/partial-history`, `board/partial-comments`, `common/comment-form`, `common/comment-update-form`, `common/child-comments`, `common/comment-delete-modal`, `common/markdown`, `common/select2`, `issue/partial-select-label`, `issue/partial-show-selected-label`, `help/keymap('boardDetail')`.

### 2. 레이아웃 계약

| 영역 | 내용 | 근거 |
|---|---|---|
| 머리 `.board-header.issue` | `#번호 제목`. 작성 시각은 두 벌: 데스크톱은 `float-end ... hide-in-mobile`, 모바일은 제목 안 `hide show-in-mobile` | `view.html:17-27` |
| 본문 `.board-body.row` | 왼쪽 `col-lg-9.span-left-pane`(작성자 줄, 본문, 첨부, 행동 줄, 댓글), 오른쪽 `col-lg-3.span-right-pane.sticky-top`(글쓰기, 라벨, 아이콘). 720px 이하에서 오른쪽 열은 사라지고 왼쪽이 전체 폭 | `view.html:30-123`, `_responsive.scss:261-267` |
| 푸터 `.board-footer` | 키맵 단추 | `view.html:127-129` |
| 빈 본문 | `post.body`가 비면 `div.content.empty-content`만 있고 본문, 체크리스트 폼은 없다 | `view.html:53` |
| 첨부 | `#attachments[data-attachments=<json>]`를 `yona.Attachments`가 그린다 | `view.html:65`, `board.View.js:107-113` |

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한(서버) | 근거 |
|---|---|---|---|---|---|---|---|
| 작성자 링크 | 링크 | 항상 | 사용자 페이지 | `GET /{loginId}` | 작성자 없음이면 `common.noAuthor` 글자 | | `view.html:33-42` |
| "변경 이력" (익명) | 링크(모달 속성 없음) | `post.historyHtml != null`이고 `viewerAnonymous` | 로그인 폼으로 | `GET /users/loginform?redirectUrl={글 주소}`(`@{|...|}`로 컨텍스트 경로 접두어) | | | `view.html:43-46` |
| "변경 이력" (로그인) | 링크, 모달 `#-yona-posting-history` | `historyHtml != null`이고 로그인 | 이력 모달 열기 | 없음 | 모달 내용은 `common/partial-history` | | `view.html:47-50` |
| 지켜보기 `#watch-button` | 단추 | `post.canWatch` | 지켜보지 않으면 `watch`, 지켜보면 `unwatch` | `POST /watch?resource.type=board_post&resource.id={id}` 또는 `POST /unwatch?...`(`$yona.sendForm`, 본문 없음, `X-Requested-With`) | 성공하면 `data-watching` 반전, 클래스 `ybtn-watching` 토글, 글자를 `post.unwatch`/`post.watch`로 바꾸고 3초 알림 `post.watch.start`/`post.unwatch.start`. 실패 처리는 없다(조용히 무시) | 서버는 **읽기 권한만** 본다. 익명은 403 본문 `Anonymous cannot watch it.`, 읽기 불가는 403 `You have no permission to watch it.`(`WatchController.kt:40-47`). `unwatch`는 JSON이 아니면 리소스 주소로 303(`:50-62`) | `view.html:69-71`, `board.View.js:56-71`, `WatchController.kt` |
| 번역 `#translate` | 단추 | `site.translationApiConfigured` | 한 번만(`one('click')`) 본문 번역 요청 | `POST /translation` JSON `{owner, projectName, type:"posting", number}` | 응답 `{translated}`(마크다운 렌더 HTML)로 첫 `.markdown-wrap`을 바꾸고 단추를 비활성화 | 서버: 번역 설정 없음이면 412, 프로젝트 읽기 불가 403, 글 없음 404(`TranslationApiController.kt:47-57`). 실패 처리는 없다 | `view.html:75`, `:203-222` |
| 글 수정 아이콘(행동 줄) | 단추(`onclick="window.location = '{base}/post/{n}/editform'"`, 컨텍스트 경로 접두어 없음, [board-F17](#발견-사항)) | `post.canUpdate` | `{base}/post/{n}/editform`으로 이동 | `GET` | | `editPostForm` 자체가 읽기만 요구 | `view.html:77-78` |
| 원본 보기 아이콘 | 링크 안 단추 | `!post.canUpdate` | 같은 수정 화면(저장 단추 없음)으로 이동. 툴팁 `button.show.original` | `GET .../editform` | | | `view.html:79` |
| 삭제 아이콘 | 링크 `#deleteConfirm`(모달) | `post.canDelete` | 삭제 확인창 | 없음 | | | `view.html:81-82` |
| 삭제 확인창 "예" | 단추 `data-request-method="delete"` | 모달 안 | 삭제 요청 | `DELETE {base}/post/{n}/delete` (`Request.js`, `X-Requested-With`) | 204 + `Location: {base}/posts`이면 목록으로 이동 | `@IsAllowed(DELETE, BOARD_POST)` + 컨트롤러 재검사. 403 | `view.html:139-152`, `BoardWriteController.kt:174-189` |
| 삭제 확인창 "아니요", X | 단추 | | 닫기 | | | | `view.html:141`, `:149` |
| 오른쪽 `글쓰기` | 링크 | `projectView.menuSetting.board` | 작성 화면 | `GET {base}/postform` | | `IsCreatable` | `view.html:101-103` |
| 오른쪽 라벨 선택기 | select2 다중 | `post.hasProjectLabels`이고 `post.canUpdate` | 선택이 바뀌면 | `POST {base}/post/{n}/label` JSON 본문 `JSON.stringify($(this).val() || [])`, `Content-Type: application/json; charset=UTF-8` | 응답은 쓰지 않고 오류도 무시. [board-F3](#발견-사항) | 서버: 수정 권한 필요. 익명 또는 권한 없음 403(본문 없음), JSON 아님 400 `Expecting Json data`, 숫자 아님 400 `Wrong label id`, 없는 라벨 400, 이 프로젝트의 것이 아닌 라벨이 섞이면 400 `Wrong label id` | `view.html:106-109`, `:186-197`, `BoardApiController.kt:55-71` |
| 오른쪽 라벨 목록 | 읽기 전용 | `hasProjectLabels`이고 `!canUpdate` | 표시만 | | | | `view.html:110-112` |
| 오른쪽 수정, 원본 보기, 삭제 아이콘 | 위 행동 줄과 같음 | 같은 조건 | | | | | `view.html:114-121` |
| 댓글 입력 폼 | `common/comment-form` | `post.canComment` | 제출 | `POST {base}/post/{n}/comment` 멀티파트: `contents`, `temporaryUploadFiles`, 첨부 | 303 → `{base}/post/{n}#comment-{새 댓글 번호}` | 아래 8절 | `view.html:93`, `common/comment-form.html` |
| 댓글 입력 불가 안내 | 비활성 영역 | `!post.canComment` | `data-login="required"` 영역(비활성 텍스트 영역) | | | | `common/comment-form.html` 아랫부분 |
| 댓글 번역 | 단추 `.comment-translate` | 번역 설정됨 | 한 번만 번역 | `POST /translation` `{type:"post-comment", number:<댓글 id>}` | `#comment-body-{id}`의 `.markdown-wrap` 교체 | | `partial-comments.html:36`, `view.html:224-242` |
| 댓글 수정 아이콘 | 단추 `data-toggle="comment-edit"` | `c.canUpdate`(수정 권한) | 수정 폼(`#comment-editform-{id}`)과 본문(`#comment-body-{id}`)을 번갈아 보임(`Comment.js`) | 없음 | 폼의 저장 단추도 `c.canUpdate`일 때 있다 | | `partial-comments.html:38`, `esm/Comment.js:36-38`, `:55-62` |
| 댓글 수정 폼 저장 | 폼 제출 | 수정 폼 안, `c.canUpdate` | 제출 | `POST {base}/post/{n}/comment/{id}` 멀티파트: `id`, `contents-{id}`(편집기 이름), `notificationMail=yes`(작성자 본인일 때만 체크박스), `temporaryUploadFiles` | 303 → `...#comment-{id}` | 수정 권한 | `common/comment-update-form.html` |
| 댓글 삭제 아이콘 | 단추 `data-toggle="comment-delete"` | `c.canDelete` | 삭제 확인 모달 `#comment-delete-modal` | | 확인 "예" → `DELETE {base}/post/{n}/comment/{id}/delete` → 204 + `Location: {base}/post/{n}` → 이동 | 서버: 삭제 권한 없으면 403, 없는 댓글이나 주소의 프로젝트, 글에 속하지 않은 댓글은 404 | `partial-comments.html:39-40`, `Comment.js:64-71`, `BoardCommentController.kt:79-96` |
| 한 줄 댓글(대댓글) 입력 | `common/child-comments` | `c.canReply` | 제출 | `POST {base}/post/{n}/comment` + `parentCommentId` | 303 | 댓글 만들기 권한 | `partial-comments.html:52` |
| 한 줄 댓글 `x` | 링크 `data-toggle="comment-delete"` | `child.canDelete` | 위와 같이 삭제 모달 | `DELETE {base}/post/{n}/comment/{childId}/delete` | | | `common/child-comments.html` |
| 댓글 작성자 아바타, 이름, 시각 | 링크 | 항상 | 사용자 페이지, `#comment-{id}` 앵커 | | | | `partial-comments.html:17-33` |
| `[Link]` | 링크(`display:none`) | 항상 있으나 숨김 | | | | | `partial-comments.html:33` |
| 본문 체크리스트 | `Tasklist.js` | 본문에 체크리스트가 있고 `data-allowed-update=post.canUpdate` | 체크 토글 | `PATCH {base}/post/{n}/content` JSON `{content, original}` | 다른 사람이 먼저 바꿨으면 409 `{message, storedContent}`. 요청 주소는 본문 폼의 `action`(`'/' + owner + '/' + project + '/post/' + n + '/content'`, 컨텍스트 경로 접두어 없음, [board-F17](#발견-사항)) | 수정 권한(익명 401) | `view.html:55-62`, `Tasklist.js:32-43`, `BoardApiController.kt:73-84` |
| `elevator` | 오른쪽 아래 위로 가기 | 스크립트가 그림 | `yona.ui.Elevator.init({shape:'rounded', glass:true})` | | | | `view.html:244-247` |

### 4. 스크립트 동작

- 모듈: `esm/Tasklist.js`, `esm/SubComment.js`, `esm/CommentAttachmentsUpdate.js`, `esm/service/board.View.js`, `esm/Comment.js`(삭제 모달 조각이 불러옴), `esm/CommentForm.js`(댓글 폼 조각), `esm/Mention.js`. 스타일: `lib/elevator/jquery.elevator.css`.
- 인라인 스크립트(`view.html:162-249`)가 하는 일:
  - `board.View` 초기화: 지켜보기(`sWatchUrl`, `sUnwatchUrl`은 `@{}`로 컨텍스트 경로 접두어), 첨부 내려받기 목록(`yona.Attachments`), 댓글 업로더.
  - `yona.Mention({target: 'textarea[id^=editor-], .editorSeries', url: @{{base}/mentionList?number={n}&resourceType=board_post}})`. 호출 때 `mentionType=user|issue`와 `query`는 `Mention.js`가 붙인다(`Mention.js:337`).
  - `.comment-body`에 현재 사용자의 `pureName`이 들어 있으면 `.comment`에 `mentioned`, `.user-link`에는 `me` 클래스(`:199-201`).
  - 번역 단추 두 가지(위 표).
  - `yona.ui.Elevator.init`.
- 로컬 저장소: 댓글 입력란 초안은 `esm/service/temporarySaveHandler.js`가 `localStorage[location.pathname]`(새 댓글)와 `localStorage[<pathname>-last-comment-update-draft-<댓글id>]`(수정)에 5초 뒤 저장하고 1시간 뒤 지운다(`DRAFT_DELAY`, `DRAFT_TTL`, `yona.draftTimes` 표). 저장 뒤 입력란 옆 안내 글자는 `draft.saved` 메시지를 `$yona.escapeHtml`로 감싸 보인다(`temporarySaveHandler.js:132`).
- 폴링, 지연 로딩: 없다.

### 5. 단축키

| 키 | 동작 | 등록 |
|---|---|---|
| `L` | 목록(`listUrl`)으로 | `view.html:170-171`. 항상 |
| `N` | 글쓰기 | `menuSetting.board`일 때만(`:172-174`) |
| `E` | 수정 화면 | `post.canUpdate`일 때만(`:175-177`) |
| `CTRL+ENTER` | 한 줄 댓글 제출 | `SubComment.js:24` (`ctrlKey` 또는 `metaKey` + Enter). 큰 댓글 폼의 같은 키는 `common/editor` 담당 |

입력란에 초점이 있으면 위 단일 키는 동작하지 않는다.
키맵 모달의 `title.boardDetail`은 [board-F1](#발견-사항) 참고.

### 6. 팝업, 모달, 툴팁

| 대상 | 여는 요소 | 내용 | 닫기 |
|---|---|---|---|
| 글 삭제 확인 `#deleteConfirm` | 삭제 아이콘 | `issue.delete` 제목, `post.delete.confirm`, 예/아니요 | X, 아니요, Esc, 바깥 클릭 |
| 댓글 삭제 확인 `#comment-delete-modal` | 댓글 삭제 아이콘, 한 줄 댓글 x | `common.comment.delete.confirm`, 예 `#comment-delete-confirm`, 아니요 | 위와 같음 |
| 변경 이력 `#-yona-posting-history` | "변경 이력" | `common/partial-history` | Bootstrap 기본 |
| 툴팁 | 아이콘 단추 `data-bs-toggle="tooltip"` | `title` 글자 | |
| 알림(토스트) | 지켜보기 성공 | `$yona.notify(post.watch.start 또는 post.unwatch.start, 3000)` | 3초 뒤 |
| 키맵 모달 `#helpKeys` | 푸터 단추 | `help/keymap` | |

### 7. 요청과 응답

| 요청 | 응답 |
|---|---|
| `GET {base}/post/{n}` | 200 HTML 또는 `Accept: application/json`이면 200 JSON. 없는 글 404(프로젝트 화면 틀, `error.notfound`), 읽기 불가 403 |
| `POST /watch` | 200 빈 본문. 익명 403 텍스트, 권한 없음 403 텍스트, 잘못된 인자는 `BindFailureException`(400 계열) |
| `POST /unwatch` | JSON 선호면 200, 아니면 303(글 주소, 첫 해제일 때만 flash `post.unwatch.start`). `$yona.sendForm`의 `Accept`는 `*/*`이라 303을 따라가 글 HTML을 받는다([board-F9](#발견-사항)) |
| `POST .../label` | 200 `{"id": 프로젝트 소유자, "labels": 개수}` |
| 댓글, 글 삭제 | 204 + `Location` 또는 303 |

### 8. 권한 행렬 (글 보기와 댓글)

| 기능 | 비로그인 | 로그인 비멤버(공개 프로젝트) | 프로젝트 멤버 | 글 작성자 | 프로젝트 관리자 | 사이트 관리자, 조직 관리자 |
|---|---|---|---|---|---|---|
| 보기 | 공개 프로젝트이고 익명 허용일 때 | 예 | 예 | 예 | 예 | 예 |
| 수정(`UPDATE`) | 아니오 | 아니오 | **예(남의 글도)** | 예 | 예 | 예 |
| 삭제(`DELETE`) | 아니오 | 아니오 | **아니오** | 예 | 예 | 예 |
| 지켜보기 단추 보임(`WATCH`) | 아니오 | 예(로그인) | 예 | 예 | 예 | 예 |
| 댓글 쓰기 | 아니오 | 예 | 예 | 예 | 예 | 예 |
| 댓글 삭제 | 아니오 | 자기 댓글만 | 자기 댓글만 | | 예 | 예 |
| 글 수정, 삭제의 작성자 몫 | 해당 없음 | 해당 없음 | 해당 없음 | 프로젝트를 볼 수 있을 때만(공개 프로젝트, 직접 구성원, 조직 구성원 규칙) | 해당 없음 | 해당 없음 |

근거: `AccessControl.kt:106-117`(사이트 관리자 무조건 허용), `:175-226`(`UPDATE`는 멤버 허용, `DELETE`는 `CONTENT_TYPES`에서 작성자와 관리자만, 추가 개발 항목 D-48. 작성자 몫은 `authorHasAccess`일 때만 `:195-198`), `:65-77`(만들기). 화면과 서버가 다른 곳: 지켜보기 단추는 `WATCH` 기준으로 보이고 서버는 `READ`만 본다.

### 9. 빈 상태, 경계 값

- 본문 없음: `.empty-content`. 댓글 없음: 목록이 비고 헤더의 `post.commentCount`가 0.
- 제목은 `<th:block th:text="${post.title}">`로 이스케이프된다.
- 시험: `BoardViewTest`(머리와 본문, 작성자 없음, 빈 본문, 체크리스트 폼, 변경 이력, 수정 권한 단추, 구독 단추, 댓글, 아바타 크기, 대댓글 폼, 댓글 입력 권한, 라벨, 쓰기 단추 조건, README, JSON, 404/403, 최근 본 글), `BoardCommentTest`(댓글 등록, 수정, 삭제, 라벨 API, 본문과 댓글 갱신).

## 화면 3: 글 작성 (`board/create.html`)

### 1. 화면 개요

- `BoardWriteController.newPostForm` (`:71-87`): 쿼리 `readme`(있으면 저장소 README 본문), `issueTemplate`(있으면 `ISSUE_TEMPLATE.md`), `path`와 `branch`와 `edit`(저장소 파일을 열어 온라인 커밋). `path`가 있으면 `COMMIT` 만들기 권한이 없을 때 403(`:83`).
- 모델: `title`(`post.new`), `projectView`, `errors`, `titleValue`(README면 `Update README.md`, 이슈 템플릿이면 `ISSUE_TEMPLATE.md: Project Issue Template`, 아니면 빈 글자), `issueTemplate`, `hasIssueTemplate`, `branch`, `path`, `hasPath`, `isEdit`, `readmeRequested`, `preparedBody`, `lineEnding`, `allowedToNotice`, `canCommit`, `isGit`(`:229-251`).
- 이 화면은 세 가지 일을 한다: 글 쓰기, README 만들기(`readme`), 이슈 템플릿 만들기(`issueTemplate`), 저장소 파일 쓰기(`path`).

### 2. 레이아웃 계약

`.page-wrap-outer > .project-page-wrap > form.nm > .content-wrap.frm-wrap > dl`: 제목(`dd`), 파일 경로 또는 첨부 불가 안내(`dd`), 편집기(`dd`, `position:relative`) 순서. 아래에 업로더, 오른쪽 정렬 줄(`.right-txt.mt10.mb10`: 공지, README 체크박스), `.actions`(저장, 취소)(`create.html:16-71`).
- `hasIssueTemplate`이면 업로더 대신 안내 `issue.template.no.attachment.allow`. `hasPath`이면 `{branch}: /{path}`와 (수정이 아닐 때) 새 파일 이름 입력.
- 업로더(`common/file-uploader('BOARD_POST', null)`)는 `!hasIssueTemplate and !hasPath`이고 로그인한 경우(`!currentUser.anonymous`)만(`create.html:41-45`).

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한(서버) | 근거 |
|---|---|---|---|---|---|---|---|
| 제목 `title-input#title[name=title]` | 사용자 정의 요소(`lib/title-input`) | 항상 | 입력. `maxlength=250`. Enter는 본문으로 초점 이동(`board.Write.js:25-30`). 자동 완성 `yonaTitleHeadModule` | 제출 필드 `title`. 자동 완성은 `GET /{owner}/{project}/titleHeads?query=` (`Accept: application/json`) | 오류가 있으면 `.error` 클래스와 아래 메시지 | `titleHeads`는 `@IsAllowed(READ)`, JSON이 아니면 406 | `create.html:22-26`, `:114-117`, `IssueFormApiController.kt:43-62` |
| 새 파일 이름 `input.new-file-name` | 입력란 | `hasPath`이고 `!isEdit` | 키를 칠 때마다 `#path`를 `{path}{입력값}`로 바꿈 | 제출 필드 `path`(`new-file-name`은 서버가 읽지 않는다) | | | `create.html:33`, `:105-108` |
| 본문 `textarea[name=body]` | `common/editor('body', preparedBody, 'tabindex=3', 'content-body', false)` | 항상 | 입력 | `body`(필수 파라미터) | | | `create.html:37` |
| 첨부 업로더 | `common/file-uploader` | 위 조건 | 파일을 임시 업로드 | 제출 필드 `temporaryUploadFiles`(쉼표 목록) | 글 저장 뒤 글로 옮겨짐 | | `create.html:43` |
| 공지 체크박스 `#notice[name=notice]` | 체크박스 | `allowedToNotice and !hasIssueTemplate and !hasPath` | | `notice=on` | 서버는 공지 권한(`isProjectResourceCreatable(BOARD_NOTICE)`)이 있을 때만 값을 저장하고 없으면 `false`로 저장한다(`BoardWriteController.kt:100`, `:226-227`) | | `create.html:49-52` |
| 숨은 `issueTemplate`, `branch`, `path`, `lineEnding` | 숨은 입력 | 항상 | | `issueTemplate=true`면 `ISSUE_TEMPLATE.md` 커밋 | | | `create.html:54-57` |
| README 체크박스 `#readme[name=readme]` | 체크박스 | `canCommit and isGit and readmeRequested` | 체크된 채 열림 | `readme=on` | | | `create.html:58-63` |
| 저장 `button.ybtn-success` | 제출 | 항상 | 폼 제출 전에 `board.Write`가 제목을 검사 | `POST {base}/posts` 멀티파트 | 아래 7절 | `@IsCreatable(BOARD_POST)` | `create.html:67`, `board.Write.js:76-89` |
| 취소 | 링크 `javascript:history.back();` | 항상 | 이전 화면으로 | 없음 | | | `create.html:68` |
| 키맵 단추 | | | | | | | 이 화면에는 없다(`help/keymap` 조각 없음) |

### 4. 스크립트 동작

- 모듈: `lib/title-input/title-input.min.js`, `esm/TitleHeadAutoCompletion.js`, `esm/service/board.Write.js`.
- `$yona.loadModule("board.Write", {sMode:"new", elTextarea:'textarea[data-editor-mode="content-body"]'})`(`create.html:82-85`). 모듈은 시작 때 제목 입력란에 초점을 주고, 업로더(`yona.Files.getUploader`)와 `yona.Attachments`를 연결하고, `form` 제출 처리기를 단다.
- 제출 처리(`board.Write.js:76-89`): `#title` 값이 `""`이면 `$yona.showAlert(post.error.emptyTitle)` 후 제목에 초점, 제출 취소. 통과하면 `beforeunload` 경고를 끄고 현재 경로의 임시 저장 초안을 지운다(`removeCurrentPageTemprarySavedContent`).
- 이탈 경고: 본문 입력란에 초점이 생기면 `beforeunload`를 걸고, 본문이 비어 있지 않으면 `post.error.beforeunload` 문구를 보인다(`:62-71`).
- 임시 저장: `temporarySaveHandler`(위 글 보기 절). 키는 `location.pathname`(= `.../postform`).
- 온라인 커밋(`hasPath`) 전용(`create.html:93-112`): 본문이 비어 있지 않으면 텍스트 영역 높이를 내용에 맞게 늘림(최소 300px), `.project-menu-gruop > li`의 `active`를 지우고 `.code-menu`에 준다(탭 표시를 코드로).
- `yona.Mention({target:'textarea[id^=editor-]', url:{base}/mentionList})`(`create.html:87-90`).
- 폴링, 지연 로딩: 없다.

### 5. 단축키

이 화면에 등록하는 키는 없다. 전역 키는 입력란에서는 동작하지 않는다.

### 6. 팝업, 모달, 툴팁

- `$yona.showAlert`(빈 제목, 확인 후 초점). 그 밖의 모달은 없다.

### 7. 요청과 응답

`POST {base}/posts` (멀티파트). 필드: `title`, `body`, `notice`, `readme`, `issueTemplate`, `branch`, `path`, `lineEnding`, `new-file-name`(무시), `temporaryUploadFiles`.

| 조건 | 응답 | 근거 |
|---|---|---|
| 익명 또는 만들기 권한 없음 | 403 `error.forbidden`(프로젝트 틀) | `ProjectCheckStage.kt:169-173` |
| `title`이 비었음 | 400, `board/create`를 `errors={"title":["error.required"]}`로 다시 그림. 제출한 제목과 본문이 입력란에 되돌아온다 | `:199-206`, `:97` |
| `title` 길이 255 초과 | 400, `error.maxLength` | `:203` |
| `body` 파라미터 없음 | 413 `error/request-text-entity-too-large` | `:98` |
| 정상(일반 글) | 303 → `{base}/post/{새 번호}`. 임시 첨부를 못 옮기면 flash 제목/설명 | `:119-121` |
| `readme` 체크 | 이미 README 글이 있으면 그 글을 고친다(수정 권한 필요, 없으면 403). 없으면 저장소에 `README.md`를 커밋(실패는 로그만)하고 글도 만든 뒤 **프로젝트 홈**으로 303 | `:102-106`, `:121` |
| `issueTemplate=true` | 저장소에 `ISSUE_TEMPLATE.md` 커밋, 글은 저장하지 않고 프로젝트 홈으로 303 | `:107-110` |
| `path`가 있고 로그인 사용자가 이 프로젝트 구성원 | 저장소에 커밋, `{base}/code/{브랜치}/{path}`로 303 | `:111-116` |
| `path`가 있으나 구성원이 아님 | 경로를 무시하고 일반 글로 저장 | `:111-119`, 시험 "경로가 있어도 구성원이 아니면 일반 글로 저장한다" |

오류 재그림 때 `createView`에 요청과 제출한 제목(`submittedTitle`)을 넘기므로 `readme`, `issueTemplate`, `path`, `branch` 상태와 사용자가 입력한 본문과 제목이 그대로 남는다(`:97`, `:229-251`)([board-F5](#발견-사항)).

### 8. 권한 행렬

| 역할 | 작성 화면 | 공지 체크박스 | README/경로 커밋 |
|---|---|---|---|
| 비로그인 | 로그인 폼으로 | 없음 | 없음 |
| 로그인 비멤버 | 공개 프로젝트만(비공개 403) | 없음 | `path`가 있으면 폼 단계에서 `COMMIT` 만들기 권한이 없어 403 |
| 멤버, 그룹(조직) 구성원 | 예 | 예 | 예 |
| 사이트 관리자, 조직 관리자 | 예 | 예 | 예 |

근거: `AccessControl.isProjectResourceCreatable`(`:65-77`, `PUBLIC_CREATABLE_TYPES`에 `BOARD_NOTICE`, `COMMIT`는 없다). 공지 체크박스를 서버가 검사하지 않는 점은 [board-F2](#발견-사항).

### 9. 경계 값

- 제목 `maxlength=250`(HTML)과 서버 한도 255(`BoardWriteController.kt:271`)가 다르다.
- 시험: `BoardWriteTest`(작성 폼, 공지 선택, README, 이슈 템플릿, 경로, 로그인, 작성, 400, 413, 403, 첨부).

## 화면 4: 글 수정 (`board/edit.html`)

### 1. 화면 개요

- `editPostForm` (`BoardWriteController.kt:126-137`, `@NullProjectCheck`): 프로젝트가 없으면 404, 글이 없으면 404(`error.notfound`, 프로젝트 틀), 읽기 권한이 없으면 403. README 글이면 본문은 저장소 README. 이 화면은 **읽기 권한만 있어도 열린다.**
- 모델: `title`(`post.modify`), `projectView`, `errors`, `number`, `postId`, `titleValue`, `bodyValue`, `notice`, `readme`, `allowedToNotice`, `canCommit`, `isGit`, `canUpdate`, `authoredByViewer`(`:253-268`).
- `allowedToNotice`는 `allowedToNotice(viewer, project)`로 작성 화면과 같은 `isProjectResourceCreatable(BOARD_NOTICE)`(사이트 관리자, 조직 관리자, 직접 구성원, 그룹 구성원)이다(`:226-227`, [board-F4](#발견-사항)). 오류 재그림(400)은 같은 판정을 컨트롤러에서 직접 계산해 넘긴다(`:154`).

### 2. 레이아웃 계약

작성 화면과 같은 틀. 제목에는 `<label for="title">`이 있다(`edit.html:19-21`). 제목은 일반 `input#title.form-control`이고 `title-input` 사용자 정의 요소가 아니다(`:23`), 제목 머리말 자동 완성도 없다. 업로더 `fileUploader('BOARD_POST', postId)`는 로그인한 사용자에게만(`:34-36`).

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한(서버) | 근거 |
|---|---|---|---|---|---|---|---|
| 제목 `#title[name=title]` | 입력란 | 항상 | `maxlength=250`, `autocomplete=off` | `title` | 오류 메시지는 제목 아래 | | `edit.html:23-26` |
| 본문 `textarea[name=body]` | 편집기 | 항상 | | `body` | | | `edit.html:29` |
| 첨부 업로더 | | 로그인 | | `temporaryUploadFiles` | | | `edit.html:35` |
| 공지 `#notice` | 체크박스 | `allowedToNotice`. 체크 상태는 현재 `notice` | | `notice=on` | 체크 해제 상태는 값이 오지 않아 공지 해제 | 서버는 공지 권한이 있을 때만 값을 따르고, 없으면 보낸 값을 무시하고 지금 값을 유지한다 | `edit.html:40-43`, `BoardWriteController.kt:167` |
| README `#readme` | 체크박스 | `isGit and canCommit` | | `readme=on` | 체크하면 `README.md`를 저장소에 커밋하고 다른 README 글의 표시를 푼다 | `canCommit`은 화면 조건일 뿐 서버는 `commitRootFile`을 시도하고 실패는 로그만(`:169-172`) | `edit.html:45-48` |
| 알림 메일 `#notificationMail` | 체크박스(기본 체크, `value=yes`) | `authoredByViewer and !readme`(글이 README 글이면 숨김) | | `notificationMail=yes` | 체크하지 않으면 작성자가 직접 고쳤을 때 수정 활동(알림)을 발행하지 않는다. 작성자가 아니면 항상 발행(시험 "알림 메일을 고르거나 작성자가 아니면 수정 활동을 발행한다") | | `edit.html:52-57`, `BoardWriteController.kt:173` |
| 저장 | 제출 `ybtn-info` | `canUpdate` | 제출 | `POST {base}/post/{n}/edit` 멀티파트 | 아래 7절 | | `edit.html:58` |
| 취소 | 링크 `javascript:history.back();` | 항상 | | | | | `edit.html:59` |

수정 권한이 없는 사용자에게는 저장 단추가 없고 입력란은 그대로 열려 있다(읽기 전용이 아니다). 글 보기의 "원본 보기" 아이콘이 이 화면으로 온다.

### 4. 스크립트 동작

- `board.Write`를 `sMode:"edit"`로(제목 Enter 이동, 제출 검사, 이탈 경고, 임시 저장은 작성 화면과 같다).
- `yona.Mention({url:{base}/mentionList?number={n}&resourceType=board_post})`(`edit.html:76-79`).
- 임시 저장 키는 `location.pathname`(= `.../post/{n}/editform`).

### 5. 단축키, 6. 팝업

없음(키맵 조각 없음). 빈 제목 `showAlert`는 작성 화면과 같다.

### 7. 요청과 응답

`POST {base}/post/{n}/edit`:

| 조건 | 응답 | 근거 |
|---|---|---|
| 글 없음 | 404 (프로젝트 틀) | `:148-149` |
| 글을 읽을 수 없음 | 403. **입력 검사보다 먼저** 판정한다 | `:151` |
| 제목 비었음 또는 255 초과 | 400. `board/edit`을 제출한 제목, 본문으로 다시 그림(`body`나 `title`을 보내지 않았으면 저장된 값) | `:152-157` |
| 수정 권한 없음(익명 포함) | 403 | `:158`, `:164` |
| `body` 파라미터 없음 | 413 | `:165` |
| 정상 | 303 → `{base}/post/{n}`(+ 첨부 flash) | `:175` |

### 8. 권한 행렬

| 역할 | 수정 화면 | 저장 단추 | 서버 저장 |
|---|---|---|---|
| 비로그인 | 읽을 수 있으면 열림 | 없음 | 403 |
| 로그인 비멤버(공개) | 열림 | 없음 | 403 |
| 멤버 | 열림 | 있음 | 허용(남의 글도) |
| 작성자 | 열림 | 있음 | 허용 |
| 관리자 | 열림 | 있음 | 허용 |

### 9. 시험

`BoardWriteTest` "수정 폼의 값과 단추", "수정할 수 없으면 저장 단추가 없고 알림 메일 선택은 작성자만 있다", "수정 폼의 없는 글은 404이고 읽을 수 없으면 403이다", "수정하면 값과 이력이 바뀌고 글로 돌아온다", "공지를 해제할 수 있다", "본문이 같으면 이력을 쌓지 않는다", "수정 오류와 권한".

## 조직 게시판과의 연결

`/organizations/{name}/boards`(`OrganizationBoardController.kt:34`, 템플릿 `organization/board-list`, 이 영역 밖)는 `board/partial-list :: item(post, base)`를 줄마다 호출하고 `base`를 줄의 프로젝트로 넘긴다. 이 조각의 글 링크는 `@{${base + '/post/' + post.number}}`로 컨텍스트 경로 접두어가 붙는다(`partial-list.html:10`, `:22`). 조직 게시판의 정렬 링크는 검색어(`filter`)와 고른 프로젝트(`projectNames[]`)를 이어 받는다(`OrganizationBoardController.kt:52-58`, organization-F17).

## 발견 사항

| 번호 | 내용 | 근거 |
|---|---|---|
| board-F1 **[브라우저로 확인됨, v1.6 결함 유지, R-151]** | 글 보기의 키맵 모달 제목이 `title.boardDetail`인데 모든 `messages*.properties`에 없다. `use-code-as-default-message: true`(`application.yml:88`)라서 모달 머리에 키 이름 `title.boardDetail`이 그대로 보인다(`title.boardList`, `title.issueDetail`, `title.issueList`는 있다). v1.6의 `conf/messages`에도 같은 키가 없다(grep) | `help/keymap.html:48`, `board/view.html:128` |
| board-F2 **[재현됨, 수정됨, A-459]** | 글 작성 서버는 `notice`를 권한 없이 저장했다(공식 v1.16.0 동작). 로그인한 비멤버가 폼 값을 직접 보내면 공지가 되었다. 수정(`edit`)도 같았다. (2026-11-11 수정: 작성은 `checked("notice") && allowedToNotice`로 권한이 없으면 공지로 저장하지 않고, 수정은 권한이 없으면 보낸 값을 무시하고 저장된 `notice`를 유지한다) | `BoardWriteController.kt:100`, `:167` |
| board-F3 **[수정됨, A-457]** | 글 보기의 라벨 변경 처리기는 select2 3.x의 `e.val`을 읽었다(`JSON.stringify(e.val)`). 로드하는 select2는 4.1.0이라 `e.val`이 `undefined`여서 본문 없는 JSON 요청이 가고 400 `Expecting Json data`가 된다는 것이 코드상의 추론이었다(추측). (2026-11-11 수정: 처리기가 `JSON.stringify($(this).val() || [])`를 보낸다.) 서버 시험(`BoardCommentTest` "글의 라벨을 바꾼다")은 API만 검증한다 | `view.html:186-197`, `BoardApiController.kt:55-71` |
| board-F4 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 공지 체크박스 표시 기준이 화면마다 달랐다. 작성 화면은 `isProjectResourceCreatable(BOARD_NOTICE)`, 수정 화면은 `isSiteManager or 직접 구성원`이었다. (2026-11-11 수정: `allowedToNotice`가 `isProjectResourceCreatable(BOARD_NOTICE)` 하나로 통일되어 작성, 수정 화면, 저장 판정이 같다) | `BoardWriteController.kt:226-227`, `:248` |
| board-F5 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 글 작성 검증 실패(400)는 `createView(..., request=null)`로 다시 그려서 사용자가 입력한 본문, 제목과 `readme`, `issueTemplate`, `path`, `branch` 상태를 잃었고, 수정 화면 오류는 저장된 제목과 본문을 보였다. (2026-11-11 수정: 작성은 요청과 제출한 제목, 본문을 되돌려 주고, 수정은 제출한 `title`, `body`를 되돌려 준다) | `BoardWriteController.kt:97`, `:156`, `:229-251` |
| board-F6 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `board.List.js`의 `_onClickPage`는 도달하지 못하는 코드이다. `welPages`를 `Pagination.update`가 링크를 만들기 전에 잡아 비어 있고 폼에도 `pageNum` 입력이 없다. 실제 이동은 `Pagination.js`가 만든 `<a href>`와 번호 입력 Enter(`document.location.href`)로 한다 | `board.List.js:78`, `:114-116`, `Pagination.js:122-159` |
| board-F7 **[수정됨, A-457]** | `error.maxLength`는 `messages-play.properties`에만 있고(영어 `Maximum length is {0}`) 템플릿이 `#{${error}}`로 인자 없이 출력해서 제목이 255자를 넘으면 `{0}`이 그대로 보인다. `error.required`도 영어(`This field is required`)로만 나온다(한국어 `messages_ko_KR`에 없음). 또한 서버 한도 255와 입력란 `maxlength=250`이 달라 UI로는 255 초과 오류에 닿기 어렵다 | `create.html:24-26`, `edit.html:24-26`, `messages-play.properties:6`, `:12` |
| board-F8 **[재현됨, 수정됨, A-459]** | `POST /{owner}/{project}/post/{n}/edit`는 `@NullProjectCheck`만 붙어 프로젝트 읽기 권한을 확인하지 않아 제목을 비워 보내면 읽기 권한 없는 사용자도 400 응답에 글의 저장된 제목과 본문이 담긴 수정 화면을 받았다(v1.6 `BoardApp.editPost`도 같은 순서, `BoardApp.java:341-345`). (2026-11-11 수정: 글 읽기 권한 검사를 입력 검사보다 먼저 두어 읽을 수 없으면 403이다) | `BoardWriteController.kt:139-158` |
| board-F9 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 지켜보기 해제 요청(`$yona.sendForm`, `Accept: */*`)은 JSON을 선호하지 않아 서버가 303으로 글 주소를 응답하고 fetch가 따라가 글 HTML 전체를 받는다. 첫 해제이면 flash `post.unwatch.start`가 그 응답에서 소비된다(추측: 화면에는 나타나지 않는다) | `WatchController.kt:50-62`, `yona.Common.js:312-352` |
| board-F10 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 글 목록 `글쓰기` 단추는 익명, 권한 없는 사용자, 게시판 메뉴가 꺼진 프로젝트에도 항상 보였고 `N` 키와 글 보기의 오른쪽 `글쓰기`는 `menuSetting.board`가 켜졌을 때만 동작했다. (2026-11-11 수정: 목록 단추도 `projectView.menuSetting.board`일 때만 보인다. 익명, 권한 없는 사용자에게는 여전히 보인다) | `list.html:31`, `:75-79`, `view.html:101` |
| board-F11 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 라벨 API는 `findAllById`로 라벨을 찾고 그 라벨이 이 프로젝트의 것인지 확인하지 않아 다른 프로젝트의 라벨 번호도 글에 붙일 수 있었다. (2026-11-11 수정: 이 프로젝트의 것이 아닌 라벨이 있으면 400 `Wrong label id`) | `BoardApiController.kt:55-71` |
| board-F12 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 댓글 삭제 `DELETE .../post/{n}/comment/{commentId}/delete`의 `{n}`은 댓글이 속한 글과 대조하지 않고 리다이렉트 주소에만 쓰였다. (2026-11-11 수정: 주소의 소유자, 프로젝트 이름, 글 번호가 댓글의 것과 다르면 404) | `BoardCommentController.kt:86-93` |
| board-F13 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 댓글 수정 아이콘은 `c.canRead` 기준으로 보여서 수정 권한이 없는 로그인 사용자도 수정 폼을 열 수 있고 저장하지 못했다. (2026-11-11 수정: 아이콘이 `c.canUpdate`일 때만 보인다) | `partial-comments.html:38`, `common/comment-update-form.html` |
| board-F14 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | `AccessControl.kt`의 `isAllowedIfAuthor`는 `BOARD_POST`가 `AUTHOR_TYPES`에 있어서 글 작성자가 프로젝트 구성원이 아니게 되어도 자기 글을 고칠 수 있었다(읽기와 삭제는 막히는데 수정만 열림). (2026-11-11 수정: 작성자 몫은 프로젝트가 공개이거나 직접 구성원이거나 조직 구성원 규칙을 만족할 때만 준다. `IsAllowed`는 프로젝트 읽기를 먼저 요구한다) | `AccessControl.kt:195-198`, `:313-316`, `ProjectCheckStage.kt:66-74`, `:177-186` |
| board-F15 **[검증: v2 신규 결함, 수정됨 A-460]** | `view.html`의 변경 이력 링크(익명)는 `href`가 로그인 주소인데 `data-bs-toggle="modal"`이 함께 붙어 있었다. (2026-11-11 수정: 익명 링크에서 모달 속성을 뺐다) | `view.html:44-46` |
| board-F16 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `create.html`, `edit.html`의 취소 링크와 `form action`은 `javascript:history.back()`, 편집 화면의 `tabindex`는 저장이 3, 취소가 4인데 편집기의 `tabindex=3`과 겹친다(작성 화면). 동작에는 영향이 없다 | `create.html:37`, `:67-68` |
| board-F17 **[재조사 2026-11-11]** | 글 보기의 수정 아이콘 두 곳은 `onclick`에 `window.location = '${base}/post/{n}/editform'`을 쓰고, 본문 체크리스트 폼의 `action`은 `'/' + owner + '/' + project + '/post/' + n + '/content'`이다. 둘 다 `@{}`로 감싸지 않아 컨텍스트 경로 접두어가 붙지 않는다(`#paths.project`는 접두어를 붙이지 않는다, `TemplatePaths.kt:21`). 같은 화면의 나머지 링크는 `@{}`이다. 체크리스트의 `Tasklist.js`는 이 `action`을 그대로 PATCH 주소로 쓴다 | `view.html:56`, `:78`, `:116`, `Tasklist.js:32` |

## 범위 점검

| 템플릿 | 다룬 절 |
|---|---|
| `board/list.html` | 화면 1 |
| `board/partial-list.html` | 화면 1(줄 요소), 조직 게시판 절 |
| `board/view.html` | 화면 2 |
| `board/partial-comments.html` | 화면 2 |
| `board/create.html` | 화면 3 |
| `board/edit.html` | 화면 4 |

조사한 템플릿 6개 / 담당 6개. 이 영역에서 따라간 스크립트: `esm/service/board.List.js`, `board.View.js`, `board.Write.js`, `esm/service/twoColumnMode.js`, `esm/Pagination.js`, `esm/Request.js`, `esm/Comment.js`, `esm/ShortcutKey.js`, `esm/service/temporarySaveHandler.js`, `common/yona.Csrf.js`, `common/yona.Common.js`(`sendForm`). 편집기, 업로더, 마크다운, 멘션, 제목 입력 요소, 라벨 선택 조각, 댓글 폼 조각은 `shell`/`common` 담당이므로 사용처와 설정값만 적었다.

## 갱신 기록 (2026-11-11 재조사)

- 다시 확인한 템플릿 6개: `board/list.html`, `partial-list.html`, `view.html`, `partial-comments.html`, `create.html`, `edit.html`. 함께 `help/keymap.html`, `organization/board-list.html`(정렬 링크 연결), `common/child-comments.html`, 컨트롤러 `BoardWriteController`, `BoardCommentController`, `BoardApiController`, `OrganizationBoardController`, 접근 단계(`AccessChainInterceptor`, `AnonymousCheckStage`, `ProjectCheckStage`), `AccessControl`, `esm/Request.js`, `ShortcutKey.js`, `temporarySaveHandler.js`를 대조했다.
- 내용을 고친 행과 문장: 약 36(글쓰기 단추 조건, 쓰기 권한 검사 순서, 공지 선택, 오류 때 본문 유지, 익명 변경 이력 링크, 댓글 수정 아이콘 조건, 댓글 삭제 소속 확인, 라벨 API의 프로젝트 확인, 실패 때 알림, 단축키 안내 조건, 로그인 이동 때 질의 문자열, 작성자 권한 조건, 발견 사항 F2, F3, F4, F5, F8, F10~F15의 수정 표기).
- 줄 번호만 고친 곳: 약 22(`BoardWriteController`, `BoardApiController`, `AccessControl`, `ProjectCheckStage`, `AnonymousCheckStage`, `AccessChainInterceptor`, `Pagination.js`, `ShortcutKey.js`, `RedirectSupport` 인용).
- 더한 행: 접근 검사 순서 표의 메뉴 꺼짐 안내, 글 보기 권한 행렬의 작성자 몫 행, 발견 사항 board-F17.
- 없앤 행: 없음(옛 동작을 적은 문장은 현재 동작으로 바꿨다).
- 새 발견 사항: board-F17(수정 아이콘 `onclick`과 체크리스트 폼 `action`에 컨텍스트 경로 접두어 없음).
