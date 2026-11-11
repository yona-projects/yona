---
title: 이슈 보기 화면 계약 (이슈 보기, 댓글, 추천, 공유자, 담당자 등 보조 API)
type: ui-contract
sources:
  - src/main/resources/templates/issue/view.html
  - src/main/resources/templates/issue/partial-comment.html
  - src/main/resources/templates/issue/partial-comments.html
  - src/main/resources/templates/issue/partial-index-comments.html
  - src/main/resources/templates/issue/partial-subtasks.html
  - src/main/resources/templates/issue/partial-voters.html
  - src/main/resources/templates/issue/partial-voter-list.html
  - src/main/resources/templates/issue/partial-show-selected-label.html
  - src/main/resources/templates/issue/partial-assignee.html
  - src/main/resources/templates/issue/partial-select-label.html
  - src/main/kotlin/dev/yona/web/issue/IssueViewController.kt
  - src/main/kotlin/dev/yona/web/issue/IssueViewAssembler.kt
  - src/main/kotlin/dev/yona/web/issue/IssueCommentController.kt
  - src/main/kotlin/dev/yona/web/issue/IssueVoteController.kt
  - src/main/kotlin/dev/yona/web/api/IssueApiController.kt
  - src/main/resources/static/assets/javascripts/esm/service/issue.View.js
updated: 2026-11-11
---

# 이슈 보기 화면 계약

공통 계약, 권한 행렬, 발견 사항(`issue-F1`...)은 [issue.md](issue.md)에 있다. 서버 동작의 서술은 [web/issue-view.md](../web/issue-view.md), [web/issue-comments.md](../web/issue-comments.md), [web/issue-api.md](../web/issue-api.md)에 있고 여기서는 화면 계약과 어긋난 점만 적는다.

경로 약어: `T/` = `src/main/resources/templates/`, `K/` = `src/main/kotlin/dev/yona/`, `J/` = `src/main/resources/static/assets/javascripts/`, `S/` = `app/assets/stylesheets/scss/`, `TEST/` = `src/test/kotlin/dev/yona/web/issue/`.

## 1. 화면 개요

| 항목 | 내용 |
|---|---|
| URL | `GET /:user/:project/issue/:number`(번호는 숫자만, `IssueViewController.kt:59`) |
| 어노테이션 | `@NullProjectCheck`(60), 클래스 `@AnonymousCheck`. 읽기 권한은 메서드 안에서 직접 검사(85) |
| 템플릿 | `T/issue/view.html`, 레이아웃 `project-layout(제목, projectView, 'ISSUE', viewPage)` + `project/menu :: projectMenu(..., 'main-menu-only')` |
| 모델 | `title`(`이슈제목 |:| 본문 앞 200자`), `projectView`, `issue`(`IssueDetail`), `listUrl`, `issueResourceId`, `nextState`(`open`/`closed`)(`IssueViewController.kt:103-109`) |
| 응답 분기 | Accept가 JSON 선호면 `{id,title,state,body(200자 줄임),createdDate,link}`, 없는 이슈는 JSON이면 `200 {"title":번호,"body":"..."}`, 아니면 404 프로젝트 오류 화면. `Vary: Accept`(72, 76-81, 92-99) |
| 오류 | 이슈 없음 404, 초안이고 작성자가 아님 **403**(내용은 "찾을 수 없음", 83), 읽기 권한 없음 403(86) |
| 부수 효과 | 로그인 사용자의 최근 방문 프로젝트, 최근 본 이슈, 읽음 표시(`recentVisits.markIssueOrPostingVisited`)(89-91) |
| 조각 | `GET .../timeline` → `partial-comments :: timelineList`(`IssueViewController.kt:112-122`, `@IsAllowed(READ, ISSUE_POST)`) |

`listUrl`(L 단축키 대상)은 Referer가 없으면 `{목록}?state=open`, 있으면 호스트, 포트, 경로가 이슈 상태의 기본 목록 주소(`?state=이슈상태`)와 같을 때만 Referer를 쓰고 아니면 기본 주소다(`IssueViewController.kt:172-176`, 시험 `TEST/IssueViewTest.kt:147-155`).

## 2. 레이아웃 계약

- 바깥 `.page-wrap-outer > .project-page-wrap.board-view`(`view.html:14-15`).
- **머리** `.board-header.issue`(17-35): 오른쪽에 만든 시각과 상태 배지(`badge-issue-상태`, 모바일 숨김 `hide-in-mobile`, 18-21), 제목 줄(`subtask` 표시, 번호 또는 `#Draft`, 제목, 즐겨찾기 별, 모바일 전용 시각과 배지 `hide show-in-mobile`, 22-33), 초안이면 설명 `issue.draft.description`(34).
- **본문 단** `.board-body.row.d-flex`(38): 왼쪽 `.col-lg-9.span-left-pane`(작성자, 본문, 첨부, 동작 줄, 공유자, 구독자 자리, 하위 이슈, 댓글), 오른쪽 `.col-lg-3.span-right-pane.mb20`(`.issue-info.sticky-top`: 담당자, 마일스톤, 마감일, 라벨, 아이콘 줄, 댓글 색인).
- **바닥** `.board-footer`: 숨은 입력 3개(`issueBodyChecksum`, `numOfComments`, `issueUpdateDate`, 255-259), `help/keymap :: keymap('issueDetail', projectView)`(261).
- 반응형(`S/_responsive.scss`, 폭 720px 이하): `.span-right-pane`은 `display:none !important`, `.span-left-pane`은 100%(`_responsive.scss:261-267`). 그러므로 **모바일에서는 담당자, 마일스톤, 마감일, 라벨 편집과 댓글 색인이 없다.** 모바일 전용으로 `new subtask` 단추가 동작 줄에 `show-in-mobile-inline`로 따로 있다(`view.html:88-90`). 상태 배지와 시각도 모바일용을 따로 둔다(29-32).
- 상태에 따른 차이: 본문이 없으면 `div.content.empty-content`(67), 초안이면 `#comments` 영역 전체가 없다(`th:if="${!issue.draft}"`, 146, 시험 `TEST/IssueViewTest.kt:554`), 공유자가 없고 수정 권한도 없으면 `dl.sharer-list`가 `hideFromDisplayOnly`(`display:none`, `S/_common.scss:299`)로 숨는다(123, 127), 수정 권한이 있고 공유자가 없으면 공유 단추를 눌러야 입력 상자가 보인다.
- 같은 id가 두 번 있다: `#comments`(146, 244), `#timeline`(147, 245). 위 둘은 왼쪽 본문의 타임라인과 오른쪽 색인이다(`issue-F13`).

## 3. 동작 요소 표

### 3.1 머리와 작성자 영역

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 즐겨찾기 별 `.favorite-issue[data-issue-id]` | 아이콘 | **항상**(비로그인 포함). 즐겨찾기한 이슈면 `starred` | 클릭하면 토글. 성공하면 별 `starred` 토글과 `issue.favorite.added|deleted` 알림 3초 | `POST /user/favoriteIssues/{id}` | 200 `{"issueId","favored","message"}`. 실패(비로그인 403 빈 본문)는 `JSON.parse(빈문자열)`이 예외를 내서 아무 일도 안 보인다(`issue-F21`) | 로그인 필요(`FavoriteApiController.kt:40`) | `view.html:26-28`, `J/esm/Usermenu.js:122-136`, `K/web/api/FavoriteApiController.kt:36-46` |
| 작성자 링크 | 링크 | 작성자가 있으면 사용자 페이지, 없으면 `/`와 `issue.noAuthor` | 이동 | `GET /{loginId}` | 사용자 페이지 | 공개 | `:41-50` |
| 변경 이력 | 링크/모달 | `issue.historyHtml != null`. 비로그인이면 로그인 링크(`/login?redirectUrl=...`, `data-bs-toggle="modal"`), 로그인이면 `#-yona-posting-history` 모달(`common/partial-history`)과 마지막 수정자, 시각 | 모달 열기 | 없음 | 이력 HTML(`rawMarkdown.sanitize`) | 보기 권한 | `:51-65`, `K/web/issue/IssueViewAssembler.kt:89` |

비로그인용 이력 링크는 `/login?redirectUrl=`이다. 다른 곳의 로그인 폼 주소는 `/users/loginform`이다(`K/web/access/AnonymousCheckStage.kt:29`). 어느 쪽이 맞는지는 로그인 담당이 확인한다(추측: `/login`은 별칭, 이 조사에서 라우트를 확인하지 않음).

### 3.2 본문과 동작 줄(왼쪽 단)

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 본문 `.markdown-wrap` | 마크다운 HTML | 본문이 있을 때(`th:utext`). `data-allowed-update=canUpdate` | 체크리스트 `input[type=checkbox]`: 수정 권한이 없으면 모두 `disabled`. 클릭하면 숨은 textarea(`#issue-{번호} form textarea`)의 해당 순번 `- [ ]`를 `[x]`로 바꿔(하위 목록도 같이) 서버에 보낸다 | `PATCH {base}/issue/{n}/content` JSON `{content, original}` | 200 이슈 JSON. 충돌이면 `409 {"message":"Already modified by someone.","storedContent"}`, 비로그인 401, 권한 없음 403 `{"message":"Forbidden request"}`. 실패 시 `[상태] 메시지 + Refresh the page!` 경고창 | 이슈 `UPDATE` | `view.html:68-78`, `J/esm/Tasklist.js:29-58`, `K/web/api/IssueApiController.kt:277-287` |
| 체크리스트 진행 막대 `.tasklist` | 막대 | `common/tasklist-bar` | 체크 수/전체를 `(n/m)`로 채우고 100%면 초록 | 없음 | 없음 | 없음 | `view.html:75`, `Tasklist.js:98-122` |
| 첨부 `#attachments[data-attachments]` | 목록 | 첨부 JSON | `yona.Attachments`가 그린다 | 파일 다운로드 `GET /files/...`(첨부 담당) | | 보기 권한 | `view.html:79`, `issue.View.js:305-311` |
| 지켜보기 `#watch-button` | 단추 | `issue.watchable`(`WATCH` 권한: 로그인 + 공개 프로젝트 또는 구성원 등). 구독 중이면 `ybtn-watching`과 `issue.unwatch` 글자 | 클릭하면 토글. 성공하면 글자, `data-watching`, 클래스를 바꾸고 `issue.watch.start`/`issue.unwatch.start` 알림 3초 | `POST /watch?resource.type=issue_post&resource.id={id}` 또는 `/unwatch?...` | 200 빈 본문. 실패 처리기 없음 | `WatchController`: 로그인 필요(403 평문 `Anonymous cannot watch it.`), 리소스 `READ` 필요 | `view.html:83-85`, `issue.View.js:266-279`, `K/web/WatchController.kt:40-56` |
| 공유 `#issue-share-button` | 단추 | `issue.canUpdate` | 마우스를 올리면 설명 팝오버. 누르면 공유자 영역 `#sharer-list`, `.sharer-list`를 보이고 테두리를 더하고 select2 검색란에 초점 | 없음 | | 이슈 `UPDATE` | `view.html:86-87`, `:354-358` |
| 새 하위 이슈(모바일) | 링크 | 모바일에서만 보임(`show-in-mobile-inline`) | 이동 | `GET {base}/issueform?parentIssueId={issue.parentIssueId}` | 이슈 작성 폼(하위 이슈 펼침) | 로그인 + 이슈 생성 | `:88-90` |
| 가중치 올림 `#upvote-issue-weight`, 내림 `#down-vote-issue-weight`, 숫자 `.weight-number` | 단추 | **항상**(권한 무관) | 클릭하면 숫자를 응답 `weight`로 바꾼다 | `POST {base}/issue/{n}/upvoteWeight`, `.../downvoteWeight` | 200 `{"weight":N}`. 권한 없음 403 `{"message":"Permission denied"}`(처리기 없음 → 아무 변화 없음, `issue-F3`) | 이슈 `UPDATE` | `:91`, `:424-440`, `K/web/api/IssueApiController.kt:213-226` |
| 번역 `#translate` | 단추 | `site.translationApiConfigured` | 한 번만(`.one`). 본문을 번역해 `.markdown-wrap`(첫 것)에 넣고 단추를 `disabled` | `POST /translation` JSON `{owner,projectName,type:"issue",number}` | 200 `{"translated":HTML}`. 미설정 412, 읽기 권한 없음 403, 없음 404 | 로그인 필요(`@AnonymousCheck(requiresLogin)`), 프로젝트 `READ` | `:109`, `:360-379`, `K/web/api/TranslationApiController.kt:31`, `:50-56` |
| 추천 하트 | 링크 | `issue.canVoteOrComment`면 링크(`data-request-method="post"`, `href=.../vote|unvote`, 투표했으면 `ybtn-watching`), 아니면 비활성 `span.ybtn-disabled[data-login=required]`(누르면 로그인 대화상자, `J/esm/LoginDialog.js:57`) | 클릭하면 `yona.Request.send("post", href)` → 성공하면 새로고침 | `POST {base}/issue/{n}/vote` 또는 `/unvote`(Accept 기본) | 서버는 `303 → 이슈 보기`, fetch가 따라가 200이 되어 `Request.js`가 새로고침(`J/esm/Request.js:75-102`) | 로그인 필요(클래스 `@AnonymousCheck(requiresLogin)`, 메서드 `@IsAllowed(READ)`). 서버가 `canVoteOrComment`를 따로 검사하지는 않는다 | `:94-101`, `K/web/issue/IssueVoteController.kt:36-46` |
| 추천자 아바타 목록 | 아바타 | 추천자가 있을 때(`partial-voters :: voters(3, 5)`) | 나를 맨 앞, 나머지는 최대 3명, 그 이상이면 이름 5개까지 툴팁이 있는 "외 N명" 링크 `#voters`(모달 열기) | 없음 | | 없음 | `view.html:103`, `partial-voters.html:9-25` |
| 추천자 모달 `#voters` | 모달 | 추천자가 있을 때 | 추천자 전체(나 포함, R-25), `site.showUserEmail`이면 "이메일 복사" 단추(`#copyEmailBtn`, `data-copy-text="이름 <메일>;"` 이어 붙임), 닫기 | 없음. 복사는 `yona.Clipboard.copy` | 성공 `button.copy.email.success.message` 알림창, 실패 `site.features.error.clipboard` 알림 1.5초 | 없음 | `:107`, `partial-voter-list.html`, `view.html:402-410` |
| 수정(연필) | 단추/링크 | `canUpdate`면 단추(클릭하면 `window.location=.../editform`, 인라인 `onclick`), 아니면 "원본 보기" 링크 안의 단추 | 이동 | `GET {base}/issue/{n}/editform` | 수정 폼(읽기 전용 모드) | 읽기 | `:112-114`, 오른쪽 단 `:233-235` |
| 삭제(휴지통) | 단추 | `issue.deletable`(이슈 작성자가 아닌 사람의 댓글이 없음)일 때 `canDelete`면 모달 링크 `#deleteConfirm`. `deletable`이고 `canDelete`가 아니면 **아무것도 안 그린다**. `deletable`이 아니면 비활성 단추와 설명 팝오버 `issue.can.not.be.deleted` | 모달 열기 | 없음 | | `DELETE` 권한 | `:115-120`, `:236-241` |
| 삭제 확인 모달 `#deleteConfirm` | 모달 | 항상 DOM에 있음 | "예" `data-request-method="delete"` + `data-request-uri={base}/issue/{n}/delete`, "아니오" 닫기, X 닫기 | `DELETE {base}/issue/{n}/delete`(Ajax) | `204` + `Location: {base}/issues?state=open` → `Request.js`가 그 주소로 이동. 일반 요청이면 303 | 작성자가 아닌 댓글이 있으면 400, 삭제 권한 없으면 403, 없는 이슈 404 | `:271-284`, `K/web/issue/IssueViewController.kt:142-160` |

### 3.3 공유자, 구독자, 하위 이슈

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 공유자 select2 `#issueSharer[multiple]` | select2 | `canUpdate`. 옵션은 현재 공유자(`selected`) | 열릴 때 `minimumInputLength: 1`, 검색 `GET .../sharableUsers?query=`(사용자와 공개 프로젝트). 처음에 현재 공유자의 `type`을 `GET .../findSharer?query=a,b`로 채운다. 선택(`select2:selecting`)이면 추가, 제거(`select2:unselecting`)이면 삭제를 보낸다. 값이 바뀌면 `.issue-sharer-count` 숫자를 갱신 | `POST {base}/issue/{n}/share` JSON `{sharer:{loginId,type:"user"|"project"},action:"add"|"delete"}` | 200 `{"action":..,"sharer":이름}` → 알림 `action: sharer` 3초. 실패는 조용히 무시 | `UPDATE`(없으면 403 `{"message":"Permission denied"}`), `findSharer`와 `sharableUsers`는 읽기 검사가 다르다(`issue-F5`) | `view.html:129-131`, `J/esm/service/issue.Sharer.js:43-114`, `K/web/api/IssueApiController.kt:145-209` |
| 공유자 이름 목록 | 링크 | `canUpdate`가 아닐 때 | 이동 | `GET /{loginId}` | | 없음 | `:132-138` |
| 공유자 수 `.issue-sharer-count` | 숫자 | 공유자가 있을 때 | | | | | `:125` |
| 구독자 자리 `.watcher-list` | 빈 `div` | 항상 | **채우는 코드가 없다**(`J`에서 `.watcher-list`를 찾는 곳이 없음) | | | | `:141` |
| 하위 이슈 `partial-subtasks` | 목록 | `issue.subtasks != null`(열림 또는 닫힌 하위 이슈가 있을 때. 공유받은 하위 이슈에서는 없음) | 부모 줄(링크, 진행 막대, `done/total`, 상태), 구분선, 하위 이슈마다 줄(상태 점, 번호, 제목 링크, 담당자 이름, 댓글과 추천 수 링크, 라벨 링크, 날짜). 현재 이슈는 `selected-child`. 링크는 `twoColumeModeTarget` | 이슈 보기, 목록 | 이동 | 읽기 | `:142-144`, `partial-subtasks.html`, `K/web/issue/IssueViewAssembler.kt:208-236` |

### 3.4 타임라인과 댓글(왼쪽 아래)

`#comments.board-comment-wrap > #timeline > .timeline-list`(`view.html:146-150`). 맨 위 `div.comment-header`(댓글 수), `<hr class="nm">`, `ul.comments`(비어 있지 않을 때). `ul` 안에 댓글 `li.comment`와 이벤트 `li.event`가 시간순으로 섞인다(`partial-comments.html:10-19`). 한 줄 댓글(`parentComment != null`)은 타임라인에서 빼고 부모 아래에 그린다(`IssueViewAssembler.kt:149`). 본문 변경 이벤트는 그리지 않는다(`IssueEventHtml.kt`, 시험 `TEST/IssueViewTest.kt:519`).

댓글 `li.comment#comment-{id}`(`partial-comment.html:10-80`), 작성자 본인이면 `author` 클래스, 내 이름이 들어 있으면 `mentioned`(`view.html:336`):

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 작성자 아바타, 이름 | 링크 | 항상 | 사용자 페이지 | `GET /{loginId}` | | 공개 | `:12-26` |
| 시각 `a.ago` | 링크 | 항상 | `#comment-{id}`로 이동(앵커) | | | | `:28` |
| `[Link]` `a.share-link` | 링크 | 처음엔 `display:none`, 댓글에 마우스가 들어오면 서서히 보임(`SubComment.js:37-48`) | 앵커 이동 | | | | `:29` |
| "이 댓글로 새 이슈"(`issue.menu.new.by`) | 링크 `.new-issue-by` | 항상(마우스 올림 때 보임, 비로그인에게도 있음) | 이동 | `GET /user/issues/new?commentId={id}` | 로그인하지 않으면 최근 방문 프로젝트가 없어 안내와 함께 첫 화면(`issue-F22`) | `IssueCreateController.newDirectIssueForm`: 방문한 프로젝트가 있어야 함. 그 프로젝트의 생성 권한은 따로 확인하지 않는다(폼에서 확인) | `:32-34`, `K/web/issue/IssueCreateController.kt:78-91` |
| 추천자 | 아바타 또는 요약 | `c.canRead`(그 댓글을 읽을 수 있음). 5명 초과면 "N명 동의" 링크(`#voters-{id}` 모달, 툴팁에 앞 5명 이름 + …), 아니면 아바타들 | 모달 열기 | 없음 | 모달은 `voterList` | 읽기 | `:35-47` |
| 추천/취소 하트 `[data-request-type=comment-vote]` | 단추 | `c.canRead`. `votedByMe`면 취소 단추(`.../unvote`), 아니면 비로그인에게는 비활성 아이콘, 로그인한 사용자에게는 추천 단추(`.../vote`) | 클릭 → 요청 → 성공하면 `location.reload()`, 실패하면 응답 본문(메시지 키)을 `Messages()`로 풀어 3초 알림 | `POST {base}/issue/{n}/comment/{id}/vote` 또는 `/unvote` | 서버 `303 → #comment-id`. 없는 댓글 404 평문 `issue.comment.error.vote`, 추천 안 한 댓글의 취소 404 평문 `...have.not.voted` | 로그인, 경로 프로젝트 `READ`. 댓글이 그 경로의 이슈와 프로젝트에 속하는지는 검사하지 않는다(`issue-F6`) | `:49-60`, `J/esm/service/issue.View.js:98-108`, `K/web/issue/IssueVoteController.kt:48-76` |
| 번역 `.comment-translate` | 단추 | `site.translationApiConfigured` | 한 번만. 그 댓글 본문(`#comment-body-{id} .markdown-wrap`)을 번역문으로 교체, 단추 `disabled` | `POST /translation` `{type:"issue-comment", number: 댓글번호, owner, projectName}` | 위 번역과 같음. 댓글이 경로의 프로젝트가 아니면 404 | 로그인 | `:62`, `view.html:381-399` |
| 수정 `[data-toggle=comment-edit]` | 단추 | `c.canRead`(**수정 권한이 아니라 읽기**) | 그 댓글의 본문 영역을 숨기고 `#comment-editform-{id}`를 보인다(`Comment.js:55-63`) | 없음 | 폼 열림 | 서버가 저장 때 `UPDATE`를 검사 | `:64`, `J/esm/Comment.js:35-63` |
| 삭제 `[data-toggle=comment-delete]` | 단추 | `c.canDelete` | `#comment-delete-modal` 열고 확인하면 `DELETE` | `DELETE {base}/issue/{n}/comment/{id}/delete` | Ajax면 `204` + `Location: 이슈 주소` → 이동. 권한 없음 403, 없는 댓글 404 | 댓글 `DELETE`(작성자, 프로젝트 관리자, 조직 관리자, 사이트 관리자) | `:66-67`, `common/comment-delete-modal.html`, `J/esm/Comment.js:49-52`, `K/web/issue/IssueCommentController.kt:80-95` |
| 수정 폼 `common/comment-update-form` | 폼 | `.comment-update-form`(처음엔 숨김). 저장 단추는 `c.canUpdate`일 때만, 알림 메일 체크는 `c.own`일 때만 | 저장 = 폼 제출. 취소 = `.ybtn-cancel`로 닫기. 파일 올리기, 드래그와 붙여넣기 이미지 업로드(`CommentAttachmentsUpdate.js`) | `POST {base}/issue/{n}/comments/{id}` 멀티파트: `id`, `contents`, `notificationMail=yes`, `temporaryUploadFiles` | `303 → {이슈}#comment-{id}`. 본문이 비면 400 오류 화면(`post.comment.empty`). 권한 없음 403 | 댓글 `UPDATE`(작성자, 구성원, 관리자 등) | `common/comment-update-form.html`, `K/web/issue/IssueCommentController.kt:50-78` |
| 본문 `.comment-body.markdown-wrap` | HTML | `data-allowed-update=c.canUpdate`, `data-via-email`(이메일로 온 댓글) | 체크리스트 클릭 → `PATCH {base}/issue/{n}/comments/{id}` `{content, original}` | | 위 이슈 본문과 같다(409, 403 등). 권한 검사가 충돌 검사보다 먼저 | 댓글 `UPDATE` | `:73-77`, `K/web/api/IssueApiController.kt:290-317` |
| "한 줄 댓글 추가"(`comment.oneline.comment.placeholder`) `.add-a-comment` | 링크 | 마우스 올림 때 보임. 한 줄 댓글 입력 폼은 `c.canReply`(그 이슈에 댓글 생성 권한)일 때만 있다 | 클릭하면 입력 폼 토글, 초점. `CTRL/⌘+ENTER`로 제출, `ESC`로 모든 한 줄 입력 닫기 | `POST {base}/issue/{n}/comments` 멀티파트 `contents`, `parentCommentId` | `303 → 댓글 앵커` | 댓글 생성(`isResourceCreatable`) | `common/child-comments.html:11-27`, `J/esm/SubComment.js:13-35` |
| 한 줄 댓글 삭제 `x` | 링크 | `child.canDelete` | 댓글 삭제와 같은 모달 | `DELETE {base}/issue/{n}/comment/{childId}/delete` | | 한 줄 댓글 `DELETE` | `child-comments.html:20-21` |

이벤트 `li.event#event-{id}`(`IssueEventHtml.kt:49-82`)는 상태 변경, 담당자, 마일스톤, 커밋과 풀 리퀘스트 참조, 이동, 공유자 추가와 삭제, 라벨 추가와 삭제를 그린다. 라벨 색 상자는 `div.label.issue-label[style=background-color]`이고 `view.html:413-417`이 배경색의 밝기로 글자 색(`dimgray`/`white`)을 정한다(`$yona.getContrastColor`).

### 3.5 댓글 쓰기(`common/comment-form`)

`#comment-form`(멀티파트, `action={base}/issue/{n}/comments`, `view.html:152`): `canCreate = issue.canVoteOrComment`(`IssueViewAssembler.kt:98`, `isResourceCreatable(issue, ISSUE_COMMENT)`)이면 폼, 아니면 비활성 상자(`data-login="required"`, 누르면 로그인 대화상자).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| 에디터 `textarea[name=contents]#editor-contents-*` | textarea + 탭(편집, 미리보기) + 서식 도구 + 체크리스트 추가 | 폼일 때 | 입력 5초 뒤 `localStorage[location.pathname]`에 초안 저장, 포커스하면 알림 받을 사람 미리 보기 시작(아래), `@` 멘션 자동 완성 | 멘션 `GET {base}/mentionList?number={n}&resourceType=issue_post&mentionType=user|issue&query=` | JSON `{result:[...]}` | `@IsAllowed(READ)`(`MentionListController.kt:44`) | `common/comment-form.html:13`, `view.html:329-333` |
| 알림 받을 사람 `.notification-receiver-list` | 목록 | 에디터 초점 때 `.notification-receiver` 보임 | 포커스하면 즉시, 이후 `keyup` 1초 뒤(디바운스)에 요청. 최대 10명 + `+N`. 포커스를 잃으면 핸들러 해제 | `POST {base}/issue/{n}/commentNotiReceivers` JSON `{comment, parentCommentId}` | `{"receivers":[사용자 노드]}`. 실패는 경고창 | 읽기 검사 없음(`issue-F5`) | `view.html:442-452`, `J/esm/ReceiverList.js`, `K/web/api/IssueApiController.kt:260-272` |
| 파일 올리기 | 업로드 영역 `#upload` | 로그인 사용자만(`!currentUser.anonymous`) | 파일 선택, 드래그, 붙여넣기 → 임시 업로드 | `POST /files` | 임시 첨부 id가 `temporaryUploadFiles`에 담겨 댓글 저장 때 이슈 댓글로 옮겨진다 | 로그인 | `comment-form.html:15-17` |
| 이슈 닫기/다시 열기 `#dynamic-comment-btn` | 단추 | 스크립트가 `hidden` 클래스를 지워 보이게 한다. 글자: 입력이 비면 `button.nextState.closed|open`(이슈 닫기, 다시 열기), 있으면 `button.commentAndNextState.*` | 비었으면 `yona.http.post({base}/issue/{n}/nextstate)` 뒤 새로 고침(2026-11-11부터 POST이다. 서버는 v1.6 호환으로 GET도 받는다, issue-F7), 입력이 있으면 `withStateTransition=true` 숨은 입력을 달아 폼 제출. 단축키 `CTRL+SHIFT+ENTER`도 같다(대상이 댓글 textarea일 때) | `POST .../nextstate`(GET도 받는다), 또는 `POST .../comments` + `withStateTransition=true` | nextstate: `303 → 이슈 보기`(상태 `closed`↔`open`). 댓글+전환은 `303 → 댓글 앵커` | nextstate는 이슈 `UPDATE`(`@IsAllowed(UPDATE, ISSUE_POST)`, 403). 댓글 생성 권한만 있는 사용자가 전환을 보내면 서버가 어떻게 처리하는지는 `IssueCommentService.add`가 정한다(이 조사에서는 확인하지 않음) | `view.html:152`, `issue.View.js:463-498`, `K/web/issue/IssueViewController.kt:125-137` |
| 댓글 달기 | 제출 단추 | 폼일 때 | `CommentForm.js`가 제출을 가로채 본문이 비면 `post.comment.empty` 알림 3초, 아니면 NProgress 후 0.3초 뒤 제출(중복 제출 방지, `ESC`로 푼다). 임시 초안 삭제 | `POST {base}/issue/{n}/comments` 멀티파트 `contents`, `filePath`, `temporaryUploadFiles`, `withStateTransition` | `303 → {이슈}#comment-{id}`. 서버에서 본문이 비면 400 | `isResourceCreatable(이슈, ISSUE_COMMENT)`. 불가이면 403 | `comment-form.html:22`, `J/esm/CommentForm.js:47-72`, `IssueCommentController.kt:50-78` |

창을 닫거나 떠날 때 댓글 입력란에 글이 남아 있고 제출 중이 아니면 `common.comment.beforeunload.confirm` 확인창(`CommentForm.js:111-115`).

### 3.6 오른쪽 단(`.issue-info.sticky-top`)

`form#issueUpdateForm[action={base}/issues][method=post]`(`view.html:158`). 폼 자체는 제출하지 않는다. 각 입력의 `change`를 `J/esm/service/issue.View.js:88-89`가 받아 **개별 ajax로** `POST {base}/issues`(일괄 변경)에 `issues[0].id`와 바뀐 필드만 보낸다(`issue.View.js:159-213`).

| 요소 | 종류 | 조건 | 동작 | 요청 | 응답과 이어지는 동작 | 권한 | 근거 |
|---|---|---|---|---|---|---|---|
| "새 하위 이슈" | 링크 | `projectView.menuSetting.issue` | 이동 | `GET {base}/issueform?parentIssueId={issue.parentIssueId}` | | 로그인 + 이슈 생성 | `view.html:163-165` |
| 담당자 select2 `#assignee[name=assigneeLoginId]` | select2 | `canUpdate`, 아니면 이름 링크 또는 `issue.noAssignee` | 열면 `GET .../assignableUsers`(검색어 없으면 나에게 할당, 작성자에게 할당, 담당자 없음, 현재 담당자, 담당 가능 사용자 순). 선택(`select2:selecting`)이면 바로 저장하고 `이슈.assignee: 이름` 알림 3초. 이 select에는 `data-toggle=select2`가 없어 위 일괄 변경 경로를 타지 않는다 | `POST {base}/issue/{n}/assignees` JSON `{"assignees":[로그인아이디 또는 ""]}` | 200 `{"assignee":{loginId,name},"issue":경로}`. **수정 권한이 없으면 400 아닌 200 `{"issue":경로}`만**(`issue-F4`). 빈 `assignees`는 400 `No assignee` | 이슈 `UPDATE` | `view.html:168-181`, `partial-assignee.html`, `issue.Assginee.js:43-89`, `IssueApiController.kt:79-140` |
| 마일스톤 | 링크 또는 select2 | `menuSetting.milestone`일 때만 `dl`. 마일스톤이 하나도 없으면 "새 마일스톤"(`target=_blank`, **권한과 무관**), 있으면 `canUpdate`일 때 `#milestone[name="milestone.id"]` select2(`data-format=milestone`, 옵션 `-1` 없음, 열림/닫힘 optgroup), 아니면 링크 또는 `issue.noMilestone` | 값이 바뀌면 일괄 변경 요청, 성공하면 타임라인 새로고침(`GET .../timeline`)과 `issue.update.milestone.id` 알림 | `GET {base}/newMilestoneForm`(링크), `POST {base}/issues` `milestone.id=` | `{}` JSON | 이슈 `UPDATE` | `view.html:186-206` |
| 마감일 `input[name=dueDate][data-toggle=calendar]` + 달력 단추 | 달력 | `canUpdate`, 아니면 날짜 글자 또는 `issue.noDuedate` | 값이 바뀌면 `isValidDate` 검사(틀리면 `issue.error.invalid.duedate`, 중단), 일괄 변경으로 `dueDate`와 `isDueDateChanged=true`. 응답의 `dueDateMsg`를 `(…)`로 `.duedate-status`에 넣고 `isOverDue`면 `overdue` 클래스. 지우면 서버가 `null`을 주어 화면에 `(null)`이 찍힌다(`issue-F8`) | `POST {base}/issues` | `{"isOverDue":bool,"dueDateMsg":문자열|null}` | 이슈 `UPDATE` | `view.html:208-225`, `issue.View.js:116-139`, `IssueMassUpdateController.kt:101-109` |
| 라벨 select2 `#labelIds` | select2 다중 | `projectLabels`가 비어 있지 않을 때. `canUpdate`면 select(`partial-select-label`, 배타 분류 라벨을 고르면 같은 분류의 이전 선택을 해제), 아니면 `a.issue-label.static` 목록(링크 `{base}/issues?state={이슈상태}&labelIds=`) | 선택 변화에서 `Select2.selectionChanges`로 추가분과 제거분을 구해 `attachingLabelIds`, `detachingLabelIds`로 일괄 변경 | `POST {base}/issues` | `{}` | 이슈 `UPDATE` | `view.html:228-231`, `issue.View.js:200-206`, `partial-select-label.html` |
| 수정, 삭제 아이콘 | 단추 | 동작 줄과 같은 조건(둘째 줄) | 같음 | | | | `:232-242` |
| 댓글 색인 `#comments`(두 번째) | 목록 | 댓글 또는 이벤트가 하나라도 있으면 `ul.comments`. 한 줄 댓글을 뺀 댓글마다 앞 60자(`...`), 작성자, 시각, 한 줄 댓글 수(`yobicon-comment2`), 멘션되었으면 `mentioned`/`mentionedInChild` | 클릭하면 `#comment-{id}`로 이동(`window.location = data-location`, 기록 push/replace) | 없음 | | | `:244-250`, `partial-index-comments.html`, `view.html:454-461` |

일괄 변경 요청이 실패하면(`yona.http.ajax` 거부) `error.failedTo`(필드 이름, 상태, 상태 글)를 알림으로 보인다(`issue.View.js:172-177`). 성공하면 `GET {base}/issue/{n}/timeline`으로 타임라인 조각을 다시 읽어 `.timeline-list`를 0.5초 뒤 바꿔 끼운다(변하지 않았으면 건너뛴다, 같은 파일 318-370).

## 4. 스크립트 동작 요약

- 불러오는 모듈(`view.html:291-302`): `labels.css`(`GET {base}/issue/labels.css`, 라벨 색 스타일시트, `ETag` 304), `jquery.elevator`(css), `videojs`(video.min.js와 css, 첨부 동영상), `issue.Assginee.js`, `issue.Sharer.js`, `detectChange.js`, `Tasklist.js`, `SubComment.js`, `CommentAttachmentsUpdate.js`, `ReceiverList.js`, `issue.View.js`. 공통 조각: `common/markdown`, `common/comment-delete-modal`(→`Comment.js`), `common/select2`, `common/calendar`.
- `$yona.loadModule("issue.View", {issueId, nextState, urls:{watch, unwatch, timeline, nextState, massUpdate}})`(`view.html:306-316`).
- 인라인 스크립트: `yona.Mention`(대상 `textarea[id^=editor-], .editorSeries`), 내 이름이 든 댓글에 `mentioned` 클래스와 내 사용자 링크에 `me` 클래스(`:336-337`), `yonaAssgineeModule`, `yonaIssueSharerModule`, 번역, 이메일 복사, 라벨 글자색 보정, `yona.ui.Elevator.init({shape:'rounded', tooltips:true})`(왼쪽 위로 가기 도우미), 가중치, 알림 받을 사람 미리 보기, 색인 클릭, `detectPageChange(.../detectChange)`.
- **변경 감지 폴링**(`J/esm/service/detectChange.js:23-77`): 첫 호출은 3.6초 뒤이고 이후 간격은 직전 간격의 1.2배씩 늘어 5분(300초)에서 멈춘다(`runIntervalAction`: 3000이 5분 초과가 아니면 `*1.2`, 초과면 5분 고정). 매번 `POST {base}/issue/{n}/detectChange` JSON `{issueBodyChecksum, numOfComments, lastUpdateDate}`. 응답에서 `numOfComments`가 1 늘었으면 "New comment by 이름" 알림(`$yona.notify(html, 0, 제목)`, 닫지 않는 알림), 2 이상 늘었으면 "New comments added!", `issueBodyChanged`면 "Issue updated!". 모두 "Reload page" 링크(`location.reload(true)`)와 파비콘 `N` 배지(`yona.ui.Favicon`). 서버는 `lastUpdateDate`를 읽지 않는다. 비로그인이면 401을 받고 무시하지만 폴링은 계속된다(`issue-F16`).
- 로컬 저장소: 댓글 초안은 `localStorage[location.pathname]`(수정 댓글 초안은 `pathname + "-last-comment-update-draft-" + 댓글번호`), 시간 기록 `yona.draftTimes`, 1시간 지나면 지운다, 5초 입력 멈춤 뒤 저장(`J/esm/service/temporarySaveHandler.js`).
- 죽은 코드: `issue.View.js`의 `_setTimelineUpdateTimer`(호출이 주석 처리, 29행), `_isDockedInspectorOpened`, `_unsetTimelineUpdateTimer`는 쓰이지 않는다. `Tasklist.js:18-26`의 `$this.closest()`는 인자가 없어 빈 집합이므로 그 클릭과 호버 처리는 아무 일도 하지 않는다(`issue-F13`).

## 5. 단축키(화면 범위)

| 키 | 동작 | 등록 | 조건 |
|---|---|---|---|
| `L` | 목록으로 이동(`listUrl`) | `view.html:319-321` `setKeymapLink` | 항상. 초점이 입력란이면 무시 |
| `N` | 새 이슈 폼 | 같은 곳 | `projectView.menuSetting.issue`일 때만 등록 |
| `E` | 수정 폼(`{base}/issue/{n}/editform`) | 같은 곳 | `issue.canUpdate`일 때만 등록. "원본 보기" 사용자에게는 없다 |
| `CTRL+SHIFT+ENTER` | `#dynamic-comment-btn` 클릭 | `issue.View.js:491-497` | 초점이 댓글 textarea(`data-editor-mode=comment-body`)일 때만 동작한다(2026-11-11부터 실제로 동작한다, shell-F4) |
| `CTRL/⌘+ENTER` | 한 줄 댓글 폼 제출 | `SubComment.js:20-27` | 한 줄 댓글 textarea에서 |
| `ESC` | 한 줄 댓글 입력 닫기, 제출 중 풀기 | `SubComment.js:28-33`, `CommentForm.js:99-104` | |

`ShortcutKey`는 키 하나에 처리기 하나이므로 나중에 등록한 것이 이긴다(`ShortcutKey.js`의 `_setHandler`). 이 화면에서 같은 키를 두 번 등록하는 곳은 없다. 단축키 도움말(`issueDetail`)은 `L`, `E`, `N`을 권한과 무관하게 모두 보여 준다(`T/help/keymap.html`).

## 6. 팝업, 모달, 툴팁, 알림

| 대상 | 여는 요소 | 내용 | 닫기 | 서버 요청 |
|---|---|---|---|---|
| `#deleteConfirm` 삭제 확인 | 삭제 아이콘(`href="#deleteConfirm" data-bs-toggle="modal"`) | `issue.delete` 제목, `post.delete.confirm` 본문, 예/아니오 | X, 아니오, Esc, 바깥 클릭(부트스트랩 기본) | 예 → `DELETE .../delete` |
| `#comment-delete-modal` | 댓글 삭제 단추 | `common.comment.delete.confirm`, 예/아니오 | 같음 | 예 → `DELETE .../comment/{id}/delete`(`yona.Request.send`) |
| `#-yona-posting-history` 변경 이력 | "수정함" 링크 | 이력 HTML | 같음 | 없음 |
| `#voters`, `#voters-{id}` | 추천자 링크 | 사람 목록(새 탭 링크), 이메일 복사, 닫기 | 같음 | 없음 |
| `#helpKeys` | 단축키 도움말 | 키 목록 | 확인 단추 | 없음 |
| 팝오버/툴팁 | 공유 단추(`issue.sharer.description`), 가중치 숫자(`issue.weight.description`), 삭제 불가 단추, 구독 단추(`issue.watch.description`) 등 | 설명 | hover(`data-bs-trigger="hover"`) | 없음 |
| 로그인 대화상자 | `[data-login="required"]` 클릭(비활성 추천 하트, 비활성 댓글 상자) | 로그인 안내 | `J/esm/LoginDialog.js` | 로그인 폼 |
| 알림 `$yona.notify` | 담당자, 공유자, 구독, 일괄 변경 성공 등 | 3초 후 사라짐. 변경 감지 알림은 0(닫을 때까지) | | |
| 경고 `$yona.alert`/`showAlert` | 체크리스트 충돌, 이메일 복사 성공, 알림 받을 사람 오류 | 확인 단추 | | |

## 7. 요청과 응답 계약(이 화면에서 나가는 요청)

모든 상태 변경 요청은 `yona.Csrf.js`가 헤더 `X-XSRF-TOKEN`(쿠키 `XSRF-TOKEN`) 또는 폼의 `_csrf`로 토큰을 싣는다. `yona.http.ajax`는 같은 출처 요청에 `X-Requested-With: XMLHttpRequest`와 `dataType`별 `Accept`를 붙인다(`J/esm/http.js:105`, `128`).

| 요청 | 호출 위치 | Accept와 본문 | 정상 | 오류 |
|---|---|---|---|---|
| `GET {base}/issue/{n}/timeline` | `issue.View.js:328` | HTML 조각 | `ul.comments` 조각 | 404 |
| `POST {base}/issues` | `issue.View.js:167` | 폼 인코딩, `dataType=json` | `{}` 또는 `{isOverDue,dueDateMsg}` | 400 JSON(필수, 잘못된 값), 403 HTML, 404 HTML |
| `POST .../assignees`, `.../share`, `.../upvoteWeight`, `.../downvoteWeight`, `.../detectChange`, `.../commentNotiReceivers`, `PATCH .../content`, `PATCH .../comments/{id}` | 위 3절 | JSON 본문 | 위 표 | JSON이 아니면 `400 {"message":"Expecting Json data"}`, 권한 없음 403 `{"message":...}`, 비로그인 401 `{"message":"unauthorized request"}`(변경 감지, 본문, 댓글 갱신) |
| `GET .../assignableUsers?query=&type=`, `.../sharableUsers?query=`, `.../findSharer?query=` | select2 | JSON을 받지 못하면 `406` | 사용자 노드 배열 `{loginId,name,pureNameOnly,avatarUrl,type}`. 10명 초과면 `Content-Range: items 10/전체` | 406 |
| `POST /watch`, `/unwatch` | `issue.View.js:271` | 쿼리 `resource.type`, `resource.id` | 200 | 403 평문 |
| `POST {base}/issue/{n}/vote|unvote`, `.../comment/{id}/vote|unvote` | `Request.js`, `issue.View.js:99` | 폼 | `303` | 404 평문(댓글), 로그인 폼 303 |
| `POST/DELETE` 댓글 | 폼, `Request.js` | 멀티파트 | 303 또는 204 + `Location` | 400, 403, 404 |
| `DELETE {base}/issue/{n}/delete` | `Request.js` | 없음 | `204` + `Location` | 400, 403, 404 |
| `POST /translation` | `view.html:369`, `389` | JSON | 200 `{"translated"}` | 412, 403, 404 |
| `POST /user/favoriteIssues/{id}` | `Usermenu.js:122` | 없음 | 200 JSON | 403 빈 본문, 400 평문 |

## 8. 권한 행렬(이슈 보기)

규칙의 출처: `K/access/AccessControl.kt`(`isAllowed` 106행, 프로젝트 리소스 규칙 165-225, 보조 규칙 228-262, 상수 304-328). 수정(`UPDATE`)은 작성자, 담당자, 프로젝트 관리자, 조직 관리자, 사이트 관리자, 구성원, 그룹(조직) 구성원(공개나 보호 프로젝트). **삭제(`DELETE`)는 작성자, 프로젝트 관리자, 조직 관리자, 사이트 관리자만**(`AccessControl.kt:195-196`, 추가 개발 항목 D-48). 공유자는 읽기와 댓글만 가능하다(`isAllowedIfSharer`는 읽기 기본 규칙과 `isResourceCreatable`에만 쓰인다).

| 기능 | 비로그인 | 로그인 비구성원(공개) | 공유자 | 담당자 | 구성원 | 작성자 | 프로젝트 관리자 | 조직 관리자 | 사이트 관리자 |
|---|---|---|---|---|---|---|---|---|---|
| 보기 | 허용(익명 접근 허용 시) | 허용 | 허용(비공개도) | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 초안 보기 | 403 | 403 | 403 | 403 | 403 | 허용 | 403 | 403 | 403(`IssueViewController.kt:82`: 작성자만) |
| 지켜보기 단추 | 안 보임 | 보임 | 비공개면 안 보임(WATCH는 구성원 또는 공개 로그인) | 보임 | 보임 | 보임 | 보임 | 보임 | 보임 |
| 추천 | 로그인 대화상자 | 보임, 서버 허용(`READ`만 필요) | 보임 | 보임 | 보임 | 보임 | 보임 | 보임 | 보임 |
| 댓글 쓰기 | 비활성 상자 | 가능(공개 프로젝트) | 가능 | 가능 | 가능 | 가능 | 가능 | 가능 | 가능 |
| 댓글 수정 단추 | `canRead`면 보임(저장은 `UPDATE`) | 보임, 저장 403(작성자 아님) | 같음 | 같음 | 보임, 저장 허용 | 허용 | 허용 | 허용 | 허용 |
| 댓글 삭제 | 안 보임 | 본인 것만 | 본인 것만 | 본인 것만 | 본인 것만 | 본인 댓글 | 모두 | 모두 | 모두 |
| 이슈 본문 수정(편집 폼, 체크리스트) | "원본 보기" | "원본 보기" | "원본 보기" | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 담당자, 마일스톤, 마감일, 라벨 변경 | 읽기 전용 표시 | 읽기 전용 | 읽기 전용 | 변경 | 변경 | 변경 | 변경 | 변경 | 변경 |
| 공유자 변경, 가중치 | 가중치 단추는 보이고 서버 403, 공유 단추 없음 | 위와 같음 | 같음 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |
| 이슈 삭제 | 안 보임 | 안 보임 | 안 보임 | 안 보임(`deletable`이면 아이콘 없음) | 안 보임 | 보임(다른 사람 댓글이 없을 때) | 보임 | 보임 | 보임 |
| 다음 상태(닫기, 열기) | 단추는 보이나 서버 403 | 403 | 403 | 허용 | 허용 | 허용 | 허용 | 허용 | 허용 |

"다음 상태" 단추(`#dynamic-comment-btn`)는 댓글을 쓸 수 있는 사용자에게만 폼 안에 있으므로, 위 표의 "비로그인"과 "비구성원" 칸은 폼이 있는 공개 프로젝트의 로그인 비구성원 경우를 뜻한다. 비구성원이 댓글 없이 단추를 누르면 `nextstate` 요청이 403 오류 화면이다.

## 9. 빈 상태, 오류 상태, 경계 값

- 댓글 없음: 머리와 `<hr>`만 있고 `ul`은 그리지 않는다(`partial-comments.html:14`).
- 추천자 없음: 하트만(`view.html:94`, `.voter-exists` 클래스는 추천자가 있을 때).
- 추천자 아바타 수 3, 이름 수 5(`view.html:103` 인자), 댓글 추천자 요약은 5명 초과일 때(`partial-comment.html:36`).
- 댓글 색인 줄은 본문 앞 60자(`ELLIPSIS_LENGTH`, `IssueViewAssembler.kt:242`).
- 변경 감지 간격 상한 5분(`detectChange.js:35-37`), 알림 지속 3초(`$yona.notify` 인자 3000).
- 댓글 초안 저장 지연 5초, 보관 1시간, 정리 주기 5분(`temporarySaveHandler.js` 상수).
- 삭제는 이슈 작성자가 아닌 사람의 댓글이 하나라도 있으면 거부(`IssueService.canBeDeleted`, `K/domain/issue/IssueService.kt:177-179`).
- 본문 크기 한도 102400바이트(`parsers.text.maxLength`)는 `application/x-www-form-urlencoded`에만 적용되고(`K/web/TextBodyLimitFilter.kt:34-36`) 이 화면의 댓글과 이슈 폼은 멀티파트이므로 적용되지 않는다. 일괄 변경 폼은 urlencoded이지만 본문이 작다.

## 10. 시험 근거

`TEST/IssueViewTest.kt`(보기 구성 168-239, 구독 269, 공유 282, 삭제 모달 309, 추천 319-344, 공유자 360, 하위 이슈 375, 타임라인 402-519, 댓글 폼 539, 초안 554, 오른쪽 창 564-615, 없는 이슈와 JSON 629-654), `TEST/IssueActionsTest.kt`(다음 상태 96-124, 삭제 138-184), `TEST/IssueCommentTest.kt`(117-381), `TEST/IssueApiTest.kt`(담당자 127-218, 공유자 231-304, 알림 수신자 317-337, 가중치 355, 변경 감지 371-386, 본문과 댓글 갱신 396-463), `TEST/WatchAndFavoriteTest.kt`. 화면 스크립트(`issue.View.js`, `Tasklist.js` 등)의 단위 시험은 이 조사에서 찾지 못했다(시험 없음). 비공개 프로젝트에서 `detectChange`, `commentNotiReceivers`, `findSharer`를 거부하는 시험도 찾지 못했다.
