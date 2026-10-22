---
title: 풀 리퀘스트 보기
type: web
sources: [app/controllers/PullRequestApp.java:369-420, app/controllers/ReviewApp.java, app/views/git/view.scala.html, viewChanges.scala.html, partial_info.scala.html, partial_state.scala.html, partial_branch.scala.html, partial_pull_request_event.scala.html, partial_reviewlist.scala.html, app/models/PullRequest.java:1005-1184, app/models/CodeCommentThread.java, app/controllers/ProjectApp.java:541-600]
updated: 2026-10-23
---

# 풀 리퀘스트 보기

| 주소 | 설명 |
|---|---|
| `GET /:user/:project/pullRequest/:id` | 보기. 읽기 권한(풀 리퀘스트) |
| `GET .../:id/state` | 상태 조각. Ajax(`X-Requested-With`, PJAX 아님)이면 JSON(`id, isOpen, isClosed, isMerged, isMerging, isConflict, canDeleteBranch, canRestoreBranch, html`), 아니면 HTML 조각 |
| `GET .../:id/changes`, `.../changes/:commitId` | 변경 내용(전체 또는 커밋 하나) |
| `POST .../:id/review`, `.../unreview` | 리뷰 표시와 취소. 로그인과 받아들일 권한 |
| `GET /:user/:project/mentionListAtPullRequest` | 편집기 멘션 후보. 풀 리퀘스트가 없으면 404 |

## 보기

- 머리: 번호와 제목, 상태 뱃지(충돌이면 `Conflict`), 시각. 프로젝트가 리뷰어 수를 쓰고 받아들일 권한이 있으면 리뷰어 수와 아바타, 리뷰·리뷰 취소 단추. 받아들일 권한이 있으면 받아들이기 단추(조건을 못 채우면 이유를 툴팁으로 담은 막힌 단추).
- 상태 조각(`#state`): 병합 중이면 경고, 병합되었으면 받은 사람(보낸 사람에게는 보낸 브랜치 삭제 또는 복원 단추), 닫힘이면 없음, 충돌이 없으면 "안전", 충돌이면 오류(보낸 사람에게는 해결 방법 열 단계).
- 타임라인: 새 풀 리퀘스트를 뺀 이벤트(리뷰 상태, 상태 변경, 병합 상태, 새 커밋 + 커밋 목록 + "추가 변경" 비교 버튼).
- 하단: 구독 단추(구독 권한), 수정(수정 권한), 닫기(열림이고 닫기 권한), 다시 열기(닫힘이고 열기 권한).

## 변경 화면

병합 기준 커밋 사이의 차이(충돌했거나 기준이 없으면 보이지 않음)를 공용 diff 조각으로 그린다. 코드 범위 스레드는 줄 사이에, 파일 단위 스레드는 아래에 보인다. 오른쪽 리뷰 카드는 열림, 닫힘 탭으로 나뉘고 어긋난(outdated) 스레드는 표시한다. 커밋 선택 상자는 현재 커밋만 담는다. 스레드 댓글 등록과 상태 변경은 5d에서 옮긴다.

어긋남 판단은 `PullRequestChangesService.isOutdated`이다. 위치 정보가 없거나 병합 기준이 없으면 어긋나지 않았고, 커밋 하나에 단 코멘트는 그 커밋이 요청에 없으면 어긋났다. 그 밖에는 이전, 현재 기준 커밋과 스레드의 커밋에서 파일 내용(blob)이 달라졌는지를 본다.

차이는 A-78, 결함 재현은 R-43을 본다.
