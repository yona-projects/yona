---
title: 웹훅 설정
type: web
sources: [app/controllers/ProjectApp.java:1262-1325, app/views/project/webhooks.scala.html, app/views/project/partial_webhooks_list.scala.html, app/models/Webhook.java:40-150]
updated: 2026-10-23
---

# 웹훅 설정

| 주소 | 설명 |
|---|---|
| `GET /:user/:project/webhooks` | 목록과 등록 폼(웹훅 만들기 권한이 있을 때만 폼). 프로젝트 수정 권한 |
| `POST /:user/:project/webhooks` | 등록. 목록으로 303. 입력이 올바르지 않으면 오류를 담은 400 |
| `DELETE /:user/:project/webhooks/:id` | 삭제. 없으면 404 (A-82) |

등록 값: `payloadUrl`(필수, 2000자 이하), `secret`(250자 이하, 없으면 빈 문자열), `webhookType`(`SIMPLE`, `DETAIL_SLACK`, `DETAIL_HANGOUT_CHAT`, `JSON`, 없으면 `SIMPLE`), `gitPush`(체크박스, 없으면 `false`).
목록에는 주소, 토큰(없으면 `NONE`), 형식, 푸시 포함 여부(읽기 전용)가 나온다. `JSON` 형식은 화면의 스크립트가 푸시 포함을 켜고 바꾸지 못하게 한다.
