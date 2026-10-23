---
title: 외부 저장소 가져오기
type: web
sources: [app/controllers/ImportApp.java, app/views/project/importing.scala.html]
updated: 2026-10-23
---

# 외부 저장소 가져오기

| 주소 | 설명 |
|---|---|
| `GET /_import?owner=` | 가져오기 폼. 로그인 필요(안내 `user.login.alert`) |
| `POST /_import` | 가져오기. 성공하면 프로젝트 홈으로 303, 입력이나 저장소 오류이면 400과 함께 같은 폼 |

## 입력

`url`(필수), `authId`, `authPw`(필요할 때만), `owner`, `name`, `overview`, `projectScope`, 메뉴(`code`, `issue`, `pullRequest`, `review`, `milestone`, `board`: 값이 `true`인 것만 켬), `vcs`(숨은 값 `GIT`).

## 검증과 오류

순서: 이름 규칙과 공개 범위 → 소유자(사용자도 조직도 아니거나 다른 사용자이면 `project.owner.invalidate`, 조직이면 관리자가 아닐 때 403) → 같은 이름 `project.name.duplicate` → 주소가 비면 `project.import.error.empty.url`. 하나라도 오류가 있으면 이름에 `project.name.alert`를 더한다.

저장소 복제 실패: 주소가 틀렸거나 저장소가 아니면 `project.import.error.wrong.url`. 접근 권한이 없으면 인증 정보가 없을 때 `repoAuth` 오류와 `…transport.unauthorized`, 있을 때 `authId`에 `…transport.failedToAuth`. 금지되면 `…transport.forbidden`. 그 밖의 전송 오류는 응답 코드를 넣은 `…transport`. 실패하면 복제하다 만 저장소 디렉터리를 지운다.

## 만들기

저장소를 모든 브랜치와 함께 bare로 복제한 뒤 프로젝트를 저장한다. 사이트 관리자(1번)는 SITEMANAGER, 소유자가 사용자이면 만든 사람은 MANAGER이다. 소유자가 조직이면 조직에 연결하고 만든 사람에게 역할을 주지 않는다. 구독과 방문 기록은 남기지 않는다. 결함은 R-46, 차이는 A-83을 본다.
