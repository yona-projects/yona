---
title: 외부 이슈 REST API
type: web
sources: [app/controllers/api/IssueApi.java, app/controllers/api/ProjectApi.java]
updated: 2026-10-23
---

# 외부 이슈 REST API

외부 도구(마이그레이션 화면, 자동화)가 쓰는 이슈 API이다. 구현: `ExternalIssueApiController`(웹), `ExternalIssueService`(도메인). 응답 JSON 형식은 화면용 이슈 API와 같은 `IssueJson`을 쓴다.

| 요청 | 인증 | 동작 |
|---|---|---|
| `GET /-_-api/v1/owners/:owner/projects/:name/issues/:number` | 토큰(권한 확인 없음) | `{"result": 이슈 + events}`. 이벤트가 없으면 `events` 키를 뺀다. 이벤트 항목: `id`, `createdDate`(ISO), `eventType`, `eventDescription`(번역 문구), `oldValue`, `newValue`, `actor`(`name`, `loginId`, `englishName`) |
| `POST …/issues` | 세션 또는 토큰, 이슈 만들기 권한 | `{"issues": [...], "sendNotification"}` → 항목마다 `{"status":201,"location"}`을 모아 `201`. `number`를 주면 그 번호로 만들고, `author`는 이메일로 찾거나 사용자를 만든다. 사용자를 만들 수 없으면(허용하지 않는 메일 도메인, 이미 있는 로그인 아이디) v1.6 `NullUser`처럼 익명 사용자(번호 -1, 로그인 아이디 빈 문자열)가 작성자가 되고 요청은 성공한다(2026-10-26 정정, 이전에는 `400`). 담당자는 이메일, 라벨은 `(labelName, category)`로 찾는다. 알림은 `sendNotification`일 때만 |
| `PUT …/issues/:number` | 토큰(권한 확인 없음) | 제목, 본문, 마일스톤 제목, 상태, 담당자(이메일). 상태와 담당자가 바뀌면 이슈 이벤트를 남긴다(합치지 않음) |
| `PATCH …/issues/:number` | 토큰(권한 확인 없음) | 상태만. `OPEN`(대소문자 무시)이 아니면 닫힘 |
| `POST …/issues/:number/comments` | 토큰 헤더가 있으면 토큰(`{"comment"}`, 이슈 결과를 `201`), 없으면 세션(`{"body","createdAt","author","temporaryUploadFiles"}`, `{"status","location"}`를 `201`) | 댓글을 달고 이슈의 댓글 수를 맞춘다. 알림과 웹훅은 보내지 않는다 |
| `POST …/issuelabel/:number` | 세션 또는 토큰 + 수정 권한 | 본문 `["라벨번호", …]`로 라벨을 바꾼다. 결과 `{"id": 소유자, "labels": 개수}` |
| `POST …/issues/imports?postNumber=N` | 세션 또는 토큰 + (글 삭제, 이슈 만들기) 권한 | 게시글을 이슈로 옮긴다. 제목, 본문, 이력, 시각, 작성자를 그대로 두고 새 번호를 받으며, 댓글(대댓글 관계 포함)과 글, 댓글의 첨부를 옮기고 글을 지운다. 결과 `{"number"}` |

이슈 수정, 상태 변경, 라벨 변경은 v1.6 `issue.save()`처럼 댓글 수를 다시 세고 제목 머리말 개수를 한 번 더 늘리며 멘션 기록을 맞춘다([refactoring-backlog.md] R-29와 같은 동작, 2026-10-26 정정).

토큰으로만 인증하는 경로는 v1.6처럼 프로젝트 권한을 확인하지 않는다([decisions.md] D-20). 라벨 바꾸기와 글 가져오기는 v1.6에 인증이 없어 프로젝트 권한을 확인한다. 필수 값이 없으면 `400`이다(A-64). `eventDescription`은 v1.6 `EventType.getDescr()`처럼 요청 언어로 번역한 문구이다(`EventType.descrKey`는 메시지 키).
