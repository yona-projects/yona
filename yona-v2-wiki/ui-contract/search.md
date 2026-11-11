---
title: UI 계약 - 통합 검색 (search)
type: ui-contract
sources:
  - src/main/resources/templates/search/partial-search.html
  - src/main/resources/templates/search/partial-items.html
  - src/main/resources/templates/search/partial-milestones.html
  - src/main/resources/templates/search/partial-projects.html
  - src/main/resources/templates/search/partial-users.html
  - src/main/resources/templates/search/result-organization.html
  - src/main/resources/templates/search/result-project.html
  - src/main/resources/templates/search/result-site.html
  - src/main/kotlin/dev/yona/web/search/SearchController.kt
  - src/main/kotlin/dev/yona/web/search/SearchRowAssembler.kt
  - src/main/kotlin/dev/yona/web/search/SearchRows.kt
  - src/main/kotlin/dev/yona/domain/search/SearchService.kt
  - src/main/kotlin/dev/yona/domain/search/SearchSnippets.kt
updated: 2026-11-11
---

# UI 계약: 통합 검색 (`search/`)

기존 설명은 [web/search.md](../web/search.md). 공통 규칙(CSRF, 접근 검사 순서, 반응형)은 [board.md](board.md#공통-규칙)를 본다. 검색 입력란(상단 바의 검색 상자와 범위 드롭다운)은 `common/navbar.html`(`shell` 담당)이 그리고, 여기서는 그 상자가 보내는 요청과 결과 화면을 다룬다.

## 영역 요약

| 템플릿 | URL (메서드) | 컨트롤러 (`web/search/SearchController.kt`) | 용도 |
|---|---|---|---|
| `search/result-site.html` | `/search` (GET) | `searchInAll` `:48-59` | 전체 범위 결과. 틀 `layout/site-layout('NONE')` |
| `search/result-organization.html` | `/organizations/{organizationName}/search` (GET) | `searchInAGroup` `:61-79` | 조직 범위. 틀 `layout/organization-layout`, `organization/header`, `organization/menu` |
| `search/result-project.html` | `/{user}/{project}/search` (GET) | `searchInAProject` `:81-95` | 프로젝트 범위. 틀 `layout/project-layout('NONE')`, `projectMenu(..., 'NONE', 'main-menu-only')` |
| `search/partial-search.html` | (조각 `search`) | 위 세 화면이 쓴다 | 탭 목록, 검색 폼, 결과 제목, 결과 영역, 키워드 강조 스크립트 |
| `search/partial-items.html` | (조각 `items`) | 이슈, 게시글, 이슈 댓글, 게시글 댓글, 코드 리뷰 결과 | 글 목록 모양 결과 |
| `search/partial-milestones.html` | (조각 `milestones`) | 마일스톤 결과 | |
| `search/partial-users.html` | (조각 `users`) | 사용자 결과 | |
| `search/partial-projects.html` | (조각 `projects`) | 프로젝트 결과(프로젝트 범위에는 없음) | |

세 `result-*` 화면의 `title`은 메시지 키 `title.search`를 `#{__${title}__}`로 푼 글자이다.

## 요청 계약

공통 쿼리: `keyword`(필수. 공백만이어도 유효), `searchType`(필수), `pageNum`(1부터, 선택).

| 규칙 | 결과 | 근거 |
|---|---|---|
| `keyword`가 비었거나 없음, `searchType`이 비었거나 없음 | 400 `ErrorViews.badRequest()` | `SearchController.kt:53`, `:66`, `:88` |
| `searchType`이 `auto, user, project, issue, post, milestone, issue_comment, post_comment, review` 가운데 하나가 아님(대소문자 구분) | 400 | `SearchService.kt:24-31` |
| 프로젝트 범위에서 `searchType=project` | 400 | `SearchController.kt:90` |
| 조직 범위에서 없는 조직 | 400(404가 아니다) | `:74` |
| 조직 범위에서 `yona.hide-project-listing`이 켜져 있고 보는 사람이 그 조직의 구성원도 관리자도 아님 | 400 | `:69-72` |
| `pageNum`이 숫자가 아니거나 1보다 작음 | 400 | `:98-102` |
| 프로젝트 범위에서 프로젝트를 읽을 수 없음 | `@IsAllowed(READ)`가 먼저 막아 403(익명은 로그인 화면) 또는 404 | `:82`, `ProjectCheckStage.kt:63-67` |

- 접근 검사 순서: `/search`와 조직 범위는 클래스의 `@AnonymousCheck`만 있어, 사이트가 익명 접근을 막았으면(`allows-anonymous-access=false`) 익명을 `/users/loginform?redirectUrl=<요청 경로>`로 보낸다(`AnonymousCheckStage.kt:24-33`).
- 응답은 항상 HTML이다(JSON 분기 없음). 읽기 전용 트랜잭션.
- `searchType=auto`(상단 바 검색의 기본값, `common/navbar.html:37`)이면 건수가 있는 첫 종류로 정한다. 순서는 이슈, 사용자, 프로젝트, 게시글, 마일스톤, 이슈 댓글, 게시글 댓글, 코드 리뷰이고 모두 0이면 이슈(`SearchService.kt:98-101`, `:255-258`). 그 밖의 종류는 건수가 0이어도 그대로 쓴다.

## 화면: 결과 (`partial-search.html`)

### 1. 화면 개요

모델 `search(SearchView)`: `keyword`, `searchType`(확정된 종류), `counts`, `rows`, `totalPageCount`, `action`(폼이 보낼 주소), `projectScoped`(`web/search/SearchRows.kt:56-62`). `counts`는 종류별 건수를 모두 센다. 프로젝트 범위는 `projects`를 세지 않아 0(`SearchService.kt:85`). 쪽 크기는 20(`SearchService.kt:253`).

### 2. 레이아웃 계약

| 영역 | 내용 | 근거 |
|---|---|---|
| 머리 `.site-breadcrumb-outer` | 제목 `title.search`. 조직 화면에서는 조직 머리와 메뉴 아래에 이어진다 | `partial-search.html:11-15` |
| 왼쪽 `col-lg-2` | 탭 목록 `.search-category-wrap`. 항목마다 이름과 건수 배지 `.num-badge` | `:20-63` |
| 오른쪽 `col-lg-10` | 검색 폼(`.search-box-wrap`), 결과 제목, 결과 영역 `.search-result-wrap` | `:64-87` |

- 탭: `이슈`, `사용자`, `프로젝트`(프로젝트 범위에서는 항목 없음, `th:unless="${search.projectScoped}"`), `게시판`, `마일스톤`, `이슈 댓글`, `게시판 댓글`, `코드 리뷰` 순서(`:22-61`). 현재 탭은 `active` 클래스, 건수가 0인 탭은 `empty` 클래스(`th:class`의 두 값을 이어 붙임). 둘 다 CSS로만 구분되고 클릭은 막히지 않는다.
- 결과 제목은 `search.result.title(건수, 종류 이름)`을 `th:utext`로 그린다. 한국어 문구는 `{1}에서 <strong>{0}</strong> 건이 검색 되었습니다`(`messages_ko_KR.properties:918`). 키워드는 제목에 들어가지 않는다.
- 결과 영역은 `th:switch`: `user` → `partial-users`, `project` → `partial-projects`, `milestone` → `partial-milestones`, 그 밖 → `partial-items`(`:80-85`).
- 빈 결과: 목록 대신 빈 `div.empty-result`만 있다(각 조각의 끝, 문구 없음). 쪽 막대도 없다.
- 반응형: 이 화면에 `hide-in-mobile` 같은 별도 클래스는 없다. `col-lg-2`/`col-lg-10`은 992px 미만에서 탭 목록 위, 결과 아래로 쌓인다.

### 3. 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 탭 링크 `a[data-toggle=search-category][data-type]` | 링크(`href="#"`) | 항상(건수 0 포함) | 기본 이동을 막고 숨은 `searchType`을 그 탭의 종류로 바꾼 뒤 `#searchInnerForm`을 제출. `pageNum`은 보내지 않으므로 1쪽 | `GET {action}?searchType=<type>&keyword=<현재 입력값>` | 같은 화면을 새로 그림 | 범위 규칙 | `partial-search.html:23-61`, `:95-99` |
| 키워드 `#searchKeyword[name=keyword]` | 입력란 | 항상 | 입력. Enter는 폼 제출 | `keyword` | | | `:68` |
| 숨은 `searchType` | 숨은 입력 | 항상 | 현재 종류 | | | | `:67` |
| `검색` 단추 | 제출 | 항상 | 폼 제출 | `GET {action}?searchType=&keyword=` | | | `:69` |
| 결과 제목 링크 | 링크 | 줄에 `titleUrl` | 글, 이슈 등으로 이동 | `GET <titleUrl>` | | 대상 화면 | `partial-items.html:14` |
| 발췌 영역 | 링크 | `snippetsUrl != null`(커밋에 달린 코드 리뷰 댓글) | 발췌 전체가 링크 | `GET <snippetsUrl>` | | | `partial-items.html:17-19` |
| 프로젝트 링크 `.project-link` | 링크 | `projectLabel != null`(전체와 조직 범위만) | 프로젝트로 | `GET /{owner}/{name}` | | | `partial-items.html:25` |
| 작성자 링크 | 링크 | `authorName != null` | 사용자 페이지. 툴팁은 로그인 ID | `GET /{loginId}` | 이름이 없으면 글자 `noAuthorText`(이슈 계열 `issue.noAuthor`, 게시글 댓글 `posting.noAuthor`) | | `partial-items.html:26-27` |
| 시각 | 글자 | 항상 | 툴팁은 전체 시각, 글자는 상대 시각 | | | | `partial-items.html:28` |
| 쪽 막대 `#pagination` | `esm/Pagination.js` | `rows`가 비어 있지 않을 때 | 이전, 다음 링크는 현재 URL에서 `pageNum`만 바꿈. 번호 입력 Enter도 이동 | `GET <현재 URL>&pageNum=N` | 키 `←`, `→`도 이 링크를 따라간다(`Pagination.js:312-317`) | | `partial-items.html:32-40` |
| 사용자 줄 | 링크 둘 | | 아바타와 `이름 (@loginId)` 링크 | `GET /{loginId}` | 기본 아바타가 아니면 32x32 `alt=이름` | | `partial-users.html:12-19` |
| 프로젝트 줄 | 링크 | | 아바타, `owner/name` 링크, 포크이면 원본 프로젝트 링크 | `GET /{owner}/{name}` | 개요(`overview`), 생성 시각, 마지막 코드 갱신 시각(있을 때) | | `partial-projects.html:12-36` |
| 마일스톤 줄 | 링크 | | 제목 링크 | `GET {프로젝트}/milestone/{id}` | 마감일이 있으면 `기한 <strong>날짜</strong> (남은 기간)` | | `partial-milestones.html:13`, `:20` |

### 4. 스크립트 동작

- 인라인 스크립트(`partial-search.html:93-124`)와 각 결과 조각 안의 `yona.Pagination.update($("#pagination"), totalPageCount)`.
- 키워드 강조: 문서가 준비되면 `.search-content-body, .title` 각각의 **글자(text)**를 읽어, `#searchKeyword` 값을 앞뒤 공백을 지우고 정규식 특수문자를 이스케이프한 뒤 대소문자 무시(`ig`)로 찾아 `<strong class="keyword">`로 감싼다. 조각마다 `&`, `<`, `>`, `"`를 HTML 인코딩해 `.html()`로 다시 넣는다(`:101-123`). 키워드가 비면(공백만) 빈 문자열 정규식이 되어 `lastIndex`를 올리며 건너뛴다.
- 이 선택자 `.title`은 결과 영역에 한정되지 않는다. 문서 안의 모든 `.title` 요소(결과 줄의 제목 링크 외에 레이아웃에 `.title` 클래스가 있으면 그것도)가 같은 처리를 받는다.
- 로컬 저장소, 쿠키, 폴링은 쓰지 않는다. 지연 로딩 없음.

### 5. 단축키

`←`, `→`(`Pagination`이 등록, 입력란에 초점이 있으면 무시). 상단 검색 단축키 `ALT+S`는 `common` 담당이다. 이 화면의 키맵 모달은 없다.

### 6. 팝업, 툴팁

팝업 없음. 툴팁(`data-bs-toggle="tooltip"`): 결과의 작성자 링크(로그인 ID), 사용자 아바타(로그인 ID).

### 7. 요청과 응답

위 "요청 계약" 표. 성공은 200 HTML이다. 결과가 없어도 200.

### 8. 권한 행렬: 무엇이 검색되는가

가시성은 서버 쿼리가 정한다(`SearchService.kt:160-239`). 화면이 숨기는 것은 없다.

| 종류 | 비로그인 | 로그인 | 비고 |
|---|---|---|---|
| 이슈, 게시글, 마일스톤, 댓글, 코드 리뷰 | 공개 프로젝트의 것만(`hide-project-listing`이면 아무것도 없음) | 공개 프로젝트, 내가 구성원인 프로젝트, 내 조직의 비공개가 아닌 프로젝트(전체 범위). 조직 범위에서 조직 구성원이면 공개와 보호 프로젝트 | `hide-project-listing`이면 공개 부분이 빠진다 |
| 사용자 | **전체 범위에서는 아무도 찾지 못한다** | 활성 사용자 전체 | 조직 범위는 그 조직 구성원만, 프로젝트 범위는 그 프로젝트 구성원만 |
| 프로젝트 | 공개 프로젝트(숨김 설정이면 없음) | 공개, 내 조직의 보호 프로젝트, 내가 구성원인 프로젝트 | |
| 내가 쓴 글, 내가 담당한 이슈 | 권한을 잃은 프로젝트에서는 찾지 않는다(R-50-5, 시험 고정) | | `SearchService`의 클래스 설명(`:66-67`)은 이전 동작을 적고 있다([search-F1](#발견-사항)) |

프로젝트 범위는 라우트에서 읽기 권한을 이미 확인하므로 프로젝트와 키워드만 본다(`:177`). 조직의 OAuth 앱 정책(X-39)이 막는 조직의 비공개, 보호 프로젝트는 결과에서 빠진다(`deniedOrganizations`, `:166-169`).

### 9. 빈 상태, 경계 값

- 쪽당 20개(`SearchService.PAGE_SIZE`, `:253`). `pageNum`은 1부터.
- 키워드는 대소문자 무시 부분 일치(`lower(...) like`)이고 `%`, `_`, `\`는 글자 그대로이다(`LikeEscape.contains`). 찾는 필드: 이슈와 게시글은 제목, 본문. 마일스톤은 제목, 설명. 댓글은 내용. 사용자는 이름, 로그인 ID. 프로젝트는 개요, 이름(`:131-141`).
- 정렬: 이슈, 게시글, 댓글, 코드 리뷰는 작성 시각 내림차순(같으면 id 내림차순). 마일스톤은 마감일 내림차순(없는 것은 맨 뒤). 사용자와 프로젝트는 이름 오름차순.
- 발췌: 키워드 앞뒤 40자를 한 조각으로 하고 겹치거나 이어지면 합친다(`SearchRowAssembler.THRESHOLD`, `SearchSnippets.kt:17-33`). 본문 전체보다 짧으면 조각 뒤에 `.....`가 붙는다(`partial-items.html:18`). 키워드가 본문에 없으면(제목에만 있을 때) 발췌 줄이 없다. 사용자 가입일은 `MMM dd, yyyy`(미국 로캘).
- 결과 줄 구성: 이슈, 게시글은 `#번호` + 제목 링크(`title` 클래스 포함). 댓글 계열은 `#번호` + `Re) 글제목` 링크(주소 뒤에 `#comment-{id}`, `title` 클래스 없음). 코드 리뷰는 풀 리퀘스트가 있으면 `#번호`와 `Re) 제목`, 커밋이면 제목 줄 없이 발췌 전체가 링크(`SearchRowAssembler.kt:92-127`).
- 시험: `SearchTest`(오류, 공백 키워드, AUTO, 탭 8개와 empty, 제목의 건수, 가시성, 발췌, 쪽, 사용자, 프로젝트, 마일스톤, 댓글, 코드 리뷰, 조직, 프로젝트 범위), `SearchHiddenListingTest`, `ExternalSearchApiTest`(API 쪽).

## 발견 사항

| 번호 | 내용 | 근거 |
|---|---|---|
| search-F1 | `SearchService`의 클래스 설명(`:66-67`)은 "내가 쓴 글과 담당한 이슈는 권한을 잃은 프로젝트에서도 찾는다"고 적었지만 코드와 시험은 찾지 않는다(R-50-5). 코드 안 주석(`:178`)이 정정하고 있다. `Spec.authorPath`와 `Spec.assignee`는 선언만 있고 쓰이지 않는다. 마지막의 `like 이스케이프` 설명 주석(`:250`)도 빈 자리를 가리킨다 | `SearchService.kt:66-67`, `:126-129`, `:178`, `:250` |
| search-F2 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 조직 범위의 오류는 모두 400이다. 없는 조직도 404가 아니라 400(`ErrorViews.badRequest()`)이고 본문은 프로젝트 틀이 아닌 기본 오류 화면이다. 프로젝트 범위의 `keyword`, `searchType` 오류도 400 | `SearchController.kt:74`, `:88` |
| search-F3 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | `search.result.title`에는 키워드가 들어가지 않고 건수와 종류 이름만 있다. 결과가 0건이어도 문구는 "0 건이 검색 되었습니다"이고 빈 안내 문구는 없다(`div.empty-result`는 내용 없는 요소) | `partial-search.html:72-77`, `partial-items.html:41` |
| search-F4 **[검증: v2 신규 결함, 수정됨 A-460]** | 강조 스크립트의 `.title` 선택자는 결과 영역으로 한정되지 않는다. 결과 줄이 아닌 `.title` 요소의 HTML이 글자로 바뀌어 안쪽 마크업(링크, 아이콘)이 사라진다. 조직 범위에서는 `organization/header.html:22`의 `div.dropdown-menu.flat.right.title`(가입 안내 드롭다운)이 그 대상이다(그 요소가 그려질 때). 프로젝트 메뉴, 사이트 틀, 상단 바에는 `.title` 클래스 요소가 없음을 grep으로 확인했다 | `partial-search.html:107-123`, `organization/header.html:22` |
| search-F5 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 탭은 건수가 0이어도 클릭할 수 있고, 클릭하면 그 종류로 `searchType`을 보낸다(AUTO 해석을 거치지 않는다). 프로젝트 범위에서 `searchType=project`를 직접 보내면 400이다 | `partial-search.html:95-99`, `SearchController.kt:90` |
| search-F6 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 쪽 이동의 `Pagination`은 `document.URL`을 기준으로 `pageNum`만 바꾸므로 AUTO로 들어온 첫 화면의 URL(`searchType=auto`)이 쪽 링크에도 남는다. 건수가 바뀌지 않는 한 같은 종류로 풀리지만, 다른 요청 사이에 건수가 바뀌면 쪽을 넘길 때 종류가 바뀔 수 있다(추측) | `Pagination.js:130`, `:80-93` |
| search-F7 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 사용자 결과 아바타 `img`는 기본 아바타일 때 크기 속성이 없다(`helper.isDefaultAvatar`) | `partial-users.html:13-14` |

## 범위 점검

| 템플릿 | 다룬 절 |
|---|---|
| `search/partial-search.html` | 결과 화면(1~9) |
| `search/partial-items.html` | 결과 화면 3, 9 |
| `search/partial-milestones.html` | 결과 화면 3, 9 |
| `search/partial-projects.html` | 결과 화면 3, 9 |
| `search/partial-users.html` | 결과 화면 3, 9 |
| `search/result-organization.html` | 영역 요약, 요청 계약 |
| `search/result-project.html` | 영역 요약, 요청 계약 |
| `search/result-site.html` | 영역 요약, 요청 계약 |

조사한 템플릿 8개 / 담당 8개. 따라간 코드: `SearchController`, `SearchRowAssembler`, `SearchRows`, `SearchService`, `SearchSnippets`, `Pagination.js`, `ShortcutKey.js`, `AnonymousCheckStage`.
