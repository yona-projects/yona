---
title: 담당 C 이슈, 마일스톤 영역 발견 사항 재현과 v1.6 대조
type: ui-contract-verification
updated: 2026-11-11
---

# 담당 C: 이슈, 마일스톤 영역 발견 사항 재현과 v1.6 대조

## 요약

- 대상 28건(`issue-F2, F3, F7~F13, F15~F25`, `milestone-F1, F3~F9`). 모두 판정했다.
- 검증 환경: v2 검증용 앱(포트 19412, 사본 `work/C`)과 공식 v1.16.0 실행본(포트 19512, 스냅샷 `data2`)을 모두 직접 띄워 같은 요청과 같은 화면 동작을 비교했다. 실행 결과를 1순위로 하고 v1.6 소스(`/home/jiho/yona-convert/yona-v1.6-run`)를 보조 근거로 썼다. 스크립트는 `work/C/scripts/`에 있다.
- 판정별 개수(항목 단위, 묶음은 항목마다 센다)

| 판정 | 개수 | 항목 |
|---|---|---|
| v2 신규 결함 | 1 | issue-F20 |
| v2 추가 기능의 결함 | 0 | |
| v1.6 결함 유지 | 24 | issue-F2, F3, F7, F8, F9, F11, F12, F13, F15, F16, F18, F19, F21, F22, F23, F24, F25, milestone-F1, F3, F5, F6, F7, F8, F9 |
| 결함 아님 | 2 | issue-F17, milestone-F4 |
| 문서 문제 | 1 | issue-F10 |
| 재현 불가 | 0 | |

합계 28.

- **v2 신규 결함 목록**: issue-F20(댓글 추천자 모달의 이메일 복사 단추가 동작하지 않음. v1.6은 둘 다 동작).
- 이번에 데이터를 만들어 재현한 것: 추천자 6명 가입과 추천(F20), 배타 분류 라벨과 다른 프로젝트 라벨(F9), 번역 API 주소를 지정해 재기동한 v2와 v1.6(F21), 프로젝트 메뉴 설정 변경(F24, milestone-F6), 다른 프로젝트 번호 마일스톤(milestone-F1) 등.
- 부수 관찰(발견 사항 밖): v1.6은 이슈 제목 앞에 `[프로젝트] ` 머리말이 붙어 길이 한도 근처에서 DB 오류 500이 난다(F11 참고).

## 항목별 결과

범례: 재현 열은 v2 앱에서 실제로 일어났는지다. v1.6 열은 v1.6을 실행해 같은 요청을 한 결과이고 소스 근거를 함께 적는다. `V6` = `/home/jiho/yona-convert/yona-v1.6-run`.

### 이슈 영역(issue.md)

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| issue-F2 | 재현됨 | 작성 화면 `/admin/hglfs/issueform`과 수정 화면 `/admin/hglfs/issue/18/editform`을 열어 `#milestoneOption .yobicon-refresh` 개수를 셈: 둘 다 0, `.yobicon-refresh` 전체도 0, `#milestoneOption` 요소 자체도 없음(ms false). | 같음. v1.6 `/tester/proj1/issueform`, `/issue/2/editform`: `#milestoneOption .yobicon-refresh` 0, `.yobicon-refresh,.icon-refresh` 0(`#milestoneOption`은 있으나 새로고침 아이콘 없음). 설정 줄 `V6/app/views/issue/create.scala.html:166`, `edit.scala.html:206`, 처리기 `V6/public/javascripts/service/yobi.issue.Write.js:55`. | v1.6 결함 유지(죽은 코드) | 장부에 올린다 |
| issue-F3 | 재현됨 | 비로그인과 `plainuser`(비구성원) 모두 `/admin/hglfs/issue/52`에서 가중치 단추(`#upvote-issue-weight`)와 별(`.favorite-issue`)이 보임. 가중치 단추 클릭: `POST .../issue/52/upvoteWeight` 403, 화면 숫자 0 그대로(알림 없음). 직접 호출 `plainuser`: 403 `{"message":"Permission denied"}`. 별 클릭: 비로그인은 `POST /user/favoriteIssues/82` 403 후 `Unexpected end of JSON input` 예외, 로그인은 200. | 같음. v1.6 비로그인: 가중치 403, `POST /-_-api/v1/favoriteIssues/2` 500 후 `Unexpected token '<' ... is not valid JSON` 예외(상태 코드는 403 대신 500이나 같은 형태의 실패 처리 부재). 구성원 아닌 `third`도 가중치 403. 소스 `V6/app/views/issue/view.scala.html:207`(가중치 조건 없음), `:619-626`(실패 처리기 없음), `V6/app/controllers/api/IssueApi.java:1176-1183`(403 같은 본문), `V6/public/javascripts/common/yona.Usermenu.js:135`(`JSON.parse(data.responseText)`). | v1.6 결함 유지 | 장부에 올린다 |
| issue-F7 | 재현됨 | `admin`으로 `GET /admin/hglfs/issue/52/nextstate`: 303 `/admin/hglfs/issue/52`, 상태가 open에서 closed로 바뀜. 한 번 더(헤더 `Purpose: prefetch` 포함) 부르니 다시 open. | 같음. v1.6 `GET /tester/proj1/issue/2/nextstate` 303, 상태 Closed에서 Open으로 바뀜. 소스 `V6/conf/routes:270`, `V6/app/controllers/IssueApp.java:682-696`. | v1.6 결함 유지 | 장부에 올린다 |
| issue-F8 | 재현됨 | 보기 화면의 마감일 입력란에 `2030-01-01` 입력 후 change: `(1,147일)` 표시. 값을 지우고 change: `.duedate-status`가 `(null)`로 바뀜. | 같음. v1.6도 마감일을 지우면 `(null)` 표시. 소스 `V6/app/controllers/IssueApp.java:446-451`(`issue.until()`이 null), `V6/public/javascripts/service/yobi.issue.View.js:151`. | v1.6 결함 유지 | 장부에 올린다 |
| issue-F9 | 재현됨 (a~d 모두) | (a) 목록 화면에 `#delete` 요소 없음, 일괄 변경 폼 `#mass-update-form`만 있음. (b) `admin`이 `POST /plainuser/ownerproj/issues`로 `issues[0].id=82`(hglfs 이슈 52) `state=CLOSED`: 200 `{}`, 이슈 52가 closed로 바뀜(경로 프로젝트 소속 검사 없음). `plainuser`가 같은 요청을 하면 403(권한은 이슈 기준). (c) 배타 분류 라벨 X1, X2를 한 이슈에 차례로 붙임: 둘 다 선택됨. 다른 프로젝트(`plainuser/ownerproj`)의 라벨 9를 붙임: 200이고 `labelIds=9` 검색에 이슈 52가 나옴(`labelIds=999`에는 안 나옴). (d) `state=MERGED`, `DRAFT`, `REJECTED` 모두 200이고 이슈 상태가 바뀜. `bogus`는 400 `{"state":["Invalid value"]}`. | 같음. v1.6 `tester/proj1`: (a) `#delete` 없음. (b) `POST /tester/proj1/issues`로 `tester/priv1` 이슈(id 4)를 CLOSED로 바꿈 200. (c) 배타 분류 X1, X2 둘 다 붙음, 다른 프로젝트 라벨 35도 붙음. (d) MERGED 200, DRAFT 200(이슈가 Draft가 됨), `bogus` 400 `{"state":["Invalid value"]}`. 소스 `V6/app/controllers/IssueApp.java:389-445`(`issue.refresh()` 후 이슈별 권한만 검사, 라벨 검사 없음), `V6/app/views/issue/partial_massupdate.scala.html:139`. | v1.6 결함 유지 | 장부에 올린다 |
| issue-F10 | 재현됨(문서 대 코드 대조) | (a) `web/issue-view.md:63-65`, `web/issue-edit.md:35-37`에 "3c-3에서 옮긴다" 대기 문구가 남아 있음. 그러나 가중치, 일괄 변경, 구독, 추천, 댓글, 변경 감지, 번역 서버 쪽은 이 검증에서 모두 동작함(F3, F7, F8, F9, F16, F21). (b) `web/my-issues.md:35`, `web/issue-list.md:61`은 `div[pjax-container]`로 적었으나 `partial-list-wrap.html:10`은 `div list-container`(같은 파일 `issue-list.md:19`는 맞게 적음). (c) `IssueService.kt:210-211`이 멘션 기록을 지우는데(`mentions.deleteAll`) `web/issue-view.md:61`은 "지우지 않는다(R-24)". 코드 주석이 "v1.6은 남겨 고아 행이 되었다"고 밝힘. | (c)만 v1.6과 동작이 다른 의도된 수정이다(삭제 시 멘션 정리). v1.6 쪽 멘션 기록 잔존은 DB를 직접 보지 않아 실행으로는 확인하지 못했고 코드 주석에 따랐다(실행 불가, 소스 대조). 나머지는 위키 서술 문제로 v1.6과 무관. | 문서 문제 | 문서만 고친다 |
| issue-F11 | 재현됨 (a, b, c). b는 서술 틀림 | (a) 작성 화면 제목 입력란 `maxlength="250"`. 서버 한도 255: 제목 250, 251, 255자는 303으로 작성됨, 256자는 400. (b) 256자 제목은 400이며 화면 문구가 `Maximum length is 255`로 숫자가 채워져 나옴(영어로 나옴. 한국어 번역 없음). 발견 사항이 예상한 `{0}` 그대로 출력은 틀렸다. (c) `plainuser`가 `/plainuser/ownerproj/issues/latest`에 `milestoneId=1`(다른 프로젝트 `admin/hglfs`의 마일스톤)로 작성: 303, 만들어진 이슈 4에 `milestone/1` 링크가 붙음. | v1.6: (a) 제목 입력란 `maxlength="250"`(`V6/app/views/issue/create.scala.html:45`). 서버 한도는 DB 열 255이고 제목 머리말 `[프로젝트] `이 더해져 `a`x255 제목도 DB 오류 500(`Value too long for column "TITLE VARCHAR(255)"`), 256자 이상 제목은 500 오류 화면. 즉 v1.6은 서버 검증이 없고 500이다. (c) `tester/priv1`에 `milestoneId=1`(`tester/proj1`의 마일스톤)로 작성하니 303이고 `proj1` 마일스톤 목록에 그 이슈가 올라옴. 소스 `V6/app/controllers/IssueApp.java:927-931`(`Milestone.findById`만 함). | v1.6 결함 유지(a, c). (b)의 `{0}` 서술은 틀림(v2가 오히려 v1.6의 500을 400과 문구로 개선) | 장부에 올린다(c). 문서에서 (b)를 고친다 |
| issue-F12 | 재현됨 | `admin/hglfs`에 `plainuser`를 구성원으로 추가(`POST /admin/hglfs/members` 303). `plainuser`로 `/admin/hglfs/issues`: 톱니바퀴 링크 `issue/labelsform`이 보임. `GET /admin/hglfs/issue/labelsform`은 403. | 같음. v1.6 구성원 `other`가 `/tester/proj1/issues`: `labelsform` 링크(`ybtn-mini pull-right`)가 보임, `GET /tester/proj1/issue/labelsform` 403. | v1.6 결함 유지 | 장부에 올린다 |
| issue-F13 | 재현됨(일부는 소스 대조) | DOM 중복 id: 이슈 보기 `comments`, `timeline`, `tplAttachedFile` 각 2개, 목록 `two-column-mode-checkbox` 2개. `li.post-item` 속성에 `data-item`, `href`, `data-value`가 있음. 작성 제출 단추를 한 번 눌러 `POST .../issues/latest` 개수를 셈: 1건(중복 제출 없음, (j)의 추측은 사실이 아님). 나머지((a)(b)(e)(f)(g)(h)(i))는 템플릿과 스크립트 줄 확인. | 같음. v1.6 이슈 보기 중복 id `comments`, `timeline`, `tplAttachedFile` 외에 더 많음(`query`, `comment-N`, `upload-N` 등), 목록 `two-column-mode-checkbox` 중복. `li.post-item`에 같은 속성. 작성 제출 1건 POST. 소스: `V6/app/views/issue/partial_comment.scala.html:54-64`(5명 초과 안의 단수 가지), `V6/public/javascripts/service/yobi.issue.View.js:30,429`(타이머 죽은 코드), `V6/public/javascripts/common/yona.Tasklist.js:17`(`closest()` 인자 없음), `V6/app/views/issue/view.scala.html:269`(`.watcher-list`). (a)의 `or true`는 v1.6에서 자식 이슈 목록이 `partial_view_childIssueListOnly`로 상태와 무관하게 나오는 것과 같은 결과. | v1.6 결함 유지(죽은 코드와 중복 id) | 장부에 올린다 |
| issue-F15 | 재현됨 | 보기 화면 가중치 단추 title이 `Issue weight: Upvote`, `Issue weight: Down vote`(한국어 화면에서도 영어). 이슈 영역 템플릿이 쓰는 메시지 키 93개 중 `messages_ja_JP`에 없는 것 51개, `messages_ru_RU` 21개, `messages_uz_UZ` 21개(`messages.properties`, `messages_ko_KR.properties`는 0개). | 같음. v1.6도 같은 title(`Issue weight: Upvote`). `이미 부모 이슈입니다.` 하드코딩 `V6/app/views/issue/partial_select_subtask.scala.html:26`, `Set to default:` `V6/app/views/issue/my_list.scala.html:30`, `Draft saved` `V6/public/javascripts/service/yona.temporarySaveHandler.js:33`, `Reload page`와 알림 문구 `yona.detectChange.js:51-61`, `Update failed:` `yona.Usermenu.js:117`. v1.6 번역 파일도 `messages.ja-JP` 629줄로 본 파일(1,116줄)보다 짧다. `Due date: ` 하드코딩은 v2와 v1.6 모두 이 화면에서 `Due date: ` 문자열이 출력되지 않았다(내 이슈 화면 응답에 없음: 조건부). | v1.6 결함 유지 | 장부에 올린다 |
| issue-F16 | 재현됨 | 비로그인으로 `/admin/hglfs/issue/52`를 14초 열어 둠: `POST .../issue/52/detectChange` 401이 3회 반복됨. | 같음. v1.6 비로그인 `/tester/proj1/issue/2` 14초: `POST /-_-api/v1/owners/tester/projects/proj1/issues/2/detectChange` 401 3회. 소스 `V6/app/controllers/api/IssueApi.java:552-553`(401), `:580`(`issueUpdateDate`만 반환, `lastUpdateDate`는 읽지 않음). | v1.6 결함 유지 | 장부에 올린다 |
| issue-F17 | 재현 안 됨 | `plainuser`(비구성원)로 `admin/gitproj`, `admin/a-b`, `orgx/alpha-one`, `admin/한글` 목록: 빠른 검색 "할당된 이슈" 링크(`data-assignee-id=67`)가 있고 담당자 select에도 `value=67` 옵션이 있음(`IssueListService.assignees`가 `withViewer`로 조회자를 더함). "할당된 이슈"를 누르면 `assigneeId=67`이 요청에 실리고 select 값도 67. | 같음. v1.6 `third`(비구성원) `/tester/proj1/issues`: 링크 `data-assignee-id=35`, select에 `35:Third` 옵션 있음, 클릭하면 `assigneeId=35`로 요청됨. | 결함 아님(발견 사항의 서술이 틀림: 비구성원에게도 본인 옵션이 들어 있어 필터가 비지 않음) | 문서만 고친다 |
| issue-F18 | 재현됨 | `/admin/hglfs/issues?state=all`(2쪽 이상)에서 `ArrowRight`: `pageNum=2`로 이동하며 페이지에 심은 `window.__mark`가 사라짐(전체 이동). 같은 화면에서 쪽 링크 `pageNum=2`를 클릭: `__mark`가 유지됨(영역만 교체). | 같음. v1.6 `/tester/proj1/issues?state=all`(이슈 25건으로 만든 뒤): `ArrowRight` 전체 이동(`__mark` 사라짐), 링크 클릭은 `_pjax`로 영역만 교체(`__mark` 유지). 소스 `V6/public/javascripts/common/yobi.Pagination.js:210,236`. | v1.6 결함 유지 | 장부에 올린다 |
| issue-F19 | 재현됨 | `plainuser`(구성원)로 `/admin/hglfs/issueform`의 부모 이슈 후보에 `admin` 작성의 초안 이슈 `18:#18. 임시 저장 제목 시험 (고침)`이 들어 있음. 닫힌 이슈 `#52`도 들어 있음. | 같음. v1.6 `other`가 `/tester/proj1/issueform`: `tester`의 초안 `3:#3. 초안 이슈`와 닫힌 이슈가 후보에 있음(같은 사용자가 이슈 보기를 열면 403이 나는 초안임). 소스 `V6/app/models/Issue.java:549-556`(`project`와 `parent is null`만 조건). | v1.6 결함 유지 | 장부에 올린다 |
| issue-F20 | 재현됨 | 사용자 6명을 가입시켜 이슈 52와 댓글 142에 추천하게 함. 보기 화면 DOM에 `[id=copyEmailBtn]`이 2개(`#voters` 안, `#voters-142` 안). jQuery 클릭 처리기는 첫 번째에만 있음(첫째 1개, 둘째 0개). 첫 번째 단추 클릭: 클립보드에 `Voter1 <voter1@example.com>;...`가 복사되고 "이메일이 복사되었습니다." 대화상자. 두 번째(댓글 추천자 모달) 단추 클릭: 클립보드 변화 없음, 대화상자 없음. | **다름(v1.6은 둘 다 동작)**. v1.6에서 같은 구성(이슈 추천자 모달과 5명 초과 댓글 모달, DOM에 `copyEmailBtn` 2개): 단추 0 클릭 시 `Other <other@example.com>;Voter1 ...` 복사, 단추 1(`voters-2`) 클릭 시 `Voter1 <voter1@example.com>;Voter2 ...` 복사. 이유: v1.6은 `new ClipboardJS('#copyEmailBtn')`(`V6/app/views/issue/view.scala.html:598`)로 클립보드 라이브러리가 문서 위임 방식으로 모든 단추를 처리하지만 v2는 `$("#copyEmailBtn").on("click", ...)`(`src/main/resources/templates/issue/view.html:402`)로 첫 요소에만 처리기를 달았다. | **v2 신규 결함** | 고친다. 수정 방법: `partial-voter-list.html:29`의 `id="copyEmailBtn"`을 클래스(`copy-email-btn`)로 바꾸고 `view.html:402`를 `$(document).on("click", ".copy-email-btn", ...)` 위임으로 바꾼다(또는 `$("[id=copyEmailBtn]")`로 전부 잡는다). 모달마다 `data-copy-text`를 이미 가지므로 처리기 본문은 그대로 쓴다. |
| issue-F21 | 재현됨 | v2를 `--yona.translation-api=http://127.0.0.1:9/none`으로 다시 띄워 번역 단추(`#translate`)를 나오게 함. 비로그인으로 클릭: `POST /translation` 303 후 `GET /users/loginform?redirectUrl=/translation` 200, 화면은 이슈 페이지 그대로이고 아무 표시 없음. 두 번째 클릭은 요청이 더 가지 않음(`.one`으로 소모됨). | 같음. v1.6을 `-Dapplication.extras.translation.api=...`로 띄워 비로그인 클릭: `POST /-_-api/v1/translation` 303 후 로그인 폼 200, 화면 변화 없음, 두 번째 클릭은 요청 없음. 소스 `V6/app/views/issue/view.scala.html:231,559`(`.one('click')`). | v1.6 결함 유지 | 장부에 올린다 |
| issue-F22 | 재현됨 | 비로그인으로 `/admin/hglfs/issue/52`에 `이 댓글로 새 이슈` 링크(`/user/issues/new?commentId=142`)가 보임. 따라가면 200으로 첫 화면(`<title>Yona`)이 나옴. 방금 가입한 사용자(방문한 프로젝트 없음)도 같은 첫 화면 200. | 같음. v1.6 비로그인 `/tester/proj1/issue/1`에 같은 링크(`/user/issues/new?commentId=1`)가 있고 따라가면 200 첫 화면. 방금 가입한 사용자도 200 첫 화면. 소스 `V6/app/views/issue/partial_comment.scala.html:46-50`(조건 없는 링크), `V6/conf/routes:126`. | v1.6 결함 유지 | 장부에 올린다 |
| issue-F23 | 재현됨 | `POST /admin/hglfs/issues`(일괄 변경, JSON 아님)에 `Referer: http://evil.example/x`: 303 `Location: http://evil.example/x`. Referer 없음: 303 `/admin/hglfs/issues`. | 같음(Referer 값 그대로 리다이렉트). v1.6 `POST /tester/proj1/issues`에 같은 Referer: 303 `Location: http://evil.example/x`. Referer가 없으면 v1.6은 `Location: null`을 돌려줘 v2(목록으로 이동)와 다르나 이는 v2가 낫게 처리한 경우. | v1.6 결함 유지(열린 리다이렉트) | 장부에 올린다 |
| issue-F24 | 재현됨 | `plainuser/ownerproj` 설정에서 이슈, 마일스톤 메뉴를 끔(`POST /plainuser/ownerproj/setting` 303): 프로젝트 홈에 이슈 메뉴 링크가 사라졌지만 `/issues`, `/issueform`, `/issue/1`, `/milestones` 모두 200. 보기 화면 단축키 등록 코드는 `view.html:319-327`에서 `E`는 `canUpdate`, `N`은 `menuSetting.issue` 조건, 목록은 `N`을 조건 없이 등록(템플릿 확인). | 같음. v1.6 `tester/proj1`에서 이슈, 마일스톤 메뉴를 끄고(`POST /tester/proj1/setting`, 멀티파트 303): 홈 링크 사라짐, 같은 네 주소 모두 200. 소스 `V6/app/views/issue/view.scala.html:518-526`(보기: `N`은 `menuSetting.issue`, `E`는 `isAllowedUpdate`), `V6/app/views/issue/list.scala.html:32-34`(목록 `N` 무조건 등록). | v1.6 결함 유지 | 장부에 올린다 |
| issue-F25 | 재현됨 | `/admin/hglfs/issue/52` 응답에 인라인 `onclick="window.location='/admi...'"` 단추 2개(수정 이동). 응답 헤더에 `Content-Security-Policy` 없음. | 같음. 소스 `V6/app/views/issue/view.scala.html:237,423`(같은 `onclick="window.location=..."`). 발견 사항은 새 UI가 CSP를 쓸 때의 가정이다. | v1.6 결함 유지 | 조치 없음(새 UI에서 CSP를 도입할 때만 처리) |

### 마일스톤 영역(milestone.md)

| 항목 | 재현 | v2 재현 방법과 관찰값 | v1.6 실행 결과와 소스 근거 | 판정 | 권장 조치 |
|---|---|---|---|---|---|
| milestone-F1 | 재현됨 | `admin`이 다른 프로젝트 경로 `/plainuser/ownerproj/milestone/1`(마일스톤 1은 `admin/hglfs` 소속)로: 상세 200, `editform` 200, `close` 303, `open` 303(리다이렉트도 틀린 경로로 감). 삭제 `DELETE /plainuser/ownerproj/milestone/3/delete`(XHR)는 500이며 마일스톤은 그대로 남음(상세 200). | 같음. v1.6 `/tester/priv1/milestone/1`(`proj1` 소속): 상세 200, `editform` 200, `close` 303, `open` 303, 다른 경로 삭제 500이고 그대로 남음. 소스 `V6/conf/routes:254-259`(번호만 받는 경로). | v1.6 결함 유지 | 장부에 올린다 |
| milestone-F3 | 재현됨 | `POST /admin/hglfs/milestones`에 `contents=` 빈 값: 303 `/admin/hglfs/milestone/4`(만들어짐). | 같음. v1.6 `POST /tester/proj1/milestones`(`state=OPEN`) 빈 `contents`: 303 `/tester/proj1/milestone/33`(만들어짐). 소스 `V6/app/controllers/MilestoneApp.java:96-113`(제목, 상태 검증만). | v1.6 결함 유지 | 장부에 올린다 |
| milestone-F4 | 재현됨(관찰은 맞음) | `/admin/hglfs/newMilestoneForm`의 폼은 `enctype="multipart/form-data"`, `/milestone/1/editform`의 폼은 없음. 마감일 입력란 `autocomplete="off"`는 만들기에만 있음(true/false). | **다름**. v1.6는 만들기와 수정 폼 모두 `enctype`이 없고(`<form ... method="POST" id="milestone-form">`) `autocomplete="off"`는 만들기에만 있음. v2의 만들기 `enctype` 추가는 R-23(임시 첨부 옮기기) 수정이며(`git show fee572591`, `refactoring-backlog.md` R-23) 의도된 수정이다. 수정 폼은 v1.6과 같다(서버가 `attachments.moveAll`로 처리). | 결함 아님(의도된 수정과 v1.6과 같은 부분) | 조치 없음 |
| milestone-F5 | 재현됨 | `/admin/hglfs/milestone/1`(라벨이 붙은 이슈 연결 후): `[data-color]` 요소 0개, `a.issue-label[data-color]` 0개. 라벨은 `span.issue-label` 뿐. | 같음. v1.6 `/tester/proj1/milestone/1`: `[data-color]` 0개, 라벨은 `span.issue-label`. 소스 `V6/public/javascripts/service/yobi.milestone.View.js:45`(`a.issue-label[data-color]`), `V6/app/views/milestone`에 `data-color` 없음. | v1.6 결함 유지(죽은 코드) | 장부에 올린다 |
| milestone-F6 | 재현됨 | 이슈 메뉴 설정을 바꿔 마일스톤 메뉴를 켠 `admin/hglfs`의 `/milestones`에서 비로그인이 `n` 누름: `/users/loginform?redirectUrl=/admin/hglfs/newMilestoneForm`으로 이동. 로그인한 관리자는 `/admin/hglfs/newMilestoneForm`으로 감. (메뉴가 꺼진 프로젝트에서는 `N`이 등록되지 않음) | 같음. v1.6 `/tester/proj1/milestones`에서 비로그인 `n`: `/users/loginform?redirectUrl=/tester/proj1/newMilestoneForm`. `tester`는 새 마일스톤 폼으로 감. 소스: v1.6 목록 스크립트의 `N` 등록에 권한 조건 없음. | v1.6 결함 유지 | 장부에 올린다 |
| milestone-F7 | 재현됨 | 마일스톤 6의 상세를 연 채 다른 요청으로 먼저 삭제(204)하고 "닫기" 단추 클릭: `POST .../milestone/6/close` 404, 화면은 그대로이며 대화상자, 알림 없음. | 같음. v1.6 마일스톤 35에서 같은 방법: `POST .../milestone/35/close` 404, 화면 변화 없음. | v1.6 결함 유지 | 장부에 올린다 |
| milestone-F8 | 재현됨 | 제목 비움, 마감일 `2030-13-45`, 같은 제목 반복 모두 상태 200으로 폼 재표시. 같은 제목 `CaseBv2` 두 번째는 200 "already exists". 대소문자만 다른 `casebv2`는 303으로 만들어짐(제목 중복 검사가 대소문자 구분). | 같음. v1.6도 세 경우 200, 같은 제목 200 `already exists`, `caseBv16`/`CaseBv16` 대소문자만 다른 제목은 303으로 만들어짐. 소스 `V6/app/controllers/MilestoneApp.java:102-103`(`ok(create.render(...))`). | v1.6 결함 유지 | 장부에 올린다 |
| milestone-F9 | 재현됨 | `GET /admin/hglfs/milestones?state=all&orderBy=title&orderDir=asc`: 200, 제목 오름차순(`Casev2`, `T-empty-v2`, `달력 ...`), `desc`는 역순. 화면 정렬 링크는 `orderBy=dueDate`, `orderBy=completionRate`뿐. | 같음. v1.6 `orderBy=title`이 오름, 내림 정렬로 동작하고 화면 링크는 `dueDate`, `completionRate`만. | v1.6 결함 유지(화면에 없는 서버 경로) | 조치 없음 |

## 범위 점검

| 담당 목록 번호 | 결과 파일에 있음 | 판정 |
|---|---|---|
| issue-F2 | 있음 | v1.6 결함 유지 |
| issue-F3 | 있음 | v1.6 결함 유지 |
| issue-F7 | 있음 | v1.6 결함 유지 |
| issue-F8 | 있음 | v1.6 결함 유지 |
| issue-F9 | 있음 | v1.6 결함 유지 |
| issue-F10 | 있음 | 문서 문제 |
| issue-F11 | 있음 | v1.6 결함 유지 ((b) 서술 틀림) |
| issue-F12 | 있음 | v1.6 결함 유지 |
| issue-F13 | 있음 | v1.6 결함 유지 |
| issue-F15 | 있음 | v1.6 결함 유지 |
| issue-F16 | 있음 | v1.6 결함 유지 |
| issue-F17 | 있음 | 결함 아님 |
| issue-F18 | 있음 | v1.6 결함 유지 |
| issue-F19 | 있음 | v1.6 결함 유지 |
| issue-F20 | 있음 | v2 신규 결함 |
| issue-F21 | 있음 | v1.6 결함 유지 |
| issue-F22 | 있음 | v1.6 결함 유지 |
| issue-F23 | 있음 | v1.6 결함 유지 |
| issue-F24 | 있음 | v1.6 결함 유지 |
| issue-F25 | 있음 | v1.6 결함 유지 |
| milestone-F1 | 있음 | v1.6 결함 유지 |
| milestone-F3 | 있음 | v1.6 결함 유지 |
| milestone-F4 | 있음 | 결함 아님 |
| milestone-F5 | 있음 | v1.6 결함 유지 |
| milestone-F6 | 있음 | v1.6 결함 유지 |
| milestone-F7 | 있음 | v1.6 결함 유지 |
| milestone-F8 | 있음 | v1.6 결함 유지 |
| milestone-F9 | 있음 | v1.6 결함 유지 |

합계: 28건. v2 신규 결함 1(F20), v2 추가 기능의 결함 0, v1.6 결함 유지 24, 결함 아님 2(issue-F17, milestone-F4), 문서 문제 1(issue-F10), 재현 불가 0.

## 문서에 반영할 만한 정정

- issue-F11 (b): `error.maxLength`는 `{0}`가 그대로 보이지 않고 `Maximum length is 255`로 숫자가 채워져 나온다. 영어로만 나오는 점(한국어 번역 없음)은 사실이다.
- issue-F13 (j): 제출 단추를 한 번 누르면 POST는 1건이다(중복 제출 없음).
- issue-F17: 서술과 달리 조회자가 `withViewer`로 담당자 목록에 들어가므로 비구성원도 "할당된 이슈"가 비지 않는다.
- issue-F23: v1.6은 Referer가 없으면 `Location: null`을 돌려주고 v2는 목록으로 보낸다.
- milestone-F4: 만들기 폼의 `enctype`은 v1.6에는 없던 R-23 수정이다.
