---
title: UI 계약 - 프로젝트 위키 (wiki)
type: ui-contract
sources:
  - src/main/resources/templates/wiki/view.html
  - src/main/resources/templates/wiki/shell.html
  - src/main/resources/templates/wiki/empty.html
  - src/main/resources/templates/wiki/edit.html
  - src/main/resources/templates/wiki/history.html
  - src/main/resources/templates/wiki/compare.html
  - src/main/kotlin/dev/yona/web/wiki/WikiViewController.kt
  - src/main/kotlin/dev/yona/domain/wiki/WikiService.kt
  - src/main/kotlin/dev/yona/domain/wiki/WikiLinks.kt
  - app/assets/stylesheets/scss/_wiki.scss
updated: 2026-11-11
---

# UI 계약: 프로젝트 위키 (`wiki/`)

v1.6에 없는 추가 화면이다(로드맵 X-17, D-60, D-61). 설계는 [architecture/project-wiki.md](../architecture/project-wiki.md)에 있고, 여기에는 화면 동작 계약을 적는다. 접근 검사 순서와 요청 공통 규칙은 [board.md의 공통 규칙](board.md#공통-규칙)과 같다(CSRF, 303 이동, 반응형).

## 영역 요약

| 템플릿 | URL (메서드) | 컨트롤러 (`web/wiki/WikiViewController.kt`) | 용도 |
|---|---|---|---|
| `wiki/view.html` | `/{user}/{project}/wiki` (GET, Home), `/{user}/{project}/wiki/{page}` (GET) | `home` `:81`, `view` `:90` | 페이지 보기 |
| `wiki/empty.html` | `/{user}/{project}/wiki` (GET, Home 페이지 없음) | `home` `:81-88` | 빈 위키 안내 |
| `wiki/edit.html` | `/wiki/_new` (GET) `:148`, `/wiki/{page}/_edit` (GET) `:177`, 제출은 `POST /wiki` `:158`, `POST /wiki/{page}/_edit` `:188`, 실패하면 이 화면을 4xx로 다시 그림 | `newForm`, `create`, `editForm`, `edit` | 페이지 만들기와 고치기, 삭제, 파일 첨부 |
| `wiki/history.html` | `/wiki/{page}/_history` (GET) | `history` `:99` | 리비전 목록과 비교할 두 개 고르기 |
| `wiki/compare.html` | `/wiki/{page}/_compare` (GET) | `compare` `:120` | 통합 diff |
| `wiki/shell.html` | (조각 `sidebar`, `footer`) | 모든 위키 화면이 쓴다 | 오른쪽 `Pages` 사이드바, 복제 주소, 푸터 |

템플릿 없는 경로: `POST /wiki/{page}/_delete` `:209`, `POST /wiki/_upload` `:235`(JSON), `GET /wiki/_uploads/{file}` `:269`(파일).
이 경로들의 `{user}/{project}` 앞은 모두 같다. 경로 변수 `{page}`에는 `_new`, `_upload`, `_uploads`가 올 수 없는 리터럴이 우선한다(스프링은 리터럴 경로를 먼저 고른다).

## 공통 규칙

- 접근 어노테이션: 읽기 메서드는 `@IsAllowed(Operation.READ)`(프로젝트 읽기) 다음 클래스 `@AnonymousCheck`. 만들기 폼, 만들기, 고치기 폼, 고치기, 삭제(`_new`, `POST /wiki`, `_edit` GET과 POST, `_delete`)는 메서드에 `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)`가 먼저 붙고 그다음 `@IsAllowed(READ)`이다(`WikiViewController.kt:149`, `:159`, `:178`, `:189`, `:210`). 쓰기 권한은 컨트롤러가 `deny(project)`로 따로 막는다(`:302-308`). `_upload`에는 `@AnonymousCheck(requiresLogin)`이 없다.
- 위키가 꺼져 있으면(`ProjectMenuSetting.wiki != true`) 모든 위키 경로가 `ErrorViews.notFound()` 404이다(`wikiProject`, `:294-296`, `WikiService.enabled`). 프로젝트 메뉴의 위키 탭도 `menuSetting.wiki == true`일 때만 보인다(`project/menu.html:52`).
- 쓰기 권한: `ResourceType.WIKI_PAGE`의 `UPDATE`(`canWrite`, `:302-305`). 규칙은 사이트 관리자, 조직 관리자, 프로젝트 관리자, 직접 구성원, 그룹 구성원이다(`AccessControl.kt:106-117`, `:175-226`). **익명이 쓰기 경로(`_new`, `POST /wiki`, `_edit`, `_delete`)를 요청하면 로그인 폼으로 303**(`/users/loginform?redirectUrl=<경로>`, `user.login.alert` 안내, GET이면 질의 문자열도 값에 실린다, `AnonymousCheckStage.kt:25-36`)이다. 로그인한 비멤버는 403 `error.forbidden` 화면을 받는다(`:308`). `POST /wiki/_upload`는 익명도 로그인 이동 없이 403 JSON이다(7절).
- 모든 위키 화면의 공통 모델(`shell`, `:314-329`): `title`, `projectView`, `base`(위키 주소, 컨텍스트 경로를 이미 포함한다, `wikiBase`는 `siteUrls.path(...)`, `:298`), `canWrite`, `activeName`, `cloneUrl`, `sshCloneUrl`, `pages`, `sidebarHtml`, `footerHtml`. 문서 제목(`title`)은 보는 사람의 언어로 만든다(`text(key)`, `:312`): 빈 위키 `menu.wiki`, 만들기 `wiki.page.new`, 고치기 `wiki.page.edit` + 공백 + 페이지 제목, 이력 `페이지 제목 - wiki.history.title`, 비교 `페이지 제목 - wiki.compare.title`, 보기 화면은 페이지 제목. 한국어는 `위키`, `새 페이지`, `편집 X`, `X - 리비전`, `X - 리비전 비교`.
- 페이지 이름은 제목의 공백을 `-`로 바꾼 값이다(`WikiService.nameOf`). 제목 규칙은 비어 있지 않음, 200자 이하, `\ / : * ? " < > |`와 제어 문자 금지, 점만으로 이뤄진 이름 금지, `_`로 시작하는 이름은 `_Sidebar`와 `_Footer`만 허용(`WikiService.kt:451-466`). 사이드바 목록에서 이 두 페이지는 빼고(`:86-100`) 본문 오른쪽 사이드바와 아래 푸터 영역에 렌더링한다.
- 마크다운 렌더링: `[[Page]]` 위키 링크를 풀고 렌더링한 뒤 상대 링크를 위키 주소로 바꾼다(`render`, `:331-335`, `WikiLinks`). 본문은 `th:utext`(서버가 렌더한 HTML)이다.

## 화면 1: 페이지 보기 (`wiki/view.html`) 와 빈 위키 (`wiki/empty.html`)

### 1. 화면 개요

- `home`: `Home` 페이지가 있으면 `pageView`, 없으면 `wiki/empty`(+ `available`, 문서 제목 `menu.wiki`). `view`: 없는 페이지는 `ErrorViews.notFound("wiki.page.notFound", 프로젝트 틀, null)` 404.
- `pageView` 모델: `pageTitle`, `pageUrl`, `bodyHtml`, `lastAuthor`, `lastDate`(`helper.agoOrDateString`). 틀: `layout/project-layout(..., 'WIKI')`, `projectMenu(..., 'WIKI', 'main-menu-only')`, `common/markdown`(`view.html:11-32`).

### 2. 레이아웃 계약

- `.project-page-wrap.wiki-wrap > .row`: 왼쪽 `col-lg-9`(머리, 마지막 편집, 본문, 푸터), 오른쪽 `col-lg-3`(사이드바). 992px 미만에서는 두 열이 위아래로 쌓인다(Bootstrap `lg`). `hide-in-mobile` 같은 별도 규칙은 없다.
- 머리 `.wiki-page-header`: 제목 `h2.wiki-page-title`(1.75rem, `_wiki.scss:3-5`)과 단추 줄(`d-flex flex-wrap`).
- 마지막 편집 줄은 `lastAuthor != null`일 때만(`view.html:22`). 푸터 `_Footer`는 `footerHtml != null`일 때만, 위쪽 테두리와 함께(`shell.html:72-74`).
- 빈 위키(`empty.html`): 중앙 정렬 `h3 wiki.empty.title`, `available`이 거짓이면 `wiki.error.unavailable`, 만들기 링크는 `canWrite and available`일 때만(`empty.html:16-20`). 이때도 오른쪽 사이드바는 그려진다(페이지 목록은 비어 `wiki.pages.empty`).

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한(서버) | 근거 |
|---|---|---|---|---|---|---|---|
| 편집 `.wiki-edit` | 링크 | `canWrite` | 고치기 화면 | `GET {페이지}/_edit` | 200 폼 | 비멤버 403 | `view.html:18` |
| 새 페이지 `.wiki-new` | 링크 | `canWrite` | 만들기 화면 | `GET {base}/_new` | 200 폼 | 비멤버 403 | `view.html:19`(`${base + '/_new'}`. `base`가 이미 컨텍스트 경로를 포함하므로 `@{}`로 다시 감싸지 않는다, [wiki-F9](#발견-사항) 수정함) |
| 이력 `.wiki-history` | 링크 | **항상**(읽을 수 있으면 누구나) | 이력 화면 | `GET {페이지}/_history` | | 읽기 | `view.html:20` |
| 첫 페이지 만들기 `.wiki-create-first` | 링크 | `canWrite and available` | `Home` 제목을 채운 만들기 폼 | `GET {base}/_new?title=Home` | | 구성원 | `empty.html:19`(`${base + '/_new?title=Home'}`, [wiki-F9](#발견-사항) 수정함) |
| 본문 안 링크 | 링크 | | 위키 링크는 위키 주소로, 없는 페이지의 `[[...]]`는 만들기로 간다(시험 "대괄호 두 겹 링크는 페이지 링크가 되고 없는 페이지는 만들기로 간다") | | | | `WikiLinks` |
| 사이드바 | `shell.html` 조각 | 아래 | | | | | |

### 4. 사이드바와 복제 상자 (`wiki/shell.html`)

| 요소 | 종류 | 조건 | 동작 | 근거 |
|---|---|---|---|---|
| `.wiki-pages-filter` | `input[type=search]` | 항상 | 입력할 때마다(`input`) 목록을 걸러 낸다. 검사는 제목 글자 포함, 대소문자 무시, 서버 요청 없음. 거르는 중에는 15개 제한과 "더 보기" 단추를 무시한다 | `shell.html:10`, `:35-48`, `:66` |
| 페이지 목록 | `ul.wiki-pages-list` | 목록이 비면 `wiki.pages.empty` 한 줄 | 15개까지 보이고 16번째부터 `wiki-page-extra d-none`. 현재 페이지는 `a.active`(굵게). 제목순(대소문자 무시) | `shell.html:11-17`, `WikiService.kt:86-100` |
| `.wiki-pages-more` | 단추 | 페이지 수가 15를 넘을 때 | 누르면 전부 펼치고 단추를 숨긴다 | `shell.html:17`, `:67` |
| 사용자 지정 사이드바 | `.wiki-custom-sidebar` | `_Sidebar` 페이지가 있을 때 | 렌더 결과 표시 | `shell.html:18` |
| 복제 상자 `.wiki-clone` | | `cloneUrl != null` | 제목 `wiki.clone.title` | `shell.html:19-31` |
| HTTP/SSH 전환 `.wiki-clone-protocol` | 단추 둘 | `sshCloneUrl != null`(내장 SSH나 시스템 sshd 연동을 켠 사이트, D-68) | 누르면 `selected` 클래스를 옮기고 주소 입력란의 값을 `data-clone-url`로 바꿈 | `shell.html:22-25`, `:49-55` |
| `.wiki-clone-url` | 읽기 전용 입력 | | 클릭하면 전체 선택(`onclick="this.select()"`) | `shell.html:27` |
| `.wiki-clone-copy` | 단추 | | 주소를 선택하고 `navigator.clipboard.writeText`(없으면 `execCommand("copy")`) | `shell.html:28`, `:56-65` |
| `.wiki-clone-feedback` | `span[role=status]` | 복제 상자 안 | 복사 결과를 보인다. 성공은 `wiki.clone.copied`(`text-success`), 실패는 `wiki.clone.copyFailed`(`text-danger`). `clipboard.writeText`가 거부되면 `execCommand("copy")`의 결과로 다시 판단한다. 복사 단추를 누르기 전에는 비어 있다 | `shell.html:29`, `:59-60`, `:63-64` |

- 스크립트는 `shell.html` 안 인라인(`:33-69`)이고 `window`에 전역을 만들지 않는 즉시 실행 함수이다. 로컬 저장소, 쿠키, 폴링은 쓰지 않는다. 조각이 한 화면에 한 번만 있다고 가정하고 `document.querySelector`를 쓴다.
- `cloneUrl`은 `RepositoryUrls.wikiUrlFor(project, request, 로그인 ID)`(`:323`)이고 로그인 ID가 있으면 주소에 포함될 수 있다(`dev.yona.web.project.RepositoryUrls`는 이 영역 밖).

### 5. 단축키, 6. 팝업

없음. 키맵 조각(`help/keymap`)도 위키에는 없다. 이 화면의 입력란에 초점이 있어도 전역 단일 키는 `ShortcutKey`가 무시한다(INPUT, TEXTAREA).

### 7. 요청과 응답

| 요청 | 응답 |
|---|---|
| `GET /wiki` | 200 보기 또는 빈 위키 안내. 위키 꺼짐 404 |
| `GET /wiki/{page}` | 200. 이름 규칙에 어긋나거나 없으면 404 `wiki.page.notFound` |

### 8. 권한 행렬

| 역할 | 보기, 이력, 비교, 첨부 내려받기 | 편집, 새 페이지 단추 | 서버(쓰기) |
|---|---|---|---|
| 비로그인 | 공개 프로젝트이고 익명 허용일 때 | 없음 | 403 |
| 로그인 비멤버 | 공개 프로젝트 | 없음 | 403 |
| 멤버, 그룹 구성원 | 예 | 예 | 허용 |
| 프로젝트 관리자, 조직 관리자, 사이트 관리자 | 예 | 예 | 허용 |

### 9. 시험

`WikiViewTest`(빈 위키, 위키 탭, 만들기, Home, 한글과 공백 제목, 404, 쓰기 단추 가시성, 사이드바 제목순과 현재 페이지 강조, 위키 링크, 복제 주소, 비공개 프로젝트), `WikiToggleTest`, `WikiSshCloneTest`, `WikiEditorHeightTest`.

## 화면 2: 만들기와 고치기 (`wiki/edit.html`)

### 1. 화면 개요

- 만들기 `newForm`(`:148-156`): 쿼리 `title`(선택)로 제목을 채운다. 고치기 `editForm`(`:177-186`): 현재 제목과 내용을 채운다. 익명은 로그인 폼으로 303(공통 규칙), 두 경우 `deny`로 로그인한 비멤버는 403, 없는 페이지는 404.
- `form(...)`(`:350-364`) 모델: `editing`, `formAction`(만들기 `{base}`, 고치기 `{페이지}/_edit`), `deleteAction`(`{페이지}/_delete`), `uploadAction`(`{base}/_upload`), `cancelUrl`(고치기는 그 페이지, 만들기는 위키 첫 화면), `titleValue`, `contentValue`, `messageValue`, `errorKey`, 상태 코드.

### 2. 레이아웃 계약

- 왼쪽 열: 오류 알림(`errorKey != null`, `.wiki-error.alert.alert-danger`), 폼 `.wiki-form`(제목 입력, 편집기, 파일 첨부, 편집 메시지, 저장과 취소), 고치기일 때만 삭제 폼 `.wiki-delete-form`. 오른쪽 열은 사이드바.
- 편집창 높이는 `.wiki-form textarea.content { height: 300px }`(`_wiki.scss`, A-452). `.content-wrap` 밖이라 별도 규칙이다.

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한(서버) | 근거 |
|---|---|---|---|---|---|---|---|
| 제목 `input[name=title]` | 입력란 | 항상 | `maxlength=200`, `required`(브라우저 검사), `tabindex=1`. 도움말 `wiki.page.title.help`(공백은 하이픈). 입력하면 이탈 경고 대상이 된다 | 폼 필드 `title` | | | `edit.html:19-20`, `:80` |
| 본문 `textarea[name=content]` | `common/editor('content', contentValue, 'tabindex=2', 'content-body', false)` | 항상 | 입력하면 이탈 경고 대상이 된다 | `content` | | | `edit.html:23`, `:80` |
| 파일 선택 `.wiki-attach-input` | `input[type=file]` | 항상(고치기, 만들기 모두) | 파일을 고르면(`change`) 바로 업로드 | `POST {base}/_upload` 멀티파트 `file`, `Accept: application/json`, `credentials: same-origin` | 201 JSON `{name, path, markdown, url}`: 본문의 커서 위치에 `markdown`을 끼워 넣고 `input` 이벤트를 낸다. 응답이 2xx가 아니면(403 포함) `.wiki-attach-error`에 `wiki.attach.failed`를 보이고 어떤 경우든 파일 선택을 비운다 | 구성원 | `edit.html:27`, `:50-73` |
| 편집 메시지 `input[name=message]` | 입력란 | 항상 | `maxlength=250`, `tabindex=3`. 비면 서버가 기본 메시지 | `message` | 커밋 메시지가 된다 | | `edit.html:32` |
| 저장 `.wiki-save` | 제출 | 항상 | 제출 | 만들기 `POST {base}`, 고치기 `POST {페이지}/_edit` (urlencoded, 필드 `title`, `content`, `message`) | 성공 303 → 저장된 페이지(제목을 바꾸면 새 이름 주소). 실패 아래 7절 | 구성원 | `edit.html:35`, `:158-175`, `:188-207` |
| 취소 | 링크 | 항상 | `cancelUrl` | `GET` | | | `edit.html:36` |
| 삭제 `.wiki-delete` | 제출 | `editing` | 제출 전 `window.confirm(wiki.page.delete.confirm(제목))`. 취소하면 제출 중단 | `POST {페이지}/_delete` (필드 없음, 서버는 `message`를 선택 값으로 받는다) | 303 → 위키 첫 화면. 없는 페이지 404, 그 밖의 위키 오류 400 `wiki.error.conflict` | 구성원 | `edit.html:39-42`, `:85-89`, `:209-227` |

### 4. 스크립트 동작

- 인라인 스크립트 셋(업로드 `edit.html:50-73`, 이탈 경고 `:74-84`, 삭제 확인 `:85-89`)과 `common/markdown`, 편집기 조각의 스크립트. 업로드와 삭제 확인은 순수 `fetch`, `window.confirm`이며 `$yona`를 쓰지 않는다. CSRF는 `yona.Csrf.js`가 `fetch`와 폼 제출에 싣는다.
- 이탈 경고: 제목 입력란이나 본문 `textarea`에 `input` 이벤트가 한 번이라도 생기면 `dirty`가 되고, 그 뒤 창을 떠나려 하면 `beforeunload`로 브라우저 기본 확인창을 띄운다(글자는 브라우저가 정한다). 저장 폼(본문 `textarea`가 든 폼)이 제출되면 `submitting`이 켜져 경고하지 않는다. 삭제 폼 제출은 `submitting`을 켜지 않으므로 내용을 고친 뒤 삭제하면 경고가 뜬다(코드상의 추론, 브라우저로 확인하지 않았다). `window.location` 이동이 아닌 취소 링크 같은 일반 이동도 `dirty`이면 경고한다.
- `common/editor`가 제공하는 임시 저장(`temporarySaveHandler`)이 이 화면에서도 동작하는지는 이 영역에서 확인하지 않았다. `board.Write` 같은 화면 모듈을 부르지 않으므로 초안 복원과 제목 빈칸 `showAlert`는 없다(제목은 HTML `required`만).

### 5. 단축키, 6. 팝업

단축키 없음. 팝업은 삭제 확인 `window.confirm` 하나(브라우저 기본, Esc로 취소)와 이탈 경고 `beforeunload` 확인창이다.

### 7. 요청과 응답

| 요청 | 성공 | 실패 |
|---|---|---|
| `POST /wiki` | 303 → `{base}/{이름}` | 제목 규칙 위반 400 `wiki.error.title`, 같은 이름 있음 409 `wiki.error.exists`, 위키 저장소 이름 충돌 409 `wiki.error.unavailable`, 동시 수정 충돌 409 `wiki.error.conflict`. 모두 같은 폼을 **입력을 보존해서** 해당 상태 코드로 다시 그린다(`failure`, `:356-367`) |
| `POST /wiki/{page}/_edit` | 303 → 새 이름 주소 | 없는 페이지 404, 위와 같은 오류는 폼 재그림 |
| `POST /wiki/{page}/_delete` | 303 → `{base}` | 위 표 |
| `POST /wiki/_upload` | 201 JSON | 파일 없음 또는 빈 파일 400 `{"message":"file is required"}`, 이름 규칙 위반 400 `invalid file name`, 한도 초과 413 `file is too large`, 그 밖 위키 오류 409. 비멤버와 익명 403 JSON `{"message":"forbidden"}`(`WikiViewController.kt:245`) |
| `GET /wiki/_uploads/{file}` | 200 파일. 이미지(SVG 제외)만 `inline`, 나머지는 `attachment`. `X-Content-Type-Options: nosniff`, `Cache-Control: no-cache` | 없는 파일 404 |

경계 값: 제목 200자(`WikiService.MAX_TITLE`), 파일 이름 200자와 `.` 시작 금지, 첨부 크기는 `min(yona.max-file-size, 25MB)`(`WikiService.kt:420`, `:66-67`).

### 8. 권한 행렬

화면 1의 표와 같다. 업로드와 내려받기: 업로드는 구성원, 내려받기는 읽을 수 있는 누구나.

### 9. 시험

`WikiViewTest`: 새 페이지 화면은 구성원에게만, 잘못된 제목과 중복 제목은 폼을 다시 보이고 입력을 보존, 고치기, 제목 변경, 이미 있는 이름으로 변경, 비멤버 금지, 삭제, 첨부 업로드, 편집 화면에 파일 첨부 입력.

## 화면 3: 이력 (`wiki/history.html`)

### 1. 화면 개요

`history`(`:99-118`): 쿼리 `number`(0부터, 음수는 0, 기본 0)로 쪽을 고른다. 한 쪽에 30개(`HISTORY_SIZE`)이고 31개를 읽어 다음 쪽 유무를 판단한다. 모델: `pageTitle`, `pageUrl`, `compareAction`(`{페이지}/_compare`), `revisions`(`id`, `shortId`, `message`, `author`, `date`, `dateTitle`, `compareUrl`), `olderUrl`, `newerUrl`. 이름 규칙 위반은 404 `wiki.page.notFound`.

### 2. 레이아웃 계약

머리에 페이지 제목 링크와 작은 글자 `wiki.history.title`. 아래 `form.wiki-compare-form` 안 표(`table-sm`)와 이전, 다음 `nav`. 리비전이 없으면 한 줄 `wiki.history.empty`(`history.html:32`).

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 근거 |
|---|---|---|---|---|---|---|
| 페이지 제목 링크 | 링크 | 항상 | 그 페이지 | `GET {페이지}` | | `history.html:17` |
| 라디오 `from`, `to` (줄마다 한 쌍) | 라디오 | 리비전마다 | 기본: `to`는 첫 줄(최신), `from`은 둘째 줄 | 필드 `from`, `to` = 커밋 ID | | `history.html:25-26` |
| `Compare Revisions` | 제출 | 리비전이 2개 이상(`#lists.size(revisions) > 1`)일 때만 | 선택한 두 리비전을 비교 | `GET {페이지}/_compare?from=&to=` | 200 diff | `history.html:21` |
| 리비전 메시지 링크 `.wiki-revision-message` | 링크 | 줄마다 | 그 커밋 하나의 변경 | `GET {페이지}/_compare?commit={id}` | 200 diff | `history.html:27`, `WikiViewController.kt:379-383` |
| 최신 쪽 `Newer` / 이전 쪽 `Older` | 링크 | `newerUrl`(`number > 0`), `olderUrl`(30개 초과로 더 있음) | 쪽 이동 | `GET {페이지}/_history?number=N±1` | | `history.html:37-38`, `WikiViewController.kt:116-117` |

시각 칸은 `th:title`에 전체 시각, 글자에는 "몇 시간 전" 같은 상대 표시이다. 짧은 커밋 해시는 `code`로 표시한다.

### 4~6. 스크립트, 단축키, 팝업

없다(스크립트 없음).

### 7. 요청과 응답

| 요청 | 응답 |
|---|---|
| `GET .../_history` | 200. 이름 규칙 위반 404. 저장소가 없으면 빈 목록 |
| `GET .../_compare?...` | 쿼리가 없으면 400 `wiki.compare.required`(프로젝트 틀), 모르는 리비전 404 `wiki.revision.notFound`, 그 밖 200 |

### 8. 권한

읽기만 필요하다(비멤버, 익명도 공개 프로젝트면 가능).

### 9. 경계 값

쪽 크기 30. 이름이 바뀐 페이지는 이름 변경을 따라가는 이력이다(D-61, A-314). 시험: `WikiViewTest` "이력은 최신순이고 두 리비전을 골라 비교한다", "비교에 필요한 값이 없거나 리비전이 틀리면 오류이다".

## 화면 4: 비교 (`wiki/compare.html`)

### 1. 화면 개요

`compare`(`:120-144`): 쿼리 `commit`이 있으면 그 커밋 하나의 변경, 없고 `from`과 `to`가 둘 다 있으면 두 리비전 비교. `commit`이 `from`/`to`보다 우선한다. 모델: `pageTitle`, `pageUrl`, `historyUrl`, `lines`(`kind`=`hunk`, `add`, `del`, `ctx`와 `text`). 파일 머리말(`diff --git`, `index`, `---`, `+++`)은 `@@` 앞까지 버린다(`diffLines`, `:386-395`).

### 2. 레이아웃 계약

머리에 제목 링크와 `wiki.compare.title`, 오른쪽 `Revisions` 단추. 본문 `.wiki-diff`는 모노스페이스 0.8125rem, 테두리, 가로 스크롤(`_wiki.scss`). 줄은 `white-space: pre-wrap`. 추가 줄은 성공색 15% 배경, 삭제 줄은 위험색 15%, `@@` 줄은 회색 글자와 정보색 10% 배경. 변경이 없으면 `wiki.compare.nochange`(`compare.html:22`).

### 3. 동작 요소

| 요소 | 종류 | 동작 | 요청 | 근거 |
|---|---|---|---|---|
| 페이지 제목 링크 | 링크 | 그 페이지 | `GET {페이지}` | `compare.html:17` |
| `Revisions` | 링크 | 이력 화면 | `GET {페이지}/_history` | `compare.html:18` |

### 4~9

스크립트, 단축키, 팝업 없음. 권한은 읽기. 오류 응답은 이력 화면 표와 같다. 줄 텍스트는 `th:text`로 이스케이프된다.

## 발견 사항

| 번호 | 내용 | 근거 |
|---|---|---|
| wiki-F1 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 이력 화면에서 리비전이 하나뿐이면 `from` 라디오(`stat.index == 1`)가 선택되지 않아 `Compare Revisions`를 누르면 `from`이 빠져 400 `wiki.compare.required`가 나왔다. 리비전 메시지 링크는 정상. (2026-11-11 수정: 리비전이 2개 미만이면 `Compare Revisions` 단추를 그리지 않는다) | `history.html:21`, `:25`, `WikiViewController.kt:129-133` |
| wiki-F2 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 익명, 비멤버가 쓰기 경로(`_new`, `_edit`, `POST`)를 열면 로그인 폼이 아니라 403 `error.forbidden`이 나왔다. (2026-11-11 수정: 쓰기 경로 5개에 `@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)`를 더해 익명은 로그인 폼으로 303이다. 로그인한 비멤버는 그대로 403) | `WikiViewController.kt:149`, `:159`, `:178`, `:189`, `:210`, `:302-308` |
| wiki-F3 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | `POST /wiki/_upload`가 비멤버에게 JSON이 아니라 HTML 오류 화면 403을 돌려줬다. (2026-11-11 수정: 쓰기 권한이 없으면 403 JSON `{"message":"forbidden"}`이다. 편집 화면 스크립트는 응답이 2xx가 아니면 `wiki.attach.failed`를 보이므로 화면 표시는 같다) | `WikiViewController.kt:244-245`, `edit.html:61`, `:69` |
| wiki-F4 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 편집 화면은 `common/editor`만 쓰고 `board.Write` 같은 화면 모듈이 없어서 게시판에 있는 이탈 경고가 없었다. (2026-11-11 수정: 인라인 `beforeunload` 경고를 더했다. 제목 빈칸 알림과 현재 경로 임시 저장 정리는 여전히 없다. 임시 저장이 편집기 조각에서 켜지는지는 `common/editor`가 `shell` 담당이라 확인하지 못했다) | `edit.html:74-84` |
| wiki-F5 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 위키 화면의 `title`(문서 제목)은 `messages`가 아니라 영어 상수였다(`Wiki`, `New Page`, `Edit X`, `X - Revisions`, `X - Compare`). 템플릿 안 글자는 `#{wiki.*}`로 번역된다. (2026-11-11 수정: `text(key)`로 `menu.wiki`, `wiki.page.new`, `wiki.page.edit`, `wiki.history.title`, `wiki.compare.title` 메시지를 쓴다) | `WikiViewController.kt:87`, `:111`, `:139`, `:312`, `:353` |
| wiki-F6 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 복제 상자의 복사 단추는 성공과 실패 어느 쪽도 화면에 알리지 않았다. (2026-11-11 수정: `.wiki-clone-feedback`에 `wiki.clone.copied` 또는 `wiki.clone.copyFailed`를 보인다) | `shell.html:29`, `:56-65` |
| wiki-F7 **[검증: 결함 아님]** | 같은 화면에서 사이드바 스크립트가 `document.querySelector`로 첫 요소를 잡으므로 `wiki/shell :: sidebar`가 한 문서에 두 번 들어가면 두 번째는 동작하지 않는다. 현재 모든 위키 화면은 한 번만 쓴다 | `shell.html:33-69` |
| wiki-F8 **[검증: 결함 아님]** | 첨부 파일 삭제와 목록 화면은 없다(A-315). 올린 파일은 git으로만 지운다 | `decisions.md` A-315 |
| wiki-F9 **[검증: 수정함(A-466, 2026-11-11)]** | 위키의 `base`는 `siteUrls.path(...)`로 컨텍스트 경로를 이미 포함하는데, `view.html`의 `새 페이지` 링크와 `empty.html`의 `첫 페이지 만들기` 링크만 `@{${base + ...}}`로 한 번 더 감싼다. 하위 경로 배포(컨텍스트 경로가 비어 있지 않을 때)에서는 접두어가 두 번 붙는다(Thymeleaf의 `/`로 시작하는 링크 식은 컨텍스트 경로를 붙인다는 코드상의 추론이며 하위 경로로 띄워 확인하지 않았다). 같은 화면의 다른 링크(`view.html:18`, `:20`, `edit.html`, `history.html`, `compare.html`, `shell.html` 목록)는 접두어를 한 번만 가진다 (2026-11-11 수정: 두 링크의 `@{}` 감싸기를 없앴다(재현됨: 하위 경로에서 `/yona/yona/...`로 나왔다). 시험 `ContextPathLinksTest`가 중복 접두어도 찾는다) | `view.html:19`, `empty.html:19`, `WikiViewController.kt:298` |
## 범위 점검

| 템플릿 | 다룬 절 |
|---|---|
| `wiki/view.html` | 화면 1 |
| `wiki/empty.html` | 화면 1 |
| `wiki/shell.html` | 화면 1의 4(사이드바와 복제 상자) |
| `wiki/edit.html` | 화면 2 |
| `wiki/history.html` | 화면 3 |
| `wiki/compare.html` | 화면 4 |

조사한 템플릿 6개 / 담당 6개. 따라간 컨트롤러: `WikiViewController`(전체), `WikiService`(이름 규칙, 한도, 목록), `ProjectCheckStage`와 `AccessControl`(권한). 스타일: `app/assets/stylesheets/scss/_wiki.scss`.

## 갱신 기록 (2026-11-11 재조사)

- 다시 확인한 템플릿 6개: `wiki/view.html`, `empty.html`, `shell.html`, `edit.html`, `history.html`, `compare.html`. 함께 `WikiViewController`(전체), `AnonymousCheckStage`, `ProjectCheckStage`, `AccessControl`, `messages_ko_KR.properties`의 위키 문구를 대조했다.
- 내용을 고친 행과 문장: 약 17(쓰기 경로의 로그인 이동, 업로드 JSON 403, 문서 제목 현지화, 이탈 경고 스크립트, 복사 결과 표시, 이력의 비교 단추 조건, 새 페이지 링크, 발견 사항 wiki-F1~F6의 수정 표기).
- 줄 번호만 고친 곳: 약 30(`WikiViewController.kt` 전부, `shell.html`, `edit.html`, `history.html`).
- 더한 행: 복사 결과 표시 요소 `.wiki-clone-feedback`, 발견 사항 wiki-F9.
- 없앤 행: 없음(옛 동작을 적은 문장은 현재 동작으로 바꿨다).
- 새 발견 사항: wiki-F9(`새 페이지`, `첫 페이지 만들기` 링크의 컨텍스트 경로 이중 접두어 가능성, 추측).
