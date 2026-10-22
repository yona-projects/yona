---
title: 풀 리퀘스트 동작
type: web
sources: [app/controllers/PullRequestApp.java:384-560, app/models/PullRequest.java:320-355, app/models/PullRequest.java:533-565, app/models/PullRequest.java:667-675, app/actors/RelatedPullRequestMergingActor.java, app/views/git/edit.scala.html]
updated: 2026-10-23
---

# 풀 리퀘스트 동작

모두 로그인해야 하고 끝나면 풀 리퀘스트 화면으로 303 이동한다.

| 주소 | 권한 | 동작 |
|---|---|---|
| `POST .../:id/accept` | 받아들이기 | 병합 |
| `POST .../:id/close` | 닫기 | 닫힘으로 |
| `POST .../:id/open` | 다시 열기 | 열림으로. 병합됐거나 이미 열려 있으면 400 |
| `GET .../:id/editform`, `POST .../:id/edit` | 수정 | 제목, 본문 수정 |
| `DELETE .../:id/deletefrombranch` | 수정 | 병합된 요청의 보낸 브랜치 삭제 |
| `POST .../:id/restorefrombranch` | 수정 | 지운 브랜치 복원 |

## 받아들이기

리뷰어 수를 쓰는 프로젝트에서 리뷰어가 모자라면 400(A-76). 아니면 병합 중 표시를 켜고 `PullRequestMerger.merge`로 받는 브랜치를 보낸 사람(받아들인 사람) 이름의 병합 커밋으로 갱신한다. 충돌하면 아무것도 바꾸지 않는다. 병합하면 병합 기준 커밋과 `MERGED` 상태, 받은 시각과 받은 사람을 저장하고 상태 변경 알림(`open → merged`)과 타임라인 이벤트를 남기며, 같은 프로젝트의 같은 브랜치와 관련된 다른 열린 요청의 병합 검사를 다시 시작한다.

## 닫기와 다시 열기

상태, 받은 시각, 받은 사람을 바꾸고 상태 변경 알림과 타임라인 이벤트(이전 → 새 상태)를 남긴다. 다시 열면 병합 검사를 다시 시작한다.

## 수정

수정 폼은 브랜치를 고정(숨은 값)하고 제목과 본문만 고치게 한다. 저장하면 이슈 언급 이벤트를 지우고 새 제목과 본문으로 다시 남기며 첨부를 옮긴다. 브랜치가 바뀌어 다른 열린 요청과 겹치면 경고(`pullRequest.duplicated`)와 함께 수정 폼으로 되돌린다(주소 결함은 R-43).

## 보낸 브랜치 삭제와 복원

보내는 브랜치를 받는 저장소가 이미 가지고 있고 보내는 저장소의 현재 브랜치가 아닐 때만 삭제할 수 있다. 삭제하면 마지막 커밋 번호를 요청의 `lastCommitId`에 남기고, 복원은 그 커밋에서 같은 이름의 브랜치를 만든다.
