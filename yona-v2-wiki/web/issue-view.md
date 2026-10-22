---
title: 이슈 보기, 다음 상태, 삭제
type: web
sources: [app/controllers/IssueApp.java:248-309, 680-700, 938-958, app/views/issue/view.scala.html, app/views/issue/partial_*.scala.html, app/views/common/{commentForm,commentUpdateForm,childComments,child_commentForm,commentDeleteModal,partial_history,attachmentFile}.scala.html, app/models/Issue.java, app/models/RecentIssue.java]
updated: 2026-10-23
---

# 이슈 보기, 다음 상태, 삭제

구현은 `IssueViewController`, `IssueViewAssembler`(화면 모델), `IssueEventHtml`(타임라인 이벤트), `IssueService`(`toNextState`, `canBeDeleted`, `delete`), `RecentIssueService`이고 화면은 `issue/{view,partial-comments,partial-comment,partial-index-comments,partial-voters,partial-voter-list,partial-show-selected-label,partial-subtasks}.html`, `common/{comment-form,comment-update-form,child-comments,child-comment-form,comment-delete-modal,partial-history,attachment-file,tasklist-bar}.html`이다. 테스트는 `IssueViewTest` 38건, `IssueActionsTest` 8건, `IssueServiceTest`의 상태, 삭제 6건이다.

## 보기 (`GET /:user/:project/issue/:number`)

프로젝트가 있어야 한다(`NullProjectCheck`). 응답에는 `Vary: Accept`가 붙는다.

| 조건 | 응답 |
|---|---|
| 이슈가 없다 | JSON을 원하면 `200 {"title": 번호, "body": "Issue does not exist"}`, 아니면 `404` 프로젝트 오류 화면 |
| 초안인데 쓴 사람이 아니다 | `403`(오류 화면의 내용은 "찾을 수 없음") |
| 읽을 수 없다 | `403` |
| JSON을 원한다 | `{id, title, state, body(200자로 줄임), createdDate, link}` |
| 그 밖 | 화면. 로그인한 사용자의 최근 방문 프로젝트와 최근 본 이슈에 기록한다 |

문서 제목은 `이슈 제목 |:| 본문 앞 200자`(소셜 미리보기에 쓰이는 설명)이다.

### 화면의 구성

- **머리**: 만든 시각, 상태 배지, 번호(초안은 `#Draft`), 하위 이슈 표시(`subtask`), 제목, 즐겨찾기 별(`starred`), 초안 설명.
- **작성자**: 아바타, 표시 이름, 아이디. 변경 이력이 있으면 익명에게는 로그인 링크, 로그인한 사용자에게는 대화상자(정화한 HTML)와 마지막 수정자, 시각.
- **본문**: 마크다운 렌더링(수정 권한이 있으면 체크리스트를 고칠 수 있게 `data-allowed-update`), 숨은 폼(체크리스트 갱신 API용 본문 원문), 첨부 목록(JSON), 비어 있으면 빈 칸.
- **동작 줄**: 구독 단추(구독 권한이 있을 때, 구독 중이면 `ybtn-watching`), 공유 단추(수정 권한), 가중치 올림, 내림, 추천(댓글을 쓸 수 있으면 링크, 아니면 로그인 안내)과 추천자 아바타, 번역 단추(번역 API가 설정된 경우만), 수정, 삭제 아이콘(삭제할 수 없는 이슈는 설명이 붙은 비활성 단추, 수정 권한이 없으면 "원본 보기" 아이콘).
- **공유자**: 수정 권한이 있으면 입력 상자(`issueSharer`, 로그인 ID를 쉼표로 이은 값), 없으면 이름 목록.
- **하위 이슈**: 부모와 열림, 닫힘 하위 이슈 목록과 진행 막대. 공유받은 하위 이슈에서는 보이지 않는다.
- **댓글(초안은 제외)**: 시간 순서로 댓글과 이벤트를 섞은 타임라인, 댓글 등록 폼(권한이 없으면 비활성 상자). 한 줄 댓글은 부모 댓글 아래에 보이고 타임라인에는 따로 나오지 않는다. 각 댓글에 새 이슈로 만들기, 추천자(5명을 넘으면 요약과 대화상자), 추천 단추, 수정(`common/comment-update-form`), 삭제 단추가 있다.
- **오른쪽 창**: 새 하위 이슈, 담당자(수정 권한이 있으면 선택, 없으면 이름), 마일스톤(없으면 새 마일스톤 링크, 수정 권한이 있으면 선택, 없으면 링크), 마감일과 상태(기한 지남, 남은 기간), 라벨(수정 권한이 있으면 선택, 없으면 목록 링크), 수정, 삭제 아이콘, 댓글 색인(앞 60자, 멘션 표시). 이 폼(`issueUpdateForm`)은 일괄 변경(`POST issues`)으로 전송한다.
- **숨은 값**: `issueBodyChecksum`(본문 SHA-1), `numOfComments`, `issueUpdateDate`(변경 감지용).

### 타임라인 이벤트

`IssueEventHtml`이 `li.event`를 만든다. 상태(`issue.event.open|closed`), 담당자(자신에게 할당, 할당, 해제), 마일스톤, 커밋 참조, 이동, 풀 리퀘스트 참조, 공유자 추가, 삭제(이어지면 제목 생략), 라벨 추가, 삭제(색 상자), 그 밖(`값 by 사용자`)을 그린다. 본문 변경 이벤트는 그리지 않는다. 사용자가 쓴 값은 모두 이스케이프한다.

### 추천자 대화상자 (v1.6 동작 재현)

v1.6은 추천자 아바타를 그리면서 같은 집합에서 현재 사용자를 실제로 빼 버려, 그 뒤에 그려지는 추천자 대화상자에는 **현재 사용자가 빠지고**, 추천자가 나 하나뿐이면 대화상자가 없다(R-25).

## 타임라인 조각 (`GET issue/:number/timeline`)

읽기 권한이 필요하다. 댓글과 이벤트 목록(`ul.comments`)만 돌려준다. 화면의 자바스크립트가 댓글을 등록한 뒤 목록을 다시 읽는다.

## 다음 상태 (`GET issue/:number/nextstate`)

이슈 수정 권한(구성원, 작성자)이 필요하다. 열림 ↔ 닫힘으로 바꾸고(초안은 닫힘), 수정 시각을 갱신하며, 타임라인 이벤트를 남기고(30초 안에 연달아 바꾸면 합친다), `IssueChangedActivity`를 발행한 뒤 `303`으로 보기 화면에 보낸다. 하위 이슈를 다시 열면 닫힌 부모도 다시 연다.

## 삭제 (`DELETE issue/:number/delete`)

1. 이슈 작성자가 아닌 사람의 댓글이 있으면 `400`.
2. 삭제 권한이 없으면 `403`. (v1.6은 권한을 확인하기 전에 알림 이벤트를 만들었지만 v2는 확인 뒤에 만든다.)
3. 댓글(첨부, 알림 이벤트 포함), 타임라인 이벤트, 제목 머리말 개수, 이슈의 첨부와 알림 이벤트를 지우고 하위 이슈의 부모 연결은 끊는다. 초안이 아니면 `IssueDeletedActivity`를 발행한다.
4. Ajax(`X-Requested-With`)이면 `204`와 `Location: /소유자/프로젝트/issues?state=open`, 아니면 `303`으로 같은 주소.

v1.6처럼 멘션 기록은 지우지 않는다(R-24).

## 대기

구독 단추, 추천, 공유자, 가중치, 담당자와 마일스톤, 라벨, 마감일 변경(일괄 변경), 댓글 등록, 수정, 삭제, 한 줄 댓글, 체크리스트 갱신, 변경 감지, 알림 수신자 미리 보기, 번역 요청의 서버 쪽은 3c-3에서 옮긴다. 화면은 이미 해당 주소를 부른다.
