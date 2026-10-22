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
| `GET /-_-api/v1/owners/:owner/projects/:name/issues/:number` | 토큰 | `{"result": 이슈 + events}`. 이벤트가 없으면 `events` 키를 뺀다. 이벤트 항목: `id`, `createdDate`(ISO), `eventType`, `eventDescription`(번역 문구), `oldValue`, `newValue`, `actor`(`name`, `loginId`, `englishName`) |
| `POST …/issues` | 세션 또는 토큰, 이슈 만들기 권한 | `{"issues": [...], "sendNotification"}` → 항목마다 `{"status":201,"location"}`을 모아 `201`. `number`를 주면 그 번호로 만들고, `author`는 이메일로 찾거나 사용자를 만든다. 담당자는 이메일, 라벨은 `(labelName, category)`로 찾는다. 알림은 `sendNotification`일 때만 |
| `PUT …/issues/:number` | 토큰 + 수정 권한 | 제목, 본문, 마일스톤 제목, 상태, 담당자(이메일). 상태와 담당자가 바뀌면 이슈 이벤트를 남긴다(합치지 않음) |
| `PATCH …/issues/:number` | 토큰 + 수정 권한 | 상태만. `OPEN`(대소문자 무시)이 아니면 닫힘 |
| `POST …/issues/:number/comments` | 토큰 헤더가 있으면 토큰(`{"comment"}`, 이슈 결과를 `201`), 없으면 세션(`{"body","createdAt","author","temporaryUploadFiles"}`, `{"status","location"}`를 `201`) | 댓글을 달고 이슈의 댓글 수를 맞춘다. 알림과 웹훅은 보내지 않는다 |
| `POST …/issuelabel/:number` | 세션 또는 토큰 + 수정 권한 | 본문 `["라벨번호", …]`로 라벨을 바꾼다. 결과 `{"id": 소유자, "labels": 개수}` |
| `POST …/issues/imports?postNumber=N` | 세션 또는 토큰 + (글 삭제, 이슈 만들기) 권한 | 게시글을 이슈로 옮긴다. 제목, 본문, 이력, 시각, 작성자를 그대로 두고 새 번호를 받으며, 댓글(대댓글 관계 포함)과 글, 댓글의 첨부를 옮기고 글을 지운다. 결과 `{"number"}` |

v1.6과 달라진 점은 [decisions.md] A-63(권한 확인), A-64(필수 값 `400`)이다. `eventDescription`은 v1.6 `EventType.getDescr()`처럼 요청 언어로 번역한 문구이다(`EventType.descrKey`는 메시지 키).
