---
title: 화면 동작 계약, 코드 비교(diff) 화면의 스크립트
type: ui-contract
sources:
  - src/main/resources/static/assets/javascripts/esm/service/code.Diff.js
  - src/main/resources/static/assets/javascripts/esm/service/code.SvnDiff.js
  - src/main/resources/static/assets/javascripts/esm/CodeCommentBlock.js
  - src/main/resources/static/assets/javascripts/esm/CodeCommentBox.js
  - src/main/kotlin/dev/yona/web/code/DiffHtmlRenderer.kt
  - src/main/kotlin/dev/yona/web/code/ThreadViewAssembler.kt
  - src/main/resources/templates/code/diff.html
  - src/main/resources/templates/code/svn-diff.html
  - src/main/resources/templates/code/compare-svn.html
  - src/main/resources/templates/pullrequest/changes.html
  - src/main/resources/templates/common/review-form.html
  - app/assets/stylesheets/scss/_page.scss
  - app/assets/stylesheets/scss/_responsive.scss
updated: 2026-11-11
---

# 코드 비교 화면의 스크립트 동작

커밋 보기(`code/diff.html`), 풀 리퀘스트 변경 탭(`pullrequest/changes.html`), Subversion 커밋 보기(`code/svn-diff.html`)가 공유하는 복잡한 동작이다. 화면별 요소와 요청은 [code.md](code.md), [pullrequest.md](pullrequest.md)에 있다. 근거 표기: `S:`=`src/main/resources/static/assets/javascripts/`, `T:`=`src/main/resources/templates/`, `K:`=`src/main/kotlin/dev/yona/web/`, `C:`=`app/assets/stylesheets/scss/`. 모듈 시험은 `src/test/js/codecommentblock.test.mjs`, `codecommentbox.test.mjs`, `svndiff.test.mjs`가 있고 `code.Diff` 자체를 시험하는 파일은 없다.

## 1. 레이아웃과 CSS 계약

| 요소 | 규칙 | 근거 |
|---|---|---|
| `.codediff-wrap` | `position:relative`. 이 아래에서 스크립트가 모든 것을 찾는다 | `C:_page.scss:4173` |
| `.diffs-wrap` | 왼쪽 본문. `margin-right:282px` | `:4176-4178` |
| `.review-wrap` | 오른쪽 카드 영역. 절대 위치(`top/right/bottom:0`), 폭 260px | `:4191-4196` |
| `.review-container` | `position:sticky; top:10px`, `max-height:calc(100vh - 20px)`, flex 세로. 안의 `.review-list`가 `overflow:auto`로 스크롤(스크립트가 높이를 계산하지 않음, D-124) | `:4198-4215` |
| `.btn-hide-reviewcards` | `position:absolute; right:0`(카드 영역 안) | `:4217` |
| `.btn-show-reviewcards` | 기본 `display:none` | `:4222` |
| `.codediff-wrap.diffs-only` | `.diffs-wrap{width:100%}`, `.review-wrap` 숨김, 숨기기 단추 숨김, 펴기 단추는 `display:block; position:absolute; top:0; right:-52px; z-index:10` | `:4226-4237` |
| `.btnPop` | `position:absolute; display:none`(`.diff-body` 안). JS가 `show()`와 `top`, `left`를 정함 | `:5840` |
| `.diff-body.show-comments tr.comments`, `table.diff-container.show-comments tr.comments` | `display:table-row`. `show-comments`가 없으면 스레드 행이 숨음. 서버가 `table.diff-container`에 항상 `show-comments`를 붙여 그린다 | `:5643,6018` |
| `.review-form`(줄 댓글 팝업 `#review-form`) | 기본 `display:none`. JS가 `show()` | `:6140` 부근 |
| `.comment-thread-wrap.fold` | 헤더, 댓글, 답글 영역 숨김, 배경 없음. `.btn-thread-here`만 `display:block !important`, `position:absolute; right:0`. `.open`은 왼쪽 3px 선색 `$state-open`, `.closed`는 `$state-closed` | `:6106-6137` |
| `.comment-thread-wrap + .comment-thread-wrap` | 위 여백 10px(`fold` 뒤는 0) | `:6139` |
| `.minimap-outer` | 기본 `display:none`, `position:fixed`, 폭 15px, 높이 400px, `right:1%`, `top:192px`, 어두운 반투명, `z-index:999`. 안 `.minimap-curr`(흰 반투명, `z-index:10`), `.minimap-links a`(노랑 `rgb(255,224,0)`, 최소 높이 2px, `z-index:11`) | `:4452-4485` |
| 720px 이하 | `.codediff-wrap .diffs-wrap{margin-right:inherit}`, `.review-wrap{position:inherit}`, `.review-container{position:inherit}`(카드가 본문 아래로 쌓임), `.diff-body .diff-partial-outer{margin-left:15px}` | `C:_responsive.scss:140-165` |

## 2. diff DOM 계약(서버가 그리고 스크립트가 읽는 것)

`K:code/DiffHtmlRenderer.kt`가 문자열로 그린다. 스크립트가 쓰는 선택자는 아래뿐이므로 새 UI도 같은 구조를 주거나 스크립트를 바꿔야 한다.

- 파일 하나: `div#<경로의 /와 .를 -로 바꾼 값>.diff-partial-outer > .diff-partial-inner > .diff-partial-meta`(`.diff-partial-commit`의 `.diff-partial-commit-id`(A, B 커밋 링크, `target=_blank`), `.diff-partial-file > span.filename`) 다음 `.diff-partial-code[data-hashcode]`(`.patch-header` + `table.diff-container.show-comments[data-path-a, data-path-b, data-commit-a, data-commit-b, data-file-path]`).
- 줄: `tr.add|remove|context[data-line=<줄 번호>, data-type=add|remove|context, data-side=A|B]`. `remove`는 A쪽 줄 번호, 나머지는 B쪽 줄 번호가 `data-line`이다. 칸 3개: `td.linenum`(`i.yobicon-comments` + `div.line-number[data-line-num]` + 숨은 `span.hidden`)이 A, B쪽 두 개, `td.code > pre.diff-partial-codeline`(앞에 `+`, `-`, 공백 한 글자를 포함). 마지막 줄 줄바꿈 없음 표시는 `td.code`의 `span`.
- 구간 머리: `tr.range`(`td.hunk`에 `@@ -a,b +c,d @@`). 이진 파일 `td.isBinary`.
- 스레드: 줄 바로 다음에 `tr.comments.board-comment-wrap[data-commit-id] > td[colspan=3]` 안에 `div.comment-thread-wrap`들. 스레드가 붙는 줄은 `(path, 끝쪽(side), 끝 줄)`이 같은 줄이다. 코드 범위 스레드 조각의 `data-toggle="CodeCommentThread"`와 `data-range-*`(`code.md 8.2`).
- 파일이 너무 많으면(`DIFF_FILE_LIMIT` 2000) 맨 앞에 `p.alert`(`code.fileDiffLimitExceeded`). 파일별 오류 줄: `code.skipDiff`, `code.tooBigFile`, `code.tooBigDiff`, `code.unknownError`, `code.noChanges`.

## 3. 모듈 초기화(`code.Diff`)

호출: `$yona.loadModule("code.Diff", {bCommentable, sWatchUrl?, sUnwatchUrl?})`(`diff.html:119`, `changes.html:167`, `compare.html:24`).

초기화 순서(`S:esm/service/code.Diff.js:33-45`): 변수, 요소 찾기, 이벤트 연결, 파일 업로더(댓글 폼과 각 `form.review-form`의 `.upload-wrap`), 첨부 내려받기 목록(`.attachments`), `#toggle-comments` 체크박스(커밋 화면에는 없음), 코드 댓글(`_initCodeComment`), 미니맵, 해시 이동(`_scrollToHash`), 접힌 스레드 단추 위치.

요소 찾기: `.codediff-wrap`(없으면 모두 빈 선택: `compare.html`), `div.diffs-wrap`, `.diff-body`, `.diff-container`, `div.board-comment-wrap`, `div.review-wrap`, `div.review-container`, `button.btn-show-reviewcards, button.btn-hide-reviewcards`, `#watch-button`, `#minimap`.

연결되는 처리기:

| 대상 | 이벤트 | 동작 |
|---|---|---|
| `#watch-button` | click | `active` 클래스로 구독 상태를 판단해 `sUnwatchUrl`/`sWatchUrl`로 `$yona.sendForm`(POST), 성공이면 `active ybtn-watching` 토글 |
| 카드 영역 토글 단추 | click | `.codediff-wrap`에 `diffs-only` 토글 |
| `.review-wrap` | click `a.review-card` | 5절 |
| window | hashchange | 5절 |
| `#branches` | change | `location.href = weEvt.val`(이 화면에는 대상 없음, code-F1) |
| `#toggle-comments` | click | 모든 `.diff-container`에 `show-comments` 토글, `#minimap` 토글(Subversion 화면용, Git 화면에는 요소 없음) |

`bCommentable`이 거짓이면 `.diff-body`의 `.linenum > .yobicon-comments`를 숨긴다(댓글 입력 불가 사용자). 참이면 4절의 처리기를 모두 건다. `.btn-thread-minimize`(접기)와 `div[data-toggle="CodeCommentThread"]`(마우스 진입과 이탈)는 `bCommentable`과 무관하게 건다.

## 4. 드래그 선택과 줄 댓글 팝업(`CodeCommentBlock`, `CodeCommentBox`)

초기화(`bCommentable`일 때): `yona.CodeCommentBox.init({sTplFileItem})`, 창 이벤트 `CodeCommentBox:aftershow`(미니맵 갱신), `CodeCommentBox:afterhide`(미니맵 갱신, 블록 해제, 저장한 블록 정보 비움), `yona.CodeCommentBlock.init({welContainer: .diff-body, welPopButtonOnBlock: .diff-body의 .btnPop})`, `.diff-body`에 `click .btnPop`과 `mousedown :not(.btnPop)` 연결, `div.diff-body[data-outdated!="true"]`에 `click tr[data-line] .linenum` 연결(`data-outdated`를 쓰는 템플릿이 없어 항상 해당).

### 4.1 선택에서 팝업 단추까지

1. **mousedown**(`td.code pre` 위, 왼쪽 버튼): 시작 요소를 기억하고, 기존 블록(`span[data-toggle="comment-block"]`)을 모두 풀고(`unwrapAll`), 팝업 단추를 숨기고, 선택을 비운다.
2. **mouseup**(`.diff-body` 안 어디서든): 선택 글자가 있으면 유효성을 본다. 유효 조건: 선택의 시작과 끝 `tr` 둘 다 `data-line`을 가짐, 같은 `table`(같은 파일), 두 줄 사이의 모든 줄이 `tr.comments`이거나 `td.code > pre`를 정확히 하나 가진 줄(구간 머리 `tr.range`나 이진 줄이 끼면 무효, 즉 **다른 구간(hunk)을 가로지르는 선택은 안 된다**).
3. 유효하면 블록 정보를 만든다: `bIsReversed`(끝에서 시작으로 거꾸로 드래그했는지: 선택 시작 줄이 mousedown 줄보다 위이거나, 같은 줄에서 시작 오프셋이 끝 오프셋보다 큼), `nStartLine/nEndLine`(`data-line`), `sStartType/sEndType`(`data-type`), `sStartSide/sEndSide`(`remove`면 `A`, 아니면 `B`), `nStartColumn/nEndColumn`(선택 오프셋), `sPathA, sPathB, sPrevCommitId, sCommitId`(table의 `data-path-a, -b, -commit-a, -commit-b`), `sFilePath`, `sPath`(둘 다 `data-file-path`).
4. **팝업 단추 `.btnPop`**을 보인다: 위치는 선택 끝(거꾸로면 시작) 줄의 `td.code` 기준 `top = 줄 위치 - 줄 높이*1.5`, `left = min(코드 칸 왼쪽 + 열*7px, 컨테이너 폭 - 단추 폭*2)`. 50ms마다 선택이 비었는지 보고 비면 단추를 숨기고 감시를 멈춘다.
5. `.diff-body`의 **mousedown**(`.btnPop` 제외)은: 왼쪽 버튼이고, 대상이 `.comment-thread-wrap`이나 `.review-form` 안이 아니고, 선택이 없으면 팝업을 닫고 저장한 블록 정보를 지운다.

### 4.2 팝업 열기(`.btnPop` 클릭 또는 줄 번호 클릭)

- `.btnPop` 클릭: 저장된 블록 정보로 `CodeCommentBlock.block`을 호출해 범위를 하이라이트한다. 각 줄의 `pre` 첫 텍스트 노드를 `Range.surroundContents`로 `span.review-block[data-toggle=comment-block]`로 감싼다(한 줄이면 시작, 끝 열, 첫 줄이면 시작 열부터 끝까지, 마지막 줄이면 처음부터 끝 열까지, 중간 줄은 전부). 시작과 끝 줄은 `table.diff-container[data-file-path=<경로>] tr[data-line=..][data-side=..]`로 찾고 사이 줄은 `data-line`이 있는 것만.
- 팝업을 붙일 줄: 거꾸로이면 시작 줄, 아니면 끝 줄(`data-line`과 `data-type`으로 찾음). 그 `tr`에 `blockInfo`를 jQuery 데이터로 저장하고 `CodeCommentBox.show(tr, {sPlacement: 거꾸로면 "top" 아니면 "bottom", nAdjustmentTop: .diff-body의 위치})`.
- 팝업이 창 밖으로 나가면 스크롤한다: 아래 배치이면 팝업 아래쪽이 보이게(`창 높이 - 20px` 여백), 위 배치이면 팝업 위쪽이 보이게.
- **줄 번호 클릭**(`tr[data-line] .linenum`, `bCommentable`일 때): 모든 선택을 지우고 그 줄 `td.code pre`의 텍스트 전체를 선택한 뒤 `mouseup`을 일으켜 위 4.1의 흐름을 타고 곧바로 `.btnPop` 클릭과 같은 처리를 한다. 즉 **줄 번호를 누르면 그 줄 전체에 대한 팝업이 바로 열린다**.

### 4.3 `CodeCommentBox`(팝업 폼 하나를 옮겨 다님)

- 폼 하나(`#review-form`, `common/review-form` 조각의 `div.review-form` 안 `form`)를 문서에서 하나만 두고 위치를 옮긴다(`T:common/review-form.html`).
- `show(대상 tr, 옵션)`: 화살표 방향 클래스(`arrow-top`/`arrow-bottom`: 배치의 반대쪽), 새 행 `<tr class="comment-form"><td colspan="3" class="write-comment-form">`을 대상 줄 뒤(`bottom`) 또는 앞(`top`)에 만들어 그 안으로 폼을 옮기고, 숨은 입력을 채우고, 폼을 `show()`하고 `textarea`에 초점을 준 뒤 `CodeCommentBox:aftershow`를 낸다.
- **숨은 입력**: 블록 정보 키에서 첫 글자(`b`, `n`, `s`)를 빼고 소문자화한 이름(`startLine`, `startType`, `startSide`, `startColumn`, `endLine`, `endType`, `endSide`, `endColumn`, `pathA`, `pathB`, `prevCommitId`, `commitId`, `filePath`, `path`)으로 `input[type=hidden]`을 만들거나 값을 갱신한다(`bIsReversed`는 제외). 서버가 읽는 것은 `path`, `startSide`, `startLine`, `startColumn`, `endSide`, `endLine`, `endColumn`뿐(code-F9).
- 닫기: 폼 안 `[data-toggle=close]`(`×` 단추)만 닫는다(Esc, 바깥 클릭은 닫지 않음, 단 4.1의 5번이 선택이 없을 때 바깥 mousedown으로 닫는다). 닫으면 폼을 숨기고 원래 부모(처음 문서 위치)로 되돌리고, 임시 `tr.comment-form`을 지우고 `CodeCommentBox:afterhide`를 낸다. 그러면 블록을 풀고 저장한 정보를 비운다.
- 제출: 폼 `action`은 서버가 정한 `commentAction`(커밋 `/commit/<ID>/comments`, PR `/pullRequest/<DB번호>/comments[?commitId=<커밋>]`). multipart POST. 필드: `contents`, 숨은 범위 필드, 첨부, 선택적 `pending=true`. 응답 `303`으로 `…#comment-<번호>`로 돌아와 페이지가 새로 로드되고 5.2의 해시 이동이 새 댓글로 스크롤한다.
- 폼 안 버튼: 변경 탭은 `리뷰 시작`/`리뷰에 추가`(`pending=true`)와 `단일 댓글 쓰기`, 커밋 보기는 `댓글 쓰기`뿐.
- 팝업이 열릴 때 `textarea[id^=editor-]`의 멘션은 처음 로드 때 `yona.Mention`이 이미 붙인 것을 그대로 쓴다.

### 4.4 기존 스레드 위 마우스 올림

`div[data-toggle="CodeCommentThread"]`에 마우스가 들어오면(눌린 버튼이 없을 때) 그 스레드의 `data-range-*`로 블록 정보를 만들어 `CodeCommentBlock.block`으로 코드 범위를 노랗게 표시하고, 나가면 `unblock`한 뒤 팝업이 열려 있고 저장한 블록 정보가 있으면 그 블록을 다시 표시한다.

## 5. 리뷰 카드와 해시 이동

### 5.1 카드 영역

- 탭 두 개(`#reviewcards-open`, `#reviewcards-closed`, 부트스트랩 `data-bs-toggle=tab`), 카드 `a.review-card.<open|closed>[.outdated]`.
- 커밋 보기: 카드 `href = #thread-<id>`, 내용은 첫 댓글, 아바타, 시각, 댓글 수(스레드 댓글이 2개 이상이면 전체 수). 변경 탭: `href = 스레드 주소(ReviewLinks)`, 어긋남 표시(`review.outdated`), 댓글 수도 같은 규칙(스레드 댓글이 2개 이상이면 전체 수, 아니면 0).
- `a.review-card` 클릭(`.review-wrap`에 위임): `href`의 `#` 뒤 id가 이 페이지에 있으면(`#<id>` 존재) `location.hash`를 바꿔 이동하고(이미 같은 해시이면 `hashchange`를 수동으로 일으킴) 기본 동작을 막는다. 없으면 일반 링크로 이동한다(다른 커밋의 스레드).

### 5.2 `hashchange`

해시가 있으면: 대상 요소의 스레드(`.comment-thread-wrap` 자신이거나 조상)를 찾고, 접혀 있고(`fold`) 안에 `.btn-thread-here`가 있으면 `fold`를 풀고, 스레드 상태(`data-state`가 `open|closed`)에 맞는 카드 탭(`a[href="#reviewcards-<state>"]`)을 `$yona.showTab`으로 연 뒤, `window.scrollTo(0, 대상 offset().top - 50)`, jQuery UI `effect("highlight")`로 강조한다. 페이지 로드 직후에도 해시가 있으면 한 번 일으킨다(`jquery-ui-1.14.2` 필수).

## 6. 접기

| 종류 | 대상 | 동작 | 화면 |
|---|---|---|---|
| 스레드 접기 | `.comment-thread-wrap`(코드 범위 스레드) | `.btn-thread-minimize`(헤더의 `maximize` 단추, 접힌 상태의 `btn-thread-here` 단추) 클릭(`.diff-body` 위임)이 조상 스레드의 `fold` 토글. 이어서 모든 `.btn-thread-here`의 위치를 다시 계산. 닫힌 스레드는 서버가 `fold`로 그림 | 커밋, 변경 |
| 접힌 단추 위치 | `.btn-thread-here` | `right = (스레드 인덱스 * 단추 폭 + 10)px`, `top = 끝 줄 위치 - 10px`. 끝 줄은 스레드가 든 `tr`의 **바로 앞 `tr`**이 `[data-line=<range-endline>][data-side=<range-endside>]`일 때만 찾는다 | 커밋, 변경 |
| 파일 diff 접기 | `.diff-partial-meta` | 클릭이 형제 `.diff-partial-code`를 `toggle`. 마우스 커서 `pointer`. 화면 템플릿의 인라인 스크립트가 건다(`T:pullrequest/changes.html:176-178`, `T:code/diff.html:136-139`). 비교 화면(`compare.html`)에는 없다 | 변경 탭, 커밋 |
| 카드 영역 접기 | `.codediff-wrap.diffs-only` | 1절 | 커밋, 변경(스레드가 있을 때) |
| 파일 단위 스레드 | `.non-ranged-threads-wrap` | `.btn-thread-minimize`를 CSS가 숨김, 접기 없음 | |
| 댓글 숨기기 | `#toggle-comments` | `.diff-container`의 `show-comments` 토글 | Subversion만 |

## 7. Subversion 커밋 보기(`code.SvnDiff`)

호출: `svn-diff.html:113`(`bCommentable`, `sWatchUrl`, `sUnwatchUrl`, `sParentCommitId`, `sCommitId`, `sCodeURL`), `compare-svn.html:28`(`bCommentable:false`, `sParentCommitId`, `sCommitId`, `sCodeURL`(모델 `filesUrl` = `<프로젝트>/code`), `sTplFileURL`, `sTplRawURL`. 파일 링크는 `<sCodeURL>/<리비전>/<경로>`, `S:code.SvnDiff.js:454`, code-F2). Git과 달리 **서버가 diff HTML을 그리지 않고** 패치 원문을 `#commit.diff-body`에 텍스트로 넣고 브라우저가 해석한다.

- 해석: `Index: <경로>\n=====\n`로 파일을 나누고, `---`/`+++`로 파일 머리, `@@ -a,b +c,d @@`로 구간 머리(`tr.range`, `td.hunk`)를 만들고, 첫 구간이 시작된 뒤에는 모든 줄을 내용으로 본다(`add`, `remove`, `context`). 이진 파일(`file marked as a binary type`)은 `code.isBinary` 한 줄. 파일 구조는 Git과 같은 `diff-partial-*` 클래스이나 `tr`에 `data-line`, `data-type`만 있고 **`data-side`와 `data-file-path`(`table`에는 있음) 중 `data-side`는 없다**.
- 줄 번호 클릭(`.diff-wrap td.linenum`): 그 줄 다음 행에 이미 댓글 폼(`#comment-form`)이 있으면 닫고, 아니면 줄(`add|context|remove`)에 인라인 댓글 상자를 연다. 폼(`#comment-form`)에 숨은 `path`, `line`, `side`(`remove`면 `A`, 아니면 `B`)를 채워 제출(`POST /commit/<ID>/comments`).
- 줄에 달린 기존 댓글(`ul.comments > li.comment[data-path, data-side, data-line]`)은 해당 줄 아래로 옮겨 그리고, `bCommentable`이면 `댓글창 열기/닫기` 단추(`#comment-button-template`)를 같이 둔다.
- `#toggle-comments`는 댓글이 있을 때만 DOM에 있고 `show-comments` 토글과 `#minimap` 토글을 한다. 해시(`#...`)가 있으면 `/`를 `-`로 바꾼 id를 찾아 스크롤한다.
- 구독 단추는 `active`만 토글, 토스트 없음.

## 8. 미니맵

- 구성: `#minimap.minimap-outer > .minimap-wrap > .minimap-curr + .minimap-links`(`svn-diff.html:89-94`). 대상은 `li.comment`(기본 선택자 `sQueryMiniMap`).
- 계산(`code.Diff.js:597-639`, `code.SvnDiff.js` 같음): 비율 = `.minimap-wrap` 높이(400px) / 문서 높이. `.minimap-curr`의 `top = ceil(scrollTop * 비율)`, `height = ceil(창 높이 * 비율)`. 대상마다 `<a href="#<id>" style="top:..px; height:..px">`를 `.minimap-links`에 넣는다. 대상이 없으면 `#minimap`을 숨기고 있으면 `show()`.
- 갱신: 스크롤(프레임당 한 번, `rafThrottle`, passive), 창 크기와 문서 크기 변경(`ResizeObserver(document.documentElement)`와 `resize`), 팝업이 열리거나 닫힐 때(`CodeCommentBox:aftershow/afterhide`), `#toggle-comments` 클릭, Subversion의 댓글 상자 열고 닫기.
- **Git 화면(`diff.html`, `changes.html`)에는 `#minimap` 요소가 없다.** 모듈은 같은 계산을 하지만 빈 선택에 적용되어 아무 것도 보이지 않는다(code-F11). 미니맵이 보이는 화면은 Subversion 커밋 보기뿐이다.

## 9. 이벤트 총표

| 이벤트 | 대상 | 조건 | 결과 |
|---|---|---|---|
| mousedown | `td.code pre` | 왼쪽 | 블록 해제, 선택 비움 |
| mouseup | `.diff-body` | 선택이 유효 | 블록 정보 저장, `.btnPop` 표시, 50ms 감시 |
| click | `.btnPop` | | 블록 표시, 팝업 열기, 필요하면 스크롤 |
| click | `tr[data-line] .linenum` | `bCommentable` | 줄 전체 선택 후 위와 같음 |
| mousedown | `.diff-body :not(.btnPop)` | 왼쪽, 스레드와 폼 밖, 선택 없음 | 팝업 닫기, 블록 정보 비움 |
| click | `[data-toggle=close]` | 팝업 안 | 팝업 닫기 |
| mouseenter/leave | `[data-toggle=CodeCommentThread]` | 눌린 버튼 없음 | 범위 강조, 해제 |
| click | `.btn-thread-minimize` | `.diff-body` 안 | 스레드 접기 토글, 단추 위치 재계산 |
| click | `a.review-card` | 같은 페이지에 스레드가 있음 | 해시 이동, 카드 탭 전환, 스크롤, 강조 |
| click | `.btn-show/hide-reviewcards` | | `diffs-only` 토글 |
| click | `.diff-partial-meta` | 변경 탭과 커밋 보기만 | 파일 diff 접기 |
| click | `#watch-button` | | 구독 토글 |
| hashchange | window | 해시가 있음 | 5.2 |
| scroll, resize | window | | 미니맵 갱신 |
| click | `.comment-edit` | `review.CommentEdit.js` | 그 자리 고치기 |
| click | `[data-toggle=comment-delete]` | `Comment.js` | 삭제 모달 |

## 10. 이 영역의 발견 사항

[code.md](code.md)의 code-F1, F2, F4, F8(커밋 보기의 파일 머리 접기는 수정됨), F9, F10, F11, F14와 [pullrequest.md](pullrequest.md)의 pullrequest-F12에 정리했다. 추가로 이 문서에서 확인한 사실: (1) 줄 번호를 눌러 팝업을 여는 처리기는 `data-outdated="true"`가 아닌 `.diff-body`에 건다고 쓰였으나 `data-outdated`를 내는 템플릿이 없어 조건이 항상 참이다. (2) `CodeCommentBox`가 `thread-id` 대상을 다루는 분기와 `code.Diff`의 `button.btn-thread` 처리기는 Git 화면의 마크업에서 도달하지 않는다. (3) 미니맵과 `#toggle-comments`는 Subversion 화면에서만 의미가 있다.

---

## 갱신 기록 (2026-11-11 재조사)

기준: 커밋 `5ef425c98` 이후 소스와 대조했다. `code.Diff.js`, `code.SvnDiff.js`, `CodeCommentBlock.js`, `CodeCommentBox.js`, `DiffHtmlRenderer.kt`, `ThreadViewAssembler.kt`, `_page.scss`, `_responsive.scss`는 바뀌지 않았고, 인용한 `파일:줄`(`code.Diff.js:33-45,597-639`, `diff.html:119`, `changes.html:167`, `compare.html:24`, `svn-diff.html:89-94,113`)이 지금도 같은 내용을 가리키는 것을 다시 확인했다.

- 다시 확인한 템플릿: `code/diff.html`, `code/svn-diff.html`, `code/compare.html`, `code/compare-svn.html`, `pullrequest/changes.html`, `common/review-form.html`(6개).
- 고친 행과 문장: 5곳(변경 탭 카드 댓글 수 규칙, 6절 파일 diff 접기 행, 9절 접기 행, 7절 `compare-svn` 호출 인자와 파일 링크, 10절 발견 사항 참조). 줄 번호만 고친 곳: 0곳. 더한 행: 0. 없앤 행: 0. 새 발견 사항: 없음.
- `sources`에 `svn-diff.html`, `compare-svn.html`을 더했다.
