---
title: 웹훅 전송
type: subsystem
sources: [app/models/Webhook.java, app/models/WebhookThread.java, app/models/NotificationEvent.java:601-681]
updated: 2026-10-23
---

# 웹훅 전송

구성: `WebhookListener`(활동을 받아 보낼 이벤트를 고름) → `WebhookService`(프로젝트의 웹훅마다 형식에 맞는 본문 선택) → `WebhookPayloads`(본문 JSON) → `WebhookSender`(POST).

## 보내는 이벤트

| 이벤트 | 받는 웹훅 |
|---|---|
| 새 이슈, 이슈 상태·담당자·본문·마일스톤 변경, 이슈 삭제, 이슈 이동 | `JSON`을 뺀 모두 |
| 새 글 | 〃 |
| 새 댓글, 댓글 수정(이슈, 글) | 〃 |
| 새 풀 리퀘스트, 병합됨, 리뷰 상태 변경, 풀 리퀘스트 새 리뷰 댓글 | 〃 (닫기, 다시 열기, 새 커밋은 보내지 않음, R-45) |
| 푸시 | `JSON`은 커밋 내용, 나머지는 "푸시 포함"을 켠 경우 한 줄 |

## 형식

- `SIMPLE`: `{"text": "[프로젝트] 보낸이 이벤트 설명 <주소|#번호: 제목>"}`.
- `DETAIL_SLACK`: 위에 `attachments`(본문, `fields`, 이벤트별 `color`)를 더한다. 글 속 `>`는 `&gt;`로 바꾼다. 색은 `yona.slack.<이벤트 종류>` 설정이다.
- `DETAIL_HANGOUT_CHAT`: 위에 `thread`를 더한다. 응답이 2xx이고 그 리소스의 스레드가 아직 없으면 응답의 `thread.name`을 `webhook_thread`에 저장하고 다음부터 본문에 넣는다.
- `JSON`: 푸시만. `ref`, `commits`, `head_commit`, `sender`, `pusher`, `repository`.

## 요청

`POST`, 헤더는 `Content-Type: application/json`, `User-Agent: Yobi-Hookshot`, 토큰이 있으면 `Authorization: token <토큰> `. 응답이 2xx가 아니거나 연결하지 못하면 로그만 남긴다. 주소의 서버 부분은 `application.hostname`(없으면 `localhost:9000`)이다.
