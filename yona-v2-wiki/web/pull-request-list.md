---
title: 풀 리퀘스트 목록
type: web
sources: [app/controllers/PullRequestApp.java:326-367, app/controllers/PullRequestApp.java:694-762, app/models/PullRequest.java:752-830, app/views/git/list.scala.html, app/views/git/partial_search.scala.html, app/views/git/partial_list.scala.html, app/views/git/partial_recently_pushed_branches.scala.html, app/controllers/ProjectApp.java:1325-1334]
updated: 2026-10-23
---

# 풀 리퀘스트 목록

## 주소

| 주소 | 분류 | 프로젝트 조건 | 상태 | 정렬 |
|---|---|---|---|---|
| `GET /:user/:project/pullRequests` | 열림(`open`) | 받는 프로젝트 | 열림 | `number` 내림차순 |
| `GET /:user/:project/closedPullRequests` | 닫힘(`closed`) | 받는 프로젝트 | 닫힘, 병합 | `received` 내림차순 |
| `GET /:user/:project/sentPullRequests` | 보냄(`sent`) | 보내는 프로젝트 | 전체 | `created` 내림차순 |
| `DELETE /:user/:project/pushedBranch/:id/delete` | | | | 최근 푸시 기록 닫기(삭제 권한, 로그인 필요) |

읽기 권한이 필요하다. 코드 접근을 구성원으로 제한한 프로젝트는 비구성원에게 403이다. `X-PJAX: true`이면 `div[pjax-container]` 조각(`pullrequest/partial-search :: search`)만 돌려주고 `Cache-Control: no-cache, no-store`를 붙인다.

## 검색

- `filter`: 제목, 본문, 병합 커밋(`mergedCommitIdTo`), 이 요청의 리뷰 댓글 내용, 현재(`CURRENT`) 커밋의 메시지와 해시에서 대소문자 구별 없이 찾는다. `%`, `_`는 글자 그대로 찾는다.
- `contributorId`: 보낸 사람. 보냄 목록에서는 입력란이 없다.
- `pageNum`: 한 쪽 15개. 값이 없는 정렬 기준(`received` 등)은 뒤로 보낸다(MariaDB 동작에 맞춤). 같은 값이면 `id` 큰 것이 앞이다(A-74).

## 화면

- 탭 숫자: 열림, 닫힘 개수. 포크이면 "보냄" 탭이 더 나오고 `합쳐진 수 / 보낸 수`를 보여 준다.
- 한 줄: 보낸 사람 아바타(기본 아바타이면 Gravatar), 번호, 머리말, 제목(충돌이면 `conflict`), 시각, 리뷰 스레드 진행(닫힌 수 / 전체 수), 리뷰어 수(프로젝트가 리뷰어 수를 쓰고 리뷰어가 있을 때, 내가 리뷰했으면 `over`), 대상 브랜치(기본 브랜치이면 `to-default-branch`), 받은 사람, 상태(충돌이면 `Conflict`).
- 보낸 사람 선택지: 전체, 내가 보냈으면 "내가 보낸 것", 이 프로젝트로 보낸 적이 있는 사람 이름순.
- 최근 푸시 브랜치(최근 한 시간): 포크이면 그 프로젝트의 것, 아니면 내가 포크한 프로젝트들의 것(풀 리퀘스트를 만들 권한이 있을 때). 링크는 원본의 기본 브랜치를 받는 브랜치로 넣는다.
