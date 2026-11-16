---
title: 계약 발견 사항 재현과 v1.6 대조 B(코드, 이력, 커밋, 비교, 브랜치, 태그, 코드 주고받기, 포크)
type: ui-contract-verification
updated: 2026-11-11
---

# 요약

- 항목 33건(code-F2~F17 16건, pullrequest-F5~F21 17건). 전부 판정했다.
- 방법: v2(포트 19411)와 공식 v1.16.0 실행본(포트 19511)에 같은 저장소 내용(같은 커밋 해시)과 같은 요청을 만들어 Playwright 또는 직접 요청으로 대조했다. 소스 줄은 보조 근거다. v1.6에 없는 기능(Mercurial, 병합 상자, 이슈형 담당자/라벨/리뷰 필터, 코드 주고받기 담당자/라벨)은 "해당 없음"으로 적었다.
- 판정별 개수

| 판정 | 개수 | 항목 |
|---|---|---|
| v2 신규 결함 | 1 | code-F5 |
| v2 추가 기능의 결함 | 5 | code-F7, pullrequest-F10, F13, F14, F15 |
| v1.6 결함 유지 | 18 | code-F2, F3, F4, F8, F9, F10, F11, F12, F14, F15, F17 / pullrequest-F5, F6, F9, F11, F16, F17, F18 |
| 결함 아님 | 7 | code-F13 / pullrequest-F7, F8, F12, F19, F20, F21 |
| 문서 문제 | 2 | code-F6, code-F16 |
| 재현 불가 | 0 | |

- v2 신규 결함
  - code-F5: Subversion 커밋 화면의 브랜치 토글 글자가 `?branch=foo`일 때 v1.6은 `foo`, v2는 항상 `HEAD`.
- v2 추가 기능의 결함
  - code-F7: Mercurial 파일 보기의 리비전은 7자, 목록은 12자. Mercurial에 `브랜치` 탭이 있는데 누르면 404.
  - pullrequest-F10: 목록의 담당자, 리뷰 요청, 라벨 선택을 바꿔도 제출되지 않는다.
  - pullrequest-F13: 라벨, 담당자 변경 뒤 타임라인을 다시 읽으면 `더 보기` 단추가 동작하지 않는다.
  - pullrequest-F14: 같은 새로 읽기 뒤 어두운 라벨 상자의 글자색이 어두운 색으로 남는다.
  - pullrequest-F15: 충돌로 바뀌어도 병합 방식 드롭다운 단추와 방식 목록이 남아 열린다.

# 준비한 데이터(자기 사본)

- v2: `admin/vb`(git, 커밋 3개와 브랜치 `feature`, `tmp1`, `tmp2`, `feat/x+y`, 태그 `v1`, `light`, `deltag`, 폴더 `dir sp/sub#1`, `한글 파일.txt`), `admin/vsvn`(svn r1~r3), `admin/vhg`(hg 3커밋), `admin/vempty`(빈 저장소), `admin/fk`와 포크 `plainuser/fk`(PR 병합, 충돌, 푸시 알림), `admin/vb` PR 1~2.
- v1.6: `tester/vb`(같은 커밋 해시), `tester/vsvn`(같은 svn 리비전), `tester/vempty`, `tester/fk`와 포크 `other/fk`, `tester/vb` PR 1.
- 사용 후 일부 설정을 바꿨다(`vb`는 코드 접근 구성원 제한, 기본 브랜치 되돌림 등). 모두 자기 사본이다.

# 항목별 결과

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| code-F2 | 재현됨 | Subversion 프로젝트 `admin/vsvn`에서 `/compare/1..3`을 열면 파일 머리의 리비전 링크가 `undefined/1/a.txt`, `undefined/3/a.txt`, `undefined/1/sub/b.txt`, `undefined/3/sub/b.txt`. 같은 프로젝트의 커밋 화면(`/commit/2`)은 `/admin/vsvn/code/1/a.txt`로 정상 | 같음. `tester/vsvn` 같은 요청에서 링크가 `undefined/1/...`로 같다. `app/views/code/compare_svn.scala.html:47`은 `sCodeURL`을 넘기지 않고 `public/javascripts/service/yobi.code.SvnDiff.js:448`이 `htVar.sCodeURL`로 만든다(`svnDiff.scala.html:182`만 넘김) | v1.6 결함 유지 | 장부에 올린다 |
| code-F3 | 재현됨 | `admin/vb`를 코드 접근 구성원 제한으로 저장한 뒤 비구성원 `plainuser`로 요청: `/code/main`, `/code`, `/commits`, `/commit/<id>`, `/rawcode/...`, `/branches`, `/pullRequests` 모두 403. `/files/main/README.md` 200(파일 본문), `/compare/main..feature` 200(커밋 목록과 diff) | 같음. `tester/vb`에서 `third`가 같은 요청에 같은 상태(403들, files 200, compare 200). `app/controllers/CodeApp.java:60`, `CodeAccessCheckAction.java:22`는 코드 화면에만 걸림 | v1.6 결함 유지 | 장부에 올린다 |
| code-F4 | 재현됨 | 커밋 화면 소스에 `yona.Mention({target:'textarea[id^=editor-]'...})`와 `yona.Mention({target:'review-editor'...})`가 모두 있고 `#review-editor` 요소 수는 0 | 같음. `tester/vb` 커밋 화면 `#review-editor` 0개. `app/views/code/diff.scala.html:196-204`에 같은 두 호출 | v1.6 결함 유지 | 장부에 올린다(죽은 코드) |
| code-F5 | 재현됨 | `admin/vsvn/commit/2?branch=foo`의 `#branches .d-label` 글자가 `HEAD`. 템플릿 `svn-diff.html:20`이 `isEmpty ? 'HEAD' : 'HEAD'` | 다름. `tester/vsvn/commit/2?branch=foo`는 `foo`로 보인다. `app/views/code/svnDiff.scala.html:39` `@if(selectedBranch){ @selectedBranch } else { HEAD }`, 컨트롤러가 `CodeHistoryApp.java:124`에서 `branch` 쿼리를 넘김 | v2 신규 결함 | 고친다: `templates/code/svn-diff.html:20`의 `th:text`를 선택한 브랜치(쿼리 `branch`, 기본 `HEAD`)로 바꾸고 `CommitViewController`가 모델에 `selectedBranch`를 넣는다 |
| code-F6 | 재현됨 | 빈 저장소 `admin/vempty`: `/code` 200(nohead 화면), `/commits` 404, `/commits/main/` 404, `/branches` 404, `/code/main` 404. 코드 화면에서만 `NoheadViews`가 쓰인다 | 같음. `tester/vempty`: `/commits` 404, `/commits/main/` 404, `/code` 200(`/branches`는 v1.6이 500, v2는 404로 다름). 동작은 v1.6 그대로이고 KDoc 문장만 사실과 다르다 | 문서 문제 | 문서만 고친다: `NoheadViews.kt:18-21` KDoc에서 "커밋 이력 화면" 문구 삭제 |
| code-F7 | 재현됨(일부) | Mercurial `admin/vhg`(3커밋): 파일 보기 `/code/default/h.txt`의 리비전 링크 글자는 `f5fa8a6`(7자), 폴더 목록은 `f5fa8a6ff8bc`(12자). `/branches`는 404라 `기본으로 설정` 단추는 화면에 나타나지 않는다(그 단추의 서술은 재현 안 됨). 그런데 상단 탭에 `브랜치`(`/admin/vhg/branches`) 링크가 있어 누르면 404 | 해당 없음. v1.6은 Mercurial을 지원하지 않는다(`app`, `conf`에 mercurial 없음) | v2 추가 기능의 결함 | 고친다: `templates/code/view.html:98`의 `substring(f.revId,0,7)`을 짧은 ID 길이 규칙(Mercurial 12자, A-378)으로 맞추고, `BRANCHES`를 지원하지 않는 저장소 종류에서는 `브랜치` 탭을 숨긴다 |
| code-F8 | 재현됨 | 커밋 화면에서 `.diff-partial-meta`를 눌러도 `.diff-partial-code` 표시가 `block`에서 `block`(접히지 않음). PR 변경 탭에서는 `block`에서 `none`(접힘) | 같음. `tester/vb` 커밋 화면 `block`/`block`, PR 변경 탭 `none`. `app/views/git/viewChanges.scala.html:216-218`에만 처리기가 있고 `code/diff.scala.html`에는 없음 | v1.6 결함 유지 | 장부에 올린다 |
| code-F9 | 재현됨 | 커밋 화면에서 줄 댓글 팝업의 제출 필드: `startLine,startType,startSide,startColumn,endLine,endType,endSide,endColumn,pathA,pathB,prevCommitId,commitId,filePath,path,_csrf,contents,temporaryUploadFiles,filePath`. 실제 전송 본문에도 `startType=remove`, `pathA=README.md`, `prevCommitId=...`, `commitId=...`가 있고 응답 303 | 같음(`_csrf` 제외). `tester/vb`에서 같은 필드 목록과 303. 서버가 쓰지 않는 필드를 보내는 구조는 v1.6 그대로 | v1.6 결함 유지 | 조치 없음(해가 없음, 계약 문서에 기록) |
| code-F10 | 재현됨 | 커밋 화면 스레드의 `button.btn-thread` 0개, 답글 폼 `display:block`(스레드 2개 모두) | 같음. v1.6도 `btn-thread` 0개, 답글 폼 `block`. `app/views/code/svnDiff.scala.html:167`에만 `btn-thread`가 있다. `partial_comment_form_on_thread.scala.html:39` `style="display:block;"` | v1.6 결함 유지 | 장부에 올린다(죽은 코드) |
| code-F11 | 재현됨 | Git 커밋 화면의 `#minimap` 0개. Subversion 커밋 화면(`vsvn/commit/2`)은 1개 | 같음. v1.6 Git 커밋 화면 `#minimap` 0개, Subversion 1개. `app/views/code/svnDiff.scala.html:149`에만 있음 | v1.6 결함 유지 | 장부에 올린다 |
| code-F12 | 재현됨 | 같은 스레드(첫 댓글 + 답글 1개)가 커밋 화면 리뷰 카드에는 `2`, PR 변경 탭 리뷰 카드에는 `1`로 보인다 | 같음. v1.6 커밋 화면 `2`, PR 변경 탭 `1`. `app/views/code/diff.scala.html:135`(`reviewComments.size`) 대 `app/views/git/partial_reviewlist.scala.html:35`(`size-1`) | v1.6 결함 유지 | 장부에 올린다 |
| code-F13 | 재현 안 됨(서술 틀림) | 삭제 단추의 `href`로 JS 없이 GET: 브랜치 `GET /admin/vb/code/refs%252Fheads%252Ffeature/`는 303으로 `.../feature`(브랜치 코드 화면)로 이동, 태그 `GET /admin/vb/tags/light`는 404. 405는 나오지 않았다. 같은 주소에 POST도 404 | 브랜치는 같음. `tester/vb` `GET .../code/refs%252Fheads%252Ffeature/`도 303이다. v1.6에는 태그 화면이 없고(`conf/routes`에 tags 없음) 태그 삭제 링크는 브랜치 화면에 같은 모양으로 있다 | 결함 아님 | 문서만 고친다: 발견 사항의 "405"를 "브랜치는 코드 화면으로 이동(303), 태그는 404"로 정정 |
| code-F14 | 재현됨 | 파일 단위 스레드의 삭제(`data-request-method=delete`)를 누르면 모달 없이 즉시 `DELETE /comments/review_comment/<id>` 200. 코드 스레드 댓글 삭제(`data-toggle=comment-delete`)는 모달이 뜬다(모달 1개) | 같음. v1.6도 파일 단위는 모달 없이 DELETE 200, 코드 스레드는 모달 1개. `partial_nonrange_codecomment_thread.scala.html:52` 대 `partial_comment_thread.scala.html:70` | v1.6 결함 유지 | 장부에 올린다 |
| code-F15 | 재현됨 | 일본어(`Accept-Language: ja`) 코드 화면에서 `Download`, `New file`이 영어로 나온다. `messages_ja_JP.properties`에 서술한 13개 키(`code.authorDate`, `code.copyCommitId`, `code.download`, `code.new.file`, `code.open`, `code.open.desc`, `code.viewRaw`, `code.tooBigFileForCodeBrowser`, `code.looseFileSizeLimitForCodeBrowser`, `commentThread.close/open`, `common.comment.delete/edit`) 모두 없음 | 같음. v1.6 일본어 코드 화면도 영어로 나오고, `conf/messages.ja-JP`에도 같은 13개 키가 없다(기본 `conf/messages`에는 있음) | v1.6 결함 유지 | 장부에 올린다(번역 보충은 별도) |
| code-F16 | 재현됨 | 일반 구성원 `plainuser`: 브랜치 삭제 `DELETE .../code/refs%252Fheads%252Ftmp1/` 403, 기본 브랜치 설정 `POST .../setAsDefault` 403, 태그 삭제 403, 브랜치 화면에 삭제 단추 없음. 소유자(관리자)는 둘 다 303 | 같음. v1.6 일반 구성원 `other`도 403, 403, 단추 없음, 소유자는 303. `AccessControl.java`의 프로젝트 `DELETE`/`UPDATE` | 문서 문제 | 문서만 고친다: 위키의 "삭제 권한, 수정 권한"을 "프로젝트 삭제, 수정 권한(매니저, 조직 관리자, 사이트 관리자)"로 정확히 적는다(코드는 v1.6과 같고 맞음) |
| code-F17 | 재현됨 | `/code/main/dir%20sp/sub%231` 등에서 링크: 폴더 경로는 `/admin/vb/code/main/dir sp/sub%231`(경로 공백 그대로), 파일 이름만 인코딩(`.../dir sp/%ED%95%9C%EA%B8%80%20%ED%8C%8C%EC%9D%BC.txt`, `a%23b.txt`). 브랜치 `feat/x+y`는 `feat%252Fx%252By`(두 번 인코딩) | 같음. v1.6 링크가 한 글자도 다르지 않다 | v1.6 결함 유지(계약) | 조치 없음(새 UI가 따라야 할 계약으로 문서에 유지) |
| pullrequest-F5 | 재현됨 | 포크 `plainuser/fk`에서 만든 PR이 `admin/fk`에 병합된 뒤 보낸 사람(`plainuser`, 받는 프로젝트의 비구성원)의 개요에 `브랜치 삭제` 단추가 보인다. `DELETE .../deletefrombranch` 403, `POST .../restorefrombranch` 403. 소유자는 둘 다 303 | 같음. `other/fk` 보낸 사람에게 `브랜치 삭제` 단추가 보이고 DELETE, POST 모두 403, 소유자는 303 | v1.6 결함 유지 | 장부에 올린다 |
| pullrequest-F6 | 재현됨 | 병합된 PR에 `accept` 다시 → 303, `close` → 303이고 상태가 `닫힘`으로 바뀌어 `user 님이 이 요청을 닫았습니다`가 타임라인에 생긴다. 충돌 PR(`admin/fk` #2)에 `accept` → 303(개요로, 별도 응답 없음, 개요에 `충돌이 발생합니다`) | 같음. v1.6도 재병합 303, 병합된 PR 닫기가 `닫힘`으로 바뀜, 충돌 병합 303 | v1.6 결함 유지 | 장부에 올린다 |
| pullrequest-F7 | 재현 안 됨(서술 틀림) | `GET .../pullRequest/2/close`, `GET .../open` 모두 404(405 아님). 단추는 `<a data-request-method=post href=".../close">` | 같음. v1.6도 GET 404. `app/views/git/view.scala.html:61,64` | 결함 아님 | 문서만 고친다: "405"를 "404"로 정정 |
| pullrequest-F8 | 재현 안 됨(차이가 드러나지 않음) | 비구성원 로그인 사용자: 구독 단추가 보이고 `POST /watch?resource.type=pull_request&resource.id=36`이 200. 익명은 단추가 없고 서버도 거부(403). `WATCH` 권한과 `READ` 권한이 갈리는 사용자 유형을 만들지 못했다(공개 프로젝트는 로그인하면 둘 다 허용, 구성원은 둘 다 허용) | 같음. `WatchApp.java:47`이 `READ`만 확인하고 `third`도 200, 익명 403(`Anonymous cannot watch it.`). `AccessControl.java:293-296` | 결함 아님 | 조치 없음(관찰되는 차이 없음) |
| pullrequest-F9 | 재현됨 | 보낸 포크 `plainuser/fk`에 `pb2`를 푸시하면 익명, 비구성원 `thirdv2` 모두 목록에 최근 푸시 알림과 닫기 단추가 보인다. `thirdv2`가 `DELETE /plainuser/fk/pushedBranch/2/delete` → 403. 익명 DELETE는 303(로그인 이동) | 같음. `other/fk` 푸시 알림이 `third`에게 보이고 `DELETE` → 403(`tester`도 원본 소유자로서 403). 포크 소유자만 200 | v1.6 결함 유지 | 장부에 올린다 |
| pullrequest-F10 | 재현됨 | `admin/vb/pullRequests`에서 `#assignees`, `#reviewRequested`, `#labelIds` 값을 바꾸고 `change`를 일으켜도 요청 없음. `#contributors`를 바꾸면 `GET ...?filter=&contributorId=1&assigneeId=1&reviewRequestedId=1&labelIds=8`처럼 다른 값이 함께 제출된다 | 해당 없음. v1.6 목록에는 `#contributors`만 있다(`app/views/git/partial_search.scala.html:61,133`). 담당자, 리뷰 요청, 라벨 필터는 v2 추가 | v2 추가 기능의 결함 | 고친다: `templates/pullrequest/partial-search.html:167-179` 스크립트에 `#assignees`, `#reviewRequested`, 라벨 선택의 `change`에서 `yona.Htmx.submit('#search')`를 부르는 처리기를 추가 |
| pullrequest-F11 | 재현됨 | 두 단 보기를 켜고 목록 제목을 누르면 오른쪽 패널(`#pageslide` iframe 1개, 페이지 유지). 작성자 필터를 바꾼 뒤(htmx 교체) 제목을 누르면 패널이 열리지 않고 전체 이동(표식이 사라짐) | 같음. v1.6도 필터 변경 뒤에는 전체 이동이고 패널 iframe이 0개 | v1.6 결함 유지 | 장부에 올린다(향후 UI 개편 때 처리) |
| pullrequest-F12 | 재현 안 됨(서술 틀림) | 한 화면 안에 `#review-form` 중복은 없다(개요는 `FORM#review-form`, 변경 탭은 `DIV#review-form`). 개요에서 `#review-form`은 textarea 1개를 가진 form이고 콘솔 오류 0건, 머리 `리뷰` 링크 클릭 뒤에도 오류 0건. 개요의 리뷰 판정 폼은 v2 추가 | v1.6 변경 탭만 `div#review-form`(`app/views/common/reviewForm.scala.html:26`), 개요에는 없음 | 결함 아님 | 문서만 고친다: "같은 id를 쓴다"는 서로 다른 화면 사이의 일이며 한 화면에 중복이 없고 오류도 없다고 정정 |
| pullrequest-F13 | 재현됨 | PR #2(`admin/vb`)에 커밋 메시지가 여러 줄인 커밋을 푸시한 뒤, 라벨 선택을 바꿔 타임라인을 다시 읽으면 `button.moreBtn`을 눌러도 `pre.commitMsg.desc`의 `hidden`이 그대로. 새로 읽은 페이지에서는 눌렀을 때 `hidden`이 사라진다 | 해당 없음. 타임라인 Ajax 새로 읽기(`pullrequest.Meta.js`)는 v2 추가이고 v1.6은 댓글, 담당 변경 뒤 전체 읽기 | v2 추가 기능의 결함 | 고친다: `templates/pullrequest/view.html:217-219`의 직접 바인딩을 `$("#comments").on("click","button.moreBtn",...)` 위임으로 바꾼다 |
| pullrequest-F14 | 재현됨 | 라벨 `Lightlab`(#ffff99)과 `Darklab`(#223344)을 PR에 붙인다. 새로 읽은 직후의 서버 그림에서는 `dimgray` 클래스가 붙는다. 라벨 변경 뒤 Ajax로 다시 읽으면 두 상자 모두 클래스 없이 글자색 `rgb(32,32,32)`이라 `Darklab`(배경 `rgb(34,51,68)`)이 어두운 글자로 보인다 | 해당 없음. v1.6에는 PR 라벨 타임라인이 없다 | v2 추가 기능의 결함 | 고친다: `static/assets/javascripts/esm/service/pullrequest.Meta.js:68`의 `.event > .badge`를 `.event > .label`로 바꾼다 |
| pullrequest-F15 | 재현됨 | 열린 PR(#2)의 개요를 연 채 받는 브랜치에 충돌 커밋을 푸시하고 폴링을 기다리면 `#btnAccept`는 `display:none`이 되지만 `#btnMergeOptions`와 `#merge-box`는 그대로 보이고 클릭하면 병합 방식 3개가 열린다. `#state`에는 충돌 안내가 나온다 | 해당 없음. v1.6에는 분할 단추가 없다 | v2 추가 기능의 결함 | 고친다: `static/assets/javascripts/esm/service/git.View.js:179`에서 충돌이면 `#btnAccept`만이 아니라 `#merge-box`(또는 `#btnAccept, #btnMergeOptions`와 확인 폼)를 숨기고 충돌이 풀리면 다시 보인다 |
| pullrequest-F16 | 재현 안 됨(사용자에게 닿지 않음) | `pullRequest.fromProjectId.required`, `pullRequest.toProjectId.required`는 기본, 한국어 번들에 없다. 새 PR 폼에서 보내는/받는 프로젝트를 비우면 키 알림이 아니라 `변경 내역이 없습니다.`가 먼저 나온다(`git.Write.js` `_validateForm`이 `numOfCommits` 검사를 먼저 함) | 같음. v1.6도 두 키가 없고 같은 순서라 같은 알림이 나온다 | v1.6 결함 유지 | 조치 없음(실제로 보이지 않음. 원하면 번역 키만 보충) |
| pullrequest-F17 | 재현됨 | 포크 `POST /admin/vb/fork`에 이름 `bad name!`, 이름 빈 값, 공개 범위 `BOGUS` → 모두 500. `POST /clone`도 500. 정상 값은 200 | 같음. v1.6도 `/fork` 세 경우 500, `/clone` 500 | v1.6 결함 유지 | 장부에 올린다 |
| pullrequest-F18 | 재현됨 | `/admin/fk/newFork`의 `label[for=inputOwner]`가 가리키는 요소가 없다(select의 id는 `project-owner`) | 같음. v1.6 `newFork`도 `inputOwner` 없음, select는 `project-owner` | v1.6 결함 유지 | 장부에 올린다 |
| pullrequest-F19 | 재현 안 됨(서술 일부 틀림) | 비구성원 `plainuser`: 비공개 `admin/secretproj`의 `pendingReview/discard`, `comments/1/delete`는 모두 403(`PendingReviewController`에 `@IsAllowed`가 없어도 프로젝트 읽기 검사가 앞에서 적용된다. 없는 프로젝트도 403). 공개 `admin/gitproj`에서는 `discard` 303(할 일 없음), `comments/1/delete` 404. 같은 사용자의 `close`는 403 | 해당 없음. 임시 리뷰는 v2 추가 | 결함 아님 | 문서만 고친다: "읽기 검사가 없다"가 아니라 "공통 읽기 검사 뒤라 영향 없음"으로 정정 |
| pullrequest-F20 | 확인됨 | `src/test/js`에 `code.Diff` 시험 없음(`svndiff.test.mjs`만 있음), 병합 상자 인라인 스크립트 시험 없음, `gitview.test.mjs`는 `btnAccept` 표시 전환(86,91줄)까지만 보고 폴링 뒤 `Request.bind`는 보지 않음 | 해당 없음(시험 공백) | 결함 아님 | 장부에 올린다(시험 추가 대상) |
| pullrequest-F21 | 재현됨 | PR 개요와 변경 탭 응답 모두에 `#merge-box` 스타일과 `getElementById('merge-box')` 스크립트가 각각 1개씩 들어 있다. `/state` 응답에는 없다(폴링이 다시 실행하지 않음). 폴링 13초 뒤에도 `style` 1개, `#merge-box` 1개로 늘지 않고 `btnAccept` 클릭이 정상으로 폼을 연다 | 해당 없음. v1.6에는 병합 상자가 없다(`partial_info.scala.html`에 style, script 없음) | 결함 아님 | 조치 없음(동작 이상 없음. 인라인 구조일 뿐) |

# 범위 점검

| 번호 | 있음 | 번호 | 있음 |
|---|---|---|---|
| code-F2 | 예 | pullrequest-F5 | 예 |
| code-F3 | 예 | pullrequest-F6 | 예 |
| code-F4 | 예 | pullrequest-F7 | 예 |
| code-F5 | 예 | pullrequest-F8 | 예 |
| code-F6 | 예 | pullrequest-F9 | 예 |
| code-F7 | 예 | pullrequest-F10 | 예 |
| code-F8 | 예 | pullrequest-F11 | 예 |
| code-F9 | 예 | pullrequest-F12 | 예 |
| code-F10 | 예 | pullrequest-F13 | 예 |
| code-F11 | 예 | pullrequest-F14 | 예 |
| code-F12 | 예 | pullrequest-F15 | 예 |
| code-F13 | 예 | pullrequest-F16 | 예 |
| code-F14 | 예 | pullrequest-F17 | 예 |
| code-F15 | 예 | pullrequest-F18 | 예 |
| code-F16 | 예 | pullrequest-F19 | 예 |
| code-F17 | 예 | pullrequest-F20 | 예 |
| | | pullrequest-F21 | 예 |

# 부수 관찰(목록 밖)

- v1.6는 빈 저장소의 `/branches`가 500, v2는 404.
- v1.6 커밋 화면은 jQuery 선택자 오류(`a[href=#reviewcards-open]`)가 콘솔에 나고, v2는 나지 않는다.
- 새 프로젝트를 만들 때 메뉴 체크를 보내지 않으면 코드 보기와 코드 주고받기 메뉴가 꺼져 포크의 PR 받는 프로젝트 선택지에 원본이 나오지 않는다(두 버전 모두).
- v2에서 PR 변경 탭의 줄 댓글 팝업 기본 제출 단추는 `대기 중 추가`(임시 리뷰, v2 추가)라 바로 공개되지 않는다.
