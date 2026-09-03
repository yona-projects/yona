# 웹훅 기능의 내부 동작

legacy Yona의 `docs/ko/technical/webhook-server-internal.md`를 옮김. 웹훅 등록/삭제 URL
경로와 payload 검증 규칙(2000자/250자 제한)까지 `WebhookController`에 그대로 남아 있고,
push 이벤트 payload JSON 구조도 legacy 예시와 필드 구성이 거의 동일하다 — 코드로 확인했다.

## 개요

웹훅 기능은 프로젝트 내 특정 이벤트(현재는 push 이벤트만 구현)와, 그로 인해 발생하는 HTTP
요청으로 이루어진다.

웹훅의 등록/삭제는 `WebhookController`가 처리한다.

```
GET     /projects/{owner}/{projectName}/webhooks              등록된 웹훅 목록
POST    /projects/{owner}/{projectName}/webhooks              웹훅 등록
DELETE  /projects/{owner}/{projectName}/webhooks/{id}          웹훅 삭제
```

**legacy 대비 바뀐 점**: URL에 `/projects` 접두사가 붙었고(legacy는 `/:user/:project/webhooks`),
삭제는 POST가 아니라 표준 HTTP `DELETE` 메서드를 쓴다.

## 웹훅 등록/삭제

등록된 웹훅은 해당 프로젝트 안에서만 확인·관리할 수 있으며, 그 프로젝트에서 발생하는
이벤트에만 반응한다.

새 웹훅 등록은 `POST .../webhooks`로 하며, 아래 두 값을 폼으로 받는다.

- **Payload URL** — 웹훅이 동작할 때 HTTP 요청을 보낼 주소. 최대 2000자.
- **Secret** — 요청을 받는 서버가 그 요청을 구분하기 위한 비밀 토큰. 최대 250자, 선택 사항.

두 제한을 넘으면 `400 Bad Request`로 거부된다(legacy와 동일한 2000/250 제한을 그대로
유지했다).

삭제는 `DELETE .../webhooks/{id}`로 한다.

## 웹훅 동작

등록된 웹훅은 프로젝트에서 특정 이벤트가 발생하면 동작한다 — 현재는 push 이벤트만
구현되어 있다(legacy와 동일한 범위).

push 이벤트가 발생하면(`GitPushHooks`가 git push 훅으로 감지) 프로젝트에 걸린 웹훅들을
순회하며 각 payload URL에 HTTP POST 요청을 보낸다(`WebhookNotificationEventListener`,
`WebhookServiceImpl`).

요청 payload는 아래와 같은 JSON 구조다(legacy의 GitHub 웹훅 유사 구조를 그대로 유지).

```json
{
    "ref": ["refs/heads/master"],
    "commits": [
        {
            "id": "c2f9f27ea16004020d1f4e846217c2825d217a12",
            "message": "test\n",
            "timestamp": "2015-06-12T04:41:21+0900",
            "url": "http://localhost:8080/dddeeee/commit/c2f9f27ea16004020d1f4e846217c2825d217a12",
            "author": {"name": "hello", "email": "hello@hello.com"},
            "committer": {"name": "hello", "email": "hello@hello.com"}
        }
    ],
    "head_commit": { "...": "commits[0]과 동일 구조" },
    "sender": {
        "login": "hello",
        "id": 2,
        "avatar_url": "/assets/images/default-avatar-128.png",
        "type": "User",
        "site_admin": false
    },
    "pusher": {"name": "hello", "email": "hello@hello.com"},
    "repository": {
        "id": 33,
        "name": "dddeeee",
        "owner": "hello",
        "html_url": "/hello/dddeeee",
        "overview": "eee",
        "private": false
    }
}
```

이벤트 종류를 확장하려면(예: 이슈 생성, PR 생성 등) 해당 이벤트가 발생하는 지점에서
`WebhookServiceImpl`을 호출하도록 새로 연결해야 한다 — 현재 구현은 push 이벤트 전용이다.
