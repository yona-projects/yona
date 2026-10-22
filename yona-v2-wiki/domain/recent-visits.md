---
title: 최근 방문 프로젝트 기록
type: domain
sources: [app/models/User.java:856-880, app/models/RecentProject.java:54-110]
updated: 2026-10-22
---

# 최근 방문 프로젝트 기록 (`RecentVisitService`)

- `visits(user, project)`: 익명이면 기록하지 않는다. 사용자가 **직전에 방문한 프로젝트와 같으면** 기록하지 않는다(프로젝트 홈을 새로고침할 때마다 행을 바꾸지 않으려는 규칙).
- `record(user, project)`: 같은 프로젝트의 이전 기록을 지우고 새로 남기고, 사용자당 30개를 넘으면 가장 오래된 것부터 지운다. 프로젝트 생성은 직전 방문과 관계없이 이 메서드로 바로 기록한다.

## v1.6과 달라진 점

- v1.6 `User`는 `lastVisitedProjectId`를 메모리의 사용자 캐시(`CacheStore.yonaUsers`, 2000명)에 들고 있었다. v2는 요청마다 사용자를 새로 읽으므로 같은 크기의 Caffeine 캐시(사용자 번호 -> 마지막 프로젝트 번호)에 둔다. 서버를 다시 시작하면 비워지는 점은 같다.
- v1.6은 기록을 비동기로 처리했다. v2는 요청 트랜잭션 안에서 처리한다(A-15).
- v1.6의 이슈와 게시글 방문(`visits(Issue)`, `visits(Posting)`)은 `lastVisitedProjectId`에 이슈 번호를 대입하는 결함이 있다. 해당 화면을 옮길 때 같게 재현하고 백로그에 올린다.

테스트 5건(`RecentVisitServiceTest`).
