---
title: 풀 리퀘스트 만들기
type: web
sources: [app/controllers/PullRequestApp.java:134-316, app/views/git/create.scala.html, app/views/git/partial_merge_result.scala.html, app/models/Project.java:850-859, app/models/PullRequest.java:1005-1020]
updated: 2026-10-23
---

# 풀 리퀘스트 만들기

| 주소 | 설명 |
|---|---|
| `GET /:user/:project/newPullRequestForm` | 폼. 로그인과 포크 만들기 권한이 필요하고 게스트는 403 |
| `GET /:user/:project/newPullRequest/mergeResult` | 폼의 브랜치를 고르면 Ajax로 가져오는 커밋 표 조각. `Cache-Control: no-cache, no-store` |
| `POST /:user/:project/pullRequests` | 만들기(멀티파트). 만든 뒤 풀 리퀘스트 화면으로 303 |

## 폼

- 보내는 곳 선택지는 내가 구성원인 연관 프로젝트, 받는 곳은 연관 프로젝트 전체이다. 연관 프로젝트는 현재 프로젝트, 그 포크들, 포크이고 원본이 코드와 풀 리퀘스트 메뉴를 켰으면 원본이다.
- 기본값: 보내는 곳은 현재 프로젝트, 받는 곳은 (포크이고 원본이 풀 리퀘스트를 받으면) 원본. 쿼리 `fromProjectId`, `toProjectId`, `fromBranch`, `toBranch`로 바꾼다. 보내는 브랜치 기본값은 가장 최근 커밋의 브랜치, 받는 브랜치 기본값은 **현재 프로젝트**의 기본 브랜치이다(R-42).
- 어느 쪽 저장소든 브랜치가 없으면 400 오류 화면(`error.pullRequest.empty.from.repository`, `...to.repository`).

## 병합 결과 조각

받는 브랜치 기본값은 목록의 첫 브랜치(폼과 다르다)이다. 커밋이 하나이면 첫 줄이 제목 제안, 나머지가 본문 제안이다. 둘 이상이면 제목은 비우고 각 첫 줄을 본문으로 나열한다. `data-conflict`는 커밋이 있을 때만 붙는다.

## 만들기

같은 두 프로젝트의 같은 두 브랜치 사이에 열린 요청이 있으면 새로 만들지 않고 그 요청으로 303. 아니면 번호를 받아(받는 프로젝트의 최대 번호 + 1) 저장하고 제목과 본문의 `#번호` 이슈에 언급 이벤트를 남기며, 보내는 브랜치의 최근 푸시 기록을 지우고, 첨부를 옮기고, 새 풀 리퀘스트 알림과 타임라인 이벤트를 쌓은 뒤 병합 검사를 시작한다. 빈 값 검사는 A-75를 본다.
