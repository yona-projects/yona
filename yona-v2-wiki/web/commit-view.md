---
title: 커밋 보기와 두 커밋 비교
type: web
sources: [app/controllers/CodeHistoryApp.java:107-157, app/controllers/CompareApp.java, app/views/code/diff.scala.html, app/views/code/compare.scala.html, app/views/partial_comment_thread.scala.html, app/models/Commit.java, app/controllers/ProjectApp.java:500-538]
updated: 2026-10-23
---

# 커밋 보기와 두 커밋 비교

## 주소

| 주소 | 설명 |
|---|---|
| `GET /:user/:project/commit/:id` | 커밋 보기. 없는 커밋은 `error.notfound.commit` 404 |
| `GET /:user/:project/compare/:revA..:revB` | 두 커밋 비교. 어느 쪽이든 없으면 404, 차이가 없으면 `code.noChanges` 안내 |
| `GET /:user/:project/mentionListAtCommitDiff` | 코드 리뷰 편집기의 멘션 후보. 커밋이 없으면 404 (A-72) |
| `/watch`, `/unwatch` | 리소스 종류 `commit`, 번호 `프로젝트번호:커밋해시`를 받는다 |

## 구성

- `CommitViewController`: 커밋 정보, 첫 부모와 비교한 차이, 스레드, 리뷰 카드, 구독 상태를 모델로 만든다.
- `DiffHtmlRenderer`: 파일별 차이를 v1.6처럼 HTML 문자열로 그리고 줄 사이에 스레드를 끼운다.
- `ThreadHtmlRenderer`, `ThreadViewAssembler`, `code/partial-comment-thread.html`: 스레드 하나를 템플릿 엔진으로 그린다. 코드 범위 스레드는 `thread`, 파일 단위 스레드는 `nonRanged` 조각이다.
- `ReviewThreadService`: 커밋의 스레드 조회와 개수 계산(이력의 댓글 수, 파일 보기의 댓글 수 R-41).
- `NotificationReceivers.commitWatchers`: 커밋 작성자, 이 커밋의 리뷰 댓글 작성자, 프로젝트 구독자와 커밋 구독자에서 구독 해제자를 뺀 집합(v1.6 `Commit.getWatchers`).
- `ResourceLookup`: 종류 `COMMIT`을 지원한다. 커밋이 저장소에 있는지는 보지 않는다.

## 이번 범위 밖

스레드 댓글 등록, 스레드 열기와 닫기 요청 처리는 5d에서 이식한다. 지금은 화면 마크업과 단추 주소만 v1.6과 같게 나온다.
