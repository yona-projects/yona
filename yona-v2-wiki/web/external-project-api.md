---
title: 외부 프로젝트, 게시판 REST API
type: web
sources: [app/controllers/api/ProjectApi.java, app/controllers/api/BoardApi.java, app/controllers/api/MilestoneApi.java, app/controllers/api/WatcherApi.java, app/controllers/api/GlobalApi.java]
updated: 2026-10-23
---

# 외부 프로젝트, 게시판 REST API

외부 도구(마이그레이션 화면, 자동화)가 쓰는 API이다. 구현: `ExternalProjectApiController`, `ExternalBoardApiController`(웹), `ExternalProjectService`, `ExternalPostingService`(도메인). 이슈 API는 [external-issue-api.md](external-issue-api.md), 사용자 API는 `ExternalUserApiController`이다.

| 요청 | 인증과 권한 | 동작 |
|---|---|---|
| `GET /-_-api/v1/hello` | 없음 | `{"message": "I'm alive!", "ok": true}` |
| `POST …/owners/:owner/projects` | 사이트 관리자(세션 또는 토큰), 소유자가 조직이면 조직 관리자 | `{"projectName", "projectDescription", "projectVcs", "projectScope", "members": [{"email", "role"}]}`로 프로젝트를 만든다. 저장소 생성, 메뉴 전부 켬, 구성원 역할(`member`, `manager`) 지정. 같은 이름이 있으면 `400`과 `{"status": 409, "reason": "Conflict", "project": …}`. 범위를 정하지 않으면 비공개. 사이트 관리자(`SITEMANAGER`) 역할 행만 두고 만든 사람을 관리자로 넣지 않는다 |
| `POST …/projects/:name/labels` | 라벨 만들기 권한 | `{"labels": [{"labelName", "labelColor", "category"}]}`. 분류가 없으면 만든다. 항목마다 `{"status": 201, …}` 또는 이미 있으면 `{"status": 409, "reason": "Conflict", "message", "user": 입력}`. 모두 `201` |
| `POST …/projects/:name/milestones` | 마일스톤 만들기 권한 | `{"milestones": [{"title", "description", "due_on", "state"}]}`. 같은 제목은 `{"milestone": 입력, "message"}`. `due_on`(ISO)은 그날의 마지막 초로 맞추고 응답에서는 `yyyy-MM-dd'T'HH:mm:ss'Z'`(서버 시간대)로 돌려준다. `state`는 `closed`(대소문자 무시)만 닫힘 |
| `GET …/projects/:name/exports` | 프로젝트 삭제 권한 | 프로젝트, 구성원, 라벨, 이슈(`IssueJson`), 글, 마일스톤을 한 JSON으로 내보낸다. `authors`는 이슈와 글을 쓴 사용자이다(풀 리퀘스트 기여자는 5단계에서 더한다) |
| `GET …/projects/:name/posts/:number/watchers?type=issues\|posts` | 프로젝트 읽기 권한 | 이슈나 글의 구독자를 최대 100명까지 `{"name", "url"}`로, 전체 수와 함께 준다. `type`이 없거나 다르면 빈 `200` |
| `POST …/projects/:name/posts` | 글 만들기 권한 | `{"posts": [{"title", "body", "author", "createdAt", "updatedAt", "number", "temporaryUploadFiles"}]}`. 항목마다 `{"status": 201, "location"}`을 모아 `201`. 알림은 보내지 않는다 |
| `POST …/projects/:name/posts/:number/comments` | 글에 댓글 만들기 권한 | `{"body", "createdAt", "author", "temporaryUploadFiles"}`. `{"status": 201, "location"}`을 `201`로 답하고 글의 댓글 수를 맞춘다 |

`author`는 이메일로 찾고 없으면 사용자를 만든다(`ApiUsers.create`). 없으면 요청한 사용자이다. 번호를 지정해 만들면 프로젝트의 마지막 번호를 올리지 않고 제목 머리말 개수도 건드리지 않는다.

v1.6과 달라진 점은 [decisions.md] A-64(필수 값 `400`), A-65(구독자 목록 권한과 `404`)이다. 같게 재현한 결함은 [refactoring-backlog.md] R-36(프로젝트 만든 날짜 덮어쓰기), R-37(라벨의 `isExclusive` 무시)이다.
