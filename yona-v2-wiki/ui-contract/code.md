---
title: 화면 동작 계약, 코드(탐색, 이력, 커밋, 비교, 브랜치, 태그)
type: ui-contract
sources:
  - src/main/resources/templates/code/*.html
  - src/main/kotlin/dev/yona/web/code/*.kt
  - src/main/kotlin/dev/yona/web/access/*.kt
  - src/main/kotlin/dev/yona/web/WatchController.kt
  - src/main/kotlin/dev/yona/domain/vcs/VcsType.kt
  - src/main/resources/static/assets/javascripts/esm/service/code.Browser.js
  - src/main/resources/static/assets/javascripts/esm/service/code.Nohead.js
  - src/main/resources/static/assets/javascripts/esm/service/review.CommentEdit.js
  - src/main/resources/static/assets/javascripts/esm/Comment.js
updated: 2026-11-11
---

# 화면 동작 계약: 코드 영역

`templates/code/`(14개)의 계약이다. 코드 비교 화면의 스크립트(미니맵, 접기, 드래그 선택, 줄 댓글 팝업, 리뷰 카드, DOM 계약)는 [code-diff-script.md](code-diff-script.md), 저장소 종류별 차이와 프로토콜 경로는 [code-vcs.md](code-vcs.md), 코드 주고받기는 [pullrequest.md](pullrequest.md)에 있다. 같은 내용의 기존 위키는 [web/code-browser.md](../web/code-browser.md), [web/commit-view.md](../web/commit-view.md), [web/tags.md](../web/tags.md), [web/review-comments.md](../web/review-comments.md), [web/subversion.md](../web/subversion.md)이다.

근거 표기는 pullrequest.md와 같다: `T:`=`src/main/resources/templates/`, `K:`=`src/main/kotlin/dev/yona/web/`, `D:`=`src/main/kotlin/dev/yona/domain/`, `S:`=`src/main/resources/static/assets/javascripts/`. 공통 규칙(CSRF, `data-request-method`, 접근 검사 순서, 오류 응답, `303`)은 [pullrequest.md 0.3](pullrequest.md)을 따른다.

## 0. 영역 요약

| 템플릿 | URL(메서드) | 컨트롤러 | 용도 |
|---|---|---|---|
| `code/view.html` | `GET /:user/:project/code`(기본 브랜치로 `303`), `.../code/:branch`, `.../code/:branch/*path` | `K:code/CodeBrowserController.kt:54-104` | 폴더 목록과 파일 보기 |
| `code/nohead.html`, `nohead-hg.html`, `nohead-svn.html` | `GET .../code`(저장소가 비었을 때) | `K:code/CodeBrowserController.kt:66-68`, `K:code/NoheadViews.kt` | 비어 있는 저장소의 처음 올리는 방법 안내 |
| `code/history.html` | `GET .../commits`, `.../commits/:branch[/]`, `.../commits/:branch/*path` | `K:code/CommitHistoryController.kt:62-81` | 커밋 이력 |
| `code/diff.html` | `GET .../commit/:id` (Git, Mercurial) | `K:code/CommitViewController.kt:91-135` | 커밋 보기(diff, 줄 댓글, 리뷰 카드) |
| `code/svn-diff.html` | `GET .../commit/:id` (Subversion) | `K:code/CommitViewController.kt:140-175` | Subversion 커밋 보기 |
| `code/compare.html` | `GET .../compare/:revA..:revB` (Git, Mercurial) | `K:code/CommitViewController.kt:195-216` | 두 커밋 비교 |
| `code/compare-svn.html` | 같은 URL (Subversion) | 같은 메서드 | Subversion 두 리비전 비교 |
| `code/branches.html` | `GET .../branches`, `POST .../code/:branch/setAsDefault`, `DELETE .../code/:branch/` | `K:code/BranchController.kt:65-133` | 브랜치 목록과 관리 |
| `code/tags.html` | `GET .../tags`, `DELETE .../tags/:tag` | `K:code/TagController.kt:62-102` | 태그 목록과 삭제 |
| `code/partial-comment-thread.html`, `thread-wrapper.html` | (조각) | `K:code/ThreadHtmlRenderer.kt`, `ThreadViewAssembler.kt` | 리뷰 스레드 조각 |
| `code/partial-signature.html` | (조각) | `K:code/SignatureBadges.kt` | 서명 배지 |

원본 파일 경로: `GET .../rawcode/:rev/*path`(`text/plain`), `.../image/:rev/*path`, `.../files/:rev/*path`, `.../code/:branch/download`(zip), `GET .../code/!`와 `.../code/:branch/!`(+`/!/*path`, 폴더 메타 JSON), `GET .../lfs/:oid`(LFS 객체)가 이 화면의 링크 대상이다(응답 형식은 [web/code-browser.md](../web/code-browser.md)).

공통 권한 어노테이션: `@CodeAccessCheck`(읽기 권한 + `isCodeAccessibleMemberOnly`이면 구성원만, 아니면 익명 로그인 화면 `403`, 로그인 사용자 `403 error.forbidden.or.notfound`, `K:access/ProjectCheckStage.kt:72-76`)가 `code/:branch...`, `commits...`, `commit/:id`, `rawcode`, `image`, `branches`, `tags`, `lfs`에 있다. `/code`는 `@IsAllowed(READ)` 뒤 본문에서 같은 제한을 다시 검사하고(`403 error.forbidden`, 프로젝트 레이아웃), `files/...`와 `compare/...`는 `@IsAllowed(READ)`만 있다(발견 code-F3). 저장소 종류를 모르는 프로젝트의 `/code`와 하위는 `501`(`<vcs> is not supported!`, text/plain)이다.

---

## 1. 코드 보기(view)

### 1.1 화면 개요

- `K:code/CodeBrowserController.kt:77-104`가 `browse`를 부른다. 브랜치 구간은 두 번 디코딩(`UrlPaths.decodeSegmentOrSelf`), `*path`는 스프링이 한 번 디코딩한 값을 그대로 쓴다. 브랜치나 경로가 없으면 `404`(메시지는 브랜치 이름, 프로젝트 레이아웃, 메뉴 `code`).
- 모델 `title="menu.code"`, `projectView`, `page(CodePage)`, `maxFileSize`. `page`: `showTabs`, `filesTabUrl`, `commitsTabUrl`, `branchesTabUrl`(Git, Mercurial만), `tagsTabUrl`, `branchGroups`, `rootUrl`, `projectName`, `crumbs`, `downloadUrl`(zip 지원 종류만), `newFileUrl`(온라인 편집 지원 종류이고 로그인했을 때만, Git만), `items`(폴더 목록들과 마지막 파일 보기를 차례로), `metaInfoUrl`, `basePathUrl`(`K:code/CodePageAssembler.kt:39-79`).
- 레이아웃 `T:code/view.html:15-149`: `.code-browse-wrap` 안에 탭(`showTabs`일 때), `.code-browse-header`(브랜치 select 왼쪽, 이동 경로, 오른쪽 `내려받기`, `새 파일`), `.code-viewer-wrap`(스피너 `#spin`, 폴더 목록 `div.list-wrap[data-type=folder]`들, 파일 `div.file-wrap[data-type=file]`).
- 반응형: 이 템플릿 자체의 `hide-in-mobile` 없음. 폴더 목록 줄의 열은 `col-lg-6/5/1`(992px 미만에서 세로로 쌓임).
- 빈 상태: 폴더에 항목이 없으면 `div.alert.alert-warning`(`code.nofiles`). 저장소 자체가 비면 `nohead`(2절).

### 1.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 탭 `Files`(활성), `Commits`, `Branches`, `Tags` | 링크 | `showTabs`(첫 항목이 폴더). `Branches`는 `branchesTabUrl != null`, `Tags`는 `tagsTabUrl != null` | 이동 | `GET .../code/<브랜치>/<상위 경로>`, `.../commits/<브랜치>/`, `.../branches`, `.../tags` | | 읽기 | `T:code/view.html:18-23` |
| 브랜치 `#branches` | select2(`data-format=branch`, 폭 220px) | 항상 | 값(같은 경로의 다른 브랜치 주소)이 바뀌면 이동. 구역은 `title.branches`(`refs/heads/`), `title.tags`(`refs/tags/`), Subversion은 구역 없음. 원격 참조(`refs/remotes/`)는 제외 | `GET .../code/<ref>/<경로>` | 전체 이동 | 읽기 | `:26-35,157-159`, `K:CodePageAssembler.kt:49-51,155-163` |
| 이동 경로 `#breadcrumbs` | 링크들 | 항상 | 프로젝트 이름 링크 + 경로 조각마다 링크. 폴더를 펼치면 JS가 다시 그림 | GET | | 읽기 | `:36-38`, `S:code.Browser.js:update Breadcrumbs` |
| `내려받기` | 링크 | `downloadUrl != null`(Git, Mercurial) | 이동 | `GET .../code/<브랜치>/download` | `application/octet-stream` zip, `Content-Disposition: attachment; filename=<프로젝트>-<브랜치>.zip`(ASCII가 아니면 RFC 5987) | 코드 읽기 | `:39-41`, `K:CodeBrowserController.kt:129-146` |
| `새 파일` `#new-file-link` | 링크 | `newFileUrl != null`(Git, 로그인) | 이동. 폴더를 펼칠 때마다 JS가 쿼리 `path=`를 바꿈 | `GET .../postform?path=<폴더/>&branch=<브랜치>` | 파일 작성 화면(다른 영역) | 로그인 | `:42-44`, `S:code.Browser.js` |
| 폴더 목록 줄 `div.listitem[data-path]` | 영역 | 항목이 있을 때 | 폴더 링크 클릭은 이동하지 않고 해시 `#<경로>`로 바꿔 폴더 목록을 **Ajax로 펼치기, 접기**. 파일 링크는 일반 이동. 작성자 아바타 링크, 커밋 메시지 링크(`commitUrl`), 날짜 | 폴더 펼침 `GET .../code/<브랜치>/!/<경로>`(JSON `{data:{이름:{...}}}`) | 응답으로 `div.list-wrap`을 만들어 폴더 줄 뒤에 끼움(깊이 클래스 `depth-N`, 들여쓰기 `padding-left:20px*N`을 스타일시트에 규칙으로 더함) | 코드 읽기 | `:49-84`, `S:code.Browser.js` |
| 파일 머리 | 표시 | 파일 | 작성자, 시각, 리비전(Git, Mercurial은 앞 7자, Subversion은 `Revision N`) 링크(`/commit/<ID>?branch=..#<경로>`)와 댓글 수(`code`에 스레드가 있을 때), 커밋 메시지, 줄바꿈 종류(`DOS`, `UNIX` 등) | | | | `:89-105`, `K:CodePageAssembler.kt:100-131` |
| `Raw` | 링크 `target=_blank` | 이진 파일이 아닐 때 | `GET .../rawcode/<리비전>/<경로>` | `text/plain` | 코드 읽기 | `:107-108` |
| `Edit` | 링크 | `editUrl != null`(로그인, 이진 아님) | `GET .../postform?path=<경로>&branch=<브랜치>&edit=true` | | 로그인 | `:109` |
| `브라우저로 열기` `#open-in-browser` | 링크 `target=_blank` | 항상 | 호버 팝오버(`code.open.desc`). `GET .../files/<브랜치 또는 리비전>/<경로>` | 감지한 종류 그대로 | 읽기만(발견 code-F3) | `:111-113,162` |
| `변경 이력` | 링크 | 항상 | `GET .../commits/<브랜치>/<경로>` | | | `:114` |
| LFS 안내 `#lfs-pointer` | 알림 | `f.lfs != null`(LFS 포인터 파일, 프로젝트가 LFS 사용) | `code.lfs.stored(크기)`. `내려받기` 링크(`lfs.downloadUrl`, 서버에 객체가 있을 때) 아니면 `code.lfs.missing` | `GET .../lfs/<oid>?name=<파일 이름>`(`@CodeAccessCheck`, `application/octet-stream` 첨부, 객체 없음 `404`) | | 코드 읽기 | `:118-122`, `K:lfs/LfsDownloadController.kt:26-38` |
| 본문(방식별) | 영역 | `f.mode` | `image`: `<img src=rawUrl>`. `binary`: 파일 이름, 크기(JS가 읽기 쉬운 크기로 바꿈), `다운로드` 링크. `tooBig`: `code.tooBigFileForCodeBrowser(최대 크기)`, 사이트 관리자에게 `code.looseFileSizeLimitForCodeBrowser`, `원본 보기` 링크. `markdown`: 서버가 렌더링한 HTML. `code`: 숨은 `#codeVal`(텍스트)과 `pre#showCode[data-mimeType]` | | | | `:123-140`, `K:CodePageAssembler.kt:110-115` |

방식 판정(`K:CodePageAssembler.kt:110-115`): 이진 파일이면 MIME이 `image/`로 시작할 때 `image`, 아니면 `binary`. 텍스트가 없고 크기가 0보다 크면 `tooBig`. 확장자가 `markdown, mdown, mkdn, mkd, md, mdwn`이면 `markdown`. 그 밖은 `code`.

### 1.3 스크립트 동작

- 불러오는 것: `common/select2`, `dynatree` CSS, `S:esm/service/code.Browser.js`, 파일이 있으면 `lib/ace/ace.js`, `common/markdown`. 문서 준비 때 `#branches` change(`location.href = $(this).val()`), `#open-in-browser`에 `bootstrap.Popover`(호버, 위), `$yona.loadModule("code.Browser", {sProjectName, sInitialPath, sMetaInfoURL, sBasePathURL})`(`T:code/view.html:151-171`).
- `code.Browser`(`S:esm/service/code.Browser.js`): 파일 보기(`.file-wrap[data-type=file]` 존재)이면 ace 읽기 전용 편집기(테마 `clouds`, 글자 12px, 활성 줄 강조 없음)를 `#showCode`에 만들어 `#codeVal` 텍스트를 넣는다. 모드는 `data-mimetype`의 `text/x-<언어>-source`에서, 없으면 확장자 표(약 35개 언어)로, 없으면 `text`. 마크다운 확장자이면 편집기 대신 `#codeVal`을 보이고 `markdown-wrap codebrowser-markdown` 클래스를 줌. 편집기 높이는 화면 줄 수 x 줄 높이로 50ms 뒤 조정. 폴더 목록 모드이면 `.list-wrap[data-listpath]`를 해당 폴더 줄 뒤로 옮기고 해시 변경(`hashchange`)에 따라 목록을 펼치거나 접는다: 앵커 `#cb-<경로>`는 `cb-` 접두어를 벗기되 그 이름의 폴더가 실제로 있으면 접두어가 아니다. 해시의 경로 조각마다 `GET <sMetaInfoURL>/<경로>`를 차례로 보내 아직 없는 목록만 만들고 이동 경로와 `새 파일` 링크를 갱신, 진행 막대 표시. 목록 줄은 `#tplFileListItem` 템플릿에서 만들되 파일 이름 등 저장소에서 온 글자는 이스케이프한다(`codebrowserxss.test.mjs`).
- **브랜치 이동 처리기가 둘이다**: 템플릿 인라인(`$(this).val()`)과 `code.Browser`(`weEvt.val`, 폴더 목록 모드에서만)가 모두 `location.href`를 정한다. select2 4.1.0의 change 이벤트에는 `val`이 없다(발견 code-F1).
- 단축키: 이 화면 범위 키 없음.

### 1.4 요청과 응답

| 요청 | 응답 |
|---|---|
| `GET /code` | 저장소가 비면 nohead 화면 `200`, 아니면 `303 /code/<기본 브랜치>`(`refs/heads/` 접두어 제거). 코드 제한 프로젝트의 비구성원은 `403` |
| `GET /code/:branch[/*path]` | 폴더 또는 파일 화면 `200`. 없으면 `404` |
| `GET /code/!`, `/code/:branch/!`, `.../!/*path` | JSON `200`. 없으면 본문 없는 `404` |
| 파일 크기 한도 | `yona.code-browser-max-file-size`(기본 1MB, 정의 `K:`가 아니라 `YonaProperties.codeBrowserMaxFileSize`). 넘으면 `data`가 비어 `tooBig` |

---

## 2. 비어 있는 저장소(nohead 3종)

- 그리는 곳: `CodeBrowserController.codeBrowser`가 `service.isEmpty(project)`일 때 `NoheadViews.of`(`K:code/NoheadViews.kt:34-52`). 템플릿은 `project.vcs`가 Subversion이면 `nohead-svn`, Mercurial이면 `nohead-hg`, 아니면 `nohead`. 제목 `프로젝트 - 코드`(Subversion은 `title.commitHistory`). 커밋 이력 화면은 이 화면을 쓰지 않는다(빈 저장소는 `404`, 발견 code-F6).
- 모델: `cloneUrl`(구성원이면 `로그인ID@` 포함, 사이트 관리자와 Subversion은 넣지 않음), `sshCloneUrl`(SSH 켠 사이트의 Git, Mercurial만), `loginId`, `canUpdate`(프로젝트 `UPDATE`).
- 요소(`T:code/nohead.html:15-71`): `code.nohead`(HTML 안내 글), **`canUpdate`일 때만** 안내 블록 전체. SSH를 켰으면 `HTTP`/`SSH` 전환 단추(`.clone-protocol`, 클릭이 선택 표시와 `.clone-url` 글자를 바꿈, 인라인 스크립트 `:52-63`). 이어서 명령 예시 4단락(복제 후 첫 커밋, 새로 만들기, 원격 추가, pull과 push)의 `pre code`. Mercurial(`nohead-hg.html:25-42`)은 `hg` 명령 3단락, Subversion(`nohead-svn.html:20-25`)은 `svn co <주소> --username <로그인ID>`와 첫 커밋 예시 하나(SSH 전환 없음).
- 스크립트 `code.Nohead.js`: 창이 포커스를 받으면 즉시 한 번, 그 뒤 5초마다 `GET code/HEAD/!/`(상대 주소, 페이지가 `/o/p/code`일 때 `/o/p/code/HEAD/!/`)를 보내 성공(JSON `200`)하면 `document.location.reload()`. 블러에서 멈춘다. 저장소가 비어 있는 동안 메타 응답이 `404`라 계속 대기한다. 비구성원도(안내 블록이 없어도) 폴링한다.
- 권한: 읽기(`/code`의 `@IsAllowed(READ)`와 구성원 제한). 안내 블록은 프로젝트 `UPDATE` 권한자(매니저, 조직 관리자, 사이트 관리자)만.

---

## 3. 커밋 이력(history)

### 3.1 화면 개요

- `K:code/CommitHistoryController.kt:62-164`. 모든 경로 `@CodeAccessCheck`. `page`(0부터)가 숫자가 아니거나 음수이면 `400`(`error.badrequest`, 메뉴 `CODE`). 커밋이 없거나 풀 수 없는 리비전이면 `404`(`error.notfound`). 한 쪽 25개(`PAGE_SIZE`), 최근이 위.
- 모델: `title="title.commitHistory"`, `git`(숫자 리비전이 아닌 종류), `rows`, `hasPath`(경로를 정함), `rootUrl`, `crumbs`, `branchGroups`, `filesTabUrl`, `commitsTabUrl`, `branchesTabUrl`, `tagsTabUrl`, `newerUrl`(`page>0`), `olderUrl`(마지막 커밋에 부모가 있을 때).
- 레이아웃 `T:code/history.html:15-97`: 경로를 정하지 않았으면 오른쪽 위 브랜치 select와 탭(`Files`, `Commits`(활성), `git`이면 `Branches`, `Tags`). 경로를 정했으면 이동 경로(`#breadcrumbs`)만 보이고 select와 탭은 없다. 표 `table.code-table.commits`(열: `@`, 메시지, 경로가 있으면 `코드 보기` 열, 날짜, 작성자), 아래 `이전`, `다음` 단추.
- 빈 상태: 행이 없으면 `code.nocommits` 한 줄(실제로는 컨트롤러가 먼저 `404`).

### 3.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 브랜치 `#branches` | select2 | `!hasPath` | change에서 `location.href = evt.val`(값은 `.../commits/<ref>/`) | GET | 전체 이동. `evt.val` 문제는 발견 code-F1 | 읽기 | `T:code/history.html:24-33,107-109` |
| 복사 `button.btn-copy-commitId` | 단추 | 행마다 | 클릭이면 `yona.Clipboard.copy(data-commitid)` 후 성공이면 토스트 `code.copyCommitId.copied` 1초(`#history`에 위임) | 없음 | | | `:58-60,112-118` |
| 커밋 번호 링크 | 링크 | 행마다 | `GET .../commit/<ID>[?branch=<브랜치>[&path=<경로>#<경로의 /와 .를 -로>]]` | | 커밋 보기 | 읽기 | `:61`, `K:CommitHistoryController.kt:127-135` |
| 메시지 링크, 댓글 수 | 링크, 표시 | 행마다 | 첫 줄(이스케이프 후), 댓글이 있으면 `yobicon-comments` 수 | | | | `:63-66` |
| 서명 배지 | 표시 | 서명이 있는 커밋(Git) | `gpg-badge`(검증 `gpg-verified bg-success`, 아니면 `gpg-unverified bg-secondary`, 툴팁은 이유 메시지) | | | | `:66`, 8.3 |
| `…` `button.moreBtn` | 단추 | 메시지가 여러 줄 | 다음 `pre.commitMsg.desc`의 `hidden` 전환(문서 준비 때 한 번 묶음) | JS만 | | | `:67-68,103-105` |
| `코드 보기` | 링크 | `hasPath` | `GET .../code/<짧은 ID>/<경로>` | | | 읽기 | `:70-72`, `K:...:138` |
| 작성자 | 표시 | 행마다 | 가입 사용자면 아바타 링크(툴팁 로그인 ID, 기본 아바타가 아니면 이름 대체), 가입하지 않았으면 이메일 툴팁 아바타(Gravatar), 이메일 없고 이름만 있으면 이름, 다 없으면 `user.notExists.name` | | | | `:74-84` |
| `이전`/`다음` | 링크 | `newerUrl`/`olderUrl` | `?page=<n>` 이동 | | | | `:91-94` |

### 3.3 단축키

`history.html:120-129`가 `yona.ShortcutKey.setKeymapLink`로 `A`(newerUrl), `S`(olderUrl)를 등록한다(URL이 있을 때만). 입력란에 초점이 있으면(`bFormInput`) 무시, 아니면 `document.location.href`로 이동한다(`S:esm/ShortcutKey.js:253-268`). `A`는 전역 "내 이슈"와 같은 키이고 나중에 등록한 쪽이 이기는데 전역이 나중이라 `A`는 전역이 이긴다(이미 기록됨: [architecture/ui-shortcuts.md](../architecture/ui-shortcuts.md) 표의 `A, S`, R-147).

### 3.4 경계 값

한 쪽 25개, `page`는 0부터. 짧은 ID는 Git 7자, Mercurial 12자(`D:vcs/VcsType.kt` `shortIdLength`). 날짜 열 이름은 Git이면 `code.authorDate`, 아니면 `code.commitDate`.

---

## 4. 커밋 보기(diff.html, Git과 Mercurial)

### 4.1 화면 개요

- `K:code/CommitViewController.kt:91-135`: `@CodeAccessCheck`. 커밋이 없으면 `404`(`error.notfound.commit`), diff를 못 만들면 `404`. 쿼리 `branch`, `path`는 "목록으로" 링크 계산에만 쓰인다(기본 `HEAD`).
- 모델: `title="커밋 @<ID>"`, `commit`(작성자 정보), `signature`, `shortMessageHtml`, `moreMessageHtml`, `diffHtml`, `nonRangedHtml`, `canComment`(프로젝트에 `COMMIT_COMMENT` 만들기), `commentAction`(`/commit/<ID>/comments`), `cards`, `openCount`, `closedCount`, `watching`, `watchUrl`, `unwatchUrl`, `listUrl`, `mentionUrl`(`mentionListAtCommitDiff?commitId=`), 탭 URL 4개.
- 레이아웃 `T:code/diff.html:16-109`: 탭(`Files`, `Commits`(활성), `Branches`, `Tags`), `.codediff-wrap` 안 왼쪽 `.diffs-wrap`(커밋 정보, `.diff-body`(diff + `.btnPop`), `.board-comment-wrap`(파일 단위 스레드, 댓글 폼), 줄 댓글 팝업 폼), 오른쪽 `.review-wrap`(카드). 바깥 아래에 `#watch-button`(왼쪽), `목록` 링크(`float-end`).
- 반응형: [code-diff-script.md 1절](code-diff-script.md)의 CSS 값을 따른다(`.diffs-wrap`의 `margin-right:282px`, 720px 이하에서 해제).
- 변경 탭과 달리 카드 영역은 **스레드가 없어도 항상** 그려진다.

### 4.2 동작 요소 표

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 작성자 줄 | 표시 | 항상 | 가입 사용자면 아바타 링크와 이름, 아니면 아바타(이메일 Gravatar)와 이름, 시각(툴팁 정확한 시각) | | | | `T:code/diff.html:29-43` |
| 서명 상자 `.gpg-signature-box` | 표시 | `signature != null` | 배지, `서명자`(`signer`), 키 ID 또는 SSH 지문(`format == ssh`이면 `sshKey.fingerprint`) | | | | `:45-49` |
| 커밋 메시지 | 표시 | 항상 | 첫 줄 `.commitMsg.short`(비면 `code.commitMsg.empty`), 나머지 `pre.commitMsg.desc`(항상 보임). 자동 링크 적용 후 이스케이프 | | | | `:50-54`, `K:...:231-239` |
| `@<전체 ID>` | 표시 | 항상 | | | | | `:54` |
| diff 본문 | 표시 | 항상 | [code-diff-script.md](code-diff-script.md) | | | | `:57-60` |
| 파일 단위 스레드, 댓글 폼 `#comment-form` | 영역, 폼 | `canComment`이면 폼, 아니면 비활성 상자 | 커밋 전체(파일 단위) 댓글. 첨부 가능(로그인) | `POST .../commit/<ID>/comments` multipart(`contents`, `path`?, 범위 필드?) | `303`이 댓글 위치(`ReviewLinks`)로. 내용 비면 `400`, 커밋 없음 `404`, 스레드가 다른 프로젝트 `400` | 로그인, 프로젝트에 `COMMIT_COMMENT` 만들기(공개 프로젝트면 로그인 사용자 모두) | `:62-65`, `K:ReviewCommentController.kt:73-93` |
| 줄 댓글 팝업 | 폼 | `canComment` | `단일 댓글 쓰기`만(`pendingCount`가 `null`이라 임시 저장 단추 없음). 팝업 상세는 [code-diff-script.md 4절](code-diff-script.md) | 위와 같음 | | | `:67-69` |
| 리뷰 카드 | 링크 | 항상 | 열림, 닫힘 탭. 카드 `a.review-card.<state>`는 `#thread-<id>`로 이동. 카드의 댓글 수는 스레드 댓글이 2개 이상일 때 **전체 댓글 수**(변경 탭 카드는 답글 수) | | | | `:72-102`, `K:...:241-246` |
| `#watch-button` | 토글 단추 | 항상 보임(조건 없음) | 클릭이면 `active`면 `unwatchUrl`, 아니면 `watchUrl`로 `$yona.sendForm`(POST). 성공이면 `active ybtn-watching` 토글. 토스트 없음 | `POST /watch?resource.type=commit&resource.id=<프로젝트번호>:<커밋ID>` / `/unwatch` | `200`. 익명 `403`(`Anonymous cannot watch it.`) | 로그인, `READ` | `:106`, `K:...:131-132`, `K:WatchController.kt:40-60`, `S:code.Diff.js:231-241` |
| `목록` | 링크 | 항상 | `GET .../commits/<브랜치>/<경로>` | | | 읽기 | `:107` |
| 스레드 상태 `열기`/`닫기`, 댓글 고치기와 삭제 | | | 7절과 8절 | | | | |

### 4.3 스크립트

`T:code/diff.html:110-136`: `common/markdown`, 삭제 모달(컨테이너 `#code-browse-wrap`), `common/select2`, `CodeCommentBlock.js`, `CodeCommentBox.js`, jQuery UI, `code.Diff.js`, `review.CommentEdit.js`. `$yona.loadModule("code.Diff", {bCommentable: <canComment>, sWatchUrl, sUnwatchUrl})`(구독 주소의 `&`는 `&amp;`로 치환해 넘기고 `sendForm`이 풀어 쓴다). `yona.Mention`을 두 번 부르는데 두 번째의 `target:'review-editor'`는 요소가 없어 아무 일도 하지 않는다(발견 code-F4). 이 화면의 파일 머리 접기 처리기는 없다(발견 code-F8). 단축키 없음.

---

## 5. Subversion 커밋 보기(svn-diff.html)

- `K:code/CommitViewController.kt:140-175`. 모델: `commit`(맵), `patch`(원본 패치 글), `comments`, `canComment`, `commentAction`, `watching`, `watchUrl`, `unwatchUrl`, `listUrl`, `mentionUrl`, `filesUrl`, `commitsUrl`, `branches`, `parentId`.
- 레이아웃 `T:code/svn-diff.html:17-96`: 오른쪽 위 `div#branches.btn-group`(부트스트랩 드롭다운, 토글 글자는 항상 `HEAD`: 조건식의 두 갈래가 같다, 발견 code-F5), 탭(`Files`, `Commits`(활성)만), 커밋 정보(작성자, 시각, `@ID`, **댓글이 있으면** `#toggle-comments` 체크박스), 메시지, `div.diff-wrap > #commit.diff-body.hide`(패치 글이 텍스트로 들어 있고 JS가 표로 바꿈), 댓글 목록 `ul.comments`, 댓글 폼, `#watch-button`, `목록`, 우측 고정 미니맵 `#minimap`.
- 댓글 줄 `li.comment#comment-<번호>[data-path, data-side, data-line]`: 아바타, 로그인 ID 링크, 시각, 삭제 단추(`c.deletable`: 해당 댓글 `DELETE` 권한, `data-toggle=comment-delete`, `data-request-uri=/commit/<ID>/comments/<번호>/delete`, 삭제 모달), 본문(`th:utext`), 첨부.
- 요청: 댓글 등록 `POST .../commit/<ID>/comments`(Subversion 분기: 커밋 리소스 아래 `COMMIT_COMMENT` 만들기, `path`, `line`, `side` 선택; `303 .../commit/<ID>#comment-<번호>`), 삭제 `DELETE .../commit/<ID>/comments/<번호>/delete`(`@IsAllowed(DELETE, COMMIT_COMMENT)`, `303` 커밋 보기, 없는 댓글 `404`).
- 스크립트 `code.SvnDiff.js`(`T:...:110-127`)의 동작은 [code-diff-script.md 7절](code-diff-script.md).
- 구독: `#watch-button`은 `active`만 토글. 서버 대상은 `svnCommitWatchers`.

---

## 6. 두 커밋 비교(compare, compare-svn)

- `K:code/CommitViewController.kt:195-216`: `@IsAllowed(READ)`만(코드 접근 제한 검사 없음, 발견 code-F3). 두 커밋 중 하나라도 없으면 `404`(`error.notfound.commit`). 제목 `<A>..<B>`.
- `compare.html`(Git, Mercurial): `p.commitInfo > strong.commitId`(`@A..B`), 변경이 없으면 `alert-warning`(`code.noChanges`), 있으면 `div.diff-body.discommentable`(diff HTML). `code.Diff`를 `{bCommentable:false}`로 불러온다. `.codediff-wrap`이 없어 리뷰 카드, 접기 처리기는 모두 빈 선택이라 동작하지 않는다. 줄 번호의 말풍선 아이콘 숨김 처리도 빈 선택에 걸려 일어나지 않는다. 이 화면의 diff에는 스레드가 없다(`threads = emptyList()`).
- `compare-svn.html`: 같은 머리 + 패치 글(`#commit.diff-body.hide`) + `code.SvnDiff`를 `{bCommentable:false, sParentCommitId, sCommitId, sTplFileURL, sTplRawURL}`로 호출. **`sCodeURL`을 넘기지 않아** 파일 링크가 `undefined/<리비전>/<경로>`가 된다(발견 code-F2). 변경이 없으면 `alert-warning`.
- 타임라인에서 "추가 변경"(`/compare/<이전>..<새>`)으로 들어온다([pullrequest.md 2.6](pullrequest.md)).

---

## 7. 리뷰 댓글 요청(코드, 풀 리퀘스트 공통)

`K:code/ReviewCommentController.kt`. 아래는 `partial-comment-thread`, 댓글 폼, 삭제 모달이 부르는 요청이다.

| 요청 | 본문, 파라미터 | 권한(서버) | 응답 |
|---|---|---|---|
| `POST /:user/:project/commit/:commitId/comments` | `contents`(필수), `thread.id`(선택), 범위(`path`, `startSide`, `startLine`, `startColumn`, `endSide`, `endLine`, `endColumn`: 줄과 열 네 값이 모두 숫자이면 코드 범위 스레드, 아니면 파일 단위) | `@IsCreatable(COMMIT_COMMENT)` | `303 <댓글 위치>`. `contents` 비면 `400`(메뉴 `PROJECT_HOME`), 커밋 없음 `404`, 스레드 불일치 `400`(메뉴 `CODE`) |
| `POST /:user/:project/pullRequest/:pullRequestId/comments[?commitId=]` | 위 + `pending=true`(임시 저장). `commitId`는 쿼리 문자열만 읽는다 | `@IsCreatable(REVIEW_COMMENT)`, `@RequiresVcs(PULL_REQUESTS)` | `303 .../pullRequest/<번호>/changes[/<커밋>]#comment-<번호>`. 첨부 flash |
| `POST /threads/:id/open`, `/close` | 없음 | 스레드에 `REOPEN`, `CLOSE`(작성자, 매니저, 구성원, 조직 구성원, 조직 관리자, 사이트 관리자) | `200` 빈 본문. 없으면 `404`, 권한 없음 `403`(익명도 `403`) |
| `POST /comments/review_comment/:id/edit` | `contents` | 로그인. 공개 댓글 `UPDATE`, 임시 댓글 작성자 | `200 {id, edited, bodyHtml}`, 권한 없음 `403`, 공백 `400`, 없는 임시 댓글 `404` |
| `DELETE /comments/review_comment/:id`, `DELETE /comments/code_comment/:id` | 없음 | 댓글 `DELETE`(작성자, 매니저, 조직 관리자, 사이트 관리자. 일반 구성원은 불가, D-48) | `200` 빈 본문. 종류 모름, 없는 대상 `400`, 권한 없음 `403` |
| `POST <PR>/pendingReview/comments/:commentId/delete` | 없음 | 로그인, 내 임시 댓글만 | `303 <PR>/changes`, 아니면 `404` |

화면 쪽 조건은 `K:code/ThreadViewAssembler.kt`가 정한다: 스레드 `열기/닫기` 단추는 스레드에 `UPDATE`(서버는 `REOPEN/CLOSE`, 규칙상 같은 사람들), 답글 입력은 풀 리퀘스트 스레드면 `REVIEW_COMMENT`, 커밋 스레드면 `COMMIT_COMMENT`를 만들 수 있을 때, 삭제 단추는 댓글 `DELETE`, 고치기 단추는 공개 댓글 `UPDATE`나 임시 댓글 작성자일 때(`:51-76`). 응답이 모두 `Request.js`의 새로 고침으로 끝난다(`열기/닫기`, `임시 삭제`, 모달 삭제).

---

## 8. 스레드 조각과 서명 배지

### 8.1 `thread-wrapper.html`

`ThreadHtmlRenderer`가 스레드마다 문맥(`thread`, `full`)을 주어 호출한다. `thread.code`이거나 `full`이면 `partial-comment-thread :: thread`, 아니면 `:: nonRanged`를 그린다. 커밋 보기의 파일 단위 스레드는 `nonRanged`, 풀 리퀘스트 변경 탭은 `full=true`라 `thread`다.

### 8.2 `thread` 조각(`T:code/partial-comment-thread.html:9-60`)

| 요소 | 조건 | 동작 |
|---|---|---|
| 루트 `div#thread-<id>.comment-thread-wrap.<open\|closed>[.fold][.thread-pending]` | 항상 | `data-state`, 코드 범위이면 `data-toggle="CodeCommentThread"`와 `data-range-path/-startSide/-startLine/-startColumn/-endSide/-endLine/-endColumn`(JS가 읽음). 닫힌 코드 스레드는 `fold` 클래스로 시작 |
| `.btn-thread-here.btn-thread-minimize > button`(`yobicon-post2`) | 항상 DOM에 있고 `fold`일 때만 CSS로 보임 | 접힌 스레드를 폄 |
| `.thread-header` | 항상 | 상태 배지(`issue.state.open/closed`), 임시이면 `임시` 배지, 접기 단추 `btn-thread-minimize`(`yobicon-maximize`) |
| 댓글 `li.comment#comment-<id>[.comment-pending]` | 댓글마다 | 아바타, 로그인 ID 링크, 시각 링크(`#comment-<id>`), `(수정됨)`, 임시 댓글이면 배지(스레드 자체가 임시가 아닐 때) |
| 고치기 `button.comment-edit[data-url]` | `c.editUrl != null` | [pullrequest.md 3.5](pullrequest.md) |
| 삭제 `[data-toggle=comment-delete]` | 공개 댓글이고 `deleteUrl != null` | 삭제 모달(컨테이너 `Comment.js`가 지정한 `#changes`, `#code-browse-wrap` 안에서 위임) → `DELETE /comments/review_comment/<id>` |
| 임시 댓글 삭제 `button[data-request-method=post]` | `deleteUrl != null`이고 임시 | `POST <PR>/pendingReview/comments/<id>/delete`(확인 모달 없음) |
| 본문, 숨은 `textarea.comment-raw`(고치기 가능할 때), 첨부 | 항상 | 본문은 마크다운 렌더링 HTML. 첨부는 `data-attachments` |
| 답글 영역 `replyArea` | `thread.form != null`이면 폼(`form.review-form`이 `display:block` 인라인, 숨은 `thread.id`), 아니면 `p.thread-actrow`에 상태 단추만 | 폼에는 아바타(`hide-in-mobile`), 편집기(`contents`, 높이 100px), 첨부(`!anonymous`), `열기/닫기`(`stateUrl`, `data-request-method=post`), `리뷰에 추가`(`f.pendingButton`: PR 스레드이고 임시가 아님, `name=pending value=true`), `댓글 쓰기`(또는 `pendingButton`이면 `단일 댓글`) |

### 8.3 `nonRanged` 조각(`:62-91`)과 서명 배지

`nonRanged`는 상태 배지, 접기 단추, 임시 표시, 고치기가 **없다**. 삭제 단추는 `data-request-method=delete`로 **확인 모달 없이** 바로 `DELETE`한다(발견 code-F14). 아이콘은 `yobicon-comments`.

서명 배지 `partial-signature.html:5-7`: `b == null`이면 아무것도 그리지 않는다. `span.gpg-badge.badge`(`gpg-verified bg-success` 또는 `gpg-unverified bg-secondary`), `data-reason`, `data-format`, 툴팁은 `gpgKey.reason.<이유>` 또는 `sshKey.reason.<이유>`, 글자 `gpgKey.verified/unverified`. 쓰이는 곳: 이력 행, 커밋 보기 서명 상자, 태그 줄, 변경 탭 커밋 정보.

---

## 9. 브랜치 목록(branches)

- `K:code/BranchController.kt:65-111`. 클래스에 `@RequiresVcs(BRANCHES)` → `@AnonymousCheck`, 메서드 `@CodeAccessCheck`. 기본 브랜치가 없으면 `404`(메뉴 `code`). Git과 Mercurial.
- 모델: `filesTabUrl`, `commitsTabUrl`, `showActions`(`DELETE` 또는 `UPDATE` 권한), `rows`(첫 줄이 기본 브랜치, 나머지는 머리 커밋이 최근인 순).
- 요소(`T:code/branches.html:17-61`): 탭(`Files`, `Commits`, `Branches`(활성), `Tags`: 탭 링크는 이 템플릿에 고정 주소), 표(열 `브랜치`, `커밋`, `풀 리퀘스트`, `showActions`이면 빈 머리 열).

| 요소 | 조건 | 동작 | 요청 | 응답 | 권한 |
|---|---|---|---|---|---|
| 브랜치 이름 링크 + `기본 브랜치` 표시 | 행마다 | `GET .../code/<ref>` | | | 읽기 |
| 커밋 링크(짧은 ID, 툴팁 전체 ID) + 시각(툴팁) | 행마다 | `GET .../commits/<ref>/` | | | |
| 풀 리퀘스트 | 있으면 `pullrequest-<번호>` 링크(상태 클래스, 툴팁 상태), 없으면 `code.branches.noPullRequest` | `GET .../pullRequest/<번호>` | | | |
| `기본으로 설정` `button[data-request-method=post]` | `setDefaultUrl != null` = `UPDATE` 권한이고 기본이 아닌 행 | `POST .../code/<ref>/setAsDefault` | XHR이면 `200`, 아니면 `303 .../branches`. `Request.js`가 새로 고침 | `UPDATE` |
| `삭제` `a.branch-delete[data-request-method=delete][data-confirm]` | `deleteUrl != null` = `DELETE` 권한, 기본이 아님, 삭제를 막는 규칙 묶음이 없거나 모두 우회 가능 | 캡처 단계 `click` 처리기(`T:...:65-71`)가 `confirm(code.branches.deleteConfirm(<이름>))`을 먼저 묻고 취소면 `preventDefault`+`stopImmediatePropagation`. 확인하면 `DELETE .../code/<ref>/` | `303 .../branches`(새로 고침). 규칙이 삭제를 막으면 `403` | `DELETE` + 규칙 |
| `…`(`button.moreBtn`) | 이 화면에는 대상 요소가 없다 | 처리기만 있음(`:73-77`) | | | |

`href`가 `DELETE` 주소라 JS 없이 열면 `GET`이 되어 `405`(발견 code-F13). 브랜치 이름은 참조 전체 이름을 두 번 인코딩해 주소에 넣는다.

---

## 10. 태그 목록(tags)

- `K:code/TagController.kt:62-102`(추가 항목 X-18). `@RequiresVcs(TAGS)`, `@CodeAccessCheck`. 목록은 태그 시각이 최근인 순.
- 모델: `filesTabUrl`, `commitsTabUrl`, `showActions`(태그 관리 가능 종류이고 프로젝트 `DELETE`), `rows`.
- 요소(`T:code/tags.html:16-61`): 탭, 표(`태그`, `커밋`, `showActions`이면 빈 머리 열). 이름은 `commitUrl != null`이면 링크(`.../code/<ref>`), 아니면(커밋이 아닌 대상을 가리킴) 글자만. `주석`(annotated)/`경량` 배지, 서명 배지, 메시지(`tag-message`, 툴팁 전체), 태거와 시각. 커밋 열: 짧은 ID 링크(`/commit/<ID>`)와 시각, 없으면 `code.tags.notCommit`. 태그가 하나도 없으면 `p.no-tags`(`code.tags.empty`).
- `삭제` `a.tag-delete[data-request-method=delete][data-confirm]`(`deleteUrl != null`일 때만): 캡처 단계에서 `confirm(code.tags.deleteConfirm(<이름>))` 후 `DELETE .../tags/<이름>`(이름은 두 번 인코딩, 슬래시 가능). 응답 `303 .../tags`, 없는 태그나 태그가 아닌 이름 `404`, 종류가 태그를 관리하지 못하면 `400`. 권한 프로젝트 `DELETE`(매니저, 조직 관리자, 사이트 관리자만, 11절).

---

## 11. 권한 행렬(코드 영역)

| 기능 | 비로그인 | 로그인 비구성원(공개) | 구성원 | 매니저 | 사이트 관리자 |
|---|---|---|---|---|---|
| 코드 보기, 이력, 커밋, 비교(공개 프로젝트) | R | R | R | R | R |
| 위 기능(코드 구성원 제한 프로젝트) | -(`403`, 익명은 로그인 화면) | -(`403`) | R | R | R |
| `files/`, `compare/`(제한 프로젝트) | 읽기 권한만 있으면 R | R | R | R | R |
| 새 파일, Edit 링크 | -(숨김) | 보임(로그인만 조건). 서버(`postform`)는 다른 영역 | R | R | R |
| 커밋 댓글, 줄 댓글 | - | R(공개 프로젝트) | R | R | R |
| 스레드 열기와 닫기 | - | 자기 스레드만 | R | R | R |
| 댓글 삭제(공개) | - | 자기 것 | 자기 것 | R | R |
| 브랜치 기본 설정, 브랜치와 태그 삭제 | - | - | -(`403`) | R | R |
| nohead 안내 블록(`canUpdate`) | - | - | - | R | R |

근거: `src/main/kotlin/dev/yona/access/AccessControl.kt:151-166`. 프로젝트 자체(`ResourceType.PROJECT`, `project.asResource()`는 `GlobalResource`)의 `UPDATE`, `DELETE`는 **프로젝트 매니저, 조직 관리자, 사이트 관리자**만 통과시킨다. 일반 구성원은 통과하지 못한다. 브랜치 목록의 `showActions`, 태그 목록의 `showActions`, nohead의 `canUpdate`, `@IsAllowed(UPDATE/DELETE)`가 모두 이 같은 판정을 쓰므로 화면과 서버는 일치한다. 삭제는 브랜치에 한해 규칙 묶음(`pushViolation(DELETE)`)이 더 막을 수 있다.

---

## 12. 발견 사항(code-F)

| 번호 | 구분 | 내용 | 근거 |
|---|---|---|---|
| code-F1 **[수정됨, A-457]** | 스크립트(코드 읽기, 실행 확인 안 함) | 이력 화면의 브랜치 select는 `evt.val`을 읽어 이동한다. select2 4.1.0은 일반 jQuery `change`만 내보내 `val` 속성이 없다(이 저장소의 `ui/Select2.js`에도 `val`을 채우는 코드가 없음). 코드 보기는 인라인(`$(this).val()`)이 맞게 이동하지만 `code.Browser`(`weEvt.val`)가 같은 이벤트에 `location.href = undefined`를 또 설정한다. `code.Diff`의 `#branches` 처리기도 같은 `weEvt.val`이나 그 화면에 `#branches` select가 없어 동작 대상이 없다. 시험 없음(`CommitHistoryTest`는 option 값만 확인) | `T:code/history.html:107-109`, `T:code/view.html:157-159`, `S:esm/service/code.Browser.js:105-107`, `S:esm/service/code.Diff.js:117-119`, `S:esm/ui/Select2.js`, `lib/select2/select2.js:2022` |
| code-F2 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 스크립트 | `compare-svn`은 `code.SvnDiff`에 `sCodeURL`을 넘기지 않는데 `_makeCommitLink`는 `sCodeURL`로 링크를 만든다. 넘기는 `sTplFileURL`, `sTplRawURL`은 읽기만 하고 쓰지 않는다 | `T:code/compare-svn.html:26-35`, `S:esm/service/code.SvnDiff.js:36-46,396-403` |
| code-F3 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 화면과 서버 | `files/<rev>/<path>`와 `compare/<A>..<B>`는 `@IsAllowed(READ)`만 있어 코드 접근을 구성원으로 제한한 프로젝트의 비구성원도 서버에서는 허용한다. 화면의 코드 화면에는 들어갈 수 없다. 위키는 `files`의 "읽기 권한만"을 이미 적었다 | `K:code/CodeBrowserController.kt:178-186`, `K:code/CommitViewController.kt:195-197` |
| code-F4 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 죽은 코드 | `code/diff.html`의 두 번째 `yona.Mention({target:'review-editor'})`는 그런 요소가 없어 아무 일도 하지 않는다 | `T:code/diff.html:130-133` |
| code-F5 **[검증: v2 신규 결함, 수정됨 A-460]** | 마크업 | `svn-diff.html`의 브랜치 드롭다운 토글 글자는 `isEmpty ? 'HEAD' : 'HEAD'`로 양쪽이 같다(선택한 브랜치를 반영하지 않음) | `T:code/svn-diff.html:20` |
| code-F6 **[검증: 문서 문제]** | 문서 불일치 | `NoheadViews`의 KDoc은 "코드 화면과 커밋 이력 화면이 함께 쓴다"고 하나 호출은 `CodeBrowserController` 한 곳뿐이고 이력 화면은 빈 저장소에서 `404`다 | `K:code/NoheadViews.kt:18-21`, `K:code/CommitHistoryController.kt` |
| code-F7 **[검증: v2 추가 기능의 결함, 수정됨 A-460]** | 저장소 종류 | Mercurial에서 파일 리비전은 `substring(f.revId,0,7)`(7자)인데 짧은 ID 길이는 12자(A-378)다. 브랜치 `기본으로 설정` 단추는 `UPDATE` 권한만 보고 보이며 Mercurial은 `DEFAULT_BRANCH`를 지원하지 않는다(서버도 `BRANCHES` 지원만 확인). 실제 동작은 확인하지 못함(추측) | `T:code/view.html:98`, `D:vcs/VcsType.kt:50-60`, `K:code/BranchController.kt:100,125-133` |
| code-F8 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 화면 일관성 | 파일 머리(`.diff-partial-meta`)를 눌러 파일 diff를 접는 처리기는 PR 변경 탭의 인라인에만 있고 커밋 보기, 비교 화면에는 없다 | `T:pullrequest/changes.html:176-178`, `T:code/diff.html` |
| code-F9 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 계약 | 줄 댓글 팝업은 서버가 쓰지 않는 숨은 필드(`startType`, `endType`, `pathA`, `pathB`, `prevCommitId`, `commitId`, `filePath`)도 보낸다. 서버는 범위 7개 필드만 읽고, PR의 `commitId`는 쿼리 문자열에서만 읽는다 | `S:esm/CodeCommentBlock.js:_setBlockDataBySelection`, `K:code/ReviewCommentController.kt:195-203` |
| code-F10 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 죽은 코드 | 스레드 조각에는 `button.btn-thread`(답글 열기)가 없다. 그래서 `code.Diff`의 `_onClickBtnReplyOnThread`와 `CodeCommentBox`의 `thread-id` 경로(`welTarget.data("thread-id")`)는 Git 화면에서 실행되지 않는다. 답글 폼은 처음부터 `display:block`이다. `btn-thread`는 `svn-diff.html`의 mustache 템플릿에만 있다 | `T:code/partial-comment-thread.html:94-120`, `S:esm/service/code.Diff.js:308,325-355` |
| code-F11 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 마크업 | 미니맵 요소는 `svn-diff.html`에만 있다. Git 화면의 `code.Diff`는 미니맵 계산을 하지만 `#minimap`이 없어 효과가 없다(스타일 `.minimap-outer`는 `app/assets/stylesheets/scss/_page.scss:4452`) | `T:code/svn-diff.html:89-94`, `S:esm/service/code.Diff.js:68-93,597-639` |
| code-F12 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 계약 | 리뷰 카드의 댓글 수가 커밋 보기는 "댓글이 2개 이상이면 전체 수", 변경 탭은 "전체 수 - 1(답글 수)"로 다르다 | `K:code/CommitViewController.kt:241-246`, `K:pullrequest/PullRequestPageAssembler.kt:328-336` |
| code-F13 **[검증: 결함 아님]** | 화면 | `a[data-request-method=delete]`의 `href`가 `DELETE`(또는 `POST`) 전용 주소라 JS가 처리하지 못하면 `GET`이 되어 `405`가 된다(브랜치 삭제, 태그 삭제) | `T:code/branches.html:53`, `T:code/tags.html:53` |
| code-F14 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 화면 일관성 | 파일 단위 스레드의 삭제는 확인 모달 없이 즉시 `DELETE`하고(`nonRanged`), 코드 스레드의 삭제는 모달을 거친다 | `T:code/partial-comment-thread.html:80-82` 대 `:43-45` |
| code-F15 **[검증: v1.6 결함 유지, 수정함(A-462, 2026-11-11)]** | 메시지 | 일본어 번들에 이 영역 키 21개가 없다(`code.authorDate`, `code.copyCommitId`, `code.download`, `code.new.file`, `code.open`, `code.open.desc`, `code.viewRaw`, `code.tooBigFileForCodeBrowser`, `code.looseFileSizeLimitForCodeBrowser`, `commentThread.close/open`, `common.comment.delete/edit` 등). 기본 번들(영어)로 대체된다. 한국어와 기본 번들은 모두 있다 | `src/main/resources/messages_ja_JP.properties` |
| code-F16 **[검증: 문서 문제]** | 권한 해석(위키 보완) | 브랜치, 태그 삭제와 기본 브랜치 설정은 "삭제 권한", "수정 권한"이라고만 적혀 있으나 실제로는 프로젝트 자체의 `DELETE`, `UPDATE`라 일반 구성원은 불가이고 매니저, 조직 관리자, 사이트 관리자만 가능하다 | `K:code/BranchController.kt:80-81,113-133`, `K:code/TagController.kt:66-67,92-95`, `src/main/kotlin/dev/yona/access/AccessControl.kt:151-166` |
| code-F17 **[검증: v1.6 결함 유지, 미수정(v1.6과 같아 지침대로 유지)]** | 계약 | 코드 보기의 `폴더 경로`는 인코딩하지 않고 파일 이름만 인코딩한다(v1.6 그대로). 브랜치와 리비전은 두 번 인코딩한다. 새 UI가 링크를 만들 때 이 규칙을 따라야 한다 | `K:code/CodePageAssembler.kt:81-98` |

---

## 13. 범위 점검(담당 템플릿 14개)

| 템플릿 | 다룬 절 |
|---|---|
| `code/view.html` | 1 |
| `code/nohead.html`, `code/nohead-hg.html`, `code/nohead-svn.html` | 2 |
| `code/history.html` | 3 |
| `code/diff.html` | 4, code-diff-script.md |
| `code/svn-diff.html` | 5, code-diff-script.md 7절 |
| `code/compare.html` | 6 |
| `code/compare-svn.html` | 6 |
| `code/branches.html` | 9 |
| `code/tags.html` | 10 |
| `code/partial-comment-thread.html` | 8.2, 8.3 |
| `code/thread-wrapper.html` | 8.1 |
| `code/partial-signature.html` | 8.3 |
